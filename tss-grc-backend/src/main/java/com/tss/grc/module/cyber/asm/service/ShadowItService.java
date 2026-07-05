package com.tss.grc.module.cyber.asm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItBlockRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItSanctionRequest;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceShadowIt;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceShadowItRepository;
import com.tss.grc.module.cyber.service.CyberInputValidation;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shadow-IT discovery service — Sprint 11 ASM.
 *
 * <p>Lifecycle: {@code PENDING_REVIEW → IN_PROGRESS → SANCTIONED |
 * MIGRATED_TO_SANCTIONED | BLOCKED | NA}. {@code SANCTIONED} requires
 * a {@code sanctionedVendorId} pointing at a TPRM vendor row;
 * {@code BLOCKED} is a terminal sink.
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F7 audit_log: SHADOW_IT_DISCOVERED, SHADOW_IT_TRANSITION,
 *       SHADOW_IT_PDPL_ESCALATED</li>
 *   <li>F8 events:
 *     <ul>
 *       <li>{@code SHADOW_IT_DISCOVERED} — every new discovery</li>
 *       <li>{@code SHADOW_IT_SANCTIONED} — promoted to TPRM</li>
 *       <li>{@code SHADOW_IT_BLOCKED} — terminal block</li>
 *       <li>{@code SHADOW_IT_PDPL_FLAG} — discovery flagged with
 *           {@code pdpl_data_processed = true}; the DPO automation
 *           rule subscribes to this trigger.</li>
 *     </ul></li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowItService {

    /** Allowed status transitions. {@code BLOCKED} and {@code NA} are terminal. */
    static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING_REVIEW",          Set.of("IN_PROGRESS", "SANCTIONED", "MIGRATED_TO_SANCTIONED", "BLOCKED", "NA"),
            "IN_PROGRESS",             Set.of("SANCTIONED", "MIGRATED_TO_SANCTIONED", "BLOCKED", "NA"),
            "SANCTIONED",              Set.of(),
            "MIGRATED_TO_SANCTIONED",  Set.of(),
            "BLOCKED",                 Set.of(),
            "NA",                      Set.of()
    );

    /** CY-H — mirror the DB CHECK constraints (CK_asm_shadow_*) so a bad value
     *  returns a clean 400 instead of a 500 from the constraint violation. */
    static final Set<String> DISCOVERED_VIA =
            Set.of("USER_DECLARATION", "NETWORK_TRAFFIC", "EXPENSE_REPORT", "EMAIL_SIGNUPS");
    static final Set<String> DATA_CLASSIFICATIONS =
            Set.of("UNKNOWN", "RESTRICTED", "CONFIDENTIAL", "INTERNAL", "PUBLIC");

    private final AttackSurfaceShadowItRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ShadowItResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByFirstObservedAtDesc(orgId)
                .stream().map(ShadowItResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ShadowItResponse get(UUID externalId) {
        return ShadowItResponse.from(mustGet(externalId));
    }

    // ─── Mutations ────────────────────────────────────────────────

    @Transactional
    public ShadowItResponse recordDiscovery(ShadowItRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        AttackSurfaceShadowIt s = new AttackSurfaceShadowIt();
        s.setOrganisationId(orgId);
        s.setServiceName(req.serviceName());
        s.setServiceNameAr(req.serviceNameAr());
        s.setVendorDomain(req.vendorDomain());
        // CY-H — validate enum-like fields against the DB CHECK sets → 400 on a bad value.
        s.setDiscoveredVia(CyberInputValidation.checkEnum(
                req.discoveredVia(), "discoveredVia", DISCOVERED_VIA, true, null));
        s.setFirstObservedAt(LocalDateTime.now());
        s.setObservedUserCount(req.observedUserCount());
        s.setDataClassification(CyberInputValidation.checkEnum(
                req.dataClassification(), "dataClassification", DATA_CLASSIFICATIONS, false, "UNKNOWN"));
        s.setPdplDataProcessed(Boolean.TRUE.equals(req.pdplDataProcessed()));
        s.setStatus("PENDING_REVIEW");
        s.setAssignedToUserId(req.assignedToUserId());
        s.setDeadline(req.deadline());
        s.setNotesEn(req.notesEn());
        s.setNotesAr(req.notesAr());
        s = repo.save(s);

        audit.log("SHADOW_IT_DISCOVERED", "AttackSurfaceShadowIt", s.getId(),
                "Shadow IT discovered: " + s.getServiceName()
                        + " · via=" + s.getDiscoveredVia()
                        + " · users=" + (s.getObservedUserCount() == null ? "?" : s.getObservedUserCount())
                        + " · pdpl=" + (Boolean.TRUE.equals(s.getPdplDataProcessed()) ? "YES" : "no"));
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "SHADOW_IT_DISCOVERED",
                "AttackSurfaceShadowIt", s.getId(), s.getServiceName(),
                payload(s, null)));

        if (Boolean.TRUE.equals(s.getPdplDataProcessed())) {
            escalatePdpl(s);
        }
        return ShadowItResponse.from(s);
    }

    /**
     * Promote a pending shadow-IT row to {@code SANCTIONED} status and
     * record the link to the TPRM vendor record.
     */
    @Transactional
    public ShadowItResponse sanction(UUID externalId, ShadowItSanctionRequest req) {
        if (req.sanctionedVendorId() == null) {
            throw new IllegalArgumentException("sanctionedVendorId is required to sanction shadow IT");
        }
        AttackSurfaceShadowIt s = mustGet(externalId);
        String from = s.getStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains("SANCTIONED")) {
            throw new IllegalStateException(
                    "Shadow IT cannot be sanctioned from status " + from);
        }
        s.setStatus("SANCTIONED");
        s.setSanctionedVendorId(req.sanctionedVendorId());
        s = repo.save(s);

        audit.log("SHADOW_IT_TRANSITION", "AttackSurfaceShadowIt", s.getId(),
                "Shadow IT " + s.getServiceName() + " " + from + " → SANCTIONED · vendorId="
                        + req.sanctionedVendorId()
                        + (req.note() == null ? "" : " — " + req.note()));
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SHADOW_IT_SANCTIONED",
                "AttackSurfaceShadowIt", s.getId(), s.getServiceName(),
                payload(s, from)));
        return ShadowItResponse.from(s);
    }

    /**
     * Block a shadow-IT entry. Terminal — once blocked, no further
     * transitions are allowed.
     */
    @Transactional
    public ShadowItResponse block(UUID externalId, ShadowItBlockRequest req) {
        AttackSurfaceShadowIt s = mustGet(externalId);
        String from = s.getStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains("BLOCKED")) {
            throw new IllegalStateException(
                    "Shadow IT cannot be blocked from status " + from);
        }
        s.setStatus("BLOCKED");
        s = repo.save(s);

        audit.log("SHADOW_IT_TRANSITION", "AttackSurfaceShadowIt", s.getId(),
                "Shadow IT " + s.getServiceName() + " " + from + " → BLOCKED"
                        + (req == null || req.note() == null ? "" : " — " + req.note()));
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SHADOW_IT_BLOCKED",
                "AttackSurfaceShadowIt", s.getId(), s.getServiceName(),
                payload(s, from)));
        return ShadowItResponse.from(s);
    }

    /** Mark an entry as not applicable (false positive in the discovery pipeline). */
    @Transactional
    public ShadowItResponse markNotApplicable(UUID externalId, String note) {
        AttackSurfaceShadowIt s = mustGet(externalId);
        String from = s.getStatus();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains("NA")) {
            throw new IllegalStateException(
                    "Shadow IT cannot be marked NA from status " + from);
        }
        s.setStatus("NA");
        s = repo.save(s);
        audit.log("SHADOW_IT_TRANSITION", "AttackSurfaceShadowIt", s.getId(),
                "Shadow IT " + s.getServiceName() + " " + from + " → NA"
                        + (note == null ? "" : " — " + note));
        return ShadowItResponse.from(s);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private void escalatePdpl(AttackSurfaceShadowIt s) {
        audit.log("SHADOW_IT_PDPL_ESCALATED", "AttackSurfaceShadowIt", s.getId(),
                "Shadow IT " + s.getServiceName() + " carries PDPL data — DPO review queued.");
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SHADOW_IT_PDPL_FLAG",
                "AttackSurfaceShadowIt", s.getId(), s.getServiceName(),
                payload(s, null)));
    }

    private AttackSurfaceShadowIt mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("AttackSurfaceShadowIt not found: " + externalId));
    }

    private static Map<String, Object> payload(AttackSurfaceShadowIt s, String previousStatus) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("serviceName",         safe(s.getServiceName()));
        p.put("vendorDomain",        safe(s.getVendorDomain()));
        p.put("discoveredVia",       safe(s.getDiscoveredVia()));
        p.put("dataClassification",  safe(s.getDataClassification()));
        p.put("pdplDataProcessed",   Boolean.TRUE.equals(s.getPdplDataProcessed()));
        p.put("status",              safe(s.getStatus()));
        p.put("sanctionedVendorId",  s.getSanctionedVendorId());
        if (previousStatus != null) p.put("previousStatus", previousStatus);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
