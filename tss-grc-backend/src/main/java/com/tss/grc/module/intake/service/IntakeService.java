package com.tss.grc.module.intake.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.dto.IntakeRecordDto;
import com.tss.grc.module.intake.dto.IntakeSubmissionRequest;
import com.tss.grc.module.intake.dto.TriageDecisionRequest;
import com.tss.grc.module.intake.entity.IntakeEvent;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * IntakeService — DD-82.
 *
 * Implements the same submit / triage / case-track lifecycle the React
 * useIntake() hook drives. Routing metadata (default assignee, SLA hours,
 * code prefix, target register) is mirrored from frontend INTAKE_CATALOG
 * so the two stay in sync. When the backend grows further, this catalog
 * should move to a shared module.
 */
@Service
@RequiredArgsConstructor
public class IntakeService {

    private final IntakeRecordRepository repo;
    private static final SecureRandom RNG = new SecureRandom();

    /** SLA hours per intake kind — mirrors frontend INTAKE_CATALOG. */
    private static final Map<String, Integer> SLA_HOURS = Map.ofEntries(
        Map.entry("DSR_REQUEST",         30 * 24),
        Map.entry("BREACH_REPORT",       72),
        Map.entry("VRP_DISCLOSURE",      24 * 5),
        Map.entry("SPEAKUP",             24 * 7),
        Map.entry("SECURITY_INCIDENT",   72),
        Map.entry("PRIVACY_BREACH",      72),
        Map.entry("RISK_ISSUE",          24 * 14),
        Map.entry("CONTROL_OBSERVATION", 24 * 14),
        Map.entry("POLICY_EXCEPTION",    24 * 7),
        Map.entry("VENDOR_ONBOARDING",   24 * 14),
        Map.entry("DPIA_INITIATION",     24 * 14),
        Map.entry("BIA_SUBMISSION",      24 * 30),
        Map.entry("AUDIT_REQUEST",       24 * 14),
        Map.entry("CHANGE_REQUEST",      48),
        Map.entry("ETHICS_DISCLOSURE",   24 * 14),
        Map.entry("CONTROL_ATTESTATION", 24 * 14)
    );

    /** Default assignee per intake kind. */
    private static final Map<String, String> DEFAULT_ASSIGNEE = Map.ofEntries(
        Map.entry("DSR_REQUEST",         "DPO"),
        Map.entry("BREACH_REPORT",       "DPO"),
        Map.entry("VRP_DISCLOSURE",      "CISO_OFFICE"),
        Map.entry("SPEAKUP",             "ETHICS_CTE"),
        Map.entry("SECURITY_INCIDENT",   "CSIRT"),
        Map.entry("PRIVACY_BREACH",      "DPO"),
        Map.entry("RISK_ISSUE",          "RISK_OFFICER"),
        Map.entry("CONTROL_OBSERVATION", "COMPLIANCE_LEAD"),
        Map.entry("POLICY_EXCEPTION",    "POLICY_OFFICER"),
        Map.entry("VENDOR_ONBOARDING",   "TPRM_LEAD"),
        Map.entry("DPIA_INITIATION",     "DPO"),
        Map.entry("BIA_SUBMISSION",      "BCM_LEAD"),
        Map.entry("AUDIT_REQUEST",       "AUDIT_LEAD"),
        Map.entry("CHANGE_REQUEST",      "CAB"),
        Map.entry("ETHICS_DISCLOSURE",   "ETHICS_OFFICER"),
        Map.entry("CONTROL_ATTESTATION", "COMPLIANCE_LEAD")
    );

    /** Two-letter code prefix per intake kind for human-friendly tracking codes. */
    private static final Map<String, String> CODE_PREFIX = Map.ofEntries(
        Map.entry("DSR_REQUEST", "DS"),         Map.entry("BREACH_REPORT", "BR"),
        Map.entry("VRP_DISCLOSURE", "VR"),      Map.entry("SPEAKUP", "SU"),
        Map.entry("SECURITY_INCIDENT", "SI"),   Map.entry("PRIVACY_BREACH", "PB"),
        Map.entry("RISK_ISSUE", "RI"),          Map.entry("CONTROL_OBSERVATION", "CO"),
        Map.entry("POLICY_EXCEPTION", "PE"),    Map.entry("VENDOR_ONBOARDING", "VO"),
        Map.entry("DPIA_INITIATION", "DI"),     Map.entry("BIA_SUBMISSION", "BI"),
        Map.entry("AUDIT_REQUEST", "AR"),       Map.entry("CHANGE_REQUEST", "CR"),
        Map.entry("ETHICS_DISCLOSURE", "ED"),   Map.entry("CONTROL_ATTESTATION", "CA")
    );

    @Transactional
    public IntakeRecordDto submit(IntakeSubmissionRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException("No tenant context — public intake submission requires an org-resolver");
        }
        if (!SLA_HOURS.containsKey(req.getKind())) {
            throw new IllegalArgumentException("Unknown intake kind: " + req.getKind());
        }

