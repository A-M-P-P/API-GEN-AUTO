package com.apigen.service;

import com.apigen.domain.AuditEvent;
import com.apigen.repository.AuditEventRepository;
import com.apigen.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final SecurityUtils securityUtils;

    public AuditService(AuditEventRepository repository, SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public void record(String action, String entityType, String entityId, String details) {
        record(securityUtils.currentUsername(), action, entityType, entityId, details);
    }

    @Transactional
    public void record(String actor, String action, String entityType, String entityId, String details) {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetails(details);
        repository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> recent(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
