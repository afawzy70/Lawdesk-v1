package com.lawdesk.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Deny-by-default baseline. Only health and info are public until Phase 2 adds database-backed login; every other
 * request is answered with an RFC 9457 problem document and no internals.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // Error pages are rendered on the container's error dispatch only; /error itself is not public.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED))
                        // The CSRF filter runs before authorization, so an anonymous write would otherwise get 403;
                        // anyone not signed in is told to authenticate instead (QAD-011).
                        .accessDeniedHandler((request, response, e) -> writeProblem(response,
                                isAnonymous() ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .cacheControl(Customizer.withDefaults()));
        return http.build();
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
