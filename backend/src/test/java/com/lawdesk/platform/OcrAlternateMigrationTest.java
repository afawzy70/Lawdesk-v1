package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V850 (OCR alternate readings and confidence) on a database that already holds documents and recognised text: the migration
 * applies, the old text and its normalised search text are exactly as they were, the old documents are still found, the new
 * measurements are NULL ("not measured", never a made-up zero), a second run changes nothing, the migration is restore-safe (no
 * function, no schema-qualified name), and a pg_dump / pg_restore of the post-V850 schema keeps text and alternates (skipped, visibly,
 * where pg_dump is not on the PATH).
 */
@SpringBootTest
class OcrAlternateMigrationTest extends PostgresTestSupport {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    @Autowired
    private Flyway flyway;

    private Flyway configured(DataSource database, MigrationVersion target) {
        return Flyway.configure().configuration(flyway.getConfiguration()).dataSource(database)
                .locations("classpath:db/migration").target(target).load();
    }

    private record Old(UUID document, UUID version) {
    }

    private Old oldDocument(JdbcTemplate jdbc, UUID caseId, String title, String status, String method, String text) {
        UUID doc = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, ?, ?, 10, repeat('a', 64), ?) returning id""", UUID.class, caseId, title, title + ".png", ACTOR);
        UUID version = doc;
        jdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 1, ?, 'image/png', 10, repeat('a', 64), ?)""", version, doc, title + ".png", ACTOR);
        jdbc.update("insert into document_text (version_id, document_id, status, method, text, processed_at) values (?, ?, ?, ?, ?, now())",
                version, doc, status, method, text);
        return new Old(doc, version);
    }

    private List<Map<String, Object>> snapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select t.version_id, t.document_id, t.status, t.method, t.text, t.search_text, t.page_count, t.truncated, t.attempts,
                       d.search_vec::text as search_vec
                from document_text t join case_document d on d.id = t.document_id order by t.version_id""");
    }

    @Test
    void oldDocumentsKeepTheirTextAndSearchAndTheNewMeasurementsAreNotFabricated() {
        DataSource database = freshDatabase();
        configured(database, MigrationVersion.fromVersion("845")).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        UUID caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('LD-2025-00031', 'Existing case', 'CIVIL', date '2025-01-01', ?, ?) returning id""", UUID.class, ACTOR, ACTOR);
        jdbc.update("insert into case_document_quota (case_id) values (?) on conflict do nothing", caseId);
        // documents as the release before V850 stored them: a scan read by OCR, a file with a text layer, one still waiting
        Old scan = oldDocument(jdbc, caseId, "scan", "OCR_DONE", "OCR", "المادة 12 من القانون المدني الكوبتي\nمذكرة دفاع");
        Old layer = oldDocument(jdbc, caseId, "layer", "EXTRACTED", "PDF_TEXT", "عقد الإيجار رقم LD-2026/045");
        Old waiting = oldDocument(jdbc, caseId, "waiting", "PENDING", "OCR", null);
        List<Map<String, Object>> before = snapshot(jdbc);
        assertThat(before).hasSize(3);
        assertThat(before.get(0).get("search_text")).isNotNull();

        configured(database, MigrationVersion.LATEST).migrate();

        // text, normalised text, state and the stored search vector of every old document are exactly what they were
        assertThat(snapshot(jdbc)).isEqualTo(before);
        // old documents are still found: by the normalised text and by the stored vector, in Arabic variants too
        assertThat(jdbc.queryForObject("select count(*) from document_text where position(library_normalize('القانون المدني') in search_text) > 0",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from case_document where search_vec @@ to_tsquery('simple', library_normalize('مذكره'))",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from case_document where search_vec @@ to_tsquery('simple', 'ld')", Integer.class))
                .isEqualTo(1);
        // the new state is explicit: nothing measured, no alternates, no claim; not zero
        assertThat(jdbc.queryForObject("""
                select count(*) from document_text where words_read is null and words_uncertain is null and claimed_at is null""",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from document_text_alternate", Integer.class)).isZero();
        // the pending one is still pending and claimable
        assertThat(jdbc.queryForObject("select status from document_text where version_id = ?", String.class, waiting.version())).isEqualTo("PENDING");
        assertThat(scan.document()).isNotEqualTo(layer.document());

        // a second run changes nothing
        configured(database, MigrationVersion.LATEST).migrate();
        assertThat(snapshot(jdbc)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from document_text_alternate", Integer.class)).isZero();
    }

    @Test
    void theMigrationIsRestoreSafe() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V850__LD-055_ocr_alternate_readings_and_confidence.sql"));
        String code = sql.lines().filter(l -> !l.stripLeading().startsWith("--")).collect(java.util.stream.Collectors.joining("\n")).toLowerCase();
        // no function (so nothing whose search path pg_restore would empty), no schema-qualified object
        assertThat(code).doesNotContain("create function").doesNotContain("create or replace function").doesNotContain("public.");
        // the one expression that calls a function, the generated column, uses library_normalize, which V800 made restore-safe
        // and which depends on pg_catalog only
        assertThat(code).contains("library_normalize(word)");
    }

    @Test
    void aDumpAndRestoreOfThePostV850SchemaKeepsTextAndAlternates() throws Exception {
        assumeTrue(onPath("pg_dump") && onPath("pg_restore") && onPath("psql"), "BLOCKED: pg_dump / pg_restore / psql are not on the PATH");
        PGSimpleDataSource source = (PGSimpleDataSource) freshDatabase();
        configured(source, MigrationVersion.fromVersion("845")).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        UUID caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('LD-2025-00032', 'Dump case', 'CIVIL', date '2025-01-01', ?, ?) returning id""", UUID.class, ACTOR, ACTOR);
        jdbc.update("insert into case_document_quota (case_id) values (?) on conflict do nothing", caseId);
        Old old = oldDocument(jdbc, caseId, "scan", "OCR_DONE", "OCR", "نص قديم عن الكويتي");
        configured(source, MigrationVersion.LATEST).migrate();
        jdbc.update("""
                insert into document_text_alternate (version_id, document_id, page, line_no, word, pass, confidence)
                values (?, ?, 1, 2, 'LD-2026/045', 'grey-sparse', 88.5)""", old.version(), old.document());
        jdbc.update("update document_text set words_read = 9, words_uncertain = 2 where version_id = ?", old.version());
        String fingerprint = fingerprint(jdbc);

        String target = "lawdesk_restore_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create database " + target);
        Path dump = Files.createTempFile("v850", ".dump");
        try {
            run(source, source.getDatabaseName(), "pg_dump", "-Fc", "-f", dump.toString(), "-d", source.getDatabaseName());
            run(source, target, "pg_restore", "--exit-on-error", "-d", target, dump.toString());
        } finally {
            Files.deleteIfExists(dump);
        }
        PGSimpleDataSource restored = new PGSimpleDataSource();
        restored.setUrl(source.getUrl().substring(0, source.getUrl().lastIndexOf('/') + 1) + target);
        restored.setUser(source.getUser());
        restored.setPassword(source.getPassword());
        JdbcTemplate copy = new JdbcTemplate(restored);
        assertThat(fingerprint(copy)).isEqualTo(fingerprint).contains("LD-2026/045").contains("نص قديم");
        // the generated columns were recomputed by the restore, with the same values
        assertThat(copy.queryForObject("select norm from document_text_alternate", String.class)).isEqualTo("ld 2026 045");
    }

    private static String fingerprint(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select t.status || '|' || coalesce(t.text, '') || '|' || coalesce(t.search_text, '') || '|' || coalesce(t.words_read::text, 'null')
                       || '|' || coalesce(t.words_uncertain::text, 'null') || '|' || coalesce(a.word, '') || '|' || coalesce(a.norm, '')
                       || '|' || coalesce(a.pass, '') || '|' || coalesce(a.confidence::text, '') as f
                from document_text t left join document_text_alternate a on a.version_id = t.version_id order by t.version_id, a.id""",
                String.class).toString();
    }

    private static boolean onPath(String tool) {
        for (String dir : System.getenv("PATH").split(java.io.File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, tool))) {
                return true;
            }
        }
        return false;
    }

    private static void run(PGSimpleDataSource source, String database, String... command) throws Exception {
        List<String> args = new java.util.ArrayList<>(List.of(command));
        args.addAll(1, List.of("-h", source.getServerNames()[0], "-p", String.valueOf(source.getPortNumbers()[0]), "-U", source.getUser()));
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().put("PGPASSWORD", source.getPassword());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(String.join(" ", command) + ": " + output).isZero();
        assertThat(output).as("stderr of " + command[0]).isEmpty();
    }
}
