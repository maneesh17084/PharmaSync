package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Customer;
import com.pharmasync.model.CustomerType;
import com.pharmasync.model.IndianState;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CustomerDao {

    private static Customer map(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt("id"), rs.getString("name"), CustomerType.valueOf(rs.getString("customer_type")),
                rs.getString("contact_person"), rs.getString("phone"), rs.getString("email"), rs.getString("address"),
                IndianState.ofCode(rs.getString("state")), rs.getString("gstin"), rs.getString("drug_license_no"), rs.getBoolean("active"));
    }

    public List<Customer> search(String text, boolean activeOnly) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        return Jdbc.query("SELECT * FROM customers WHERE (name LIKE ? OR contact_person LIKE ? OR phone LIKE ?)"
                + (activeOnly ? " AND active = TRUE" : "") + " ORDER BY name", CustomerDao::map, like, like, like);
    }

    public Optional<Customer> findById(int id) {
        return Jdbc.queryOne("SELECT * FROM customers WHERE id = ?", CustomerDao::map, id);
    }

    public int insert(Customer c) {
        return Jdbc.insert("INSERT INTO customers (name, customer_type, contact_person, phone, email, address, state, "
                        + "gstin, drug_license_no, active) VALUES (?,?,?,?,?,?,?,?,?,?)",
                c.name(), c.type(), c.contactPerson(), c.phone(), c.email(), c.address(), IndianState.codeOf(c.state()), c.gstin(),
                c.drugLicenseNo(), c.active());
    }

    public void update(Customer c) {
        Jdbc.update("UPDATE customers SET name = ?, customer_type = ?, contact_person = ?, phone = ?, email = ?, "
                        + "address = ?, state = ?, gstin = ?, drug_license_no = ?, active = ? WHERE id = ?",
                c.name(), c.type(), c.contactPerson(), c.phone(), c.email(), c.address(), IndianState.codeOf(c.state()), c.gstin(),
                c.drugLicenseNo(), c.active(), c.id());
    }
}
