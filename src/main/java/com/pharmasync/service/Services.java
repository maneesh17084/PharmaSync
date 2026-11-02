package com.pharmasync.service;

import com.pharmasync.dao.AuditDao;
import com.pharmasync.dao.BatchDao;
import com.pharmasync.dao.CategoryDao;
import com.pharmasync.dao.CustomerDao;
import com.pharmasync.dao.MedicineDao;
import com.pharmasync.dao.PurchaseDao;
import com.pharmasync.dao.SaleDao;
import com.pharmasync.dao.SettingsDao;
import com.pharmasync.dao.StockTransactionDao;
import com.pharmasync.dao.SupplierDao;
import com.pharmasync.dao.UserDao;

/** Wires DAOs and services together. The DAOs are stateless, so single shared instances are enough. */
public final class Services {

    public static final SettingsDao SETTINGS_DAO = new SettingsDao();
    public static final UserDao USER_DAO = new UserDao();
    public static final MedicineDao MEDICINE_DAO = new MedicineDao();
    public static final BatchDao BATCH_DAO = new BatchDao();
    public static final StockTransactionDao STOCK_TXN_DAO = new StockTransactionDao();
    public static final AuditDao AUDIT_DAO = new AuditDao();

    public static final AuditService AUDIT = new AuditService(AUDIT_DAO);
    public static final ExpiryService EXPIRY = new ExpiryService(SETTINGS_DAO);
    public static final SettingsService SETTINGS = new SettingsService(SETTINGS_DAO, AUDIT);
    public static final AuthService AUTH = new AuthService(USER_DAO, AUDIT_DAO, AUDIT);
    public static final MasterDataService MASTER = new MasterDataService(MEDICINE_DAO, new CategoryDao(),
            new SupplierDao(), new CustomerDao(), AUDIT);
    public static final StockService STOCK = new StockService(BATCH_DAO, MEDICINE_DAO, STOCK_TXN_DAO, EXPIRY, AUDIT);
    public static final PurchaseService PURCHASES = new PurchaseService(new PurchaseDao(), BATCH_DAO, MEDICINE_DAO,
            new SupplierDao(), STOCK_TXN_DAO, SETTINGS, AUDIT);
    public static final SalesService SALES = new SalesService(new SaleDao(), BATCH_DAO, MEDICINE_DAO, new CustomerDao(),
            STOCK_TXN_DAO, SETTINGS, AUDIT);

    public static final ReportService REPORTS = new ReportService(EXPIRY, AUDIT);
    public static final DashboardService DASHBOARD = new DashboardService(STOCK);
    public static final InvoicePdfGenerator PDF = new InvoicePdfGenerator(SETTINGS);

    private Services() {
    }
}
