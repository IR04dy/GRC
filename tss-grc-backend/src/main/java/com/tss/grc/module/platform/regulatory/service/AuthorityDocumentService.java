package com.tss.grc.module.platform.regulatory.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Authority Documents catalog service — Framework Intersection F1.
 *
 * <p>Single platform-wide catalog of regulations (NCA ECC, SAMA BCM,
 * PDPL, ISO 22301 …) plus their citations. No regulator gets its own
 * backend module; everything pivots off this catalog.
 *
 * <p>Lifecycle of an Authority Document:
 * DRAFT → ACTIVE → DEPRECATED → SUPERSEDED. Supersession requires
 * a target document via {@code superseded_by_id} so audit trails
 * can follow the chain.
 *
 * <p>See {@code docs/grc/framework-intersection-plan.md} §4-5.
 */
@Service
@RequiredArgsConstructor
public class AuthorityDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AuthorityDocumentService.class);

    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DEPRECATED", "SUPERSEDED");
    private static final Set<String> TRANSLATION_STATUSES = Set.of(
        "EN_ONLY", "AR_AUTO", "AR_HUMAN_REVIEWED", "AR_OFFICIAL");

    private final AuthorityDocumentRepository documents;
    private final AuthorityDocumentCitationRepository citations;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── Documents ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuthorityDocument> list(String family, String status) {
        Long o = org();
        if (family != null && !family.isBlank())
            return documents.findByOrganisationIdAndFamilyOrderByCodeAsc(o, family);
        if (status != null && !status.isBlank())
            return documents.findByOrganisationIdAndStatusOrderByCodeAsc(o, status);
        return documents.findByOrganisationIdOrderByFamilyAscCodeAsc(o);
    }

    @Transactional(readOnly = true)
    public AuthorityDocument get(Long id) {
        return documents.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Authority document not found: " + id));
    }

    @Transactional
    public AuthorityDocument create(AuthorityDocument body) {
        if (body.getCode() == null || body.getCode().isBlank())
            throw new IllegalArgumentException("code is required");
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getFamily() == null)       throw new IllegalArgumentException("family is required");
        if (body.getJurisdiction() == null) throw new IllegalArgumentException("jurisdiction is required");
        if (body.getIssuer() == null)       throw new IllegalArgumentException("issuer is required");
        if (body.getVersion() == null)      body.setVersion("1.0");
        if (body.getStatus() == null)       body.setStatus("ACTIVE");
        if (!STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        if (body.getTranslationStatus() == null) body.setTranslationStatus("EN_ONLY");
        if (!TRANSLATION_STATUSES.contains(body.getTranslationStatus()))
            throw new IllegalArgumentException("Invalid translation status: " + body.getTranslationStatus());
        if (documents.findByOrganisationIdAndCodeAndVersion(org(), body.getCode(), body.getVersion()).isPresent())
            throw new IllegalArgumentException("Authority document already exists: " + body.getCode() + " v" + body.getVersion());
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getIsPlaceholder() == null) body.setIsPlaceholder(true);
        AuthorityDocument saved = documents.save(body);
        log.info("[regulatory] created authority document {} v{} id={}",
            saved.getCode(), saved.getVersion(), saved.getId());
        return saved;
    }

    @Transactional
    public AuthorityDocument update(Long id, AuthorityDocument body) {
        AuthorityDocument d = get(id);
        if (body.getTitleEn() != null)         d.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)         d.setTitleAr(body.getTitleAr());
        if (body.getSummaryEn() != null)       d.setSummaryEn(body.getSummaryEn());
        if (body.getSummaryAr() != null)       d.setSummaryAr(body.getSummaryAr());
        if (body.getFamily() != null)          d.setFamily(body.getFamily());
        if (body.getJurisdiction() != null)    d.setJurisdiction(body.getJurisdiction());
        if (body.getIssuer() != null)          d.setIssuer(body.getIssuer());
        if (body.getApplicabilityJson() != null) d.setApplicabilityJson(body.getApplicabilityJson());
        if (body.getEffectiveDateG() != null)  d.setEffectiveDateG(body.getEffectiveDateG());
        if (body.getEffectiveDateH() != null)  d.setEffectiveDateH(body.getEffectiveDateH());
        if (body.getNotes() != null)           d.setNotes(body.getNotes());
        if (body.getTranslationStatus() != null) {
            if (!TRANSLATION_STATUSES.contains(body.getTranslationStatus()))
                throw new IllegalArgumentException("Invalid translation status: " + body.getTranslationStatus());
            d.setTranslationStatus(body.getTranslationStatus());
        }
        if (body.getIsPlaceholder() != null)   d.setIsPlaceholder(body.getIsPlaceholder());
        d.setUpdatedAt(LocalDateTime.now());
        return documents.save(d);
    }

    /** DRAFT → ACTIVE → DEPRECATED → SUPERSEDED; no jumping. */
    @Transactional
    public AuthorityDocument transition(Long id, String to, Long supersededById) {
        AuthorityDocument d = get(id);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = d.getStatus();
        boolean ok = switch (from) {
            case "DRAFT"      -> "ACTIVE".equals(to);
            case "ACTIVE"     -> "DEPRECATED".equals(to) || "SUPERSEDED".equals(to);
            case "DEPRECATED" -> "SUPERSEDED".equals(to);
            default           -> false;
        };
        if (!ok) throw new IllegalArgumentException("Illegal transition " + from + " → " + to);
        if ("SUPERSEDED".equals(to)) {
            if (supersededById == null)
                throw new IllegalArgumentException("supersededById is required when transitioning to SUPERSEDED");
            AuthorityDocument target = documents.findByIdAndOrganisationId(supersededById, org())
                .orElseThrow(() -> new EntityNotFoundException("Successor document not found: " + supersededById));
            if (Objects.equals(target.getId(), d.getId()))
                throw new IllegalArgumentException("A document cannot supersede itself");
            d.setSupersededById(target.getId());
        }
        d.setStatus(to);
        d.setUpdatedAt(LocalDateTime.now());
        return documents.save(d);
    }

    @Transactional
    public void delete(Long id) {
        AuthorityDocument d = get(id);
        if ("ACTIVE".equals(d.getStatus()))
            throw new IllegalStateException("Cannot delete an ACTIVE authority document — transition to DEPRECATED first");
        d.setIsDeleted(true);
        documents.save(d);
    }

    /** Per-family rollup for the regulatory library landing page. */
    @Transactional(readOnly = true)
    public Map<String, Long> rollupByFamily() {
        Long o = org();
        Map<String, Long> out = new TreeMap<>();
        for (AuthorityDocument d : documents.findByOrganisationIdOrderByFamilyAscCodeAsc(o)) {
            out.merge(d.getFamily(), 1L, Long::sum);
        }
        return out;
    }

    // ─── Citations ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AuthorityDocumentCitation> listCitations(Long authorityDocumentId) {
        get(authorityDocumentId);   // tenant guard
        return citations.findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(
            org(), authorityDocumentId);
    }

    @Transactional(readOnly = true)
    public long citationCount(Long authorityDocumentId) {
        return citations.countByOrganisationIdAndAuthorityDocumentId(org(), authorityDocumentId);
    }

    @Transactional
    public AuthorityDocumentCitation createCitation(Long authorityDocumentId, AuthorityDocumentCitation body) {
        AuthorityDocument d = get(authorityDocumentId);
        if (!"DRAFT".equals(d.getStatus()) && !"ACTIVE".equals(d.getStatus()))
            throw new IllegalStateException("Cannot add citations to a " + d.getStatus() + " document");
        if (body.getCitationCode() == null || body.getCitationCode().isBlank())
            throw new IllegalArgumentException("citationCode is required");
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (citations.findByAuthorityDocumentIdAndCitationCode(authorityDocumentId, body.getCitationCode()).isPresent())
            throw new IllegalArgumentException("Citation already exists: " + body.getCitationCode());
        body.setId(null);
        body.setOrganisationId(org());
        body.setAuthorityDocumentId(authorityDocumentId);
        if (body.getDepth() == null)          body.setDepth(0);
        if (body.getSequenceOrder() == null)  body.setSequenceOrder(0);
        if (body.getMandatory() == null)      body.setMandatory(true);
        if (body.getIsPlaceholder() == null)  body.setIsPlaceholder(d.getIsPlaceholder());
        if (body.getTranslationStatus() == null) body.setTranslationStatus("EN_ONLY");
        return citations.save(body);
    }

    @Transactional
    public AuthorityDocumentCitation updateCitation(Long id, AuthorityDocumentCitation body) {
        AuthorityDocumentCitation c = citations.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Citation not found: " + id));
        if (body.getTitleEn() != null)            c.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)            c.setTitleAr(body.getTitleAr());
        if (body.getRequirementTextEn() != null)  c.setRequirementTextEn(body.getRequirementTextEn());
        if (body.getRequirementTextAr() != null)  c.setRequirementTextAr(body.getRequirementTextAr());
        if (body.getIntentEn() != null)           c.setIntentEn(body.getIntentEn());
        if (body.getIntentAr() != null)           c.setIntentAr(body.getIntentAr());
        if (body.getTestingGuidanceEn() != null)  c.setTestingGuidanceEn(body.getTestingGuidanceEn());
        if (body.getTestingGuidanceAr() != null)  c.setTestingGuidanceAr(body.getTestingGuidanceAr());
        if (body.getWeight() != null)             c.setWeight(body.getWeight());
        if (body.getMandatory() != null)          c.setMandatory(body.getMandatory());
        if (body.getSequenceOrder() != null)      c.setSequenceOrder(body.getSequenceOrder());
        if (body.getIsPlaceholder() != null)      c.setIsPlaceholder(body.getIsPlaceholder());
        if (body.getTranslationStatus() != null)  c.setTranslationStatus(body.getTranslationStatus());
        c.setUpdatedAt(LocalDateTime.now());
        return citations.save(c);
    }

    @Transactional
    public void deleteCitation(Long id) {
        AuthorityDocumentCitation c = citations.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Citation not found: " + id));
        c.setIsDeleted(true);
        citations.save(c);
    }
}
