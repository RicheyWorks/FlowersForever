package com.flowerfarm.backup;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Force all saved rows and columns to be read before a backup is accepted as healthy. */
final class RecordsReadProbe {
    private RecordsReadProbe() { }

    static void readAll(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                    + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            for (String table : tables) {
                // Metadata identifiers are quoted as one name, including embedded quotes.
                try (ResultSet rows = statement.executeQuery("SELECT * FROM \"" + table.replace("\"", "\"\"") + "\"")) {
                    int columns = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                        for (int column = 1; column <= columns; column++) rows.getObject(column);
                    }
                }
            }
        }
    }
}
