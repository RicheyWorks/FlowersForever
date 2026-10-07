package com.flowerfarm.model;

/** A beekeeper's recorded status at a hive check, without diagnosis or treatment advice. */
public enum HiveCheckStatus {
    NOT_ASSESSED,
    ACTIVE,
    NEEDS_FOLLOW_UP,
    OVERWINTERING,
    INACTIVE
}
