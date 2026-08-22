package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.entity.ScreeningHit;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.module.ethics.repository.ScreeningHitRepository;
import com.tss.grc.common.security.CurrentActor;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KycService {

    private final KycSubjectRepository subjectRepo;
    private final ScreeningHitRepository hitRepo;
    private final KycSubjectDirectory directory;

    @Transactional(readOnly = true)
    public Page<KycSubject> list(String type, String status, String risk, Pageable pageable) {
        return subjectRepo.findWithFilters(TenantContext.getCurrentTenant(),
            blank(type), blank(status), blank(risk), pageable);
    }

    @Transactional(readOnly = true)
    public KycSubject get(Long id) { return require(id); }

    @Transactional
    public KycSubject create(KycSubject req) {
        KycSubject s = new KycSubject();
        s.setOrganisationId(TenantContext.getCurrentTenant());
        apply(s, req);
        if (s.getStatus() == null) s.setStatus("PENDING");
        if (s.getRiskRating() == null) s.setRiskRating("STANDARD");
        return subjectRepo.save(s);
    }

    @Transactional
    public KycSubject update(Long id, KycSubject req) {
        KycSubject s = require(id);
        apply(s, req);
        return subjectRepo.save(s);
    }

    @Transactional
    public void delete(Long id) {
        KycSubject s = require(id);
        s.setIsDeleted(true);
        subjectRepo.save(s);
    }

    @Transactional
    public KycSubject completeCdd(Long id) {
        KycSubject s = require(id);
        s.setCddCompletedAt(LocalDateTime.now());
        if ("PENDING".equals(s.getStatus())) s.setStatus("UNDER_REVIEW");
        return subjectRepo.save(s);
    }

    @Transactional
    public KycSubject completeEdd(Long id) {
        KycSubject s = require(id);
        s.setEddCompletedAt(LocalDateTime.now());
        s.setEddRequired(false);
        return subjectRepo.save(s);
    }

    @Transactional
    public KycSubject approve(Long id) {
        KycSubject s = require(id);
        if (Boolean.TRUE.equals(s.getIsSanctioned()))
            throw new IllegalArgumentException("Cannot approve a sanctioned subject");
        s.setStatus("APPROVED");
        return subjectRepo.save(s);
    }

    @Transactional
    public KycSubject reject(Long id, String reason) {
        KycSubject s = require(id);
        s.setStatus("REJECTED");
        if (reason != null) s.setNotes((s.getNotes() == null ? "" : s.getNotes() + "\n") + "REJECTED: " + reason);
        return subjectRepo.save(s);
    }

    @Transactional
    public KycSubject runScreening(Long id) {
        KycSubject s = require(id);
        s.setLastScreenedAt(LocalDateTime.now());
        return subjectRepo.save(s);
    }

    @Transactional(readOnly = true)
    public List<ScreeningHit> hits(Long subjectId) {
        require(subjectId);
        return hitRepo.findBySubjectIdOrderByScreenedAtDesc(subjectId);
    }

    @Transactional
    public ScreeningHit recordHit(Long subjectId, ScreeningHit body) {
        KycSubject s = require(subjectId);
        body.setSubjectId(subjectId);
        body.setId(null);
        // F2-22b(vii) — a newly reported screening hit has NOT been adjudicated.
        //
        // This saved the body wholesale, so a caller could create a sanctions/PEP hit that already
        // carried a decision: `decidedAt`, `decidedByName`, `dispositionNotes` — and
        // `sarFiled`/`sarReference`, which assert that a **Suspicious Activity Report was filed
        // with the regulator**. On a SAMA/AML screening register that is the highest-value forgery
        // in the platform: the whole point of the register is to show which matches were reviewed,
        // by whom, when, and what was reported.
        //
        // `disposition` itself stays caller-set on purpose. The screening feed is what classifies a
        // match (TRUE_MATCH / NEEDS_REVIEW / FALSE_POSITIVE) and the service *reacts* to it below by
        // flagging the subject sanctioned or PEP — refusing it would break the mechanism, which is
        // the F2-40 trap. What is refused is the claim to have already *decided* about it.
        body.setDecidedAt(null);
        body.setDecidedByName(null);
        body.setDispositionNotes(null);
        body.setSarFiled(false);
        body.setSarReference(null);
        // F2-286 — `screenedAt` used to be `insertable = false`, so a caller could send it, the
        // response echoed it, and the column took `sysutcdatetime()` at insert time. Nothing
        // could ever state when a screening actually ran: measured, all 12 live rows are
        // microsecond instants in three insert batches, and two of them read as decided 370 days
        // before they were screened. When a screening ran is the screening run's fact to report
        // (F2-268's third kind), so the column is insertable now and the caller may state it --
        // and this default is mandatory rather than tidy, because the column is NOT NULL and an
        // omitted value would otherwise reach the INSERT as null.
        //
        // A screening cannot have happened in the future, and refusing that is what makes the
        // widening safe: it is the one value that could only be a forgery. Nothing sends this
        // field today, so no working request can start failing (F2-182).
        if (body.getScreenedAt() == null) {
            body.setScreenedAt(LocalDateTime.now());
        } else if (body.getScreenedAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new IllegalArgumentException(
                "screenedAt cannot be in the future — a screening that has not happened yet "
                + "cannot have produced a match. Omit it to record the screening as happening now.");
        }
        if (body.getDisposition() == null) body.setDisposition("NEEDS_REVIEW");
        if ("TRUE_MATCH".equals(body.getDisposition())
            && List.of("OFAC_SDN", "UN_CONSOLIDATED", "EU_CONSOLIDATED", "SAMA_LOCAL").contains(body.getListSource())) {
            s.setIsSanctioned(true);
            s.setRiskRating("PROHIBITED");
            subjectRepo.save(s);
        }
        if ("TRUE_MATCH".equals(body.getDisposition()) && "PEP_GLOBAL".equals(body.getListSource())) {
            s.setIsPep(true);
            if ("STANDARD".equals(s.getRiskRating()) || "LOW".equals(s.getRiskRating())) s.setRiskRating("HIGH");
            s.setEddRequired(true);
            subjectRepo.save(s);
        }
        return hitRepo.save(body);
    }

    /**
     * F2-22b(vii) — who cleared a sanctions match is recorded from the security context.
     *
     * <p>The {@code decidedBy} parameter is retained so the controller's shape does not change, but
     * it is <b>ignored</b>: it came straight from the request body, which is DF-3's defect on the one
     * register where "who decided" is the entire audit question. No UI ever sent it —
     * {@code kycApi.disposition} declares the field and no page calls it — so nothing depended on
     * naming a third party.
     */
    @Transactional
    public ScreeningHit disposition(Long hitId, String disposition, String notes, String decidedBy, Boolean sarFiled, String sarRef) {
        ScreeningHit h = hitRepo.findById(hitId).orElseThrow(() -> new EntityNotFoundException("Hit not found: " + hitId));
        require(h.getSubjectId());
        String actor = CurrentActor.username();
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the decision-maker on screening hit "
                + hitId + ". An AML disposition with no decider is not a disposition.");
        }
        h.setDisposition(disposition);
        if (notes      != null) h.setDispositionNotes(notes);
        h.setDecidedByName(actor);
        h.setDecidedAt(LocalDateTime.now());
        if (sarFiled != null) h.setSarFiled(sarFiled);
        if (sarRef   != null) h.setSarReference(sarRef);
        return hitRepo.save(h);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total",         subjectRepo.countByOrganisationId(org));
        out.put("approved",      subjectRepo.countByOrganisationIdAndStatus(org, "APPROVED"));
        out.put("rejected",      subjectRepo.countByOrganisationIdAndStatus(org, "REJECTED"));
        out.put("pending",       subjectRepo.countByOrganisationIdAndStatus(org, "PENDING"));
        out.put("underReview",   subjectRepo.countByOrganisationIdAndStatus(org, "UNDER_REVIEW"));
        out.put("highRisk",      subjectRepo.countByOrganisationIdAndRiskRating(org, "HIGH"));
        out.put("prohibited",    subjectRepo.countByOrganisationIdAndRiskRating(org, "PROHIBITED"));
        out.put("peps",          subjectRepo.countByOrganisationIdAndIsPep(org, true));
        out.put("sanctioned",    subjectRepo.countByOrganisationIdAndIsSanctioned(org, true));
        return out;
    }

    private void apply(KycSubject t, KycSubject s) {
        if (s.getSubjectCode()            != null) t.setSubjectCode(s.getSubjectCode());
        if (s.getSubjectType()            != null) t.setSubjectType(s.getSubjectType());
        if (s.getFullName()               != null) t.setFullName(s.getFullName());
        if (s.getFullNameAr()             != null) t.setFullNameAr(s.getFullNameAr());
        if (s.getNationality()            != null) t.setNationality(s.getNationality());
        if (s.getCountryOfResidence()     != null) t.setCountryOfResidence(s.getCountryOfResidence());
        if (s.getDateOfBirth()            != null) t.setDateOfBirth(s.getDateOfBirth());
        if (s.getNationalId()             != null) t.setNationalId(s.getNationalId());
        if (s.getCommercialRegistration() != null) t.setCommercialRegistration(s.getCommercialRegistration());
        if (s.getSubjectLinkType()        != null) t.setSubjectLinkType(s.getSubjectLinkType());
        if (s.getSubjectLinkId()          != null) t.setSubjectLinkId(s.getSubjectLinkId());
        if (s.getRiskRating()             != null) t.setRiskRating(s.getRiskRating());
        if (s.getIsPep()                  != null) t.setIsPep(s.getIsPep());
        if (s.getIsSanctioned()           != null) t.setIsSanctioned(s.getIsSanctioned());
        if (s.getCddLevel()               != null) t.setCddLevel(s.getCddLevel());
        if (s.getEddRequired()            != null) t.setEddRequired(s.getEddRequired());
        if (s.getNextReviewDate()         != null) t.setNextReviewDate(s.getNextReviewDate());
        if (s.getStatus()                 != null) t.setStatus(s.getStatus());
        if (s.getFrameworkId()            != null) t.setFrameworkId(s.getFrameworkId());
        if (s.getNotes()                  != null) t.setNotes(s.getNotes());
    }

    /**
     * F2-250 — the tenant-narrowed resolution is {@link KycSubjectDirectory}'s, converted rather
     * than left as a second copy (F2-190). Only the <em>refusal</em> stays local: these routes
     * address a subject by path variable, so a miss is a 404, whereas a register that names a
     * subject in its body gets the directory's 400. One predicate, two refusals.
     */
    private KycSubject require(Long id) {
        return directory.resolve(id)
            .orElseThrow(() -> new EntityNotFoundException("KYC subject not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
