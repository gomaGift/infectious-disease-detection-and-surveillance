package org.youthintech.health.alert;

public enum AlertLevel {
    MONITOR,
    WATCH,
    CRITICAL;

    public String toDbValue() {
        return name().toLowerCase();
    }
}
