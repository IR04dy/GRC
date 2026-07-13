package com.tss.grc.module.sust.sgi.dto;

import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * S-L — validated create request for an SGI commitment. Lifecycle columns
 * (status + the achieved / retired stamps) are server-owned and NOT bindable.
 */
public record SgiCreateRequest(
        @NotBlank @Size(max = 40) String commitmentCode,
        @NotBlank @Size(max = 30) String sector,
        @NotBlank @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        String descriptionEn,
        String descriptionAr,
        BigDecimal baselineValue,
        Short baselineYear,
        BigDecimal currentValue,
        LocalDate currentValueAsOf,
        @NotNull BigDecimal targetValue,
        @NotNull Short targetYear,
        @NotBlank @Size(max = 40) String unit,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail,
        LocalDate nextMilestoneDate,
        @Size(max = 500) String nextMilestoneEn,
        @Size(max = 500) String nextMilestoneAr,
        String notesEn,
        String notesAr,
        @Size(max = 255) String evidenceRef,
        // SGI-C — current_value provenance (all optional; default MANUAL).
        @Pattern(regexp = "MANUAL|ESG_METRIC|GHG_TOTAL") String valueSource,
        Long sourceMetricId,
        @Min(1) @Max(3) Short sourceScope
) {
    public SgiCommitment toEntity() {
        SgiCommitment e = new SgiCommitment();
        e.setCommitmentCode(commitmentCode);
        e.setSector(sector);
        e.setTitleEn(titleEn);
        e.setTitleAr(titleAr);
        e.setDescriptionEn(descriptionEn);
        e.setDescriptionAr(descriptionAr);
        e.setBaselineValue(baselineValue);
        e.setBaselineYear(baselineYear);
        e.setCurrentValue(currentValue);
        e.setCurrentValueAsOf(currentValueAsOf);
        e.setTargetValue(targetValue);
        e.setTargetYear(targetYear);
        e.setUnit(unit);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        e.setNextMilestoneDate(nextMilestoneDate);
        e.setNextMilestoneEn(nextMilestoneEn);
        e.setNextMilestoneAr(nextMilestoneAr);
        e.setNotesEn(notesEn);
        e.setNotesAr(notesAr);
        e.setEvidenceRef(evidenceRef);
        e.setValueSource(valueSource == null || valueSource.isBlank() ? "MANUAL" : valueSource);
        e.setSourceMetricId(sourceMetricId);
        e.setSourceScope(sourceScope);
        return e;
    }
}
