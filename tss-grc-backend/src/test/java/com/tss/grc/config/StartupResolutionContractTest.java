package com.tss.grc.config;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-443 — two things that stop the application starting, and a hermetic build cannot see either.
 *
 * <h3>The class of defect</h3>
 *
 * {@link UniqueBeanNameContractTest} (F2-247, a duplicate bean name),
 * {@link RequestMappingBindabilityContractTest} (F2-439, an unsatisfiable path placeholder) and
 * {@link CommitPhasedEventListenerContractTest} (F2-442, a propagation Spring's
 * {@code RestrictedTransactionalEventListenerFactory} refuses) are three members of one class:
 * <b>fatal when the context refreshes, and invisible to {@code mvn -o clean package}</b>, because
 * nothing in the suite boots a Spring context and — deliberately — nothing should
 * ({@code CLAUDE.md}: "hermetic: needs no database and no secrets — keep it that way").
 *
 * <p>F2-442 is the measured cost of that blind spot: {@code BUILD SUCCESS} printed over a tree the
 * application could not start from, <b>twice</b>, and the only instrument that saw it was launching
 * the app by hand.
 *
 * <p>{@code CLAUDE.md} names a further member as explicitly ungated, in these words:
 * <blockquote>a new {@code @Query} fails at STARTUP if the entity name/property is wrong, and the
 * hermetic suite boots no context to catch it</blockquote>
 * F2-376 verified its own JPQL <em>by hand</em> against that hazard. This gate closes it, and the
 * {@code @Value} placeholder beside it.
 *
 * <h3>What this asserts</h3>
 *
 * <ol>
 *   <li>Every JPQL {@code @Query} names an entity Hibernate will accept — hard zero over 206
 *       queries, <b>no exemption map</b>, because a {@code FROM} naming a type no {@code @Entity}
 *       declares is never correct code (F2-214's test for whether a hard zero is safe).</li>
 *   <li>Every first-hop property path in those queries is a field its entity declares — hard zero,
 *       with the aliases this gate could not type <b>reported and asserted exactly</b> rather than
 *       dropped, because a hard zero over a population the instrument cannot see is the most
 *       reassuring possible artefact (F2-205).</li>
 *   <li>Every {@code @Value("${key}")} with no default names a property some packaged document
 *       declares. Without a default an unbound placeholder is an
 *       {@code IllegalArgumentException: Could not resolve placeholder} at startup.</li>
 * </ol>
 *
 * <h3>⚠ Reflection, never a source regex — and this slice measured the cost of getting that wrong</h3>
 *
 * {@code @Query} is {@code @Retention(RUNTIME)}, so reflection yields the exact value <b>and</b> the
 * {@code nativeQuery} flag. A source scan gets both wrong, and did: a lazily-captured annotation
 * body truncates at the first {@code )} in {@code COUNT(*)}, so {@code nativeQuery = true} falls
 * outside it and native SQL reads as JPQL; and taking the first class declared after {@code @Entity}
 * misses the <b>fourteen</b> nested {@code @Entity} classes in {@code data_officer/Entities.java}
 * (F2-256: a source file is not a type). Measured: that regex reported <b>31</b> unresolvable
 * {@code FROM} clauses and the honest answer is <b>0</b> — every one was the detector.
 *
 * <h3>⚠ Three JPQL shapes a first draft could not resolve, all legal</h3>
 *
 * <ul>
 *   <li>{@code UPDATE BcpPlanAcknowledgement a SET …} binds its alias with no {@code FROM} at all,
 *       so a {@code FROM}-only scan reports five of its property reads as unresolvable.</li>
 *   <li>{@code JOIN com.tss.grc.…entity.AuthorityDocumentCitation c} is a legal
 *       <b>fully-qualified</b> entity reference; refusing it leaves {@code c} unbound and then
 *       splits the package name into {@code com.tss}, {@code grc.module}, {@code platform.regulatory}
 *       and reads each as a property path.</li>
 *   <li>{@code JOIN e.children c} is a <em>path</em> join, which binds an alias whose type this gate
 *       deliberately does not resolve — recorded, so its property reads are not mistaken for
 *       unresolvable ones.</li>
 * </ul>
 *
 * <p>Only the <b>first hop</b> of a property path is checked ({@code a.b} in {@code a.b.c}), which
 * is the subset a field walk can settle without a metamodel. Stated rather than implied.
 *
 * <h3>⚙ Two sibling hazards measured in the same firing and NOT gated here</h3>
 *
 * A <b>cycle in constructor injection</b> ({@code BeanCurrentlyInCreationException}) measures
 * <b>0</b> over 594 {@code @RequiredArgsConstructor} classes — F2-385 hit one
 * ({@code ObligationCampaignService} ↔ {@code ObligationAttestationService}) and closed it by
 * extracting a component, so it demonstrably happens here. It is filed rather than gated because a
 * correct detector must first subtract every legal cycle-breaker — {@code @Lazy} on a parameter,
 * {@code ObjectProvider}/{@code ObjectFactory} injection, and setter injection — and a gate that
 * fails on correct code trains the next reader to edit the assertion rather than the code (F2-214).
 * Filing the measurement is the honest half.
 *
 * <p>DB-free and infrastructure-free: reflection over {@code target/classes} plus four config files
 * off disk.
 */
