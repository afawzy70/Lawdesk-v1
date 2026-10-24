package com.lawdesk.cases;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/parties} and {@code PUT /api/v1/parties/{id}} (LD-016). Text is trimmed and blank optional
 * values count as absent; {@code version} is required only when editing ({@link Update}).
 */
record PartyRequest(
        @NotNull @Pattern(regexp = PartyKind.PATTERN) String kind,
        @NotBlank @Size(max = 200) String name,
        @Pattern(regexp = "[0-9]{14}") String nationalId,
        @Size(max = 50) String registrationNo,
        @Size(max = 30) @Pattern(regexp = "[0-9 +()-]+") String phone,
        @Size(max = 254) @Email String email,
        @Size(max = 500) String address,
        @NotNull(groups = Update.class) Long version) {

    /** Validation group of an edit. */
    interface Update {
    }

    PartyRequest {
        name = Text.trimToNull(name);
        nationalId = Text.trimToNull(nationalId);
        registrationNo = Text.trimToNull(registrationNo);
        phone = Text.trimToNull(phone);
        email = Text.trimToNull(email);
        address = Text.trimToNull(address);
    }

    PartyKind partyKind() {
        return PartyKind.valueOf(kind);
    }
}
