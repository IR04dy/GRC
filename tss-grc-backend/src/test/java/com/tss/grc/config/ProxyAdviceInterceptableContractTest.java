package com.tss.grc.config;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>One question, two mechanisms: can Spring's advice actually reach this annotation?</b>
 *
 * <p>Every annotation here is <b>proxy-based</b> — the behaviour lives in an interceptor on a proxy
 * around the bean, not in the method itself. So it goes inert in two ways, both silent, both with
 * the annotation visibly present:
 *
 * <ol>
 *   <li><b>The method cannot be intercepted.</b> Spring's proxy-based attribute sources consider
 *       <b>public</b> methods only, and a proxy cannot intercept a private one at all. (F2-411)</li>
 *   <li><b>The call never goes through the proxy.</b> {@code this.advised()} from inside the same
 *       bean bypasses every interceptor. (F2-412)</li>
 * </ol>
 *
 * <p><b>F2-411 shipped mechanism 1 for {@code @Transactional} alone, and this class is the widening
 * it should have been.</b> That is F2-215's two-of-N at the level of the gate: a reader who finds a
 * hard-zero interceptability gate concludes the interceptability question is settled, and the
 * annotation where the same mistake is worst is not {@code @Transactional} — it is
 * {@code @PreAuthorize}, where an inert annotation is an <b>authorization bypass</b> rather than a
 * lost transaction. Measured across the family: <b>0 inert</b> of 2,018 {@code @Transactional} +
 * 2,202 {@code @PreAuthorize} + 7 cache + 1 {@code @Async}. It was renamed from
 * {@code TransactionalInterceptableContractTest} because a name keyed on one annotation is a claim
 * about the population, and an understating name is what stops a reader checking (F2-312).
 *
 * <p><b>Mechanism 2 needs a narrowing, and getting it wrong would fail on correct code</b>
 * (F2-214). The raw self-invocation population is <b>416</b> and almost all of it is right: the
 * house idiom is a {@code @Transactional(readOnly = true) get(id)} called from a
 * {@code @Transactional} write method, where the callee's annotation is bypassed and
 * <b>nothing changes</b> — under default {@code REQUIRED} propagation it would have joined the
 * caller's transaction anyway. A gate counting those would report 416 defects over a codebase doing
 * the right thing. The subsets where a bypass does change behaviour:
 *
 * <ul>
 *   <li><b>Non-transactional advice</b> — {@code @Cacheable} (never consulted, and never
 *       populated), {@code @CacheEvict} (never evicted), {@code @Async} (runs inline),
 *       {@code @PreAuthorize} (not checked). Held at a <b>hard zero with no exemption map</b>: the
 *       alternative is always available — annotate the public entry point and put the body in an
 *       unannotated private helper, or move the advised method to its own bean — so there is no
 *       correct code for this leg to fail on.</li>
 *   <li><b>{@code REQUIRES_NEW}</b> — the promised isolation is absent. Swept at 0 by F2-411 and
 *       asserted here so it stays there.</li>
 *   <li><b>An untransacted caller</b> — the callee's boundary is genuinely lost rather than merged.
 *       Measured at <b>13 distinct callees</b> and shipped as an <b>exactly-asserted census with no
 *       verdicts</b> (F2-188), because several are the F2-322/F2-323 per-tenant scheduler family
 *       where losing the batch-wide boundary is the <i>desired</i> shape, and manufacturing 13
 *       reasons is how that finding measured five claims and five false.</li>
 * </ul>
 *
 * <p>The defect that produced mechanism 2's zero: {@code LicensingService.isModuleEnabled} called
 * {@code this.enabledModulesFor(...)}, so the F9 {@code licenses} {@code @Cacheable} never applied
 * on the {@code LicenseGuard} path — the path its own class javadoc calls <i>"every authenticated
 * request hot-path"</i>, and the reason the cache exists. The cache was populated only by JWT
 * issuance, so every guarded request queried {@code organisation_licenses} past a warm entry. The
 * two {@code @CacheEvict} sites are external calls and always worked, which is exactly what made
 * the read path look wired.
 *
 * <p><b>Measured from reflection and bytecode, never a source regex.</b> On the
 * {@code @Transactional} half a regex sweep of the same tree was wrong 3 times in 6 — it attributed
 * an annotation to the next signature it could parse, and missed the {@code protected} one entirely
 * — and a self-call scan over source matched {@code publish (} inside a string literal.
 */
class ProxyAdviceInterceptableContractTest {

    /** Every proxy-based annotation the platform uses, or could. */
    private static final List<Class<? extends Annotation>> PROXIED = List.of(
            Transactional.class, Async.class,
            Cacheable.class, CacheEvict.class, CachePut.class,
            PreAuthorize.class, PostAuthorize.class, Secured.class);

    /**
     * The 13 callees whose {@code @Transactional} is bypassed by a self-call from a method that
     * carries no transaction of its own. <b>Deliberately no reasons</b> — this is a work list, and
     * a named map of unverified reasons is the artefact F2-188 measured at five claims, five false.
     *
     * <p>Keyed on the CALLEE, not the caller: one caller is {@code lambda$rosterOnly$2}, and javac's
     * lambda numbering is not something a gate should depend on.
     *
     * <p>Asserted <b>exactly</b> rather than decrease-only (F2-204): a ceiling would pressure the
     * next reader to "fix" members that are correct, and several are. Growth and shrinkage both fail
     * here, so either direction forces an adjudication.
     *
     * <p>⚙ Two notes so the next reader does not re-derive them.
     * {@code AuditLogService#recordEvent} being bypassed from {@code #log} does <b>not</b> disturb
     * F2-228/F2-322's reasoning: their conclusion is that an audit write lands in the caller's
     * transaction, and with the annotation bypassed it lands in whatever transaction is ambient —
     * the same outcome by a different mechanism. And the four {@code #sweep} / {@code #runSweep}
     * pairs are {@code @Scheduled} entry points calling a per-tenant body, which is the shape
     * F2-322 and F2-323 argued <i>for</i>.
     */
    private static final Set<String> UNTRANSACTED_CALLER_CENSUS = new TreeSet<>(List.of(
            "module.bcm.cycle.runtime.service.ExerciseRuntimeService#getRunTimeline",
            "module.change_adapters.WorkflowAdapter#reach",
            "module.change_mgmt.service.ChangeGovernanceHealthService#gateConfigurationFault",
            "module.change_mgmt.service.ChangeGovernanceHealthService#unmappedGates",
            "module.change_mgmt.service.ChangeManagementService#requiredRoleStates",
            "module.cyber.service.CyberActionPlanSlaScheduler#sweep",
            "module.cyber.service.CyberEvidenceExpiryScheduler#sweep",
            "module.evidence.service.EvidenceRequestSlaScheduler#remindOverdue",
            "module.iam.governance.service.RecertificationService#autoScheduleForOrg",
            "module.iam.governance.service.SodService#scanForOrg",
            "module.platform.auditlog.service.AuditChainService#sealTenant",
            "module.platform.auditlog.service.AuditLogService#recordEvent",
            "module.workflow.requests.service.WorkflowRequestSlaScheduler#sweep"));

    // ─── mechanism 1: the method cannot be intercepted ───────────────────────

    /**
     * The claim as a predicate, so the synthetic control exercises the same code the corpus
     * assertion uses. A "collect the violations, assert the list is empty" leg is vacuous on a clean
     * tree — an empty list is what a correct detector and a dead one both produce (F2-250/F2-295).
     */
    static boolean cannotBeIntercepted(Method m) {
        boolean advised = PROXIED.stream().anyMatch(m::isAnnotationPresent);
        if (!advised) return false;
        return !Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers());
    }

    // ─── mechanism 2: the call never goes through the proxy ──────────────────

    /** What the advice on a callee promises, and whether the callee is itself transactional. */
    record Advice(String labels, boolean requiresNew, boolean transactional) { }

    /** A self-call that changes behaviour: {@code kind} says which of the three subsets. */
    record Bypass(String kind, String callee, String caller) { }

    /**
     * Walk one class's bytecode for self-calls onto its own advised methods. Extracted so the
     * synthetic control below can drive it over a fixture rather than over the corpus — on a clean
     * corpus the hard zero cannot tell a working detector from a dead one.
     */
    static List<Bypass> selfCalls(byte[] bytes, String internalName,
                                  Map<String, Advice> advised,
                                  Set<String> transactionalMethods, boolean classTransactional) {
        List<Bypass> out = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String caller, String desc,
                                             String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name,
                                                String d, boolean itf) {
                        if (!internalName.equals(owner)) return;         // not a self-call
                        Advice a = advised.get(name);
                        if (a == null) return;                           // callee not advised
                        if (name.equals(caller)) return;                 // recursion, not a bypass
                        boolean callerTx = classTransactional || transactionalMethods.contains(caller);
                        String kind;
                        if (!"Transactional".equals(a.labels()))  kind = "NON-TX ADVICE (" + a.labels() + ")";
                        else if (a.requiresNew())                 kind = "REQUIRES_NEW";
                        else if (!callerTx)                       kind = "UNTRANSACTED CALLER";
                        else return;                                     // tx joins tx — harmless
                        out.add(new Bypass(kind,
                                internalName.replace('/', '.').replace("com.tss.grc.", "") + "#" + name,
                                caller));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }

    // ─── corpus ──────────────────────────────────────────────────────────────

    private record Scan(List<String> notInterceptable,
                        List<Bypass> bypasses,
                        int publicAdvised,
                        int harmlessSelfCalls,
                        int classes) { }

    private static Scan scan() throws IOException {
        List<String> notInterceptable = new ArrayList<>();
        List<Bypass> bypasses = new ArrayList<>();
        int publicAdvised = 0, harmless = 0, classes = 0;

        for (Resource r : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/tss/grc/**/*.class")) {
            String url = r.getURL().toString().replace('\\', '/');
            if (!url.contains("/target/classes/")) continue;             // never test classes
            String fq = url.substring(url.indexOf("com/tss/grc/"))
                           .replace(".class", "").replace('/', '.');
            Class<?> c;
            try {
                c = Class.forName(fq, false, ProxyAdviceInterceptableContractTest.class.getClassLoader());
            } catch (Throwable ignored) {
                continue;                                                // cannot load, cannot assert
            }
            classes++;
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable ignored) {
                continue;
            }

            Map<String, Advice> advised = new HashMap<>();
            Set<String> txMethods = new HashSet<>();
            for (Method m : ms) {
                StringBuilder labels = new StringBuilder();
                for (Class<? extends Annotation> a : PROXIED) {
                    if (!m.isAnnotationPresent(a)) continue;
                    if (labels.length() > 0) labels.append('+');
                    labels.append(a.getSimpleName());
                }
                Transactional t = m.getAnnotation(Transactional.class);
                if (t != null) txMethods.add(m.getName());
                if (labels.length() == 0) continue;

                if (cannotBeIntercepted(m)) {
                    notInterceptable.add("%s#%s [%s] %s".formatted(
                            fq.replace("com.tss.grc.", ""), m.getName(),
                            Modifier.toString(m.getModifiers()), labels));
                } else {
                    publicAdvised++;
                }
                advised.put(m.getName(), new Advice(labels.toString(),
                        t != null && t.propagation() == Propagation.REQUIRES_NEW, t != null));
            }
            if (advised.isEmpty()) continue;

            byte[] bytes;
            try (InputStream in = r.getInputStream()) {
                bytes = in.readAllBytes();
            }
            List<Bypass> all = selfCalls(bytes, fq.replace('.', '/'), advised, txMethods,
                    c.isAnnotationPresent(Transactional.class));
            // selfCalls already drops the harmless tx-joins-tx case; count it separately so the
            // anti-vacuity floor below sits above the largest WRONG answer rather than above zero.
            harmless += countHarmless(bytes, fq.replace('.', '/'), advised, txMethods,
                    c.isAnnotationPresent(Transactional.class));
            bypasses.addAll(new LinkedHashSet<>(all));
        }
        return new Scan(notInterceptable, bypasses, publicAdvised, harmless, classes);
    }

    /** The tx-joins-tx population, measured so the floor can sit above it. */
    private static int countHarmless(byte[] bytes, String internalName, Map<String, Advice> advised,
                                     Set<String> txMethods, boolean classTx) {
        int[] n = {0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int a, String caller, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String name,
                                                String dd, boolean itf) {
                        if (!internalName.equals(owner) || name.equals(caller)) return;
                        Advice adv = advised.get(name);
                        if (adv == null || !"Transactional".equals(adv.labels())) return;
                        if (adv.requiresNew()) return;
                        if (classTx || txMethods.contains(caller)) n[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return n[0];
    }

    // ─── legs ────────────────────────────────────────────────────────────────

    @Test
    void noProxyAdvisedAnnotationSitsOnAMethodASpringProxyCannotIntercept() throws IOException {
        assertThat(scan().notInterceptable())
                .as("""
                    A proxy-based annotation on a non-public or static method of a Spring bean is
                    INERT: the proxy-based attribute sources consider public methods only, and a
                    proxy cannot intercept a private one at all. It is worse than absent, because it
                    answers the question a reader was about to ask (F2-184) — and for @PreAuthorize
                    it is an authorization bypass, not a lost transaction.

                    The usual cause is the annotation being written ABOVE a javadoc rather than below
                    it, which silently attaches it to the next method — as F2-213 did four times in
                    one commit. Put the annotation on the public method that needs it, or delete it.
                    Do not add an exemption: there is no correct code for this leg to fail on.""")
                .isEmpty();
    }

    @Test
    void noNonTransactionalAdviceIsReachedByASelfCall() throws IOException {
        List<Bypass> bad = scan().bypasses().stream()
                .filter(b -> b.kind().startsWith("NON-TX ADVICE")).toList();
        assertThat(bad)
                .as("""
                    A self-call bypasses the proxy, so this @Cacheable / @CacheEvict / @Async /
                    @PreAuthorize does nothing on that path — no error, no log, the annotation
                    visibly present. F2-412: LicensingService.isModuleEnabled self-called its own
                    @Cacheable read, so the licences cache was never consulted on the LicenseGuard
                    hot path the cache exists for.

                    Fix it structurally, not by exemption: move the advised method to its own bean
                    (LicensedModuleReader is the worked example), or annotate the public entry point
                    and put the body in an unannotated private helper. Both make the bypass
                    unrepresentable, which is why this leg has no exemption map.""")
                .isEmpty();
    }

    @Test
    void noRequiresNewTransactionIsReachedByASelfCall() throws IOException {
        assertThat(scan().bypasses().stream().filter(b -> "REQUIRES_NEW".equals(b.kind())).toList())
                .as("""
                    A self-invoked REQUIRES_NEW never starts its own transaction, so the isolation
                    the annotation promises is absent — and CLAUDE.md's engine-wiring rule exists
                    because a joined transaction poisons the caller's commit. Swept at 0 by F2-411;
                    this leg is what keeps it there.""")
                .isEmpty();
    }

    /**
     * The work list, exactly asserted and deliberately unadjudicated. See
     * {@link #UNTRANSACTED_CALLER_CENSUS}.
     */
    @Test
    void theUntransactedCallerCensusIsExactlyWhatWasMeasured() throws IOException {
        Set<String> found = new TreeSet<>();
        for (Bypass b : scan().bypasses())
            if ("UNTRANSACTED CALLER".equals(b.kind())) found.add(b.callee());
        assertThat(found)
                .as("""
                    A @Transactional reached only by a self-call from an untransacted method has no
                    boundary of its own. This is a measured WORK LIST with no verdicts, not a defect
                    count: several members are the F2-322/F2-323 per-tenant scheduler shape, where
                    losing the batch-wide transaction is what those findings argued for.

                    It is asserted exactly rather than decrease-only, so growth and shrinkage both
                    fail and either forces a reading. If you adjudicate one, record the verdict in
                    the findings register and move it out — do not silently drop it.""")
                .isEqualTo(UNTRANSACTED_CALLER_CENSUS);
    }

    @Test
    void theScanIsNotVacuous() throws IOException {
        Scan s = scan();
        // Every floor sits above the largest WRONG answer, which for a scanner that resolves
        // nothing is zero — not above "obviously not broken" (F2-215).
        assertThat(s.classes())
                .as("the class scan resolved almost nothing, so the zeros above mean nothing")
                .isGreaterThan(2_000);
        assertThat(s.publicAdvised())
                .as("no public proxy-advised methods were found, so the detector is not reading "
                    + "annotations at all and its empty violation lists prove nothing")
                .isGreaterThan(3_500);
        assertThat(s.harmlessSelfCalls())
                .as("""
                    The tx-joins-tx self-call population measured 401. If this collapses, the
                    bytecode walk has stopped resolving self-calls and every emptiness assertion
                    above it has gone vacuous — the failure mode where a detector that cannot match
                    reads exactly like a clean tree (F2-235).""")
                .isGreaterThan(300);
    }

    /**
     * Without this the hard zeros are indistinguishable from detectors that have stopped matching.
     * Both mechanisms get a control, and both are checked in the negative direction too — a
     * narrowing that is too wide silently re-tiers the codebase, and one that is too narrow
     * silently shrinks the population.
     */
    @Test
    void bothDetectorsFireOnTheInertShapesAndOnNoneOfTheLegitimateOnes() throws Exception {
        // mechanism 1
        for (String bad : List.of("privateTx", "protectedTx", "staticTx", "privateCacheable",
                                  "privatePreAuthorize")) {
            assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod(bad)))
                    .as("%s must be reported as not interceptable", bad).isTrue();
        }
        for (String ok : List.of("publicTx", "publicCacheable", "privatePlain")) {
            assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod(ok)))
                    .as("%s is a legitimate shape and must NOT be reported — a gate that fails on "
                        + "correct code trains the next reader to edit the assertion (F2-214)", ok)
                    .isFalse();
        }

        // mechanism 2, driven over the fixture's real bytecode
        Map<String, Advice> advised = new LinkedHashMap<>();
        advised.put("cachedRead",   new Advice("Cacheable", false, false));
        advised.put("newTx",        new Advice("Transactional", true, true));
        advised.put("plainTx",      new Advice("Transactional", false, true));
        Set<String> txMethods = new HashSet<>(List.of("newTx", "plainTx", "transactedCaller"));

        List<Bypass> found = selfCalls(bytesOf(SelfCallFixture.class),
                SelfCallFixture.class.getName().replace('.', '/'),
                advised, txMethods, false);
        Set<String> kinds = new TreeSet<>();
        for (Bypass b : found) kinds.add(b.kind() + " -> " + b.callee().replaceAll(".*#", "#"));

        assertThat(kinds).as("""
                the self-call detector must report exactly the three shapes that change behaviour
                and must NOT report the tx-joins-tx case — that harmless population is 401 sites of
                a codebase doing the right thing""")
                .containsExactlyInAnyOrder(
                        "NON-TX ADVICE (Cacheable) -> #cachedRead",
                        "REQUIRES_NEW -> #newTx",
                        "UNTRANSACTED CALLER -> #plainTx");
    }

    /**
     * The four handlers F2-411 repointed, named individually so a revert fails here rather than
     * silently shrinking a population. A count could not see one of the four losing its annotation.
     */
    @Test
    void theFourCyberUpdateHandlersCarryTheirOwnTransactional() throws Exception {
        String[][] sites = {
                {"com.tss.grc.module.cyber.service.CyberActionPlanService", "updateActionPlan"},
                {"com.tss.grc.module.cyber.service.CyberAssessmentService", "updateAssessment"},
                {"com.tss.grc.module.cyber.service.CyberControlService", "updateControl"},
                {"com.tss.grc.module.cyber.service.CyberVulnerabilityService", "updateVulnerability"},
        };
        for (String[] s : sites) {
            Class<?> c = Class.forName(s[0]);
            Method m = Arrays.stream(c.getDeclaredMethods())
                    .filter(cand -> cand.getName().equals(s[1])).findFirst().orElse(null);
            assertThat(m).as("%s#%s must exist", s[0], s[1]).isNotNull();
            assertThat(Modifier.isPublic(m.getModifiers()))
                    .as("%s#%s must be public for the annotation to apply", s[0], s[1]).isTrue();
            assertThat(m.isAnnotationPresent(Transactional.class))
                    .as("""
                        %s#%s lost its @Transactional. F2-213 stranded it on the private
                        applyEditable helper above, where a proxy cannot see it, leaving this
                        handler the only non-transactional write method in its class.""",
                            s[0], s[1])
                    .isTrue();
        }
    }

    /**
     * F2-412's own fix, asserted structurally: the cached read must NOT live on
     * {@code LicensingService}, because that is the only thing that stops a future self-call
     * silently un-caching the hot path again.
     */
    @Test
    void theLicensingCacheLivesOnItsOwnBean() throws Exception {
        Class<?> svc = Class.forName("com.tss.grc.module.platform.licensing.service.LicensingService");
        assertThat(Arrays.stream(svc.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Cacheable.class)).toList())
                .as("""
                    LicensingService must not own a @Cacheable method. It did, and
                    isModuleEnabled self-called it, so the licences cache was never consulted on the
                    LicenseGuard hot path. The cached read belongs on LicensedModuleReader, where
                    every caller necessarily goes through a proxy.""")
                .isEmpty();

        Class<?> reader = Class.forName(
                "com.tss.grc.module.platform.licensing.service.LicensedModuleReader");
        Method cached = reader.getDeclaredMethod("enabledModulesFor", Long.class);
        assertThat(cached.isAnnotationPresent(Cacheable.class))
                .as("LicensedModuleReader#enabledModulesFor must carry the @Cacheable").isTrue();
        assertThat(Modifier.isPublic(cached.getModifiers()))
                .as("a non-public @Cacheable cannot be intercepted at all").isTrue();
    }

    /**
     * ⚠ The class FILE of a nested class is {@code Outer$Inner.class}, not {@code Inner.class}, so
     * {@code getSimpleName()} resolves to a resource that does not exist. The non-null assertion is
     * load-bearing: a null stream would have made the control report that the detector found
     * nothing, which reads exactly like a detector that has stopped matching.
     */
    private static byte[] bytesOf(Class<?> c) throws IOException {
        String binary = c.getName();
        String file = binary.substring(binary.lastIndexOf('.') + 1) + ".class";
        try (InputStream in = c.getResourceAsStream(file)) {
            assertThat(in).as("fixture bytecode must be readable at %s", file).isNotNull();
            return in.readAllBytes();
        }
    }

    /** Synthetic corpus for mechanism 1's control — never scanned, only inspected by name. */
    @SuppressWarnings("unused")
    static class Fixture {
        @Transactional      public    void publicTx() { }
        @Transactional      private   void privateTx() { }
        @Transactional      protected void protectedTx() { }
        @Transactional      static    void staticTx() { }
        @Cacheable("x")     public    void publicCacheable() { }
        @Cacheable("x")     private   void privateCacheable() { }
        @PreAuthorize("permitAll()") private void privatePreAuthorize() { }
        private void privatePlain() { }
    }

    /**
     * Synthetic corpus for mechanism 2's control. The annotations are supplied to
     * {@link #selfCalls} as a map rather than read off these methods, so the fixture only has to
     * produce the four CALL shapes.
     */
    @SuppressWarnings("unused")
    static class SelfCallFixture {
        void cachedRead() { }
        void newTx() { }
        void plainTx() { }

        /** Untransacted caller reaching all three — the three reportable shapes. */
        void entry() {
            cachedRead();
            newTx();
            plainTx();
        }

        /** Transacted caller reaching a plain @Transactional — the harmless case, must not fire. */
        void transactedCaller() {
            plainTx();
        }
    }
}
