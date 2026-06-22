package org.youthintech.enums;

public enum AlertLevel {
    MONITOR,
    WATCH,
    CRITICAL;

    public String toDbValue() {
        return name().toLowerCase();
    }
}
