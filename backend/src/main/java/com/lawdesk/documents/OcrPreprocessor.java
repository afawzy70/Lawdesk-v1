package com.lawdesk.documents;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Image clean-up applied before recognition: grey, Otsu threshold and deskew. Each step is separate so that the
 * measurement (docs/evidence/DOC-OCR) can compare them; the engine uses only the steps the measurement showed to help.
 * Pure Java2D: no native library, nothing leaves the server.
 */
final class OcrPreprocessor {

    /** Largest angle searched (degrees) and the step; a page skewed more than this is left as it is. */
    static final double MAX_ANGLE = 10;
    private static final double STEP = 0.25;
    /** Longest side processed; larger pages are analysed on a reduced copy and rotated at full size. */
    private static final int ANALYSIS_SIDE = 1000;

    /** Pages above this many pixels are not decoded here (a decompression bomb would exhaust the heap); the engine gets them as they are. */
    static final long MAX_PIXELS = 40_000_000L;

    private OcrPreprocessor() {
    }

    /**
     * What the engine is given for one stored page: the page deskewed (up to {@value #MAX_ANGLE} degrees) and thresholded.
     * When the picture cannot be prepared safely (an unreadable format, a multi-page TIFF, more than {@value #MAX_PIXELS}
     * pixels, any failure) the original bytes are returned unchanged: preprocessing can only improve a page, never lose one.
     */
    static byte[] prepare(byte[] original) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            if (input == null) {
                return original;
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return original;
            }
            var reader = readers.next();
            try {
                reader.setInput(input, false, true);
                if (reader.getNumImages(true) != 1 || (long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    return original;
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    return original;
                }
                return png(pad(threshold(deskew(image))));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return original;
        }
    }

    /** Decodes bytes into a picture, or null when Java cannot read the format (the engine then gets the original). */
    static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static BufferedImage gray(BufferedImage in) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(in, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Global Otsu threshold; the result is black ink on white paper. */
    static BufferedImage threshold(BufferedImage in) {
        BufferedImage grey = gray(in);
        int w = grey.getWidth();
        int h = grey.getHeight();
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[grey.getRaster().getSample(x, y, 0)]++;
            }
        }
        long total = (long) w * h;
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * (double) hist[i];
        }
        double sumB = 0;
        long wB = 0;
        double best = -1;
        int level = 127;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) {
                continue;
            }
            long wF = total - wB;
            if (wF == 0) {
                break;
            }
            sumB += t * (double) hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) {
                best = between;
                level = t;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.getRaster().setSample(x, y, 0, grey.getRaster().getSample(x, y, 0) > level ? 255 : 0);
            }
        }
        return out;
    }

    /**
     * The angle (degrees, positive = the page is rotated clockwise) that makes the rows of ink sharpest, by maximising the
     * variance of the row profile of a reduced copy; 0 when the page has too little ink to tell.
     */
    static double skew(BufferedImage in) {
        BufferedImage bw = threshold(in);
        double scale = Math.min(1.0, (double) ANALYSIS_SIDE / Math.max(bw.getWidth(), bw.getHeight()));
        int w = Math.max(1, (int) Math.round(bw.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(bw.getHeight() * scale));
        boolean[][] ink = new boolean[h][w];
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = Math.min(bw.getWidth() - 1, (int) (x / scale));
                int sy = Math.min(bw.getHeight() - 1, (int) (y / scale));
                if (bw.getRaster().getSample(sx, sy, 0) == 0) {
                    ink[y][x] = true;
                    count++;
                }
            }
        }
        if (count < 50) {
            return 0;
        }
        double bestAngle = 0;
        double bestScore = -1;
        for (double a = -MAX_ANGLE; a <= MAX_ANGLE + 1e-9; a += STEP) {
            double score = profileScore(ink, w, h, Math.toRadians(a));
            if (score > bestScore + 1e-9 || (Math.abs(score - bestScore) <= 1e-9 && Math.abs(a) < Math.abs(bestAngle))) {
                bestScore = score;
                bestAngle = a;
            }
        }
        return bestAngle;
    }

    private static double profileScore(boolean[][] ink, int w, int h, double radians) {
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        int rows = h + (int) Math.ceil(Math.abs(sin) * w) + 2;
        int[] profile = new int[rows];
        int offset = (int) Math.ceil(Math.abs(sin) * w) / 2 + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (ink[y][x]) {
                    // the row this pixel lands in when the page is rotated back by the angle
                    int r = (int) Math.round(y * cos - x * sin) + offset;
                    if (r >= 0 && r < rows) {
                        profile[r]++;
                    }
                }
            }
        }
        double score = 0;
        for (int i = 1; i < rows; i++) {
            double d = profile[i] - profile[i - 1];
            score += d * d;
        }
        return score;
    }

    /** Rotates the page back by {@code degrees} about its centre, on white. */
    static BufferedImage rotate(BufferedImage in, double degrees) {
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setTransform(AffineTransform.getRotateInstance(Math.toRadians(-degrees), in.getWidth() / 2.0, in.getHeight() / 2.0));
            g.drawImage(in, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static BufferedImage deskew(BufferedImage in) {
        double angle = skew(in);
        return Math.abs(angle) < STEP ? gray(in) : rotate(gray(in), angle);
    }

    /**
     * A white margin around the page. Tesseract drops the first line of a page whose text starts close to the edge (measured:
     * a two-line Arabic image with a 30 px top margin lost its first line); scans normally have a margin, cropped images do not.
     */
    static BufferedImage pad(BufferedImage in) {
        int margin = Math.max(32, Math.min(in.getWidth(), in.getHeight()) / 10);
        BufferedImage out = new BufferedImage(in.getWidth() + 2 * margin, in.getHeight() + 2 * margin, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(in, margin, margin, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    static BufferedImage scale(BufferedImage in, double factor) {
        int w = (int) Math.round(in.getWidth() * factor);
        int h = (int) Math.round(in.getHeight() * factor);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(in, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
