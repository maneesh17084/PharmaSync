package com.pharmasync.model;

public enum ExpiryStatus {
    EXPIRED("Expired"), NEAR_EXPIRY("Near expiry"), VALID("Valid");

    private final String label;

    ExpiryStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
