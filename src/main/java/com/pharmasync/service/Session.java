package com.pharmasync.service;

import com.pharmasync.model.User;

/** Holds the logged-in user for this desktop session. */
public final class Session {

    private static volatile User current;

    private Session() {
    }

    public static void start(User user) {
        current = user;
    }

    public static void end() {
        current = null;
    }

    public static User current() {
        return current;
    }

    public static Integer userId() {
        return current == null ? null : current.id();
    }

    public static String username() {
        return current == null ? "system" : current.username();
    }

    public static boolean isAdmin() {
        return current != null && current.isAdmin();
    }

    public static void requireLogin() {
        if (current == null) {
            throw new BusinessException("Please log in first");
        }
    }

    public static void requireAdmin() {
        requireLogin();
        if (!current.isAdmin()) {
            throw new BusinessException("This action requires an administrator");
        }
    }
}
