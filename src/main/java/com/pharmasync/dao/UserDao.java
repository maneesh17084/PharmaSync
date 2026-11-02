package com.pharmasync.dao;

import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Role;
import com.pharmasync.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UserDao {

    private static final String SELECT = "SELECT id, username, password_hash, full_name, role, active, "
            + "must_change_password, last_login FROM users";

    private static User map(ResultSet rs) throws SQLException {
        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("full_name"), Role.valueOf(rs.getString("role")), rs.getBoolean("active"),
                rs.getBoolean("must_change_password"), Jdbc.getDateTime(rs, "last_login"));
    }

    public List<User> findAll() {
        return Jdbc.query(SELECT + " ORDER BY username", UserDao::map);
    }

    public Optional<User> findByUsername(String username) {
        return Jdbc.queryOne(SELECT + " WHERE username = ?", UserDao::map, username);
    }

    public Optional<User> findById(int id) {
        return Jdbc.queryOne(SELECT + " WHERE id = ?", UserDao::map, id);
    }

    public long count() {
        return Jdbc.queryLong("SELECT COUNT(*) FROM users");
    }

    public long countActiveAdmins() {
        return Jdbc.queryLong("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = TRUE");
    }

    public int insert(String username, String passwordHash, String fullName, Role role, boolean mustChange) {
        return Jdbc.insert("INSERT INTO users (username, password_hash, full_name, role, must_change_password) "
                + "VALUES (?, ?, ?, ?, ?)", username, passwordHash, fullName, role, mustChange);
    }

    public void update(int id, String fullName, Role role, boolean active) {
        Jdbc.update("UPDATE users SET full_name = ?, role = ?, active = ? WHERE id = ?", fullName, role, active, id);
    }

    public void updatePassword(int id, String passwordHash, boolean mustChange) {
        Jdbc.update("UPDATE users SET password_hash = ?, must_change_password = ? WHERE id = ?",
                passwordHash, mustChange, id);
    }

    public void touchLastLogin(int id) {
        Jdbc.update("UPDATE users SET last_login = datetime('now','localtime') WHERE id = ?", id);
    }
}
