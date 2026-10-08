package com.flowerfarm.scouting;

import java.io.IOException;

/**
 * Where the scouting log is kept. The app keeps it in the farm database ({@link DatabaseScoutingStore});
 * the JSON file ({@link ScoutingStore}) is the older format, read once for the import and kept as a backup.
 */
public interface ScoutingLogStore {

    /** The whole log. Fails with a plain message when it cannot be read; nothing is changed then. */
    ScoutingStore.Log load() throws IOException;

    /** Replaces the whole log. */
    void save(ScoutingStore.Log log) throws IOException;

    /** A plain message about the last load (a recovery or the one-time import), or empty. Cleared when read. */
    String takeNotice();
}
