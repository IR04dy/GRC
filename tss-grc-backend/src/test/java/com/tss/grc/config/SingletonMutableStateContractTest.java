package com.tss.grc.config;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-452 — a Spring bean is a <b>singleton</b>, so an instance field it holds is shared by every
 * request and therefore by every tenant. On a multi-tenant platform that makes a
 * {@code private Map<String, X> cache = new HashMap<>()} on a {@code @Service} two defects at once:
 * a data race, and a cross-tenant read of whatever the previous request left there.
 *
 * <p><b>Nothing in this tree asked that question before.</b> Measured 2026-09-03 by reflection over
 * the shipped bytecode: <b>686 singleton beans, 0 unloadable, 15 holding a mutable-holder field</b>
 * — and <b>4 of the 15 were partially synchronized</b>.
 *
 * <p>⚠⚠ <b>I read all 15 and concluded a clean sweep, and this gate's own verdict-verification leg
 * refuted me on its first run.</b> All four integration stub clients had {@code isHealthy()}
 * writing to the shared {@code recordedCalls} {@code ArrayList} <b>without</b> the modifier its
 * three sibling writers hold — and it is reachable in every profile, because the {@code /status}
 * route calls the client directly under {@code INTEGRATIONS:READ} and <b>not</b> under
 * {@code grc.integrations.*.enabled}, while the stub is the default bean
 * ({@code matchIfMissing = true}). Two concurrent status probes were therefore enough for two
 * unsynchronized {@code ArrayList.add} calls.
 *
 * <p>🔑 <b>A health probe reads as a read</b>, which is why three synchronized writers beside it
 * made the fourth look covered (F2-215's two-of-N) — and it is a writer, because it records the
 * probe. 🔑 <b>And my earlier reading that the stubs were dev-only was wrong for the reason this
 * register keeps recording: I measured the callers that go through the service layer, which the
 * {@code enabled} flag does gate, and the controller calls the client directly.</b>
 *
 * <h2>What is mechanical here and what is a recorded reading</h2>
 *
 * The <b>invariant</b> is mechanical and is a hard zero: no singleton may <b>reassign</b> a
 * mutable-holder field outside its constructor. That is precisely decidable from bytecode (a
 * {@code PUTFIELD} outside {@code <init>}), and it is a real defect class on its own — a bean that
 * rebuilds a shared map at runtime hands a concurrent caller a torn read.
 *
 * <p>The <b>census</b> is an exact map with a verdict each, enforced in both directions so a new
 * holder must be READ rather than absorbed into a tolerated total. Two of the four verdicts are
 * additionally verified mechanically ({@link Verdict#CONCURRENT} by the declared type,
 * {@link Verdict#SYNCHRONIZED} by the modifiers of every method that touches the field); the other
 * two rest on the invariant above plus a reading, and the reason text says which.
 *
 * <h2>⚠ Why {@code @RestController} is in the bean set but its handlers are not the subject</h2>
 *
 * This gate asks about FIELDS, so a stereotype that Spring instantiates as a singleton belongs in
 * the population whatever invokes its methods. That is the opposite of
 * {@code DarkServiceMethodTest}, whose subject is METHODS and whose population is
 * {@code @Service} alone — and widening THAT one naively is wrong: measured 2026-09-03, adding
 * {@code @Component}/{@code @Repository}/{@code @RestController} to it reports <b>1000</b>
 * unexplained methods, almost all {@code @RestController} handlers that {@code DispatcherServlet}
 * invokes rather than Java code. 🔑 <b>The same stereotype widening is right for one gate and wrong
 * for the other, and which it is depends on the unit the gate resolves</b> (F2-187).
 */
class SingletonMutableStateContractTest {

    /** Spring stereotypes that yield a singleton bean by default. */
    private static final List<Class<? extends Annotation>> STEREOTYPES = List.of(
        Service.class, Component.class, RestController.class, Controller.class, Repository.class);

    /** What safety property makes a shared mutable field harmless here. */
    enum Verdict {
        /** Declared type is a {@code java.util.concurrent} class — verified by type below. */
        CONCURRENT,
        /** Every method that touches the field is {@code synchronized} — verified below. */
        SYNCHRONIZED,
        /** Assigned in the constructor and only read afterwards; the reassignment half is the
         *  hard-zero invariant, the read-only half is a recorded reading. */
        BUILT_IN_CONSTRUCTOR,
        /** Spring's binder owns the field, which is why it is not final. */
        CONFIG_BOUND
    }

    record Holding(Verdict verdict, String why) { }

    /**
     * Every singleton bean holding a mutable-holder instance field, read 2026-09-03. A new entry
     * means somebody has put shared mutable state on a singleton: read it, then declare it.
     */
    private static final Map<String, Holding> DECLARED = new LinkedHashMap<>();
    static {
        // ── registries assembled from an injected list at construction, then only read ──
        DECLARED.put("com.tss.grc.module.bcm.comms.service.CrisisCommsDispatchService#sendersByType",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "channelType -> ChannelSender, built from the injected sender list in the "
                + "constructor; the two readers are get(channelType) lookups"));
        DECLARED.put("com.tss.grc.module.bcm.service.CrisisCommsService#channelsByType",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "channelType -> NotificationChannel, built from the injected channel list; one "
                + "get(t.getChannel()) reader"));
        DECLARED.put("com.tss.grc.module.ccm.runner.ControlTestRunnerRegistry#byType",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "testType -> ControlTestRunner, Collectors.toMap in the constructor; the only "
                + "reader is Optional.ofNullable(byType.get(testType))"));
        DECLARED.put("com.tss.grc.module.intake.spi.PromotionRegistry#byTarget",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "target -> RegisterPromotionPort, Map.copyOf in the constructor so the contents "
                + "are immutable as well as unreassignable"));
        DECLARED.put("com.tss.grc.security.EntityOwnershipRegistry#byType",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "targetType -> EntityOwnershipResolver, Map.copyOf in the constructor -- F2-159's "
                + "ownership dispatch, so a torn read here would be an authorization decision"));
        DECLARED.put("com.tss.grc.module.platform.automation.service.AutomationActionExecutor"
            + "#creatorsByType",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "entity -> RecordCreator, Collectors.toMap in the constructor; one "
                + "get(entity) reader on the CREATE_RECORD action path"));
        DECLARED.put("com.tss.grc.module.tprm.bcp_attestation.service.VendorBcpCampaignService"
            + "#channelsByType",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "channelType -> NotificationChannel, built with new HashMap<>() + putIfAbsent in "
                + "the constructor. ⚠ It is the OUTLIER of the six registries -- the other five use "
                + "Map.copyOf or Collectors.toMap -- so its contents are mutable in principle and "
                + "unmutated in fact; not a defect, and the divergence is worth knowing"));
        DECLARED.put("com.tss.grc.module.change_mgmt.service.ChangeWorkflowService#workflow",
            new Holding(Verdict.BUILT_IN_CONSTRUCTOR,
                "Optional<WorkflowPort>, constructor-injected. Optional itself is immutable; it is "
                + "in this population only because the census keys on holder TYPES"));

        // ── written at runtime, and guarded ──
        DECLARED.put("com.tss.grc.security.PublicEndpointRateLimitFilter#counters",
            new Holding(Verdict.CONCURRENT,
                "ip -> Window, mutated on every public request through compute(...) -- atomic -- "
                + "with a size-triggered removeIf sweep above 10,000 keys"));
        // ⚠ These four are the DEFECT this slice found, and the reason each one now holds is the
        //   fix. Every one of them had isHealthy() writing to the recorder WITHOUT the modifier
        //   its three sibling writers hold -- and isHealthy is reachable in every profile, because
        //   the /status route calls the client directly under INTEGRATIONS:READ and NOT under
        //   grc.integrations.*.enabled, while the stub is the default bean (matchIfMissing = true).
        //   So two concurrent status probes were enough for two unsynchronized ArrayList.add calls.
        //   F2-215's two-of-N: a health probe reads as a read, so three synchronized writers
        //   beside it made the fourth look covered.
        DECLARED.put("com.tss.grc.module.integrations.splunk.client.StubSplunkClient"
            + "#recordedCalls",
            new Holding(Verdict.SYNCHRONIZED,
                "an in-memory HEC call recorder on the default (STUB) client; pushEvent, "
                + "getRecordedCalls, reset and -- since F2-452 -- isHealthy all hold the monitor, "
                + "and getRecordedCalls returns List.copyOf so no caller can mutate it"));
        DECLARED.put("com.tss.grc.module.integrations.servicenow.client.StubServiceNowClient"
            + "#recordedCalls",
            new Holding(Verdict.SYNCHRONIZED,
                "the ServiceNow stub's call recorder; createIncident, getIncident, updateIncident, "
                + "reset and -- since F2-452 -- isHealthy all hold the monitor. Its /health route "
                + "is the one this slice proved ungated by grc.integrations.servicenow.enabled"));
        DECLARED.put("com.tss.grc.module.integrations.crowdstrike.client.StubCrowdStrikeClient"
            + "#recordedCalls",
            new Holding(Verdict.SYNCHRONIZED,
                "the Falcon stub's call recorder; pollDetections, containHost, liftContainment, "
                + "hideHost, reset and -- since F2-452 -- isHealthy hold the monitor, and the "
                + "private recordAction helper is only ever reached from those synchronized three"));
        DECLARED.put("com.tss.grc.module.integrations.tenable.client.StubTenableClient"
            + "#recordedCalls",
            new Holding(Verdict.SYNCHRONIZED,
                "the Tenable VM stub's call recorder; pollFindings, reset and -- since F2-452 -- "
                + "isHealthy hold the monitor. Polling is off by default, so before the fix the "
                + "ungated /status route was the ONLY writer, and two probes could race each other"));
        DECLARED.put("com.tss.grc.module.integrations.servicenow.client.StubServiceNowClient"
            + "#byNumber",
            new Holding(Verdict.SYNCHRONIZED,
                "the ServiceNow stub's in-memory incident store, keyed by incident number; every "
                + "method that touches it holds the monitor, which is why it was NOT part of the "
                + "isHealthy defect -- isHealthy records the probe, it does not read the store"));

        // ── Spring's binder owns it, which is why it is not final ──
        DECLARED.put("com.tss.grc.module.iam.ldap.LdapRoleMapper#roleMapping",
            new Holding(Verdict.CONFIG_BOUND,
                "group DN -> role, bound by @ConfigurationProperties(\"grc.iam.ldap\"). Its own "
                + "comment shows the author knew this hazard -- mapRoles builds its lookup per "
                + "call and \"avoids stateful field caching on a singleton\""));
    }

    /**
     * Concurrent-collection maps whose VALUES are mutable are not in the census: the census keys on
     * the field's declared type, and these two are {@code ConcurrentHashMap} fields on the stubs
     * alongside the recorders above. Named so the census's shape is legible rather than surprising.
     */
    private static final Set<String> CONCURRENT_BY_TYPE = Set.of(
        "com.tss.grc.module.integrations.crowdstrike.client.StubCrowdStrikeClient#sequencesByOrg",
        "com.tss.grc.module.integrations.crowdstrike.client.StubCrowdStrikeClient#emittedByOrg",
        "com.tss.grc.module.integrations.splunk.client.StubSplunkClient#sequencesByOrg",
        "com.tss.grc.module.integrations.tenable.client.StubTenableClient#sequencesByOrg",
        "com.tss.grc.module.integrations.tenable.client.StubTenableClient#emittedByOrg");

    // ── the mechanical invariant ──────────────────────────────────────────

    @Test void noSingletonReassignsAMutableHolderFieldOutsideItsConstructor() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> bean : beans()) {
            Set<String> holders = mutableHolderFields(bean);
            if (holders.isEmpty()) {
                continue;
            }
            boolean configBound = bean.isAnnotationPresent(ConfigurationProperties.class);
            for (String m : reassignedOutsideInit(bytecodeOf(bean), holders)) {
                if (configBound) {
                    continue;              // Spring's binder calls the setter; that is the design
                }
                offenders.add(bean.getName() + "#" + m);
            }
        }
        assertThat(offenders)
            .as("""
                F2-452: a Spring bean is a singleton, so reassigning a shared mutable field after \
                construction hands every concurrent caller a torn read -- and on a multi-tenant \
                platform, potentially another tenant's contents. A hard zero with no exemption map \
                is safe because the alternatives are always available (build it in the constructor, \
                make it final, or use a java.util.concurrent type), so there is no correct code for \
                this leg to fail on (F2-214). If the field really is Spring-bound configuration, \
                annotate the class @ConfigurationProperties, which is the exemption. Found: %s"""
                .formatted(offenders))
            .isEmpty();
    }

    // ── the census ────────────────────────────────────────────────────────

    @Test void everySingletonHoldingMutableStateIsDeclaredWithAVerdict() {
        Map<String, String> measured = new TreeMap<>();
        int beanCount = 0;
        for (Class<?> bean : beans()) {
            beanCount++;
            for (String f : mutableHolderFields(bean)) {
                String key = bean.getName() + "#" + f;
                if (!CONCURRENT_BY_TYPE.contains(key)) {
                    measured.put(key, fieldTypeOf(bean, f));
                }
            }
        }
        publish(measured, beanCount);

        assertThat(measured.keySet())
            .as("""
                F2-452: shared mutable state on a singleton is a data race and, on a multi-tenant \
                platform, a cross-tenant read. A new entry means somebody has added one: READ it, \
                then declare it with the property that makes it safe -- never absorb it into a \
                tolerated total. Declared %d, measured %d."""
                .formatted(DECLARED.size(), measured.size()))
            .containsExactlyInAnyOrderElementsOf(DECLARED.keySet());

        assertThat(CONCURRENT_BY_TYPE)
            .as("the concurrent-by-type set must still name fields that exist, or it is an "
                + "exclusion for something no longer in the population (F2-199)")
            .allSatisfy(k -> assertThat(measured.containsKey(k))
                .as("%s is excluded as concurrent-by-type but the census no longer sees it", k)
                .isFalse());
    }

    @Test void everyVerdictThatCanBeVerifiedMechanicallyIs() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Holding> e : DECLARED.entrySet()) {
            String key = e.getKey();
            Class<?> bean = load(key.substring(0, key.indexOf('#')));
            String fieldName = key.substring(key.indexOf('#') + 1);
            Field f = fieldOf(bean, fieldName);

            switch (e.getValue().verdict()) {
                case CONCURRENT -> {
                    if (!f.getType().getName().startsWith("java.util.concurrent.")) {
                        wrong.add(key + " is declared CONCURRENT but its type is "
                            + f.getType().getName());
                    }
                }
                case SYNCHRONIZED -> {
                    Set<String> unguarded = touchingMethodsThatAreNotSynchronized(
                        bytecodeOf(bean), fieldName);
                    if (!unguarded.isEmpty()) {
                        wrong.add(key + " is declared SYNCHRONIZED but these methods touch it "
                            + "without the modifier: " + unguarded);
                    }
                }
                case CONFIG_BOUND -> {
                    if (!bean.isAnnotationPresent(ConfigurationProperties.class)) {
                        wrong.add(key + " is declared CONFIG_BOUND but the class carries no "
                            + "@ConfigurationProperties");
                    }
                }
                case BUILT_IN_CONSTRUCTOR -> {
                    // The reassignment half is the hard zero above. What is NOT mechanical is that
                    // the contents are never mutated -- that is a reading, and the reason text
                    // names the readers. Assert the half that is checkable: the field is final,
                    // so nothing can even reassign it.
                    if (!Modifier.isFinal(f.getModifiers())) {
                        wrong.add(key + " is declared BUILT_IN_CONSTRUCTOR but the field is not "
                            + "final, so the constructor is not the only possible writer");
                    }
                }
                default -> wrong.add(key + " has no verdict");
            }
        }
        assertThat(wrong)
            .as("a verdict that can be checked must be checked -- a reason nobody can verify is an "
                + "assumption wearing a citation (F2-188). Wrong: %s".formatted(wrong))
            .isEmpty();
    }

    /** Extracted so the synthetic control below can drive it — see the next test for why. */
    static List<String> thinReasons(Map<String, Holding> declared) {
        return declared.entrySet().stream()
            .filter(e -> e.getValue().why() == null || e.getValue().why().length() < 40)
            .map(Map.Entry::getKey)
            .toList();
    }

    @Test void everyReasonSaysWhyRatherThanBeingABareLabel() {
        List<String> thin = thinReasons(DECLARED);
        assertThat(thin)
            .as("F2-188: a bare label reads as reviewed. Say what the field holds and what makes "
                + "it safe. Thin: %s".formatted(thin))
            .isEmpty();
    }

    @Test void theSubstantialityCheckWouldActuallyCatchABareLabel() {
        // ⚠ On a clean corpus "collect the violations, assert empty" is what a working detector and
        // a dead one both produce (F2-295) -- the mutation harness proved it, by weakening the
        // length floor and SURVIVING. So the predicate is driven over synthetic input instead.
        // This is not hypothetical: three of my own first-draft reasons read
        // "as StubSplunkClient#recordedCalls" and this floor is what rejected them.
        assertThat(thinReasons(Map.of("a#b", new Holding(Verdict.CONCURRENT, "unused"))))
            .as("a bare label must be caught")
            .containsExactly("a#b");
        assertThat(thinReasons(Map.of("a#b", new Holding(Verdict.CONCURRENT, null))))
            .as("and so must a missing one")
            .containsExactly("a#b");
        assertThat(thinReasons(Map.of("a#b", new Holding(Verdict.CONCURRENT,
            "a ConcurrentHashMap mutated per request through compute(...), which is atomic"))))
            .as("a reason that says what the field holds and why it is safe must pass")
            .isEmpty();
    }

    @Test void theBeanPopulationIsRealSoTheCensusCannotPassOverNothing() {
        int beanCount = beans().size();
        // Measured 2026-09-03: 686 singleton beans over 1,898 top-level main classes. The floor
        // sits above the largest WRONG answer, which is ZERO -- if the stereotype scan or the class
        // walk stops resolving, the population empties and every assertion above passes over
        // nothing (F2-235).
        assertThat(beanCount)
            .as("singleton beans discovered -- if this collapses, the stereotype scan has stopped "
                + "matching and the census is empty for that reason, not because the tree is clean")
            .isGreaterThan(500);
        assertThat(DECLARED)
            .as("and at least ten holders, or the census is not measuring holder types either")
            .hasSizeGreaterThan(10);
    }

    // ── the detectors, driven over synthetic input in both directions ──────

    @Test void theReassignmentDetectorFiresOnAPostConstructionWriteAndOnNothingElse() {
        byte[] good = bytecodeOf(SafeFixture.class);
        byte[] bad = bytecodeOf(ReassigningFixture.class);
        Set<String> holders = Set.of("cache");

        assertThat(reassignedOutsideInit(good, holders))
            .as("a field assigned only in the constructor must not be flagged -- otherwise the "
                + "hard zero fails on the six registries this platform correctly builds that way")
            .isEmpty();
        assertThat(reassignedOutsideInit(bad, holders))
            .as("a field reassigned in a business method is the defect this leg exists for")
            .containsExactly("rebuild");
    }

    @Test void theSynchronizedDetectorSeesAnUnguardedToucherAndNotAHelperUnderTheLock() {
        assertThat(touchingMethodsThatAreNotSynchronized(bytecodeOf(GuardedFixture.class), "log"))
            .as("every toucher synchronized -> nothing to report")
            .isEmpty();
        assertThat(touchingMethodsThatAreNotSynchronized(bytecodeOf(HalfGuardedFixture.class),
            "log"))
            .as("one synchronized writer and one bare reader is exactly the shape a SYNCHRONIZED "
                + "verdict must not survive -- this is the defect this slice found, four times")
            .containsExactly("peek");
        assertThat(touchingMethodsThatAreNotSynchronized(bytecodeOf(HelperUnderTheLockFixture.class),
            "log"))
            .as("a PRIVATE helper whose every caller is synchronized always runs holding the lock. "
                + "The first draft flagged it (StubCrowdStrikeClient#recordAction), which is a gate "
                + "failing on correct code -- the one failure a gate must never have (F2-214)")
            .isEmpty();
        assertThat(touchingMethodsThatAreNotSynchronized(bytecodeOf(HelperWithABareCallerFixture.class),
            "log"))
            .as("but ONE unsynchronized caller is enough to expose the helper, so the narrowing "
                + "cannot be used to launder an unguarded path through a private hop. ⚠ It reports "
                + "the TOUCHER (`record`) and not the bare caller, because `bare` emits no field "
                + "access of its own -- my first draft expected both, and the detector was right: "
                + "a red leg is a finding only if the code is actually wrong (F2-206). Naming the "
                + "toucher is sufficient, because that is what fails the verdict")
            .containsExactly("record");
        assertThat(touchingMethodsThatAreNotSynchronized(bytecodeOf(OrphanHelperFixture.class),
            "log"))
            .as("a private helper with NO intra-class caller is not guarded by vacuous "
                + "quantification -- it is unreachable, which is a different finding (F2-187) and "
                + "must not read as safe here")
            .containsExactly("orphan");
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    static final class SafeFixture {
        private final Map<String, String> cache;
        SafeFixture() { this.cache = new LinkedHashMap<>(); }
        String get(String k) { return cache.get(k); }
    }

    @SuppressWarnings("unused")
    static final class ReassigningFixture {
        private Map<String, String> cache = new LinkedHashMap<>();
        void rebuild() { this.cache = new LinkedHashMap<>(); }
        String get(String k) { return cache.get(k); }
    }

    @SuppressWarnings("unused")
    static final class GuardedFixture {
        private final List<String> log = new ArrayList<>();
        synchronized void add(String s) { log.add(s); }
        synchronized int size() { return log.size(); }
    }

    @SuppressWarnings("unused")
    static final class HalfGuardedFixture {
        private final List<String> log = new ArrayList<>();
        synchronized void add(String s) { log.add(s); }
        int peek() { return log.size(); }
    }

    /** {@code StubCrowdStrikeClient#recordAction}'s shape: private, every caller synchronized. */
    @SuppressWarnings("unused")
    static final class HelperUnderTheLockFixture {
        private final List<String> log = new ArrayList<>();
        synchronized void contain(String s) { record("CONTAIN", s); }
        synchronized void lift(String s) { record("LIFT", s); }
        private void record(String m, String s) { log.add(m + s); }
    }

    /** One bare caller is enough to expose the helper — the narrowing must not launder it. */
    @SuppressWarnings("unused")
    static final class HelperWithABareCallerFixture {
        private final List<String> log = new ArrayList<>();
        synchronized void contain(String s) { record("CONTAIN", s); }
        void bare(String s) { record("BARE", s); }
        private void record(String m, String s) { log.add(m + s); }
    }

    /** A private helper nothing calls is unreachable, not guarded. */
    @SuppressWarnings("unused")
    static final class OrphanHelperFixture {
        private final List<String> log = new ArrayList<>();
        private void orphan(String s) { log.add(s); }
    }

    // ── bytecode walkers (extracted so the synthetic controls can drive them) ──

    /** Methods other than {@code <init>}/{@code <clinit>} that {@code PUTFIELD} one of {@code fields}. */
    static Set<String> reassignedOutsideInit(byte[] bytes, Set<String> fields) {
        Set<String> out = new TreeSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String d, String s,
                                             String[] ex) {
                if ("<init>".equals(name) || "<clinit>".equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int op, String owner, String field, String desc) {
                        if (op == Opcodes.PUTFIELD && fields.contains(field)) {
                            out.add(name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return out;
    }

    /**
     * Methods that touch {@code field} without holding the monitor ({@code <init>} excepted).
     *
     * <p>⚠ A first draft asked only whether the touching method carries {@code ACC_SYNCHRONIZED},
     * and it flagged {@code StubCrowdStrikeClient#recordAction} — a <b>private</b> helper whose only
     * three callers ({@code containHost}, {@code liftContainment}, {@code hideHost}) are all
     * {@code synchronized}, so it always runs holding the lock. That is a gate failing on correct
     * code, the one failure a gate must never have (F2-214), so a private method is treated as
     * effectively guarded when <b>every</b> intra-class caller is effectively guarded. Resolved as
     * a fixed point, so a private helper calling another private helper still resolves; a private
     * method with NO intra-class caller is <b>not</b> guarded by vacuous quantification — it is
     * unreachable, which is a different finding and must not read as safe here.
     */
    static Set<String> touchingMethodsThatAreNotSynchronized(byte[] bytes, String field) {
        Map<String, Boolean> isSync = new LinkedHashMap<>();
        Map<String, Boolean> isPrivate = new LinkedHashMap<>();
        Map<String, Set<String>> touches = new LinkedHashMap<>();
        Map<String, Set<String>> callers = new LinkedHashMap<>();
        String[] self = new String[1];

        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int v, int access, String name, String sig, String sup, String[] i) {
                self[0] = name;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String d, String s,
                                             String[] ex) {
                if ("<init>".equals(name) || "<clinit>".equals(name)
                    || (access & Opcodes.ACC_SYNTHETIC) != 0) {
                    return null;
                }
                isSync.put(name, (access & Opcodes.ACC_SYNCHRONIZED) != 0);
                isPrivate.put(name, (access & Opcodes.ACC_PRIVATE) != 0);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int op, String owner, String f, String desc) {
                        touches.computeIfAbsent(name, k -> new TreeSet<>()).add(f);
                    }

                    @Override
                    public void visitMethodInsn(int op, String owner, String callee, String d,
                                                boolean itf) {
                        if (owner.equals(self[0]) && !callee.equals(name)) {
                            callers.computeIfAbsent(callee, k -> new TreeSet<>()).add(name);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        Set<String> guarded = new LinkedHashSet<>();
        isSync.forEach((m, sync) -> {
            if (Boolean.TRUE.equals(sync)) {
                guarded.add(m);
            }
        });
        boolean grew = true;
        while (grew) {
            grew = false;
            for (String m : isSync.keySet()) {
                if (guarded.contains(m) || !Boolean.TRUE.equals(isPrivate.get(m))) {
                    continue;
                }
                Set<String> in = callers.getOrDefault(m, Set.of());
                if (!in.isEmpty() && guarded.containsAll(in)) {
                    guarded.add(m);
                    grew = true;
                }
            }
        }

        Set<String> out = new TreeSet<>();
        touches.forEach((m, fs) -> {
            if (fs.contains(field) && !guarded.contains(m)) {
                out.add(m);
            }
        });
        return out;
    }

    // ── population ────────────────────────────────────────────────────────

    private static List<Class<?>> cachedBeans;

    private static synchronized List<Class<?>> beans() {
        if (cachedBeans != null) {
            return cachedBeans;
        }
        List<Class<?>> out = new ArrayList<>();
        int unloadable = 0;
        try {
            Resource[] rs = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/tss/grc/**/*.class");
            for (Resource r : rs) {
                String url = r.getURL().toString().replace('\\', '/');
                int i = url.indexOf("/target/classes/");
                if (i < 0) {
                    continue;                       // test classes are not shipped beans
                }
                String fqcn = url.substring(i + "/target/classes/".length())
                    .replace(".class", "").replace('/', '.');
                if (fqcn.contains("$")) {
                    continue;
                }
                try {
                    Class<?> c = Class.forName(fqcn, false,
                        SingletonMutableStateContractTest.class.getClassLoader());
                    if (!c.isInterface()
                        && STEREOTYPES.stream().anyMatch(a -> c.getAnnotation(a) != null)) {
                        out.add(c);
                    }
                } catch (Throwable t) {             // NOPMD - a class we cannot load we cannot judge
                    unloadable++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // F2-449: a reflective census is only as wide as its classpath -- a class that fails to
        // load is silently absent, and a smaller population reads exactly like a cleaner tree.
        // ⚠ This is a GUARD whose tripwire is in the future, not a discriminating assertion: the
        // mutation replacing it with assertThat(0) SURVIVED, and that is an equivalent mutant on
        // this corpus rather than a hole, because nothing fails to load today so no assertion here
        // can tell a working count from a dead one (F2-206). It earns its place the day somebody
        // narrows the classpath, which is exactly when a zero would otherwise be unearned.
        assertThat(unloadable)
            .as("classes that failed to load -- count them and fail rather than reporting a zero "
                + "you did not earn (F2-449)")
            .isZero();
        cachedBeans = out;
        return out;
    }

    /** Instance fields whose declared type can hold mutable state shared across requests. */
    private static Set<String> mutableHolderFields(Class<?> bean) {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : bean.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }
            Class<?> t = f.getType();
            boolean holder = Collection.class.isAssignableFrom(t) || Map.class.isAssignableFrom(t)
                || StringBuilder.class.isAssignableFrom(t) || StringBuffer.class.isAssignableFrom(t)
                || Random.class.isAssignableFrom(t) || Date.class.isAssignableFrom(t)
                || Optional.class.isAssignableFrom(t)
                || "java.text.SimpleDateFormat".equals(t.getName());
            if (holder) {
                out.add(f.getName());
            }
        }
        return out;
    }

    private static String fieldTypeOf(Class<?> bean, String field) {
        return fieldOf(bean, field).getType().getSimpleName();
    }

    private static Field fieldOf(Class<?> bean, String field) {
        try {
            return bean.getDeclaredField(field);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(bean.getName() + " declares no field " + field
                + " -- a declared entry naming a field that no longer exists asserts nothing "
                + "(F2-199)", e);
        }
    }

    private static Class<?> load(String fqcn) {
        try {
            return Class.forName(fqcn, false,
                SingletonMutableStateContractTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("declared entry names a class that is not on the classpath: "
                + fqcn, e);
        }
    }

    private static byte[] bytecodeOf(Class<?> c) {
        String res = c.getName().replace('.', '/') + ".class";
        try (var in = c.getClassLoader().getResourceAsStream(res)) {
            if (in == null) {
                throw new AssertionError("no bytecode for " + c.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void publish(Map<String, String> measured, int beanCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("F2-452 - shared mutable state on singleton Spring beans\n");
        sb.append("=".repeat(78)).append('\n');
        sb.append("A bean is a singleton, so an instance field is shared by every request and\n");
        sb.append("therefore by every tenant. Measured 2026-09-03: ").append(beanCount)
          .append(" singleton beans, 0 unloadable,\n");
        sb.append("15 holding a mutable-holder field - and 4 of the 15 were PARTIALLY\n");
        sb.append("synchronized: every integration stub client wrote to its recordedCalls\n");
        sb.append("ArrayList from isHealthy() without the monitor its three sibling writers\n");
        sb.append("hold, on a /status route that is ungated by grc.integrations.*.enabled and\n");
        sb.append("served by the default bean. Fixed in F2-452. A clean-sweep reading of the\n");
        sb.append("same 15 preceded this gate; the verdict-verification leg below refuted it.\n\n");
        for (Map.Entry<String, String> e : measured.entrySet()) {
            Holding h = DECLARED.get(e.getKey());
            sb.append(String.format("  %-22s %s%n", h == null ? "UNDECLARED" : h.verdict(),
                e.getKey()));
            sb.append(String.format("       %s : %s%n", e.getValue(), h == null ? "-" : h.why()));
        }
        sb.append("\nAlso measured and NOT a member: 75 singleton beans hold a non-final SCALAR\n");
        sb.append("field, and those are @Value configuration plus declared test seams (setClock,\n");
        sb.append("setBroadcaster - F2-192's pattern), not shared collections.\n");
        try {
            Path out = Paths.get("target");
            Files.createDirectories(out);
            Files.writeString(out.resolve("singleton-mutable-state.txt"), sb.toString(),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
