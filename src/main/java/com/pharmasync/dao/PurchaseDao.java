package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Purchase;
import com.pharmasync.model.PurchaseItem;
import com.pharmasync.model.PurchaseStatus;
import com.pharmasync.model.SupplyType;


import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class PurchaseDao {

    private static final String SELECT = "SELECT p.*, s.name AS supplier_name, u.username AS created_by_name "
            + "FROM purchases p JOIN suppliers s ON s.id = p.supplier_id LEFT JOIN users u ON u.id = p.created_by";

    private static final String SELECT_ITEMS = "SELECT pi.*, CONCAT(m.brand_name, ' (', m.generic_name, ')') AS medicine_name "
            + "FROM purchase_items pi JOIN medicines m ON m.id = pi.medicine_id";

    private static Purchase map(ResultSet rs) throws SQLException {
        return new Purchase(rs.getInt("id"), rs.getString("purchase_no"), rs.getInt("supplier_id"),
                rs.getString("supplier_name"), rs.getString("supplier_invoice_ref"), Jdbc.getDate(rs, "order_date"),
                Jdbc.getDate(rs, "received_date"), PurchaseStatus.valueOf(rs.getString("status")),
                Jdbc.getDecimal(rs, "subtotal"), Jdbc.getDecimal(rs, "tax_amount"), Jdbc.getDecimal(rs, "total_amount"),
                PaymentStatus.valueOf(rs.getString("payment_status")), Jdbc.getDecimal(rs, "amount_paid"),
                SupplyType.valueOf(rs.getString("supply_type")), rs.getString("notes"), rs.getString("created_by_name"));
    }

    private static PurchaseItem mapItem(ResultSet rs) throws SQLException {
        return new PurchaseItem(rs.getInt("id"), rs.getInt("purchase_id"), rs.getInt("medicine_id"),
                rs.getString("medicine_name"), Jdbc.getNullableInt(rs, "batch_id"), rs.getString("batch_no"),
                Jdbc.getDate(rs, "mfg_date"), Jdbc.getDate(rs, "expiry_date"), rs.getInt("quantity"),
                Jdbc.getDecimal(rs, "unit_cost"), Jdbc.getDecimal(rs, "mrp"), Jdbc.getDecimal(rs, "gst_rate"),
                Jdbc.getDecimal(rs, "line_total"));
    }

    public List<Purchase> find(LocalDate from, LocalDate to, PurchaseStatus status) {
        String sql = SELECT + " WHERE p.order_date BETWEEN ? AND ?"
                + (status == null ? "" : " AND p.status = '" + status.name() + "'")
                + " ORDER BY p.order_date DESC, p.id DESC";
        return Jdbc.query(sql, PurchaseDao::map, from, to);
    }

    public Optional<Purchase> findById(int id) {
        return Jdbc.queryOne(SELECT + " WHERE p.id = ?", PurchaseDao::map, id);
    }

    public Optional<Purchase> findByIdForUpdate(int id) {
        return Jdbc.queryOne("SELECT p.*, NULL AS supplier_name, NULL AS created_by_name FROM purchases p "
                + "WHERE p.id = ?", PurchaseDao::map, id);
    }

    public List<PurchaseItem> findItems(int purchaseId) {
        return Jdbc.query(SELECT_ITEMS + " WHERE pi.purchase_id = ? ORDER BY pi.id", PurchaseDao::mapItem, purchaseId);
    }

    public int insert(String purchaseNo, int supplierId, String invoiceRef, LocalDate orderDate, BigDecimal subtotal,
                      BigDecimal tax, BigDecimal total, SupplyType supplyType, String notes, Integer userId) {
        return Jdbc.insert("INSERT INTO purchases (purchase_no, supplier_id, supplier_invoice_ref, order_date, status, "
                        + "subtotal, tax_amount, total_amount, supply_type, notes, created_by) "
                        + "VALUES (?,?,?,?, 'ORDERED', ?,?,?,?,?,?)",
                purchaseNo, supplierId, invoiceRef, orderDate, subtotal, tax, total, supplyType, notes, userId);
    }

    public int insertItem(int purchaseId, PurchaseItem i) {
        return Jdbc.insert("INSERT INTO purchase_items (purchase_id, medicine_id, batch_no, mfg_date, expiry_date, "
                        + "quantity, unit_cost, mrp, gst_rate, line_total) VALUES (?,?,?,?,?,?,?,?,?,?)",
                purchaseId, i.medicineId(), i.batchNo(), i.mfgDate(), i.expiryDate(), i.quantity(), i.unitCost(),
                i.mrp(), i.gstRate(), i.lineTotal());
    }

    public void updateItemBatch(int itemId, int batchId, String batchNo, LocalDate mfg, LocalDate expiry) {
        Jdbc.update("UPDATE purchase_items SET batch_id = ?, batch_no = ?, mfg_date = ?, expiry_date = ? WHERE id = ?",
                batchId, batchNo, mfg, expiry, itemId);
    }

    public void markReceived(int id, LocalDate receivedDate, String invoiceRef) {
        Jdbc.update("UPDATE purchases SET status = 'RECEIVED', received_date = ?, "
                + "supplier_invoice_ref = COALESCE(?, supplier_invoice_ref) WHERE id = ?", receivedDate, invoiceRef, id);
    }

    public void updateStatus(int id, PurchaseStatus status) {
        Jdbc.update("UPDATE purchases SET status = ? WHERE id = ?", status, id);
    }

    public void updatePayment(int id, BigDecimal amountPaid, PaymentStatus status) {
        Jdbc.update("UPDATE purchases SET amount_paid = ?, payment_status = ? WHERE id = ?", amountPaid, status, id);
    }

    /** Highest purchase number issued with the given prefix, or null. */
    public String lastNumberWithPrefix(String prefix) {
        return Jdbc.queryOne("SELECT purchase_no FROM purchases WHERE purchase_no LIKE ? "
                + "ORDER BY purchase_no DESC LIMIT 1", rs -> rs.getString(1), prefix + "%").orElse(null);
    }
}
