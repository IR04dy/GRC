package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.util.LikePatterns;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.dto.ScenarioLibraryDtos.InjectView;
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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Slice 10-A — Exercise Scenario Library service.
 *
 * <p>Three jobs:
 * <ol>
 *   <li>Search and surface library scenarios with filters (type, complexity,
 *       framework, free-text). The library is a global catalog seeded by
 *       V111 + V171 — every tenant sees the same scenarios.</li>
 *   <li>Return the full scenario detail (header + ordered injects) for the
 *       detail page''s timeline view.</li>
 *   <li>Instantiate a scenario as a runnable exercise — pre-populates the
 *       new {@link BcmExercise} row with the scenario''s learning_objectives
 *       and success_criteria, links it via {@code scenario_id}, increments
 *       the scenario''s {@code usage_count}, writes an F7 audit row, and
 *       emits the F8 {@code EXERCISE_INSTANTIATED_FROM_SCENARIO} domain
 *       event.</li>
 * </ol>
 *
 * <p>Notes on tenancy: scenarios are a global catalog (no
 * {@code organisationId} on the scenario row) so library reads are
 * tenant-agnostic. The instantiated exercise IS org-scoped — it lives
 * on {@code bcm.exercises} with the caller''s tenant.
 */
@Service
@RequiredArgsConstructor
public class ExerciseScenarioLibraryService {

    private static final Logger log =
        LoggerFactory.getLogger(ExerciseScenarioLibraryService.class);

    /** Inject types accepted by the V171 CHECK constraint. */
    public static final Set<String> INJECT_TYPES = Set.of(
        "INFORMATION", "ESCALATION", "DECISION", "MEDIA", "REGULATOR");

    private final BcmExerciseScenarioRepository scenarioRepo;
    private final ExerciseScenarioInjectRepository injectRepo;
    private final BcmExerciseRepository exerciseRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────────

    /**
     * Filtered, paginated library view. All filter parameters are
     * optional — pass {@code null} to disable. {@code framework} matches
     * the comma-separated framework_alignment string via LIKE.
     */
    @Transactional(readOnly = true)
    public Page<ScenarioSummary> search(
            String scenarioType, String complexity, String framework, String query,
            int page, int size) {
        Pageable p = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        String type = blankToNull(scenarioType);
        String cmp  = blankToNull(complexity);
        // F2-449: both reach a LIKE pattern, so the caller's own %, _ and [ must be escaped
        String fw   = LikePatterns.escape(blankToNull(framework));
        String q    = LikePatterns.escape(blankToNull(query));
        return scenarioRepo.filter(type, cmp, fw, q, p).map(ExerciseScenarioLibraryService::toSummary);
    }

