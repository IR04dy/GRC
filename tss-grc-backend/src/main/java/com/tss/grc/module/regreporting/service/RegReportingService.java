package com.tss.grc.module.regreporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.CalendarResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.CreateObligationRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.DashboardResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.ExemptFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.FilingEventResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.FilingResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.GenerateFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.ObligationResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.RejectFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.SubmitFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.TransitionFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.UpdateFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.UpdateObligationRequest;
import com.tss.grc.module.regreporting.entity.RegReportEvent;
import com.tss.grc.module.regreporting.entity.RegReportFiling;
import com.tss.grc.module.regreporting.entity.RegReportObligation;
import com.tss.grc.module.regreporting.repository.RegReportEventRepository;
import com.tss.grc.module.regreporting.repository.RegReportFilingRepository;
import com.tss.grc.module.regreporting.repository.RegReportObligationRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mandatory Regulatory Reporting Calendar service — Sprint 16 PR #1.
 *
 * <p>Manages the obligation catalog + generates filing cycles + runs the
 * 9-state filing lifecycle with F7 audit + F8 emission throughout.
 *
 * <p>State machine for {@link RegReportFiling}:
 * <pre>
 *   SCHEDULED → IN_PREPARATION → IN_REVIEW → APPROVED →
 *   SUBMITTED → ACKNOWLEDGED → CLOSED
 *
 *   IN_REVIEW   ↩  IN_PREPARATION    (sent back for rework)
 *   APPROVED    ↩  IN_PREPARATION    (last-mile fix)
 *   SUBMITTED   →  REJECTED → IN_PREPARATION   (regulator pushed back)
 *   *           →  EXEMPTED   (formal waiver, terminal)
 * </pre>
 *
 * <p>F7+F8 codes:
 * <ul>
 *   <li>REG_REPORT_OBLIGATION_CREATED / UPDATED</li>
 *   <li>REG_REPORT_FILING_SCHEDULED — fresh cycle</li>
 *   <li>REG_REPORT_FILING_STATE_CHANGED</li>
 *   <li>REG_REPORT_FILING_SUBMITTED</li>
 *   <li>REG_REPORT_FILING_REJECTED</li>
 *   <li>REG_REPORT_FILING_EXEMPTED</li>
 *   <li>REG_REPORT_FILING_CLOSED</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RegReportingService {

    /** Allowed transitions via generic /transition endpoint. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "SCHEDULED",       Set.of("IN_PREPARATION", "EXEMPTED"),
            "IN_PREPARATION",  Set.of("IN_REVIEW", "EXEMPTED"),
            "IN_REVIEW",       Set.of("APPROVED", "IN_PREPARATION", "EXEMPTED"),
            "APPROVED",        Set.of("IN_PREPARATION"),
            "SUBMITTED",       Set.of("ACKNOWLEDGED"),
            "ACKNOWLEDGED",    Set.of("CLOSED"),
            "REJECTED",        Set.of("IN_PREPARATION"),
            "CLOSED",          Set.of(),
            "EXEMPTED",        Set.of());

    /** Terminal states — the declared states with no outgoing transition. Derived from the
     *  machine, never typed (F2-196/F2-343), so it cannot drift from {@link #ALLOWED}. */
    private static final Set<String> TERMINAL_STATUSES = ALLOWED.entrySet().stream()
            .filter(e -> e.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** "In flight" = every declared state that is not terminal — the COMPLEMENT of the terminal
     *  set, not a hand-typed subset. A hand-typed list had dropped ACKNOWLEDGED (a filing the
     *  regulator has acknowledged but that is not yet CLOSED — {@code ACKNOWLEDGED → CLOSED}),
     *  so an acknowledged-awaiting-closure filing was absent from the in-flight count and could
     *  never appear in the by-status breakdown (F2-364). */
    private static final Set<String> IN_FLIGHT_STATUSES = ALLOWED.keySet().stream()
            .filter(s -> !TERMINAL_STATUSES.contains(s))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** Statuses where overdue applies — not submitted/closed/exempted. */
    private static final Set<String> OVERDUE_APPLICABLE = Set.of(
            "SCHEDULED", "IN_PREPARATION", "IN_REVIEW",
            "APPROVED", "REJECTED");

    private final RegReportObligationRepository obligationRepo;
    private final RegReportFilingRepository filingRepo;
    private final RegReportEventRepository eventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Obligation writes ─────────────────────────────────────────

    @Transactional
    public ObligationResponse createObligation(CreateObligationRequest req) {
        obligationRepo.findByCodeAndOrganisationId(req.getCode(), org())
                .ifPresent(x -> {
                    throw new IllegalStateException(
                            "Obligation with code " + req.getCode() + " already exists");
                });

        RegReportObligation o = new RegReportObligation();
        o.setOrganisationId(org());
        o.setCode(req.getCode());
        o.setRegulator(req.getRegulator());
        o.setFrequency(req.getFrequency());
        o.setTitleEn(req.getTitleEn());
        o.setTitleAr(req.getTitleAr());
        o.setDescriptionEn(req.getDescriptionEn());
        o.setDescriptionAr(req.getDescriptionAr());
        o.setDueOffsetBusinessDays(req.getDueOffsetBusinessDays());
        o.setStatutoryBasis(req.getStatutoryBasis());
        o.setOwnerRole(req.getOwnerRole());
        o.setReviewerRole(req.getReviewerRole());
        o.setFilingChannel(req.getFilingChannel());
        if (req.getReminderLadder() != null && !req.getReminderLadder().isBlank()) {
            o.setReminderLadder(req.getReminderLadder());
        }
        o.setActive(true);

        RegReportObligation saved = obligationRepo.save(o);
        audit.log("REG_REPORT_OBLIGATION_CREATED", "RegReportObligation",
                saved.getId(),
                "Obligation " + saved.getCode() + " (" + saved.getRegulator() + " "
                        + saved.getFrequency() + ") added to catalog");
        emit("REG_REPORT_OBLIGATION_CREATED", "RegReportObligation",
                saved.getId(), saved.getCode(),
                Map.of("code", saved.getCode(),
                        "regulator", saved.getRegulator(),
                        "frequency", saved.getFrequency()));
        return toObligation(saved);
    }

    @Transactional
    public ObligationResponse updateObligation(Long id, UpdateObligationRequest req) {
        RegReportObligation o = loadObligation(id);
        if (req.getTitleEn() != null) o.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null) o.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null) o.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null) o.setDescriptionAr(req.getDescriptionAr());
        if (req.getDueOffsetBusinessDays() != null)
            o.setDueOffsetBusinessDays(req.getDueOffsetBusinessDays());
        if (req.getStatutoryBasis() != null) o.setStatutoryBasis(req.getStatutoryBasis());
        if (req.getOwnerRole() != null) o.setOwnerRole(req.getOwnerRole());
        if (req.getReviewerRole() != null) o.setReviewerRole(req.getReviewerRole());
        if (req.getFilingChannel() != null) o.setFilingChannel(req.getFilingChannel());
        if (req.getReminderLadder() != null) o.setReminderLadder(req.getReminderLadder());
        if (req.getActive() != null) o.setActive(req.getActive());
        RegReportObligation saved = obligationRepo.save(o);
        audit.log("REG_REPORT_OBLIGATION_UPDATED", "RegReportObligation",
                saved.getId(), "Obligation " + saved.getCode() + " updated");
        return toObligation(saved);
    }

    // ─── Filing writes ─────────────────────────────────────────────

    @Transactional
    public FilingResponse generateFiling(Long obligationId, GenerateFilingRequest req) {
        RegReportObligation o = loadObligation(obligationId);
        if (!Boolean.TRUE.equals(o.getActive())) {
            throw new IllegalStateException(
                    "Obligation " + o.getCode() + " is inactive — cannot generate filing");
        }

        LocalDate periodEnd = req.getPeriodEnd() != null
                ? req.getPeriodEnd()
                : nextPeriodEnd(o);
        LocalDate periodStart = periodStartFor(o.getFrequency(), periodEnd);
        LocalDate dueDate = req.getDueDate() != null
                ? req.getDueDate()
                : addBusinessDays(periodEnd, o.getDueOffsetBusinessDays() == null
                        ? 5 : o.getDueOffsetBusinessDays());

        RegReportFiling f = new RegReportFiling();
        f.setOrganisationId(org());
        f.setObligationId(o.getId());
        f.setReference(buildReference(o, periodEnd));
        f.setStatus("SCHEDULED");
        f.setPeriodStart(periodStart);
        f.setPeriodEnd(periodEnd);
        f.setDueDate(dueDate);
        f.setInternalTargetDate(addBusinessDays(dueDate, -2));
        f.setPreparerUserId(req.getPreparerUserId());
        f.setReviewerUserId(req.getReviewerUserId());
        f.setApproverUserId(req.getApproverUserId());

        RegReportFiling saved = filingRepo.save(f);
        audit.log("REG_REPORT_FILING_SCHEDULED", "RegReportFiling", saved.getId(),
                "Filing " + saved.getReference() + " scheduled, due "
                        + saved.getDueDate());
        emit("REG_REPORT_FILING_SCHEDULED", "RegReportFiling",
                saved.getId(), saved.getReference(),
                Map.of("obligationId", String.valueOf(o.getId()),
                        "code", o.getCode(),
                        "regulator", o.getRegulator(),
                        "frequency", o.getFrequency(),
                        "periodEnd", String.valueOf(saved.getPeriodEnd()),
                        "dueDate", String.valueOf(saved.getDueDate())));
        timeline(saved.getId(), "STATUS_CHANGED", "SCHEDULED",
                "Filing cycle opened for period " + periodStart + " → " + periodEnd
                        + ", due " + dueDate,
                "فتح دورة التقديم للفترة " + periodStart + " - " + periodEnd
                        + "، تاريخ الاستحقاق " + dueDate);
        return toFiling(saved, o);
    }

    @Transactional
    public FilingResponse updateFiling(Long id, UpdateFilingRequest req) {
        RegReportFiling f = loadFiling(id);
        if (req.getInternalTargetDate() != null)
            f.setInternalTargetDate(req.getInternalTargetDate());
        if (req.getPreparerUserId() != null) f.setPreparerUserId(req.getPreparerUserId());
        if (req.getReviewerUserId() != null) f.setReviewerUserId(req.getReviewerUserId());
        if (req.getApproverUserId() != null) f.setApproverUserId(req.getApproverUserId());
        if (req.getPreparationNotesEn() != null)
            f.setPreparationNotesEn(req.getPreparationNotesEn());
        if (req.getPreparationNotesAr() != null)
            f.setPreparationNotesAr(req.getPreparationNotesAr());
        if (req.getFilingArtifactReference() != null)
            f.setFilingArtifactReference(req.getFilingArtifactReference());
        RegReportFiling saved = filingRepo.save(f);
        audit.log("REG_REPORT_FILING_UPDATED", "RegReportFiling",
                saved.getId(), "Filing " + saved.getReference() + " metadata updated");
        return toFiling(saved, loadObligation(saved.getObligationId()));
    }

    @Transactional
    public FilingResponse transitionFiling(Long id, TransitionFilingRequest req) {
        RegReportFiling f = loadFiling(id);
        String from = f.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        // Submit / Reject / Exempt have dedicated endpoints — disallow here.
        if (Set.of("SUBMITTED", "REJECTED", "EXEMPTED").contains(to)) {
            throw new IllegalStateException(
                    "Use the dedicated endpoint to move to " + to);
        }
        f.setStatus(to);
        if ("CLOSED".equals(to)) {
            f.setClosedAt(LocalDateTime.now());
        }
        RegReportFiling saved = filingRepo.save(f);

        String trigger = "CLOSED".equals(to)
                ? "REG_REPORT_FILING_CLOSED"
                : "REG_REPORT_FILING_STATE_CHANGED";
        audit.log(trigger, "RegReportFiling", saved.getId(),
                "Filing " + saved.getReference() + " " + from + " → " + to);
        RegReportObligation o = loadObligation(saved.getObligationId());
        emit(trigger, "RegReportFiling", saved.getId(), saved.getReference(),
                Map.of("from", from, "to", to,
                        "code", o.getCode(),
                        "regulator", o.getRegulator(),
                        "dueDate", String.valueOf(saved.getDueDate())));
        timeline(saved.getId(), "STATUS_CHANGED", to, req.getNoteEn(), req.getNoteAr());
        return toFiling(saved, o);
    }

    @Transactional
    public FilingResponse submitFiling(Long id, SubmitFilingRequest req) {
        RegReportFiling f = loadFiling(id);
        if (!"APPROVED".equals(f.getStatus()) && !"REJECTED".equals(f.getStatus())) {
            throw new IllegalStateException(
                    "Filing must be APPROVED (or post-REJECTED) to submit; current=" + f.getStatus());
        }
        f.setStatus("SUBMITTED");
        f.setSubmittedAt(LocalDateTime.now());
        f.setRegulatorAckReference(req.getRegulatorAckReference());
        if (req.getFilingArtifactReference() != null)
            f.setFilingArtifactReference(req.getFilingArtifactReference());
        RegReportFiling saved = filingRepo.save(f);

        RegReportObligation o = loadObligation(saved.getObligationId());
        boolean late = saved.getDueDate() != null
                && saved.getDueDate().isBefore(LocalDate.now());

        audit.log("REG_REPORT_FILING_SUBMITTED", "RegReportFiling", saved.getId(),
                "Filing " + saved.getReference() + " submitted, ack "
                        + saved.getRegulatorAckReference()
                        + (late ? " (LATE)" : ""));
        emit("REG_REPORT_FILING_SUBMITTED", "RegReportFiling",
                saved.getId(), saved.getReference(),
                Map.of("code", o.getCode(),
                        "regulator", o.getRegulator(),
                        "regulatorAckReference", saved.getRegulatorAckReference(),
                        "late", String.valueOf(late),
                        "dueDate", String.valueOf(saved.getDueDate())));

        String note = (req.getNoteEn() == null || req.getNoteEn().isBlank())
                ? "Filing submitted — ack ref: " + saved.getRegulatorAckReference()
                : req.getNoteEn();
        String noteAr = (req.getNoteAr() == null || req.getNoteAr().isBlank())
                ? "تم التقديم — رقم إقرار الاستلام: " + saved.getRegulatorAckReference()
                : req.getNoteAr();
        timeline(saved.getId(), "SUBMITTED", "SUBMITTED", note, noteAr);
        return toFiling(saved, o);
    }

    @Transactional
    public FilingResponse acknowledgeFiling(Long id) {
        RegReportFiling f = loadFiling(id);
        if (!"SUBMITTED".equals(f.getStatus())) {
            throw new IllegalStateException(
                    "Filing must be SUBMITTED to acknowledge; current=" + f.getStatus());
        }
        f.setStatus("ACKNOWLEDGED");
        f.setAcknowledgedAt(LocalDateTime.now());
        RegReportFiling saved = filingRepo.save(f);
        RegReportObligation o = loadObligation(saved.getObligationId());
        audit.log("REG_REPORT_FILING_ACKNOWLEDGED", "RegReportFiling",
                saved.getId(), "Filing " + saved.getReference() + " acknowledged by "
                        + o.getRegulator());
        emit("REG_REPORT_FILING_STATE_CHANGED", "RegReportFiling",
                saved.getId(), saved.getReference(),
                Map.of("from", "SUBMITTED", "to", "ACKNOWLEDGED",
                        "code", o.getCode(),
                        "regulator", o.getRegulator()));
        timeline(saved.getId(), "ACK_RECEIVED", "ACKNOWLEDGED",
                "Regulator acknowledgment received",
                "تم استلام إقرار الاستلام من الجهة التنظيمية");
        return toFiling(saved, o);
    }

    @Transactional
    public FilingResponse rejectFiling(Long id, RejectFilingRequest req) {
        RegReportFiling f = loadFiling(id);
        if (!"SUBMITTED".equals(f.getStatus())) {
            throw new IllegalStateException(
                    "Filing must be SUBMITTED to reject; current=" + f.getStatus());
        }
        f.setStatus("REJECTED");
        f.setRejectionReasonEn(req.getRejectionReasonEn());
        f.setRejectionReasonAr(req.getRejectionReasonAr());
        RegReportFiling saved = filingRepo.save(f);
        RegReportObligation o = loadObligation(saved.getObligationId());

        audit.log("REG_REPORT_FILING_REJECTED", "RegReportFiling", saved.getId(),
                "Filing " + saved.getReference() + " rejected by "
                        + o.getRegulator() + ": " + req.getRejectionReasonEn());
        emit("REG_REPORT_FILING_REJECTED", "RegReportFiling",
                saved.getId(), saved.getReference(),
                Map.of("code", o.getCode(),
                        "regulator", o.getRegulator(),
                        "rejectionReason", req.getRejectionReasonEn()));
        timeline(saved.getId(), "REJECTED", "REJECTED",
                "Regulator rejection — " + req.getRejectionReasonEn(),
                "رفض من الجهة التنظيمية — " + req.getRejectionReasonAr());
        return toFiling(saved, o);
    }

    @Transactional
    public FilingResponse exemptFiling(Long id, ExemptFilingRequest req) {
        RegReportFiling f = loadFiling(id);
        if (TERMINAL_STATUSES.contains(f.getStatus())) {
            throw new IllegalStateException(
                    "Filing already terminal — cannot exempt");
        }
        f.setStatus("EXEMPTED");
        f.setExemptionReasonEn(req.getExemptionReasonEn());
        f.setExemptionReasonAr(req.getExemptionReasonAr());
        f.setExemptionReference(req.getExemptionReference());
        RegReportFiling saved = filingRepo.save(f);
        RegReportObligation o = loadObligation(saved.getObligationId());

        audit.log("REG_REPORT_FILING_EXEMPTED", "RegReportFiling", saved.getId(),
                "Filing " + saved.getReference() + " exempted ref="
                        + req.getExemptionReference());
        emit("REG_REPORT_FILING_EXEMPTED", "RegReportFiling",
                saved.getId(), saved.getReference(),
                Map.of("code", o.getCode(),
                        "regulator", o.getRegulator(),
                        "exemptionReference", req.getExemptionReference()));
        timeline(saved.getId(), "EXEMPTION_GRANTED", "EXEMPTED",
                "Formal exemption granted — " + req.getExemptionReference(),
                "إعفاء رسمي مُمنوح — " + req.getExemptionReference());
        return toFiling(saved, o);
    }

    // ─── Reads ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ObligationResponse> listObligations(int page, int size) {
        return obligationRepo.findByOrganisationIdOrderByCodeAsc(
                org(), PageRequest.of(page, size))
                .map(this::toObligation);
    }

    @Transactional(readOnly = true)
    public ObligationResponse getObligation(Long id) {
        return toObligation(loadObligation(id));
    }

    @Transactional(readOnly = true)
    public List<FilingResponse> listFilingsForObligation(Long obligationId) {
        RegReportObligation o = loadObligation(obligationId);
        return filingRepo.findByObligationIdAndOrganisationIdOrderByPeriodEndDesc(
                        o.getId(), org()).stream()
                .map(f -> toFiling(f, o)).toList();
    }

    @Transactional(readOnly = true)
    public Page<FilingResponse> listFilings(int page, int size) {
        return filingRepo.findByOrganisationIdOrderByDueDateAsc(
                org(), PageRequest.of(page, size))
                .map(this::toFilingHydrate);
    }

    @Transactional(readOnly = true)
    public FilingResponse getFiling(Long id) {
        RegReportFiling f = loadFiling(id);
        return toFiling(f, loadObligation(f.getObligationId()));
    }

    @Transactional(readOnly = true)
    public List<FilingEventResponse> listEvents(Long filingId) {
        loadFiling(filingId);
        return eventRepo.findByFilingIdAndOrganisationIdOrderByOccurredAtDesc(
                        filingId, org()).stream()
                .map(RegReportingService::toEvent).toList();
    }

    @Transactional(readOnly = true)
    public CalendarResponse calendar(LocalDate from, LocalDate to) {
        LocalDate effFrom = from != null ? from : LocalDate.now();
        LocalDate effTo = to != null ? to : effFrom.plusDays(30);
        List<FilingResponse> filings = filingRepo
                .findByOrganisationIdAndDueDateBetweenOrderByDueDateAsc(
                        org(), effFrom, effTo).stream()
                .map(this::toFilingHydrate).toList();
        return CalendarResponse.builder()
                .from(effFrom).to(effTo).filings(filings).build();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        LocalDate today = LocalDate.now();

        long activeOblig = obligationRepo.countByOrganisationIdAndActive(org(), true);
        long inFlight = filingRepo.countByOrganisationIdAndStatusIn(
                org(), IN_FLIGHT_STATUSES);
        long dueWithin7 = countDueWithin7(today);
        long overdue = countOverdue(today);
        long submittedThisQuarter = countSubmittedSince(quarterStart(today));

        // In-flight breakdowns share a per-obligation cache with the upcoming /
        // overdue lists below so each obligation is loaded at most once.
        List<RegReportFiling> inflightList = filingRepo
                .findByOrganisationIdAndStatusInOrderByDueDateAsc(
                        org(), IN_FLIGHT_STATUSES);
        Map<Long, RegReportObligation> obligCache = new HashMap<>();
        for (RegReportFiling f : inflightList) {
            obligCache.computeIfAbsent(f.getObligationId(),
                    oid -> obligationRepo.findByIdAndOrganisationId(oid, org()).orElse(null));
        }
        Map<String, Long> byRegulator = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (RegReportFiling f : inflightList) {
            RegReportObligation o = obligCache.get(f.getObligationId());
            String reg = o == null ? "OTHER" : o.getRegulator();
            byRegulator.merge(reg, 1L, Long::sum);
            byStatus.merge(f.getStatus(), 1L, Long::sum);
        }

        List<FilingResponse> upcoming = upcoming(today, obligCache);
        List<FilingResponse> overdueRecent = overdueRecent(today, inflightList, obligCache);

        return DashboardResponse.builder()
                .activeObligationCount(activeOblig)
                .inFlightFilingCount(inFlight)
                .dueWithin7Count(dueWithin7)
                .overdueCount(overdue)
                .submittedThisQuarterCount(submittedThisQuarter)
                .inFlightByRegulator(byRegulator)
                .inFlightByStatus(byStatus)
                .upcoming(upcoming)
                .overdueRecent(overdueRecent)
                .build();
    }

    /** Filings due in the next 7 days that are still SCHEDULED or otherwise overdue-applicable. */
    private long countDueWithin7(LocalDate today) {
        return filingRepo
                .findByOrganisationIdAndDueDateBetweenOrderByDueDateAsc(
                        org(), today, today.plusDays(7)).stream()
                .filter(f -> OVERDUE_APPLICABLE.contains(f.getStatus())
                        || "SCHEDULED".equals(f.getStatus()))
                .count();
    }

    /** Filings past their due date that have not been submitted/closed/exempted. */
    private long countOverdue(LocalDate today) {
        return filingRepo.countByOrganisationIdAndStatusNotInAndDueDateBefore(
                org(),
                Set.of("SUBMITTED", "ACKNOWLEDGED", "CLOSED", "EXEMPTED"),
                today);
    }

    /** Count of submitted/acknowledged/closed filings actually submitted on or after {@code since}. */
    private long countSubmittedSince(LocalDate since) {
        return filingRepo
                .findByOrganisationIdAndStatusInOrderByDueDateAsc(
                        org(), Set.of("SUBMITTED", "ACKNOWLEDGED", "CLOSED")).stream()
                .filter(f -> f.getSubmittedAt() != null
                        && !f.getSubmittedAt().toLocalDate().isBefore(since))
                .count();
    }

    /** First day of the calendar quarter containing {@code today}. */
    static LocalDate quarterStart(LocalDate today) {
        return today
                .withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1)
                .with(TemporalAdjusters.firstDayOfMonth());
    }

    /** Non-terminal filings due within the next 14 days; hydrates via the shared cache. */
    private List<FilingResponse> upcoming(LocalDate today, Map<Long, RegReportObligation> obligCache) {
        return filingRepo
                .findByOrganisationIdAndDueDateBetweenOrderByDueDateAsc(
                        org(), today, today.plusDays(14)).stream()
                .filter(f -> !TERMINAL_STATUSES.contains(f.getStatus()))
                .map(f -> toFiling(f, obligCache.computeIfAbsent(f.getObligationId(),
                        oid -> obligationRepo.findByIdAndOrganisationId(oid, org()).orElse(null))))
                .toList();
    }

    /** Up to 10 in-flight, overdue filings sorted by due date; obligations read from the shared cache. */
    private List<FilingResponse> overdueRecent(LocalDate today, List<RegReportFiling> inflightList,
                                               Map<Long, RegReportObligation> obligCache) {
        return inflightList.stream()
                .filter(f -> OVERDUE_APPLICABLE.contains(f.getStatus())
                        && f.getDueDate() != null
                        && f.getDueDate().isBefore(today))
                .sorted(Comparator.comparing(RegReportFiling::getDueDate))
                .limit(10)
                .map(f -> toFiling(f, obligCache.get(f.getObligationId())))
                .toList();
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private RegReportObligation loadObligation(Long id) {
        return obligationRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Obligation " + id + " not found in tenant"));
    }

    private RegReportFiling loadFiling(Long id) {
        return filingRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Filing " + id + " not found in tenant"));
    }

    /** Sun→Thu is the Saudi workweek; skip Fri/Sat when adding business days. */
    static LocalDate addBusinessDays(LocalDate from, int n) {
        if (from == null) return null;
        int step = n >= 0 ? 1 : -1;
        int remaining = Math.abs(n);
        LocalDate cur = from;
        while (remaining > 0) {
            cur = cur.plusDays(step);
            DayOfWeek d = cur.getDayOfWeek();
            if (d != DayOfWeek.FRIDAY && d != DayOfWeek.SATURDAY) {
                remaining--;
            }
        }
        return cur;
    }

    static LocalDate periodStartFor(String frequency, LocalDate periodEnd) {
        if (periodEnd == null) return null;
        return switch (frequency) {
            case "DAILY"       -> periodEnd;
            case "WEEKLY"      -> periodEnd.minusDays(6);
            case "MONTHLY"     -> periodEnd.with(TemporalAdjusters.firstDayOfMonth());
            case "QUARTERLY"   -> {
                int q = ((periodEnd.getMonthValue() - 1) / 3);
                yield periodEnd.withMonth(q * 3 + 1)
                        .with(TemporalAdjusters.firstDayOfMonth());
            }
            case "SEMIANNUAL"  -> {
                int h = periodEnd.getMonthValue() <= 6 ? 1 : 7;
                yield periodEnd.withMonth(h).with(TemporalAdjusters.firstDayOfMonth());
            }
            case "ANNUAL"      -> periodEnd.with(TemporalAdjusters.firstDayOfYear());
            default            -> periodEnd; // AD_HOC + fallback
        };
    }

    /** Compute the next periodEnd for a cycle, based on prior filing or today. */
    LocalDate nextPeriodEnd(RegReportObligation o) {
        LocalDate seed = filingRepo
                .findFirstByObligationIdAndOrganisationIdOrderByPeriodEndDesc(
                        o.getId(), org())
                .map(RegReportFiling::getPeriodEnd)
                .orElse(LocalDate.now());

        return switch (o.getFrequency()) {
            case "DAILY"      -> seed.plusDays(1);
            case "WEEKLY"     -> seed.plusWeeks(1);
            case "MONTHLY"    -> seed.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            case "QUARTERLY"  -> {
                LocalDate next = seed.plusMonths(3);
                int q = ((next.getMonthValue() - 1) / 3);
                yield next.withMonth(q * 3 + 3)
                        .with(TemporalAdjusters.lastDayOfMonth());
            }
            case "SEMIANNUAL" -> {
                LocalDate next = seed.plusMonths(6);
                int month = next.getMonthValue() <= 6 ? 6 : 12;
                yield next.withMonth(month).with(TemporalAdjusters.lastDayOfMonth());
            }
            case "ANNUAL"     -> seed.plusYears(1).with(TemporalAdjusters.lastDayOfYear());
            default           -> LocalDate.now();
        };
    }

    static String buildReference(RegReportObligation o, LocalDate periodEnd) {
        return switch (o.getFrequency()) {
            case "DAILY"      -> o.getCode() + "-" + periodEnd;
            case "WEEKLY"     -> o.getCode() + "-W"
                    + periodEnd.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
                    + "-" + periodEnd.getYear();
            case "MONTHLY"    -> o.getCode() + "-" + periodEnd.getYear() + "-"
                    + String.format("%02d", periodEnd.getMonthValue());
            case "QUARTERLY"  -> o.getCode() + "-" + periodEnd.getYear() + "-Q"
                    + (((periodEnd.getMonthValue() - 1) / 3) + 1);
            case "SEMIANNUAL" -> o.getCode() + "-" + periodEnd.getYear() + "-H"
                    + (periodEnd.getMonthValue() <= 6 ? 1 : 2);
            case "ANNUAL"     -> o.getCode() + "-" + periodEnd.getYear();
            default           -> o.getCode() + "-" + periodEnd;
        };
    }

    private void timeline(Long filingId, String eventType, String statusAfter,
                          String noteEn, String noteAr) {
        RegReportEvent e = new RegReportEvent();
        e.setOrganisationId(org());
        e.setFilingId(filingId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setActorName(currentActorName());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
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

    // ─── DTO mappers ───────────────────────────────────────────────

    private ObligationResponse toObligation(RegReportObligation o) {
        RegReportFiling latest = filingRepo
                .findFirstByObligationIdAndOrganisationIdOrderByPeriodEndDesc(
                        o.getId(), org()).orElse(null);
        return ObligationResponse.builder()
                .id(o.getId())
                .code(o.getCode())
                .regulator(o.getRegulator())
                .frequency(o.getFrequency())
                .titleEn(o.getTitleEn())
                .titleAr(o.getTitleAr())
                .descriptionEn(o.getDescriptionEn())
                .descriptionAr(o.getDescriptionAr())
                .dueOffsetBusinessDays(o.getDueOffsetBusinessDays())
                .statutoryBasis(o.getStatutoryBasis())
                .ownerRole(o.getOwnerRole())
                .reviewerRole(o.getReviewerRole())
                .filingChannel(o.getFilingChannel())
                .reminderLadder(o.getReminderLadder())
                .active(o.getActive())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .latestFilingStatus(latest == null ? null : latest.getStatus())
                .latestFilingDueDate(latest == null ? null : latest.getDueDate())
                .build();
    }

    /** Hydrates the obligation reference (one lookup per row — acceptable for page). */
    private FilingResponse toFilingHydrate(RegReportFiling f) {
        RegReportObligation o = obligationRepo
                .findByIdAndOrganisationId(f.getObligationId(), org())
                .orElse(null);
        return toFiling(f, o);
    }

    private FilingResponse toFiling(RegReportFiling f, RegReportObligation o) {
        LocalDate today = LocalDate.now();
        boolean overdue = OVERDUE_APPLICABLE.contains(f.getStatus())
                && f.getDueDate() != null
                && f.getDueDate().isBefore(today);
        long days = f.getDueDate() == null ? 0L
                : ChronoUnit.DAYS.between(today, f.getDueDate());
        return FilingResponse.builder()
                .id(f.getId())
                .obligationId(f.getObligationId())
                .obligationCode(o == null ? null : o.getCode())
                .obligationTitleEn(o == null ? null : o.getTitleEn())
                .obligationTitleAr(o == null ? null : o.getTitleAr())
                .regulator(o == null ? null : o.getRegulator())
                .frequency(o == null ? null : o.getFrequency())
                .reference(f.getReference())
                .status(f.getStatus())
                .periodStart(f.getPeriodStart())
                .periodEnd(f.getPeriodEnd())
                .dueDate(f.getDueDate())
                .internalTargetDate(f.getInternalTargetDate())
                .preparerUserId(f.getPreparerUserId())
                .reviewerUserId(f.getReviewerUserId())
                .approverUserId(f.getApproverUserId())
                .submittedAt(f.getSubmittedAt())
                .regulatorAckReference(f.getRegulatorAckReference())
                .acknowledgedAt(f.getAcknowledgedAt())
                .closedAt(f.getClosedAt())
                .filingArtifactReference(f.getFilingArtifactReference())
                .preparationNotesEn(f.getPreparationNotesEn())
                .preparationNotesAr(f.getPreparationNotesAr())
                .rejectionReasonEn(f.getRejectionReasonEn())
                .rejectionReasonAr(f.getRejectionReasonAr())
                .exemptionReasonEn(f.getExemptionReasonEn())
                .exemptionReasonAr(f.getExemptionReasonAr())
                .exemptionReference(f.getExemptionReference())
                .overdue(overdue)
                .daysUntilDue(days)
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }

    private static FilingEventResponse toEvent(RegReportEvent e) {
        return FilingEventResponse.builder()
                .id(e.getId())
                .filingId(e.getFilingId())
                .occurredAt(e.getOccurredAt())
                .eventType(e.getEventType())
                .actorUserId(e.getActorUserId())
                .actorName(e.getActorName())
                .statusAfter(e.getStatusAfter())
                .noteEn(e.getNoteEn())
                .noteAr(e.getNoteAr())
                .build();
    }

}
