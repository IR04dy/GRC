package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.DetectionRule;
import com.tss.grc.module.socops.repository.DetectionRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SOC-E — the SOC's detection-rule / use-case library.
 *
 * <p>Every rule must carry a MITRE ATT&amp;CK technique mapping ({@code mitreTechniques}) — that
 * is the whole point of the library: reasoning about ATT&amp;CK coverage and letting a
 * threat-intel action link an IOC to the concrete detection that watches for it. Malformed
 * input fails as {@code 400} (via {@link IllegalArgumentException} → GlobalExceptionHandler)
 * rather than a 500, mirroring the CY-H entity-bound validation convention.
 */
@Service
@RequiredArgsConstructor
public class DetectionRuleService {

    private final DetectionRuleRepository   repo;
    private final AuditLogService           audit;
    private final ApplicationEventPublisher publisher;

    static final Set<String> DETECTION_TYPES = Set.of(
        "SIEM", "EDR", "NDR", "IDS", "IPS", "CORRELATION", "THREAT_HUNT", "UEBA", "SOAR", "CLOUD", "CUSTOM");
    static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "TUNING", "DISABLED", "RETIRED");

    @Transactional(readOnly = true)
    public Page<DetectionRule> list(String technique, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        if (technique != null && !technique.isBlank()) {
            return repo.findByOrganisationIdAndMitreTechniquesContainingIgnoreCaseOrderByCreatedAtDesc(
                org, technique.trim(), pageable);
        }
        return repo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable);
    }

    @Transactional(readOnly = true)
    public DetectionRule get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("DetectionRule " + id));
    }

    @Transactional
    public DetectionRule save(DetectionRule in) {
        Long org = TenantContext.getCurrentTenant();
        validate(in);
        if (in.getId() == null) {
            // Reject a duplicate rule_code up front (400) rather than letting the unique
            // constraint surface as a 500.
            repo.findByOrganisationIdAndRuleCode(org, in.getRuleCode().trim()).ifPresent(existing -> {
                throw new IllegalArgumentException("A detection rule with code '" + in.getRuleCode().trim()
                    + "' already exists");
            });
            // SEC-CYB-002 — force id=null so a forged id can't overwrite an existing row via merge.
            in.setId(null);
            in.setOrganisationId(org);
            normalise(in);
            DetectionRule saved = repo.save(in);
            audit.log("DETECTION_RULE_CREATE", "DetectionRule", saved.getId(),
                saved.getRuleCode() + " · " + saved.getDetectionType() + " · ATT&CK " + saved.getMitreTechniques());
            // F8 — a newly ACTIVE detection is broadcast so the SOC knows new coverage went live.
            publisher.publishEvent(DomainEvent.of(org, "DETECTION_RULE_CREATED", "DetectionRule", saved.getId(),
                Map.of("ruleCode",  saved.getRuleCode(),
                       "type",      saved.getDetectionType(),
                       "technique", saved.getMitreTechniques(),
                       "status",    saved.getStatus() == null ? "" : saved.getStatus())));
            return saved;
        }
        // SEC-CYB-002 — explicit update path: load tenant-scoped, copy only mutable fields.
        DetectionRule existing = repo.findByIdAndOrganisationId(in.getId(), org)
            .orElseThrow(() -> new EntityNotFoundException("DetectionRule " + in.getId()));
        copyMutable(in, existing);
        normalise(existing);
        DetectionRule saved = repo.save(existing);
        audit.log("DETECTION_RULE_UPDATE", "DetectionRule", saved.getId(),
            saved.getRuleCode() + " · " + saved.getStatus());
        return saved;
    }

    private void validate(DetectionRule in) {
        if (in.getRuleCode() == null || in.getRuleCode().isBlank())
            throw new IllegalArgumentException("ruleCode is required");
        if (in.getTitleEn() == null || in.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (in.getDetectionType() == null || !DETECTION_TYPES.contains(in.getDetectionType().toUpperCase(Locale.ROOT)))
            throw new IllegalArgumentException("detectionType must be one of " + DETECTION_TYPES);
        // The whole point of the library — a detection with no ATT&CK technique is rejected.
        if (in.getMitreTechniques() == null || in.getMitreTechniques().isBlank())
            throw new IllegalArgumentException("mitreTechniques is required (ATT&CK technique id, e.g. T1566)");
        if (in.getStatus() != null && !in.getStatus().isBlank()
                && !STATUSES.contains(in.getStatus().toUpperCase(Locale.ROOT)))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
    }

    /** Canonicalise enum-ish fields + default the status so the DB CHECK never trips. */
    private void normalise(DetectionRule d) {
        d.setDetectionType(d.getDetectionType().toUpperCase(Locale.ROOT));
        d.setMitreTechniques(d.getMitreTechniques().trim());
        if (d.getStatus() == null || d.getStatus().isBlank()) d.setStatus("DRAFT");
        else d.setStatus(d.getStatus().toUpperCase(Locale.ROOT));
        if (d.getEnabled() == null) d.setEnabled(true);
    }

    private void copyMutable(DetectionRule in, DetectionRule target) {
        target.setRuleCode(in.getRuleCode());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setDescription(in.getDescription());
        target.setDetectionType(in.getDetectionType());
        target.setPlatform(in.getPlatform());
        target.setLogSource(in.getLogSource());
        target.setDetectionLogic(in.getDetectionLogic());
        target.setMitreTactics(in.getMitreTactics());
        target.setMitreTechniques(in.getMitreTechniques());
        target.setDataSources(in.getDataSources());
        target.setSeverity(in.getSeverity());
        target.setStatus(in.getStatus());
        target.setEnabled(in.getEnabled());
        target.setFalsePositiveRate(in.getFalsePositiveRate());
        target.setAlignedFrameworks(in.getAlignedFrameworks());
        target.setLastTriggeredAt(in.getLastTriggeredAt());
    }

    @Transactional
    public void delete(Long id) {
        DetectionRule d = get(id);
        d.softDelete(null);
        repo.save(d);
        audit.log("DETECTION_RULE_DELETE", "DetectionRule", id, "Soft-deleted " + d.getRuleCode());
    }
}
