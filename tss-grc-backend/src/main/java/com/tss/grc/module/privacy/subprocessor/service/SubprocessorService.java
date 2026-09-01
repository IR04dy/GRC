package com.tss.grc.module.privacy.subprocessor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.LifecycleTransitionRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.MaterialChangeRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorEventRepository;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PDPL sub-processor lifecycle — Sprint 11.
 *
 * <p>State machine (enforced here):
 * <pre>
 *   PROPOSED         → UNDER_DPO_REVIEW | RETIRED
 *   UNDER_DPO_REVIEW → APPROVED | PROPOSED | RETIRED
 *   APPROVED         → SUSPENDED | RETIRED | UNDER_DPO_REVIEW  (material change forces re-review)
 *   SUSPENDED        → APPROVED  | RETIRED
 *   RETIRED          → (terminal)
 * </pre>
 *
 * <p>DPO approval gate ({@link #dpoApprove(UUID, Long)}) enforces:
 * <ol>
 *   <li>{@code hasDpaInPlace}</li>
 *   <li>{@code dpaSignedDate} present</li>
 *   <li>{@code processingPurposeEn} non-blank</li>
 *   <li>{@code crossBorderSafeguardType} set if {@code countryOfProcessing != 'SA'}</li>
 * </ol>
 * Missing keys are listed in the {@link IllegalStateException} so the
 * UI can render the gap directly.
 *
 * <p>F7+F8 events:
 * <ul>
 *   <li>F8 events: {@code SUBPROCESSOR_ADDED},
 *       {@code SUBPROCESSOR_LIFECYCLE_TRANSITION},
 *       {@code SUBPROCESSOR_MATERIAL_CHANGE},
 *       {@code SUBPROCESSOR_REVIEWED},
 *       {@code SUBPROCESSOR_CROSS_BORDER_CHANGED}</li>
 *   <li>F7 audit_log: CREATE, UPDATE, LIFECYCLE_TRANSITION,
 *       MATERIAL_CHANGE, REVIEW_COMPLETED, CROSS_BORDER_CHANGE,
 *       DPO_APPROVE</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubprocessorService {

    /** 12-month review cadence — PDPL Article 30 + SDAIA inspection-readiness norm. */
    static final int REVIEW_CADENCE_MONTHS = 12;

    /** Allowed lifecycle transitions. */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PROPOSED",         Set.of("UNDER_DPO_REVIEW", "RETIRED"),
            "UNDER_DPO_REVIEW", Set.of("APPROVED", "PROPOSED", "RETIRED"),
            "APPROVED",         Set.of("SUSPENDED", "RETIRED", "UNDER_DPO_REVIEW"),
            "SUSPENDED",        Set.of("APPROVED", "RETIRED"),
            "RETIRED",          Set.of()
    );

    private final SubprocessorRepository repo;
    private final SubprocessorEventRepository eventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SubprocessorResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId)
                .stream().map(SubprocessorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SubprocessorResponse get(UUID externalId) {
        return SubprocessorResponse.from(mustGet(externalId));
    }

    @Transactional(readOnly = true)
    public List<SubprocessorResponse> findByCountry(String country) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndCountryOfProcessingAndIsDeletedFalse(orgId, country.toUpperCase())
                .stream().map(SubprocessorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SubprocessorResponse> findOverdueReviews(Long orgId) {
        return repo.findRequiringReview(orgId, LocalDateTime.now())
                .stream().map(SubprocessorResponse::from).toList();
    }

    // ─── Mutations ────────────────────────────────────────────────

    @Transactional
    public SubprocessorResponse create(SubprocessorRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        Subprocessor s = new Subprocessor();
        s.setOrganisationId(orgId);
        s.setSubprocessorCode(req.subprocessorCode() == null || req.subprocessorCode().isBlank()
                ? generateCode(orgId) : req.subprocessorCode());
        applyFields(s, req);
        s.setLifecycleStage("PROPOSED");
        s = repo.save(s);

        // Seed an ADDED event so the ledger has a starting narrative.
        SubprocessorEvent ev = new SubprocessorEvent();
        ev.setOrganisationId(orgId);
        ev.setSubprocessorId(s.getId());
        ev.setEventType("ADDED");
        ev.setDescriptionEn("Sub-processor onboarded: " + safe(s.getSubProcessorLegalName())
                + " (" + safe(s.getCountryOfProcessing()) + ")");
        ev.setDescriptionAr("إدخال المعالج الفرعي: " + safe(s.getSubProcessorLegalNameAr() != null
                ? s.getSubProcessorLegalNameAr() : s.getSubProcessorLegalName())
                + " (" + safe(s.getCountryOfProcessing()) + ")");
        eventRepo.save(ev);

        audit.log("CREATE", "Subprocessor", s.getId(),
                "Sub-processor added: " + s.getSubprocessorCode() + " — "
                        + safe(s.getSubProcessorLegalName())
                        + " · country=" + safe(s.getCountryOfProcessing())
                        + " · lifecycle=PROPOSED");
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "SUBPROCESSOR_ADDED",
                "Subprocessor", s.getId(), s.getSubprocessorCode(),
                payload(s, null)));
        return SubprocessorResponse.from(s);
    }

    @Transactional
    public SubprocessorResponse update(UUID externalId, SubprocessorRequest req) {
        Subprocessor s = mustGet(externalId);
        String previousSafeguard = s.getCrossBorderSafeguardType();
        applyFields(s, req);

        // F2-403: a cross-border safeguard change is material (PDPL Article 30). materialChange()
        // forces re-review on exactly this kind of change, and update() already classifies it as
        // material (it emits SUBPROCESSOR_CROSS_BORDER_CHANGED below). So if the row was
        // DPO-approved, send it back to DPO review here too — mirroring materialChange() — so the
        // new legal mechanism is signed off. Otherwise the APPROVED-only public register
        // (SubprocessorPublicListService, PDPL Article 12) keeps listing it as DPO-approved with the
        // stale dpoApprovedBy/At attribution, defeating the material-change re-review control.
        String newSafeguard = s.getCrossBorderSafeguardType();
        boolean crossBorderChanged = !java.util.Objects.equals(previousSafeguard, newSafeguard);
        boolean forcedReview = crossBorderChanged && "APPROVED".equals(s.getLifecycleStage());
        if (forcedReview) {
            s.setLifecycleStage("UNDER_DPO_REVIEW");
        }
        s = repo.save(s);

        audit.log("UPDATE", "Subprocessor", s.getId(),
                "Sub-processor updated: " + s.getSubprocessorCode());

        // If cross-border safeguard changed, emit the dedicated event.
        if (crossBorderChanged) {
            audit.log("CROSS_BORDER_CHANGE", "Subprocessor", s.getId(),
                    "Cross-border safeguard changed: "
                            + (previousSafeguard == null ? "<none>" : previousSafeguard)
                            + " → " + (newSafeguard == null ? "<none>" : newSafeguard)
                            + (forcedReview ? " — forced re-review (APPROVED → UNDER_DPO_REVIEW)" : ""));
            Map<String, Object> p = payload(s, null);
            p.put("previousSafeguard", previousSafeguard == null ? "" : previousSafeguard);
            eventPublisher.publishEvent(new DomainEvent(
                    s.getOrganisationId(), "SUBPROCESSOR_CROSS_BORDER_CHANGED",
                    "Subprocessor", s.getId(), s.getSubprocessorCode(), p));
            // Record on the ledger too.
            SubprocessorEvent ev = new SubprocessorEvent();
            ev.setOrganisationId(s.getOrganisationId());
            ev.setSubprocessorId(s.getId());
            ev.setEventType("CROSS_BORDER_CHANGE");
            ev.setDescriptionEn("Cross-border safeguard changed: "
                    + (previousSafeguard == null ? "<none>" : previousSafeguard)
                    + " → " + (newSafeguard == null ? "<none>" : newSafeguard));
            eventRepo.save(ev);
        }
        return SubprocessorResponse.from(s);
    }

    /**
     * Apply a lifecycle transition. Throws {@link IllegalStateException}
     * when the transition is not allowed by the state machine.
     */
    @Transactional
    public SubprocessorResponse transitionLifecycle(UUID externalId, LifecycleTransitionRequest req) {
        Subprocessor s = mustGet(externalId);
        String from = s.getLifecycleStage();
        String to = req.targetStage();
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Subprocessor lifecycle transition not allowed: " + from + " → " + to);
        }
        s.setLifecycleStage(to);
        s = repo.save(s);

        // Mirror onto the ledger.
        SubprocessorEvent ev = new SubprocessorEvent();
        ev.setOrganisationId(s.getOrganisationId());
        ev.setSubprocessorId(s.getId());
        ev.setEventType(mapTransitionToEventType(to));
        ev.setDescriptionEn("Lifecycle " + from + " → " + to
                + (req.note() == null ? "" : " — " + req.note()));
        eventRepo.save(ev);

        audit.log("LIFECYCLE_TRANSITION", "Subprocessor", s.getId(),
                "Sub-processor " + s.getSubprocessorCode() + " " + from + " → " + to
                        + (req.note() == null ? "" : " — " + req.note()));
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SUBPROCESSOR_LIFECYCLE_TRANSITION",
                "Subprocessor", s.getId(), s.getSubprocessorCode(),
                payload(s, from)));
        return SubprocessorResponse.from(s);
    }

    /**
     * DPO approves the sub-processor. Validates the gate fields; if
     * any are missing throws {@link IllegalStateException} listing
     * the missing keys so the UI can render the gap.
     */
    @Transactional
    public SubprocessorResponse dpoApprove(UUID externalId, Long approverUserId) {
        Subprocessor s = mustGet(externalId);
        List<String> missing = new ArrayList<>();
        if (s.getHasDpaInPlace() == null || !s.getHasDpaInPlace()) missing.add("hasDpaInPlace");
        if (s.getDpaSignedDate() == null)                          missing.add("dpaSignedDate");
        if (s.getProcessingPurposeEn() == null || s.getProcessingPurposeEn().isBlank())
                                                                    missing.add("processingPurposeEn");
        if (!"SA".equalsIgnoreCase(s.getCountryOfProcessing())
                && (s.getCrossBorderSafeguardType() == null
                    || s.getCrossBorderSafeguardType().isBlank())) {
            missing.add("crossBorderSafeguardType");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "DPO approval blocked — missing required keys: " + String.join(", ", missing));
        }

        String from = s.getLifecycleStage();
        s.setLifecycleStage("APPROVED");
        s.setDpoApprovedAt(LocalDateTime.now());
        s.setDpoApprovedByUserId(approverUserId);
        // First-time approval seeds the review window.
        if (s.getLastReviewedAt() == null) {
            s.setLastReviewedAt(LocalDateTime.now());
        }
        if (s.getNextReviewDueAt() == null) {
            s.setNextReviewDueAt(LocalDateTime.now().plusMonths(REVIEW_CADENCE_MONTHS));
        }
        s = repo.save(s);

        audit.log("DPO_APPROVE", "Subprocessor", s.getId(),
                "DPO approval for " + s.getSubprocessorCode()
                        + " (" + from + " → APPROVED) by user " + approverUserId);
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SUBPROCESSOR_LIFECYCLE_TRANSITION",
                "Subprocessor", s.getId(), s.getSubprocessorCode(),
                payload(s, from)));
        return SubprocessorResponse.from(s);
    }

    /**
     * Record a 12-month review completion. Bumps {@code lastReviewedAt}
     * and computes {@code nextReviewDueAt} = now + 12 months.
     */
    @Transactional
    public SubprocessorResponse recordReview(UUID externalId, Long reviewerUserId) {
        Subprocessor s = mustGet(externalId);
        LocalDateTime now = LocalDateTime.now();
        s.setLastReviewedAt(now);
        s.setNextReviewDueAt(now.plusMonths(REVIEW_CADENCE_MONTHS));
        s = repo.save(s);

        SubprocessorEvent ev = new SubprocessorEvent();
        ev.setOrganisationId(s.getOrganisationId());
        ev.setSubprocessorId(s.getId());
        ev.setEventType("REVIEW_COMPLETED");
        ev.setRecordedByUserId(reviewerUserId);
        ev.setDescriptionEn("12-month review completed by user " + reviewerUserId);
        eventRepo.save(ev);

        audit.log("REVIEW_COMPLETED", "Subprocessor", s.getId(),
                "Review completed for " + s.getSubprocessorCode()
                        + " — next review due " + s.getNextReviewDueAt());
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SUBPROCESSOR_REVIEWED",
                "Subprocessor", s.getId(), s.getSubprocessorCode(),
                payload(s, null)));
        return SubprocessorResponse.from(s);
    }

    /**
     * Record a material change — scope, purpose, or safeguard. Forces
     * the sub-processor back into DPO review so the change is signed
     * off explicitly.
     */
    @Transactional
    public SubprocessorResponse materialChange(UUID externalId, MaterialChangeRequest req) {
        Subprocessor s = mustGet(externalId);
        String from = s.getLifecycleStage();
        // Material change forces re-review regardless of current stage
        // (unless terminal).
        if (!"RETIRED".equals(from)) {
            s.setLifecycleStage("UNDER_DPO_REVIEW");
        }
        s = repo.save(s);

        SubprocessorEvent ev = new SubprocessorEvent();
        ev.setOrganisationId(s.getOrganisationId());
        ev.setSubprocessorId(s.getId());
        ev.setEventType("MATERIAL_CHANGE");
        ev.setDescriptionEn(req.description());
        ev.setDescriptionAr(req.descriptionAr());
        eventRepo.save(ev);

        audit.log("MATERIAL_CHANGE", "Subprocessor", s.getId(),
                "Material change recorded for " + s.getSubprocessorCode()
                        + ": " + safe(req.description()));
        Map<String, Object> p = payload(s, from);
        p.put("changeDescription", safe(req.description()));
        eventPublisher.publishEvent(new DomainEvent(
                s.getOrganisationId(), "SUBPROCESSOR_MATERIAL_CHANGE",
                "Subprocessor", s.getId(), s.getSubprocessorCode(), p));
        return SubprocessorResponse.from(s);
    }

    // ─── Internal helpers ─────────────────────────────────────────

    Subprocessor mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("Subprocessor not found: " + externalId));
    }

    private void applyFields(Subprocessor s, SubprocessorRequest r) {
        if (r.parentProcessorId()      != null) s.setParentProcessorId(r.parentProcessorId());
        if (r.subProcessorLegalName()  != null) s.setSubProcessorLegalName(r.subProcessorLegalName());
        if (r.subProcessorLegalNameAr()!= null) s.setSubProcessorLegalNameAr(r.subProcessorLegalNameAr());
        if (r.vendorId()               != null) s.setVendorId(r.vendorId());
        if (r.countryOfProcessing()    != null) s.setCountryOfProcessing(r.countryOfProcessing().toUpperCase());
        if (r.regionOfProcessing()     != null) s.setRegionOfProcessing(r.regionOfProcessing());
        if (r.dataCategoriesJson()     != null) s.setDataCategoriesJson(r.dataCategoriesJson());
        if (r.dataSubjectCategoriesJson()!= null) s.setDataSubjectCategoriesJson(r.dataSubjectCategoriesJson());
        if (r.processingPurposeEn()    != null) s.setProcessingPurposeEn(r.processingPurposeEn());
        if (r.processingPurposeAr()    != null) s.setProcessingPurposeAr(r.processingPurposeAr());
        if (r.lawfulBasis()            != null) s.setLawfulBasis(r.lawfulBasis());
        if (r.authorisationType()      != null) s.setAuthorisationType(r.authorisationType());
        if (r.authorisationBasisDocRef() != null) s.setAuthorisationBasisDocRef(r.authorisationBasisDocRef());
        if (r.crossBorderSafeguardType() != null) s.setCrossBorderSafeguardType(r.crossBorderSafeguardType());
        if (r.crossBorderSafeguardDocRef() != null) s.setCrossBorderSafeguardDocRef(r.crossBorderSafeguardDocRef());
        if (r.tiaReference()           != null) s.setTiaReference(r.tiaReference());
        if (r.tiaCompletedAt()         != null) s.setTiaCompletedAt(r.tiaCompletedAt());
        if (r.adequacyDecisionRef()    != null) s.setAdequacyDecisionRef(r.adequacyDecisionRef());
        if (r.dataVolumeBand()         != null) s.setDataVolumeBand(r.dataVolumeBand());
        if (r.hasDpaInPlace()          != null) s.setHasDpaInPlace(r.hasDpaInPlace());
        if (r.dpaSignedDate()          != null) s.setDpaSignedDate(r.dpaSignedDate());
        if (r.dpaExpiryDate()          != null) s.setDpaExpiryDate(r.dpaExpiryDate());
    }

    private String generateCode(Long orgId) {
        int year = LocalDateTime.now().getYear();
        String prefix = "SUBP-" + year + "-";
        for (int i = 0; i < 50; i++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format(Locale.ROOT, "%s%03d", prefix, seq);
            if (repo.countByOrganisationIdAndSubprocessorCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        return prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private static String mapTransitionToEventType(String targetStage) {
        return switch (targetStage) {
            case "SUSPENDED" -> "SUSPENDED";
            case "APPROVED"  -> "RESUMED";    // APPROVED after SUSPENDED is a resume
            case "RETIRED"   -> "RETIRED";
            default          -> "MATERIAL_CHANGE";  // for UNDER_DPO_REVIEW / PROPOSED returns
        };
    }

    private static Map<String, Object> payload(Subprocessor s, String previousStage) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("subprocessorCode",          safe(s.getSubprocessorCode()));
        p.put("externalId",                s.getExternalId() == null ? "" : s.getExternalId().toString());
        p.put("subProcessorLegalName",     safe(s.getSubProcessorLegalName()));
        p.put("countryOfProcessing",       safe(s.getCountryOfProcessing()));
        p.put("crossBorderSafeguardType",  safe(s.getCrossBorderSafeguardType()));
        p.put("lifecycleStage",            safe(s.getLifecycleStage()));
        p.put("authorisationType",         safe(s.getAuthorisationType()));
        p.put("dataVolumeBand",            safe(s.getDataVolumeBand()));
        if (previousStage != null) p.put("previousLifecycleStage", previousStage);
        return p;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
