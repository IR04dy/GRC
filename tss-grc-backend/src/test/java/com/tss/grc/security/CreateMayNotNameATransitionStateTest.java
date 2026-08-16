package com.tss.grc.security;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import com.tss.grc.module.bcm.cycle.entity.BcmRisk;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.ContinuityStrategy;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmLessonLearnedRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmRiskRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpSectionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpVersionRepository;
import com.tss.grc.module.bcm.cycle.repository.ContinuityStrategyRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisDecisionRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisRegulatoryNotificationRepository;
import com.tss.grc.module.bcm.cycle.repository.CrisisSituationReportRepository;
import com.tss.grc.module.bcm.cycle.service.BcmRemediationService;
import com.tss.grc.module.bcm.cycle.service.BcmRiskService;
import com.tss.grc.module.bcm.cycle.service.BcpPlanService;
import com.tss.grc.module.bcm.cycle.service.ContinuityStrategyService;
import com.tss.grc.module.bcm.cycle.service.CrisisEventService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentRepository;
import com.tss.grc.module.platform.regulatory.service.AuthorityDocumentService;
import com.tss.grc.module.platform.repository.IssueRepository;
import com.tss.grc.module.platform.service.IssueService;
import com.tss.grc.module.tprm.entity.QuestionnaireTemplate;
import com.tss.grc.module.tprm.repository.QuestionnaireQuestionRepository;
import com.tss.grc.module.tprm.repository.QuestionnaireTemplateRepository;
import com.tss.grc.module.tprm.service.QuestionnaireService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * F2-220 — nine registers whose create could name a state their transition route adjudicates.
 *
 * <p>Every one was probed live 2026-08-16 (36/36 legs) as {@code rmanager} (BCM:WRITE, TPRM:WRITE),
 * {@code compliance} (CONTROL_LIB:WRITE, REGULATORY:WRITE) and {@code admin} for the Issue register,
 * whose {@code PLATFORM:WRITE} is held by one role with <b>zero</b> active users. In each case the
 * create stored the forbidden state and the transition route answered 400 or 409 for the identical
 * edge.
 *
 * <p>🔑 <b>The create did not merely bypass the machine, it bypassed the machine's evidence
 * requirements.</b> Each transition demands something on the way through — a successor document, a
 * linked corrective action, an RTO or an RPO, at least one question, a resolution code, a root
 * cause and a lessons-learned record — and a create landing straight in the end state supplies
 * none of it. That is what the assertions below pin, register by register, in BOTH directions: the
 * refusal, and the guarded route still doing its job. Without the second half, a slice that closed
 * the bypass by breaking the lifecycle would score identically.
 *
 * <p>The structural half lives in {@code ServerStampedFieldContractTest} — a hard zero over every
 * service in the tree, with the nine machines named so the population cannot silently empty.
 */
@ExtendWith(MockitoExtension.class)
class CreateMayNotNameATransitionStateTest {

    private static final Long ORG = 1L;

    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach void tearDown() { TenantContext.clear(); }

    // ── the regulatory library: the one register with TWO creatable states ───

    @Mock private AuthorityDocumentRepository docs;
    @Mock private AuthorityDocumentCitationRepository citations;

    private AuthorityDocument doc(String status) {
        AuthorityDocument d = new AuthorityDocument();
        d.setCode("NCA-ECC-1");
        d.setTitleEn("Essential Cybersecurity Controls");
        d.setFamily("NCA");
        d.setJurisdiction("SA");
        d.setIssuer("NCA");
        d.setStatus(status);
        return d;
    }

