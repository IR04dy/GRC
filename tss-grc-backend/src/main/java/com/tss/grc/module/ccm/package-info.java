/**
 * Continuous Control Monitoring (CCM) Engine — Sprint 11 deliverable.
 *
 * <p>Replaces the manual "auditor screenshots AD, pastes into a Word
 * doc, files under Q2 SOX review" ritual with a scheduled-test +
 * tamper-evident-evidence pipeline.
 *
 * <p>Regulatory driver:
 * <ul>
 *   <li><b>SAMA Cyber Security Framework 2.6 (Continuous Monitoring)</b>
 *       — regulated entities MUST continuously monitor critical
 *       controls and surface deviations in near-real-time.</li>
 *   <li><b>NCA ECC-2 control 2-12 (Cybersecurity Event Logs &amp;
 *       Monitoring Management)</b> — every control protecting a
 *       critical asset must have a documented monitoring + evidence
 *       scheme.</li>
 *   <li><b>SOX / Saudi MoCT internal-controls programme</b> —
 *       quarterly access certification of privileged groups becomes
 *       a continuous test instead of a calendar event.</li>
 *   <li><b>PDPL Article 16 (retention)</b> — retention windows must
 *       be enforced AND evidenced.</li>
 * </ul>
 *
 * <p>Two entities:
 * <ul>
 *   <li>{@link com.tss.grc.module.ccm.entity.ControlTestDefinition}
 *       — "what to test, when, how often". Operator opts in
 *       ({@code is_enabled = false} by default).</li>
 *   <li>{@link com.tss.grc.module.ccm.entity.ControlTestRun}
 *       — one row per execution; immutable; carries a
 *       SHA-256 {@code evidence_hash} of
 *       {@code actual_value_json || ISO-8601(run_at)} so an auditor
 *       can reproduce it and confirm nothing was rewritten.</li>
 * </ul>
 *
 * <p>Runner dispatch — every {@code test_type} has a corresponding
 * {@link com.tss.grc.module.ccm.runner.ControlTestRunner} bean. Live
 * runners ({@code LDAP_GROUP_MEMBERSHIP}, {@code SSL_CERT_EXPIRY},
 * {@code CUSTOM_SQL}) plus stub runners (the rest) make up the
 * dispatch table.
 *
 * <p>F7+F8 contract:
 * <ul>
 *   <li>F8 events: {@code CCM_TEST_DEFINED}, {@code CCM_TEST_ENABLED},
 *       {@code CCM_TEST_DISABLED}, {@code CCM_TEST_RUN_COMPLETED}.</li>
 *   <li>F7 audit_log entries for every CREATE / UPDATE / ENABLE /
 *       DISABLE / RUN_COMPLETED.</li>
 * </ul>
 *
 * <p>Three consecutive {@code FAIL}/{@code ERROR}/{@code TIMEOUT} runs
 * on a {@code CRITICAL}-severity test auto-open a cross-module
 * {@link com.tss.grc.module.platform.entity.Issue} so the
 * audit-committee dashboard surfaces the problem without operator
 * action.
 */
package com.tss.grc.module.ccm;
