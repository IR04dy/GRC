package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.EthicsCaseLifecycle;
import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.entity.KycSubject;
import com.tss.grc.module.ethics.repository.CaseActionRepository;
import com.tss.grc.module.ethics.repository.CaseLinkRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import com.tss.grc.module.ethics.repository.KycSubjectRepository;
import com.tss.grc.module.ethics.repository.ScreeningHitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-291 — what a PARTIAL body does on an entity-bound {@code PUT}, and what a bare create defaults
 * to now that the entity carries no field initialisers.
 *
 * <p>These two registers bind their entity on POST and PUT, so Jackson deserialises an omitted key
 * into {@code new T()} and a field initialiser is indistinguishable from a value the caller stated.
 * Both services copy under {@code if (src.getX() != null)}, which an initialiser makes true on every
 * request — so a partial PUT was a full replace with the initialisers.
 *
 * <p>⚠ <b>The gate is structural and cannot see this.</b>
 * {@code NeverFiringNullGuardContractTest} asserts the initialiser is absent and that a create-path
 * writer exists; only a behavioural test says the UPDATE leaves the stored value alone, and only
 * these legs distinguish "the initialiser moved" from "the initialiser was deleted and nothing owns
 * the default" — which on a NOT NULL column is a 500 rather than a reset (F2-238's one real hazard).
 *
 * <p>⚠ Deliberately built with {@code new T()} and explicit setters rather than a helper that fills
 * everything: a fixture that supplies a value twice cannot tell you which half is load-bearing
 * (F2-179), and here the whole question is what happens when a field is <em>not</em> supplied.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityBoundPartialUpdateTest {

    private static final Long ORG = 1L;
    private static final Long ID = 4242L;

    @Mock private EthicsCaseRepository caseRepo;
    @Mock private CaseActionRepository actionRepo;
    @Mock private CaseLinkRepository linkRepo;
    @InjectMocks private EthicsCaseService cases;

    @Mock private KycSubjectRepository subjectRepo;
    @Mock private ScreeningHitRepository hitRepo;
    private KycService kyc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        kyc = new KycService(subjectRepo, hitRepo, new KycSubjectDirectory(subjectRepo));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(i -> i.getArgument(0));
        when(subjectRepo.save(any(KycSubject.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ── the whistleblowing register ──────────────────────────────────────────

    /** A CRITICAL, in-person, ANONYMOUS case, at the one state the refusal below did not shield. */
    private EthicsCase stored() {
        EthicsCase c = new EthicsCase();
        c.setId(ID);
        c.setOrganisationId(ORG);
        c.setCaseCode("ETH-F2-291");
        c.setTitleEn("Reported retaliation by a line manager");
        c.setCategory("RETALIATION");
        c.setStatus("INTAKE");
        c.setSeverity("CRITICAL");
        c.setIntakeChannel("IN_PERSON");
        c.setIsAnonymous(true);
        return c;
    }

    /** A body carrying only the field the caller edited — what any non-echoing client sends. */
    private static EthicsCase titleOnly() {
        EthicsCase req = new EthicsCase();
        req.setTitleEn("Reported retaliation by a line manager (corrected)");
        return req;
    }

    @Test
    void aPartialUpdateLeavesTheSeverityTheIntakeChannelAndTheAnonymityFlagAlone() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(stored()));

        EthicsCase after = cases.update(ID, titleOnly());

        assertThat(after.getTitleEn()).contains("corrected");
        assertThat(after.getSeverity())
            .as("a CRITICAL whistleblowing report must not become MEDIUM because the caller did "
                + "not mention severity")
            .isEqualTo("CRITICAL");
        assertThat(after.getIntakeChannel())
            .as("an in-person disclosure must not be rewritten as a hotline call — the channel is "
                + "the record's provenance")
            .isEqualTo("IN_PERSON");
        assertThat(after.getIsAnonymous())
            .as("⚠⚠ the sharpest one: this flag is what protects the reporter, and the reporter's "
                + "name and email columns sit beside it")
            .isTrue();
    }

    @Test
    void aPartialUpdateOfACasePastIntakeIsNoLongerRefused() {
        EthicsCase c = stored();
        c.setStatus("TRIAGE");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));

        // Before F2-291 this threw: `status` was initialised to INTAKE, so an omitted key read as a
        // request to move the case BACK, and the message named a state the caller never sent.
        EthicsCase after = cases.update(ID, titleOnly());
        assertThat(after.getStatus()).isEqualTo("TRIAGE");
    }

    @Test
    void aContradictingStatusIsStillRefusedAndAnEchoIsStillTolerated() {
        EthicsCase c = stored();
        c.setStatus("TRIAGE");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));

        EthicsCase echo = titleOnly();
        echo.setStatus("TRIAGE");
        assertThat(cases.update(ID, echo).getStatus())
            .as("the edit form round-trips the whole DTO, so an unchanged value must keep working — "
                + "that is F2-22b(v)'s own reason for comparing rather than refusing outright")
            .isEqualTo("TRIAGE");

        EthicsCase moved = titleOnly();
        moved.setStatus("CLOSED");
        assertThatThrownBy(() -> cases.update(ID, moved))
            .as("and a DIFFERENT value must still be refused, or removing the initialiser has "
                + "opened the lifecycle to the update route")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/transition");
    }

    @Test
    void aBareCreateStillLandsInTheInitialStateWithEveryNotNullColumnFilled() {
        EthicsCase req = new EthicsCase();
        req.setTitleEn("Anonymous concern raised at the town hall");
        req.setCategory("OTHER");

        EthicsCase made = cases.create(req);

        assertThat(made.getStatus()).isEqualTo(EthicsCaseLifecycle.INITIAL);
        assertThat(made.getSeverity()).isEqualTo("MEDIUM");
        assertThat(made.getIntakeChannel()).isEqualTo("HOTLINE");
        assertThat(made.getIsAnonymous())
            .as("NOT NULL, so this must be OWNED by the create path — removing the initialiser "
                + "without a writer here turns a silent reset into a 500 (F2-238)")
            .isFalse();
    }

    @Test
    void theInitialStateIsDerivedFromTheMachineRatherThanTyped() {
        assertThat(EthicsCaseLifecycle.INITIAL)
            .as("the one state that is a key and never a target")
            .isEqualTo("INTAKE");
        assertThat(EthicsCaseLifecycle.ALLOWED.values().stream()
                .noneMatch(t -> t.contains(EthicsCaseLifecycle.INITIAL)))
            .as("if some transition ever targets it, INITIAL is no longer derivable this way and "
                + "the static initialiser must fail rather than pick one")
            .isTrue();
    }

    // ── the KYC / sanctions register ─────────────────────────────────────────

    /** A subject F2-288's escalation has already flagged from a confirmed OFAC match. */
    private KycSubject flagged() {
        KycSubject s = new KycSubject();
        s.setId(ID);
        s.setOrganisationId(ORG);
        s.setSubjectType("INDIVIDUAL");
        s.setFullName("OffshoreCapital Holdings");
        s.setIsSanctioned(true);
        s.setRiskRating("PROHIBITED");
        s.setIsPep(true);
        s.setEddRequired(true);
        s.setCddLevel("ENHANCED");
        s.setStatus("UNDER_REVIEW");
        return s;
    }

    @Test
    void aPartialUpdateDoesNotClearAConfirmedSanctionsOrPepMatch() {
        when(subjectRepo.findById(ID)).thenReturn(Optional.of(flagged()));

        KycSubject req = new KycSubject();
        req.setFullName("OffshoreCapital Holdings Ltd");
        KycSubject after = kyc.update(ID, req);

        assertThat(after.getIsSanctioned())
            .as("⚠⚠ this is the input to approve()'s \"Cannot approve a sanctioned subject\" guard, "
                + "which F2-288 armed one slice earlier. Clearing it here is how a subject with a "
                + "confirmed OFAC_SDN match was approved for business")
            .isTrue();
        assertThat(after.getRiskRating()).isEqualTo("PROHIBITED");
        assertThat(after.getIsPep()).isTrue();
        assertThat(after.getEddRequired())
            .as("the flag FATF R.12 and SAMA's AML/CFT rules turn on for a PEP")
            .isTrue();
        assertThat(after.getCddLevel()).isEqualTo("ENHANCED");
        assertThat(after.getStatus())
            .as("and an APPROVED or REJECTED subject must not silently revert to PENDING")
            .isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approveStillRefusesASanctionedSubjectAfterAPartialEdit() {
        when(subjectRepo.findById(ID)).thenReturn(Optional.of(flagged()));

        KycSubject req = new KycSubject();
        req.setFullName("OffshoreCapital Holdings Ltd");
        kyc.update(ID, req);

        assertThatThrownBy(() -> kyc.approve(ID))
            .as("the control and the edit route in one test, because the defect was only visible "
                + "as the two together: each was correct on its own")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sanctioned");
    }

    @Test
    void aBareKycCreateFillsEveryNotNullFlag() {
        KycSubject req = new KycSubject();
        req.setSubjectType("ENTITY");
        req.setFullName("Newly onboarded counterparty");

        KycSubject made = kyc.create(req);

        assertThat(made.getStatus()).isEqualTo("PENDING");
        assertThat(made.getRiskRating()).isEqualTo("STANDARD");
        assertThat(made.getCddLevel()).isEqualTo("STANDARD");
        assertThat(made.getIsPep()).isFalse();
        assertThat(made.getIsSanctioned())
            .as("NOT NULL — and a subject born null-sanctioned would 500 rather than reset")
            .isFalse();
        assertThat(made.getEddRequired()).isFalse();
    }

    @Test
    void aCreateThatSTATESAFlagKeepsIt() {
        KycSubject req = new KycSubject();
        req.setSubjectType("INDIVIDUAL");
        req.setFullName("Politically exposed person");
        req.setIsPep(true);
        req.setEddRequired(true);
        req.setCddLevel("ENHANCED");

        KycSubject made = kyc.create(req);

        assertThat(made.getIsPep())
            .as("the create-path defaults must be DEFAULTS — a guard that overwrote a stated value "
                + "would be the mirror defect, and the onboarding form does let an operator flag a "
                + "PEP by hand (F2-288)")
            .isTrue();
        assertThat(made.getEddRequired()).isTrue();
        assertThat(made.getCddLevel()).isEqualTo("ENHANCED");
    }
}
