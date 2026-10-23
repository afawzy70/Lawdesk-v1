package com.lawdesk.platform.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.lawdesk.platform.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * LD-006 observability: the release record is also visible in the structured log. Found by the CI stack job, where
 * a custom {@code service.version} key collided with the ECS {@code service} object and the line was dropped.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class ReleaseRecorderTest extends PostgresTestSupport {

    @Autowired
    ReleaseRecorder recorder;

    @Autowired
    BuildProperties build;

    @Autowired
    JdbcTemplate jdbc;

    private long maxIdBefore;

    @AfterEach
    void removeRecordedRow() {
        jdbc.update("delete from app_release where id > ?", maxIdBefore);
    }

    @Test
    void logsTheRecordedReleaseAsStructuredJson(CapturedOutput output) {
        maxIdBefore = jdbc.queryForObject("select coalesce(max(id), 0) from app_release", Long.class);

        recorder.recordRelease();

        String line = output.getOut().lines().filter(l -> l.contains("release recorded")).findFirst()
                .orElseThrow(() -> new AssertionError("no 'release recorded' log line in:\n" + output));
        assertThat(line).startsWith("{").contains("\"version\":\"" + build.getVersion() + "\"");
        assertThat(output.getOut()).doesNotContain("has already been written");
    }
}
