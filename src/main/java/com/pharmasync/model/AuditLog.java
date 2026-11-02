package com.pharmasync.model;

import java.time.LocalDateTime;

public record AuditLog(int id, LocalDateTime createdAt, String username, String action, String entity,
                       String entityId, String details) {
}
