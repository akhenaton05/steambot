package ru.steam.entity;

public enum ItemStatus {
    HOLD("Hold"),
    ON_SALE("On sale"),
    SOLD("Sold");

    private final String displayName;

    ItemStatus(String displayName) { this.displayName = displayName; }
}
