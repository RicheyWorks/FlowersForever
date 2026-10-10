package com.flowerfarm.backup;

/** A backup or restore problem whose message is written for the grower. */
public class BackupException extends Exception {

    private static final long serialVersionUID = 1L;

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
