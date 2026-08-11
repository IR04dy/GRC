package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.repository.CommitteeActionItemRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-188 — the OVERDUE transition existed twice, and the copy that ran was the poorer one.
 *
 * <h3>What was wrong</h3>
 *
 * <p>{@link CommitteeActionItemService#markOverdue(Long)} owns the transition: it writes the audit
 * entry {@code ACTION_OVERDUE · "Status OPEN → OVERDUE · was due <date>"} and emits
 * {@code COMMITTEE_ACTION_OVERDUE} through the same {@code publishTransition} every other status
 * change uses. Its own javadoc said <i>"Called by the scheduled sweep"</i>.
 *
 * <p>{@link CommitteeScheduledSweep} did not call it. It flipped {@code status} in place, saved, and
 * emitted its own event. Because the sweep is the <b>only</b> producer of OVERDUE in production, two
 * things followed that nobody could see from either file alone:
 *
 * <ul>
 *   <li>No {@code ACTION_OVERDUE} row has ever been written for any tenant. The only audit trace is
 *       the dedup marker {@code COM_FIRE:COMMITTEE_ACTION_OVERDUE · "Sweep fired …"} — a record that a
 *       trigger fired, not that a status changed and from what. V158's own rule text says SAMA
 *       examiners treat this register as the primary follow-through evidence.</li>
 *   <li>The two payloads disagreed: {@code ownerName} from the sweep against {@code owner} from the
 *       service, and the sweep omitted {@code fromStatus}/{@code toStatus}. Latent — AR-148 carries
 *       {@code condition_json = '{}'} so it matches unconditionally — but {@code AutomationRuleEngine}
 *       has already paid for one condition that could never match, for any payload.</li>
 * </ul>
 *
 * <h3>Why a test and not just the fix</h3>
 *
 * <p>The duplicate agreed with the original on the part that is easy to check (the row ends up
 * OVERDUE) and differed on the parts nothing asserted (the audit sentence, the payload keys). That is
 * the shape the fix has to be pinned against: a future edit that re-inlines the flip for speed would
 * pass any test that only reads {@code status}.
 */
class CommitteeOverdueTransitionTest {

    private static final Long ORG = 1L;

    private CommitteeActionItemRepository actions;
    private AuditLogRepository auditRepo;
    private AuditLogService audit;
    private ApplicationEventPublisher publisher;
    private CommitteeActionItemService service;
    private CommitteeScheduledSweep sweep;

    @BeforeEach
    void setUp() throws Exception {
        actions   = mock(CommitteeActionItemRepository.class);
        auditRepo = mock(AuditLogRepository.class);
        audit     = mock(AuditLogService.class);
        publisher = mock(ApplicationEventPublisher.class);
        service   = new CommitteeActionItemService(actions, audit, publisher);
        sweep     = new CommitteeScheduledSweep(
            actions, service,
            mock(CommitteeResolutionRepository.class),
            mock(CommitteeRepository.class),
            auditRepo,
            audit, publisher);
        when(actions.save(any(CommitteeActionItem.class))).thenAnswer(i -> i.getArgument(0));
        // No prior dedup marker — alreadyFired() reads the audit ledger, not the action repository.
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            anyLong(), anyString(), anyLong())).thenReturn(List.of());
    }

    private CommitteeActionItem late() {
        CommitteeActionItem a = new CommitteeActionItem();
        a.setId(77L);
        a.setOrganisationId(ORG);
        a.setActionCode("CAI-0077");
        a.setCommitteeId(3L);
        a.setMeetingId(9L);
        a.setOwnerName("Layla Al-Harbi");
        a.setPriority("HIGH");
        a.setDueDate(LocalDate.of(2026, 1, 31));
        a.setStatus("OPEN");
        a.setLinkedModule("RISK");
        a.setLinkedEntityCode("RSK-0042");
        return a;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedPayload() {
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(1)).publishEvent(ev.capture());
        Object e = ev.getValue();
        assertThat(e).isInstanceOf(DomainEvent.class);
        return ((DomainEvent) e).payload();
    }

    /**
     * Drive the private sweep method directly — {@code tick()} would need the whole tenant loop.
     *
     * <p>⚠ {@code TenantContext} has to be set by hand here, and that is not test scaffolding noise:
     * the service resolves the row through {@code findByIdAndOrganisationId(id, TenantContext…)},
     * so routing the sweep through it means the transition is now <b>tenant-scoped</b> where the
     * in-place flip trusted the query. {@code tick()} sets the tenant per organisation before
     * calling, which is why this holds in production.
     */
    private int runOverdueSweep() throws Exception {
        com.tss.grc.common.tenant.TenantContext.setCurrentTenant(ORG);
        java.lang.reflect.Method m = CommitteeScheduledSweep.class
            .getDeclaredMethod("sweepOverdueActions", Long.class, LocalDate.class);
        m.setAccessible(true);
        try {
            return (int) m.invoke(sweep, ORG, LocalDate.of(2026, 8, 11));
        } finally {
            com.tss.grc.common.tenant.TenantContext.clear();
        }
    }

    // ── the fix ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the sweep routes the transition through the service, so the audit sentence exists")
    void sweepGoesThroughTheService() throws Exception {
        CommitteeActionItem a = late();
        when(actions.findShouldBeOverdue(ORG, LocalDate.of(2026, 8, 11))).thenReturn(List.of(a));
        when(actions.findByIdAndOrganisationId(77L, ORG)).thenReturn(Optional.of(a));

        assertThat(runOverdueSweep()).isEqualTo(1);

        assertThat(a.getStatus()).isEqualTo("OVERDUE");
        // The transition record, not just the dedup marker. This is the assertion that fails if the
        // flip is ever re-inlined into the sweep.
        verify(audit).log("ACTION_OVERDUE", "CommitteeActionItem", 77L,
            "Status OPEN → OVERDUE · was due 2026-01-31");
    }

    @Test
    @DisplayName("one event, and its payload is the union of what the two emitters used to send")
    void thePayloadIsTheUnionOfBoth() throws Exception {
        CommitteeActionItem a = late();
        when(actions.findShouldBeOverdue(ORG, LocalDate.of(2026, 8, 11))).thenReturn(List.of(a));
        when(actions.findByIdAndOrganisationId(77L, ORG)).thenReturn(Optional.of(a));

        runOverdueSweep();

        Map<String, Object> p = capturedPayload();
        // The sweep's keys — dropping any of these would silently change a notification's content.
        assertThat(p).containsEntry("ownerName", "Layla Al-Harbi")
                     .containsEntry("dueDate", "2026-01-31")
                     .containsEntry("linkedModule", "RISK")
                     .containsEntry("linkedEntityCode", "RSK-0042");
        // The service's keys — the shape that never actually occurred in production before this fix.
        assertThat(p).containsEntry("owner", "Layla Al-Harbi")
                     .containsEntry("fromStatus", "OPEN")
                     .containsEntry("toStatus", "OVERDUE")
                     .containsEntry("actionCode", "CAI-0077")
                     .containsEntry("priority", "HIGH");
    }

    /**
     * Exactly one event, not two. The obvious wrong fix — call the service <em>and</em> keep the
     * sweep's publish — would double-fire AR-148, i.e. escalate twice to the owner's line manager.
     */
    @Test
    @DisplayName("the transition fires once, not once per emitter")
    void exactlyOneEvent() throws Exception {
        CommitteeActionItem a = late();
        when(actions.findShouldBeOverdue(ORG, LocalDate.of(2026, 8, 11))).thenReturn(List.of(a));
        when(actions.findByIdAndOrganisationId(77L, ORG)).thenReturn(Optional.of(a));

        runOverdueSweep();

        verify(publisher, times(1)).publishEvent(any(Object.class));
        verify(actions, times(1)).save(any(CommitteeActionItem.class));
    }

    /**
     * The guard now has one home. {@code findShouldBeOverdue} filters {@code OPEN|IN_PROGRESS} and
     * {@code markOverdue} refuses {@code COMPLETED|CANCELLED|OVERDUE} — two answers that agree today,
     * which is precisely why a divergence would have gone unnoticed (F2-175's SLA-arithmetic shape).
     * A row the query returned but the service refuses must not be flipped or announced.
     */
    @Test
    @DisplayName("a row the service refuses is not flipped and raises no event")
    void theServiceGuardStillDecides() throws Exception {
        CommitteeActionItem done = late();
        done.setStatus("COMPLETED");
        when(actions.findShouldBeOverdue(ORG, LocalDate.of(2026, 8, 11))).thenReturn(List.of(done));
        when(actions.findByIdAndOrganisationId(77L, ORG)).thenReturn(Optional.of(done));

        runOverdueSweep();

        assertThat(done.getStatus()).isEqualTo("COMPLETED");
        verify(publisher, never()).publishEvent(any(Object.class));
        verify(audit, never()).log(eq("ACTION_OVERDUE"), anyString(), anyLong(), anyString());
    }

    private static String eq(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }

    /**
     * The structural half: {@code CommitteeScheduledSweep} may not write the status itself.
     *
     * <p>A behaviour test can be satisfied by a sweep that calls the service <em>and</em> also sets the
     * field; this cannot. It is the F2-175 single-writer shape scoped to one transition — an invariant
     * rather than a ratchet, because the population is one and a raisable count would make a tolerated
     * second writer indistinguishable from a reviewed one.
     */
    @Test
    @DisplayName("the sweep holds no copy of the state machine")
    void theSweepDoesNotWriteTheStatus() throws Exception {
        java.nio.file.Path src = java.nio.file.Path.of(
            "src/main/java/com/tss/grc/module/admin/committee/service/CommitteeScheduledSweep.java");
        assertThat(java.nio.file.Files.isRegularFile(src))
            .as("%s not found — this assertion is vacuous without it", src.toAbsolutePath())
            .isTrue();
        String text = new String(java.nio.file.Files.readAllBytes(src),
            java.nio.charset.StandardCharsets.UTF_8);
        // Strip block comments: this file's javadoc quotes the retired line on purpose, and a raw scan
        // would read its own explanation as a violation (F2-175's comment-stripper lesson).
        String code = text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        assertThat(code)
            .as("CommitteeScheduledSweep must decide WHICH rows are late, not what late does to a "
                + "row. The status write belongs to CommitteeActionItemService, which is where the "
                + "audit sentence and the event payload live.")
            .doesNotContain("setStatus(");
    }

    /** Anti-vacuity: the reflective entry point must still exist, or every test above passes over nothing. */
    @Test
    void theSweepMethodUnderTestStillExists() throws Exception {
        java.lang.reflect.Method m = CommitteeScheduledSweep.class
            .getDeclaredMethod("sweepOverdueActions", Long.class, LocalDate.class);
        assertThat(m).isNotNull();
        Field f = CommitteeScheduledSweep.class.getDeclaredField("actionItems");
        assertThat(f.getType()).isEqualTo(CommitteeActionItemService.class);
    }
}
