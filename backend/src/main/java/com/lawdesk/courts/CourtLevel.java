package com.lawdesk.courts;

/** The level of an Egyptian court; a proceeding's stage must match its court's level (LD-019…LD-021). */
public enum CourtLevel {
    /** Partial and primary courts (المحاكم الجزئية والابتدائية). */
    FIRST_INSTANCE,
    /** Courts of appeal (محاكم الاستئناف). */
    APPEAL,
    /** The Court of Cassation (محكمة النقض). */
    CASSATION
}
