package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.service.IssueService;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriBreachResponse;
import com.tss.grc.module.risk.kri.entity.KriBreach;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import com.tss.grc.module.risk.kri.repository.KriBreachRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KriBreachServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long KRI_ID = 42L;
    private static final UUID KRI_EX = UUID.randomUUID();
    private static final UUID BREACH_EX = UUID.randomUUID();

    @Mock private KriDefinitionRepository defRepo;
    @Mock private KriMeasurementRepository measRepo;
    @Mock private KriBreachRepository breachRepo;
    @Mock private IssueService issueService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private KriBreachService service;

    @BeforeEach
    void setUp() {
        service = new KriBreachService(defRepo, measRepo, breachRepo, issueService, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static KriDefinition kri() {
        KriDefinition d = new KriDefinition();
        d.setId(KRI_ID);
        d.setExternalId(KRI_EX);
        d.setOrganisationId(ORG_ID);
        d.setKriCode("KRI-CYBER-MTTR-001");
        d.setTitleEn("MTTR P1");
        d.setTitleAr("متوسط زمن الإغلاق للحوادث من المستوى الأول");
        d.setCategory("CYBER");
        d.setMeasurementUnit("HOURS");
        d.setValueDirection("LOWER_IS_BETTER");
        d.setGreenThreshold(new BigDecimal("4"));
        d.setAmberThreshold(new BigDecimal("12"));
        return d;
    }

    private static KriBreach openRedBreach() {
        KriBreach b = new KriBreach();
        b.setId(7777L);
        b.setExternalId(BREACH_EX);
        b.setKriId(KRI_ID);
        b.setOrganisationId(ORG_ID);
        b.setBreachLevel("RED");
        b.setOpenedAt(LocalDateTime.now().minusDays(3));
        return b;
    }

    private static KriMeasurement red(int hoursAgo) {
        KriMeasurement m = new KriMeasurement();
        m.setKriId(KRI_ID);
        m.setOrganisationId(ORG_ID);
        m.setValue(new BigDecimal("48"));
        m.setStatus("RED");
        m.setMeasuredAt(LocalDateTime.now().minusHours(hoursAgo));
        return m;
    }

    // ─── Acknowledge ─────────────────────────────────────────────

    @Test
    void acknowledge_stampsActorAndTimestamp() {
        KriBreach b = openRedBreach();
        when(breachRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BREACH_EX))
                .thenReturn(Optional.of(b));
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(inv -> inv.getArgument(0));
        when(defRepo.findById(KRI_ID)).thenReturn(Optional.of(kri()));

        KriBreachResponse out = service.acknowledge(BREACH_EX, 555L);

        assertThat(out.acknowledgedAt()).isNotNull();
        assertThat(out.acknowledgedByUserId()).isEqualTo(555L);
        verify(audit).log(eq("KRI_BREACH_ACKNOWLEDGED"), eq("KriBreach"), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("KRI_BREACH_ACKNOWLEDGED");
    }

    @Test
    void acknowledge_idempotent_whenAlreadyAcknowledged() {
        KriBreach b = openRedBreach();
        b.setAcknowledgedAt(LocalDateTime.now().minusHours(1));
        b.setAcknowledgedByUserId(111L);
        when(breachRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BREACH_EX))
                .thenReturn(Optional.of(b));
        when(defRepo.findById(KRI_ID)).thenReturn(Optional.of(kri()));

        KriBreachResponse out = service.acknowledge(BREACH_EX, 555L);

        assertThat(out.acknowledgedByUserId()).isEqualTo(111L);
        verify(breachRepo, never()).save(any(KriBreach.class));
    }

    @Test
    void acknowledge_rejectsClosedBreach() {
        KriBreach b = openRedBreach();
        b.setClosedAt(LocalDateTime.now());
        when(breachRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BREACH_EX))
                .thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.acknowledge(BREACH_EX, 555L))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── Close ──────────────────────────────────────────────────

    @Test
    void close_stampsClosedAtAndEmitsEvent() {
        KriBreach b = openRedBreach();
        when(breachRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, BREACH_EX))
                .thenReturn(Optional.of(b));
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(inv -> inv.getArgument(0));
        when(defRepo.findById(KRI_ID)).thenReturn(Optional.of(kri()));

        KriBreachResponse out = service.close(BREACH_EX, 555L);

        assertThat(out.closedAt()).isNotNull();
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("KRI_BREACH_CLOSED");
        assertThat(ev.getValue().payload()).containsEntry("closedManually", "true");
    }

    // ─── Sustained-red ladder ─────────────────────────────────────

    @Test
    void checkSustainedRed_opensCapaWhenThreeConsecutiveReds() {
        KriDefinition d = kri();
        KriBreach open = openRedBreach();
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(open));
        when(measRepo.findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                eq(ORG_ID), eq(KRI_ID), any()))
                .thenReturn(List.of(red(1), red(25), red(49)));

        Issue created = new Issue();
        created.setId(8888L);
        when(issueService.create(any(Issue.class))).thenReturn(created);
        when(breachRepo.save(any(KriBreach.class))).thenAnswer(inv -> inv.getArgument(0));

        Long issueId = service.checkSustainedRed(d);

        assertThat(issueId).isEqualTo(8888L);
        ArgumentCaptor<Issue> iss = ArgumentCaptor.forClass(Issue.class);
        verify(issueService).create(iss.capture());
        Issue issue = iss.getValue();
        assertThat(issue.getSourceModule()).isEqualTo("risk");
        assertThat(issue.getSourceEntityType()).isEqualTo("KriBreach");
        assertThat(issue.getSeverity()).isEqualTo("CRITICAL");
        assertThat(issue.getPriority()).isEqualTo("URGENT");
        assertThat(issue.getTitleEn()).contains(d.getKriCode());
        assertThat(issue.getTitleAr()).contains("مؤشّر المخاطر");
    }

    @Test
    void checkSustainedRed_skipsWhenLessThanThreeRedMeasurements() {
        KriDefinition d = kri();
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(openRedBreach()));
        when(measRepo.findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                eq(ORG_ID), eq(KRI_ID), any()))
                .thenReturn(List.of(red(1), red(25))); // only 2 reds

        Long issueId = service.checkSustainedRed(d);

        assertThat(issueId).isNull();
        verify(issueService, never()).create(any(Issue.class));
    }

    @Test
    void checkSustainedRed_skipsWhenNonRedInTrailingWindow() {
        KriDefinition d = kri();
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(openRedBreach()));
        KriMeasurement amber = new KriMeasurement();
        amber.setStatus("AMBER");
        amber.setValue(new BigDecimal("8"));
        when(measRepo.findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
                eq(ORG_ID), eq(KRI_ID), any()))
                .thenReturn(List.of(red(1), amber, red(49)));

        Long issueId = service.checkSustainedRed(d);

        assertThat(issueId).isNull();
        verify(issueService, never()).create(any(Issue.class));
    }

    @Test
    void checkSustainedRed_idempotentWhenCapaAlreadyOpened() {
        KriDefinition d = kri();
        KriBreach open = openRedBreach();
        open.setCapaTaskId(99999L);
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(open));

        Long issueId = service.checkSustainedRed(d);

        assertThat(issueId).isEqualTo(99999L);
        verify(issueService, never()).create(any(Issue.class));
    }

    @Test
    void checkSustainedRed_noOpWhenOpenBreachIsAmber() {
        KriDefinition d = kri();
        KriBreach amber = openRedBreach();
        amber.setBreachLevel("AMBER");
        when(breachRepo.findFirstByOrganisationIdAndKriIdAndClosedAtIsNullAndIsDeletedFalseOrderByOpenedAtDesc(
                ORG_ID, KRI_ID)).thenReturn(Optional.of(amber));

        assertThat(service.checkSustainedRed(d)).isNull();
        verify(issueService, never()).create(any(Issue.class));
    }
}
