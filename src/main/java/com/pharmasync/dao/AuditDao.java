package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.AuditLog;

import java.time.LocalDate;
import java.util.List;

public class AuditDao {

    public void insert(Integer userId, String username, String action, String entity, String entityId, String details) {
        String trimmed = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        Jdbc.update("INSERT INTO audit_logs (user_id, username, action, entity, entity_id, details) VALUES (?,?,?,?,?,?)",
                userId, username, action, entity, entityId, trimmed);
    }

    public List<AuditLog> find(LocalDate from, LocalDate to, String text) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        return Jdbc.query("SELECT * FROM audit_logs WHERE created_at >= ? AND created_at < ? "
                        + "AND (username LIKE ? OR action LIKE ? OR entity LIKE ? OR details LIKE ?) "
                        + "ORDER BY created_at DESC, id DESC LIMIT 2000",
                rs -> new AuditLog(rs.getInt("id"), Jdbc.getDateTime(rs, "created_at"), rs.getString("username"),
                        rs.getString("action"), rs.getString("entity"), rs.getString("entity_id"),
                        rs.getString("details")),
                from, to.plusDays(1), like, like, like, like);
    }
}
