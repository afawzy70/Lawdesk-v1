package com.lawdesk.documents;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.lawdesk.cases.CaseAccessPolicy;

/** The document tables (V403, V701): the head, its versions, their bytes, and the per-case lock row. */
@Repository
class DocumentStore {

    private static final String COLUMNS = """
            id, case_id, title, file_name, media_type, size_bytes, sha256, version, created_at, created_by, category_id,
            document_date, proceeding_id, description, current_version_no, withdrawn_at, withdrawn_reason, updated_at""";
    private static final String SELECT = "select " + COLUMNS + " from case_document";
    private static final String VERSION_SELECT = """
            select id, document_id, version_no, file_name, media_type, size_bytes, sha256, change_note, uploaded_by,
                   uploaded_at from document_version""";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    DocumentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /**
     * Locks the case's document count until the transaction ends: every upload, restore and removal of the case takes this
     * lock first, so counting and inserting cannot interleave.
     */
    void lockCase(UUID caseId) {
        jdbc.update("insert into case_document_quota (case_id) values (?) on conflict do nothing", caseId);
        jdbc.queryForList("select case_id from case_document_quota where case_id = ? for update", caseId);
    }

    /** Documents still in the case file (not withdrawn). */
    int countForCase(UUID caseId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from case_document where case_id = ? and withdrawn_at is null", Integer.class, caseId);
        return count == null ? 0 : count;
    }

    CaseDocument insert(UUID caseId, String title, String fileName, String mediaType, byte[] content, String sha256,
            UUID actor) {
        CaseDocument stored = jdbc.queryForObject("""
                insert into case_document (case_id, title, file_name, media_type, size_bytes, sha256, created_by, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?) returning""" + " " + COLUMNS, (rs, n) -> row(rs), caseId, title, fileName,
                mediaType, content.length, sha256, actor, actor);
        // Version 1 takes the document's own id, as the migrated documents do.
        insertVersion(stored.id(), stored.id(), 1, fileName, mediaType, content, sha256, null, actor);
        return stored;
    }

    DocumentVersion insertVersion(UUID id, UUID documentId, int versionNo, String fileName, String mediaType,
            byte[] content, String sha256, String note, UUID actor) {
        DocumentVersion version = jdbc.queryForObject("""
                insert into document_version (id, document_id, version_no, file_name, media_type, size_bytes, sha256,
                                              change_note, uploaded_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id, document_id, version_no, file_name, media_type,
                       size_bytes, sha256, change_note, uploaded_by, uploaded_at""", (rs, n) -> versionRow(rs), id,
                documentId, versionNo, fileName, mediaType, content.length, sha256, note, actor);
        jdbc.update("insert into document_version_content (version_id, content) values (?, ?)", id, content);
        return version;
    }

    /** Makes the version current: the head takes its file metadata. */
    void makeCurrent(UUID documentId, DocumentVersion version, UUID actor) {
        jdbc.update("""
                update case_document set file_name = ?, media_type = ?, size_bytes = ?, sha256 = ?, current_version_no = ?,
                       version = version + 1, updated_at = now(), updated_by = ? where id = ?""", version.fileName(),
                version.mediaType(), version.sizeBytes(), version.sha256(), version.versionNo(), actor, documentId);
    }

    int nextVersionNo(UUID documentId) {
        Integer next = jdbc.queryForObject("select coalesce(max(version_no), 0) + 1 from document_version where document_id = ?",
                Integer.class, documentId);
        return next == null ? 1 : next;
    }

    Optional<CaseDocument> find(UUID id) {
        return jdbc.query(SELECT + " where id = ?", (rs, n) -> row(rs), id).stream().findFirst();
    }

    /** Locks the head row until the transaction ends (versions of one document are numbered one at a time). */
    Optional<CaseDocument> findForUpdate(UUID id) {
        return jdbc.query(SELECT + " where id = ? for update", (rs, n) -> row(rs), id).stream().findFirst();
    }

    List<CaseDocument> forCase(UUID caseId) {
        return jdbc.query(SELECT + " where case_id = ? and withdrawn_at is null order by created_at desc, id",
                (rs, n) -> row(rs), caseId);
    }

    /** Withdrawn documents of a case, for the people who may restore or delete them. */
    List<CaseDocument> withdrawnForCase(UUID caseId) {
        return jdbc.query(SELECT + " where case_id = ? and withdrawn_at is not null order by withdrawn_at desc, id",
                (rs, n) -> row(rs), caseId);
    }

