package com.lawdesk.pleadings;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Views of the claim-result and defence-position statistics. */
final class StatisticsRequests {

    private StatisticsRequests() {
    }

    /** {@code awardedTotal} adds only the awarded amounts of the results counted, never another kind of amount. */
    record ByResult(ClaimResult result, long count, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal awardedTotal) {
    }

    record ByStage(String stage, long count) {
    }

    record ByStageAndResult(String stage, ClaimResult result, long count) {
    }

    record ClaimResults(long total, List<ByResult> byResult, List<ByStage> byStage, List<ByStageAndResult> byStageAndResult) {
    }

    record ByPosition(DefensePosition position, long count) {
    }

    record ByKindAndPosition(DefenseKind kind, DefensePosition position, long count) {
    }

    record MostUsed(String title, long count) {
    }

    record DefensePositions(long total, List<ByPosition> byPosition, List<ByStage> byStage,
            List<ByKindAndPosition> byKindAndPosition, List<MostUsed> mostUsed) {
    }
}
