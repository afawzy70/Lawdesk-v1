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

    Status status();

    /** The text of one page image (PNG, JPEG or TIFF bytes), in reading order, one line per line of the page. */
    String recognise(byte[] image) throws OcrException;
}
