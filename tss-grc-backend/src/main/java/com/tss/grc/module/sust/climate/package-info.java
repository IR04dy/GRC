/**
 * Climate &amp; Scope-3 financed emissions module — Sprint 11 strategic
 * deliverable.
 *
 * <p>Saudi listed banks face a tri-track climate-disclosure surface
 * starting FY2025:
 * <ul>
 *   <li><b>CMA Implementing Regulations (2023)</b> — IFRS S2
 *       climate-related disclosures mandatory for listed entities from
 *       FY2025.</li>
 *   <li><b>SAMA Sustainable Finance Roadmap (2024)</b> — TCFD
 *       recommendations baked into prudential supervision; scenario
 *       analysis required for material climate risks (transition +
 *       physical).</li>
 *   <li><b>TADAWUL ESG disclosure index</b> — part of the Saudi
 *       Exchange's ESG scoring; scenarios + financed emissions are
 *       observed inputs.</li>
 *   <li><b>PCAF (Partnership for Carbon Accounting Financials)</b> —
 *       the global methodology for measuring Scope-3 Category 15
 *       (Financed Emissions). Saudi banks adopting via the SAMA
 *       Sustainable Finance Working Group.</li>
 * </ul>
 *
 * <p>Two entity clusters live in {@code module/sust/climate/}:
 * <ul>
 *   <li><b>TCFD scenarios</b> —
 *       {@link com.tss.grc.module.sust.climate.entity.TcfdClimateScenario}
 *       (header) +
 *       {@link com.tss.grc.module.sust.climate.entity.TcfdSectorImpact}
 *       (per-sector exposure, transition + physical risk scores, PD
 *       uplift in bps).</li>
 *   <li><b>Financed emissions</b> —
 *       {@link com.tss.grc.module.sust.climate.entity.FinancedEmissionsRecord}
 *       (PCAF asset-class row, attribution factor × verified or proxied
 *       scope-1/2/3 emissions) +
 *       {@link com.tss.grc.module.sust.climate.entity.FinancedEmissionsTarget}
 *       (net-zero / interim reduction commitment).</li>
 * </ul>
 *
 * <p>Scenario lifecycle: {@code DRAFT → IN_REVIEW → BOARD_APPROVED →
 * ARCHIVED}. Emissions record lifecycle: {@code DRAFT → IN_REVIEW →
 * APPROVED → PUBLISHED → ARCHIVED}.
 *
 * <p>F8 events emitted:
 * <ul>
 *   <li>{@code TCFD_SCENARIO_CREATED}, {@code TCFD_SCENARIO_SUBMITTED},
 *       {@code TCFD_SCENARIO_APPROVED}, {@code TCFD_SCENARIO_ARCHIVED}</li>
 *   <li>{@code FINANCED_EMISSIONS_CREATED},
 *       {@code FINANCED_EMISSIONS_SUBMITTED},
 *       {@code FINANCED_EMISSIONS_APPROVED},
 *       {@code FINANCED_EMISSIONS_PUBLISHED},
 *       {@code FINANCED_EMISSIONS_ARCHIVED}</li>
 * </ul>
 *
 * <p>Probability-weighted expected loss is computed across all
 * {@code BOARD_APPROVED} scenarios as
 * {@code Σ (expected_loss × probability_weight)} — the
 * board-blendable view that goes into the annual IFRS S2 disclosure
 * pack.
 *
 * <p>Roles:
 * <ul>
 *   <li><b>Read</b>: SUSTAINABILITY_OFFICER, CFO, CISO, RISK_MANAGER,
 *       BOARD_MEMBER, AUDITOR, PLATFORM_ADMIN.</li>
 *   <li><b>Write</b>: SUSTAINABILITY_OFFICER, CFO, PLATFORM_ADMIN.</li>
 * </ul>
 */
package com.tss.grc.module.sust.climate;
