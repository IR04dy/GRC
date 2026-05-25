package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanResponse;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingExitPlan;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingExitPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutsourcingExitPlanServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private OutsourcingExitPlanRepository repo;
    @Mock private OutsourcingArrangementRepository arrangementRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private OutsourcingExitPlanService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new OutsourcingExitPlanService(repo, arrangementRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static OutsourcingArrangement arr() {
        OutsourcingArrangement a = new OutsourcingArrangement();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setArrangementCode("OUT-2026-001");
        return a;
    }

    @Test void create_persistsExitPlan() {
        when(arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.of(arr()));
        when(repo.findByOrganisationIdAndArrangementIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.empty());
        when(repo.save(any(OutsourcingExitPlan.class))).thenAnswer(inv -> {
            OutsourcingExitPlan p = inv.getArgument(0);
            p.setId(700L);
            return p;
        });

        ExitPlanCreateRequest req = new ExitPlanCreateRequest(
            50L, "trigger en", "trigger ar",
            "[]",
            "repat en", "repat ar",
            "ANNUAL", null
        );

        ExitPlanResponse out = service.create(req);

        assertThat(out.testFrequency()).isEqualTo("ANNUAL");
        verify(audit).log(eq("CREATE"), eq("OutsourcingExitPlan"), eq(700L), anyString());
    }

    @Test void create_duplicatePerArrangement_throws() {
        OutsourcingExitPlan existing = new OutsourcingExitPlan();
        existing.setId(700L);
        existing.setArrangementId(50L);
        when(arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.of(arr()));
        when(repo.findByOrganisationIdAndArrangementIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.of(existing));

        ExitPlanCreateRequest req = new ExitPlanCreateRequest(
            50L, "trigger en", "trigger ar", "[]",
            null, null, "ANNUAL", null
        );

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Exit plan already exists");
        verify(repo, never()).save(any());
    }

    @Test void recordTest_updatesLastTestedAndEmitsEvent() {
        OutsourcingExitPlan p = new OutsourcingExitPlan();
        p.setId(700L);
        p.setOrganisationId(ORG_ID);
        p.setArrangementId(50L);
        p.setTriggerConditionsEn("en");
        p.setTriggerConditionsAr("ar");
        p.setTestFrequency("ANNUAL");

        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(700L)))
            .thenReturn(Optional.of(p));
        when(repo.save(any(OutsourcingExitPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate when = LocalDate.of(2026, 5, 10);
        ExitPlanResponse out = service.recordTest(700L, when);

        assertThat(out.lastTestedDate()).isEqualTo(when);
        verify(audit).log(eq("TEST"), eq("OutsourcingExitPlan"), eq(700L), anyString());

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(events.capture());
        assertThat(events.getValue().triggerEvent()).isEqualTo("OUTSOURCING_EXIT_TESTED");
        assertThat(events.getValue().payload()).containsEntry("testFrequency", "ANNUAL");
    }

    @Test void findOverdueTest_passesCutoffsToRepo() {
        when(repo.findOverdueTest(eq(ORG_ID), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of());

        service.findOverdueTest();

        verify(repo).findOverdueTest(eq(ORG_ID), any(LocalDate.class), any(LocalDate.class));
    }
}