class StartupResolutionContractTest {

    private static final Path CLASSES = Path.of("target", "classes");
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /**
     * A query root: {@code FROM X a}, {@code UPDATE X a}, {@code DELETE FROM X a}, {@code JOIN X a}.
     * The name is captured <b>dotted</b>, so a fully-qualified entity reference and a path join
     * ({@code e.children}) both land here and are told apart by resolution rather than by shape.
     */
    private static final Pattern ROOT = Pattern.compile(
        "\\b(?:FROM|UPDATE|JOIN\\s+FETCH|JOIN)\\s+((?:[A-Za-z_]\\w*\\.)*[A-Za-z_]\\w*)"
            + "(?:\\s+(?:AS\\s+)?([A-Za-z_]\\w*))?",
        Pattern.CASE_INSENSITIVE);

    /**
     * A comma-continued root: {@code from ChangeLink l, ChangeRequest c} is a legal implicit join,
     * and the second root is preceded by no keyword at all.
     *
     * <p>⚠ Resolution decides whether a match IS a root, and that asymmetry is deliberate: a bare
     * comma also introduces a select item ({@code SELECT a.x, a.y}) and a function argument
     * ({@code COALESCE(a, b)}), so a comma-introduced name that does not resolve to an entity is
     * <b>ignored</b> rather than reported. Reporting it would fail the hard zero on correct code,
     * which is the one failure a gate must never have (F2-214). Only the FROM/UPDATE/JOIN branch
     * can report a failure, because there an unresolved unqualified name really is one.
     */
    private static final Pattern COMMA_ROOT = Pattern.compile(
        ",\\s*((?:[A-Za-z_]\\w*\\.)*[A-Za-z_]\\w*)(?:\\s+(?:AS\\s+)?([A-Za-z_]\\w*))?");

    /** A constructor expression's qualified type — blanked before the path scan for the same reason. */
    private static final Pattern NEW_TYPE = Pattern.compile(
        "\\bnew\\s+((?:[A-Za-z_]\\w*\\.)+[A-Za-z_]\\w*)", Pattern.CASE_INSENSITIVE);

