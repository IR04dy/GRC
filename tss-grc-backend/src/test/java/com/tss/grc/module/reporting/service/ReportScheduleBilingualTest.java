package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.reporting.entity.ReportSchedule;
import com.tss.grc.module.reporting.repository.ReportRunRepository;
import com.tss.grc.module.reporting.repository.ReportScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2-13a — the Arabic half of a report schedule was accepted, mapped, and thrown away.
 *
 * <p>{@code ReportScheduleService.apply()} copies the caller-settable fields one by one.
 * {@code nameAr} and {@code descriptionAr} — the two columns V20 added — were not in the list, so
 * a bilingual create returned 201 with the Arabic name missing and no error anywhere. It survived
 * because the endpoints behind it were unreachable: no role held {@code REPORTING:WRITE}, so in the
 * platform's whole history not one create had ever completed. V321 granted it; the first live probe
 * through the newly opened endpoint failed on exactly this.
 *
 * <p>Guarding it here rather than only live, because the failure mode is a *silent omission* in a
 * hand-written copy loop — the shape that comes back whenever a column is added to the entity and
 * not to {@code apply()}. CLAUDE.md's bilingual rule makes both columns part of the contract.
 *
 * <p>⚠⚠ <b>F2-293 — this class held the one test that could have caught the reset, and its fixture
 * chose the two fields where the principle happened to hold.</b>
 * {@link #anOmittedArabicFieldIsLeftAloneRatherThanBlanked} states the general rule in its own
 * javadoc — <em>"{@code apply()} treats null as 'leave alone' so a partial PATCH-style body cannot
 * wipe fields"</em> — and exercised it on {@code nameAr} and {@code descriptionAr}, which are two of
 * the <b>only</b> guarded fields on this entity that had no initialiser. For the five that did
 * ({@code format}, {@code language}, {@code cadence}, {@code timeOfDay}, {@code isActive}) the rule
 * was FALSE, because the controller binds the entity and Jackson deserialised an omitted key into
 * the initialiser's value, so the null guard could never be false. 🔑 <b>A partial-update test that
 * picks the uninitialised fields proves the principle on exactly the fields where it holds</b> —
 * F2-215's two-of-N inside a test fixture, and the fixture is the artefact a reader trusts most.
 * The legs below cover the other five, and a mocked repository cannot see this: it was probed live.
 */
class ReportScheduleBilingualTest {

    private static final String NAME_AR = "سجل المخاطر الأسبوعي";
    private static final String DESC_AR = "تقرير أسبوعي لمجلس الإدارة";

    private ReportScheduleRepository scheduleRepo;
    private ReportScheduleService service;

    @BeforeEach
    void setUp() {
        scheduleRepo = mock(ReportScheduleRepository.class);
        service = new ReportScheduleService(scheduleRepo, mock(ReportRunRepository.class),
                                            mock(ReportSchedulerService.class));
        when(scheduleRepo.save(any(ReportSchedule.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ReportSchedule bilingualRequest() {
        ReportSchedule req = new ReportSchedule();
        req.setName("Weekly Risk Register");
        req.setNameAr(NAME_AR);
        req.setDescription("Board weekly pack");
        req.setDescriptionAr(DESC_AR);
        req.setReportType("RISK_REGISTER");
        req.setRecipients("board@example.com");
        return req;
    }

    @Test
    void createKeepsBothLanguages() {
        ReportSchedule saved = service.create(bilingualRequest());

        assertThat(saved.getName()).isEqualTo("Weekly Risk Register");
        assertThat(saved.getNameAr())
            .as("nameAr was dropped by apply() until F2-13a — the create returned 201 with the "
                + "Arabic name silently missing")
            .isEqualTo(NAME_AR);
        assertThat(saved.getDescriptionAr()).isEqualTo(DESC_AR);
        assertThat(saved.getOrganisationId())
            .as("the tenant stamp comes from TenantContext, never from the request body")
            .isEqualTo(1L);
        assertThat(saved.getNextRunAt())
            .as("an unset nextRunAt is defaulted, or the NOT NULL column rejects the insert")
            .isNotNull();
    }

    @Test
    void updateKeepsBothLanguages() {
        ReportSchedule existing = new ReportSchedule();
        existing.setOrganisationId(1L);
        existing.setName("old");
        existing.setNameAr("قديم");
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(existing));

        ReportSchedule saved = service.update(7L, bilingualRequest());

        assertThat(saved.getNameAr()).isEqualTo(NAME_AR);
        assertThat(saved.getDescriptionAr()).isEqualTo(DESC_AR);
    }

    /**
     * {@code apply()} treats null as "leave alone" so a partial PATCH-style body cannot wipe
     * fields. That has to hold for the Arabic columns too, or adding them to the copy loop would
     * trade a silent drop for a silent erase — a rename that omits {@code nameAr} would blank it.
     */
    @Test
    void anOmittedArabicFieldIsLeftAloneRatherThanBlanked() {
        ReportSchedule existing = new ReportSchedule();
        existing.setOrganisationId(1L);
        existing.setName("old");
        existing.setNameAr(NAME_AR);
        existing.setDescriptionAr(DESC_AR);
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(existing));

        ReportSchedule rename = new ReportSchedule();
        rename.setName("Renamed, English only");

        ReportSchedule saved = service.update(7L, rename);

        assertThat(saved.getName()).isEqualTo("Renamed, English only");
        assertThat(saved.getNameAr()).isEqualTo(NAME_AR);
        assertThat(saved.getDescriptionAr()).isEqualTo(DESC_AR);
    }

    /** A schedule as the register really holds them: nothing equal to any former initialiser. */
    private static ReportSchedule storedSchedule() {
        ReportSchedule existing = new ReportSchedule();
        existing.setOrganisationId(1L);
        existing.setName("Quarterly NCA ECC Cybersecurity Posture");
        existing.setNameAr(NAME_AR);
        existing.setReportType("RISK_REGISTER");
        existing.setFormat("XLSX");          // former initialiser: PDF
        existing.setLanguage("ar");          // former initialiser: en
        existing.setCadence("QUARTERLY");    // former initialiser: WEEKLY
        existing.setCadenceAnchor("15");     // never had one — the control
        existing.setTimeOfDay("22:00");      // former initialiser: 06:00
        existing.setIsActive(false);         // former initialiser: true
        existing.setRecipients("board@example.com");
        return existing;
    }

    /**
     * F2-293 — the exact body {@code ReportSchedulesPage#togglePause} sends. Before the fix this
     * rewrote four columns of a scheduled regulatory report; probed live, XLSX/ar/QUARTERLY/22:00
     * became PDF/en/WEEKLY/06:00.
     */
    @Test
    void thePauseTogglesOneFieldBodyLeavesTheOtherFiveAlone() {
        ReportSchedule existing = storedSchedule();
        existing.setIsActive(true);
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(existing));

        ReportSchedule toggle = new ReportSchedule();
        toggle.setIsActive(false);           // { isActive: !s.isActive } and nothing else

        ReportSchedule saved = service.update(7L, toggle);

        assertThat(saved.getIsActive()).as("the field the caller DID send is written").isFalse();
        assertThat(saved.getFormat()).isEqualTo("XLSX");
        assertThat(saved.getLanguage())
            .as("nine of the eleven live schedules are Arabic — house rule 7")
            .isEqualTo("ar");
        assertThat(saved.getCadence()).isEqualTo("QUARTERLY");
        assertThat(saved.getTimeOfDay()).isEqualTo("22:00");
        assertThat(saved.getCadenceAnchor())
            .as("the guarded field that never had an initialiser — it survived either way, which "
                + "is what shows the reset came from the initialisers and not from the guards")
            .isEqualTo("15");
    }

    /**
     * The mirror: a body that omits {@code isActive} must not resume a paused report. That reset
     * MANUFACTURES an assurance rather than losing a fact, which is why it is closed although no
     * live row differs from the initialiser (F2-240's ordering rule).
     */
    @Test
    void aBodyThatOmitsIsActiveDoesNotResumeAPausedReport() {
        ReportSchedule existing = storedSchedule();   // isActive = false
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(existing));

        ReportSchedule justCadence = new ReportSchedule();
        justCadence.setCadence("MONTHLY");

        ReportSchedule saved = service.update(7L, justCadence);

        assertThat(saved.getIsActive())
            .as("a paused regulator report must not restart because the caller said nothing "
                + "about isActive")
            .isFalse();
        assertThat(saved.getCadence()).isEqualTo("MONTHLY");
    }

    /**
     * The other half of removing an initialiser: every one of these columns is NOT NULL, so a create
     * that stops setting one turns a silent reset into a 500 that nothing else in the tree can see
     * under {@code ddl-auto=none}.
     */
    @Test
    void aBareCreateStillGetsEveryDefaultTheEntityUsedToCarry() {
        ReportSchedule saved = service.create(bilingualRequest());

        assertThat(saved.getFormat()).isEqualTo("PDF");
        assertThat(saved.getLanguage()).isEqualTo("en");
        assertThat(saved.getCadence()).isEqualTo("WEEKLY");
        assertThat(saved.getTimeOfDay()).isEqualTo("06:00");
        assertThat(saved.getIsActive()).isTrue();
    }

    /** The mirror defect: a create-path default must not overwrite a value the caller stated. */
    @Test
    void aCreateThatStatesTheseFieldsKeepsWhatItStated() {
        ReportSchedule req = bilingualRequest();
        req.setFormat("XLSX");
        req.setLanguage("ar");
        req.setCadence("SEMIANNUAL");
        req.setTimeOfDay("22:00");
        req.setIsActive(false);

        ReportSchedule saved = service.create(req);

        assertThat(saved.getFormat()).isEqualTo("XLSX");
        assertThat(saved.getLanguage()).isEqualTo("ar");
        assertThat(saved.getCadence()).isEqualTo("SEMIANNUAL");
        assertThat(saved.getTimeOfDay()).isEqualTo("22:00");
        assertThat(saved.getIsActive()).isFalse();
    }

    /**
     * F2-293 — {@code reporting.report_schedules} has zero CHECK constraints, so before this the
     * cadence vocabulary lived only in the scheduler's {@code case} labels and, separately, in the
     * page's {@code CADENCE_OPTIONS}. The seed wrote {@code SEMIANNUAL}, which neither knew, and
     * {@code advanceNextRun}'s {@code default} rescheduled it daily.
     */
    @Test
    void aCadenceTheSchedulerCannotComputeIsRefusedRatherThanSilentlyRescheduledDaily() {
        ReportSchedule existing = storedSchedule();
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(existing));

        ReportSchedule bad = new ReportSchedule();
        bad.setCadence("ANNUAL");

        assertThatThrownBy(() -> service.update(7L, bad))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cadence")
            .hasMessageContaining("ANNUAL")
            .hasMessageContaining("rescheduled daily");

        assertThat(existing.getCadence())
            .as("the stored value is untouched by the refusal")
            .isEqualTo("QUARTERLY");
    }

    /** Every declared cadence must be accepted, or the guard and the scheduler have drifted. */
    @Test
    void everySupportedCadenceIsAcceptedAndNormalised() {
        ReportSchedule existing = storedSchedule();
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(existing));

        for (String cadence : ReportSchedulerService.SUPPORTED_CADENCES) {
            ReportSchedule req = new ReportSchedule();
            req.setCadence(cadence.toLowerCase(java.util.Locale.ROOT));
            assertThat(service.update(7L, req).getCadence())
                .as("the column collates case-insensitively, so the writer normalises rather than "
                    + "leaving a variant spelling for a case-sensitive reader to miss (F2-179)")
                .isEqualTo(cadence);
        }
    }
}
