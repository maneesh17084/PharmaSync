package com.pharmasync.service;

import com.pharmasync.dao.AuditDao;
import com.pharmasync.dao.UserDao;
import com.pharmasync.db.Jdbc;
import com.pharmasync.model.Role;
import com.pharmasync.model.User;

import java.util.List;
import java.util.regex.Pattern;

/** Login, logout, passwords and user administration (FR-01, FR-02). */
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,50}");

    private final UserDao users;
    private final AuditDao auditDao;
    private final AuditService audit;

    public AuthService(UserDao users, AuditDao auditDao, AuditService audit) {
        this.users = users;
        this.auditDao = auditDao;
        this.audit = audit;
    }

    public User login(String username, String password) {
        String name = username == null ? "" : username.trim();
        User user = users.findByUsername(name).orElse(null);
        if (user == null || !PasswordHasher.matches(password, user.passwordHash())) {
            auditDao.insert(null, name, "LOGIN_FAILED", "USER", null, "Invalid username or password");
            throw new BusinessException("Invalid username or password");
        }
        if (!user.active()) {
            auditDao.insert(user.id(), name, "LOGIN_FAILED", "USER", String.valueOf(user.id()), "Account disabled");
            throw new BusinessException("This account is disabled. Contact an administrator.");
        }
        users.touchLastLogin(user.id());
        Session.start(user);
        audit.log("LOGIN", "USER", user.id(), "Role " + user.role());
        return user;
    }

    public void logout() {
        if (Session.current() != null) {
            audit.log("LOGOUT", "USER", Session.userId(), null);
        }
        Session.end();
    }

    public void changeOwnPassword(String oldPassword, String newPassword) {
        Session.requireLogin();
        User me = users.findById(Session.userId()).orElseThrow();
        if (!PasswordHasher.matches(oldPassword, me.passwordHash())) {
            throw new BusinessException("Current password is incorrect");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException("New password must be different from the current one");
        }
        PasswordHasher.validateStrength(newPassword);
        users.updatePassword(me.id(), PasswordHasher.hash(newPassword), false);
        Session.start(users.findById(me.id()).orElseThrow());
        audit.log("PASSWORD_CHANGE", "USER", me.id(), null);
    }

    public List<User> listUsers() {
        Session.requireAdmin();
        return users.findAll();
    }

    public int createUser(String username, String fullName, Role role, String password) {
        Session.requireAdmin();
        String name = username == null ? "" : username.trim();
        if (!USERNAME.matcher(name).matches()) {
            throw new BusinessException("Username must be 3-50 characters: letters, digits, '_' or '.'");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("Full name is required");
        }
        if (users.findByUsername(name).isPresent()) {
            throw new BusinessException("Username already exists");
        }
        PasswordHasher.validateStrength(password);
        return Jdbc.inTransaction(() -> {
            int id = users.insert(name, PasswordHasher.hash(password), fullName.trim(), role, true);
            audit.log("CREATE", "USER", id, name + " as " + role);
            return id;
        });
    }

    public void updateUser(int id, String fullName, Role role, boolean active) {
        Session.requireAdmin();
        if (fullName == null || fullName.isBlank()) {
            throw new BusinessException("Full name is required");
        }
        User existing = users.findById(id).orElseThrow(() -> new BusinessException("User not found"));
        boolean losesAdmin = existing.role() == Role.ADMIN && existing.active() && (role != Role.ADMIN || !active);
        if (losesAdmin && users.countActiveAdmins() <= 1) {
            throw new BusinessException("At least one active administrator is required");
        }
        if (id == Session.userId() && !active) {
            throw new BusinessException("You cannot disable your own account");
        }
        Jdbc.inTransaction(() -> {
            users.update(id, fullName.trim(), role, active);
            audit.log("UPDATE", "USER", id, existing.username() + ": role=" + role + ", active=" + active);
        });
    }

    public void resetPassword(int id, String newPassword) {
        Session.requireAdmin();
        PasswordHasher.validateStrength(newPassword);
        User existing = users.findById(id).orElseThrow(() -> new BusinessException("User not found"));
        Jdbc.inTransaction(() -> {
            users.updatePassword(id, PasswordHasher.hash(newPassword), true);
            audit.log("PASSWORD_RESET", "USER", id, existing.username());
        });
    }
}
