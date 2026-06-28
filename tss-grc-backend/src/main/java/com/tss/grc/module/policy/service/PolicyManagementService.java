package com.tss.grc.module.policy.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.compliance.entity.Control;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.repository.PolicyAttestationRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.repository.PolicyExceptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Policy Management service — V79.
 *
 * <p>Three state machines:
 * <ul>
 *   <li><b>Policy</b>: DRAFT → REVIEW → APPROVED → PUBLISHED → EXPIRED |
 *       ARCHIVED. PUBLISHED stamps published_at; transition to PUBLISHED
 *       requires effective_date.</li>
 *   <li><b>Exception</b>: REQUESTED → UNDER_REVIEW → APPROVED | REJECTED.
 *       APPROVED can later flip to EXPIRED (expiry sweep) or REVOKED.</li>
 *   <li><b>Attestation</b>: PENDING → ATTESTED | DECLINED. EXPIRED applies
 *       when the underlying policy is superseded.</li>
 * </ul>
 */
// QUAL-RC-001: renamed from PolicyService → PolicyManagementService so the bean
// name is the natural class-derived "policyManagementService" and it no longer
// collides with module.compliance's PolicyService (which stays "policyService").
@Service
@RequiredArgsConstructor
public class PolicyManagementService {

    private static final Set<String> POLICY_STATUSES = Set.of(
        "DRAFT", "REVIEW", "APPROVED", "PUBLISHED", "EXPIRED", "ARCHIVED");
    private static final Set<String> EXCEPTION_STATUSES = Set.of(
        "REQUESTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "EXPIRED", "REVOKED");
    private static final Set<String> ATT_STATUSES = Set.of(
        "PENDING", "ATTESTED", "DECLINED", "EXPIRED");

    private final PolicyDocumentRepository docs;
    private final PolicyExceptionRepository exceptions;
    private final PolicyAttestationRepository attestations;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final ControlRepository controls;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── C-12: read-side resolution of free-text control cross-links ────────────
    //
    // Policies store their mapped controls as a free-text field (relatedControls,
    // e.g. "1-1-1, 2-3-1"). Rather than a destructive migration, this resolves
    // those codes against the real compliance control library at read time:
    // every code is returned flagged matched/unmatched (+ the control's id,
    // bilingual name and framework when matched), so stale or typo'd references
    // surface to the auditor and the UI can render verified links. The stored
    // free-text is never altered.

    /** Resolve a free-text control-code list to real controls (read-only, additive). */
    public List<ResolvedControl> resolveControls(String relatedControls) {
        if (relatedControls == null || relatedControls.isBlank()) return List.of();
        // split on comma / semicolon / whitespace; trim; dedupe; preserve order
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String tok : relatedControls.split("[,;\\s]+")) {
            String c = tok.trim();
            if (!c.isEmpty()) codes.add(c);
        }
        if (codes.isEmpty()) return List.of();
        Map<String, Control> byCode = new HashMap<>();
        for (Control ctl : controls.findByOrganisationIdAndCodeIn(org(), codes)) {
            byCode.putIfAbsent(ctl.getCode(), ctl);
        }
        List<ResolvedControl> out = new ArrayList<>(codes.size());
        for (String code : codes) {
            Control c = byCode.get(code);
            out.add(c != null
                ? new ResolvedControl(code, true, c.getId(), c.getNameEn(), c.getNameAr(), c.getFramework())
                : new ResolvedControl(code, false, null, null, null, null));
        }
        return out;
    }

    /** One resolved (or unresolved) control cross-link (C-12). */
    public record ResolvedControl(
        String code, boolean matched, Long controlId,
        String nameEn, String nameAr, String framework) {}

    // ── F8 payload + emit helpers ─────────────────────────────────────────────
    //
    // Policy events anchor downstream attestation + compliance workflows:
    //   POLICY_PUBLISHED         — new version went live (payload carries
    //                              policyCode + version + category)
    //   POLICY_TRANSITIONED      — any other state change (carries
    //                              previousStatus + status)
    //   POLICY_EXPIRED           — expiry sweep flipped a row
    //   POLICY_EXCEPTION_APPROVED — exception approved (carries riskLevel)
    //   POLICY_EXCEPTION_EXPIRED  — exception expiry sweep
    //   POLICY_ATTESTED          — attestation recorded
    //   POLICY_ATTESTATION_DECLINED — attestation declined

