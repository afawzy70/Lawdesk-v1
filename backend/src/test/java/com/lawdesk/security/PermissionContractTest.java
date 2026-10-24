package com.lawdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** architecture/CONTRACTS.md §3: one permission list; every code has a matching @PreAuthorize expression. */
class PermissionContractTest {

    @Test
    void containsThePermissionsTheWorkstreamsBuildOn() {
        Set<String> names = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(names).contains("USER_VIEW", "USER_MANAGE", "ROLE_VIEW", "ROLE_MANAGE", "AUDIT_VIEW",
                "CASE_VIEW", "CASE_CREATE", "CASE_EDIT", "CASE_DELETE", "CASE_STATUS_CHANGE", "PARTY_MANAGE",
                "COURT_VIEW", "COURT_MANAGE", "PROCEEDING_VIEW", "PROCEEDING_MANAGE", "JUDGMENT_VIEW",
                "JUDGMENT_MANAGE");
    }

    // Phases 5 and 6 (architecture/CONTRACTS.md §8), reserved before the two workstreams start.
    @Test
    void containsThePermissionsOfLegalOperationsAndReporting() {
        Set<String> names = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(names).contains("HEARING_VIEW", "HEARING_MANAGE", "EXPERT_VIEW", "EXPERT_MANAGE", "DOCUMENT_VIEW",
                "DOCUMENT_MANAGE", "TASK_VIEW", "TASK_MANAGE", "NOTE_MANAGE", "REPORT_VIEW", "REPORT_EXPORT");
    }

    @Test
    void codesAreUpperSnakeCase() {
        for (Permission permission : Permission.values()) {
            assertThat(permission.name()).matches("[A-Z]+(_[A-Z]+)*");
        }
    }

    @Test
    void everyPermissionHasExactlyOneAuthorizationExpression() throws IllegalAccessException {
        Field[] fields = Permission.Codes.class.getDeclaredFields();
        Set<String> constants = Arrays.stream(fields).filter(f -> Modifier.isStatic(f.getModifiers()))
                .map(Field::getName).collect(Collectors.toSet());

        assertThat(constants).isEqualTo(Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet()));
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                assertThat(field.get(null)).isEqualTo("hasAuthority('" + field.getName() + "')");
            }
        }
    }
}
