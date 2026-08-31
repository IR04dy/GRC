package com.tss.grc.module.compliance.obligation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignCloseRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.CampaignStatsResponse;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.entity.ObligationAttestation;
import com.tss.grc.module.compliance.obligation.entity.ObligationAttestationCampaign;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationCampaignRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 — Obligation attestation campaign service.
 *
 * <p>The Compliance Office launches a quarterly / annual cycle ("Q2
 * 2026 SAMA monthly obligations"); the service materialises one
 * attestation row per matching active obligation, tracks reminder
 * cadence, flips overdue rows, and produces a regulator-ready summary
 * on close.
 *
 * <h3>State machine</h3>
 * <pre>
 *   DRAFT       → OPEN | CANCELLED
 *   OPEN        → IN_PROGRESS | ESCALATED | CLOSED | CANCELLED
 *   IN_PROGRESS → ESCALATED | CLOSED | CANCELLED
 *   ESCALATED   → CLOSED | CANCELLED
 *   CLOSED      (terminal)
 *   CANCELLED   (terminal)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ObligationCampaignService {

    private static final Logger log = LoggerFactory.getLogger(ObligationCampaignService.class);

    public static final Set<String> CAMPAIGN_STATES = Set.of(
        "DRAFT", "OPEN", "IN_PROGRESS", "ESCALATED", "CLOSED", "CANCELLED");

    private final ObligationAttestationCampaignRepository campaignRepo;
    private final ObligationAttestationRepository attestationRepo;
    /** F2-191 — the owner of the attestation lifecycle. The campaign decides WHEN to escalate; it
     *  does not keep its own copy of what OVERDUE does to a cycle. */
    private final ObligationAttestationService attestationService;
    private final ObligationRepository obligationRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    /** F2-385 — the single writer of the campaign status rollups, shared with the attestation
     *  service so every child-status change (record / review / close) recomputes through one path. */
    private final ObligationCampaignRollups rollups;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CampaignResponse> list() {
        return campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org())
            .stream().map(ObligationCampaignService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public CampaignStatsResponse stats(Long id) {
        ObligationAttestationCampaign c = loadOrThrow(id);
        int invited = Optional.ofNullable(c.getTotalInvited()).orElse(0);
        int attested = Optional.ofNullable(c.getTotalAttested()).orElse(0)
            + Optional.ofNullable(c.getTotalAttestedWithExcs()).orElse(0);
        int completionPct = invited == 0 ? 0 : (int) Math.round((attested * 100.0) / invited);
        int daysUntilClose = c.getClosesAt() == null ? 0
            : (int) Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), c.getClosesAt()));
        return new CampaignStatsResponse(
            c.getId(),
            c.getTotalInvited(),
            c.getTotalInProgress(),
            c.getTotalAttested(),
            c.getTotalAttestedWithExcs(),
            c.getTotalOverdue(),
            c.getTotalNa(),
            completionPct,
            daysUntilClose
        );
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest req) {
        Long orgId = org();
        ObligationAttestationCampaign c = new ObligationAttestationCampaign();
        c.setOrganisationId(orgId);
        c.setCampaignCode(req.campaignCode() != null && !req.campaignCode().isBlank()
            ? req.campaignCode() : nextCampaignCode(req.cycleYear(), req.cycleQuarter(), req.regulatorFilter()));
        if (campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(orgId, c.getCampaignCode())) {
            throw new IllegalArgumentException("Campaign code already exists: " + c.getCampaignCode());
        }
        c.setTitleEn(req.titleEn());
        c.setTitleAr(req.titleAr());
        c.setDescriptionEn(req.descriptionEn());
        c.setDescriptionAr(req.descriptionAr());
        c.setCycleYear(req.cycleYear());
        c.setCycleQuarter(blankToNull(req.cycleQuarter()));
        c.setRegulatorFilter(blankToNull(req.regulatorFilter()));
        c.setFrequencyFilter(blankToNull(req.frequencyFilter()));
        c.setClosesAt(req.closesAt());
        c.setReminderCadenceDays(req.reminderCadenceDays() != null ? req.reminderCadenceDays() : 7);
        c.setStatus("DRAFT");

        ObligationAttestationCampaign saved = campaignRepo.save(c);
        audit.log("CREATE", "ObligationAttestationCampaign", saved.getId(),
            "Obligation campaign created: " + saved.getCampaignCode() + " (" + saved.getTitleEn() + ")");
        emit("OBLIGATION_CAMPAIGN_CREATED", saved, null);
        return toResponse(saved);
    }

    /**
     * DRAFT → OPEN. Materialises one attestation row per matching
     * ACTIVE obligation (filtered by regulator and / or frequency).
     */
    @Transactional
    public CampaignResponse openCampaign(Long campaignId) {
        ObligationAttestationCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "OPEN");

        List<Obligation> targets = resolveTargets(c);
        String periodLabel = computePeriodLabel(c);
        LocalDate dueDate = c.getClosesAt() != null ? c.getClosesAt().toLocalDate() : LocalDate.now().plusDays(30);

        LocalDateTime now = LocalDateTime.now();
        List<ObligationAttestation> created = new ArrayList<>(targets.size());
        for (Obligation o : targets) {
            ObligationAttestation a = new ObligationAttestation();
            a.setOrganisationId(c.getOrganisationId());
            a.setObligationId(o.getId());
            a.setCampaignId(c.getId());
            a.setPeriodLabel(periodLabel);
            a.setPeriodStart(periodStart(c));
            a.setPeriodEnd(periodEnd(c));
            a.setDueDate(dueDate);
            a.setAttestationStatus("DUE");
            created.add(attestationRepo.save(a));
        }

        c.setStatus("OPEN");
        c.setOpenedAt(now);
        c.setTotalInvited(created.size());
        c.setUpdatedAt(now);
        ObligationAttestationCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "ObligationAttestationCampaign", saved.getId(),
            "Obligation campaign opened: " + saved.getCampaignCode()
                + " — " + created.size() + " obligations materialised");
        emit("OBLIGATION_CAMPAIGN_OPENED", saved, "DRAFT");
        return toResponse(saved);
    }

    /**
     * Send reminder emails. For v1 we only flip overdue / progress
     * markers and emit an F8 event the rule engine routes; an SMTP
     * channel hook can be wired later without changing the surface.
     */
    @Transactional
    public CampaignResponse sendReminders(Long campaignId) {
        ObligationAttestationCampaign c = loadOrThrow(campaignId);
        // Allow reminders only while attestations are in flight.
        if (!Set.of("OPEN", "IN_PROGRESS").contains(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "REMINDED", Set.of("OPEN", "IN_PROGRESS"));
        }

        List<ObligationAttestation> dueRows = attestationRepo
            .findByOrganisationIdAndCampaignIdAndAttestationStatusInAndIsDeletedFalse(
                c.getOrganisationId(), c.getId(),
                List.of("DUE", "IN_PROGRESS"));

        // Skip rows already past their due_date — those will be handled
        // by escalateOverdue, not the gentle reminder path.
        LocalDate today = LocalDate.now();
        int reminded = 0;
        for (ObligationAttestation a : dueRows) {
            if (a.getDueDate() != null && a.getDueDate().isBefore(today)) continue;
            // Skip already-attested rows defensively (the IN list should already exclude them).
            if ("ATTESTED".equals(a.getAttestationStatus())
                    || "ATTESTED_WITH_EXCEPTIONS".equals(a.getAttestationStatus())) continue;
            reminded++;
        }

        LocalDateTime now = LocalDateTime.now();
        if ("OPEN".equals(c.getStatus())) c.setStatus("IN_PROGRESS");
        c.setUpdatedAt(now);
        ObligationAttestationCampaign saved = campaignRepo.save(c);

        audit.log("REMINDER", "ObligationAttestationCampaign", saved.getId(),
            "Obligation campaign reminder pass: " + reminded + " owner(s) notified ("
                + saved.getCampaignCode() + ")");
        emit("OBLIGATION_CAMPAIGN_REMINDERS_SENT", saved, null,
            Map.of("reminded", String.valueOf(reminded)));
        return toResponse(saved);
    }

    /**
     * Escalate overdue rows: mark DUE / IN_PROGRESS rows whose
     * due_date is in the past as OVERDUE and advance the campaign to
     * ESCALATED. The F8 event is what the rule engine routes to the
     * Audit Committee action-item generator.
     */
    @Transactional
    public CampaignResponse escalateOverdue(Long campaignId) {
        ObligationAttestationCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "ESCALATED");

        // F2-191 — the third copy of this loop, and the one that was silent. It wrote the status with
        // no guard and, unlike the two sweeps, emitted no per-attestation OBLIGATION_OVERDUE event at
        // all: only the campaign-level event fired, so an F8 rule keyed on the row-level trigger saw
        // nothing for campaign-driven escalation. Routing through the attestation service gives each
        // row its guard, its event and the correct previous status.
        LocalDateTime now = LocalDateTime.now();
        // F2-405 — markOverdueForCampaign returns only the rows flipped THIS pass, but the daily
        // ComplianceDeadlineScheduler sweep (sweepOverdueForOrg) also flips this campaign's rows to
        // OVERDUE without touching the rollup, so by escalation time the delta can be 0 while the
        // true OVERDUE population is N. Recompute total_overdue — and the other rollups, including
        // total_in_progress, which the inline write never touched — from the live child rows, the
        // single source of truth, exactly as closeCampaign does (F2-385 / F2-282). Storing the delta
        // left stats() / CampaignResponse, the audit line and the F8 overdueCount all reporting 0
        // overdue on a campaign the sweep had already made N-overdue.
        attestationService.markOverdueForCampaign(c.getId(), LocalDate.now());
        rollups.recompute(c);
        int overdue = c.getTotalOverdue();

        c.setStatus("ESCALATED");
        c.setUpdatedAt(now);
        ObligationAttestationCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "ObligationAttestationCampaign", saved.getId(),
            "Obligation campaign escalated to Audit Committee: " + overdue
                + " overdue (" + saved.getCampaignCode() + ")");
        emit("OBLIGATION_CAMPAIGN_ESCALATED", saved, null,
            Map.of("overdueCount", String.valueOf(overdue)));
        return toResponse(saved);
    }

    @Transactional
    public CampaignResponse closeCampaign(Long campaignId, CampaignCloseRequest req) {
        ObligationAttestationCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "CLOSED");

        rollups.recompute(c);

        LocalDateTime now = LocalDateTime.now();
        c.setStatus("CLOSED");
        c.setClosedAt(now);
        c.setUpdatedAt(now);
        if (req != null) {
            c.setClosingSummaryEn(req.closingSummaryEn());
            c.setClosingSummaryAr(req.closingSummaryAr());
        }
        ObligationAttestationCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "ObligationAttestationCampaign", saved.getId(),
            "Obligation campaign closed: " + saved.getCampaignCode()
                + " — " + (saved.getTotalAttested() + saved.getTotalAttestedWithExcs())
                + "/" + saved.getTotalInvited() + " attested");
        emit("OBLIGATION_CAMPAIGN_CLOSED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public CampaignResponse cancelCampaign(Long campaignId, String reason) {
        ObligationAttestationCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "CANCELLED");
        LocalDateTime now = LocalDateTime.now();
        c.setStatus("CANCELLED");
        c.setClosedAt(now);
        c.setUpdatedAt(now);
        if (reason != null && !reason.isBlank()) c.setClosingSummaryEn(reason);
        ObligationAttestationCampaign saved = campaignRepo.save(c);
        audit.log("TRANSITION", "ObligationAttestationCampaign", saved.getId(),
            "Obligation campaign cancelled: " + saved.getCampaignCode()
                + (reason != null ? " — " + reason : ""));
        emit("OBLIGATION_CAMPAIGN_CANCELLED", saved, null);
        return toResponse(saved);
    }


    // ─── Guards ─────────────────────────────────────────────────────

    static void guardTransition(String from, String to) {
        if (!CAMPAIGN_STATES.contains(to)) {
            throw new IllegalArgumentException("Unknown campaign status: " + to);
        }
        Set<String> allowed = switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"       -> Set.of("OPEN", "CANCELLED");
            case "OPEN"        -> Set.of("IN_PROGRESS", "ESCALATED", "CLOSED", "CANCELLED");
            case "IN_PROGRESS" -> Set.of("ESCALATED", "CLOSED", "CANCELLED");
            case "ESCALATED"   -> Set.of("CLOSED", "CANCELLED");
            default            -> Set.of();
        };
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private ObligationAttestationCampaign loadOrThrow(Long id) {
        return campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + id));
    }

    private List<Obligation> resolveTargets(ObligationAttestationCampaign c) {
        Long orgId = c.getOrganisationId();
        String reg = c.getRegulatorFilter();
        String freq = c.getFrequencyFilter();
        if (reg != null && freq != null) {
            return obligationRepo.findByOrganisationIdAndLifecycleStageAndRegulatorAndFrequencyAndIsDeletedFalse(
                orgId, "ACTIVE", reg, freq);
        }
        if (reg != null) {
            return obligationRepo.findByOrganisationIdAndLifecycleStageAndRegulatorAndIsDeletedFalse(
                orgId, "ACTIVE", reg);
        }
        if (freq != null) {
            return obligationRepo.findByOrganisationIdAndLifecycleStageAndFrequencyAndIsDeletedFalse(
                orgId, "ACTIVE", freq);
        }
        return obligationRepo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByNextDueDateAsc(
            orgId, "ACTIVE");
    }

    private static String computePeriodLabel(ObligationAttestationCampaign c) {
        if (c.getCycleQuarter() != null && !c.getCycleQuarter().isBlank()) {
            return c.getCycleQuarter() + "-" + c.getCycleYear();
        }
        return String.valueOf(c.getCycleYear());
    }

    private static LocalDate periodStart(ObligationAttestationCampaign c) {
        int y = c.getCycleYear();
        String q = c.getCycleQuarter();
        if (q == null || q.isBlank()) return LocalDate.of(y, 1, 1);
        return switch (q) {
            case "Q1" -> LocalDate.of(y, 1, 1);
            case "Q2" -> LocalDate.of(y, 4, 1);
            case "Q3" -> LocalDate.of(y, 7, 1);
            case "Q4" -> LocalDate.of(y, 10, 1);
            default   -> LocalDate.of(y, 1, 1);
        };
    }

    private static LocalDate periodEnd(ObligationAttestationCampaign c) {
        int y = c.getCycleYear();
        String q = c.getCycleQuarter();
        if (q == null || q.isBlank()) return LocalDate.of(y, 12, 31);
        return switch (q) {
            case "Q1" -> LocalDate.of(y, 3, 31);
            case "Q2" -> LocalDate.of(y, 6, 30);
            case "Q3" -> LocalDate.of(y, 9, 30);
            case "Q4" -> LocalDate.of(y, 12, 31);
            default   -> LocalDate.of(y, 12, 31);
        };
    }

    private static String nextCampaignCode(Integer cycleYear, String quarter, String regulator) {
        int year = cycleYear != null ? cycleYear : LocalDate.now().getYear();
        String qPart = (quarter == null || quarter.isBlank()) ? "ANNUAL" : quarter;
        String regPart = (regulator == null || regulator.isBlank()) ? "ALL" : regulator;
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "OBLCAM-" + year + "-" + qPart + "-" + regPart + "-" + suffix;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private void emit(String trigger, ObligationAttestationCampaign c, String previousStatus) {
        emit(trigger, c, previousStatus, Map.of());
    }

    private void emit(String trigger, ObligationAttestationCampaign c, String previousStatus,
                       Map<String, String> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", String.valueOf(c.getId()));
        payload.put("campaignCode", c.getCampaignCode());
        payload.put("status", c.getStatus());
        payload.put("previousStatus", previousStatus == null ? "" : previousStatus);
        payload.put("cycleYear", String.valueOf(c.getCycleYear()));
        payload.put("cycleQuarter", c.getCycleQuarter() == null ? "" : c.getCycleQuarter());
        payload.put("regulatorFilter", c.getRegulatorFilter() == null ? "" : c.getRegulatorFilter());
        payload.put("totalInvited", String.valueOf(c.getTotalInvited()));
        payload.put("totalAttested", String.valueOf(c.getTotalAttested()));
        if (extra != null) payload.putAll(extra);
        publisher.publishEvent(new DomainEvent(
            c.getOrganisationId(), trigger, "ObligationAttestationCampaign", c.getId(),
            "obligation-campaign:" + c.getId(), payload));
    }

    static CampaignResponse toResponse(ObligationAttestationCampaign c) {
        return new CampaignResponse(
            c.getId(), c.getExternalId(), c.getOrganisationId(),
            c.getCampaignCode(), c.getTitleEn(), c.getTitleAr(),
            c.getDescriptionEn(), c.getDescriptionAr(),
            c.getCycleYear(), c.getCycleQuarter(),
            c.getRegulatorFilter(), c.getFrequencyFilter(),
            c.getOpenedAt(), c.getClosesAt(), c.getClosedAt(),
            c.getReminderCadenceDays(), c.getStatus(),
            c.getTotalInvited(), c.getTotalInProgress(),
            c.getTotalAttested(), c.getTotalAttestedWithExcs(),
            c.getTotalOverdue(), c.getTotalNa(),
            c.getClosingSummaryEn(), c.getClosingSummaryAr(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
