package com.flowerfarm.backup;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Writes a consistent copy of the open database without closing it. */
@FunctionalInterface
public interface DatabaseSnapshot {

    /** Writes a ZIP that contains the database file to {@code zipFile}. */
    void writeTo(Path zipFile) throws SQLException;

    /** H2's online {@code BACKUP TO}: consistent even while the farm keeps working. */
    static DatabaseSnapshot h2(DataSource dataSource) {
        return zipFile -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement backup = connection.prepareStatement("BACKUP TO ?")) {
                backup.setString(1, zipFile.toAbsolutePath().toString());
                backup.execute();
            }
        };
    }
}
