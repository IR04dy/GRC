package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.service.IssueService;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriBreachResponse;
import com.tss.grc.module.risk.kri.entity.KriBreach;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.kri.repository.KriBreachRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Breach acknowledgement + sustained-red CAPA ladder — Sprint 13.
 *
 * <p>Two operator surfaces:
 * <ul>
 *   <li>{@link #acknowledge(UUID, Long)} — operator records that the
 *       breach has been seen and is being actioned. Stops the audit
 *       committee escalation clock for governance reporting.</li>
 *   <li>{@link #close(UUID, Long)} — manual close (rare — usually the
 *       engine closes a breach when GREEN returns).</li>
 * </ul>
 *
 * <p>{@link #checkSustainedRed(KriDefinition)} is invoked from the
 * measurement service after each RED measurement. When the trailing
 * N measurements for the KRI are all RED, the open RED breach is
 * promoted into a platform-level {@link Issue} (auto-CAPA).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KriBreachService {

    /**
     * Number of consecutive RED measurements that, together with the
     * open RED breach, trigger an auto-CAPA Issue. Picked at 3 to
     * match the CCM-engine sustained-failure threshold operators
     * already know.
     */
    static final int SUSTAINED_RED_THRESHOLD = 3;

    private final KriDefinitionRepository defRepo;
    private final KriMeasurementRepository measRepo;
    private final KriBreachRepository breachRepo;
    private final IssueService issueService;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KriBreachResponse> listOpen() {
        Long orgId = TenantContext.getCurrentTenant();
        return breachRepo
                .findByOrganisationIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(orgId)
                .stream()
                .map(b -> KriBreachResponse.from(b, defRepo.findById(b.getKriId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KriBreachResponse> listForKri(UUID kriExternalId) {
        Long orgId = TenantContext.getCurrentTenant();
        KriDefinition d = defRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, kriExternalId)
                .orElseThrow(() -> new EntityNotFoundException("KriDefinition not found: " + kriExternalId));
        return breachRepo
                .findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByOpenedAtDesc(orgId, d.getId())
                .stream()
                .map(b -> KriBreachResponse.from(b, d))
                .toList();
    }

    // ─── Acknowledge + Close ─────────────────────────────────────

    @Transactional
    public KriBreachResponse acknowledge(UUID breachExternalId, Long byUserId) {
        KriBreach b = mustGet(breachExternalId);
        if (b.getClosedAt() != null)
            throw new IllegalStateException("Cannot acknowledge a closed breach");
        if (b.getAcknowledgedAt() != null) {
            // Idempotent — return the row as-is.
            return KriBreachResponse.from(b, defRepo.findById(b.getKriId()).orElse(null));
        }
        b.setAcknowledgedAt(LocalDateTime.now());
        b.setAcknowledgedByUserId(byUserId);
        b = breachRepo.save(b);

        KriDefinition d = defRepo.findById(b.getKriId()).orElse(null);
        audit.log("KRI_BREACH_ACKNOWLEDGED", "KriBreach", b.getId(),
                "KRI breach acknowledged"
                        + (d == null ? "" : " for " + d.getKriCode())
                        + (byUserId == null ? "" : " by user " + byUserId));
        eventPublisher.publishEvent(new DomainEvent(
                b.getOrganisationId(), "KRI_BREACH_ACKNOWLEDGED",
                "KriBreach", b.getId(),
                d == null ? null : "kri:" + d.getKriCode(),
                java.util.Map.of(
                        "breachLevel", safe(b.getBreachLevel()),
                        "acknowledgedAt", b.getAcknowledgedAt().toString())));
        return KriBreachResponse.from(b, d);
    }

    @Transactional
    public KriBreachResponse close(UUID breachExternalId, Long byUserId) {
        KriBreach b = mustGet(breachExternalId);
        if (b.getClosedAt() != null) {
            return KriBreachResponse.from(b, defRepo.findById(b.getKriId()).orElse(null));
        }
        b.setClosedAt(LocalDateTime.now());
        b = breachRepo.save(b);

        KriDefinition d = defRepo.findById(b.getKriId()).orElse(null);
        audit.log("KRI_BREACH_CLOSED", "KriBreach", b.getId(),
                "KRI breach manually closed"
                        + (d == null ? "" : " for " + d.getKriCode())
                        + (byUserId == null ? "" : " by user " + byUserId));
        eventPublisher.publishEvent(new DomainEvent(
                b.getOrganisationId(), "KRI_BREACH_CLOSED",
                "KriBreach", b.getId(),
                d == null ? null : "kri:" + d.getKriCode(),
                java.util.Map.of(
                        "breachLevel", safe(b.getBreachLevel()),
                        "closedAt", b.getClosedAt().toString(),
                        "closedManually", "true")));
        return KriBreachResponse.from(b, d);
    }

    // ─── Sustained-red ladder ─────────────────────────────────────

    /**
     * Visible for testing — called by the measurement service after
     * each RED measurement. Walks the most-recent
     * {@link #SUSTAINED_RED_THRESHOLD} measurements; when all RED,
     * promotes the open RED breach into a platform {@link Issue}.
     *
     * <p>Best-effort: if Issue creation fails the audit log records
     * a WARN but the breach row stays intact. Idempotent: a breach
     * that already has a {@code capa_task_id} is skipped.
     */
    @Transactional
    public Long checkSustainedRed(KriDefinition d) {
        Long orgId = d.getOrganisationId();
        KriBreach open = breachRepo
                .findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                        orgId, d.getId())
                .orElse(null);
        if (open == null || !"RED".equals(open.getBreachLevel())) return null;
        if (open.getCapaTaskId() != null) return open.getCapaTaskId();

        List<KriMeasurement> recent = measRepo
                .findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                        orgId, d.getId(), PageRequest.of(0, SUSTAINED_RED_THRESHOLD));
        if (recent.size() < SUSTAINED_RED_THRESHOLD) return null;
        for (KriMeasurement m : recent) {
            if (!"RED".equals(m.getStatus())) return null;
        }

        try {
            Issue issue = new Issue();
            issue.setOrganisationId(orgId);
            issue.setTitleEn("KRI: " + d.getKriCode() + " sustained RED for "
                    + SUSTAINED_RED_THRESHOLD + " consecutive measurements");
            issue.setTitleAr("مؤشّر المخاطر " + d.getKriCode()
                    + " بحالة حمراء مستمرّة في " + SUSTAINED_RED_THRESHOLD + " قياسات متتالية");
            issue.setDescription("KRI " + safe(d.getKriCode()) + " (" + safe(d.getTitleEn())
                    + ") has been RED for " + SUSTAINED_RED_THRESHOLD + " consecutive measurements. "
                    + "Category: " + safe(d.getCategory()) + ". "
                    + "Latest value: " + (recent.get(0).getValue() == null ? "?" : recent.get(0).getValue().toPlainString()) + ".");
            issue.setSourceModule("risk");
            issue.setSourceEntityType("KriBreach");
            issue.setSourceEntityId(open.getId());
            issue.setSeverity("CRITICAL");
            issue.setPriority("URGENT");
            Issue saved = issueService.create(issue);
            open.setCapaTaskId(saved.getId());
            breachRepo.save(open);
            audit.log("KRI_BREACH_CAPA_OPENED", "KriBreach", open.getId(),
                    "Auto-CAPA opened for sustained RED breach of " + d.getKriCode()
                            + " — Issue#" + saved.getId());
            return saved.getId();
        } catch (RuntimeException ex) {
            log.warn("Failed to auto-open CAPA for KRI breach {}: {}",
                    d.getKriCode(), ex.getMessage());
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private KriBreach mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return breachRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("KriBreach not found: " + externalId));
    }

    static String safe(String v) { return v == null ? "" : v; }
}
