package com.pharmasync.service;

import com.pharmasync.dao.CategoryDao;
import com.pharmasync.dao.CustomerDao;
import com.pharmasync.dao.MedicineDao;
import com.pharmasync.dao.SupplierDao;
import com.pharmasync.db.DataAccessException;
import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Category;
import com.pharmasync.model.Customer;
import com.pharmasync.model.IndianState;

import com.pharmasync.model.Medicine;
import com.pharmasync.model.Supplier;

import java.math.BigDecimal;
import java.util.List;

/** Medicines, categories, suppliers and customers (FR-03, FR-04, FR-07, FR-10). */
public class MasterDataService {

    private final MedicineDao medicines;
    private final CategoryDao categories;
    private final SupplierDao suppliers;
    private final CustomerDao customers;
    private final AuditService audit;

    public MasterDataService(MedicineDao medicines, CategoryDao categories, SupplierDao suppliers,
                             CustomerDao customers, AuditService audit) {
        this.medicines = medicines;
        this.categories = categories;
        this.suppliers = suppliers;
        this.customers = customers;
        this.audit = audit;
    }

    // ---- medicines ----

    public List<Medicine> searchMedicines(String text, boolean activeOnly) {
        return medicines.search(text, activeOnly);
    }

    public String suggestMedicineCode() {
        return medicines.nextCode();
    }

    public int saveMedicine(Medicine m) {
        Session.requireAdmin();
        require(m.code(), "Medicine code");
        require(m.brandName(), "Brand name");
        require(m.genericName(), "Generic name");
        nonNegative(m.purchasePrice(), "Purchase price");
        nonNegative(m.sellingPrice(), "Selling price");
        nonNegative(m.gstRate(), "GST rate");
        if (m.gstRate().compareTo(BigDecimal.valueOf(28)) > 0) {
            throw new BusinessException("GST rate cannot exceed 28%");
        }
        if (m.reorderLevel() < 0) {
            throw new BusinessException("Reorder level cannot be negative");
        }
        if (medicines.codeExists(m.code().trim(), m.id())) {
            throw new BusinessException("Medicine code " + m.code() + " is already used");
        }
        return Jdbc.inTransaction(() -> {
            if (m.id() == 0) {
                int id = medicines.insert(m);
                audit.log("CREATE", "MEDICINE", id, m.code() + " " + m.brandName());
                return id;
            }
            medicines.update(m);
            audit.log("UPDATE", "MEDICINE", m.id(), m.code() + " " + m.brandName() + (m.active() ? "" : " (inactive)"));
            return m.id();
        });
    }

    // ---- categories ----

    public List<Category> listCategories() {
        return categories.findAll();
    }

    public void saveCategory(Category c) {
        Session.requireAdmin();
        require(c.name(), "Category name");
        wrapDuplicate(() -> Jdbc.inTransaction(() -> {
            if (c.id() == 0) {
                int id = categories.insert(c.name().trim(), c.description());
                audit.log("CREATE", "CATEGORY", id, c.name());
            } else {
                categories.update(c.id(), c.name().trim(), c.description());
                audit.log("UPDATE", "CATEGORY", c.id(), c.name());
            }
        }), "A category with this name already exists");
    }

    public void deleteCategory(Category c) {
        Session.requireAdmin();
        if (categories.countMedicines(c.id()) > 0) {
            throw new BusinessException("Category is used by medicines and cannot be deleted");
        }
        Jdbc.inTransaction(() -> {
            categories.delete(c.id());
            audit.log("DELETE", "CATEGORY", c.id(), c.name());
        });
    }

    // ---- suppliers ----

    public List<Supplier> searchSuppliers(String text, boolean activeOnly) {
        return suppliers.search(text, activeOnly);
    }

    public void saveSupplier(Supplier input) {
        Session.requireAdmin();
        require(input.name(), "Supplier name");
        validateEmail(input.email());
        IndianState state = requireState(input.state(), input.gstin(), "the supplier");
        Supplier s = new Supplier(input.id(), input.name(), input.contactPerson(), input.phone(), input.email(),
                input.address(), state, Gst.normaliseGstin(input.gstin(), state, "the supplier"), input.drugLicenseNo(),
                input.active());
        wrapDuplicate(() -> Jdbc.inTransaction(() -> {
            if (s.id() == 0) {
                int id = suppliers.insert(s);
                audit.log("CREATE", "SUPPLIER", id, s.name());
            } else {
                suppliers.update(s);
                audit.log("UPDATE", "SUPPLIER", s.id(), s.name());
            }
        }), "A supplier with this name already exists");
    }

    // ---- customers ----

    public List<Customer> searchCustomers(String text, boolean activeOnly) {
        return customers.search(text, activeOnly);
    }

    public void saveCustomer(Customer input) {
        Session.requireLogin();
        require(input.name(), "Customer name");
        validateEmail(input.email());
        IndianState state = requireState(input.state(), input.gstin(), "the customer");
        Customer c = new Customer(input.id(), input.name(), input.type(), input.contactPerson(), input.phone(),
                input.email(), input.address(), state, Gst.normaliseGstin(input.gstin(), state, "the customer"),
                input.drugLicenseNo(), input.active());
        wrapDuplicate(() -> Jdbc.inTransaction(() -> {
            if (c.id() == 0) {
                int id = customers.insert(c);
                audit.log("CREATE", "CUSTOMER", id, c.name());
            } else {
                customers.update(c);
                audit.log("UPDATE", "CUSTOMER", c.id(), c.name());
            }
        }), "A customer with this name already exists");
    }

    // ---- helpers ----

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " is required");
        }
    }

    /** The state decides CGST/SGST vs IGST, so it is mandatory; it can be taken from the GSTIN when not chosen. */
    private static IndianState requireState(IndianState state, String gstin, String owner) {
        IndianState s = state != null ? state : Gst.stateOfGstin(gstin);
        if (s == null) {
            throw new BusinessException("Select the state of " + owner + " (needed for GST: CGST/SGST or IGST)");
        }
        return s;
    }

    private static void nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException(field + " must be zero or more");
        }
    }

    private static void validateEmail(String email) {
        if (email != null && !email.isBlank() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new BusinessException("Email address is not valid");
        }
    }

    private static void wrapDuplicate(Runnable work, String message) {
        try {
            work.run();
        } catch (DataAccessException e) {
            if (Jdbc.isConstraintViolation(e)) {
                throw new BusinessException(message);
            }
            throw e;
        }
    }
}
