package com.flowerfarm.safety;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

/**
 * Runs in a separate JVM so tests can kill it the hard way (like Task Manager's
 * "End task" or a crash) in the middle of saving.
 * <ul>
 *   <li>{@code records <jdbcUrl> <pauseMillis>}: saves numbered records forever and prints
 *       {@code SAVED n} only after each commit returned.</li>
 *   <li>{@code file <target> <megabytes>}: replaces a file over and over with SafeFiles,
 *       alternating between all-'A' and all-'B' content, printing {@code WROTE x}.</li>
 *   <li>{@code hold <jdbcUrl>}: opens the records, prints {@code OPEN}, and keeps them open.</li>
 *   <li>{@code scouting <dataDir>}: saves a growing scouting log forever, printing
 *       {@code SAVED n} after each save of n findings returned.</li>
 * </ul>
 */
public final class CrashChild {

    private CrashChild() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "records" -> records(args[1], Long.parseLong(args[2]));
            case "file" -> file(Path.of(args[1]), Integer.parseInt(args[2]));
            case "hold" -> hold(args[1]);
            case "scouting" -> scouting(Path.of(args[1]));
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static void records(String url, long pause) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS SAVED_RECORD (ID INT PRIMARY KEY, NOTE VARCHAR(200))");
            }
            connection.setAutoCommit(false);
            int next;
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(ID), 0) FROM SAVED_RECORD")) {
                rows.next();
                next = rows.getInt(1) + 1;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO SAVED_RECORD (ID, NOTE) VALUES (?, ?)")) {
                for (int id = next; ; id++) {
                    insert.setInt(1, id);
                    insert.setString(2, "Harvest note " + id + " - dahlias, bed 4, 30 stems");
                    insert.executeUpdate();
                    connection.commit();
                    System.out.println("SAVED " + id);
                    System.out.flush();
                    if (pause > 0) {
                        Thread.sleep(pause);
                    }
                }
            }
        }
    }

    private static void file(Path target, int megabytes) throws Exception {
        byte[] a = new byte[megabytes * 1024 * 1024];
        byte[] b = new byte[a.length];
        Arrays.fill(a, (byte) 'A');
        Arrays.fill(b, (byte) 'B');
        for (int round = 0; ; round++) {
            boolean useA = round % 2 == 0;
            SafeFiles.write(target, useA ? a : b);
            System.out.println("WROTE " + (useA ? 'A' : 'B'));
            System.out.flush();
        }
    }

    private static void scouting(Path data) throws Exception {
        com.flowerfarm.scouting.ScoutingStore store = new com.flowerfarm.scouting.ScoutingStore(data,
                java.time.Clock.systemUTC());
        for (int count = 1; ; count++) {
            store.save(scoutingLog(count));
            System.out.println("SAVED " + count);
            System.out.flush();
        }
    }

    /** A scouting log with {@code count} findings, each with plenty of text. */
    static com.flowerfarm.scouting.ScoutingStore.Log scoutingLog(int count) {
        java.util.List<com.flowerfarm.scouting.Scouting.Finding> findings = new java.util.ArrayList<>();
        java.time.LocalDate day = java.time.LocalDate.of(2026, 10, 7);
        for (int i = 1; i <= count; i++) {
            findings.add(new com.flowerfarm.scouting.Scouting.Finding("F-20261007-" + i, day, null, "Bed " + i,
                    "Dahlia", "Cafe au Lait", com.flowerfarm.scouting.Scouting.Category.INSECT_MITE, "Thrips",
                    "Silvery streaks on petals and black specks. ".repeat(20),
                    com.flowerfarm.scouting.Scouting.Severity.MODERATE, 10.0, "", "Removed blooms"));
        }
        return new com.flowerfarm.scouting.ScoutingStore.Log(1, 14, findings, java.util.List.of());
    }

    private static void hold(String url) throws Exception {
        try (Connection ignored = DriverManager.getConnection(url, "sa", "")) {
            System.out.println("OPEN");
            System.out.flush();
            Thread.sleep(120_000);
        }
    }
}
