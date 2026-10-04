package com.flowerfarm.update;

/** Records update attempts in the farm's Activity History. */
@FunctionalInterface
public interface UpdateActivityLog {

    String SOURCE = "App updates";

    void record(String operation, boolean success, String message);

    static UpdateActivityLog none() {
        return (operation, success, message) -> { };
    }
}
