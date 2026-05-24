/**
 * PDPL Sub-processor Tracking — Sprint 11 deliverable.
 *
 * <p>Saudi PDPL Article 30 (Implementing Regulation Article 39) makes
 * the controller (the bank) responsible for inventorying every
 * sub-processor used in personal-data processing along with:
 * <ul>
 *   <li>the chain of authorisation (GENERAL vs SPECIFIC),</li>
 *   <li>the lawful basis for the engagement, and</li>
 *   <li>the cross-border safeguards each sub-processor relies on.</li>
 * </ul>
 *
 * <p>Replaces the scattered Legal-team spreadsheets — Procurement
 * spreadsheets, IT-vendor lists, separate cross-border tracker — with
 * one live register that SDAIA inspectors can spot-check against. The
 * event ledger gives every sub-processor an auditable narrative over
 * time.
 *
 * <p>Regulatory anchors:
 * <ul>
 *   <li><b>PDPL Article 30 / Implementing Regulation Article 39</b> —
 *       sub-processor inventory + authorisation chain.</li>
 *   <li><b>SDAIA Cross-Border Data Transfer Regulations 2023</b> —
 *       any sub-processor outside KSA needs SCCs, an adequacy
 *       decision, or explicit consent.</li>
 *   <li><b>PDPL Article 5 / Article 6</b> — purpose limitation and
 *       lawful basis recorded on every row.</li>
 *   <li><b>PDPL Article 29</b> — cross-border safeguard surface
 *       links into the existing CrossBorderTransfer module.</li>
 * </ul>
 *
 * <p>Two entities:
 * <ul>
 *   <li>{@link com.tss.grc.module.privacy.subprocessor.entity.Subprocessor}
 *       — the register row, with a lifecycle state machine
 *       (PROPOSED → UNDER_DPO_REVIEW → APPROVED → SUSPENDED | RETIRED).</li>
 *   <li>{@link com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent}
 *       — append-only event ledger
 *       (ADDED / MATERIAL_CHANGE / SUSPENDED / RESUMED / RETIRED /
 *        CROSS_BORDER_CHANGE / REVIEW_COMPLETED / INCIDENT_REPORTED).</li>
 * </ul>
 *
 * <p>The DPO-approval gate enforces (in
 * {@link com.tss.grc.module.privacy.subprocessor.service.SubprocessorService#dpoApprove}):
 * <ol>
 *   <li>{@code has_dpa_in_place = 1}</li>
 *   <li>{@code dpa_signed_date} present</li>
 *   <li>processing_purpose (EN) non-empty</li>
 *   <li>{@code cross_border_safeguard_type} set if
 *       {@code country_of_processing != 'SA'}</li>
 * </ol>
 * Missing keys are listed in the {@code IllegalStateException} message
 * so the UI can render the gap directly.
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F8 events emitted: {@code SUBPROCESSOR_ADDED},
 *       {@code SUBPROCESSOR_LIFECYCLE_TRANSITION},
 *       {@code SUBPROCESSOR_MATERIAL_CHANGE},
 *       {@code SUBPROCESSOR_REVIEWED},
 *       {@code SUBPROCESSOR_CROSS_BORDER_CHANGED}.</li>
 *   <li>F7 audit_log entries for every CREATE / UPDATE /
 *       LIFECYCLE_TRANSITION / MATERIAL_CHANGE / REVIEW_COMPLETED /
 *       CROSS_BORDER_CHANGE / DPO_APPROVE.</li>
 * </ul>
 *
 * <p>A public PDPL-transparency endpoint serves a filtered view of
 * APPROVED sub-processors only, with internal fields stripped — so a
 * data subject (or a competitor reading the public privacy notice)
 * can see the current list without authentication. This satisfies
 * the PDPL Article 12 transparency obligation.
 */
package com.tss.grc.module.privacy.subprocessor;
