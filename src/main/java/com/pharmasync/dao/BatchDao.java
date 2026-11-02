package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Batch;
import com.pharmasync.model.BatchStatus;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class BatchDao {

    private static final String SELECT = "SELECT b.*, CONCAT(m.brand_name, ' (', m.generic_name, ')') AS medicine_name, "
            + "s.name AS supplier_name FROM batches b JOIN medicines m ON m.id = b.medicine_id "
            + "LEFT JOIN suppliers s ON s.id = b.supplier_id";

    private static Batch map(ResultSet rs) throws SQLException {
        return new Batch(rs.getInt("id"), rs.getInt("medicine_id"), rs.getString("medicine_name"),
                rs.getString("batch_no"), Jdbc.getDate(rs, "mfg_date"), Jdbc.getDate(rs, "expiry_date"),
                rs.getInt("quantity"), Jdbc.getDecimal(rs, "purchase_price"), Jdbc.getDecimal(rs, "mrp"),
                Jdbc.getNullableInt(rs, "supplier_id"), rs.getString("supplier_name"),
                BatchStatus.valueOf(rs.getString("status")));
    }

    /** Batches matching a medicine/batch text filter; status null means any status. */
    public List<Batch> search(String text, BatchStatus status, boolean inStockOnly) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE (b.batch_no LIKE ? OR m.brand_name LIKE ? OR m.generic_name LIKE ? OR m.code LIKE ?)");
        if (status != null) {
            sql.append(" AND b.status = '").append(status.name()).append("'");
        }
        if (inStockOnly) {
            sql.append(" AND b.quantity > 0");
        }
        sql.append(" ORDER BY m.brand_name, b.expiry_date");
        return Jdbc.query(sql.toString(), BatchDao::map, like, like, like, like);
    }

    public List<Batch> findByMedicine(int medicineId) {
        return Jdbc.query(SELECT + " WHERE b.medicine_id = ? ORDER BY b.expiry_date, b.id", BatchDao::map, medicineId);
    }

    /** Medicine's batches for dispatch; callers run inside a transaction, which holds SQLite's write lock. */
    public List<Batch> findByMedicineForUpdate(int medicineId) {
        return Jdbc.query(SELECT + " WHERE b.medicine_id = ? ORDER BY b.expiry_date, b.id",
                BatchDao::map, medicineId);
    }

    /** ACTIVE batches with stock expiring between today and the given date (near-expiry candidates). */
    public List<Batch> findActiveExpiringBetween(LocalDate today, LocalDate until) {
        return Jdbc.query(SELECT + " WHERE b.status = 'ACTIVE' AND b.quantity > 0 AND b.expiry_date >= ? "
                + "AND b.expiry_date <= ? ORDER BY b.expiry_date", BatchDao::map, today, until);
    }

    /** Expired batches still holding stock (ACTIVE or QUARANTINED), awaiting return or disposal. */
    public List<Batch> findExpiredInStock(LocalDate today) {
        return Jdbc.query(SELECT + " WHERE b.status IN ('ACTIVE', 'QUARANTINED') AND b.quantity > 0 "
                + "AND b.expiry_date < ? ORDER BY b.expiry_date", BatchDao::map, today);
    }

    public Optional<Batch> findById(int id) {
        return Jdbc.queryOne(SELECT + " WHERE b.id = ?", BatchDao::map, id);
    }

    public Optional<Batch> findByIdForUpdate(int id) {
        return Jdbc.queryOne(SELECT + " WHERE b.id = ?", BatchDao::map, id);
    }

    public Optional<Batch> findByMedicineAndBatchNoForUpdate(int medicineId, String batchNo) {
        return Jdbc.queryOne(SELECT + " WHERE b.medicine_id = ? AND b.batch_no = ?",
                BatchDao::map, medicineId, batchNo);
    }

    public int insert(int medicineId, String batchNo, LocalDate mfg, LocalDate expiry, int quantity,
                      BigDecimal purchasePrice, BigDecimal mrp, Integer supplierId) {
        return Jdbc.insert("INSERT INTO batches (medicine_id, batch_no, mfg_date, expiry_date, quantity, "
                        + "purchase_price, mrp, supplier_id, status) VALUES (?,?,?,?,?,?,?,?, 'ACTIVE')",
                medicineId, batchNo, mfg, expiry, quantity, purchasePrice, mrp, supplierId);
    }

    public void updateQuantity(int id, int quantity) {
        Jdbc.update("UPDATE batches SET quantity = ? WHERE id = ?", quantity, id);
    }

    public void updateStatus(int id, BatchStatus status, int quantity) {
        Jdbc.update("UPDATE batches SET status = ?, quantity = ? WHERE id = ?", status, quantity, id);
    }
}