    @Test
    void anAuthorityDocumentMayStillBeCreatedDraftOrActiveAndNeverSuperseded() {
        AuthorityDocumentService svc = new AuthorityDocumentService(docs, citations);
        lenient().when(docs.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(docs.findByOrganisationIdAndCodeAndVersion(anyLong(), any(), any()))
            .thenReturn(Optional.empty());

        // ⚠ The reason this register uses requireCreatable and the other eight do not: DRAFT is a
        // legitimate create today and forcing one initial state would have refused it. F2-182's
        // test — a fix may not break a request that works.
        assertThat(svc.create(doc("DRAFT")).getStatus()).isEqualTo("DRAFT");
        assertThat(svc.create(doc(null)).getStatus())
            .as("the default is unchanged: a library of instruments already in force")
            .isEqualTo("ACTIVE");
        assertThat(svc.create(doc("ACTIVE")).getStatus()).isEqualTo("ACTIVE");

        assertThatThrownBy(() -> svc.create(doc("SUPERSEDED")))
            .as("transition() to SUPERSEDED resolves a successor document and refuses one that "
                + "does not exist or is the document itself. A create lands there with nothing "
                + "superseding it.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SUPERSEDED")
            .hasMessageContaining("transition");
        assertThatThrownBy(() -> svc.create(doc("DEPRECATED")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCreatedDocumentNamesNoSuccessor() {
        AuthorityDocumentService svc = new AuthorityDocumentService(docs, citations);
        when(docs.save(any())).thenAnswer(i -> i.getArgument(0));
        when(docs.findByOrganisationIdAndCodeAndVersion(anyLong(), any(), any()))
            .thenReturn(Optional.empty());

        AuthorityDocument body = doc("ACTIVE");
        body.setSupersededById(99L);
        assertThat(svc.create(body).getSupersededById())
            .as("supersededById is the one thing transition() resolves and validates")
            .isNull();
    }

    // ── BCM remediation: a critical gap closed with no corrective action ─────

    @Mock private BcmLessonLearnedRepository lessons;
    @Mock private BcmGapRepository gaps;
    @Mock private BcmCorrectiveActionRepository capas;

    @Test
    void aCriticalGapCannotBeCreatedClosed() {
        BcmRemediationService svc =
            new BcmRemediationService(lessons, gaps, capas, audit, publisher);
        lenient().when(gaps.save(any())).thenAnswer(i -> i.getArgument(0));

        BcmGap g = new BcmGap();
        g.setTitleEn("No alternate site for core banking");
        g.setSource("BIA");
        g.setSeverity("CRITICAL");
        g.setStatus("CLOSED");

        assertThatThrownBy(() -> svc.createGap(g))
            .as("transitionGap() refuses to CLOSE a HIGH or CRITICAL gap without at least one "
                + "linked corrective action — the whole point of the gap register")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");

        g.setStatus(null);
        assertThat(svc.createGap(g).getStatus()).isEqualTo("OPEN");
    }

    // ── BCM risk ─────────────────────────────────────────────────────────────

    @Mock private BcmRiskRepository risks;

    @Test
    void aBcmRiskCannotBeCreatedAcceptedOrClosed() {
        BcmRiskService svc = new BcmRiskService(risks, audit, publisher);
        lenient().when(risks.save(any())).thenAnswer(i -> i.getArgument(0));

        BcmRisk r = new BcmRisk();
        r.setTitleEn("Regional power failure");
        r.setThreatCategory("POWER_FAILURE");
        r.setLikelihood(3);
        r.setImpact(4);

        r.setStatus("ACCEPTED");
        assertThatThrownBy(() -> svc.create(r)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ACCEPTED");
        r.setStatus("CLOSED");
        assertThatThrownBy(() -> svc.create(r)).isInstanceOf(IllegalArgumentException.class);

        r.setStatus(null);
        BcmRisk saved = svc.create(r);
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getInherentRiskScore())
            .as("scoring is derived from the caller's own likelihood and impact and must survive")
            .isNotNull();
    }

    // ── the continuity plan: published without ever being reviewed ───────────

    @Mock private BcpPlanRepository plans;
    @Mock private BcpSectionRepository sections;
    @Mock private BcpVersionRepository versions;
    @Mock private BcmBiaAssessmentRepository bias;
    @Mock private BcpPlanAcknowledgementRepository acks;

    private BcpPlanService bcpService() {
        return new BcpPlanService(plans, sections, versions, bias, acks, audit, publisher);
    }

    @Test
    void aContinuityPlanCannotBeCreatedPublished() {
        BcpPlanService svc = bcpService();
        lenient().when(plans.save(any())).thenAnswer(i -> i.getArgument(0));

        BcpPlan p = new BcpPlan();
        p.setTitleEn("Core banking continuity plan");
        p.setPlanType("BCP");
        p.setStatus("PUBLISHED");

        assertThatThrownBy(() -> svc.create(p))
            .as("transition() to APPROVED snapshots every section into a BcpVersion row — the "
                + "artefact recording WHAT was approved. A plan created PUBLISHED has none, and "
                + "isLocked() then refuses to edit it.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PUBLISHED");
    }

    @Test
    void aCreatedPlanCarriesNoApprovalOrPublicationStamp() {
        BcpPlanService svc = bcpService();
        when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        when(plans.findByOrganisationIdOrderByIdDesc(anyLong(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());   // the code minter

        BcpPlan p = new BcpPlan();
        p.setTitleEn("Core banking continuity plan");
        p.setPlanType("BCP");
        p.setApprovedBy("Nobody Who Approved This");
        p.setApprovedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        p.setPublishedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        BcpPlan saved = svc.create(p);
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getApprovedBy()).isNull();
        assertThat(saved.getApprovedAt()).isNull();
        assertThat(saved.getPublishedAt())
            .as("⚠ approvedBy and approvedAt already carried @JsonProperty(READ_ONLY) and "
                + "publishedAt did not — two of three marked, the sibling forgotten (F2-215). The "
                + "scrub covers all three so the answer does not depend on which were annotated.")
            .isNull();
    }

    // ── the continuity strategy: approved with no recovery objective ─────────

    @Mock private ContinuityStrategyRepository strategies;

    @Test
    void aContinuityStrategyCannotBeCreatedApproved() {
        ContinuityStrategyService svc = new ContinuityStrategyService(strategies, audit);
        lenient().when(strategies.save(any())).thenAnswer(i -> i.getArgument(0));

        ContinuityStrategy s = new ContinuityStrategy();
        s.setSelectedStrategy("BACKUP_SITE");
        s.setApprovalStatus("APPROVED");

        assertThatThrownBy(() -> svc.create(s))
            .as("transitionApproval() refuses APPROVED for a strategy with neither an RTO nor an "
                + "RPO — its own comment says a strategy without a recovery target should not be "
                + "approved. This create supplied neither and reached APPROVED anyway.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVED");

        s.setApprovalStatus(null);
        assertThat(svc.create(s).getApprovalStatus()).isEqualTo("DRAFT");
    }

    @Test
    void theStrategyApprovalGateStillRefusesAStrategyWithNoRecoveryObjective() {
        ContinuityStrategyService svc = new ContinuityStrategyService(strategies, audit);
        ContinuityStrategy draft = new ContinuityStrategy();
        draft.setId(4L);
        draft.setSelectedStrategy("BACKUP_SITE");
        draft.setApprovalStatus("UNDER_REVIEW");
        when(strategies.findByIdAndOrganisationId(4L, ORG)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> svc.transitionApproval(4L, "APPROVED", "compliance"))
            .as("the control the create was reaching around must still be a control")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RTO");
    }

    // ── the crisis event: closed with no root cause and no lessons learned ───

    @Mock private CrisisEventRepository crises;
    @Mock private CrisisSituationReportRepository sitreps;
    @Mock private CrisisDecisionRepository decisions;
    @Mock private CrisisRegulatoryNotificationRepository notifications;

    private CrisisEventService crisisService() {
        return new CrisisEventService(crises, sitreps, decisions, notifications, audit, publisher);
    }

    @Test
    void aCrisisEventCannotBeCreatedClosedAndCarriesNoLifecycleTimestamps() {
        CrisisEventService svc = crisisService();
        lenient().when(crises.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(crises.findByOrganisationIdOrderByIdDesc(anyLong(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());   // the code minter

        CrisisEvent e = new CrisisEvent();
        e.setTitleEn("Data centre power loss");
        e.setSeverity(4);
        e.setStatus("CLOSED");

        assertThatThrownBy(() -> svc.create(e))
            .as("spec §J.13 — transition() refuses CLOSED without a root cause AND a "
                + "lessons-learned record. bcm.crisis_events has no CHECK on status either, so "
                + "this create had nothing at all standing between it and any string.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");

        e.setStatus(null);
        e.setActivationTime(LocalDateTime.of(2001, 1, 1, 0, 0));
        e.setClosureTime(LocalDateTime.of(2001, 1, 1, 0, 0));
        CrisisEvent saved = svc.create(e);
        assertThat(saved.getStatus()).isEqualTo("DETECTED");
        assertThat(saved.getActivationTime()).isNull();
        assertThat(saved.getClosureTime()).isNull();
        assertThat(saved.getDetectionTime())
            .as("detectionTime IS the caller's — when the event was noticed — and the create "
                + "defaults it. Server-derived state must survive the fix (F2-218).")
            .isNotNull();
    }

    // ── the issue tracker: closed with no resolution code ────────────────────

    @Mock private IssueRepository issues;

    private Issue issue(String status) {
        Issue i = new Issue();
        i.setTitleEn("Evidence missing for control AC-2");
        i.setSourceModule("COMPLIANCE");
        i.setStatus(status);
        return i;
    }

    @Test
    void anIssueCannotBeCreatedClosedOrIntoAStateTheMachineDoesNotKnow() {
        IssueService svc = new IssueService(issues, audit, publisher);
        lenient().when(issues.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(issues.findByOrganisationIdOrderByIdDesc(anyLong(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());

        assertThatThrownBy(() -> svc.create(issue("CLOSED")))
            .as("update() has refused to change status since this class was written, and its "
                + "javadoc says that is to keep the state machine inspectable. The create did not.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");

        assertThatThrownBy(() -> svc.create(issue("BANANA")))
            .as("platform.issues has NO check constraints, so an unrecognised status was storable "
                + "— and transition()'s `default -> false` then made the row permanently "
                + "untransitionable by any route (F2-203's audit_lc shape, on demand)")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCreatedIssueCarriesNoResolution() {
        IssueService svc = new IssueService(issues, audit, publisher);
        when(issues.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(issues.findByOrganisationIdOrderByIdDesc(anyLong(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());

        Issue i = issue(null);
        i.setResolution("FIXED");
        i.setResolvedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        i.setClosedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        Issue saved = svc.create(i);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(saved.getResolution())
            .as("transition() demands a resolution code when resolving or closing, and validates "
                + "it against RESOLUTIONS. An issue that was never worked on has none.")
            .isNull();
        assertThat(saved.getResolvedAt()).isNull();
        assertThat(saved.getClosedAt()).isNull();
        assertThat(saved.getIssueCode())
            .as("the server still mints the reference (F2-172(d))")
            .isNotBlank();
    }

    @Test
    void theIssueMachineStillDemandsAResolutionOnTheWayToClosed() {
        IssueService svc = new IssueService(issues, audit, publisher);
        Issue open = issue("OPEN");
        open.setId(9L);
        when(issues.findByIdAndOrganisationId(9L, ORG)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> svc.transition(9L, "RESOLVED", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resolution");

        when(issues.save(any())).thenAnswer(i -> i.getArgument(0));
        Issue resolved = svc.transition(9L, "RESOLVED", "FIXED");
        assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getResolvedAt())
            .as("and the route allowed to resolve must still stamp when")
            .isNotNull();
    }

    // ── the TPRM questionnaire: active with no questions ─────────────────────

    @Mock private QuestionnaireTemplateRepository templates;
    @Mock private QuestionnaireQuestionRepository questions;

    private QuestionnaireService questionnaireService() {
        return new QuestionnaireService(templates, questions, null, null, null, null);
    }

    @Test
    void aQuestionnaireTemplateCannotBeCreatedActive() {
        QuestionnaireService svc = questionnaireService();
        lenient().when(templates.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(templates.findByOrganisationIdAndTemplateCodeAndVersion(anyLong(), any(), any()))
            .thenReturn(Optional.empty());

        QuestionnaireTemplate t = new QuestionnaireTemplate();
        t.setTemplateCode("QT-CYBER");
        t.setNameEn("Vendor cyber questionnaire");
        t.setStatus("ACTIVE");

        assertThatThrownBy(() -> svc.createTemplate(t))
            .as("transitionTemplate() refuses to activate a template with no questions. A "
                + "template created ACTIVE with none is then sent to vendors as an assessment.")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ACTIVE");

        t.setStatus(null);
        t.setActivatedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        QuestionnaireTemplate saved = svc.createTemplate(t);
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getActivatedAt()).isNull();
    }

    @Test
    void theTemplateActivationGateStillRefusesATemplateWithNoQuestions() {
        QuestionnaireService svc = questionnaireService();
        QuestionnaireTemplate draft = new QuestionnaireTemplate();
        draft.setId(6L);
        draft.setTemplateCode("QT-CYBER");
        draft.setNameEn("Vendor cyber questionnaire");
        draft.setStatus("DRAFT");
        when(templates.findByIdAndOrganisationId(6L, ORG)).thenReturn(Optional.of(draft));
        when(questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(ORG, 6L))
            .thenReturn(List.of());

        assertThatThrownBy(() -> svc.transitionTemplate(6L, "ACTIVE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no questions");
    }
}
