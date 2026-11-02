package com.pharmasync.service;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {

    private static final int COST = 10;

    private PasswordHasher() {
    }

    public static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(COST));
    }

    public static boolean matches(String plain, String hash) {
        if (plain == null || hash == null || !hash.startsWith("$2")) {
            return false;
        }
        return BCrypt.checkpw(plain, hash);
    }

    public static void validateStrength(String plain) {
        if (plain == null || plain.length() < 6) {
            throw new BusinessException("Password must be at least 6 characters");
        }
    }
}
