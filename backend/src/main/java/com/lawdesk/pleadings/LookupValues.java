package com.lawdesk.pleadings;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.FieldErrorException;

/**
 * The port other modules use to check a code against an administrator-managed list in {@code lookup_value} (LD-DOC-r5).
 * A record keeps the code it was saved with even when the option is later deactivated, so only a CHANGED code must be an
 * active option.
 */
@Component
public class LookupValues {

    public static final String CASE_IMPORTANCE = "CASE_IMPORTANCE";
    public static final String CLAIM_KIND = "CLAIM_KIND";
    public static final String EVIDENCE_IMPORTANCE = "EVIDENCE_IMPORTANCE";

    private final JdbcClient jdbc;

    LookupValues(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Refuses (400, rule Exists on {@code field}) a code that is neither the {@code previous} one nor an active option. */
    @Transactional(readOnly = true)
    public void requireUsable(String list, String code, String previous, String field) {
        if (code == null || code.equals(previous)) {
            return;
        }
        boolean usable = jdbc.sql("select exists (select 1 from lookup_value where list_code = :list and code = :code and active)")
                .param("list", list).param("code", code).query(Boolean.class).single();
        if (!usable) {
            throw new FieldErrorException(field, "Exists");
        }
    }
}
