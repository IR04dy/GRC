package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
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

    @Test
    void create_respects_explicit_status_severity_intake() {
        EthicsCase req = new EthicsCase();
        req.setTitleEn("Pre-triaged");
        req.setStatus("TRIAGE");
        req.setSeverity("CRITICAL");
        req.setIntakeChannel("EMAIL");
        when(caseRepo.save(any(EthicsCase.class))).thenAnswer(inv -> inv.getArgument(0));

        EthicsCase saved = service.create(req);
        assertThat(saved.getStatus()).isEqualTo("TRIAGE");
        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getIntakeChannel()).isEqualTo("EMAIL");
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

        CaseAction missingType = new CaseAction();
        missingType.setTitle("ok");
        assertThatThrownBy(() -> service.addAction(ID, missingType))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actionType");

        CaseAction missingTitle = new CaseAction();
        missingTitle.setActionType("INTERVIEW");
        assertThatThrownBy(() -> service.addAction(ID, missingTitle))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title");

        verify(actionRepo, never()).save(any());
    }

    @Test
    void addAction_persists_with_caseId_when_valid() {
        when(caseRepo.findById(ID)).thenReturn(Optional.of(ethicsCase("TRIAGE")));
        when(actionRepo.save(any(CaseAction.class))).thenAnswer(inv -> inv.getArgument(0));

        CaseAction a = new CaseAction();
        a.setActionType("INTERVIEW");
        a.setTitle("Interview reporter");
        CaseAction saved = service.addAction(ID, a);

        assertThat(saved.getCaseId()).isEqualTo(ID);
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
}
