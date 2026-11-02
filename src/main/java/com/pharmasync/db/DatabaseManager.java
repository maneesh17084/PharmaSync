package com.pharmasync.db;

import com.pharmasync.config.DbConfig;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;

/** Hands out JDBC connections for the configured SQLite database file. */
public final class DatabaseManager {

    private static volatile DbConfig config = DbConfig.load();

    private DatabaseManager() {
    }

    public static void configure(DbConfig cfg) {
        config = cfg;
    }

    public static DbConfig config() {
        return config;
    }

    /**
     * Opens a connection with foreign keys enforced. Transactions start with BEGIN IMMEDIATE, so a
     * transaction holds the write lock from its first statement; this replaces MySQL's SELECT ... FOR UPDATE.
     */
    public static Connection getConnection() throws SQLException {
        try {
            if (config.getFile().getParent() != null) {
                Files.createDirectories(config.getFile().getParent());
            }
        } catch (IOException e) {
            throw new SQLException("Cannot create folder for " + config.getFile(), e);
        }
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.enforceForeignKeys(true);
        sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqlite.setBusyTimeout(10_000);
        sqlite.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return sqlite.createConnection(config.databaseUrl());
    }

    public static boolean databaseExists() {
        return Files.exists(config.getFile());
    }
}
