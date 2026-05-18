package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crisis Event lock — independent audit Tier-1 fix.
 *
 * <ul>
 *   <li>create validates severity 1..5</li>
 *   <li>severity ≥ 4 emits BCM_CRISIS_HIGH_SEVERITY event</li>
 *   <li>personalDataAffected emits BCM_CRISIS_PDPL_BREACH event</li>
 *   <li>cyberRelated emits BCM_CRISIS_CYBER_LINKED event</li>
 *   <li>supplierInvolved emits BCM_CRISIS_SUPPLIER_LINKED event</li>
 *   <li>illegal state transitions rejected</li>
 *   <li>CLOSED requires rootCause AND lessonsLearnedId</li>
 *   <li>activateBcp appends to relatedBcpIdsJson and emits event</li>
 *   <li>SitRep with severityChange propagates onto event + emits if ≥ 4</li>
 *   <li>addSitRep / addDecision validate required fields</li>
 *   <li>logRegulatoryNotification validates regulator enum</li>
 *   <li>update cannot bypass transition() for status changes</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrisisEventServiceTest {

    private static final Long ORG_ID = 31L;

    @Mock private CrisisEventRepository events;
    @Mock private CrisisSituationReportRepository sitreps;
    @Mock private CrisisDecisionRepository decisions;
    @Mock private CrisisRegulatoryNotificationRepository regulatoryNotifications;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private CrisisEventService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Make every save echo the entity back with an id.
        when(events.save(any(CrisisEvent.class))).thenAnswer(inv -> {
            CrisisEvent e = inv.getArgument(0);
            if (e.getId() == null) e.setId(99L);
            return e;
        });
        // Cheap stub for code generation: empty page = 0 events so far.
        when(events.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of()));
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    private static CrisisEvent event(int severity, String status) {
        CrisisEvent e = new CrisisEvent();
        e.setId(1L); e.setOrganisationId(ORG_ID);
        e.setEventCode("CRIS-2026-000001");
        e.setTitleEn("Test event");
        e.setSeverity(severity);
        e.setStatus(status);
        return e;
    }

    // ─── Create validation ──────────────────────────────────────

    @Test void create_requiresTitleEn() {
        CrisisEvent e = event(2, "DETECTED"); e.setTitleEn(null); e.setId(null);
        assertThatThrownBy(() -> service.create(e))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("titleEn");
    }

    @Test void create_requiresSeverityInRange() {
        CrisisEvent e = event(7, "DETECTED"); e.setId(null);
        assertThatThrownBy(() -> service.create(e))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("severity");
    }

    // ─── Trigger event fan-out ──────────────────────────────────

    @Test void create_severity4_emitsHighSeverityEvent() {
        CrisisEvent e = event(4, "DETECTED"); e.setId(null);
        service.create(e);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_DETECTED", "BCM_CRISIS_HIGH_SEVERITY");
    }

    @Test void create_severity2_doesNotEmitHighSeverity() {
        CrisisEvent e = event(2, "DETECTED"); e.setId(null);
        service.create(e);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_DETECTED")
            .doesNotContain("BCM_CRISIS_HIGH_SEVERITY");
    }

    @Test void create_personalDataAffected_emitsPdplBreach() {
        CrisisEvent e = event(3, "DETECTED"); e.setId(null);
        e.setPersonalDataAffected(true);
        service.create(e);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_PDPL_BREACH");
    }

    @Test void create_cyberRelated_emitsCyberLinked() {
        CrisisEvent e = event(3, "DETECTED"); e.setId(null);
        e.setCyberRelated(true);
        service.create(e);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_CYBER_LINKED");
    }

    @Test void create_supplierInvolved_emitsSupplierLinked() {
        CrisisEvent e = event(3, "DETECTED"); e.setId(null);
        e.setSupplierInvolved(true);
        service.create(e);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_SUPPLIER_LINKED");
    }

    // ─── State machine ──────────────────────────────────────────

    @Test void transition_legalPath_DETECTEDtoASSESSING() {
        CrisisEvent e = event(2, "DETECTED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent out = service.transition(1L, "ASSESSING");
        assertThat(out.getStatus()).isEqualTo("ASSESSING");
    }

    @Test void transition_illegal_CLOSEDfromDETECTED_rejected() {
        CrisisEvent e = event(2, "DETECTED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.transition(1L, "CLOSED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Illegal transition");
    }

    @Test void transition_CLOSED_requiresRootCauseAndLessons() {
        CrisisEvent e = event(2, "RECOVERED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.transition(1L, "CLOSED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("rootCause");
    }

    @Test void transition_CLOSED_succeedsWithRootCauseAndLessons() {
        CrisisEvent e = event(2, "RECOVERED");
        e.setRootCause("Network switch failure in data centre A");
        e.setLessonsLearnedId(77L);
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent out = service.transition(1L, "CLOSED");
        assertThat(out.getStatus()).isEqualTo("CLOSED");
        assertThat(out.getClosureTime()).isNotNull();

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_CLOSED");
    }

    @Test void transition_ACTIVATED_stampsActivationTime() {
        CrisisEvent e = event(3, "ASSESSING");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent out = service.transition(1L, "ACTIVATED");
        assertThat(out.getStatus()).isEqualTo("ACTIVATED");
        assertThat(out.getActivationTime()).isNotNull();
    }

    // ─── BCP / DRP activation ───────────────────────────────────

    @Test void activateBcp_appendsToJsonArrayAndEmitsEvent() {
        CrisisEvent e = event(3, "ACTIVATED");
        e.setRelatedBcpIdsJson("[5]");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent out = service.activateBcp(1L, 12L);
        assertThat(out.getRelatedBcpIdsJson()).isEqualTo("[5,12]");
        verify(eventPublisher, times(1)).publishEvent(any(DomainEvent.class));
    }

    @Test void activateBcp_initialEmptyArray() {
        CrisisEvent e = event(3, "ACTIVATED");
        e.setRelatedBcpIdsJson(null);
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent out = service.activateBcp(1L, 7L);
        assertThat(out.getRelatedBcpIdsJson()).isEqualTo("[7]");
    }

    // ─── SitReps ────────────────────────────────────────────────

    @Test void addSitRep_requiresSummary() {
        CrisisEvent e = event(2, "ACTIVATED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisSituationReport r = new CrisisSituationReport();
        assertThatThrownBy(() -> service.addSitRep(1L, r))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("summary");
    }

    @Test void addSitRep_severityEscalationPropagates() {
        CrisisEvent e = event(2, "ACTIVATED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));
        when(sitreps.save(any(CrisisSituationReport.class))).thenAnswer(inv -> {
            CrisisSituationReport s = inv.getArgument(0); s.setId(2L); return s;
        });

        CrisisSituationReport r = new CrisisSituationReport();
        r.setSummary("Spread to second data centre");
        r.setSeverityChange(4);

        service.addSitRep(1L, r);

        // Event entity severity should be updated to the new sev
        assertThat(e.getSeverity()).isEqualTo(4);
        // Both BCM_CRISIS_HIGH_SEVERITY (from sev change) emitted
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .contains("BCM_CRISIS_HIGH_SEVERITY");
    }

    // ─── Decisions ──────────────────────────────────────────────

    @Test void addDecision_requiresDecisionText() {
        CrisisEvent e = event(2, "ACTIVATED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisDecision d = new CrisisDecision();
        assertThatThrownBy(() -> service.addDecision(1L, d))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("decisionText");
    }

    // ─── Regulatory notifications ───────────────────────────────

    @Test void logRegulatoryNotification_validatesRegulator() {
        CrisisEvent e = event(4, "ACTIVATED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisRegulatoryNotification n = new CrisisRegulatoryNotification();
        n.setRegulator("WHO");  // not in the closed set
        assertThatThrownBy(() -> service.logRegulatoryNotification(1L, n))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("regulator");
    }

    @Test void logRegulatoryNotification_acceptsKnownRegulator() {
        CrisisEvent e = event(4, "ACTIVATED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));
        when(regulatoryNotifications.save(any(CrisisRegulatoryNotification.class)))
            .thenAnswer(inv -> { CrisisRegulatoryNotification x = inv.getArgument(0); x.setId(3L); return x; });

        CrisisRegulatoryNotification n = new CrisisRegulatoryNotification();
        n.setRegulator("NCEMA");

        CrisisRegulatoryNotification saved = service.logRegulatoryNotification(1L, n);
        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getCrisisEventId()).isEqualTo(1L);
    }

    // ─── Update guard ───────────────────────────────────────────

    @Test void update_cannotChangeStatus() {
        CrisisEvent e = event(3, "ACTIVATED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent patch = new CrisisEvent();
        patch.setStatus("CLOSED");
        assertThatThrownBy(() -> service.update(1L, patch))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("transition");
    }

    @Test void update_cannotEditClosedEvent() {
        CrisisEvent e = event(3, "CLOSED");
        when(events.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(e));

        CrisisEvent patch = new CrisisEvent();
        patch.setTitleEn("changed");
        assertThatThrownBy(() -> service.update(1L, patch))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CLOSED");
    }

    @Test void create_skipsAllTriggerEventsForLowSevWithoutFlags() {
        CrisisEvent e = event(2, "DETECTED"); e.setId(null);
        service.create(e);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent)
            .doesNotContain("BCM_CRISIS_HIGH_SEVERITY",
                            "BCM_CRISIS_PDPL_BREACH",
                            "BCM_CRISIS_CYBER_LINKED",
                            "BCM_CRISIS_SUPPLIER_LINKED");
        verify(audit, atLeastOnce()).log(any(), any(), any(), any());
        verify(audit, never()).log(eq("UPDATE"), any(), any(), any());
    }
}
