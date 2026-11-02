package com.pharmasync.service;

/** Applies stock movements to a balance, refusing to go below zero. */
public final class StockCalculator {

    private StockCalculator() {
    }

    public static int apply(int currentBalance, int delta) {
        long result = (long) currentBalance + delta;
        if (result < 0) {
            throw new BusinessException("Stock cannot go negative (balance " + currentBalance + ", change " + delta + ")");
        }
        if (result > Integer.MAX_VALUE) {
            throw new BusinessException("Stock quantity too large");
        }
        return (int) result;
    }

    public static int applyAll(int openingBalance, int... deltas) {
        int balance = openingBalance;
        for (int d : deltas) {
            balance = apply(balance, d);
        }
        return balance;
    }
}
