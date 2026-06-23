package com.thecircle.users.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Assigns a public_id to any user row that predates the column.
 *
 * <p>The opaque public_id is generated server-side for new users, but with
 * {@code ddl-auto=update} the column is simply added (nullable) to existing
 * databases. This boot-time backfill gives every pre-existing user a UUID so the
 * by-public-id profile endpoint can resolve them. Idempotent: once filled, the
 * WHERE clause matches nothing.
 */
@Component
public class PublicIdBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfill.class);

    private final JdbcTemplate jdbc;

    public PublicIdBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            // UUIDs are generated in Java rather than via a DB function so the
            // backfill does not depend on a Postgres version / extension being
            // present (e.g. gen_random_uuid). One UPDATE per missing row; the set
            // is empty after the first run, so steady-state cost is a single SELECT.
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM users WHERE public_id IS NULL", Long.class);
            for (Long id : ids) {
                jdbc.update("UPDATE users SET public_id = ? WHERE id = ?",
                        UUID.randomUUID().toString(), id);
            }
            if (!ids.isEmpty()) {
                log.info("Backfilled public_id for {} existing user(s)", ids.size());
            }
        } catch (Exception e) {
            // Non-fatal: a fresh DB has no rows to backfill, and new users get a
            // public_id from the entity lifecycle regardless.
            log.warn("Could not backfill public_id (continuing): {}", e.getMessage());
        }
    }
}
