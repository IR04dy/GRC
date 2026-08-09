package com.tss.grc.module.intake.spi;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * F2-175 — the <b>inbound</b> seam, and the counterpart to {@link RegisterPromotionPort}.
 *
 * <h3>The defect this exists to close</h3>
 *
 * <p>A CRITICAL model-drift observation opens a {@code RISK_ISSUE} intake automatically. It did so by
 * constructing an {@code IntakeRecord} by hand in {@code MlModelDriftService} and calling
 * {@code intakeRepo.save()} — reaching across a module boundary into another module's repository, and
 * therefore consulting <b>none</b> of what F2-172 established. Measured, not inferred:
 *
 * <ul>
 *   <li><b>The assignee was wrong.</b> The catalogue routes {@code RISK_ISSUE} to
 *       {@code RISK_OFFICER}; this writer hard-coded {@code RISK_MANAGER} — which is not a
 *       stakeholder code at all but a <em>role name</em>, copied from the controller's own
 *       {@code hasAnyRole} guard. Nothing routes on it (see F2-174), and the two lists have no
 *       reason to agree.</li>
 *   <li><b>The reference code was minted by a second rule.</b> {@code ISS-{epoch36}-{4hex}} against
 *       the catalogue's {@code RI-XXXX-YYYY}. Three characters of prefix where every other code has
 *       two, under a letter pair that is in no catalogue entry — so a submitter holding one could not
 *       be told what kind of submission it was, and the operator reading the inbox could not tell it
 *       came from the same platform.</li>
 *   <li><b>The payload was hand-escaped, and the escaping is wrong.</b> Its {@code esc()} covered
 *       backslash, quote, {@code \n} and {@code \r} — not the other C0 control characters, which JSON
 *       also requires escaped. A model title containing a <b>tab</b> (the character a title acquires
 *       by being pasted out of a spreadsheet) produced a payload Jackson refuses, and
 *       {@code IntakeService#contextFor} turns that into <em>"has an unreadable payload and cannot be
 *       promoted"</em>. So the drift issue could be filed and then <b>never escalated into the risk
 *       register</b> — the one thing the mechanism exists to do — and the error would name intake
 *       while the fault sat in AI governance. Verified against Jackson, not reasoned about.</li>
 *   <li><b>No history.</b> {@code submit()} writes the {@code SUBMITTED} and {@code TRIAGING} events
 *       that are an intake record's entire audit trail. This path wrote none, so the record arrived
 *       with an empty timeline and no statement of who filed it or where it was routed.</li>
 *   <li><b>The SLA agreed by coincidence.</b> {@code plusDays(14)} against the catalogue's
 *       {@code 24 * 14} hours. Equal today; separately editable, with nothing comparing them.</li>
 * </ul>
 *
 * <h3>Why a port rather than "call IntakeService"</h3>
 *
 * <p>Moving the construction into {@code IntakeService} is necessary but not sufficient: a second
 * {@code new IntakeRecord()} inside the same class is the same defect one file further in. So there is
 * exactly one construction site, both public entry points fund it, and
 * {@code IntakeSingleWriterInvariantTest} pins <em>one</em> — a third writer cannot appear quietly the
 * way this one did.
 *
 * <p>The seam is an interface for the reason {@code RegisterPromotionPort} is: it lets a peer module
 * file a submission while importing nothing of intake's internals. {@code module/intake} is Base under
 * ADR-018 and {@code ai/governance} is a sold SKU, so SKU → Base is the legal direction and this edge
 * was never an ADR-012 violation — worth saying plainly, because F2-175 was filed suspecting one.
 * {@code ModuleGraphRatchetTest} could not have caught it: its {@code KERNEL_ISH} set is
 * {@code platform, integrations, dashboard}, so this edge is neither a kernel inversion nor half of a
 * cycle. What was actually wrong is <b>encapsulation</b>, which nothing in the tree measured until now.
 *
 * <h3>Why the organisation is a parameter and not read from {@code TenantContext}</h3>
 *
 * <p>Because the callers are systems. Today's one runs on a request thread and could have read the
 * thread-local, but the javadoc on the class that calls it describes "automated monitoring jobs", and
 * a scheduler has no {@code TenantContext} — the house rule is that schedulers and async work are
 * org-explicit. A signature that <em>accepts</em> the ambient tenant is one that silently files
 * another customer's record the first time it is called off-request. Passing it is one argument;
 * failing closed on {@code null} is one line.
 *
 * @see RegisterPromotionPort the outbound half — promotion out of intake into a register
 */
public interface SystemIntakePort {

    /**
     * File an intake submission on behalf of a platform mechanism rather than a person.
     *
     * <p>Routing — reference-code prefix, SLA and assignee — comes from {@code IntakeKindRouting},
     * the same catalogue every human submission uses and the one parity-checked against the
     * frontend. An uncatalogued kind is refused rather than stored with no deadline.
     *
     * @throws IllegalArgumentException on a null organisation, a blank system actor, an uncatalogued
     *         kind, or a payload that cannot be serialised
     */
    Receipt fileSystemSubmission(SystemSubmission submission);

    /**
     * What a system says when it files a submission. Deliberately <b>not</b> a mirror of
     * {@code IntakeSubmissionRequest}: there is no submitter identity to carry, and no anonymity flag
     * — a machine cannot be a whistleblower, and offering the field would invite a caller to set it
     * and quietly drop the record's provenance.
     *
     * @param organisationId the owning tenant. Required; never defaulted from the ambient context
     * @param kind           must be catalogued in {@code IntakeKindRouting}
     * @param channel        how it arrived — {@code API_INGESTION} for a connector or monitor
     * @param severity       {@code LOW|MEDIUM|HIGH|CRITICAL}; {@code MEDIUM} when null, matching
     *                       the human path
     * @param systemActor    the mechanism filing it, e.g. {@code "AI Governance — drift monitor"}.
     *                       Required, and it is what the record's history names as having filed it
     * @param payload        the submission's facts. Serialised <b>by the mapper</b>, so it is
     *                       re-readable by promotion <em>by construction</em> — which is the half of
     *                       this finding that had a live consequence
     */
    record SystemSubmission(
        Long organisationId,
        String kind,
        String channel,
        String severity,
        String systemActor,
        Map<String, Object> payload
    ) {
    }

    /**
     * The filed record, as much of it as a caller has any business knowing.
     *
     * <p>{@code id} is what a caller stamps into its own row (the drift observation's
     * {@code triggered_issue_id}). {@code referenceCode} and {@code assignedTo} are here so the
     * caller's audit line can state where the submission went instead of only that one was opened —
     * the old code logged neither, so a CRITICAL drift left no trace naming the issue it raised.
     */
    record Receipt(
        Long id,
        String referenceCode,
        String assignedTo,
        LocalDateTime slaDueAt
    ) {
    }
}
