package com.lawdesk.platform;

import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.lawdesk.api.FieldErrorException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Owner decisions 2026-10-25 (QAD-029, architecture/CONTRACTS.md §4): a missing {@code page} or {@code size} takes the
 * default (page 0, size 20); every other value that is not an integer in range is refused with 400 on every list,
 * instead of silently becoming the default or being clamped. {@code page} is at least 0, {@code size} between 1 and
 * {@value #MAX_SIZE}; an explicitly empty value is invalid, not "omitted".
 *
 * <p>It applies to any handler with a {@link Pageable} parameter or a query record with {@code page} and {@code size}
 * components (the case list, the timeline, the parties and the reports), so a new list is covered without anyone
 * remembering to. It runs after {@link AuthorizeBeforeBindingInterceptor}: a caller without the permission is answered
 * 403 and learns nothing about the parameters (QAD-026). Exports are not lists and take no paging.
 */
final class PagingValidationInterceptor implements HandlerInterceptor {

    static final int MAX_SIZE = 100;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !paged(method)) {
            return true;
        }
        check(request, "page", 0, Integer.MAX_VALUE);
        check(request, "size", 1, MAX_SIZE);
        return true;
    }

    private static boolean paged(HandlerMethod method) {
        if (ResponseEntity.class.isAssignableFrom(method.getMethod().getReturnType())) {
            return false;
        }
        for (var parameter : method.getMethodParameters()) {
            Class<?> type = parameter.getParameterType();
            if (Pageable.class.isAssignableFrom(type) || type.isRecord() && hasPagingComponents(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPagingComponents(Class<?> record) {
        Set<String> names = new HashSet<>();
        for (RecordComponent component : record.getRecordComponents()) {
            names.add(component.getName());
        }
        return names.containsAll(Set.of("page", "size"));
    }

    private static void check(HttpServletRequest request, String name, int minimum, int maximum) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return;
        }
        for (String value : values) {
            int number;
            try {
                number = Integer.parseInt(value.strip());
            } catch (NumberFormatException e) {
                throw new FieldErrorException(name, "Digits");
            }
            if (number < minimum) {
                throw new FieldErrorException(name, "Min");
            }
            if (number > maximum) {
                throw new FieldErrorException(name, "Max");
            }
        }
    }
}
