package com.carelink.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaCompatibilityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaCompatibilityRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureCompanionReminderImageUrlColumn();
    }

    private void ensureCompanionReminderImageUrlColumn() {
        try {
            if (!tableExists("companion_reminders")) {
                return;
            }

            boolean hasSnakeCase = columnExists("companion_reminders", "image_url");
            boolean hasCamelCase = columnExists("companion_reminders", "imageUrl");

            if (!hasSnakeCase) {
                jdbcTemplate.execute(
                        "ALTER TABLE companion_reminders ADD COLUMN image_url VARCHAR(1000) NULL AFTER message"
                );
                log.info("Schema patched: companion_reminders.image_url added.");
            }

            if (hasCamelCase) {
                jdbcTemplate.execute(
                        "UPDATE companion_reminders " +
                                "SET image_url = COALESCE(image_url, imageUrl) " +
                                "WHERE (image_url IS NULL OR image_url = '') " +
                                "AND imageUrl IS NOT NULL AND imageUrl <> ''"
                );
                log.info("Schema patched: companion_reminders.imageUrl data migrated to image_url.");
            }
        } catch (Exception ex) {
            log.error("Failed to patch companion_reminders.image_url schema", ex);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }
}
