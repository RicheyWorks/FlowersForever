package com.flowerfarm.update;

/** The first-launch check a new version must pass before the farm can work in it. */
@FunctionalInterface
public interface UpdateHealthCheck {

    /** Throws with a short reason when the records are not usable by this version. */
    void verify() throws Exception;
}
