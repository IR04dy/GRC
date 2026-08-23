package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.entity.ScreeningHit;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-288 — what a CONFIRMED screening match does to its subject, on <b>both</b> write routes.
 *
 * <p>The escalation lived inline in {@code recordHit} only, so {@code disposition()} — the route a
 * reviewer uses to adjudicate a hit — wrote the decision and applied none of the consequences. The
 * legs below are paired deliberately: the {@code recordHit} half passed before this slice and the
 * {@code disposition} half did not, so a fix that merely moved the code without wiring the second
 * caller fails exactly half of them.
 *
 * <p>⚠ No mocked repository can see a CHECK constraint or a collation, so the casing leg here pins
 * the <em>service's</em> normalisation and V392 pins the column. Neither is sufficient alone: the
 * CHECK accepts {@code true_match} because the collation is case-insensitive, and the writer alone
 * leaves a direct SQL edit unconstrained (F2-183's rule that both land in one commit).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KycScreeningEscalationTest {

    private static final Long ORG = 1L;
    private static final Long SUBJECT = 77L;
    private static final Long HIT = 909L;

    @Mock KycSubjectRepository subjectRepo;
    @Mock com.tss.grc.module.ethics.repository.ScreeningHitRepository hitRepo;

    KycService service;
    KycSubject subject;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new KycService(subjectRepo, hitRepo,
                new KycSubjectDirectory(subjectRepo));
        subject = new KycSubject();
        subject.setId(SUBJECT);
        subject.setOrganisationId(ORG);
        subject.setFullName("[REVIEW-TEST] OffshoreCapital Holdings");
        subject.setRiskRating("STANDARD");
        subject.setIsPep(false);
        subject.setIsSanctioned(false);
        subject.setEddRequired(false);
        when(subjectRepo.findById(SUBJECT)).thenReturn(Optional.of(subject));
        when(subjectRepo.save(any(KycSubject.class))).thenAnswer(i -> i.getArgument(0));
        when(hitRepo.save(any(ScreeningHit.class))).thenAnswer(i -> i.getArgument(0));

        // A real principal: disposition() refuses outright without one, so a leg about the
        // escalation would otherwise fail for a reason unrelated to its claim (F2-244). Built with
        // the house fixture idiom rather than a hand-rolled one (F2-261).
        GrcUserDetails principal = new GrcUserDetails(3L, ORG, "compliance", "x", true, true,
                List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static ScreeningHit hit(String listSource, String disposition) {
        ScreeningHit h = new ScreeningHit();
        h.setListSource(listSource);
        h.setDisposition(disposition);
        h.setMatchScore(92);
        h.setMatchSummary("[REVIEW-TEST] name and DOB");
        return h;
    }

    private ScreeningHit stored(String listSource, String disposition) {
        ScreeningHit h = hit(listSource, disposition);
        h.setId(HIT);
        h.setSubjectId(SUBJECT);
        when(hitRepo.findById(HIT)).thenReturn(Optional.of(h));
        return h;
    }

    // ─── recordHit: the path that already had the escalation ────────────────

    @Test
    @DisplayName("recording a confirmed sanctions match makes the subject sanctioned")
    void recordHitConfirmedSanctionsEscalates() {
        service.recordHit(SUBJECT, hit("UN_CONSOLIDATED", "TRUE_MATCH"));

        assertThat(subject.getIsSanctioned()).isTrue();
        assertThat(subject.getRiskRating()).isEqualTo("PROHIBITED");
    }

    /**
     * 🔑 The live half of F2-179 on this register: the collation is case-INSENSITIVE, so before
     * this slice {@code true_match} was stored verbatim, {@code "TRUE_MATCH".equals(...)} was
     * false, and the register recorded a confirmed UN sanctions match against a subject it left
     * completely unflagged. Nothing anywhere reported an error.
     */
    @Test
    @DisplayName("a lower-case confirmed match is normalised and still escalates")
    void recordHitNormalisesTheCasingAndStillEscalates() {
        ScreeningHit saved = service.recordHit(SUBJECT, hit("un_consolidated", "true_match"));

        assertThat(saved.getDisposition()).isEqualTo("TRUE_MATCH");
        assertThat(saved.getListSource()).isEqualTo("UN_CONSOLIDATED");
        assertThat(subject.getIsSanctioned())
            .as("the escalation must fire on the normalised value, not on what the caller typed")
            .isTrue();
        assertThat(subject.getRiskRating()).isEqualTo("PROHIBITED");
    }

    @Test
    @DisplayName("recording a confirmed PEP match requires enhanced due diligence")
    void recordHitConfirmedPepEscalates() {
        service.recordHit(SUBJECT, hit("PEP_GLOBAL", "TRUE_MATCH"));

        assertThat(subject.getIsPep()).isTrue();
        assertThat(subject.getEddRequired())
            .as("FATF R.12 / SAMA AML-CFT: a confirmed PEP requires EDD, and this flag is what the "
              + "platform's own Complete EDD control keys on")
            .isTrue();
        assertThat(subject.getRiskRating()).isEqualTo("HIGH");
        assertThat(subject.getIsSanctioned())
            .as("a PEP is not a sanctioned party — the two branches are separate on purpose")
            .isFalse();
    }

    @Test
    @DisplayName("an unadjudicated hit escalates nothing")
    void recordHitNeedsReviewDoesNotEscalate() {
        service.recordHit(SUBJECT, hit("UN_CONSOLIDATED", null));

        assertThat(subject.getIsSanctioned()).isFalse();
        assertThat(subject.getRiskRating()).isEqualTo("STANDARD");
        verify(subjectRepo, never()).save(any(KycSubject.class));
    }

    @Test
    @DisplayName("an unrecognised list source is refused, not silently un-escalated")
    void recordHitRefusesAnUnknownListSource() {
        assertThatThrownBy(() -> service.recordHit(SUBJECT, hit("MY_OWN_LIST", "TRUE_MATCH")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("listSource");
    }

    // ─── disposition: the reviewer's path, which had NONE of it ─────────────

    @Test
    @DisplayName("confirming a sanctions match ON REVIEW makes the subject sanctioned")
    void dispositionConfirmedSanctionsEscalates() {
        stored("OFAC_SDN", "NEEDS_REVIEW");

        ScreeningHit out = service.disposition(HIT, "TRUE_MATCH", "OFAC SDN, exact DOB",
                                               null, true, "STR-2026-0001");

        assertThat(out.getDisposition()).isEqualTo("TRUE_MATCH");
        assertThat(out.getDecidedByName()).isEqualTo("compliance");
        assertThat(subject.getIsSanctioned())
            .as("THE HEADLINE: before F2-288 this route wrote the decision and left the subject "
              + "unflagged, so a reviewer could confirm an OFAC match and the platform would go "
              + "on treating the party as ordinary")
            .isTrue();
        assertThat(subject.getRiskRating()).isEqualTo("PROHIBITED");
    }

    @Test
    @DisplayName("confirming a PEP match ON REVIEW requires enhanced due diligence")
    void dispositionConfirmedPepEscalates() {
        stored("PEP_GLOBAL", "NEEDS_REVIEW");

        service.disposition(HIT, "TRUE_MATCH", null, null, null, null);

        assertThat(subject.getIsPep()).isTrue();
        assertThat(subject.getEddRequired()).isTrue();
        assertThat(subject.getRiskRating()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("clearing a match on review escalates nothing")
    void dispositionFalsePositiveEscalatesNothing() {
        stored("OFAC_SDN", "NEEDS_REVIEW");

        service.disposition(HIT, "FALSE_POSITIVE", "different national id", null, null, null);

        assertThat(subject.getIsSanctioned()).isFalse();
        assertThat(subject.getRiskRating()).isEqualTo("STANDARD");
        verify(subjectRepo, never()).save(any(KycSubject.class));
    }

    @Test
    @DisplayName("a lower-case confirmation on review is normalised and still escalates")
    void dispositionNormalisesTheCasing() {
        stored("SAMA_LOCAL", "NEEDS_REVIEW");

        ScreeningHit out = service.disposition(HIT, " true_match ", null, null, null, null);

        assertThat(out.getDisposition()).isEqualTo("TRUE_MATCH");
        assertThat(subject.getIsSanctioned()).isTrue();
    }

    @Test
    @DisplayName("an unrecognised disposition is refused and the message names the field")
    void dispositionRefusesAnUnknownToken() {
        stored("OFAC_SDN", "NEEDS_REVIEW");

        assertThatThrownBy(() -> service.disposition(HIT, "PROBABLY_FINE", null, null, null, null))
            .as("require, not coerce: an unrecognised AML disposition does not degrade gracefully, "
              + "it silently skips the sanctions escalation (F2-182)")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("disposition");
    }

    /**
     * The PEP branch raises STANDARD/LOW to HIGH and must never lower a rating: a subject already
     * PROHIBITED for a sanctions match stays PROHIBITED when a PEP hit is confirmed afterwards.
     * A control that reports LESS risk must never be a side effect of a de-duplication (F2-282).
     */
    @Test
    @DisplayName("a confirmed PEP match never lowers an already-prohibited rating")
    void pepConfirmationNeverLowersProhibited() {
        subject.setRiskRating("PROHIBITED");
        subject.setIsSanctioned(true);
        stored("PEP_GLOBAL", "NEEDS_REVIEW");

        service.disposition(HIT, "TRUE_MATCH", null, null, null, null);

        assertThat(subject.getRiskRating()).isEqualTo("PROHIBITED");
        assertThat(subject.getIsPep()).isTrue();
    }

    @Test
    @DisplayName("a confirmed match on a non-escalating list still records the decision")
    void adverseMediaConfirmationRecordsWithoutEscalating() {
        stored("ADVERSE_MEDIA", "NEEDS_REVIEW");

        ScreeningHit out = service.disposition(HIT, "TRUE_MATCH", null, null, null, null);

        assertThat(out.getDisposition()).isEqualTo("TRUE_MATCH");
        assertThat(subject.getIsSanctioned())
            .as("whether a confirmed adverse-media or Interpol match should move a customer's risk "
              + "rating is a product decision this slice does not invent (F2-183/F2-196) — it is "
              + "filed. What must NOT happen is a silent partial escalation.")
            .isFalse();
        assertThat(subject.getRiskRating()).isEqualTo("STANDARD");
    }
}
