package com.pharmasync.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Sale(int id, String invoiceNo, int customerId, String customerName, LocalDateTime saleDate,
                   BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                   String paymentMode, PaymentStatus paymentStatus, BigDecimal amountPaid,
                   IndianState placeOfSupply, SupplyType supplyType, String createdBy) {

    public BigDecimal balanceDue() {
        return totalAmount.subtract(amountPaid);
    }
}
