package com.lawdesk.documents;

import java.util.function.Function;

/** A recognition engine the tests control: available or not, and what it "reads" from an image. */
public class StubOcrEngine implements OcrEngine {

    public volatile boolean available = true;
    public volatile String detail = null;
    public volatile Function<byte[], String> reader = image -> "";
    public volatile int calls;
    public volatile boolean failing;

    public void reset() {
        available = true;
        detail = null;
        reader = image -> "";
        calls = 0;
        failing = false;
    }

    @Override
    public Status status() {
        return new Status(available, "stub", "ara+eng", available ? null : (detail == null ? "engine not installed on this server" : detail));
    }

    @Override
    public String recognise(byte[] image) throws OcrException {
        calls++;
        if (failing) {
            throw new OcrException("the stub failed", null);
        }
        return reader.apply(image);
    }
}
