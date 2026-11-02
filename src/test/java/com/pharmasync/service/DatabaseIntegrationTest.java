package com.pharmasync.service;

import com.pharmasync.config.DbConfig;
import com.pharmasync.db.DatabaseManager;
import com.pharmasync.db.DemoDataSeeder;
import com.pharmasync.db.Jdbc;
import com.pharmasync.db.SchemaInitializer;
import com.pharmasync.db.TableData;
import com.pharmasync.model.Batch;
import com.pharmasync.model.Category;
import com.pharmasync.model.Customer;
import com.pharmasync.model.CustomerType;
import com.pharmasync.model.IndianState;
import com.pharmasync.model.Medicine;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Purchase;
import com.pharmasync.model.Sale;
import com.pharmasync.model.SalePayment;
import com.pharmasync.model.SupplyType;

import com.pharmasync.model.TxnType;
import com.pharmasync.service.PurchaseService.PurchaseLine;
import com.pharmasync.service.PurchaseService.PurchaseRequest;
import com.pharmasync.service.SalesService.SaleLine;
import com.pharmasync.service.SalesService.SaleRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end checks against a throwaway SQLite database file in the temp folder. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {

    private Path dbFile;
    private final LocalDate today = LocalDate.now();
    private int counter;

    @BeforeAll
    void createDatabase() throws Exception {
        dbFile = Files.createTempDirectory("pharmasync-test").resolve("pharmasync_test.db");
        DatabaseManager.configure(new DbConfig(dbFile));
        SchemaInitializer.initialize(true);
    }

    @AfterAll
    void cleanUp() throws Exception {
        Session.end();
        for (String suffix : new String[] {"", "-wal", "-shm"}) {
            Files.deleteIfExists(dbFile.resolveSibling(dbFile.getFileName() + suffix));
        }
        Files.deleteIfExists(dbFile.getParent());
    }

    @BeforeEach
    void loginAsAdmin() {
        Services.AUTH.login(DemoDataSeeder.DEFAULT_ADMIN, DemoDataSeeder.DEFAULT_ADMIN_PASSWORD);
    }

    @Test
    void demoDataProducesAlerts() {
        assertTrue(Services.STOCK.expiredInStock().size() >= 2, "seeded expired batches");
        assertTrue(Services.STOCK.nearExpiry().size() >= 1, "seeded near-expiry batches");
        assertTrue(Services.STOCK.lowStock().size() >= 1, "seeded low-stock medicines");
        assertTrue(Jdbc.queryLong("SELECT COUNT(*) FROM sales") > 0, "demo sales");
        for (ReportService.Report r : ReportService.Report.values()) {
            Services.REPORTS.run(r, today.minusDays(30), today);
        }
    }

    @Test
    void saleUsesFefoAndSkipsExpiredBatch() {
        int medId = newMedicine();
        receive(medId, 100, "B101", 120);
        receive(medId, 150, "B102", 200);
        receive(medId, 200, "B103", 400);
        int expiredId = Jdbc.inTransaction(() -> Services.BATCH_DAO.insert(medId, "OLD1", today.minusDays(800),
                today.minusDays(1), 500, BigDecimal.ONE, BigDecimal.TEN, 1));

        SalesService.SaleResult sale = Services.SALES.createSale(new SaleRequest(1, "CASH", PaymentStatus.PAID, null,
                List.of(new SaleLine(medId, 50, null, null, null)), null));

        assertEquals(1, sale.items().size());
        assertEquals("B101", sale.items().get(0).batchNo());
        Map<String, Integer> qty = quantities(medId);
        assertEquals(50, qty.get("B101"));
        assertEquals(150, qty.get("B102"));
        assertEquals(500, qty.get("OLD1"), "expired batch untouched");

        // Choosing the expired batch by hand is blocked.
        assertThrows(BusinessException.class, () -> Services.SALES.createSale(new SaleRequest(1, "CASH",
                PaymentStatus.PAID, null, List.of(new SaleLine(medId, 1, expiredId, null, null)), null)));
    }

    @Test
    void failedSaleRollsBackEverything() {
        int medId = newMedicine();
        receive(medId, 30, "R1", 300);
        int otherId = newMedicine();
        receive(otherId, 10, "R2", 300);
        long salesBefore = Jdbc.queryLong("SELECT COUNT(*) FROM sales");

        assertThrows(InsufficientStockException.class, () -> Services.SALES.createSale(new SaleRequest(1, "CASH",
                PaymentStatus.PAID, null, List.of(new SaleLine(medId, 20, null, null, null),
                new SaleLine(otherId, 11, null, null, null)), null)));

        assertEquals(salesBefore, Jdbc.queryLong("SELECT COUNT(*) FROM sales"));
        assertEquals(30, quantities(medId).get("R1"));
    }

    @Test
    void stockMathPurchaseThenSale() {
        int medId = newMedicine();
        receive(medId, 500, "S1", 300);
        receive(medId, 200, "S2", 400);
        Services.SALES.createSale(new SaleRequest(2, "CREDIT", PaymentStatus.UNPAID, null,
                List.of(new SaleLine(medId, 150, null, null, null)), null));
        Medicine m = Services.MEDICINE_DAO.findById(medId).orElseThrow();
        assertEquals(550, m.currentStock());
        long movements = Jdbc.queryLong("SELECT COUNT(*) FROM stock_transactions WHERE medicine_id = ? AND txn_type IN (?, ?)",
                medId, TxnType.PURCHASE_IN, TxnType.SALE_OUT);
        assertEquals(3, movements);
    }

    @Test
    void staffCannotPerformAdminActions() {
        Services.AUTH.logout();
        Services.AUTH.login(DemoDataSeeder.DEFAULT_STAFF, DemoDataSeeder.DEFAULT_STAFF_PASSWORD);
        assertThrows(BusinessException.class, () -> Services.STOCK.quarantineAllExpired());
        assertThrows(BusinessException.class, () -> Services.AUTH.listUsers());
        assertThrows(BusinessException.class,
                () -> Services.REPORTS.run(ReportService.Report.SALES, today, today));
    }

    @Test
    void duplicateNameBecomesBusinessError() {
        assertThrows(BusinessException.class,
                () -> Services.MASTER.saveCategory(new Category(0, "Analgesics", "duplicate")));
    }

    @Test
    void reportsReturnMoneyAsDecimalsAndDatesAsDates() {
        TableData sales = Services.REPORTS.run(ReportService.Report.SALES, today.minusDays(30), today);
        assertTrue(!sales.rows().isEmpty());
        Object[] row = sales.rows().get(0);
        assertInstanceOf(LocalDateTime.class, row[sales.columns().indexOf("Date")]);
        BigDecimal total = (BigDecimal) row[sales.columns().indexOf("Total")];
        assertEquals(2, total.scale());

        TableData expiry = Services.REPORTS.run(ReportService.Report.BATCH_EXPIRY, today, today);
        assertInstanceOf(LocalDate.class, expiry.rows().get(0)[expiry.columns().indexOf("Expiry Date")]);
        assertTrue(expiry.rows().stream().anyMatch(r -> "EXPIRED".equals(r[expiry.columns().indexOf("Expiry Status")])));
        assertTrue(expiry.rows().stream().anyMatch(r -> "NEAR EXPIRY".equals(r[expiry.columns().indexOf("Expiry Status")])));
    }

    @Test
    void partialPaymentsBuildALedger() {
        int medId = newMedicine();
        receive(medId, 100, "P1", 300);
        // 10 x 15.00 + 12% GST = 168.00
        Sale sale = Services.SALES.createSale(new SaleRequest(1, "UPI", PaymentStatus.PARTIAL, new BigDecimal("100"),
                List.of(new SaleLine(medId, 10, null, null, null)), null)).sale();
        assertEquals(new BigDecimal("168.00"), sale.totalAmount());
        assertEquals(new BigDecimal("100.00"), sale.amountPaid());
        assertEquals(new BigDecimal("68.00"), sale.balanceDue());
        assertEquals(PaymentStatus.PARTIAL, sale.paymentStatus());

        assertThrows(BusinessException.class,
                () -> Services.SALES.recordPayment(sale.id(), new BigDecimal("68.01"), "CASH", null, null),
                "cannot pay more than the balance");
        Sale paid = Services.SALES.recordPayment(sale.id(), new BigDecimal("68"), "CHEQUE", "CHQ 123456", null);
        assertEquals(PaymentStatus.PAID, paid.paymentStatus());
        assertEquals(0, paid.balanceDue().signum());

        List<SalePayment> ledger = Services.SALES.payments(sale.id());
        assertEquals(2, ledger.size());
        assertEquals("CHQ 123456", ledger.get(1).reference());
        assertEquals(paid.amountPaid(), ledger.stream().map(SalePayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void partialStatusNeedsAnAmountBelowTheTotal() {
        int medId = newMedicine();
        receive(medId, 100, "P2", 300);
        for (BigDecimal bad : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("168.00")}) {
            assertThrows(BusinessException.class, () -> Services.SALES.createSale(new SaleRequest(1, "CASH",
                    PaymentStatus.PARTIAL, bad, List.of(new SaleLine(medId, 10, null, null, null)), null)));
        }
    }

    @Test
    void placeOfSupplyChoosesIgstOrCgstSgst() {
        int medId = newMedicine();
        receive(medId, 100, "G1", 300);
        Customer local = customer("Telangana Test Pharmacy", IndianState.TELANGANA);
        Customer outOfState = customer("Karnataka Test Pharmacy", IndianState.KARNATAKA);

        Sale intra = Services.SALES.createSale(new SaleRequest(local.id(), "CASH", PaymentStatus.PAID, null,
                List.of(new SaleLine(medId, 1, null, null, null)), null)).sale();
        Sale inter = Services.SALES.createSale(new SaleRequest(outOfState.id(), "CASH", PaymentStatus.PAID, null,
                List.of(new SaleLine(medId, 1, null, null, null)), null)).sale();

        assertEquals(SupplyType.INTRA_STATE, intra.supplyType());
        assertEquals(IndianState.TELANGANA, intra.placeOfSupply());
        assertEquals(SupplyType.INTER_STATE, inter.supplyType());
        assertEquals(IndianState.KARNATAKA, inter.placeOfSupply());
        // Supplier in Andhra Pradesh, company in Telangana: purchase bill carries IGST.
        Purchase p = Services.PURCHASES.createOrder(new PurchaseRequest(2, null, today, null,
                List.of(new PurchaseLine(medId, 5, new BigDecimal("10.00"), null, new BigDecimal("12"), null, null, null))));
        assertEquals(SupplyType.INTER_STATE, p.supplyType());
    }

    @Test
    void customerStateAndGstinMustAgree() {
        assertThrows(BusinessException.class, () -> Services.MASTER.saveCustomer(new Customer(0, "Mismatch Pharmacy",
                CustomerType.PHARMACY, null, null, null, null, IndianState.KARNATAKA, "36AAAFC1001A1Z1", null, true)));
        assertThrows(BusinessException.class, () -> Services.MASTER.saveCustomer(new Customer(0, "No State Pharmacy",
                CustomerType.PHARMACY, null, null, null, null, null, null, null, true)));
        // State is taken from the GSTIN when not chosen.
        Services.MASTER.saveCustomer(new Customer(0, "Gstin State Pharmacy", CustomerType.PHARMACY, null, null, null,
                null, null, "29aaafc1001a1z1", null, true));
        Customer saved = Services.MASTER.searchCustomers("Gstin State Pharmacy", false).get(0);
        assertEquals(IndianState.KARNATAKA, saved.state());
        assertEquals("29AAAFC1001A1Z1", saved.gstin());
    }

    @Test
    void demoCompanyIsFlaggedAsSample() {
        assertTrue(!Services.SETTINGS.isCompanyConfigured(), "shipped company details are demo values");
        assertEquals("", Services.SETTINGS.get(SettingsService.COMPANY_GSTIN));
        assertEquals(IndianState.TELANGANA, Services.SETTINGS.companyState());
    }

    @Test
    void wrongPasswordIsRejected() {
        Services.AUTH.logout();
        assertThrows(BusinessException.class, () -> Services.AUTH.login("admin", "wrong-password"));
    }

    private int newMedicine() {
        String code = "TST" + System.nanoTime() % 1_000_000 + (counter++);
        return Services.MASTER.saveMedicine(new Medicine(0, code, "Test " + code, "Testamol", null, null, null, null,
                "Tablet", "500mg", null, new BigDecimal("10.00"), new BigDecimal("15.00"), new BigDecimal("12"),
                10, null, true, 0));
    }

    private static Customer customer(String name, IndianState state) {
        Services.MASTER.saveCustomer(new Customer(0, name, CustomerType.PHARMACY, null, null, null, null, state,
                null, null, true));
        return Services.MASTER.searchCustomers(name, false).get(0);
    }

    private void receive(int medId, int qty, String batchNo, int expiresInDays) {
        Services.PURCHASES.createAndReceive(new PurchaseRequest(1, null, today, null,
                List.of(new PurchaseLine(medId, qty, new BigDecimal("10.00"), new BigDecimal("18.00"),
                        new BigDecimal("12"), batchNo, today.minusDays(30), today.plusDays(expiresInDays)))), today);
    }

    private static Map<String, Integer> quantities(int medId) {
        return Services.BATCH_DAO.findByMedicine(medId).stream()
                .collect(Collectors.toMap(Batch::batchNo, Batch::quantity));
    }
}
