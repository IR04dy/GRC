package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.evidence.entity.EvidenceConnector;
import com.tss.grc.module.evidence.repository.EvidenceConnectorRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAttestation;
import com.tss.grc.module.risk.entity.RiskControl;
import com.tss.grc.module.risk.repository.RiskAttestationRepository;
import com.tss.grc.module.risk.repository.RiskControlRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for the V115/V116 BuyerGapService.
 *
 * Focuses on the code-review fixes shipped in commit 17a2fb1:
 * <ul>
 *   <li>linkRiskControl rejects a cross-tenant riskId (was a CVE)</li>
 *   <li>dsrSlaSweep dedups — first sweep fires, second is a no-op</li>
 *   <li>dsrSlaSweep audit.log only on first detection (was: every sweep)</li>
 *   <li>dsrSlaSweep handles null sla_breach_notified correctly</li>
 *   <li>pdpl72hSweep only saves CrisisEvents it actually mutated</li>
 *   <li>attestRisk verifies risk belongs to the caller's tenant</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuyerGapServiceTest {

    private static final Long ORG_ID = 41L;
    private static final Long OTHER_ORG = 99L;

    @Mock private RiskControlRepository         riskControls;
    @Mock private RiskRepository                risks;
    @Mock private RiskAttestationRepository     riskAttestations;
    @Mock private EvidenceItemRepository        evidence;
    @Mock private EvidenceConnectorRepository   connectors;
    @Mock private ProcessingActivityRepository  ropa;
    @Mock private DsrCaseRepository             dsrCases;
    @Mock private CrisisEventRepository         crises;
    @Mock private AuditLogService               audit;
    @Mock private ApplicationEventPublisher     eventPublisher;
    @Mock private EntityManager                 em;

    @InjectMocks private BuyerGapService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(riskControls.save(any(RiskControl.class))).thenAnswer(inv -> {
            RiskControl rc = inv.getArgument(0);
            if (rc.getId() == null) rc.setId(1001L);
            return rc;
        });
        when(riskAttestations.save(any(RiskAttestation.class))).thenAnswer(inv -> {
            RiskAttestation a = inv.getArgument(0);
            if (a.getId() == null) a.setId(2001L);
            return a;
        });
        when(dsrCases.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(crises.save(any(CrisisEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ══════════════════════════════════════════════════════════════════
    // linkRiskControl — cross-tenant FK leak fix
    // ══════════════════════════════════════════════════════════════════

    @Test void linkRiskControl_rejectsRiskFromOtherTenant() {
        // Risk #500 belongs to OTHER_ORG, current caller is ORG_ID.
        Risk otherTenantRisk = new Risk();
        otherTenantRisk.setOrganisationId(OTHER_ORG);
        when(risks.findById(500L)).thenReturn(Optional.of(otherTenantRisk));

        assertThatThrownBy(() ->
            service.linkRiskControl(500L, 999L, "EFFECTIVE", BigDecimal.valueOf(40), "test"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Risk 500");

        // Verify we never reached the riskControls.save() — cross-tenant
        // FK leak is what we're guarding against.
        verify(riskControls, never()).save(any());
    }

    @Test void linkRiskControl_acceptsRiskFromCallerTenant() {
        Risk myRisk = new Risk();
        myRisk.setOrganisationId(ORG_ID);
        when(risks.findById(42L)).thenReturn(Optional.of(myRisk));
        when(riskControls.findByOrganisationIdAndRiskIdAndControlId(ORG_ID, 42L, 7L))
            .thenReturn(Optional.empty());

        RiskControl out = service.linkRiskControl(42L, 7L, "EFFECTIVE",
            BigDecimal.valueOf(25), "Primary mitigation");

        assertThat(out.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(out.getRiskId()).isEqualTo(42L);
        assertThat(out.getControlId()).isEqualTo(7L);
        assertThat(out.getEffectiveness()).isEqualTo("EFFECTIVE");
        verify(audit).log(eq("LINK"), eq("RiskControl"), anyLong(), any());
    }

    // ══════════════════════════════════════════════════════════════════
    // dsrSlaSweep — idempotency + null handling
    // ══════════════════════════════════════════════════════════════════

    @Test void dsrSlaSweep_firesOnceForNullSlaBreachNotified() {
        DsrCase nullDedupCase = newDsrCase("DSR-001", null /* slaBreachNotified */);
        when(dsrCases.findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
                eq(ORG_ID), any(), any())).thenReturn(List.of(nullDedupCase));

        BuyerGapService.DsrSlaSweepResult r1 = service.dsrSlaSweep();

        assertThat(r1.openOverdue()).isEqualTo(1);
        assertThat(r1.firedEvents()).isEqualTo(1);
        assertThat(nullDedupCase.getSlaBreachNotified()).isTrue();
        verify(eventPublisher, times(1)).publishEvent(any(DomainEvent.class));
        verify(audit, times(1)).log(eq("SLA_BREACH"), eq("DsrCase"), anyLong(), any());
    }

    @Test void dsrSlaSweep_secondCallIsNoOp_NoSpamAuditLog() {
        DsrCase alreadyNotified = newDsrCase("DSR-002", Boolean.TRUE);
        alreadyNotified.setSlaBreachedAt(LocalDateTime.now().minusDays(1));
        when(dsrCases.findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
                eq(ORG_ID), any(), any())).thenReturn(List.of(alreadyNotified));

        BuyerGapService.DsrSlaSweepResult r = service.dsrSlaSweep();

        assertThat(r.openOverdue()).isEqualTo(1);   // still counted as overdue
        assertThat(r.firedEvents()).isEqualTo(0);    // but no event fired
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
        verify(audit, never()).log(eq("SLA_BREACH"), eq("DsrCase"), anyLong(), any());
        // Should not save either — nothing changed.
        verify(dsrCases, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════
    // pdpl72hSweep — only save when changed
    // ══════════════════════════════════════════════════════════════════

    @Test void pdpl72hSweep_skipsSaveWhenNothingChanged() {
        CrisisEvent ce = new CrisisEvent();
        ce.setId(1L);
        ce.setOrganisationId(ORG_ID);
        ce.setPersonalDataAffected(true);
        ce.setDetectionTime(LocalDateTime.now().minusHours(10));
        ce.setPdpl72hDeadlineAt(LocalDateTime.now().plusHours(62));     // still in window
        ce.setPdpl72hBreached(false);
        // No notification drafted yet, but deadline hasn't elapsed → no flip

        when(crises.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(ce)));

        BuyerGapService.Pdpl72hSweepResult r = service.pdpl72hSweep();

        assertThat(r.personalDataCrises()).isEqualTo(1);
        assertThat(r.deadlinesSet()).isEqualTo(0);
        assertThat(r.newlyBreached()).isEqualTo(0);
        verify(crises, never()).save(any());
    }

    @Test void pdpl72hSweep_savesWhenDeadlineComputed() {
        CrisisEvent ce = new CrisisEvent();
        ce.setId(2L);
        ce.setOrganisationId(ORG_ID);
        ce.setPersonalDataAffected(true);
        ce.setDetectionTime(LocalDateTime.now().minusDays(1));
        ce.setPdpl72hDeadlineAt(null);  // not yet computed
        ce.setPdpl72hBreached(false);

        when(crises.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(ce)));

        BuyerGapService.Pdpl72hSweepResult r = service.pdpl72hSweep();

        assertThat(r.deadlinesSet()).isEqualTo(1);
        assertThat(ce.getPdpl72hDeadlineAt()).isNotNull();
        verify(crises, atLeastOnce()).save(ce);
    }

    @Test void pdpl72hSweep_skipsEventsWithoutPersonalData() {
        CrisisEvent noPdImpact = new CrisisEvent();
        noPdImpact.setId(3L);
        noPdImpact.setOrganisationId(ORG_ID);
        noPdImpact.setPersonalDataAffected(false);

        when(crises.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(noPdImpact)));

        BuyerGapService.Pdpl72hSweepResult r = service.pdpl72hSweep();

        assertThat(r.personalDataCrises()).isEqualTo(0);
        verify(crises, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════
    // attestRisk — tenant verification
    // ══════════════════════════════════════════════════════════════════

    @Test void attestRisk_rejectsCrossTenant() {
        Risk other = new Risk();
        other.setOrganisationId(OTHER_ORG);
        when(risks.findById(99L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.attestRisk(99L,
            new BuyerGapService.AttestBody("CONFIRMED", "ok", "موافق", "Tester")))
            .isInstanceOf(EntityNotFoundException.class);

        verify(riskAttestations, never()).save(any());
    }

    @Test void attestRisk_persistsAndAdvancesNextDueDate() {
        Risk r = new Risk();
        r.setId(1L);
        r.setOrganisationId(ORG_ID);
        r.setAttestationCadenceDays(90);
        when(risks.findById(1L)).thenReturn(Optional.of(r));
        when(risks.save(any(Risk.class))).thenReturn(r);

        ArgumentCaptor<RiskAttestation> cap = ArgumentCaptor.forClass(RiskAttestation.class);

        service.attestRisk(1L, new BuyerGapService.AttestBody("CONFIRMED",
            "Risk reviewed, controls effective", "تمت المراجعة", "Khalid Al-Mutairi"));

        verify(riskAttestations).save(cap.capture());
        RiskAttestation saved = cap.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getRiskId()).isEqualTo(1L);
        assertThat(saved.getDecision()).isEqualTo("CONFIRMED");
        assertThat(saved.getNextDueDate()).isEqualTo(LocalDate.now().plusDays(90));
        // And the risk's own next_attestation_date got advanced.
        assertThat(r.getNextAttestationDate()).isEqualTo(LocalDate.now().plusDays(90));
        assertThat(r.getLastAttestedAt()).isNotNull();
    }

    @Test void attestRisk_defaultsDecisionToCONFIRMED_whenBlank() {
        Risk r = new Risk();
        r.setId(1L); r.setOrganisationId(ORG_ID); r.setAttestationCadenceDays(90);
        when(risks.findById(1L)).thenReturn(Optional.of(r));
        when(risks.save(any(Risk.class))).thenReturn(r);

        ArgumentCaptor<RiskAttestation> cap = ArgumentCaptor.forClass(RiskAttestation.class);

        service.attestRisk(1L, new BuyerGapService.AttestBody("", null, null, null));

        verify(riskAttestations).save(cap.capture());
        assertThat(cap.getValue().getDecision()).isEqualTo("CONFIRMED");
    }

    // ══════════════════════════════════════════════════════════════════
    // updateConnectorStatus — tenant scope + audit
    // ══════════════════════════════════════════════════════════════════

    @Test void updateConnectorStatus_404OnUnknownCode() {
        when(connectors.findByOrganisationIdAndCode(ORG_ID, "GHOST"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConnectorStatus(
                "GHOST", new BuyerGapService.ConnectorPatch("ENABLED", null)))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void updateConnectorStatus_appliesStatus() {
        EvidenceConnector c = new EvidenceConnector();
        c.setId(50L); c.setOrganisationId(ORG_ID); c.setCode("AD");
        c.setStatus("AVAILABLE");
        when(connectors.findByOrganisationIdAndCode(ORG_ID, "AD")).thenReturn(Optional.of(c));
        when(connectors.save(any(EvidenceConnector.class))).thenAnswer(inv -> inv.getArgument(0));

        EvidenceConnector saved = service.updateConnectorStatus("AD",
            new BuyerGapService.ConnectorPatch("ENABLED", null));

        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        verify(audit).log(eq("UPDATE"), eq("EvidenceConnector"), anyLong(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────
    private DsrCase newDsrCase(String externalId, Boolean slaBreachNotified) {
        DsrCase c = new DsrCase();
        c.setId(System.nanoTime());
        c.setOrganisationId(ORG_ID);
        c.setExternalId(externalId);
        c.setType("ACCESS");
        c.setStatus("COLLECTING");
        c.setSlaBreachNotified(slaBreachNotified);
        c.setSlaDeadline(LocalDate.now().minusDays(5));
        return c;
    }
}
