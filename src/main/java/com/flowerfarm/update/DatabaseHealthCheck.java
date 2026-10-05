package com.flowerfarm.update;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

/**
 * Requires: Flyway reports no failed and no pending migrations and validates,
 * and the database answers a real query. Spring already refused to start if a
 * migration threw or the JPA schema check failed; this catches the rest.
 */
public final class DatabaseHealthCheck implements UpdateHealthCheck {

    private final DataSource dataSource;
    private final Optional<Flyway> flyway;

    public DatabaseHealthCheck(DataSource dataSource, Optional<Flyway> flyway) {
        this.dataSource = dataSource;
        this.flyway = flyway;
    }

    @Override
    public void verify() throws Exception {
        if (flyway.isPresent()) {
            var info = flyway.get().info();
            for (MigrationInfo migration : info.all()) {
                if (migration.getState().isFailed()) {
                    throw new IllegalStateException("database upgrade step " + migration.getVersion() + " failed");
                }
            }
            if (info.pending().length > 0) {
                throw new IllegalStateException(info.pending().length + " database upgrade steps did not run");
            }
            var validation = flyway.get().validateWithResult();
            if (!validation.validationSuccessful) {
                throw new IllegalStateException("database upgrade history does not validate");
            }
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES")) {
            if (!connection.isValid(5) || !tables.next() || tables.getLong(1) <= 0) {
                throw new IllegalStateException("the farm records database did not answer");
            }
        }
    }
}
