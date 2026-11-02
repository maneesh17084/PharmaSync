package com.pharmasync.ui.dialogs;

import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import com.pharmasync.service.ExpiryService;
import com.pharmasync.ui.StyledTable;

import java.awt.Color;
import java.time.LocalDate;

/** Row colour for a batch: red expired, amber near expiry, grey closed/blocked. */
public final class BatchTint {

    private BatchTint() {
    }

    public static Color of(Batch b, LocalDate today, int threshold) {
        if (b.status() == BatchStatus.RETURNED || b.status() == BatchStatus.DISPOSED) {
            return StyledTable.GREY;
        }
        return switch (ExpiryService.classify(b.expiryDate(), today, threshold)) {
            case EXPIRED -> StyledTable.RED;
            case NEAR_EXPIRY -> StyledTable.AMBER;
            case VALID -> b.status() == BatchStatus.QUARANTINED ? StyledTable.GREY : null;
        };
    }
}
