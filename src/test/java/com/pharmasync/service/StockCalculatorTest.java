package com.pharmasync.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockCalculatorTest {

    @Test
    void specExampleInitialPlusPurchaseMinusSale() {
        // 500 + 200 - 150 = 550
        assertEquals(550, StockCalculator.applyAll(500, 200, -150));
    }

    @Test
    void neverGoesNegative() {
        assertThrows(BusinessException.class, () -> StockCalculator.apply(100, -101));
        assertEquals(0, StockCalculator.apply(100, -100));
    }

    @Test
    void failedMovementLeavesEarlierMovementsUnapplied() {
        assertThrows(BusinessException.class, () -> StockCalculator.applyAll(10, 5, -20));
    }
}
