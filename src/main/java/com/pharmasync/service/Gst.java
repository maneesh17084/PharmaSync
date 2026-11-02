package com.pharmasync.service;

import com.pharmasync.model.IndianState;
import com.pharmasync.model.SupplyType;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Indian GST rules used for billing: GSTIN format and state check, intra- vs inter-state supply, and
 * splitting the tax into CGST + SGST/UTGST (same state) or IGST (different states).
 */
public final class Gst {

    /** 2-digit state code, 10-character PAN, entity number, 'Z', check character. */
    private static final Pattern GSTIN = Pattern.compile("\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]");

    /** Tax components. For intra-state supplies igst is zero, otherwise cgst and sgst are zero. */
    public record Split(BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
    }

    private Gst() {
    }

    /** Place of supply in the supplier's own state is intra-state; anywhere else is inter-state. */
    public static SupplyType supplyType(IndianState supplierState, IndianState placeOfSupply) {
        if (supplierState == null || placeOfSupply == null) {
            throw new BusinessException("State is required to decide between CGST/SGST and IGST");
        }
        return supplierState == placeOfSupply ? SupplyType.INTRA_STATE : SupplyType.INTER_STATE;
    }

    /** CGST gets half the tax rounded half-up; SGST/UTGST gets the remainder so the parts always add up. */
    public static Split split(BigDecimal tax, SupplyType type) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        if (type == SupplyType.INTER_STATE) {
            return new Split(zero, zero, InvoiceCalculator.round(tax));
        }
        BigDecimal half = InvoiceCalculator.round(tax.divide(BigDecimal.valueOf(2)));
        return new Split(half, InvoiceCalculator.round(tax.subtract(half)), zero);
    }

    /**
     * Normalises a GSTIN (trim, upper case) and checks its format and that its state code matches the given state.
     * Returns null for a blank value.
     */
    public static String normaliseGstin(String gstin, IndianState state, String owner) {
        if (gstin == null || gstin.isBlank()) {
            return null;
        }
        String g = gstin.trim().toUpperCase();
        if (!GSTIN.matcher(g).matches()) {
            throw new BusinessException("GSTIN of " + owner + " must be 15 characters, e.g. 36AABCU9603R1ZM");
        }
        if (state != null && !g.startsWith(state.code())) {
            throw new BusinessException("GSTIN of " + owner + " starts with " + g.substring(0, 2)
                    + " but the state is " + state + ". The first two digits are the GST state code.");
        }
        return g;
    }

    /** State implied by a GSTIN's first two digits, or null. */
    public static IndianState stateOfGstin(String gstin) {
        return gstin == null || gstin.trim().length() < 2 ? null : IndianState.ofCode(gstin.trim().substring(0, 2));
    }
}
