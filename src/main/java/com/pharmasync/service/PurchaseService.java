package com.pharmasync.service;

import com.pharmasync.dao.BatchDao;
import com.pharmasync.dao.MedicineDao;
import com.pharmasync.dao.PurchaseDao;
import com.pharmasync.dao.StockTransactionDao;
import com.pharmasync.dao.SupplierDao;
import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Purchase;
import com.pharmasync.model.PurchaseItem;
import com.pharmasync.model.PurchaseStatus;
import com.pharmasync.model.Supplier;
import com.pharmasync.model.SupplyType;

import com.pharmasync.model.TxnType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Purchase orders and batch-wise stock-in (FR-08, FR-09, FR-13). */
public class PurchaseService {

    public record PurchaseLine(int medicineId, int quantity, BigDecimal unitCost, BigDecimal mrp, BigDecimal gstRate,
                               String batchNo, LocalDate mfgDate, LocalDate expiryDate) {
    }

    public record PurchaseRequest(int supplierId, String supplierInvoiceRef, LocalDate orderDate, String notes,
                                  List<PurchaseLine> lines) {
    }

    private final PurchaseDao purchases;
    private final BatchDao batches;
    private final MedicineDao medicines;
    private final SupplierDao suppliers;
    private final StockTransactionDao stockTxns;
    private final SettingsService settings;
    private final AuditService audit;

    public PurchaseService(PurchaseDao purchases, BatchDao batches, MedicineDao medicines, SupplierDao suppliers,
                           StockTransactionDao stockTxns, SettingsService settings, AuditService audit) {
        this.purchases = purchases;
        this.batches = batches;
        this.medicines = medicines;
        this.suppliers = suppliers;
        this.stockTxns = stockTxns;
        this.settings = settings;
        this.audit = audit;
    }

    public List<Purchase> find(LocalDate from, LocalDate to, PurchaseStatus status) {
        return purchases.find(from, to, status);
    }

    public List<PurchaseItem> items(int purchaseId) {
        return purchases.findItems(purchaseId);
    }

    /** Saves a purchase order (status ORDERED). Batch details may be filled in now or when receiving. */
    public Purchase createOrder(PurchaseRequest request) {
        Session.requireLogin();
        validateRequest(request);
        return Jdbc.inTransaction(() -> {
            int id = insertOrder(request);
            audit.log("CREATE", "PURCHASE", id, "Order with " + request.lines().size() + " line(s)");
            return purchases.findById(id).orElseThrow();
        });
    }

    /** Records a shipment that arrives without a prior order: creates the purchase and receives it in one transaction. */
    public Purchase createAndReceive(PurchaseRequest request, LocalDate receivedDate) {
        Session.requireLogin();
        validateRequest(request);
        return Jdbc.inTransaction(() -> {
            int id = insertOrder(request);
            audit.log("CREATE", "PURCHASE", id, "Direct receipt with " + request.lines().size() + " line(s)");
            return receiveInternal(id, purchases.findItems(id), request.supplierInvoiceRef(), receivedDate);
        });
    }

    /**
     * Receives an ordered purchase. Each item must carry batch number, manufacturing and expiry dates.
     * Batches are created (or topped up when the same batch number already exists) and stock is posted.
     */
    public Purchase receive(int purchaseId, List<PurchaseItem> itemsWithBatchInfo, String invoiceRef,
                            LocalDate receivedDate) {
        Session.requireLogin();
        return Jdbc.inTransaction(() -> receiveInternal(purchaseId, itemsWithBatchInfo, invoiceRef, receivedDate));
    }

    public void cancel(int purchaseId) {
        Session.requireAdmin();
        Jdbc.inTransaction(() -> {
            Purchase p = purchases.findByIdForUpdate(purchaseId)
                    .orElseThrow(() -> new BusinessException("Purchase not found"));
            if (p.status() != PurchaseStatus.ORDERED) {
                throw new BusinessException("Only open orders can be cancelled");
            }
            purchases.updateStatus(purchaseId, PurchaseStatus.CANCELLED);
            audit.log("CANCEL", "PURCHASE", purchaseId, p.purchaseNo());
        });
    }

    /** Adds a supplier payment and updates the payment status. */
    public void recordPayment(int purchaseId, BigDecimal amount) {
        Session.requireAdmin();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        Jdbc.inTransaction(() -> {
            Purchase p = purchases.findByIdForUpdate(purchaseId)
                    .orElseThrow(() -> new BusinessException("Purchase not found"));
            if (p.status() == PurchaseStatus.CANCELLED) {
                throw new BusinessException("Cannot pay a cancelled purchase");
            }
            BigDecimal paid = p.amountPaid().add(amount);
            if (paid.compareTo(p.totalAmount()) > 0) {
                throw new BusinessException("Payment exceeds the outstanding balance of " + p.totalAmount().subtract(p.amountPaid()));
            }
            PaymentStatus status = paid.compareTo(p.totalAmount()) == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL;
            purchases.updatePayment(purchaseId, paid, status);
            audit.log("PAYMENT", "PURCHASE", purchaseId, p.purchaseNo() + " paid " + amount);
        });
    }

