package com.flowerfarm.backup;

import java.io.IOException;

/** The farm records are damaged and no verified backup could be put back; the message is for the grower. */
public final class DamagedRecordsException extends IOException {

    private static final long serialVersionUID = 1L;

    public DamagedRecordsException(String message) {
        super(message);
    }
}
