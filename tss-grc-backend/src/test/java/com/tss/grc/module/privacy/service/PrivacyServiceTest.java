package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.DsrRequestLifecycle;
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
import static org.mockito.ArgumentMatchers.eq;
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
 *      Terminal states cannot be re-transitioned. F2-196: that used to read
 *      "(FULFILLED, REJECTED)" — the two the guards knew — while this register in fact
 *      holds RESOLVED and COMPLETED, and holds FULFILLED in none of its 20 rows. The
 *      authority is now {@link DsrRequestLifecycle#TERMINAL}; do not restate it here.
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
    // F2-458: getStats() reads all five repositories, so the two the lifecycle legs never
    // needed have to be mocked for it to run at all. An unstubbed count defaults to 0, which
    // is what keeps each leg's arithmetic readable.
    @Mock private com.tss.grc.module.privacy.repository.DpiaRepository dpiaRepo;
    @Mock private com.tss.grc.module.privacy.repository.ConsentRecordRepository consentRepo;
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

    /**
     * F2-196 — RESOLVED and COMPLETED are terminal too, and this register holds 7 such rows.
     *
     * <p>The two tests above cover exactly the two tokens the implementation knew about —
     * written by reading the guard, so they could only agree with it. Meanwhile
     * {@code FULFILLED} appears in 0 of the 20 real rows in dev and prod, and RESOLVED (×6) /
     * COMPLETED (×1) do. So the guard admitted every finished request in the database: a
     * completed DSR could be re-started, re-fulfilled, and have its {@code completedAt}
     * overwritten — losing the timestamp that evidences PDPL Article 23 compliance.
     */
    @Test
    void dsrGuards_treat_every_finished_status_this_register_holds_as_terminal() {
        for (String finished : DsrRequestLifecycle.TERMINAL) {
            when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr(finished)));

            assertThatThrownBy(() -> service.startDsr(ID))
                .as("startDsr must refuse a %s request", finished)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already terminal");
            assertThatThrownBy(() -> service.fulfilDsr(ID))
                .as("fulfilDsr must refuse a %s request — re-fulfilling overwrites completedAt", finished)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already terminal");
            assertThatThrownBy(() -> service.rejectDsr(ID))
                .as("rejectDsr must refuse a %s request", finished)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already terminal");
        }
        // ...and the lower-case form too: the column is CI-collated with no CHECK, so a data
        // fix or a machine caller can store 'resolved' and a bare .equals would miss it.
        when(dsrRepo.findById(ID)).thenReturn(Optional.of(dsr("resolved")));
        assertThatThrownBy(() -> service.fulfilDsr(ID))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already terminal");
    }

    @Test
    void dsrGuards_still_admit_every_live_status_including_the_intake_adapters_OPEN() {
        // The complement direction: these must NOT be refused. ESCALATED and OPEN are the two
        // the frontend's open count used to drop, and OPEN is what DsrPromotionAdapter writes.
        for (String live : java.util.List.of("NEW", "PENDING", "OPEN", "IN_PROGRESS", "ESCALATED")) {
            DataSubjectRequest r = dsr(live);
            when(dsrRepo.findById(ID)).thenReturn(Optional.of(r));
            when(dsrRepo.save(any(DataSubjectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.fulfilDsr(ID).getStatus())
                .as("%s is a live status; fulfilling it must be allowed", live)
                .isEqualTo("FULFILLED");
        }
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
    // ─── PDPL dashboard figures (F2-458) ─────────────────────────────
    //
    // getStats() had NO test of any kind before this (F2-248: a path with no test is not a
    // path somebody decided was safe), and four of its keys asserted a statutory condition
    // the arithmetic behind them did not measure:
    //
    //   dsrSlaBreaches       = count(status = ESCALATED)      -> now open AND past due_date
    //   breachNotifications  = count(status = NOTIFIED)       -> now SDAIA-discharged
    //   processorsWithoutDpa = literal 0L                     -> not published at all
    //   retentionViolations  = literal 0L                     -> not published at all
    //
    // These legs are BEHAVIOURAL rather than structural, and that is forced: a source scan
    // cannot tell a re-added `m.put("dsrSlaBreaches", dsrEscalated)` from the comment that
    // quotes it, and this fix quotes every retired expression on purpose (F2-450). The only
    // assertion that discriminates is what the map actually contains.

    /** Enough stubbing for getStats() to run; every unstubbed count defaults to 0. */
    private void stubRopaReadsForStats() {
        when(ropaRepo.findByOrganisationId(ORG_ID, org.springframework.data.domain.Pageable.unpaged()))
            .thenReturn(org.springframework.data.domain.Page.empty());
    }

    @Test
    void stats_dsrSlaBreaches_countsOpenRequestsPastTheirDeadline_notTheEscalatedOnes() {
        stubRopaReadsForStats();
        when(dsrRepo.countByOrganisationIdAndStatusNotIn(ORG_ID, DsrRequestLifecycle.TERMINAL_LIST))
            .thenReturn(12L);
        when(dsrRepo.countByOrganisationIdAndStatusNotInAndDueDateBefore(
                eq(ORG_ID), eq(DsrRequestLifecycle.TERMINAL_LIST), any(LocalDate.class)))
            .thenReturn(12L);

        assertThat(service.getStats()).as("""
            `dsrSlaBreaches` must be the count of OPEN requests past their recorded statutory \
            deadline, which is what the key is named for and what the dashboard renders beside a \
            gauge captioned "within SLA".

            It used to be count(status = ESCALATED). Escalation is what an operator does when \
            somebody NOTICES a request is late; the register carries the deadline itself, so the \
            proxy was measurable against the real thing and diverged badly. Measured 2026-09-04, \
            byte-identical in dev and the deployed database: 12 of 12 open requests were past \
            their due_date (74 to 504 days over) and 2 were ESCALATED, so the gauge reported 83%% \
            within SLA where the honest answer is 0%%.""")
            .containsEntry("dsrOpen", 12L)
            .containsEntry("dsrSlaBreaches", 12L);

        // The old computation must be gone, not merely outvoted. This is the whole population:
        // the ESCALATED proxy was the service's only use of the single-status DSR count.
        verify(dsrRepo, never()).countByOrganisationIdAndStatus(any(), any());
    }

    @Test
    void stats_pastDueCount_isScopedToTheOpenSet_soItCanNeverExceedIt() {
        stubRopaReadsForStats();
        when(dsrRepo.countByOrganisationIdAndStatusNotIn(ORG_ID, DsrRequestLifecycle.TERMINAL_LIST))
            .thenReturn(5L);
        when(dsrRepo.countByOrganisationIdAndStatusNotInAndDueDateBefore(
                eq(ORG_ID), eq(DsrRequestLifecycle.TERMINAL_LIST), any(LocalDate.class)))
            .thenReturn(3L);

        service.getStats();

        org.mockito.ArgumentCaptor<java.util.List<String>> terminal =
            org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        org.mockito.ArgumentCaptor<LocalDate> asOf = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(dsrRepo).countByOrganisationIdAndStatusNotInAndDueDateBefore(
            eq(ORG_ID), terminal.capture(), asOf.capture());

        assertThat(terminal.getValue()).as("""
            The past-due count must AND the same open predicate the open count uses, so the \
            breached set is a subset of `dsrOpen` by construction.

            That relation is why the client no longer clamps: PdplDashboardPage used to compute \
            `Math.min(stats.dsrSlaBreaches ?? 0, open)`, a defence against two figures describing \
            different populations -- and the wrong defence, because it hid the divergence instead \
            of surfacing it (F2-457: a cap on a ratio's numerator is a bug report about the \
            numerator). If this list ever stops being the terminal set, the two figures are free \
            to disagree again and the clamp would have to come back.""")
            .isEqualTo(DsrRequestLifecycle.TERMINAL_LIST);
        assertThat(asOf.getValue()).as(
            "The deadline is compared against a date, not left open-ended.").isNotNull();
    }

    @Test
    void stats_breachNotifications_countsSdaiaSubmissions_notTheLifecycleStatus() {
        stubRopaReadsForStats();
        // Answered on the argument rather than stubbed per status: getStats() calls this with
        // seven different statuses to build incidents12m, and a single-status stub trips
        // strict stubbing. This also makes the incidents12m assertion below exact -- only
        // NOTIFIED contributes, so 4 can only have come from the lifecycle count.
        when(breachRepo.countByOrganisationIdAndStatus(eq(ORG_ID), any()))
            .thenAnswer(inv -> "NOTIFIED".equals(inv.getArgument(1)) ? 4L : 0L);
        when(breachRepo.countByOrganisationIdAndSdaiaNotificationStatusIn(
                ORG_ID, PrivacyService.SDAIA_DISCHARGED)).thenReturn(0L);

        java.util.Map<String, Long> m = service.getStats();

        assertThat(m).as("""
            "Regulator-notified" must be answered from `sdaia_notification_status`, the column \
            that records what was said to SDAIA -- never from the breach LIFECYCLE status, which \
            is a state an operator sets on the record itself.

            F2-209 measured that those two disagree and corrected the reader in \
            PdplBreachConsole; this is the reader it did not touch. Measured 2026-09-04, \
            byte-identical in both databases: 4 of 17 breaches are status NOTIFIED while all 17 \
            are sdaia_notification_status = NOT_REPORTED and sdaia_submitted_at is NULL on every \
            one -- so the dashboard told an operator four breaches had been reported to the \
            regulator when none had.""")
            .containsEntry("breachNotifications", 0L);

        // The boundary in the other direction: the lifecycle count is still a legitimate term of
        // "how many breaches exist", so it must keep feeding incidents12m. Dropping it there
        // would trade a false assurance for an under-count (F2-457's rule -- assert what a fix
        // must NOT change, or the next reader removes the rest of it).
        assertThat(m).as(
            "The lifecycle NOTIFIED count must still be one term of the incident total.")
            .containsEntry("privacyIncidents12m", 4L);
    }

    @Test
    void sdaiaDischarged_isTheTwoPostedStates_andASubsetOfTheVocabulary() {
        assertThat(PrivacyService.SDAIA_DISCHARGED).as("""
            IN_PREPARATION is progress, not discharge. The entity's own javadoc puts the discharge \
            at sdaia_submitted_at being set, and that stamp is written only on the way to \
            SUBMITTED or ACKNOWLEDGED -- so admitting IN_PREPARATION here would count a \
            notification somebody started six months ago and never sent as one that had left the \
            building. F2-209 drew exactly this distinction on the frontend (isSdaiaSubmitted vs \
            isSdaiaReported) and this is its server-side twin.""")
            .containsExactly("ACKNOWLEDGED", "SUBMITTED");

        assertThat(PrivacyService.SDAIA_STATES).as("""
            SDAIA_DISCHARGED must be a genuine subset of the vocabulary. A token one letter out \
            would match no stored row and read exactly like a tenant that has reported no \
            breaches (F2-177's one-letter-away trap). The static initialiser refuses it at \
            class-init; this leg proves the initialiser's premise rather than trusting it.""")
            .containsAll(PrivacyService.SDAIA_DISCHARGED);
    }

    @Test
    void stats_publishesNoFigureForAControlThisModuleDoesNotTrack() {
        stubRopaReadsForStats();

        assertThat(service.getStats()).as("""
            `processorsWithoutDpa` and `retentionViolations` were published as a literal 0L under \
            the comment "Not tracked in this module yet -- surfaced as 0 rather than absent", as \
            though 0 were the safer of the two. It is the opposite: PdplDashboardPage rendered \
            both with tone `> 0 ? danger : success`, so a control this module does not track at \
            all appeared as a GREEN tile asserting zero processors without a DPA and zero \
            retention violations.

            An invented figure invites a question; an invented all-clear closes one (F2-197). If \
            either control is ever really measured, publish it then -- and the surface will stop \
            rendering the em-dash on its own.""")
            .doesNotContainKey("processorsWithoutDpa")
            .doesNotContainKey("retentionViolations");
    }

    /**
     * The frontend half, asserted from here because nothing else can see it.
     *
     * <p>Reverting it re-declares the two retired fields on {@code PrivacyStatsShape}, {@code tsc}
     * is satisfied because the server's map is untyped on the wire, and the panels interpolate
     * {@code undefined} into a green Badge with nothing in either suite to notice. The
     * {@code ConnectorBackingContractTest} precedent (F2-193, F2-213, F2-437, F2-457).
     *
     * <p>Comments are stripped first, in ONE left-to-right alternation, because this fix quotes
     * every retired name on purpose -- a raw scan would read the epitaph as the corpse (F2-193)
     * and fail on a correct tree. Two sequential passes corrupt the source (F2-198), and the
     * {@code (?<!:)} stops a {@code https://} inside a string literal opening a comment.
     */
    @Test
    void thePdplDashboardNamesNoKeyTheStatsMapNoLongerSends() throws java.io.IOException {
        java.nio.file.Path page = java.nio.file.Paths.get(
            "../tss-grc-ui-v2/src/pages/dashboards/PdplDashboardPage.tsx")
            .toAbsolutePath().normalize();
        assertThat(java.nio.file.Files.exists(page)).as(
            "Expected %s to exist. A wrong path would make this leg vacuous rather than failing, "
            + "which is the one outcome a cross-codebase assertion must not have.", page)
            .isTrue();

        String stripped = java.util.regex.Pattern
            .compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/")
            .matcher(java.nio.file.Files.readString(page))
            .replaceAll("");

        // Anti-vacuity: the stripper must leave the page's real code behind, or every absence
        // assertion below passes over nothing (F2-235).
        assertThat(stripped).as(
            "The comment stripper appears to have blanked real code -- the page's own KPI array "
            + "should survive it.").contains("const kpis").contains("dsrSlaBreaches");

        for (String retired : java.util.List.of("processorsWithoutDpa", "retentionViolations")) {
            assertThat(stripped).as("""
                PdplDashboardPage still references %s outside a comment, and PrivacyService no \
                longer publishes it. Both were a server-side literal 0L for a control this module \
                does not track, rendered `> 0 ? danger : success` -- a green tile asserting \
                compliance nobody measured.

                If either control is ever really measured, add it to the stats map FIRST and this \
                leg will let the page read it again.""".formatted(retired))
                .doesNotContain(retired);
        }

        // Both surfaces must survive as an explicit absence rather than disappearing. UX-008's
        // convention exists so an unmeasurable control stays visible; deleting the tile drops the
        // question instead of answering it (F2-197). Asserted as a PAIRING, never as the token --
        // F2-457's control G survived a `contains("notMeasured")` leg because the const
        // DECLARATION satisfied it, so the tile could be reverted with the build still green.
        assertThat(stripped).as("""
            The "Processors w/o DPA" tile must survive as an em-dash whose hint is `notTracked`.

            UX-008 distinguishes a figure that is COMING from one that is NOT, and this is the \
            second: nothing in this module tracks the control, so "not yet computed" would \
            promise a number that never arrives (F2-457).""")
            .containsPattern("(?s)'Processors w/o DPA',.{0,120}?value:\\s*null,\\s*emptyHint:\\s*notTracked");

        assertThat(stripped).as("""
            The retention-violations line must survive as an em-dash carrying its reason on BOTH \
            `title` and `aria-label`. A `title` alone is a tooltip a keyboard or screen-reader \
            user never receives (F2-209), which on an Arabic-primary platform is house rule 7 as \
            much as it is accessibility.""")
            .containsPattern("(?s)title=\\{notTracked\\}.{0,80}?aria-label=\\{notTracked\\}");

        // And the clamp must stay gone. With the server's count scoped to the open set, the
        // breached figure is a subset by construction; a Math.min would hide a future divergence
        // rather than surface it, which is exactly what it did for as long as it was there.
        assertThat(stripped).as("""
            PdplDashboardPage must not clamp the breached count to the open count. The clamp \
            existed because the two figures described different populations -- ESCALATED against \
            open -- and it hid that divergence for as long as it was there. The server now ANDs \
            the open predicate, so there is nothing to clamp, and re-adding one would mean the \
            populations have come apart again (F2-457).""")
            .doesNotContain("Math.min(stats.dsrSlaBreaches");
    }
}
