package com.lawdesk.cases;

/** Whether the case has a financial impact (الأثر المالي); "not specified" is the absence of a value. */
public enum FinancialImpact {
    YES,
    NO;

    static final String PATTERN = "YES|NO";
}
