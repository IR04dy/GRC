/**
 * Sprint 13 — Sanctions Screening engine + hit triage workflow.
 *
 * <p>FATF Recommendations 5, 6, 7 + SAMA AML/CFT Rules 2018 §3.4 mandate
 * real-time screening of every customer onboarding and transaction
 * counterparty against OFAC SDN, UN Consolidated, EU Consolidated, SAMA
 * Local Terrorism, and the KSA National Watchlist. Today the vendor
 * service is a black box (FircoSoft / ComplyAdvantage / Refinitiv) that
 * produces 1,000+ daily hits with 95%+ false positives, triaged in Excel.
 *
 * <p>This module owns the in-platform screening + triage workflow. The
 * vendor service plugs in as a {@code SCREENING_SOURCE = INTEGRATION_VENDOR}
 * adapter (real adapters land in a follow-up slice).
 *
 * <p>Tables (V196):
 * <ul>
 *   <li>{@code aml.sanctions_list}        — one row per list (org_id NULL = global)</li>
 *   <li>{@code aml.sanctions_list_entry}  — denormalised entries per list</li>
 *   <li>{@code aml.screening_check}       — one row per subject screened</li>
 *   <li>{@code aml.screening_hit}         — one row per potential/confirmed match</li>
 * </ul>
 *
 * <p>F8 events emitted:
 * {@code SANCTIONS_CHECK_PERFORMED}, {@code SANCTIONS_HIT_OPENED},
 * {@code SANCTIONS_HIT_REVIEWED}, {@code SANCTIONS_HIT_ESCALATED},
 * {@code SANCTIONS_HIT_CLOSED}, {@code SANCTIONS_HIT_SLA_BREACHED}.
 */
package com.tss.grc.module.aml.sanctions;
