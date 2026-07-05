package com.tss.grc.module.cyber.service;

import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import com.tss.grc.module.evidence.dto.EvidenceRequestCreateDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestResponse;
import com.tss.grc.module.evidence.service.EvidenceRequestService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CY-J — {@link CyberEvidenceExpiryScheduler}. An expired live evidence row is flipped to STALE
 * (with staleSince stamped), an evidence re-request is raised through the shared subsystem anchored
 * to the evidence, and the new request id is recorded on the row.
 */
@ExtendWith(MockitoExtension.class)
class CyberEvidenceExpirySchedulerTest {

    private static final Long ORG = 1L;

    @Mock private CyberEvidenceRepository   evidenceRepo;
    @Mock private EvidenceRequestService    evidenceRequests;
    @Mock private NotificationService       notifications;
    @Mock private AuditLogService           audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private CyberEvidenceExpiryScheduler scheduler;

    private static CyberEvidence evidence(Long id, String status, LocalDate expiry) {
        CyberEvidence e = new CyberEvidence();
        e.setId(id);
        e.setOrganisationId(ORG);
        e.setTitleEn("Firewall ruleset export");
        e.setStatus(status);
        e.setExpiryDate(expiry);
        return e;
    }

    private static EvidenceRequestResponse resp(Long id) {
        return new EvidenceRequestResponse(id, java.util.UUID.randomUUID(),
            "REQ-" + String.format("%05d", id), null, null, null, null,
            "CYBER_EVIDENCE", 10L, "Re-collect", null, null, null, null, null, null, null, null,
            LocalDate.now().plusDays(14), "MEDIUM", "OPEN", null, null, null);
    }

    @Test
    void sweep_expiresLiveEvidence_flipsStale_raisesReRequest_recordsId() {
        CyberEvidence ev = evidence(10L, "CURRENT", LocalDate.now().minusDays(3));
        when(evidenceRepo.findByStatusInAndExpiryDateBefore(any(), any())).thenReturn(List.of(ev));
        when(evidenceRequests.create(any(), isNull())).thenReturn(resp(99L));

        int expired = scheduler.sweep();

        assertThat(expired).isEqualTo(1);
        // parent flipped to STALE with bookkeeping
        assertThat(ev.getStatus()).isEqualTo("STALE");
        assertThat(ev.getStaleSince()).isEqualTo(LocalDate.now());
        assertThat(ev.getReRequestId()).isEqualTo(99L);
        verify(evidenceRepo).save(ev);

        // re-request raised, anchored to the evidence (CY-A convention)
        ArgumentCaptor<EvidenceRequestCreateDto> cap = ArgumentCaptor.forClass(EvidenceRequestCreateDto.class);
        verify(evidenceRequests).create(cap.capture(), isNull());
        EvidenceRequestCreateDto dto = cap.getValue();
        assertThat(dto.entityType()).isEqualTo("CYBER_EVIDENCE");
        assertThat(dto.entityId()).isEqualTo(10L);
        assertThat(dto.dueDate()).isEqualTo(LocalDate.now().plusDays(14));

        verify(audit).log(eq("CYBER_EVIDENCE_EXPIRED"), eq("CyberEvidence"), eq(10L), anyString());
        verify(notifications).broadcastToRole(eq(ORG), eq("SECURITY_ANALYST"), anyString(), anyString(),
            anyString(), eq("CYBER_EVIDENCE_EXPIRED"), eq("CyberEvidence"), eq(10L));
        verify(publisher).publishEvent(any(DomainEvent.class));
    }

    @Test
    void sweep_noExpired_returnsZero_noReRequest() {
        when(evidenceRepo.findByStatusInAndExpiryDateBefore(any(), any())).thenReturn(List.of());
        int expired = scheduler.sweep();
        assertThat(expired).isZero();
        verify(evidenceRequests, never()).create(any(), any());
        verify(evidenceRepo, never()).save(any());
    }
}
