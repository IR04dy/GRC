package com.tss.grc.module.governance.threelines.dto;

import java.time.LocalDate;

/**
 * Single artefact row in the per-band drill-down list (Open Risks, Open
 * Findings, Open CAPA, Pending Policies, Open DSR cases). Flat shape so a
 * single React component can render the table regardless of artefact type.
 *
 * @param artefactType  one of RISK / FINDING / CAPA / POLICY / DSR
 * @param escalatedToBoard true when the artefact has been escalated to
 *                      the Audit Committee / Board level (e.g. CRITICAL
 *                      finding, breach of SAMA-grade risk appetite).
 */
public record ThreeLinesArtefactDto(
        String artefactType,
        Long id,
        String externalId,
        String titleEn,
        String titleAr,
        String severity,
        String status,
        String ownerLabel,
        LocalDate dueDate,
        String slaStatus,                 // ON_TRACK / DUE_SOON / OVERDUE
        boolean escalatedToBoard
) {}
