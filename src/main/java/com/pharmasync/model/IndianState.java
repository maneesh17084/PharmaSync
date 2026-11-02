package com.pharmasync.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * States and union territories with their GST state codes (the first two digits of a GSTIN).
 * Union territories without a legislature charge UTGST instead of SGST on intra-state supplies.
 */
public enum IndianState {
    JAMMU_AND_KASHMIR("01", "Jammu and Kashmir", false),
    HIMACHAL_PRADESH("02", "Himachal Pradesh", false),
    PUNJAB("03", "Punjab", false),
    CHANDIGARH("04", "Chandigarh", true),
    UTTARAKHAND("05", "Uttarakhand", false),
    HARYANA("06", "Haryana", false),
    DELHI("07", "Delhi", false),
    RAJASTHAN("08", "Rajasthan", false),
    UTTAR_PRADESH("09", "Uttar Pradesh", false),
    BIHAR("10", "Bihar", false),
    SIKKIM("11", "Sikkim", false),
    ARUNACHAL_PRADESH("12", "Arunachal Pradesh", false),
    NAGALAND("13", "Nagaland", false),
    MANIPUR("14", "Manipur", false),
    MIZORAM("15", "Mizoram", false),
    TRIPURA("16", "Tripura", false),
    MEGHALAYA("17", "Meghalaya", false),
    ASSAM("18", "Assam", false),
    WEST_BENGAL("19", "West Bengal", false),
    JHARKHAND("20", "Jharkhand", false),
    ODISHA("21", "Odisha", false),
    CHHATTISGARH("22", "Chhattisgarh", false),
    MADHYA_PRADESH("23", "Madhya Pradesh", false),
    GUJARAT("24", "Gujarat", false),
    DADRA_NAGAR_HAVELI_DAMAN_DIU("26", "Dadra and Nagar Haveli and Daman and Diu", true),
    MAHARASHTRA("27", "Maharashtra", false),
    KARNATAKA("29", "Karnataka", false),
    GOA("30", "Goa", false),
    LAKSHADWEEP("31", "Lakshadweep", true),
    KERALA("32", "Kerala", false),
    TAMIL_NADU("33", "Tamil Nadu", false),
    PUDUCHERRY("34", "Puducherry", false),
    ANDAMAN_AND_NICOBAR("35", "Andaman and Nicobar Islands", true),
    TELANGANA("36", "Telangana", false),
    ANDHRA_PRADESH("37", "Andhra Pradesh", false),
    LADAKH("38", "Ladakh", true);

    private final String code;
    private final String label;
    private final boolean utgst;

    IndianState(String code, String label, boolean utgst) {
        this.code = code;
        this.label = label;
        this.utgst = utgst;
    }

    public String code() { return code; }
    public String label() { return label; }

    /** Name of the state-level component of intra-state GST: SGST, or UTGST for UTs without a legislature. */
    public String stateTaxName() {
        return utgst ? "UTGST" : "SGST";
    }

    public static Optional<IndianState> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.code.equals(code.trim())).findFirst();
    }

    /** Null-tolerant lookup for values read from the database. */
    public static IndianState ofCode(String code) {
        return fromCode(code).orElse(null);
    }

    public static String codeOf(IndianState s) {
        return s == null ? null : s.code;
    }

    @Override
    public String toString() {
        return code + " - " + label;
    }
}
