package com.pharmasync.db;

import com.pharmasync.model.Customer;
import com.pharmasync.model.Medicine;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Role;
import com.pharmasync.model.Sale;

import com.pharmasync.model.TxnType;
import com.pharmasync.model.User;
import com.pharmasync.service.BusinessException;
import com.pharmasync.service.PasswordHasher;
import com.pharmasync.service.PurchaseService.PurchaseLine;
import com.pharmasync.service.PurchaseService.PurchaseRequest;
import com.pharmasync.service.SalesService.SaleLine;
import com.pharmasync.service.SalesService.SaleRequest;
import com.pharmasync.service.Services;
import com.pharmasync.service.Session;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default accounts plus realistic demo stock: batches with dates relative to today (so expired,
 * near-expiry and low-stock alerts appear immediately), a few purchases and a month of sales.
 */
public final class DemoDataSeeder {

    public static final String DEFAULT_ADMIN = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    public static final String DEFAULT_STAFF = "staff";
    public static final String DEFAULT_STAFF_PASSWORD = "staff123";

    private record OpeningBatch(String code, String batchNo, int mfgOffset, int expiryOffset, int qty, String mrp) {
    }

    private static final List<OpeningBatch> OPENING = List.of(
            // Paracetamol: the FEFO example from the specification (B101 expires first).
            new OpeningBatch("MED001", "B101", -300, 60, 100, "18.00"),
            new OpeningBatch("MED001", "B102", -200, 210, 150, "18.00"),
            new OpeningBatch("MED001", "B103", -60, 400, 200, "18.00"),
            new OpeningBatch("MED002", "CTZ2401", -240, 330, 260, "13.50"),
            new OpeningBatch("MED003", "LMC2311", -500, 75, 80, "28.00"),
            new OpeningBatch("MED003", "LMC2409", -90, 500, 300, "28.00"),
            new OpeningBatch("MED004", "AUG2402", -200, 380, 140, "228.00"),
            // Antibiotic with an expired batch still on the shelf and too little valid stock.
            new OpeningBatch("MED005", "AZ0921", -720, -20, 40, "132.00"),
            new OpeningBatch("MED005", "AZ1123", -150, 300, 30, "132.00"),
            new OpeningBatch("MED006", "BRF2405", -120, 560, 220, "36.00"),
            new OpeningBatch("MED007", "BEN2402", -330, 50, 60, "128.00"),
            new OpeningBatch("MED007", "BEN2410", -30, 700, 120, "128.00"),
            new OpeningBatch("MED008", "PAN2406", -100, 620, 240, "178.00"),
            new OpeningBatch("MED009", "GLY2403", -210, 450, 400, "32.00"),
            new OpeningBatch("MED010", "HMX2408", -60, 120, 30, "170.00"),
            new OpeningBatch("MED011", "MNC2301", -700, -5, 25, "65.00"),
            new OpeningBatch("MED011", "MNC2404", -300, 45, 20, "65.00"),
            new OpeningBatch("MED012", "BEC2407", -80, 640, 180, "52.00"),
            new OpeningBatch("MED013", "ORS2405", -150, 580, 500, "25.00"),
            new OpeningBatch("MED014", "GLV2401", -280, 1000, 900, "22.00"),
            new OpeningBatch("MED015", "AML2404", -180, 15, 50, "26.00"),
            new OpeningBatch("MED015", "AML2408", -40, 690, 160, "26.00"));

    /** Medicines kept out of demo sales so their low-stock and expiry alerts stay visible. */
    private static final Set<String> NOT_SOLD_IN_DEMO = Set.of("MED005", "MED010", "MED011");

    private DemoDataSeeder() {
    }

    public static void seed(boolean withDemoData) {
        int adminId = Services.USER_DAO.insert(DEFAULT_ADMIN, PasswordHasher.hash(DEFAULT_ADMIN_PASSWORD),
                "Store Administrator", Role.ADMIN, true);
        Services.USER_DAO.insert(DEFAULT_STAFF, PasswordHasher.hash(DEFAULT_STAFF_PASSWORD),
                "Counter Staff", Role.STAFF, true);
        if (!withDemoData) {
            return;
        }
        User admin = Services.USER_DAO.findById(adminId).orElseThrow();
        Session.start(admin);
        try {
            Map<String, Medicine> meds = Services.MEDICINE_DAO.search("", true).stream()
                    .collect(Collectors.toMap(Medicine::code, Function.identity()));
            seedOpeningStock(meds);
            seedPurchases(meds);
            seedSales(meds);
            Services.AUDIT.log("SEED", "DATABASE", null, "Demo data loaded");
        } finally {
            Session.end();
        }
    }

    private static void seedOpeningStock(Map<String, Medicine> meds) {
        LocalDate today = LocalDate.now();
        Jdbc.inTransaction(() -> {
            for (OpeningBatch o : OPENING) {
                Medicine m = meds.get(o.code());
                int batchId = Services.BATCH_DAO.insert(m.id(), o.batchNo(), today.plusDays(o.mfgOffset()),
                        today.plusDays(o.expiryOffset()), o.qty(), m.purchasePrice(), new BigDecimal(o.mrp()), 1);
                Services.STOCK_TXN_DAO.insert(batchId, m.id(), TxnType.OPENING, o.qty(), o.qty(), "OPENING",
                        "OPENING", "Opening stock", Session.userId());
            }
        });
    }

