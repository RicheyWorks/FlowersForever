package com.flowerfarm.safety;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.TreeSet;

/** Shared helpers: the app's own JDBC URL settings, pointed at a test folder. */
final class DataSafetyTestSupport {

    private DataSafetyTestSupport() {
    }

    /** {@code spring.datasource.url} exactly as shipped, with the records folder replaced. */
    static String shippedUrl(Path dataDirectory) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = DataSafetyTestSupport.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        String url = properties.getProperty("spring.datasource.url");
        return url.replace("${flowerfarm.data-dir:./data}", dataDirectory.toAbsolutePath().toString()
                .replace('\\', '/'));
    }

    static String shippedOptions(Path dataDirectory) throws IOException {
        String url = shippedUrl(dataDirectory);
        return url.substring(url.indexOf(';'));
    }

    static TreeSet<Integer> savedIds(String url) throws SQLException {
        TreeSet<Integer> ids = new TreeSet<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS SAVED_RECORD (ID INT PRIMARY KEY, NOTE VARCHAR(200))");
            try (ResultSet rows = statement.executeQuery("SELECT ID FROM SAVED_RECORD")) {
                while (rows.next()) {
                    ids.add(rows.getInt(1));
                }
            }
            statement.execute("SHUTDOWN");
        }
        return ids;
    }

    static void saveRecords(String url, int from, int to) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS SAVED_RECORD (ID INT PRIMARY KEY, NOTE VARCHAR(200))");
            for (int id = from; id <= to; id++) {
                statement.execute("INSERT INTO SAVED_RECORD VALUES (" + id + ", 'Bed " + id + " cut')");
            }
            statement.execute("SHUTDOWN"); // like the app's own clean exit
        }
    }

    static int parse(String progressLine) {
        return progressLine.isEmpty() ? 0 : Integer.parseInt(progressLine.substring(progressLine.indexOf(' ') + 1));
    }
}
