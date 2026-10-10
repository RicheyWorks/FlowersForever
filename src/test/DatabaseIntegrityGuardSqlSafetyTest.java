package com.flowerfarm.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseIntegrityGuardSqlSafetyTest {

    @TempDir Path data;

    @Test
    void metadataIdentifiersCannotExecuteSqlAndReadOnlyUsersCanCheckRecords() throws Exception {
        String url = "jdbc:h2:file:" + data.toAbsolutePath().resolve("flowerfarm");
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE saved (id INT PRIMARY KEY, note VARCHAR)");
            statement.execute("INSERT INTO saved VALUES (1, 'keep this record')");
            statement.execute("CREATE TABLE \"harvest\"\"; DROP TABLE saved;--\" (id INT, note CLOB)");
            statement.execute("INSERT INTO \"harvest\"\"; DROP TABLE saved;--\" VALUES (2, 'saved harvest')");
            statement.execute("CREATE USER reader PASSWORD ''");
            statement.execute("GRANT SELECT ON saved TO reader");
            statement.execute("GRANT SELECT ON \"harvest\"\"; DROP TABLE saved;--\" TO reader");
        }

        String previousUser = System.getProperty("spring.datasource.username");
        try {
            System.setProperty("spring.datasource.username", "reader");
            assertThat(DatabaseIntegrityGuard.check(data)).isEqualTo(DatabaseIntegrityGuard.Health.HEALTHY);
        } finally {
            if (previousUser == null) {
                System.clearProperty("spring.datasource.username");
            } else {
                System.setProperty("spring.datasource.username", previousUser);
            }
        }

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet saved = statement.executeQuery("SELECT note FROM saved WHERE id = 1")) {
                assertThat(saved.next()).isTrue();
                assertThat(saved.getString(1)).isEqualTo("keep this record");
            }
            try (ResultSet harvest = statement.executeQuery(
                    "SELECT note FROM \"harvest\"\"; DROP TABLE saved;--\" WHERE id = 2")) {
                assertThat(harvest.next()).isTrue();
                assertThat(harvest.getString(1)).isEqualTo("saved harvest");
            }
        }
    }
}
