package com.lawdesk.cases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The case record beyond its numbers, parties and members (requirements section 1, LD-050): the chamber, when the legal
 * department received the case, who referred it, the facts, the amount claimed, the importance and internal notes. Every
 * value is optional and null means "not specified"; the same record is accepted by the intake, the edit and the older
 * create endpoint, so the three apply one set of rules. On an edit that carries it, the record replaces the stored
 * values as a whole (a null clears one); an edit without it leaves them alone.
 */
public record CaseDetails(
        @Size(max = 100) String chamber,
        LocalDate receivedOn,
        UUID referringUnitId,
        @Size(max = 200) String referredBy,
        @Size(max = 8000) String facts,
        /** The amount claimed, KWD with three decimals; kept apart from the financial impact and every judged amount. */
        @JsonFormat(shape = JsonFormat.Shape.STRING) @PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal claimedAmount,
        @Pattern(regexp = "LOW|NORMAL|HIGH|CRITICAL") String importance,
        @Size(max = 4000) String internalNotes) {

    public static final CaseDetails EMPTY = new CaseDetails(null, null, null, null, null, null, null, null);

    public CaseDetails {
        chamber = Text.trimToNull(chamber);
        referredBy = Text.trimToNull(referredBy);
        facts = Text.trimToNull(facts);
        internalNotes = Text.trimToNull(internalNotes);
        importance = Text.trimToNull(importance);
    }

    /** True when no value is specified. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }
}
