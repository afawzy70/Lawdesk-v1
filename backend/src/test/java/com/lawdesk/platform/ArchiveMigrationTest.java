package com.lawdesk.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * LD-055 (owner decision D7): the files stay where they are and every existing attachment keeps its id, its bytes and its
 * links. The archive migrations (V700 to V705) are applied to a database holding documents written the way the previous
 * release wrote them; row counts, keys, metadata and content hashes are compared before and after. A logical copy of the
 * archive tables into a second database then shows the content survives a dump and restore with the same hashes.
 */
@SpringBootTest
class ArchiveMigrationTest extends PostgresTestSupport {

    private static final UUID ACTOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    @Autowired
    private Flyway flyway;

    private Flyway configured(DataSource database, MigrationVersion target) {
        return Flyway.configure().configuration(flyway.getConfiguration()).dataSource(database)
                .locations("classpath:db/migration").target(target).load();
    }

    /** The last migration older than the archive's first one (V700): the state of a deployed system before LD-055. */
    private MigrationVersion before() {
        return java.util.Arrays.stream(flyway.info().all()).map(i -> i.getVersion()).filter(v -> v != null && v.compareTo(MigrationVersion.fromVersion("700")) < 0)
                .max(MigrationVersion::compareTo).orElseThrow();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Legacy(UUID id, byte[] content) {
    }

    /** Documents as the previous release stored them: a head row, a bytes row and the quota row of the case. */
    private List<Legacy> legacyDocuments(JdbcTemplate jdbc) throws Exception {
        UUID caseId = jdbc.queryForObject("""
                insert into legal_case (reference_no, title, case_type, opened_on, created_by, updated_by)
                values ('LD-2025-00009', 'Existing case', 'CIVIL', date '2025-01-01', ?, ?) returning id""", UUID.class, ACTOR, ACTOR);
        jdbc.update("insert into case_document_quota (case_id) values (?)", caseId);
        List<Legacy> documents = new java.util.ArrayList<>();
        String[] texts = {"%PDF-1.7 statement of claim", "%PDF-1.4 صحيفة الدعوى", "%PDF-1.7 " + "x".repeat(50_000)};
        for (int i = 0; i < texts.length; i++) {
            byte[] content = texts[i].getBytes(StandardCharsets.UTF_8);
            UUID id = jdbc.queryForObject("""
                    insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                    values (?, ?, ?, ?, ?, ?) returning id""", UUID.class, caseId, "Document " + i, "file" + i + ".pdf",
                    content.length, sha256(content), ACTOR);
            jdbc.update("insert into case_document_content (document_id, content) values (?, ?)", id, content);
            documents.add(new Legacy(id, content));
        }
        // A claim that cites a document as its source: the link must stay valid.
        jdbc.update("""
                insert into claim (case_id, seq, text, kind, source_document_id, created_by, updated_by)
                values (?, 1, 'claim', 'PRIMARY', ?, ?, ?)""", caseId, documents.get(0).id(), ACTOR, ACTOR);
        return documents;
    }

    private List<Map<String, Object>> headSnapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select id, case_id, title, file_name, media_type, size_bytes, sha256, version, created_at, created_by,
                       encode(sha256(c.content), 'hex') as content_sha256, length(c.content) as content_length
                from case_document d join case_document_content c on c.document_id = d.id order by d.id""");
    }

    @Test
    void everyExistingDocumentGetsVersionOneWithTheSameIdAndContentAndNothingIsLost() throws Exception {
        DataSource database = freshDatabase();
        configured(database, before()).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        List<Legacy> documents = legacyDocuments(jdbc);
        List<Map<String, Object>> before = headSnapshot(jdbc);
        assertThat(before).hasSize(3);

        configured(database, MigrationVersion.LATEST).migrate();

        // The original rows and bytes are untouched.
        List<Map<String, Object>> after = headSnapshot(jdbc);
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from case_document", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from case_document_content", Integer.class)).isEqualTo(3);
        // Version 1 of each: same id, same metadata, same bytes (compared by hash computed in the database).
        assertThat(jdbc.queryForObject("select count(*) from document_version", Integer.class)).isEqualTo(3);
        for (Legacy legacy : documents) {
            Map<String, Object> row = jdbc.queryForMap("""
                    select v.id, v.version_no, v.file_name, v.media_type, v.size_bytes, v.sha256, v.uploaded_by,
                           v.uploaded_at = d.created_at as same_time, d.current_version_no,
                           encode(sha256(vc.content), 'hex') as stored_hash, vc.content as stored_content
                    from case_document d join document_version v on v.document_id = d.id
                    join document_version_content vc on vc.version_id = v.id where d.id = ?""", legacy.id());
            assertThat(row.get("id")).isEqualTo(legacy.id());
            assertThat(row.get("version_no")).isEqualTo(1);
            assertThat(row.get("current_version_no")).isEqualTo(1);
            assertThat(row.get("media_type")).isEqualTo("application/pdf");
            assertThat(row.get("uploaded_by")).isEqualTo(ACTOR);
            assertThat(row.get("same_time")).isEqualTo(true);
            assertThat(row.get("stored_hash")).isEqualTo(sha256(legacy.content())).isEqualTo(row.get("sha256"));
            assertThat((byte[]) row.get("stored_content")).isEqualTo(legacy.content());
            assertThat(row.get("size_bytes")).isEqualTo(legacy.content().length);
        }
        // The link a claim holds to its source document still resolves to the same row.
        assertThat(jdbc.queryForObject("""
                select d.title from claim c join case_document d on d.id = c.source_document_id""", String.class)).isEqualTo("Document 0");
        // Existing documents are in the case file, unclassified and not withdrawn.
        assertThat(jdbc.queryForObject("""
                select count(*) from case_document where withdrawn_at is null and category_id is null and document_date is null""",
                Integer.class)).isEqualTo(3);
        // Running the migrations again changes nothing.
        configured(database, MigrationVersion.LATEST).migrate();
        assertThat(headSnapshot(jdbc)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from document_version", Integer.class)).isEqualTo(3);
    }

    @Test
    void anArchiveWithNoDocumentsMigratesCleanly() {
        DataSource database = freshDatabase();
        configured(database, before()).migrate();
        configured(database, MigrationVersion.LATEST).migrate();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        assertThat(jdbc.queryForObject("select count(*) from document_version", Integer.class)).isZero();
        // The settings, the formats and the document categories of the requirements text are seeded.
        assertThat(jdbc.queryForObject("select count(*) from archive_setting", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from archive_format where enabled", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from lookup_value where list_code = 'DOCUMENT_CATEGORY' and origin = 'DOCUMENT'",
                Integer.class)).isEqualTo(8);
    }

    /**
     * The deployment's backup is a pg_dump of the database. No dump tool is available to the tests, so this copies the archive
     * tables to a second, freshly migrated database in PostgreSQL's binary copy format (what pg_dump uses for table data) and
     * compares the content hashes: bytes written by the application come back identical.
     */
    @Test
    void archivedContentSurvivesALogicalDumpAndRestoreWithTheSameHashes() throws Exception {
        DataSource source = freshDatabase();
        configured(source, before()).migrate();
        JdbcTemplate sourceJdbc = new JdbcTemplate(source);
        List<Legacy> documents = legacyDocuments(sourceJdbc);
        configured(source, MigrationVersion.LATEST).migrate();
        // A second version of the first document, as the application writes it.
        byte[] second = "%PDF-1.7 revised statement".getBytes(StandardCharsets.UTF_8);
        UUID versionId = UUID.randomUUID();
        sourceJdbc.update("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256, uploaded_by)
                values (?, ?, 2, 'file0-v2.pdf', 'application/pdf', ?, ?, ?)""", versionId, documents.get(0).id(), second.length, sha256(second), ACTOR);
        sourceJdbc.update("insert into document_version_content (version_id, content) values (?, ?)", versionId, second);

