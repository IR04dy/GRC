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
            + "authoring lives on the canonical register (PolicyManagementService#transition). "
            + "RegChangePolicyController says so in a comment and names CH-9 as when the whole "
            + "service goes. Nothing is lost: the effect has a live twin."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#legalApprove",
            "RETIRED BY DESIGN — see #submitForLegalReview."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#executiveApprove",
            "RETIRED BY DESIGN — see #submitForLegalReview."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#markReviewDue",
            "RETIRED BY DESIGN — see #submitForLegalReview."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#create",
            "RETIRED BY DESIGN — see #submitForLegalReview. Its mass-assignment defence is still "
            + "worth reading: RegulatoryChangeService cites it by name."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#update",
            "RETIRED BY DESIGN — see #submitForLegalReview."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#delete",
            "RETIRED BY DESIGN — see #submitForLegalReview."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#reject",
            "RETIRED BY DESIGN — see #submitForLegalReview."),
        Map.entry("com.tss.grc.module.compliance.service.PolicyService#retire",
            "RETIRED BY DESIGN — see #submitForLegalReview."),

        // ── Declared test seams: reached only by tests, on purpose ───────────────────────────
        // Listed rather than subtracted by a name rule. A `setClock`-shaped exemption would let any
        // dark method be hidden by renaming it, which is the "classification rule you can only apply
        // by intuition" F2-165 warns manufactures a finding on first use.
        Map.entry("com.tss.grc.module.aml.sanctions.service.SanctionsHitService#setClock",
            "DECLARED TEST SEAM — injects a fixed Clock so date-relative assertions are stable. "
            + "CLAUDE.md records why this seam matters here specifically: LocalDate.now() in a test "
            + "is UTC+3 on this machine while the services are UTC, so a date-relative fixture is "
            + "wrong for three hours of every day (the F2-178 correction). Reached by tests only, "
            + "which is the point."),
        Map.entry("com.tss.grc.module.aml.sanctions.service.SanctionsScreeningSchedulerService#setClock",
            "DECLARED TEST SEAM — see SanctionsHitService#setClock."),

        // ── Outbound integrations with no production trigger (filed: F2-187) ──────────────────
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushCyberIncident",
            "NO PRODUCTION TRIGGER. ServiceNowController exposes /health, /config and /test-push "
            + "only, so a real cyber incident is never pushed to ServiceNow — the integration reads "
            + "as working (health green, config present, test-push succeeds) while no domain event "
            + "reaches it. The service's own javadoc states the v1 intent (\"modules whose DTOs "
            + "haven't stabilised\"), so this is a posture rather than a bug, but the posture is "
            + "invisible to a buyer. F2-187."),
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushFinding",
            "NO PRODUCTION TRIGGER — see #pushCyberIncident. F2-187."),
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushCapaTask",
            "NO PRODUCTION TRIGGER — see #pushCyberIncident. F2-187."),
        Map.entry("com.tss.grc.module.integrations.servicenow.service.ServiceNowOutboundService#pushComplianceGap",
            "NO PRODUCTION TRIGGER — see #pushCyberIncident. F2-187."),
        Map.entry("com.tss.grc.module.integrations.splunk.service.SplunkOutboundService#pushGenericEvent",
            "NO PRODUCTION TRIGGER for the free-form variant. Splunk differs from ServiceNow: "
            + "SplunkController also exposes POST /security-event/{id}, so that module has one real "
            + "trigger and only the generic overload is dark. F2-187."),
        Map.entry("com.tss.grc.module.integrations.crowdstrike.service.CrowdStrikeOutboundService#hideHost",
            "DEAD DUPLICATE-ish: CrowdStrikeController exposes /contain/{hostId} and /lift/{hostId}, "
            + "so host containment IS reachable; #hideHost is a second containment verb nothing "
            + "routes to. Listed rather than deleted because CrowdStrikeClient#hideHost is the "
            + "vendor API call the contain path may need to switch to. F2-187."),

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
            + "calls runDefinitionInternal, and POST /ccm/tests/{externalId}/run-now reaches "
            + "runTestNow — both land in recordRunInternal, which owns the consecutive-failure Issue "
            + "ladder and the last_run_* stamps. Kept as the ingestion seam for an external runner. "
            + "F2-187/F2-188."),
        Map.entry("com.tss.grc.module.socops.service.IrPlaybookService#recordInvocation",
            "SUPERSEDED by IrPlaybookRunService, whose own comment says it preserves 'the legacy "
            + "invocation semantics the dashboard + F8 rules rely on' and emits invocationCount "
            + "itself. Invocations ARE recorded, as trackable runs with snapshotted tasks. Kept "
            + "because the counter-only path is cheaper for a bulk import. F2-187/F2-188."),
        Map.entry("com.tss.grc.module.socops.service.ThreatIntelService#markActioned",
            "A LEGACY SHIM, and its own javadoc says so one line above the signature: 'the old "
            + "\"mark done\" path (no concrete target) now records a REVIEWED action'. It delegates "
            + "to actionIoc, which is live and writes the action history plus the denormalised "
            + "actioned/pending queue. F2-187/F2-188."),

        // ── Built and unwired: the capability exists in code and no route reaches it ──────────
        Map.entry("com.tss.grc.module.ai.governance.service.MlModelDriftService#listUnacknowledged",
            "No endpoint lists unacknowledged drift observations, so the CRITICAL-drift escalation "
            + "F2-175 fixed can be filed and never reviewed — the intake record is created and the "
            + "queue that would surface it is unreachable. F2-187."),
        Map.entry("com.tss.grc.module.bcm.cycle.runtime.observer.service.ExerciseObserverService#buildAarSection",
            "The observer's after-action-report section is built by nothing, so observer findings "
            + "from a BCM exercise never reach the AAR that NCEMA evidence is drawn from. F2-187."),
        Map.entry("com.tss.grc.module.platform.push.service.WebPushService#dispatchToUserIds",
            "Targeted web-push fan-out with no caller: a notification cannot be pushed to specific "
            + "users. The delivery half of what F2-38(d) left open as an owner decision. F2-187."),
        Map.entry("com.tss.grc.platform.realtime.RealtimeBroadcaster#sendToUser",
            "Per-user realtime send with no caller; only BroadcastHelper's tenant-wide path is live. "
            + "Pairs with WebPushService#dispatchToUserIds — targeted delivery is built and unwired "
            + "on BOTH transports, which is why neither looks like the odd one out. F2-187."),
        Map.entry("com.tss.grc.module.privacy.subprocessor.service.SubprocessorEventService#listAll",
            "The tenant-wide subprocessor-event feed is unreachable; only the per-subprocessor list "
            + "is wired. So a DPO cannot review every sub-processor change in one place, which is the "
            + "view PDPL sub-processor oversight actually needs. F2-187."),

        // ── Predicates and readers nothing consults: low blast radius, but say why they matter ──
        Map.entry("com.tss.grc.module.platform.controls.service.ControlLibraryService#hasRecentApprovedAssessment",
            "A control-assessment freshness predicate nothing asks. Worth a second look before "
            + "deletion rather than after: a freshness check that is never consulted is exactly how "
            + "a stale control passes for current. F2-187."),
        Map.entry("com.tss.grc.module.change_mgmt.service.ChangeManagementService#isConditional",
            "Derived, never stored, and never asked: nothing distinguishes a change approved SUBJECT "
            + "TO CONDITIONS from an unconditional approval, so the conditions attached at CAB are "
            + "invisible downstream. F2-187."),
        Map.entry("com.tss.grc.module.change_mgmt.service.ChangeGovernanceHealthService#isGateConfigured",
            "Public gate-configuration probe with no caller; the class's @EventListener sibling is "
            + "the live path and warns on the same condition, so this is the query form of an answer "
            + "the platform only ever logs. F2-187."),
        Map.entry("com.tss.grc.module.admin.committee.service.CommitteeCatalogService#countStandingItems",
            "Standing-item count nothing reads; the committee registry screen derives its own. A "
            + "second, unconsulted counter is how two numbers for one thing start disagreeing. "
            + "F2-187."),
        Map.entry("com.tss.grc.module.admin.committee.service.CommitteeCatalogService#findTemplate",
            "Committee-template lookup with no caller: templates are catalogued and cannot be "
            + "resolved by any route, so a tenant cannot instantiate a committee from one. F2-187."),
        Map.entry("com.tss.grc.module.change_mgmt.service.ChangeModelService#usageFor",
            "Per-change-model usage count nothing reads, so a tenant cannot see whether a change "
            + "model is in use before editing or retiring it. F2-187."),
        Map.entry("com.tss.grc.module.governance.threelines.service.ThreeLinesService#bandForUser",
            "Resolves which of the three lines of defence a user sits in — and nothing asks, so the "
            + "independence model is recorded and never enforced or displayed per user. F2-187."),
        Map.entry("com.tss.grc.module.ndi.settings.service.NdiSettingService#stringValue",
            "Typed accessor for NDI settings with no caller; callers read the raw setting instead, "
            + "which is how a default declared in one place stops being applied. F2-187."),
        Map.entry("com.tss.grc.module.platform.regulatory.service.AuthorityDocumentService#citationCount",
            "Citation count per authority document is computed by nothing, so a regulatory-library "
            + "screen cannot show how heavily a document is relied upon. F2-187."),
        Map.entry("com.tss.grc.module.sust.ghg.service.EmissionFactorService#resolveActive",
            "Active-emission-factor resolver with no caller: GHG calculations pick factors by another "
            + "path, so the 'which factor was active on this date' question this method answers is "
            + "not the one the numbers were produced with. Check before deleting — ISAE 3410 "
            + "assurance lineage depends on that answer. F2-187."),
        Map.entry("com.tss.grc.module.sust.climate.service.FinancedEmissionsService#getTarget",
            "Single-PCAF-target read is unreachable; only the list is wired, so a financed-emissions "
            + "target has no detail view. F2-187.")
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
        List<Resource> out = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/tss/grc/**/*.class")) {
            String url = r.getURL().toString().replace('\\', '/');
            // Deliberate: test classes are NOT callers. See the class javadoc.
            if (url.contains("/target/classes/")) {
                out.add(r);
            }
        }
        return out;
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
