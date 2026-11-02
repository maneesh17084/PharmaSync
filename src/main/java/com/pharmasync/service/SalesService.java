package com.pharmasync.service;

import com.pharmasync.dao.BatchDao;
import com.pharmasync.dao.CustomerDao;
import com.pharmasync.dao.MedicineDao;
import com.pharmasync.dao.SaleDao;
import com.pharmasync.dao.StockTransactionDao;
import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Batch;
import com.pharmasync.model.Customer;
import com.pharmasync.model.IndianState;
import com.pharmasync.model.Medicine;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Sale;
import com.pharmasync.model.SaleItem;
import com.pharmasync.model.SalePayment;
import com.pharmasync.model.SupplyType;

import com.pharmasync.model.TxnType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Retailer orders, FEFO dispatch and invoicing (FR-11, FR-12, FR-13, FR-16). */
public class SalesService {

    /** One ordered medicine. batchId null means "pick batches by FEFO". unitPrice null means list price. */
    public record SaleLine(int medicineId, int quantity, Integer batchId, BigDecimal unitPrice, BigDecimal discountPct) {
    }

    /**
     * paymentStatus says what is received at billing: PAID = the full total, UNPAID = nothing,
     * PARTIAL = amountReceived (required only for PARTIAL).
     */
    public record SaleRequest(int customerId, String paymentMode, PaymentStatus paymentStatus, BigDecimal amountReceived,
                              List<SaleLine> lines, LocalDateTime saleDate) {
    }

    public record SaleResult(Sale sale, List<SaleItem> items) {
    }

    private record PlannedItem(Medicine medicine, Batch batch, int quantity, BigDecimal unitPrice,
                               BigDecimal discountPct, InvoiceCalculator.LineAmounts amounts) {
    }

    private final SaleDao sales;
    private final BatchDao batches;
    private final MedicineDao medicines;
    private final CustomerDao customers;
    private final StockTransactionDao stockTxns;
    private final SettingsService settings;
    private final AuditService audit;

    public SalesService(SaleDao sales, BatchDao batches, MedicineDao medicines, CustomerDao customers,
                        StockTransactionDao stockTxns, SettingsService settings, AuditService audit) {
        this.sales = sales;
        this.batches = batches;
        this.medicines = medicines;
        this.customers = customers;
        this.stockTxns = stockTxns;
        this.settings = settings;
        this.audit = audit;
    }

    public List<Sale> find(LocalDate from, LocalDate to, String text) {
        return sales.find(from, to, text);
    }

    public List<SaleItem> items(int saleId) {
        return sales.findItems(saleId);
    }

    public Sale findSale(int saleId) {
        return sales.findById(saleId).orElseThrow(() -> new BusinessException("Sale not found"));
    }

    /** Batches that can be sold for a medicine, in FEFO order. */
    public List<Batch> sellableBatches(int medicineId) {
        return FefoAllocator.dispatchOrder(batches.findByMedicine(medicineId), LocalDate.now());
    }

    /** FEFO suggestion without reserving stock. Throws InsufficientStockException when not enough valid stock. */
    public List<FefoAllocator.Allocation> suggestBatches(int medicineId, int quantity) {
        return FefoAllocator.allocate(batches.findByMedicine(medicineId), quantity, LocalDate.now());
    }

    public List<SalePayment> payments(int saleId) {
        return sales.findPayments(saleId);
    }

    /** Records money received against an invoice; the amount paid and payment status follow from the ledger. */
    public Sale recordPayment(int saleId, BigDecimal amount, String mode, String reference, LocalDate date) {
        Session.requireLogin();
        return Jdbc.inTransaction(() -> {
            Sale s = findSale(saleId);
            addPayment(s, amount, mode, reference, date == null ? LocalDate.now() : date);
            return findSale(saleId);
        });
    }

