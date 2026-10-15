package org.enggis.enventx.api.service;

import lombok.RequiredArgsConstructor;
import org.enggis.enventx.api.domain.entity.AuditLog;
import org.enggis.enventx.api.domain.entity.User;
import org.enggis.enventx.api.domain.enums.AuditAction;
import org.enggis.enventx.api.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository logs;

    /** Mandatory transaction: the audit row commits or rolls back together with the change it describes. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, AuditAction action, String targetType, String targetId, String details) {
        var entry = new AuditLog();
        entry.setActorId(actor.getId());
        entry.setActorEmail(actor.getEmail());
        entry.setAction(action.name());
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        logs.save(entry);
    }
}
