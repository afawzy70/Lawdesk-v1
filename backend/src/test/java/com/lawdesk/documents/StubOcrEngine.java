package com.lawdesk.documents;

import java.util.function.Function;

/** A recognition engine the tests control: available or not, and what it "reads" from an image. */
public class StubOcrEngine implements OcrEngine {

    public volatile boolean available = true;
    public volatile String detail = null;
    public volatile Function<byte[], String> reader = image -> "";
    public volatile int calls;
    public volatile boolean failing;
    /** When set, the engine answers {@link #read} with this (a main text, alternates and a confidence count) instead of {@link #reader}. */
    public volatile Function<byte[], Reading> reading;

    public void reset() {
        reading = null;
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

    @Override
    public Reading read(byte[] image) throws OcrException {
        Function<byte[], Reading> custom = reading;
        if (custom == null) {
            return OcrEngine.super.read(image);
        }
        calls++;
        if (failing) {
            throw new OcrException("the stub failed", null);
        }
        return custom.apply(image);
    }
}
