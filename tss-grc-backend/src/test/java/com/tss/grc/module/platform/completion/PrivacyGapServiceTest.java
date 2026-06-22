package com.tss.grc.module.platform.completion;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import com.tss.grc.module.bcm.cycle.repository.CrisisEventRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link PrivacyGapService} (§5.4 Privacy slice, extracted from
 * the former BuyerGapService — QUAL-BCMP-003). Locks the code-review fixes
 * from commit 17a2fb1:
 * <ul>
 *   <li>dsrSlaSweep dedups — first sweep fires, second is a no-op</li>
 *   <li>dsrSlaSweep audit.log only on first detection (was: every sweep)</li>
 *   <li>dsrSlaSweep handles null sla_breach_notified correctly</li>
 *   <li>pdpl72hSweep only saves CrisisEvents it actually mutated</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrivacyGapServiceTest {

    private static final Long ORG_ID = 41L;

    @Mock private ProcessingActivityRepository ropa;
    @Mock private DsrCaseRepository            dsrCases;
    @Mock private CrisisEventRepository        crises;
    @Mock private AuditLogService              audit;
    @Mock private ApplicationEventPublisher    eventPublisher;

    @InjectMocks private PrivacyGapService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(dsrCases.save(any(DsrCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(crises.save(any(CrisisEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ══════════════════════════════════════════════════════════════════
    // dsrSlaSweep — idempotency + null handling
    // ══════════════════════════════════════════════════════════════════

    @Test void dsrSlaSweep_firesOnceForNullSlaBreachNotified() {
        DsrCase nullDedupCase = newDsrCase("DSR-001", null /* slaBreachNotified */);
        when(dsrCases.findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
                eq(ORG_ID), any(), any())).thenReturn(List.of(nullDedupCase));

        PrivacyGapService.DsrSlaSweepResult r1 = service.dsrSlaSweep();

        assertThat(r1.openOverdue()).isEqualTo(1);
        assertThat(r1.firedEvents()).isEqualTo(1);
        assertThat(nullDedupCase.getSlaBreachNotified()).isTrue();
        verify(eventPublisher, times(1)).publishEvent(any(DomainEvent.class));
        verify(audit, times(1)).log(eq("SLA_BREACH"), eq("DsrCase"), anyLong(), any());
    }

    @Test void dsrSlaSweep_secondCallIsNoOp_NoSpamAuditLog() {
        DsrCase alreadyNotified = newDsrCase("DSR-002", Boolean.TRUE);
        alreadyNotified.setSlaBreachedAt(LocalDateTime.now().minusDays(1));
        when(dsrCases.findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
                eq(ORG_ID), any(), any())).thenReturn(List.of(alreadyNotified));

        PrivacyGapService.DsrSlaSweepResult r = service.dsrSlaSweep();

        assertThat(r.openOverdue()).isEqualTo(1);   // still counted as overdue
        assertThat(r.firedEvents()).isEqualTo(0);    // but no event fired
        verify(eventPublisher, never()).publishEvent(any(DomainEvent.class));
        verify(audit, never()).log(eq("SLA_BREACH"), eq("DsrCase"), anyLong(), any());
        // Should not save either — nothing changed.
        verify(dsrCases, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════
    // pdpl72hSweep — only save when changed
    // ══════════════════════════════════════════════════════════════════

    @Test void pdpl72hSweep_skipsSaveWhenNothingChanged() {
        CrisisEvent ce = new CrisisEvent();
        ce.setId(1L);
        ce.setOrganisationId(ORG_ID);
        ce.setPersonalDataAffected(true);
        ce.setDetectionTime(LocalDateTime.now().minusHours(10));
        ce.setPdpl72hDeadlineAt(LocalDateTime.now().plusHours(62));     // still in window
        ce.setPdpl72hBreached(false);
        // No notification drafted yet, but deadline hasn't elapsed → no flip

        when(crises.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(ce)));

        PrivacyGapService.Pdpl72hSweepResult r = service.pdpl72hSweep();

        assertThat(r.personalDataCrises()).isEqualTo(1);
        assertThat(r.deadlinesSet()).isEqualTo(0);
        assertThat(r.newlyBreached()).isEqualTo(0);
        verify(crises, never()).save(any());
    }

    @Test void pdpl72hSweep_savesWhenDeadlineComputed() {
        CrisisEvent ce = new CrisisEvent();
        ce.setId(2L);
        ce.setOrganisationId(ORG_ID);
        ce.setPersonalDataAffected(true);
        ce.setDetectionTime(LocalDateTime.now().minusDays(1));
        ce.setPdpl72hDeadlineAt(null);  // not yet computed
        ce.setPdpl72hBreached(false);

        when(crises.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(ce)));

        PrivacyGapService.Pdpl72hSweepResult r = service.pdpl72hSweep();

        assertThat(r.deadlinesSet()).isEqualTo(1);
        assertThat(ce.getPdpl72hDeadlineAt()).isNotNull();
        verify(crises, atLeastOnce()).save(ce);
    }

    @Test void pdpl72hSweep_skipsEventsWithoutPersonalData() {
        CrisisEvent noPdImpact = new CrisisEvent();
        noPdImpact.setId(3L);
        noPdImpact.setOrganisationId(ORG_ID);
        noPdImpact.setPersonalDataAffected(false);

        when(crises.findByOrganisationIdOrderByIdDesc(eq(ORG_ID), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(noPdImpact)));

        PrivacyGapService.Pdpl72hSweepResult r = service.pdpl72hSweep();

        assertThat(r.personalDataCrises()).isEqualTo(0);
        verify(crises, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────
    private DsrCase newDsrCase(String externalId, Boolean slaBreachNotified) {
        DsrCase c = new DsrCase();
        c.setId(System.nanoTime());
        c.setOrganisationId(ORG_ID);
        c.setExternalId(externalId);
        c.setType("ACCESS");
        c.setStatus("COLLECTING");
        c.setSlaBreachNotified(slaBreachNotified);
        c.setSlaDeadline(LocalDate.now().minusDays(5));
        return c;
    }
}
