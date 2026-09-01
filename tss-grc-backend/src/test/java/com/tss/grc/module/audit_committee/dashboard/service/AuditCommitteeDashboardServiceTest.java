package com.tss.grc.module.audit_committee.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import com.tss.grc.module.ai.governance.repository.MlModelDriftObservationRepository;
import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestationResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.FinalizeRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.GenerateSnapshotRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.LiveDashboardResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RecordResolutionRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotResponse;
import com.tss.grc.module.audit_committee.dashboard.entity.DashboardAttestation;
import com.tss.grc.module.audit_committee.dashboard.entity.DashboardSnapshot;
import com.tss.grc.module.audit_committee.dashboard.repository.DashboardAttestationRepository;
import com.tss.grc.module.audit_committee.dashboard.repository.DashboardSnapshotRepository;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import com.tss.grc.module.ccm.repository.ControlTestDefinitionRepository;
import com.tss.grc.module.ccm.repository.ControlTestRunRepository;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.obligation.entity.Obligation;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.repository.ObligationRepository;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesOverviewDto;
import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuditCommitteeDashboardService — Sprint 11.
 *
 * <p>Locks the 10 live-section computation, the snapshot →
 * attestation → resolution → finalize workflow, tenant isolation,
 * tamper-evident SHA-256 signature determinism, F7+F8 emission, and
 * graceful handling of missing sub-module data.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditCommitteeDashboardServiceTest {

    private static final Long ORG_ID = 1L;
    private static final UUID SNAP_EX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long SNAP_ID = 99L;

    @Mock private DashboardSnapshotRepository snapshotRepo;
    @Mock private DashboardAttestationRepository attestationRepo;
    @Mock private RiskRepository riskRepo;
    @Mock private ControlRepository controlRepo;
    @Mock private AuditFindingRepository findingRepo;
    @Mock private CapaTaskRepository capaRepo;
    @Mock private IncidentRepository incidentRepo;
    @Mock private MlModelRepository mlModelRepo;
    @Mock private MlModelDriftObservationRepository mlDriftRepo;
    @Mock private ClimateRiskOpportunityRepository climateRepo;
    @Mock private GhgEmissionRecordRepository ghgRepo;
    @Mock private ObligationRepository obligationRepo;
    @Mock private ObligationAttestationRepository obligationAttRepo;
    @Mock private VendorBcpCampaignRepository bcpCampaignRepo;
    @Mock private VendorBcpAttestationRepository bcpAttRepo;
    @Mock private ControlTestDefinitionRepository ccmDefRepo;
    @Mock private ControlTestRunRepository ccmRunRepo;
    @Mock private ThreeLinesService threeLinesService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuditCommitteeDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AuditCommitteeDashboardService(
                snapshotRepo, attestationRepo,
                riskRepo, controlRepo, findingRepo, capaRepo, incidentRepo,
                mlModelRepo, mlDriftRepo,
                climateRepo, ghgRepo,
                obligationRepo, obligationAttRepo,
                bcpCampaignRepo, bcpAttRepo,
                ccmDefRepo, ccmRunRepo,
                threeLinesService,
                audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
        stubAllZeros();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // F2-422: these stubbed `findByOrganisationId(eq(ORG), any(Pageable.class))`. A mocked
    // repository has no page size -- it returns whatever it is handed -- so the
    // `Pageable.ofSize(N)` window every figure below was derived from sat outside this
    // class's reach BY CONSTRUCTION. Repointed at the uncapped overload; the assertions
    // are unchanged, because the predicates were never what was wrong.
    /** Stub every repo so safeXxxSection() falls back gracefully. */
    private void stubAllZeros() {
        when(riskRepo.findByOrganisationId(ORG_ID))
                .thenReturn(List.of());
        when(controlRepo.countByOrganisationId(ORG_ID)).thenReturn(0L);
        when(controlRepo.countByOrganisationIdAndStatus(eq(ORG_ID), anyString())).thenReturn(0L);
        when(findingRepo.findByOrganisationId(ORG_ID))
                .thenReturn(List.of());
        when(capaRepo.findByOrganisationIdOrderByCreatedAtDesc(ORG_ID)).thenReturn(List.of());
        when(incidentRepo.findByOrganisationId(ORG_ID))
                .thenReturn(List.of());
        when(mlModelRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of());
        when(mlModelRepo.findHighRiskModelsWithoutFairnessAudit(ORG_ID)).thenReturn(List.of());
        when(mlDriftRepo.findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(ORG_ID))
                .thenReturn(List.of());
        when(climateRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(ghgRepo.findByOrganisationIdAndReportingYear(eq(ORG_ID), any(Short.class))).thenReturn(List.of());
        when(obligationRepo.findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(ORG_ID))
                .thenReturn(List.of());
        when(obligationAttRepo.countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(eq(ORG_ID), anyString()))
                .thenReturn(0L);
        when(bcpCampaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of());
        when(ccmDefRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of());
        when(ccmDefRepo.findByOrganisationIdAndIsEnabledTrueAndIsDeletedFalse(ORG_ID))
                .thenReturn(List.of());
        when(ccmRunRepo.countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(
                eq(ORG_ID), anyString(), any(LocalDateTime.class))).thenReturn(0L);
        when(threeLinesService.overview()).thenReturn(
                new ThreeLinesOverviewDto(List.of(), 0, 0, 0, 0, 0));
    }

    // ─── Live dashboard ───────────────────────────────────────────

    @Test
    void computeLiveDashboard_returnsAll10Sections() {
        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        assertThat(out).isNotNull();
        assertThat(out.computedAt()).isNotNull();
        assertThat(out.risk()).isNotNull();
        assertThat(out.controls()).isNotNull();
        assertThat(out.findings()).isNotNull();
        assertThat(out.capa()).isNotNull();
        assertThat(out.ai()).isNotNull();
        assertThat(out.climate()).isNotNull();
        assertThat(out.obligations()).isNotNull();
        assertThat(out.incidents()).isNotNull();
        assertThat(out.vendorBcp()).isNotNull();
        assertThat(out.threeLines()).isNotNull();
    }

    @Test
    void computeLiveDashboard_riskSection_bucketsByLevel() {
        when(riskRepo.findByOrganisationId(ORG_ID))
                .thenReturn(List.of(
                        risk("R1", "CRITICAL", 20, "OPEN"),
                        risk("R2", "HIGH", 16, "OPEN"),
                        risk("R3", "MEDIUM", 9, "OPEN"),
                        risk("R4", "LOW", 4, "OPEN"),
                        risk("R5", "CLOSED-RISK", null, "CLOSED")  // filtered out
                ));
        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        assertThat(out.risk().totalRisks()).isEqualTo(4);
        assertThat(out.risk().criticalCount()).isEqualTo(1);
        assertThat(out.risk().highCount()).isEqualTo(1);
        assertThat(out.risk().mediumCount()).isEqualTo(1);
        assertThat(out.risk().lowCount()).isEqualTo(1);
        assertThat(out.risk().aboveAppetiteCount()).isEqualTo(2);
        // Top above-appetite is sorted by residualScore desc.
        assertThat(out.risk().topAboveAppetite()).hasSize(2);
        assertThat(out.risk().topAboveAppetite().get(0).titleEn()).isEqualTo("R1");
    }

    @Test
    void computeLiveDashboard_capaSection_overdueAndOnTrack() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(capaRepo.findByOrganisationIdOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of(
                        capa("CAPA1", "OPEN", yesterday),       // overdue
                        capa("CAPA2", "IN_PROGRESS", tomorrow), // on track
                        capa("CAPA3", "CLOSED", yesterday)      // not counted
                ));
        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        assertThat(out.capa().totalOpen()).isEqualTo(2);
        assertThat(out.capa().overdueCount()).isEqualTo(1);
        assertThat(out.capa().onTrackRatioPct()).isEqualTo(50.0);
    }

    @Test
    void computeLiveDashboard_aiSection_pullsRiskTierAndDrift() {
        when(mlModelRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of(
                        model("M1", "CRITICAL"), model("M2", "HIGH"),
                        model("M3", "MEDIUM"), model("M4", "LOW")));
        when(mlModelRepo.findHighRiskModelsWithoutFairnessAudit(ORG_ID))
                .thenReturn(List.of(model("M1", "CRITICAL")));
        when(mlDriftRepo.findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(ORG_ID))
                .thenReturn(List.of(new MlModelDriftObservation(), new MlModelDriftObservation()));
        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        assertThat(out.ai().totalModels()).isEqualTo(4);
        assertThat(out.ai().criticalCount()).isEqualTo(1);
        assertThat(out.ai().missingFairnessAuditCount()).isEqualTo(1);
        assertThat(out.ai().unacknowledgedDriftCount()).isEqualTo(2);
    }

    @Test
    void computeLiveDashboard_handlesMissingSubmoduleData_returnsZerosNotNPE() {
        // Make one repo blow up — service must keep going and still
        // return a complete LiveDashboardResponse with that section
        // zero-filled.
        when(riskRepo.findByOrganisationId(ORG_ID))
                .thenThrow(new RuntimeException("table risks does not exist"));
        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        assertThat(out.risk().totalRisks()).isEqualTo(0);
        assertThat(out.risk().topAboveAppetite()).isEmpty();
        // Other sections still populated.
        assertThat(out.controls()).isNotNull();
    }

    // ─── Snapshot generate ───────────────────────────────────────

    @Test
    void generateSnapshot_freezesJson_andEmitsF8() {
        when(snapshotRepo.existsByOrganisationIdAndSnapshotCodeAndIsDeletedFalse(
                eq(ORG_ID), anyString())).thenReturn(false);
        AtomicLong seq = new AtomicLong(100);
        when(snapshotRepo.save(any(DashboardSnapshot.class))).thenAnswer(inv -> {
            DashboardSnapshot s = inv.getArgument(0);
            if (s.getId() == null) s.setId(seq.incrementAndGet());
            return s;
        });

        SnapshotResponse out = service.generateSnapshot(
                new GenerateSnapshotRequest(LocalDate.of(2026, 7, 15), "QUARTERLY", null));

        assertThat(out).isNotNull();
        assertThat(out.snapshotCode()).startsWith("AC-DASH-2026-Q3-MEETING");
        assertThat(out.kpisJson()).isNotBlank();
        // The frozen JSON should be parseable and contain the section keys.
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode parsed = parseQuietly(m, out.kpisJson());
        assertThat(parsed.has("risk")).isTrue();
        assertThat(parsed.has("controls")).isTrue();
        assertThat(parsed.has("threeLines")).isTrue();

        // F7 audit + F8 event published.
        verify(audit, atLeastOnce()).log(eq("AC_DASHBOARD_SNAPSHOT_GENERATED"),
                anyString(), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("AC_DASHBOARD_SNAPSHOT_GENERATED");
    }

    @Test
    void generateSnapshot_collisionSuffixesCode() {
        when(snapshotRepo.existsByOrganisationIdAndSnapshotCodeAndIsDeletedFalse(
                eq(ORG_ID), anyString())).thenReturn(true);
        when(snapshotRepo.save(any(DashboardSnapshot.class))).thenAnswer(inv -> {
            DashboardSnapshot s = inv.getArgument(0);
            s.setId(101L);
            return s;
        });
        SnapshotResponse out = service.generateSnapshot(
                new GenerateSnapshotRequest(LocalDate.of(2026, 4, 1), "QUARTERLY", null));
        assertThat(out.snapshotCode()).matches("AC-DASH-2026-Q2-MEETING-[A-F0-9]+");
    }

    // ─── Attest ────────────────────────────────────────────────

    @Test
    void attestSnapshot_recordsSha256SignatureHash_deterministically() {
        DashboardSnapshot s = newSnap();
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.of(s));
        when(attestationRepo.save(any(DashboardAttestation.class))).thenAnswer(inv -> {
            DashboardAttestation a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });

        AttestationResponse out = service.attestSnapshot(SNAP_EX,
                new AttestRequest("CHAIR", "I confirm the figures are fairly stated.", "أُقرّ بصحة الأرقام."));

        assertThat(out.signatureHash()).isNotNull().hasSize(64);  // SHA-256 hex = 64 chars
        assertThat(out.attestedRole()).isEqualTo("CHAIR");

        // Tampering with any input MUST produce a different hash.
        String orig = AuditCommitteeDashboardService.sha256Hex(
                "msg|ar|7|2026-05-23T12:00:00");
        String tampered = AuditCommitteeDashboardService.sha256Hex(
                "msg!|ar|7|2026-05-23T12:00:00");
        assertThat(orig).isNotEqualTo(tampered);
    }

    @Test
    void attestSnapshot_emitsF7AndF8() {
        DashboardSnapshot s = newSnap();
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.of(s));
        when(attestationRepo.save(any(DashboardAttestation.class))).thenAnswer(inv -> {
            DashboardAttestation a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });
        service.attestSnapshot(SNAP_EX,
                new AttestRequest("MEMBER", "Reviewed.", "تمت المراجعة."));

        verify(audit, atLeastOnce()).log(eq("AC_DASHBOARD_ATTESTED"),
                anyString(), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("AC_DASHBOARD_ATTESTED");
    }

    @Test
    void attestSnapshot_rejectedAfterFinalize() {
        DashboardSnapshot s = newSnap();
        s.setIsFinalized(Boolean.TRUE);
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.attestSnapshot(SNAP_EX,
                new AttestRequest("CHAIR", "x", "س")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finalized");
        verify(attestationRepo, never()).save(any());
    }

    // ─── Resolutions ───────────────────────────────────────────

    @Test
    void recordResolution_appendsToJsonArray() {
        DashboardSnapshot s = newSnap();
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.of(s));
        when(snapshotRepo.save(any(DashboardSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attestationRepo.findByOrganisationIdAndSnapshotIdAndIsDeletedFalseOrderByAttestedAtAsc(
                eq(ORG_ID), anyLong())).thenReturn(List.of());

        service.recordResolution(SNAP_EX,
                new RecordResolutionRequest("Approve plan", "اعتماد الخطة"));
        service.recordResolution(SNAP_EX,
                new RecordResolutionRequest("Note PDPL finding", "أخذ العلم بالملاحظة"));

        assertThat(s.getCommitteeResolutions()).isNotNull();
        ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode arr = parseQuietly(m, s.getCommitteeResolutions());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(2);
        assertThat(arr.get(0).get("textEn").asText()).isEqualTo("Approve plan");
        assertThat(arr.get(1).get("textAr").asText()).isEqualTo("أخذ العلم بالملاحظة");

        verify(audit, atLeastOnce()).log(eq("AC_RESOLUTION_RECORDED"),
                anyString(), anyLong(), anyString());
    }

    // ─── Finalize ──────────────────────────────────────────────

    @Test
    void finalizeSnapshot_setsFlagAndExecutiveSummary_andTerminal() {
        DashboardSnapshot s = newSnap();
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.of(s));
        when(snapshotRepo.save(any(DashboardSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attestationRepo.findByOrganisationIdAndSnapshotIdAndIsDeletedFalseOrderByAttestedAtAsc(
                eq(ORG_ID), anyLong())).thenReturn(List.of());

        SnapshotResponse out = service.finalizeSnapshot(SNAP_EX,
                new FinalizeRequest("All sections reviewed; no material issues.",
                                     "تمت مراجعة جميع المحاور دون ملاحظات جوهرية."));

        assertThat(out.isFinalized()).isTrue();
        assertThat(out.finalizedAt()).isNotNull();
        assertThat(out.executiveSummaryEn()).contains("no material issues");
        verify(audit, atLeastOnce()).log(eq("AC_DASHBOARD_FINALIZED"),
                anyString(), anyLong(), anyString());

        // Second call is idempotent — no F7 / F8 emitted twice for the
        // same finalize, but executive summary stays the same.
        SnapshotResponse second = service.finalizeSnapshot(SNAP_EX,
                new FinalizeRequest("attempt to overwrite", null));
        assertThat(second.executiveSummaryEn()).contains("no material issues");  // not overwritten
    }

    @Test
    void finalizeSnapshot_preservesExecutiveSummaryAcrossReads() {
        DashboardSnapshot s = newSnap();
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.of(s));
        when(snapshotRepo.save(any(DashboardSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attestationRepo.findByOrganisationIdAndSnapshotIdAndIsDeletedFalseOrderByAttestedAtAsc(
                eq(ORG_ID), anyLong())).thenReturn(List.of());

        service.finalizeSnapshot(SNAP_EX,
                new FinalizeRequest("Q2 review concluded.", "اختُتمت مراجعة الربع الثاني."));
        SnapshotResponse read = service.getSnapshot(SNAP_EX);
        assertThat(read.executiveSummaryEn()).isEqualTo("Q2 review concluded.");
        assertThat(read.executiveSummaryAr()).isEqualTo("اختُتمت مراجعة الربع الثاني.");
        assertThat(read.isFinalized()).isTrue();
    }

    // ─── Reads ─────────────────────────────────────────────────

    @Test
    void getSnapshot_notFound_throwsEntityNotFound() {
        when(snapshotRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, SNAP_EX))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSnapshot(SNAP_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listSnapshots_returnsTenantOnly() {
        DashboardSnapshot s1 = newSnap("AC-DASH-2026-Q1-MEETING");
        DashboardSnapshot s2 = newSnap("AC-DASH-2026-Q2-MEETING");
        when(snapshotRepo.findByOrganisationIdAndIsDeletedFalseOrderByMeetingDateDesc(ORG_ID))
                .thenReturn(List.of(s1, s2));
        when(attestationRepo.countByOrganisationIdAndSnapshotIdAndIsDeletedFalse(eq(ORG_ID), anyLong()))
                .thenReturn(3L);
        var summaries = service.listSnapshots();
        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).attestationCount()).isEqualTo(3L);
    }

    // ─── Tenant isolation ─────────────────────────────────────

    @Test
    void tenantIsolation_serviceReadsTenantFromTenantContext() {
        // Switch tenant mid-run; service must use the new value.
        TenantContext.setCurrentTenant(2L);
        // We didn't stub anything for org 2, so the various repos
        // return null/empty defaults — but the safe wrappers catch
        // that and the dashboard still comes back with zeros.
        when(riskRepo.findByOrganisationId(2L))
                .thenReturn(List.of());
        when(controlRepo.countByOrganisationId(2L)).thenReturn(0L);
        when(findingRepo.findByOrganisationId(2L))
                .thenReturn(List.of());
        when(capaRepo.findByOrganisationIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        when(incidentRepo.findByOrganisationId(2L))
                .thenReturn(List.of());
        when(mlModelRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(2L))
                .thenReturn(List.of());
        when(mlModelRepo.findHighRiskModelsWithoutFairnessAudit(2L)).thenReturn(List.of());
        when(mlDriftRepo.findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(2L))
                .thenReturn(List.of());
        when(climateRepo.findByOrganisationId(2L)).thenReturn(List.of());
        when(ghgRepo.findByOrganisationIdAndReportingYear(eq(2L), any(Short.class)))
                .thenReturn(List.of());
        when(obligationRepo.findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(2L))
                .thenReturn(List.of());
        when(bcpCampaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(2L))
                .thenReturn(List.of());
        when(ccmDefRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(2L))
                .thenReturn(List.of());
        when(ccmDefRepo.findByOrganisationIdAndIsEnabledTrueAndIsDeletedFalse(2L))
                .thenReturn(List.of());

        LiveDashboardResponse out = service.computeLiveDashboard(2L);
        assertThat(out.risk().totalRisks()).isEqualTo(0);

        // Verify the org=2 finders were used, not org=1.
        verify(riskRepo).findByOrganisationId(2L);
    }

    /**
     * F2-422 — the three sections read the whole register, never a window.
     *
     * <p>All three used {@code Pageable.ofSize(N)}, an UNSORTED cap: it keeps N rows and
     * does not say which N, so every figure below was a function of the query plan — and
     * {@code generateSnapshot} serialises them into the {@code kpis_json} an Audit
     * Committee member then signs. The assertion is the NEGATIVE because that is what
     * discriminates: a value assertion also fails pre-fix, but it fails looking like an
     * arithmetic bug rather than like a window.
     */
    @Test
    void theThreeSectionsReadTheWholeRegisterNotAWindow() {
        stubAllZeros();
        service.computeLiveDashboard(ORG_ID);

        verify(riskRepo).findByOrganisationId(ORG_ID);
        verify(findingRepo).findByOrganisationId(ORG_ID);
        verify(incidentRepo).findByOrganisationId(ORG_ID);

        verify(riskRepo, never())
                .findByOrganisationId(anyLong(), any(org.springframework.data.domain.Pageable.class));
        verify(findingRepo, never())
                .findByOrganisationId(anyLong(), any(org.springframework.data.domain.Pageable.class));
        verify(incidentRepo, never())
                .findByOrganisationId(anyLong(), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void sha256Hex_pureFunction_isDeterministic() {
        String a = AuditCommitteeDashboardService.sha256Hex("hello|world|7|2026-05-23T12:00:00");
        String b = AuditCommitteeDashboardService.sha256Hex("hello|world|7|2026-05-23T12:00:00");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
        String diff = AuditCommitteeDashboardService.sha256Hex("hello|world|7|2026-05-23T12:00:01");
        assertThat(diff).isNotEqualTo(a);  // tamper changes the hash
    }

    @Test
    void vendorBcp_section_summarisesLatestCampaign() {
        VendorBcpCampaign c = new VendorBcpCampaign();
        c.setId(50L);
        c.setOrganisationId(ORG_ID);
        c.setCampaignCode("BCP-2026-Q2");
        when(bcpCampaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of(c));
        VendorBcpAttestation a1 = new VendorBcpAttestation(); a1.setStatus("SUBMITTED"); a1.setRtoBreach(true);
        VendorBcpAttestation a2 = new VendorBcpAttestation(); a2.setStatus("INVITED");   a2.setRtoBreach(false);
        VendorBcpAttestation a3 = new VendorBcpAttestation(); a3.setStatus("ACCEPTED");  a3.setRtoBreach(false);
        when(bcpAttRepo.findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByCreatedAtAsc(ORG_ID, 50L))
                .thenReturn(List.of(a1, a2, a3));

        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        assertThat(out.vendorBcp().latestCampaignCode()).isEqualTo("BCP-2026-Q2");
        assertThat(out.vendorBcp().latestCampaignInvited()).isEqualTo(3);
        assertThat(out.vendorBcp().latestCampaignSubmitted()).isEqualTo(2);  // SUBMITTED + ACCEPTED
        // 2 / 3 = 66.67%
        assertThat(out.vendorBcp().latestCampaignResponseRatePct()).isBetween(66.0, 67.0);
        assertThat(out.vendorBcp().belowRtoThresholdCount()).isEqualTo(1);
    }

    @Test
    void climate_section_computesYoYAndExpectedLoss() {
        ClimateRiskOpportunity c = new ClimateRiskOpportunity();
        c.setKind("RISK");
        c.setLikelihood((short) 4);  // 4/5 = 0.8
        c.setImpact((short) 5);      // 5/5 = 1.0
        c.setFinancialImpactSar(new BigDecimal("1000000.00"));  // 1M SAR
        c.setStatus("ASSESSED");
        when(climateRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(c));

        short ty = (short) LocalDate.now().getYear();
        short ly = (short) (ty - 1);
        GhgEmissionRecord rThis = new GhgEmissionRecord();
        rThis.setEmissionValueTco2e(new BigDecimal("1100"));
        GhgEmissionRecord rLast = new GhgEmissionRecord();
        rLast.setEmissionValueTco2e(new BigDecimal("1000"));
        when(ghgRepo.findByOrganisationIdAndReportingYear(ORG_ID, ty)).thenReturn(List.of(rThis));
        when(ghgRepo.findByOrganisationIdAndReportingYear(ORG_ID, ly)).thenReturn(List.of(rLast));

        LiveDashboardResponse out = service.computeLiveDashboard(ORG_ID);
        // pw loss = 0.8 * 1.0 * 1_000_000 = 800_000
        assertThat(out.climate().probabilityWeightedExpectedLossSar())
                .isBetween(799_999.0, 800_001.0);
        assertThat(out.climate().financedEmissionsCurrentTco2e()).isEqualTo(1100.0);
        assertThat(out.climate().financedEmissionsPreviousTco2e()).isEqualTo(1000.0);
        assertThat(out.climate().financedEmissionsYoyChangePct()).isEqualTo(10.0);
    }

    // ─── Fixtures ─────────────────────────────────────────────

    private static DashboardSnapshot newSnap() { return newSnap("AC-DASH-2026-Q2-MEETING"); }

    private static DashboardSnapshot newSnap(String code) {
        DashboardSnapshot s = new DashboardSnapshot();
        s.setId(SNAP_ID);
        s.setExternalId(SNAP_EX);
        s.setOrganisationId(ORG_ID);
        s.setSnapshotCode(code);
        s.setMeetingDate(LocalDate.of(2026, 7, 15));
        s.setMeetingType("QUARTERLY");
        s.setGeneratedAt(LocalDateTime.of(2026, 7, 15, 9, 0));
        s.setGeneratedByUserId(0L);
        s.setKpisJson("{}");
        s.setIsFinalized(Boolean.FALSE);
        return s;
    }

    private static Risk risk(String title, String level, Integer residual, String status) {
        Risk r = new Risk();
        r.setOrganisationId(ORG_ID);
        r.setExternalId(UUID.randomUUID());
        r.setTitleEn(title);
        r.setTitleAr(title + "-AR");
        r.setStatus(status);
        r.setResidualScore(residual);
        // riskLevel is a computed column; we reflectively set the private field
        // since Lombok's @Setter is generated for it.
        r.setRiskLevel(level);
        return r;
    }

    private static CapaTask capa(String code, String status, LocalDate target) {
        CapaTask c = new CapaTask();
        c.setOrganisationId(ORG_ID);
        c.setExternalId(code);
        c.setStatus(status);
        c.setTargetDate(target);
        c.setOriginType("FINDING");
        c.setOriginId("F-1");
        c.setOwner("owner");
        c.setTitleEn(code);
        c.setTitleAr(code + "-AR");
        c.setCreatedAt(LocalDateTime.now().minusDays(10));
        if ("CLOSED".equals(status)) c.setClosedAt(LocalDateTime.now().minusDays(1));
        return c;
    }

    private static MlModel model(String code, String tier) {
        MlModel m = new MlModel();
        m.setOrganisationId(ORG_ID);
        m.setModelCode(code);
        m.setTitleEn(code);
        m.setRiskTier(tier);
        m.setLifecycleStage("IN_PRODUCTION");
        return m;
    }

    private static JsonNode parseQuietly(ObjectMapper m, String json) {
        try { return m.readTree(json); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
