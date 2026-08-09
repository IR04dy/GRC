package com.tss.grc.module.intake.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.dto.IntakePublicStatusDto;
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
import java.util.Optional;

/**
 * IntakeService — DD-82, reworked by F2-172.
 *
 * <p>Implements the submit / triage / case-track lifecycle the React {@code useIntake()} hook
 * drives. Routing metadata lives in {@link IntakeKindRouting}, which is parity-checked against the
 * frontend catalogue by {@code IntakeCatalogueParityTest} — see that class for what the three
 * parallel maps this replaced were getting wrong.
 *
 * <h3>What F2-172 changed, and why each change is a security property rather than a tidy-up</h3>
 *
 * <ol>
 *   <li><b>The submitter is bound by the server.</b> {@code submit()} read
 *       {@code submitterName}/{@code Email}/{@code Phone}/{@code Role} out of the request body and
 *       stored them verbatim, and the table had no column naming a platform user at all. That is
 *       RC-E in the platform's front door. It was latent only because the endpoint's guard admitted
 *       3 of 69 roles, all administrators — the same shape as F2-144, where a lockout was the only
 *       thing preventing an unbound write. V374 adds {@code submitter_user_id} and this class fills
 *       it from {@link CurrentActor}; the body's identity fields are <b>ignored, not validated</b>,
 *       following {@code CoiService#declarantFor}'s reasoning that rejecting them would leak
 *       whether another identity exists.</li>
 *
 *   <li><b>An anonymity claim is honoured only where the catalogue allows it.</b> This is the
 *       non-obvious half. {@code submitterIsAnonymous} arrives from the client, and if the server
 *       simply obeyed it, then <em>every</em> submission could be made unattributable on request —
 *       which would defeat the binding above completely, using the field intended to protect
 *       whistleblowers. So the claim is honoured for the kinds whose catalogue entry sets
 *       {@code allowsAnonymous} (SPEAKUP, BREACH_REPORT, VRP_DISCLOSURE) and refused everywhere
 *       else. Where it IS honoured, nothing identifying is stored — not the user id, and not the
 *       name, email or phone the body may still carry — because ISO 37001 §8.3 and Nazaha expect
 *       real anonymity, and a record that quietly retains the reporter's identity is worse than one
 *       that never claimed to hide it.</li>
 *
 *   <li><b>The promotion target is server truth.</b> {@code triage()} took
 *       {@code promotedToEntityType} and {@code promotedToRecordId} from the request body and
 *       created nothing, so "promotion creates the register row" was implemented on neither side
 *       (F2-171 found the client half minting {@code Date.now() % 1_000_000} — a positive integer
 *       in exactly the range real IDENTITY values occupy). The type is now derived from the
 *       catalogue and the id is <b>refused</b>: whoever creates the downstream row is the only
 *       party that can know its id, and until an adapter exists to create it the honest value is
 *       NULL with an event note saying so. Accepting the client's number would have made a
 *       placeholder durable and visible to every user in the tenant, which is strictly worse than
 *       the browser-local one it replaced.</li>
 * </ol>
 *
 * <p>⚠ Still open, and deliberately not papered over: nothing creates the promoted register record.
 * That is F2-172's second half — a port declared here with one adapter per target module, per
 * ADR-012's boundary rules — and it is why {@code promotedToRecordId} stays NULL rather than
 * receiving a synthesised value.
 */
@Service
@RequiredArgsConstructor
public class IntakeService {

    private final IntakeRecordRepository repo;
    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Event note written when a triager promotes an intake. It names the absence rather than
     * implying a record: the old note read {@code "Promoted to DPIA #482913"}, naming an id that
     * belonged to nothing on the server and, at a measured 0.056% of promotions in dev, to a real
     * unrelated record.
     */
    static final String PROMOTION_TARGET_PENDING =
        "Promoted. The %s record has not been created yet — intake promotion does not yet write to "
        + "the target register (F2-172), so this intake is the only record of the decision.";

    @Transactional
    public IntakeRecordDto submit(IntakeSubmissionRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException("No tenant context — public intake submission requires an org-resolver");
        }
        IntakeKindRouting.Routing routing = IntakeKindRouting.get(req.getKind());
        if (routing == null) {
            throw new IllegalArgumentException("Unknown intake kind: " + req.getKind());
        }

