package com.tss.grc.module.sust.annex.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.annex.entity.SustainabilityReport;
import com.tss.grc.module.sust.annex.repository.ReportingYearRepository;
import com.tss.grc.module.sust.annex.repository.SustainabilityReportRepository;
import com.tss.grc.module.sust.cma.service.CmaDisclosureService;
import com.tss.grc.module.sust.tcfd.service.TcfdItemService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SustainabilityReportService — S-I: approving an annex stamps the approver,
 * SHA-256 hashes the frozen content, and locks the row.
 */
@ExtendWith(MockitoExtension.class)
class SustainabilityReportServiceTest {

    private static final Long ORG = 1L;

    @Mock private ReportingYearRepository yearRepo;
    @Mock private SustainabilityReportRepository reportRepo;
    @Mock private AnnexGeneratorService generator;
    @Mock private ReportComposerService composer;
    @Mock private AuditLogService audit;
    @Mock private CmaDisclosureService cmaService;
    @Mock private TcfdItemService tcfdService;
    @Mock private com.tss.grc.module.sust.metrics.repository.EsgMetricRepository metricRepo;
    @Mock private com.tss.grc.module.sust.metrics.repository.EsgMetricReadingRepository metricReadingRepo;
    @InjectMocks private SustainabilityReportService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static GrcUserDetails user(String name) {
        return new GrcUserDetails(7L, ORG, name, "x", true, true, List.of());
    }
    private static SustainabilityReport report(String status) {
        SustainabilityReport r = new SustainabilityReport();
        r.setId(5L); r.setOrganisationId(ORG); r.setReportingYearId(1L);
        r.setReportType("ANNUAL_ANNEX"); r.setReportCode("ANN-2026-v1.0");
        r.setStatus(status);
        r.setContentMarkdown("md"); r.setContentHtml("<html>"); r.setContentJson("{\"reportingYear\":2026}");
        return r;
    }

    @Test
    void approve_stampsApprover_hashesAndLocks() {
        when(reportRepo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(report("SUBMITTED")));
        when(reportRepo.save(any(SustainabilityReport.class))).thenAnswer(inv -> inv.getArgument(0));

        SustainabilityReport out = service.transitionReport(5L, "APPROVED", user("bob"));

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApprovedBy()).isEqualTo("bob");        // T-43 fix — approver stamped
        assertThat(out.getApprovedAt()).isNotNull();
        assertThat(out.getContentHash()).isNotNull().hasSize(64); // SHA-256 hex
        assertThat(out.getIsLocked()).isTrue();
    }

    /** V293 gate — a reading-required ACTIVE metric with no reading blocks APPROVED. */
    @Test
    void approve_blocksWhenReadingRequiredMetricHasNoReading() {
        when(reportRepo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(report("SUBMITTED")));
        com.tss.grc.module.sust.annex.entity.ReportingYear year = new com.tss.grc.module.sust.annex.entity.ReportingYear();
        year.setYear((short) 2026);
        when(yearRepo.findByIdAndOrganisationId(1L, ORG)).thenReturn(Optional.of(year));
        com.tss.grc.module.sust.metrics.entity.EsgMetric metric = new com.tss.grc.module.sust.metrics.entity.EsgMetric();
        metric.setId(9L); metric.setMetricCode("ENV-WATER-01");
        metric.setStatus("ACTIVE"); metric.setReadingRequired(true);
        when(metricRepo.findByOrganisationIdAndReportingYear(ORG, (short) 2026)).thenReturn(List.of(metric));
        when(metricReadingRepo.existsByOrganisationIdAndMetricId(ORG, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.transitionReport(5L, "APPROVED", user("bob")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("ENV-WATER-01");

        // With a reading present the same approval goes through.
        when(metricReadingRepo.existsByOrganisationIdAndMetricId(ORG, 9L)).thenReturn(true);
        when(reportRepo.save(any(SustainabilityReport.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.transitionReport(5L, "APPROVED", user("bob")).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void transition_rejectsIllegalLeap() {
        when(reportRepo.findByIdAndOrganisationId(5L, ORG)).thenReturn(Optional.of(report("DRAFT")));
        // DRAFT → APPROVED is illegal (must go via SUBMITTED).
        assertThatThrownBy(() -> service.transitionReport(5L, "APPROVED", user("bob")))
                .isInstanceOf(IllegalTransitionException.class);
    }
}
