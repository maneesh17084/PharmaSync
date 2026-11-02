package com.pharmasync.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Money arithmetic for invoices and purchase bills. All amounts are rounded to 2 decimals, half-up. */
public final class InvoiceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** gross = qty x price, taxable = gross - discount, total = taxable + tax. */
    public record LineAmounts(BigDecimal gross, BigDecimal discount, BigDecimal taxable, BigDecimal tax,
                              BigDecimal total) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }

    private InvoiceCalculator() {
    }

    public static LineAmounts line(int quantity, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal gstRate) {
        if (quantity <= 0) {
            throw new BusinessException("Quantity must be greater than zero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new BusinessException("Unit price must not be negative");
        }
        BigDecimal disc = discountPct == null ? BigDecimal.ZERO : discountPct;
        BigDecimal gst = gstRate == null ? BigDecimal.ZERO : gstRate;
        if (disc.signum() < 0 || disc.compareTo(HUNDRED) > 0) {
            throw new BusinessException("Discount must be between 0 and 100 percent");
        }
        if (gst.signum() < 0) {
            throw new BusinessException("GST rate must not be negative");
        }
        BigDecimal gross = round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal discount = round(gross.multiply(disc).divide(HUNDRED));
        BigDecimal taxable = gross.subtract(discount);
        BigDecimal tax = round(taxable.multiply(gst).divide(HUNDRED));
        return new LineAmounts(gross, discount, taxable, tax, taxable.add(tax));
    }

    public static Totals totals(List<LineAmounts> lines) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        BigDecimal tax = BigDecimal.ZERO.setScale(2);
        for (LineAmounts l : lines) {
            subtotal = subtotal.add(l.gross());
            discount = discount.add(l.discount());
            tax = tax.add(l.tax());
        }
        return new Totals(subtotal, discount, tax, subtotal.subtract(discount).add(tax));
    }

    public static BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
