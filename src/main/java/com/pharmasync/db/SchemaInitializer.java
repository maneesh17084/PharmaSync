package com.pharmasync.db;

import com.pharmasync.service.Services;
import com.pharmasync.service.SettingsService;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the tables on first run, upgrades older databases, and loads master and demo data into an empty one. */
public final class SchemaInitializer {

    /**
     * Company details shipped by earlier versions (when the app was called MedStock), as {key, old value, new value};
     * replaced by the current labelled demo values if never edited.
     */
    private static final String[][] OLD_SAMPLE_SETTINGS = {
            {"company_name", "MedStock Wholesale Pharma Distributors", SettingsService.DEMO_COMPANY_NAME},
            {"company_name", "MedStock Demo Pharma (SAMPLE COMPANY)", SettingsService.DEMO_COMPANY_NAME},
            {"company_address", "12 Industrial Estate, Hyderabad, Telangana 500032",
                    "Demo address, Telangana - replace in Settings"},
            {"company_phone", "+91 40 2345 6789", ""},
            {"company_gstin", "36ABCDE1234F1Z5", ""},
            {"company_license", "DL-TS-20B-123456 / 21B-123457", ""}};

    private SchemaInitializer() {
    }

    /** Returns true when a fresh database was created and seeded. */
    public static boolean initialize(boolean withDemoData) throws SQLException, IOException {
        try (Connection c = DatabaseManager.getConnection()) {
            SqlScriptRunner.run(c, "/db/schema.sql");
            upgrade(c);
        }
        if (Services.USER_DAO.count() > 0) {
            return false;
        }
        try (Connection c = DatabaseManager.getConnection()) {
            SqlScriptRunner.run(c, "/db/seed.sql");
        }
        DemoDataSeeder.seed(withDemoData);
        return true;
    }

    /**
     * Brings a database created by an earlier version up to date: GST state and supply type columns,
     * the customer payment ledger, and labelled demo company details. Each step runs only once.
     */
    static void upgrade(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            for (String[] old : OLD_SAMPLE_SETTINGS) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE settings SET setting_value = ? WHERE setting_key = ? AND setting_value = ?")) {
                    ps.setString(1, old[2]);
                    ps.setString(2, old[0]);
                    ps.setString(3, old[1]);
                    ps.executeUpdate();
                }
            }
            // A real company GSTIN already entered decides the company's state.
            st.executeUpdate("UPDATE settings SET setting_value = (SELECT substr(setting_value, 1, 2) FROM settings "
                    + "WHERE setting_key = 'company_gstin') WHERE setting_key = 'company_state' AND EXISTS (SELECT 1 "
                    + "FROM settings WHERE setting_key = 'company_gstin' AND setting_value GLOB '[0-9][0-9]*')");

            for (String table : new String[]{"customers", "suppliers"}) {
                if (addColumn(c, table, "state", "TEXT NULL")) {
                    st.executeUpdate("UPDATE " + table + " SET state = substr(gstin, 1, 2) WHERE gstin GLOB '[0-9][0-9]*'");
                }
            }

            String companyState = "(SELECT setting_value FROM settings WHERE setting_key = 'company_state')";
            if (addColumn(c, "purchases", "supply_type", "TEXT NOT NULL DEFAULT 'INTRA_STATE' "
                    + "CHECK (supply_type IN ('INTRA_STATE','INTER_STATE'))")) {
                st.executeUpdate("UPDATE purchases SET supply_type = 'INTER_STATE' WHERE (SELECT s.state FROM suppliers s "
                        + "WHERE s.id = purchases.supplier_id) <> " + companyState);
            }
            if (addColumn(c, "sales", "place_of_supply", "TEXT NULL")) {
                st.executeUpdate("UPDATE sales SET place_of_supply = (SELECT cu.state FROM customers cu "
                        + "WHERE cu.id = sales.customer_id)");
            }
            if (addColumn(c, "sales", "supply_type", "TEXT NOT NULL DEFAULT 'INTRA_STATE' "
                    + "CHECK (supply_type IN ('INTRA_STATE','INTER_STATE'))")) {
                st.executeUpdate("UPDATE sales SET supply_type = 'INTER_STATE' WHERE place_of_supply <> " + companyState);
            }
            if (addColumn(c, "sales", "amount_paid", "REAL NOT NULL DEFAULT 0")) {
                // Invoices marked PAID become fully paid, with one ledger entry so the history adds up.
                st.executeUpdate("UPDATE sales SET amount_paid = total_amount WHERE payment_status = 'PAID'");
                st.executeUpdate("INSERT INTO sale_payments (sale_id, payment_date, amount, mode, reference, user_id) "
                        + "SELECT id, date(sale_date), total_amount, CASE WHEN payment_mode = 'CREDIT' THEN 'CASH' "
                        + "ELSE payment_mode END, 'Before payment ledger', created_by FROM sales "
                        + "WHERE payment_status = 'PAID' AND total_amount > 0");
                // Earlier PARTIAL invoices never recorded how much was paid: treat them as unpaid until recorded.
                st.executeUpdate("UPDATE sales SET payment_status = 'UNPAID' WHERE payment_status = 'PARTIAL'");
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Adds the column when the table lacks it. Returns true when it was added. */
    private static boolean addColumn(Connection c, String table, String column, String definition) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return false;
                }
            }
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
        return true;
    }
}
