package com.lawdesk.platform;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * The deployment's migration job ({@code java -jar app.jar migrate}): applies the Flyway migrations with the settings of
 * application.yaml and exits. It is the only part of the product that connects as the schema owner; the backend runs with Flyway
 * switched off and a credential that can only read and write rows (deploy/postgres/ownership.sql).
 *
 * <p>Deliberately not a configuration class or component, so the application's component scan never picks it up, and it starts
 * nothing but a data source and Flyway.
 */
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
public class MigrateOnly {

    /** Runs the migrations and closes; any failure is thrown, so the process exits with a non-zero status. */
    public static void run(String... args) {
        String[] arguments = new String[args.length + 1];
        System.arraycopy(args, 0, arguments, 0, args.length);
        arguments[args.length] = "--spring.flyway.enabled=true";
        new SpringApplicationBuilder(MigrateOnly.class).web(WebApplicationType.NONE).run(arguments).close();
    }
}
