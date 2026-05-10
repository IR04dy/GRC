package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.entity.ScreeningHit;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.module.ethics.repository.ScreeningHitRepository;
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

    @Transactional
    public ScreeningHit disposition(Long hitId, String disposition, String notes, String decidedBy, Boolean sarFiled, String sarRef) {
        ScreeningHit h = hitRepo.findById(hitId).orElseThrow(() -> new EntityNotFoundException("Hit not found: " + hitId));
        require(h.getSubjectId());
        h.setDisposition(disposition);
        if (notes      != null) h.setDispositionNotes(notes);
        if (decidedBy  != null) h.setDecidedByName(decidedBy);
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

    private KycSubject require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return subjectRepo.findById(id)
            .filter(s -> s.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("KYC subject not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
