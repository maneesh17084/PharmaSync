package com.pharmasync.service;

import com.pharmasync.db.Jdbc;
import com.pharmasync.db.TableData;

import java.time.LocalDate;

/** Inventory, purchase, sales and expiry reports (FR-17, FR-18). */
public class ReportService {

    /** chartLabelCol / chartValueCol pick the columns for the report's bar chart (-1 = no chart). */
    public enum Report {
        CURRENT_INVENTORY("Current inventory", false, 1, 4),
        INVENTORY_VALUATION("Inventory valuation by category", false, 0, 3),
        BATCH_EXPIRY("Batch-wise expiry report", false, -1, -1),
        PURCHASES("Purchase report", true, -1, -1),
        SUPPLIER_PURCHASES("Supplier-wise purchases", true, 0, 3),
        SALES("Sales report", true, -1, -1),
        SALES_BY_DAY("Daily sales summary", true, 0, 2),
        CUSTOMER_SALES("Customer-wise sales", true, 0, 3),
        CUSTOMER_PAYMENTS("Customer payments received", true, -1, -1),
        FAST_MOVING("Fast-moving medicines", true, 0, 2),
        SLOW_MOVING("Slow-moving medicines", true, 0, 3),
        STOCK_MOVEMENTS("Stock movement history", true, -1, -1);

        private final String title;
        private final boolean usesDateRange;
        private final int chartLabelCol;
        private final int chartValueCol;

        Report(String title, boolean usesDateRange, int chartLabelCol, int chartValueCol) {
            this.title = title;
            this.usesDateRange = usesDateRange;
            this.chartLabelCol = chartLabelCol;
            this.chartValueCol = chartValueCol;
        }

        public String title() { return title; }
        public boolean usesDateRange() { return usesDateRange; }
        public int chartLabelCol() { return chartLabelCol; }
        public int chartValueCol() { return chartValueCol; }

        @Override
        public String toString() {
            return title;
        }
    }

    private final ExpiryService expiry;
    private final AuditService audit;

    public ReportService(ExpiryService expiry, AuditService audit) {
        this.expiry = expiry;
        this.audit = audit;
    }

