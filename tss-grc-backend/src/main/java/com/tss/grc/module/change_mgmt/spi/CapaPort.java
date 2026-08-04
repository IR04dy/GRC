package com.tss.grc.module.change_mgmt.spi;

import java.time.LocalDate;
import java.util.Optional;

/**
 * S7d / CE-7 — the change module's outbound port to the platform's CAPA tracker (ADR-012).
 *
 * <p>CH-11 gave the change record a post-implementation review: {@code pir_summary},
 * {@code pir_objectives_met}, {@code pir_lessons}, {@code pir_follow_up}, with a mandatory-PIR gate
 * on closure. The follow-up field was <b>free text going nowhere</b> — a reviewer could write "add a
 * pre-flight check to the deployment runbook", the change would close, and nothing tracked it, chased
 * it or reported it overdue. This port is where that sentence becomes work.
 *
 * <p><b>Which CAPA store, and why not the one the plan named.</b> UNIFIED_IMPLEMENTATION_PLAN's S7
 * row reads "{@code CHANGE_PIR} in {@code ck_ca_source}" — that is {@code sust.corrective_action},
 * and measurement in both databases says it is the wrong home on five counts:
 *
 * <ol>
 *   <li>{@code sust} is homed to the <b>ESG</b> SKU and its CAPA controller carries
 *       {@code @RequireModule("ESG")}. A tenant holding CHANGE without ESG would get corrective
 *       actions written into a module it cannot open — a CAPA nobody can ever see.</li>
 *   <li>{@code CorrectiveActionService.autoOpen} notifies {@code SUSTAINABILITY_OFFICER} and dates
 *       the action from a <em>sustainability</em> setting ({@code capaExecutiveEscalationDays}).</li>
 *   <li>All eleven {@code ck_ca_source} values are ESG-domain (GHG_RECORD, TCFD_ITEM, VENDOR_ESG…).</li>
 *   <li>{@code module/capa} is a <b>Base</b> package whose own package-info calls it the "universal
 *       Corrective and Preventive Actions tracker, linked to originating issue/finding/incident via
 *       (origin_type, origin_id)" — and it already carries <b>eight origin types from eight
 *       modules</b> (control_test, rca, audit_finding, risk_treatment, dpia, incident,
 *       inspection_finding, processor_assessment) in both dev and prod. This is the store built for
 *       exactly this call.</li>
 *   <li>{@code AuditCommitteeDashboardService} aggregates {@code capa.tasks} org-wide, so a change
 *       follow-up raised here lands in the audit committee's CAPA rollup. Raised in {@code sust} it
 *       would not.</li>
 * </ol>
 *
 * <p>{@code capa.tasks.origin_type} has <b>no CHECK constraint</b> (verified in both databases), so
 * the row's headline migration deliverable is answered by choosing the right store rather than by
 * widening the wrong one.
 *
 * <p><b>Narrowed on purpose,</b> like {@link IncidentPort}: one verb, and the CAPA's own
 * {@code origin_type} vocabulary stays on the far side — the verb's name is what says where the
 * record came from. The change module passes {@code capaType} and {@code severity} because both are
 * statements about the <em>change</em> (was its objective met, how risky was it), the same reasoning
 * that puts the risk→severity mapping on this side of {@link IncidentPort}.
 *
 * <p>Implemented in {@code module.change_adapters}, outside both modules. Optional: when the adapter
 * is absent the hook no-ops rather than failing the transition, because losing the derived CAPA must
 * not block recording the review itself.
 */
public interface CapaPort {

    /** Just enough of the opened CAPA to back-link it. */
    record RaisedCapa(Long id, String capaCode) { }

    /**
     * Open a CAPA task carrying a change's post-implementation-review follow-up.
     *
     * <p>Not idempotent here — the caller owns dedup, because the caller owns the marker that
     * records "this change already has its follow-up CAPA" (a {@code CAPA} / {@code FOLLOW_UP}
     * change link, mirroring CH-13's rollback-incident hook).
     *
     * @param changeCode becomes the CAPA's {@code origin_id}, so the tracker can be queried by
     *                   change without the CAPA module knowing what a change is
     * @param capaType   {@code CORRECTIVE} or {@code PREVENTIVE}
     * @return the new CAPA, or empty when the CAPA tracker is unavailable
     */
    Optional<RaisedCapa> raiseFromChangePir(String changeCode,
                                            String titleEn, String titleAr,
                                            String descriptionEn, String descriptionAr,
                                            String capaType, String severity,
                                            String owner, LocalDate targetDate);
}
