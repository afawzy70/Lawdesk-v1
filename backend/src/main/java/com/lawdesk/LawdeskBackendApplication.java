package com.lawdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// No fallback in-memory user: its generated password ends up in the logs. Users come from the database (Phase 2).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LawdeskBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawdeskBackendApplication.class, args);
    }

}