    public TableData run(Report report, LocalDate from, LocalDate to) {
        Session.requireAdmin();
        LocalDate end = to.plusDays(1);
        TableData data = switch (report) {
            case CURRENT_INVENTORY -> Jdbc.queryTable("""
                    SELECT m.code AS `Code`, m.brand_name AS `Medicine`, m.generic_name AS `Generic`, c.name AS `Category`,
                      COALESCE(SUM(CASE WHEN b.status = 'ACTIVE' AND b.expiry_date >= date('now','localtime') THEN b.quantity END), 0) AS `Sellable Qty`,
                      COALESCE(SUM(CASE WHEN b.status = 'QUARANTINED' AND b.expiry_date >= date('now','localtime') THEN b.quantity END), 0) AS `Quarantined Qty`,
                      COALESCE(SUM(CASE WHEN b.status IN ('ACTIVE', 'QUARANTINED') AND b.expiry_date < date('now','localtime') THEN b.quantity END), 0) AS `Expired Qty`,
                      m.reorder_level AS `Reorder Level`,
                      CASE WHEN COALESCE(SUM(CASE WHEN b.status = 'ACTIVE' AND b.expiry_date >= date('now','localtime') THEN b.quantity END), 0) < m.reorder_level
                           THEN 'LOW STOCK' ELSE 'OK' END AS `Stock Status`,
                      COUNT(CASE WHEN b.status = 'ACTIVE' AND b.quantity > 0 THEN 1 END) AS `Active Batches`
                    FROM medicines m
                    LEFT JOIN categories c ON c.id = m.category_id
                    LEFT JOIN batches b ON b.medicine_id = m.id
                    WHERE m.active = TRUE
                    GROUP BY m.id, m.code, m.brand_name, m.generic_name, c.name, m.reorder_level
                    ORDER BY m.brand_name""");
            case INVENTORY_VALUATION -> Jdbc.queryTable("""
                    SELECT COALESCE(c.name, 'Uncategorised') AS `Category`, COUNT(DISTINCT m.id) AS `Medicines`,
                      SUM(b.quantity) AS `Units`,
                      SUM(b.quantity * b.purchase_price) AS `Cost Value`,
                      SUM(b.quantity * m.selling_price) AS `Selling Value`,
                      SUM(b.quantity * (m.selling_price - b.purchase_price)) AS `Potential Margin`
                    FROM batches b
                    JOIN medicines m ON m.id = b.medicine_id
                    LEFT JOIN categories c ON c.id = m.category_id
                    WHERE b.status = 'ACTIVE' AND b.quantity > 0 AND b.expiry_date >= date('now','localtime')
                    GROUP BY c.name
                    ORDER BY `Cost Value` DESC""");
            case BATCH_EXPIRY -> Jdbc.queryTable("""
                    SELECT b.batch_no AS `Batch`, m.brand_name AS `Medicine`, b.mfg_date AS `Mfg Date`,
                      b.expiry_date AS `Expiry Date`, CAST(julianday(b.expiry_date) - julianday(date('now','localtime')) AS INTEGER) AS `Days Left`,
                      b.quantity AS `Qty`, b.status AS `Batch Status`,
                      CASE WHEN b.expiry_date < date('now','localtime') THEN 'EXPIRED'
                           WHEN b.expiry_date <= date('now','localtime', '+' || ? || ' days') THEN 'NEAR EXPIRY'
                           ELSE 'VALID' END AS `Expiry Status`,
                      s.name AS `Supplier`, b.quantity * b.purchase_price AS `Value at Cost`
                    FROM batches b
                    JOIN medicines m ON m.id = b.medicine_id
                    LEFT JOIN suppliers s ON s.id = b.supplier_id
                    WHERE b.quantity > 0
                    ORDER BY b.expiry_date, m.brand_name""", expiry.thresholdDays());
            case PURCHASES -> Jdbc.queryTable("""
                    SELECT p.purchase_no AS `PO No`, p.order_date AS `Order Date`, p.received_date AS `Received`,
                      s.name AS `Supplier`, p.status AS `Status`, COALESCE(q.line_count, 0) AS `Items`,
                      CASE p.supply_type WHEN 'INTER_STATE' THEN 'IGST' ELSE 'CGST+SGST' END AS `Tax Type`,

                      p.subtotal AS `Subtotal`, p.tax_amount AS `GST`, p.total_amount AS `Total`,
                      p.payment_status AS `Payment`, p.amount_paid AS `Paid`
                    FROM purchases p
                    JOIN suppliers s ON s.id = p.supplier_id
                    LEFT JOIN (SELECT purchase_id, COUNT(*) AS line_count FROM purchase_items GROUP BY purchase_id) q
                      ON q.purchase_id = p.id
                    WHERE p.order_date BETWEEN ? AND ?
                    ORDER BY p.order_date, p.purchase_no""", from, to);
            case SUPPLIER_PURCHASES -> Jdbc.queryTable("""
                    SELECT s.name AS `Supplier`, COUNT(p.id) AS `Purchases`, COALESCE(SUM(q.qty), 0) AS `Units`,
                      COALESCE(SUM(p.total_amount), 0.0) AS `Total Value`, COALESCE(SUM(p.amount_paid), 0.0) AS `Paid`,
                      COALESCE(SUM(p.total_amount - p.amount_paid), 0.0) AS `Outstanding`
                    FROM suppliers s
                    LEFT JOIN purchases p ON p.supplier_id = s.id AND p.status <> 'CANCELLED'
                      AND p.order_date BETWEEN ? AND ?
                    LEFT JOIN (SELECT purchase_id, SUM(quantity) AS qty FROM purchase_items GROUP BY purchase_id) q
                      ON q.purchase_id = p.id
                    GROUP BY s.id, s.name
                    ORDER BY `Total Value` DESC""", from, to);
            case SALES -> Jdbc.queryTable("""
                    SELECT s.invoice_no AS `Invoice`, s.sale_date AS `Date`, c.name AS `Customer`, c.customer_type AS `Type`,
                      s.place_of_supply AS `Place of Supply`,
                      CASE s.supply_type WHEN 'INTER_STATE' THEN 'IGST' ELSE 'CGST+SGST' END AS `Tax Type`,
                      s.subtotal AS `Subtotal`, s.discount_amount AS `Discount`,
                      CASE WHEN s.supply_type = 'INTRA_STATE' THEN ROUND(s.tax_amount / 2.0, 2) ELSE 0.0 END AS `CGST`,
                      CASE WHEN s.supply_type = 'INTRA_STATE' THEN s.tax_amount - ROUND(s.tax_amount / 2.0, 2) ELSE 0.0 END AS `SGST`,
                      CASE WHEN s.supply_type = 'INTER_STATE' THEN s.tax_amount ELSE 0.0 END AS `IGST`,
                      s.total_amount AS `Total`, s.amount_paid AS `Paid`, s.total_amount - s.amount_paid AS `Balance`,
                      s.payment_mode AS `Mode`, s.payment_status AS `Payment`, u.username AS `By`
                    FROM sales s
                    JOIN customers c ON c.id = s.customer_id
                    LEFT JOIN users u ON u.id = s.created_by
                    WHERE s.sale_date >= ? AND s.sale_date < ?
                    ORDER BY s.sale_date""", from, end);
            case SALES_BY_DAY -> Jdbc.queryTable("""
                    SELECT DATE(s.sale_date) AS `Day`, COUNT(*) AS `Invoices`, SUM(s.total_amount) AS `Revenue`,
                      SUM(s.tax_amount) AS `GST Collected`
                    FROM sales s
                    WHERE s.sale_date >= ? AND s.sale_date < ?
                    GROUP BY DATE(s.sale_date)
                    ORDER BY `Day`""", from, end);
            case CUSTOMER_SALES -> Jdbc.queryTable("""
                    SELECT c.name AS `Customer`, c.customer_type AS `Type`, COUNT(s.id) AS `Invoices`,
                      COALESCE(SUM(s.total_amount), 0.0) AS `Total Sales`,
                      COALESCE(SUM(s.amount_paid), 0.0) AS `Paid`,
                      COALESCE(SUM(s.total_amount - s.amount_paid), 0.0) AS `Outstanding`
                    FROM customers c
                    LEFT JOIN sales s ON s.customer_id = c.id AND s.sale_date >= ? AND s.sale_date < ?
                    GROUP BY c.id, c.name, c.customer_type
                    ORDER BY `Total Sales` DESC""", from, end);
            case CUSTOMER_PAYMENTS -> Jdbc.queryTable("""
                    SELECT p.payment_date AS `Date`, s.invoice_no AS `Invoice`, c.name AS `Customer`,
                      p.amount AS `Amount Received`, p.mode AS `Mode`, p.reference AS `Reference`,
                      s.total_amount AS `Invoice Total`, s.total_amount - s.amount_paid AS `Balance Now`,
                      u.username AS `Recorded By`
                    FROM sale_payments p
                    JOIN sales s ON s.id = p.sale_id
                    JOIN customers c ON c.id = s.customer_id
                    LEFT JOIN users u ON u.id = p.user_id
                    WHERE p.payment_date BETWEEN ? AND ?
                    ORDER BY p.payment_date, p.id""", from, to);
            case FAST_MOVING -> Jdbc.queryTable("""
                    SELECT m.brand_name AS `Medicine`, m.generic_name AS `Generic`, SUM(si.quantity) AS `Qty Sold`,
                      COUNT(DISTINCT si.sale_id) AS `Invoices`, SUM(si.line_total) AS `Revenue`
                    FROM sales_items si
                    JOIN sales s ON s.id = si.sale_id
                    JOIN medicines m ON m.id = si.medicine_id
                    WHERE s.sale_date >= ? AND s.sale_date < ?
                    GROUP BY m.id, m.brand_name, m.generic_name
                    ORDER BY `Qty Sold` DESC""", from, end);
            case SLOW_MOVING -> Jdbc.queryTable("""
                    SELECT m.brand_name AS `Medicine`, m.generic_name AS `Generic`, COALESCE(x.qty, 0) AS `Qty Sold`,
                      COALESCE(st.stock, 0) AS `Stock on Hand`,
                      CASE WHEN COALESCE(x.qty, 0) = 0 THEN 'No sales in period'
                           ELSE CONCAT(ROUND(COALESCE(st.stock, 0) * 1.0 / x.qty, 1), 'x period sales') END AS `Stock Cover`
                    FROM medicines m
                    LEFT JOIN (SELECT si.medicine_id, SUM(si.quantity) AS qty FROM sales_items si
                               JOIN sales s ON s.id = si.sale_id
                               WHERE s.sale_date >= ? AND s.sale_date < ? GROUP BY si.medicine_id) x
                      ON x.medicine_id = m.id
                    LEFT JOIN (SELECT medicine_id, SUM(quantity) AS stock FROM batches
                               WHERE status = 'ACTIVE' AND expiry_date >= date('now','localtime') GROUP BY medicine_id) st
                      ON st.medicine_id = m.id
                    WHERE m.active = TRUE
                    ORDER BY `Qty Sold` ASC, `Stock on Hand` DESC""", from, end);
            case STOCK_MOVEMENTS -> Jdbc.queryTable("""
                    SELECT t.created_at AS `Time`, m.brand_name AS `Medicine`, b.batch_no AS `Batch`, t.txn_type AS `Type`,
                      t.quantity AS `Qty`, t.balance_after AS `Batch Balance`, t.reference_no AS `Reference`,
                      t.remarks AS `Remarks`, u.username AS `User`
                    FROM stock_transactions t
                    JOIN medicines m ON m.id = t.medicine_id
                    JOIN batches b ON b.id = t.batch_id
                    LEFT JOIN users u ON u.id = t.user_id
                    WHERE t.created_at >= ? AND t.created_at < ?
                    ORDER BY t.created_at, t.id""", from, end);
        };
        audit.log("REPORT", "REPORT", report.name(),
                report.usesDateRange() ? from + " to " + to : null);
        return data;
    }
}
