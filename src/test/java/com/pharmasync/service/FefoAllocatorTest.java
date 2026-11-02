package com.pharmasync.service;

import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FefoAllocatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    private static Batch batch(int id, String no, LocalDate expiry, int qty, BatchStatus status) {
        return new Batch(id, 1, "Paracetamol 500mg", no, expiry.minusYears(2), expiry, qty,
                BigDecimal.TEN, BigDecimal.TEN, null, null, status);
    }

    /** The example from the specification: B101 (Mar 2027), B102 (Jun 2027), B103 (Dec 2027). */
    private static List<Batch> specBatches() {
        // Deliberately out of order to prove sorting.
        return List.of(
                batch(3, "B103", LocalDate.of(2027, 12, 31), 200, BatchStatus.ACTIVE),
                batch(1, "B101", LocalDate.of(2027, 3, 31), 100, BatchStatus.ACTIVE),
                batch(2, "B102", LocalDate.of(2027, 6, 30), 150, BatchStatus.ACTIVE));
    }

    @Test
    void dispatchesFiftyUnitsFromEarliestExpiringBatch() {
        List<FefoAllocator.Allocation> result = FefoAllocator.allocate(specBatches(), 50, TODAY);
        assertEquals(1, result.size());
        assertEquals("B101", result.get(0).batch().batchNo());
        assertEquals(50, result.get(0).quantity());
    }

    @Test
    void spillsOverIntoNextBatchInExpiryOrder() {
        List<FefoAllocator.Allocation> result = FefoAllocator.allocate(specBatches(), 300, TODAY);
        assertEquals(3, result.size());
        assertEquals("B101", result.get(0).batch().batchNo());
        assertEquals(100, result.get(0).quantity());
        assertEquals("B102", result.get(1).batch().batchNo());
        assertEquals(150, result.get(1).quantity());
        assertEquals("B103", result.get(2).batch().batchNo());
        assertEquals(50, result.get(2).quantity());
    }

    @Test
    void skipsExpiredQuarantinedAndEmptyBatches() {
        List<Batch> batches = List.of(
                batch(1, "OLD", TODAY.minusDays(1), 500, BatchStatus.ACTIVE),
                batch(2, "HOLD", TODAY.plusDays(10), 500, BatchStatus.QUARANTINED),
                batch(3, "EMPTY", TODAY.plusDays(20), 0, BatchStatus.ACTIVE),
                batch(4, "GOOD", TODAY.plusDays(100), 80, BatchStatus.ACTIVE));
        List<FefoAllocator.Allocation> result = FefoAllocator.allocate(batches, 60, TODAY);
        assertEquals(1, result.size());
        assertEquals("GOOD", result.get(0).batch().batchNo());
        assertEquals(80, FefoAllocator.availableQuantity(batches, TODAY));
    }

    @Test
    void batchExpiringTodayIsStillDispatchable() {
        List<Batch> batches = List.of(batch(1, "TODAY", TODAY, 10, BatchStatus.ACTIVE));
        assertEquals(10, FefoAllocator.allocate(batches, 10, TODAY).get(0).quantity());
    }

    @Test
    void rejectsQuantityAboveValidStock() {
        List<Batch> batches = List.of(
                batch(1, "OLD", TODAY.minusDays(5), 1000, BatchStatus.ACTIVE),
                batch(2, "GOOD", TODAY.plusDays(100), 40, BatchStatus.ACTIVE));
        InsufficientStockException e = assertThrows(InsufficientStockException.class,
                () -> FefoAllocator.allocate(batches, 50, TODAY));
        assertEquals(50, e.getRequested());
        assertEquals(40, e.getAvailable());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(BusinessException.class, () -> FefoAllocator.allocate(specBatches(), 0, TODAY));
    }

    @Test
    void dispatchOrderIsAscendingByExpiry() {
        List<Batch> ordered = FefoAllocator.dispatchOrder(specBatches(), TODAY);
        assertEquals(List.of("B101", "B102", "B103"), ordered.stream().map(Batch::batchNo).toList());
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(!ordered.get(i).expiryDate().isBefore(ordered.get(i - 1).expiryDate()));
        }
    }
}
