package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.Policy;
import com.tss.grc.module.compliance.repository.PolicyRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Implements the buyer-assessment §5.3 critical recommendation #2:
 * policy lifecycle workflow (Draft → Legal Review → Exec Approval → Published
 * → Review Due → Retired).
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository repo;

    @Autowired(required = false)
    private BroadcastHelper bcast;
    private void emit(Policy p) {
        if (bcast != null) bcast.entity(p.getOrganisationId(), "policy", p.getId(), p);
    }

    @Transactional(readOnly = true)
    public Page<Policy> list(Pageable pageable) {
        return repo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Policy get(Long id) {
        return repo.findById(id)
            .filter(p -> p.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Policy not found: " + id));
    }

    @Transactional
    public Policy create(Policy req) {
        // Mass-assignment defense — controller accepts the entity via
        // @RequestBody. Null id forces save() to INSERT instead of
        // merge() into a potentially cross-tenant row.
        req.setId(null);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        Policy saved = repo.save(req);
        emit(saved);
        return saved;
    }

    @Transactional
    public Policy update(Long id, Policy req) {
        Policy p = get(id);
        p.setCode(req.getCode());
        p.setTitleEn(req.getTitleEn());
        p.setTitleAr(req.getTitleAr());
        p.setVersion(req.getVersion());
        p.setCategory(req.getCategory());
        p.setContentEn(req.getContentEn());
        p.setContentAr(req.getContentAr());
        p.setEffectiveFrom(req.getEffectiveFrom());
        p.setNextReviewDate(req.getNextReviewDate());
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Policy p = get(id);
        p.softDelete(null);
        repo.save(p);
        emit(p);
    }

    // ── Lifecycle workflow actions ────────────────────────────────────────────

    @Transactional
    public Policy submitForLegalReview(Long id) {
        Policy p = get(id);
        require(p, "DRAFT", "REJECTED");
        p.setStatus("LEGAL_REVIEW");
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    @Transactional
    public Policy legalApprove(Long id) {
        Policy p = get(id);
        require(p, "LEGAL_REVIEW");
        p.setStatus("EXEC_APPROVAL");
        p.setLegalReviewedAt(LocalDateTime.now());
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    /** EXEC_APPROVAL → PUBLISHED, sets publishedAt = now and effectiveFrom = today if blank, and a default 12-month review date. */
    @Transactional
    public Policy executiveApprove(Long id) {
        Policy p = get(id);
        require(p, "EXEC_APPROVAL");
        p.setStatus("PUBLISHED");
        p.setApprovedAt(LocalDateTime.now());
        p.setPublishedAt(LocalDateTime.now());
        if (p.getEffectiveFrom() == null) p.setEffectiveFrom(LocalDate.now());
        if (p.getNextReviewDate() == null) p.setNextReviewDate(LocalDate.now().plusYears(1));
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    /** Operator can manually mark a published policy review-due (typically a scheduler does this when nextReviewDate passes). */
    @Transactional
    public Policy markReviewDue(Long id) {
        Policy p = get(id);
        require(p, "PUBLISHED");
        p.setStatus("REVIEW_DUE");
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    @Transactional
    public Policy retire(Long id) {
        Policy p = get(id);
        require(p, "PUBLISHED", "REVIEW_DUE");
        p.setStatus("RETIRED");
        p.setRetiredAt(LocalDateTime.now());
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    @Transactional
    public Policy reject(Long id, Map<String, Object> body) {
        Policy p = get(id);
        require(p, "LEGAL_REVIEW", "EXEC_APPROVAL");
        p.setStatus("REJECTED");
        Object reason = body.get("reason");
        if (reason instanceof String s) p.setRejectionReason(s);
        Policy saved = repo.save(p);
        emit(saved);
        return saved;
    }

    private void require(Policy p, String... allowed) {
        for (String s : allowed) if (s.equals(p.getStatus())) return;
        throw new IllegalArgumentException(
            "Policy must be in " + java.util.Arrays.toString(allowed) + " (was " + p.getStatus() + ")");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        return Map.of(
            "draftCount",        repo.countByOrganisationIdAndStatus(org, "DRAFT"),
            "legalReviewCount",  repo.countByOrganisationIdAndStatus(org, "LEGAL_REVIEW"),
            "execApprovalCount", repo.countByOrganisationIdAndStatus(org, "EXEC_APPROVAL"),
            "publishedCount",    repo.countByOrganisationIdAndStatus(org, "PUBLISHED"),
            "reviewDueCount",    repo.countByOrganisationIdAndStatus(org, "REVIEW_DUE"),
            "retiredCount",      repo.countByOrganisationIdAndStatus(org, "RETIRED"),
            "rejectedCount",     repo.countByOrganisationIdAndStatus(org, "REJECTED")
        );
    }
}
