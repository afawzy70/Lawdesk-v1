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
    private final boolean preprocess;
    private final int pageSegmentation;
    private final boolean multiPass;
    private volatile Status cached;
    private volatile long cachedAt;

    @org.springframework.beans.factory.annotation.Autowired
    TesseractOcrEngine(@Value("${lawdesk.ocr.tesseract-command:tesseract}") String command,
            @Value("${lawdesk.ocr.languages:ara+eng}") String languages,
            @Value("${lawdesk.ocr.preprocess:true}") boolean preprocess,
            @Value("${lawdesk.ocr.psm:3}") int pageSegmentation,
            @Value("${lawdesk.ocr.multi-pass:true}") boolean multiPass) {
        this.command = command;
        this.multiPass = multiPass;
        this.pageSegmentation = pageSegmentation;
        this.languages = languages;
        this.preprocess = preprocess;
    }

    TesseractOcrEngine(String command, String languages) {
        this(command, languages, true, 3, true);
    }

    TesseractOcrEngine(String command, String languages, boolean preprocess, int pageSegmentation) {
        this(command, languages, preprocess, pageSegmentation, true);
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

    /** Pages whose first reading has fewer lines than this are read a second time in sparse mode (see {@link #merge}). */
    static final int SPARSE_BELOW_LINES = 8;

    /** A word read with less confidence than this (0 to 100) is "uncertain"; an alternate word must reach {@link #ALTERNATE_MIN}. */
    static final double UNCERTAIN_BELOW = 80;
    static final double ALTERNATE_MIN = 60;

    /** One run of the engine: its text, and every word with the confidence the engine gave it. */
    record Pass(String name, String text, List<Word> words) {
    }

    record Word(String text, double confidence) {
    }

    @Override
    public String recognise(byte[] image) throws OcrException {
        return read(image).text();
    }

    /**
     * The main reading (the thresholded, deskewed page, automatic layout, plus the sparse reading of short pages) and, when the
     * main reading holds a word the engine was unsure of or the page is short, further readings of the same picture: the page
     * as scanned, deskewed but not thresholded, enlarged to about 300 dpi, and sparse-text layout. Words those readings are
     * confident about and the main reading lacks become the {@code alternates}. Nothing is invented: every word was read by the
     * engine from this page, and no pass can remove a word of another.
     */
    @Override
    public Reading read(byte[] image) throws OcrException {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("lawdesk-ocr");
            byte[] prepared = preprocess ? OcrPreprocessor.prepare(image) : image;
            Pass main = run(dir, "main", prepared, pageSegmentation);
            String text = main.text();
            // Measured (docs/evidence/DOC-OCR): on a page of only a few lines the automatic layout analysis can drop a whole
            // line (a two-line Arabic page at 300 dpi lost its first line); sparse-text mode finds it. The second reading is
            // cheap on such pages and only adds lines the first did not contain, so it can add words to the search but never remove any.
            boolean shortPage = text.lines().filter(l -> !l.isBlank()).count() < SPARSE_BELOW_LINES;
            List<Pass> others = new java.util.ArrayList<>();
            if (pageSegmentation != 11 && shortPage) {
                Pass sparse = run(dir, "sparse", prepared, 11);
                text = merge(text, sparse.text());
                others.add(sparse);
            }
            long unsure = main.words().stream().filter(w -> w.confidence() < UNCERTAIN_BELOW).count();
            if (multiPass && preprocess && (unsure > 0 || shortPage)) {
                // The thresholded page is the one most likely to have lost a dot or a stroke (observed: one yeh read as beh); the
                // other readings start from the picture as it was stored.
                others.add(run(dir, "original", image, pageSegmentation));
                byte[] grey = OcrPreprocessor.prepareGrey(image, false);
                if (grey != null) {
                    others.add(run(dir, "grey", grey, pageSegmentation));
                }
                byte[] enlarged = OcrPreprocessor.prepareGrey(image, true);
                if (enlarged != null) {
                    others.add(run(dir, "enlarged", enlarged, pageSegmentation));
                }
                if (pageSegmentation != 11) {
                    others.add(run(dir, "original-sparse", image, 11));
                }
            }
            // A word counts as already read when its normalised form (a reference such as LD-2026/045 is "ld 2026 045") occurs
            // as a run of words in the text read so far.
            StringBuilder known = new StringBuilder(" ");
            for (String line : text.split("\\R")) {
                known.append(String.join(" ", words(line))).append("  ");
            }
            Set<String> alternates = new java.util.LinkedHashSet<>();
            for (Pass pass : others) {
                for (Word w : pass.words()) {
                    String key = String.join(" ", words(w.text()));
                    if (w.confidence() >= ALTERNATE_MIN && !key.isEmpty() && !known.toString().contains(" " + key + " ")) {
                        known.append(key).append("  ");
                        alternates.add(w.text());
                    }
                }
            }
            List<String> doubtful = main.words().stream().filter(w -> w.confidence() < UNCERTAIN_BELOW)
                    .map(Word::text).filter(t -> !words(t).isEmpty()).toList();
            return new Reading(text, String.join(" ", alternates), main.words().size(), doubtful.size(), doubtful,
                    1 + others.size());
        } catch (IOException e) {
            throw new OcrException("the engine could not be run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException("interrupted", e);
        } finally {
            deleteQuietly(dir);
        }
    }

    private Pass run(Path dir, String name, byte[] bytes, int psm) throws IOException, InterruptedException, OcrException {
        Path input = dir.resolve("in-" + name);
        Files.write(input, bytes);
        Path base = dir.resolve("out-" + name + "-" + psm);
        // The output goes to files, not a pipe: reading a pipe to its end would wait for a hung engine and the time limit would never apply.
        Process process = new ProcessBuilder(command, input.toString(), base.toString(), "-l", languages, "--psm",
                Integer.toString(psm), "txt", "tsv").redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(RECOGNISE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new OcrException("the engine took too long", null);
        }
        if (process.exitValue() != 0) {
            throw new OcrException("the engine failed on this page", null);
        }
        return new Pass(name, Files.readString(Path.of(base + ".txt"), StandardCharsets.UTF_8), parse(Path.of(base + ".tsv")));
    }

    /** Word rows (level 5) of the engine's tab-separated output: confidence in column 11, the word in column 12. */
    static List<Word> parse(Path tsv) throws IOException {
        List<Word> words = new java.util.ArrayList<>();
        if (!Files.exists(tsv)) {
            return words;
        }
        for (String row : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            String[] c = row.split("\t", 12);
            if (c.length == 12 && "5".equals(c[0]) && !c[11].isBlank()) {
                try {
                    words.add(new Word(c[11].strip(), Double.parseDouble(c[10])));
                } catch (NumberFormatException ignored) {
                    // the header row
                }
            }
        }
        return words;
    }

    /**
     * The first text plus the lines of the second that the first lacks. A line is "lacked" when it shares fewer than half of its
     * words with every line of the first: a second reading of a line already read differs by a letter or two and is skipped, a
     * line the first reading dropped altogether is inserted where the second reading had it (before the first-text line that
     * follows it there, or at the end).
     */
    static String merge(String first, String second) {
        List<String> base = new java.util.ArrayList<>(first.lines().filter(l -> !l.isBlank()).toList());
        List<List<String>> baseWords = new java.util.ArrayList<>(base.stream().map(TesseractOcrEngine::words).toList());
        List<String> other = second.lines().filter(l -> !l.isBlank()).toList();
        int[] matched = new int[other.size()];
        for (int i = 0; i < other.size(); i++) {
            matched[i] = bestMatch(words(other.get(i)), baseWords);
        }
        // From the last line to the first, so that insertion points computed on the first text stay valid.
        for (int i = other.size() - 1; i >= 0; i--) {
            List<String> w = words(other.get(i));
            if (w.isEmpty() || matched[i] >= 0) {
                continue;
            }
            int at = base.size();
            for (int j = i + 1; j < other.size(); j++) {
                if (matched[j] >= 0) {
                    at = matched[j];
                    break;
                }
            }
            base.add(at, other.get(i).strip());
            baseWords.add(at, w);
            // lines after the insertion point moved by one
            for (int j = 0; j < matched.length; j++) {
                if (matched[j] >= at) {
                    matched[j]++;
                }
            }
        }
        return base.isEmpty() ? first : String.join("\n", base) + "\n";
    }

    /** The index of the line of the first text sharing at least half of this line's words, or -1. */
    private static int bestMatch(List<String> words, List<List<String>> baseWords) {
        if (words.isEmpty()) {
            return 0; // noise without letters is never added
        }
        int best = -1;
        double bestShare = 0;
        for (int k = 0; k < baseWords.size(); k++) {
            long shared = words.stream().filter(baseWords.get(k)::contains).count();
            double share = (double) shared / words.size();
            if (share >= 0.5 && share > bestShare) {
                best = k;
                bestShare = share;
            }
        }
        return best;
    }

    private static List<String> words(String text) {
        String t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).toLowerCase()
                .replaceAll("[\\u064B-\\u0670\\u0640\\u06D6-\\u06ED]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        return t.isEmpty() ? List.of() : Arrays.asList(t.split(" +"));
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
