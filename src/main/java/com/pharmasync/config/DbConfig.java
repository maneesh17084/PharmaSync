package com.pharmasync.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Location of the SQLite database file. Defaults to data/pharmasync.db (relative to the working directory);
 * override with -Dpharmasync.db.file=...
 */
public class DbConfig {

    public static final Path DEFAULT_FILE = Path.of("data", "pharmasync.db");

    /** File name used before the app was renamed from MedStock; moved to {@link #DEFAULT_FILE} on first start. */
    static final Path LEGACY_FILE = Path.of("data", "medstore.db");

    private final Path file;

    public DbConfig(Path file) {
        this.file = file.toAbsolutePath();
    }

    public static DbConfig load() {
        String override = System.getProperty("pharmasync.db.file", System.getProperty("medstore.db.file"));
        if (override != null && !override.isBlank()) {
            return new DbConfig(Path.of(override.trim()));
        }
        migrateLegacyFile(LEGACY_FILE, DEFAULT_FILE);
        return new DbConfig(DEFAULT_FILE);
    }

    /** Renames the old database (and its WAL/SHM side files) when only the old name exists. */
    static void migrateLegacyFile(Path legacy, Path target) {
        if (!Files.exists(legacy) || Files.exists(target)) {
            return;
        }
        try {
            for (String suffix : new String[]{"-wal", "-shm"}) {
                Path side = legacy.resolveSibling(legacy.getFileName() + suffix);
                if (Files.exists(side)) {
                    Files.move(side, target.resolveSibling(target.getFileName() + suffix));
                }
            }
            Files.move(legacy, target);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rename " + legacy + " to " + target, e);
        }
    }

    public Path getFile() {
        return file;
    }

    public String databaseUrl() {
        return "jdbc:sqlite:" + file;
    }
}
