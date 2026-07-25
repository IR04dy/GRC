package com.tss.grc.module.consumer.complaints.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AcknowledgeRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AssignRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.EscalateToSamaRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.PublicTrackingResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.TransitionRequest;
import com.tss.grc.module.consumer.complaints.entity.Complaint;
import com.tss.grc.module.consumer.complaints.entity.ComplaintUpdate;
import com.tss.grc.module.consumer.complaints.repository.ComplaintAggregateReportRepository;
import com.tss.grc.module.consumer.complaints.repository.ComplaintRepository;
import com.tss.grc.module.consumer.complaints.repository.ComplaintUpdateRepository;
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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ComplaintService} — Sprint 13.
 *
 * <p>Covers:
 * <ul>
 *   <li>Public submission generates a token + computes 5/20 working-day SLA</li>
 *   <li>Acknowledge transition fires F8 COMPLAINT_ACKNOWLEDGED</li>
 *   <li>Illegal transitions raise IllegalTransitionException</li>
 *   <li>Escalation to SAMA persists reference + fires F8</li>
 *   <li>Public tracking returns a PII-stripped response (no phone/email/ID/account)</li>
 *   <li>Complaint-no sequence parser handles fresh + existing rows</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private ComplaintRepository complaintRepo;
    @Mock private ComplaintUpdateRepository updateRepo;
    @Mock private ComplaintAggregateReportRepository reportRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private ComplaintService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Fixtures ──────────────────────────────────────────────────

    private static SubmitComplaintRequest publicReq() {
        return new SubmitComplaintRequest(
                "WEB", "FRAUD_UNAUTHORIZED_TXN", null, "HIGH",
                "Khalid Al-Mutairi", "خالد المطيري",
                "NATIONAL_ID", "4823", "+966501112233", "k@example.sa", "Riyadh",
                null, null,
                "Unauthorized card txn in Turkey",
                "عملية غير مصرّح بها في تركيا"
        );
    }

    private static Complaint existing(Long id, String status) {
        Complaint c = new Complaint();
        c.setId(id);
        c.setOrganisationId(ORG_ID);
        c.setComplaintNo("CMP-2026-" + String.format("%06d", id));
        c.setSubmissionToken(UUID.randomUUID());
        c.setStatus(status);
        c.setChannel("WEB");
        c.setCategory("FRAUD_UNAUTHORIZED_TXN");
        c.setSeverity("HIGH");
        c.setSubmittedOn(LocalDateTime.now().minusDays(2));
        c.setCustomerNameEn("Khalid Al-Mutairi");
        c.setCustomerNameAr("خالد المطيري");
        c.setCustomerIdLast4("4823");
        c.setCustomerPhone("+966500000000");
        c.setCustomerEmail("k@example.sa");
        c.setAccountLast4("9999");
        c.setCompensationAmountSar(BigDecimal.ZERO);
        return c;
    }

    // ─── Public submission ─────────────────────────────────────────

    @Test
    void submitPublicComplaint_generatesTokenAndComputesSlaDates() {
        when(complaintRepo.findTopByOrderByComplaintNoDesc()).thenReturn(Optional.empty());
        when(complaintRepo.existsByComplaintNo(anyString())).thenReturn(false);
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> {
            Complaint c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        // Captured BEFORE the call: the service stamps its own LocalDateTime.now(),
        // so a `now` taken afterwards is strictly later and erodes the measured
        // span by a few millis. With Duration.toDays() truncating, that turned the
        // tightest real case into a false failure — starting on a KSA weekend day
        // (Fri/Sat) five business days lands Sun..Thu, i.e. exactly 5 calendar
        // days, which measured as 4d 23h 59m -> 4. The test therefore failed only
        // when it happened to run on a Saturday. Compare whole calendar days from
        // a pre-call instant instead: intra-day noise cannot affect the result.
        LocalDate startedOn = LocalDate.now();

        SubmitComplaintResponse resp = service.submitPublicComplaint(publicReq());

        assertThat(resp.complaintNo()).matches("CMP-\\d{4}-000001");
        assertThat(resp.submissionToken()).isNotNull();
        assertThat(resp.ackSlaDueOn()).isNotNull();
        assertThat(resp.resolutionSlaDueOn()).isNotNull();
        assertThat(resp.ackSlaDueOn()).isBefore(resp.resolutionSlaDueOn());

        // The acknowledgement SLA must be at least 5 calendar days out
        // (5 business days >= 5 calendar days) and the resolution SLA
        // at least 20 calendar days out — guards against the JDK
        // Sat/Sun default sneaking in (which would understate the SLA).
        assertThat(ChronoUnit.DAYS.between(startedOn, resp.ackSlaDueOn().toLocalDate()))
                .isGreaterThanOrEqualTo(5);
        assertThat(ChronoUnit.DAYS.between(startedOn, resp.resolutionSlaDueOn().toLocalDate()))
                .isGreaterThanOrEqualTo(20);

        // KSA weekend must never be an SLA due date (the guard the durations
        // above were reaching for, asserted directly).
        assertThat(resp.ackSlaDueOn().getDayOfWeek())
                .isNotIn(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        assertThat(resp.resolutionSlaDueOn().getDayOfWeek())
                .isNotIn(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("COMPLAINT_RECEIVED");

        verify(audit).log(eq("CREATE"), eq("Complaint"), eq(100L), anyString());
    }

    @Test
    void submitPublicComplaint_falsbackToDefaultOrgWhenNoTenantContext() {
        TenantContext.clear();
        when(complaintRepo.findTopByOrderByComplaintNoDesc()).thenReturn(Optional.empty());
        when(complaintRepo.existsByComplaintNo(anyString())).thenReturn(false);
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> {
            Complaint c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        service.submitPublicComplaint(publicReq());

        // Verify the saved row had org_id = 1 (PUBLIC_PORTAL_DEFAULT_ORG).
        ArgumentCaptor<Complaint> saved = ArgumentCaptor.forClass(Complaint.class);
        verify(complaintRepo).save(saved.capture());
        assertThat(saved.getValue().getOrganisationId())
                .isEqualTo(ComplaintService.PUBLIC_PORTAL_DEFAULT_ORG);
    }

    @Test
    void submitPublicComplaint_parsesPreviousSequenceAndIncrements() {
        Complaint prev = new Complaint();
        prev.setComplaintNo("CMP-2026-000042");
        when(complaintRepo.findTopByOrderByComplaintNoDesc()).thenReturn(Optional.of(prev));
        when(complaintRepo.existsByComplaintNo(anyString())).thenReturn(false);
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> {
            Complaint c = inv.getArgument(0);
            c.setId(43L);
            return c;
        });

        SubmitComplaintResponse resp = service.submitPublicComplaint(publicReq());

        assertThat(resp.complaintNo()).endsWith("-000043");
    }

    // ─── Acknowledge ──────────────────────────────────────────────

    @Test
    void acknowledge_fromReceived_setsAckTimestampAndEmitsF8() {
        Complaint c = existing(11L, "RECEIVED");
        when(complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 11L))
                .thenReturn(Optional.of(c));
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse resp = service.acknowledge(11L, new AcknowledgeRequest(null, null));

        assertThat(resp.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(resp.acknowledgedOn()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("COMPLAINT_ACKNOWLEDGED");

        // A STATUS_CHANGE timeline row must be persisted, customer-visible.
        ArgumentCaptor<ComplaintUpdate> upd = ArgumentCaptor.forClass(ComplaintUpdate.class);
        verify(updateRepo).save(upd.capture());
        assertThat(upd.getValue().getUpdateType()).isEqualTo("STATUS_CHANGE");
        assertThat(upd.getValue().getToStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(upd.getValue().getCustomerVisible()).isTrue();
    }

    // ─── Transitions ──────────────────────────────────────────────

    @Test
    void transition_invalidPath_throwsIllegalTransition() {
        // RESOLVED → ACKNOWLEDGED is NOT allowed.
        Complaint c = existing(12L, "RESOLVED");
        when(complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 12L))
                .thenReturn(Optional.of(c));

        TransitionRequest req = new TransitionRequest(
                "ACKNOWLEDGED", "note", "ملاحظة", true,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.transition(12L, req))
                .isInstanceOf(IllegalTransitionException.class);

        verify(complaintRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void transition_toResolved_capturesResolutionFields_andEmitsResolvedEvent() {
        Complaint c = existing(13L, "UNDER_INVESTIGATION");
        when(complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 13L))
                .thenReturn(Optional.of(c));
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        TransitionRequest req = new TransitionRequest(
                "RESOLVED",
                "Resolved per investigation findings.",
                "تمّ الحلّ وفق نتائج التحقّق.",
                true,
                "Refund issued, new card sent.",
                "تمّ الاسترداد وإصدار بطاقة جديدة.",
                new BigDecimal("4200.00"),
                "MERCHANT_FRAUD",
                "Strengthen 3DS rules for cross-border merchants.");

        ComplaintResponse resp = service.transition(13L, req);

        assertThat(resp.status()).isEqualTo("RESOLVED");
        assertThat(resp.resolvedOn()).isNotNull();
        assertThat(resp.compensationAmountSar()).isEqualByComparingTo("4200.00");
        assertThat(resp.rootCauseCode()).isEqualTo("MERCHANT_FRAUD");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("COMPLAINT_TRANSITIONED", "COMPLAINT_RESOLVED");
    }

    @Test
    void transition_closedToReopened_emitsReopenedEvent() {
        Complaint c = existing(14L, "CLOSED");
        when(complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 14L))
                .thenReturn(Optional.of(c));
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transition(14L, new TransitionRequest("REOPENED", "Customer follow-up", "متابعة", true,
                null, null, null, null, null));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("COMPLAINT_REOPENED");
    }

    // ─── Escalation ───────────────────────────────────────────────

    @Test
    void escalateToSama_setsRefAndFiresEscalationEvent() {
        Complaint c = existing(15L, "UNDER_INVESTIGATION");
        when(complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 15L))
                .thenReturn(Optional.of(c));
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse resp = service.escalateToSama(15L,
                new EscalateToSamaRequest("SAMA-CC-2026-9911", null, null));

        assertThat(resp.status()).isEqualTo("ESCALATED_TO_SAMA");
        assertThat(resp.samaReferenceNo()).isEqualTo("SAMA-CC-2026-9911");
        assertThat(resp.escalatedToSamaOn()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("COMPLAINT_ESCALATED_TO_SAMA");

        // The SAMA notification timeline row must be customer-visible.
        ArgumentCaptor<ComplaintUpdate> upd = ArgumentCaptor.forClass(ComplaintUpdate.class);
        verify(updateRepo).save(upd.capture());
        assertThat(upd.getValue().getUpdateType()).isEqualTo("SAMA_NOTIFICATION");
        assertThat(upd.getValue().getCustomerVisible()).isTrue();
    }

    // ─── Assignment ──────────────────────────────────────────────

    @Test
    void assign_setsAssignee_andEmitsAssignedEvent() {
        Complaint c = existing(16L, "ACKNOWLEDGED");
        when(complaintRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 16L))
                .thenReturn(Optional.of(c));
        when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplaintResponse resp = service.assign(16L, new AssignRequest("fraud.investigator.1"));

        assertThat(resp.assignedTo()).isEqualTo("fraud.investigator.1");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("COMPLAINT_ASSIGNED");
    }

    // ─── Public tracking — PII sanitisation ───────────────────────

    @Test
    void trackByToken_returnsSanitisedResponseWithoutPii() {
        Complaint c = existing(17L, "UNDER_INVESTIGATION");
        UUID token = c.getSubmissionToken();
        when(complaintRepo.findBySubmissionToken(token)).thenReturn(Optional.of(c));

        // The only timeline row is INTERNAL_NOTE (not customer-visible);
        // the repository call we mock returns just customer-visible rows.
        ComplaintUpdate visible = new ComplaintUpdate();
        visible.setComplaintId(17L);
        visible.setUpdateType("STATUS_CHANGE");
        visible.setToStatus("UNDER_INVESTIGATION");
        visible.setNoteEn("We have started investigating.");
        visible.setNoteAr("بدأنا التحقق.");
        visible.setCustomerVisible(true);
        visible.setOccurredOn(LocalDateTime.now());
        when(updateRepo.findByComplaintIdAndCustomerVisibleTrueOrderByOccurredOnAsc(17L))
                .thenReturn(List.of(visible));

        PublicTrackingResponse resp = service.trackByToken(token);

        // What's INCLUDED.
        assertThat(resp.complaintNo()).isEqualTo(c.getComplaintNo());
        assertThat(resp.status()).isEqualTo("UNDER_INVESTIGATION");
        assertThat(resp.severity()).isEqualTo("HIGH");
        assertThat(resp.customerInitials()).isNotNull();   // initials of "Khalid Al-Mutairi"
        assertThat(resp.timeline()).hasSize(1);
        assertThat(resp.timeline().get(0).noteAr()).isEqualTo("بدأنا التحقق.");

        // What's EXCLUDED — PII fields are intentionally not on the record.
        // Spot-check that none of the public-tracking record component
        // names contain PII tokens.
        Class<?> recordClass = resp.getClass();
        java.util.List<String> componentNames = java.util.Arrays.stream(recordClass.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertThat(componentNames).noneMatch(n -> n.toLowerCase().contains("phone"));
        assertThat(componentNames).noneMatch(n -> n.toLowerCase().contains("email"));
        assertThat(componentNames).noneMatch(n -> n.toLowerCase().contains("idtype"));
        assertThat(componentNames).noneMatch(n -> n.toLowerCase().contains("idlast"));
        assertThat(componentNames).noneMatch(n -> n.toLowerCase().contains("accountlast"));
        // The full customer name field also must not leak — only "customerInitials" exists.
        assertThat(componentNames).noneMatch(n -> n.equalsIgnoreCase("customerName")
                || n.equalsIgnoreCase("customerNameEn")
                || n.equalsIgnoreCase("customerNameAr"));
    }
}
