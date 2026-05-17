/**
 * Supplier resilience — alternate suppliers and exit plans
 * (Saudi GRC prompt §3.3, gap-analysis item 4).
 *
 * <p>{@code AlternateSupplier} captures the pre-vetted backups we
 * would fall back to per critical vendor; {@code ExitPlan} captures
 * the concrete disengagement playbook (steps, owner, SLA, last-tested
 * date) so the supplier-resilience dashboard (§30.3) has real data to
 * report.
 */
package com.tss.grc.module.scm.resilience;
