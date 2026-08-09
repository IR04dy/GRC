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
import com.tss.grc.module.intake.spi.SystemIntakePort;
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
 *
 * <h3>F2-175 — one construction site, and why that is the whole fix</h3>
 *
 * <p>Everything above describes properties of {@code submit()}. A CRITICAL model-drift observation
 * opened a {@code RISK_ISSUE} intake by building the entity in {@code MlModelDriftService} and calling
 * the repository directly, so it had <b>none</b> of them: wrong assignee, its own reference-code
 * format, its own SLA arithmetic, no history events, and a hand-rolled JSON escaper that emitted
 * invalid JSON for a tab — which made the record unpromotable, blocking the escalation the mechanism
 * exists to perform. See {@link SystemIntakePort} for the measurements.
 *
 * <p>The lesson is in the shape rather than the list: <b>a rule enforced at one writer is not
 * enforced.</b> So {@link #fileDraft} is now the only place in the platform where an
 * {@code IntakeRecord} is constructed — {@code submit()} and {@link #fileSystemSubmission} each decide
 * only the questions that are genuinely theirs (who filed it, and whether an anonymity claim is
 * honoured) and hand the rest over. {@code IntakeSingleWriterInvariantTest} pins the count at one, and
 * pins that no peer module imports anything of this module but its {@code spi}.
 */
@Service
@RequiredArgsConstructor
public class IntakeService implements SystemIntakePort {

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

        return IntakeRecordDto.from(fileDraft(new Draft(
            orgId, req.getKind(), req.getChannel(), req.getSeverity(), req.getLocale(),
            req.getPayload(), anonymous, submitterUserId, req.getSubmitterRole(),
            // For an honoured anonymous submission nothing identifying is persisted, whatever the
            // body carried. Dropping these is the point of the channel, not an oversight.
            anonymous ? null : req.getSubmitterName(),
            anonymous ? null : req.getSubmitterEmail(),
            anonymous ? null : req.getSubmitterPhone(),
            actorLabel, "Submitted via " + req.getChannel())));
    }

    /**
     * F2-175 — file a submission on behalf of a platform mechanism. See {@link SystemIntakePort} for
     * the five ways the writer this replaces disagreed with the paragraph above it.
     *
     * <p>Two arguments are validated here rather than trusted, and both are the kind of thing a
     * caller gets wrong once:
     *
     * <ul>
     *   <li><b>the organisation</b>, because a system caller may have no {@code TenantContext} at all
     *       and a signature that quietly falls back to the ambient tenant files another customer's
     *       record the first time it runs on a scheduler thread;</li>
     *   <li><b>the actor label</b>, because it is the only provenance a system-filed record has. The
     *       submitter columns are empty by design — there is no user — so a blank label produces a
     *       record with a completely anonymous origin that was never <em>asked</em> to be anonymous.
     *       That is indistinguishable, in the inbox and in the history, from an honoured
     *       whistleblower submission, and the two must not be confusable.</li>
     * </ul>
     */
    @Override
    @Transactional
    public Receipt fileSystemSubmission(SystemSubmission s) {
        if (s == null) throw new IllegalArgumentException("submission is required");
        if (s.organisationId() == null) {
            throw new IllegalArgumentException(
                "A system intake submission must name its organisation explicitly: schedulers and "
                + "async listeners have no TenantContext, so there is no ambient tenant to fall back "
                + "to — and falling back is how a background job files one customer's finding into "
                + "another customer's register.");
        }
        if (s.systemActor() == null || s.systemActor().isBlank()) {
            throw new IllegalArgumentException(
                "A system intake submission must name the mechanism that filed it — it is the "
                + "record's only provenance, and without it the submission is indistinguishable "
                + "from an anonymous human report.");
        }

        IntakeRecord saved = fileDraft(new Draft(
            s.organisationId(), s.kind(), s.channel(), s.severity(),
            // No locale: a machine has no display language, and inventing "en" on an Arabic-primary
            // platform is a claim about a reader who does not exist. No adapter reads it.
            null, writePayload(s.payload(), s.kind()),
            false, null, null, s.systemActor(), null, null,
            "system", "Filed automatically by " + s.systemActor()));

        return new Receipt(saved.getId(), saved.getReferenceCode(),
            saved.getAssignedTo(), saved.getSlaDueAt());
    }

    /**
     * Everything a submission is, once the questions only its own entry point can answer have been
     * answered. Internal on purpose — it is not a second request DTO, it is the argument list of the
     * one construction site, named so a fourteen-parameter call is readable.
     */
    private record Draft(
        Long orgId, String kind, String channel, String severity, String locale, String payload,
        boolean anonymous, Long submitterUserId, String submitterRole,
        String submitterName, String submitterEmail, String submitterPhone,
        String actorLabel, String submissionNote
    ) {
    }

    /**
     * <b>The only place in the platform an {@code IntakeRecord} is constructed</b> (F2-175, pinned by
     * {@code IntakeSingleWriterInvariantTest}). Reference code, SLA and assignee all come from the
     * catalogue here, so no caller can supply or derive its own — which is exactly what the second
     * writer did with all three.
     */
    private IntakeRecord fileDraft(Draft d) {
        // Re-resolved rather than passed in: this is the validity gate, and a kind that reached here
        // uncatalogued must fail here even if some future caller forgot to ask.
        IntakeKindRouting.Routing routing = IntakeKindRouting.require(d.kind());

        IntakeRecord r = new IntakeRecord();
        r.setOrganisationId(d.orgId());
        r.setReferenceCode(generateReferenceCode(routing));
        r.setKind(d.kind());
        r.setStatus("TRIAGING");                   // auto-acknowledge → triaging
        r.setChannel(d.channel());
        r.setSubmitterIsAnonymous(d.anonymous());
        r.setSubmitterUserId(d.submitterUserId());
        r.setSubmitterRole(d.submitterRole());
        r.setSubmitterName(d.submitterName());
        r.setSubmitterEmail(d.submitterEmail());
        r.setSubmitterPhone(d.submitterPhone());
        r.setPayload(d.payload());
        r.setSeverity(Optional.ofNullable(d.severity()).orElse("MEDIUM"));
        r.setLocale(d.locale());

        LocalDateTime now = LocalDateTime.now();
        r.setAcknowledgedAt(now);
        r.setAssignedTo(routing.defaultAssignee());
        r.setSlaDueAt(now.plusHours(routing.slaHours()));

        // Auto-acknowledgement event + triage-routing event. These two lines are an intake record's
        // whole audit trail, and the writer F2-175 retired produced neither.
        r.getHistory().add(buildEvent(r, now, "SUBMITTED", d.actorLabel(), d.submissionNote()));
        r.getHistory().add(buildEvent(r, now, "TRIAGING", "system",
            "Auto-acknowledged. Routed to " + r.getAssignedTo()));

        return repo.save(r);
    }

    /**
     * Serialise a system caller's facts with the mapper, never by hand.
     *
     * <p>This is the half of F2-175 that had a live consequence. The retired writer concatenated JSON
     * with its own {@code esc()} covering backslash, quote, {@code \n} and {@code \r} — and JSON
     * requires <em>every</em> C0 control character escaped. A model title carrying a tab produced a
     * payload Jackson rejects, which {@link #contextFor} reports as
     * <em>"has an unreadable payload and cannot be promoted"</em>: the submission files, and then can
     * never be escalated into the register it was raised for. Round-tripping through the mapper makes
     * that unrepresentable rather than unlikely.
     */
    private String writePayload(Map<String, Object> payload, String kind) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            return json.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "System submission of kind " + kind + " has a payload that cannot be serialised: "
                + e.getMessage(), e);
        }
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
