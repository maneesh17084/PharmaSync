package com.pharmasync.service;

import com.pharmasync.model.IndianState;
import com.pharmasync.model.SupplyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GstTest {

    @Test
    void sameStateIsIntraStateOtherwiseInterState() {
        assertEquals(SupplyType.INTRA_STATE, Gst.supplyType(IndianState.TELANGANA, IndianState.TELANGANA));
        assertEquals(SupplyType.INTER_STATE, Gst.supplyType(IndianState.TELANGANA, IndianState.MAHARASHTRA));
        assertThrows(BusinessException.class, () -> Gst.supplyType(IndianState.TELANGANA, null));
    }

    @Test
    void intraStateSplitsIntoHalvesThatAddUp() {
        Gst.Split s = Gst.split(new BigDecimal("18.05"), SupplyType.INTRA_STATE);
        assertEquals(new BigDecimal("9.03"), s.cgst());
        assertEquals(new BigDecimal("9.02"), s.sgst());
        assertEquals(new BigDecimal("0.00"), s.igst());
        assertEquals(new BigDecimal("18.05"), s.cgst().add(s.sgst()));
    }

    @Test
    void interStateIsAllIgst() {
        Gst.Split s = Gst.split(new BigDecimal("18.05"), SupplyType.INTER_STATE);
        assertEquals(new BigDecimal("18.05"), s.igst());
        assertEquals(new BigDecimal("0.00"), s.cgst());
        assertEquals(new BigDecimal("0.00"), s.sgst());
    }

    @Test
    void gstinIsNormalisedAndCheckedAgainstState() {
        assertEquals("36AABCU9603R1ZM", Gst.normaliseGstin(" 36aabcu9603r1zm ", IndianState.TELANGANA, "x"));
        assertNull(Gst.normaliseGstin("  ", IndianState.TELANGANA, "x"));
        assertThrows(BusinessException.class, () -> Gst.normaliseGstin("36AABCU9603R1Z", IndianState.TELANGANA, "x"));
        assertThrows(BusinessException.class, () -> Gst.normaliseGstin("27AABCU9603R1ZM", IndianState.TELANGANA, "x"));
        assertEquals(IndianState.MAHARASHTRA, Gst.stateOfGstin("27AABCU9603R1ZM"));
    }

    @Test
    void unionTerritoriesWithoutLegislatureUseUtgst() {
        assertEquals("UTGST", IndianState.CHANDIGARH.stateTaxName());
        assertEquals("SGST", IndianState.DELHI.stateTaxName());
        assertEquals("SGST", IndianState.TELANGANA.stateTaxName());
    }
}
