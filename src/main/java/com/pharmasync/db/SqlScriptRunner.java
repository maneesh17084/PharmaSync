package com.pharmasync.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Runs a classpath SQL script whose statements end with a semicolon at the end of a line. */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    public static void run(Connection c, String resource) throws SQLException, IOException {
        try (Statement st = c.createStatement()) {
            for (String sql : statements(load(resource))) {
                st.execute(sql);
            }
        }
    }

    static String load(String resource) throws IOException {
        try (InputStream in = SqlScriptRunner.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static List<String> statements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                out.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            out.add(current.toString().trim());
        }
        return out;
    }
}
