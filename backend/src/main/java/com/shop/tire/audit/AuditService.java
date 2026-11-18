package com.shop.tire.audit;

import com.shop.tire.shared.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditRepository repository;

    AuditService(AuditRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(AuditEntry entry) { repository.append(entry); }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AuditEntry> list(AuditEventType type, int page, int size) {
        return PageResponse.of(repository.list(type, page, size), page, size, repository.count(type));
    }
}
