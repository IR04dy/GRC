package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmCorrectiveAction;
import com.tss.grc.module.bcm.cycle.entity.BcmGap;
import com.tss.grc.module.bcm.cycle.entity.BcmLessonLearned;
import com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGapRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmLessonLearnedRepository;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BcmRemediationService — Sprint BCM-3 rule lock.
 *
 * <ul>
 *   <li>Lesson: source / status enums validated.</li>
 *   <li>Lesson lifecycle: DRAFT → UNDER_REVIEW → APPROVED → CLOSED;
 *       REJECTED loops back; jumps refused.</li>
 *   <li>Gap: severity + source + status enums validated.</li>
 *   <li>Gap close: HIGH/CRITICAL gap cannot CLOSE without at least
 *       one corrective action linked.</li>
 *   <li>CAPA: progress ∈ [0,100]; status walks through the closure
 *       chain; CLOSED forbids evidence-less close.</li>
 *   <li>flagOverdueCapas: flips eligible rows to OVERDUE and fires
 *       BCM_CAPA_OVERDUE events.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BcmRemediationServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private BcmLessonLearnedRepository lessons;
    @Mock private BcmGapRepository gaps;
    @Mock private BcmCorrectiveActionRepository capas;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private BcmRemediationService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmLessonLearned lesson(String status) {
        BcmLessonLearned l = new BcmLessonLearned();
        l.setId(1L); l.setOrganisationId(ORG_ID);
        l.setTitleEn("Failover playbook needs update");
        l.setSourceType("EXERCISE");
        l.setStatus(status);
        return l;
    }

    private static BcmGap gap(String status, String severity) {
        BcmGap g = new BcmGap();
        g.setId(1L); g.setOrganisationId(ORG_ID);
        g.setTitleEn("RTO target exceeds capability");
        g.setSource("BIA");
        g.setSeverity(severity);
        g.setStatus(status);
        return g;
    }

    private static BcmCorrectiveAction capa(String status, Long evidenceId) {
        BcmCorrectiveAction a = new BcmCorrectiveAction();
        a.setId(1L); a.setOrganisationId(ORG_ID);
        a.setTitleEn("Roll out HA cluster");
        a.setPriority("HIGH");
        a.setProgressPercent(50);
        a.setStatus(status);
        a.setEvidenceId(evidenceId);
        return a;
    }

    // ── Lessons ────────────────────────────────────────────────────

    @Test void createLesson_invalidSourceRejected() {
        BcmLessonLearned l = lesson("DRAFT"); l.setSourceType("RUMOUR");
        assertThatThrownBy(() -> service.createLesson(l))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sourceType");
    }

    @Test void transitionLesson_approveStampsApprover() {
        BcmLessonLearned l = lesson("UNDER_REVIEW");
        when(lessons.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(l));
        when(lessons.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmLessonLearned out = service.transitionLesson(1L, "APPROVED", "alice");
        assertThat(out.getApprovedBy()).isEqualTo("alice");
        assertThat(out.getStatus()).isEqualTo("APPROVED");
    }

    @Test void transitionLesson_illegalJumpRejected() {
        BcmLessonLearned l = lesson("DRAFT");
        when(lessons.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.transitionLesson(1L, "CLOSED", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ── Gaps ───────────────────────────────────────────────────────

    @Test void createGap_invalidSeverityRejected() {
        BcmGap g = gap("OPEN", "SHOWSTOPPER");
        assertThatThrownBy(() -> service.createGap(g))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("severity");
    }

    @Test void closeHighSeverityGap_withoutCapaRefused() {
        BcmGap g = gap("UNDER_REVIEW", "HIGH");
        when(gaps.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        when(capas.findByOrganisationIdAndGapIdOrderByIdDesc(ORG_ID, 1L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.transitionGap(1L, "CLOSED", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("corrective action");
    }

    @Test void closeHighSeverityGap_withCapaOk() {
        BcmGap g = gap("UNDER_REVIEW", "HIGH");
        when(gaps.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        when(capas.findByOrganisationIdAndGapIdOrderByIdDesc(ORG_ID, 1L)).thenReturn(List.of(capa("OPEN", null)));
        when(gaps.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmGap out = service.transitionGap(1L, "CLOSED", null);
        assertThat(out.getStatus()).isEqualTo("CLOSED");
    }

    // ── CAPAs ──────────────────────────────────────────────────────

    @Test void createCapa_invalidPriorityRejected() {
        BcmCorrectiveAction a = capa("OPEN", null); a.setPriority("SUPER_HIGH");
        assertThatThrownBy(() -> service.createCapa(a))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("priority");
    }

    @Test void createCapa_progressOutOfRangeRejected() {
        BcmCorrectiveAction a = capa("OPEN", null); a.setProgressPercent(150);
        assertThatThrownBy(() -> service.createCapa(a))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("progressPercent");
    }

    @Test void closeCapa_withoutEvidenceRefused() {
        BcmCorrectiveAction a = capa("UNDER_REVIEW", null);
        when(capas.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.transitionCapa(1L, "CLOSED", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("evidence");
    }

    @Test void closeCapa_withEvidenceOk_setsProgress100() {
        BcmCorrectiveAction a = capa("UNDER_REVIEW", 42L);
        when(capas.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(a));
        when(capas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmCorrectiveAction out = service.transitionCapa(1L, "CLOSED", "bob");
        assertThat(out.getStatus()).isEqualTo("CLOSED");
        assertThat(out.getProgressPercent()).isEqualTo(100);
        assertThat(out.getClosureDate()).isNotNull();
    }

    @Test void updateCapa_closedRefused() {
        BcmCorrectiveAction a = capa("CLOSED", 42L);
        when(capas.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(a));
        BcmCorrectiveAction patch = new BcmCorrectiveAction(); patch.setTitleEn("change");
        assertThatThrownBy(() -> service.updateCapa(1L, patch))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("CLOSED");
    }

    @Test void flagOverdueCapas_flipsAndPublishesEvent() {
        BcmCorrectiveAction a = capa("IN_PROGRESS", null);
        a.setDueDate(LocalDate.now().minusDays(5));
        when(capas.findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
                any(), any(), any())).thenReturn(List.of(a));
        when(capas.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int flipped = service.flagOverdueCapas();

        assertThat(flipped).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo("OVERDUE");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events, times(1)).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("BCM_CAPA_OVERDUE");
        assertThat(ev.getValue().entityType()).isEqualTo("BcmCorrectiveAction");
    }
}