        IntakeRecord r = new IntakeRecord();
        r.setOrganisationId(orgId);
        r.setReferenceCode(generateReferenceCode(req.getKind()));
        r.setKind(req.getKind());
        r.setStatus("TRIAGING");                   // auto-acknowledge → triaging
        r.setChannel(req.getChannel());
        r.setSubmitterIsAnonymous(req.getSubmitterIsAnonymous());
        r.setSubmitterRole(req.getSubmitterRole());
        r.setSubmitterName(req.getSubmitterName());
        r.setSubmitterEmail(req.getSubmitterEmail());
        r.setSubmitterPhone(req.getSubmitterPhone());
        r.setPayload(req.getPayload());
        r.setSeverity(Optional.ofNullable(req.getSeverity()).orElse("MEDIUM"));
        r.setLocale(req.getLocale());

        LocalDateTime now = LocalDateTime.now();
        r.setAcknowledgedAt(now);
        r.setAssignedTo(DEFAULT_ASSIGNEE.get(req.getKind()));
        r.setSlaDueAt(now.plusHours(SLA_HOURS.get(req.getKind())));

        // Auto-acknowledgement event + triage-routing event.
        r.getHistory().add(buildEvent(r, now, "SUBMITTED",
            req.getSubmitterIsAnonymous() ? "anonymous"
                : Optional.ofNullable(req.getSubmitterEmail()).orElse(Optional.ofNullable(req.getSubmitterName()).orElse("unknown")),
            "Submitted via " + req.getChannel()));
        r.getHistory().add(buildEvent(r, now, "TRIAGING", "system",
            "Auto-acknowledged. Routed to " + r.getAssignedTo()));

        return IntakeRecordDto.from(repo.save(r));
    }

    @Transactional(readOnly = true)
    public Page<IntakeRecordDto> list(String status, String kind, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        Page<IntakeRecord> page;
        if (status != null) page = repo.findByOrganisationIdAndStatus(orgId, status, pageable);
        else if (kind != null) page = repo.findByOrganisationIdAndKind(orgId, kind, pageable);
        else page = repo.findByOrganisationId(orgId, pageable);
        return page.map(IntakeRecordDto::from);
    }

    /** Public case-tracker lookup — anonymous submitters use this from
     *  /portal/case-tracker?ref=<code> to see their submission status. */
    @Transactional(readOnly = true)
    public Optional<IntakeRecordDto> findByReferenceCode(String referenceCode) {
        return repo.findByReferenceCode(referenceCode).map(IntakeRecordDto::from);
    }

    @Transactional
    public IntakeRecordDto triage(Long id, TriageDecisionRequest req, String actor) {
        Long orgId = TenantContext.getCurrentTenant();
        IntakeRecord r = repo.findById(id)
            .filter(x -> orgId == null || orgId.equals(x.getOrganisationId()))
            .orElseThrow(() -> new EntityNotFoundException("IntakeRecord " + id));

        String newStatus = switch (req.getDecision()) {
            case "PROMOTE" -> "PROMOTED";
            case "REJECT"  -> "REJECTED";
            case "CLOSE"   -> "CLOSED";
            default -> throw new IllegalArgumentException("decision must be PROMOTE | REJECT | CLOSE");
        };
        r.setStatus(newStatus);

        if ("PROMOTED".equals(newStatus)) {
            r.setPromotedToEntityType(req.getPromotedToEntityType());
            r.setPromotedToRecordId(req.getPromotedToRecordId());
            r.setPromotedAt(LocalDateTime.now());
        } else if ("REJECTED".equals(newStatus)) {
            r.setRejectionReason(req.getNote());
        }

        String note = Optional.ofNullable(req.getNote()).orElse(switch (req.getDecision()) {
            case "PROMOTE" -> "Promoted to " + req.getPromotedToEntityType() + " #" + req.getPromotedToRecordId();
            case "REJECT"  -> "Rejected";
            default        -> "Closed without promotion";
        });
        r.getHistory().add(buildEvent(r, LocalDateTime.now(), newStatus, actor, note));

        return IntakeRecordDto.from(repo.save(r));
    }

    private IntakeEvent buildEvent(IntakeRecord r, LocalDateTime at, String status, String actor, String note) {
        IntakeEvent e = new IntakeEvent();
        e.setIntakeRecord(r);
        e.setAt(at);
        e.setStatus(status);
        e.setActor(actor);
        e.setNote(note);
        return e;
    }

    /** Generate the public reference code — format "XX-XXXX-YYYY". */
    private String generateReferenceCode(String kind) {
        String prefix = CODE_PREFIX.getOrDefault(kind, "II");
        return prefix + "-" + segment() + "-" + segment();
    }

    private String segment() {
        // 4 chars from base-36 alphabet.
        StringBuilder sb = new StringBuilder(4);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no I/O/0/1
        for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        return sb.toString();
    }
}
