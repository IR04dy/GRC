/**
 * Risk Bow-Tie Analysis — ISO 31010 top-event analysis technique.
 *
 * <p>A bow-tie analysis visualises one risk (the "top event") with its
 * upstream threats (and preventive controls) on the left, and downstream
 * consequences (and mitigating controls) on the right. The whole picture
 * looks like a bow-tie with the top event at the knot.
 *
 * <pre>
 *   [threats]──┐                                ┌──[consequences]
 *   [threats]──┼──preventive ──◆──  mitigating──┼──[consequences]
 *   [threats]──┘   controls   top    controls   └──[consequences]
 *                              event
 * </pre>
 *
 * <p>Five JPA entities cover the model:
 * <ul>
 *   <li>{@link com.tss.grc.module.risk.bowtie.entity.BowTieAnalysis} — header</li>
 *   <li>{@link com.tss.grc.module.risk.bowtie.entity.BowTieThreat} — left pathway</li>
 *   <li>{@link com.tss.grc.module.risk.bowtie.entity.BowTieConsequence} — right pathway</li>
 *   <li>{@link com.tss.grc.module.risk.bowtie.entity.BowTiePreventiveControl} — barrier on a threat</li>
 *   <li>{@link com.tss.grc.module.risk.bowtie.entity.BowTieMitigatingControl} — barrier on a consequence</li>
 * </ul>
 *
 * <p>Lifecycle: DRAFT → IN_REVIEW → APPROVED → SUPERSEDED | ARCHIVED.
 *
 * <p>F8 events emitted: BOWTIE_CREATED, BOWTIE_UPDATED, BOWTIE_SUBMITTED,
 * BOWTIE_APPROVED, BOWTIE_ARCHIVED.
 */
package com.tss.grc.module.risk.bowtie;
