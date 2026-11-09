package com.lawdesk.documents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lawdesk.api.FieldErrorException;
import com.lawdesk.api.NotFoundException;
import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorRef;
import com.lawdesk.security.CurrentActor;
import com.lawdesk.security.Permission;

/**
 * The electronic copy of a library judgment, stored through the archive (V842; requirements section 16, decision D7). It
 * follows the archive's own rules: the format is recognised by its signature and must be enabled in the archive settings,
 * the size is limited by them, and a replacement keeps the previous copy as history. A judgment marked RESTRICTED hands its
 * copy only to those who manage the library, like the rest of its masked content. Every download is audited.
 */
@Service
class LibraryCopyService {

    /** What is known about a stored copy; never the bytes. */
    record Copy(UUID id, UUID judgmentId, String fileName, String mediaType, int sizeBytes, String sha256,
            UUID uploadedBy, Instant uploadedAt) {
    }

    record Download(Copy copy, byte[] content) {
    }

    private static final int MAX_FILE_NAME = 255;

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final AuditLog audit;
    private final ArchiveSettings settings;

    LibraryCopyService(JdbcClient jdbc, CurrentActor currentActor, AuditLog audit, ArchiveSettings settings) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.audit = audit;
        this.settings = settings;
    }

    private static final String COLUMNS = "id, judgment_id, file_name, media_type, size_bytes, sha256, uploaded_by, uploaded_at";

    private static Copy copy(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Copy(rs.getObject("id", UUID.class), rs.getObject("judgment_id", UUID.class), rs.getString("file_name"),
                rs.getString("media_type"), rs.getInt("size_bytes"), rs.getString("sha256"),
                rs.getObject("uploaded_by", UUID.class), rs.getTimestamp("uploaded_at").toInstant());
    }

    @Transactional(readOnly = true)
    Copy current(UUID judgmentId) {
        requireReadable(judgmentId);
        return jdbc.sql("select " + COLUMNS + " from library_judgment_file where judgment_id = :id and removed_at is null")
                .param("id", judgmentId).query(LibraryCopyService::copy).optional()
                .orElseThrow(() -> new NotFoundException("copy"));
    }

    @Transactional(readOnly = true)
    List<Copy> history(UUID judgmentId) {
        requireReadable(judgmentId);
        return jdbc.sql("select " + COLUMNS + " from library_judgment_file where judgment_id = :id order by uploaded_at desc, id")
                .param("id", judgmentId).query(LibraryCopyService::copy).list();
    }

    @Transactional
    Copy store(UUID judgmentId, MultipartFile file) {
        ActorRef actor = currentActor.require();
        requireReadable(judgmentId);
        if (file == null) {
            throw new FieldErrorException("file", "NotNull");
        }
        if (file.isEmpty()) {
            throw new FieldErrorException("file", "NotEmpty");
        }
        if (file.getSize() > settings.limits().maxFileBytes()) {
            throw new FieldErrorException("file", "MaxSize");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FileFormats.Detected detected = FileFormats.detect(content)
                .orElseThrow(() -> new FieldErrorException("file", "UnsupportedFormat"));
        ArchiveSettings.Format format = settings.formats().stream().filter(f -> f.code().equals(detected.code()))
                .findFirst().orElseThrow();
        if (!format.enabled() || !FileFormats.declared(file.getContentType()).equals(detected.mediaType())) {
            throw new FieldErrorException("file", "UnsupportedFormat");
        }
        // One current copy per judgment: lock the judgment so two uploads cannot both think they are the first.
        jdbc.sql("select id from library_judgment where id = :id for update").param("id", judgmentId).query(UUID.class).list();
        jdbc.sql("update library_judgment_file set removed_at = now(), removed_by = :actor where judgment_id = :id"
                + " and removed_at is null").param("actor", actor.id()).param("id", judgmentId).update();
        Copy stored = jdbc.sql("""
                insert into library_judgment_file (judgment_id, file_name, media_type, size_bytes, sha256, content, uploaded_by)
                values (:judgment, :name, :type, :size, :sha, :content, :actor) returning\s""" + COLUMNS)
                .param("judgment", judgmentId).param("name", safeName(file.getOriginalFilename(), format.extension()))
                .param("type", detected.mediaType()).param("size", content.length).param("sha", sha256(content))
                .param("content", content).param("actor", actor.id()).query(LibraryCopyService::copy).single();
        audit.recordWithChange("LIBRARY_JUDGMENT_COPY_STORE", actor.id(), actor.username(), "LIBRARY_JUDGMENT",
                judgmentId.toString(), stored.sha256());
        return stored;
    }

    @Transactional
    void remove(UUID judgmentId) {
        ActorRef actor = currentActor.require();
        requireReadable(judgmentId);
        if (jdbc.sql("update library_judgment_file set removed_at = now(), removed_by = :actor where judgment_id = :id"
                + " and removed_at is null").param("actor", actor.id()).param("id", judgmentId).update() == 0) {
            throw new NotFoundException("copy");
        }
        audit.recordWithChange("LIBRARY_JUDGMENT_COPY_REMOVE", actor.id(), actor.username(), "LIBRARY_JUDGMENT",
                judgmentId.toString(), "copy removed; kept in history");
    }

    @Transactional(readOnly = true)
    Download download(UUID judgmentId, UUID copyId) {
        ActorRef actor = currentActor.require();
        requireReadable(judgmentId);
        String where = copyId == null ? "judgment_id = :id and removed_at is null" : "judgment_id = :id and id = :copy";
        var query = jdbc.sql("select " + COLUMNS + ", content from library_judgment_file where " + where).param("id", judgmentId);
        if (copyId != null) {
            query = query.param("copy", copyId);
        }
        Download found = query.query((rs, n) -> new Download(copy(rs, n), rs.getBytes("content"))).optional()
                .orElseThrow(() -> new NotFoundException("copy"));
        audit.record("LIBRARY_JUDGMENT_COPY_DOWNLOAD", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(),
                "LIBRARY_JUDGMENT", judgmentId.toString(), found.copy().sha256());
        return found;
    }

    /** The judgment exists; a RESTRICTED one is handled only by the library's managers (like its masked content). */
    private void requireReadable(UUID judgmentId) {
        String confidentiality = jdbc.sql("select confidentiality from library_judgment where id = :id")
                .param("id", judgmentId).query(String.class).optional().orElseThrow(() -> new NotFoundException("judgment"));
        if ("RESTRICTED".equals(confidentiality) && !currentActor.has(Permission.LIBRARY_MANAGE)) {
            throw new NotFoundException("judgment");
        }
    }

    private static String safeName(String original, String extension) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "judgment." + extension;
        }
        return name.length() <= MAX_FILE_NAME ? name : name.substring(0, MAX_FILE_NAME);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
