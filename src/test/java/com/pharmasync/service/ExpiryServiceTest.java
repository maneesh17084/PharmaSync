package com.pharmasync.service;

import com.pharmasync.model.ExpiryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpiryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    void specExamples() {
        // Paracetamol Mar 2027 is near expiry when we are within 90 days of it.
        assertEquals(ExpiryStatus.NEAR_EXPIRY,
                ExpiryService.classify(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 1, 15), 90));
        assertEquals(ExpiryStatus.VALID, ExpiryService.classify(LocalDate.of(2027, 12, 31), TODAY, 90));
        assertEquals(ExpiryStatus.EXPIRED, ExpiryService.classify(LocalDate.of(2026, 10, 31), TODAY, 90));
    }

    @Test
    void boundaries() {
        assertEquals(ExpiryStatus.EXPIRED, ExpiryService.classify(TODAY.minusDays(1), TODAY, 90));
        assertEquals(ExpiryStatus.NEAR_EXPIRY, ExpiryService.classify(TODAY, TODAY, 90));
        assertEquals(ExpiryStatus.NEAR_EXPIRY, ExpiryService.classify(TODAY.plusDays(90), TODAY, 90));
        assertEquals(ExpiryStatus.VALID, ExpiryService.classify(TODAY.plusDays(91), TODAY, 90));
    }

    @Test
    void thresholdIsConfigurable() {
        LocalDate expiry = TODAY.plusDays(120);
        assertEquals(ExpiryStatus.VALID, ExpiryService.classify(expiry, TODAY, 90));
        assertEquals(ExpiryStatus.NEAR_EXPIRY, ExpiryService.classify(expiry, TODAY, 180));
    }

    @Test
    void daysUntil() {
        assertEquals(30, ExpiryService.daysUntil(TODAY.plusDays(30), TODAY));
        assertEquals(-3, ExpiryService.daysUntil(TODAY.minusDays(3), TODAY));
    }
}
