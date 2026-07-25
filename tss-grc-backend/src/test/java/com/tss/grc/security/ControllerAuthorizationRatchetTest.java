package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS2 authorization RATCHET (from the audit remediation plan).
 *
 * <p>Scans every {@code @RestController} and flags state-changing endpoints
 * (POST/PUT/PATCH/DELETE) whose effective {@code @PreAuthorize} is NOT a real
 * permission/role check — i.e. {@code isAuthenticated()}, {@code permitAll}, or
 * missing entirely. These are the Broken-Function-Level-Authorization (A01)
 * findings from the audit: any authenticated user can invoke them.
 *
 * <p>The current count is captured as {@link #BASELINE_UNGUARDED_WRITE_ENDPOINTS}.
 * This test FAILS if the count rises — i.e. a new unguarded write endpoint is
 * introduced — so the debt can only shrink. As WS2 fixes land, lower the
 * baseline; the goal is 0.
 */
class ControllerAuthorizationRatchetTest {

    /**
     * Unguarded write endpoints remaining after the audit BFLA remediation.
     * Started at 422 when WS2 began (2026-06-21); the P0/P1 critical+high
     * authorization fixes (CyberService, socops, approvals, then the
     * security-Highs batch covering platform/automation/licensing, all 14 NDI
     * controllers, privacy, compliance, esg, evidence, scm, iam, integrations,
     * risk, tprm, bcm) brought it to 186 (measured 2026-06-21, post-batch).
     * The P1 risk-hardening batch (SEC-RISK-004: EmergingRiskController,
     * RiskScenarioController, CapaTaskController, FairCalculatorController —
     * 14 write endpoints moved from isAuthenticated() to
     * hasPermission('RISK'|'CAPA', …)) brought it to 172.
     * The P1 BCM-hardening batch (SEC-BCM-002: 55 write endpoints across
     * CrisisEventController, BcpPlanController, BcmRiskController,
     * BcmRemediationController, BcmCycleController, BcmAuditCompletionController,
     * BcmBiaAssessmentController and DrPlanController moved from
     * isAuthenticated() to hasPermission('BCM', …)) brought it to 117.
     * The full P1 security-MEDIUM batch (SEC-CMP/ESG/EVD/IAM/INT/NDI/PLAT/PRIV/
     * RISK/FIN/TPRM/CFG: controlmap, sustainability, evidence-chain & board-pack,
     * workflow-submission & committee, integrations, NDI, the platform controllers
     * — control-library/regulatory/audit-log/persistence/file-upload/buyer-gap/
     * department/issue/calendar — emerging-risk/scenario/capa/fair, fourth-party &
     * supplier-resilience, third-party-cyber, etc. all moved from isAuthenticated()
     * to hasPermission('MODULE','ACTION')) brought the measured count to 61
     * (re-measured authoritatively 2026-06-21 after the batch landed).
     * MUST only ever decrease. Do NOT raise this to make a new endpoint pass —
     * add the correct {@code hasPermission('MODULE','ACTION')} guard instead.
     * The eventual floor is the small set of legitimately-public write endpoints
     * (auth login/refresh/logout, the public intake/whistleblower trackers,
     * inbound webhooks); everything else should carry a real permission/role check.
     *
     * <p>WF-7 (2026-07-06) raised the floor by exactly ONE — from 61 to 62 — for
     * {@code PublicWorkflowRequestController#respond}: the tokenized public tracker
     * where an EXTERNAL workflow-request recipient acknowledges via their opaque
     * access token (the token IS the credential, mirroring the already-counted
     * whistleblower {@code publicReply} and complaints {@code publicSubmit} POSTs).
     * It is {@code permitAll} by design, throttled per-IP by
     * PublicEndpointRateLimitFilter and gated by a 122-bit random token — a
     * sanctioned floor member, not a missing guard.
     *
     * <p>S1/W0-5 (2026-07-25) lowered the floor by FOUR — from 62 to 58 — by guarding
     * the e-signature writes, the highest-risk members of the residual set: the
     * independent assessment (finding F-1) showed that ADR-019 sells e-signed minutes
     * as tamper-evidence while {@code SignatureController#sign} carried NO authority
     * check at all and {@code DigitalSignatureController#create/sign/reject} carried
     * only {@code isAuthenticated()} — so a read-only VIEWER could record signatures.
     * They now require {@code hasPermission('SIGNATURE', 'WRITE'|'APPROVE')},
     * catalogued and granted by migration V313. The remaining 58 are being worked
     * down per {@code UNIFIED_IMPLEMENTATION_PLAN.md} stage S2, whose target floor is
     * 17 (8 legitimately-public + 9 self-scoped endpoints, each to be marked
     * explicitly rather than silently tolerated).
     */
    private static final int BASELINE_UNGUARDED_WRITE_ENDPOINTS = 58;

    @Test
    void noNewUnguardedWriteEndpoints() {
        List<String> unguarded = scanUnguardedWriteEndpoints();
        Collections.sort(unguarded);
        System.out.println("[WS2 ratchet] unguarded write endpoints = " + unguarded.size());
        unguarded.forEach(s -> System.out.println("  - " + s));

        assertThat(unguarded.size())
            .as("A write endpoint lacks a hasPermission/hasAnyRole guard. Add the correct "
                + "@PreAuthorize(\"hasPermission('MODULE','ACTION')\") — do not raise the baseline. "
                + "Offenders are printed above.")
            .isLessThanOrEqualTo(BASELINE_UNGUARDED_WRITE_ENDPOINTS);
    }

    static List<String> scanUnguardedWriteEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> unguarded = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (isWriteMapping(m) && !isGuarded(m, c)) {
                    unguarded.add(c.getSimpleName() + "#" + m.getName());
                }
            }
        }
        return unguarded;
    }

    private static boolean isWriteMapping(Method m) {
        if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
            || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(DeleteMapping.class)) {
            return true;
        }
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null) {
            for (RequestMethod meth : rm.method()) {
                if (meth == RequestMethod.POST || meth == RequestMethod.PUT
                    || meth == RequestMethod.PATCH || meth == RequestMethod.DELETE) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A method is guarded if it (or its declaring class) carries a @PreAuthorize
     *  expressing a real permission/role check — not merely isAuthenticated/permitAll. */
    private static boolean isGuarded(Method m, Class<?> c) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        if (pa == null) {
            pa = c.getAnnotation(PreAuthorize.class);
        }
        if (pa == null) {
            return false;
        }
        String v = pa.value().toLowerCase();
        return v.contains("haspermission") || v.contains("hasanyrole")
            || v.contains("hasrole") || v.contains("hasauthority");
    }
}
