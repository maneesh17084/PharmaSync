package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Medicine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class MedicineDao {

    /** current_stock counts only sellable stock: ACTIVE batches that have not expired. */
    private static final String SELECT = "SELECT m.*, c.name AS category_name, "
            + "COALESCE((SELECT SUM(b.quantity) FROM batches b WHERE b.medicine_id = m.id "
            + "AND b.status = 'ACTIVE' AND b.expiry_date >= date('now','localtime')), 0) AS current_stock "
            + "FROM medicines m LEFT JOIN categories c ON c.id = m.category_id";

    private static Medicine map(ResultSet rs) throws SQLException {
        return new Medicine(rs.getInt("id"), rs.getString("code"), rs.getString("brand_name"),
                rs.getString("generic_name"), rs.getString("composition"), rs.getString("manufacturer"),
                Jdbc.getNullableInt(rs, "category_id"), rs.getString("category_name"), rs.getString("dosage_form"),
                rs.getString("strength"), rs.getString("pack_size"), Jdbc.getDecimal(rs, "purchase_price"),
                Jdbc.getDecimal(rs, "selling_price"), Jdbc.getDecimal(rs, "gst_rate"), rs.getInt("reorder_level"),
                rs.getString("storage_condition"), rs.getBoolean("active"), rs.getInt("current_stock"));
    }

    /** Searches code, brand, generic name, composition and manufacturer. */
    public List<Medicine> search(String text, boolean activeOnly) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        return Jdbc.query(SELECT + " WHERE (m.code LIKE ? OR m.brand_name LIKE ? OR m.generic_name LIKE ? "
                        + "OR m.composition LIKE ? OR m.manufacturer LIKE ?)"
                        + (activeOnly ? " AND m.active = TRUE" : "") + " ORDER BY m.brand_name",
                MedicineDao::map, like, like, like, like, like);
    }

    public List<Medicine> findAllActive() {
        return search("", true);
    }

    public Optional<Medicine> findById(int id) {
        return Jdbc.queryOne(SELECT + " WHERE m.id = ?", MedicineDao::map, id);
    }

    public List<Medicine> findLowStock() {
        return Jdbc.query("SELECT * FROM (" + SELECT + " WHERE m.active = TRUE) x "
                + "WHERE x.current_stock < x.reorder_level ORDER BY (x.current_stock - x.reorder_level)", MedicineDao::map);
    }

    public int insert(Medicine m) {
        return Jdbc.insert("INSERT INTO medicines (code, brand_name, generic_name, composition, manufacturer, "
                        + "category_id, dosage_form, strength, pack_size, purchase_price, selling_price, gst_rate, "
                        + "reorder_level, storage_condition, active) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                m.code(), m.brandName(), m.genericName(), m.composition(), m.manufacturer(), m.categoryId(),
                m.dosageForm(), m.strength(), m.packSize(), m.purchasePrice(), m.sellingPrice(), m.gstRate(),
                m.reorderLevel(), m.storageCondition(), m.active());
    }

    public void update(Medicine m) {
        Jdbc.update("UPDATE medicines SET code = ?, brand_name = ?, generic_name = ?, composition = ?, "
                        + "manufacturer = ?, category_id = ?, dosage_form = ?, strength = ?, pack_size = ?, "
                        + "purchase_price = ?, selling_price = ?, gst_rate = ?, reorder_level = ?, "
                        + "storage_condition = ?, active = ? WHERE id = ?",
                m.code(), m.brandName(), m.genericName(), m.composition(), m.manufacturer(), m.categoryId(),
                m.dosageForm(), m.strength(), m.packSize(), m.purchasePrice(), m.sellingPrice(), m.gstRate(),
                m.reorderLevel(), m.storageCondition(), m.active(), m.id());
    }

    public boolean codeExists(String code, int excludeId) {
        return Jdbc.queryLong("SELECT COUNT(*) FROM medicines WHERE code = ? AND id <> ?", code, excludeId) > 0;
    }

    public String nextCode() {
        long max = Jdbc.queryLong("SELECT COALESCE(MAX(CAST(substr(code, 4) AS INTEGER)), 0) "
                + "FROM medicines WHERE code GLOB 'MED[0-9]*' AND substr(code, 4) NOT GLOB '*[^0-9]*'");
        return String.format("MED%03d", max + 1);
    }
}
