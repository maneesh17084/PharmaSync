package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.StockTransaction;
import com.pharmasync.model.TxnType;

import java.time.LocalDate;
import java.util.List;

public class StockTransactionDao {

    public void insert(int batchId, int medicineId, TxnType type, int quantity, int balanceAfter,
                       String referenceType, String referenceNo, String remarks, Integer userId) {
        Jdbc.update("INSERT INTO stock_transactions (batch_id, medicine_id, txn_type, quantity, balance_after, "
                        + "reference_type, reference_no, remarks, user_id) VALUES (?,?,?,?,?,?,?,?,?)",
                batchId, medicineId, type, quantity, balanceAfter, referenceType, referenceNo, remarks, userId);
    }

    public List<StockTransaction> find(LocalDate from, LocalDate to, String text) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        return Jdbc.query("SELECT t.*, CONCAT(m.brand_name, ' (', m.generic_name, ')') AS medicine_name, b.batch_no, "
                        + "u.username FROM stock_transactions t JOIN medicines m ON m.id = t.medicine_id "
                        + "JOIN batches b ON b.id = t.batch_id LEFT JOIN users u ON u.id = t.user_id "
                        + "WHERE t.created_at >= ? AND t.created_at < ? "
                        + "AND (m.brand_name LIKE ? OR m.generic_name LIKE ? OR b.batch_no LIKE ? OR t.reference_no LIKE ?) "
                        + "ORDER BY t.created_at DESC, t.id DESC",
                rs -> new StockTransaction(rs.getInt("id"), Jdbc.getDateTime(rs, "created_at"),
                        rs.getString("medicine_name"), rs.getString("batch_no"), TxnType.valueOf(rs.getString("txn_type")),
                        rs.getInt("quantity"), rs.getInt("balance_after"), rs.getString("reference_type"),
                        rs.getString("reference_no"), rs.getString("remarks"), rs.getString("username")),
                from, to.plusDays(1), like, like, like, like);
    }
}
