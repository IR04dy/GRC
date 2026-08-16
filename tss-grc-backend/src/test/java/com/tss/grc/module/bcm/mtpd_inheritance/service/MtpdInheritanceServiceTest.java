package com.tss.grc.module.bcm.mtpd_inheritance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentDetailResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.ComputeRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.PublishRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.entity.MtpdInheritanceAssessment;
import com.tss.grc.module.bcm.mtpd_inheritance.entity.MtpdInheritanceFinding;
import com.tss.grc.module.bcm.mtpd_inheritance.entity.ProcessVendorDependency;
import com.tss.grc.module.bcm.mtpd_inheritance.repository.MtpdInheritanceAssessmentRepository;
import com.tss.grc.module.bcm.mtpd_inheritance.repository.MtpdInheritanceFindingRepository;
import com.tss.grc.module.bcm.mtpd_inheritance.repository.ProcessVendorDependencyRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MtpdInheritanceService} (Slice 10-E).
 *
 * <p>Twelve tests exercising the compute engine + downstream actions:
 * <ul>
 *   <li>Severity classification matrix (CRITICAL/HIGH/MEDIUM/LOW)</li>
 *   <li>Compute happy path with mixed BREACH / WARNING / OK / NO_ATTESTATION</li>
 *   <li>Compute when no accepted attestation exists for a vendor</li>
 *   <li>Recommendation text generation cites SAMA / ISO clauses</li>
 *   <li>Publish toggles {@code isPublished} and is idempotent</li>
 *   <li>CAPA creation forwards to CapaTaskService and links the finding</li>
 *   <li>CAPA creation rejected if finding already has a CAPA</li>
 *   <li>F8 events fired on compute / publish / CAPA</li>
 *   <li>Tenant isolation on the list reads</li>
 *   <li>Dependency CRUD persists and rejects wrong-tenant references</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MtpdInheritanceServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private ProcessVendorDependencyRepository depRepo;
    @Mock private MtpdInheritanceAssessmentRepository assessRepo;
    @Mock private MtpdInheritanceFindingRepository findingRepo;
    @Mock private CriticalProcessRepository processRepo;
    @Mock private BiaRecordRepository biaRepo;
    @Mock private VendorRepository vendorRepo;
    @Mock private VendorBcpAttestationRepository attestationRepo;
    @Mock private VendorBcpCampaignRepository campaignRepo;
    @Mock private CapaTaskService capaService;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private MtpdInheritanceService service;

    @BeforeEach void setUp() {
        // F2-224 — the service resolves the actor from CurrentActor now.
        authAs("bcm.manager", 51L); TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static CriticalProcess process(Long id, String en, String ar) {
        CriticalProcess p = new CriticalProcess();
        p.setId(id);
        p.setOrganisationId(ORG_ID);
        p.setProcessNameEn(en);
        p.setProcessNameAr(ar);
        return p;
    }

    private static Vendor vendor(Long id, String name, String nameAr) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setOrganisationId(ORG_ID);
        v.setVendorName(name);
        v.setVendorNameAr(nameAr);
        return v;
    }

    private static ProcessVendorDependency dep(Long id, Long pid, Long vid, String type, String strength) {
        ProcessVendorDependency d = new ProcessVendorDependency();
        d.setId(id);
        d.setOrganisationId(ORG_ID);
        d.setBusinessProcessId(pid);
        d.setVendorId(vid);
        d.setDependencyType(type);
        d.setDependencyStrength(strength);
        return d;
    }

    private static BiaRecord bia(Long pid, Integer mtpd) {
        BiaRecord b = new BiaRecord();
        b.setProcessId(pid);
        b.setMtpdHours(mtpd);
        b.setAssessmentDate(LocalDate.now().minusDays(30));
        return b;
    }

    private static VendorBcpCampaign campaign(Long id, String code) {
        VendorBcpCampaign c = new VendorBcpCampaign();
        c.setId(id);
        c.setOrganisationId(ORG_ID);
        c.setCampaignCode(code);
        return c;
    }

    private static VendorBcpAttestation attestation(Long id, Long campaignId, Long vendorId, Integer mtpd) {
        VendorBcpAttestation a = new VendorBcpAttestation();
        a.setId(id);
        a.setOrganisationId(ORG_ID);
        a.setCampaignId(campaignId);
        a.setVendorId(vendorId);
        a.setMtpdHoursAttested(mtpd);
        a.setStatus("ACCEPTED");
        a.setSubmittedAt(LocalDateTime.now().minusDays(7));
        return a;
    }

    // ─── 1. Severity matrix ─────────────────────────────────────────

    @Test void severityMatrix_mapsCorrectly() {
        assertThat(MtpdInheritanceService.computeSeverity("BREACH", "TIGHT")).isEqualTo("CRITICAL");
        assertThat(MtpdInheritanceService.computeSeverity("BREACH", "LOOSE")).isEqualTo("HIGH");
        assertThat(MtpdInheritanceService.computeSeverity("BREACH", "TRANSIENT")).isEqualTo("MEDIUM");
        assertThat(MtpdInheritanceService.computeSeverity("WARNING", "TIGHT")).isEqualTo("HIGH");
        assertThat(MtpdInheritanceService.computeSeverity("WARNING", "LOOSE")).isEqualTo("MEDIUM");
        assertThat(MtpdInheritanceService.computeSeverity("NO_ATTESTATION", "TIGHT")).isEqualTo("HIGH");
        assertThat(MtpdInheritanceService.computeSeverity("OK", "TIGHT")).isEqualTo("LOW");
    }

    // ─── 2. Recommendation text contains regulator citations ─────────

    @Test void recommendation_breach_citesSamaAndIso() {
        CriticalProcess p = process(11L, "Branch payment processing", "معالجة مدفوعات الفروع");
        Vendor v = vendor(21L, "SARIE", "سريع");
        String[] recs = MtpdInheritanceService.buildRecommendation("BREACH", "CRITICAL", "TIGHT", p, v, 4, 8);
        assertThat(recs[0]).contains("SAMA BCF §3.4", "ISO 22301 §8.2");
        assertThat(recs[0]).contains("Branch payment processing", "SARIE");
        // Saudi-native Arabic — "خرق" not "انتهاك", references مؤسسة النقد.
        assertThat(recs[1]).contains("خرق", "مؤسسة النقد", "سريع");
    }

    @Test void recommendation_noAttestation_citesNca() {
        CriticalProcess p = process(11L, "KYC", "اعرف عميلك");
        Vendor v = vendor(21L, "Tawakkalna", "توكلنا");
        String[] recs = MtpdInheritanceService.buildRecommendation("NO_ATTESTATION", "HIGH", "TIGHT", p, v, 4, null);
        assertThat(recs[0]).contains("NCA ECC-2", "4-2-3");
        assertThat(recs[1]).contains("غياب الإقرار", "الأمن السيبراني");
    }

    // ─── 3. Compute — mixed findings ────────────────────────────────

    @Test void compute_mixedFindings_persistsHeaderAndFindings() {
        // Setup:
        //   process 11 (MTPD=4h) ← vendor 21 (attested 8h) → BREACH, TIGHT → CRITICAL
        //   process 12 (MTPD=24h) ← vendor 22 (attested 20h) → WARNING (83% of 24h)
        //   process 13 (MTPD=72h) ← vendor 23 (attested 24h) → OK
        //   process 14 (MTPD=4h) ← vendor 24 (no attestation) → NO_ATTESTATION
        CriticalProcess p1 = process(11L, "Branch payment processing", "معالجة مدفوعات الفروع");
        CriticalProcess p2 = process(12L, "Mobile banking", "الخدمات المصرفية عبر الجوال");
        CriticalProcess p3 = process(13L, "Treasury reporting", "تقارير الخزينة");
        CriticalProcess p4 = process(14L, "Customer onboarding", "تسجيل العملاء");
        Vendor v1 = vendor(21L, "SARIE", "سريع");
        Vendor v2 = vendor(22L, "Mada Payment", "مدى");
        Vendor v3 = vendor(23L, "IBM TM1", "IBM");
        Vendor v4 = vendor(24L, "Tawakkalna", "توكلنا");

        List<ProcessVendorDependency> edges = List.of(
            dep(101L, 11L, 21L, "REQUIRED", "TIGHT"),
            dep(102L, 12L, 22L, "REQUIRED", "TIGHT"),
            dep(103L, 13L, 23L, "RECOMMENDED", "LOOSE"),
            dep(104L, 14L, 24L, "REQUIRED", "TIGHT")
        );
        when(depRepo.findByOrganisationIdAndDependencyTypeInAndIsDeletedFalse(eq(ORG_ID), anyList()))
            .thenReturn(edges);
        when(processRepo.findById(11L)).thenReturn(Optional.of(p1));
        when(processRepo.findById(12L)).thenReturn(Optional.of(p2));
        when(processRepo.findById(13L)).thenReturn(Optional.of(p3));
        when(processRepo.findById(14L)).thenReturn(Optional.of(p4));
        when(vendorRepo.findById(21L)).thenReturn(Optional.of(v1));
        when(vendorRepo.findById(22L)).thenReturn(Optional.of(v2));
        when(vendorRepo.findById(23L)).thenReturn(Optional.of(v3));
        when(vendorRepo.findById(24L)).thenReturn(Optional.of(v4));
        when(biaRepo.findByProcessIdIn(anyList())).thenReturn(List.of(
            bia(11L, 4), bia(12L, 24), bia(13L, 72), bia(14L, 4)
        ));

        VendorBcpCampaign c1 = campaign(900L, "VBCP-2026-ANNUAL");
        when(campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of(c1));
        when(attestationRepo.findByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                ORG_ID, 900L, "ACCEPTED"))
            .thenReturn(List.of(
                attestation(701L, 900L, 21L, 8),
                attestation(702L, 900L, 22L, 20),
                attestation(703L, 900L, 23L, 24)
                // vendor 24 has no attestation
            ));

        when(assessRepo.save(any(MtpdInheritanceAssessment.class))).thenAnswer(inv -> {
            MtpdInheritanceAssessment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(5001L);
            return a;
        });
        when(findingRepo.save(any(MtpdInheritanceFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        AssessmentResponse out = service.computeAssessment(new ComputeRequest(2026, "Q2", null, null));

        assertThat(out.totalBreaches()).isEqualTo(1);
        assertThat(out.totalWarnings()).isEqualTo(1);
        assertThat(out.totalOk()).isEqualTo(1);
        assertThat(out.totalNoAttestation()).isEqualTo(1);
        assertThat(out.totalProcesses()).isEqualTo(4);
        assertThat(out.assessmentCode()).contains("MTPD-INH-2026-Q2");
        // Four finding rows persisted.
        verify(findingRepo, times(4)).save(any(MtpdInheritanceFinding.class));
    }

    // ─── 4. Compute — no attestation flagged, not silently dropped ──

    @Test void compute_noAttestation_flaggedNotSkipped() {
        CriticalProcess p = process(11L, "Branch payment processing", "معالجة مدفوعات الفروع");
        Vendor v = vendor(21L, "Acme Cloud", "أكمي السحابي");

        when(depRepo.findByOrganisationIdAndDependencyTypeInAndIsDeletedFalse(eq(ORG_ID), anyList()))
            .thenReturn(List.of(dep(101L, 11L, 21L, "REQUIRED", "TIGHT")));
        when(processRepo.findById(11L)).thenReturn(Optional.of(p));
        when(vendorRepo.findById(21L)).thenReturn(Optional.of(v));
        when(biaRepo.findByProcessIdIn(anyList())).thenReturn(List.of(bia(11L, 4)));
        // No campaigns at all → no attestations.
        when(campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of());

        when(assessRepo.save(any(MtpdInheritanceAssessment.class))).thenAnswer(inv -> {
            MtpdInheritanceAssessment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(5001L);
            return a;
        });
        List<MtpdInheritanceFinding> saved = new ArrayList<>();
        when(findingRepo.save(any(MtpdInheritanceFinding.class))).thenAnswer(inv -> {
            MtpdInheritanceFinding f = inv.getArgument(0);
            saved.add(f);
            return f;
        });

        AssessmentResponse out = service.computeAssessment(new ComputeRequest(2026, null, null, null));

        assertThat(out.totalNoAttestation()).isEqualTo(1);
        assertThat(out.totalBreaches()).isZero();
        assertThat(saved).singleElement()
            .satisfies(f -> {
                assertThat(f.getFindingType()).isEqualTo("NO_ATTESTATION");
                assertThat(f.getSeverity()).isEqualTo("HIGH");   // TIGHT + NO_ATTESTATION
                assertThat(f.getVendorAttestedMtpdHours()).isNull();
                // Denormalised names preserved.
                assertThat(f.getBusinessProcessNameEn()).isEqualTo("Branch payment processing");
                assertThat(f.getVendorName()).isEqualTo("Acme Cloud");
            });
    }

    // ─── 5. Compute — F8 event emitted ──────────────────────────────

    @Test void compute_emitsAssessmentComputedEvent() {
        when(depRepo.findByOrganisationIdAndDependencyTypeInAndIsDeletedFalse(eq(ORG_ID), anyList()))
            .thenReturn(List.of());
        when(campaignRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(ORG_ID))
            .thenReturn(List.of());
        when(assessRepo.save(any(MtpdInheritanceAssessment.class))).thenAnswer(inv -> {
            MtpdInheritanceAssessment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(5001L);
            return a;
        });

        service.computeAssessment(new ComputeRequest(2026, "Q2", null, null));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        DomainEvent ev = captor.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("MTPD_ASSESSMENT_COMPUTED");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("cycleYear", "2026");
    }

    // ─── 6. Publish — idempotent + F8 ───────────────────────────────

    @Test void publish_setsFlagAndEmitsEvent() {
        MtpdInheritanceAssessment a = new MtpdInheritanceAssessment();
        a.setId(5001L);
        a.setOrganisationId(ORG_ID);
        a.setAssessmentCode("MTPD-INH-2026-Q2-01");
        a.setIsPublished(false);
        when(assessRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 5001L)).thenReturn(Optional.of(a));
        when(assessRepo.save(any(MtpdInheritanceAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        AssessmentResponse out = service.publishAssessment(5001L,
            new PublishRequest("Audit-ready snapshot.", "صورة جاهزة للمراجعة الرقابية."));

        assertThat(out.isPublished()).isTrue();
        assertThat(out.publishedAt()).isNotNull();
        // F2-224 — was isEqualTo(42L), the id the CALLER passed. The publisher of an MTPD
        // assessment for the audit committee is the authenticated principal.
        assertThat(out.publishedByUserId()).isEqualTo(51L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("MTPD_ASSESSMENT_PUBLISHED");
    }

    @Test void publish_idempotent_secondCallNoOp() {
        MtpdInheritanceAssessment a = new MtpdInheritanceAssessment();
        a.setId(5001L);
        a.setOrganisationId(ORG_ID);
        a.setIsPublished(true);
        a.setPublishedAt(LocalDateTime.now().minusDays(1));
        when(assessRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 5001L)).thenReturn(Optional.of(a));

        AssessmentResponse out = service.publishAssessment(5001L, null);

        assertThat(out.isPublished()).isTrue();
        verify(assessRepo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    // ─── 7. CAPA creation links the finding ─────────────────────────

    @Test void createCapa_linksFindingAndEmitsEvent() {
        MtpdInheritanceFinding f = new MtpdInheritanceFinding();
        f.setId(9001L);
        f.setOrganisationId(ORG_ID);
        f.setAssessmentId(5001L);
        f.setBusinessProcessId(11L);
        f.setBusinessProcessNameEn("Branch payment processing");
        f.setBusinessProcessNameAr("معالجة مدفوعات الفروع");
        f.setVendorId(21L);
        f.setVendorName("SARIE");
        f.setFindingType("BREACH");
        f.setSeverity("CRITICAL");
        f.setDependencyStrength("TIGHT");
        f.setDependencyType("REQUIRED");
        f.setRecommendationEn("Renegotiate vendor SLA.");
        f.setRecommendationAr("إعادة التفاوض على اتفاقية مستوى الخدمة.");
        when(findingRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 9001L)).thenReturn(Optional.of(f));

        CapaTaskDto stubCapa = CapaTaskDto.builder()
            .id("CAPA-2026-0042")
            .numericId(424242L)
            .status("OPEN")
            .build();
        when(capaService.create(any(CapaTaskDto.class))).thenReturn(stubCapa);
        when(findingRepo.save(any(MtpdInheritanceFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        CapaCreateResponse out = service.createCapaForFinding(9001L,
            new CapaCreateRequest("BCM Manager", null, "Add note.", "ملاحظة."));

        assertThat(out.capaTaskId()).isEqualTo(424242L);
        assertThat(out.capaExternalId()).isEqualTo("CAPA-2026-0042");
        // Finding now carries the link.
        assertThat(f.getCapaTaskId()).isEqualTo(424242L);
        assertThat(f.getCapaCreatedAt()).isNotNull();

        // Forward DTO had Saudi-native title in both languages.
        ArgumentCaptor<CapaTaskDto> dtoCap = ArgumentCaptor.forClass(CapaTaskDto.class);
        verify(capaService).create(dtoCap.capture());
        assertThat(dtoCap.getValue().getTitleEn()).contains("MTPD inheritance", "BREACH", "Branch payment processing", "SARIE");
        assertThat(dtoCap.getValue().getTitleAr()).contains("وراثة المهلة القصوى");
        assertThat(dtoCap.getValue().getOriginType()).isEqualTo("bcm.mtpd_finding");

        ArgumentCaptor<DomainEvent> evCap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(evCap.capture());
        assertThat(evCap.getValue().triggerEvent()).isEqualTo("MTPD_FINDING_CAPA_CREATED");
    }

    @Test void createCapa_rejectedIfAlreadyExists() {
        MtpdInheritanceFinding f = new MtpdInheritanceFinding();
        f.setId(9001L);
        f.setOrganisationId(ORG_ID);
        f.setCapaTaskId(424242L);     // already linked
        when(findingRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 9001L)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.createCapaForFinding(9001L,
            new CapaCreateRequest("X", null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CAPA already exists");
        verify(capaService, never()).create(any());
    }

    // ─── 8. Tenant isolation ────────────────────────────────────────

    @Test void listAssessments_filtersByOrg() {
        when(assessRepo.findByOrganisationIdAndIsDeletedFalseOrderByComputedAtDesc(ORG_ID))
            .thenReturn(List.of());
        service.listAssessments();
        verify(assessRepo).findByOrganisationIdAndIsDeletedFalseOrderByComputedAtDesc(ORG_ID);
    }

    // ─── 9. Dependency CRUD ─────────────────────────────────────────

    @Test void createDependency_persistsAndEmitsEvent() {
        when(processRepo.findById(11L)).thenReturn(Optional.of(process(11L, "Process", "عملية")));
        when(vendorRepo.findById(21L)).thenReturn(Optional.of(vendor(21L, "Vendor", "مورد")));
        when(depRepo.save(any(ProcessVendorDependency.class))).thenAnswer(inv -> {
            ProcessVendorDependency d = inv.getArgument(0);
            if (d.getId() == null) d.setId(101L);
            return d;
        });

        DependencyResponse out = service.createDependency(new DependencyCreateRequest(
            11L, 21L, "REQUIRED", "TIGHT", "Note.", "ملاحظة."));

        assertThat(out.businessProcessId()).isEqualTo(11L);
        assertThat(out.vendorId()).isEqualTo(21L);
        assertThat(out.dependencyType()).isEqualTo("REQUIRED");
        assertThat(out.dependencyStrength()).isEqualTo("TIGHT");

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().triggerEvent()).isEqualTo("MTPD_DEPENDENCY_CREATED");
    }

    @Test void createDependency_rejectsWrongTenantProcess() {
        CriticalProcess p = process(11L, "Process", "عملية");
        p.setOrganisationId(999L);   // belongs to another tenant
        when(processRepo.findById(11L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.createDependency(new DependencyCreateRequest(
            11L, 21L, "REQUIRED", "TIGHT", null, null)))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(depRepo, never()).save(any());
    }

    // ─── 10. Assessment detail returns findings sorted by severity ──

    @Test void getAssessmentDetail_returnsFindingsSortedBySeverity() {
        MtpdInheritanceAssessment a = new MtpdInheritanceAssessment();
        a.setId(5001L);
        a.setOrganisationId(ORG_ID);
        a.setAssessmentCode("MTPD-INH-2026-Q2-01");
        when(assessRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 5001L)).thenReturn(Optional.of(a));

        MtpdInheritanceFinding low = new MtpdInheritanceFinding();
        low.setSeverity("LOW"); low.setBusinessProcessNameEn("X"); low.setFindingType("OK");
        low.setDependencyStrength("LOOSE"); low.setDependencyType("REQUIRED");
        low.setVendorName("V1");
        MtpdInheritanceFinding critical = new MtpdInheritanceFinding();
        critical.setSeverity("CRITICAL"); critical.setBusinessProcessNameEn("A");
        critical.setFindingType("BREACH"); critical.setDependencyStrength("TIGHT");
        critical.setDependencyType("REQUIRED"); critical.setVendorName("V2");

        when(findingRepo.findByOrganisationIdAndAssessmentIdAndIsDeletedFalseOrderBySeverityAscBusinessProcessNameEnAsc(
                ORG_ID, 5001L))
            .thenReturn(List.of(low, critical));

        AssessmentDetailResponse detail = service.getAssessmentDetail(5001L);

        // Critical comes first because severityRank(CRITICAL)=0 < severityRank(LOW)=3.
        assertThat(detail.findings()).hasSize(2);
        assertThat(detail.findings().get(0).severity()).isEqualTo("CRITICAL");
        assertThat(detail.findings().get(1).severity()).isEqualTo("LOW");
    }

    /**
     * F2-224 — a real principal in the SecurityContext, because the service now takes the actor
     * from {@code CurrentActor} rather than from the request. {@code CurrentActor} requires the
     * principal to be a {@link com.tss.grc.security.GrcUserDetails}, so a bare username token
     * would resolve to null and the assertion would be about nothing.
     */
    private static void authAs(String username, Long userId) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .setAuthentication(new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(
                    new com.tss.grc.security.GrcUserDetails(
                        userId, 1L, username, "x", true, true, java.util.List.of()),
                    "x", java.util.List.of()));
    }

}
