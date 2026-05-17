package com.tss.grc.module.policy.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.policy.entity.PolicyAttestation;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.repository.PolicyAttestationRepository;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.repository.PolicyExceptionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
/** Bean qualified ({@code policyManagementService}) because {@code module.compliance} ships another {@code PolicyService}. */
@Service("policyManagementService")
@RequiredArgsConstructor
public class PolicyService {

    private static final Set<String> POLICY_STATUSES = Set.of(
        "DRAFT", "REVIEW", "APPROVED", "PUBLISHED", "EXPIRED", "ARCHIVED");
    private static final Set<String> EXCEPTION_STATUSES = Set.of(
        "REQUESTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "EXPIRED", "REVOKED");
    private static final Set<String> ATT_STATUSES = Set.of(
        "PENDING", "ATTESTED", "DECLINED", "EXPIRED");

    private final PolicyDocumentRepository docs;
    private final PolicyExceptionRepository exceptions;
    private final PolicyAttestationRepository attestations;

    private Long org() { return TenantContext.getCurrentTenant(); }

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
        return docs.save(body);
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

    @Transactional
    public PolicyDocument transition(Long id, String to, String actor) {
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
        if ("PUBLISHED".equals(to) && p.getEffectiveDate() == null)
            throw new IllegalArgumentException("effective_date is required before publishing");
        p.setStatus(to);
        if ("APPROVED".equals(to) && actor != null) p.setApproverName(actor);
        if ("PUBLISHED".equals(to))                 p.setPublishedAt(LocalDateTime.now());
        return docs.save(p);
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
            docs.save(p);
            flipped.add(p.getId());
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
        return exceptions.save(body);
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
        return exceptions.save(e);
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
            exceptions.save(e);
            flipped.add(e.getId());
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
        return attestations.save(body);
    }

    @Transactional
    public PolicyAttestation attest(Long id, String evidenceRef) {
        PolicyAttestation a = attestations.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Attestation not found: " + id));
        if (!"PENDING".equals(a.getStatus()))
            throw new IllegalStateException("Only PENDING attestations can be attested (was " + a.getStatus() + ")");
        a.setStatus("ATTESTED");
        a.setAttestedAt(LocalDateTime.now());
        if (evidenceRef != null) a.setEvidenceRef(evidenceRef);
        return attestations.save(a);
    }

    @Transactional
    public PolicyAttestation decline(Long id, String reason) {
        PolicyAttestation a = attestations.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Attestation not found: " + id));
        if (!"PENDING".equals(a.getStatus()))
            throw new IllegalStateException("Only PENDING attestations can be declined (was " + a.getStatus() + ")");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Decline reason is required");
        a.setStatus("DECLINED");
        a.setDeclinedAt(LocalDateTime.now());
        a.setDeclinedReason(reason);
        return attestations.save(a);
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
