package com.lawdesk.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.Map;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.lawdesk.audit.AuditLog;
import com.lawdesk.security.ActorPrincipal;
import com.lawdesk.security.PasswordChangeRequiredFilter;
import com.lawdesk.security.ReloadingSecurityContextRepository;
import com.lawdesk.users.UserAccounts;

/**
 * Deny-by-default security (LD-002, LD-007, LD-010). Signing in is a session for the SPA: the HttpOnly session cookie
 * carries it and the readable XSRF-TOKEN cookie, echoed in X-XSRF-TOKEN, protects every unsafe request
 * (architecture/CONTRACTS.md §3). Only health, info and the two endpoints needed to sign in are public; every other
 * request needs a session, and endpoints declare their permission with {@code @PreAuthorize}. Failures are RFC 9457
 * problems without internals.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    /** LD-008.1: delegating encoder, bcrypt with cost 12; hashes carry their algorithm id ("{bcrypt}..."). */
    private static final int BCRYPT_COST = 12;

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contexts, AuditLog audit)
            throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // Error pages are rendered on the container's error dispatch only; /error itself is not public.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(contexts))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getPrincipal() instanceof ActorPrincipal actor) {
                                audit.record("LOGOUT", AuditLog.Outcome.SUCCESS, actor.id(), actor.username(), "USER",
                                        actor.id().toString(), null);
                            }
                        })
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("JSESSIONID"))
                .addFilterAfter(new PasswordChangeRequiredFilter(), AuthorizationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED))
                        // The CSRF filter runs before authorization, so an anonymous write would otherwise get 403;
                        // anyone not signed in is told to authenticate instead (QAD-011).
                        .accessDeniedHandler((request, response, e) -> {
                            if (isAnonymous()) {
                                writeProblem(response, HttpStatus.UNAUTHORIZED);
                                return;
                            }
                            recordDenied(audit, request);
                            writeProblem(response, HttpStatus.FORBIDDEN);
                        }))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .cacheControl(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(BCRYPT_COST)));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository(UserAccounts accounts) {
        return new ReloadingSecurityContextRepository(accounts);
    }

    /** LD-011.2: a signed-in user refused by a permission check. */
    private static void recordDenied(AuditLog audit, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getPrincipal() instanceof ActorPrincipal actor) {
            audit.record("ACCESS_DENIED", AuditLog.Outcome.DENIED, actor.id(), actor.username(), "HTTP",
                    request.getMethod() + " " + request.getRequestURI(), null);
        }
    }

    /**
     * Requests the HTTP firewall refuses (double slashes, path parameters, encoded dots) never reach the filter chain
     * above, so its header writers do not run; this writes the same baseline headers itself (QAD-009).
     */
    @Bean
    RequestRejectedHandler requestRejectedHandler() {
        return (request, response, e) -> {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", ReferrerPolicy.NO_REFERRER.getPolicy());
            response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            writeProblem(response, HttpStatus.BAD_REQUEST);
        };
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || TRUST.isAnonymous(authentication);
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d}"
                .formatted(status.getReasonPhrase(), status.value()));
    }
}