        DataSource target = freshDatabase();
        configured(target, MigrationVersion.LATEST).migrate();
        for (String table : List.of("legal_case", "case_document_quota", "case_document", "case_document_content", "document_version",
                "document_version_content")) {
            copy(source, target, table);
        }

        JdbcTemplate targetJdbc = new JdbcTemplate(target);
        for (String table : List.of("case_document", "case_document_content", "document_version", "document_version_content")) {
            assertThat(targetJdbc.queryForObject("select count(*) from " + table, Integer.class))
                    .as(table).isEqualTo(sourceJdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
        List<Map<String, Object>> restored = targetJdbc.queryForList("""
                select v.id, v.version_no, v.sha256, encode(sha256(c.content), 'hex') as actual
                from document_version v join document_version_content c on c.version_id = v.id order by v.document_id, v.version_no""");
        assertThat(restored).hasSize(4);
        for (Map<String, Object> row : restored) {
            assertThat(row.get("actual")).isEqualTo(row.get("sha256"));
        }
        assertThat(targetJdbc.queryForList("select encode(sha256(content), 'hex') from document_version_content order by 1", String.class))
                .isEqualTo(sourceJdbc.queryForList("select encode(sha256(content), 'hex') from document_version_content order by 1", String.class));
    }

    private static void copy(DataSource from, DataSource to, String table) throws Exception {
        try (Connection source = from.getConnection(); Connection target = to.getConnection()) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            CopyManager out = source.unwrap(PGConnection.class).getCopyAPI();
            out.copyOut("copy " + table + " to stdout with (format binary)", data);
            CopyManager in = target.unwrap(PGConnection.class).getCopyAPI();
            in.copyIn("copy " + table + " from stdin with (format binary)", new ByteArrayInputStream(data.toByteArray()));
        }
    }
}
