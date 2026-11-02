package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.IndianState;
import com.pharmasync.model.PaymentStatus;
import com.pharmasync.model.Sale;
import com.pharmasync.model.SaleItem;
import com.pharmasync.model.SalePayment;
import com.pharmasync.model.SupplyType;


import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class SaleDao {

    private static final String SELECT = "SELECT s.*, c.name AS customer_name, u.username AS created_by_name "
            + "FROM sales s JOIN customers c ON c.id = s.customer_id LEFT JOIN users u ON u.id = s.created_by";

    private static final String SELECT_ITEMS = "SELECT si.*, CONCAT(m.brand_name, ' (', m.generic_name, ')') AS medicine_name, "
            + "b.batch_no, b.expiry_date FROM sales_items si JOIN medicines m ON m.id = si.medicine_id "
            + "JOIN batches b ON b.id = si.batch_id";

    private static Sale map(ResultSet rs) throws SQLException {
        return new Sale(rs.getInt("id"), rs.getString("invoice_no"), rs.getInt("customer_id"),
                rs.getString("customer_name"), Jdbc.getDateTime(rs, "sale_date"), Jdbc.getDecimal(rs, "subtotal"),
                Jdbc.getDecimal(rs, "discount_amount"), Jdbc.getDecimal(rs, "tax_amount"), Jdbc.getDecimal(rs, "total_amount"),
                rs.getString("payment_mode"), PaymentStatus.valueOf(rs.getString("payment_status")),
                Jdbc.getDecimal(rs, "amount_paid"), IndianState.ofCode(rs.getString("place_of_supply")),
                SupplyType.valueOf(rs.getString("supply_type")), rs.getString("created_by_name"));
    }

    private static SalePayment mapPayment(ResultSet rs) throws SQLException {
        return new SalePayment(rs.getInt("id"), rs.getInt("sale_id"), Jdbc.getDate(rs, "payment_date"),
                Jdbc.getDecimal(rs, "amount"), rs.getString("mode"), rs.getString("reference"),
                rs.getString("recorded_by"), Jdbc.getDateTime(rs, "created_at"));
    }

    private static SaleItem mapItem(ResultSet rs) throws SQLException {
        return new SaleItem(rs.getInt("id"), rs.getInt("sale_id"), rs.getInt("medicine_id"),
                rs.getString("medicine_name"), rs.getInt("batch_id"), rs.getString("batch_no"),
                Jdbc.getDate(rs, "expiry_date"), rs.getInt("quantity"), Jdbc.getDecimal(rs, "unit_price"),
                Jdbc.getDecimal(rs, "discount_pct"), Jdbc.getDecimal(rs, "gst_rate"), Jdbc.getDecimal(rs, "tax_amount"),
                Jdbc.getDecimal(rs, "line_total"));
    }

    public List<Sale> find(LocalDate from, LocalDate to, String customerText) {
        String like = "%" + (customerText == null ? "" : customerText.trim()) + "%";
        return Jdbc.query(SELECT + " WHERE s.sale_date >= ? AND s.sale_date < ? AND (c.name LIKE ? OR s.invoice_no LIKE ?) "
                + "ORDER BY s.sale_date DESC, s.id DESC", SaleDao::map, from, to.plusDays(1), like, like);
    }

    public Optional<Sale> findById(int id) {
        return Jdbc.queryOne(SELECT + " WHERE s.id = ?", SaleDao::map, id);
    }

    public List<SaleItem> findItems(int saleId) {
        return Jdbc.query(SELECT_ITEMS + " WHERE si.sale_id = ? ORDER BY si.id", SaleDao::mapItem, saleId);
    }

    public int insert(String invoiceNo, int customerId, LocalDateTime saleDate, BigDecimal subtotal, BigDecimal discount,
                      BigDecimal tax, BigDecimal total, String paymentMode, IndianState placeOfSupply,
                      SupplyType supplyType, Integer userId) {
        return Jdbc.insert("INSERT INTO sales (invoice_no, customer_id, sale_date, subtotal, discount_amount, tax_amount, "
                        + "total_amount, payment_mode, payment_status, amount_paid, place_of_supply, supply_type, created_by) "
                        + "VALUES (?,?,?,?,?,?,?,?, 'UNPAID', 0, ?,?,?)",
                invoiceNo, customerId, saleDate, subtotal, discount, tax, total, paymentMode,
                IndianState.codeOf(placeOfSupply), supplyType, userId);
    }

    public int insertItem(int saleId, int medicineId, int batchId, int quantity, BigDecimal unitPrice,
                          BigDecimal discountPct, BigDecimal gstRate, BigDecimal tax, BigDecimal lineTotal) {
        return Jdbc.insert("INSERT INTO sales_items (sale_id, medicine_id, batch_id, quantity, unit_price, discount_pct, "
                        + "gst_rate, tax_amount, line_total) VALUES (?,?,?,?,?,?,?,?,?)",
                saleId, medicineId, batchId, quantity, unitPrice, discountPct, gstRate, tax, lineTotal);
    }

    public void updatePayment(int id, BigDecimal amountPaid, PaymentStatus status) {
        Jdbc.update("UPDATE sales SET amount_paid = ?, payment_status = ? WHERE id = ?", amountPaid, status, id);
    }

    public int insertPayment(int saleId, LocalDate date, BigDecimal amount, String mode, String reference, Integer userId) {
        return Jdbc.insert("INSERT INTO sale_payments (sale_id, payment_date, amount, mode, reference, user_id) "
                + "VALUES (?,?,?,?,?,?)", saleId, date, amount, mode, reference, userId);
    }

    public List<SalePayment> findPayments(int saleId) {
        return Jdbc.query("SELECT p.*, u.username AS recorded_by FROM sale_payments p "
                + "LEFT JOIN users u ON u.id = p.user_id WHERE p.sale_id = ? ORDER BY p.payment_date, p.id",
                SaleDao::mapPayment, saleId);
    }

    /** Highest invoice number issued with the given prefix, or null. Sequential because transactions hold the write lock. */
    public String lastNumberWithPrefix(String prefix) {
        return Jdbc.queryOne("SELECT invoice_no FROM sales WHERE invoice_no LIKE ? "
                + "ORDER BY invoice_no DESC LIMIT 1", rs -> rs.getString(1), prefix + "%").orElse(null);
    }
}
