package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsDao {

    public String get(String key, String fallback) {
        return Jdbc.queryOne("SELECT setting_value FROM settings WHERE setting_key = ?",
                rs -> rs.getString(1), key).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void put(String key, String value) {
        Jdbc.update("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
                + "ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value", key, value);
    }

    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        Jdbc.query("SELECT setting_key, setting_value FROM settings ORDER BY setting_key",
                rs -> map.put(rs.getString(1), rs.getString(2)));
        return map;
    }
}