        // ── The two identity decisions, in this order. ──────────────────────────────────────────
        // (1) Is the submission anonymous? A claim is a request, not an instruction: it is honoured
        //     only for the kinds whose catalogue entry allows it. Otherwise every intake could be
        //     filed unattributably on demand.
        boolean anonymous = Boolean.TRUE.equals(req.getSubmitterIsAnonymous()) && routing.allowsAnonymous();
        // (2) Who filed it? From the security context, never from the body. NULL for an honoured
        //     anonymous submission — there the absence IS the feature.
        Long submitterUserId = anonymous ? null : CurrentActor.userId();
        String actorLabel = anonymous ? "anonymous"
            : Optional.ofNullable(CurrentActor.username()).orElse("unknown");

        IntakeRecord r = new IntakeRecord();
        r.setOrganisationId(orgId);
        r.setReferenceCode(generateReferenceCode(routing));
        r.setKind(req.getKind());
        r.setStatus("TRIAGING");                   // auto-acknowledge → triaging
        r.setChannel(req.getChannel());
        r.setSubmitterIsAnonymous(anonymous);
        r.setSubmitterUserId(submitterUserId);
        r.setSubmitterRole(req.getSubmitterRole());
        // For an honoured anonymous submission nothing identifying is persisted, whatever the body
        // carried. Dropping these is the point of the channel, not an oversight.
        r.setSubmitterName(anonymous ? null : req.getSubmitterName());
        r.setSubmitterEmail(anonymous ? null : req.getSubmitterEmail());
        r.setSubmitterPhone(anonymous ? null : req.getSubmitterPhone());
        r.setPayload(req.getPayload());
        r.setSeverity(Optional.ofNullable(req.getSeverity()).orElse("MEDIUM"));
        r.setLocale(req.getLocale());

        LocalDateTime now = LocalDateTime.now();
        r.setAcknowledgedAt(now);
        r.setAssignedTo(routing.defaultAssignee());
        r.setSlaDueAt(now.plusHours(routing.slaHours()));

        // Auto-acknowledgement event + triage-routing event.
        r.getHistory().add(buildEvent(r, now, "SUBMITTED", actorLabel,
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

    /**
     * One record with its full event history — the read {@code IntakeController}'s javadoc has
     * advertised since DD-82 and which was never written. Org-scoped like {@link #triage}: a record
     * belonging to another tenant is reported as absent rather than forbidden, so the response does
     * not disclose that the id exists.
     *
     * <p>Returns a DTO, not the entity. {@code IntakeRecord.history} is a LAZY {@code @OneToMany},
     * so a handler returning the entity would 500 outside the session — the landmine F2-158 found
     * live in {@code RoleController#list}, and which {@code LazyEntitySerialisationInvariantTest}
     * now refuses outright.
     */
    @Transactional(readOnly = true)
    public IntakeRecordDto findOne(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(x -> orgId == null || orgId.equals(x.getOrganisationId()))
            .map(IntakeRecordDto::from)
            .orElseThrow(() -> new EntityNotFoundException("IntakeRecord " + id));
    }

    /** Public case-tracker lookup — anonymous submitters use this from
     *  /portal/case-tracker?ref=&lt;code&gt; to see their submission status.
     *  SECURITY (SEC-FIN-001): returns a minimal, PII-free projection only —
     *  never submitter identity, payload, assignee, or event history. */
    @Transactional(readOnly = true)
    public Optional<IntakePublicStatusDto> findPublicStatusByReferenceCode(String referenceCode) {
        return repo.findByReferenceCode(referenceCode).map(IntakePublicStatusDto::from);
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

        String targetType = null;
        if ("PROMOTED".equals(newStatus)) {
            // The target register is a property of the kind, not a caller's claim. A routing is
            // guaranteed present: submit() refuses an uncatalogued kind, so a stored record always
            // has one.
            IntakeKindRouting.Routing routing = IntakeKindRouting.get(r.getKind());
            targetType = routing != null ? routing.defaultTarget() : null;
            r.setPromotedToEntityType(targetType);
            // Deliberately NOT req.getPromotedToRecordId(). See the class javadoc: no code creates
            // the downstream row yet, so the only id a client could supply is invented.
            r.setPromotedToRecordId(null);
            r.setPromotedAt(LocalDateTime.now());
        } else if ("REJECTED".equals(newStatus)) {
            r.setRejectionReason(req.getNote());
        }

        String note = Optional.ofNullable(req.getNote()).orElse(switch (req.getDecision()) {
            case "PROMOTE" -> PROMOTION_TARGET_PENDING.formatted(targetType);
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
    private String generateReferenceCode(IntakeKindRouting.Routing routing) {
        return routing.codePrefix() + "-" + segment() + "-" + segment();
    }

    private String segment() {
        // 4 chars from base-36 alphabet.
        StringBuilder sb = new StringBuilder(4);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no I/O/0/1
        for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        return sb.toString();
    }
}
