package com.pharmasync.service;

import com.pharmasync.dao.SettingsDao;
import com.pharmasync.db.Jdbc;
import com.pharmasync.model.IndianState;

import java.util.Map;

/** Company details printed on invoices and other store-wide settings. */
public class SettingsService {

    public static final String COMPANY_NAME = "company_name";
    public static final String COMPANY_ADDRESS = "company_address";
    public static final String COMPANY_PHONE = "company_phone";
    public static final String COMPANY_GSTIN = "company_gstin";
    public static final String COMPANY_LICENSE = "company_license";
    public static final String COMPANY_STATE = "company_state";

    /** Name shipped in schema.sql; while it (or a blank GSTIN) remains, documents are printed as samples. */
    public static final String DEMO_COMPANY_NAME = "PharmaSync Demo Pharma (SAMPLE COMPANY)";

    private final SettingsDao dao;
    private final AuditService audit;

    public SettingsService(SettingsDao dao, AuditService audit) {
        this.dao = dao;
        this.audit = audit;
    }

    public String get(String key) {
        return dao.get(key, "");
    }

    public Map<String, String> all() {
        return dao.all();
    }

    /** The company's GST state; sales to the same state are intra-state (CGST + SGST), others IGST. */
    public IndianState companyState() {
        return IndianState.fromCode(get(COMPANY_STATE)).orElseThrow(() -> new BusinessException(
                "Set the company's state in Settings before billing (needed to choose CGST/SGST or IGST)"));
    }

    /** False while the demo company details are still in place; such PDFs are marked as samples. */
    public boolean isCompanyConfigured() {
        return !get(COMPANY_GSTIN).isBlank() && !get(COMPANY_NAME).isBlank()
                && !get(COMPANY_NAME).equals(DEMO_COMPANY_NAME);
    }

    public void saveAll(Map<String, String> values) {
        Session.requireAdmin();
        if (values.containsKey(COMPANY_STATE) || values.containsKey(COMPANY_GSTIN)) {
            IndianState state = IndianState.fromCode(values.getOrDefault(COMPANY_STATE, get(COMPANY_STATE)))
                    .orElseThrow(() -> new BusinessException("Select the company's state"));
            String gstin = Gst.normaliseGstin(values.getOrDefault(COMPANY_GSTIN, get(COMPANY_GSTIN)), state, "the company");
            values.put(COMPANY_GSTIN, gstin == null ? "" : gstin);
        }

        Jdbc.inTransaction(() -> {
            values.forEach((k, v) -> dao.put(k, v == null ? "" : v.trim()));
            audit.log("UPDATE", "SETTINGS", null, String.join(", ", values.keySet()));
        });
    }
}
