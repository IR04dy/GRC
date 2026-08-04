package com.tss.grc.module.change_mgmt.spi;

import java.util.List;
import java.util.Optional;

/**
 * S8a / CE-9 — the change module's outbound port to the platform evidence subsystem (ADR-012).
 *
 * <p><b>G-9, the only Critical left in S8:</b> "No evidence/attachment integration at all — test
 * results, approvals, back-out proof are free text; ISO 20000/9001 documented-information not
 * satisfiable." Measured 2026-08-04: the subsystem is not missing, it is thriving, and change is the
 * one governance domain that never joined it —
 *
 * <pre>
 *   evidence.evidence_items ... 78 live rows (29 APPROVED, 29 EXPIRED, 15 PENDING_REVIEW,
 *                               4 DRAFT, 1 REJECTED) — identical in both databases
 *   evidence.evidence_links ... 21 rows across EIGHT modules: RISK, SUSTAINABILITY, PRIVACY,
 *                               COMPLIANCE, AUDIT, CYBER, INCIDENT, BCM
 *   CK_ev_links_module ........ 9 values, and CHANGE was not one of them (V353 makes it 10)
 * </pre>
 *
 * <p>⚠ These figures were corrected before commit: the first census said 104 items because it used a
 * bare {@code COUNT(*)} and swept in 25 <b>soft-deleted</b> rows. Filtering {@code is_deleted} in
 * native SQL is landmine #6 on this platform's own list, and a census is native SQL.
 *
 * <p>So {@code rollback_plan} and {@code test_plan} were prose on {@code change_requests} while every
 * other domain attached hashed, versioned, four-eyes-reviewed artefacts.
 *
 * <p><b>⚠ Not {@code change_mgmt.change_links}, which already exists.</b> That table's frontend
 * vocabulary even lists an {@code EVIDENCE} entity type — and using it would have been the mistake:
 * it is an un-hashed, un-reviewed, un-versioned pointer with no expiry and no review, which is
 * precisely the deficiency G-9 describes. Evidence has to live in the evidence subsystem or the word
 * means nothing.
 *
 * <p><b>Narrowed to two verbs</b> like {@link IncidentPort} and {@link CapaPort}. The peer's
 * {@code linked_entity_type} vocabulary stays on the far side — this port names the subject with its
 * own enum and the adapter maps it, the same division S7d settled for {@code origin_type}.
 *
 * <p>Implemented in {@code module.change_adapters}, outside both modules. Optional: when the adapter
 * is absent, reads return empty and attach returns empty rather than throwing. ⚠ But note where that
 * polarity does <b>not</b> apply — {@code ChangeManagementService.requireVerifiedEvidence} treats an
 * absent port as "cannot verify" and <em>refuses</em>, because a satisfaction gate that passes when
 * it cannot see the evidence is the fail-open direction and F2-95's exact defect.
 */
public interface EvidencePort {

    /** What the change module may attach evidence to. The adapter maps these to the subsystem's own
     *  {@code linked_entity_type} strings, so that vocabulary is not duplicated here. */
    enum Subject {
        /** A change request as a whole — test results, back-out proof, implementation logs. */
        CHANGE,
        /** One condition on an approval — the artefact that discharges the obligation (S7e). */
        CONDITION
    }

    /**
     * Just enough of an evidence item to render it and to judge whether it counts.
     *
     * @param verified {@code true} only for {@code APPROVED} — the subsystem's four-eyes-reviewed
     *                 state. Measured: <b>29 of 78</b> live items are APPROVED, so 49 are not and this
     *                 is a real distinction rather than a formality; {@code PENDING_REVIEW} evidence is
     *                 evidence somebody uploaded, not evidence anybody checked.
     */
    record EvidenceRef(Long id, String externalId, String titleEn, String titleAr,
                       String evidenceType, String status, boolean verified,
                       String fileName, String sha256) { }

    /**
     * Attach an existing evidence item to a change or to one of its conditions.
     *
     * <p>Attaches, never uploads: the item is created and reviewed in the evidence subsystem, which
     * owns hashing, versioning and the four-eyes review. A change-module upload path would be a
     * second door into the same store with none of that.
     *
     * @return the attached item, or empty when the evidence subsystem is unavailable or the id does
     *         not resolve inside the caller's tenant
     */
    Optional<EvidenceRef> attach(Subject subject, Long subjectId, Long evidenceId, String notes);

    /** Everything attached to this subject, newest first. Empty when the subsystem is absent. */
    List<EvidenceRef> attached(Subject subject, Long subjectId);
}
