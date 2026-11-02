package com.pharmasync.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseItem(int id, int purchaseId, int medicineId, String medicineName, Integer batchId,
                           String batchNo, LocalDate mfgDate, LocalDate expiryDate, int quantity,
                           BigDecimal unitCost, BigDecimal mrp, BigDecimal gstRate, BigDecimal lineTotal) {

    public PurchaseItem withBatchInfo(String newBatchNo, LocalDate newMfg, LocalDate newExpiry) {
        return new PurchaseItem(id, purchaseId, medicineId, medicineName, batchId, newBatchNo, newMfg, newExpiry,
                quantity, unitCost, mrp, gstRate, lineTotal);
    }
}
