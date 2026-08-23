package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.AnnualPlan;
import com.tss.grc.module.audit_lc.entity.WorkingPaper;
import com.tss.grc.module.audit_lc.repository.AnnualPlanRepository;
import com.tss.grc.module.audit_lc.repository.CapaTaskRepository;
import com.tss.grc.module.audit_lc.repository.WorkingPaperRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-294 — the two audit_lc members of the sweep, and the pair with the widest PROPORTION of live
 * rows at risk: {@code WorkingPaper#hours} differs from its initialiser on <b>12 of 12</b> rows in
 * both databases and {@code AnnualPlan#budgetHours} on <b>2 of 2</b>. So a partial body zeroed every
 * IIA Standard 2330 time record in the register, and both annual plans' budgeted effort.
 *
 * <p>These are Group B of the three kinds: unlike nine of the 27, neither create path had a default
 * of its own, and both creates {@code save} the BOUND entity. Both columns are NOT NULL, so the
 * defaults added on the create path are load-bearing rather than tidy — without them an omitted key
 * reaches the INSERT as null and 500s, which under {@code ddl-auto=none} nothing else in the tree
 * would notice (F2-238's hazard). The database DEFAULT does not rescue it: the entity maps the
 * column insertable, so Hibernate names it in the INSERT and sends the explicit null (F2-240).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLifecycleInitialiserSweepTest {

    private static final Long ORG_ID = 1L;

    @Mock private AnnualPlanRepository annualPlans;
    @Mock private WorkingPaperRepository workingPapers;
    @Mock private CapaTaskRepository capas;
    @InjectMocks private AuditLifecycleService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(annualPlans.save(any(AnnualPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(workingPapers.save(any(WorkingPaper.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void editingAnAnnualPlansNotesLeavesItsBudgetedHoursAlone() {
        AnnualPlan stored = new AnnualPlan();
        stored.setId(1L);
        stored.setOrganisationId(ORG_ID);
        stored.setExternalId("AP-2026");
        stored.setYear(2026);
        stored.setStatus("EXECUTING");
        stored.setBudgetHours(5200);
        when(annualPlans.findByOrganisationIdAndExternalId(ORG_ID, "AP-2026"))
            .thenReturn(Optional.of(stored));

        AnnualPlan body = new AnnualPlan();
        body.setAcNotesEn("Committee asked for a Q3 re-scope");

        assertThat(service.updateAnnualPlan("AP-2026", body).getBudgetHours()).isEqualTo(5200);
    }

    @Test
    void editingAWorkingPapersConclusionLeavesItsRecordedHoursAlone() {
        WorkingPaper stored = new WorkingPaper();
        stored.setId(2L);
        stored.setOrganisationId(ORG_ID);
        stored.setExternalId("WP-2026-011");
        stored.setEngagementCode("IA-2026-001");
        stored.setTitleEn("Access review sampling");
        stored.setStatus("DRAFT");
        stored.setHours(new BigDecimal("16.00"));
        when(workingPapers.findByOrganisationIdAndExternalId(ORG_ID, "WP-2026-011"))
            .thenReturn(Optional.of(stored));

        WorkingPaper body = new WorkingPaper();
        body.setConclusionEn("Two exceptions raised to management.");

        assertThat(service.updateWorkingPaper("WP-2026-011", body).getHours())
            .as("12 of 12 live working papers carry a real figure, so this reset zeroed the whole "
                + "register's time records")
            .isEqualByComparingTo("16.00");
    }

    @Test
    void aBareCreateStillGetsTheDefaultsTheEntitiesUsedToCarry() {
        AnnualPlan plan = new AnnualPlan();
        plan.setExternalId("AP-2027");
        plan.setYear(2027);
        assertThat(service.createAnnualPlan(plan).getBudgetHours())
            .as("NOT NULL, and this create saves the bound entity — without the relocated default an "
                + "omitted key reaches the INSERT as null")
            .isZero();

        WorkingPaper wp = new WorkingPaper();
        wp.setExternalId("WP-2027-001");
        wp.setEngagementCode("IA-2027-001");
        wp.setTitleEn("Walkthrough");
        assertThat(service.createWorkingPaper(wp).getHours()).isEqualByComparingTo("0");
    }

    @Test
    void aCreateThatStatesTheseFiguresKeepsWhatItStated() {
        AnnualPlan plan = new AnnualPlan();
        plan.setExternalId("AP-2028");
        plan.setYear(2028);
        plan.setBudgetHours(4800);
        assertThat(service.createAnnualPlan(plan).getBudgetHours())
            .as("the mirror defect: a create-path default that overwrites a STATED value")
            .isEqualTo(4800);

        WorkingPaper wp = new WorkingPaper();
        wp.setExternalId("WP-2028-001");
        wp.setEngagementCode("IA-2028-001");
        wp.setTitleEn("Sampling");
        wp.setHours(new BigDecimal("7.50"));
        assertThat(service.createWorkingPaper(wp).getHours()).isEqualByComparingTo("7.50");
    }
}