    private int insertOrder(PurchaseRequest request) {
        LocalDate orderDate = request.orderDate() == null ? LocalDate.now() : request.orderDate();
        List<InvoiceCalculator.LineAmounts> amounts = new ArrayList<>();
        for (PurchaseLine l : request.lines()) {
            amounts.add(InvoiceCalculator.line(l.quantity(), l.unitCost(), BigDecimal.ZERO, l.gstRate()));
        }
        InvoiceCalculator.Totals totals = InvoiceCalculator.totals(amounts);
        Supplier supplier = suppliers.findById(request.supplierId())
                .orElseThrow(() -> new BusinessException("Select a supplier"));
        if (supplier.state() == null) {
            throw new BusinessException("Set the state of " + supplier.name()
                    + " in Suppliers first: it decides whether the bill carries CGST/SGST or IGST");
        }
        // Goods are received at our premises: same state as the supplier means CGST + SGST, otherwise IGST.
        SupplyType supplyType = Gst.supplyType(supplier.state(), settings.companyState());
        String prefix = NumberGenerator.prefix("PO", orderDate);
        String number = NumberGenerator.next(prefix, purchases.lastNumberWithPrefix(prefix));
        int id = purchases.insert(number, request.supplierId(), blankToNull(request.supplierInvoiceRef()), orderDate,
                totals.subtotal(), totals.tax(), totals.total(), supplyType, blankToNull(request.notes()), Session.userId());
        for (int i = 0; i < request.lines().size(); i++) {
            PurchaseLine l = request.lines().get(i);
            purchases.insertItem(id, new PurchaseItem(0, id, l.medicineId(), null, null, blankToNull(l.batchNo()),
                    l.mfgDate(), l.expiryDate(), l.quantity(), l.unitCost(), orZero(l.mrp()), orZero(l.gstRate()),
                    amounts.get(i).total()));
        }
        return id;
    }

    private Purchase receiveInternal(int purchaseId, List<PurchaseItem> items, String invoiceRef, LocalDate receivedDate) {
        LocalDate received = receivedDate == null ? LocalDate.now() : receivedDate;
        Purchase p = purchases.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new BusinessException("Purchase not found"));
        if (p.status() != PurchaseStatus.ORDERED) {
            throw new BusinessException("Purchase " + p.purchaseNo() + " is already " + p.status());
        }
        List<PurchaseItem> stored = purchases.findItems(purchaseId);
        if (items.size() != stored.size()) {
            throw new BusinessException("Batch details are required for every item");
        }
        for (PurchaseItem item : items) {
            PurchaseItem original = stored.stream().filter(s -> s.id() == item.id()).findFirst()
                    .orElseThrow(() -> new BusinessException("Item does not belong to this purchase"));
            validateBatchInfo(item.batchNo(), item.mfgDate(), item.expiryDate(), received, original.medicineName());
            String batchNo = item.batchNo().trim();

            int batchId;
            int balance;
            Optional<Batch> existing = batches.findByMedicineAndBatchNoForUpdate(original.medicineId(), batchNo);
            if (existing.isPresent()) {
                Batch b = existing.get();
                if (!b.expiryDate().equals(item.expiryDate()) || !b.mfgDate().equals(item.mfgDate())) {
                    throw new BusinessException("Batch " + batchNo + " of " + original.medicineName()
                            + " already exists with different dates (mfg " + b.mfgDate() + ", exp " + b.expiryDate() + ")");
                }
                if (b.status() != BatchStatus.ACTIVE) {
                    throw new BusinessException("Batch " + batchNo + " is " + b.status() + " and cannot receive stock");
                }
                balance = StockCalculator.apply(b.quantity(), original.quantity());
                batches.updateQuantity(b.id(), balance);
                batchId = b.id();
            } else {
                balance = original.quantity();
                batchId = batches.insert(original.medicineId(), batchNo, item.mfgDate(), item.expiryDate(),
                        balance, original.unitCost(), original.mrp(), p.supplierId());
            }
            purchases.updateItemBatch(original.id(), batchId, batchNo, item.mfgDate(), item.expiryDate());
            stockTxns.insert(batchId, original.medicineId(), TxnType.PURCHASE_IN, original.quantity(), balance,
                    "PURCHASE", p.purchaseNo(), null, Session.userId());
        }
        purchases.markReceived(purchaseId, received, blankToNull(invoiceRef));
        audit.log("RECEIVE", "PURCHASE", purchaseId, p.purchaseNo() + " received, " + items.size() + " batch line(s)");
        return purchases.findById(purchaseId).orElseThrow();
    }

    private void validateRequest(PurchaseRequest r) {
        if (r.supplierId() <= 0) {
            throw new BusinessException("Select a supplier");
        }
        if (r.lines() == null || r.lines().isEmpty()) {
            throw new BusinessException("Add at least one medicine");
        }
        for (PurchaseLine l : r.lines()) {
            var med = medicines.findById(l.medicineId()).orElseThrow(() -> new BusinessException("Medicine not found"));
            if (l.quantity() <= 0) {
                throw new BusinessException("Quantity for " + med.brandName() + " must be greater than zero");
            }
            if (l.unitCost() == null || l.unitCost().signum() < 0) {
                throw new BusinessException("Unit cost for " + med.brandName() + " must not be negative");
            }
        }
    }

    static void validateBatchInfo(String batchNo, LocalDate mfg, LocalDate expiry, LocalDate receivedOn, String medicine) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException("Batch number is required for " + medicine);
        }
        if (mfg == null || expiry == null) {
            throw new BusinessException("Manufacturing and expiry dates are required for " + medicine);
        }
        if (!expiry.isAfter(mfg)) {
            throw new BusinessException("Expiry date must be after manufacturing date for " + medicine);
        }
        if (mfg.isAfter(receivedOn)) {
            throw new BusinessException("Manufacturing date cannot be in the future for " + medicine);
        }
        if (expiry.isBefore(receivedOn)) {
            throw new BusinessException("Cannot receive expired stock: " + medicine + " batch " + batchNo);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
