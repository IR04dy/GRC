package com.tss.grc.module.fraud.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.fraud.dto.FraudDtos.CaseResponse;
import com.tss.grc.module.fraud.dto.FraudDtos.CaseUpdateResponse;
import com.tss.grc.module.fraud.dto.FraudDtos.CloseCaseRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.CreateCaseRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.DashboardResponse;
import com.tss.grc.module.fraud.dto.FraudDtos.InvestigationUpdateRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.OutcomeRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.TriageRequest;
import com.tss.grc.module.fraud.dto.FraudDtos.TypologyResponse;
import com.tss.grc.module.fraud.entity.FraudCase;
import com.tss.grc.module.fraud.entity.FraudCaseUpdate;
import com.tss.grc.module.fraud.entity.FraudTypology;
import com.tss.grc.module.fraud.repository.FraudCaseRepository;
import com.tss.grc.module.fraud.repository.FraudCaseUpdateRepository;
import com.tss.grc.module.fraud.repository.FraudTypologyRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fraud Risk Management service — Sprint 14.
 *
 * <p>State machine for {@code FraudCase.status}:
 * <pre>
 *   DETECTED → TRIAGE → INVESTIGATING → CONFIRMED|CLEARED|FALSE_POSITIVE → CLOSED
 *   DETECTED → TRIAGE → FALSE_POSITIVE → CLOSED
 * </pre>
 *
 * <h3>F7 / F8 events</h3>
 * Every mutation writes an {@link AuditLogService} row and publishes a
 * {@link DomainEvent} on the Spring {@code ApplicationEventPublisher}
 * for the automation rules engine to consume. Event trigger codes:
 * <ul>
 *   <li>{@code FRAUD_CASE_CREATED} — case opened</li>
 *   <li>{@code FRAUD_CASE_TRIAGED} — triage outcome recorded</li>
 *   <li>{@code FRAUD_INVESTIGATION_UPDATED} — investigator added evidence/finding</li>
 *   <li>{@code FRAUD_CASE_CONFIRMED} — outcome=CONFIRMED set</li>
 *   <li>{@code FRAUD_CASE_CLEARED} — outcome=CLEARED set</li>
 *   <li>{@code FRAUD_CASE_FALSE_POSITIVE} — outcome=FALSE_POSITIVE</li>
 *   <li>{@code FRAUD_STR_FILED} — SAFIU STR filed</li>
 *   <li>{@code FRAUD_LAW_ENFORCEMENT_NOTIFIED} — CID/MHRSD referral</li>
 *   <li>{@code FRAUD_CASE_CLOSED} — case closed</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FraudService {

    // NOTE: TRIAGE is not modelled as a held state — the {@code triage()}
    // method sets {@code triagedAt}/{@code triagedByUserId} on the case and
    // immediately advances DETECTED to INVESTIGATING or FALSE_POSITIVE based
    // on the triage decision. Keeping TRIAGE as a held state would require a
    // second API call per case which is the wrong UX for a single-decision
    // step. The timeline row captures the TRIAGE_DECISION event explicitly.
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "DETECTED",       Set.of("INVESTIGATING", "FALSE_POSITIVE"),
            "INVESTIGATING",  Set.of("CONFIRMED", "CLEARED", "FALSE_POSITIVE"),
            "CONFIRMED",      Set.of("CLOSED"),
            "CLEARED",        Set.of("CLOSED"),
            "FALSE_POSITIVE", Set.of("CLOSED"),
            "CLOSED",         Set.of()
    );

    private final FraudTypologyRepository typologyRepo;
    private final FraudCaseRepository caseRepo;
    private final FraudCaseUpdateRepository updateRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Typology catalog (read-only) ───────────────────────────────

    @Transactional(readOnly = true)
    public List<TypologyResponse> listTypologies() {
        return typologyRepo.findActiveForOrg(org()).stream()
                .map(FraudService::toTypology)
                .toList();
    }

    // ─── Case CRUD + lifecycle ──────────────────────────────────────

    @Transactional
    public CaseResponse createCase(CreateCaseRequest req) {
        Long orgId = org();
        FraudTypology typology = typologyRepo.findById(req.getTypologyId())
                .filter(t -> t.getOrganisationId() == null || t.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fraud typology not found or not visible to tenant: " + req.getTypologyId()));

        FraudCase c = new FraudCase();
        c.setOrganisationId(orgId);
        c.setExternalReference(nextExternalRef(orgId));
        c.setTypologyId(typology.getId());
        c.setTypologyCodeSnapshot(typology.getCode());
        c.setSubjectEn(req.getSubjectEn());
        c.setSubjectAr(req.getSubjectAr());
        c.setNarrativeEn(req.getNarrativeEn());
        c.setNarrativeAr(req.getNarrativeAr());
        c.setDetectionSource(req.getDetectionSource());
        c.setDetectionReference(req.getDetectionReference());
        c.setIncidentDate(req.getIncidentDate());
        c.setEstimatedLossSar(req.getEstimatedLossSar());
        c.setAffectedCustomerCount(req.getAffectedCustomerCount() == null ? 0 : req.getAffectedCustomerCount());
        c.setSeverity(typology.getTypicalSeverity());
        c.setAssignedInvestigatorUserId(req.getAssignedInvestigatorUserId());
        c.setStatus("DETECTED");
        FraudCase saved = caseRepo.save(c);

        recordUpdate(saved, "STATUS_CHANGED", "DETECTED",
                "Case opened from " + saved.getDetectionSource(),
                "تم فتح القضية من " + saved.getDetectionSource(), null);

        audit.log("FRAUD_CASE_CREATED", "FraudCase", saved.getId(),
                "Fraud case opened: " + saved.getExternalReference()
                        + " · typology=" + typology.getCode()
                        + " · severity=" + saved.getSeverity());
        emit("FRAUD_CASE_CREATED", "FraudCase", saved.getId(), saved.getExternalReference(),
                Map.of(
                        "typologyCode", typology.getCode(),
                        "samaCategoryCode", typology.getSamaCategoryCode(),
                        "severity", saved.getSeverity(),
                        "detectionSource", saved.getDetectionSource(),
                        "estimatedLossSar", saved.getEstimatedLossSar() == null ? "0"
                                : saved.getEstimatedLossSar().toPlainString(),
                        "affectedCustomerCount", String.valueOf(saved.getAffectedCustomerCount())));
        return toCase(saved, typology);
    }

    @Transactional
    public CaseResponse triage(Long caseId, TriageRequest req) {
        FraudCase c = loadCase(caseId);
        guardTransition(c.getStatus(), req.getProceedToInvestigation() ? "INVESTIGATING" : "FALSE_POSITIVE");

        // We model TRIAGE as an intermediate state that closes immediately as
        // the triage call sets both the triage timestamp + next status in one shot.
        LocalDateTime now = LocalDateTime.now();
        c.setSeverity(req.getSeverity());
        c.setTriagedAt(now);
        c.setTriagedByUserId(currentUserId());
        if (req.getAssignedInvestigatorUserId() != null) {
            c.setAssignedInvestigatorUserId(req.getAssignedInvestigatorUserId());
        }
        String nextStatus = req.getProceedToInvestigation() ? "INVESTIGATING" : "FALSE_POSITIVE";
        c.setStatus(nextStatus);
        if ("INVESTIGATING".equals(nextStatus)) {
            c.setInvestigationStartedAt(now);
        } else {
            c.setOutcomeSetAt(now);
            c.setOutcomeSetByUserId(currentUserId());
        }
        FraudCase saved = caseRepo.save(c);

        recordUpdate(saved, "TRIAGE_DECISION", nextStatus,
                req.getTriageNotesEn(), req.getTriageNotesAr(), null);

        audit.log("FRAUD_CASE_TRIAGED", "FraudCase", saved.getId(),
                "Triaged " + saved.getExternalReference() + " → " + nextStatus
                        + " · severity=" + saved.getSeverity());
        emit("FRAUD_CASE_TRIAGED", "FraudCase", saved.getId(), saved.getExternalReference(),
                Map.of("nextStatus", nextStatus, "severity", saved.getSeverity()));
        return toCase(saved, null);
    }

    @Transactional
    public CaseUpdateResponse recordInvestigationUpdate(Long caseId, InvestigationUpdateRequest req) {
        FraudCase c = loadCase(caseId);
        if (!"INVESTIGATING".equals(c.getStatus())) {
            throw new IllegalStateException(
                    "Investigation updates only allowed while INVESTIGATING (current: " + c.getStatus() + ")");
        }

        FraudCaseUpdate u = new FraudCaseUpdate();
        u.setOrganisationId(c.getOrganisationId());
        u.setCaseId(c.getId());
        u.setUpdateType(req.getUpdateType());
        u.setActorUserId(currentUserId());
        u.setActorName(currentActorName());
        u.setNoteEn(req.getNoteEn());
        u.setNoteAr(req.getNoteAr());
        u.setEvidenceReference(req.getEvidenceReference());
        u.setStatusAfter(c.getStatus());

        // Side-effects on the case itself.
        switch (req.getUpdateType()) {
            case "STR_FILED" -> {
                c.setStrFiled(true);
                c.setStrReference(req.getStrReference());
                c.setStrFiledAt(LocalDateTime.now());
                caseRepo.save(c);
                audit.log("FRAUD_STR_FILED", "FraudCase", c.getId(),
                        "STR filed for " + c.getExternalReference() + " · ref=" + req.getStrReference());
                emit("FRAUD_STR_FILED", "FraudCase", c.getId(), c.getExternalReference(),
                        Map.of("strReference", Objects.toString(req.getStrReference(), "")));
            }
            case "LAW_ENFORCEMENT_NOTIFIED" -> {
                c.setLawEnforcementEngaged(true);
                c.setLawEnforcementReference(req.getLawEnforcementReference());
                caseRepo.save(c);
                audit.log("FRAUD_LAW_ENFORCEMENT_NOTIFIED", "FraudCase", c.getId(),
                        "Law enforcement notified for " + c.getExternalReference()
                                + " · ref=" + req.getLawEnforcementReference());
                emit("FRAUD_LAW_ENFORCEMENT_NOTIFIED", "FraudCase", c.getId(), c.getExternalReference(),
                        Map.of("lawEnforcementReference",
                                Objects.toString(req.getLawEnforcementReference(), "")));
            }
            case "RECOVERY_RECORDED" -> {
                BigDecimal prev = c.getRecoveredAmountSar() == null ? BigDecimal.ZERO : c.getRecoveredAmountSar();
                BigDecimal add  = req.getRecoveredAmountSar() == null ? BigDecimal.ZERO : req.getRecoveredAmountSar();
                c.setRecoveredAmountSar(prev.add(add));
                caseRepo.save(c);
            }
            default -> { /* EVIDENCE_RECORDED / FINDING_RECORDED — no case-level side-effects */ }
        }

        FraudCaseUpdate saved = updateRepo.save(u);
        audit.log("FRAUD_INVESTIGATION_UPDATED", "FraudCaseUpdate", saved.getId(),
                "Investigation update on " + c.getExternalReference()
                        + " · type=" + saved.getUpdateType());
        emit("FRAUD_INVESTIGATION_UPDATED", "FraudCaseUpdate", saved.getId(), c.getExternalReference(),
                Map.of("caseId", String.valueOf(c.getId()),
                        "updateType", saved.getUpdateType()));
        return toCaseUpdate(saved);
    }

    @Transactional
    public CaseResponse setOutcome(Long caseId, OutcomeRequest req) {
        FraudCase c = loadCase(caseId);
        guardTransition(c.getStatus(), req.getOutcome());

        c.setStatus(req.getOutcome());
        if ("CONFIRMED".equals(req.getOutcome())) {
            c.setConfirmedLossSar(req.getConfirmedLossSar());
        }
        c.setOutcomeSetAt(LocalDateTime.now());
        c.setOutcomeSetByUserId(currentUserId());
        FraudCase saved = caseRepo.save(c);

        recordUpdate(saved, "FINDING_RECORDED", req.getOutcome(),
                req.getFindingsEn(), req.getFindingsAr(), null);

        String trigger = switch (req.getOutcome()) {
            case "CONFIRMED" -> "FRAUD_CASE_CONFIRMED";
            case "CLEARED"   -> "FRAUD_CASE_CLEARED";
            default          -> "FRAUD_CASE_FALSE_POSITIVE";
        };
        audit.log(trigger, "FraudCase", saved.getId(),
                "Outcome on " + saved.getExternalReference() + " = " + req.getOutcome()
                        + (req.getConfirmedLossSar() == null ? ""
                                : " · loss=SAR " + req.getConfirmedLossSar().toPlainString()));
        emit(trigger, "FraudCase", saved.getId(), saved.getExternalReference(),
                Map.of("outcome", req.getOutcome(),
                        "confirmedLossSar", req.getConfirmedLossSar() == null ? "0"
                                : req.getConfirmedLossSar().toPlainString(),
                        "severity", saved.getSeverity()));
        return toCase(saved, null);
    }

    @Transactional
    public CaseResponse closeCase(Long caseId, CloseCaseRequest req) {
        FraudCase c = loadCase(caseId);
        guardTransition(c.getStatus(), "CLOSED");

        c.setStatus("CLOSED");
        c.setClosedAt(LocalDateTime.now());
        c.setClosedByUserId(currentUserId());
        c.setClosureNotesEn(req.getClosureNotesEn());
        c.setClosureNotesAr(req.getClosureNotesAr());
        FraudCase saved = caseRepo.save(c);

        recordUpdate(saved, "CLOSURE", "CLOSED",
                req.getClosureNotesEn(), req.getClosureNotesAr(), null);

        audit.log("FRAUD_CASE_CLOSED", "FraudCase", saved.getId(),
                "Closed " + saved.getExternalReference());
        emit("FRAUD_CASE_CLOSED", "FraudCase", saved.getId(), saved.getExternalReference(),
                Map.of("priorStatus",
                        // The status before this method was called — we already overwrote it,
                        // so derive from the outcome timestamps instead.
                        saved.getConfirmedLossSar() != null ? "CONFIRMED"
                                : saved.getOutcomeSetAt() != null ? "RESOLVED" : "OPEN"));
        return toCase(saved, null);
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CaseResponse> listCases(int page, int size) {
        return caseRepo.findByOrganisationIdOrderByDetectedAtDesc(org(), PageRequest.of(page, size))
                .map(c -> toCase(c, null));
    }

    @Transactional(readOnly = true)
    public CaseResponse getCase(Long caseId) {
        return toCase(loadCase(caseId), null);
    }

    @Transactional(readOnly = true)
    public List<CaseUpdateResponse> listUpdates(Long caseId) {
        FraudCase c = loadCase(caseId);
        return updateRepo.findByCaseIdAndOrganisationIdOrderByOccurredAtDesc(c.getId(), c.getOrganisationId()).stream()
                .map(FraudService::toCaseUpdate)
                .toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Long orgId = org();
        LocalDateTime yearStart = LocalDateTime.of(LocalDateTime.now().getYear(), 1, 1, 0, 0);
        BigDecimal ytdLoss = caseRepo.sumConfirmedLossSince(orgId, yearStart);
        return DashboardResponse.builder()
                .openCases(caseRepo.countOpenByOrg(orgId))
                .detectedCount(caseRepo.countByOrganisationIdAndStatus(orgId, "DETECTED"))
                .investigatingCount(caseRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATING"))
                .confirmedCount(caseRepo.countByOrganisationIdAndStatus(orgId, "CONFIRMED"))
                .closedCount(caseRepo.countByOrganisationIdAndStatus(orgId, "CLOSED"))
                .confirmedLossYtdSar(ytdLoss == null ? BigDecimal.ZERO : ytdLoss)
                .recentCases(caseRepo.findTop10ByOrganisationIdOrderByDetectedAtDesc(orgId).stream()
                        .map(c -> toCase(c, null)).toList())
                .build();
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private FraudCase loadCase(Long id) {
        return caseRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("Fraud case not found: " + id));
    }

    private void guardTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal fraud case transition: " + from + " → " + to + " (allowed: " + allowed + ")");
        }
    }

    private void recordUpdate(FraudCase c, String updateType, String statusAfter,
                               String noteEn, String noteAr, String evidenceRef) {
        FraudCaseUpdate u = new FraudCaseUpdate();
        u.setOrganisationId(c.getOrganisationId());
        u.setCaseId(c.getId());
        u.setUpdateType(updateType);
        u.setActorUserId(currentUserId());
        u.setActorName(currentActorName());
        u.setStatusAfter(statusAfter);
        u.setNoteEn(noteEn);
        u.setNoteAr(noteAr);
        u.setEvidenceReference(evidenceRef);
        updateRepo.save(u);
    }

    /**
     * Generate the next case reference for the current org+month:
     * {@code FR-YYYY-MM-NNNN}.
     */
    private String nextExternalRef(Long orgId) {
        YearMonth ym = YearMonth.now();
        LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay();
        long inMonth = caseRepo.countCasesInMonth(orgId, monthStart, monthEnd);
        return String.format("FR-%04d-%02d-%04d", ym.getYear(), ym.getMonthValue(), inMonth + 1);
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    /**
     * F2-257 — the display half of the actor pair, from the same primitive as {@link
     * #currentUserId()}.
     *
     * <p>Deliberately {@code username()} and not {@code requireUsername(...)}: the id half is
     * nullable and comes from the same place, so both halves are populated together or absent
     * together. Refusing here would let a timeline write abort the business operation it exists to
     * record.
     */
    private static String currentActorName() {
        return com.tss.grc.common.security.CurrentActor.username();
    }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    // ─── DTO mappers ────────────────────────────────────────────────

    private CaseResponse toCase(FraudCase c, FraudTypology typology) {
        if (typology == null) {
            typology = typologyRepo.findById(c.getTypologyId()).orElse(null);
        }
        return CaseResponse.builder()
                .id(c.getId())
                .externalReference(c.getExternalReference())
                .typologyId(c.getTypologyId())
                .typologyCodeSnapshot(c.getTypologyCodeSnapshot())
                .typologyNameEn(typology == null ? null : typology.getNameEn())
                .typologyNameAr(typology == null ? null : typology.getNameAr())
                .subjectEn(c.getSubjectEn())
                .subjectAr(c.getSubjectAr())
                .narrativeEn(c.getNarrativeEn())
                .narrativeAr(c.getNarrativeAr())
                .status(c.getStatus())
                .severity(c.getSeverity())
                .detectionSource(c.getDetectionSource())
                .detectionReference(c.getDetectionReference())
                .incidentDate(c.getIncidentDate())
                .detectedAt(c.getDetectedAt())
                .reportedAt(c.getReportedAt())
                .estimatedLossSar(c.getEstimatedLossSar())
                .confirmedLossSar(c.getConfirmedLossSar())
                .recoveredAmountSar(c.getRecoveredAmountSar())
                .lossCurrency(c.getLossCurrency())
                .affectedCustomerCount(c.getAffectedCustomerCount())
                .assignedInvestigatorUserId(c.getAssignedInvestigatorUserId())
                .triagedAt(c.getTriagedAt())
                .triagedByUserId(c.getTriagedByUserId())
                .investigationStartedAt(c.getInvestigationStartedAt())
                .outcomeSetAt(c.getOutcomeSetAt())
                .outcomeSetByUserId(c.getOutcomeSetByUserId())
                .closedAt(c.getClosedAt())
                .closedByUserId(c.getClosedByUserId())
                .closureNotesEn(c.getClosureNotesEn())
                .closureNotesAr(c.getClosureNotesAr())
                .strFiled(c.getStrFiled())
                .strReference(c.getStrReference())
                .strFiledAt(c.getStrFiledAt())
                .lawEnforcementEngaged(c.getLawEnforcementEngaged())
                .lawEnforcementReference(c.getLawEnforcementReference())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private static CaseUpdateResponse toCaseUpdate(FraudCaseUpdate u) {
        return CaseUpdateResponse.builder()
                .id(u.getId())
                .caseId(u.getCaseId())
                .updateType(u.getUpdateType())
                .occurredAt(u.getOccurredAt())
                .actorUserId(u.getActorUserId())
                .actorName(u.getActorName())
                .noteEn(u.getNoteEn())
                .noteAr(u.getNoteAr())
                .statusAfter(u.getStatusAfter())
                .evidenceReference(u.getEvidenceReference())
                .build();
    }

    private static TypologyResponse toTypology(FraudTypology t) {
        return TypologyResponse.builder()
                .id(t.getId())
                .code(t.getCode())
                .nameEn(t.getNameEn())
                .nameAr(t.getNameAr())
                .descriptionEn(t.getDescriptionEn())
                .descriptionAr(t.getDescriptionAr())
                .samaCategoryCode(t.getSamaCategoryCode())
                .perpetratorOrigin(t.getPerpetratorOrigin())
                .primaryChannel(t.getPrimaryChannel())
                .typicalSeverity(t.getTypicalSeverity())
                .requiresStrFiling(t.getRequiresStrFiling())
                .isActive(t.getIsActive())
                .build();
    }
}
