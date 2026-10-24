package com.lawdesk.users;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the role administration API (LD-009.1). Permission codes are those of {@code Permission}. */
final class RoleRequests {

    private RoleRequests() {
    }

    record Create(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull List<@NotNull String> permissions) {
    }

    record Update(
            @NotBlank @Size(max = 128) String name,
            @NotNull List<@NotNull String> permissions,
            @NotNull Long version) {
    }
}
