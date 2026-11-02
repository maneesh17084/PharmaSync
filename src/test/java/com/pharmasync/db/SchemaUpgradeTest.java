package com.pharmasync.db;

import com.pharmasync.config.DbConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A database created before the payment ledger and GST states existed is upgraded in place. */
class SchemaUpgradeTest {

    @TempDir
    Path dir;

    @Test
    void oldDatabaseGetsPaymentsStatesAndDemoCompany() throws Exception {
        DbConfig previous = DatabaseManager.config();
        DatabaseManager.configure(new DbConfig(dir.resolve("old.db")));
        try (Connection c = DatabaseManager.getConnection(); Statement st = c.createStatement()) {
            // The relevant parts of the previous schema, with data.
            st.executeUpdate("CREATE TABLE settings (setting_key VARCHAR(50) PRIMARY KEY, setting_value VARCHAR(255))");
            st.executeUpdate("INSERT INTO settings VALUES ('company_name', 'MedStock Wholesale Pharma Distributors'), "
                    + "('company_gstin', '36ABCDE1234F1Z5'), ('company_state', '36')");
            st.executeUpdate("CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, gstin TEXT)");
            st.executeUpdate("INSERT INTO customers VALUES (1, 'Local', '36AAAFC1001A1Z1'), (2, 'Mumbai', '27AAAFC1001A1Z1'), "
                    + "(3, 'No GSTIN', NULL)");
            st.executeUpdate("CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT, gstin TEXT)");
            st.executeUpdate("INSERT INTO suppliers VALUES (1, 'AP Supplier', '37AAACS2222B1Z2')");
            st.executeUpdate("CREATE TABLE purchases (id INTEGER PRIMARY KEY, supplier_id INTEGER)");
            st.executeUpdate("INSERT INTO purchases VALUES (1, 1)");
            st.executeUpdate("CREATE TABLE sales (id INTEGER PRIMARY KEY, customer_id INTEGER, sale_date DATETIME, "
                    + "total_amount REAL, payment_mode TEXT, payment_status TEXT, created_by INTEGER)");
            st.executeUpdate("INSERT INTO sales VALUES (1, 1, '2026-10-01 10:00:00', 168.0, 'NEFT', 'PAID', NULL), "
                    + "(2, 2, '2026-10-02 10:00:00', 50.0, 'CREDIT', 'UNPAID', NULL), "
                    + "(3, 1, '2026-10-03 10:00:00', 70.0, 'CREDIT', 'PARTIAL', NULL)");
            st.executeUpdate("CREATE TABLE sale_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER, "
                    + "payment_date DATE, amount REAL, mode TEXT, reference TEXT, user_id INTEGER)");

            SchemaInitializer.upgrade(c);
            SchemaInitializer.upgrade(c); // running twice changes nothing

            assertEquals("PharmaSync Demo Pharma (SAMPLE COMPANY)", text(st, "SELECT setting_value FROM settings WHERE setting_key = 'company_name'"));
            assertEquals("", text(st, "SELECT setting_value FROM settings WHERE setting_key = 'company_gstin'"));
            assertEquals("36,27,null", text(st, "SELECT group_concat(COALESCE(state, 'null')) FROM (SELECT state FROM customers ORDER BY id)"));
            assertEquals("INTER_STATE", text(st, "SELECT supply_type FROM purchases"));
            assertEquals("INTRA_STATE:36,INTER_STATE:27,INTRA_STATE:36", text(st,
                    "SELECT group_concat(supply_type || ':' || place_of_supply) FROM (SELECT * FROM sales ORDER BY id)"));
            assertEquals("168.0,0.0,0.0", text(st, "SELECT group_concat(amount_paid) FROM (SELECT * FROM sales ORDER BY id)"));
            assertEquals("PAID,UNPAID,UNPAID", text(st, "SELECT group_concat(payment_status) FROM (SELECT * FROM sales ORDER BY id)"));
            assertEquals("1:168.0:NEFT", text(st, "SELECT group_concat(sale_id || ':' || amount || ':' || mode) FROM sale_payments"));
        } finally {
            DatabaseManager.configure(previous);
        }
    }

    private static String text(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
