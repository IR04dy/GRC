package com.tss.grc.module.tprm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.repository.VendorAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TprmService {

    private final VendorRepository vendorRepo;
    private final VendorAssessmentRepository assessmentRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── Vendors ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Vendor> listVendors(Pageable pageable) {
        return vendorRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Vendor getVendor(Long id) {
        return vendorRepo.findById(id)
            .filter(v -> v.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + id));
    }

    @Transactional
    public Vendor createVendor(Vendor req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        Vendor saved = vendorRepo.save(req);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vendor", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public Vendor updateVendor(Long id, Vendor req) {
        Vendor existing = getVendor(id);
        existing.setVendorName(req.getVendorName());
        existing.setVendorNameAr(req.getVendorNameAr());
        existing.setVendorType(req.getVendorType());
        existing.setCountry(req.getCountry());
        // F2-213 — contactName, contactEmail and contractStartDate are absent from
        // `TprmVendorRequest` and from the vendor form, so they arrived null on every edit
        // and were written null: changing a vendor's risk rating silently deleted its
        // contact and the start of its contract. Populated on 8 of 28 live rows in both
        // databases. Preserving them takes no capability away — the form never offered
        // them, so today the only thing an operator can do to these columns is destroy
        // them. Making them editable means adding the inputs; filed, not invented here.
        existing.setRiskRating(req.getRiskRating());
        existing.setStatus(req.getStatus());
        existing.setContractEndDate(req.getContractEndDate());
        existing.setDescription(req.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        Vendor saved = vendorRepo.save(existing);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vendor", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deleteVendor(Long id) {
        Vendor v = getVendor(id);
        v.setIsDeleted(true);
        vendorRepo.save(v);
        if (bcast != null) bcast.entity(v.getOrganisationId(), "vendor", v.getId(), java.util.Map.of("id", v.getId(), "deleted", true));
    }

    // ── Assessments ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<VendorAssessment> listAssessments(Long vendorId, Pageable pageable) {
        if (vendorId != null) {
            getVendor(vendorId); // verify org ownership
            return assessmentRepo.findByVendorId(vendorId, pageable);
        }
        // PERF-PG-002: paged, org-scoped finder — no longer scans every vendor with
        // Pageable.unpaged() and builds an unbounded findByVendorIdIn(...) IN-list.
        return assessmentRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional
    public VendorAssessment createAssessment(VendorAssessment req) {
        Vendor v = getVendor(req.getVendorId()); // verify org ownership
        req.setOrganisationId(v.getOrganisationId()); // PERF-PG-002: denormalised tenant anchor
        // F2-22b(iv): this saved the request body wholesale. The five questionnaire-engine
        // fields are now READ_ONLY on the entity so they cannot arrive at all; belt-and-braces,
        // force them to their initial values here too, because a future DTO or an internal
        // caller passing an entity would not go through Jackson. A brand-new assessment has
        // answered nothing.
        req.setId(null);
        req.setTemplateId(null);
        req.setWorkflowStatus("NOT_STARTED");
        req.setAnsweredCount(0);
        req.setTotalQuestions(0);
        req.setComputedScorePct(null);
        return assessmentRepo.save(req);
    }

    /**
     * F2-22b(iv) — refuse a manual score/rating on an assessment the questionnaire owns.
     *
     * <p>{@code templateId != null} is the ownership marker: {@code assign-template} sets it and
     * {@code recompute()} then derives {@code computedScorePct}, mirrors it to the legacy
     * {@code riskScore} column and bands {@code riskRating} from the template thresholds. A
     * manual value here is not a smaller edit — it is a claim that contradicts the responses on
     * file, which is precisely the evidence a SAMA outsourcing review asks for.
     *
     * <p>Deliberately narrow: it fires only when the caller supplies a value that <em>differs</em>
     * from what is stored, so an ordinary round-trip PUT (read, change the findings text, send it
     * all back) still works.
     */
    private void requireScoreIsNotQuestionnaireOwned(VendorAssessment existing, VendorAssessment req) {
        if (existing.getTemplateId() == null) return; // manual assessment — free-form by design
        boolean scoreDiffers  = req.getRiskScore() != null
            && (existing.getRiskScore() == null
                || existing.getRiskScore().compareTo(req.getRiskScore()) != 0);
        boolean ratingDiffers = req.getRiskRating() != null
            && !req.getRiskRating().equals(existing.getRiskRating());
        if (scoreDiffers || ratingDiffers) {
            throw new IllegalStateException(
                "Assessment " + existing.getId() + " is scored by questionnaire template "
                + existing.getTemplateId() + ", so riskScore/riskRating are derived from the "
                + "responses (computedScorePct=" + existing.getComputedScorePct() + ", rating="
                + existing.getRiskRating() + "). Change an answer via POST "
                + "/api/tprm/assessments/" + existing.getId() + "/responses, or complete the "
                + "assessment, to move the score. Received riskScore=" + req.getRiskScore()
                + ", riskRating=" + req.getRiskRating() + ".");
        }
    }

    @Transactional
    public VendorAssessment updateAssessment(Long id, VendorAssessment req) {
        VendorAssessment existing = assessmentRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        // Tenant guard (SEC-TPRM-001): VendorAssessment has no organisation_id, so
        // ownership is enforced via the parent vendor — getVendor throws if the
        // vendor is not in the caller's org, blocking cross-tenant tampering.
        getVendor(existing.getVendorId());
        // F2-22b(iv): riskScore/riskRating are the "legacy free-form" columns and stay
        // caller-supplied for a MANUAL assessment — that is a real path and refusing it
        // outright would be the F2-40 trap (mistaking a designed assertion for a bypass).
        // But once a questionnaire owns the row, QuestionnaireService.recompute() derives
        // both from the weighted responses, and letting a plain PUT overwrite them let a
        // vendor be re-banded without touching a single answer: live-proven, risk_score went
        // 17.31 → 1.00 while computed_score_pct stayed 17.31, so the register and the rubric
        // disagreed with no trace of which was which. Reject, naming the path that owns it —
        // never silently drop the value (F2-44).
        requireScoreIsNotQuestionnaireOwned(existing, req);
        existing.setAssessmentDate(req.getAssessmentDate());
        existing.setAssessor(req.getAssessor());
        if (req.getRiskScore()  != null) existing.setRiskScore(req.getRiskScore());
        if (req.getRiskRating() != null) existing.setRiskRating(req.getRiskRating());
        existing.setStatus(req.getStatus());
        existing.setFindings(req.getFindings());
        existing.setNextReviewDate(req.getNextReviewDate());
        return assessmentRepo.save(existing);
    }

    @Transactional
    public void deleteAssessment(Long id) {
        VendorAssessment a = assessmentRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        // Tenant guard (SEC-TPRM-001): block cross-tenant soft-delete via the parent vendor.
        getVendor(a.getVendorId());
        a.setIsDeleted(true);
        assessmentRepo.save(a);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        return Map.of(
            "totalVendors",    vendorRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements(),
            "highRiskVendors", vendorRepo.countByOrganisationIdAndRiskRating(orgId, "HIGH")
                             + vendorRepo.countByOrganisationIdAndRiskRating(orgId, "CRITICAL")
        );
    }
}
