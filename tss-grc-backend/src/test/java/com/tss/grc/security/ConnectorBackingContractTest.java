package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * F2-193 — a connector may not advertise an automatic trigger the backend does not provide.
 *
 * <p>The frontend catalogue {@code tss-grc-ui-v2/src/lib/connectors.ts} is what an operator reads on
 * the Connectors screen. Until 2026-08-12 each of its twelve entries carried a hand-set
 * {@code status}, a {@code cadence}, a {@code lastRunAt} and an {@code intake30d} count, and eight
 * read ACTIVE. Measured against this codebase: <b>five of the eight had no implementation of any
 * kind</b> — the SDAIA / NCA / SAMA regulator feeds, the vendor SOC 2 expiry watcher and the CAPA
 * effectiveness re-test, the last describing a {@code VERIFICATION_FAILED} status that appears in no
 * Java source. A sixth (Splunk) is webhook-only, not the five-minute poll it claimed. The two with a
 * real {@code @Scheduled} loop had the wrong interval in the flattering direction (15 minutes shown
 * as "Daily", 06:00 daily shown as "Hourly"). And the connector with the shortest real interval of
 * all — CrowdStrike at 60 seconds — was <b>not in the catalogue</b>, because the list was written
 * from intent and nothing compared it with the code.
 *
 * <p>🔑 <b>A capability claim is only as good as the artefact that has to agree with it.</b> The
 * frontend half of this gate ({@code connectorTriggerHonesty.test.ts}) can check the catalogue's
 * shape — that the invented fields are gone, that automation names a backing symbol, that an
 * unimplemented connector describes itself in the planned voice. It cannot check whether the named
 * symbol is real. That is this file: every {@code backing} resolves, a claimed poll must carry
 * {@code @Scheduled} on the named method, and a claimed webhook must be a mapped route. Neither half
 * is sufficient alone, which is the point — this is the same two-sided arrangement as F2-170's
 * endpoint contract, for the same reason.
 *
 * <p>⚠ It also checks the <b>gate</b>: the property an operator must set. Both scanner pollers ship
 * {@code polling-enabled: false} and the KRI cycle's flag is defaulted false in code and set in no
 * profile, so <i>"a poller exists"</i> and <i>"the poller runs"</i> are different facts. The old
 * catalogue asserted the second by never distinguishing them. A gate that names a property nothing
 * binds would send an operator to edit a key with no effect, so each one is resolved against
 * {@code application.yml} or a {@code @Value} placeholder in the sources.
 *
 * <p>Hermetic: reads the catalogue from disk and this application's own classpath. No database, no
 * secrets, no running server — {@code mvn -o clean package} must stay that way.
 */
class ConnectorBackingContractTest {

    private static final Path FE_CONNECTORS =
        Path.of("..", "tss-grc-ui-v2", "src", "lib", "connectors.ts");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    /** One catalogue entry, as declared by the frontend. */
    private record Connector(String id, String trigger, String backing, String gate, String cadence) {
        boolean automated() {
            return "SCHEDULED_POLL".equals(trigger) || "INBOUND_WEBHOOK".equals(trigger);
        }
    }

    // ── the parse ─────────────────────────────────────────────────────────────────────────────

    private static final Pattern ENTRY = Pattern.compile("\\{([^{}]*?)\\}", Pattern.DOTALL);
    private static final Pattern FIELD_ID = field("id");
    private static final Pattern FIELD_TRIGGER = field("trigger");
    private static final Pattern FIELD_BACKING = field("backing");
    private static final Pattern FIELD_GATE = field("gate");
    private static final Pattern FIELD_CADENCE = field("cadence");

    private static Pattern field(String name) {
        return Pattern.compile("\\b" + name + ":\\s*'([^']*)'");
    }

    private static List<Connector> catalogue() {
        String src = stripComments(read(FE_CONNECTORS));
        int start = src.indexOf("CONNECTOR_REGISTRY");
        assertThat(start).as("connectors.ts no longer declares CONNECTOR_REGISTRY").isGreaterThan(0);
        String body = src.substring(start);

        List<Connector> out = new ArrayList<>();
        Matcher m = ENTRY.matcher(body);
        while (m.find()) {
            String block = m.group(1);
            String id = one(FIELD_ID, block);
            if (id == null || !id.startsWith("cn-")) {
                continue;   // the label/tone maps at the end of the file are not entries
            }
            out.add(new Connector(id, one(FIELD_TRIGGER, block), one(FIELD_BACKING, block),
                one(FIELD_GATE, block), one(FIELD_CADENCE, block)));
        }
        return out;
    }

    private static String one(Pattern p, String block) {
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : null;
    }

    // ── the backend side ──────────────────────────────────────────────────────────────────────

