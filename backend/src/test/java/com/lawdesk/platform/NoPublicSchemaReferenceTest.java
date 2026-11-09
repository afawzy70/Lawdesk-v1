package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guard for the defect of LD-058 (production #79): the deployed database role has its own schema as search path and no rights on
 * {@code public} (deploy/postgres/app-role.sql). SQL that names {@code public.<table>}, asks {@code to_regclass('public...')} or
 * reads the {@code public} schema's catalogue rows fails there with "permission denied" while it passes on every test database,
 * where the user is a superuser. No SQL of the application, written in Java or in a migration, may do so: names are written plain
 * and resolve through the search path. {@link RestrictedRoleApplicationTest} proves the behaviour; this fails at once, naming the
 * file and line, and also covers code paths no test reaches.
 */
class NoPublicSchemaReferenceTest {

    /** {@code public.name}, {@code "public".name}, {@code to_regclass('public...')}, {@code schema public}, {@code schemaname/table_schema/nspname = 'public'}. */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("(?i)(?<![\\w.])\"?public\"?\\s*\\.\\s*[\"a-z_]"),
            Pattern.compile("(?i)to_regclass\\s*\\(\\s*[^)]*public"),
            Pattern.compile("(?i)\\bschema\\s+\"?public\\b"),
            Pattern.compile("(?i)\\b(?:table_schema|schemaname|nspname|schema_name)\\b\\s*(?:=|in|like)\\s*\\(?\\s*'public'"),
            Pattern.compile("(?i)set\\s+search_path[^;\"]*\\bpublic\\b"));

    static List<String> violations(String fileName, String source) {
        String text = fileName.endsWith(".java") ? stripJavaComments(source) : fileName.endsWith(".sql") ? stripSqlComments(source) : source;
        List<String> found = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            for (Pattern pattern : FORBIDDEN) {
                if (pattern.matcher(lines[i]).find()) {
                    found.add(fileName + ":" + (i + 1) + ": " + lines[i].strip());
                    break;
                }
            }
        }
        return found;
    }

    /** Comments are removed line for line (blanked), so reported line numbers stay right. */
    private static String stripJavaComments(String source) {
        StringBuilder out = new StringBuilder();
        boolean block = false;
        for (String line : source.split("\n", -1)) {
            StringBuilder kept = new StringBuilder();
            int i = 0;
            boolean inString = false;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (block) {
                    if (line.startsWith("*/", i)) {
                        block = false;
                        i += 2;
                    } else {
                        i++;
                    }
                } else if (!inString && line.startsWith("/*", i)) {
                    block = true;
                    i += 2;
                } else if (!inString && line.startsWith("//", i) && !(i > 0 && line.charAt(i - 1) == ':')) {
                    break;
                } else {
                    if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                        inString = !inString;
                    }
                    kept.append(c);
                    i++;
                }
            }
            out.append(kept).append('\n');
        }
        return out.toString();
    }

    private static String stripSqlComments(String source) {
        return Pattern.compile("--[^\n]*").matcher(source).replaceAll("");
    }

    @Test
    void theApplicationNeverNamesThePublicSchema() throws IOException {
        Path root = Path.of("src", "main");
        if (!Files.isDirectory(root)) {
            root = Path.of("backend", "src", "main");
        }
        List<String> found = new ArrayList<>();
        int files = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".java") || name.endsWith(".sql") || name.endsWith(".xml") || name.endsWith(".yml")
                        || name.endsWith(".yaml") || name.endsWith(".properties")) {
                    files++;
                    found.addAll(violations(root.relativize(file).toString(), Files.readString(file)));
                }
            }
        }
        assertThat(files).as("source files scanned").isGreaterThan(150);
        assertThat(found).as("SQL that names the public schema; deployed roles have no rights on it, write the plain name").isEmpty();
    }

    @Test
    void theDetectorFindsEveryWayOfNamingPublicAndIgnoresComments() {
        for (String bad : List.of("select * from public.deadline", "select * from \"public\".deadline", "select to_regclass('public.deadline')",
                "select to_regclass(:schema || 'public')", "create table public.x (id int)", "grant usage on schema public to x",
                "where table_schema = 'public'", "where schemaname='public'", "set search_path = lawdesk, public",
                "jdbc.sql(\"select count(*) from public.task\")")) {
            assertThat(violations("X.java", bad)).as(bad).hasSize(1);
        }
        for (String ok : List.of("select to_regclass('deadline')", "public class A { public static void main() {} }",
                "// public.deadline is wrong\nint x;", "/* to_regclass('public.x')\n public.y */ int z;",
                "import com.lawdesk.public_api.X;", "String s = \"http://example.invalid\"; int pub = 1;")) {
            assertThat(violations("Y.java", ok)).as(ok).isEmpty();
        }
        assertThat(violations("V1.sql", "-- public.deadline\ncreate table t (id int); -- public.t")).isEmpty();
        assertThat(violations("V2.sql", "create table t (id int);\ninsert into public.t values (1);")).containsExactly("V2.sql:2: insert into public.t values (1);");
    }
}
