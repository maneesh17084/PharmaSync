package com.pharmasync.model;

import java.time.LocalDateTime;

public record StockTransaction(int id, LocalDateTime createdAt, String medicineName, String batchNo, TxnType type,
                               int quantity, int balanceAfter, String referenceType, String referenceNo,
                               String remarks, String username) {
}