    /** Simple class name → the class, for every Spring component in the application. */
    private static Map<String, Class<?>> components() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));   // meta: @Service too
        Map<String, Class<?>> out = new LinkedHashMap<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                out.putIfAbsent(c.getSimpleName(), c);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // a component we cannot load cannot be somebody's declared backing either
            }
        }
        return out;
    }

    /** Every {@code VERB /path} a REST controller serves. */
    private static Set<String> routes() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> out = new TreeSet<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            var onClass = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
            String[] bases = onClass == null || onClass.path().length == 0
                ? new String[] { "" } : onClass.path();
            for (Method m : c.getMethods()) {
                var ann = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (ann == null || ann.method().length == 0) {
                    continue;
                }
                String[] paths = ann.path().length == 0 ? new String[] { "" } : ann.path();
                for (String b : bases) {
                    for (String p : paths) {
                        for (RequestMethod v : ann.method()) {
                            out.add(v.name() + " " + ("" + b + p).replaceAll("//+", "/"));
                        }
                    }
                }
            }
        }
        return out;
    }

    // ── assertions ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the catalogue parses, so nothing below passes because it found nothing")
    void theCatalogueParses() {
        assumeCheckedOut();
        List<Connector> all = catalogue();
        // A count-independent non-vacuity check: every `id: 'cn-…'` in the file must have become an
        // entry. A regex that silently matched half the file is how F2-192's phantom counts happened.
        long declared = stripComments(read(FE_CONNECTORS)).lines()
            .filter(l -> l.contains("id: 'cn-")).count();
        assertThat(all).as("entries parsed out of connectors.ts").hasSize((int) declared);
        assertThat(all.stream().filter(Connector::automated).count())
            .as("automated connectors — zero would make every backing assertion vacuous")
            .isGreaterThan(0);
        assertThat(all.stream().map(Connector::trigger).distinct())
            .as("trigger vocabulary in use").contains("SCHEDULED_POLL", "INBOUND_WEBHOOK",
                "NOT_IMPLEMENTED");
        for (Connector c : all) {
            assertThat(c.trigger()).as("%s declares no trigger", c.id()).isNotNull();
        }
    }

    @Test
    @DisplayName("every claimed scheduled poll names a method the container really calls")
    void everyClaimedPollIsScheduled() {
        assumeCheckedOut();
        Map<String, Class<?>> components = components();
        List<String> problems = new ArrayList<>();

        for (Connector c : catalogue()) {
            if (!"SCHEDULED_POLL".equals(c.trigger())) {
                continue;
            }
            if (c.backing() == null || !c.backing().contains("#")) {
                problems.add(c.id() + ": claims a scheduled poll and names no Class#method");
                continue;
            }
            String[] parts = c.backing().split("#", 2);
            Class<?> owner = components.get(parts[0]);
            if (owner == null) {
                problems.add(c.id() + ": no Spring component named " + parts[0]);
                continue;
            }
            List<Method> named = Stream.of(owner.getMethods())
                .filter(m -> m.getName().equals(parts[1])).toList();
            if (named.isEmpty()) {
                problems.add(c.id() + ": " + parts[0] + " has no method " + parts[1] + "()");
                continue;
            }
            boolean scheduled = named.stream()
                .anyMatch(m -> AnnotatedElementUtils.findMergedAnnotation(m, Scheduled.class) != null);
            if (!scheduled) {
                problems.add(c.id() + ": " + c.backing() + " exists but carries no @Scheduled — "
                    + "a method somebody has to call is not an automatic trigger");
            }
        }
        assertThat(problems).as(
            "F2-193: a catalogue entry claiming SCHEDULED_POLL must name a real @Scheduled method. "
            + "Five entries claimed hourly/weekly/daily ingestion with no backend at all.").isEmpty();
    }

    @Test
    @DisplayName("every claimed webhook or operator route is a route this application maps")
    void everyClaimedRouteExists() {
        assumeCheckedOut();
        Set<String> routes = routes();
        assertThat(routes).as("mapped routes found by reflection").hasSizeGreaterThan(500);

        List<String> problems = new ArrayList<>();
        for (Connector c : catalogue()) {
            if ("SCHEDULED_POLL".equals(c.trigger()) || "NOT_IMPLEMENTED".equals(c.trigger())) {
                continue;
            }
            if (c.backing() == null) {
                problems.add(c.id() + ": claims " + c.trigger() + " and names no route");
            } else if (!routes.contains(c.backing())) {
                problems.add(c.id() + ": no handler serves " + c.backing());
            }
        }
        assertThat(problems).as("F2-193: a claimed inbound route must exist").isEmpty();
    }

    @Test
    @DisplayName("an unimplemented connector names no backend, no gate and no cadence")
    void unimplementedConnectorsClaimNothing() {
        assumeCheckedOut();
        // The both-directions half. Without it, a future edit could flip a real connector to
        // NOT_IMPLEMENTED to silence the assertions above while leaving the claims on screen.
        List<String> problems = new ArrayList<>();
        for (Connector c : catalogue()) {
            if (!"NOT_IMPLEMENTED".equals(c.trigger())) {
                continue;
            }
            if (c.backing() != null) problems.add(c.id() + " is NOT_IMPLEMENTED and names " + c.backing());
            if (c.gate() != null)    problems.add(c.id() + " is NOT_IMPLEMENTED and names gate " + c.gate());
            if (c.cadence() != null) problems.add(c.id() + " is NOT_IMPLEMENTED and declares a cadence");
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("every gate an operator is told to set is a property this application binds")
    void everyGateIsARealProperty() {
        assumeCheckedOut();
        PropertySource<?> yaml = applicationYaml();
        String sources = allMainSources();

        List<String> problems = new ArrayList<>();
        for (Connector c : catalogue()) {
            if (!c.automated()) {
                continue;
            }
            assertThat(c.gate()).as("%s is automated and names no gate", c.id()).isNotNull();
            for (String prop : expandGate(c.gate())) {
                boolean bound = yaml.getProperty(prop) != null
                    || sources.contains("${" + prop);
                if (!bound) {
                    problems.add(c.id() + ": gate " + prop
                        + " is neither declared in application.yml nor read by a @Value placeholder");
                }
            }
        }
        assertThat(problems).as(
            "F2-193: the property an operator is told to set must be one the application reads. "
            + "Note this deliberately does NOT assert the gate is ON — both scanner pollers ship "
            + "polling-enabled:false and grc.kri.scheduler-enabled is set in no profile, which is "
            + "why the catalogue has to show the gate at all.").isEmpty();
    }

    @Test
    @DisplayName("the gate shorthand expands the way the screen reads it")
    void gateShorthandExpansion() {
        // `grc.integrations.tenable.enabled + .polling-enabled` is one string on screen and two
        // properties in fact. If this helper silently produced one, everyGateIsARealProperty would
        // check half of each gate and still pass.
        assertThat(expandGate("grc.kri.scheduler-enabled"))
            .containsExactly("grc.kri.scheduler-enabled");
        assertThat(expandGate("grc.integrations.tenable.enabled + .polling-enabled"))
            .containsExactly("grc.integrations.tenable.enabled",
                "grc.integrations.tenable.polling-enabled");
        assertThat(expandGate("grc.integrations.splunk.enabled + .webhook-shared-secret"))
            .containsExactly("grc.integrations.splunk.enabled",
                "grc.integrations.splunk.webhook-shared-secret");
    }

    /** `a.b.c + .d` → [a.b.c, a.b.d]. A continuation inherits the previous property's parent. */
    static List<String> expandGate(String gate) {
        List<String> out = new ArrayList<>();
        String previous = null;
        for (String raw : gate.split("\\+")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.startsWith(".")) {
                if (previous == null) {
                    throw new IllegalArgumentException("gate starts with a continuation: " + gate);
                }
                String parent = previous.substring(0, previous.lastIndexOf('.'));
                part = parent + part;
            }
            out.add(part);
            previous = part;
        }
        return out;
    }

    // ── plumbing ──────────────────────────────────────────────────────────────────────────────

    private static void assumeCheckedOut() {
        assumeTrue(Files.exists(FE_CONNECTORS),
            "frontend not checked out at " + FE_CONNECTORS.toAbsolutePath()
                + " — nothing to contract-check");
    }

    private static PropertySource<?> applicationYaml() {
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
            assertThat(loaded).as("application.yml did not load").isNotEmpty();
            return loaded.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException("could not load application.yml", e);
        }
    }

    /** Every main source concatenated — used only to look for a `${property}` placeholder. */
    private static String allMainSources() {
        StringBuilder sb = new StringBuilder(1 << 22);
        try (Stream<Path> s = Files.walk(MAIN_JAVA)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                sb.append(read(p)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + MAIN_JAVA.toAbsolutePath(), e);
        }
        return sb.toString();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    /**
     * Strips {@code //} and block comments. Load-bearing: this file's own header documents the old
     * `status: 'ACTIVE'` / `lastRunAt` shape in prose, and a parse that read the commentary would
     * find entries that no longer exist.
     */
    private static String stripComments(String src) {
        String noBlocks = src.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder out = new StringBuilder(noBlocks.length());
        for (String line : noBlocks.split("\n", -1)) {
            int i = line.indexOf("//");
            out.append(i < 0 ? line : line.substring(0, i)).append('\n');
        }
        return out.toString();
    }

    /** Kept for symmetry with the frontend gate's field list — see the class javadoc. */
    static final Set<String> RETIRED_CATALOGUE_FIELDS =
        new LinkedHashSet<>(List.of("status", "lastRunAt", "intake30d", "owner"));

    @Test
    @DisplayName("the retired fabrication fields have not returned to the catalogue")
    void retiredFieldsHaveNotReturned() {
        assumeCheckedOut();
        String src = stripComments(read(FE_CONNECTORS));
        int registry = src.indexOf("CONNECTOR_REGISTRY");
        String body = src.substring(registry);
        List<String> back = new ArrayList<>();
        for (String f : RETIRED_CATALOGUE_FIELDS) {
            if (Pattern.compile("\\b" + f + ":\\s").matcher(body).find()) {
                back.add(f);
            }
        }
        // Duplicated on purpose from connectorTriggerHonesty.test.ts: the frontend suite and the
        // backend suite are run by different commands, and a fabricated `lastRunAt` must fail
        // whichever one CI happens to run.
        assertThat(back).as(
            "F2-193: %s is state no server supplies for this catalogue. The 30-day counts summed to "
            + "292 in a KPI linking to a queue with zero rows.", back).isEmpty();
    }
}
