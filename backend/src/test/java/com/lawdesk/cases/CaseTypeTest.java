package com.lawdesk.cases;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** The validation constant must name exactly the enum values, in order. */
class CaseTypeTest {

    @Test
    void patternListsEveryCaseType() {
        assertThat(CaseType.PATTERN.split("\\|"))
                .containsExactly(Arrays.stream(CaseType.values()).map(Enum::name).toArray(String[]::new));
    }
}
