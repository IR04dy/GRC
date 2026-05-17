package com.tss.grc.module.risk.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskTreatment;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.repository.RiskTreatmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RiskTreatmentService — locks the ISO 31000 treatment lifecycle and
 * Rule T1 (a Risk in TREATMENT must keep at least one live treatment).
 *
 * <ul>
 *   <li>Type must be one of AVOID/MITIGATE/TRANSFER/ACCEPT.</li>
 *   <li>State machine: PLANNED → IN_PROGRESS → COMPLETED, with CANCELLED
 *       as escape valve from non-terminal states; terminal states refuse
 *       further transitions.</li>
 *   <li>COMPLETED stamps {@code completedAt}.</li>
 *   <li>Deleting the last live treatment from a Risk in TREATMENT
 *       refused unless another one is still active.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RiskTreatmentServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long RISK_ID = 100L;

    @Mock private RiskTreatmentRepository treatments;
    @Mock private RiskRepository risks;
    @InjectMocks private RiskTreatmentService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Risk risk(String status) {
        Risk r = new Risk();
        r.setId(RISK_ID);
        r.setOrganisationId(ORG_ID);
        r.setStatus(status);
        return r;
    }

    private static RiskTreatment treatment(Long id, String type, String status) {
        RiskTreatment t = new RiskTreatment();
        t.setId(id);
        t.setRiskId(RISK_ID);
        t.setTreatmentType(type);
        t.setStatus(status);
        t.setDescriptionEn("Implement compensating controls");
        return t;
    }

    @Test void create_invalidTypeRejected() {
        RiskTreatment bad = treatment(null, "DELEGATE", null);
        bad.setId(null);

        assertThatThrownBy(() -> service.create(bad))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("treatmentType");
    }

    @Test void create_defaultsStatusToPlanned() {
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN")));
        when(treatments.save(any())).thenAnswer(inv -> {
            RiskTreatment x = inv.getArgument(0);
            x.setId(1L);
            return x;
        });
        RiskTreatment body = treatment(null, "MITIGATE", null);
        body.setId(null);
        body.setStatus(null);

        RiskTreatment saved = service.create(body);
        assertThat(saved.getStatus()).isEqualTo("PLANNED");
        assertThat(saved.getId()).isEqualTo(1L);
    }

    @Test void create_missingDescriptionRejected() {
        RiskTreatment t = treatment(null, "MITIGATE", null);
        t.setId(null);
        t.setDescriptionEn(null);

        assertThatThrownBy(() -> service.create(t))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("descriptionEn");
    }

    @Test void transition_plannedToInProgressOk() {
        RiskTreatment t = treatment(1L, "MITIGATE", "PLANNED");
        when(treatments.findById(1L)).thenReturn(Optional.of(t));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("TREATMENT")));
        when(treatments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskTreatment out = service.transition(1L, "IN_PROGRESS");
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(out.getCompletedAt()).isNull();
    }

    @Test void transition_completedStampsTimestamp() {
        RiskTreatment t = treatment(1L, "MITIGATE", "IN_PROGRESS");
        when(treatments.findById(1L)).thenReturn(Optional.of(t));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("TREATMENT")));
        when(treatments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskTreatment out = service.transition(1L, "COMPLETED");
        assertThat(out.getStatus()).isEqualTo("COMPLETED");
        assertThat(out.getCompletedAt()).isNotNull();
    }

    @Test void transition_terminalStateRefuses() {
        RiskTreatment t = treatment(1L, "MITIGATE", "COMPLETED");
        when(treatments.findById(1L)).thenReturn(Optional.of(t));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("TREATMENT")));

        assertThatThrownBy(() -> service.transition(1L, "IN_PROGRESS"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void transition_cancelledFromPlannedOk() {
        RiskTreatment t = treatment(1L, "AVOID", "PLANNED");
        when(treatments.findById(1L)).thenReturn(Optional.of(t));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("TREATMENT")));
        when(treatments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskTreatment out = service.transition(1L, "CANCELLED");
        assertThat(out.getStatus()).isEqualTo("CANCELLED");
    }

    @Test void delete_lastLiveTreatmentOfRiskInTreatmentRefused() {
        RiskTreatment only = treatment(1L, "MITIGATE", "IN_PROGRESS");
        when(treatments.findById(1L)).thenReturn(Optional.of(only));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("TREATMENT")));
        when(treatments.findByRiskIdOrderByCreatedAtDesc(RISK_ID)).thenReturn(List.of(only));

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("last live treatment");
    }

    @Test void delete_lastLiveTreatmentAllowedWhenRiskNotInTreatment() {
        RiskTreatment only = treatment(1L, "MITIGATE", "IN_PROGRESS");
        when(treatments.findById(1L)).thenReturn(Optional.of(only));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("OPEN")));
        when(treatments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);
        assertThat(only.getIsDeleted()).isTrue();
    }

    @Test void delete_withSiblingLiveTreatmentAllowed() {
        RiskTreatment a = treatment(1L, "MITIGATE", "IN_PROGRESS");
        RiskTreatment b = treatment(2L, "MITIGATE", "PLANNED");
        when(treatments.findById(1L)).thenReturn(Optional.of(a));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk("TREATMENT")));
        when(treatments.findByRiskIdOrderByCreatedAtDesc(RISK_ID)).thenReturn(List.of(a, b));
        when(treatments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);
        assertThat(a.getIsDeleted()).isTrue();
    }
}
