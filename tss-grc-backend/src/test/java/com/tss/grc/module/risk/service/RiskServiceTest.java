package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.dto.RiskResponse;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskCategoryRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskService — Sprint 4 §4.1 test backlog.
 *
 * Locks the ISO 31000 / COSO ERM lifecycle state machine and tenant-scope rules:
 *
 *   OPEN ─────► UNDER_REVIEW ─────► MITIGATING ─────► CLOSED
 *     │              │
 *     └──────────────┴────► ACCEPTED ─────► CLOSED
 *                                              │
 *                                              ▼
 *                                            OPEN  (reopen on recurrence)
 *
 * Plus the incident-feedback loop: bumpLikelihoodFromIncident must
 * increment residualLikelihood by 1 capped at 5.
 */
@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long RISK_ID   = 17L;

    @Mock private RiskRepository riskRepository;
    @Mock private RiskCategoryRepository riskCategoryRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private RiskService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Risk risk(String status, Long orgId) {
        Risk r = new Risk();
        r.setId(RISK_ID);
        r.setOrganisationId(orgId);
        r.setTitleEn("Sample risk");
        r.setStatus(status);
        return r;
    }

    private static Risk risk(String status) { return risk(status, ORG_ID); }

    private static RiskRequest req() {
        return new RiskRequest(
            "Title EN", "العنوان", "desc", null, null,
            3, 4, 2, 3, null, null
        );
    }

    // ─── lifecycle: happy path ───────────────────────────────────────

    @Test
    void submitForReview_OPEN_to_UNDER_REVIEW() {
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskResponse resp = service.submitForReview(RISK_ID);
        assertThat(resp.status()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approve_UNDER_REVIEW_to_MITIGATING() {
        Risk r = risk("UNDER_REVIEW");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.approve(RISK_ID).status()).isEqualTo("MITIGATING");
    }

    @Test
    void close_from_MITIGATING_or_ACCEPTED() {
        // Allowed from MITIGATING
        Risk a = risk("MITIGATING");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(a));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");

        // Allowed from ACCEPTED
        Risk b = risk("ACCEPTED");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(b));
        assertThat(service.close(RISK_ID).status()).isEqualTo("CLOSED");
    }

    @Test
    void reopen_CLOSED_to_OPEN() {
        Risk r = risk("CLOSED");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.reopen(RISK_ID).status()).isEqualTo("OPEN");
    }

    @Test
    void accept_allowed_from_OPEN_and_UNDER_REVIEW() {
        Risk a = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(a));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.accept(RISK_ID).status()).isEqualTo("ACCEPTED");

        Risk b = risk("UNDER_REVIEW");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(b));
        assertThat(service.accept(RISK_ID).status()).isEqualTo("ACCEPTED");
    }

    // ─── lifecycle: illegal transitions ──────────────────────────────

    @Test
    void submitForReview_from_CLOSED_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("CLOSED")));

        assertThatThrownBy(() -> service.submitForReview(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OPEN");
    }

    @Test
    void approve_skipping_review_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN")));

        assertThatThrownBy(() -> service.approve(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void accept_from_MITIGATING_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("MITIGATING")));

        assertThatThrownBy(() -> service.accept(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reopen_from_OPEN_throws() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN")));

        assertThatThrownBy(() -> service.reopen(RISK_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");
    }

    // ─── incident feedback loop ──────────────────────────────────────

    @Test
    void bumpLikelihoodFromIncident_increments_by_1() {
        Risk r = risk("OPEN");
        r.setResidualLikelihood(2);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bumpLikelihoodFromIncident(RISK_ID);
        assertThat(r.getResidualLikelihood()).isEqualTo(3);
    }

    @Test
    void bumpLikelihoodFromIncident_caps_at_5() {
        Risk r = risk("OPEN");
        r.setResidualLikelihood(5);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bumpLikelihoodFromIncident(RISK_ID);
        assertThat(r.getResidualLikelihood()).isEqualTo(5);  // ceiling, not 6
    }

    @Test
    void bumpLikelihoodFromIncident_null_starts_at_2() {
        // Defensive: a risk with no residual likelihood yet should get 1+1=2
        Risk r = risk("OPEN");
        r.setResidualLikelihood(null);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bumpLikelihoodFromIncident(RISK_ID);
        assertThat(r.getResidualLikelihood()).isEqualTo(2);
    }

    // ─── tenant scope ────────────────────────────────────────────────

    @Test
    void get_other_tenant_throws_EntityNotFound() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN", OTHER_ORG)));

        assertThatThrownBy(() -> service.get(RISK_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Risk not found");
    }

    @Test
    void approve_other_tenant_throws_EntityNotFound() {
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(risk("UNDER_REVIEW", OTHER_ORG)));

        assertThatThrownBy(() -> service.approve(RISK_ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(riskRepository, never()).save(any());
    }

    // ─── soft delete ─────────────────────────────────────────────────

    @Test
    void delete_soft_deletes_via_save() {
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(RISK_ID);

        assertThat(r.getIsDeleted()).isTrue();
        verify(riskRepository).save(r);
        verify(riskRepository, never()).deleteById(any());
    }

    // ─── create stamps tenant + identifiedDate ───────────────────────

    @Test
    void create_stamps_organisation_and_identifiedDate() {
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> {
            Risk saved = inv.getArgument(0);
            saved.setId(RISK_ID);
            return saved;
        });

        service.create(req());

        org.mockito.ArgumentCaptor<Risk> captor = org.mockito.ArgumentCaptor.forClass(Risk.class);
        verify(riskRepository).save(captor.capture());
        Risk saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getIdentifiedDate()).isNotNull();
    }
}
