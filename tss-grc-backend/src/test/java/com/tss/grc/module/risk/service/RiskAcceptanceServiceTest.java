package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.ApproveDto;
import com.tss.grc.module.risk.dto.RiskAcceptanceDtos.CreateDto;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAcceptance;
import com.tss.grc.module.risk.repository.RiskAcceptanceRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAcceptanceServiceTest {

    private static final Long ORG = 1L;

    @Mock RiskAcceptanceRepository acceptances;
    @Mock RiskRepository risks;
    @Mock AuditLogService audit;
    @Mock ApplicationEventPublisher events;
    @InjectMocks RiskAcceptanceService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private Risk risk(String status, Integer residual) {
        Risk r = new Risk();
        r.setId(7L); r.setOrganisationId(ORG); r.setStatus(status); r.setResidualScore(residual);
        return r;
    }
    private CreateDto dto(LocalDate expiry, Integer appetite, String cro, String board) {
        return new CreateDto("justify", null, null, null, appetite, expiry, cro, board);
    }

    @Test void request_rejectsPastExpiry() {
        when(risks.findById(7L)).thenReturn(Optional.of(risk("OPEN", 20)));
        assertThatThrownBy(() -> service.request(7L, dto(LocalDate.now().minusDays(1), 12, "CRO", null), 1L))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiry");
    }

    @Test void request_snapshotsResidual_flagsBoardAboveAppetite() {
        when(risks.findById(7L)).thenReturn(Optional.of(risk("UNDER_REVIEW", 20)));
        when(acceptances.save(any())).thenAnswer(i -> i.getArgument(0));
        RiskAcceptance a = service.request(7L, dto(LocalDate.now().plusDays(90), 12, "CRO", null), 1L);
        assertThat(a.getResidualScoreAtAcceptance()).isEqualTo(20);  // server-stamped from the risk
        assertThat(a.getRequiresBoard()).isTrue();                    // 20 > 12
        assertThat(a.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test void approve_requiresBoardWhenAboveAppetite() {
        RiskAcceptance a = new RiskAcceptance();
        a.setStatus("SUBMITTED"); a.setRiskId(7L); a.setCroSignedBy("CRO"); a.setRequiresBoard(true);
        when(acceptances.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.approve(5L, new ApproveDto(null), "admin"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Board");
    }

    @Test void approve_setsRiskAccepted() {
        RiskAcceptance a = new RiskAcceptance();
        a.setStatus("SUBMITTED"); a.setRiskId(7L); a.setCroSignedBy("CRO");
        a.setRequiresBoard(false); a.setExpiresAt(LocalDate.now().plusDays(30));
        when(acceptances.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(a));
        when(acceptances.save(any())).thenAnswer(i -> i.getArgument(0));
        Risk r = risk("UNDER_REVIEW", 20);
        when(risks.findById(7L)).thenReturn(Optional.of(r));
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));
        RiskAcceptance out = service.approve(5L, new ApproveDto(null), "admin");
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getStatus()).isEqualTo("ACCEPTED");  // parent risk flipped
    }

    @Test void expireSweep_flipsExpiredAndReopensRisk() {
        RiskAcceptance a = new RiskAcceptance();
        a.setStatus("APPROVED"); a.setRiskId(7L); a.setOrganisationId(ORG);
        a.setExpiresAt(LocalDate.now().minusDays(1));
        when(acceptances.findByStatusAndExpiresAtLessThan(eq("APPROVED"), any())).thenReturn(List.of(a));
        when(acceptances.save(any())).thenAnswer(i -> i.getArgument(0));
        Risk r = risk("ACCEPTED", 20);
        when(risks.findById(7L)).thenReturn(Optional.of(r));
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));
        int n = service.expireSweep();
        assertThat(n).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo("EXPIRED");
        assertThat(r.getStatus()).isEqualTo("UNDER_REVIEW");  // auto-reopened
    }
}
