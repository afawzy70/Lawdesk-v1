package com.lawdesk.users;

import java.nio.charset.StandardCharsets;

/**
 * LD-008.2: at least 12 characters, at most 72 bytes of UTF-8, and not the username or email (ASSUMPTION,
 * docs/delivery/PHASE-2-ACCEPTANCE.md). bcrypt reads only the first 72 bytes, so a longer password is refused rather
 * than truncated (QAD-022); 72 bytes are 72 Latin or 36 Arabic letters.
 */
public final class PasswordPolicy {

    public static final String CODE = "PasswordPolicy";
    static final int MIN_LENGTH = 12;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean accepts(String password, String username, String email) {
        if (password == null || password.length() < MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        return !password.equalsIgnoreCase(username) && !password.equalsIgnoreCase(email);
    }
}
