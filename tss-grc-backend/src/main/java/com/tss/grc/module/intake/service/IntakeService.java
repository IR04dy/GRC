package com.tss.grc.module.intake.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.dto.IntakePublicStatusDto;
import com.tss.grc.module.intake.dto.IntakeRecordDto;
import com.tss.grc.module.intake.dto.IntakeSubmissionRequest;
import com.tss.grc.module.intake.dto.TriageDecisionRequest;
import com.tss.grc.module.intake.entity.IntakeEvent;
import com.tss.grc.module.intake.entity.IntakeRecord;
import com.tss.grc.module.intake.repository.IntakeRecordRepository;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.PromotionRegistry;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * IntakeService — DD-82, reworked by F2-172.
 *
 * <p>Implements the submit / triage / case-track lifecycle the React {@code useIntake()} hook
 * drives. Routing metadata lives in {@link IntakeKindRouting}, which is parity-checked against the
 * frontend catalogue by {@code IntakeCatalogueParityTest} — see that class for what the three
 * parallel maps this replaced were getting wrong, and for the ten kinds the server had never heard
 * of.
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
 *       {@code promotedToEntityType} and {@code promotedToRecordId} from the request body, so a
 *       client could promote a DSR into the risk register and name the row it landed in. The type
 *       is now derived from the catalogue and the id comes from the write — see below.</li>
 *
 *   <li><b>Promotion creates the register record.</b> "Promotion creates the target row" was
 *       implemented on <em>neither</em> side: this method created nothing, and the browser minted
 *       {@code Date.now() % 1_000_000} — a positive integer in exactly the range real IDENTITY
 *       values occupy (F2-171). Promotion now calls a {@link com.tss.grc.module.intake.spi
 *       .RegisterPromotionPort} implemented once per target register, stamps
 *       {@code promoted_to_record_id} from <b>the row that was written</b>, and requires the target
 *       module's own write permission on top of {@code INTAKE:TRIAGE} — promotion is a decision
 *       about a submission, not a side door into fourteen modules. Where no register can take the
 *       submission it <b>refuses</b> (409) rather than flipping the status and writing nothing.</li>
 * </ol>
 *
 * <p>The create and the stamp share this transaction on purpose. A promoted status with no record —
 * or a record with no promoted status — is the inconsistency the whole finding is about, and
 * rolling both back together is the only way the intake row cannot outlive its own claim.
 */
@Service
@RequiredArgsConstructor
public class IntakeService {

    private final IntakeRecordRepository repo;
    private final PromotionRegistry promotions;
    private final PermissionEvaluator permissions;
    private final ObjectMapper json;
    private static final SecureRandom RNG = new SecureRandom();

    @Transactional
    public IntakeRecordDto submit(IntakeSubmissionRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException("No tenant context — public intake submission requires an org-resolver");
        }
        IntakeKindRouting.Routing routing = IntakeKindRouting.require(req.getKind());

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

        String promotionNote = null;
        if ("PROMOTED".equals(newStatus)) {
            // Not idempotent by nature: promoting twice creates two register records, and the
            // second one silently overwrites the pointer to the first, orphaning it. Cheap to
            // notice here; impossible to notice afterwards.
            if ("PROMOTED".equals(r.getStatus())) {
                throw new IllegalStateException(r.getReferenceCode() + " was already promoted to "
                    + r.getPromotedToEntityType() + " #" + r.getPromotedToRecordId()
                    + " on " + r.getPromotedAt() + ". Promoting again would create a second record.");
            }
            // The target register is a property of the kind, not a caller's claim. A routing is
            // guaranteed present: submit() refuses an uncatalogued kind, so a stored record has one.
            String target = IntakeKindRouting.require(r.getKind()).defaultTarget();
            requireTargetPermission(target, r.getReferenceCode());
            String recordId = promotions.promote(target, contextFor(r, req, actor));

            r.setPromotedToEntityType(target);
            r.setPromotedToRecordId(recordId);
            r.setPromotedAt(LocalDateTime.now());
            promotionNote = "Promoted to " + target + " #" + recordId;
        } else if ("REJECTED".equals(newStatus)) {
            r.setRejectionReason(req.getNote());
        }
        r.setStatus(newStatus);

        // The promotion note names the record that was actually created, so a triager's own note
        // cannot replace it: the history line "Promoted to DPIA #482913" pointing at nothing is the
        // artefact F2-171 was found by, and the id in it has to come from the write.
        String note = "PROMOTE".equals(req.getDecision())
            ? promotionNote + (req.getNote() == null || req.getNote().isBlank() ? "" : " — " + req.getNote())
            : Optional.ofNullable(req.getNote()).orElse(
                "REJECT".equals(req.getDecision()) ? "Rejected" : "Closed without promotion");
        r.getHistory().add(buildEvent(r, LocalDateTime.now(), newStatus, actor, note));

        return IntakeRecordDto.from(repo.save(r));
    }

    /**
     * Build the read-only view an adapter gets. The payload is parsed here, once, so fourteen
     * adapters do not each hand-roll a JSON parse — and so a malformed payload fails with a message
     * naming the intake rather than as a {@code JsonParseException} from inside some other module.
     */
    private IntakePromotionContext contextFor(IntakeRecord r, TriageDecisionRequest req, String actor) {
        IntakeKindRouting.Routing routing = IntakeKindRouting.require(r.getKind());
        Map<String, Object> payload;
        try {
            payload = (r.getPayload() == null || r.getPayload().isBlank())
                ? Map.of()
                : json.readValue(r.getPayload(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(
                "Intake " + r.getReferenceCode() + " has an unreadable payload and cannot be "
                + "promoted: " + e.getMessage());
        }
        // submitterName/Email are already NULL for an honoured anonymous submission — submit()
        // drops them at the door. ownerName() falls through to the triager, so an anonymous
        // whistleblower report still gets an owned record without naming the reporter.
        return new IntakePromotionContext(
            r.getId(), r.getReferenceCode(), r.getKind(), r.getSeverity(),
            r.getSubmitterName(), r.getSubmitterEmail(),
            Boolean.TRUE.equals(r.getSubmitterIsAnonymous()),
            r.getLocale(), r.getAcknowledgedAt(), req.getNote(), actor,
            payload, routing.titleEn(), routing.titleAr());
    }

    /**
     * {@code INTAKE:TRIAGE} lets you decide a submission; it does not let you create a record in
     * somebody else's register. See {@code RegisterPromotionPort#requiredPermission} for the
     * measurement behind that.
     */
    private void requireTargetPermission(String target, String referenceCode) {
        String required = promotions.requiredPermissionFor(target);
        if (required == null) return;          // no port — PromotionRegistry raises the real error
        String[] parts = required.split(":", 2);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !permissions.hasPermission(auth, parts[0], parts[1])) {
            throw new DomainAccessDeniedException(
                "Promoting " + referenceCode + " creates a " + target + " record, which requires "
                + required + " in addition to INTAKE:TRIAGE. Reassign the submission to someone who "
                + "holds it, or reject it.");
        }
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
