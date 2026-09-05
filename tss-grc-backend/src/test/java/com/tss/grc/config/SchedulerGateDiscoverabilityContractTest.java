package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.tss.grc.support.GuardExpressions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * F2-462 — a scheduler an operator cannot reach is a scheduler that is off for ever.
 *
 * <p>Ten classes in this platform gate a {@code @Scheduled} tick behind a boolean
 * {@code @Value("${…:false}")}. Five of them were declared in <strong>no packaged yml at all</strong>
 * — so there was no {@code ${PLACEHOLDER}} for {@code external.properties} to override
 * (F2-456 established that it overrides placeholders, not property paths) and no entry in
 * {@code external.properties.template}, the file {@code CLAUDE.md} calls the home of all runtime
 * configuration. They were off in every profile including production.
 *
 * <p>🔑🔑 <strong>The operator-facing artefact enumerated exactly the schedulers that were already
 * ON.</strong> That block read <em>"The four SLA/expiry clocks. Turning one off stops reminders and
 * overdue escalations for that module — which is a compliance gap on NCA/SAMA reviews, not merely a
 * quiet inbox."</em> Every clause true, and the four were the four that could not produce that gap,
 * while five that already were producing it went unmentioned. F2-198's shape on an operator surface:
 * the enumeration is real, and its POPULATION is <em>the switches somebody had already wired</em>.
 *
 * <p>🔑 And the code half said the same thing in the same reassuring direction. Four javadocs read
 * <em>"Off by default in dev … Operators flip it on per-env"</em> — "in dev" implies production
 * enables it, and "flip it on per-env" claims a mechanism that did not exist. F2-184's shape,
 * five files over, which is why the corrections quote the sentence they replace.
 *
 * <h2>What this asserts</h2>
 * <ol>
 *   <li>Every scheduler gate's property is declared in a packaged yml — hard zero.</li>
 *   <li>Every one is declared <em>as a placeholder somewhere</em>, and that placeholder's env name
 *       appears in {@code external.properties.template} — hard zero. A literal is not enough: a
 *       property with no placeholder cannot be reached from {@code external.properties} at all.</li>
 *   <li>The population is measured, with floors above the largest <em>wrong</em> answer (F2-215) and
 *       both of the detector's subtractions asserted non-empty, so a narrowing that stopped
 *       narrowing is visible rather than silently widening the population.</li>
 *   <li>The detector fires on the shape it claims and on none of the shapes it must not.</li>
 * </ol>
 *
 * <p>⚠ This does <strong>not</strong> assert which gates default on. Enabling a sweep writes audit
 * rows, raises events and flags records on a live tenant, so that is an operator decision (F2-188)
 * and a gate that pinned the split would fail the build on a legitimate one.
 *
 * <p>⚠ The population is derived <em>structurally</em> — a boolean {@code @Value} gate in a class
 * that also declares {@code @Scheduled} — never from a list of key names, because a census keyed on
 * a list of names measures the list (F2-217). It is also what correctly excludes
 * {@code grc.ccm.custom-sql.enabled} and {@code grc.ccm.ldap.enabled}: neither runner schedules
 * anything, and whether a switch that executes operator-supplied SQL belongs in an operator template
 * is a security question rather than a discoverability one.
 */
class SchedulerGateDiscoverabilityContractTest {

    /** A boolean feature gate: {@code @Value("${key:true|false}") private boolean field;}. */
    private static final Pattern BOOLEAN_GATE = Pattern.compile(
        "@Value\\(\"\\$\\{([\\w.\\-]+):(true|false)\\}\"\\)\\s*private\\s+boolean\\s+(\\w+)\\s*;");

