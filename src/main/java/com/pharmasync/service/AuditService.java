package com.pharmasync.service;

import com.pharmasync.dao.AuditDao;

/** Records who did what (FR-19). Joins the caller's transaction when one is open. */
public class AuditService {

    private final AuditDao dao;

    public AuditService(AuditDao dao) {
        this.dao = dao;
    }

    public void log(String action, String entity, Object entityId, String details) {
        dao.insert(Session.userId(), Session.username(), action, entity,
                entityId == null ? null : String.valueOf(entityId), details);
    }
}
