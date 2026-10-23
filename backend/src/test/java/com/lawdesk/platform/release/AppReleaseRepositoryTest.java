package com.lawdesk.platform.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import com.lawdesk.platform.PostgresTestSupport;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LD-004 persistence through Spring Data JPA: QA-0025 (persist and read back), QA-0026 (required field), QA-0027
 * (length boundary), QA-0030 (rollback leaves no partial data), QA-0031 (concurrent inserts).
 */
@SpringBootTest
class AppReleaseRepositoryTest extends PostgresTestSupport {

    @Autowired
    AppReleaseRepository releases;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    BuildProperties build;

    private List<Long> existingIds;

    @BeforeEach
    void rememberExistingRows() {
        existingIds = releases.findAll().stream().map(AppRelease::getId).toList();
    }

    @AfterEach
    void removeRowsCreatedByTheTest() {
        jdbc.update("delete from app_release where not (id = any (?))", (Object) existingIds.toArray(Long[]::new));
    }

    @Test
    void recordsTheRunningReleaseAtStartup() {
        assertThat(releases.findAll()).extracting(AppRelease::getVersion).contains(build.getVersion());
    }

    @Test
    void persistsAndReadsBackARelease() {
        AppRelease saved = releases.save(new AppRelease("1.2.3"));

        AppRelease loaded = releases.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getVersion()).isEqualTo("1.2.3");
        assertThat(loaded.getStartedAt()).isNotNull();
    }

    @Test
    void rejectsMissingVersionWithoutPersisting() {
        long before = releases.count();

        assertThatThrownBy(() -> releases.save(new AppRelease(" "))).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> releases.save(new AppRelease(null))).isInstanceOf(ConstraintViolationException.class);
        assertThat(releases.count()).isEqualTo(before);
    }

    @Test
    void databaseEnforcesTheSameRulesWhenTheApplicationIsBypassed() {
        assertThatThrownBy(() -> jdbc.update("insert into app_release (version) values ('')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into app_release (version) values (?)", "v".repeat(65)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsVersionAtTheLengthLimitAndRejectsOneCharacterMore() {
        assertThat(releases.save(new AppRelease("v".repeat(64))).getId()).isNotNull();
        assertThatThrownBy(() -> releases.save(new AppRelease("v".repeat(65))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void failedTransactionLeavesNoPartialData() {
        long before = releases.count();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            releases.saveAndFlush(new AppRelease("2.0.0"));
            jdbc.update("insert into app_release (version) values ('')");
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(releases.count()).isEqualTo(before);
    }

    @Test
    void concurrentInsertsGetDistinctIdentifiers() {
        List<CompletableFuture<Long>> inserts = IntStream.range(0, 10)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> releases.save(new AppRelease("c" + i)).getId()))
                .toList();

        List<Long> ids = inserts.stream().map(CompletableFuture::join).toList();

        assertThat(ids).doesNotHaveDuplicates().hasSize(10);
    }
}
