package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.dto.CaseActionRequest;
import com.tss.grc.module.ethics.entity.CaseAction;
import com.tss.grc.module.ethics.entity.CaseLink;
import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.repository.CaseActionRepository;
import com.tss.grc.module.ethics.repository.CaseLinkRepository;
import com.tss.grc.module.ethics.repository.EthicsCaseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EthicsCaseService — Sprint 4 §4.1 test backlog.
 *
 * Locks the whistle-blower case lifecycle (Saudi-context: Nazaha + HRSD pathways):
 *
 *   INTAKE ─► TRIAGE ─► INVESTIGATION ┬─► SUBSTANTIATED ─► REMEDIATION ─► CLOSED
 *                                     ├─► UNSUBSTANTIATED ─► CLOSED
 *                                     └─► NO_VIOLATION ─► CLOSED
 *   any state ─► ESCALATED (regulator notified)
 *   any state ─► DISMISSED (terminal sink)
 *
 * Each transition has unique side-effects (timestamps, fields). The tests below
 * lock both the topology and those side-effects.
 */
@ExtendWith(MockitoExtension.class)
class EthicsCaseServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long ID        = 50L;

    @Mock private EthicsCaseRepository caseRepo;
    @Mock private CaseActionRepository actionRepo;
    @Mock private CaseLinkRepository linkRepo;
    @InjectMocks private EthicsCaseService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static EthicsCase ethicsCase(String status, Long orgId) {
        EthicsCase c = new EthicsCase();
        c.setId(ID);
        c.setOrganisationId(orgId);
        c.setCaseCode("ETH-001");
        c.setTitleEn("Reported retaliation");
        c.setStatus(status);
        c.setSeverity("HIGH");
        c.setIntakeChannel("HOTLINE");
        return c;
    }

    private static EthicsCase ethicsCase(String status) { return ethicsCase(status, ORG_ID); }

    // ─── Lifecycle: happy paths ──────────────────────────────────────

    @Test
    void transition_INTAKE_to_TRIAGE_stamps_triagedAt() {
        EthicsCase c = ethicsCase("INTAKE");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "TRIAGE", Map.of("triageDecision", "Investigate"));
        assertThat(after.getStatus()).isEqualTo("TRIAGE");
        assertThat(after.getTriagedAt()).isNotNull();
        assertThat(after.getTriageDecision()).isEqualTo("Investigate");
    }

    @Test
    void transition_TRIAGE_to_INVESTIGATION_stamps_investigationStarted() {
        EthicsCase c = ethicsCase("TRIAGE");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "INVESTIGATION",
            Map.of("investigatorName", "Dr. Khalid", "committeeName", "Ethics Committee"));
        assertThat(after.getStatus()).isEqualTo("INVESTIGATION");
        assertThat(after.getInvestigationStarted()).isNotNull();
        assertThat(after.getInvestigatorName()).isEqualTo("Dr. Khalid");
        assertThat(after.getCommitteeName()).isEqualTo("Ethics Committee");
    }

    @Test
    void transition_INVESTIGATION_to_SUBSTANTIATED_stamps_investigationEnded_and_outcomeSummary() {
        EthicsCase c = ethicsCase("INVESTIGATION");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "SUBSTANTIATED",
            Map.of("outcomeSummary", "Allegations confirmed via interviews + email evidence"));
        assertThat(after.getStatus()).isEqualTo("SUBSTANTIATED");
        assertThat(after.getInvestigationEnded()).isNotNull();
        assertThat(after.getOutcomeSummary()).contains("confirmed");
    }

    @Test
    void transition_SUBSTANTIATED_to_REMEDIATION_records_outcome() {
        EthicsCase c = ethicsCase("SUBSTANTIATED");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "REMEDIATION",
            Map.of("outcome", "TERMINATION_AND_TRAINING"));
        assertThat(after.getStatus()).isEqualTo("REMEDIATION");
        assertThat(after.getOutcome()).isEqualTo("TERMINATION_AND_TRAINING");
    }

    @Test
    void transition_REMEDIATION_to_CLOSED_stamps_closureDate() {
        EthicsCase c = ethicsCase("REMEDIATION");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "CLOSED", Map.of());
        assertThat(after.getStatus()).isEqualTo("CLOSED");
        assertThat(after.getClosureDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void transition_to_ESCALATED_marks_regulatorNotified_and_stores_reference() {
        // Critical Saudi-context: ESCALATED means Nazaha or HRSD has been pinged
        EthicsCase c = ethicsCase("INVESTIGATION");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "ESCALATED",
            Map.of("regulatorReference", "NAZAHA-2026-0042"));
        assertThat(after.getStatus()).isEqualTo("ESCALATED");
        assertThat(after.getRegulatorNotified()).isTrue();
        assertThat(after.getRegulatorReference()).isEqualTo("NAZAHA-2026-0042");
    }

    @Test
    void transition_to_DISMISSED_stamps_closureDate_and_records_reason() {
        EthicsCase c = ethicsCase("INTAKE");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase after = service.transition(ID, "DISMISSED",
            Map.of("outcomeSummary", "No reasonable grounds — vexatious complaint"));
        assertThat(after.getStatus()).isEqualTo("DISMISSED");
        assertThat(after.getClosureDate()).isEqualTo(LocalDate.now());
        assertThat(after.getOutcomeSummary()).contains("vexatious");
    }

    // ─── Lifecycle: illegal transitions ─────────────────────────────

    @Test
    void transition_CLOSED_to_anywhere_throws() {
        // CLOSED is a terminal sink — no more transitions allowed
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("CLOSED")));

        assertThatThrownBy(() -> service.transition(ID, "INVESTIGATION", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot transition from CLOSED");
    }

    @Test
    void transition_DISMISSED_to_anywhere_throws() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("DISMISSED")));

        assertThatThrownBy(() -> service.transition(ID, "TRIAGE", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transition_INTAKE_skipping_TRIAGE_to_INVESTIGATION_throws() {
        // Must go through TRIAGE first
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("INTAKE")));

        assertThatThrownBy(() -> service.transition(ID, "INVESTIGATION", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transition_NO_VIOLATION_to_REMEDIATION_throws() {
        // Clean cases don't get remediated — that's a logic error
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("NO_VIOLATION")));

        assertThatThrownBy(() -> service.transition(ID, "REMEDIATION", Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Tenant scope ────────────────────────────────────────────────

    @Test
    void get_other_tenant_throws_EntityNotFound() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("INTAKE", OTHER_ORG)));

        assertThatThrownBy(() -> service.get(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Case not found");
    }

    @Test
    void transition_other_tenant_throws_EntityNotFound() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("INTAKE", OTHER_ORG)));

        assertThatThrownBy(() -> service.transition(ID, "TRIAGE", Map.of()))
            .isInstanceOf(EntityNotFoundException.class);

        verify(caseRepo, never()).save(any());
    }

    // ─── Create defaults ─────────────────────────────────────────────

    @Test
    void create_applies_INTAKE_status_MEDIUM_severity_HOTLINE_intake_when_unspecified() {
        EthicsCase req = new EthicsCase();
        req.setTitleEn("New report");
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase saved = service.create(req);
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getStatus()).isEqualTo("INTAKE");
        assertThat(saved.getSeverity()).isEqualTo("MEDIUM");
        assertThat(saved.getIntakeChannel()).isEqualTo("HOTLINE");
    }

    /**
     * F2-22b(v) — <b>deliberate behaviour change, recorded rather than quietly dropped.</b>
     *
     * <p>This test used to be {@code create_respects_explicit_status_severity_intake} and asserted
     * that a create supplying {@code status="TRIAGE"} was honoured — a case "arriving already
     * triaged", presumably from an external hotline provider. That intent is real, but it is in
     * direct tension with lifecycle integrity, because {@code transition()} is the only thing that
     * stamps {@code triagedAt}: a pre-triaged create produced a case claiming TRIAGE with <b>no
     * triage timestamp</b>. The same tension was resolved the same way for {@code ChangeRequest} by
     * CM-C3.
     *
     * <p>The pre-triaged flow is still available and now yields <em>better</em> data: create (lands
     * INTAKE) then {@code POST /transition {"target":"TRIAGE"}}, which stamps the timestamp. Two
     * calls instead of one, and the record is complete.
     *
     * <p>{@code severity} and {@code intakeChannel} were never the problem — they are ordinary form
     * fields and are still respected, which is what the rest of this test asserts.
     */
    @Test
    void create_respects_explicit_severity_and_channel_but_not_status() {
        EthicsCase req = new EthicsCase();
        req.setTitleEn("Pre-triaged");
        req.setSeverity("CRITICAL");
        req.setIntakeChannel("EMAIL");
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase saved = service.create(req);
        assertThat(saved.getStatus()).isEqualTo("INTAKE");
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getIntakeChannel()).isEqualTo("EMAIL");

        // ...and asking for TRIAGE at create time is now refused, with the two-call route named.
        EthicsCase preTriaged = new EthicsCase();
        preTriaged.setTitleEn("Pre-triaged");
        preTriaged.setStatus("TRIAGE");
        assertThatThrownBy(() -> service.create(preTriaged))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/transition");
    }

    // ─── Soft delete ─────────────────────────────────────────────────

    @Test
    void delete_soft_deletes_via_save() {
        EthicsCase c = ethicsCase("INTAKE");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(c));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(ID);

        assertThat(c.getIsDeleted()).isTrue();
        verify(caseRepo).save(c);
        verify(caseRepo, never()).deleteById(any());
    }

    // ─── Actions / links ─────────────────────────────────────────────

    @Test
    void addAction_requires_actionType_and_title() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("TRIAGE")));

        assertThatThrownBy(() -> service.addAction(ID,
                new CaseActionRequest(null, "ok", null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actionType");

        assertThatThrownBy(() -> service.addAction(ID,
                new CaseActionRequest("INTERVIEW", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");

        verify(actionRepo, never()).save(any());
    }

    @Test
    void addAction_persists_with_caseId_when_valid() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("TRIAGE")));
        when(actionRepo.save(any(CaseAction.class))).thenAnswer(inv -> inv.getArgument(0));
        f248AuthAs("compliance", 3L);

        CaseAction saved = service.addAction(ID,
            new CaseActionRequest("INTERVIEW", "Interview reporter", null, null));

        assertThat(saved.getCaseId()).isEqualTo(ID);
    }

    // ─── F2-248: the actor is the principal, and the caller cannot name one ─────────────

    /**
     * F2-248 — both halves of the attribution come from the authenticated principal.
     *
     * <p>The old signature took the {@link CaseAction} entity and never touched
     * {@code actorName}/{@code actorId}, so both were stored exactly as they arrived. The proof that
     * the fix is structural rather than a guard is that the request record has <b>no field</b> for
     * either — a caller cannot express the forgery, so this test cannot even be written the old way.
     */
    @Test
    void addAction_takesBothHalvesOfTheAttributionFromThePrincipal() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("INVESTIGATION")));
        when(actionRepo.save(any(CaseAction.class))).thenAnswer(inv -> inv.getArgument(0));
        f248AuthAs("compliance", 3L);

        CaseAction saved = service.addAction(ID,
            new CaseActionRequest("FINDING", "Substantiated — payment irregularity", "detail", null));

        assertThat(saved.getActorName()).isEqualTo("compliance");
        assertThat(saved.getActorId()).isEqualTo(3L);
    }

    /**
     * F2-248 — refusing is louder than recording a blank (F2-236).
     *
     * <p>An action on a misconduct case whose actor is {@code ""} or {@code null} still reads as a
     * record of who did it, which is worse than one that could not be written. Measured: every
     * action added through {@code EthicsPage} stored {@code actor_name = NULL}, because the page
     * sends no actor and nothing supplied one.
     */
    @Test
    void addAction_refusesWhenThereIsNoPrincipalToRecord() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("INVESTIGATION")));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.addAction(ID,
                new CaseActionRequest("INTERVIEW", "Interview reporter", null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("a case action");

        verify(actionRepo, never()).save(any());
    }

    /** The entity's own default survives: an ethics action is confidential unless stated. */
    @Test
    void addAction_keepsTheConfidentialDefaultAndHonoursAnExplicitFalse() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("TRIAGE")));
        when(actionRepo.save(any(CaseAction.class))).thenAnswer(inv -> inv.getArgument(0));
        f248AuthAs("compliance", 3L);

        assertThat(service.addAction(ID, new CaseActionRequest("NOTE", "t", null, null))
                .getIsConfidential()).isTrue();
        assertThat(service.addAction(ID, new CaseActionRequest("NOTE", "t", null, false))
                .getIsConfidential()).isFalse();
    }

    /**
     * F2-248 — the structural closure, asserted rather than assumed.
     *
     * <p>The strongest guarantee here is a compile-time one: {@link CaseActionRequest} has no field
     * for the actor, so a caller cannot express the forgery (F2-233). No runtime assertion above
     * could notice it coming back — a caller-supplied actor that happens to equal the principal
     * looks correct — and {@code MassAssignmentRatchetTest}'s entity-bound ranking cannot see this
     * type at all, because binding a record is precisely what moved the endpoint out of that
     * population. So the invariant lives here, next to the behaviour it protects.
     */
    @Test
    void theCaseActionRequestCannotCarryAnActorAtAll() {
        assertThat(java.util.Arrays.stream(CaseActionRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList())
            .as("CaseActionRequest may declare only what a caller decides. An actor field here is "
              + "how an action on a whistleblowing case could be attributed to anybody.")
            .containsExactly("actionType", "title", "description", "isConfidential");
    }

    /**
     * The F2-223 idiom: {@code CurrentActor} resolves only a {@link com.tss.grc.security.GrcUserDetails}
     * principal, so a bare username token would leave the id null and the assertion would be about
     * nothing.
     */
    private static void f248AuthAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

    @Test
    void addLink_defaults_linkType_to_RELATES_TO() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("TRIAGE")));
        when(linkRepo.save(any(CaseLink.class))).thenAnswer(inv -> inv.getArgument(0));

        CaseLink link = new CaseLink();
        link.setEntityType("RISK");
        link.setEntityId(99L);
        CaseLink saved = service.addLink(ID, link);

        assertThat(saved.getLinkType()).isEqualTo("RELATES_TO");
        assertThat(saved.getCaseId()).isEqualTo(ID);
    }

    @Test
    void addLink_requires_entityType_and_entityId() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("TRIAGE")));

        CaseLink incomplete = new CaseLink();
        incomplete.setEntityType("RISK");  // entityId missing
        assertThatThrownBy(() -> service.addLink(ID, incomplete))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("entityType and entityId");

        verify(linkRepo, never()).save(any());
    }

    // ─── F2-22b(v): the lifecycle belongs to transition(), not the body ──────
    //
    // The topology tests above prove transition() is correct. These prove it is the ONLY
    // way in. `apply` used to copy `status` and `outcome` straight from the request body on
    // both create and update, so the whole chain — and every timestamp only transition()
    // stamps — could be skipped in one call.

    @Test
    void create_cannot_file_a_case_that_is_already_closed() {
        EthicsCase body = new EthicsCase();
        body.setTitleEn("Reported retaliation");
        body.setStatus("CLOSED");

        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("starts at INTAKE")
            .hasMessageContaining("/transition");

        verify(caseRepo, never()).save(any());
    }

    @Test
    void create_cannot_declare_an_outcome_no_investigation_produced() {
        EthicsCase body = new EthicsCase();
        body.setTitleEn("Reported retaliation");
        body.setOutcome("SUBSTANTIATED");

        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("REMEDIATION");

        verify(caseRepo, never()).save(any());
    }

    @Test
    void update_cannot_move_the_case_and_says_what_would_be_legal() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("INTAKE")));

        EthicsCase body = new EthicsCase();
        body.setStatus("CLOSED");

        assertThatThrownBy(() -> service.update(ID, body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is at INTAKE")
            // the message must be actionable: name the endpoint AND the legal targets
            .hasMessageContaining("/transition")
            .hasMessageContaining("TRIAGE");

        verify(caseRepo, never()).save(any());
    }

    /**
     * The leg that a careless fix breaks. {@code EthicsPage}'s edit modal seeds itself from
     * {@code reqFromDto(c) = { ...c }} — the whole DTO — so an ordinary metadata edit PUTs the
     * case's <em>current</em> status and outcome straight back. Rejecting any present value
     * would 400 every edit on every case past INTAKE. An unchanged value is ignored.
     */
    @Test
    void update_still_accepts_the_edit_forms_unchanged_status_echo() {
        EthicsCase existing = ethicsCase("INVESTIGATION");
        existing.setOutcome("PENDING");
        when(caseRepo.findById(ID)).thenReturn(Optional.of(existing));
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase body = new EthicsCase();
        body.setStatus("INVESTIGATION");   // echoed, not changed
        body.setOutcome("PENDING");        // echoed, not changed
        body.setLocation("Riyadh");        // the actual edit

        EthicsCase saved = service.update(ID, body);

        assertThat(saved.getLocation()).isEqualTo("Riyadh");
        assertThat(saved.getStatus()).isEqualTo("INVESTIGATION");
        assertThat(saved.getOutcome()).isEqualTo("PENDING");
    }
}
