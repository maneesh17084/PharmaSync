package com.pharmasync.model;

import java.time.LocalDateTime;

public record User(int id, String username, String passwordHash, String fullName, Role role,
                   boolean active, boolean mustChangePassword, LocalDateTime lastLogin) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
