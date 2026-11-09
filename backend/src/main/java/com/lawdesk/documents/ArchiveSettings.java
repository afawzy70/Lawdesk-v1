package com.lawdesk.documents;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;

/**
 * The archive's limits and accepted formats, read from {@code archive_setting} and {@code archive_format} (V700) on every
 * use so that a manager's change applies at once. The defaults are assumptions the owner can change in the archive settings.
 */
@Component
public class ArchiveSettings {

    /** The limits in force. */
    public record Limits(int maxFileBytes, int maxDocumentsPerCase, int maxVersionsPerDocument, int maxOcrPages,
            int maxTextChars) {
    }

    /** One format the archive can verify; {@code enabled} says whether this installation accepts it. */
    public record Format(String code, String family, String mediaType, String extension, boolean enabled) {
    }

    private final JdbcClient jdbc;

    ArchiveSettings(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Limits limits() {
        Map<String, Integer> values = new java.util.HashMap<>();
        jdbc.sql("select code, int_value from archive_setting").query((rs, n) -> {
            values.put(rs.getString("code"), rs.getInt("int_value"));
            return null;
        }).list();
        return new Limits(values.get("MAX_FILE_BYTES"), values.get("MAX_DOCUMENTS_PER_CASE"),
                values.get("MAX_VERSIONS_PER_DOCUMENT"), values.get("MAX_OCR_PAGES"), values.get("MAX_TEXT_CHARS"));
    }

    @Transactional(readOnly = true)
    public List<Format> formats() {
        return jdbc.sql("select code, family, media_type, extension, enabled from archive_format order by code")
                .query((rs, n) -> new Format(rs.getString("code"), rs.getString("family"), rs.getString("media_type"),
                        rs.getString("extension"), rs.getBoolean("enabled"))).list();
    }

    /** Replaces the limits and the set of enabled formats; every value is checked against the bounds stored with it. */
    @Transactional
    void update(Limits limits, List<String> enabledFormats, UUID actor) {
        set("MAX_FILE_BYTES", limits.maxFileBytes(), "maxFileBytes", actor);
        set("MAX_DOCUMENTS_PER_CASE", limits.maxDocumentsPerCase(), "maxDocumentsPerCase", actor);
        set("MAX_VERSIONS_PER_DOCUMENT", limits.maxVersionsPerDocument(), "maxVersionsPerDocument", actor);
        set("MAX_OCR_PAGES", limits.maxOcrPages(), "maxOcrPages", actor);
        set("MAX_TEXT_CHARS", limits.maxTextChars(), "maxTextChars", actor);
        List<String> known = formats().stream().map(Format::code).toList();
        for (String code : enabledFormats) {
            if (!known.contains(code)) {
                throw new FieldErrorException("enabledFormats", "Unknown");
            }
        }
        if (enabledFormats.isEmpty()) {
            throw new FieldErrorException("enabledFormats", "NotEmpty");
        }
        for (String code : known) {
            jdbc.sql("update archive_format set enabled = :on, updated_at = now(), updated_by = :actor where code = :code")
                    .param("on", enabledFormats.contains(code)).param("actor", actor).param("code", code).update();
        }
    }

    private void set(String code, int value, String field, UUID actor) {
        int updated = jdbc.sql("""
                update archive_setting set int_value = :value, updated_at = now(), updated_by = :actor
                where code = :code and :value between min_value and max_value""").param("value", value)
                .param("actor", actor).param("code", code).update();
        if (updated == 0) {
            throw new FieldErrorException(field, "Range");
        }
    }
}
