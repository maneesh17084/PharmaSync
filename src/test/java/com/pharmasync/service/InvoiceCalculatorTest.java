package com.pharmasync.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceCalculatorTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void lineWithGstOnly() {
        // 100 x 15.50 = 1550.00, GST 12% = 186.00
        InvoiceCalculator.LineAmounts l = InvoiceCalculator.line(100, d("15.50"), BigDecimal.ZERO, d("12"));
        assertEquals(d("1550.00"), l.gross());
        assertEquals(d("0.00"), l.discount());
        assertEquals(d("186.00"), l.tax());
        assertEquals(d("1736.00"), l.total());
    }

    @Test
    void discountAppliesBeforeTax() {
        // 50 x 11.00 = 550.00, 5% off = 27.50, taxable 522.50, GST 12% = 62.70
        InvoiceCalculator.LineAmounts l = InvoiceCalculator.line(50, d("11.00"), d("5"), d("12"));
        assertEquals(d("27.50"), l.discount());
        assertEquals(d("522.50"), l.taxable());
        assertEquals(d("62.70"), l.tax());
        assertEquals(d("585.20"), l.total());
    }

    @Test
    void roundsHalfUp() {
        // 3 x 0.35 = 1.05, GST 5% = 0.0525 -> 0.05
        InvoiceCalculator.LineAmounts l = InvoiceCalculator.line(3, d("0.35"), null, d("5"));
        assertEquals(d("0.05"), l.tax());
        // 1 x 0.10 at 5% = 0.005 -> 0.01
        assertEquals(d("0.01"), InvoiceCalculator.line(1, d("0.10"), null, d("5")).tax());
    }

    @Test
    void totalsOfSpecOrder() {
        // Paracetamol 100, Cetirizine 50, Vitamin C 75
        InvoiceCalculator.Totals t = InvoiceCalculator.totals(List.of(
                InvoiceCalculator.line(100, d("15.50"), null, d("12")),
                InvoiceCalculator.line(50, d("11.00"), null, d("12")),
                InvoiceCalculator.line(75, d("24.00"), null, d("12"))));
        assertEquals(d("3900.00"), t.subtotal());
        assertEquals(d("468.00"), t.tax());
        assertEquals(d("4368.00"), t.total());
    }

    @Test
    void validatesInput() {
        assertThrows(BusinessException.class, () -> InvoiceCalculator.line(0, d("1"), null, null));
        assertThrows(BusinessException.class, () -> InvoiceCalculator.line(1, d("-1"), null, null));
        assertThrows(BusinessException.class, () -> InvoiceCalculator.line(1, d("1"), d("101"), null));
    }
}
