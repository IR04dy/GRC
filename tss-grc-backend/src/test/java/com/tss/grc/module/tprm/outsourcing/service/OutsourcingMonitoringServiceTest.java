package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventResolveRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.MonitoringEventResponse;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingMonitoringEvent;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingMonitoringEventRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutsourcingMonitoringServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private OutsourcingMonitoringEventRepository repo;
    @Mock private OutsourcingArrangementRepository arrangementRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private OutsourcingMonitoringService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new OutsourcingMonitoringService(repo, arrangementRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static OutsourcingArrangement arr() {
        OutsourcingArrangement a = new OutsourcingArrangement();
        a.setId(50L);
        a.setOrganisationId(ORG_ID);
        a.setArrangementCode("OUT-2026-001");
        return a;
    }

    private static MonitoringEventCreateRequest req(String type, String severity) {
        return new MonitoringEventCreateRequest(
            50L, type, LocalDate.of(2026, 3, 4), severity,
            "EN desc", "AR desc",
            null, null,
            false
        );
    }

    @Test void record_kpiReview_savesAndEmitsBaseEvent() {
        when(arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.of(arr()));
        when(repo.save(any(OutsourcingMonitoringEvent.class))).thenAnswer(inv -> {
            OutsourcingMonitoringEvent e = inv.getArgument(0);
            e.setId(300L);
            return e;
        });

        service.record(req("KPI_REVIEW", "INFO"));

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(1)).publishEvent(events.capture());
        assertThat(events.getValue().triggerEvent()).isEqualTo("OUTSOURCING_MONITORING_EVENT_RECORDED");
    }

    @Test void record_slaBreach_emitsBaseAndSlaEvents() {
        when(arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(50L)))
            .thenReturn(Optional.of(arr()));
        when(repo.save(any(OutsourcingMonitoringEvent.class))).thenAnswer(inv -> {
            OutsourcingMonitoringEvent e = inv.getArgument(0);
            e.setId(301L);
            return e;
        });

        service.record(req("SLA_BREACH", "MAJOR"));

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(DomainEvent::triggerEvent)
            .containsExactly("OUTSOURCING_MONITORING_EVENT_RECORDED", "OUTSOURCING_SLA_BREACH");
        assertThat(events.getAllValues().get(1).payload())
            .containsEntry("eventType", "SLA_BREACH")
            .containsEntry("severity", "MAJOR");
    }

    @Test void resolve_setsResolvedTrueAndAuditLogs() {
        OutsourcingMonitoringEvent e = new OutsourcingMonitoringEvent();
        e.setId(301L);
        e.setOrganisationId(ORG_ID);
        e.setArrangementId(50L);
        e.setEventType("SLA_BREACH");
        e.setSeverity("MAJOR");
        e.setEventDate(LocalDate.of(2026, 3, 4));
        e.setDescriptionEn("en");
        e.setDescriptionAr("ar");
        e.setResolved(false);
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(eq(ORG_ID), eq(301L)))
            .thenReturn(Optional.of(e));
        when(repo.save(any(OutsourcingMonitoringEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        MonitoringEventResponse out = service.resolve(301L,
            new MonitoringEventResolveRequest("Did the thing", "أنجزنا"));

        assertThat(out.resolved()).isTrue();
        assertThat(out.actionTakenEn()).isEqualTo("Did the thing");
        assertThat(out.actionTakenAr()).isEqualTo("أنجزنا");
        verify(audit).log(eq("RESOLVE"), eq("OutsourcingMonitoringEvent"), eq(301L), anyString());
    }

    @Test void listForArrangement_passesCurrentTenant() {
        when(repo.findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByEventDateDesc(eq(ORG_ID), eq(50L)))
            .thenReturn(List.of());

        service.listForArrangement(50L);

        verify(repo).findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByEventDateDesc(eq(ORG_ID), eq(50L));
    }

    @Test void listOpen_filtersByResolvedFalse() {
        when(repo.findByOrganisationIdAndResolvedAndIsDeletedFalseOrderByEventDateDesc(eq(ORG_ID), eq(false)))
            .thenReturn(List.of());

        service.listOpen();

        verify(repo).findByOrganisationIdAndResolvedAndIsDeletedFalseOrderByEventDateDesc(eq(ORG_ID), eq(false));
    }
}
