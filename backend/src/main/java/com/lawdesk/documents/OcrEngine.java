package com.lawdesk.documents;

/**
 * The port to a text-recognition engine that runs on this server (owner decision D7): nothing is sent outside the server.
 * An implementation that cannot work says so in {@link #status()}; it never pretends to have read a file.
 */
public interface OcrEngine {

    /** Whether the engine can read documents now, which engine it is, and a reason when it cannot. */
    record Status(boolean available, String engine, String languages, String detail) {
    }

    /** The recognition failed for this image (not an unavailable engine). */
    class OcrException extends Exception {
        public OcrException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A word a pass other than the main one read with enough confidence and the main reading lacks: which pass, which line of it, how sure. */
    record Alternate(String word, String pass, int line, double confidence) {
    }

    /**
     * One page as read by every pass the engine made. {@code text} is the main reading (the only text that is ever shown,
     * quoted or analysed); {@code alternates} are single words other passes read, kept one by one so that no phrase can be
     * composed from them; the counts say how sure the engine was, so that "recognition uncertain" can be shown instead of a
     * silent miss. {@code uncertainWords}: words read with low confidence plus words only another pass read.
     */
    record Reading(String text, java.util.List<Alternate> alternates, int words, int uncertainWords,
            java.util.List<String> uncertainWordList, int passes) {
        /** The alternate words joined by spaces (measurement and tests only; never a text). */
        public String alternateText() {
            return alternates.stream().map(Alternate::word).collect(java.util.stream.Collectors.joining(" "));
        }
    }

    Status status();

    /** The text of one page image (PNG, JPEG or TIFF bytes), in reading order, one line per line of the page. */
    String recognise(byte[] image) throws OcrException;

    /** All passes of the engine for one page; an engine with a single pass returns that reading and no alternates. */
    default Reading read(byte[] image) throws OcrException {
        return new Reading(recognise(image), java.util.List.of(), 0, 0, java.util.List.of(), 1);
    }
}