    private Map<String, Object> policyPayload(PolicyDocument p, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("policyId",       nullSafeStr(p.getId()));
        m.put("policyCode",     nullSafeStr(p.getPolicyCode()));
        m.put("version",        nullSafeStr(p.getVersion()));
        m.put("status",         nullSafeStr(p.getStatus()));
        m.put("previousStatus", nullSafeStr(previousStatus));
        m.put("category",       nullSafeStr(p.getCategory()));
        m.put("ownerEmail",     nullSafeStr(p.getOwnerEmail()));
        m.put("approverEmail",  nullSafeStr(p.getApproverEmail()));
        m.put("effectiveDate",  nullSafeStr(p.getEffectiveDate()));
        m.put("expiryDate",     nullSafeStr(p.getExpiryDate()));
        return m;
    }

    private Map<String, Object> exceptionPayload(PolicyException e, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("exceptionId",    nullSafeStr(e.getId()));
        m.put("policyId",       nullSafeStr(e.getPolicyId()));
        m.put("status",         nullSafeStr(e.getStatus()));
        m.put("previousStatus", nullSafeStr(previousStatus));
        m.put("riskLevel",      nullSafeStr(e.getRiskLevel()));
        m.put("expiryDate",     nullSafeStr(e.getExpiryDate()));
        return m;
    }

    private Map<String, Object> attestationPayload(PolicyAttestation a, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("attestationId",  nullSafeStr(a.getId()));
        m.put("policyId",       nullSafeStr(a.getPolicyId()));
        m.put("status",         nullSafeStr(a.getStatus()));
        m.put("previousStatus", nullSafeStr(previousStatus));
        m.put("userUsername",   nullSafeStr(a.getUserUsername()));
        m.put("evidenceRef",    nullSafeStr(a.getEvidenceRef()));
        return m;
    }

    /** "" for null so the rule engine's equality matcher cannot NPE. */
    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    /** "<not provided>" for null — for human-readable audit messages. */
    private static String nullSafe(Object o) { return o == null ? "<not provided>" : o.toString(); }

