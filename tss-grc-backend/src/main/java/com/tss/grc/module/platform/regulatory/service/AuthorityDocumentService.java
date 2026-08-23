package com.tss.grc.module.platform.regulatory.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.common.validation.ServerOwnedState;
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

import java.math.BigDecimal;
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
    /**
     * F2-220 — the states a document may be CREATED in, and the one the server picks when the
     * caller says nothing. Read by the create; the rest of {@link #STATUSES} is
     * {@link #transition}'s, which walks DRAFT → ACTIVE → DEPRECATED → SUPERSEDED and, for
     * SUPERSEDED, demands a successor document it verifies exists and is not this one.
     *
     * <p>⚠ This is the ONE register in the sweep needing a set rather than a single initial state:
     * the default is ACTIVE (a library of instruments already in force) while the machine's root is
     * DRAFT (one being prepared), and both are accepted today. See
     * {@link ServerOwnedState#requireCreatable}.
     */
    private static final Set<String> CREATABLE_STATUSES = Set.of("DRAFT", "ACTIVE");
    private static final String INITIAL_STATUS = "ACTIVE";
    /**
     * F2-239 — the translation vocabulary, one declaration per COLUMN because there are two CHECK
     * constraints and a {@link ClosedVocabulary} names the one it mirrors. That name is what makes
     * the set checkable against the schema, so {@code ClosedVocabularyPinTest} compares both against
     * the migration SQL by reflection.
     *
     * <p>⚠ This replaced a hand-rolled {@code Set<String>} that was consulted on the DOCUMENT paths
     * and on neither CITATION path — so a caller could store any string in
     * {@code authority_document_citations.translation_status} and get the 409 the CHECK raises rather
     * than a 400 naming the field. Worse, the column collates case-INSENSITIVELY (F2-179), so
     * {@code 'ar_official'} was ACCEPTED and stored verbatim, where every reader — the badge, the
     * tone map, the label lookup — compares case-sensitively. Probed live before the fix.
     *
     * <p>{@code require}, not {@code coerce}, per F2-182's test: the CHECK is already refusing
     * everything outside the vocabulary, so every value that succeeds today is a case-variant of a
     * legal token and no working request can start failing.
     */
    private static final ClosedVocabulary DOC_TRANSLATION = ClosedVocabulary.of(
        "translationStatus", "ck_authdoc_translation",
        "EN_ONLY", "AR_AUTO", "AR_HUMAN_REVIEWED", "AR_OFFICIAL");
    private static final ClosedVocabulary CITATION_TRANSLATION = ClosedVocabulary.of(
        "translationStatus", "ck_citation_translation",
        "EN_ONLY", "AR_AUTO", "AR_HUMAN_REVIEWED", "AR_OFFICIAL");
    private static final String INITIAL_TRANSLATION = "EN_ONLY";

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
        // F2-220 — `if (status == null) ACTIVE` is a DEFAULT, not a guard: it kept whatever the
        // caller sent, and the check below it only asked whether that was a declared state. So a
        // create could file a document SUPERSEDED — the one transition that demands a successor —
        // with nothing superseding it, or DEPRECATED, a claim that it was once in force.
        body.setStatus(ServerOwnedState.requireCreatable(
            body.getStatus(), CREATABLE_STATUSES, INITIAL_STATUS, "an authority document",
            "POST /api/v1/regulatory/authority-documents/{id}/transition"));
        body.setSupersededById(null);   // transition()'s, and only when it reaches SUPERSEDED
        body.setTranslationStatus(DOC_TRANSLATION.orDefault(body.getTranslationStatus(), INITIAL_TRANSLATION));
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
        if (body.getTranslationStatus() != null)
            d.setTranslationStatus(DOC_TRANSLATION.require(body.getTranslationStatus()));
        if (body.getIsPlaceholder() != null)   d.setIsPlaceholder(body.getIsPlaceholder());
        d.setUpdatedAt(LocalDateTime.now());
        return documents.save(d);
    }

    /**
     * The register's declared edges, in ONE place. F2-303 — this was an inline
     * {@code switch} of {@code Set.of(...).contains(...)}, so the legal-target list was not
     * derivable at runtime and the refusal below could only say which move was rejected, never
     * which moves would be accepted. Terminal states are present with an <b>empty</b> set rather
     * than omitted, so the terminal set is derivable from the machine instead of typed out a
     * second time ([[F2-196]]).
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "DRAFT",      Set.of("ACTIVE"),
        "ACTIVE",     Set.of("DEPRECATED", "SUPERSEDED"),
        "DEPRECATED", Set.of("SUPERSEDED"),
        "SUPERSEDED", Set.of());

    /** DRAFT → ACTIVE → DEPRECATED → SUPERSEDED; no jumping. */
    @Transactional

    public AuthorityDocument transition(Long id, String to, Long supersededById) {
        AuthorityDocument d = get(id);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = d.getStatus();
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) throw new IllegalTransitionException(from, to, allowed);
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
        // F2-239 — these are the ONLY defaults for these five columns, because the entity no longer
        // carries initialisers (an initialiser makes `updateCitation`'s null-guards unfirable). Every
        // one is NOT NULL, so dropping a line here turns a silent reset into a 500 on create;
        // NeverFiringNullGuardContractTest asserts the (entity, field, writer) triples both ways.
        if (body.getDepth() == null)          body.setDepth(0);
        if (body.getSequenceOrder() == null)  body.setSequenceOrder(0);
        if (body.getWeight() == null)         body.setWeight(BigDecimal.ONE);
        if (body.getMandatory() == null)      body.setMandatory(true);
        // Inherited from the parent document on purpose: a citation transcribed into a document that
        // is itself a placeholder is a placeholder. That reasoning predates F2-239 and is the reason
        // this create was already the right shape — it was `updateCitation` that discarded it.
        if (body.getIsPlaceholder() == null)  body.setIsPlaceholder(d.getIsPlaceholder());
        body.setTranslationStatus(
            CITATION_TRANSLATION.orDefault(body.getTranslationStatus(), INITIAL_TRANSLATION));
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
        // F2-239 — these three guards now mean what they read. Before the entity's initialisers were
        // removed each one was true on every request, so the register's own edit form (six keys, and
        // none of these three) moved the paragraph to the top of the regulation, marked a transcribed
        // requirement a placeholder, and recorded it EN_ONLY in the same request that carried its
        // Arabic text. Measured, dev = prod: 207 of 214 live citations are not placeholders, 212 of
        // 214 have a non-zero sequence_order, 207 of 214 are past EN_ONLY.
        if (body.getTranslationStatus() != null)
            c.setTranslationStatus(CITATION_TRANSLATION.require(body.getTranslationStatus()));
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
