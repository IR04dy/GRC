package com.tss.grc.module.sust.cma.dto;

import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * S-L — validated create request for a CMA disclosure. Approval columns
 * ({@code approvalStatus}, {@code approvedBy/at}) and {@code linkedCapaId} are
 * NOT bindable (approval + auto-CAPA are server-owned). Article/status are
 * additionally validated in the service.
 */
public record CmaCreateRequest(
        @NotNull Short reportingYear,
        @NotBlank @Size(max = 10) String article,
        @Size(max = 10) String clause,
        @NotBlank @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        String requirementEn,
        String requirementAr,
        @Size(max = 30) String disclosureStatus,
        @Size(max = 255) String evidenceRef,
        @Size(max = 255) String evidenceOwner,
        @Size(max = 120) String responsibleDepartment,
        String gapDescriptionEn,
        String gapDescriptionAr,
        @Size(max = 120) String reportSectionReference,
        @Size(max = 255) String reviewerName,
        @Size(max = 255) String reviewerEmail,
        String notes
) {
    public CmaDisclosure toEntity() {
        CmaDisclosure e = new CmaDisclosure();
        e.setReportingYear(reportingYear);
        e.setArticle(article);
        e.setClause(clause);
        e.setTitleEn(titleEn);
        e.setTitleAr(titleAr);
        e.setRequirementEn(requirementEn);
        e.setRequirementAr(requirementAr);
        if (disclosureStatus != null) e.setDisclosureStatus(disclosureStatus);
        e.setEvidenceRef(evidenceRef);
        e.setEvidenceOwner(evidenceOwner);
        e.setResponsibleDepartment(responsibleDepartment);
        e.setGapDescriptionEn(gapDescriptionEn);
        e.setGapDescriptionAr(gapDescriptionAr);
        e.setReportSectionReference(reportSectionReference);
        e.setReviewerName(reviewerName);
        e.setReviewerEmail(reviewerEmail);
        e.setNotes(notes);
        return e;
    }
}
