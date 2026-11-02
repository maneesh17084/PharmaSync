package com.pharmasync.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Purchase(int id, String purchaseNo, int supplierId, String supplierName, String supplierInvoiceRef,
                       LocalDate orderDate, LocalDate receivedDate, PurchaseStatus status, BigDecimal subtotal,
                       BigDecimal taxAmount, BigDecimal totalAmount, PaymentStatus paymentStatus,
                       BigDecimal amountPaid, SupplyType supplyType, String notes, String createdBy) {
}
