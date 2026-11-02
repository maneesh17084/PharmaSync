package com.pharmasync.service;

import com.pharmasync.dao.BatchDao;
import com.pharmasync.dao.MedicineDao;
import com.pharmasync.dao.StockTransactionDao;
import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import com.pharmasync.model.Medicine;
import com.pharmasync.model.StockTransaction;
import com.pharmasync.model.TxnType;

import java.time.LocalDate;
import java.util.List;

/** Batch lookups, alerts and non-sale stock movements: quarantine, return, disposal, adjustment (FR-05, FR-14, FR-15). */
public class StockService {

    private final BatchDao batches;
    private final MedicineDao medicines;
    private final StockTransactionDao stockTxns;
    private final ExpiryService expiry;
    private final AuditService audit;

    public StockService(BatchDao batches, MedicineDao medicines, StockTransactionDao stockTxns,
                        ExpiryService expiry, AuditService audit) {
        this.batches = batches;
        this.medicines = medicines;
        this.stockTxns = stockTxns;
        this.expiry = expiry;
        this.audit = audit;
    }

    public List<Batch> searchBatches(String text, BatchStatus status, boolean inStockOnly) {
        return batches.search(text, status, inStockOnly);
    }

    public List<Batch> batchesOf(int medicineId) {
        return batches.findByMedicine(medicineId);
    }

    public List<Medicine> lowStock() {
        return medicines.findLowStock();
    }

    public List<Batch> nearExpiry() {
        LocalDate today = LocalDate.now();
        return batches.findActiveExpiringBetween(today, today.plusDays(expiry.thresholdDays()));
    }

    public List<Batch> expiredInStock() {
        return batches.findExpiredInStock(LocalDate.now());
    }

    public List<StockTransaction> movements(LocalDate from, LocalDate to, String text) {
        return stockTxns.find(from, to, text);
    }

    /** Blocks a batch from sale while keeping its quantity on record. */
    public void quarantine(int batchId, String remarks) {
        Session.requireAdmin();
        Jdbc.inTransaction(() -> {
            Batch b = lockActive(batchId, BatchStatus.ACTIVE);
            batches.updateStatus(b.id(), BatchStatus.QUARANTINED, b.quantity());
            stockTxns.insert(b.id(), b.medicineId(), TxnType.QUARANTINE, 0, b.quantity(), "BATCH", b.batchNo(),
                    remarks, Session.userId());
            audit.log("QUARANTINE", "BATCH", b.id(), b.batchNo() + " " + nullToEmpty(remarks));
        });
    }

    /** Puts a quarantined batch back on sale; refused if it has expired. */
    public void release(int batchId) {
        Session.requireAdmin();
        Jdbc.inTransaction(() -> {
            Batch b = lockActive(batchId, BatchStatus.QUARANTINED);
            if (b.isExpired(LocalDate.now())) {
                throw new BusinessException("Batch " + b.batchNo() + " has expired and cannot be released for sale");
            }
            batches.updateStatus(b.id(), BatchStatus.ACTIVE, b.quantity());
            audit.log("RELEASE", "BATCH", b.id(), b.batchNo());
        });
    }

    /** Sends the remaining quantity back to the supplier. */
    public void returnToSupplier(int batchId, String remarks) {
        writeOff(batchId, BatchStatus.RETURNED, TxnType.RETURN, remarks);
    }

    /** Destroys the remaining quantity (e.g. expired or damaged stock). */
    public void dispose(int batchId, String remarks) {
        writeOff(batchId, BatchStatus.DISPOSED, TxnType.DISPOSAL, remarks);
    }

    /** Quarantines every ACTIVE batch that has expired. Returns how many were moved. */
    public int quarantineAllExpired() {
        Session.requireAdmin();
        return Jdbc.inTransaction(() -> {
            int count = 0;
            for (Batch b : batches.findExpiredInStock(LocalDate.now())) {
                if (b.status() == BatchStatus.ACTIVE) {
                    batches.updateStatus(b.id(), BatchStatus.QUARANTINED, b.quantity());
                    stockTxns.insert(b.id(), b.medicineId(), TxnType.QUARANTINE, 0, b.quantity(), "BATCH",
                            b.batchNo(), "Expired on " + b.expiryDate(), Session.userId());
                    count++;
                }
            }
            audit.log("QUARANTINE_EXPIRED", "BATCH", null, count + " expired batch(es) quarantined");
            return count;
        });
    }

    /** Manual correction after a physical count; delta may be positive or negative. */
    public void adjust(int batchId, int delta, String reason) {
        Session.requireAdmin();
        if (delta == 0) {
            throw new BusinessException("Adjustment quantity cannot be zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A reason is required for stock adjustments");
        }
        Jdbc.inTransaction(() -> {
            Batch b = batches.findByIdForUpdate(batchId).orElseThrow(() -> new BusinessException("Batch not found"));
            if (b.status() == BatchStatus.RETURNED || b.status() == BatchStatus.DISPOSED) {
                throw new BusinessException("Batch " + b.batchNo() + " is closed (" + b.status() + ")");
            }
            int balance = StockCalculator.apply(b.quantity(), delta);
            batches.updateQuantity(b.id(), balance);
            stockTxns.insert(b.id(), b.medicineId(), TxnType.ADJUSTMENT, delta, balance, "BATCH", b.batchNo(),
                    reason.trim(), Session.userId());
            audit.log("ADJUST", "BATCH", b.id(), b.batchNo() + " " + (delta > 0 ? "+" : "") + delta + ": " + reason.trim());
        });
    }

    private void writeOff(int batchId, BatchStatus newStatus, TxnType type, String remarks) {
        Session.requireAdmin();
        Jdbc.inTransaction(() -> {
            Batch b = batches.findByIdForUpdate(batchId).orElseThrow(() -> new BusinessException("Batch not found"));
            if (b.status() == BatchStatus.RETURNED || b.status() == BatchStatus.DISPOSED) {
                throw new BusinessException("Batch " + b.batchNo() + " is already " + b.status());
            }
            int qty = b.quantity();
            batches.updateStatus(b.id(), newStatus, 0);
            stockTxns.insert(b.id(), b.medicineId(), type, -qty, 0, "BATCH", b.batchNo(), remarks, Session.userId());
            audit.log(type.name(), "BATCH", b.id(), b.batchNo() + " qty " + qty + " " + nullToEmpty(remarks));
        });
    }

    private Batch lockActive(int batchId, BatchStatus expected) {
        Batch b = batches.findByIdForUpdate(batchId).orElseThrow(() -> new BusinessException("Batch not found"));
        if (b.status() != expected) {
            throw new BusinessException("Batch " + b.batchNo() + " is " + b.status() + ", expected " + expected);
        }
        return b;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
