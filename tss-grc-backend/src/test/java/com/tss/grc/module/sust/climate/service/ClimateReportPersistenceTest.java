package com.tss.grc.module.sust.climate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.entity.ClimateReport;
import com.tss.grc.module.sust.climate.repository.ClimateReportRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ClimateReportService — S-G persistence, versioning, separation-of-duties
 * approval, and SHA-256 content lock.
 */
@ExtendWith(MockitoExtension.class)
class ClimateReportPersistenceTest {

    private static final Long ORG_ID = 1L;

    @Mock private TcfdReportService reportService;
    @Mock private ClimateReportRepository repo;
    @Mock private AuditLogService audit;

    private ClimateReportService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new ClimateReportService(reportService, repo, new ObjectMapper(), audit);
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    private static AnnualClimateReport pack(int year) {
        return new AnnualClimateReport(year, List.of(), List.of(), null, null, List.of(), List.of(),
                List.of(), Map.of("grandTotal", 100), Map.of("itemCount", 0), List.of(),
                "2091-01-01T00:00:00Z");
    }

    private static GrcUserDetails user(String name) {
        return new GrcUserDetails(7L, ORG_ID, name, "x", true, true, List.of());
    }

    private static ClimateReport report(Long id, String status, String generatedBy) {
        ClimateReport r = new ClimateReport();
        r.setId(id); r.setOrganisationId(ORG_ID);
        r.setReportingYear(2091); r.setVersion(1);
        r.setReportCode("CLIM-2091-v1");
        r.setStatus(status); r.setGeneratedBy(generatedBy);
        r.setContentJson("{\"reportingYear\":2091}");
        return r;
    }

    @Test
    void generate_persistsDraftSnapshot_v1() {
        when(reportService.generateAnnualReportData(2091)).thenReturn(pack(2091));
        when(repo.findFirstByOrganisationIdAndReportingYearOrderByVersionDesc(ORG_ID, 2091))
                .thenReturn(Optional.empty());
        when(repo.save(any(ClimateReport.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateReport r = service.generate(2091, user("alice"));

        assertThat(r.getStatus()).isEqualTo("DRAFT");
        assertThat(r.getVersion()).isEqualTo(1);
        assertThat(r.getGeneratedBy()).isEqualTo("alice");
        assertThat(r.getIsLocked()).isFalse();
        assertThat(r.getContentJson()).contains("2091");   // real serialized snapshot
    }

    @Test
    void generate_bumpsVersion_whenPriorExists() {
        ClimateReport prior = report(9L, "APPROVED", "alice"); prior.setVersion(3);
        when(reportService.generateAnnualReportData(2091)).thenReturn(pack(2091));
        when(repo.findFirstByOrganisationIdAndReportingYearOrderByVersionDesc(ORG_ID, 2091))
                .thenReturn(Optional.of(prior));
        when(repo.save(any(ClimateReport.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateReport r = service.generate(2091, user("alice"));
        assertThat(r.getVersion()).isEqualTo(4);   // prior v3 → v4
    }

    @Test
    void approve_locksAndHashes_withSeparationOfDuties() {
        ClimateReport rep = report(5L, "IN_REVIEW", "alice");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(rep));
        when(repo.save(any(ClimateReport.class))).thenAnswer(inv -> inv.getArgument(0));

        ClimateReport out = service.approve(5L, user("bob"));   // different reviewer

        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getIsLocked()).isTrue();
        assertThat(out.getApprovedBy()).isEqualTo("bob");
        assertThat(out.getContentHash()).isNotNull().hasSize(64);   // SHA-256 hex
    }

    @Test
    void approve_refusesSelfApprovalByGenerator() {
        ClimateReport rep = report(5L, "IN_REVIEW", "alice");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(rep));

        assertThatThrownBy(() -> service.approve(5L, user("alice")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Separation of duties");
    }

    @Test
    void approve_rejectsFromWrongState() {
        ClimateReport rep = report(5L, "DRAFT", "alice");   // must be IN_REVIEW
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(rep));

        assertThatThrownBy(() -> service.approve(5L, user("bob")))
                .isInstanceOf(IllegalTransitionException.class);
    }
}
