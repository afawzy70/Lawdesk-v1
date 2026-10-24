package com.lawdesk.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.lawdesk.users.UserAccounts;

/** Finds the user signing in by username or email, ignoring case and surrounding spaces (LD-007.1). */
@Service
class LawdeskUserDetailsService implements UserDetailsService {

    private final UserAccounts accounts;

    LawdeskUserDetailsService(UserAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {
        return accounts.findByLogin(login.strip()).map(LawdeskUser::of)
                .orElseThrow(() -> new UsernameNotFoundException("unknown user"));
    }
}
