package com.lawdesk.platform.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.lawdesk.platform.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Codex review on 020154c: every staging deployment runs the same Maven version, so the start record must carry the
 * deployed commit (LAWDESK_RELEASE, set by compose from LAWDESK_TAG) to tell deployments and rollbacks apart.
 */
@SpringBootTest(properties = "lawdesk.release=0123456789abcdef0123456789abcdef01234567")
@ExtendWith(OutputCaptureExtension.class)
class ReleaseIdentifierTest extends PostgresTestSupport {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @Autowired
    ReleaseRecorder recorder;

    @Autowired
    BuildProperties build;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void startupRecordsTheDeployedCommit() {
        assertThat(jdbc.queryForList("select version from app_release", String.class)).containsExactly(SHA);
    }

    @Test
    void logLineCarriesBothTheCommitAndTheBuildVersion(CapturedOutput output) {
        recorder.recordRelease();

        String line = output.getOut().lines().filter(l -> l.contains("release recorded")).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("no 'release recorded' log line in:\n" + output));
        assertThat(line).contains("\"id\":\"" + SHA + "\"").contains("\"version\":\"" + build.getVersion() + "\"");
        jdbc.update("delete from app_release where id = (select max(id) from app_release)");
    }
}
