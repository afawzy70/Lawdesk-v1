package com.lawdesk.cases;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** The validation constants must name exactly the enum values, in order. */
class CaseTypeTest {

    @Test
    void patternListsEveryCaseType() {
        assertThat(CaseType.PATTERN.split("\\|"))
                .containsExactly(Arrays.stream(CaseType.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void patternListsEveryCaseStatus() {
        assertThat(CaseStatus.PATTERN.split("\\|"))
                .containsExactly(Arrays.stream(CaseStatus.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    void patternListsEveryPartyKindAndRole() {
        assertThat(PartyKind.PATTERN.split("\\|"))
                .containsExactly(Arrays.stream(PartyKind.values()).map(Enum::name).toArray(String[]::new));
        assertThat(PartyRole.PATTERN.split("\\|"))
                .containsExactly(Arrays.stream(PartyRole.values()).map(Enum::name).toArray(String[]::new));
    }
}
