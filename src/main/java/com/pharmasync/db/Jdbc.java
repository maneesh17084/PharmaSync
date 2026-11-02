package com.pharmasync.db;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Small JDBC helper. Statements run on the current thread's transaction connection when
 * one is open (see {@link #inTransaction}), otherwise on a short-lived connection.
 * SQLite has no date or decimal types: dates are bound as ISO text and money is read back rounded to 2 places.
 */
public final class Jdbc {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MONEY_SCALE = 2;

    private static final ThreadLocal<Connection> TX = new ThreadLocal<>();

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private Jdbc() {
    }

    /** Runs work in one database transaction: commit on success, rollback on any exception. */
    public static <T> T inTransaction(Supplier<T> work) {
        if (TX.get() != null) {
            return work.get();
        }
        Connection c;
        try {
            c = DatabaseManager.getConnection();
            c.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
        TX.set(c);
        try {
            T result = work.get();
            c.commit();
            return result;
        } catch (RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException(e);
        } finally {
            TX.remove();
            try {
                c.close();
            } catch (SQLException ignored) {
                // connection is discarded anyway
            }
        }
    }

    public static void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    public static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.map(rs));
                    }
                    return out;
                }
            }
        });
    }

    public static <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> list = query(sql, mapper, params);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public static long queryLong(String sql, Object... params) {
        return queryOne(sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    public static BigDecimal queryDecimal(String sql, Object... params) {
        BigDecimal v = queryOne(sql, rs -> getDecimal(rs, 1), params).orElse(null);
        return v == null ? BigDecimal.ZERO : v;
    }

    public static int update(String sql, Object... params) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, params);
                return ps.executeUpdate();
            }
        });
    }

    /** Executes an INSERT and returns the generated key. */
    public static int insert(String sql, Object... params) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, params);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : 0;
                }
            }
        });
    }

    /** Runs a query and returns its columns and rows as-is (used by reports). */
    public static TableData queryTable(String sql, Object... params) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    List<String> cols = new ArrayList<>();
                    List<String> types = new ArrayList<>();
                    for (int i = 1; i <= n; i++) {
                        cols.add(md.getColumnLabel(i));
                        types.add(String.valueOf(md.getColumnTypeName(i)).toUpperCase());
                    }
                    List<Object[]> rows = new ArrayList<>();
                    while (rs.next()) {
                        Object[] row = new Object[n];
                        for (int i = 1; i <= n; i++) {
                            Object v = rs.getObject(i);
                            String type = types.get(i - 1);
                            if (v instanceof String text && type.equals("DATE")) {
                                v = parseDate(text);
                            } else if (v instanceof String text && (type.equals("DATETIME") || type.equals("TIMESTAMP"))) {
                                v = parseDateTime(text);
                            } else if (v instanceof Double d) {
                                v = money(BigDecimal.valueOf(d));
                            }
                            row[i - 1] = v;
                        }
                        rows.add(row);
                    }
                    return new TableData(cols, rows);
                }
            }
        });
    }

    public static LocalDate getDate(ResultSet rs, String col) throws SQLException {
        return parseDate(rs.getString(col));
    }

    public static LocalDateTime getDateTime(ResultSet rs, String col) throws SQLException {
        return parseDateTime(rs.getString(col));
    }

    public static BigDecimal getDecimal(ResultSet rs, String col) throws SQLException {
        return money(rs.getBigDecimal(col));
    }

    public static BigDecimal getDecimal(ResultSet rs, int col) throws SQLException {
        return money(rs.getBigDecimal(col));
    }

    /** True when the error (or its cause) is a UNIQUE, CHECK, NOT NULL or FOREIGN KEY violation. */
    public static boolean isConstraintViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            // SQLITE_CONSTRAINT is 19; extended codes (e.g. 2067 for UNIQUE) keep it in the low byte.
            if (t instanceof SQLException s && (s.getErrorCode() & 0xFF) == 19) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static LocalDate parseDate(String text) {
        return text == null || text.isBlank() ? null : LocalDate.parse(text.substring(0, 10));
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.length() == 10 ? LocalDate.parse(text).atStartOfDay() : LocalDateTime.parse(text.replace(' ', 'T'));
    }

    public static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    @FunctionalInterface
    private interface ConnectionWork<T> {
        T run(Connection c) throws SQLException;
    }

    private static <T> T withConnection(ConnectionWork<T> work) {
        Connection tx = TX.get();
        try {
            if (tx != null) {
                return work.run(tx);
            }
            try (Connection c = DatabaseManager.getConnection()) {
                return work.run(c);
            }
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int idx = i + 1;
            if (p == null) {
                ps.setNull(idx, Types.NULL);
            } else if (p instanceof Enum<?> e) {
                ps.setString(idx, e.name());
            } else if (p instanceof LocalDate d) {
                ps.setString(idx, d.toString());
            } else if (p instanceof LocalDateTime dt) {
                ps.setString(idx, dt.format(DATE_TIME));
            } else if (p instanceof Boolean b) {
                ps.setInt(idx, b ? 1 : 0);
            } else if (p instanceof BigDecimal bd) {
                ps.setDouble(idx, bd.doubleValue());
            } else {
                ps.setObject(idx, p);
            }
        }
    }
}
