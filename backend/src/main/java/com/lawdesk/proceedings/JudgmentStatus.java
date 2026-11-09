package com.lawdesk.proceedings;

/** The state of a judgment as the legal department tracks it (LD-051, requirements section 9: "حالة الحكم"). */
public enum JudgmentStatus {
    ISSUED,
    NOTIFIED,
    CHALLENGED,
    OTHER
}