    /** A yml value that is a reference: {@code ${ENV_NAME}} or {@code ${ENV_NAME:default}}. */
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)[:}]");

    private static final Path JAVA = Paths.get("src/main/java").toAbsolutePath().normalize();
    private static final Path TEMPLATE =
        Paths.get("external.properties.template").toAbsolutePath().normalize();

    /**
     * Measured 2026-09-05. The floor sits above the largest WRONG answer rather than above zero
     * (F2-215): a walk that resolves nothing gives 0, and a detector that stops matching
     * {@code @Scheduled} gives the whole boolean-gate population instead — which is LARGER, so a
     * floor alone cannot see it. That is what the two subtraction assertions below are for.
     */
    private static final int GATES = 10;

    record Gate(String key, String fallback, String field, String owner) {}

    // ─────────────────────────── the population ───────────────────────────

    /**
     * Every boolean gate in a class that also schedules something.
     *
     * <p>⚠ Comments are stripped first. This codebase documents a retirement by quoting what it
     * replaced — the five javadocs this finding corrected each quote their own retired sentence —
     * so a raw scan reads the epitaph as the corpse (F2-193).
     */
    static List<Gate> schedulerGates() throws IOException {
        List<Gate> out = new ArrayList<>();
        for (Path p : javaSources()) {
            String code = GuardExpressions.stripComments(Files.readString(p));
            if (!code.contains("@Scheduled")) continue;
            String owner = JAVA.relativize(p).toString().replace('\\', '/');
            for (Matcher m = BOOLEAN_GATE.matcher(code); m.find(); ) {
                out.add(new Gate(m.group(1), m.group(2), m.group(3), owner));
            }
        }
        out.sort((a, b) -> a.key().compareTo(b.key()));
        return out;
    }

    /** Boolean gates in classes that schedule NOTHING — the half this population excludes. */
    static List<Gate> unscheduledGates() throws IOException {
        List<Gate> out = new ArrayList<>();
        for (Path p : javaSources()) {
            String code = GuardExpressions.stripComments(Files.readString(p));
            if (code.contains("@Scheduled")) continue;
            String owner = JAVA.relativize(p).toString().replace('\\', '/');
            for (Matcher m = BOOLEAN_GATE.matcher(code); m.find(); ) {
                out.add(new Gate(m.group(1), m.group(2), m.group(3), owner));
            }
        }
        return out;
    }

    /** Classes that schedule something and gate it behind nothing. */
    static List<String> ungatedSchedulers() throws IOException {
        List<String> out = new ArrayList<>();
        for (Path p : javaSources()) {
            String code = GuardExpressions.stripComments(Files.readString(p));
            if (!code.contains("@Scheduled")) continue;
            if (BOOLEAN_GATE.matcher(code).find()) continue;
            out.add(JAVA.relativize(p).toString().replace('\\', '/'));
        }
        return out;
    }

    // ─────────────────────────── the legs ───────────────────────────

    @Test
    void everySchedulerGateIsDeclaredInAPackagedYml() throws IOException {
        Map<String, Set<String>> declared = StartupResolutionContractTest.declaredValues();
        List<String> undeclared = new ArrayList<>();
        for (Gate g : schedulerGates()) {
            if (!declared.containsKey(g.key())) {
                undeclared.add(g.key() + "   (" + g.owner() + ")");
            }
        }
        assertThat(undeclared)
            .as("""
                A scheduler gate declared in no packaged yml is off in every profile, and the only
                way to reach it is to know a property name that appears in no artefact an operator
                reads. Five of the ten were in exactly that state until F2-462 — including the AML
                sanctions SLA sweep, the daily KRI evaluation, the risk-review overdue sweep, the
                approval-chain SLA sweep and the 60-second CCM tick.

                Declare it in application.yml as ${ENV_NAME:false}. That changes nothing at runtime
                — the value is the same one the @Value fallback already produced — and it is what
                makes the switch exist for somebody other than its author.""")
            .isEmpty();
    }

    @Test
    void everySchedulerGateIsReachableFromExternalProperties() throws IOException {
        Map<String, Set<String>> declared = StartupResolutionContractTest.declaredValues();
        String template = Files.readString(TEMPLATE);

        List<String> unreachable = new ArrayList<>();
        for (Gate g : schedulerGates()) {
            Set<String> values = declared.getOrDefault(g.key(), Set.of());
            String env = null;
            for (String v : values) {
                Matcher m = REFERENCE.matcher(v);
                if (m.find()) {
                    env = m.group(1);
                    break;
                }
            }
            if (env == null) {
                unreachable.add(g.key() + "   declared only as a literal " + values);
            } else if (!template.contains(env)) {
                unreachable.add(g.key() + "   ${" + env + "} is in no line of the template");
            }
        }
        assertThat(unreachable)
            .as("""
                external.properties overrides ${PLACEHOLDER} names and NOT Spring property paths
                (F2-456), so a gate declared as a literal cannot be reached from the operator's own
                file however plainly it is written in the yml — and a placeholder the template never
                names cannot be found by anybody who did not write it.

                The template is the file CLAUDE.md calls the home of all runtime configuration. Its
                scheduler block used to list four switches under the heading "The four SLA/expiry
                clocks", which were precisely the four already ON: the enumeration read as the whole
                population while five more sat off and unmentioned.""")
            .isEmpty();
    }

    @Test
    void thePopulationIsMeasured() throws IOException {
        List<Gate> gates = schedulerGates();
        assertThat(gates)
            .as("scheduler gates found. If this collapses, both hard zeros above are passing over "
                + "nothing rather than over a reachable population (F2-205, F2-235).")
            .hasSizeGreaterThanOrEqualTo(GATES);

        // Both subtractions asserted non-empty. A detector that stopped requiring @Scheduled would
        // report a LARGER population, which no floor can see — so what catches it is that the
        // excluded half must still have members.
        assertThat(unscheduledGates())
            .as("""
                Boolean @Value gates in classes that schedule nothing. This must not be empty: it is
                the half the population excludes, and it is what makes the @Scheduled narrowing
                load-bearing rather than decorative.

                It is also why grc.ccm.custom-sql.enabled and grc.ccm.ldap.enabled are outside this
                gate by construction rather than by exemption — neither runner schedules anything,
                and whether a switch that executes operator-supplied SQL belongs in an operator
                template is a security decision, not a discoverability one.""")
            .isNotEmpty();

        assertThat(ungatedSchedulers())
            .as("Classes that schedule something behind no gate at all. Also not empty: most "
                + "schedulers here are unconditional, so a detector that started treating every "
                + "@Scheduled class as gated would be reporting on a population that is mostly "
                + "not this one.")
            .isNotEmpty();

        assertThat(StartupResolutionContractTest.declaredValues())
            .as("properties read off the packaged documents. Under this, the two hard zeros above "
                + "would pass because nothing was declared to compare against.")
            .hasSizeGreaterThan(100);
    }

    @Test
    void theDetectorFiresOnTheShapeItClaims() {
        // MUST match: the house shape, with and without an intervening newline.
        assertThat(gatesIn("""
            class X {
                @Scheduled(fixedDelay = 60_000L)
                void tick() { }
                @Value("${grc.x.scheduler-enabled:false}")
                private boolean schedulerEnabled;
            }"""))
            .extracting(Gate::key)
            .containsExactly("grc.x.scheduler-enabled");

        assertThat(gatesIn("""
            class X {
                @Scheduled(cron = "0 0 6 * * *") void tick() { }
                @Value("${grc.y.review-scheduler-enabled:true}") private boolean on;
            }"""))
            .extracting(Gate::key)
            .containsExactly("grc.y.review-scheduler-enabled");

        // MUST NOT match: a gate with no scheduler beside it. This is the narrowing, and getting it
        // wrong pulls in security switches whose discoverability is a different question.
        assertThat(gatesIn("""
            class X {
                @Value("${grc.ccm.custom-sql.enabled:false}")
                private boolean enabled;
            }"""))
            .isEmpty();

        // MUST NOT match: a non-boolean @Value. A cadence or a threshold is not a gate.
        assertThat(gatesIn("""
            class X {
                @Scheduled(fixedDelay = 60_000L) void tick() { }
                @Value("${grc.x.batch-size:50}") private int batchSize;
            }"""))
            .isEmpty();

        // MUST NOT match: a @Value with no default. It cannot carry the "off by default" claim this
        // gate is about, and it fails at startup rather than silently defaulting (F2-443).
        assertThat(gatesIn("""
            class X {
                @Scheduled(fixedDelay = 60_000L) void tick() { }
                @Value("${grc.x.scheduler-enabled}") private boolean on;
            }"""))
            .isEmpty();

        // MUST NOT match: the shape inside a comment. Every one of the five javadocs this finding
        // corrected quotes its own retired sentence, so without stripping the gate reads the
        // epitaph as the corpse (F2-193).
        assertThat(gatesIn("""
            class X {
                /* was: @Value("${grc.old.scheduler-enabled:false}") private boolean on; */
                @Scheduled(fixedDelay = 60_000L) void tick() { }
            }"""))
            .isEmpty();
    }

    /** The detector, over synthetic input: on a clean corpus a working one and a dead one both
     *  report an empty offender list (F2-235), so it is driven directly. */
    private static List<Gate> gatesIn(String source) {
        String code = GuardExpressions.stripComments(source);
        List<Gate> out = new ArrayList<>();
        if (!code.contains("@Scheduled")) return out;
        for (Matcher m = BOOLEAN_GATE.matcher(code); m.find(); ) {
            out.add(new Gate(m.group(1), m.group(2), m.group(3), "<synthetic>"));
        }
        return out;
    }

    private static List<Path> javaSources() throws IOException {
        assertThat(Files.isDirectory(JAVA))
            .as("expected %s to exist — a wrong path would make every leg above pass over nothing",
                JAVA)
            .isTrue();
        try (Stream<Path> s = Files.walk(JAVA)) {
            return s.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .sorted()
                .toList();
        }
    }

    static {
        if (!Files.exists(TEMPLATE)) {
            throw new UncheckedIOException(new IOException(
                "external.properties.template is the operator's own surface and this gate reads it; "
                + "a wrong path would make the reachability leg pass over nothing"));
        }
    }
}
