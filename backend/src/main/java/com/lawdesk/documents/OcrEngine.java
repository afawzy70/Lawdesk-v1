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

    /**
     * One page as read by every pass the engine made. {@code text} is the main reading; {@code alternates} are words other
     * passes read with enough confidence and the main reading lacks (searched, never shown as the page text); the counts say how
     * sure the engine was, so that "recognition uncertain" can be shown instead of a silent miss.
     */
    /** {@code uncertainWords}: words read with low confidence plus words only another pass read. */
    record Reading(String text, String alternates, int words, int uncertainWords, java.util.List<String> uncertainWordList,
            int passes) {
    }

    Status status();

    /** The text of one page image (PNG, JPEG or TIFF bytes), in reading order, one line per line of the page. */
    String recognise(byte[] image) throws OcrException;

    /** All passes of the engine for one page; an engine with a single pass returns that reading and no alternates. */
    default Reading read(byte[] image) throws OcrException {
        return new Reading(recognise(image), "", 0, 0, java.util.List.of(), 1);
    }
}
