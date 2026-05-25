package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DueDiligenceCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DueDiligenceResponse;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingDueDiligence;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingDueDiligenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutsourcingDueDiligenceServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private OutsourcingDueDiligenceRepository ddRepo;
    @Mock private OutsourcingArrangementRepository arrangementRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private OutsourcingDueDiligenceService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new OutsourcingDueDiligenceService(ddRepo, arrangementRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static OutsourcingArrangement arr() {
        OutsourcingArrangement a = new OutsourcingArrangement();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setArrangementCode("OUT-2026-001");
        a.setTitleEn("Test arrangement");
        return a;
    }

    @Test void computeComposite_averagesSixScores() {
        // (100 + 80 + 60 + 90 + 70 + 50) / 6 = 75
        BigDecimal c = OutsourcingDueDiligenceService.computeComposite(100, 80, 60, 90, 70, 50);
        assertThat(c).isEqualByComparingTo("75.00");
    }

    @Test void computeComposite_roundsHalfUp() {
        // (95 + 92 + 88 + 90 + 87 + 96) = 548 / 6 = 91.333... → 91.33
        BigDecimal c = OutsourcingDueDiligenceService.computeComposite(95, 92, 88, 90, 87, 96);
        assertThat(c).isEqualByComparingTo("91.33");

        // (1 + 1 + 1 + 1 + 1 + 4) = 9 / 6 = 1.5 → 1.50
        BigDecimal c2 = OutsourcingDueDiligenceService.computeComposite(1, 1, 1, 1, 1, 4);
        assertThat(c2).isEqualByComparingTo("1.50");
    }

    @Test void record_savesWithComputedComposite() {
        when(arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.of(arr()));
        when(ddRepo.save(any(OutsourcingDueDiligence.class))).thenAnswer(inv -> {
            OutsourcingDueDiligence dd = inv.getArgument(0);
            dd.setId(900L);
            return dd;
        });

        DueDiligenceCreateRequest req = new DueDiligenceCreateRequest(
            50L, LocalDate.of(2026, 5, 1), 1L,
            90, 80, 70, 85, 95, 75,
            "APPROVE",
            "Strong overall posture", "وضع شامل قوي"
        );

        DueDiligenceResponse out = service.record(req);

        assertThat(out.compositeScore()).isEqualByComparingTo("82.50");
        assertThat(out.recommendation()).isEqualTo("APPROVE");
        verify(audit).log(eq("CREATE"), eq("OutsourcingDueDiligence"), eq(900L), anyString());

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue().triggerEvent()).isEqualTo("OUTSOURCING_DD_COMPLETED");
    }

    @Test void record_unknownArrangement_throws() {
        when(arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(999L)))
            .thenReturn(Optional.empty());

        DueDiligenceCreateRequest req = new DueDiligenceCreateRequest(
            999L, LocalDate.of(2026, 5, 1), 1L,
            90, 80, 70, 85, 95, 75,
            "APPROVE", "", ""
        );

        assertThatThrownBy(() -> service.record(req))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void listForArrangement_filtersByTenant() {
        OutsourcingDueDiligence dd = new OutsourcingDueDiligence();
        dd.setId(1L);
        dd.setOrganisationId(ORG_ID);
        dd.setArrangementId(50L);
        dd.setAssessmentDate(LocalDate.now());
        dd.setAssessorUserId(1L);
        dd.setCompositeScore(new BigDecimal("80.00"));
        dd.setRecommendation("APPROVE");
        dd.setRationaleEn("ok");
        dd.setRationaleAr("ok");
        dd.setFinancialHealthScore(80);
        dd.setOperationalCapabilityScore(80);
        dd.setCybersecurityScore(80);
        dd.setBcmScore(80);
        dd.setPdplComplianceScore(80);
        dd.setReputationScore(80);

        when(ddRepo.findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByAssessmentDateDesc(eq(ORG_ID), eq(50L)))
            .thenReturn(List.of(dd));

        List<DueDiligenceResponse> out = service.listForArrangement(50L);

        assertThat(out).hasSize(1);
        verify(ddRepo).findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByAssessmentDateDesc(eq(ORG_ID), eq(50L));
    }
}
