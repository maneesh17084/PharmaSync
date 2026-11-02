package com.pharmasync.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleItem(int id, int saleId, int medicineId, String medicineName, int batchId, String batchNo,
                       LocalDate expiryDate, int quantity, BigDecimal unitPrice, BigDecimal discountPct,
                       BigDecimal gstRate, BigDecimal taxAmount, BigDecimal lineTotal) {
}
