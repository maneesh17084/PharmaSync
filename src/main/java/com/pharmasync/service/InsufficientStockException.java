package com.pharmasync.service;

/** Thrown when the valid (unexpired, active) stock cannot cover a requested quantity. */
public class InsufficientStockException extends BusinessException {

    private final String medicine;
    private final int requested;
    private final int available;

    public InsufficientStockException(String medicine, int requested, int available) {
        super("Insufficient valid stock for " + medicine + ": requested " + requested + ", available " + available);
        this.medicine = medicine;
        this.requested = requested;
        this.available = available;
    }

    public String getMedicine() { return medicine; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