    private void emitPolicy(String trigger, PolicyDocument p, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            org(), trigger, "PolicyDocument", p.getId(),
            "policy:" + nullSafe(p.getPolicyCode()) + ":v" + nullSafe(p.getVersion()),
            policyPayload(p, previousStatus)));
    }

    private void emitException(String trigger, PolicyException e, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            org(), trigger, "PolicyException", e.getId(),
            "policy_exception:" + e.getId(),
            exceptionPayload(e, previousStatus)));
    }

    private void emitAttestation(String trigger, PolicyAttestation a, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            org(), trigger, "PolicyAttestation", a.getId(),
            "policy_attestation:" + a.getId(),
            attestationPayload(a, previousStatus)));
    }

    public record PolicyAggregate(
        long total,
        int published,
        int inReview,
        int draft,
        int expired,
        int expiringSoon,
        int openExceptions,
        int approvedExceptions,
        int pendingAttestations,
        Map<String, Long> byCategory
    ) {}

    // ─── Documents ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PolicyDocument> list(String status, String category, boolean latestOnly) {
        Long o = org();
        if (latestOnly) return docs.findLatestVersionPerCode(o);
        if (status != null)   return docs.findByOrganisationIdAndStatusOrderByPolicyCodeAscVersionDesc(o, status);
        if (category != null) return docs.findByOrganisationIdAndCategoryOrderByPolicyCodeAscVersionDesc(o, category);
        return docs.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(o);
    }

    @Transactional(readOnly = true)
    public PolicyDocument get(Long id) {
        return docs.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Policy not found: " + id));
    }

    @Transactional
    public PolicyDocument create(PolicyDocument body) {
        if (body.getPolicyCode() == null || body.getPolicyCode().isBlank())
            throw new IllegalArgumentException("policyCode is required");
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getVersion() == null) body.setVersion("1.0");
        if (body.getStatus() == null)  body.setStatus("DRAFT");
        if (!POLICY_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid policy status: " + body.getStatus());
        if (docs.findByOrganisationIdAndPolicyCodeAndVersion(org(), body.getPolicyCode(), body.getVersion()).isPresent())
            throw new IllegalArgumentException(
                "Policy version already exists: " + body.getPolicyCode() + " v" + body.getVersion());
        body.setId(null);
        body.setOrganisationId(org());
        PolicyDocument saved = docs.save(body);
        audit.log("CREATE", "PolicyDocument", saved.getId(),
            "Policy created: " + nullSafe(saved.getPolicyCode()) + " v" + nullSafe(saved.getVersion())
                + " (" + nullSafe(saved.getStatus()) + ")");
        emitPolicy("POLICY_CREATED", saved, null);
        return saved;
    }

    @Transactional
    public PolicyDocument update(Long id, PolicyDocument body) {
        PolicyDocument p = get(id);
        // Once PUBLISHED, only metadata fields are editable — body / classification stay frozen
        // unless the row is reopened via /transition to DRAFT (which clones a new version).
        if ("PUBLISHED".equals(p.getStatus())) {
            if (body.getNextReviewDate() != null) p.setNextReviewDate(body.getNextReviewDate());
            if (body.getNotes() != null)          p.setNotes(body.getNotes());
            if (body.getRetiredReason() != null)  p.setRetiredReason(body.getRetiredReason());
            return docs.save(p);
        }
        if (body.getTitleEn() != null)         p.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)         p.setTitleAr(body.getTitleAr());
        if (body.getSummaryEn() != null)       p.setSummaryEn(body.getSummaryEn());
        if (body.getSummaryAr() != null)       p.setSummaryAr(body.getSummaryAr());
        if (body.getBodyUrl() != null)         p.setBodyUrl(body.getBodyUrl());
        if (body.getCategory() != null)        p.setCategory(body.getCategory());
        if (body.getApplicabilityScope() != null) p.setApplicabilityScope(body.getApplicabilityScope());
        if (body.getOwnerName() != null)       p.setOwnerName(body.getOwnerName());
        if (body.getOwnerEmail() != null)      p.setOwnerEmail(body.getOwnerEmail());
        if (body.getApproverName() != null)    p.setApproverName(body.getApproverName());
        if (body.getApproverEmail() != null)   p.setApproverEmail(body.getApproverEmail());
        if (body.getEffectiveDate() != null)   p.setEffectiveDate(body.getEffectiveDate());
        if (body.getExpiryDate() != null)      p.setExpiryDate(body.getExpiryDate());
        if (body.getNextReviewDate() != null)  p.setNextReviewDate(body.getNextReviewDate());
        if (body.getReviewCadence() != null)   p.setReviewCadence(body.getReviewCadence());
        if (body.getRelatedFramework() != null) p.setRelatedFramework(body.getRelatedFramework());
        if (body.getRelatedControls() != null)  p.setRelatedControls(body.getRelatedControls());
        if (body.getNotes() != null)           p.setNotes(body.getNotes());
        return docs.save(p);
    }

    /** Backward-compatible entry point — no admin override, so SoD is enforced. */
    @Transactional
    public PolicyDocument transition(Long id, String to, String actor) {
        return transition(id, to, actor, false);
    }

    /**
     * Status transition with C-6 segregation-of-duty on approval.
     *
     * @param callerIsAdmin when true (compliance/platform admin) the SoD guard is
     *                      waived for proxy approval; otherwise the policy author /
     *                      owner may not approve their own policy.
     */
    @Transactional
    public PolicyDocument transition(Long id, String to, String actor, boolean callerIsAdmin) {
        PolicyDocument p = get(id);
        String from = p.getStatus();
        boolean ok = switch (from) {
            case "DRAFT"      -> "REVIEW".equals(to) || "ARCHIVED".equals(to);
            case "REVIEW"     -> "APPROVED".equals(to) || "DRAFT".equals(to);
            case "APPROVED"   -> "PUBLISHED".equals(to) || "REVIEW".equals(to);
            case "PUBLISHED"  -> "EXPIRED".equals(to) || "ARCHIVED".equals(to);
            case "EXPIRED"    -> "ARCHIVED".equals(to);
            default           -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        // C-6 segregation of duty: the author / owner cannot approve their own
        // policy. A compliance/platform admin may override (proxy approval).
        if ("APPROVED".equals(to) && !callerIsAdmin && actor != null
                && (actor.equalsIgnoreCase(p.getCreatedBy())
                    || actor.equalsIgnoreCase(p.getOwnerName())
                    || actor.equalsIgnoreCase(p.getOwnerEmail()))) {
            throw new AccessDeniedException(
                "Segregation of duty: the policy author/owner cannot approve their own policy");
        }
        if ("PUBLISHED".equals(to) && p.getEffectiveDate() == null)
            throw new IllegalArgumentException("effective_date is required before publishing");
        p.setStatus(to);
        if ("APPROVED".equals(to) && actor != null) p.setApproverName(actor);
        if ("PUBLISHED".equals(to))                 p.setPublishedAt(LocalDateTime.now());
        PolicyDocument saved = docs.save(p);
        audit.log("TRANSITION", "PolicyDocument", saved.getId(),
            "Status " + from + " → " + to
                + (actor != null ? " by " + actor : ""));
        // PUBLISHED is the anchor event — every attestation campaign and
        // compliance posture refresh keys on it, so it gets a dedicated
        // trigger separate from the generic transition stream.
        if ("PUBLISHED".equals(to)) {
            emitPolicy("POLICY_PUBLISHED", saved, from);
        } else if ("EXPIRED".equals(to)) {
            emitPolicy("POLICY_EXPIRED", saved, from);
        } else {
            emitPolicy("POLICY_TRANSITIONED", saved, from);
        }
        return saved;
    }

    /**
     * Create the next version of an existing policy — copies metadata
     * from the latest version and resets the new row to DRAFT.
     */
    @Transactional
    public PolicyDocument newVersion(String policyCode, String newVersion) {
        Long o = org();
        List<PolicyDocument> existing = docs.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(o).stream()
            .filter(p -> policyCode.equals(p.getPolicyCode())).toList();
        if (existing.isEmpty())
            throw new EntityNotFoundException("Policy code not found: " + policyCode);
        PolicyDocument latest = existing.get(0);
        if (docs.findByOrganisationIdAndPolicyCodeAndVersion(o, policyCode, newVersion).isPresent())
            throw new IllegalArgumentException("Version already exists: " + policyCode + " v" + newVersion);
        PolicyDocument next = new PolicyDocument();
        next.setOrganisationId(o);
        next.setPolicyCode(policyCode);
        next.setVersion(newVersion);
        next.setTitleEn(latest.getTitleEn());
        next.setTitleAr(latest.getTitleAr());
        next.setSummaryEn(latest.getSummaryEn());
        next.setSummaryAr(latest.getSummaryAr());
        next.setBodyUrl(latest.getBodyUrl());
        next.setCategory(latest.getCategory());
        next.setApplicabilityScope(latest.getApplicabilityScope());
        next.setOwnerName(latest.getOwnerName());
        next.setOwnerEmail(latest.getOwnerEmail());
        next.setReviewCadence(latest.getReviewCadence());
        next.setRelatedFramework(latest.getRelatedFramework());
        next.setRelatedControls(latest.getRelatedControls());
        next.setStatus("DRAFT");
        next.setNotes("Cloned from " + policyCode + " v" + latest.getVersion());
        return docs.save(next);
    }

    @Transactional
    public void delete(Long id) {
        PolicyDocument p = get(id);
        if ("PUBLISHED".equals(p.getStatus()))
            throw new IllegalStateException("Cannot delete a PUBLISHED policy — transition to ARCHIVED first");
        p.setIsDeleted(true);
        docs.save(p);
    }

    @Transactional
    public List<Long> runExpirySweep() {
        LocalDate today = LocalDate.now();
        List<PolicyDocument> hits = docs.findExpiredPublished(org(), today);
        List<Long> flipped = new ArrayList<>();
        for (PolicyDocument p : hits) {
            p.setStatus("EXPIRED");
            PolicyDocument saved = docs.save(p);
            audit.log("EXPIRY_SWEEP", "PolicyDocument", saved.getId(),
                "Policy expired by sweep: " + nullSafe(saved.getPolicyCode()) + " v" + nullSafe(saved.getVersion()));
            emitPolicy("POLICY_EXPIRED", saved, "PUBLISHED");
            flipped.add(saved.getId());
        }
        return flipped;
    }

    // ─── Exceptions ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PolicyException> listExceptions(Long policyId, String status) {
        Long o = org();
        if (policyId != null) return exceptions.findByOrganisationIdAndPolicyIdOrderByRequestedAtDesc(o, policyId);
        if (status != null)   return exceptions.findByOrganisationIdAndStatusOrderByRequestedAtDesc(o, status);
        return exceptions.findByOrganisationIdOrderByRequestedAtDesc(o);
    }

    @Transactional(readOnly = true)
    public PolicyException getException(Long id) {
        return exceptions.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Exception not found: " + id));
    }

    @Transactional
    public PolicyException createException(PolicyException body) {
        if (body.getPolicyId() == null)       throw new IllegalArgumentException("policyId is required");
        if (body.getTitleEn() == null)        throw new IllegalArgumentException("titleEn is required");
        if (body.getJustificationEn() == null) throw new IllegalArgumentException("justificationEn is required");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null)    body.setStatus("REQUESTED");
        if (body.getRiskLevel() == null) body.setRiskLevel("MEDIUM");
        if (body.getRequestedAt() == null) body.setRequestedAt(LocalDateTime.now());
        PolicyException saved = exceptions.save(body);
        audit.log("CREATE", "PolicyException", saved.getId(),
            "Policy exception requested on policy " + saved.getPolicyId() + " (risk " + nullSafe(saved.getRiskLevel()) + ")");
        emitException("POLICY_EXCEPTION_REQUESTED", saved, null);
        return saved;
    }

    @Transactional
    public PolicyException updateException(Long id, PolicyException body) {
        PolicyException e = getException(id);
        if (body.getTitleEn() != null)              e.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)              e.setTitleAr(body.getTitleAr());
        if (body.getJustificationEn() != null)      e.setJustificationEn(body.getJustificationEn());
        if (body.getJustificationAr() != null)      e.setJustificationAr(body.getJustificationAr());
        if (body.getCompensatingControlsEn() != null) e.setCompensatingControlsEn(body.getCompensatingControlsEn());
        if (body.getCompensatingControlsAr() != null) e.setCompensatingControlsAr(body.getCompensatingControlsAr());
        if (body.getRiskLevel() != null)            e.setRiskLevel(body.getRiskLevel());
        if (body.getRiskId() != null)               e.setRiskId(body.getRiskId());
        if (body.getEffectiveDate() != null)        e.setEffectiveDate(body.getEffectiveDate());
        if (body.getExpiryDate() != null)           e.setExpiryDate(body.getExpiryDate());
        if (body.getNotes() != null)                e.setNotes(body.getNotes());
        return exceptions.save(e);
    }

    @Transactional
    public PolicyException transitionException(Long id, String to, String actor, String reason) {
        PolicyException e = getException(id);
        String from = e.getStatus();
        boolean ok = switch (from) {
            case "REQUESTED"    -> "UNDER_REVIEW".equals(to) || "REJECTED".equals(to);
            case "UNDER_REVIEW" -> "APPROVED".equals(to) || "REJECTED".equals(to) || "REQUESTED".equals(to);
            case "APPROVED"     -> "EXPIRED".equals(to) || "REVOKED".equals(to);
            default             -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        if ("REJECTED".equals(to) && (reason == null || reason.isBlank()))
            throw new IllegalArgumentException("Rejection reason is required");
        e.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        if ("UNDER_REVIEW".equals(to)) { e.setReviewerName(actor); e.setReviewedAt(now); }
        if ("APPROVED".equals(to))     { e.setApproverName(actor); e.setApprovedAt(now); }
        if ("REJECTED".equals(to))     { e.setRejectedAt(now); e.setRejectionReason(reason); }
        PolicyException saved = exceptions.save(e);
        audit.log("TRANSITION", "PolicyException", saved.getId(),
            "Exception status " + from + " → " + to
                + (actor != null ? " by " + actor : "")
                + ("REJECTED".equals(to) && reason != null ? " (" + reason + ")" : ""));
        // Approved exceptions need a dedicated trigger — they're the
        // legally significant ones (residual risk accepted with
        // compensating controls + expiry).
        String trigger = "APPROVED".equals(to) ? "POLICY_EXCEPTION_APPROVED" :
                         "REJECTED".equals(to) ? "POLICY_EXCEPTION_REJECTED" :
                         "EXPIRED".equals(to)  ? "POLICY_EXCEPTION_EXPIRED"  :
                         "REVOKED".equals(to)  ? "POLICY_EXCEPTION_REVOKED"  :
                                                 "POLICY_EXCEPTION_TRANSITIONED";
        emitException(trigger, saved, from);
        return saved;
    }

    @Transactional
    public void deleteException(Long id) {
        PolicyException e = getException(id);
        e.setIsDeleted(true);
        exceptions.save(e);
    }

    @Transactional
    public List<Long> runExceptionExpirySweep() {
        LocalDate today = LocalDate.now();
        List<PolicyException> hits = exceptions.findExpiredApproved(org(), today);
        List<Long> flipped = new ArrayList<>();
        for (PolicyException e : hits) {
            e.setStatus("EXPIRED");
            PolicyException saved = exceptions.save(e);
            audit.log("EXPIRY_SWEEP", "PolicyException", saved.getId(),
                "Exception expired by sweep on policy " + saved.getPolicyId());
            emitException("POLICY_EXCEPTION_EXPIRED", saved, "APPROVED");
            flipped.add(saved.getId());
        }
        return flipped;
    }

    // ─── Attestations ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PolicyAttestation> listAttestations(Long policyId, String username, String status) {
        Long o = org();
        if (policyId != null) return attestations.findByOrganisationIdAndPolicyIdOrderByUserUsernameAsc(o, policyId);
        if (username != null) return attestations.findByOrganisationIdAndUserUsernameOrderByCreatedAtDesc(o, username);
        if (status != null)   return attestations.findByOrganisationIdAndStatusOrderByCreatedAtDesc(o, status);
        return attestations.findByOrganisationIdOrderByCreatedAtDesc(o);
    }

    @Transactional
    public PolicyAttestation createAttestation(PolicyAttestation body) {
        if (body.getPolicyId() == null)     throw new IllegalArgumentException("policyId is required");
        if (body.getUserUsername() == null) throw new IllegalArgumentException("userUsername is required");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("PENDING");
        PolicyAttestation saved = attestations.save(body);
        audit.log("CREATE", "PolicyAttestation", saved.getId(),
            "Attestation assigned to " + nullSafe(saved.getUserUsername()) + " on policy " + saved.getPolicyId());
        emitAttestation("POLICY_ATTESTATION_ASSIGNED", saved, null);
        return saved;
    }

    /**
     * Complete an attestation.
     *
     * <p>SEC-CMP-004 hardening: an attestation-of-record may only be completed by
     * the user it is assigned to ({@code userUsername}); a compliance/platform
     * admin may override (campaign cleanup / proxy attestation). The caller's
     * identity is taken from the authenticated principal — never from a body
     * field — and recorded in {@code updatedBy} for forensics so the row shows
     * both who it was assigned to and who actually acted.
     *
     * @param currentUsername the authenticated principal's username (auth.getName())
     * @param callerIsAdmin   true when the caller holds the compliance/platform admin override
     */
    @Transactional
    public PolicyAttestation attest(Long id, String evidenceRef, String currentUsername, boolean callerIsAdmin) {
        PolicyAttestation a = attestations.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Attestation not found: " + id));
        // SEC-CMP-004: object-level authorization — only the assignee (or an admin
        // override) may complete an attestation-of-record.
        boolean isAssignee = currentUsername != null && currentUsername.equalsIgnoreCase(a.getUserUsername());
        if (!isAssignee && !callerIsAdmin)
            throw new AccessDeniedException("Attestation may only be completed by its assignee");
        if (!"PENDING".equals(a.getStatus()))
            throw new IllegalStateException("Only PENDING attestations can be attested (was " + a.getStatus() + ")");
        a.setStatus("ATTESTED");
        a.setAttestedAt(LocalDateTime.now());
        if (evidenceRef != null) a.setEvidenceRef(evidenceRef);
        // Record the actual actor separately from the assignee for non-repudiation.
        if (currentUsername != null) a.setUpdatedBy(currentUsername);
        PolicyAttestation saved = attestations.save(a);
        audit.log("ATTEST", "PolicyAttestation", saved.getId(),
            "Attestation completed by " + nullSafe(currentUsername)
                + " for " + nullSafe(saved.getUserUsername())
                + (evidenceRef != null ? " (evidence " + evidenceRef + ")" : ""));
        emitAttestation("POLICY_ATTESTED", saved, "PENDING");
        return saved;
    }

    /**
     * Decline an attestation.
     *
     * <p>SEC-CMP-004 hardening: only the assignee (or an admin override) may
     * decline; the acting identity is the authenticated principal and is recorded
     * in {@code updatedBy}.
     *
     * @param currentUsername the authenticated principal's username (auth.getName())
     * @param callerIsAdmin   true when the caller holds the compliance/platform admin override
     */
    @Transactional
    public PolicyAttestation decline(Long id, String reason, String currentUsername, boolean callerIsAdmin) {
        PolicyAttestation a = attestations.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Attestation not found: " + id));
        // SEC-CMP-004: object-level authorization — only the assignee (or an admin
        // override) may decline an attestation-of-record.
        boolean isAssignee = currentUsername != null && currentUsername.equalsIgnoreCase(a.getUserUsername());
        if (!isAssignee && !callerIsAdmin)
            throw new AccessDeniedException("Attestation may only be declined by its assignee");
        if (!"PENDING".equals(a.getStatus()))
            throw new IllegalStateException("Only PENDING attestations can be declined (was " + a.getStatus() + ")");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Decline reason is required");
        a.setStatus("DECLINED");
        a.setDeclinedAt(LocalDateTime.now());
        a.setDeclinedReason(reason);
        // Record the actual actor separately from the assignee for non-repudiation.
        if (currentUsername != null) a.setUpdatedBy(currentUsername);
        PolicyAttestation saved = attestations.save(a);
        audit.log("DECLINE", "PolicyAttestation", saved.getId(),
            "Attestation declined by " + nullSafe(currentUsername)
                + " for " + nullSafe(saved.getUserUsername()) + ": " + reason);
        emitAttestation("POLICY_ATTESTATION_DECLINED", saved, "PENDING");
        return saved;
    }

    // ─── Aggregate ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PolicyAggregate aggregate() {
        Long o = org();
        List<PolicyDocument> all = docs.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(o);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(60);

        int published = 0, inReview = 0, draft = 0, expired = 0, expiringSoon = 0;
        Map<String, Long> byCat = new TreeMap<>();
        for (PolicyDocument p : all) {
            byCat.merge(p.getCategory(), 1L, Long::sum);
            switch (p.getStatus()) {
                case "PUBLISHED" -> {
                    published++;
                    if (p.getExpiryDate() != null
                        && !p.getExpiryDate().isBefore(today)
                        && p.getExpiryDate().isBefore(horizon)) expiringSoon++;
                }
                case "REVIEW"   -> inReview++;
                case "DRAFT"    -> draft++;
                case "EXPIRED"  -> expired++;
                default -> {}
            }
        }
        int openExc = exceptions.findByOrganisationIdAndStatusOrderByRequestedAtDesc(o, "REQUESTED").size()
            + exceptions.findByOrganisationIdAndStatusOrderByRequestedAtDesc(o, "UNDER_REVIEW").size();
        int approvedExc = exceptions.findByOrganisationIdAndStatusOrderByRequestedAtDesc(o, "APPROVED").size();
        int pendingAtt = attestations.findByOrganisationIdAndStatusOrderByCreatedAtDesc(o, "PENDING").size();

        return new PolicyAggregate(
            all.size(), published, inReview, draft, expired, expiringSoon,
            openExc, approvedExc, pendingAtt, byCat);
    }
}
