package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KycService {

    /**
     * F2-288 — the disposition of a sanctions/PEP screening hit.
     *
     * <p>Before this it was declared in <b>four</b> places and enforced in none: the frontend's
     * {@code HIT_DISPOSITIONS} dropdown (4 tokens), {@code recordHit}'s case-sensitive
     * {@code "TRUE_MATCH".equals(...)}, the badge's tone map, and {@code recordHit}'s own comment,
     * which lists <em>three</em> of the four. {@code ethics.screening_hits} had <b>zero</b> CHECK
     * constraints, so the column was narrowed by nothing at all and {@link #disposition} stored
     * whatever string arrived.
     *
     * <p>🔑 {@code require} rather than {@code coerce}, and F2-182's test is what settles it: no
     * coercion exists upstream, so out-of-vocabulary values do succeed today — but every value
     * stored in either database is already a member, and an unrecognised AML disposition does not
     * degrade gracefully here, it <b>silently skips the sanctions escalation below</b>. On a
     * governance decision that is the unsafe direction; this is not the public whistleblower
     * portal, where a lost report is worse than a mis-graded one.
     */
    static final ClosedVocabulary HIT_DISPOSITION = ClosedVocabulary.of(
        "disposition", "CK_screening_hits_disposition",
        "NEEDS_REVIEW", "POTENTIAL_MATCH", "TRUE_MATCH", "FALSE_POSITIVE");

    /**
     * The list a match came from. Closed because {@link #applyConfirmedMatch} <b>dispatches</b> on
     * it: an unrecognised source is not a cosmetic problem, it is a confirmed match that escalates
     * nothing. The eight tokens are the frontend's {@code HIT_LIST_SOURCES} — the only place the
     * vocabulary was ever written down — and all three values stored in either database are members.
     */
    static final ClosedVocabulary HIT_LIST_SOURCE = ClosedVocabulary.of(
        "listSource", "CK_screening_hits_list_source",
        "OFAC_SDN", "UN_CONSOLIDATED", "EU_CONSOLIDATED", "SAMA_LOCAL",
        "PEP_GLOBAL", "INTERPOL_RED", "ADVERSE_MEDIA", "INTERNAL");

    /**
     * A subject's AML risk rating. Closed in the same commit because it is both an <b>input</b> to
     * the PEP branch of {@link #applyConfirmedMatch} (which raises STANDARD/LOW to HIGH) and its
     * <b>output</b> — so a non-canonical value there does not merely render oddly, it decides
     * whether a confirmed PEP match raises the rating at all.
     */
    static final ClosedVocabulary RISK_RATING = ClosedVocabulary.of(
        "riskRating", "CK_kyc_subjects_risk_rating",
        "LOW", "STANDARD", "HIGH", "PROHIBITED");

    /** The disposition that means "this match is real". */
    static final String CONFIRMED = "TRUE_MATCH";

    /**
     * The four lists whose confirmed match makes a subject <b>sanctioned</b>. The other four
     * {@link #HIT_LIST_SOURCE} members deliberately escalate nothing here: {@code PEP_GLOBAL} has
     * its own branch below, and whether a confirmed {@code INTERPOL_RED}, {@code ADVERSE_MEDIA} or
     * {@code INTERNAL} match should move a customer's risk rating is a product decision this slice
     * does not get to invent (F2-183/F2-196) — it is filed, not guessed.
     */
    static final Set<String> SANCTIONS_LISTS =
        Set.of("OFAC_SDN", "UN_CONSOLIDATED", "EU_CONSOLIDATED", "SAMA_LOCAL");

    /** The list whose confirmed match makes a subject a politically exposed person. */
    static final String PEP_LIST = "PEP_GLOBAL";

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
        // F2-288 -- one call where there were two literals for one fact: the default and the
        // vocabulary can no longer disagree (F2-217).
        s.setRiskRating(RISK_RATING.orDefault(s.getRiskRating(), "STANDARD"));
        // F2-291 — these four defaults live here because `KycSubject` no longer carries field
        // initialisers for them: on an entity-bound PUT an initialiser made `apply`'s null guards
        // fire on every request and reset a confirmed sanctions or PEP match. The columns are NOT
        // NULL, so a create that stops setting one turns that silent reset into a 500 —
        // `NeverFiringNullGuardContractTest#everyRemovedInitialiserHasAWriterOnTheCreatePath` pins
        // this method as the writer for all five (this and `status` above).
        if (s.getIsPep()        == null) s.setIsPep(false);
        if (s.getIsSanctioned() == null) s.setIsSanctioned(false);
        if (s.getEddRequired()  == null) s.setEddRequired(false);
        if (s.getCddLevel()     == null) s.setCddLevel("STANDARD");
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
        // F2-288 — normalise both closed columns on write, so the CHECK, the Java comparison below
        // and the frontend's label map agree. The collation is case-INSENSITIVE, so before this a
        // caller sending `true_match` had it stored verbatim and the escalation never fired: the
        // register recorded a confirmed sanctions match and the subject stayed unflagged (F2-179).
        body.setDisposition(HIT_DISPOSITION.orDefault(body.getDisposition(), "NEEDS_REVIEW"));
        body.setListSource(HIT_LIST_SOURCE.require(body.getListSource()));
        applyConfirmedMatch(s, body);
        return hitRepo.save(body);
    }

    /**
     * F2-288 — the single writer of what a <b>confirmed</b> screening match does to its subject.
     *
     * <p>This used to live inline in {@link #recordHit} only, and {@link #disposition} — the route
     * a reviewer uses to adjudicate a hit — did not have it. So a reviewer confirming a UN or OFAC
     * match set {@code disposition = TRUE_MATCH} and left {@code isSanctioned} false and the risk
     * rating untouched; confirming a PEP match left {@code isPep} false and {@code eddRequired}
     * false, which is the flag FATF R.12 and SAMA's AML/CFT rules turn on.
     *
     * <p>🔑 The comment in {@code recordHit} explaining the reaction mechanism is <b>why nobody
     * asked</b>: it documents the escalation carefully, on the one path that had it, and a reader
     * who finds a mechanism explained does not then ask which sibling route is missing it
     * (F2-215's two-of-N). That comment also lists three of the vocabulary's four tokens.
     *
     * <p>⚠ Deliberately reads the hit's <em>normalised</em> values, so it must be called after the
     * two {@code ClosedVocabulary} calls and never before — a raw {@code "true_match"} would slip
     * past {@link ClosedVocabulary#is} exactly as it slipped past the old {@code equals}.
     */
    private void applyConfirmedMatch(KycSubject s, ScreeningHit h) {
        if (!HIT_DISPOSITION.is(h.getDisposition(), CONFIRMED)) return;
        boolean escalated = false;
        if (SANCTIONS_LISTS.contains(h.getListSource())) {
            s.setIsSanctioned(true);
            s.setRiskRating("PROHIBITED");
            escalated = true;
        }
        if (PEP_LIST.equals(h.getListSource())) {
            s.setIsPep(true);
            if (RISK_RATING.is(s.getRiskRating(), "STANDARD") || RISK_RATING.is(s.getRiskRating(), "LOW")) {
                s.setRiskRating("HIGH");
            }
            s.setEddRequired(true);
            escalated = true;
        }
        if (escalated) subjectRepo.save(s);
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
        KycSubject subject = require(h.getSubjectId());
        String actor = CurrentActor.username();
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the decision-maker on screening hit "
                + hitId + ". An AML disposition with no decider is not a disposition.");
        }
        // F2-288 — this is the reviewer's route, so it is the route where the consequences of a
        // confirmed match matter most; before this it wrote the decision and applied none of them.
        h.setDisposition(HIT_DISPOSITION.require(disposition));
        if (notes      != null) h.setDispositionNotes(notes);
        h.setDecidedByName(actor);
        h.setDecidedAt(LocalDateTime.now());
        if (sarFiled != null) h.setSarFiled(sarFiled);
        if (sarRef   != null) h.setSarReference(sarRef);
        applyConfirmedMatch(subject, h);
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
        if (s.getRiskRating()             != null) t.setRiskRating(RISK_RATING.require(s.getRiskRating()));
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