    /** The latest documents of every visible case, newest first. */
    List<CaseDocument> latest(CaseAccessPolicy.Predicate visible, long offset, int limit) {
        Map<String, Object> params = new HashMap<>(visible.params());
        params.put("offset", offset);
        params.put("limit", limit);
        return named.query(SELECT + " where withdrawn_at is null and " + visible.sql()
                + " order by created_at desc, id offset :offset limit :limit", params, (rs, n) -> row(rs));
    }

    long count(CaseAccessPolicy.Predicate visible) {
        Long count = named.queryForObject("select count(*) from case_document where withdrawn_at is null and "
                + visible.sql(), visible.params(), Long.class);
        return count == null ? 0 : count;
    }

    Optional<byte[]> content(UUID versionId) {
        return jdbc.query("select content from document_version_content where version_id = ?",
                (rs, n) -> rs.getBytes("content"), versionId).stream().findFirst();
    }

    List<DocumentVersion> versions(UUID documentId) {
        return jdbc.query(VERSION_SELECT + " where document_id = ? order by version_no desc", (rs, n) -> versionRow(rs),
                documentId);
    }

    Optional<DocumentVersion> version(UUID documentId, int versionNo) {
        return jdbc.query(VERSION_SELECT + " where document_id = ? and version_no = ?", (rs, n) -> versionRow(rs),
                documentId, versionNo).stream().findFirst();
    }

    int versionCount(UUID documentId) {
        Integer count = jdbc.queryForObject("select count(*) from document_version where document_id = ?", Integer.class,
                documentId);
        return count == null ? 0 : count;
    }

    boolean classify(UUID id, long version, String title, UUID categoryId, LocalDate date, UUID proceedingId,
            String description, UUID actor) {
        return jdbc.update("""
                update case_document set title = ?, category_id = ?, document_date = ?, proceeding_id = ?, description = ?,
                       version = version + 1, updated_at = now(), updated_by = ? where id = ? and version = ?""", title,
                categoryId, date, proceedingId, description, actor, id, version) == 1;
    }

    boolean withdraw(UUID id, long version, String reason, UUID actor) {
        return jdbc.update("""
                update case_document set withdrawn_at = now(), withdrawn_by = ?, withdrawn_reason = ?, version = version + 1,
                       updated_at = now(), updated_by = ? where id = ? and version = ? and withdrawn_at is null""", actor,
                reason, actor, id, version) == 1;
    }

    boolean restore(UUID id, long version, UUID actor) {
        return jdbc.update("""
                update case_document set withdrawn_at = null, withdrawn_by = null, withdrawn_reason = null,
                       version = version + 1, updated_at = now(), updated_by = ? where id = ? and version = ?
                       and withdrawn_at is not null""", actor, id, version) == 1;
    }

    /** Who withdrew the document, for the deletion log. */
    Map<String, Object> withdrawal(UUID id) {
        return jdbc.queryForMap("select withdrawn_by, withdrawn_at, withdrawn_reason from case_document where id = ?", id);
    }

    private static CaseDocument row(ResultSet rs) throws SQLException {
        Timestamp withdrawn = rs.getTimestamp("withdrawn_at");
        return new CaseDocument(rs.getObject("id", UUID.class), rs.getObject("case_id", UUID.class),
                rs.getString("title"), rs.getString("file_name"), rs.getString("media_type"), rs.getInt("size_bytes"),
                rs.getString("sha256"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by", UUID.class), rs.getObject("category_id", UUID.class),
                rs.getObject("document_date", LocalDate.class), rs.getObject("proceeding_id", UUID.class),
                rs.getString("description"), rs.getInt("current_version_no"),
                withdrawn == null ? null : withdrawn.toInstant(), rs.getString("withdrawn_reason"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static DocumentVersion versionRow(ResultSet rs) throws SQLException {
        Instant uploaded = rs.getTimestamp("uploaded_at").toInstant();
        return new DocumentVersion(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getInt("version_no"), rs.getString("file_name"), rs.getString("media_type"), rs.getInt("size_bytes"),
                rs.getString("sha256"), rs.getString("change_note"), rs.getObject("uploaded_by", UUID.class), uploaded);
    }
}
