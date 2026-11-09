package com.lawdesk.proceedings;

import com.lawdesk.courts.CourtLevel;

/** The stage of a proceeding, in the order of the judicial ladder; each stage is heard by a court of that level. */
public enum Stage {
    FIRST_INSTANCE(CourtLevel.FIRST_INSTANCE),
    APPEAL(CourtLevel.APPEAL),
    CASSATION(CourtLevel.CASSATION),
    /** Tracks beside the ladder (owner decision D2): they attack or follow a proceeding of the same case. */
    CONSTITUTIONAL(CourtLevel.CONSTITUTIONAL),
    ENFORCEMENT(CourtLevel.ENFORCEMENT),
    GRIEVANCE(CourtLevel.GRIEVANCE);

    private final CourtLevel courtLevel;

    Stage(CourtLevel courtLevel) {
        this.courtLevel = courtLevel;
    }

    CourtLevel courtLevel() {
        return courtLevel;
    }
}
