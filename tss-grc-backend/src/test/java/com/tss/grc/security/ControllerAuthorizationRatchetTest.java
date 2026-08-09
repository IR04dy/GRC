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
import java.util.Set;
import java.util.TreeSet;

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
     *
     * <p>S2 tranche 1 (2026-07-25) lowered it a further FOURTEEN — 58 to 44:
     * the 7 {@code AiSuggestionController} POSTs plus {@code AiControlMappingController
     * #suggest} now require {@code hasPermission('AI','WRITE')} (they read tenant
     * governance data and draft recommendations — practitioners only), and the six
     * {@code RootCauseAnalysisController} writes are split by authority:
     * create/update/submit need {@code INCIDENT WRITE}, generate-capas needs
     * {@code CAPA WRITE}, and approve/reject need {@code CAPA APPROVE} — approving an
     * RCA gates the corrective actions generated from it, so it is an authority act,
     * not an authoring one. Read endpoints were deliberately left alone in this
     * tranche (the ratchet counts writes; read-gating is a separate reviewed sweep).
     *
     * <p>S2 tranche 2 (2026-07-25) lowered it a further TWENTY-THREE — 44 to 21 — and
     * that 21 is now the <em>enumerated</em> floor rather than an unexplained residue:
     * <ul>
     *   <li><b>8 legitimately public</b>: AuthController login/logout/refresh,
     *       ComplaintController#submitPublic, PublicWorkflowRequestController#respond,
     *       VendorBcpAttestationController#submitByToken, WhistleblowerCaseController
     *       #publicReply/#publicSubmit — all rate-limited by
     *       PublicEndpointRateLimitFilter, several gated by an opaque token.</li>
     *   <li><b>10 self-scoped</b> (the principal IS the authority, and the handler
     *       resolves the row from the caller): HelpController#trackView,
     *       NotificationController#markRead/#markAllRead, RefreshTokenAdminController
     *       #revokeAllMine/#revokeOne (verified: lists only the caller's sessions and
     *       404s otherwise), SavedViewController#create/#update/#delete,
     *       WebPushController#register/#unregister.</li>
     *   <li><b>3 participant actions pending an OWNERSHIP fix</b>, deliberately NOT
     *       permission-guarded: PlanDistributionController#acknowledge/#decline and
     *       ExerciseRuntimeController#respond. BCM_READ is granted to only 17 of 57
     *       roles, so a permission here would lock out ordinary staff who are plan
     *       recipients / exercise participants. The real defect is that
     *       PlanDistributionService.transition() scopes by organisation but never
     *       checks the caller is the row's designated recipient — so one user can
     *       acknowledge another's plan distribution, and that acknowledgement is BCM
     *       compliance evidence. Fix = bind to recipientUserId (the column exists),
     *       not a role. Tracked as its own slice.</li>
     * </ul>
     * Guarded in this tranche: 17 manager-side BCM writes (contacts, compliance
     * mappings, library clone, settings, distribute/mark-stale, review attest+sweep,
     * crisis AAR generate/update/transition), the two @PatchMapping updates an earlier
     * Post|Put|Delete sweep had missed, ApprovalChain#recordAction/#withdraw and
     * ApprovalRequest#create/#transition. Authority actions (attest, AAR transition,
     * approval record/withdraw/transition) take APPROVE, authoring takes WRITE,
     * settings reset takes PLATFORM_ADMIN.
     *
     * <p><b>Caveat this ratchet cannot see — "guarded" is not "restricted".</b> The scan
     * only asks whether a {@code hasPermission}/{@code hasAnyRole} guard is present, not
     * whether the permission behind it is narrowly granted. {@code WORKFLOW_WRITE} is
     * held by <b>55 of 57 roles, including VIEWER</b> (the over-grant that caused CH-1 to
     * carve a dedicated CHANGE module out of it in V301), so
     * {@code ApprovalRequestController#create} now counts as guarded yet a read-only
     * viewer still passes it — live-verified 200. The guard is still worth having (it is
     * module-scoped and tightens automatically if those grants are ever cleaned up), but
     * this floor number must not be read as least-privilege. Deciding is the control that
     * matters there, and it IS properly gated: {@code #transition} requires WORKFLOW
     * APPROVE, which VIEWER does not hold (403 verified). A grant-tightness audit —
     * "which permissions are so widely granted that guarding on them is cosmetic?" — is
     * a separate exercise from this endpoint-coverage ratchet.
     *
     * <h3>⚠ 2026-08-06 (F2-144b) — the floor is now a PINNED SET, not a count</h3>
     * <p>Everything above stays true and is kept as the derivation of the number. What changed is the
     * assertion. For a year this test asserted {@code count <= 21} while the javadoc *enumerated* the
     * 21 in three reasoned buckets — so the prose was an invariant and the code was a ceiling, and only
     * the code runs. A ceiling tolerates the population it measures: any of the 21 could have been
     * swapped for a different unguarded endpoint, or all 21 guarded and 21 fresh ones introduced, and
     * this test would have read green both times. That is the same defect house rule 3 records against
     * {@code BARE_EXPORT_CEILING} — <b>once a residue is enumerated and reviewed, leaving a count in
     * place makes a tolerated endpoint indistinguishable from a reviewed one</b>.
     *
     * <p>The three buckets are now {@link #PUBLIC_BY_DESIGN}, {@link #SELF_SCOPED} and
     * {@link #PENDING_OWNERSHIP_FIX}, and the scan must equal their union <b>exactly</b>. That makes the
     * test honest in the direction a ceiling can never be: when an endpoint on this list is finally
     * guarded, renamed or deleted, the test <b>fails</b> and the entry must be removed — so the floor
     * cannot silently retain members that no longer exist, and the count is derived from the reviewed
     * set rather than declared beside it.
     *
     * <p>What prompted the conversion: F2-144(b) is the first request to <em>add</em> a member since the
     * floor was enumerated, and the honest way to grant one is to make admission a reviewed act with a
     * stated reason instead of a number bump. See {@link #SELF_SCOPED}'s note on
     * {@code CoiController#submitDeclaration}.
     */
    private static final Set<String> PUBLIC_BY_DESIGN = Set.of(
            "AuthController#login",
            "AuthController#logout",
            "AuthController#refresh",
            "ComplaintController#submitPublic",
            "PublicWorkflowRequestController#respond",
            "VendorBcpAttestationController#submitByToken",
            "WhistleblowerCaseController#publicReply",
            "WhistleblowerCaseController#publicSubmit");

    /**
     * The principal IS the authority: the handler resolves the row from the caller, so there is no
     * subject for a permission to be wrong about.
     *
     * <p><b>{@code CoiController#submitDeclaration} — admitted 2026-08-06 (F2-144b), and the order of
     * the two halves is the whole point.</b> The guard was
     * {@code hasAnyRole(…,'ETHICS_OFFICER','USER')} where <b>both of those names exist in no row of
     * {@code iam.roles}</b>, so a Nazaha conflict-of-interest campaign ran with every declarant locked
     * out and three administrators able to file on their behalf. Opening it to
     * {@code isAuthenticated()} was <em>unsafe until F2-144(a) landed</em>, because
     * {@code submitDeclaration} took {@code userId} from the request body — so a wider guard would have
     * let any authenticated user attest in another person's name, the one thing a COI process exists to
     * prevent. Part (a) made the server choose the declarant, which is exactly what qualifies this
     * endpoint for <em>this</em> bucket rather than for a raised ceiling: it is self-scoped <b>now</b>,
     * and it was not before. A permission is the wrong instrument here for F2-53/ADR-017 D-7's reason —
     * an obligation every employee is under cannot be gated by a grant list, because a grant list can
     * only ever wrongly exclude someone, and an employee who cannot file their declaration is a
     * compliance failure that looks like the employee's fault. ADR-010's amended §Sanctioned exceptions
     * states the rule; {@code CoiSubmitGuardTest} pins the expression.
     *
     * <p><b>{@code IntakeController#submit} — admitted 2026-08-09 (F2-172), and it is
     * {@code CoiController#submitDeclaration}'s superset rather than its analogue.</b> The intake
     * front door carries 26 kinds, and {@code ETHICS_DISCLOSURE} is the very duty the COI endpoint
     * was admitted for — so the two could not defensibly be gated differently. The others include
     * reporting a security event (NCA ECC 6.1), a privacy breach (PDPL Art. 27), a workplace injury
     * (GOSI, 24 hours) and wrongdoing anonymously (Nazaha, ISO 37001 §8.3). The old guard was
     * {@code hasPermission('INTAKE','WRITE')}, held by <b>3 of 69 roles, all administrators</b>
     * (COMPLIANCE_OFFICER, GRC_ADMINISTRATOR, ORG_ADMIN) — measured, not assumed.
     *
     * <p>The same ordering trap as F2-144, and avoided the same way: {@code submit()} read the
     * submitter's name, email and phone from the request body and the table had no column naming a
     * platform user, so widening the guard first would have bought an impersonation path. V374 adds
     * {@code submitter_user_id} and the service fills it from {@code CurrentActor}. There is a
     * second half with no COI counterpart — {@code submitterIsAnonymous} also arrived from the body,
     * and a server that simply obeyed it would let any caller make any submission unattributable on
     * request, using the field that exists to protect whistleblowers. The claim is honoured only for
     * the three kinds whose catalogue entry allows it. {@code IntakeSubmitGuardTest} pins the
     * expression, {@code IntakeSubmitterBindingTest} pins both identity properties.
     */
    private static final Set<String> SELF_SCOPED = Set.of(
            "CoiController#submitDeclaration",
            "IntakeController#submit",
            "HelpController#trackView",
            "NotificationController#markAllRead",
            "NotificationController#markRead",
            "RefreshTokenAdminController#revokeAllMine",
            "RefreshTokenAdminController#revokeOne",
            "SavedViewController#create",
            "SavedViewController#delete",
            "SavedViewController#update",
            "WebPushController#register",
            "WebPushController#unregister");

    /** Deliberately NOT permission-guarded — see the class javadoc: the real defect is recipient
     *  ownership, and a BCM permission here would lock out the ordinary staff who are the participants. */
    private static final Set<String> PENDING_OWNERSHIP_FIX = Set.of(
            "ExerciseRuntimeController#respond",
            "PlanDistributionController#acknowledge",
            "PlanDistributionController#decline");

    private static Set<String> sanctionedFloor() {
        Set<String> all = new TreeSet<>(PUBLIC_BY_DESIGN);
        all.addAll(SELF_SCOPED);
        all.addAll(PENDING_OWNERSHIP_FIX);
        return all;
    }

    /**
     * Anti-vacuity, and not a formality: both set comparisons below are satisfied by a scan that resolved
     * nothing at all. A classpath scan that silently matches no controller — a renamed base package, a
     * shaded jar, an annotation swap — would make "no new offenders" pass for the most reassuring possible
     * wrong reason. This is its own test rather than a first assertion inside the others, because an
     * earlier assertion in the same method masks the ones after it.
     */
    @Test
    void theScanResolvesARealCorpus() {
        int total = totalWriteEndpoints();
        System.out.println("[WS2 ratchet] total write endpoints scanned = " + total);
        assertThat(total)
            .as("the classpath scan resolved almost no write endpoints — the SCAN is broken, not the "
                + "codebase clean. Every other assertion in this class is vacuous until this passes.")
            .isGreaterThan(900);
    }

    @Test
    void noUnsanctionedUnguardedWriteEndpoint() {
        Set<String> unsanctioned = new TreeSet<>(scan());
        unsanctioned.removeAll(sanctionedFloor());
        assertThat(unsanctioned)
            .as("New write endpoint(s) with no hasPermission/hasAnyRole guard. Add the correct "
                + "@PreAuthorize(\"hasPermission('MODULE','ACTION')\"). Admitting one to the floor "
                + "instead requires a bucket above, a stated reason, and an ADR-010 exception if it is "
                + "neither public nor self-scoped — see F2-144(b) for the worked example.")
            .isEmpty();
    }

    /**
     * The direction the old count ceiling could never fail in — which is the whole reason for the
     * conversion. When a floor member is finally guarded, renamed or deleted, this fails until the entry
     * is removed, so the floor cannot go on tolerating endpoints that no longer exist.
     */
    @Test
    void theSanctionedFloorHasNoStaleMembers() {
        Set<String> stale = new TreeSet<>(sanctionedFloor());
        stale.removeAll(scan());
        assertThat(stale)
            .as("Sanctioned floor member(s) no longer appear in the scan — they were guarded, renamed "
                + "or deleted. Remove them from the set above: a floor that keeps entries for endpoints "
                + "that no longer exist stops being a record of what is tolerated.")
            .isEmpty();
    }

    private static List<String> scan() {
        List<String> unguarded = scanUnguardedWriteEndpoints();
        Collections.sort(unguarded);
        System.out.println("[WS2 ratchet] unguarded write endpoints = " + unguarded.size()
                + " (sanctioned floor = " + sanctionedFloor().size() + ")");
        unguarded.forEach(s -> System.out.println("  - " + s));
        return unguarded;
    }

    /** Total write endpoints reachable by the same scan, guarded or not — the denominator that proves
     *  the scan is looking at something. */
    private static int totalWriteEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int n = 0;
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (isWriteMapping(m)) n++;
            }
        }
        return n;
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
