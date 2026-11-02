package com.pharmasync.service;

import com.pharmasync.db.SqlScriptRunner;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscLogicTest {

    @Test
    void passwordsAreHashedAndVerified() {
        String hash = PasswordHasher.hash("admin123");
        assertTrue(hash.startsWith("$2"));
        assertTrue(PasswordHasher.matches("admin123", hash));
        assertFalse(PasswordHasher.matches("Admin123", hash));
        assertFalse(PasswordHasher.matches("admin123", "plain-text"));
        assertThrows(BusinessException.class, () -> PasswordHasher.validateStrength("12345"));
    }

    @Test
    void documentNumbersAreSequentialPerDay() {
        String prefix = NumberGenerator.prefix("INV", LocalDate.of(2026, 11, 1));
        assertEquals("INV-20261101-", prefix);
        assertEquals("INV-20261101-0001", NumberGenerator.next(prefix, null));
        assertEquals("INV-20261101-0013", NumberGenerator.next(prefix, "INV-20261101-0012"));
    }

    @Test
    void purchaseBatchValidation() {
        LocalDate today = LocalDate.of(2026, 11, 1);
        assertThrows(BusinessException.class,
                () -> PurchaseService.validateBatchInfo("", today.minusDays(10), today.plusDays(100), today, "X"));
        assertThrows(BusinessException.class,
                () -> PurchaseService.validateBatchInfo("B1", today.minusDays(10), today.minusDays(20), today, "X"));
        assertThrows(BusinessException.class, // expired stock cannot be received
                () -> PurchaseService.validateBatchInfo("B1", today.minusDays(400), today.minusDays(1), today, "X"));
        assertThrows(BusinessException.class, // manufactured in the future
                () -> PurchaseService.validateBatchInfo("B1", today.plusDays(1), today.plusDays(400), today, "X"));
        PurchaseService.validateBatchInfo("B1", today.minusDays(10), today.plusDays(400), today, "X");
    }

    @Test
    void sqlScriptsSplitIntoStatements() throws Exception {
        List<String> stmts = SqlScriptRunner.statements("""
                -- comment
                CREATE TABLE a (id INT);

                INSERT INTO a VALUES
                    (1),
                    (2);
                """);
        assertEquals(2, stmts.size());
        assertTrue(stmts.get(1).startsWith("INSERT INTO a"));
        assertFalse(stmts.get(1).endsWith(";"));
    }
}
