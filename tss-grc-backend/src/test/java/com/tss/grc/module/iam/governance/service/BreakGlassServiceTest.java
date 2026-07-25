package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.governance.repository.BreakGlassEventRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DF-1/BL-C1 regression tests for the break-glass expiry sweep.
 *
 * <p>The prod defect: {@code scheduledExpirySweep()} ran the {@code @Modifying} expiry
 * update directly on the scheduler thread — no ambient transaction — so every run died
 * with {@code TransactionRequiredException} and expired grants outlived their window.
 * These tests pin the fix: the update must execute <em>between</em>
 * {@code getTransaction(REQUIRES_NEW)} and {@code commit}, per org, with the tenant
 * bound to {@link TenantContext} for the duration and cleared afterwards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BreakGlassServiceTest {

    @Mock BreakGlassEventRepository events;
    @Mock RoleRepository roles;
    @Mock AuditLogService audit;
    @Mock NotificationService notifications;
    @Mock PlatformTransactionManager txManager;

    @InjectMocks BreakGlassService service;

    @BeforeEach
    void stubTransactionManager() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void scheduledSweep_runsModifyingUpdateInsideRequiresNewTransaction() {
        when(events.distinctActiveOrganisationIds()).thenReturn(List.of(1L));
        when(events.expireEventsForOrg(1L)).thenReturn(2);

        service.scheduledExpirySweep();

        // The @Modifying update must run strictly inside the transaction window.
        InOrder txWindow = inOrder(txManager, events);
        ArgumentCaptor<TransactionDefinition> def = ArgumentCaptor.forClass(TransactionDefinition.class);
        txWindow.verify(txManager).getTransaction(def.capture());
        txWindow.verify(events).expireEventsForOrg(1L);
        txWindow.verify(txManager).commit(any());
        assertThat(def.getValue().getPropagationBehavior())
            .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void scheduledSweep_bindsTenantPerOrg_auditsInsideBinding_andClearsAfter() {
        when(events.distinctActiveOrganisationIds()).thenReturn(List.of(7L));
        AtomicReference<Long> tenantAtUpdate = new AtomicReference<>();
        AtomicReference<Long> tenantAtAudit = new AtomicReference<>();
        when(events.expireEventsForOrg(7L)).thenAnswer(inv -> {
            tenantAtUpdate.set(TenantContext.getCurrentTenant());
            return 1;
        });
        when(audit.recordEvent(any())).thenAnswer(inv -> {
            tenantAtAudit.set(TenantContext.getCurrentTenant());
            return null;
        });

        service.scheduledExpirySweep();

        assertThat(tenantAtUpdate.get()).isEqualTo(7L);
        assertThat(tenantAtAudit.get()).isEqualTo(7L);
        assertThat(TenantContext.getCurrentTenant())
            .as("scheduler thread must not leak the tenant binding")
            .isNull();
    }

    @Test
    void scheduledSweep_zeroExpired_skipsAudit() {
        when(events.distinctActiveOrganisationIds()).thenReturn(List.of(1L));
        when(events.expireEventsForOrg(1L)).thenReturn(0);

        service.scheduledExpirySweep();

        verify(audit, never()).recordEvent(any());
        verify(txManager).commit(any());
    }

    @Test
    void scheduledSweep_orgFailureRollsBack_andContinuesToNextOrg() {
        when(events.distinctActiveOrganisationIds()).thenReturn(List.of(1L, 2L));
        when(events.expireEventsForOrg(1L)).thenThrow(new IllegalStateException("boom"));
        when(events.expireEventsForOrg(2L)).thenReturn(1);

        service.scheduledExpirySweep();   // must not propagate

        verify(txManager).rollback(any());
        verify(txManager).commit(any());
        verify(events).expireEventsForOrg(2L);
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void scheduledSweep_noActiveOrgs_touchesNoTransaction() {
        when(events.distinctActiveOrganisationIds()).thenReturn(List.of());

        service.scheduledExpirySweep();

        verify(txManager, never()).getTransaction(any());
        verify(events, never()).expireEventsForOrg(any());
    }

    @Test
    void manualSweep_isTenantScoped_andAudits() {
        TenantContext.setCurrentTenant(5L);
        when(events.expireEventsForOrg(5L)).thenReturn(3);

        int n = service.expirySweep();

        assertThat(n).isEqualTo(3);
        verify(events).expireEventsForOrg(5L);
        verify(events, times(1)).expireEventsForOrg(any());
        verify(audit).recordEvent(any());
    }
}
