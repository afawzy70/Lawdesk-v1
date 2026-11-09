package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * "Today" is the Kuwait day everywhere (owner assumption: Kuwait time for display; requirements sections 14 and 32): the
 * due and overdue of tasks, deadlines, hearings, information requests and reports must agree with the calendar. A zone
 * literal other than Asia/Kuwait in the production code would make one screen disagree with another around midnight.
 */
class TimeZoneConsistencyTest {

    private static final Pattern ZONE = Pattern.compile("(?:ZoneId\\.of\\(|at time zone )\\\\?\"?'?([A-Za-z]+/[A-Za-z_]+)");

    @Test
    void everyZoneNamedInTheProductionCodeIsKuwait() throws IOException {
        List<String> others;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            others = files.filter(p -> p.toString().endsWith(".java")).flatMap(p -> {
                try {
                    Matcher m = ZONE.matcher(Files.readString(p));
                    return m.results().map(r -> p.getFileName() + ": " + r.group(1));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).filter(s -> !s.endsWith("Asia/Kuwait")).toList();
        }
        assertThat(others).isEmpty();
    }
}
