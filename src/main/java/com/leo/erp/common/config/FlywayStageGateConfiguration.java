package com.leo.erp.common.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.regex.Pattern;

/**
 * Prevents a production process from silently migrating beyond its approved stage.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class FlywayStageGateConfiguration {

    private static final Pattern POSITIVE_VERSION = Pattern.compile("^[1-9][0-9]*$");
    private final String target;

    public FlywayStageGateConfiguration(@Value("${spring.flyway.target:}") String target) {
        validateTarget(target);
        this.target = target;
    }

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return guardedMigrationStrategy(target);
    }

    static FlywayMigrationStrategy guardedMigrationStrategy(String target) {
        return flyway -> {
            validateTarget(target);
            flyway.migrate();
        };
    }

    static void validateTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalStateException("Missing production Flyway target");
        }
        if (!POSITIVE_VERSION.matcher(target).matches()) {
            throw new IllegalStateException(
                    "Production Flyway target must be a positive integer version: " + target
            );
        }
    }
}
