# PharmaSync — Wholesale Medical Store Inventory & Stock Management System

Java 21 · Swing (FlatLaf) · JDBC · SQLite · JFreeChart · OpenPDF · JUnit 5

A desktop application for a wholesale pharmaceutical distributor: medicine master data, batch-wise stock with
manufacturing/expiry dates, FEFO (First Expiry, First Out) dispatch, supplier purchases, retailer sales with GST
invoices, low-stock / near-expiry / expired alerts, reports, role-based access and a full audit trail.

**Download:** the latest build is on the [Releases page](https://github.com/maneesh17084/PharmaSync/releases/latest)
(`pharmasync.jar`, run with `java -jar pharmasync.jar`, Java 21 required).

### Website (Vercel) and releases

PharmaSync is a desktop (Swing) application, so it cannot run on Vercel, which hosts websites. The `public/`
folder is the product website: features, screenshots, setup steps and a Download button. `vercel.json` tells
Vercel to serve that folder as a static site with no build step, so the Java project is ignored there. Every push
to `main` redeploys it.

The Download button points at the latest GitHub Release. To publish a new version, push a tag:

```bat
git tag v1.0.1
git push origin v1.0.1
```

`.github/workflows/release.yml` then builds and tests the app and attaches `pharmasync.jar` to a release for that
tag. `.github/workflows/build.yml` builds and tests every push to `main`.

---

## 1. Running it

**Requirements:** JDK 21 and the bundled Maven Wrapper (`mvnw.cmd`); a separate Maven install is not needed.
Nothing else: the database is an SQLite file created by the app.

```bat
mvnw.cmd package            :: compiles, runs tests, builds target\pharmasync.jar
java -jar target\pharmasync.jar
```

In IntelliJ IDEA: *File → Open* → select `pom.xml`, then run `com.pharmasync.App`.

### First start
1. The app creates the database file `data\pharmasync.db` (relative to the working directory) and asks whether to load
   demo data: 15 medicines, 3 suppliers (Telangana, Andhra Pradesh, Maharashtra), 6 customers (five in Telangana,
   one in Andhra Pradesh), batches with expiry dates relative to today, 4 purchases and about a month of sales,
   some of them part-paid. Answer *No* to start with an empty store; the two user accounts are still created.
2. Sign in with **`admin` / `admin123`** (administrator) or **`staff` / `staff123`** (staff). You must choose a new
   password the first time.
3. **Before printing real invoices**, open *Settings* and enter your company name, address, state, GSTIN and drug
   licence. The database ships with clearly labelled demo company details (*PharmaSync Demo Pharma (SAMPLE COMPANY)*,
   no GSTIN), and until a real name and GSTIN are saved every PDF is stamped
   **"SAMPLE - NOT A VALID TAX INVOICE"**. Customer and supplier GSTINs in the demo data are made up as well.

Databases created by an earlier version are upgraded automatically on start: new columns are added, invoices that
were marked PAID get a matching payment entry, and unchanged old sample company details are replaced by the
labelled demo values.

The app was previously called *MedStock*. A database from that version (`data\medstore.db`) is renamed to
`data\pharmasync.db` on first start, and the old `-Dmedstore.db.file` option is still accepted.

To use a different file, start with `java -Dpharmasync.db.file=D:\shop\pharmasync.db -jar target\pharmasync.jar`.
**Back up** by copying `data\pharmasync.db` while the app is closed. To start over, close the app and delete the
`data` folder.

The scripts `src/main/resources/db/schema.sql` and `seed.sql` can also be run by hand, e.g. in DB Browser for SQLite.

---

## 2. Modules

| Screen | Who | What it does |
|---|---|---|
| Dashboard | all | KPI tiles (stock value, sales, low stock, near expiry, expired, receivables), 14-day sales chart, top sellers |
| Stock Alerts | all (actions: admin) | Low stock with suggested order quantity and a one-click PO; near-expiry; expired with quarantine / return / dispose |
| New Sales Order | all | Retailer order entry, live FEFO batch suggestion, rejects or trims unavailable quantities, GST bill, PDF invoice |
| Invoices | all | Sales history with paid amount and balance, batch-wise items, re-print PDF, record customer payments (amount, mode, reference, date), payment history |
| Customers | all | Register pharmacies, hospitals and clinics, with state (place of supply) and GSTIN |
| Medicines | all (edit: admin) | Brand, generic, composition, manufacturer, category, dosage, prices, GST, reorder level; view batches in FEFO order |
| Batches & Expiry | all (actions: admin) | Every batch, colour-coded red/amber/grey; quarantine, release, return, dispose, adjust |
| Categories | admin | Medicine categories |
| Purchases | all (cancel/pay: admin) | Purchase orders → receive with batch/mfg/expiry; direct shipment entry; supplier payments; GRN PDF |
| Suppliers | admin | Supplier master with state, GSTIN and drug licence |
| Reports | admin | 12 reports with CSV export and charts (see below) |
| Users | admin | Create users, change role, disable, reset password |
| Audit Log | admin | User activity (including failed logins) and the stock movement ledger |
| Settings | admin | Near-expiry threshold (default 90 days) and company details printed on invoices, including the company's GST state |

**Reports:** current inventory · inventory valuation by category · batch-wise expiry · purchase report ·
supplier-wise purchases · sales report (with CGST/SGST/IGST, paid and balance) · daily sales summary ·
customer-wise sales and outstanding · customer payments received · fast-moving medicines ·
slow-moving medicines · stock movement history.

---

## 3. Key business rules

* **Batch-wise stock.** A medicine (`medicines`) has many batches (`batches`), each with its own batch number,
  mfg date, expiry date, quantity, cost and MRP. `UNIQUE(medicine_id, batch_no)`, `CHECK(expiry_date > mfg_date)`
  and `CHECK(quantity >= 0)` are enforced by the database.
* **FEFO** (`FefoAllocator`). Sellable batches (ACTIVE, quantity > 0, not expired) are sorted by expiry date and
  consumed in that order. When one batch can't cover the quantity, the order spills over to the next.
  Example: B101 (Mar 2027) / B102 (Jun 2027) / B103 (Dec 2027), 50 units → all from B101.
* **Expiry classification** (`ExpiryService`). *Expired* = expiry date has passed. *Near expiry* = within the
  configurable threshold. *Valid* = otherwise. Expired batches are excluded from FEFO, rejected if a user picks
  one by hand, and cannot be received on a purchase.
* **Expired stock handling.** Quarantine (blocked, quantity kept), Return to supplier, or Dispose. The last two zero
  the batch and write a stock movement.
* **Automatic stock updates.** Every purchase receipt, sale, adjustment, return and disposal updates the batch and
  writes a `stock_transactions` row with the resulting balance: 500 + 200 − 150 = 550.
* **Transactions.** Purchase receipt and sale confirmation each run in one JDBC transaction. Transactions start with
  `BEGIN IMMEDIATE`, so they hold SQLite's write lock from the start and stock cannot change underneath them. Any
  failure, such as a later line in the order being short, rolls back the whole order.
* **Storage.** Dates are stored as ISO text (`2027-03-31`, `2027-03-31 14:05:00`). Money is stored as `REAL` and
  rounded back to 2-decimal `BigDecimal` when read, so invoice maths stays exact. Foreign keys are enforced on
  every connection.
* **Invoice maths** (`InvoiceCalculator`). gross = qty × rate, discount on gross, GST on (gross − discount), all
  rounded half-up to 2 decimals.
* **GST: CGST + SGST or IGST** (`Gst`). Customers, suppliers and the company each have a state (GST state code).
  The place of supply of a sale is the customer's state. Same state as the company: intra-state, split into CGST and
  SGST (UTGST for union territories without a legislature), CGST being half the tax rounded half-up and SGST the rest.
  Different state: inter-state, all IGST. Purchases compare the supplier's state with the company's. Place of supply
  and supply type are stored on each invoice and purchase, so reprints stay correct if a customer later moves.
  A GSTIN must be 15 characters and its first two digits must match the selected state; if no state is chosen it
  is taken from the GSTIN.
* **Customer payments.** Each invoice keeps `amount_paid`; every receipt is a row in the `sale_payments` ledger
  (date, amount, mode, cheque/UTR reference, user). At billing, PAID records the full total, UNPAID nothing and
  PARTIAL the amount entered. Later receipts are added with *Record payment*. A payment cannot exceed the balance
  due, and the status (UNPAID / PARTIAL / PAID) always follows from the amounts. The dashboard's receivables are the
  sum of balances due.
* **Security.** BCrypt password hashes, forced password change for new and reset accounts, role checks in the service
  layer (not only hidden buttons), and audit entries for logins, failed logins and every change.

---

## 4. Architecture

```
com.pharmasync
├── App                     entry point: open/create SQLite file → schema init → login
├── config/DbConfig         database file location (data/pharmasync.db)
├── db/                     Jdbc helper (thread-bound transactions), DatabaseManager,
│                           SchemaInitializer, SqlScriptRunner, DemoDataSeeder
├── model/                  records + enums (Medicine, Batch, Sale, Purchase, …)
├── dao/                    one DAO per table, plain JDBC with PreparedStatements
├── service/                business rules: Auth, MasterData, Purchase, Sales, Stock,
│                           Expiry, FefoAllocator, InvoiceCalculator, Report, Dashboard,
│                           InvoicePdfGenerator, Session, Services (wiring)
└── ui/                     Swing: LoginFrame, MainFrame, panels/, dialogs/, helpers
                            (Theme, Icons, Card, StyledTable, UiUtil = the design system)
```

**Look and feel.** FlatLaf with a teal accent, a dark navy sidebar and light or dark mode (toggle in the top bar;
the choice is remembered). Colours, rounding and table styling live in
`src/main/resources/com/pharmasync/themes/*.properties`, including the `PharmaSync.*` palette for cards, the sidebar,
alert-row tints and status pills. Icons are SVGs in `src/main/resources/com/pharmasync/icons`, recoloured to the
theme. `UiUtil.button` picks a button's icon from its label and draws each screen's main action as a filled
accent button. `StyledTable` formats money and dates, shows status values (Paid, Expired, Near expiry...) as
coloured pills, stripes rows and sizes columns to their content.

**Logo.** The original artwork is `branding/pharmasync-logo.png`. `branding/make_logo.py` (Python + Pillow + numpy)
turns it into the PNGs in `src/main/resources/com/pharmasync/brand`: `logo-mark-*` (emblem and wordmark with the
dark tile removed, so it blends into the sign-in panel's gradient) and `app-*` (emblem only on a rounded tile, used for window/taskbar icons, the sidebar and the splash, where
the wordmark would be too small to read). After changing the artwork, run
`python branding/make_logo.py branding/pharmasync-logo.png src/main/resources/com/pharmasync/brand` and rebuild.

Database tables: `users, categories, medicines, batches, suppliers, purchases, purchase_items, customers, sales,
sales_items, sale_payments, stock_transactions, audit_logs, settings`.

Generated PDFs go to `invoices/` in the working directory.

---

## 5. Tests

```bat
mvnw.cmd test
```

* **Unit tests (always run):** FEFO allocation (including the specification example, spill-over, and skipping
  expired, quarantined and empty batches), expiry classification and boundaries, stock arithmetic, invoice/GST
  rounding, GST rules (CGST/SGST vs IGST split, GSTIN format and state check, UTGST), password hashing, document
  numbering, purchase batch validation, and the SQL script splitter.
* **`SchemaUpgradeTest`:** upgrades a database in the previous layout and checks the new columns, backfilled states
  and supply types, payment ledger entries and replaced sample company details.
* **`DatabaseIntegrationTest` (always runs):** builds a throwaway SQLite database in the temp folder, seeds it,
  runs every report, checks FEFO dispatch against real rows, rollback of a failed multi-line order, the
  500 + 200 − 150 = 550 stock flow, duplicate-name handling, report value types, partial payments and the payment
  ledger (including overpayment rejection), IGST vs CGST/SGST by place of supply for sales and purchases, GSTIN/state
  validation, the demo-company flag, staff permission denials and wrong-password rejection, then deletes the file.

---

## 6. Requirement coverage

| ID | Requirement | Where |
|---|---|---|
| FR-01 | Secure login | `AuthService`, `PasswordHasher` (BCrypt), `LoginFrame` |
| FR-02 | Admin / staff roles | `Session.requireAdmin`, `MainFrame` menu filtering, `UsersPanel` |
| FR-03 | Medicine management | `MedicinePanel`, `MasterDataService` |
| FR-04 | Categories | `CategoryPanel` |
| FR-05 | Batch numbers | `batches` table, `BatchPanel` |
| FR-06 | Mfg / expiry tracking | `PurchaseService.validateBatchInfo`, `ExpiryService` |
| FR-07 | Suppliers | `SupplierPanel` |
| FR-08 | Purchase orders | `PurchaseDialog`, `PurchaseService.createOrder` |
| FR-09 | Batch-wise stock-in | `ReceiveDialog`, `PurchaseService.receive` |
| FR-10 | Customers | `CustomerPanel` |
| FR-11 | Sales orders | `SalesPanel`, `SalesService.createSale` |
| FR-12 | FEFO | `FefoAllocator` |
| FR-13 | Automatic stock updates | `PurchaseService`, `SalesService`, `StockService` + `stock_transactions` |
| FR-14 | Low-stock alerts | `AlertsPanel`, `MedicineDao.findLowStock` |
| FR-15 | Near-expiry / expired alerts | `AlertsPanel`, `StockService.nearExpiry/expiredInStock` |
| FR-16 | Invoices | `InvoicePdfGenerator`, `Gst` (CGST/SGST or IGST by place of supply) |
| FR-17 | Purchase & sales history | `PurchasePanel`, `SalesHistoryPanel` |
| FR-18 | Inventory & financial reports | `ReportService`, `ReportsPanel` |
| FR-19 | Audit logs & transaction tracking | `AuditService`, `AuditPanel` |

## 7. Possible extensions
Barcode scanning, a shared server database (e.g. PostgreSQL/MySQL) for several counters, GST e-invoicing (IRN/QR code), a place-of-supply override for goods shipped to a different address, credit notes
for returns, email alerts, multi-branch stock, online retailer ordering, and a supplier payment ledger like the
customer one.

