package com.lawdesk.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Counts the SQL statements a thread sends to the database (LD-058 round 2). Import it into a Spring test
 * ({@code @Import(SqlStatementCounter.Config.class)}), call {@link #reset()} before a request and {@link #count()} after it.
 * Regression guards assert on the number of statements a request needs, which does not depend on how loaded the host is.
 */
public final class SqlStatementCounter {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    private SqlStatementCounter() {
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int count() {
        return COUNT.get()[0];
    }

    @TestConfiguration
    public static class Config {
        @Bean
        static BeanPostProcessor sqlStatementCountingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource ds && !Proxy.isProxyClass(bean.getClass())
                            ? wrap(DataSource.class, ds, SqlStatementCounter::connection) : bean;
                }
            };
        }
    }

    private interface Wrapper {
        Object wrap(Object result);
    }

    private static Object connection(Object result) {
        return result instanceof Connection c ? wrap(Connection.class, c, SqlStatementCounter::statement) : result;
    }

    private static Object statement(Object result) {
        if (result instanceof PreparedStatement ps) {
            return wrap(PreparedStatement.class, ps, r -> r);
        }
        return result instanceof Statement s ? wrap(Statement.class, s, r -> r) : result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T wrap(Class<T> type, T target, Wrapper next) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            if (method.getName().startsWith("execute") && (target instanceof Statement)) {
                COUNT.get()[0]++;
            }
            return invoke(method, target, args, next);
        });
    }

    private static Object invoke(Method method, Object target, Object[] args, Wrapper next) throws Throwable {
        try {
            Object result = method.invoke(target, args);
            String name = method.getName();
            if (name.equals("getConnection") || name.equals("createStatement") || name.startsWith("prepare")) {
                return next.wrap(result);
            }
            return result;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