    /** Detail-page payload — header + injects in play order. */
    @Transactional(readOnly = true)
    public ScenarioDetail detail(Long scenarioId) {
        BcmExerciseScenario s = scenarioRepo.findById(scenarioId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Scenario not found in library: " + scenarioId));
        List<ExerciseScenarioInject> injects =
            injectRepo.findByScenarioIdOrderByInjectOrderAsc(scenarioId);
        return new ScenarioDetail(
            s.getId(), s.getScenarioCode(), s.getTitleEn(), s.getTitleAr(),
            s.getScenarioCategory(), s.getExerciseType(),
            s.getScenarioType(), s.getComplexity(),
            s.getDurationMinutes(), s.getRecommendedDurationMinutes(),
            s.getFrameworkAlignment(), s.getTargetAudience(),
            s.getDescriptionEn(), s.getDescriptionAr(),
            s.getLearningObjectivesEn(), s.getLearningObjectivesAr(),
            s.getSuccessCriteriaEn(), s.getSuccessCriteriaAr(),
            s.getRecommendedObservers(), s.getUsageCount(),
            injects.stream().map(ExerciseScenarioLibraryService::toInjectView).toList()
        );
    }

    /** "Popular this quarter" — top-N by usage_count. */
    @Transactional(readOnly = true)
    public List<ScenarioSummary> popular(int limit) {
        int n = Math.max(1, Math.min(limit, 20));
        return scenarioRepo.findTop20ByOrderByUsageCountDescTitleEnAsc()
            .stream().limit(n)
            .map(ExerciseScenarioLibraryService::toSummary).toList();
    }

    /** Recommended for a specific framework (e.g. SAMA_CSF, NCA_ECC2, PDPL). */
    @Transactional(readOnly = true)
    public List<ScenarioSummary> recommended(String framework) {
        if (framework == null || framework.isBlank()) {
            throw new IllegalArgumentException("framework is required");
        }
        return scenarioRepo.findByFrameworkAlignmentContaining(LikePatterns.escape(framework.trim()))
            .stream().map(ExerciseScenarioLibraryService::toSummary).toList();
    }

    // ─── Write — scenario→exercise instantiation ────────────────────────

    /**
     * Instantiate the library scenario {@code scenarioId} as a runnable
     * exercise in the caller''s tenant.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the scenario; 404 if missing.</li>
     *   <li>Build a {@link BcmExercise} row pre-populated from the
     *       scenario (title, type, scenario text, planned date,
     *       facilitator, scenario_id link).</li>
     *   <li>Pull the inject sequence so the caller knows how many were
     *       attached to the exercise (the runtime exercise reads them
     *       on demand via the scenario_id link — we do NOT duplicate
     *       the inject rows onto a new {@code exercise_inject} table).</li>
     *   <li>Increment {@code usage_count} atomically (save).</li>
     *   <li>Audit (F7) + emit (F8) {@code EXERCISE_INSTANTIATED_FROM_SCENARIO}.</li>
     * </ol>
     */
    @Transactional
    public InstantiatedExercise instantiate(Long scenarioId, InstantiateRequest req) {
        Long orgId = org();
        BcmExerciseScenario s = scenarioRepo.findById(scenarioId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Scenario not found in library: " + scenarioId));

        // Build the new exercise. Title falls back to the scenario title
        // when the wizard didn''t override it.
        BcmExercise ex = new BcmExercise();
        ex.setOrganisationId(orgId);
        ex.setScenarioId(s.getId());
        ex.setExerciseNameEn(nonBlank(req.exerciseNameEn(), s.getTitleEn()));
        ex.setExerciseNameAr(nonBlank(req.exerciseNameAr(), s.getTitleAr()));
        ex.setExerciseType(Objects.requireNonNullElse(s.getExerciseType(), "TABLETOP"));
        ex.setScenario(buildScenarioBrief(s, req));
        ex.setScheduledDate(req.plannedDate());
        ex.setFacilitator(req.leadFacilitator());
        ex.setStatus("SCHEDULED");
        ex.setCapaCount(0);
        ex.setCreatedAt(LocalDateTime.now());
        BcmExercise saved = exerciseRepo.save(ex);

        // Inject sequence — count to return to caller. The runtime
        // exercise reads these via the scenario_id link.
        List<ExerciseScenarioInject> injects =
            injectRepo.findByScenarioIdOrderByInjectOrderAsc(s.getId());

        // Atomic increment — the JPA save will write the updated
        // usage_count back to the row.
        s.setUsageCount(Objects.requireNonNullElse(s.getUsageCount(), 0) + 1);
        scenarioRepo.save(s);

        audit.log("CREATE", "BcmExercise", saved.getId(),
            "Exercise instantiated from scenario " + s.getScenarioCode()
                + " (" + s.getTitleEn() + ") — facilitator: " + req.leadFacilitator()
                + ", scheduled: " + req.plannedDate()
                + ", " + injects.size() + " injects attached");

        emit(orgId, "EXERCISE_INSTANTIATED_FROM_SCENARIO", saved, s, injects.size());

        return new InstantiatedExercise(
            saved.getId(), s.getId(), s.getScenarioCode(),
            saved.getExerciseNameEn(), saved.getExerciseNameAr(),
            saved.getExerciseType(), saved.getScheduledDate(),
            saved.getFacilitator(), saved.getStatus(),
            injects.size(), s.getUsageCount()
        );
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /**
     * Build the {@code scenario} text body that lands on the runtime
     * exercise. Combines the scenario''s description, learning objectives,
     * success criteria, and (when supplied) the observer + participant
     * roster from the wizard. The result is plain text, both languages
     * stacked — the runtime UI renders whichever language is active.
     */
    private static String buildScenarioBrief(BcmExerciseScenario s, InstantiateRequest req) {
        StringBuilder sb = new StringBuilder();
        if (s.getDescriptionEn() != null && !s.getDescriptionEn().isBlank()) {
            sb.append("== Scenario ==\n").append(s.getDescriptionEn()).append("\n\n");
        }
        if (s.getDescriptionAr() != null && !s.getDescriptionAr().isBlank()) {
            sb.append("== السيناريو ==\n").append(s.getDescriptionAr()).append("\n\n");
        }
        if (s.getLearningObjectivesEn() != null && !s.getLearningObjectivesEn().isBlank()) {
            sb.append("== Learning Objectives ==\n")
              .append(s.getLearningObjectivesEn()).append("\n\n");
        }
        if (s.getLearningObjectivesAr() != null && !s.getLearningObjectivesAr().isBlank()) {
            sb.append("== أهداف التعلم ==\n")
              .append(s.getLearningObjectivesAr()).append("\n\n");
        }
        if (s.getSuccessCriteriaEn() != null && !s.getSuccessCriteriaEn().isBlank()) {
            sb.append("== Success Criteria ==\n")
              .append(s.getSuccessCriteriaEn()).append("\n\n");
        }
        if (s.getSuccessCriteriaAr() != null && !s.getSuccessCriteriaAr().isBlank()) {
            sb.append("== معايير النجاح ==\n")
              .append(s.getSuccessCriteriaAr()).append("\n\n");
        }
        if (req.observers() != null && !req.observers().isBlank()) {
            sb.append("== Observers ==\n").append(req.observers()).append("\n\n");
        }
        if (req.participants() != null && !req.participants().isBlank()) {
            sb.append("== Participants ==\n").append(req.participants()).append("\n");
        }
        return sb.toString();
    }

    private static String nonBlank(String preferred, String fallback) {
        return (preferred == null || preferred.isBlank()) ? fallback : preferred;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void emit(Long orgId, String trigger, BcmExercise ex, BcmExerciseScenario s, int injectCount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exerciseId",      String.valueOf(ex.getId()));
        payload.put("scenarioId",      String.valueOf(s.getId()));
        payload.put("scenarioCode",    String.valueOf(s.getScenarioCode()));
        payload.put("exerciseType",    String.valueOf(ex.getExerciseType()));
        payload.put("scenarioType",    String.valueOf(s.getScenarioType()));
        payload.put("complexity",      String.valueOf(s.getComplexity()));
        payload.put("framework",       String.valueOf(s.getFrameworkAlignment()));
        payload.put("scheduledDate",   String.valueOf(ex.getScheduledDate()));
        payload.put("facilitator",     String.valueOf(ex.getFacilitator()));
        payload.put("injectCount",     String.valueOf(injectCount));
        payload.put("usageCountAfter", String.valueOf(s.getUsageCount()));
        publisher.publishEvent(new DomainEvent(
            orgId, trigger, "BcmExercise", ex.getId(),
            "scenario_lib:" + s.getId() + ":exercise:" + ex.getId(),
            payload));
        log.debug("emitted {} for exercise {} from scenario {}", trigger, ex.getId(), s.getId());
    }

    private static ScenarioSummary toSummary(BcmExerciseScenario s) {
        return new ScenarioSummary(
            s.getId(), s.getScenarioCode(), s.getTitleEn(), s.getTitleAr(),
            s.getScenarioCategory(), s.getExerciseType(),
            s.getScenarioType(), s.getComplexity(),
            s.getDurationMinutes(), s.getRecommendedDurationMinutes(),
            s.getFrameworkAlignment(), s.getTargetAudience(),
            s.getUsageCount()
        );
    }

    private static InjectView toInjectView(ExerciseScenarioInject i) {
        return new InjectView(
            i.getId(), i.getInjectOrder(), i.getTimeOffsetMinutes(),
            i.getInjectType(),
            i.getInjectTextEn(), i.getInjectTextAr(),
            i.getExpectedActionEn(), i.getExpectedActionAr()
        );
    }
}
