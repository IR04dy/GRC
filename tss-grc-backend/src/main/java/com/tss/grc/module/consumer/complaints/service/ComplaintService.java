package com.tss.grc.module.consumer.complaints.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.util.SaudiWorkdayUtil;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AcknowledgeRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AssignRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintUpdateResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.EscalateToSamaRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.PublicTrackingResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.QuarterlyReportResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.TransitionRequest;
import com.tss.grc.module.consumer.complaints.entity.Complaint;
import com.tss.grc.module.consumer.complaints.entity.ComplaintAggregateReport;
import com.tss.grc.module.consumer.complaints.entity.ComplaintUpdate;
import com.tss.grc.module.consumer.complaints.repository.ComplaintAggregateReportRepository;
import com.tss.grc.module.consumer.complaints.repository.ComplaintRepository;
import com.tss.grc.module.consumer.complaints.repository.ComplaintUpdateRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.tenancy.PublicPortalTenantResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 13 — Customer Complaints service.
 *
 * <p>Implements the SAMA Banking Consumer Protection Principles (2021)
 * Principle 6 ("Complaint Handling") complaints lifecycle:
 *
 * <pre>
 *   RECEIVED            → ACKNOWLEDGED | REJECTED
 *   ACKNOWLEDGED        → UNDER_INVESTIGATION | RESOLVED | REJECTED
 *   UNDER_INVESTIGATION → AWAITING_CUSTOMER | RESOLVED | ESCALATED_TO_SAMA
 *   AWAITING_CUSTOMER   → UNDER_INVESTIGATION | RESOLVED
 *   RESOLVED            → CLOSED | REOPENED
 *   CLOSED              → REOPENED
 *   REOPENED            → UNDER_INVESTIGATION
 *   ESCALATED_TO_SAMA   → CLOSED | RESOLVED
 *   REJECTED            (terminal)
 * </pre>
 *
 * <h3>SLA clocks</h3>
 * <ul>
 *   <li>{@code ackSlaDueOn} = submission + 5 working days</li>
 *   <li>{@code resolutionSlaDueOn} = submission + 20 working days</li>
 * </ul>
 * Both use {@link SaudiWorkdayUtil} so Friday + Saturday (KSA weekend)
 * are skipped — the JDK default Sat/Sun convention is wrong here.
 *
 * <h3>F8 events</h3>
 * <ul>
 *   <li>{@code COMPLAINT_RECEIVED}</li>
 *   <li>{@code COMPLAINT_ACKNOWLEDGED}</li>
 *   <li>{@code COMPLAINT_ASSIGNED}</li>
 *   <li>{@code COMPLAINT_TRANSITIONED}</li>
 *   <li>{@code COMPLAINT_RESOLVED}</li>
 *   <li>{@code COMPLAINT_REOPENED}</li>
 *   <li>{@code COMPLAINT_ESCALATED_TO_SAMA}</li>
 * </ul>
 *
 * <h3>Public submission tenant resolution</h3>
 * Public-portal complaints are anonymous — there is no JWT and so no
 * {@code TenantContext}. The tenant must therefore be <i>named</i> by the portal link's
 * {@code ?org=} short code; see {@link PublicPortalTenantResolver}. An unresolvable tenant is a
 * 404, never a default (F2-83 — this used to default to org 1, and for an anonymous caller that
 * default was the only path, so every complaint on the platform was filed into org 1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintService {

    /** SAMA Banking Consumer Protection Principles — 5 working-day acknowledgement. */
    public static final int ACK_SLA_BUSINESS_DAYS = 5;

    /** SAMA Banking Consumer Protection Principles — 20 working-day resolution. */
    public static final int RESOLUTION_SLA_BUSINESS_DAYS = 20;

    /*
     * ─── Closed vocabularies (F2-183) ────────────────────────────────────────────────────────
     *
     * Until V375 the whole `consumer` schema carried ZERO check constraints, so every one of these
     * columns was `NVARCHAR(n)` with its vocabulary living only in a javadoc comment — in three
     * places (this class, the entity, and `lib/complaints.ts`) with nothing keeping them in step.
     *
     * ⚠ Each of these names a CHECK, so `ClosedVocabularyPinTest` reads V375 and fails the build if
     * the two disagree. That is the point: the previous arrangement was the AttachmentAccessPolicy
     * shape — independent lists that happened to agree, maintained by nobody.
     */

    /** {@code CK_complaint_channel} — how the complaint reached the bank. */
    static final ClosedVocabulary CHANNEL = ClosedVocabulary.of(
            "channel", "CK_complaint_channel",
            "WEB", "EMAIL", "PHONE", "BRANCH", "SAMA_REFERRAL", "SOCIAL_MEDIA");

    /**
     * {@code CK_complaint_category} — the 13-code Saudi-bank taxonomy from the V199 header.
     *
     * <p>This was the worst of the four and the filing did not name it: {@code category} was written
     * straight from the request body with only {@code @NotBlank} in front of it, on an <b>anonymous
     * public endpoint</b>, into a column with no constraint. So it accepted arbitrary text — and
     * {@code generateQuarterlyReport} groups by this column into a case-<b>sensitive</b>
     * {@code HashMap} to pick the {@code topCategory} it reports to SAMA. Two spellings of one
     * category therefore became two buckets, splitting the count and changing which category the
     * regulator is told is the most complained-about.
     */
    static final ClosedVocabulary CATEGORY = ClosedVocabulary.of(
            "category", "CK_complaint_category",
            "ACCOUNT_ACCESS", "CARDS_DISPUTES", "ATM_ISSUES", "TRANSFER_DELAYS",
            "FRAUD_UNAUTHORIZED_TXN", "FEES_CHARGES", "LOAN_FINANCING", "INSURANCE",
            "INVESTMENT_PRODUCTS", "DIGITAL_BANKING", "BRANCH_SERVICE",
            "MISLEADING_INFORMATION", "OTHER");

    /** {@code CK_complaint_severity} — triage band. */
    static final ClosedVocabulary SEVERITY = ClosedVocabulary.of(
            "severity", "CK_complaint_severity", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    /**
     * {@code CK_complaint_status} — the lifecycle states.
     *
     * <p>⚠ Unlike the four above, <b>no writer here was open</b>: the initial status is a literal and
     * every later one goes through {@link #guardTransition}, which refuses anything the state machine
     * does not list. So this vocabulary exists to pin {@link #ALLOWED} to the column rather than to
     * normalise a caller's input — {@code ComplaintVocabularyTest} asserts the two agree in both
     * directions. Deliberately <b>not</b> used to normalise {@code toStatus}: that would make
     * {@code "resolved"} start succeeding where it is refused today, which is a widening of accepted
     * input dressed as a casing fix.
     */
    static final ClosedVocabulary STATUS = ClosedVocabulary.of(
            "status", "CK_complaint_status",
            "RECEIVED", "ACKNOWLEDGED", "UNDER_INVESTIGATION", "AWAITING_CUSTOMER",
            "RESOLVED", "CLOSED", "ESCALATED_TO_SAMA", "REOPENED", "REJECTED");

    /** {@code CK_complaint_id_type} — nullable, so this one coerces to {@code null}, not to a member. */
    static final ClosedVocabulary CUSTOMER_ID_TYPE = ClosedVocabulary.of(
            "customerIdType", "CK_complaint_id_type",
            "NATIONAL_ID", "IQAMA", "PASSPORT", "CR");

    /** {@code CK_cmpl_update_type} — timeline entry kinds. Written only from the constants below. */
    static final ClosedVocabulary UPDATE_TYPE = ClosedVocabulary.of(
            "updateType", "CK_cmpl_update_type",
            "STATUS_CHANGE", "CUSTOMER_CONTACT", "INTERNAL_NOTE",
            "EVIDENCE_REQUEST", "SAMA_NOTIFICATION");

    /** The channel assumed when an anonymous submitter names none — they used the web form. */
    static final String CHANNEL_DEFAULT = "WEB";

    /** The band an ungraded complaint enters at; also the column's own DEFAULT. */
    static final String SEVERITY_DEFAULT = "MEDIUM";

    /**
     * The taxonomy's own "we could not classify this" member.
     *
     * <p>🔑 It is what makes {@code coerce} defensible for {@code category}: the fallback is not a
     * guess at what the customer meant, it is the code the taxonomy provides for exactly this case.
     */
    static final String CATEGORY_UNCLASSIFIED = "OTHER";

    static final String UPDATE_STATUS_CHANGE = "STATUS_CHANGE";
    static final String UPDATE_SAMA_NOTIFICATION = "SAMA_NOTIFICATION";
    static final String UPDATE_INTERNAL_NOTE = "INTERNAL_NOTE";

    /**
     * Lifecycle state machine.
     *
     * <p>Package-private rather than private so {@code ComplaintVocabularyTest} can assert it against
     * {@link #STATUS} — the states this map names and the states the column accepts are two lists that
     * must not drift, and reflection to read a private field would make the pin look optional.
     */
    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("RECEIVED",            Set.of("ACKNOWLEDGED", "REJECTED")),
            Map.entry("ACKNOWLEDGED",        Set.of("UNDER_INVESTIGATION", "RESOLVED", "REJECTED")),
            Map.entry("UNDER_INVESTIGATION", Set.of("AWAITING_CUSTOMER", "RESOLVED", "ESCALATED_TO_SAMA")),
            Map.entry("AWAITING_CUSTOMER",   Set.of("UNDER_INVESTIGATION", "RESOLVED")),
            Map.entry("RESOLVED",            Set.of("CLOSED", "REOPENED")),
            Map.entry("CLOSED",              Set.of("REOPENED")),
            Map.entry("REOPENED",            Set.of("UNDER_INVESTIGATION")),
            Map.entry("ESCALATED_TO_SAMA",   Set.of("CLOSED", "RESOLVED")),
            Map.entry("REJECTED",            Set.of())
    );

    private final ComplaintRepository complaintRepo;
    private final ComplaintUpdateRepository updateRepo;
    private final ComplaintAggregateReportRepository reportRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final PublicPortalTenantResolver publicTenant;

    // ─── Reads (internal — tenant-scoped) ───────────────────────────

    @Transactional(readOnly = true)
    public List<ComplaintResponse> list() {
        return complaintRepo.findByOrganisationIdAndIsDeletedFalseOrderBySubmittedOnDesc(org())
                .stream().map(ComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> listByStatus(String status) {
        return complaintRepo.findByOrganisationIdAndStatus(org(), status)
                .stream().map(ComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> listByCategory(String category) {
        return complaintRepo.findByOrganisationIdAndCategoryAndIsDeletedFalse(org(), category)
                .stream().map(ComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(Long id) {
        return ComplaintResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ComplaintUpdateResponse> listUpdates(Long complaintId) {
        // Ensures it's in-tenant.
        loadOrThrow(complaintId);
        return updateRepo.findByComplaintIdOrderByOccurredOnAsc(complaintId)
                .stream().map(ComplaintUpdateResponse::from).toList();
    }

    // ─── Public-portal submission ───────────────────────────────────

    /**
     * Public submission — anonymous caller, no TenantContext. The
     * returned response contains only the complaint number + tracking
     * token + SLA timestamps. Caller MUST capture the token; we don't
     * recover it.
     */
    @Transactional
    public SubmitComplaintResponse submitPublicComplaint(SubmitComplaintRequest req,
                                                        String orgShortCode) {
        Long orgId = publicTenant.resolve(orgShortCode);

        Complaint c = new Complaint();
        c.setOrganisationId(orgId);
        c.setComplaintNo(generateNextComplaintNo());
        c.setSubmissionToken(UUID.randomUUID());
        // F2-183: all four coerce rather than refuse. This is the anonymous SAMA-mandated intake
        // surface, so a 400 on an unrecognised classifier would lose the complaint — the line
        // F2-179 drew on the whistleblower portal, for the same reason. What changes is that a
        // case-variant now lands on its own token instead of falling through to the fallback:
        // `channel: "email"` was stored as WEB and `severity: "critical"` as MEDIUM.
        c.setChannel(CHANNEL.coerce(req.channel(), CHANNEL_DEFAULT));
        c.setCategory(CATEGORY.coerce(req.category(), CATEGORY_UNCLASSIFIED));
        c.setSubcategory(req.subcategory());
        c.setSeverity(SEVERITY.coerce(req.severity(), SEVERITY_DEFAULT));
        c.setStatus("RECEIVED");
        c.setCustomerNameEn(req.customerNameEn());
        c.setCustomerNameAr(req.customerNameAr());
        c.setCustomerIdType(CUSTOMER_ID_TYPE.coerceOrNull(req.customerIdType()));
        c.setCustomerIdLast4(req.customerIdLast4());
        c.setCustomerPhone(req.customerPhone());
        c.setCustomerEmail(req.customerEmail());
        c.setCustomerCity(req.customerCity());
        c.setProductCode(req.productCode());
        c.setAccountLast4(req.accountLast4());
        c.setDescriptionEn(req.descriptionEn());
        c.setDescriptionAr(req.descriptionAr());

        LocalDateTime now = LocalDateTime.now();
        c.setSubmittedOn(now);
        c.setAckSlaDueOn(SaudiWorkdayUtil.addBusinessDays(now, ACK_SLA_BUSINESS_DAYS));
        c.setResolutionSlaDueOn(SaudiWorkdayUtil.addBusinessDays(now, RESOLUTION_SLA_BUSINESS_DAYS));

        Complaint saved = complaintRepo.save(c);

        audit.log("CREATE", "Complaint", saved.getId(),
                "Public complaint submitted " + saved.getComplaintNo()
                        + " · category=" + saved.getCategory()
                        + " · channel=" + saved.getChannel()
                        + " · severity=" + saved.getSeverity());

        emit("COMPLAINT_RECEIVED", saved, null);
        return SubmitComplaintResponse.from(saved);
    }

    // ─── Public anonymous tracking ──────────────────────────────────

    /**
     * Anonymous lookup by submission token. Returns ONLY:
     * <ul>
     *   <li>complaint_no, status, category, severity</li>
     *   <li>customer initials (no full name, no PII)</li>
     *   <li>milestone timestamps</li>
     *   <li>customer-visible timeline entries</li>
     * </ul>
     * Customer phone, email, ID, account fragments — explicitly stripped.
     */
    @Transactional(readOnly = true)
    public PublicTrackingResponse trackByToken(UUID token) {
        Complaint c = complaintRepo.findBySubmissionToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid tracking token"));
        List<ComplaintUpdate> visible = updateRepo
                .findByComplaintIdAndCustomerVisibleTrueOrderByOccurredOnAsc(c.getId());
        return PublicTrackingResponse.from(c, visible);
    }

    // ─── Internal: acknowledge / transition / escalate / assign ─────

    @Transactional
    public ComplaintResponse acknowledge(Long id, AcknowledgeRequest req) {
        Complaint c = loadOrThrow(id);
        guardTransition(c.getStatus(), "ACKNOWLEDGED");

        String from = c.getStatus();
        c.setStatus("ACKNOWLEDGED");
        c.setAcknowledgedOn(LocalDateTime.now());
        c.setAcknowledgedBy(currentActor());
        Complaint saved = complaintRepo.save(c);

        writeUpdate(saved, UPDATE_STATUS_CHANGE, from, "ACKNOWLEDGED",
                noteOrDefault(req == null ? null : req.noteEn(),
                        "Complaint received and acknowledged. Investigation will start shortly."),
                noteOrDefault(req == null ? null : req.noteAr(),
                        "تمّ استلام الشكوى والإقرار بها. سيبدأ التحقّق قريباً."),
                true);

        audit.log("ACKNOWLEDGE", "Complaint", saved.getId(),
                "Acknowledged complaint " + saved.getComplaintNo());

        emit("COMPLAINT_ACKNOWLEDGED", saved, from);
        return ComplaintResponse.from(saved);
    }

    /**
     * General transition with state-machine enforcement. The optional
     * resolution fields on {@code req} are applied only when
     * transitioning to RESOLVED.
     */
    @Transactional
    public ComplaintResponse transition(Long id, TransitionRequest req) {
        Complaint c = loadOrThrow(id);
        String from = c.getStatus();
        String to = req.toStatus();
        guardTransition(from, to);

        c.setStatus(to);

        // RESOLVED — capture resolution fields.
        if ("RESOLVED".equals(to)) {
            c.setResolvedOn(LocalDateTime.now());
            c.setResolvedBy(currentActor());
            if (req.resolutionSummaryEn() != null) c.setResolutionSummaryEn(req.resolutionSummaryEn());
            if (req.resolutionSummaryAr() != null) c.setResolutionSummaryAr(req.resolutionSummaryAr());
            if (req.compensationAmountSar() != null) c.setCompensationAmountSar(req.compensationAmountSar());
            if (req.rootCauseCode() != null) c.setRootCauseCode(req.rootCauseCode());
            if (req.lessonsLearned() != null) c.setLessonsLearned(req.lessonsLearned());
        }

        // REOPENED — clear resolution fingerprints? Keep them as history; tests assert state only.
        Complaint saved = complaintRepo.save(c);

        writeUpdate(saved, UPDATE_STATUS_CHANGE, from, to,
                req.noteEn(), req.noteAr(),
                Boolean.TRUE.equals(req.customerVisible()));

        audit.log("TRANSITION", "Complaint", saved.getId(),
                "Complaint " + saved.getComplaintNo() + " transitioned " + from + " → " + to);

        emit("COMPLAINT_TRANSITIONED", saved, from);
        if ("RESOLVED".equals(to)) emit("COMPLAINT_RESOLVED", saved, from);
        if ("REOPENED".equals(to)) emit("COMPLAINT_REOPENED", saved, from);
        return ComplaintResponse.from(saved);
    }

    @Transactional
    public ComplaintResponse escalateToSama(Long id, EscalateToSamaRequest req) {
        Complaint c = loadOrThrow(id);
        String from = c.getStatus();
        guardTransition(from, "ESCALATED_TO_SAMA");

        c.setStatus("ESCALATED_TO_SAMA");
        c.setEscalatedToSamaOn(LocalDateTime.now());
        c.setSamaReferenceNo(req.samaReferenceNo());
        Complaint saved = complaintRepo.save(c);

        writeUpdate(saved, UPDATE_SAMA_NOTIFICATION, from, "ESCALATED_TO_SAMA",
                noteOrDefault(req.noteEn(),
                        "Complaint escalated to SAMA; SAMA reference: " + req.samaReferenceNo()),
                noteOrDefault(req.noteAr(),
                        "تمّ رفع الشكوى إلى مؤسسة النقد؛ المرجع: " + req.samaReferenceNo()),
                true);

        audit.log("ESCALATE_TO_SAMA", "Complaint", saved.getId(),
                "Escalated complaint " + saved.getComplaintNo()
                        + " to SAMA · ref=" + req.samaReferenceNo());

        emit("COMPLAINT_ESCALATED_TO_SAMA", saved, from);
        return ComplaintResponse.from(saved);
    }

    @Transactional
    public ComplaintResponse assign(Long id, AssignRequest req) {
        Complaint c = loadOrThrow(id);
        String previousAssignee = c.getAssignedTo();
        c.setAssignedTo(req.assignedTo());
        Complaint saved = complaintRepo.save(c);

        writeUpdate(saved, UPDATE_INTERNAL_NOTE, saved.getStatus(), saved.getStatus(),
                "Assigned to " + req.assignedTo(),
                "تمّ الإسناد إلى " + req.assignedTo(),
                false);

        audit.log("ASSIGN", "Complaint", saved.getId(),
                "Assigned complaint " + saved.getComplaintNo()
                        + " · " + previousAssignee + " → " + req.assignedTo());

        emit("COMPLAINT_ASSIGNED", saved, null);
        return ComplaintResponse.from(saved);
    }

    // ─── Quarterly aggregate report ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuarterlyReportResponse> listQuarterlyReports() {
        return reportRepo.findByOrganisationIdOrderByReportPeriodDesc(org())
                .stream().map(QuarterlyReportResponse::from).toList();
    }

    @Transactional
    public QuarterlyReportResponse generateQuarterlyReport(String reportPeriod) {
        Long orgId = org();
        List<Complaint> all = complaintRepo
                .findByOrganisationIdAndIsDeletedFalseOrderBySubmittedOnDesc(orgId);

        int totalReceived = all.size();
        int totalResolved = (int) all.stream()
                .filter(c -> "RESOLVED".equals(c.getStatus()) || "CLOSED".equals(c.getStatus()))
                .count();
        int totalEscalated = (int) all.stream()
                .filter(c -> c.getEscalatedToSamaOn() != null)
                .count();

        // Average resolution days (calendar days between submitted and resolved).
        double avgResolution = all.stream()
                .filter(c -> c.getResolvedOn() != null)
                .mapToLong(c -> ChronoUnit.DAYS.between(c.getSubmittedOn(), c.getResolvedOn()))
                .average().orElse(0);

        long ackBreaches = all.stream()
                .filter(c -> c.getAckSlaDueOn() != null
                        && (c.getAcknowledgedOn() == null
                            ? LocalDateTime.now().isAfter(c.getAckSlaDueOn())
                            : c.getAcknowledgedOn().isAfter(c.getAckSlaDueOn())))
                .count();
        long resBreaches = all.stream()
                .filter(c -> c.getResolutionSlaDueOn() != null
                        && (c.getResolvedOn() == null
                            ? LocalDateTime.now().isAfter(c.getResolutionSlaDueOn())
                            : c.getResolvedOn().isAfter(c.getResolutionSlaDueOn())))
                .count();
        BigDecimal ackBreachPct = totalReceived == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((double) ackBreaches * 100 / totalReceived)
                    .setScale(2, RoundingMode.HALF_UP);
        BigDecimal resBreachPct = totalReceived == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf((double) resBreaches * 100 / totalReceived)
                    .setScale(2, RoundingMode.HALF_UP);

        Map<String, Long> byCategory = new HashMap<>();
        for (Complaint c : all) {
            byCategory.merge(c.getCategory(), 1L, Long::sum);
        }
        Map.Entry<String, Long> top = byCategory.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .orElse(null);

        BigDecimal totalCompensation = all.stream()
                .map(Complaint::getCompensationAmountSar)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Upsert.
        ComplaintAggregateReport report = reportRepo
                .findByOrganisationIdAndReportPeriod(orgId, reportPeriod)
                .orElseGet(() -> {
                    ComplaintAggregateReport r = new ComplaintAggregateReport();
                    r.setOrganisationId(orgId);
                    r.setReportPeriod(reportPeriod);
                    return r;
                });

        report.setTotalReceived(totalReceived);
        report.setTotalResolved(totalResolved);
        report.setTotalEscalatedSama(totalEscalated);
        report.setAvgResolutionDays(BigDecimal.valueOf(avgResolution).setScale(2, RoundingMode.HALF_UP));
        report.setAckSlaBreachPct(ackBreachPct);
        report.setResolutionSlaBreachPct(resBreachPct);
        report.setTopCategory(top == null ? null : top.getKey());
        report.setTopCategoryCount(top == null ? 0 : top.getValue().intValue());
        report.setCompensationTotalSar(totalCompensation);
        report.setGeneratedOn(LocalDateTime.now());
        report.setGeneratedBy(currentActor());

        ComplaintAggregateReport saved = reportRepo.save(report);

        audit.log("GENERATE_REPORT", "ComplaintAggregateReport", saved.getId(),
                "Generated quarterly complaints report for " + reportPeriod);

        return QuarterlyReportResponse.from(saved);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private Complaint loadOrThrow(Long id) {
        return complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + id));
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    /**
     * Generate the next {@code CMP-YYYY-NNNNNN} number. Parses the
     * largest existing complaint_no, increments, and zero-pads. If
     * there are no complaints yet, starts at 000001.
     */
    String generateNextComplaintNo() {
        int year = java.time.Year.now().getValue();
        Optional<Complaint> top = complaintRepo.findTopByOrderByComplaintNoDesc();
        int next = 1;
        if (top.isPresent()) {
            String existing = top.get().getComplaintNo();
            if (existing != null && existing.matches("CMP-\\d{4}-\\d{6}")) {
                int seq = Integer.parseInt(existing.substring(existing.lastIndexOf('-') + 1));
                next = seq + 1;
            }
        }
        // Avoid collisions if multiple writers race; loop is cheap.
        for (int seq = next; seq < next + 1000; seq++) {
            String candidate = String.format("CMP-%d-%06d", year, seq);
            if (!complaintRepo.existsByComplaintNo(candidate)) {
                return candidate;
            }
        }
        return String.format("CMP-%d-%06d-%s", year, next,
                UUID.randomUUID().toString().substring(0, 6));
    }

    private static void guardTransition(String from, String to) {
        Set<String> allowed = ALLOWED.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    private void writeUpdate(Complaint c, String type, String from, String to,
                             String noteEn, String noteAr, boolean customerVisible) {
        ComplaintUpdate u = new ComplaintUpdate();
        u.setComplaintId(c.getId());
        u.setUpdateType(type);
        u.setFromStatus(from);
        u.setToStatus(to);
        u.setNoteEn(noteEn);
        u.setNoteAr(noteAr);
        u.setActor(currentActor());
        u.setCustomerVisible(customerVisible);
        u.setOccurredOn(LocalDateTime.now());
        updateRepo.save(u);
    }

    private void emit(String trigger, Complaint c, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("complaintId", String.valueOf(c.getId()));
        payload.put("complaintNo", c.getComplaintNo());
        payload.put("status", c.getStatus());
        if (previousStatus != null) payload.put("previousStatus", previousStatus);
        payload.put("category", c.getCategory());
        payload.put("severity", c.getSeverity());
        payload.put("channel", c.getChannel());
        if (c.getSamaReferenceNo() != null) payload.put("samaReferenceNo", c.getSamaReferenceNo());
        publisher.publishEvent(new DomainEvent(
                c.getOrganisationId(), trigger,
                "Complaint", c.getId(), c.getComplaintNo(), payload));
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "public.portal";
        }
        return auth.getName();
    }

    /*
     * F2-183: `safeChannel` and `safeSeverity` used to sit here as case-SENSITIVE switches over the
     * same tokens the vocabularies now hold. They were the `safe*` shape CLAUDE.md tells you to grep
     * for — third and fourth instances after WhistleblowerCaseService's pair — and their default arm
     * made every unrecognised value, including a correct one in the wrong case, indistinguishable
     * from silence. Deleted rather than repaired: a hand-rolled mirror of a CHECK is the thing
     * ClosedVocabulary exists to stop, and nothing checked these two against the schema because
     * there was no constraint in `consumer` to check them against.
     */

    private static String noteOrDefault(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
