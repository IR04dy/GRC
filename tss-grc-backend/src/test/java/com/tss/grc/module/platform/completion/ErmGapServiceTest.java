package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link ErmGapService} (§5.1 ERM slice, extracted from the
 * former BuyerGapService — QUAL-BCMP-003). Locks the code-review fixes from
 * commit 17a2fb1:
 * <ul>
 *   <li>linkRiskControl rejects a cross-tenant riskId (was a CVE)</li>
 *   <li>attestRisk verifies the risk belongs to the caller's tenant</li>
 *   <li>attestRisk advances next-due-date and defaults blank decision</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErmGapServiceTest {

    private static final Long ORG_ID = 41L;
    private static final Long OTHER_ORG = 99L;

    @Mock private RiskControlRepository     riskControls;
    @Mock private RiskRepository            risks;
    @Mock private RiskAttestationRepository riskAttestations;
    @Mock private AuditLogService           audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager             em;

    @InjectMocks private ErmGapService service;

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
    // attestRisk — tenant verification
    // ══════════════════════════════════════════════════════════════════

    @Test void attestRisk_rejectsCrossTenant() {
        Risk other = new Risk();
        other.setOrganisationId(OTHER_ORG);
        when(risks.findById(99L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.attestRisk(99L,
            new ErmGapService.AttestBody("CONFIRMED", "ok", "موافق", "Tester")))
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

        service.attestRisk(1L, new ErmGapService.AttestBody("CONFIRMED",
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

        service.attestRisk(1L, new ErmGapService.AttestBody("", null, null, null));

        verify(riskAttestations).save(cap.capture());
        assertThat(cap.getValue().getDecision()).isEqualTo("CONFIRMED");
    }
}
