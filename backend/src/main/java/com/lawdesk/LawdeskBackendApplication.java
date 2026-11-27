package com.lawdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

import com.lawdesk.platform.MigrateOnly;

// No fallback in-memory user: its generated password ends up in the logs. Users come from the database (Phase 2).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LawdeskBackendApplication {

    public static void main(String[] args) {
        // "migrate": the deployment's one-shot migration job, run as the schema owner (deploy/compose.yaml).
        if (args.length > 0 && "migrate".equals(args[0])) {
            MigrateOnly.run(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        SpringApplication.run(LawdeskBackendApplication.class, args);
    }

}
