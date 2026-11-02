package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.IndianState;
import com.pharmasync.model.Supplier;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SupplierDao {

    private static Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(rs.getInt("id"), rs.getString("name"), rs.getString("contact_person"),
                rs.getString("phone"), rs.getString("email"), rs.getString("address"),
                IndianState.ofCode(rs.getString("state")), rs.getString("gstin"),
                rs.getString("drug_license_no"), rs.getBoolean("active"));
    }

    public List<Supplier> search(String text, boolean activeOnly) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        return Jdbc.query("SELECT * FROM suppliers WHERE (name LIKE ? OR contact_person LIKE ? OR phone LIKE ?)"
                + (activeOnly ? " AND active = TRUE" : "") + " ORDER BY name", SupplierDao::map, like, like, like);
    }

    public Optional<Supplier> findById(int id) {
        return Jdbc.queryOne("SELECT * FROM suppliers WHERE id = ?", SupplierDao::map, id);
    }

    public int insert(Supplier s) {

        return Jdbc.insert("INSERT INTO suppliers (name, contact_person, phone, email, address, state, gstin, "
                        + "drug_license_no, active) VALUES (?,?,?,?,?,?,?,?,?)",
                s.name(), s.contactPerson(), s.phone(), s.email(), s.address(), IndianState.codeOf(s.state()),
                s.gstin(), s.drugLicenseNo(),
                s.active());
    }

    public void update(Supplier s) {
        Jdbc.update("UPDATE suppliers SET name = ?, contact_person = ?, phone = ?, email = ?, address = ?, "
                        + "state = ?, gstin = ?, drug_license_no = ?, active = ? WHERE id = ?",
                s.name(), s.contactPerson(), s.phone(), s.email(), s.address(), IndianState.codeOf(s.state()),
                s.gstin(), s.drugLicenseNo(),
                s.active(), s.id());
    }
}
