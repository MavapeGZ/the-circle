package com.thecircle.contracts.config;

import com.thecircle.contracts.dto.ContractStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Repairs the {@code contracts_status_check} constraint at boot.
 *
 * <p>Hibernate auto-generates a CHECK constraint listing the enum values for the
 * {@code status} column, but with {@code ddl-auto=update} it is created once and
 * never refreshed. When a new {@link ContractStatus} value is added (e.g.
 * {@code DELIVERED}), databases created before that change keep the stale
 * constraint and reject the new status with a {@code contracts_status_check}
 * violation. Fresh databases already get the full list, so this is a no-op there.
 *
 * <p>The constraint is dropped and recreated from the live enum values, so it
 * stays in sync automatically as the enum evolves. Enum names are compile-time
 * identifiers, so the inlined list is injection-safe.
 */
@Component
public class ContractStatusConstraintFix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ContractStatusConstraintFix.class);

    private final JdbcTemplate jdbc;

    public ContractStatusConstraintFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        String allowed = Arrays.stream(ContractStatus.values())
                .map(s -> "'" + s.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbc.execute("ALTER TABLE contracts DROP CONSTRAINT IF EXISTS contracts_status_check");
            jdbc.execute("ALTER TABLE contracts ADD CONSTRAINT contracts_status_check "
                    + "CHECK (status IN (" + allowed + "))");
            log.info("Ensured contracts_status_check allows: {}", allowed);
        } catch (Exception e) {
            // Non-fatal: a fresh DB may not have the table yet on first boot, and
            // the Hibernate-generated constraint already covers the current values.
            log.warn("Could not refresh contracts_status_check (continuing): {}", e.getMessage());
        }
    }
}
