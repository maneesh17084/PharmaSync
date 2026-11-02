package com.pharmasync.service;

import com.pharmasync.model.Batch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * First Expiry, First Out: picks stock from the batches that expire earliest.
 * Expired, blocked (non-ACTIVE) and empty batches are never used.
 */
public final class FefoAllocator {

    public record Allocation(Batch batch, int quantity) {
    }

    private static final Comparator<Batch> FEFO_ORDER =
            Comparator.comparing(Batch::expiryDate).thenComparingInt(Batch::id);

    private FefoAllocator() {
    }

    /** Sellable batches in the order they should be dispatched. */
    public static List<Batch> dispatchOrder(List<Batch> batches, LocalDate today) {
        return batches.stream()
                .filter(b -> b.isSellable(today))
                .sorted(FEFO_ORDER)
                .toList();
    }

    public static int availableQuantity(List<Batch> batches, LocalDate today) {
        return dispatchOrder(batches, today).stream().mapToInt(Batch::quantity).sum();
    }

    public static List<Allocation> allocate(List<Batch> batches, int quantity, LocalDate today) {
        if (quantity <= 0) {
            throw new BusinessException("Quantity must be greater than zero");
        }
        List<Batch> ordered = dispatchOrder(batches, today);
        int available = ordered.stream().mapToInt(Batch::quantity).sum();
        if (available < quantity) {
            String name = batches.isEmpty() ? "medicine" : batches.get(0).medicineName();
            throw new InsufficientStockException(name, quantity, available);
        }
        List<Allocation> result = new ArrayList<>();
        int remaining = quantity;
        for (Batch b : ordered) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, b.quantity());
            result.add(new Allocation(b, take));
            remaining -= take;
        }
        return result;
    }
}
