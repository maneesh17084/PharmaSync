package com.pharmasync.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Batch(int id, int medicineId, String medicineName, String batchNo, LocalDate mfgDate,
                    LocalDate expiryDate, int quantity, BigDecimal purchasePrice, BigDecimal mrp,
                    Integer supplierId, String supplierName, BatchStatus status) {

    public boolean isExpired(LocalDate today) {
        return expiryDate.isBefore(today);
    }

    /** True when the batch may be dispatched: active, in stock and not expired. */
    public boolean isSellable(LocalDate today) {
        return status == BatchStatus.ACTIVE && quantity > 0 && !isExpired(today);
    }

    public Batch withQuantity(int newQuantity) {
        return new Batch(id, medicineId, medicineName, batchNo, mfgDate, expiryDate, newQuantity,
                purchasePrice, mrp, supplierId, supplierName, status);
    }

    @Override
    public String toString() {
        return batchNo + " (exp " + expiryDate + ", qty " + quantity + ")";
    }
}
