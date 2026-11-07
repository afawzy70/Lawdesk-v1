package com.lawdesk.cases;

import java.util.UUID;

/** A member of the legal department assigned to a case (العضو المختص / العضو البديل). */
public record MemberRef(UUID id, String displayName) {
}
