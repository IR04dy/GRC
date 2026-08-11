package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WF-4 — {@link WorkflowEvidenceBridge}: an anchored evidence outcome drives the linked workflow
 * request (APPROVED → APPROVED, REJECTED → RETURNED, via SUBMITTED where the machine needs it);
 * non-anchored events and terminal requests are ignored.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEvidenceBridgeTest {

    private static final Long ORG = 1L;

    @Mock private WorkflowRequestRepository      requestRepo;
    @Mock private WorkflowRequestEventRepository eventRepo;
    @Mock private NotificationService            notifications;
    @Mock private AuditLogService                audit;
    @InjectMocks private WorkflowEvidenceBridge  bridge;

    @BeforeEach
    void stubSaves() {
        lenient().when(requestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private WorkflowRequest request(Long id, String status) {
        WorkflowRequest r = new WorkflowRequest();
        r.setId(id);
        r.setOrganisationId(ORG);
        r.setSourceModule("RISK");
        r.setRequestType("EVIDENCE_REQUEST");
        r.setTitleEn("t");
        r.setStatus(status);
        r.setInitiatedBy(1L);
        return r;
    }

    private DomainEvent evt(String trigger, Long anchorId) {
        return new DomainEvent(ORG, trigger, "EvidenceRequest", 99L, "evreq:99",
            Map.of("anchorType", "WORKFLOW_REQUEST", "anchorId", anchorId, "requestCode", "REQ-00099"));
    }

    @Test
    void approvedEvidence_drivesOpenRequestToApproved() {
        WorkflowRequest r = request(5L, "IN_PROGRESS");
        when(requestRepo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(r));

        bridge.onEvidenceOutcome(evt("EVIDENCE_REQUEST_APPROVED", 5L));

        assertThat(r.getStatus()).isEqualTo("APPROVED");
        // F2-190: TWO events now, and the change is the point. IN_PROGRESS -> APPROVED is not in
        // WorkflowRequestService.TRANSITIONS, so the machine requires a hop via SUBMITTED. The bridge
        // always intended that ("pre-submit states hop via SUBMITTED first") but implemented it as two
        // consecutive setStatus calls, which collapse — so the row never rested in SUBMITTED and no
        // event recorded it. This assertion read `save(any())` (Mockito's exactly-once) and so pinned
        // the collapsed behaviour as correct.
        verify(eventRepo, org.mockito.Mockito.times(2)).save(any());
        verify(notifications).notifyUser(any(), any(), any(), any(), any(), any(), any(), any());
        verify(audit).log(any(), any(), any(), any());
    }

    @Test
    void rejectedEvidence_drivesRequestToReturned() {
        WorkflowRequest r = request(6L, "SENT");
        when(requestRepo.findByIdAndOrganisationId(6L, ORG)).thenReturn(Optional.of(r));

        bridge.onEvidenceOutcome(evt("EVIDENCE_REQUEST_REJECTED", 6L));

        assertThat(r.getStatus()).isEqualTo("RETURNED");
    }

    @Test
    void terminalRequest_ignored() {
        WorkflowRequest r = request(7L, "CLOSED");
        when(requestRepo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(r));

        bridge.onEvidenceOutcome(evt("EVIDENCE_REQUEST_APPROVED", 7L));

        assertThat(r.getStatus()).isEqualTo("CLOSED");
        verify(requestRepo, never()).save(any());
    }

    @Test
    void nonWorkflowAnchor_ignored() {
        bridge.onEvidenceOutcome(new DomainEvent(ORG, "EVIDENCE_REQUEST_APPROVED", "EvidenceRequest",
            99L, null, Map.of("anchorType", "BCM_PLAN", "anchorId", 5L)));
        verify(requestRepo, never()).findByIdAndOrganisationId(any(), any());
    }

    @Test
    void unrelatedTrigger_ignored() {
        bridge.onEvidenceOutcome(DomainEvent.of(ORG, "INCIDENT_CREATED", "Incident", 1L, Map.of()));
        verify(requestRepo, never()).findByIdAndOrganisationId(any(), any());
    }
}
