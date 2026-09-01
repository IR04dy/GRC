package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationKpis;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationTransitionRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationUpdateRequest;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 — Obligation register CRUD + lifecycle service.
 *
 * <p>Owns the canonical obligation record. Per-cycle attestations are
 * handled by {@link ObligationAttestationService}; campaign launching
 * by {@link ObligationCampaignService}.
 *
 * <p>Mandated by SAMA Compliance Framework §3.2 and NCA ECC-2 control
 * 1-1. The {@link #computeNextDueDate(Obligation, LocalDate)} helper
 * implements the cycle-rollover math for all nine supported
 * frequencies and is what the campaign materialiser and the
 * post-attest hook call when an obligation cycle closes.
 *
 * <h3>Lifecycle state machine</h3>
 * <pre>
 *   DRAFT     → ACTIVE | RETIRED
 *   ACTIVE    → SUSPENDED | RETIRED
 *   SUSPENDED → ACTIVE | RETIRED
 *   RETIRED   (terminal)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ObligationService {

    private static final Logger log = LoggerFactory.getLogger(ObligationService.class);

    public static final Set<String> LIFECYCLE_STATES = Set.of("DRAFT", "ACTIVE", "SUSPENDED", "RETIRED");

    private final ObligationRepository repo;
    private final ObligationAttestationRepository attestationRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ObligationResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(org())
            .stream().map(ObligationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ObligationResponse> listByRegulator(String regulator) {
        return repo.findByOrganisationIdAndRegulatorAndIsDeletedFalseOrderByNextDueDateAsc(org(), regulator)
            .stream().map(ObligationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ObligationResponse> listByOwner(Long ownerUserId) {
        return repo.findByOrganisationIdAndOwnerUserIdAndIsDeletedFalseOrderByNextDueDateAsc(org(), ownerUserId)
            .stream().map(ObligationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ObligationResponse> listDueWithinDays(int days) {
        LocalDate today = LocalDate.now();
        return repo.findDueWithinDays(org(), today, today.plusDays(days))
            .stream().map(ObligationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ObligationResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * Register KPI tiles for the landing page (active / overdue /
     * due-this-month / attested-this-cycle).
     */
    @Transactional(readOnly = true)
    public ObligationKpis kpis() {
        Long orgId = org();
        long active = repo.countByOrganisationIdAndLifecycleStageAndIsDeletedFalse(orgId, "ACTIVE");

        LocalDate today = LocalDate.now();
        long overdue = repo.findDueWithinDays(orgId, LocalDate.of(1900, 1, 1), today.minusDays(1)).size();

        LocalDate firstOfMonth = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());
        long dueThisMonth = repo.findDueWithinDays(orgId,
            today.isAfter(firstOfMonth) ? today : firstOfMonth, lastOfMonth).size();

        long attestedThisCycle =
            attestationRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "ATTESTED")
            + attestationRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(orgId, "ATTESTED_WITH_EXCEPTIONS");

        return new ObligationKpis(active, overdue, dueThisMonth, attestedThisCycle);
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public ObligationResponse create(ObligationCreateRequest req) {
        Long orgId = org();
        Obligation o = new Obligation();
        o.setOrganisationId(orgId);
        o.setObligationCode(req.obligationCode() != null && !req.obligationCode().isBlank()
            ? req.obligationCode() : nextObligationCode(req.regulator()));
        if (repo.existsByOrganisationIdAndObligationCodeAndIsDeletedFalse(orgId, o.getObligationCode())) {
            throw new IllegalArgumentException("Obligation code already exists: " + o.getObligationCode());
        }
        o.setTitleEn(req.titleEn());
        o.setTitleAr(req.titleAr());
        o.setDescriptionEn(req.descriptionEn());
        o.setDescriptionAr(req.descriptionAr());
        o.setRegulator(req.regulator());
        o.setRegulationReference(req.regulationReference());
        o.setObligationType(req.obligationType());
        o.setFrequency(req.frequency());
        o.setSeverityIfMissed(req.severityIfMissed() != null ? req.severityIfMissed() : "MEDIUM");
        o.setFirstDueDate(req.firstDueDate());
        o.setNextDueDate(req.nextDueDate());
        o.setLeadTimeDays(req.leadTimeDays() != null ? req.leadTimeDays() : 14);
        o.setOwnerUserId(req.ownerUserId());
        o.setBackupOwnerUserId(req.backupOwnerUserId());
        o.setBusinessUnitEn(req.businessUnitEn());
        o.setBusinessUnitAr(req.businessUnitAr());
        o.setRelatedControlId(req.relatedControlId());
        o.setRelatedRiskId(req.relatedRiskId());
        o.setLifecycleStage("DRAFT");

        Obligation saved = repo.save(o);
        audit.log("CREATE", "Obligation", saved.getId(),
            "Obligation created: " + saved.getObligationCode() + " (" + saved.getTitleEn() + ")");
        emit("OBLIGATION_CREATED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public ObligationResponse update(Long id, ObligationUpdateRequest req) {
        Obligation o = loadOrThrow(id);
        if (req.titleEn() != null)             o.setTitleEn(req.titleEn());
        if (req.titleAr() != null)             o.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null)       o.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null)       o.setDescriptionAr(req.descriptionAr());
        if (req.regulator() != null)           o.setRegulator(req.regulator());
        if (req.regulationReference() != null) o.setRegulationReference(req.regulationReference());
        if (req.obligationType() != null)      o.setObligationType(req.obligationType());
        if (req.frequency() != null)           o.setFrequency(req.frequency());
        if (req.severityIfMissed() != null)    o.setSeverityIfMissed(req.severityIfMissed());
        if (req.firstDueDate() != null)        o.setFirstDueDate(req.firstDueDate());
        if (req.nextDueDate() != null)         o.setNextDueDate(req.nextDueDate());
        if (req.leadTimeDays() != null)        o.setLeadTimeDays(req.leadTimeDays());
        if (req.ownerUserId() != null)         o.setOwnerUserId(req.ownerUserId());
        if (req.backupOwnerUserId() != null)   o.setBackupOwnerUserId(req.backupOwnerUserId());
        if (req.businessUnitEn() != null)      o.setBusinessUnitEn(req.businessUnitEn());
        if (req.businessUnitAr() != null)      o.setBusinessUnitAr(req.businessUnitAr());
        if (req.relatedControlId() != null)    o.setRelatedControlId(req.relatedControlId());
        if (req.relatedRiskId() != null)       o.setRelatedRiskId(req.relatedRiskId());
        o.setUpdatedAt(LocalDateTime.now());
        Obligation saved = repo.save(o);
        audit.log("UPDATE", "Obligation", saved.getId(),
            "Obligation updated: " + saved.getObligationCode());
        return toResponse(saved);
    }

    @Transactional
    public ObligationResponse transition(Long id, ObligationTransitionRequest req) {
        Obligation o = loadOrThrow(id);
        String from = o.getLifecycleStage();
        guardLifecycleTransition(from, req.toStage());
        o.setLifecycleStage(req.toStage());
        o.setUpdatedAt(LocalDateTime.now());
        Obligation saved = repo.save(o);
        audit.log("TRANSITION", "Obligation", saved.getId(),
            "Obligation " + saved.getObligationCode() + " " + from + " → " + req.toStage()
                + (req.reason() != null && !req.reason().isBlank() ? " — " + req.reason() : ""));
        emit("OBLIGATION_TRANSITIONED", saved, from);
        return toResponse(saved);
    }

    @Transactional
    public void softDelete(Long id) {
        Obligation o = loadOrThrow(id);
        o.setIsDeleted(true);
        o.setDeletedAt(LocalDateTime.now());
        repo.save(o);
        audit.log("DELETE", "Obligation", id,
            "Obligation soft-deleted: " + o.getObligationCode());
    }

    /**
     * Rolls the obligation forward to its next due date after an
     * attestation has been recorded. Called by
     * {@link ObligationAttestationService} on a successful ATTESTED /
     * ATTESTED_WITH_EXCEPTIONS submission.
     */
    @Transactional
    public Obligation rollForwardAfterAttestation(Long obligationId, Long attestedByUserId, LocalDateTime attestedAt) {
        Obligation o = loadOrThrow(obligationId);
        LocalDate next = computeNextDueDate(o, o.getNextDueDate());
        o.setNextDueDate(next);
        o.setLastAttestedAt(attestedAt);
        o.setLastAttestedByUserId(attestedByUserId);
        o.setUpdatedAt(LocalDateTime.now());
        return repo.save(o);
    }

    // ─── Frequency math ─────────────────────────────────────────────

    /**
     * Computes the next cycle's due date given an obligation and the
     * date the current cycle was due. The 9 supported frequencies map
     * to plain calendar arithmetic; ON_EVENT and AD_HOC return the
     * same date (next cycle is driven by external trigger, not the
     * calendar).
     */
    public LocalDate computeNextDueDate(Obligation o, LocalDate fromDate) {
        Objects.requireNonNull(o, "obligation");
        LocalDate base = fromDate != null ? fromDate : LocalDate.now();
        return switch (o.getFrequency()) {
            case "DAILY"        -> base.plusDays(1);
            case "WEEKLY"       -> base.plusWeeks(1);
            case "MONTHLY"      -> base.plusMonths(1);
            case "QUARTERLY"    -> base.plusMonths(3);
            case "SEMI_ANNUAL"  -> base.plusMonths(6);
            case "ANNUAL"       -> base.plusYears(1);
            case "BIENNIAL"     -> base.plusYears(2);
            case "ON_EVENT", "AD_HOC" -> base; // event-driven; service updates next_due_date when the next event registers.
            default -> throw new IllegalArgumentException("Unsupported frequency: " + o.getFrequency());
        };
    }

    // ─── Guards ─────────────────────────────────────────────────────

    static void guardLifecycleTransition(String from, String to) {
        if (!LIFECYCLE_STATES.contains(to)) {
            throw new IllegalArgumentException("Unknown lifecycle stage: " + to);
        }
        Set<String> allowed = switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"     -> Set.of("ACTIVE", "RETIRED");
            case "ACTIVE"    -> Set.of("SUSPENDED", "RETIRED");
            case "SUSPENDED" -> Set.of("ACTIVE", "RETIRED");
            default          -> Set.of();
        };
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private Obligation loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Obligation not found: " + id));
    }

    private static String nextObligationCode(String regulator) {
        int year = LocalDate.now().getYear();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return "OBL-" + regulator + "-" + year + "-" + suffix;
    }

    private void emit(String trigger, Obligation o, String previousStage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("obligationId", String.valueOf(o.getId()));
        payload.put("obligationCode", o.getObligationCode());
        payload.put("regulator", o.getRegulator());
        payload.put("obligationType", o.getObligationType());
        payload.put("frequency", o.getFrequency());
        payload.put("severityIfMissed", o.getSeverityIfMissed());
        payload.put("lifecycleStage", o.getLifecycleStage());
        payload.put("previousLifecycleStage", previousStage == null ? "" : previousStage);
        payload.put("ownerUserId", String.valueOf(o.getOwnerUserId()));
        payload.put("nextDueDate", String.valueOf(o.getNextDueDate()));
        publisher.publishEvent(new DomainEvent(
            o.getOrganisationId(), trigger, "Obligation", o.getId(),
            "obligation:" + o.getId(), payload));
    }

    static ObligationResponse toResponse(Obligation o) {
        return new ObligationResponse(
            o.getId(), o.getExternalId(), o.getOrganisationId(),
            o.getObligationCode(), o.getTitleEn(), o.getTitleAr(),
            o.getDescriptionEn(), o.getDescriptionAr(),
            o.getRegulator(), o.getRegulationReference(),
            o.getObligationType(), o.getFrequency(), o.getSeverityIfMissed(),
            o.getFirstDueDate(), o.getNextDueDate(), o.getLeadTimeDays(),
            o.getOwnerUserId(), o.getBackupOwnerUserId(),
            o.getBusinessUnitEn(), o.getBusinessUnitAr(),
            o.getRelatedControlId(), o.getRelatedRiskId(),
            o.getLifecycleStage(),
            o.getLastAttestedAt(), o.getLastAttestedByUserId(),
            o.getCreatedAt(), o.getUpdatedAt()
        );
    }
}