    /** The first hop of a property path. Deliberately one hop — see the class javadoc. */
    private static final Pattern PATH = Pattern.compile("\\b([a-z]\\w*)\\.([a-zA-Z_]\\w*)");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(:[^}]*)?}");

    private static final Set<String> KEYWORDS = Set.of("select", "from", "where", "and", "or",
        "order", "group", "having", "join", "left", "inner", "outer", "fetch", "on", "by", "asc",
        "desc", "count", "sum", "min", "max", "avg", "distinct", "new", "as", "in", "not", "is",
        "null", "like", "between", "case", "when", "then", "else", "end", "coalesce", "trim",
        "lower", "upper", "size", "delete", "update", "set", "exists", "all", "any", "some");

    // ─────────────────────────── the corpus ───────────────────────────

    private static List<Class<?>> shippedClasses() {
        List<Path> files;
        try (Stream<Path> s = Files.walk(CLASSES)) {
            files = s.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + CLASSES.toAbsolutePath(), e);
        }
        List<Class<?>> out = new ArrayList<>();
        for (Path p : files) {
            String fqcn = CLASSES.relativize(p).toString()
                .replace(File.separatorChar, '.').replaceAll("\\.class$", "");
            try {
                out.add(ClassUtils.forName(fqcn, StartupResolutionContractTest.class.getClassLoader()));
            } catch (Throwable ignored) {
                // an optional-dependency reference that is genuinely absent — never a query holder
            }
        }
        return out;
    }

    /** Every name JPQL may use for an entity: the {@code @Entity(name)} override, else the simple name. */
    private static Map<String, Class<?>> entityNames(List<Class<?>> classes) {
        Map<String, Class<?>> byName = new LinkedHashMap<>();
        for (Class<?> c : classes) {
            Entity e = c.getAnnotation(Entity.class);
            if (e == null) continue;
            byName.put(e.name().isEmpty() ? c.getSimpleName() : e.name(), c);
        }
        return byName;
    }

    private record Jpql(String site, String query) { }

    private static List<Jpql> jpqlQueries(List<Class<?>> classes) {
        List<Jpql> out = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q == null || q.nativeQuery() || q.value().isBlank()) continue;
                out.add(new Jpql(c.getSimpleName() + "#" + m.getName(), q.value()));
            }
        }
        return out;
    }

    // ─────────────────────────── the detectors ───────────────────────────

    /** What a single query resolved to. Every field is reported; nothing is silently dropped. */
    record Resolution(List<String> unknownEntities,
                      List<String> unknownProperties,
                      List<String> untypedAliases) { }

    /**
     * Extracted so {@link #theDetectorsFireOnTheShapesTheyClaimToCatch()} can drive it over
     * synthetic input: on a clean tree every leg below asserts an empty list, and an empty list is
     * what a correct detector and a dead one both produce (F2-235).
     */
    static Resolution resolve(String site, String query, Map<String, Class<?>> byName) {
        List<String> unknownEntities = new ArrayList<>();
        List<String> unknownProperties = new ArrayList<>();
        List<String> untypedAliases = new ArrayList<>();

        Map<String, Class<?>> alias = new LinkedHashMap<>();
        Set<String> pathAlias = new LinkedHashSet<>();
        StringBuilder scannable = new StringBuilder(query);

        // A qualified type in a constructor expression is not a property path.
        blank(scannable, NEW_TYPE, 1);

        for (Matcher r = ROOT.matcher(query); r.find(); ) {
            String name = r.group(1);
            String bound = r.group(2);
            if (KEYWORDS.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (bound != null && KEYWORDS.contains(bound.toLowerCase(Locale.ROOT))) bound = null;

            Class<?> ent = resolveEntity(name, byName);
            if (ent != null) {
                if (bound != null) alias.put(bound, ent);
                // blank the type's own span so a qualified name is not read as a path
                blankSpan(scannable, r.start(1), r.end(1));
                continue;
            }
            int dot = name.indexOf('.');
            if (dot > 0 && !name.substring(0, dot).contains("$")) {
                // `e.children c` — a path join. The alias exists and its type is not resolved here.
                if (bound != null) pathAlias.add(bound);
                blankSpan(scannable, r.start(1), r.end(1));
                continue;
            }
            unknownEntities.add(site + "   " + r.group().trim());
        }

        for (Matcher r = COMMA_ROOT.matcher(query); r.find(); ) {
            Class<?> ent = resolveEntity(r.group(1), byName);
            if (ent == null) continue;                       // a select item or a function argument
            String bound = r.group(2);
            if (bound != null && !KEYWORDS.contains(bound.toLowerCase(Locale.ROOT))) {
                alias.put(bound, ent);
            }
            blankSpan(scannable, r.start(1), r.end(1));
        }

        for (Matcher p = PATH.matcher(scannable); p.find(); ) {
            String al = p.group(1), prop = p.group(2);
            if (KEYWORDS.contains(al.toLowerCase(Locale.ROOT))) continue;
            if (pathAlias.contains(al)) continue;
            Class<?> ent = alias.get(al);
            if (ent == null) { untypedAliases.add(site + "   " + al + "." + prop); continue; }
            if (!declaresField(ent, prop)) {
                unknownProperties.add(site + "   " + ent.getSimpleName() + "." + prop);
            }
        }
        return new Resolution(unknownEntities, unknownProperties, untypedAliases);
    }

    /** An unqualified name is a JPQL entity name; a qualified one is a class. Both are legal. */
    private static Class<?> resolveEntity(String name, Map<String, Class<?>> byName) {
        Class<?> direct = byName.get(name);
        if (direct != null) return direct;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        Class<?> tail = byName.get(name.substring(dot + 1));
        return tail != null && tail.getName().equals(name) ? tail : null;
    }

    private static boolean declaresField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getName().equals(name)) return true;
            }
        }
        return false;
    }

    private static void blank(StringBuilder s, Pattern p, int group) {
        for (Matcher m = p.matcher(s.toString()); m.find(); ) blankSpan(s, m.start(group), m.end(group));
    }

    private static void blankSpan(StringBuilder s, int from, int to) {
        for (int i = from; i < to && i < s.length(); i++) {
            if (s.charAt(i) != '\n') s.setCharAt(i, ' ');
        }
    }

    /** One {@code ${…}} occurrence inside one {@code @Value}. */
    record Placeholder(String site, String key, boolean hasDefault) { }

    /**
     * Every {@code ${…}} the platform reads through {@code @Value}, whether or not it carries a
     * default — so the anti-vacuity floor can assert that the DETECTOR works rather than that the
     * defect exists.
     *
     * <p>⚠ That distinction cost an iteration. The first draft returned only the defaultless ones
     * and floored <em>that</em> at {@code > 20}; measured, <b>every</b> {@code @Value} in the
     * platform carries a default, so the honest answer is <b>0</b> — and the floor then failed on a
     * correct tree while claiming the corpus had not been read. F2-219's rule arriving from the
     * inside: <b>a floor asserts the instrument, never the finding.</b>
     */
    static List<Placeholder> placeholders(List<Class<?>> classes) {
        List<Placeholder> out = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Field f : c.getDeclaredFields()) {
                collect(out, c.getSimpleName() + "#" + f.getName(), f.getAnnotation(Value.class));
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                for (Parameter p : k.getParameters()) {
                    collect(out, c.getSimpleName() + "#<init>(" + p.getName() + ")",
                        p.getAnnotation(Value.class));
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                collect(out, c.getSimpleName() + "#" + m.getName(), m.getAnnotation(Value.class));
                for (Parameter p : m.getParameters()) {
                    collect(out, c.getSimpleName() + "#" + m.getName() + "(" + p.getName() + ")",
                        p.getAnnotation(Value.class));
                }
            }
        }
        return out;
    }

    private static void collect(List<Placeholder> out, String site, Value v) {
        if (v == null) return;
        for (Matcher m = PLACEHOLDER.matcher(v.value()); m.find(); ) {
            out.add(new Placeholder(site, m.group(1).trim(), m.group(2) != null));
        }
    }

    /**
     * Every property key the packaged documents declare, read with Spring's own YAML loader rather
     * than a hand-rolled parser — the loader flattens multi-document files and list indices exactly
     * as the {@code Environment} will.
     */
    static Set<String> declaredProperties() throws IOException {
        Set<String> keys = new TreeSet<>();
        YamlPropertySourceLoader yaml = new YamlPropertySourceLoader();
        List<Path> files;
        try (Stream<Path> s = Files.walk(RESOURCES, 1)) {
            files = s.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".yml") || n.endsWith(".yaml") || n.endsWith(".properties");
                }).sorted().toList();
        }
        for (Path p : files) {
            String n = p.getFileName().toString();
            if (n.endsWith(".properties")) {
                for (String line : Files.readAllLines(p)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
                    keys.add(t.substring(0, t.indexOf('=')).trim());
                }
                continue;
            }
            for (PropertySource<?> src : yaml.load(n, new FileSystemResource(p.toFile()))) {
                if (src instanceof EnumerablePropertySource<?> e) keys.addAll(List.of(e.getPropertyNames()));
            }
        }
        return keys;
    }

    // ─────────────────────────── the legs ───────────────────────────

    /**
     * The placeholders that would throw at startup: no default, and no property source declares the
     * key. Extracted so {@link #theDetectorsFireOnTheShapesTheyClaimToCatch()} can drive it over a
     * synthetic class — on this tree the answer is 0, and 0 is what a correct detector and a dead
     * one both produce (F2-235).
     */
    static List<String> unresolvable(List<Placeholder> found, Set<String> declared) {
        List<String> missing = new ArrayList<>();
        for (Placeholder p : found) {
            if (!p.hasDefault() && !declared.contains(p.key())) {
                missing.add(p.site() + "   ${" + p.key() + "}");
            }
        }
        return missing.stream().distinct().sorted().toList();
    }

    @Test
    void everyJpqlQueryNamesAnEntityHibernateWillAccept() {
        List<Class<?>> classes = shippedClasses();
        Map<String, Class<?>> byName = entityNames(classes);
        List<String> unknown = new ArrayList<>();
        for (Jpql q : jpqlQueries(classes)) {
            unknown.addAll(resolve(q.site(), q.query(), byName).unknownEntities());
        }
        assertThat(unknown)
            .as("""
                A JPQL @Query naming a type no @Entity declares is a STARTUP FAILURE, and
                `mvn -o clean package` cannot see it: nothing in the suite boots a context.
                Hard zero with no exemptions — a FROM naming a nonexistent entity is never
                correct code. Fix the query; do not add an allow-list.""")
            .isEmpty();
    }

    @Test
    void everyJpqlPropertyPathIsAFieldItsEntityDeclares() {
        List<Class<?>> classes = shippedClasses();
        Map<String, Class<?>> byName = entityNames(classes);
        List<String> unknown = new ArrayList<>();
        List<String> untyped = new ArrayList<>();
        for (Jpql q : jpqlQueries(classes)) {
            Resolution r = resolve(q.site(), q.query(), byName);
            unknown.addAll(r.unknownProperties());
            untyped.addAll(r.untypedAliases());
        }
        assertThat(unknown)
            .as("""
                A JPQL property path no entity declares is a STARTUP FAILURE. Only the FIRST hop
                is checked (`a.b` in `a.b.c`) — the subset a field walk can settle without a
                metamodel.""")
            .isEmpty();
        assertThat(untyped.stream().distinct().toList())
            .as("""
                Aliases this gate could not type. Asserted EXACTLY rather than dropped: a hard
                zero over a population the instrument cannot see is the most reassuring possible
                artefact (F2-205). If this list grows, the new shape is a JPQL form the ROOT
                pattern does not bind — read it before widening anything.""")
            .isEmpty();
    }

    @Test
    void everyValuePlaceholderWithNoDefaultIsDeclaredBySomePackagedDocument() throws IOException {
        Set<String> declared = declaredProperties();
        List<String> missing = unresolvable(placeholders(shippedClasses()), declared);
        assertThat(missing)
            .as("""
                `@Value("${key}")` with no default and no property anywhere is
                `IllegalArgumentException: Could not resolve placeholder` at startup — in EVERY
                profile, which is why this claim needs no profile model. Give it a default, or
                declare the key.

                ⚠ Declaring it only in `external.properties.template` is not enough: that file is
                not on the classpath, so dev would still fail to start.""")
            .isEmpty();
    }

    @Test
    void theDetectorsFireOnTheShapesTheyClaimToCatch() {
        Map<String, Class<?>> byName = entityNames(shippedClasses());
        String real = byName.keySet().stream().findFirst().orElseThrow();
        Class<?> realClass = byName.get(real);

        // ── must flag ──
        assertThat(resolve("t", "SELECT e FROM NoSuchEntityAtAll e", byName).unknownEntities())
            .as("a FROM naming no entity must be reported").hasSize(1);
        assertThat(resolve("t", "SELECT e FROM " + real + " e WHERE e.thisFieldDoesNotExist = 1", byName)
            .unknownProperties())
            .as("a property the entity does not declare must be reported").hasSize(1);
        assertThat(resolve("t", "SELECT x.id FROM " + real + " e", byName).untypedAliases())
            .as("a path on an alias nothing bound must be reported, never dropped").hasSize(1);

        // ── must NOT flag: the three legal shapes a first draft could not resolve ──
        assertThat(resolve("t", "UPDATE " + real + " a SET a.id = 1 WHERE a.id = 2", byName))
            .as("UPDATE binds its alias with no FROM at all")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.unknownProperties()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty());
        assertThat(resolve("t", "SELECT c.id FROM " + real + " m JOIN " + realClass.getName() + " c ON c.id = m.id", byName))
            .as("a fully-qualified entity reference is legal, and its package is not a property path")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.unknownProperties()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty());
        assertThat(resolve("t", "SELECT c.anything FROM " + real + " e JOIN e.someChildren c", byName))
            .as("a path join binds an alias this gate deliberately does not type")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.unknownProperties()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty());
        assertThat(resolve("t", "SELECT new java.util.ArrayList(e.id) FROM " + real + " e", byName)
            .untypedAliases())
            .as("a constructor expression's qualified type is not a property path").isEmpty();
        assertThat(resolve("t", "DELETE FROM " + real + " a WHERE a.id = :id", byName).unknownEntities())
            .as("DELETE FROM binds its alias through the FROM branch").isEmpty();
        assertThat(resolve("t", "select l, c from " + real + " l, " + real + " c where c.id = l.id", byName))
            .as("a comma-continued root is a legal implicit join and binds its alias")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty());
        assertThat(resolve("t", "SELECT e.id, e.titleEn FROM " + real + " e WHERE e.id IN (:a, :b)", byName)
            .unknownEntities())
            .as("""
                a comma introducing a select item or a parameter must NOT be read as a root —
                reporting it would fail the hard zero on correct code, which is the one failure a
                gate must never have (F2-214)""")
            .isEmpty();

        // ── the placeholder detector, over a class the corpus can never contain ──
        // The corpus walk is rooted at target/classes and this fixture lives in target/test-classes,
        // so it is excluded STRUCTURALLY rather than by a name ending in `Test` (F2-301).
        List<Placeholder> synthetic = placeholders(List.of(SyntheticValueHolder.class));
        assertThat(synthetic).as("the @Value detector reads a field annotation").hasSize(2);
        assertThat(unresolvable(synthetic, Set.of("f2443.declared.key")))
            .as("""
                A defaultless placeholder nothing declares must be reported, and one WITH a default
                must not. On the real tree the answer is 0 because every @Value in the platform
                carries a default — and 0 is what a correct detector and a dead one both
                produce (F2-235), so this is the only leg that proves it fires.""")
            .containsExactly("SyntheticValueHolder#undeclared   ${f2443.no.such.property}");
    }

    /** Fixture for the placeholder self-test. Never in the corpus — see the leg above. */
    static final class SyntheticValueHolder {
        @Value("${f2443.no.such.property}")
        String undeclared;
        @Value("${f2443.declared.key}")
        String declared;
    }

    @Test
    void theCorpusWasActuallyRead() throws IOException {
        List<Class<?>> classes = shippedClasses();
        long topLevel = classes.stream().filter(c -> !c.getName().contains("$")).count();

        // Derived, never arithmetic: F2-438 asserted `> 3_000` top-level classes from a guess and
        // failed on a correct tree, where the honest figure is under 2,000.
        assertThat(topLevel)
            .as("the classpath walk resolved the shipped tree")
            .isGreaterThan(1_500L);
        assertThat(entityNames(classes).size())
            .as("""
                @Entity names, INCLUDING the fourteen nested classes in data_officer/Entities.java
                that a source regex misses (F2-256: a source file is not a type). The wrong answer
                if `getAnnotation` stops resolving is 0.""")
            .isGreaterThan(300);
        assertThat(jpqlQueries(classes).size())
            .as("""
                JPQL @Query sites. The wrong answer if `nativeQuery()` inverts is the whole 253,
                and if the annotation stops resolving it is 0 — so the floor sits above neither
                bound by accident (F2-215: a floor is only worth having above the largest WRONG
                answer).""")
            .isBetween(150, 240);
        assertThat(placeholders(classes).size())
            .as("""
                @Value placeholders the detector can SEE — with or without a default. Floored
                here rather than the defaultless subset, which is legitimately 0: flooring a
                defect population asserts the defect exists.""")
            .isGreaterThan(20);
        assertThat(declaredProperties())
            .as("property keys read by Spring's own YAML loader").hasSizeGreaterThan(300);
    }
}
