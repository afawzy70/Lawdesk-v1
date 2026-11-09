package com.lawdesk.lists;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lawdesk.api.BusinessRuleException;
import com.lawdesk.security.Permission;

/**
 * The port other modules use to check a code against a managed list (LD-057, owner decision D10). A record keeps the code
 * it was recorded with even when the value is later deactivated, so only a NEW record is refused a deactivated value.
 */
@Component
public class ManagedLists {

    public static final String SETTLEMENT_KIND = "SETTLEMENT_KIND";
    public static final String DIRECTIVE_KIND = "DIRECTIVE_KIND";
    public static final String TEMPLATE_KIND = "TEMPLATE_KIND";

    private final JdbcClient jdbc;

    ManagedLists(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The permission that manages the list, or empty for a list that does not exist. */
    static Optional<Permission> managedBy(String list) {
        return switch (list) {
            case SETTLEMENT_KIND -> Optional.of(Permission.SETTLEMENT_MANAGE);
            case DIRECTIVE_KIND -> Optional.of(Permission.DIRECTIVE_MANAGE);
            case TEMPLATE_KIND -> Optional.of(Permission.TEMPLATE_MANAGE);
            default -> Optional.empty();
        };
    }

    /** True when the list has an active value with this code. */
    @Transactional(readOnly = true)
    public boolean isActive(String list, String code) {
        return jdbc.sql("select exists (select 1 from managed_list_value where list_code = :list and code = :code and active)")
                .param("list", list).param("code", code).query(Boolean.class).single();
    }

    /** @throws BusinessRuleException with {@code rule} when the code is not an active value of the list (422) */
    @Transactional(readOnly = true)
    public void requireActive(String list, String code, String rule) {
        if (!isActive(list, code)) {
            throw new BusinessRuleException(rule, code + " is not an active value of " + list);
        }
    }
}
