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
}
