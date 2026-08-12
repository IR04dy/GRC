package com.tss.grc.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * F2-185(a) — a <b>public method on a shipped {@code @Service} that nothing invokes</b>.
 *
 * <h3>Why this instrument did not exist, and what found the gap</h3>
 * <p>Two reachability instruments already guard this platform and <b>neither can see this</b>:
 * <ul>
 *   <li>{@code apiClientMethodReachability.test.ts} (F2-165) resolves <b>TypeScript API-client
 *       methods</b> — it answers "does a screen call this client method", and it lives in the
 *       frontend. A Java service method is not in its universe.</li>
 *   <li>{@code FrontendEndpointContractTest} (F2-170) resolves <b>endpoints</b> — every endpoint an
 *       FE client calls must have a controller, plus a decrease-only count of endpoints with no FE
 *       client. A service method with no controller <b>has no endpoint</b>, so it is not in that
 *       population either.</li>
 * </ul>
 * <p>F2-184 found {@code WhistleblowerRetaliationService#transitionTo} only by accident: its
 * request DTO carried an inert {@code @NotBlank}, and an inert constraint on a {@code *Request}
 * type is a fingerprint of a dark path. 🔑 <b>That fingerprint only exists where the dark method
 * happens to take a constrained DTO.</b> A dark method taking primitives leaves none — so the
 * fingerprint could never have measured the population, only stumbled on members of it.
 *
 * <h3>Bytecode, not a source regex — and this time the reason is arithmetic</h3>
 * <p>Call sites come from the compiled constant pool via ASM. The register records four pattern
 * mistakes from one slice of doing this with regexes (F2-165), three phantom counts over Java in
 * three consecutive findings (F2-170's 1,763 endpoints from a javadoc sentence, F2-179's 0-of-389,
 * F2-184's 75 types), and F2-182's setter-site scan whose 10 "open" writers were 6 already closed.
 * A constant pool has no comments, no prose and no strings that look like calls.
 *
 * <h3>⚠ What this instrument deliberately does not count as a caller</h3>
 * <ul>
 *   <li><b>Tests.</b> Only {@code target/classes} is scanned for call sites. A method whose only
 *       caller is its own unit test is reached by nothing that ships — the F2-159 decoy shape,
 *       where a test asserted a defect as correct.</li>
 *   <li><b>Reflection, SpEL and scheduler-by-name.</b> Invisible in bytecode, so they are
 *       subtracted structurally below (container annotations, interface contracts) rather than
 *       guessed at. This is the honest limit of the instrument and it is why the survivors are a
 *       named list and not an assertion of zero.</li>
 * </ul>
 *
 * <h3>🔑 Why the survivors are a named map with a reason each</h3>
 * <p>Because the first run proved the population is not homogeneous. Its raw answer was <b>51</b>;
 * 16 are {@code @Scheduled}/{@code @EventListener} (the container calls them), 5 implement a
 * framework interface, 2 are declared test seams — and of the 28 that remained, <b>four are a
 * documented expand-contract residue</b>: {@code PolicyService}'s legacy lifecycle, retired from
 * HTTP by CH-6/PM-C1 with {@code PolicyManagementService#transition} as the live twin and CH-9
 * named as the exit. A hard zero would have manufactured four false P2s on its first run —
 * F2-165's own warning that <i>"a classification rule you can only apply by intuition will
 * manufacture a P1 on its first use"</i>, arriving again one instrument later.
 *
 * <p>So this is the {@code InertValidationConstraintTest.TOLERATED} shape, for the same reason
 * CLAUDE.md gives for retiring {@code BARE_EXPORT_CEILING}: a bare count lets a new dark method be
 * swapped in for a deleted one and still pass, and a tolerated entry becomes indistinguishable
 * from a reviewed one. Both directions are enforced, so a method that gains a caller — or is
 * deleted — must lose its entry in the same commit.
 *
 * <h3>⚠⚠ And the reasons in that map have now been audited twice, from two angles</h3>
 * <ul>
 *   <li><b>F2-188</b> took the five entries claiming <i>"a row can never leave this state"</i> and
 *       found <b>all five false</b>. It added
 *       {@link #noReasonAssertsUnreachabilityWithoutAScanThatVerifiesIt()}, scoped by
 *       {@link #UNREACHABILITY_CLAIM} to state-transition verbs.</li>
 *   <li><b>F2-192</b> took the <em>larger</em> bucket — the capability claims (<i>"no endpoint does
 *       X", "only the list is wired", "cannot be resolved by any route"</i>) — which a state-verb
 *       pattern cannot see, and found <b>seven false and four overstated</b>. Every one had been
 *       written from the dark method alone while a routed sibling on the same class already did the
 *       thing the reason called impossible. It added
 *       {@link #everyReasonIsWrittenAgainstItsOwnerClassLiveSurface()}.</li>
 * </ul>
 * <p>🔑 <b>A gate that verifies claims verifies the claim shape it was written for.</b> That is
 * F2-187's own headline — <i>a reachability gate certifies the granularity it resolves</i> — arriving
 * one level up, at the gate built to answer it. Assume the next reader finds a third shape.
 */
class DarkServiceMethodTest {

    /** Names the JDK, Spring or Jackson invokes, which no call site of ours will show. */
    private static final Set<String> FRAMEWORK_NAMES = Set.of(
        "equals", "hashCode", "toString", "clone", "finalize", "compareTo", "run", "call", "get",
        "apply", "accept", "test", "close", "afterPropertiesSet", "destroy", "onApplicationEvent",
        "getOrder", "afterCommit", "afterCompletion", "beforeCommit", "supports", "convert",
        "matches", "resolveArgument", "handle", "initialize", "isValid", "compare");

    /** Annotations that mean "the container calls this, not us". */
    private static final List<Class<? extends java.lang.annotation.Annotation>> CONTAINER_ANN =
        List.of(Scheduled.class, EventListener.class, TransactionalEventListener.class,
                PostConstruct.class, PreDestroy.class);

    /**
     * The dark public service methods this slice deliberately left, each with the reason and — the
     * part that matters — <b>what a user cannot do because of it</b>.
     *
     * <p>F2-165's rule for sorting this population is by <b>effect, not verb name</b>: a dark method
     * whose effect a live sibling already achieves is dead code; one with no live sibling is lost
     * capability. That rule is applied per entry below, because applying it in the aggregate is what
     * produced its "no screen can approve evidence" false alarm.
     */
    private static final Map<String, String> TOLERATED = new TreeMap<>(Map.ofEntries(

        // ── Deliberate expand-contract residue: retired from HTTP, live twin named, exit named ──
        // ⚠ NINE methods, not the four the first (Python, name-fallback) pass reported. The whole
        // legacy write lifecycle is here, which is what RegChangePolicyController's comment already
        // said out loud: "Legacy policy writes RETIRED (CH-6/PM-C1) … only the read endpoints
        // (list/get/stats) remain … PolicyService keeps the now HTTP-unexposed lifecycle methods
        // (+ their tests) through this expand-contract window; the whole legacy service goes with
        // CH-9." 🔑 The comment is the difference between dead code and a decision — and it sits by
        // the CALLER, not by the methods, which is why no scan of the service could find it.
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#submitForLegalReview",
            "RETIRED BY DESIGN. CH-6/PM-C1 retired the legacy compliance.policies write lifecycle; "
            + "authoring lives on the canonical register (PolicyManagementService#transition). The "
            + "READS on this very class are still live — RegChangePolicyController keeps #list, #get "
            + "and #stats — which is why the class survives at all, and its comment names CH-9 as "
            + "when the whole service goes. Nothing is lost: the effect has a live twin."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#legalApprove",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#executiveApprove",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#markReviewDue",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#create",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats. Its "
            + "mass-assignment defence is still worth reading: RegulatoryChangeService cites it by "
            + "name."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#update",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#delete",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#reject",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#retire",
            "RETIRED BY DESIGN — see #submitForLegalReview. Live surface: #list, #get, #stats."),

        // ── Declared test seams: reached only by tests, on purpose ───────────────────────────
        // Listed rather than subtracted by a name rule. A `setClock`-shaped exemption would let any
        // dark method be hidden by renaming it, which is the "classification rule you can only apply
        // by intuition" F2-165 warns manufactures a finding on first use.
        Map.entry("com.tss.grc.module.aml.sanctions.service.SanctionsHitService#setClock",
            "DECLARED TEST SEAM — injects a fixed Clock so date-relative assertions are stable. What "
            + "it exists to make testable is live: #detectSlaBreachesForOrg is called by the "
            + "screening scheduler, and #reviewHit / #escalate are routed. CLAUDE.md records why this "
            + "seam matters here specifically: LocalDate.now() in a test is UTC+3 on this machine "
            + "while the services are UTC, so a date-relative fixture is wrong for three hours of "
            + "every day (the F2-178 correction). Reached by tests only, which is the point."),
        Map.entry("com.tss.grc.module.aml.sanctions.service.SanctionsScreeningSchedulerService#setClock",
            "TEST SEAM — and ⚠⚠ IT WAS NOT ONE UNTIL F2-192. This entry read \"DECLARED TEST SEAM — "
            + "reached by tests only, which is the point\", and the new scan found NO caller at all: "
            + "not in main sources, not in any test. SanctionsHitServiceTest calls the identically "
            + "named setter on the sibling class. So it had been classified a seam BECAUSE it is "
            + "called setClock — the exact error the comment above this block warns about. What that "
            + "exposed is the real gap: #runSlaSweep flips sla_breach and fires "
            + "SANCTIONS_HIT_SLA_BREACHED across tenants off a LocalDateTime.now(clock) cutoff and had "
            + "no test of any kind. SanctionsSchedulerSweepTest now drives both ticks through this "
            + "seam, so the claim is true and the scan proves it. ⚠ This is also the one entry with no "
            + "live sibling to cite — every method here is @Scheduled (#refreshTick, #slaSweepTick: "
            + "the container calls them, so no call site exists in the bytecode) or package-private — "
            + "which is why SEAM_VERIFIED_BY_TESTS exists and why it is checked, not trusted."),

        // ── Outbound integrations with no production trigger (filed: F2-187) ──────────────────
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushCyberIncident",
            "NO PRODUCTION TRIGGER. The class's entire live surface is #push, reached from "
            + "ServiceNowController's /test-push; the controller otherwise exposes /health and "
            + "/config only. So a real cyber incident is never pushed to ServiceNow — the integration "
            + "reads as working (health green, config present, test-push succeeds) while no domain "
            + "event reaches it. The service's own javadoc states the v1 intent (\"modules whose DTOs "
            + "haven't stabilised\"), so this is a posture rather than a bug, but the posture is "
            + "invisible to a buyer. F2-187."),
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushFinding",
            "NO PRODUCTION TRIGGER — see #pushCyberIncident. F2-187."),
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushCapaTask",
            "NO PRODUCTION TRIGGER — see #pushCyberIncident. F2-187."),
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushComplianceGap",
            "NO PRODUCTION TRIGGER — see #pushCyberIncident. F2-187."),
        Map.entry("com.tss.grc.module.integrations.splunk.service.SplunkOutboundService#pushGenericEvent",
            "F2-193 CORRECTION — this said Splunk \"has one real trigger\" where ServiceNow has none, "
            + "citing #pushSecurityEvent from SplunkController's POST /security-event/{id}. That route "
            + "exists, and its own @Operation summary reads \"MANUALLY push a known SOC security event "
            + "into Splunk\". A ROUTE IS NOT A TRIGGER. Measured across the whole module: no "
            + "@Scheduled class and no event listener calls this service, so Splunk egress has no "
            + "production trigger either — what it has that ServiceNow lacks is a typed manual route "
            + "next to the smoke test. #push is live from POST /test-push, and the free-form overload "
            + "on top of it is the dark one. F2-187/F2-193."),
        Map.entry("com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeOutboundService#hideHost",
            "DEAD DUPLICATE-ish: #containHost and #liftContainment are live from CrowdStrikeController "
            + "(/contain/{hostId} and /lift/{hostId}), so host containment IS reachable; #hideHost is "
            + "a second containment verb nothing routes to. Listed rather than deleted because "
            + "CrowdStrikeClient#hideHost is the vendor API call the contain path may need to switch "
            + "to. F2-187."),

        // ── ⚠⚠ F2-188: this used to be "a state nothing can ever move to — the sharpest bucket". ──
        // All FIVE reasons were false, and every one of them was written from the method alone. Each
        // has a live twin, and in four cases the evidence was in a comment I did not open: the
        // scheduler's, the replacing service's, or the method's own javadoc one line above the
        // signature. See theSharpestBucketWasEmpty() for the audit and what it cost.
        //
        // markOverdue is NOT in this map any more: CommitteeScheduledSweep now calls it, which is the
        // one real defect the audit found. It had been flipping the row itself.
        // markOverdueForCampaign is NOT in this map any more (F2-191). Its reason said the two variants
        // "must not diverge" — they had: the campaign service held a third copy of the loop, unguarded
        // and silent. ObligationCampaignService#escalateOverdue now calls it, so it has a caller and the
        // both-directions assertion required this entry's deletion. That is twice this gate has been the
        // thing that noticed a fix was incomplete (markOverdue was the first).
        Map.entry("com.tss.grc.module.ccm.service.CcmService#recordRun",
            "A THIRD entry point to recordRunInternal, for a result computed outside the platform. "
            + "The ladder is not dark: CcmSchedulerService#tick is @Scheduled(fixedDelay=60s) and "
            + "calls #runDefinitionInternal, and POST /ccm/tests/{externalId}/run-now reaches "
            + "#runTestNow — both land in recordRunInternal, which owns the consecutive-failure Issue "
            + "ladder and the last_run_* stamps (#listLatestRuns and #getRunsForTest read them back). "
            + "Kept as the ingestion seam for an external runner. F2-187/F2-188."),
        Map.entry("com.tss.grc.module.socops.service.IrPlaybookService#recordInvocation",
            "SUPERSEDED by IrPlaybookRunService, whose own comment says it preserves 'the legacy "
            + "invocation semantics the dashboard + F8 rules rely on' and emits invocationCount "
            + "itself. Invocations ARE recorded, as trackable runs with snapshotted tasks; the "
            + "playbook itself is fully live on this class (#save, #cloneFromTemplate, #stepsOf, "
            + "#templates). Kept because the counter-only path is cheaper for a bulk import. "
            + "F2-187/F2-188."),
        Map.entry("com.tss.grc.module.socops.service.ThreatIntelService#markActioned",
            "A LEGACY SHIM, and its own javadoc says so one line above the signature: 'the old "
            + "\"mark done\" path (no concrete target) now records a REVIEWED action'. It delegates "
            + "to #actionIoc, which is live from ThreatIntelController and writes the action history "
            + "plus the denormalised actioned/pending queue (#listActions reads it). F2-187/F2-188."),

        // ── ⚠⚠ F2-192: "built and unwired" — the bucket F2-188's gate could not see. ─────────
        // These reasons claim a CAPABILITY is absent ("no endpoint does X", "only the list is
        // wired"). UNREACHABILITY_CLAIM is scoped to state-transition verbs, so none of them ever
        // came under scrutiny. Measured against the owner classes' live surfaces, four of the five
        // were wrong or overstated — and each was written from the dark method alone, exactly as the
        // five F2-188 audited were. The corrections are kept in place rather than rewritten away.
        Map.entry("com.tss.grc.module.ai.governance.service.MlModelDriftService#listUnacknowledged",
            "F2-192 CORRECTION — this said \"the CRITICAL-drift escalation F2-175 fixed can be filed "
            + "and never reviewed\", and that was false twice over. #list is live from "
            + "MlModelDriftController and its response carries isAcknowledged, so drift observations "
            + "ARE readable; and the escalation F2-175 wired files an INTAKE RECORD, whose queue "
            + "(GET /api/v1/intake, server-backed end to end since F2-172) is the review surface. "
            + "What is genuinely absent is narrower: no tenant-wide, cross-model unacknowledged feed, "
            + "so a reviewer must already know which model drifted. F2-187."),
        Map.entry("com.tss.grc.module.bcm.cycle.runtime.observer.service.ExerciseObserverService#buildAarSection",
            "F2-192 CORRECTION — \"observer findings never reach the AAR\" overstated it. "
            + "#flagObservationForAar and #promoteObservationToGap are both live from "
            + "ExerciseObserverController, so a flagged observation does reach the BCM gap register, "
            + "which is where NCEMA evidence is actually drawn from, and #listObservations / "
            + "#getObservationsByType read them. The narrow truth: nothing composes this markdown "
            + "section, and CrisisAar is keyed to crisis_event_id rather than to an exercise run, so "
            + "there is no exercise-run AAR for it to be composed into. F2-187."),
        Map.entry("com.tss.grc.module.platform.push.service.WebPushService#dispatchToUserIds",
            "⚠ DECLARED v1 STUB — and this entry used to read \"a notification cannot be pushed to "
            + "specific users\", which invites the wrong remedy. The method's own javadoc, four lines "
            + "above the signature, says \"v1 stub for the actual Web Push dispatch … the real "
            + "VAPID-signed POST to the browser push service is wired in Sprint 12\": it resolves "
            + "subscriptions, writes an audit row and emits WEB_PUSH_DISPATCHED, and delivers "
            + "nothing. So giving it a caller would produce a dispatch trail for pushes that never "
            + "left the server. Registration IS live (#register, #unregister, #listForCurrentUser), "
            + "so browsers subscribe and nothing is ever sent — the delivery half of what F2-38(d) "
            + "left open as an owner decision. F2-187/F2-192."),
        Map.entry("com.tss.grc.platform.realtime.RealtimeBroadcaster#sendToUser",
            "F2-192 CORRECTION — \"only BroadcastHelper's tenant-wide path is live\" named the wrong "
            + "caller: #broadcastEntityUpdate is called directly by 14 services and #broadcastOrgEvent "
            + "by 2. The per-user channel is documented in THREE artefacts and implemented in none — "
            + "WebSocketConfig's javadoc lists /user/queue/notifications, lib/realtime.ts repeats it "
            + "in its topic conventions, and this method publishes to it — while no frontend "
            + "subscription targets it (every subscribe() call builds /topic/org.{orgId}.…). It is "
            + "dark on BOTH ends, so wiring the publisher alone would deliver to nobody. Pairs with "
            + "WebPushService#dispatchToUserIds. F2-187/F2-192."),
        Map.entry("com.tss.grc.module.privacy.subprocessor.service.SubprocessorEventService#listAll",
            "SURVIVED the F2-192 audit, because the live sibling answers a different question: "
            + "#listForSubprocessor is routed per subprocessor (and #recordEvent writes the events), "
            + "while the tenant-wide feed this method returns has no route at all. So a DPO cannot "
            + "review every sub-processor change in one place, which is the view PDPL sub-processor "
            + "oversight actually needs. F2-187."),

        // ── Predicates and readers nothing consults: low blast radius, but say why they matter ──
        // ⚠ F2-192 rewrote six of these. Each had described a capability as missing when a routed
        // sibling on the same class already provided it — and in one case (#usageFor) the reason had
        // the method's own parameter wrong, which reading the signature would have shown.
        Map.entry("com.tss.grc.module.platform.controls.service.ControlLibraryService#hasRecentApprovedAssessment",
            "A control-assessment freshness predicate nothing asks. #listAssessmentsForControl is live "
            + "from ControlLibraryController, so the assessments are visible — what no code does is "
            + "JUDGE them, and a freshness check that is never consulted is exactly how a stale "
            + "control passes for current. Worth a second look before deletion rather than after. "
            + "F2-187."),
        Map.entry("com.tss.grc.module.change_mgmt.service.ChangeManagementService#isConditional",
            "F2-192 CORRECTION — \"the conditions attached at CAB are invisible downstream\" was "
            + "false: #conditions and #openConditions are both live from ChangeManagementController, "
            + "and #addCondition writes them. What is dark is only the derived boolean over "
            + "conditionRepo.count, not the conditions themselves. F2-187."),
        Map.entry("com.tss.grc.module.change_mgmt.service.ChangeGovernanceHealthService#isGateConfigured",
            "F2-192 CORRECTION — this claimed \"the query form of an answer the platform only ever "
            + "logs\", and both halves were wrong. The method is a one-line delegate to "
            + "#gateConfigurationFault, which is live and consulted twice: ChangeManagementService "
            + "turns a non-null fault into ChangeAuthorityNotConfiguredException, so an unconfigured "
            + "CAB/ECAB gate REFUSES the approval (A17-D2, fail closed), and CommitteeGateMapService "
            + "renders configured/fault per gate. #currentTenantHealth is routed too. So this is a "
            + "redundant boolean over a live, load-bearing method — not an unasked question. F2-187."),
        Map.entry("com.tss.grc.module.admin.committee.service.CommitteeCatalogService#countStandingItems",
            "Standing-item count nothing reads; #getStandingItems is live from "
            + "CommitteeCatalogController and the committee registry screen derives its own count from "
            + "that list. A second, unconsulted counter is how two numbers for one thing start "
            + "disagreeing. F2-187."),
        Map.entry("com.tss.grc.module.admin.committee.service.CommitteeCatalogService#findTemplate",
            "F2-192 CORRECTION — \"templates cannot be resolved by any route, so a tenant cannot "
            + "instantiate a committee from one\" was false: #getTemplate, #listAllTemplates and "
            + "#listTemplatesByDomain are all live from CommitteeCatalogController. This is the "
            + "tolerant Optional twin of #getTemplate — it swallows a missing-object error where the "
            + "throwing variant propagates it. F2-187."),
        Map.entry("com.tss.grc.module.change_mgmt.service.ChangeModelService#usageFor",
            "F2-192 CORRECTION — this called the method a \"per-change-model usage count\" and it "
            + "takes a changeId, not a model id; reading the signature would have shown that. "
            + "#backingFor runs the identical usageRepo.findByOrganisationIdAndChangeRequestId lookup "
            + "and is live from ChangeManagementController, with #models and #liveModelFor live too — "
            + "so whether a model backs a change IS answerable. F2-187."),
        Map.entry("com.tss.grc.module.governance.threelines.service.ThreeLinesService#bandForUser",
            "F2-192 CORRECTION — \"the independence model is recorded and never enforced or displayed "
            + "per user\" was false on both counts. #userBandFor is live from ThreeLinesController and "
            + "returns this exact effective band inside UserBandDto, and #bandsForUser is live from "
            + "JwtTokenProvider, which puts lod_band into the token — so the model is available to "
            + "authorisation on every request. This is the third variant of one computation (scalar / "
            + "list / DTO) and the only dark one. Deleting it is an owner call rather than a fix: four "
            + "ThreeLinesServiceTest cases assert the band ranking through it. F2-187."),
        Map.entry("com.tss.grc.module.ndi.settings.service.NdiSettingService#stringValue",
            "Typed accessor with a fallback that no caller uses; #getByKey is live from "
            + "NdiSettingController and callers read the raw setting through it, which is how a "
            + "default declared in one place stops being applied. #list, #listGrouped, #setValue and "
            + "#resetToDefault are all routed. F2-187."),
        Map.entry("com.tss.grc.module.platform.regulatory.service.AuthorityDocumentService#citationCount",
            "F2-192 CORRECTION — \"a regulatory-library screen cannot show how heavily a document is "
            + "relied upon\" was false: #listCitations is live from AuthorityDocumentController, so a "
            + "screen counts the list, and #rollupByFamily is live for the aggregate view. This is the "
            + "count-only variant of a routed read. F2-187."),
        Map.entry("com.tss.grc.module.sust.ghg.service.EmissionFactorService#resolveActive",
            "SURVIVED the F2-192 audit, and it is the sharpest survivor: #list and #get are live from "
            + "EmissionFactorController, but GhgEmissionRecordService resolves a record's factor by "
            + "stored FK (factorRepo.findByIdAndOrganisationId(r.getEmissionFactorId(), …)) and never "
            + "by code + isActive. So the 'which factor is active for this code' question this method "
            + "answers is not the one the numbers were produced with. Check before deleting — ISAE "
            + "3410 assurance lineage depends on that answer. F2-187."),
        Map.entry("com.tss.grc.module.sust.climate.service.FinancedEmissionsService#getTarget",
            "F2-192 CORRECTION — \"unreachable\" overstated it: #listTargets is live from "
            + "FinancedEmissionsController and carries the same fields, and #computeReductionAgainstTarget "
            + "is live and consulted by TcfdReportService. What is missing is only a single-target "
            + "detail ROUTE. F2-187.")
    ));

    // ── the scan ─────────────────────────────────────────────────────────────────────────────

    /** (ownerInternalName, methodName) of every call site in main classes. */
    private static Set<String> callSites() throws IOException {
        Set<String> calls = new LinkedHashSet<>();
        for (Resource r : mainClasses()) {
            try (InputStream in = r.getInputStream()) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName,
                                                        String desc, boolean isInterface) {
                                calls.add(owner + "#" + mName);
                            }

                            @Override
                            public void visitInvokeDynamicInsn(String iName, String desc,
                                                               Handle bsm, Object... bsmArgs) {
                                // Method references (Foo::bar) and lambdas land here; the referenced
                                // method is a Handle among the bootstrap arguments. Missing these
                                // would report every ::-referenced method as dark.
                                for (Object a : bsmArgs) {
                                    if (a instanceof Handle h) {
                                        calls.add(h.getOwner() + "#" + h.getName());
                                    }
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_FRAMES);
            }
        }
        return calls;
    }

    private static List<Resource> mainClasses() throws IOException {
        return classesUnder("/target/classes/");
    }

    /**
     * Test classes — used for exactly one purpose: verifying a "reached by tests only" claim.
     *
     * <p>They are still not callers for {@link #measureDark()}. The difference matters: a seam's
     * whole claim is that a test reaches it, and that claim is checkable rather than assertable.
     */
    private static List<Resource> testClasses() throws IOException {
        return classesUnder("/target/test-classes/");
    }

    private static List<Resource> classesUnder(String segment) throws IOException {
        List<Resource> out = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/tss/grc/**/*.class")) {
            String url = r.getURL().toString().replace('\\', '/');
            // Deliberate: test classes are NOT callers for the dark measurement. See class javadoc.
            if (url.contains(segment)) {
                out.add(r);
            }
        }
        return out;
    }

    private static String internalNameOf(Resource r, String segment) throws IOException {
        String url = r.getURL().toString().replace('\\', '/');
        int i = url.indexOf(segment);
        return url.substring(i + segment.length()).replace(".class", "");
    }

    /**
     * F2-192 — {@code owner#method} for every call site whose <b>calling class is not the owner</b>.
     *
     * <p>Deliberately narrower than {@link #callSites()}, and the narrowing is the point: this set
     * answers <i>"what does something OUTSIDE this service use"</i>, which is the question a
     * capability claim is actually about. A method reached only from inside its own class is not
     * dark (so {@code measureDark} is right to use the wider set) and is also not part of the
     * class's live surface (so this one is right to exclude it).
     *
     * <p>⚠ A nested class counts as a different caller. That errs toward reporting a live surface,
     * which makes the citation requirement below fire more often, not less — the safe direction for
     * an instrument whose failure mode is letting an unexamined reason through.
     */
    private static Set<String> crossClassCallSites(Iterable<Resource> from, String segment)
            throws IOException {
        Set<String> calls = new LinkedHashSet<>();
        for (Resource r : from) {
            String self = internalNameOf(r, segment);
            try (InputStream in = r.getInputStream()) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName,
                                                        String desc, boolean isInterface) {
                                if (!owner.equals(self)) {
                                    calls.add(owner + "#" + mName);
                                }
                            }

                            @Override
                            public void visitInvokeDynamicInsn(String iName, String desc,
                                                               Handle bsm, Object... bsmArgs) {
                                for (Object a : bsmArgs) {
                                    if (a instanceof Handle h && !h.getOwner().equals(self)) {
                                        calls.add(h.getOwner() + "#" + h.getName());
                                    }
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_FRAMES);
            }
        }
        return calls;
    }

    /**
     * Owner internal name → the internal names of the classes that call anything on it.
     *
     * <p>F2-193 needs the caller, not just the callee. {@link #crossClassCallSites} answers
     * <i>"is this method used"</i>; a <b>trigger</b> claim asks a different question —
     * <i>"what KIND of thing uses this class"</i>. A controller route and a {@code @Scheduled} loop
     * are both call sites and are not both triggers, and that distinction is invisible in a set of
     * {@code owner#method} strings.
     */
    private static Map<String, Set<String>> callersByOwner(Iterable<Resource> from, String segment)
            throws IOException {
        Map<String, Set<String>> out = new TreeMap<>();
        for (Resource r : from) {
            String self = internalNameOf(r, segment);
            try (InputStream in = r.getInputStream()) {
                new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName,
                                                        String desc, boolean isInterface) {
                                if (!owner.equals(self)) {
                                    out.computeIfAbsent(owner, k -> new TreeSet<>()).add(self);
                                }
                            }

                            @Override
                            public void visitInvokeDynamicInsn(String iName, String desc,
                                                               Handle bsm, Object... bsmArgs) {
                                for (Object a : bsmArgs) {
                                    if (a instanceof Handle h && !h.getOwner().equals(self)) {
                                        out.computeIfAbsent(h.getOwner(), k -> new TreeSet<>())
                                            .add(self);
                                    }
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_FRAMES);
            }
        }
        return out;
    }

    /**
     * True when this class is something the CONTAINER drives rather than a person: it declares a
     * {@code @Scheduled} tick or an event listener. That is what "automatic trigger" means.
     *
     * <p>A {@code @RestController} is deliberately not automatic even when the caller is another
     * system — a webhook route still runs only when somebody sends the request, and the platform
     * cannot claim it as a trigger of its own.
     */
    private static boolean containerDriven(String internalName) {
        Class<?> c;
        try {
            c = Class.forName(internalName.replace('/', '.'), false,
                DarkServiceMethodTest.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
        for (Method m : c.getDeclaredMethods()) {
            for (java.lang.annotation.Annotation a : m.getAnnotations()) {
                String n = a.annotationType().getName();
                if (n.equals("org.springframework.scheduling.annotation.Scheduled")
                        || n.equals("org.springframework.context.event.EventListener")
                        || n.equals("org.springframework.transaction.event.TransactionalEventListener")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The public methods of {@code fqcn} that some other main class calls — its <b>live surface</b>.
     *
     * <p>This is the list a capability claim has to be written against, and the list every one of
     * F2-192's seven false reasons was written without.
     */
    static Set<String> liveSiblings(String fqcn, Set<String> crossClassCalls) {
        Class<?> c;
        try {
            c = Class.forName(fqcn, false, DarkServiceMethodTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return Set.of();
        }
        Set<String> internalNames = new LinkedHashSet<>();
        for (Class<?> t : supertypes(c)) {
            internalNames.add(t.getName().replace('.', '/'));
        }
        Set<String> live = new TreeSet<>();
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || m.isBridge()
                    || FRAMEWORK_NAMES.contains(m.getName())) {
                continue;
            }
            for (String n : internalNames) {
                if (crossClassCalls.contains(n + "#" + m.getName())) {
                    live.add(m.getName());
                    break;
                }
            }
        }
        return live;
    }

    private static List<Class<?>> serviceClasses() throws IOException {
        List<Class<?>> out = new ArrayList<>();
        for (Resource r : mainClasses()) {
            String url = r.getURL().toString().replace('\\', '/');
            int i = url.indexOf("/target/classes/");
            String fqcn = url.substring(i + "/target/classes/".length())
                    .replace(".class", "").replace('/', '.');
            if (fqcn.contains("$")) {
                continue;
            }
            try {
                Class<?> c = Class.forName(fqcn, false, DarkServiceMethodTest.class.getClassLoader());
                if (c.isAnnotationPresent(Service.class)) {
                    out.add(c);
                }
            } catch (Throwable ignored) {
                // A class we cannot load is not a class we can adjudicate.
            }
        }
        return out;
    }

    /** Every supertype, so a call dispatched through an interface or superclass counts. */
    private static Set<Class<?>> supertypes(Class<?> c) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> q = new ArrayDeque<>(List.of(c));
        while (!q.isEmpty()) {
            Class<?> cur = q.poll();
            if (cur == null || !seen.add(cur)) {
                continue;
            }
            if (cur.getSuperclass() != null) {
                q.add(cur.getSuperclass());
            }
            q.addAll(List.of(cur.getInterfaces()));
        }
        return seen;
    }

    private static boolean declaredByASupertype(Class<?> owner, Method m) {
        for (Class<?> t : supertypes(owner)) {
            if (t == owner) {
                continue;
            }
            try {
                t.getDeclaredMethod(m.getName(), m.getParameterTypes());
                return true;
            } catch (NoSuchMethodException ignored) {
                // not declared there
            }
        }
        return false;
    }

    static Set<String> measureDark() throws IOException {
        Set<String> calls = callSites();
        Set<String> dark = new TreeSet<>();
        for (Class<?> svc : serviceClasses()) {
            Set<String> internalNames = new LinkedHashSet<>();
            for (Class<?> t : supertypes(svc)) {
                internalNames.add(t.getName().replace('.', '/'));
            }
            for (Method m : svc.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || m.isBridge()) {
                    continue;
                }
                if (FRAMEWORK_NAMES.contains(m.getName())) {
                    continue;
                }
                if (CONTAINER_ANN.stream().anyMatch(m::isAnnotationPresent)) {
                    continue;
                }
                if (m.isAnnotationPresent(Override.class) || declaredByASupertype(svc, m)) {
                    continue;
                }
                boolean reached = internalNames.stream()
                        .anyMatch(n -> calls.contains(n + "#" + m.getName()));
                if (!reached) {
                    dark.add(svc.getName() + "#" + m.getName());
                }
            }
        }
        return dark;
    }

    // ── the assertions ───────────────────────────────────────────────────────────────────────

    @Test
    void everyDarkServiceMethodIsAdjudicated() throws IOException {
        Set<String> dark = measureDark();
        Set<String> unexplained = new TreeSet<>(dark);
        unexplained.removeAll(TOLERATED.keySet());
        assertThat(unexplained)
            .as("""
                A public method on a shipped @Service that nothing invokes. Either wire it, delete \
                it, or add it to TOLERATED with what a USER cannot do because of it — a bare \
                "unused" is the annotation that makes a tolerated entry look reviewed. Note the \
                instrument's blind spots first (class javadoc): if the only caller is reflection, \
                SpEL or a scheduler-by-name, the honest entry says so.""")
            .isEmpty();
    }

    @Test
    void aToleratedEntryThatGainedACallerMustLoseItsEntry() throws IOException {
        Set<String> dark = measureDark();
        Set<String> fixed = new TreeSet<>(TOLERATED.keySet());
        fixed.removeAll(dark);
        assertThat(fixed)
            .as("""
                These are listed as dark but now have a caller (or were deleted). Remove the entry \
                in the same commit. This direction is the whole reason TOLERATED is a named map \
                rather than a count: without it a new dark method can be swapped in for a fixed one \
                and the gate stays green — CLAUDE.md's BARE_EXPORT_CEILING lesson.""")
            .isEmpty();
    }

    @Test
    void everyToleratedEntryCarriesARealReason() {
        List<String> thin = TOLERATED.entrySet().stream()
                .filter(e -> e.getValue().length() < 40
                        || e.getValue().toLowerCase().matches("^(unused|dead|n/?a|todo)\\.?$"))
                .map(Map.Entry::getKey)
                .toList();
        assertThat(thin)
            .as("A tolerated entry needs a reason a reader can act on, not a label")
            .isEmpty();
    }

    /**
     * Language that turns a reason into a claim about the whole system rather than about this method.
     *
     * <p>Deliberately narrow, and it took two drafts to get there. "never called", "nothing routes to
     * it", "never consulted" are claims about <b>the method</b> — exactly what the bytecode measurement
     * establishes, so a reason saying so is reporting the finding, not extending it. "is never marked
     * overdue" is a claim that <b>no path anywhere</b> moves a row into a state, which is a different
     * and much stronger assertion, and the one that sets a finding's severity because it means a row
     * sits in a register forever.
     *
     * <p>⚠ The first draft matched bare {@code is NEVER} / {@code nothing routes} and flagged three
     * entries whose reasons were already correct and already method-level ({@code hideHost} —
     * <i>"containment IS reachable; #hideHost is a second containment verb nothing routes to"</i>,
     * {@code pushCyberIncident}, {@code hasRecentApprovedAssessment}). That is the same mistake this
     * repository has now made four times in the other direction: <b>a scan not scoped to the shape of
     * the claim invents findings</b>. The verbs below are all state-transition verbs.
     */
    private static final Pattern UNREACHABILITY_CLAIM = Pattern.compile(
        "(?i)\\bnever (?:marked|flipped|moved|transitions|leaves|recorded|run)\\b"
        + "|\\bnothing (?:marks|flips|moves|records|advances)\\b"
        + "|\\bno caller and no scheduler\\b|\\bcannot be marked\\b");

    /**
     * F2-188 — the assertion that would have caught five false reasons, and the reason it exists.
     *
     * <p>{@link #everyToleratedEntryCarriesARealReason()} enforces that a reason is <b>substantial</b>.
     * Nothing enforced that it was <b>true</b>. Five entries — filed as "a state nothing can ever move
     * to: the sharpest bucket" — claimed a register sits stale forever, and <b>every one of the five was
     * wrong</b>:
     *
     * <ul>
     *   <li>{@code CcmService#recordRun} — "nothing records a CCM test run … a control that fails
     *       repeatedly raises no Issue". {@code CcmSchedulerService#tick} is
     *       {@code @Scheduled(fixedDelay = 60_000)} and {@code POST /tests/{id}/run-now} exists; both
     *       reach the same private {@code recordRunInternal} that owns the ladder.</li>
     *   <li>{@code CommitteeActionItemService#markOverdue} — "no caller and no scheduler: an action item
     *       is NEVER marked overdue". {@code CommitteeScheduledSweep} was flipping the row itself. That
     *       IS a defect, and a much narrower one than claimed — it is now fixed and the entry is gone.</li>
     *   <li>{@code ObligationAttestationService#markOverdueForCampaign} — superseded by
     *       {@code sweepOverdueForOrg}, and {@code ComplianceDeadlineScheduler}'s javadoc names the
     *       method it replaced.</li>
     *   <li>{@code IrPlaybookService#recordInvocation} — superseded by {@code IrPlaybookRunService},
     *       whose comment says it preserves the legacy semantics.</li>
     *   <li>{@code ThreatIntelService#markActioned} — a shim whose own javadoc says "the old 'mark done'
     *       path … now records a REVIEWED action", one line above the signature.</li>
     * </ul>
     *
     * <p>🔑 <b>A named-map gate proves a reason exists; it cannot prove the reason is true — so the
     * reasons are the least-verified part of the most authoritative-looking artefact.</b> And the
     * substantiality check made it worse rather than better: it rejected six of my first-draft labels,
     * so I rewrote them as fluent prose naming a consequence, and <b>fluency reads as verification</b>.
     * Four of the five truths were sitting in a comment I had not opened — the fourth consecutive
     * instance of <i>one file short is the whole error</i>.
     *
     * <p>So the rule now: a reason may make an unreachability claim only if the entry appears in
     * {@link #CLAIMS_NO_OTHER_WRITER} declaring the (entity, state) it is about, and this test then
     * <b>verifies</b> that no main-source class other than the method's own writes that state. Enforced
     * both ways, so a declaration cannot outlive the claim it supports.
     *
     * <p>The map is empty today, and that is the finding rather than a gap: after the audit, no
     * surviving entry makes such a claim. The scan still runs over all {@value #REASON_SCAN_NOTE}
     * reasons, so it is not vacuous — it is the population that is zero, not the instrument.
     */
    private static final String REASON_SCAN_NOTE = "TOLERATED";

    /** entry key → (simple entity name, state literal) the reason claims nothing else can write. */
    private static final Map<String, String[]> CLAIMS_NO_OTHER_WRITER = Map.of();

    @Test
    void noReasonAssertsUnreachabilityWithoutAScanThatVerifiesIt() throws IOException {
        List<String> unbacked = new ArrayList<>();
        for (Map.Entry<String, String> e : TOLERATED.entrySet()) {
            if (UNREACHABILITY_CLAIM.matcher(e.getValue()).find()
                    && !CLAIMS_NO_OTHER_WRITER.containsKey(e.getKey())) {
                unbacked.add(e.getKey());
            }
        }
        System.out.println("[F2-188] reasons scanned = " + TOLERATED.size()
            + " ; unreachability claims = " + unbacked.size()
            + " ; declared+verified = " + CLAIMS_NO_OTHER_WRITER.size());

        assertThat(unbacked)
            .as("""
                These reasons claim a state is unreachable — the claim that sets a finding's severity, \
                because it means a row sits in a register forever. F2-188 measured five such claims and \
                ALL FIVE were false; four of the truths were in a comment beside the method. If the \
                claim is real, declare the (entity, state) in CLAIMS_NO_OTHER_WRITER so this test can \
                check it. If it is not, say what the method actually is: a redundant path, a superseded \
                variant or a legacy shim — and name the live twin.""")
            .isEmpty();

        // The other direction: a declaration must still be backed by a claim, and the claim must hold.
        for (Map.Entry<String, String[]> d : CLAIMS_NO_OTHER_WRITER.entrySet()) {
            assertThat(TOLERATED).containsKey(d.getKey());
            assertThat(UNREACHABILITY_CLAIM.matcher(TOLERATED.get(d.getKey())).find())
                .as("%s is declared in CLAIMS_NO_OTHER_WRITER but its reason no longer makes the "
                    + "claim — delete the declaration in the same commit", d.getKey())
                .isTrue();
            String owner = d.getKey().substring(0, d.getKey().indexOf('#'));
            List<String> otherWriters = classesWriting(d.getValue()[1]).stream()
                .filter(c -> !c.equals(owner))
                .toList();
            assertThat(otherWriters)
                .as("%s claims nothing else moves a %s to %s, and these classes do",
                    d.getKey(), d.getValue()[0], d.getValue()[1])
                .isEmpty();
        }
    }

    /**
     * Fully-qualified names of main-source classes containing {@code setStatus("<state>")} or
     * {@code set*Status("<state>")}.
     *
     * <p>Source text, not bytecode, and that is a deliberate weakening: a string constant survives into
     * the constant pool but attributing it to the class that <em>writes</em> it needs the setter call,
     * and a source scan is honest about being a locator. It is only ever used to <b>refute</b> a claim
     * of exclusivity — a scan that misses a writer leaves the claim standing, so the failure direction
     * is the same one the register already carries.
     */
    private static List<String> classesWriting(String state) throws IOException {
        Path root = Path.of("src/main/java");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        Pattern write = Pattern.compile("set\\w*Status\\(\\s*\"" + Pattern.quote(state) + "\"");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                if (write.matcher(src).find()) {
                    String rel = root.relativize(p).toString()
                        .replace('\\', '/').replace('/', '.');
                    hits.add(rel.substring(0, rel.length() - ".java".length()));
                }
            }
        }
        return hits;
    }

    /**
     * Entries whose owner class has <b>no live cross-class surface at all</b>, so there is no sibling
     * to cite and the claim has to be verified another way.
     *
     * <p>Today there is exactly one, and its claim — "reached by tests only" — is the one claim this
     * instrument can check directly, by finding the call site in {@code target/test-classes}. That is
     * why the exemption is safe where a name-based one would not be: {@code setClock} does not get in
     * by being called {@code setClock}, it gets in by a scan proving what its reason says.
     */
    private static final Set<String> SEAM_VERIFIED_BY_TESTS = Set.of(
        "com.tss.grc.module.aml.sanctions.service.SanctionsScreeningSchedulerService#setClock");

    /**
     * F2-192 — the assertion F2-188 needed and could not have written, for the bucket it did not audit.
     *
     * <h3>What went wrong</h3>
     * <p>F2-188 proved that a named-map gate cannot prove a reason is <em>true</em>, and built
     * {@link #noReasonAssertsUnreachabilityWithoutAScanThatVerifiesIt()} to catch the claim shape it
     * had just found false: <i>"a row can never leave this state"</i>. {@link #UNREACHABILITY_CLAIM}
     * is scoped to state-transition verbs, deliberately and correctly.
     *
     * <p>But the <b>larger</b> bucket of this map makes a different claim — <i>"no endpoint does X",
     * "only the list is wired", "cannot be resolved by any route"</i> — and that shape is invisible to
     * a state-verb pattern. So it was never audited. Measured for F2-192 against the bytecode:
     * <b>every one of the 18 owner classes has a live cross-class surface</b>, and of the reasons
     * resting on one of those capability claims, <b>seven were outright false</b> and four more
     * overstated. In every case a routed sibling on the <em>same class</em> already did, or nearly
     * did, the thing the reason said a user could not do.
     *
     * <p>🔑 <b>A gate that verifies claims verifies the claim shape it was written for</b> — the same
     * lesson as F2-187's own headline (a reachability gate certifies the granularity it resolves),
     * arriving one level up: at the gate that was built to answer it.
     *
     * <h3>The rule</h3>
     * <p>A reason must <b>cite, in method notation, at least one live method of its own owner class</b>.
     * Notation (<code>#name</code> or <code>name(</code>) rather than a bare word because half these
     * siblings are called {@code list} or {@code get}, and "no endpoint lists unacknowledged drift"
     * would otherwise satisfy a check for {@code list} while asserting its opposite.
     *
     * <p>This is F2-185(a)'s <i>"one file short is the whole error"</i> turned into a build failure:
     * you cannot write the entry without having read what the class already exposes.
     */
    @Test
    void everyReasonIsWrittenAgainstItsOwnerClassLiveSurface() throws IOException {
        Set<String> cross = crossClassCallSites(mainClasses(), "/target/classes/");
        Set<String> testCalls = crossClassCallSites(testClasses(), "/target/test-classes/");

        List<String> uncited = new ArrayList<>();
        List<String> noSurfaceAndUndeclared = new ArrayList<>();
        Map<String, Integer> surfaceSizes = new TreeMap<>();

        for (Map.Entry<String, String> e : TOLERATED.entrySet()) {
            String key = e.getKey();
            String owner = key.substring(0, key.indexOf('#'));
            String dark = key.substring(key.indexOf('#') + 1);
            Set<String> siblings = new TreeSet<>(liveSiblings(owner, cross));
            siblings.remove(dark);          // it is dark; it cannot be its own evidence
            surfaceSizes.put(key, siblings.size());

            if (siblings.isEmpty()) {
                if (!SEAM_VERIFIED_BY_TESTS.contains(key)) {
                    noSurfaceAndUndeclared.add(key);
                }
                continue;
            }
            String reason = e.getValue();
            boolean cited = siblings.stream()
                .anyMatch(s -> reason.contains("#" + s) || reason.contains(s + "("));
            if (!cited) {
                uncited.add(key + "  (live surface: " + siblings + ")");
            }
        }

        System.out.println("[F2-192] entries = " + TOLERATED.size()
            + " ; owners with no live surface = " + surfaceSizes.values().stream()
                .filter(n -> n == 0).count()
            + " ; seam-verified = " + SEAM_VERIFIED_BY_TESTS.size());

        assertThat(uncited)
            .as("""
                These reasons say what a user cannot do without naming what the same class already \
                exposes. Cite a live sibling in method notation (#name) and say what it does and \
                does not cover. F2-192 measured this bucket and SEVEN reasons were false because \
                they were written from the dark method alone — a routed sibling on the same class \
                already did the thing the reason called impossible.""")
            .isEmpty();

        assertThat(noSurfaceAndUndeclared)
            .as("""
                This entry's owner class has no live cross-class surface at all, so there is no \
                sibling to cite and nothing here corroborates the reason. If the claim is "reached \
                by tests only", add it to SEAM_VERIFIED_BY_TESTS and this test will verify that \
                against target/test-classes. Otherwise the honest entry says the whole class is \
                dark — which is a bigger finding than one method.""")
            .isEmpty();

        // The other direction, and the part that makes the exemption a measurement: a declared seam
        // must actually be reached by a test, and must still have no live surface of its own.
        for (String key : SEAM_VERIFIED_BY_TESTS) {
            assertThat(TOLERATED).containsKey(key);
            String owner = key.substring(0, key.indexOf('#'));
            String method = key.substring(key.indexOf('#') + 1);
            assertThat(testCalls)
                .as("%s is declared a test seam, and no test calls it — so it is not a seam, it is "
                    + "dead code", key)
                .contains(owner.replace('.', '/') + "#" + method);
            Set<String> siblings = new TreeSet<>(liveSiblings(owner, cross));
            siblings.remove(method);
            assertThat(siblings)
                .as("%s no longer needs the exemption: its class has a live surface now, so the "
                    + "reason must cite it like every other entry. Delete the declaration.", key)
                .isEmpty();
        }
    }

    // ── F2-193: the THIRD claim shape — an assertion about triggers ───────────────────────────
    //
    // F2-192 closed by predicting a third shape and this is it. Six entries in this map say a
    // capability is unreachable not because no route exists but because nothing FIRES it: "NO
    // PRODUCTION TRIGGER … no domain event reaches it". That is a claim about the kind of caller a
    // class has, and nothing above measures kinds. The citation rule F2-192 added made it worse
    // rather than better: it forced each of those reasons to name a live sibling, and a named routed
    // sibling reads as evidence the integration is exercised.
    //
    // ⚠⚠ Measured, it caught the Splunk entry, which said Splunk "has one real trigger" and cited
    // POST /security-event/{id} — a route whose own @Operation summary is "MANUALLY push a known SOC
    // security event into Splunk". The handler said manual and the reason said trigger. Same error as
    // F2-188's five: the truth was one line away, in a doc annotation nobody opened.

    /** Reasons asserting that nothing fires this class. */
    private static final Pattern NO_TRIGGER_CLAIM = Pattern.compile(
        "NO PRODUCTION TRIGGER|no domain event reaches|nothing (?:ever )?(?:fires|triggers) it",
        Pattern.CASE_INSENSITIVE);

    /**
     * Reasons asserting that something DOES — the half that fired.
     *
     * <p>Narrow on purpose. F2-188's first draft matched bare {@code is NEVER} and manufactured three
     * findings against reasons that were already correct, so the lesson recorded there is that a scan
     * must be scoped to the shape of the claim. {@link #theTriggerClaimPatternsSeparateAPromiseFromItsDenial}
     * pins these two patterns against the sentence that motivated them and against its correction.
     */
    private static final Pattern HAS_TRIGGER_CLAIM = Pattern.compile(
        "\\b(?:one|a|its|the)\\s+real\\s+trigger\\b|\\btriggered\\s+(?:by|from)\\b"
        + "|\\bhas\\s+(?:a|one)\\s+trigger\\b|\\bfires\\s+automatically\\b",
        Pattern.CASE_INSENSITIVE);

    /**
     * Drops double-quoted spans before matching a claim.
     *
     * <p>⚠ Not a nicety — the first run of this gate failed on its OWN correction. Every corrected
     * reason in this map quotes the sentence it replaces, which is the register's convention and the
     * reason the corrections are auditable at all; a detector that reads a quotation as an assertion
     * forces each correction to paraphrase away its own evidence. A quoted claim is attribution, an
     * unquoted one is a claim, and {@link #theTriggerClaimPatternsSeparateAPromiseFromItsDenial}
     * pins both directions.
     */
    private static String unquoted(String reason) {
        return reason.replaceAll("\"[^\"]*\"", " ");
    }

    @Test
    void everyTriggerClaimMatchesItsOwnerClassCallerKinds() throws IOException {
        Map<String, Set<String>> callers = callersByOwner(mainClasses(), "/target/classes/");

        List<String> deniedButDriven = new ArrayList<>();
        List<String> claimedButUndriven = new ArrayList<>();
        Map<String, Set<String>> driven = new TreeMap<>();
        int withAutomatic = 0;
        int withoutAutomatic = 0;

        for (Map.Entry<String, String> e : TOLERATED.entrySet()) {
            String key = e.getKey();
            String owner = key.substring(0, key.indexOf('#'));
            Set<String> automatic = new TreeSet<>();
            for (String caller : callers.getOrDefault(owner.replace('.', '/'), Set.of())) {
                if (containerDriven(caller)) {
                    automatic.add(caller.substring(caller.lastIndexOf('/') + 1));
                }
            }
            if (automatic.isEmpty()) {
                withoutAutomatic++;
            } else {
                withAutomatic++;
                driven.put(key.substring(key.lastIndexOf('.') + 1), automatic);
            }

            String reason = unquoted(e.getValue());
            if (NO_TRIGGER_CLAIM.matcher(reason).find() && !automatic.isEmpty()) {
                deniedButDriven.add(key + "  (container-driven callers: " + automatic + ")");
            }
            if (HAS_TRIGGER_CLAIM.matcher(reason).find() && automatic.isEmpty()) {
                claimedButUndriven.add(key);
            }
        }

        System.out.println("[F2-193] trigger-claim census: owners with a container-driven caller = "
            + withAutomatic + " ; without = " + withoutAutomatic + " ; driven = " + driven);

        assertThat(deniedButDriven)
            .as("""
                This reason says nothing in production reaches the class, and a @Scheduled or \
                @EventListener class calls it. Either the claim is false or the caller is inert — \
                and "inert" is a second claim needing its own evidence.""")
            .isEmpty();

        assertThat(claimedButUndriven)
            .as("""
                This reason says a trigger exists and no container-driven class calls the owner. A \
                ROUTE IS NOT A TRIGGER: the Splunk entry said "that module has one real trigger" and \
                cited POST /security-event/{id}, whose own @Operation summary reads "Manually push". \
                Say "routed" when you mean routed.""")
            .isEmpty();

        // Non-vacuity, measured rather than exemplified (F2-192's fourth surviving mutation was a
        // negative assertion naming two methods that were never in the population). The classifier
        // must separate: some owners here are reached by a scheduler and some are not.
        assertThat(withAutomatic)
            .as("no TOLERATED owner class has a container-driven caller — the classifier is not "
                + "separating anything, so deniedButDriven cannot ever fire")
            .isGreaterThan(0);
        assertThat(withoutAutomatic)
            .as("every TOLERATED owner class has a container-driven caller — implausible, and it "
                + "would mean containerDriven() answers true unconditionally")
            .isGreaterThan(0);
    }

    @Test
    void theTriggerClaimPatternsSeparateAPromiseFromItsDenial() {
        // The sentence F2-193 found, verbatim in shape, and the correction that replaced it. Without
        // this pair the HAS_TRIGGER_CLAIM leg above is untested the moment the corpus is clean.
        String wrong = "NO PRODUCTION TRIGGER for the free-form variant. Splunk differs from "
            + "ServiceNow: #pushSecurityEvent is live from SplunkController's POST "
            + "/security-event/{id}, so that module has one real trigger and only the generic "
            + "overload is dark.";
        String corrected = "NO PRODUCTION TRIGGER, and not one either: #pushSecurityEvent is ROUTED "
            + "from SplunkController's POST /security-event/{id}, whose own @Operation summary says "
            + "\"Manually push\".";

        assertThat(HAS_TRIGGER_CLAIM.matcher(unquoted(wrong)).find())
            .as("the pattern must catch the sentence it was written for").isTrue();
        assertThat(HAS_TRIGGER_CLAIM.matcher(unquoted(corrected)).find())
            .as("...and must not catch its correction, or the corpus can never be clean").isFalse();
        assertThat(NO_TRIGGER_CLAIM.matcher(unquoted(corrected)).find())
            .as("the correction still denies a trigger, so the other leg must still apply to it")
            .isTrue();
        // A denial must not read as an assertion: this is the F2-33 rule, that a detector which
        // cannot tell a promise from its denial fails on the very sentence it exists to protect.
        assertThat(HAS_TRIGGER_CLAIM.matcher("no domain event reaches it, so there is no trigger")
            .find()).isFalse();

        // Quoting vs asserting, both directions — the failure that this gate hit on its first run
        // was on the correction it had just been given, because a correction quotes what it replaces.
        String quotesTheOldClaim = "This said the module \"has one real trigger\" and it does not.";
        assertThat(HAS_TRIGGER_CLAIM.matcher(quotesTheOldClaim).find())
            .as("unstripped, the quotation reads as an assertion — which is the bug").isTrue();
        assertThat(HAS_TRIGGER_CLAIM.matcher(unquoted(quotesTheOldClaim)).find())
            .as("stripped, an attributed quotation is not the entry's own claim").isFalse();
        assertThat(HAS_TRIGGER_CLAIM.matcher(unquoted("the module has one real trigger")).find())
            .as("...and stripping must not swallow the unquoted claim itself").isTrue();
        // Words are separated, not glued: a quoted span must not join its neighbours into a new one.
        assertThat(unquoted("nothing \"real trigger\" here")).doesNotContain("real trigger");
        assertThat(unquoted("a\"x\"b")).isEqualTo("a b");
    }

    @Test
    void theLiveSurfaceScanIsNotVacuous() throws IOException {
        Set<String> all = callSites();
        Set<String> cross = crossClassCallSites(mainClasses(), "/target/classes/");
        assertThat(cross)
            .as("the cross-class scan found call sites at all")
            .hasSizeGreaterThan(4_000);

        // A known-live method must be in the surface, and a known-dark one must not — without both
        // legs, an empty or an all-inclusive scan would satisfy the citation rule silently.
        Set<String> threeLines = liveSiblings(
            "com.tss.grc.module.governance.threelines.service.ThreeLinesService", cross);
        assertThat(threeLines)
            .as("userBandFor is routed by ThreeLinesController and bandsForUser is called by "
                + "JwtTokenProvider — the two facts that made F2-187's reason false")
            .contains("userBandFor", "bandsForUser")
            .doesNotContain("bandForUser");

        // ⚠ And the narrowing itself. The first version of this leg hand-picked two method names it
        // ASSUMED the narrowing would exclude (CcmService#recordRunInternal, #recordRun) — and the
        // mutation that deletes the narrowing survived, because one is private (so liveSiblings skips
        // it on visibility) and the other is an entry point nothing calls at all. Neither was ever
        // affected by the rule being tested. 🔑 A negative assertion naming a method that was never
        // in the population passes for a reason unrelated to the property. So measure the difference
        // instead: how many public @Service methods does the narrowing actually remove? 34 on the
        // tree as measured — a real population, and it drops to 0 if the narrowing is deleted.
        Set<String> onlySelfCalled = new TreeSet<>();
        for (Class<?> svc : serviceClasses()) {
            Set<String> names = new LinkedHashSet<>();
            for (Class<?> t : supertypes(svc)) {
                names.add(t.getName().replace('.', '/'));
            }
            for (Method m : svc.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || m.isSynthetic() || m.isBridge()
                        || FRAMEWORK_NAMES.contains(m.getName())) {
                    continue;
                }
                boolean anywhere = names.stream().anyMatch(n -> all.contains(n + "#" + m.getName()));
                boolean outside = names.stream().anyMatch(n -> cross.contains(n + "#" + m.getName()));
                if (anywhere && !outside) {
                    onlySelfCalled.add(svc.getSimpleName() + "#" + m.getName());
                }
            }
        }
        System.out.println("[F2-192] public @Service methods called only from inside their own "
            + "class = " + onlySelfCalled.size());
        assertThat(onlySelfCalled)
            .as("""
                The narrowing must actually remove something, or "does anything OUTSIDE this class \
                use it" has degraded to "does this class compile". These are public service methods \
                whose only caller is their own class: they count as reached (so measureDark is right \
                to leave them alone) and they are NOT live surface, so a reason may not cite them as \
                evidence that a capability is routed.""")
            .isNotEmpty();
        assertThat(cross.size())
            .as("and the cross-class set must be strictly smaller than the all-callers set")
            .isLessThan(all.size());
    }

    @Test
    void theRetaliationTransitionIsGoneAndItsThreeReplacementsAreNot() throws IOException {
        Set<String> dark = measureDark();
        // F2-185(a): the method the register graded P2 for "the lifecycle cannot be closed". It was
        // deleted, so it must not be dark — it must not exist.
        assertThat(dark).noneMatch(d -> d.endsWith("WhistleblowerRetaliationService#transitionTo"));
        Class<?> svc;
        try {
            svc = Class.forName(
                "com.tss.grc.module.ethics.whistleblower.service.WhistleblowerRetaliationService");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        List<String> names = List.of(svc.getDeclaredMethods()).stream().map(Method::getName).toList();
        assertThat(names)
            .as("the three real disposition paths must survive the deletion of the generic one")
            .contains("validate", "uphold", "dismiss")
            .doesNotContain("transitionTo");
    }

    @Test
    void aMethodReferenceTargetIsNotReportedDark() throws IOException {
        // What this proves: a method reached ONLY through `::` (CommitteeReferralService::states, a
        // private static used as a Predicate) is in the call set. Without that, the first public
        // service method reached only by a `::` reference would be reported dark and its reader
        // would go hunting for an endpoint that already exists.
        //
        // ⚠ What it does NOT prove, and I first claimed it did: that the visitInvokeDynamicInsn
        // handler is load-bearing. Mutation 6 deletes that handler and this assertion still passes,
        // because javac desugars a method reference to a private static into a synthetic bridge that
        // calls it with an ordinary invokestatic — which visitMethodInsn already sees. So the
        // bootstrap-handle collection is redundant on THIS tree. It is kept because the desugaring is
        // a compiler implementation detail, not a language guarantee, and a reference to a method on
        // another class can compile to a direct handle.
        //
        // 🔑 I wrote the first version of this test by reasoning about my own code rather than
        // measuring it, and named it after the mechanism I assumed was doing the work. That is the
        // F2-159 shape — a test that agrees with the implementation because it was derived from it —
        // and the mutation harness is the only reason it did not ship under a name that lied.
        assertThat(callSites())
            .as("a ::-referenced target must be collected, or ::-reached methods read as dark")
            .contains("com/tss/grc/module/admin/committee/service/CommitteeReferralService#states");
    }

    @Test
    void theInstrumentIsNotVacuous() throws IOException {
        // If the call-site scan silently returned nothing, every service method would read as dark
        // and the TOLERATED map would be swamped — but if it returned everything, the population
        // would be empty and both assertions above would pass over any defect.
        Set<String> calls = callSites();
        assertThat(calls).as("the ASM scan found call sites at all").hasSizeGreaterThan(5_000);
        assertThat(serviceClasses()).as("@Service classes were discovered").hasSizeGreaterThan(200);
        Set<String> dark = measureDark();
        assertThat(dark)
            .as("a dark population of 0 would mean the subtraction is clearing everything")
            .isNotEmpty();
        assertThat(dark.size())
            .as("""
                and a population in the hundreds would mean the reachability logic is broken rather \
                than the code being dark — the first raw run measured 51 before the container/ \
                interface/seam subtraction, so this ceiling is a sanity bound on the INSTRUMENT.""")
            .isLessThan(60);
    }

    @Test
    void theSubtractionRulesActuallyFire() throws IOException {
        // Each subtraction below removed real members on the measured tree. If a rule stops firing,
        // either the codebase changed or the rule broke — both worth knowing, because a subtraction
        // that silently matches nothing is how a false population looks correct.
        Set<String> dark = measureDark();
        assertThat(dark)
            .as("@Scheduled methods must be subtracted (16 on the measured tree)")
            .noneMatch(d -> d.endsWith("SodService#nightlyScan")
                    || d.endsWith("KriSchedulerService#tick")
                    || d.endsWith("AuditChainService#sealAllTenants"));
        assertThat(dark)
            .as("@EventListener methods must be subtracted")
            .noneMatch(d -> d.endsWith("AutomationRuleEngine#onDomainEvent"));
        assertThat(dark)
            .as("framework-interface implementations must be subtracted")
            .noneMatch(d -> d.endsWith("CustomUserDetailsService#loadUserByUsername"));
    }
}
