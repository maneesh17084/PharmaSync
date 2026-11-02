package com.pharmasync.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The database file from before the rename (medstore.db) is carried over to pharmasync.db. */
class DbConfigTest {

    @TempDir
    Path dir;

    @Test
    void legacyDatabaseAndSideFilesAreRenamed() throws Exception {
        Path legacy = dir.resolve("medstore.db");
        Path target = dir.resolve("pharmasync.db");
        Files.writeString(legacy, "data");
        Files.writeString(dir.resolve("medstore.db-wal"), "wal");

        DbConfig.migrateLegacyFile(legacy, target);

        assertEquals("data", Files.readString(target));
        assertEquals("wal", Files.readString(dir.resolve("pharmasync.db-wal")));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void existingNewDatabaseIsNeverOverwritten() throws Exception {
        Path legacy = dir.resolve("medstore.db");
        Path target = dir.resolve("pharmasync.db");
        Files.writeString(legacy, "old");
        Files.writeString(target, "new");

        DbConfig.migrateLegacyFile(legacy, target);

        assertEquals("new", Files.readString(target));
        assertEquals("old", Files.readString(legacy));
    }
}
