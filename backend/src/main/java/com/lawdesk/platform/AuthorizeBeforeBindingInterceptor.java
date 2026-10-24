package com.lawdesk.platform;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * QAD-026 / LD-010.2: checks a handler's {@code @PreAuthorize} before Spring MVC reads the body, validates it or
 * converts path values. Method security alone runs after argument resolution, so a caller without the permission would
 * get 400 with field names for an invalid request instead of 403. Expressions that refer to arguments ({@code #name})
 * cannot be decided this early and are left to method security, which still checks every expression at invocation.
 */
final class AuthorizeBeforeBindingInterceptor implements HandlerInterceptor {

    private final PreAuthorizeAuthorizationManager authorization = new PreAuthorizeAuthorizationManager();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), PreAuthorize.class);
        if (rule == null) {
            rule = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), PreAuthorize.class);
        }
        if (rule == null || rule.value().contains("#")) {
            return true;
        }
        AuthorizationResult result = authorization.authorize(
                () -> SecurityContextHolder.getContext().getAuthentication(), new WithoutArguments(method));
        if (result != null && !result.isGranted()) {
            throw new AccessDeniedException("Access Denied");
        }
        return true;
    }

    /** The handler as a method invocation whose arguments are not resolved yet; it is never proceeded. */
    private record WithoutArguments(HandlerMethod handler) implements MethodInvocation {

        @Override
        public Method getMethod() {
            return handler.getMethod();
        }

        @Override
        public Object[] getArguments() {
            return new Object[handler.getMethodParameters().length];
        }

        @Override
        public Object proceed() {
            throw new UnsupportedOperationException("authorization check only");
        }

        @Override
        public Object getThis() {
            return handler.getBean();
        }

        @Override
        public AccessibleObject getStaticPart() {
            return handler.getMethod();
        }
    }
}
