/**
 * PDPL extensions (Saudi GRC prompt §3.5 + §7.4, gap-analysis priorities 6/7/8).
 *
 * <p>Three sibling capabilities not covered by the core privacy module:
 *
 * <ul>
 *   <li><b>Cross-border transfer</b> — PDPL Art. 29 workflow. State
 *       machine REQUESTED → ASSESSED → APPROVED | REJECTED → EXPIRED.
 *       REST: {@code /api/privacy/cross-border-transfers}.</li>
 *   <li><b>Disposal certificate</b> — PDPL Art. 18 retention evidence.
 *       State machine SCHEDULED → IN_PROGRESS → COMPLETED → VERIFIED,
 *       with CANCELLED as an off-path terminal. Can link to a digital
 *       signature transaction (§28 module) for sign-off.
 *       REST: {@code /api/privacy/disposal-certificates}.</li>
 *   <li><b>Privacy notice</b> — PDPL Art. 12 versioned bilingual notice
 *       templating. State machine DRAFT → PUBLISHED → ARCHIVED. Unique
 *       (org, notice_code, version) so superseding bumps the version
 *       without overwriting history. REST: {@code /api/privacy/notices}.</li>
 * </ul>
 *
 * <p>All three are tenant-scoped via {@code TenantContext} and share the
 * {@code privacy} DB schema (created by V1, extended by V32).
 */
package com.tss.grc.module.privacy.extensions;
