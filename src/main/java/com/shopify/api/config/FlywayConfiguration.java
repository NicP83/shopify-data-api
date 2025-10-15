package com.shopify.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway configuration to handle failed migrations
 * Automatically repairs failed migrations before running migrate
 */
@Configuration
public class FlywayConfiguration {

    @Bean
    public FlywayMigrationStrategy repairStrategy() {
        return flyway -> {
            // Repair any failed migrations first
            flyway.repair();

            // Then run migrations
            flyway.migrate();
        };
    }
}
