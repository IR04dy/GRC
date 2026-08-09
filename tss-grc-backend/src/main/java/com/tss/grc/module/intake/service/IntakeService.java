package com.tss.grc.module.intake.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.IntakeCatalog;
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
 * IntakeService — DD-82, rebuilt for F2-172.
 *
 * <p>Implements the same submit / triage / case-track lifecycle the React {@code useIntake()} hook
 * drives. Routing metadata lives in {@link IntakeCatalog}, generated from the frontend catalogue
 * and pinned to it — it used to be three private maps here covering 16 of the 26 kinds, and the ten
 * missing ones would each have been refused as "unknown" the moment a client actually posted.
 *
 * <h3>What F2-172 changed, and why it is more than a wiring fix</h3>
 * {@code triage(PROMOTE)} used to take {@code promotedToEntityType} and {@code promotedToRecordId}
 * <b>from the request body</b> and create nothing. So the row it marked "Promoted to DPIA #482913"
 * pointed at a record that had never been written — on the server as well as in the browser
 * (F2-171). Promotion now:
 *
 * <ol>
 *   <li>reads the <b>target from the kind</b>, so a client cannot promote a DSR into the risk
 *       register;</li>
 *   <li>requires the target module's own write permission on top of {@code INTAKE:TRIAGE}, so
 *       promotion is not a side door into fourteen modules;</li>
 *   <li>creates the record through that module's service, in this transaction;</li>
 *   <li>stamps {@code promoted_to_record_id} from <b>the row that was created</b>, and</li>
 *   <li>refuses when no register can take it, rather than recording a promotion that did nothing.</li>
 * </ol>
 *
 * <p>Points 3 and 4 share a transaction on purpose. A promoted status with no record — or a record
 * with no promoted status — is the inconsistency the whole finding is about, and rolling both back
 * together is the only way the intake row cannot outlive its own claim.
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
        IntakeCatalog.Entry catalog = IntakeCatalog.require(req.getKind());

        IntakeRecord r = new IntakeRecord();
        r.setOrganisationId(orgId);
        r.setReferenceCode(generateReferenceCode(catalog.codePrefix()));
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
        r.setAssignedTo(catalog.defaultAssignee());
        r.setSlaDueAt(now.plusHours(catalog.slaHours()));

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
            String target = IntakeCatalog.require(r.getKind()).defaultTarget();
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
        IntakeCatalog.Entry catalog = IntakeCatalog.require(r.getKind());
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
        return new IntakePromotionContext(
            r.getId(), r.getReferenceCode(), r.getKind(), r.getSeverity(),
            r.getSubmitterName(), r.getSubmitterEmail(),
            Boolean.TRUE.equals(r.getSubmitterIsAnonymous()),
            r.getLocale(), r.getAcknowledgedAt(), req.getNote(), actor,
            payload, catalog.titleEn(), catalog.titleAr());
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
    private String generateReferenceCode(String codePrefix) {
        return codePrefix + "-" + segment() + "-" + segment();
    }

    private String segment() {
        // 4 chars from base-36 alphabet.
        StringBuilder sb = new StringBuilder(4);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no I/O/0/1
        for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        return sb.toString();
    }
}
