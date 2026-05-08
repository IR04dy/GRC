package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.repository.DataBreachRepository;
import com.tss.grc.module.privacy.repository.DataSubjectRequestRepository;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrivacyService — Sprint 4 §4.1 test backlog.
 *
 * Locks the Saudi PDPL contracts:
 *
 * 1. Data-breach lifecycle (PDPL Article 20 — 72-hour notification window):
 *      OPEN → INVESTIGATING → CLOSED
 *      OPEN → CLOSED  (allowed shortcut for trivial false-positives)
 *      notifyDataOffice() flips pdpcNotificationRequired=true and stamps pdpcNotifiedAt.
 *
 * 2. Data-subject-request lifecycle (PDPL — 30-day fulfilment SLA):
 *      OPEN/PENDING → IN_PROGRESS → FULFILLED | REJECTED
 *      startDsr() defaults dueDate = receivedAt + 30 days when blank.
 *      extendDsr() bumps the due date by N (default 30) days.
 *      Terminal states (FULFILLED, REJECTED) cannot be re-transitioned.
 *
 * 3. Tenant scope on every read/write — cross-org → 404.
 */
@ExtendWith(MockitoExtension.class)
class PrivacyServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long ID        = 50L;

    @Mock private DataBreachRepository breachRepo;
    @Mock private DataSubjectRequestRepository dsrRepo;
    @Mock private ProcessingActivityRepository ropaRepo;
    @InjectMocks private PrivacyService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static DataBreach breach(String status, Long orgId) {
        DataBreach b = new DataBreach();
        b.setId(ID);
        b.setOrganisationId(orgId);
        b.setTitleEn("Customer email leak");
        b.setStatus(status);
        return b;
    }
    private static DataBreach breach(String status) { return breach(status, ORG_ID); }

    private static DataSubjectRequest dsr(String status, Long orgId) {
        DataSubjectRequest r = new DataSubjectRequest();
        r.setId(ID);
        r.setOrganisationId(orgId);
        r.setRequestType("ACCESS");
        r.setRequesterName("Khalid");
        r.setStatus(status);
        return r;
    }
    private static DataSubjectRequest dsr(String status) { return dsr(status, ORG_ID); }

    // ─── Breach lifecycle ────────────────────────────────────────────

    @Test
    void containBreach_OPEN_to_INVESTIGATING() {
        DataBreach b = breach("OPEN");
        when(breachRepo.findById(ID)).thenReturn(Optional.of(b));
        when(breachRepo.save(any(DataBreach.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.containBreach(ID).getStatus()).isEqualTo("INVESTIGATING");
    }

    @Test
    void containBreach_from_CLOSED_throws() {
        when(breachRepo.findById(ID)).thenReturn(Optional.of(breach("CLOSED")));

        assertThatThrownBy(() -> service.containBreach(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OPEN");
    }

    @Test
    void closeBreach_from_OPEN_or_INVESTIGATING() {
        DataBreach a = breach("OPEN");
        when(breachRepo.findById(ID)).thenReturn(Optional.of(a));
        when(breachRepo.save(any(DataBreach.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.closeBreach(ID).getStatus()).isEqualTo("CLOSED");

        DataBreach b = breach("INVESTIGATING");
        when(breachRepo.findById(ID)).thenReturn(Optional.of(b));
        assertThat(service.closeBreach(ID).getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void closeBreach_from_CLOSED_throws() {
        when(breachRepo.findById(ID)).thenReturn(Optional.of(breach("CLOSED")));

        assertThatThrownBy(() -> service.closeBreach(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("OPEN or INVESTIGATING");
    }

    @Test
    void notifyDataOffice_sets_required_flag_and_stamps_notifiedAt() {
        // PDPL Article 20: regulator notification is mandatory for high-risk breaches
        DataBreach b = breach("INVESTIGATING");
        b.setPdpcNotificationRequired(false);
        b.setPdpcNotifiedAt(null);
        when(breachRepo.findById(ID)).thenReturn(Optional.of(b));
        when(breachRepo.save(any(DataBreach.class))).thenAnswer(inv -> inv.getArgument(0));

        DataBreach after = service.notifyDataOffice(ID);
        assertThat(after.getPdpcNotificationRequired()).isTrue();
        assertThat(after.getPdpcNotifiedAt()).isNotNull();
    }

    @Test
    void getBreach_other_tenant_throws_EntityNotFound() {
        when(breachRepo.findById(ID)).thenReturn(Optional.of(breach("OPEN", OTHER_ORG)));

        assertThatThrownBy(() -> service.getBreach(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Breach not found");
    }

    @Test
    void deleteBreach_soft_deletes_via_save() {
        DataBreach b = breach("CLOSED");
        when(breachRepo.findById(ID)).thenReturn(Optional.of(b));
        when(breachRepo.save(any(DataBreach.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteBreach(ID);
        assertThat(b.getIsDeleted()).isTrue();
        verify(breachRepo).save(b);
        verify(breachRepo, never()).deleteById(any());
    }

    // ─── DSR lifecycle ───────────────────────────────────────────────

    @Test
    void startDsr_defaults_dueDate_to_receivedAt_plus_30_days_when_blank() {
        DataSubjectRequest r = dsr("OPEN");
        LocalDateTime received = LocalDateTime.of(2026, 4, 1, 10, 0);
        r.setReceivedAt(received);
        r.setDueDate(null);
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DataSubjectRequest after = service.startDsr(ID);
        assertThat(after.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(after.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void startDsr_does_not_overwrite_existing_dueDate() {
        DataSubjectRequest r = dsr("OPEN");
        r.setReceivedAt(LocalDateTime.of(2026, 4, 1, 10, 0));
        LocalDate explicit = LocalDate.of(2026, 6, 15);
        r.setDueDate(explicit);
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.startDsr(ID);
        assertThat(r.getDueDate()).isEqualTo(explicit);
    }

    @Test
    void startDsr_when_receivedAt_is_null_uses_today_plus_30() {
        // Defensive: receivedAt should always be set, but if null we default to now()+30
        DataSubjectRequest r = dsr("OPEN");
        r.setReceivedAt(null);
        r.setDueDate(null);
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.startDsr(ID);
        assertThat(r.getDueDate()).isNotNull();
        assertThat(r.getDueDate()).isAfterOrEqualTo(LocalDate.now().plusDays(29));
        assertThat(r.getDueDate()).isBeforeOrEqualTo(LocalDate.now().plusDays(31));
    }

    @Test
    void startDsr_already_FULFILLED_throws() {
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr("FULFILLED")));

        assertThatThrownBy(() -> service.startDsr(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already terminal");
    }

    @Test
    void startDsr_already_REJECTED_throws() {
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr("REJECTED")));

        assertThatThrownBy(() -> service.startDsr(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already terminal");
    }

    @Test
    void fulfilDsr_sets_FULFILLED_and_stamps_completedAt() {
        DataSubjectRequest r = dsr("IN_PROGRESS");
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DataSubjectRequest after = service.fulfilDsr(ID);
        assertThat(after.getStatus()).isEqualTo("FULFILLED");
        assertThat(after.getCompletedAt()).isNotNull();
    }

    @Test
    void fulfilDsr_already_terminal_throws() {
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr("FULFILLED")));

        assertThatThrownBy(() -> service.fulfilDsr(ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectDsr_sets_REJECTED_and_stamps_completedAt() {
        DataSubjectRequest r = dsr("IN_PROGRESS");
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DataSubjectRequest after = service.rejectDsr(ID);
        assertThat(after.getStatus()).isEqualTo("REJECTED");
        assertThat(after.getCompletedAt()).isNotNull();
    }

    // ─── DSR extension (PDPL one-time extension allowance) ──────────

    @Test
    void extendDsr_default_extension_is_30_days_from_existing_dueDate() {
        DataSubjectRequest r = dsr("IN_PROGRESS");
        r.setDueDate(LocalDate.of(2026, 5, 1));
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DataSubjectRequest after = service.extendDsr(ID, null);   // null → default 30
        assertThat(after.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void extendDsr_zero_or_negative_falls_back_to_30() {
        DataSubjectRequest r = dsr("IN_PROGRESS");
        r.setDueDate(LocalDate.of(2026, 5, 1));
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.extendDsr(ID, 0);
        assertThat(r.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 31));

        r.setDueDate(LocalDate.of(2026, 5, 1));   // reset
        service.extendDsr(ID, -5);
        assertThat(r.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void extendDsr_with_explicit_days_uses_caller_value() {
        DataSubjectRequest r = dsr("IN_PROGRESS");
        r.setDueDate(LocalDate.of(2026, 5, 1));
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.extendDsr(ID, 14);
        assertThat(r.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void extendDsr_with_null_dueDate_extends_from_today() {
        DataSubjectRequest r = dsr("IN_PROGRESS");
        r.setDueDate(null);
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
        when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        service.extendDsr(ID, 7);
        assertThat(r.getDueDate()).isAfterOrEqualTo(LocalDate.now().plusDays(6));
        assertThat(r.getDueDate()).isBeforeOrEqualTo(LocalDate.now().plusDays(8));
    }

    // ─── Tenant scope ────────────────────────────────────────────────

    @Test
    void getDsr_other_tenant_throws_EntityNotFound() {
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr("OPEN", OTHER_ORG)));

        assertThatThrownBy(() -> service.getDsr(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("DSR not found");
    }

    @Test
    void fulfilDsr_other_tenant_throws_EntityNotFound() {
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr("IN_PROGRESS", OTHER_ORG)));

        assertThatThrownBy(() -> service.fulfilDsr(ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(dsrRepo, never()).save(any());
    }
}