    private void addPayment(Sale s, BigDecimal amount, String mode, String reference, LocalDate date) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        BigDecimal received = InvoiceCalculator.round(amount);
        if (received.compareTo(s.balanceDue()) > 0) {
            throw new BusinessException("Payment of " + received + " exceeds the balance due of " + s.balanceDue()
                    + " on " + s.invoiceNo());
        }
        if (date.isBefore(s.saleDate().toLocalDate())) {
            throw new BusinessException("Payment date cannot be before the invoice date " + s.saleDate().toLocalDate());
        }
        String m = mode == null || mode.isBlank() || mode.equals("CREDIT") ? "CASH" : mode.trim();
        sales.insertPayment(s.id(), date, received, m, reference == null || reference.isBlank() ? null : reference.trim(),
                Session.userId());
        BigDecimal paid = s.amountPaid().add(received);
        sales.updatePayment(s.id(), paid, statusFor(paid, s.totalAmount()));
        audit.log("PAYMENT", "SALE", s.id(), s.invoiceNo() + ": received " + received + " by " + m
                + ", balance " + s.totalAmount().subtract(paid));
    }

    static PaymentStatus statusFor(BigDecimal paid, BigDecimal total) {
        if (paid.signum() == 0) {
            return PaymentStatus.UNPAID;
        }
        return paid.compareTo(total) >= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL;
    }

    /**
     * Confirms an order: locks the medicine's batches, allocates by FEFO (or validates the chosen batch),
     * writes the invoice and its items, decrements batch stock and records stock movements, all in one transaction.
     */
    public SaleResult createSale(SaleRequest request) {
        Session.requireLogin();
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BusinessException("Add at least one medicine to the order");
        }
        LocalDateTime when = request.saleDate() == null ? LocalDateTime.now() : request.saleDate();
        // Expiry is always judged against the real dispatch day, even when the invoice date is set explicitly.
        LocalDate today = LocalDate.now();

        return Jdbc.inTransaction(() -> {
            Customer customer = customers.findById(request.customerId())
                    .orElseThrow(() -> new BusinessException("Select a customer"));
            if (!customer.active()) {
                throw new BusinessException("Customer " + customer.name() + " is inactive");
            }
            if (customer.state() == null) {
                throw new BusinessException("Set the state of " + customer.name()
                        + " in Customers first: it is the place of supply that decides CGST/SGST or IGST");
            }
            // Goods are delivered to the customer, so the customer's state is the place of supply.
            IndianState placeOfSupply = customer.state();
            SupplyType supplyType = Gst.supplyType(settings.companyState(), placeOfSupply);

            List<PlannedItem> planned = new ArrayList<>();
            for (SaleLine line : request.lines()) {
                Medicine med = medicines.findById(line.medicineId())
                        .orElseThrow(() -> new BusinessException("Medicine not found"));
                if (!med.active()) {
                    throw new BusinessException(med.brandName() + " is inactive and cannot be sold");
                }
                BigDecimal price = line.unitPrice() == null ? med.sellingPrice() : line.unitPrice();
                List<Batch> locked = withPlannedDeductions(batches.findByMedicineForUpdate(med.id()), planned);
                for (FefoAllocator.Allocation a : allocate(line, med, locked, today)) {
                    InvoiceCalculator.LineAmounts amounts =
                            InvoiceCalculator.line(a.quantity(), price, line.discountPct(), med.gstRate());
                    planned.add(new PlannedItem(med, a.batch(), a.quantity(), price,
                            line.discountPct() == null ? BigDecimal.ZERO : line.discountPct(), amounts));
                }
            }

            InvoiceCalculator.Totals totals = InvoiceCalculator.totals(planned.stream().map(PlannedItem::amounts).toList());
            String prefix = NumberGenerator.prefix("INV", when.toLocalDate());
            String invoiceNo = NumberGenerator.next(prefix, sales.lastNumberWithPrefix(prefix));
            PaymentStatus payment = request.paymentStatus() == null ? PaymentStatus.UNPAID : request.paymentStatus();
            BigDecimal received = switch (payment) {
                case PAID -> totals.total();
                case UNPAID -> BigDecimal.ZERO;
                case PARTIAL -> {
                    BigDecimal r = request.amountReceived();
                    if (r == null || r.signum() <= 0 || r.compareTo(totals.total()) >= 0) {
                        throw new BusinessException("For a partial payment enter the amount received: more than 0 "
                                + "and less than the invoice total of " + totals.total());
                    }
                    yield r;
                }
            };
            String mode = request.paymentMode() == null || request.paymentMode().isBlank() ? "CREDIT" : request.paymentMode();
            int saleId = sales.insert(invoiceNo, customer.id(), when, totals.subtotal(), totals.discount(),
                    totals.tax(), totals.total(), mode, placeOfSupply, supplyType, Session.userId());

            for (PlannedItem p : planned) {
                sales.insertItem(saleId, p.medicine().id(), p.batch().id(), p.quantity(), p.unitPrice(),
                        p.discountPct(), p.medicine().gstRate(), p.amounts().tax(), p.amounts().total());
                Batch current = batches.findByIdForUpdate(p.batch().id()).orElseThrow();
                int balance = StockCalculator.apply(current.quantity(), -p.quantity());
                batches.updateQuantity(current.id(), balance);
                stockTxns.insert(current.id(), p.medicine().id(), TxnType.SALE_OUT, -p.quantity(), balance,
                        "SALE", invoiceNo, customer.name(), Session.userId());
            }
            audit.log("CREATE", "SALE", saleId, invoiceNo + " for " + customer.name() + ", total " + totals.total()
                    + ", " + supplyType + " (place of supply " + placeOfSupply + ")");
            if (received.signum() > 0) {
                addPayment(sales.findById(saleId).orElseThrow(), received, mode, "At billing", when.toLocalDate());
            }
            return new SaleResult(sales.findById(saleId).orElseThrow(), sales.findItems(saleId));
        });
    }

    private static List<FefoAllocator.Allocation> allocate(SaleLine line, Medicine med, List<Batch> locked, LocalDate today) {
        if (line.batchId() == null) {
            return FefoAllocator.allocate(locked, line.quantity(), today);
        }
        Batch chosen = locked.stream().filter(b -> b.id() == line.batchId()).findFirst()
                .orElseThrow(() -> new BusinessException("Selected batch does not belong to " + med.brandName()));
        if (chosen.isExpired(today)) {
            throw new BusinessException("Batch " + chosen.batchNo() + " expired on " + chosen.expiryDate()
                    + " and cannot be dispatched");
        }
        if (!chosen.isSellable(today)) {
            throw new BusinessException("Batch " + chosen.batchNo() + " is " + chosen.status() + " and cannot be dispatched");
        }
        if (line.quantity() <= 0) {
            throw new BusinessException("Quantity must be greater than zero");
        }
        if (chosen.quantity() < line.quantity()) {
            throw new InsufficientStockException(med.brandName() + " batch " + chosen.batchNo(),
                    line.quantity(), chosen.quantity());
        }
        return List.of(new FefoAllocator.Allocation(chosen, line.quantity()));
    }

    /** Reduces batch quantities by what earlier lines of the same order already took. */
    private static List<Batch> withPlannedDeductions(List<Batch> locked, List<PlannedItem> planned) {
        List<Batch> adjusted = new ArrayList<>(locked.size());
        for (Batch b : locked) {
            int used = planned.stream().filter(p -> p.batch().id() == b.id()).mapToInt(PlannedItem::quantity).sum();
            adjusted.add(used == 0 ? b : b.withQuantity(b.quantity() - used));
        }
        return adjusted;
    }
}
