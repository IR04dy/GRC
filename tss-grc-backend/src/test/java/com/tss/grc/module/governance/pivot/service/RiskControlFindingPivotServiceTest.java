package com.tss.grc.module.governance.pivot.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import com.tss.grc.module.ccm.entity.ControlTestRun;
import com.tss.grc.module.ccm.repository.ControlTestDefinitionRepository;
import com.tss.grc.module.ccm.repository.ControlTestRunRepository;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.FocalEntity;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.PivotResponse;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlAssessment;
import com.tss.grc.module.platform.controls.entity.RiskControlLink;
import com.tss.grc.module.platform.controls.repository.ControlAssessmentRepository;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import com.tss.grc.module.platform.controls.repository.RiskControlLinkRepository;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskControlFindingPivotService — Sprint 11.
 *
 * <p>Locks the three-lens aggregation contract:
 * <ul>
 *   <li>Risk lens — focal risk + linked controls + findings + CAPA + CCM</li>
 *   <li>Control lens — focal control + linked risks + findings + CAPA + CCM</li>
 *   <li>Finding lens — focal finding + linked controls + risks + CAPA</li>
 * </ul>
 * Plus tenant isolation (cross-org = 404), F7+F8 emission, graceful
 * missing-link handling, and the data-completeness calculation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskControlFindingPivotServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG = 2L;

    @Mock private RiskRepository riskRepo;
    @Mock private ControlRepository controlRepo;
    @Mock private RiskControlLinkRepository riskControlLinkRepo;
    @Mock private ControlAssessmentRepository controlAssessmentRepo;
    @Mock private FindingRepository findingRepo;
    @Mock private CapaTaskRepository capaRepo;
    @Mock private com.tss.grc.module.audit_lc.repository.CapaTaskRepository auditLcCapaRepo;
    @Mock private ControlTestDefinitionRepository ccmDefRepo;
    @Mock private ControlTestRunRepository ccmRunRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RiskControlFindingPivotService service;

    @BeforeEach
    void setUp() {
        service = new RiskControlFindingPivotService(
                riskRepo, controlRepo, riskControlLinkRepo,
                controlAssessmentRepo, findingRepo, capaRepo, auditLcCapaRepo,
                ccmDefRepo, ccmRunRepo,
                audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
        // Reasonable empty defaults — individual tests stub the parts they need.
        when(riskRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(controlRepo.findByIdAndOrganisationId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(findingRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(findingRepo.findByOrganisationIdOrderByRaisedAtDesc(anyLong())).thenReturn(List.of());
        when(riskControlLinkRepo.findByOrganisationIdAndRiskIdOrderByIdAsc(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(riskControlLinkRepo.findByOrganisationIdAndControlIdOrderByIdAsc(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(controlAssessmentRepo.findByOrganisationIdAndControlIdOrderByAssessmentDateDesc(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(ccmDefRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(anyLong()))
                .thenReturn(List.of());
        when(ccmRunRepo.findRecentForTest(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(List.of());
        when(auditLcCapaRepo.findByOrganisationIdAndFindingIdOrderByTargetDateAsc(anyLong(), anyString()))
                .thenReturn(List.of());
        when(capaRepo.findByOrganisationIdAndOriginTypeAndOriginId(anyLong(), anyString(), anyString()))
                .thenReturn(List.of());
        when(controlRepo.findByOrganisationIdOrderByDomainAscControlCodeAsc(anyLong()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ─── Risk lens ─────────────────────────────────────────────────

    @Test
    void buildRiskPivot_assemblesAllFiveSections() {
        Risk r = risk(10L, "Customer-data leakage", "CRITICAL", 25);
        Control c1 = ctl(101L, "PDPL-1", "DLP at egress");
        Control c2 = ctl(102L, "ECC-2-7-1", "Vulnerability scanning");
        Finding f = finding(500L, "FIN-2026-001", "DLP misconfig", "[\"PDPL-1\"]");
        com.tss.grc.module.audit_lc.entity.CapaTask cap = legacyCapa(900L, "CAP-1", "Reconfigure DLP", "FIN-2026-001");

        when(riskRepo.findById(10L)).thenReturn(Optional.of(r));
        when(riskControlLinkRepo.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG_ID, 10L))
                .thenReturn(List.of(
                        link(1L, 10L, 101L, "EFFECTIVE"),
                        link(2L, 10L, 102L, "NEEDS_IMPROVEMENT")));
        when(controlRepo.findByIdAndOrganisationId(101L, ORG_ID)).thenReturn(Optional.of(c1));
        when(controlRepo.findByIdAndOrganisationId(102L, ORG_ID)).thenReturn(Optional.of(c2));
        when(findingRepo.findByOrganisationIdOrderByRaisedAtDesc(ORG_ID)).thenReturn(List.of(f));
        when(auditLcCapaRepo.findByOrganisationIdAndFindingIdOrderByTargetDateAsc(ORG_ID, "FIN-2026-001"))
                .thenReturn(List.of(cap));

        PivotResponse out = service.buildRiskPivot(10L);

        assertThat(out.focalEntity()).isEqualTo(FocalEntity.RISK);
        assertThat(out.risk()).isNotNull();
        assertThat(out.risk().titleEn()).isEqualTo("Customer-data leakage");
        assertThat(out.linkedControls()).hasSize(2);
        assertThat(out.linkedControls())
                .extracting("code")
                .containsExactlyInAnyOrder("PDPL-1", "ECC-2-7-1");
        assertThat(out.linkedFindings()).hasSize(1);
        assertThat(out.linkedFindings().get(0).refNumber()).isEqualTo("FIN-2026-001");
        assertThat(out.linkedCapa()).hasSize(1);
        assertThat(out.linkedCapa().get(0).sourceModule()).isEqualTo("AUDIT_LC");
        assertThat(out.ccmCoverage()).isNotNull();
        assertThat(out.summary()).isNotNull();
        assertThat(out.summary().controlsCount()).isEqualTo(2);
        assertThat(out.summary().effectiveControlsCount()).isEqualTo(1);
    }

    @Test
    void buildRiskPivot_missingCcmTests_returnsEmptyStatsNotError() {
        Risk r = risk(11L, "Insider-threat", "HIGH", 16);
        Control c = ctl(201L, "ECC-2-1", "Privileged user review");
        when(riskRepo.findById(11L)).thenReturn(Optional.of(r));
        when(riskControlLinkRepo.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG_ID, 11L))
                .thenReturn(List.of(link(1L, 11L, 201L, "EFFECTIVE")));
        when(controlRepo.findByIdAndOrganisationId(201L, ORG_ID)).thenReturn(Optional.of(c));
        // No ControlTestDefinition rows — common state for a manual control.
        when(ccmDefRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
                .thenReturn(List.of());

        PivotResponse out = service.buildRiskPivot(11L);

        assertThat(out.linkedControls()).hasSize(1);
        assertThat(out.ccmCoverage()).isNotNull();
        assertThat(out.ccmCoverage().totalTests()).isEqualTo(0L);
        assertThat(out.ccmCoverage().passingLast24h()).isEqualTo(0L);
        assertThat(out.ccmCoverage().lastRunAt()).isNull();
    }

    @Test
    void buildRiskPivot_missingCapaForFindings_returnsEmptyArrayNotError() {
        Risk r = risk(12L, "Outage risk", "MEDIUM", 9);
        Control c = ctl(301L, "BCM-1", "DR plan");
        Finding f = finding(700L, "FIN-2026-005", "DR test late", "[\"BCM-1\"]");
        when(riskRepo.findById(12L)).thenReturn(Optional.of(r));
        when(riskControlLinkRepo.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG_ID, 12L))
                .thenReturn(List.of(link(1L, 12L, 301L, "EFFECTIVE")));
        when(controlRepo.findByIdAndOrganisationId(301L, ORG_ID)).thenReturn(Optional.of(c));
        when(findingRepo.findByOrganisationIdOrderByRaisedAtDesc(ORG_ID)).thenReturn(List.of(f));
        // Both CAPA finders return empty — finding has no remediation linked yet.
        when(auditLcCapaRepo.findByOrganisationIdAndFindingIdOrderByTargetDateAsc(ORG_ID, "FIN-2026-005"))
                .thenReturn(List.of());
        when(capaRepo.findByOrganisationIdAndOriginTypeAndOriginId(ORG_ID, "FINDING", "FIN-2026-005"))
                .thenReturn(List.of());

        PivotResponse out = service.buildRiskPivot(12L);

        assertThat(out.linkedFindings()).hasSize(1);
        assertThat(out.linkedCapa()).isEmpty();
    }

    @Test
    void buildRiskPivot_tenantIsolation_crossOrgRisk_returns404() {
        Risk other = risk(13L, "Other tenant", "HIGH", 16);
        other.setOrganisationId(OTHER_ORG);
        when(riskRepo.findById(13L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.buildRiskPivot(13L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Risk not found");
    }

    @Test
    void buildRiskPivot_emitsPivotViewGeneratedF7AndF8() {
        Risk r = risk(14L, "Patch risk", "LOW", 4);
        when(riskRepo.findById(14L)).thenReturn(Optional.of(r));

        service.buildRiskPivot(14L);

        verify(audit, atLeastOnce()).log(eq("PIVOT_VIEW_GENERATED"),
                anyString(), anyLong(), anyString());
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(DomainEvent::triggerEvent)
                .contains("PIVOT_VIEW_GENERATED");
        assertThat(ev.getValue().payload()).containsEntry("focalEntity", "Risk");
    }

    @Test
    void buildRiskPivot_uncontrolledRisk_returnsEmptyControlsAndNoFindings() {
        Risk r = risk(15L, "Brand-new risk, no controls yet", "MEDIUM", 9);
        when(riskRepo.findById(15L)).thenReturn(Optional.of(r));
        when(riskControlLinkRepo.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG_ID, 15L))
                .thenReturn(List.of());

        PivotResponse out = service.buildRiskPivot(15L);

        assertThat(out.linkedControls()).isEmpty();
        assertThat(out.linkedFindings()).isEmpty();
        assertThat(out.linkedCapa()).isEmpty();
        assertThat(out.summary().controlsCount()).isEqualTo(0);
        assertThat(out.summary().dataCompletenessPct()).isLessThanOrEqualTo(50.0);   // missing controls = 3/6 gates max
    }

    // ─── Control lens (inverted) ───────────────────────────────────

    @Test
    void buildControlPivot_invertedLens_centersOnControlAndSurfacesRisks() {
        Control c = ctl(400L, "ECC-2-9", "Backup integrity check");
        Risk r1 = risk(20L, "Backup tampering", "HIGH", 16);
        Risk r2 = risk(21L, "Ransomware", "CRITICAL", 25);
        when(controlRepo.findByIdAndOrganisationId(400L, ORG_ID)).thenReturn(Optional.of(c));
        when(riskControlLinkRepo.findByOrganisationIdAndControlIdOrderByIdAsc(ORG_ID, 400L))
                .thenReturn(List.of(
                        link(1L, 20L, 400L, "EFFECTIVE"),
                        link(2L, 21L, 400L, "NEEDS_IMPROVEMENT")));
        when(riskRepo.findById(20L)).thenReturn(Optional.of(r1));
        when(riskRepo.findById(21L)).thenReturn(Optional.of(r2));

        PivotResponse out = service.buildControlPivot(400L);

        assertThat(out.focalEntity()).isEqualTo(FocalEntity.CONTROL);
        assertThat(out.control()).isNotNull();
        assertThat(out.control().code()).isEqualTo("ECC-2-9");
        assertThat(out.linkedRisks()).hasSize(2);
        assertThat(out.linkedControls()).hasSize(1);   // the focal control itself
        assertThat(out.linkedControls().get(0).code()).isEqualTo("ECC-2-9");
    }

    @Test
    void buildControlPivot_tenantIsolation_crossOrgControl_returns404() {
        when(controlRepo.findByIdAndOrganisationId(500L, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buildControlPivot(500L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Control not found");
    }

    // ─── Finding lens (inverted) ───────────────────────────────────

    @Test
    void buildFindingPivot_invertedLens_centersOnFindingAndResolvesControlChain() {
        Finding f = finding(800L, "FIN-2026-010", "Backup verification gap",
                "[{\"framework\":\"NCA\",\"controlCode\":\"ECC-2-9\"}]");
        Control c = ctl(400L, "ECC-2-9", "Backup integrity check");
        Risk r = risk(25L, "Backup tampering", "HIGH", 16);
        when(findingRepo.findById(800L)).thenReturn(Optional.of(f));
        when(controlRepo.findByOrganisationIdOrderByDomainAscControlCodeAsc(ORG_ID))
                .thenReturn(List.of(c));
        when(riskControlLinkRepo.findByOrganisationIdAndControlIdOrderByIdAsc(ORG_ID, 400L))
                .thenReturn(List.of(link(1L, 25L, 400L, "EFFECTIVE")));
        when(riskRepo.findById(25L)).thenReturn(Optional.of(r));

        PivotResponse out = service.buildFindingPivot(800L);

        assertThat(out.focalEntity()).isEqualTo(FocalEntity.FINDING);
        assertThat(out.finding()).isNotNull();
        assertThat(out.finding().refNumber()).isEqualTo("FIN-2026-010");
        assertThat(out.linkedControls()).hasSize(1);
        assertThat(out.linkedControls().get(0).code()).isEqualTo("ECC-2-9");
        assertThat(out.linkedRisks()).hasSize(1);
        assertThat(out.linkedRisks().get(0).titleEn()).isEqualTo("Backup tampering");
        assertThat(out.linkedFindings()).hasSize(1);   // focal finding mirrored back
    }

    // ─── Helpers ──────────────────────────────────────────────────

    @Test
    void parseControlCodes_acceptsStructuredJsonAndCommaSeparatedFallback() {
        List<String> structured = RiskControlFindingPivotService.parseControlCodes(
                "[{\"framework\":\"NCA\",\"controlCode\":\"ECC-2-1\"},"
                        + "{\"framework\":\"SAMA\",\"controlCode\":\"CSF-1.2\"}]");
        assertThat(structured).containsExactly("ECC-2-1", "CSF-1.2");

        List<String> plain = RiskControlFindingPivotService.parseControlCodes("ECC-2-1, CSF-1.2, BCM-1");
        assertThat(plain).containsExactly("ECC-2-1", "CSF-1.2", "BCM-1");

        List<String> bareArray = RiskControlFindingPivotService.parseControlCodes(
                "[\"ECC-2-1\", \"CSF-1.2\"]");
        assertThat(bareArray).containsExactly("ECC-2-1", "CSF-1.2");

        assertThat(RiskControlFindingPivotService.parseControlCodes(null)).isEmpty();
        assertThat(RiskControlFindingPivotService.parseControlCodes("")).isEmpty();
    }

    @Test
    void computeCompleteness_scaleIs0to100AndGatesAreSensible() {
        // Empty data → first 3 gates fail (no controls), gates 4-6 trivially pass.
        double empty = RiskControlFindingPivotService.computeCompleteness(
                List.of(), List.of(), List.of(), List.of());
        assertThat(empty).isBetween(45.0, 55.0);   // ~3/6 gates pass

        // Worst case: 1 untested control, no other data.
        var ctrl = new com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.ControlPivot(
                1L, "X", "Control", "Control AR", "NOT_TESTED", null, null, "RISK", null);
        double low = RiskControlFindingPivotService.computeCompleteness(
                List.of(ctrl), List.of(), List.of(), List.of());
        // Gate 1 passes (has a control). Gates 4-6 pass trivially. Total = 4/6.
        assertThat(low).isBetween(60.0, 70.0);

        // Best case: tested control with last run + a risk with residual score.
        var goodCtrl = new com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.ControlPivot(
                1L, "X", "Control", "Control AR", "EFFECTIVE",
                "PASS", LocalDateTime.now(), "RISK", null);
        var goodRisk = new com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.RiskPivot(
                10L, UUID.randomUUID().toString(), "R", "R-AR", "HIGH", 16, "OPEN");
        double full = RiskControlFindingPivotService.computeCompleteness(
                List.of(goodCtrl), List.of(), List.of(), List.of(goodRisk));
        assertThat(full).isEqualTo(100.0);
    }

    // ─── Fixtures ──────────────────────────────────────────────────

    private static Risk risk(Long id, String title, String level, Integer residual) {
        Risk r = new Risk();
        r.setId(id);
        r.setOrganisationId(ORG_ID);
        r.setExternalId(UUID.randomUUID());
        r.setTitleEn(title);
        r.setTitleAr(title + "-AR");
        r.setStatus("OPEN");
        r.setRiskLevel(level);
        r.setResidualScore(residual);
        return r;
    }

    private static Control ctl(Long id, String code, String title) {
        Control c = new Control();
        c.setId(id);
        c.setOrganisationId(ORG_ID);
        c.setControlCode(code);
        c.setTitleEn(title);
        c.setTitleAr(title + "-AR");
        c.setDomain("RISK");
        c.setControlType("PREVENTIVE");
        c.setAutomationLevel("MANUAL");
        c.setStatus("ACTIVE");
        return c;
    }

    private static Finding finding(Long id, String externalId, String title, String crosswalk) {
        Finding f = new Finding();
        f.setId(id);
        f.setOrganisationId(ORG_ID);
        f.setExternalId(externalId);
        f.setRefNumber(externalId);
        f.setEngagementCode("ENG-2026-01");
        f.setTitleEn(title);
        f.setTitleAr(title + "-AR");
        f.setSeverity("HIGH");
        f.setStatus("OPEN");
        f.setControlCrosswalk(crosswalk);
        f.setConditionEn("c");
        f.setConditionAr("c");
        f.setRaisedAt(LocalDateTime.now());
        return f;
    }

    private static com.tss.grc.module.audit_lc.entity.CapaTask legacyCapa(
            Long id, String externalId, String title, String findingExternalId) {
        com.tss.grc.module.audit_lc.entity.CapaTask t = new com.tss.grc.module.audit_lc.entity.CapaTask();
        t.setId(id);
        t.setOrganisationId(ORG_ID);
        t.setExternalId(externalId);
        t.setFindingId(findingExternalId);
        t.setKind("CORRECTIVE");
        t.setTitleEn(title);
        t.setTitleAr(title + "-AR");
        t.setOwnerRole("CISO");
        t.setTargetDate(LocalDate.now().plusDays(30));
        t.setStatus("OPEN");
        return t;
    }

    private static RiskControlLink link(Long id, Long riskId, Long controlId, String effectiveness) {
        RiskControlLink l = new RiskControlLink();
        l.setId(id);
        l.setOrganisationId(ORG_ID);
        l.setRiskId(riskId);
        l.setControlId(controlId);
        l.setLinkType("MITIGATES");
        l.setEffectiveness(effectiveness);
        return l;
    }
}
