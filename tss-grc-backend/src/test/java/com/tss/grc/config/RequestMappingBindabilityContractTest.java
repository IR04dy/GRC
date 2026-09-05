package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-439 — two ways a mapping can be unusable that a hermetic build cannot see.
 *
 * <h3>Why a gate rather than a test</h3>
 *
 * F2-247 is the precedent and states the class: two Spring beans with the same simple name stop the
 * application starting, and <b>the whole unit suite passes over it</b> — nothing here boots a
 * context, and {@code mvn -o clean package} is deliberately hermetic. The only instrument that
 * could see it was launching the app, and a launch is not part of the build. These are two more
 * members of that class:
 *
 * <ul>
 *   <li><b>A path placeholder no {@code @PathVariable} can satisfy.</b> {@code @GetMapping("/{runId}")}
 *       bound by a parameter named {@code id} throws at REQUEST time, so the route answers <b>500 for
 *       every caller</b> and nothing at build time notices. CLAUDE.md already records the
 *       {@code @PreAuthorize} SpEL version of exactly this mismatch — <i>"it is {@code #id} on
 *       ExerciseRuntimeController and {@code #runId} on ExerciseObserverController, so a copied
 *       constant fails silently … the endpoint answers 500, not 403"</i> — and the BINDING side is
 *       the sibling nobody checked. 🔑 F2-158's shape: a route that has answered 500 since it was
 *       written, invisible because nothing exercises it.</li>
 *   <li><b>Two handlers claiming the same (verb, path).</b> Spring refuses to start with
 *       <i>"Ambiguous mapping"</i>. Same invisibility, same cost.</li>
 * </ul>
 *
 * <p>⚙ <b>No defect was found: both are zero today</b> (0 of 1,352 placeholder-bearing mappings, 0
 * of 2,253 distinct keys). This is therefore a latent-hazard closure, not a defect discovery, and
 * saying which you have is the point (F2-283). What the gate buys is that the NEXT one fails the
 * build rather than the deployment.
 *
 * <p>⚠ <b>Hard zeros with no exemption map, and that is safe rather than strict</b> (F2-214): an
 * explicit {@code @PathVariable("name")} is always available, and so is a distinct path — so there
 * is no correct code for either leg to fail on.
 *
 * <p>⚠ Reflection over the shipped bytecode, never a source regex. F2-411 measured what the regex
 * costs on the annotation question (6 sites of which 3 wrong) and F2-424 what a fixed lookback
 * window costs (an annotation separated from its method by a long javadoc is invisible, in the
 * quiet direction).
 *
 * <h3>⚠⚠ What this gate deliberately does NOT check, and why</h3>
 *
 * The natural third member is <b>a query key the frontend sends that the endpoint declares no
 * {@code @RequestParam} for</b> — F2-210's body-key question asked of the query string, and
 * genuinely outside {@code FrontendEndpointContractTest}'s population, because its {@code norm()}
 * strips everything after the {@code ?}. It was measured and <b>rejected as not decidable by a
 * scan</b>: pairing a key to an endpoint needs the enclosing function resolved, and a regex window
 * over an object-literal API client bleeds keys across members — widening the window took the
 * candidate count from 8 to 57 and every one of the 57 was a phantom ({@code [from, to]} attributed
 * to all 36 {@code /api/v1/changes/**} calls). Both of the two candidates precise enough to read
 * were <b>refuted</b>: {@code auditLogApi.historyFor} sends exactly the {@code entityType} /
 * {@code entityId} the server declares, and {@code getDomainAlignment}'s {@code domain} had bled
 * onto {@code /posture}. 🔑 <b>F2-182's rule: reachability is a dataflow question, and a regex can
 * only tell you where to go and read.</b> A gate built on that pairing would fail on correct code.
 */
@DisplayName("F2-439 — every mapping must be bindable, and no two may collide")
class RequestMappingBindabilityContractTest {

    private static final Path CLASSES = Path.of("target", "classes");

    /** {@code {name}} or {@code {name:regex}} — the regex form is legal in a Spring path. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}:]+)(?::[^}]*)?\\}");

    /**
     * ⚠ Package-private for {@code RouteCitationContractTest} (F2-464), which resolves the routes
     * named in the platform's runtime refusal messages against this same set. A second route
     * resolver would be free to disagree with this one about which classes are shipped, how a
     * class-level and a method-level path join, or what an {@code ANY}-verb mapping means — and
     * the disagreement would be invisible, because each map looks complete on its own (F2-190).
     */
    record Mapping(String verb, String path, String owner,
                   Set<String> needs, Set<String> supplies, boolean catchAll) {}

    private static long topLevelClassFiles() {
        try (Stream<Path> f = Files.walk(CLASSES)) {
            return f.filter(x -> x.toString().endsWith(".class"))
                .filter(x -> !x.getFileName().toString().contains("$")).count();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + CLASSES, e);
        }
    }

    private static List<Class<?>> shippedClasses() {
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> f = Files.walk(CLASSES)) {
            for (Path p : f.filter(x -> x.toString().endsWith(".class")).sorted().toList()) {
                String fqcn = CLASSES.relativize(p).toString()
                    .replace(File.separatorChar, '.').replaceAll("\\.class$", "");
                if (fqcn.contains("$")) continue;
                try {
                    out.add(ClassUtils.forName(fqcn,
                        RequestMappingBindabilityContractTest.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // Swallowed HERE and caught THERE: theCorpusWasActuallyRead asserts this list
                    // equals the class files on disk, so an unloadable class fails that leg rather
                    // than quietly shrinking the population the other legs measure.
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + CLASSES, e);
        }
        return out;
    }

    /** Every (verb, path) a controller claims, with what its path needs and its parameters supply. */
    /** ⚠ Package-private — see {@link Mapping}. One walk, two readers (F2-190). */
    static List<Mapping> mappings() {
        List<Mapping> out = new ArrayList<>();
        for (Class<?> c : shippedClasses()) {
            if (AnnotatedElementUtils.findMergedAnnotation(c, RestController.class) == null
                && AnnotatedElementUtils.findMergedAnnotation(c, Controller.class) == null) {
                continue;
            }
            RequestMapping cm = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
            String[] base = cm == null || cm.value().length == 0 ? new String[]{""} : cm.value();
            for (Method m : c.getDeclaredMethods()) {
                RequestMapping mm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (mm == null) continue;

                Set<String> supplies = new LinkedHashSet<>();
                boolean catchAll = false;
                for (Parameter p : m.getParameters()) {
                    PathVariable pv = AnnotatedElementUtils.findMergedAnnotation(p, PathVariable.class);
                    if (pv == null) continue;
                    if (Map.class.isAssignableFrom(p.getType())) {
                        // @PathVariable Map<String,String> takes every placeholder by name
                        catchAll = true;
                        continue;
                    }
                    String declared = pv.value().isEmpty() ? pv.name() : pv.value();
                    if (!declared.isEmpty()) {
                        supplies.add(declared);
                    } else if (p.isNamePresent()) {
                        supplies.add(p.getName());
                    }
                    // else: neither an explicit name nor a compiled parameter name — the mapping
                    // cannot bind at all, and theBuildCompilesWithParameterNames says so.
                }

                String[] paths = mm.value().length == 0 ? new String[]{""} : mm.value();
                String[] verbs = mm.method().length == 0 ? new String[]{"ANY"}
                    : Arrays.stream(mm.method()).map(Object::toString).toArray(String[]::new);
                for (String b : base) {
                    for (String q : paths) {
                        String full = normalise(b + q);
                        Set<String> needs = new LinkedHashSet<>();
                        Matcher x = PLACEHOLDER.matcher(full);
                        while (x.find()) needs.add(x.group(1));
                        for (String v : verbs) {
                            out.add(new Mapping(v.toLowerCase(Locale.ROOT), full,
                                c.getSimpleName() + "#" + m.getName(), needs, supplies, catchAll));
                        }
                    }
                }
            }
        }
        return out;
    }

    private static String normalise(String p) {
        String s = p.replaceAll("/{2,}", "/");
        if (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    // ─── the two detectors, extracted so a self-test can drive them ────

    /**
     * ⚠ Extracted from the leg so {@link #theDetectorsFireOnTheShapesTheyClaimToCatch()} can drive
     * them over synthetic input. On a clean corpus a working detector and a rotted one both report
     * an empty list, so the corpus assertions below cannot tell you the scan still matches
     * (F2-235). Driving the same code over a fixture that must be flagged is what can.
     */
    static List<String> unbindable(List<Mapping> ms) {
        List<String> broken = new ArrayList<>();
        for (Mapping m : ms) {
            if (m.needs().isEmpty() || m.catchAll()) continue;
            Set<String> missing = new LinkedHashSet<>(m.needs());
            missing.removeAll(m.supplies());
            if (!missing.isEmpty()) {
                broken.add("%s %s [%s] needs %s, supplies %s, MISSING %s"
                    .formatted(m.verb(), m.path(), m.owner(), m.needs(), m.supplies(), missing));
            }
        }
        return broken;
    }

    static List<String> ambiguous(List<Mapping> ms) {
        Map<String, Set<String>> byKey = new LinkedHashMap<>();
        for (Mapping m : ms) {
            byKey.computeIfAbsent(m.verb() + " " + m.path(), k -> new LinkedHashSet<>()).add(m.owner());
        }
        return byKey.entrySet().stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> e.getKey() + " -> " + e.getValue()).toList();
    }

    // ─── the legs ─────────────────────────────────────────────────────

    @Test
    void everyPathPlaceholderHasAPathVariableThatCanSatisfyIt() {
        List<String> broken = unbindable(mappings());
        assertThat(broken).as("""
            A path placeholder with no @PathVariable to satisfy it throws at REQUEST time, so the \
            route answers 500 for EVERY caller and no hermetic build can see it. Name the variable \
            explicitly — @PathVariable("runId") — rather than relying on the parameter's own name. \
            Found: %s""".formatted(broken)).isEmpty();
    }

    @Test
    void noTwoHandlersClaimTheSameVerbAndPath() {
        List<String> ambiguous = ambiguous(mappings());
        assertThat(ambiguous).as("""
            Two handlers claiming one (verb, path) makes Spring refuse to start with "Ambiguous \
            mapping" — and the suite boots no context, so the build passes and the DEPLOYMENT \
            fails. F2-247 is the same class one noun over (two beans with the same simple name). \
            Found: %s""".formatted(ambiguous)).isEmpty();
    }

    @Test
    void theBuildCompilesWithParameterNamesSoTheFirstLegIsSound() {
        // The first leg reads a parameter's own name when @PathVariable declares none. That is only
        // available because spring-boot-starter-parent compiles with -parameters. If that were ever
        // dropped, `supplies` would lose those names and the first leg would start reporting the
        // whole population — it fails LOUDLY rather than quietly, but this leg names the cause.
        long unnamed = mappings().stream()
            .filter(m -> !m.needs().isEmpty() && !m.catchAll())
            .filter(m -> m.needs().size() > m.supplies().size())
            .count();
        assertThat(unnamed).as(
            "if this is non-zero while the first leg is green, the two disagree and one of them is "
            + "measuring something other than what it claims").isZero();

        boolean anyNamePresent = shippedClasses().stream()
            .filter(c -> AnnotatedElementUtils.findMergedAnnotation(c, RestController.class) != null)
            .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
            .filter(m -> AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class) != null)
            .flatMap(m -> Arrays.stream(m.getParameters()))
            .anyMatch(Parameter::isNamePresent);
        assertThat(anyNamePresent).as(
            "no controller handler parameter carries a compiled name, so -parameters is off — "
            + "every @PathVariable without an explicit value is unbindable and the first leg's "
            + "reliance on parameter names is unsound").isTrue();
    }

    @Test
    void theDetectorsFireOnTheShapesTheyClaimToCatch() {
        // MUST FLAG: the recorded mismatch — a `{runId}` path bound by a parameter named `id`.
        // That is CLAUDE.md's own @PreAuthorize example, read on the binding side.
        Mapping mismatch = new Mapping("get", "/api/v1/x/{runId}", "Fixture#byRun",
            Set.of("runId"), Set.of("id"), false);
        assertThat(unbindable(List.of(mismatch)))
            .as("the placeholder detector no longer sees the shape it exists for").hasSize(1);

        // MUST NOT FLAG, four ways — a detector that fires on any of these fails on correct code,
        // which trains the next reader to edit the assertion rather than the code (F2-214).
        Mapping byName = new Mapping("get", "/api/v1/x/{runId}", "Fixture#ok",
            Set.of("runId"), Set.of("runId"), false);
        Mapping explicit = new Mapping("get", "/api/v1/x/{runId}", "Fixture#explicit",
            Set.of("runId"), Set.of("runId", "unused"), false);
        Mapping catchAll = new Mapping("get", "/api/v1/x/{runId}", "Fixture#map",
            Set.of("runId"), Set.of(), true);
        Mapping noPlaceholder = new Mapping("get", "/api/v1/x", "Fixture#list",
            Set.of(), Set.of(), false);
        assertThat(unbindable(List.of(byName, explicit, catchAll, noPlaceholder))).as("""
            A @PathVariable Map takes every placeholder by name, and a spare supplied name is \
            harmless — flagging either would make this gate fail on working routes.""").isEmpty();

        // MUST FLAG: two owners on one key. MUST NOT: one owner reached twice (the same method
        // resolved through two class-level base paths is not a collision).
        Mapping a = new Mapping("get", "/api/v1/dup", "OneController#m", Set.of(), Set.of(), false);
        Mapping b = new Mapping("get", "/api/v1/dup", "TwoController#m", Set.of(), Set.of(), false);
        assertThat(ambiguous(List.of(a, b))).as("the collision detector stopped matching").hasSize(1);
        assertThat(ambiguous(List.of(a, a))).as(
            "one owner listed twice is not an ambiguous mapping — Spring only refuses when two "
            + "different handlers claim the key").isEmpty();
        assertThat(ambiguous(List.of(a, new Mapping("post", "/api/v1/dup", "TwoController#m",
            Set.of(), Set.of(), false)))).as(
            "the same path under a different verb is not a collision").isEmpty();
    }

    @Test
    void theCorpusWasActuallyRead() {
        // A hard zero over a collapsed corpus is the most reassuring possible artefact (F2-205), and
        // F2-438's floor failed on a correct tree because it came from arithmetic rather than a
        // measurement — so the denominator is DERIVED here.
        long onDisk = topLevelClassFiles();
        assertThat(onDisk).as("the class-file walk found almost nothing").isGreaterThan(1_500);
        assertThat((long) shippedClasses().size()).as(
            "every top-level class file must resolve — an unloadable class leaves the population "
            + "smaller than the tree, and a smaller population reads exactly like a cleaner tree")
            .isEqualTo(onDisk);

        List<Mapping> ms = mappings();
        long controllers = shippedClasses().stream()
            .filter(c -> AnnotatedElementUtils.findMergedAnnotation(c, RestController.class) != null
                      || AnnotatedElementUtils.findMergedAnnotation(c, Controller.class) != null)
            .count();
        long withPlaceholder = ms.stream().filter(m -> !m.needs().isEmpty()).count();
        assertThat(controllers).as("controller scan collapsed").isGreaterThan(200);
        assertThat(ms.size()).as("mapping scan collapsed").isGreaterThan(2_000);
        assertThat(withPlaceholder).as("""
            Placeholder-bearing mappings are the population the first leg measures over; if this \
            collapses, that hard zero means nothing. Measured 2026-09-02: 1,352 of 2,253 mappings \
            across 254 controllers.""").isGreaterThan(1_000);
    }
}
