package com.lawdesk.documents;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The document tables (V403): metadata, bytes in their own table, and the per-case lock row. */
@Repository
class DocumentStore {

    private static final String SELECT = """
            select id, case_id, title, file_name, media_type, size_bytes, sha256, version, created_at, created_by
            from case_document""";

    private final JdbcTemplate jdbc;

    DocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks the case's document count until the transaction ends: every upload and removal of the case takes this
     * lock first, so counting and inserting cannot interleave.
     */
    void lockCase(UUID caseId) {
        jdbc.update("insert into case_document_quota (case_id) values (?) on conflict do nothing", caseId);
        jdbc.queryForList("select case_id from case_document_quota where case_id = ? for update", caseId);
    }

    int countForCase(UUID caseId) {
        Integer count = jdbc.queryForObject("select count(*) from case_document where case_id = ?", Integer.class,
                caseId);
        return count == null ? 0 : count;
    }

    CaseDocument insert(UUID caseId, String title, String fileName, byte[] content, String sha256, UUID actor) {
        CaseDocument stored = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, size_bytes, sha256, created_by)
                values (?, ?, ?, ?, ?, ?)
                returning id, case_id, title, file_name, media_type, size_bytes, sha256, version, created_at, created_by
                """, (rs, n) -> row(rs), caseId, title, fileName, content.length, sha256, actor);
        jdbc.update("insert into case_document_content (document_id, content) values (?, ?)", stored.id(), content);
        return stored;
    }

    Optional<CaseDocument> find(UUID id) {
        return jdbc.query(SELECT + " where id = ?", (rs, n) -> row(rs), id).stream().findFirst();
    }

    List<CaseDocument> forCase(UUID caseId) {
        return jdbc.query(SELECT + " where case_id = ? order by created_at desc, id", (rs, n) -> row(rs), caseId);
    }

    /** The latest documents of every case, newest first. */
    List<CaseDocument> latest(long offset, int limit) {
        return jdbc.query(SELECT + " order by created_at desc, id offset ? limit ?", (rs, n) -> row(rs), offset, limit);
    }

    long count() {
        Long count = jdbc.queryForObject("select count(*) from case_document", Long.class);
        return count == null ? 0 : count;
    }

    Optional<byte[]> content(UUID id) {
        return jdbc.query("select content from case_document_content where document_id = ?",
                (rs, n) -> rs.getBytes("content"), id).stream().findFirst();
    }

    /** Removes the document and, through the cascade, its bytes; true when that version was still stored. */
    boolean delete(UUID id, long version) {
        return jdbc.update("delete from case_document where id = ? and version = ?", id, version) == 1;
    }

    private static CaseDocument row(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new CaseDocument(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("title"), rs.getString("file_name"), rs.getString("media_type"), rs.getInt("size_bytes"),
                rs.getString("sha256"), rs.getLong("version"), created.toInstant(),
                rs.getObject("created_by", UUID.class));
    }
}
