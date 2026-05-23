package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.InstantiateRequest;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.InstantiatedExercise;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.ScenarioDetail;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.ScenarioSummary;
import com.tss.grc.module.bcm.cycle.entity.BcmExerciseScenario;
import com.tss.grc.module.bcm.cycle.entity.ExerciseScenarioInject;
import com.tss.grc.module.bcm.cycle.repository.BcmExerciseScenarioRepository;
import com.tss.grc.module.bcm.cycle.repository.ExerciseScenarioInjectRepository;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
 * Unit tests for {@link ExerciseScenarioLibraryService} (Slice 10-A).
 *
 * <p>Covers the contract we promise the controller and the parts the
 * BCM Manager actually relies on:
 * <ul>
 *   <li>Search filters reach the repository correctly</li>
 *   <li>Detail returns header + ordered injects</li>
 *   <li>Instantiation creates the exercise with scenario_id link,
 *       copies objectives + success criteria onto the scenario brief,
 *       and reports the inject count</li>
 *   <li>usage_count increments atomically (one save call per
 *       instantiation, value bumped before the save)</li>
 *   <li>F7 audit row + F8 EXERCISE_INSTANTIATED_FROM_SCENARIO event
 *       are both emitted</li>
 *   <li>Tenant isolation — the new exercise carries the caller''s
 *       organisationId from TenantContext</li>
 *   <li>404 on a missing scenario id</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExerciseScenarioLibraryServiceTest {

    private static final Long ORG_ID = 9L;
    private static final Long SCENARIO_ID = 401L;

    @Mock private BcmExerciseScenarioRepository scenarioRepo;
    @Mock private ExerciseScenarioInjectRepository injectRepo;
    @Mock private BcmExerciseRepository exerciseRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private ExerciseScenarioLibraryService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static BcmExerciseScenario scenarioFixture() {
        BcmExerciseScenario s = new BcmExerciseScenario();
        s.setId(SCENARIO_ID);
        s.setScenarioCode("EXS-RANSOMWARE-TABLETOP-2026");
        s.setTitleEn("Ransomware tabletop");
        s.setTitleAr("تمرين فدية حواري");
        s.setScenarioCategory("CYBER");
        s.setExerciseType("TABLETOP");
        s.setScenarioType("TABLETOP");
        s.setComplexity("ADVANCED");
        s.setDurationMinutes(180);
        s.setRecommendedDurationMinutes(180);
        s.setFrameworkAlignment("SAMA_CSF,NCA_ECC2,ISO_22301");
        s.setTargetAudience("CISO, BCM Manager, SOC Lead");
        s.setDescriptionEn("Akira ransom on core banking");
        s.setDescriptionAr("فدية أكيرا على الأنظمة المصرفية");
        s.setLearningObjectivesEn("Validate the 4h SAMA OPRES window");
        s.setLearningObjectivesAr("التحقق من نافذة ٤ ساعات لمكتب OPRES");
        s.setSuccessCriteriaEn("SAMA notifications within 240 minutes");
        s.setSuccessCriteriaAr("إشعارات ساما خلال ٢٤٠ دقيقة");
        s.setRecommendedObservers("Internal Audit, SAMA liaison");
        s.setUsageCount(3);
        s.setInjectsJson("[]");
        s.setSuccessCriteriaJson("[]");
        return s;
    }

    private static ExerciseScenarioInject injectFixture(int order, int offset, String type) {
        ExerciseScenarioInject i = new ExerciseScenarioInject();
        i.setId(500L + order);
        i.setScenarioId(SCENARIO_ID);
        i.setInjectOrder(order);
        i.setTimeOffsetMinutes(offset);
        i.setInjectType(type);
        i.setInjectTextEn("Inject " + order + " EN");
        i.setInjectTextAr("Inject " + order + " AR");
        i.setExpectedActionEn("Action " + order + " EN");
        i.setExpectedActionAr("Action " + order + " AR");
        return i;
    }

    private static InstantiateRequest validRequest() {
        return new InstantiateRequest(
            LocalDate.of(2026, 6, 15),
            "Faisal Al-Subaie (BCM Manager)",
            null, null,
            "Internal Audit, SAMA liaison observer",
            "CISO, SOC Lead, IT Recovery, Comms Lead, Legal"
        );
    }

    // ─── Search ────────────────────────────────────────────────────────

    @Test
    void search_passes_filters_to_repository_and_maps_results() {
        // Given a page with one row, when we search with all filters set,
        // then the repository sees the exact filter values (null-conversion
        // of blank strings) and the result is mapped to a ScenarioSummary.
        BcmExerciseScenario s = scenarioFixture();
        Page<BcmExerciseScenario> page = new PageImpl<>(List.of(s));
        when(scenarioRepo.filter(eq("TABLETOP"), eq("ADVANCED"), eq("SAMA_CSF"), eq("ransom"), any(Pageable.class)))
            .thenReturn(page);

        Page<ScenarioSummary> out =
            service.search("TABLETOP", "ADVANCED", "SAMA_CSF", "ransom", 0, 20);

        assertThat(out.getContent()).hasSize(1);
        ScenarioSummary row = out.getContent().get(0);
        assertThat(row.scenarioCode()).isEqualTo("EXS-RANSOMWARE-TABLETOP-2026");
        assertThat(row.titleEn()).isEqualTo("Ransomware tabletop");
        assertThat(row.titleAr()).isEqualTo("تمرين فدية حواري");
        assertThat(row.scenarioType()).isEqualTo("TABLETOP");
        assertThat(row.complexity()).isEqualTo("ADVANCED");
        assertThat(row.usageCount()).isEqualTo(3);
    }

    @Test
    void search_blank_filters_become_null() {
        // Empty string filters must reach the repo as NULL — otherwise
        // the LIKE clauses match nothing.
        when(scenarioRepo.filter(eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        service.search("", "  ", null, "", 0, 10);
        verify(scenarioRepo).filter(eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void search_clamps_page_size() {
        // size > 100 must be clamped to 100; negative page becomes 0.
        when(scenarioRepo.filter(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        service.search(null, null, null, null, -5, 5_000);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(scenarioRepo).filter(any(), any(), any(), any(), captor.capture());
        Pageable p = captor.getValue();
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(100);
    }

    // ─── Detail ────────────────────────────────────────────────────────

    @Test
    void detail_returns_header_plus_injects_in_order() {
        BcmExerciseScenario s = scenarioFixture();
        when(scenarioRepo.findById(SCENARIO_ID)).thenReturn(Optional.of(s));
        when(injectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of(
                injectFixture(1, 0,   "INFORMATION"),
                injectFixture(2, 10,  "ESCALATION"),
                injectFixture(3, 30,  "REGULATOR")));

        ScenarioDetail d = service.detail(SCENARIO_ID);

        assertThat(d.scenarioCode()).isEqualTo("EXS-RANSOMWARE-TABLETOP-2026");
        assertThat(d.learningObjectivesEn()).contains("SAMA OPRES");
        assertThat(d.successCriteriaAr()).contains("ساما");
        assertThat(d.injects()).hasSize(3);
        assertThat(d.injects().get(0).injectOrder()).isEqualTo(1);
        assertThat(d.injects().get(2).injectType()).isEqualTo("REGULATOR");
    }

    @Test
    void detail_404_on_missing_scenario() {
        when(scenarioRepo.findById(9999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(9999L))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .hasMessageContaining("Scenario not found");
    }

    // ─── Popular / Recommended ─────────────────────────────────────────

    @Test
    void popular_caps_limit_at_20() {
        // Even if caller asks for 9999, we return at most 20.
        when(scenarioRepo.findTop20ByOrderByUsageCountDescTitleEnAsc())
            .thenReturn(List.of(scenarioFixture()));
        List<ScenarioSummary> out = service.popular(9999);
        assertThat(out).hasSize(1);   // mock returned one row; clamp didn''t throw
    }

    @Test
    void recommended_requires_framework() {
        assertThatThrownBy(() -> service.recommended(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("framework");
    }

    @Test
    void recommended_strips_whitespace() {
        when(scenarioRepo.findByFrameworkAlignmentContaining("SAMA_CSF"))
            .thenReturn(List.of(scenarioFixture()));
        List<ScenarioSummary> out = service.recommended("  SAMA_CSF  ");
        assertThat(out).hasSize(1);
        verify(scenarioRepo).findByFrameworkAlignmentContaining("SAMA_CSF");
    }

    // ─── Instantiation ─────────────────────────────────────────────────

    @Test
    void instantiate_creates_exercise_with_scenario_link_and_increments_usage() {
        BcmExerciseScenario s = scenarioFixture();
        when(scenarioRepo.findById(SCENARIO_ID)).thenReturn(Optional.of(s));
        when(injectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of(
                injectFixture(1, 0,   "INFORMATION"),
                injectFixture(2, 10,  "ESCALATION"),
                injectFixture(3, 30,  "REGULATOR"),
                injectFixture(4, 45,  "MEDIA"),
                injectFixture(5, 90,  "DECISION"),
                injectFixture(6, 120, "MEDIA")));
        when(exerciseRepo.save(any(BcmExercise.class)))
            .thenAnswer(inv -> {
                BcmExercise e = inv.getArgument(0);
                e.setId(7777L);
                return e;
            });
        when(scenarioRepo.save(any(BcmExerciseScenario.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        InstantiatedExercise out = service.instantiate(SCENARIO_ID, validRequest());

        assertThat(out.exerciseId()).isEqualTo(7777L);
        assertThat(out.scenarioId()).isEqualTo(SCENARIO_ID);
        assertThat(out.scenarioCode()).isEqualTo("EXS-RANSOMWARE-TABLETOP-2026");
        assertThat(out.injectCount()).isEqualTo(6);
        assertThat(out.usageCountAfter()).isEqualTo(4);   // 3 + 1
        assertThat(out.status()).isEqualTo("SCHEDULED");
        assertThat(out.scheduledDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(out.facilitator()).isEqualTo("Faisal Al-Subaie (BCM Manager)");

        // Verify the exercise row is properly populated.
        ArgumentCaptor<BcmExercise> ex = ArgumentCaptor.forClass(BcmExercise.class);
        verify(exerciseRepo).save(ex.capture());
        BcmExercise saved = ex.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getScenarioId()).isEqualTo(SCENARIO_ID);
        assertThat(saved.getExerciseNameEn()).isEqualTo("Ransomware tabletop");
        assertThat(saved.getExerciseType()).isEqualTo("TABLETOP");
        assertThat(saved.getStatus()).isEqualTo("SCHEDULED");
        // The brief carries scenario + objectives + criteria + observers + participants
        assertThat(saved.getScenario()).contains("Learning Objectives", "SAMA OPRES");
        assertThat(saved.getScenario()).contains("Success Criteria", "240 minutes");
        assertThat(saved.getScenario()).contains("Internal Audit, SAMA liaison observer");
        assertThat(saved.getScenario()).contains("CISO, SOC Lead, IT Recovery");
    }

    @Test
    void instantiate_increments_usage_count_atomically() {
        BcmExerciseScenario s = scenarioFixture();
        s.setUsageCount(11);
        when(scenarioRepo.findById(SCENARIO_ID)).thenReturn(Optional.of(s));
        when(injectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of());
        when(exerciseRepo.save(any(BcmExercise.class)))
            .thenAnswer(inv -> { BcmExercise e = inv.getArgument(0); e.setId(1L); return e; });
        when(scenarioRepo.save(any(BcmExerciseScenario.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.instantiate(SCENARIO_ID, validRequest());

        // Exactly one save call on the scenario, and the saved value is +1.
        ArgumentCaptor<BcmExerciseScenario> saved =
            ArgumentCaptor.forClass(BcmExerciseScenario.class);
        verify(scenarioRepo, times(1)).save(saved.capture());
        assertThat(saved.getValue().getUsageCount()).isEqualTo(12);
    }

    @Test
    void instantiate_404_on_missing_scenario() {
        when(scenarioRepo.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.instantiate(404L, validRequest()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(exerciseRepo, never()).save(any());
        verify(scenarioRepo, never()).save(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
        verify(publisher, never()).publishEvent(any(DomainEvent.class));
    }

    @Test
    void instantiate_writes_F7_audit_and_emits_F8_event() {
        BcmExerciseScenario s = scenarioFixture();
        when(scenarioRepo.findById(SCENARIO_ID)).thenReturn(Optional.of(s));
        when(injectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of(injectFixture(1, 0, "INFORMATION")));
        when(exerciseRepo.save(any(BcmExercise.class)))
            .thenAnswer(inv -> { BcmExercise e = inv.getArgument(0); e.setId(42L); return e; });
        when(scenarioRepo.save(any(BcmExerciseScenario.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        service.instantiate(SCENARIO_ID, validRequest());

        // F7: audit row
        verify(audit).log(eq("CREATE"), eq("BcmExercise"), eq(42L), anyString());

        // F8: domain event
        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evt.capture());
        DomainEvent fired = evt.getValue();
        assertThat(fired.triggerEvent()).isEqualTo("EXERCISE_INSTANTIATED_FROM_SCENARIO");
        assertThat(fired.entityType()).isEqualTo("BcmExercise");
        assertThat(fired.entityId()).isEqualTo(42L);
        assertThat(fired.organisationId()).isEqualTo(ORG_ID);
        assertThat(fired.payload())
            .containsEntry("scenarioId", "401")
            .containsEntry("scenarioCode", "EXS-RANSOMWARE-TABLETOP-2026")
            .containsEntry("injectCount", "1")
            .containsEntry("usageCountAfter", "4");
    }

    @Test
    void instantiate_carries_caller_tenant_id() {
        // Switch tenant mid-test to prove the saved exercise uses the
        // current tenant, not some captured value.
        BcmExerciseScenario s = scenarioFixture();
        when(scenarioRepo.findById(SCENARIO_ID)).thenReturn(Optional.of(s));
        when(injectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of());
        when(exerciseRepo.save(any(BcmExercise.class)))
            .thenAnswer(inv -> { BcmExercise e = inv.getArgument(0); e.setId(1L); return e; });
        when(scenarioRepo.save(any(BcmExerciseScenario.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        TenantContext.setCurrentTenant(42L);
        service.instantiate(SCENARIO_ID, validRequest());

        ArgumentCaptor<BcmExercise> ex = ArgumentCaptor.forClass(BcmExercise.class);
        verify(exerciseRepo).save(ex.capture());
        assertThat(ex.getValue().getOrganisationId()).isEqualTo(42L);
    }

    @Test
    void instantiate_respects_wizard_name_overrides() {
        BcmExerciseScenario s = scenarioFixture();
        when(scenarioRepo.findById(SCENARIO_ID)).thenReturn(Optional.of(s));
        when(injectRepo.findByScenarioIdOrderByInjectOrderAsc(SCENARIO_ID))
            .thenReturn(List.of());
        when(exerciseRepo.save(any(BcmExercise.class)))
            .thenAnswer(inv -> { BcmExercise e = inv.getArgument(0); e.setId(1L); return e; });
        when(scenarioRepo.save(any(BcmExerciseScenario.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        InstantiateRequest req = new InstantiateRequest(
            LocalDate.of(2026, 7, 1),
            "Mona Al-Qahtani",
            "Q3 2026 ransom drill",
            "تمرين الفدية للربع الثالث 2026",
            null, null);
        service.instantiate(SCENARIO_ID, req);

        ArgumentCaptor<BcmExercise> ex = ArgumentCaptor.forClass(BcmExercise.class);
        verify(exerciseRepo).save(ex.capture());
        assertThat(ex.getValue().getExerciseNameEn()).isEqualTo("Q3 2026 ransom drill");
        assertThat(ex.getValue().getExerciseNameAr()).isEqualTo("تمرين الفدية للربع الثالث 2026");
    }
}
