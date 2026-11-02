package com.pharmasync.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One amount received from a customer against an invoice. */
public record SalePayment(int id, int saleId, LocalDate paymentDate, BigDecimal amount, String mode, String reference,
                          String recordedBy, LocalDateTime createdAt) {
}
