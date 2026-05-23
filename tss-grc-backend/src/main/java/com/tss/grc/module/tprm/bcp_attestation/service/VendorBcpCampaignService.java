package com.tss.grc.module.tprm.bcp_attestation.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.notify.NotificationChannel;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCloseRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCreateRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignResponse;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignStatsResponse;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
 * Slice 10-D — Vendor BCP attestation campaign service.
 *
 * <p>Surface: launch a campaign, send reminder rounds, escalate overdue
 * rows to the Audit Committee, close with a regulator-ready summary.
 * Each state transition emits an F8 domain event and writes an F7 audit
 * row so the lifecycle is fully traceable for SAMA / NCA review.
 *
 * <h3>Campaign state machine</h3>
 * <pre>
 *   DRAFT       → OPEN | CANCELLED
 *   OPEN        → REMINDED_R1 | ESCALATED | CLOSED | CANCELLED
 *   REMINDED_R1 → REMINDED_R2 | ESCALATED | CLOSED | CANCELLED
 *   REMINDED_R2 → ESCALATED | CLOSED | CANCELLED
 *   ESCALATED   → CLOSED | CANCELLED
 *   CLOSED      (terminal)
 *   CANCELLED   (terminal)
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class VendorBcpCampaignService {

    private static final Logger log = LoggerFactory.getLogger(VendorBcpCampaignService.class);

    public static final Set<String> CAMPAIGN_STATES = Set.of(
        "DRAFT", "OPEN", "REMINDED_R1", "REMINDED_R2", "ESCALATED", "CLOSED", "CANCELLED");

    public static final Set<String> THRESHOLDS = Set.of(
        "ALL", "CRITICAL_ONLY", "HIGH_AND_ABOVE");

    private final VendorBcpCampaignRepository campaignRepo;
    private final VendorBcpAttestationRepository attestationRepo;
    private final VendorRepository vendorRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final Map<String, NotificationChannel> channelsByType;

    public VendorBcpCampaignService(VendorBcpCampaignRepository campaignRepo,
                                     VendorBcpAttestationRepository attestationRepo,
                                     VendorRepository vendorRepo,
                                     AuditLogService audit,
                                     ApplicationEventPublisher publisher,
                                     List<NotificationChannel> channels) {
        this.campaignRepo = campaignRepo;
        this.attestationRepo = attestationRepo;
        this.vendorRepo = vendorRepo;
        this.audit = audit;
        this.publisher = publisher;
        this.channelsByType = new HashMap<>();
        if (channels != null) {
            for (NotificationChannel c : channels) {
                this.channelsByType.putIfAbsent(c.channelType(), c);
            }
        }
    }

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CampaignResponse> list() {
        return campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org())
            .stream().map(VendorBcpCampaignService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public CampaignStatsResponse stats(Long id) {
        VendorBcpCampaign c = loadOrThrow(id);
        int total = Optional.ofNullable(c.getTotalInvited()).orElse(0);
        int submitted = Optional.ofNullable(c.getTotalSubmitted()).orElse(0);
        int responseRate = total == 0 ? 0 : (int) Math.round((submitted * 100.0) / total);
        int daysUntilClose = c.getClosesAt() == null ? 0
            : (int) Math.max(0, ChronoUnit.DAYS.between(LocalDateTime.now(), c.getClosesAt()));
        return new CampaignStatsResponse(
            c.getId(),
            c.getTotalInvited(),
            c.getTotalSubmitted(),
            c.getTotalAccepted(),
            c.getTotalRejected(),
            c.getTotalOverdue(),
            c.getTotalNa(),
            responseRate,
            daysUntilClose
        );
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest req) {
        validateThreshold(req.mandatoryThreshold());
        Long orgId = org();

        VendorBcpCampaign c = new VendorBcpCampaign();
        c.setOrganisationId(orgId);
        c.setCampaignCode(req.campaignCode() != null && !req.campaignCode().isBlank()
            ? req.campaignCode() : nextCampaignCode(req.cycleYear()));
        if (campaignRepo.existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(orgId, c.getCampaignCode())) {
            throw new IllegalArgumentException("Campaign code already exists: " + c.getCampaignCode());
        }
        c.setTitleEn(req.titleEn());
        c.setTitleAr(req.titleAr());
        c.setDescriptionEn(req.descriptionEn());
        c.setDescriptionAr(req.descriptionAr());
        c.setCycleYear(req.cycleYear());
        // Treat an empty-string quarter as NULL — controller / wizard may send "".
        c.setCycleQuarter(req.cycleQuarter() == null || req.cycleQuarter().isBlank() ? null : req.cycleQuarter());
        c.setClosesAt(req.closesAt());
        c.setReminderCadenceDays(req.reminderCadenceDays() != null ? req.reminderCadenceDays() : 14);
        c.setEscalationThresholdDays(req.escalationThresholdDays() != null ? req.escalationThresholdDays() : 30);
        c.setMandatoryThreshold(req.mandatoryThreshold());
        c.setRegulatorReference(req.regulatorReference());
        c.setStatus("DRAFT");

        VendorBcpCampaign saved = campaignRepo.save(c);
        audit.log("CREATE", "VendorBcpCampaign", saved.getId(),
            "Vendor BCP campaign created: " + saved.getCampaignCode() + " (" + saved.getTitleEn() + ")");
        emit("VENDOR_BCP_CAMPAIGN_CREATED", saved, null);
        return toResponse(saved);
    }

    /**
     * Transition DRAFT → OPEN. Materialises attestation rows for every
     * vendor matching {@link VendorBcpCampaign#getMandatoryThreshold()}.
     */
    @Transactional
    public CampaignResponse openCampaign(Long campaignId) {
        VendorBcpCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "OPEN");

        List<Vendor> targets = resolveTargetVendors(c.getOrganisationId(), c.getMandatoryThreshold());
        if (targets.isEmpty()) {
            // We allow opening with zero targets so a tenant with no
            // critical vendors yet can still record an empty cycle for
            // audit completeness; the close summary explains that.
            log.info("[vbcp] open campaign {} produced 0 invitations (org={})",
                c.getCampaignCode(), c.getOrganisationId());
        }

        LocalDateTime now = LocalDateTime.now();
        List<VendorBcpAttestation> created = new ArrayList<>(targets.size());
        for (Vendor v : targets) {
            VendorBcpAttestation a = new VendorBcpAttestation();
            a.setOrganisationId(c.getOrganisationId());
            a.setCampaignId(c.getId());
            a.setVendorId(v.getId());
            a.setStatus("INVITED");
            a.setInvitedAt(now);
            // contractual_rto/mtpd snapshot would come from a vendor-SLA
            // table when available; for v1 we leave null and only set
            // these when the operator records them via the contract module.
            created.add(attestationRepo.save(a));
        }

        c.setStatus("OPEN");
        c.setOpenedAt(now);
        c.setTotalInvited(created.size());
        c.setUpdatedAt(now);
        VendorBcpCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "VendorBcpCampaign", saved.getId(),
            "Vendor BCP campaign opened: " + saved.getCampaignCode()
                + " — " + created.size() + " invitations materialised");
        emit("VENDOR_BCP_CAMPAIGN_OPENED", saved, "DRAFT");
        return toResponse(saved);
    }

    /**
     * Send reminder round 1 or 2. Vendors still in INVITED (round 1) or
     * REMINDED (round 2) and overdue past {@code reminderCadenceDays} are
     * notified by email and the row + campaign state advances.
     */
    @Transactional
    public CampaignResponse sendReminders(Long campaignId, int round) {
        if (round != 1 && round != 2) {
            throw new IllegalArgumentException("Reminder round must be 1 or 2; got " + round);
        }
        VendorBcpCampaign c = loadOrThrow(campaignId);

        String requiredFrom = round == 1 ? "OPEN" : "REMINDED_R1";
        String toState     = round == 1 ? "REMINDED_R1" : "REMINDED_R2";
        guardTransition(c.getStatus(), toState);

        List<String> eligibleStatuses = round == 1
            ? List.of("INVITED")
            : List.of("INVITED", "REMINDED");
        LocalDateTime cutoff = LocalDateTime.now()
            .minusDays(c.getReminderCadenceDays() != null ? c.getReminderCadenceDays() : 14);
        List<VendorBcpAttestation> due = attestationRepo
            .findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                c.getOrganisationId(), c.getId(), eligibleStatuses, cutoff);

        LocalDateTime now = LocalDateTime.now();
        NotificationChannel email = channelsByType.get("EMAIL");
        int sent = 0;
        for (VendorBcpAttestation a : due) {
            a.setStatus("REMINDED");
            if (round == 1) a.setFirstReminderAt(now);
            else            a.setSecondReminderAt(now);
            a.setUpdatedAt(now);
            attestationRepo.save(a);
            sent++;
            // Dispatch email if a live channel is wired; otherwise we
            // simulate and only record the timestamps so the audit
            // trail is unaffected. Real subject/body comes from the
            // bilingual template files (see resources/templates/email).
            if (email != null && email.isHealthy()) {
                Vendor v = vendorRepo.findById(a.getVendorId()).orElse(null);
                String to = v != null ? v.getContactEmail() : null;
                if (to != null && !to.isBlank()) {
                    email.send(List.of(to),
                        "Reminder " + round + ": " + c.getTitleEn(),
                        "Round " + round + " reminder for campaign " + c.getCampaignCode()
                            + ". Please submit your BCP attestation before " + c.getClosesAt() + ".",
                        "vbcp-reminder-" + c.getId() + "-" + round + "-" + a.getId());
                }
            }
        }

        c.setStatus(toState);
        c.setUpdatedAt(now);
        VendorBcpCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "VendorBcpCampaign", saved.getId(),
            "Vendor BCP campaign reminder round " + round + " sent: "
                + sent + " vendor(s) reminded (" + saved.getCampaignCode() + ")");
        emit("VENDOR_BCP_REMINDER_SENT", saved, requiredFrom,
            Map.of("round", String.valueOf(round), "reminded", String.valueOf(sent)));
        return toResponse(saved);
    }

    /**
     * Escalate overdue rows. Any attestation that is past the campaign
     * escalation threshold AND still unsubmitted is flipped to ESCALATED
     * and the campaign transitions to ESCALATED. Caller is expected to
     * raise an Audit Committee action item — for v1 we emit the F8
     * event and the rule engine wires the action item creation.
     */
    @Transactional
    public CampaignResponse escalateOverdue(Long campaignId) {
        VendorBcpCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "ESCALATED");

        LocalDateTime cutoff = LocalDateTime.now()
            .minusDays(c.getEscalationThresholdDays() != null ? c.getEscalationThresholdDays() : 30);
        List<String> eligibleStatuses = List.of("INVITED", "REMINDED", "OVERDUE");
        List<VendorBcpAttestation> stale = attestationRepo
            .findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
                c.getOrganisationId(), c.getId(), eligibleStatuses, cutoff);

        LocalDateTime now = LocalDateTime.now();
        for (VendorBcpAttestation a : stale) {
            a.setStatus("OVERDUE");
            a.setOverdueAt(a.getOverdueAt() == null ? now : a.getOverdueAt());
            a.setEscalatedAt(now);
            a.setUpdatedAt(now);
            attestationRepo.save(a);
        }

        c.setStatus("ESCALATED");
        c.setTotalOverdue(stale.size());
        c.setUpdatedAt(now);
        VendorBcpCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "VendorBcpCampaign", saved.getId(),
            "Vendor BCP campaign escalated to Audit Committee: "
                + stale.size() + " overdue (" + saved.getCampaignCode() + ")");
        emit("VENDOR_BCP_ESCALATED", saved, "REMINDED_R2",
            Map.of("overdueCount", String.valueOf(stale.size())));
        return toResponse(saved);
    }

    /** Close the campaign, freeze the rollups, write the regulator summary. */
    @Transactional
    public CampaignResponse closeCampaign(Long campaignId, CampaignCloseRequest req) {
        VendorBcpCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "CLOSED");

        // Recompute rollups from the per-row table so the closing
        // snapshot is faithful regardless of intermediate transitions.
        recomputeRollups(c);

        LocalDateTime now = LocalDateTime.now();
        c.setStatus("CLOSED");
        c.setClosedAt(now);
        c.setUpdatedAt(now);
        if (req != null) {
            c.setClosingSummaryEn(req.closingSummaryEn());
            c.setClosingSummaryAr(req.closingSummaryAr());
        }
        VendorBcpCampaign saved = campaignRepo.save(c);

        audit.log("TRANSITION", "VendorBcpCampaign", saved.getId(),
            "Vendor BCP campaign closed: " + saved.getCampaignCode()
                + " — " + saved.getTotalSubmitted() + "/" + saved.getTotalInvited() + " responded");
        emit("VENDOR_BCP_CAMPAIGN_CLOSED", saved, "OPEN");
        return toResponse(saved);
    }

    @Transactional
    public CampaignResponse cancelCampaign(Long campaignId, String reason) {
        VendorBcpCampaign c = loadOrThrow(campaignId);
        guardTransition(c.getStatus(), "CANCELLED");

        LocalDateTime now = LocalDateTime.now();
        c.setStatus("CANCELLED");
        c.setClosedAt(now);
        c.setUpdatedAt(now);
        if (reason != null && !reason.isBlank()) {
            c.setClosingSummaryEn(reason);
        }
        VendorBcpCampaign saved = campaignRepo.save(c);
        audit.log("TRANSITION", "VendorBcpCampaign", saved.getId(),
            "Vendor BCP campaign cancelled: " + saved.getCampaignCode()
                + (reason != null ? " — " + reason : ""));
        emit("VENDOR_BCP_CAMPAIGN_CANCELLED", saved, null);
        return toResponse(saved);
    }

    /** Called by the attestation service whenever any row mutates. */
    @Transactional
    public void recomputeRollups(VendorBcpCampaign c) {
        Long orgId = c.getOrganisationId();
        Long cid = c.getId();
        // submitted = SUBMITTED | ACCEPTED | REJECTED (vendor did respond)
        long submitted = attestationRepo.countByOrganisationIdAndCampaignIdAndStatusInAndIsDeletedFalse(
            orgId, cid, List.of("SUBMITTED", "ACCEPTED", "REJECTED"));
        long accepted = attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(orgId, cid, "ACCEPTED");
        long rejected = attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(orgId, cid, "REJECTED");
        long overdue  = attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(orgId, cid, "OVERDUE");
        long na       = attestationRepo.countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(orgId, cid, "NA_OUT_OF_SCOPE");
        c.setTotalSubmitted((int) submitted);
        c.setTotalAccepted((int) accepted);
        c.setTotalRejected((int) rejected);
        c.setTotalOverdue((int) overdue);
        c.setTotalNa((int) na);
        campaignRepo.save(c);
    }

    // ─── Internal helpers ───────────────────────────────────────────

    private VendorBcpCampaign loadOrThrow(Long id) {
        return campaignRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Campaign not found: " + id));
    }

    private List<Vendor> resolveTargetVendors(Long orgId, String threshold) {
        return switch (threshold) {
            case "ALL"             -> vendorRepo.findByOrganisationIdAndStatus(orgId, "ACTIVE");
            case "CRITICAL_ONLY"   -> vendorRepo.findByOrganisationIdAndRiskRatingAndStatus(orgId, "CRITICAL", "ACTIVE");
            case "HIGH_AND_ABOVE"  -> vendorRepo.findByOrganisationIdAndRiskRatingInAndStatus(
                                          orgId, List.of("CRITICAL", "HIGH"), "ACTIVE");
            default -> throw new IllegalArgumentException("Unknown threshold: " + threshold);
        };
    }

    private static void validateThreshold(String t) {
        if (!THRESHOLDS.contains(t)) {
            throw new IllegalArgumentException("Invalid mandatoryThreshold: " + t);
        }
    }

    /**
     * Campaign state guard. See class-level docs for the full table.
     */
    static void guardTransition(String from, String to) {
        Set<String> allowed = switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"        -> Set.of("OPEN", "CANCELLED");
            case "OPEN"         -> Set.of("REMINDED_R1", "ESCALATED", "CLOSED", "CANCELLED");
            case "REMINDED_R1"  -> Set.of("REMINDED_R2", "ESCALATED", "CLOSED", "CANCELLED");
            case "REMINDED_R2"  -> Set.of("ESCALATED", "CLOSED", "CANCELLED");
            case "ESCALATED"    -> Set.of("CLOSED", "CANCELLED");
            default              -> Set.of();
        };
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    private static String nextCampaignCode(Integer cycleYear) {
        int year = cycleYear != null ? cycleYear : LocalDateTime.now().getYear();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "VBCP-" + year + "-" + suffix;
    }

    // ─── F8 emission ────────────────────────────────────────────────

    private void emit(String trigger, VendorBcpCampaign c, String previousStatus) {
        emit(trigger, c, previousStatus, Map.of());
    }

    private void emit(String trigger, VendorBcpCampaign c, String previousStatus, Map<String, String> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignId", String.valueOf(c.getId()));
        payload.put("campaignCode", c.getCampaignCode());
        payload.put("status", c.getStatus());
        payload.put("previousStatus", previousStatus == null ? "" : previousStatus);
        payload.put("mandatoryThreshold", c.getMandatoryThreshold());
        payload.put("totalInvited", String.valueOf(c.getTotalInvited()));
        payload.put("totalSubmitted", String.valueOf(c.getTotalSubmitted()));
        if (extra != null) payload.putAll(extra);
        publisher.publishEvent(new DomainEvent(
            c.getOrganisationId(), trigger, "VendorBcpCampaign", c.getId(),
            "vendor-bcp-campaign:" + c.getId(), payload));
    }

    // ─── Mapping ────────────────────────────────────────────────────

    static CampaignResponse toResponse(VendorBcpCampaign c) {
        return new CampaignResponse(
            c.getId(), c.getExternalId(), c.getOrganisationId(),
            c.getCampaignCode(), c.getTitleEn(), c.getTitleAr(),
            c.getDescriptionEn(), c.getDescriptionAr(),
            c.getCycleYear(), c.getCycleQuarter(),
            c.getOpenedAt(), c.getClosesAt(), c.getClosedAt(),
            c.getReminderCadenceDays(), c.getEscalationThresholdDays(),
            c.getStatus(), c.getMandatoryThreshold(), c.getRegulatorReference(),
            c.getTotalInvited(), c.getTotalSubmitted(), c.getTotalAccepted(),
            c.getTotalRejected(), c.getTotalOverdue(), c.getTotalNa(),
            c.getClosingSummaryEn(), c.getClosingSummaryAr(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    @SuppressWarnings("unused")
    private Duration _touch() { return Duration.ZERO; }
}
