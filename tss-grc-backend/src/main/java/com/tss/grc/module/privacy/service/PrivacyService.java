package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.DsrRequestLifecycle;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ConsentRecordRepository;
import com.tss.grc.module.privacy.repository.DataBreachRepository;
import com.tss.grc.module.privacy.repository.DataSubjectRequestRepository;
import com.tss.grc.module.privacy.repository.DpiaRepository;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrivacyService {

    private final DataBreachRepository breachRepo;
    private final DataSubjectRequestRepository dsrRepo;
    private final ProcessingActivityRepository ropaRepo;
    private final DpiaRepository dpiaRepo;
    private final ConsentRecordRepository consentRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    /** Open Issue #006 — optional F7 audit sink for the SDAIA submission flow. */
    @Autowired(required = false)
    private com.tss.grc.module.platform.auditlog.service.AuditLogService auditLog;

    /** Open Issue #006 — optional F8 publisher for the SDAIA submission flow. */
    @Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private void emitBreach(DataBreach b) {
        if (bcast != null) bcast.entity(b.getOrganisationId(), "data_breach", b.getId(), b);
    }
    private void emitDsr(DataSubjectRequest r) {
        if (bcast != null) bcast.entity(r.getOrganisationId(), "dsr_case", r.getId(), r);
    }
    private void emitRopa(ProcessingActivity a) {
        if (bcast != null) bcast.entity(a.getOrganisationId(), "processing_activity", a.getId(), a);
    }

    // ── Data Breaches ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DataBreach> listBreaches(Pageable pageable) {
        return breachRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public DataBreach getBreach(Long id) {
        return breachRepo.findById(id)
            .filter(b -> b.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Breach not found: " + id));
    }

    @Transactional
    /**
     * F2-231 — the SDAIA discharge stamps are {@link #recordSdaiaSubmission}'s to write and the
     * Data Office stamp is {@link #notifyDataOffice}'s, so this create must not accept either.
     *
     * <p>{@code PrivacyController} binds the {@code DataBreach} ENTITY (F2-214's shape), so before
     * this every column outside {@code ServerOwnedFieldsModule}'s nine was caller-writable at
     * create — including {@code sdaiaSubmittedAt}, which the entity's own javadoc calls the
     * discharge of the PDPL Article 31 clock. A caller could file a breach already recorded as
     * SUBMITTED to the regulator, skipping the four-state machine, with no audit row and no
     * {@code BREACH_REPORTED_TO_SDAIA} event.
     *
     * <p>⚠ Why F2-213 and F2-214 both missed it: each wrote a careful comment on
     * {@link #updateBreach} / {@link #updateDsr} explaining that exactly these fields must not come
     * from a payload — and the create two lines above accepted all of them. F2-215's headline:
     * fixing the field you noticed leaves the remainder looking covered, because the mechanism that
     * covers them is visibly present.
     *
     * <p>Latent, measured 2026-08-17: {@code sdaia_submitted_at}, {@code sdaia_submission_ref} and
     * {@code pdpc_notified_at} are null on all 17 dev / 18 prod rows and no client type declares
     * any of them, which is why clearing them cannot break a request that works today (F2-182).
     * {@code sdaia_notification_status} is NOT_REPORTED on 100% of rows in both databases, so
     * forcing the initial value is equally free.
     *
     * <p>⚠ {@code status} is deliberately NOT constrained here. The column has no CHECK, no declared
     * machine and seven stored values across two databases (F2-209 filed the vocabulary itself), so
     * naming a creatable set is a decision about live rows in two real databases — F2-183/F2-196's
     * owner boundary, not a side effect of closing a stamp. FILED.
     */
    public DataBreach createBreach(DataBreach req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setSdaiaNotificationStatus(SDAIA_NOT_REPORTED);
        req.setSdaiaSubmittedAt(null);
        req.setSdaiaSubmissionRef(null);
        req.setPdpcNotifiedAt(null);
        return breachRepo.save(req);
    }

    @Transactional
    public DataBreach updateBreach(Long id, DataBreach req) {
        DataBreach existing = getBreach(id);
        existing.setTitleEn(req.getTitleEn());
        // F2-271 — the Arabic pair is editable exactly like its English sibling. Adding a
        // column that no write path fills would be an inert column (F2-198's shape), which
        // is why V387 and these two lines are one commit.
        existing.setTitleAr(req.getTitleAr());
        existing.setDescriptionEn(req.getDescriptionEn());
        existing.setDescriptionAr(req.getDescriptionAr());
        existing.setBreachType(req.getBreachType());
        existing.setDiscoveredAt(req.getDiscoveredAt());
        existing.setDataSubjectsAffectedCount(req.getDataSubjectsAffectedCount());
        existing.setPdpcNotificationRequired(req.getPdpcNotificationRequired());
        // F2-214 — `pdpcNotifiedAt` is NOT editable through this endpoint and must not be
        // taken from its payload. It is the evidence the Saudi Data Office was actually told,
        // stamped by `notifyDataOffice`; `PrivacyBreachRequest` (the only client) cannot
        // declare it, so it arrived null on every call and was written null. Same shape as
        // F2-213's `completedAt` on the DSR update, and latent for the same reason — the
        // column is null on all 17 live rows in dev and prod, so there was nothing to lose
        // yet. A defect with nothing to damage is not a smaller defect; it is one nobody can
        // notice until the feature is used.
        existing.setStatus(req.getStatus());
        return breachRepo.save(existing);
    }

    @Transactional
    public void deleteBreach(Long id) {
        DataBreach b = getBreach(id);
        b.setIsDeleted(true);
        breachRepo.save(b);
    }

    // ── SDAIA Notification Workflow (Open Issue #006) ─────────────────────────
    //
    // PDPL Article 31 (Implementing Regulations Art. 40) — the controller
    // must notify SDAIA within 72 hours of awareness for material personal-data
    // breaches. SDAIA has no machine-readable API; submission is via the SDAIA
    // portal (https://www.sdaia.gov.sa/) and the DPO records the outcome here.
    //
    // The workflow is a simple state machine:
    //   NOT_REPORTED  → IN_PREPARATION | SUBMITTED
    //   IN_PREPARATION → SUBMITTED | NOT_REPORTED
    //   SUBMITTED     → ACKNOWLEDGED
    //   ACKNOWLEDGED  (terminal)

    /**
     * Valid SDAIA notification states.
     *
     * <p>Package-private rather than private so {@code PrivacyServiceTest} can assert that
     * {@link #SDAIA_DISCHARGED} is a genuine SUBSET of it. The static block below already
     * enforces that at class-init, but a test that can only trust the block cannot prove the
     * block's premise.
     */
    static final java.util.Set<String> SDAIA_STATES = java.util.Set.of(
        "NOT_REPORTED", "IN_PREPARATION", "SUBMITTED", "ACKNOWLEDGED");

    /**
     * The states in which the notification has actually been POSTED to SDAIA, so the
     * PDPL Article 31 72-hour clock is discharged.
     *
     * <p>{@code IN_PREPARATION} is progress, not discharge - the entity's own javadoc puts
     * the discharge at {@code sdaia_submitted_at} being set, and that stamp is written only
     * on the way to these two states. This is the server-side twin of the frontend's
     * {@code isSdaiaSubmitted} (F2-209), and it is a subset of {@link #SDAIA_STATES} rather
     * than a second vocabulary: the static block below refuses a member that is not one of
     * them, because a token one letter out would silently count nothing and read exactly
     * like a tenant that has reported no breaches (F2-177).
     *
     * <p>Sorted for a stable, diffable repository query parameter.
     */
    public static final java.util.List<String> SDAIA_DISCHARGED =
        java.util.List.of("ACKNOWLEDGED", "SUBMITTED");

    static {
        for (String s : SDAIA_DISCHARGED) {
            if (!SDAIA_STATES.contains(s)) {
                throw new IllegalStateException(
                    "SDAIA_DISCHARGED names '" + s + "', which is not a member of SDAIA_STATES "
                    + SDAIA_STATES + " - a discharged state the vocabulary does not contain can "
                    + "never match a stored row, so the count would silently read zero.");
            }
        }
    }

    /**
     * The state a breach starts in — nothing has been said to the regulator yet.
     *
     * <p>F2-231 declares this once and gives it three readers rather than adding a fourth literal:
     * {@link #createBreach} forces it, the {@code prior} fallback below reads it, and
     * {@link #recordSdaiaSubmission}'s own vocabulary contains it. F2-190's rule — an unread second
     * copy is how the next reader decides which one is the authority.
     */
    static final String SDAIA_NOT_REPORTED = "NOT_REPORTED";

    /**
     * Record a manual SDAIA submission outcome (Open Issue #006).
     *
     * <p>Called by the DPO (or CISO/PLATFORM_ADMIN) after they have posted the
     * breach report via the SDAIA portal. Transitions the breach to status
     * {@code SUBMITTED} (or {@code ACKNOWLEDGED} when SDAIA returns a reference),
     * stamps the submission reference and timestamp, and emits an F7 audit row
     * + F8 trigger {@code BREACH_REPORTED_TO_SDAIA}. The PDPL Article 31 72-hour
     * clock is discharged when {@code sdaia_submitted_at} is set.
     */
    @Transactional
    public DataBreach recordSdaiaSubmission(Long id,
                                            String submissionRef,
                                            java.time.LocalDateTime submittedAt,
                                            String targetStatus) {
        DataBreach breach = getBreach(id);
        String resolvedStatus = targetStatus == null ? "SUBMITTED" : targetStatus.toUpperCase(Locale.ROOT);
        if (!SDAIA_STATES.contains(resolvedStatus)) {
            throw new IllegalArgumentException(
                "Invalid SDAIA status: " + targetStatus +
                " (allowed: NOT_REPORTED, IN_PREPARATION, SUBMITTED, ACKNOWLEDGED)");
        }
        String prior = breach.getSdaiaNotificationStatus() == null
            ? SDAIA_NOT_REPORTED : breach.getSdaiaNotificationStatus();
        // Guard: ACKNOWLEDGED is terminal — re-opening would lose the regulator
        // closure record. Only an explicit re-classification (handled outside
        // this endpoint, via the privacy admin path) can roll it back.
        if ("ACKNOWLEDGED".equals(prior) && !"ACKNOWLEDGED".equals(resolvedStatus)) {
            throw new IllegalStateException(
                "Breach " + id + " already ACKNOWLEDGED by SDAIA — cannot move to " + resolvedStatus);
        }
        breach.setSdaiaNotificationStatus(resolvedStatus);
        if (submissionRef != null && !submissionRef.isBlank()) {
            breach.setSdaiaSubmissionRef(submissionRef);
        }
        if (submittedAt != null) {
            breach.setSdaiaSubmittedAt(submittedAt);
        } else if ("SUBMITTED".equals(resolvedStatus) && breach.getSdaiaSubmittedAt() == null) {
            // No explicit timestamp provided — stamp now (DPO submitted "just now").
            breach.setSdaiaSubmittedAt(java.time.LocalDateTime.now());
        }
        DataBreach saved = breachRepo.save(breach);
        emitBreach(saved);

        // F7 audit + F8 event
        try {
            if (auditLog != null) {
                auditLog.log("BREACH_REPORTED_TO_SDAIA", "DataBreach", saved.getId(),
                    String.format("SDAIA status: %s -> %s (ref=%s)", prior, resolvedStatus,
                        saved.getSdaiaSubmissionRef() == null ? "-" : saved.getSdaiaSubmissionRef()));
            }
        } catch (Exception ex) {
            // Audit failures must not block the regulator-discharge path.
            log.warn("F7 audit log failed for SDAIA submission on breach {}", id, ex);
        }
        try {
            if (eventPublisher != null) {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("breachId",       saved.getId());
                payload.put("priorStatus",    prior);
                payload.put("newStatus",      resolvedStatus);
                payload.put("submissionRef",  saved.getSdaiaSubmissionRef());
                payload.put("submittedAt",
                    saved.getSdaiaSubmittedAt() == null ? null : saved.getSdaiaSubmittedAt().toString());
                eventPublisher.publishEvent(new com.tss.grc.module.platform.automation.event.DomainEvent(
                    saved.getOrganisationId(),
                    "BREACH_REPORTED_TO_SDAIA",
                    "DataBreach",
                    saved.getId(),
                    null,
                    payload));
            }
        } catch (Exception ex) {
            // Event-bus failure must not block the regulator-discharge path.
            log.warn("F8 event publish failed for SDAIA submission on breach {}", id, ex);
        }

        return saved;
    }

    /** Returns the submitted state of the breach for the SDAIA status DTO. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getSdaiaStatus(Long id) {
        DataBreach b = getBreach(id);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("breachId",                 b.getId());
        out.put("discoveredAt",             b.getDiscoveredAt());
        out.put("sdaiaNotificationStatus",  b.getSdaiaNotificationStatus());
        out.put("sdaiaSubmissionRef",       b.getSdaiaSubmissionRef());
        out.put("sdaiaSubmittedAt",         b.getSdaiaSubmittedAt());
        out.put("pdplArticle31DeadlineAt",
            b.getDiscoveredAt() == null ? null : b.getDiscoveredAt().plusHours(72));
        return out;
    }

    // ── Breach workflow (PDPL Article 20 — 72-hour notification) ──────────────

    @Transactional
    public DataBreach containBreach(Long id) {
        DataBreach b = getBreach(id);
        if (!"OPEN".equals(b.getStatus())) {
            throw new IllegalArgumentException("Breach must be OPEN to contain (was " + b.getStatus() + ")");
        }
        b.setStatus("INVESTIGATING");
        DataBreach saved = breachRepo.save(b);
        emitBreach(saved);
        return saved;
    }

    /**
     * Mark the Saudi Data Office as notified for this breach. Records the
     * notification timestamp; the consumer should subsequently attach the
     * submission package as evidence.
     */
    @Transactional
    public DataBreach notifyDataOffice(Long id) {
        DataBreach b = getBreach(id);
        b.setPdpcNotificationRequired(true);
        b.setPdpcNotifiedAt(java.time.LocalDateTime.now());
        DataBreach saved = breachRepo.save(b);
        emitBreach(saved);
        return saved;
    }

    @Transactional
    public DataBreach closeBreach(Long id) {
        DataBreach b = getBreach(id);
        if (!"INVESTIGATING".equals(b.getStatus()) && !"OPEN".equals(b.getStatus())) {
            throw new IllegalArgumentException("Breach must be OPEN or INVESTIGATING to close (was " + b.getStatus() + ")");
        }
        b.setStatus("CLOSED");
        DataBreach saved = breachRepo.save(b);
        emitBreach(saved);
        return saved;
    }

    // ── Data Subject Requests ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DataSubjectRequest> listDsrs(Pageable pageable) {
        return dsrRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public DataSubjectRequest getDsr(Long id) {
        return dsrRepo.findById(id)
            .filter(r -> r.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("DSR not found: " + id));
    }

    @Transactional
    /**
     * F2-231 — {@code completedAt} is {@link #fulfilDsr}'s to stamp, so this create must not accept
     * it. It is the timestamp evidencing that the PDPL Article 23 response was made in time; a
     * caller could file a request already carrying proof it had been answered.
     *
     * <p>Latent: null on all 20 rows in dev and prod, and {@code PrivacyDsrRequest} — the only
     * client type — does not declare it, so clearing it cannot break a working request (F2-182).
     * Same field, same reason, as the comment on {@link #updateDsr} directly below; F2-213 fixed
     * the update and this create sat two lines above it.
     *
     * <p>⚠ {@code dueDate} is deliberately left alone and FILED. It is the Article 4 statutory
     * deadline and the server ought to mint it (F2-202: only the server may issue a statutory
     * deadline) — but {@code PrivacyDsrRequest} DOES declare it, it is non-null on 20 of 20 rows in
     * both databases, and nothing refuses a caller-supplied value today. So by F2-182's test every
     * value that succeeds now is a client-chosen deadline, and refusing one would break requests
     * that work: an owner decision, not a side effect. {@code status} is filed for the same reason
     * as the breach half — no CHECK, no declared machine, and {@code DsrRequestLifecycle} declares
     * only TERMINAL, so {@code requireInitial} would refuse the NEW/OPEN/PENDING creates the
     * register already holds (F2-220's measured lesson).
     */
    public DataSubjectRequest createDsr(DataSubjectRequest req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setCompletedAt(null);
        return dsrRepo.save(req);
    }

    @Transactional
    public DataSubjectRequest updateDsr(Long id, DataSubjectRequest req) {
        DataSubjectRequest existing = getDsr(id);
        existing.setRequestType(req.getRequestType());
        existing.setRequesterName(req.getRequesterName());
        existing.setRequesterEmail(req.getRequesterEmail());
        existing.setDueDate(req.getDueDate());
        existing.setStatus(req.getStatus());
        // F2-213 — `assignedTo` and `completedAt` are NOT editable through this endpoint and
        // must not be taken from its payload. `PrivacyDsrRequest` (the only client) cannot
        // declare either, so both arrived null on every call and were written null: editing
        // a PDPL Article 23 request would have discarded its owner and `completed_at`, the
        // timestamp that evidences the statutory response was made in time. Latent today —
        // both columns are null on all 20 live rows in dev and prod — which is exactly why
        // it survived: there was nothing to destroy yet. `completedAt` is stamped by the
        // fulfil path, `assignedTo` by assignment.
        return dsrRepo.save(existing);
    }

    @Transactional
    public void deleteDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        r.setIsDeleted(true);
        dsrRepo.save(r);
    }

    // ── DSR workflow (PDPL — 30-day fulfilment SLA) ───────────────────────────

    /**
     * Start fulfilling a DSR. Transitions OPEN → IN_PROGRESS and, if no
     * due_date was set, defaults it to received_at + 30 days per PDPL.
     */
    @Transactional
    public DataSubjectRequest startDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        if (DsrRequestLifecycle.isTerminal(r.getStatus())) {
            throw new IllegalArgumentException("DSR is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus("IN_PROGRESS");
        if (r.getDueDate() == null) {
            java.time.LocalDateTime base = r.getReceivedAt() != null ? r.getReceivedAt() : java.time.LocalDateTime.now();
            r.setDueDate(base.toLocalDate().plusDays(30));
        }
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    @Transactional
    public DataSubjectRequest fulfilDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        if (DsrRequestLifecycle.isTerminal(r.getStatus())) {
            throw new IllegalArgumentException("DSR is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus("FULFILLED");
        r.setCompletedAt(java.time.LocalDateTime.now());
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    /**
     * Extend the DSR due date by N days. PDPL Implementing Regulations allow
     * the controller to extend the response window once, with notification to
     * the data subject. Default extension is 30 days.
     */
    @Transactional
    public DataSubjectRequest extendDsr(Long id, Integer days) {
        DataSubjectRequest r = getDsr(id);
        int n = (days == null || days <= 0) ? 30 : days;
        java.time.LocalDate base = r.getDueDate() != null ? r.getDueDate() : java.time.LocalDate.now();
        r.setDueDate(base.plusDays(n));
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    @Transactional
    public DataSubjectRequest rejectDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        if (DsrRequestLifecycle.isTerminal(r.getStatus())) {
            throw new IllegalArgumentException("DSR is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus("REJECTED");
        r.setCompletedAt(java.time.LocalDateTime.now());
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    // ── Processing Activities (ROPA) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProcessingActivity> listRopa(Pageable pageable) {
        return ropaRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public ProcessingActivity getRopa(Long id) {
        return ropaRepo.findById(id)
            .filter(a -> a.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Processing activity not found: " + id));
    }

    @Transactional
    public ProcessingActivity createRopa(ProcessingActivity req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return ropaRepo.save(req);
    }

    @Transactional
    public ProcessingActivity updateRopa(Long id, ProcessingActivity req) {
        ProcessingActivity existing = getRopa(id);
        existing.setActivityNameEn(req.getActivityNameEn());
        existing.setActivityNameAr(req.getActivityNameAr());
        existing.setPurposeEn(req.getPurposeEn());
        // F2-272 — the Arabic purpose must be carried by the SAME whitelist as the English one.
        // Without this line V388's purpose_ar is fillable on create (the endpoint binds the entity)
        // and then permanently UNEDITABLE: this method copies its whitelist onto the LOADED row,
        // so a field it does not copy keeps its stored value rather than being nulled, and an edit
        // to the Arabic purpose is accepted with HTTP 200 and silently discarded (F2-214's shape,
        // not F2-213's reset). Probed both ways: pre-fix a PUT sending a changed purposeAr left
        // the stored value untouched.
        // ⚠ activityNameAr two lines up is exactly why this read as covered: a reader who
        // finds an Arabic field in the whitelist does not then ask which others are missing
        // (F2-215's two-of-N, inside a single method).
        existing.setPurposeAr(req.getPurposeAr());
        existing.setLegalBasis(req.getLegalBasis());
        // F2-278 — `PrivacyRopaRequest` has ALWAYS declared `dataCategoriesJson` and this
        // whitelist omitted it, so the PDPL Article 31 register's categories-of-personal-data
        // field was collected by the wizard, stored on create, and then discarded on every
        // edit (F2-214's shape: accepted with HTTP 200 and silently dropped). Populated on
        // 2 of 11 live rows in dev and prod. Nothing declared it as a decision, which is why
        // the reverse direction of the coverage gate is the leg that found it.
        existing.setDataCategoriesJson(req.getDataCategoriesJson());
        existing.setRetentionPeriodMonths(req.getRetentionPeriodMonths());
        existing.setCrossBorderTransfer(req.getCrossBorderTransfer());
        existing.setStatus(req.getStatus());
        return ropaRepo.save(existing);
    }

    @Transactional
    public void deleteRopa(Long id) {
        ProcessingActivity a = getRopa(id);
        a.setIsDeleted(true);
        ropaRepo.save(a);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Privacy / PDPL dashboard stats. Returns the full shape the dashboard
     * reads (ropaCount, highRiskRopa, pia*, dsr*, consents*, cross-border,
     * breach*) — earlier this returned only three keys with different names,
     * so every dashboard tile read {@code undefined} and rendered 0. The
     * legacy keys (totalBreaches/openDsrs/totalRopa) are kept for back-compat.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();

        long totalRopa     = ropaRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements();
        long crossBorder   = ropaRepo.findByOrganisationIdAndCrossBorderTransferTrue(orgId).size();
        long highRiskRopa  = ropaRepo.findByOrganisationIdAndSpecialCategoryFlagTrue(orgId).size();

        // F2-196: this was an inclusion list of four statuses and so answered 10 of 20 rows
        // in dev and prod — it silently dropped ESCALATED, which is emphatically open. Open
        // is now the complement of the declared terminal set, so a status nobody anticipated
        // is counted and chased rather than excused. ESCALATED is also reported separately
        // below as the at-risk bucket; being at risk does not stop it being open.
        long dsrOpen = dsrRepo.countByOrganisationIdAndStatusNotIn(orgId, DsrRequestLifecycle.TERMINAL_LIST);

        // F2-458: this was `countByOrganisationIdAndStatus(orgId, "ESCALATED")`, published under
        // the key `dsrSlaBreaches` and rendered "SLA breaches" beside a radial gauge captioned
        // "within SLA". Escalation is what an operator does when somebody NOTICES a request is
        // late; the register carries the deadline itself, so the proxy was measurable against the
        // real thing. Measured, byte-identical in dev and the deployed database: 12 of 12 open
        // requests are past their due_date (74 to 504 days over) and 2 are ESCALATED, so the
        // gauge read 83% within SLA where the honest answer is 0%. The name was right and the
        // arithmetic was a stand-in.
        long dsrPastDue = dsrRepo.countByOrganisationIdAndStatusNotInAndDueDateBefore(
                orgId, DsrRequestLifecycle.TERMINAL_LIST, java.time.LocalDate.now());

        long openBreaches = breachRepo.countByOrganisationIdAndStatus(orgId, "OPEN")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATING");
        // F2-458: `incidents12m` still counts the lifecycle status NOTIFIED, correctly - it is
        // one term of "how many breaches exist". What may NOT be read off the lifecycle is
        // whether the regulator was told, which is the next statement.
        long lifecycleNotified = breachRepo.countByOrganisationIdAndStatus(orgId, "NOTIFIED");

        // F2-458: the "Regulator-notified" figure was `lifecycleNotified` - the breach lifecycle
        // status, not the column recording what was said to SDAIA. F2-209 measured that those two
        // disagree and corrected the reader in PdplBreachConsole; this is the reader it did not
        // touch. Measured, byte-identical in both databases: 4 of 17 breaches are status NOTIFIED
        // while all 17 are sdaia_notification_status = NOT_REPORTED with sdaia_submitted_at NULL,
        // so the dashboard reported four submissions to SDAIA where there have been none.
        long sdaiaSubmitted = breachRepo.countByOrganisationIdAndSdaiaNotificationStatusIn(
                orgId, SDAIA_DISCHARGED);
        long incidents12m = openBreaches + lifecycleNotified
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATED")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "ESCALATED")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "REMEDIATED")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "CLOSED");

        long piaCompleted = dpiaRepo.countByOrganisationIdAndStatus(orgId, "APPROVED");
        long piaOverdue   = dpiaRepo.countByOrganisationIdAndStatus(orgId, "EXPIRED");

        long activeConsents = consentRepo.countByOrganisationIdAndStatus(orgId, "ACTIVE")
                            + consentRepo.countByOrganisationIdAndStatus(orgId, "GRANTED");
        long withdrawn      = consentRepo.countByOrganisationIdAndStatus(orgId, "WITHDRAWN");

        Map<String, Long> m = new LinkedHashMap<>();
        // Legacy keys (back-compat).
        m.put("totalRopa", totalRopa);
        m.put("openDsrs", dsrOpen);
        m.put("totalBreaches", openBreaches);
        // Dashboard shape.
        m.put("ropaCount", totalRopa);
        m.put("highRiskRopa", highRiskRopa);
        m.put("crossBorderTransfers", crossBorder);
        m.put("piaCompleted", piaCompleted);
        m.put("piaOverdue", piaOverdue);
        m.put("dsrOpen", dsrOpen);
        m.put("dsrSlaBreaches", dsrPastDue);
        m.put("activeConsents", activeConsents);
        m.put("withdrawnConsents30d", withdrawn);
        m.put("privacyIncidents12m", incidents12m);
        m.put("breachNotifications", sdaiaSubmitted);
        // F2-458: `processorsWithoutDpa` and `retentionViolations` used to be published here as
        // literal 0L under the comment "Not tracked in this module yet - surfaced as 0 rather
        // than absent", as though 0 were the safer of the two. It is the opposite. The dashboard
        // renders both with tone `> 0 ? danger : success`, so a control this module does not
        // track at all appeared as a GREEN tile asserting zero processors without a DPA and zero
        // retention violations. An invented figure invites a question; an invented all-clear
        // closes one (F2-197). Both keys are gone, and the surface renders UX-008's em-dash with
        // a reason, which is what "absent" actually looks like to an operator.
        return m;
    }
}
