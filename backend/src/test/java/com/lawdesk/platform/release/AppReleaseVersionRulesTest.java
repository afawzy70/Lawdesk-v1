package com.lawdesk.platform.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.lawdesk.platform.PostgresTestSupport;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * QAD-008 (QA-0026): a version made only of whitespace, ASCII or Unicode, is blank. The entity and the database must
 * agree, so a write that bypasses the application is rejected for exactly the same values.
 */
@SpringBootTest
class AppReleaseVersionRulesTest extends PostgresTestSupport {

    @Autowired
    AppReleaseRepository releases;

    @Autowired
    JdbcTemplate jdbc;

    private List<Long> existingIds;

    static List<String> blankVersions() {
        return List.of("", " ", "\t", "\n", "\r\n", "\u000B", "\f", "\u00A0", "\u1680", "\u2003", "\u2028", "\u2029",
                "\u202F", "\u205F", "\u3000", "\u0085", "\u200B", "\uFEFF", " \t\u2003\n ");
    }

    @BeforeEach
    void rememberExistingRows() {
        existingIds = releases.findAll().stream().map(AppRelease::getId).toList();
    }

    @AfterEach
    void removeRowsCreatedByTheTest() {
        jdbc.update("delete from app_release where not (id = any (?))", (Object) existingIds.toArray(Long[]::new));
    }

    @ParameterizedTest
    @MethodSource("blankVersions")
    void applicationRejectsWhitespaceOnlyVersions(String version) {
        assertThatThrownBy(() -> releases.save(new AppRelease(version)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @MethodSource("blankVersions")
    void databaseRejectsWhitespaceOnlyVersionsWhenTheApplicationIsBypassed(String version) {
        assertThatThrownBy(() -> jdbc.update("insert into app_release (version) values (?)", version))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0.0", "0.0.1-SNAPSHOT", " 1.0 ", "\u0661.\u0660", "a\u2003b"})
    void bothLayersAcceptVersionsWithVisibleCharacters(String version) {
        assertThat(releases.save(new AppRelease(version)).getId()).isNotNull();
        assertThat(jdbc.update("insert into app_release (version) values (?)", version)).isOne();
    }
}
