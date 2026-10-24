package com.lawdesk.experts;

/** The state of an expert referral (LD-025): open until the report is submitted or the referral is cancelled. */
public enum ReferralStatus {
    OPEN,
    REPORT_SUBMITTED,
    CANCELLED
}
