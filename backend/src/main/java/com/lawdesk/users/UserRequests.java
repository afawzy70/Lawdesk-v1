package com.lawdesk.users;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the user administration API (LD-009.2 field rules). Usernames are stored in lower case. */
final class UserRequests {

    private UserRequests() {
    }

    record Create(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,64}") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String displayName,
            @NotNull @Pattern(regexp = "ar|en") String locale,
            @NotNull List<String> roles,
            @NotNull @Size(min = 12, max = 128) String temporaryPassword) {
    }

    record Update(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String displayName,
            @NotNull @Pattern(regexp = "ar|en") String locale,
            @NotNull Long version) {
    }

    record Enabled(@NotNull Boolean enabled) {
    }

    record Roles(@NotNull List<String> roles) {
    }

    record PasswordReset(String temporaryPassword) {
    }
}
