package com.tss.grc.module.bcm.cycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Slice 10-A — DTOs for the Exercise Scenario Library + scenario→exercise wizard.
 *
 * <p>Three response shapes:
 * <ul>
 *   <li>{@link ScenarioSummary} — card / row in the library grid.</li>
 *   <li>{@link ScenarioDetail}  — header + ordered injects for the detail page.</li>
 *   <li>{@link InstantiatedExercise} — the new exercise row returned after wizard run.</li>
 * </ul>
 *
 * <p>One request shape: {@link InstantiateRequest} carries the user''s
 * wizard input (planned date, lead facilitator, observers, participants).
 */
public final class ScenarioLibraryDtos {

    private ScenarioLibraryDtos() {}

    /** Library card row. */
    public record ScenarioSummary(
        Long id,
        String scenarioCode,
        String titleEn,
        String titleAr,
        String scenarioCategory,
        String exerciseType,
        String scenarioType,
        String complexity,
        Integer durationMinutes,
        Integer recommendedDurationMinutes,
        String frameworkAlignment,
        String targetAudience,
        Integer usageCount
    ) {}

    /** Detail-page payload. */
    public record ScenarioDetail(
        Long id,
        String scenarioCode,
        String titleEn,
        String titleAr,
        String scenarioCategory,
        String exerciseType,
        String scenarioType,
        String complexity,
        Integer durationMinutes,
        Integer recommendedDurationMinutes,
        String frameworkAlignment,
        String targetAudience,
        String descriptionEn,
        String descriptionAr,
        String learningObjectivesEn,
        String learningObjectivesAr,
        String successCriteriaEn,
        String successCriteriaAr,
        String recommendedObservers,
        Integer usageCount,
        List<InjectView> injects
    ) {}

    /** One inject in the timeline. */
    public record InjectView(
        Long id,
        Integer injectOrder,
        Integer timeOffsetMinutes,
        String injectType,
        String injectTextEn,
        String injectTextAr,
        String expectedActionEn,
        String expectedActionAr
    ) {}

    /** Wizard input. */
    public record InstantiateRequest(
        @NotNull(message = "plannedDate is required") LocalDate plannedDate,
        @NotBlank(message = "leadFacilitator is required") String leadFacilitator,
        String exerciseNameEn,
        String exerciseNameAr,
        String observers,
        String participants
    ) {}

    /** Wizard output. */
    public record InstantiatedExercise(
        Long exerciseId,
        Long scenarioId,
        String scenarioCode,
        String exerciseNameEn,
        String exerciseNameAr,
        String exerciseType,
        LocalDate scheduledDate,
        String facilitator,
        String status,
        Integer injectCount,
        Integer usageCountAfter
    ) {}
}
