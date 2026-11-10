package com.lawdesk.platform;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lawdesk.api.DatabaseUnavailable;

/**
 * Gives every request a correlation id (the caller's {@code X-Request-Id} when well formed, otherwise a new UUID),
 * exposes it in the MDC for structured logs and in the response, and logs one line per completed request. Only the
 * method, path, status and duration are logged: query strings and headers can carry confidential legal data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdFrom(request.getHeader(HEADER));
        long start = System.nanoTime();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            // Failures outside MVC (security filters, session and account loading) never reach ApiExceptionHandler.
            if (!DatabaseUnavailable.causes(e) || response.isCommitted()) {
                // Tomcat logs this after the MDC is cleared and then answers through /error (Spring Boot's default
                // JSON), so log the cause here, once, with the request id (QA-RT-011).
                log.error("request failed outside MVC; the container will answer 500 through /error", e);
                throw e;
            }
            log.error("database connection unavailable outside MVC; answering 503", e);
            response.reset();
            response.setHeader(HEADER, requestId);
            response.setHeader("Retry-After", DatabaseUnavailable.RETRY_AFTER_SECONDS);
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/problem+json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503}");
        } finally {
            log.atInfo()
                    .addKeyValue("http.request.method", request.getMethod())
                    .addKeyValue("url.path", request.getRequestURI())
                    .addKeyValue("http.response.status_code", response.getStatus())
                    .addKeyValue("event.duration_ms", (System.nanoTime() - start) / 1_000_000)
                    .log("request completed");
            MDC.remove(MDC_KEY);
        }
    }

    static String requestIdFrom(String candidate) {
        return candidate != null && SAFE_ID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }
}
