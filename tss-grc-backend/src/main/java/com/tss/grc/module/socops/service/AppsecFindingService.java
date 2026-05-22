package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.AppsecFinding;
import com.tss.grc.module.socops.repository.AppsecFindingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AppsecFindingService {

    private final AppsecFindingRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<AppsecFinding> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByDiscoveredAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public AppsecFinding get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("AppsecFinding " + id));
    }

    /** Ingest from a scanner connector — idempotent on (scanner, externalId). */
    @Transactional
    public AppsecFinding ingest(AppsecFinding in) {
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        AppsecFinding existing = repo.findByOrganisationIdAndScannerAndExternalId(
            org, in.getScanner(), in.getExternalId()).orElse(null);
        boolean isNew = existing == null;
        if (existing != null) {
            // Refresh mutable fields; preserve status / assignment workflow
            existing.setSeverity(in.getSeverity());
            existing.setCvssScore(in.getCvssScore());
            existing.setFindingTitle(in.getFindingTitle());
            existing.setFilePath(in.getFilePath());
            existing.setLineNumber(in.getLineNumber());
            existing.setFixedVersion(in.getFixedVersion());
            existing.setDescription(in.getDescription());
            existing.setRemediationAdvice(in.getRemediationAdvice());
            existing = repo.save(existing);
        } else {
            if (in.getStatus() == null) in.setStatus("OPEN");
            if (in.getDiscoveredAt() == null) in.setDiscoveredAt(LocalDateTime.now());
            if (in.getSlaDueAt() == null) in.setSlaDueAt(computeSla(in.getDiscoveredAt(), in.getSeverity()));
            existing = repo.save(in);
        }
        audit.log(isNew ? "APPSEC_INGEST" : "APPSEC_REFRESH",
            "AppsecFinding", existing.getId(),
            existing.getScanner() + " · " + existing.getScanType() + " · " + existing.getSeverity());
        return existing;
    }

    /** NCA-aligned application-finding SLA windows. Critical 30d / High 60d
     *  / Medium 90d / Low 180d — mirrors the existing vulnerability lifecycle. */
    private LocalDateTime computeSla(LocalDateTime discovered, String severity) {
        if (discovered == null || severity == null) return null;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> discovered.plusDays(30);
            case "HIGH"     -> discovered.plusDays(60);
            case "MEDIUM"   -> discovered.plusDays(90);
            default          -> discovered.plusDays(180);
        };
    }

    @Transactional
    public AppsecFinding save(AppsecFinding in) {
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        AppsecFinding saved = repo.save(in);
        audit.log(isNew ? "APPSEC_CREATE" : "APPSEC_UPDATE",
            "AppsecFinding", saved.getId(), saved.getFindingTitle());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        AppsecFinding f = get(id);
        f.softDelete(null);
        repo.save(f);
        audit.log("APPSEC_DELETE", "AppsecFinding", id, "Soft-deleted");
    }
}
