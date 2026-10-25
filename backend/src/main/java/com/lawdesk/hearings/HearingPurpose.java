package com.lawdesk.hearings;

/** What a hearing is for (LD-024, ASSUMPTION in docs/delivery/PHASE-5-ACCEPTANCE.md). */
public enum HearingPurpose {
    /** أول جلسة. */
    FIRST_SESSION,
    /** مرافعة. */
    PLEADING,
    /** إثبات / تحقيق / سماع شهود. */
    EVIDENCE,
    /** مناقشة تقرير الخبير. */
    EXPERT_REPORT,
    /** للنطق بالحكم. */
    JUDGMENT,
    OTHER
}
