-- PharmaSync schema (SQLite). Idempotent: safe to run on an existing database.
-- Statements are separated by semicolons at end of line (no semicolons inside literals).
-- Dates are ISO text ('yyyy-MM-dd', 'yyyy-MM-dd HH:mm:ss'); money is REAL, rounded to 2 decimals when read.

CREATE TABLE IF NOT EXISTS users (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
    username             VARCHAR(50)  NOT NULL UNIQUE,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(100) NOT NULL,
    role                 TEXT NOT NULL DEFAULT 'STAFF' CHECK (role IN ('ADMIN','STAFF')),
    active               BOOLEAN NOT NULL DEFAULT 1,
    must_change_password BOOLEAN NOT NULL DEFAULT 0,
    last_login           DATETIME NULL,
    created_at           DATETIME NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS categories (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS medicines (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    code              VARCHAR(30)  NOT NULL UNIQUE,
    brand_name        VARCHAR(120) NOT NULL,
    generic_name      VARCHAR(120) NOT NULL,
    composition       VARCHAR(255),
    manufacturer      VARCHAR(120),
    category_id       INTEGER NULL,
    dosage_form       VARCHAR(40),
    strength          VARCHAR(40),
    pack_size         VARCHAR(40),
    purchase_price    REAL NOT NULL DEFAULT 0,
    selling_price     REAL NOT NULL DEFAULT 0,
    gst_rate          REAL NOT NULL DEFAULT 12.00,
    reorder_level     INTEGER NOT NULL DEFAULT 100,
    storage_condition VARCHAR(80),
    active            BOOLEAN NOT NULL DEFAULT 1,
    created_at        DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_med_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT chk_med_prices CHECK (purchase_price >= 0 AND selling_price >= 0 AND gst_rate >= 0),
    CONSTRAINT chk_med_reorder CHECK (reorder_level >= 0)
);

CREATE INDEX IF NOT EXISTS idx_med_names ON medicines (brand_name, generic_name);

CREATE TABLE IF NOT EXISTS suppliers (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(150) NOT NULL UNIQUE,
    contact_person  VARCHAR(100),
    phone           VARCHAR(30),
    email           VARCHAR(120),
    address         VARCHAR(255),
    state           TEXT NULL,
    gstin           VARCHAR(20),
    drug_license_no VARCHAR(50),
    active          BOOLEAN NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS customers (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(150) NOT NULL UNIQUE,
    customer_type   TEXT NOT NULL DEFAULT 'PHARMACY' CHECK (customer_type IN ('PHARMACY','HOSPITAL','CLINIC','OTHER')),
    contact_person  VARCHAR(100),
    phone           VARCHAR(30),
    email           VARCHAR(120),
    address         VARCHAR(255),
    state           TEXT NULL,
    gstin           VARCHAR(20),
    drug_license_no VARCHAR(50),
    active          BOOLEAN NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS batches (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id    INTEGER NOT NULL,
    batch_no       VARCHAR(40) NOT NULL,
    mfg_date       DATE NOT NULL,
    expiry_date    DATE NOT NULL,
    quantity       INTEGER NOT NULL DEFAULT 0,
    purchase_price REAL NOT NULL DEFAULT 0,
    mrp            REAL NOT NULL DEFAULT 0,
    supplier_id    INTEGER NULL,
    status         TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','QUARANTINED','RETURNED','DISPOSED')),
    received_at    DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_batch_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id),
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE SET NULL,
    CONSTRAINT uq_batch UNIQUE (medicine_id, batch_no),
    CONSTRAINT chk_batch_dates CHECK (expiry_date > mfg_date),
    CONSTRAINT chk_batch_qty CHECK (quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_batch_fefo ON batches (medicine_id, status, expiry_date);
CREATE INDEX IF NOT EXISTS idx_batch_expiry ON batches (expiry_date);

CREATE TABLE IF NOT EXISTS purchases (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_no    VARCHAR(30) NOT NULL UNIQUE,
    supplier_id    INTEGER NOT NULL,
    supplier_invoice_ref VARCHAR(50),
    order_date     DATE NOT NULL,
    received_date  DATE NULL,
    status         TEXT NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED','RECEIVED','CANCELLED')),
    subtotal       REAL NOT NULL DEFAULT 0,
    tax_amount     REAL NOT NULL DEFAULT 0,
    total_amount   REAL NOT NULL DEFAULT 0,
    payment_status TEXT NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PARTIAL','PAID')),
    amount_paid    REAL NOT NULL DEFAULT 0,
    supply_type    TEXT NOT NULL DEFAULT 'INTRA_STATE' CHECK (supply_type IN ('INTRA_STATE','INTER_STATE')),
    notes          VARCHAR(255),
    created_by     INTEGER NULL,
    created_at     DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_purchase_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_purchase_date ON purchases (order_date);

CREATE TABLE IF NOT EXISTS purchase_items (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_id INTEGER NOT NULL,
    medicine_id INTEGER NOT NULL,
    batch_id    INTEGER NULL,
    batch_no    VARCHAR(40),
    mfg_date    DATE NULL,
    expiry_date DATE NULL,
    quantity    INTEGER NOT NULL,
    unit_cost   REAL NOT NULL,
    mrp         REAL NOT NULL DEFAULT 0,
    gst_rate    REAL NOT NULL DEFAULT 0,
    line_total  REAL NOT NULL DEFAULT 0,
    CONSTRAINT fk_pi_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id) ON DELETE CASCADE,
    CONSTRAINT fk_pi_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id),
    CONSTRAINT fk_pi_batch FOREIGN KEY (batch_id) REFERENCES batches(id),
    CONSTRAINT chk_pi_qty CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS sales (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_no      VARCHAR(30) NOT NULL UNIQUE,
    customer_id     INTEGER NOT NULL,
    sale_date       DATETIME NOT NULL,
    subtotal        REAL NOT NULL DEFAULT 0,
    discount_amount REAL NOT NULL DEFAULT 0,
    tax_amount      REAL NOT NULL DEFAULT 0,
    total_amount    REAL NOT NULL DEFAULT 0,
    payment_mode    VARCHAR(20) NOT NULL DEFAULT 'CREDIT',
    payment_status  TEXT NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PARTIAL','PAID')),
    amount_paid     REAL NOT NULL DEFAULT 0,
    place_of_supply TEXT NULL,
    supply_type     TEXT NOT NULL DEFAULT 'INTRA_STATE' CHECK (supply_type IN ('INTRA_STATE','INTER_STATE')),
    created_by      INTEGER NULL,
    created_at      DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_sale_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_sale_paid CHECK (amount_paid >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sale_date ON sales (sale_date);

-- Customer payment ledger: every amount received against an invoice. sales.amount_paid is the running sum.
CREATE TABLE IF NOT EXISTS sale_payments (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id      INTEGER NOT NULL,
    payment_date DATE NOT NULL,
    amount       REAL NOT NULL,
    mode         VARCHAR(20) NOT NULL,
    reference    VARCHAR(60),
    user_id      INTEGER NULL,
    created_at   DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_sp_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_sp_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_sp_sale ON sale_payments (sale_id);
CREATE INDEX IF NOT EXISTS idx_sp_date ON sale_payments (payment_date);

CREATE TABLE IF NOT EXISTS sales_items (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id      INTEGER NOT NULL,
    medicine_id  INTEGER NOT NULL,
    batch_id     INTEGER NOT NULL,
    quantity     INTEGER NOT NULL,
    unit_price   REAL NOT NULL,
    discount_pct REAL NOT NULL DEFAULT 0,
    gst_rate     REAL NOT NULL DEFAULT 0,
    tax_amount   REAL NOT NULL DEFAULT 0,
    line_total   REAL NOT NULL DEFAULT 0,
    CONSTRAINT fk_si_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_si_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id),
    CONSTRAINT fk_si_batch FOREIGN KEY (batch_id) REFERENCES batches(id),
    CONSTRAINT chk_si_qty CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS stock_transactions (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_id       INTEGER NOT NULL,
    medicine_id    INTEGER NOT NULL,
    txn_type       TEXT NOT NULL CHECK (txn_type IN ('OPENING','PURCHASE_IN','SALE_OUT','ADJUSTMENT','QUARANTINE','RETURN','DISPOSAL')),
    quantity       INTEGER NOT NULL,
    balance_after  INTEGER NOT NULL,
    reference_type VARCHAR(20),
    reference_no   VARCHAR(30),
    remarks        VARCHAR(255),
    user_id        INTEGER NULL,
    created_at     DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_st_batch FOREIGN KEY (batch_id) REFERENCES batches(id),
    CONSTRAINT fk_st_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id),
    CONSTRAINT fk_st_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_st_medicine ON stock_transactions (medicine_id, created_at);
CREATE INDEX IF NOT EXISTS idx_st_created ON stock_transactions (created_at);

CREATE TABLE IF NOT EXISTS audit_logs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NULL,
    username   VARCHAR(50),
    action     VARCHAR(40) NOT NULL,
    entity     VARCHAR(40),
    entity_id  VARCHAR(40),
    details    VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT (datetime('now','localtime')),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs (created_at);

CREATE TABLE IF NOT EXISTS settings (
    setting_key   VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);

-- Demo company details, deliberately labelled as samples. Settings reminds the user to replace them
-- with the real company name and GSTIN before printing invoices.
INSERT OR IGNORE INTO settings (setting_key, setting_value) VALUES
    ('near_expiry_days', '90'),
    ('company_name', 'PharmaSync Demo Pharma (SAMPLE COMPANY)'),
    ('company_address', 'Demo address, Telangana - replace in Settings'),
    ('company_phone', ''),
    ('company_gstin', ''),
    ('company_license', ''),
    ('company_state', '36');

