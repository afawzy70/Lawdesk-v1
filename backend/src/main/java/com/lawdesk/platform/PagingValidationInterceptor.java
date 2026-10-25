package com.lawdesk.platform;

import org.springframework.data.domain.Pageable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.lawdesk.api.FieldErrorException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Owner decision 2026-10-25 (QAD-029, architecture/CONTRACTS.md §4): an invalid {@code page} or {@code size} is
 * refused with 400 on every list built on Spring Data paging, instead of silently becoming the default. Valid means an
 * integer, {@code page} at least 0 and {@code size} at least 1; a missing or blank value takes the default (page 0,
 * size 20) and a {@code size} above 100 is capped by Spring Data, as on the case list and the reports.
 *
 * <p>It applies to any handler with a {@link Pageable} parameter, so a new list is covered without anyone remembering
 * to, and it runs after {@link AuthorizeBeforeBindingInterceptor}: a caller without the permission is answered 403
 * and learns nothing about the parameters (QAD-026).
 */
final class PagingValidationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !takesPageable(method)) {
            return true;
        }
        check(request, "page", 0);
        check(request, "size", 1);
        return true;
    }

    private static boolean takesPageable(HandlerMethod method) {
        for (var parameter : method.getMethodParameters()) {
            if (Pageable.class.isAssignableFrom(parameter.getParameterType())) {
                return true;
            }
        }
        return false;
    }

    private static void check(HttpServletRequest request, String name, int minimum) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value.isBlank()) {
                continue;
            }
            try {
                if (Integer.parseInt(value.strip()) < minimum) {
                    throw new FieldErrorException(name, "Min");
                }
            } catch (NumberFormatException e) {
                throw new FieldErrorException(name, "Digits");
            }
        }
    }
}