    private static void seedPurchases(Map<String, Medicine> meds) {
        LocalDate today = LocalDate.now();
        Services.PURCHASES.createAndReceive(new PurchaseRequest(1, "APX/INV/7781", today.minusDays(25), "Monthly replenishment",
                List.of(line(meds.get("MED001"), 300, "B104", today.minusDays(40), today.plusDays(720)),
                        line(meds.get("MED006"), 150, "BRF2410", today.minusDays(30), today.plusDays(700)))),
                today.minusDays(24));
        Services.PURCHASES.createAndReceive(new PurchaseRequest(2, "SUN/2231", today.minusDays(12), null,
                List.of(line(meds.get("MED002"), 200, "CTZ2410", today.minusDays(20), today.plusDays(690)),
                        line(meds.get("MED008"), 100, "PAN2410", today.minusDays(25), today.plusDays(700)),
                        line(meds.get("MED013"), 300, "ORS2410", today.minusDays(18), today.plusDays(710)))),
                today.minusDays(11));
        Services.PURCHASES.createAndReceive(new PurchaseRequest(3, "NMS-5512", today.minusDays(3), "Hospital tender stock",
                List.of(line(meds.get("MED004"), 60, "AUG2410", today.minusDays(15), today.plusDays(680)),
                        line(meds.get("MED012"), 120, "BEC2410", today.minusDays(10), today.plusDays(700)))),
                today.minusDays(2));
        // An open order waiting for delivery, to demonstrate the receive workflow.
        Services.PURCHASES.createOrder(new PurchaseRequest(1, null, today.minusDays(1), "Urgent: low stock items",
                List.of(line(meds.get("MED005"), 120, null, null, null),
                        line(meds.get("MED010"), 60, null, null, null),
                        line(meds.get("MED011"), 100, null, null, null))));
    }

    private static PurchaseLine line(Medicine m, int qty, String batchNo, LocalDate mfg, LocalDate expiry) {
        BigDecimal mrp = m.sellingPrice().multiply(BigDecimal.valueOf(1.15)).setScale(2, java.math.RoundingMode.HALF_UP);
        return new PurchaseLine(m.id(), qty, m.purchasePrice(), mrp, m.gstRate(), batchNo, mfg, expiry);
    }

    private static void seedSales(Map<String, Medicine> meds) {
        Random rnd = new Random(42);
        List<Customer> customers = Services.MASTER.searchCustomers("", true);
        List<Medicine> sellable = meds.values().stream()
                .filter(m -> !NOT_SOLD_IN_DEMO.contains(m.code()))
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        for (int daysAgo = 29; daysAgo >= 0; daysAgo--) {
            int invoicesToday = rnd.nextInt(3);
            for (int k = 0; k < invoicesToday; k++) {
                LocalDateTime when = now.minusDays(daysAgo).withHour(9 + rnd.nextInt(8)).withMinute(rnd.nextInt(60));
                if (when.isAfter(now)) {
                    when = now.minusMinutes(5);
                }
                List<SaleLine> lines = new ArrayList<>();
                int lineCount = 1 + rnd.nextInt(3);
                List<Integer> used = new ArrayList<>();
                for (int l = 0; l < lineCount; l++) {
                    int idx = rnd.nextInt(sellable.size());
                    if (used.contains(idx)) {
                        continue;
                    }
                    used.add(idx);
                    // Paracetamol, cetirizine and ORS move fastest.
                    Medicine m = sellable.get(idx);
                    int base = Set.of("MED001", "MED002", "MED013").contains(m.code()) ? 30 : 8;
                    lines.add(new SaleLine(m.id(), base + rnd.nextInt(base), null, null,
                            rnd.nextInt(4) == 0 ? BigDecimal.valueOf(5) : BigDecimal.ZERO));
                }
                Customer c = customers.get(rnd.nextInt(customers.size()));
                PaymentStatus pay = daysAgo > 7 || rnd.nextBoolean() ? PaymentStatus.PAID : PaymentStatus.UNPAID;
                try {
                    Sale sale = Services.SALES.createSale(new SaleRequest(c.id(), pay == PaymentStatus.PAID ? "NEFT" : "CREDIT",
                            pay, null, lines, when)).sale();
                    // Some credit invoices get a part-payment a few days later, to show the payment ledger.
                    if (pay == PaymentStatus.UNPAID && daysAgo >= 3 && rnd.nextBoolean()) {
                        BigDecimal half = sale.totalAmount().divide(BigDecimal.valueOf(2), 0, java.math.RoundingMode.DOWN);
                        Services.SALES.recordPayment(sale.id(), half, "UPI", "Part payment",
                                when.toLocalDate().plusDays(2));
                    }
                } catch (BusinessException ignored) {
                    // not enough stock for this random order; skip it
                }
            }
        }
    }
}
