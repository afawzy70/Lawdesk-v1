package com.lawdesk.reports;

import java.util.List;

/**
 * An indicator as the dashboard shows it. {@code count} is absent (and {@code available} false) while the module that
 * owns the data is not on this system yet; such a figure is never replaced by a sample. {@code groups} is set for the
 * distributions only; every group's count equals the count of the list that group opens.
 */
public record IndicatorFigure(IndicatorCode code, int number, IndicatorCode.Entity entity, boolean available,
        String unavailableReason, Long count, List<Group> groups) {

    /** One group of a distribution; {@code key} {@code _none} and an absent label mean "not specified". */
    public record Group(String level, String key, String label, long count) {
    }
}
