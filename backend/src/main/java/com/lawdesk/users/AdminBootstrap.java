package com.lawdesk.users;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.lawdesk.audit.AuditLog;

/**
 * LD-008.4: creates the first administrator, only while no user exists, from a username, an email and a password file
 * provisioned outside source control (on staging a secret that deploy.sh generates once). The administrator must
 * change the password at first sign-in. Only the username is logged.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAccounts accounts;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final TransactionTemplate transaction;
    private final String username;
    private final String email;
    private final String passwordFile;

    public AdminBootstrap(UserAccounts accounts, PasswordEncoder encoder, AuditLog audit, TransactionTemplate transaction,
            @Value("${lawdesk.bootstrap.admin.username:}") String username,
            @Value("${lawdesk.bootstrap.admin.email:}") String email,
            @Value("${lawdesk.bootstrap.admin.password-file:}") String passwordFile) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
        this.transaction = transaction;
        this.username = username.strip().toLowerCase(Locale.ROOT);
        this.email = email.strip();
        this.passwordFile = passwordFile.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accounts.count() > 0) {
            return;
        }
        if (username.isEmpty() || email.isEmpty() || passwordFile.isEmpty()) {
            log.warn("no users exist and no bootstrap administrator is configured (lawdesk.bootstrap.admin.*)");
            return;
        }
        String hash = encoder.encode(validatedPassword());
        // QAD-042: the administrator and the event that records it exist together or not at all.
        transaction.executeWithoutResult(status -> {
            var id = accounts.createAdministrator(username, email, hash);
            audit.recordWithChange("USER_CREATE", UserAccounts.SYSTEM, "system", "USER", id.toString(),
                    "BOOTSTRAP_ADMIN");
        });
        log.info("created the bootstrap administrator {}", username);
    }

    /** The password from the file; refuses one that breaks the policy without revealing it. */
    public String validatedPassword() {
        String password;
        try {
            password = Files.readString(Path.of(passwordFile)).strip();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the bootstrap administrator password file " + passwordFile);
        }
        if (!PasswordPolicy.accepts(password, username, email)) {
            throw new IllegalStateException("the bootstrap administrator password does not meet the password policy");
        }
        return password;
    }
}
