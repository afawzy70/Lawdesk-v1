package com.lawdesk.documents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tesseract, run as a local process without a shell (no network, no service). Its Arabic data ({@code ara}) must be
 * installed for Arabic documents; when the program or a language is missing, {@link #status()} says so and no document is
 * marked as read. Whether the engine reads Arabic well enough is not assumed: the tests measure it where the engine exists.
 */
@Component
class TesseractOcrEngine implements OcrEngine {

    private static final Duration STATUS_CACHE = Duration.ofSeconds(30);
    private static final long STATUS_TIMEOUT_SECONDS = 10;
    private static final long RECOGNISE_TIMEOUT_SECONDS = 120;

    private final String command;
    private final String languages;
    private volatile Status cached;
    private volatile long cachedAt;

    TesseractOcrEngine(@Value("${lawdesk.ocr.tesseract-command:tesseract}") String command,
            @Value("${lawdesk.ocr.languages:ara+eng}") String languages) {
        this.command = command;
        this.languages = languages;
    }

    @Override
    public Status status() {
        long now = System.nanoTime();
        Status known = cached;
        if (known != null && now - cachedAt < STATUS_CACHE.toNanos()) {
            return known;
        }
        Status fresh = probe();
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    private Status probe() {
        try {
            Process process = new ProcessBuilder(command, "--list-langs").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Status(false, "tesseract", languages, "the engine did not answer in time");
            }
            Set<String> installed = Set.copyOf(Arrays.stream(output.split("\\R")).map(String::strip).toList());
            List<String> missing = Arrays.stream(languages.split("\\+")).filter(l -> !installed.contains(l)).toList();
            if (process.exitValue() != 0 || !missing.isEmpty()) {
                return new Status(false, "tesseract", languages,
                        missing.isEmpty() ? "the engine reported an error" : "language data not installed: " + String.join(",", missing));
            }
            return new Status(true, "tesseract", languages, null);
        } catch (IOException e) {
            return new Status(false, "tesseract", languages, "engine not installed on this server");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Status(false, "tesseract", languages, "interrupted while checking the engine");
        }
    }

    @Override
    public String recognise(byte[] image) throws OcrException {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("lawdesk-ocr");
            Path input = dir.resolve("page");
            Files.write(input, image);
            Process process = new ProcessBuilder(command, input.toString(), "stdout", "-l", languages)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(RECOGNISE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new OcrException("the engine took too long", null);
            }
            if (process.exitValue() != 0) {
                throw new OcrException("the engine failed on this page", null);
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OcrException("the engine could not be run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("interrupted", e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // A temporary directory the operating system will clean.
        }
    }
}
