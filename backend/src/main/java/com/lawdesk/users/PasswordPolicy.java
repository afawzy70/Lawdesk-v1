package com.lawdesk.users;

/** LD-008.2: 12 to 128 characters, and not the username or email (ASSUMPTION, docs/delivery/PHASE-2-ACCEPTANCE.md). */
public final class PasswordPolicy {

    public static final String CODE = "PasswordPolicy";
    static final int MIN_LENGTH = 12;
    static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static boolean accepts(String password, String username, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        return !password.equalsIgnoreCase(username) && !password.equalsIgnoreCase(email);
    }
}
