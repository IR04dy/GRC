package com.tss.grc.module.cyber.asm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItBlockRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItSanctionRequest;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceShadowIt;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceShadowItRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ShadowItService} — Sprint 11 ASM.
 *
 * <p>Locks the discovery → sanction / block / NA pipeline, the
 * PDPL-flag DPO escalation, and the F8 event contract.
 */
@ExtendWith(MockitoExtension.class)
class ShadowItServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ROW_ID = 5L;
    private static final UUID ROW_EX = UUID.randomUUID();

    @Mock private AttackSurfaceShadowItRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ShadowItService service;

    @BeforeEach
    void setUp() {
        service = new ShadowItService(repo, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static AttackSurfaceShadowIt row(String status, boolean pdpl) {
        AttackSurfaceShadowIt s = new AttackSurfaceShadowIt();
        s.setId(ROW_ID);
        s.setExternalId(ROW_EX);
        s.setOrganisationId(ORG_ID);
        s.setServiceName("Notion");
        s.setServiceNameAr("نوشن");
        s.setDiscoveredVia("EMAIL_SIGNUPS");
        s.setDataClassification("UNKNOWN");
        s.setPdplDataProcessed(pdpl);
        s.setStatus(status);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Discovery ────────────────────────────────────────────────

    @Test
    void recordDiscovery_persistsAndEmitsDiscoveredEvent() {
        AtomicLong seq = new AtomicLong(900);
        when(repo.save(any(AttackSurfaceShadowIt.class))).thenAnswer(assignId(seq));

        ShadowItRequest req = new ShadowItRequest(
                "Notion", "نوشن", "notion.so",
                "EMAIL_SIGNUPS", 18, "UNKNOWN", Boolean.FALSE,
                null, null, "Eighteen signups detected.", null);

        ShadowItResponse out = service.recordDiscovery(req);

        assertThat(out.status()).isEqualTo("PENDING_REVIEW");
        assertThat(out.serviceName()).isEqualTo("Notion");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SHADOW_IT_DISCOVERED");
        verify(audit).log(eq("SHADOW_IT_DISCOVERED"), eq("AttackSurfaceShadowIt"), anyLong(), anyString());
    }

    @Test
    void recordDiscovery_pdplFlagged_emitsExtraDpoEvent() {
        AtomicLong seq = new AtomicLong(900);
        when(repo.save(any(AttackSurfaceShadowIt.class))).thenAnswer(assignId(seq));

        ShadowItRequest req = new ShadowItRequest(
                "Trello", "تريلو", "trello.com",
                "NETWORK_TRAFFIC", 4, "CONFIDENTIAL", Boolean.TRUE,
                null, null, "HR payroll board observed.", null);

        service.recordDiscovery(req);

        // Two events should fire — DISCOVERED + PDPL_FLAG.
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(cap.capture());
        List<String> triggers = cap.getAllValues().stream().map(DomainEvent::triggerEvent).toList();
        assertThat(triggers).contains("SHADOW_IT_DISCOVERED", "SHADOW_IT_PDPL_FLAG");
        verify(audit).log(eq("SHADOW_IT_PDPL_ESCALATED"), eq("AttackSurfaceShadowIt"), anyLong(), anyString());
    }

    // ─── Sanction ─────────────────────────────────────────────────

    @Test
    void sanction_linksVendorAndEmitsSanctionedEvent() {
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ROW_EX))
                .thenReturn(Optional.of(row("IN_PROGRESS", false)));
        when(repo.save(any(AttackSurfaceShadowIt.class))).thenAnswer(inv -> inv.getArgument(0));

        ShadowItResponse out = service.sanction(ROW_EX, new ShadowItSanctionRequest(777L, "vendor onboarded"));

        assertThat(out.status()).isEqualTo("SANCTIONED");
        assertThat(out.sanctionedVendorId()).isEqualTo(777L);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SHADOW_IT_SANCTIONED");
        assertThat(cap.getValue().payload()).containsEntry("sanctionedVendorId", 777L);
    }

    @Test
    void sanction_requiresVendorId() {
        assertThatThrownBy(() -> service.sanction(ROW_EX, new ShadowItSanctionRequest(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Block ────────────────────────────────────────────────────

    @Test
    void block_isTerminal() {
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ROW_EX))
                .thenReturn(Optional.of(row("IN_PROGRESS", false)));
        when(repo.save(any(AttackSurfaceShadowIt.class))).thenAnswer(inv -> inv.getArgument(0));

        ShadowItResponse out = service.block(ROW_EX, new ShadowItBlockRequest("network blackhole"));

        assertThat(out.status()).isEqualTo("BLOCKED");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("SHADOW_IT_BLOCKED");
    }

    @Test
    void block_thenSanction_rejected() {
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ROW_EX))
                .thenReturn(Optional.of(row("BLOCKED", false)));
        assertThatThrownBy(() -> service.sanction(ROW_EX, new ShadowItSanctionRequest(99L, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be sanctioned");
    }

    @Test
    void notApplicable_terminal() {
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ROW_EX))
                .thenReturn(Optional.of(row("PENDING_REVIEW", false)));
        when(repo.save(any(AttackSurfaceShadowIt.class))).thenAnswer(inv -> inv.getArgument(0));

        ShadowItResponse out = service.markNotApplicable(ROW_EX, "false positive");
        assertThat(out.status()).isEqualTo("NA");
    }
}
