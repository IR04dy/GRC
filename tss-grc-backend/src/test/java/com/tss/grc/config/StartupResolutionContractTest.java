package com.tss.grc.config;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
 * F2-443 / F2-454 — four things that stop the application starting, and a hermetic build cannot
 * see any of them.
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
 *   <li><b>F2-454</b> — every {@code :name} a {@code @Query} binds is supplied by its method,
 *       under a {@code @Param} or as a compiled parameter name. Spring Data refuses the repository
 *       at bootstrap otherwise ("Name for parameter binding must not be null or empty"). Hard
 *       zero, no exemptions: {@code @Param} is always available.</li>
 *   <li><b>F2-454</b> — no {@code @Param} names a placeholder its query does not contain. Dead
 *       rather than fatal, and an F2-184 decoy: it answers "is this parameter bound?" with a yes
 *       that is not true. Safe as a hard zero only because this corpus has NO SpEL query.</li>
 *   <li><b>F2-454</b> — the shipped tree LOADED. Every leg above measures over
 *       {@code shippedClasses()}, which tolerates a load failure so one absent optional
 *       dependency cannot take the gate down; a class that fails to load instead leaves all six
 *       legs measuring a narrower tree with no symptom (F2-449).</li>
 * </ol>
 *
 * <h3>⚠ F2-454 — the fourth member, and why three legs are what hid it</h3>
 *
 * The list above had <b>three</b> entries and each was true, so nothing invited the question
 * "which other thing does Spring resolve at bootstrap?". Named-parameter binding is that thing:
 * the same annotation, the same failure moment, the same blindness in a hermetic build — and it
 * sat outside the gate built for this family. F2-215's two-of-N, in the gate that owns the family.
 *
 * <p>Measured over the shipped bytecode: <b>253</b> {@code @Query} methods, <b>224</b> binding by
 * name across <b>445</b> placeholders, <b>0</b> unbound and <b>0</b> dead {@code @Param}. A
 * latent-hazard closure, not a defect discovery — say which you have (F2-283). What it buys is
 * that the next one fails the build instead of the deployment.
 *
 * <p><b>45 of the 224</b> bind at least one placeholder ONLY by a compiled parameter name, so
 * their bootstrap depends on {@code -parameters}. That flag is asserted — by
 * {@link RequestMappingBindabilityContractTest}
 * {@code #theBuildCompilesWithParameterNamesSoTheFirstLegIsSound}, whose name says it protects
 * <em>that</em> gate's first leg. It protects these 45 repositories as well, and nothing said so:
 * a reader retiring the leg it names would take the platform's startup with it. The reliance is
 * asserted exactly here, so it is a measurement rather than an accident.
 *
 * <h3>⚠ A fully-qualified annotation defeats a substring scan — in BOTH directions</h3>
 *
 * F2-449 recorded that {@code PrivacyNoticeRepository} writes
 * {@code @org.springframework.data.jpa.repository.Query(} in full, so a source census keyed on
 * {@code "@Query("} under-reported by 5 of 14 — the quiet direction, which reads as a clean tree.
 * That same file writes {@code @Param} in full too, and F2-454's throwaway source locator tested
 * {@code "@Param" in decl}: it reported <b>49</b> methods relying on {@code -parameters} where
 * reflection says <b>45</b>, the four phantoms being
 * {@code PrivacyNoticeRepository#countByOrgAndLinkedRopa},
 * {@code #findByOrgAndLinkedRopa}, {@code ProcessingActivityRepository#findAtRiskCrossBorder} and
 * {@code ShariaNonComplianceEventRepository#sumSnciByStatus}. One spelling, two annotations, two
 * opposite error directions — and the reflective answer is authoritative here only because the
 * unloadable count is asserted zero.
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

    /**
     * The shipped tree, and <b>what could not be read of it</b>.
     *
     * <p>⚠ The second half exists because a reflective census is only as wide as its classpath
     * (F2-449), and every leg in this class measures over the first half. Load failures are
     * tolerated — so one absent optional dependency cannot take the whole gate down — and
     * {@link #theWholeShippedTreeLoadedSoEveryLegAboveMeasuredTheWholeTree()} asserts the
     * tolerance was never used, because a narrower corpus produces a narrower answer with no
     * symptom. Both halves come from ONE walk: a second walk in the leg would be a second copy of
     * a primitive (F2-190), free to disagree with the one the other legs read.
     */
    private record Corpus(List<Class<?>> loaded, List<String> unloadable) { }

    private static Corpus corpus() {
        List<Path> files;
        try (Stream<Path> s = Files.walk(CLASSES)) {
            files = s.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + CLASSES.toAbsolutePath(), e);
        }
        List<Class<?>> out = new ArrayList<>();
        List<String> unloadable = new ArrayList<>();
        for (Path p : files) {
            String fqcn = CLASSES.relativize(p).toString()
                .replace(File.separatorChar, '.').replaceAll("\\.class$", "");
            try {
                out.add(ClassUtils.forName(fqcn, StartupResolutionContractTest.class.getClassLoader()));
            } catch (Throwable t) {
                unloadable.add(fqcn + "  ->  " + t.getClass().getSimpleName());
            }
        }
        return new Corpus(out, unloadable);
    }

    private static List<Class<?>> shippedClasses() {
        return corpus().loaded();
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

    /**
      * What a single query resolved to. Every field is reported; nothing is silently dropped.
      *
      * <p>⚠ {@code checkedPaths} exists because the mutation harness found the gate could
      * degrade in silence. Reverting fully-qualified entity resolution made
      * {@code JOIN com.tss.…Citation c} fall through to the <em>path-join</em> branch, which
      * records the alias as untyped and skips its property reads — so the mutation
      * <b>SURVIVED</b>: no false report, and one query's properties quietly no longer checked.
      * An empty report is what a working resolution and a degraded one both produce (F2-235), so
      * the corpus leg floors this count and the negative is not the only claim.
      */
    record Resolution(List<String> unknownEntities,
                      List<String> unknownProperties,
                      List<String> untypedAliases,
                      int checkedPaths) { }

    /**
     * Extracted so {@link #theDetectorsFireOnTheShapesTheyClaimToCatch()} can drive it over
     * synthetic input: on a clean tree every leg below asserts an empty list, and an empty list is
     * what a correct detector and a dead one both produce (F2-235).
     */
    static Resolution resolve(String site, String query, Map<String, Class<?>> byName) {
        List<String> unknownEntities = new ArrayList<>();
        List<String> unknownProperties = new ArrayList<>();
        List<String> untypedAliases = new ArrayList<>();
        int checkedPaths = 0;

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
            checkedPaths++;
            if (!declaresField(ent, prop)) {
                unknownProperties.add(site + "   " + ent.getSimpleName() + "." + prop);
            }
        }
        return new Resolution(unknownEntities, unknownProperties, untypedAliases, checkedPaths);
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

    // ───────────── named-parameter binding: the fourth member (F2-454) ─────────────

    /**
     * A {@code :name} binding. The negative lookbehind excludes a {@code ::} cast and a colon
     * inside a word, so {@code '12:30'} and {@code x::int} cannot read as placeholders; SpEL
     * ({@code :#{…}}) cannot match either, because {@code #} is not an identifier start. Measured:
     * this corpus contains no SpEL query at all, which is why a dead {@code @Param} below is
     * genuinely dead rather than bound through an expression this pattern cannot see.
     */
    private static final Pattern NAMED_PARAM = Pattern.compile("(?<![:\\w]):([A-Za-z_]\\w*)");

    /** A SQL string literal, blanked first: a colon inside one is data, never a binding. */
    private static final Pattern SQL_STRING = Pattern.compile("'(?:[^']|'')*'");

    /**
     * One {@code @Query} and what it needs against what its method supplies.
     *
     * <p>{@code byAnnotation} is kept separately from {@code supplied} because the two answer
     * different questions: {@code supplied} decides whether the query can bind (a compiled
     * parameter name counts), while only an explicit {@code @Param} can be <em>dead</em> — a
     * compiled name that matches no placeholder is the ordinary case for {@code Pageable}.
     */
    record NamedBinding(String site, Set<String> needed, Set<String> supplied,
                        Set<String> byAnnotation, boolean nativeQuery) {

        List<String> unbound() {
            return needed.stream().filter(n -> !supplied.contains(n)).sorted().toList();
        }

        List<String> deadParams() {
            return byAnnotation.stream().filter(n -> !needed.contains(n)).sorted().toList();
        }

        /** True when at least one placeholder is bound ONLY by a compiled parameter name. */
        boolean reliesOnCompiledNames() {
            return needed.stream().anyMatch(n -> !byAnnotation.contains(n));
        }
    }

    /**
     * Extracted so {@link #theDetectorsFireOnTheShapesTheyClaimToCatch()} can drive it over
     * synthetic input: on a clean tree the leg below asserts an empty list, and an empty list is
     * what a correct detector and a dead one both produce (F2-235).
     */
    static Set<String> namedPlaceholders(String... queries) {
        Set<String> out = new LinkedHashSet<>();
        for (String q : queries) {
            if (q == null || q.isBlank()) continue;
            StringBuilder s = new StringBuilder(q);
            blank(s, SQL_STRING, 0);
            for (Matcher m = NAMED_PARAM.matcher(s.toString()); m.find(); ) out.add(m.group(1));
        }
        return out;
    }

    /**
     * The names this method can bind by. {@code @Param} WINS over the compiled name — Spring Data
     * reads the annotation first — so a parameter carrying {@code @Param("x")} supplies {@code x}
     * and NOT its own name.
     */
    static Set<String> suppliedNames(Method m) {
        Set<String> out = new LinkedHashSet<>();
        for (Parameter p : m.getParameters()) {
            Param ann = p.getAnnotation(Param.class);
            if (ann != null && !ann.value().isBlank()) out.add(ann.value());
            else if (p.isNamePresent()) out.add(p.getName());
        }
        return out;
    }

    static Set<String> paramAnnotationNames(Method m) {
        Set<String> out = new LinkedHashSet<>();
        for (Parameter p : m.getParameters()) {
            Param ann = p.getAnnotation(Param.class);
            if (ann != null && !ann.value().isBlank()) out.add(ann.value());
        }
        return out;
    }

    /**
     * Every {@code @Query} that binds by name — native ones included, because a native query's
     * {@code :name} is resolved by exactly the same machinery, and {@code countQuery} carries its
     * own placeholders bound from the same method.
     */
    static List<NamedBinding> namedBindings(List<Class<?>> classes) {
        List<NamedBinding> out = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                Query q = m.getAnnotation(Query.class);
                if (q == null) continue;
                Set<String> needed = namedPlaceholders(q.value(), q.countQuery());
                if (needed.isEmpty()) continue;
                out.add(new NamedBinding(c.getSimpleName() + "#" + m.getName(), needed,
                    suppliedNames(m), paramAnnotationNames(m), q.nativeQuery()));
            }
        }
        return out;
    }

    @Test
    void everyNamedQueryParameterIsSuppliedByItsMethod() {
        List<String> unbound = new ArrayList<>();
        for (NamedBinding b : namedBindings(shippedClasses())) {
            for (String n : b.unbound()) unbound.add(b.site() + "   :" + n);
        }
        assertThat(unbound)
            .as("""
                A `@Query` naming `:x` that its method supplies under no other name is a STARTUP
                FAILURE — Spring Data refuses the repository at bootstrap with "Name for parameter
                binding must not be null or empty", and `mvn -o clean package` cannot see it
                because nothing in the suite boots a context.

                Hard zero with no exemptions: `@Param("x")` is always available, so an unbindable
                placeholder is never correct code (F2-214's test for whether a hard zero is safe).
                Fix the query or the parameter; do not add an allow-list.""")
            .isEmpty();
    }

    @Test
    void noParamAnnotationNamesAPlaceholderItsQueryDoesNotContain() {
        List<String> dead = new ArrayList<>();
        for (NamedBinding b : namedBindings(shippedClasses())) {
            for (String n : b.deadParams()) dead.add(b.site() + "   @Param(\"" + n + "\")");
        }
        assertThat(dead)
            .as("""
                A `@Param("x")` whose query contains no `:x` binds nothing. Harmless at runtime
                and an F2-184 decoy in the source: it answers the question a reader was about to
                ask — "is this parameter bound?" — with a yes that is not true, and it is exactly
                how a renamed placeholder leaves a stale annotation behind.

                Safe as a hard zero only because this corpus has NO SpEL query: a `@Param` used
                only through `:#{#x}` would read as dead here, and that shape does not exist.""")
            .isEmpty();
    }

    @Test
    void theWholeShippedTreeLoadedSoEveryLegAboveMeasuredTheWholeTree() {
        assertThat(corpus().unloadable())
            .as("""
                F2-449's rule: a reflective census is only as wide as its CLASSPATH, so count what
                you could not load rather than reporting a zero you did not earn. Driven from a
                narrower classpath, that slice's census printed `0 methods, 0 LIKE occurrences`
                — a clean-looking zero produced by loading nothing.

                Every leg in this class measures over `shippedClasses()`, which TOLERATES a load
                failure so the other legs keep running. That tolerance is correct and it has to be
                auditable: a class that fails to load leaves all four legs measuring a narrower
                tree, with no symptom (F2-198's shape at a classpath).

                ⚠ The comment this replaces asserted such a class "is never a query holder". That
                is a CLAIM, and F2-454 measured it: it is true today — 0 classes fail to load — so
                it was true by luck rather than by construction. Asserted rather than believed.

                If this fires, an optional dependency the tree references is absent from the test
                classpath. Restore it; do not narrow the walk.""")
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
            .as("UPDATE binds its alias with no FROM at all, and its paths are CHECKED")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.unknownProperties()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty())
            .satisfies(r -> assertThat(r.checkedPaths()).isEqualTo(2));
        assertThat(resolve("t", "SELECT c.id FROM " + real + " m JOIN " + realClass.getName() + " c ON c.id = m.id", byName))
            .as("""
                A fully-qualified entity reference is legal; its package is not a property path;
                and its paths must be CHECKED.

                ⚠ The `checkedPaths` claim is the load-bearing one and was added because the
                mutation reverting that resolution SURVIVED without it: the qualified name then
                falls through to the path-join branch, which records the alias as untyped and
                SKIPS its properties — so every negative assertion above still passed while one
                query silently stopped being checked (F2-235).""")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.unknownProperties()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty())
            .satisfies(r -> assertThat(r.checkedPaths()).isEqualTo(3));
        assertThat(resolve("t", "SELECT c.anything FROM " + real + " e JOIN e.someChildren c", byName))
            .as("""
                A path join binds an alias this gate deliberately does not type — so zero paths
                are checked here, and that is the one shape where zero is the RIGHT answer. It is
                asserted so the two cases above cannot be satisfied by the same silent
                fall-through.""")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.unknownProperties()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty())
            .satisfies(r -> assertThat(r.checkedPaths()).isZero());
        assertThat(resolve("t", "SELECT new java.util.ArrayList(e.id) FROM " + real + " e", byName)
            .untypedAliases())
            .as("a constructor expression's qualified type is not a property path").isEmpty();
        assertThat(resolve("t", "DELETE FROM " + real + " a WHERE a.id = :id", byName).unknownEntities())
            .as("DELETE FROM binds its alias through the FROM branch").isEmpty();
        assertThat(resolve("t", "select l, c from " + real + " l, " + real + " c where c.id = l.id", byName))
            .as("a comma-continued root is a legal implicit join, binds its alias, and is CHECKED")
            .satisfies(r -> assertThat(r.unknownEntities()).isEmpty())
            .satisfies(r -> assertThat(r.untypedAliases()).isEmpty())
            .satisfies(r -> assertThat(r.checkedPaths()).isEqualTo(2));
        assertThat(resolve("t", "SELECT e.id, e.titleEn FROM " + real + " e WHERE e.id IN (:a, :b)", byName)
            .unknownEntities())
            .as("""
                a comma introducing a select item or a parameter must NOT be read as a root —
                reporting it would fail the hard zero on correct code, which is the one failure a
                gate must never have (F2-214)""")
            .isEmpty();

        // ── F2-454: the named-binding detector, over shapes it must and must not flag ──
        assertThat(namedPlaceholders("SELECT e FROM X e WHERE e.a = :one AND e.b = :two"))
            .as("named placeholders are found").containsExactlyInAnyOrder("one", "two");
        assertThat(namedPlaceholders("SELECT e FROM X e WHERE e.a = ?1 AND e.b = ?2"))
            .as("positional binding needs no name, so it must contribute NOTHING").isEmpty();
        assertThat(namedPlaceholders("SELECT e FROM X e WHERE e.note = 'see :notAPlaceholder'"))
            .as("""
                A colon inside a SQL string literal is DATA, and blanking literals first is what
                stops it inventing a binding and failing the hard zero on correct code — the one
                failure a gate must never have (F2-214).

                ⚠ This case reads `:notAPlaceholder` and NOT `'12:30'`, and the mutation harness
                is what forced that. With `'12:30'` the mutation removing the blanking SURVIVED,
                because `:30` cannot match a capture group requiring a letter or underscore
                first: the assertion was satisfied by the DIGIT rule while claiming to test the
                blanking. An assertion whose name claims more than it can see.""")
            .isEmpty();
        assertThat(namedPlaceholders("SELECT e FROM X e WHERE e.at = '12:30'"))
            .as("""
                and independently: a colon followed by a digit is not an identifier, so a time
                literal cannot become a binding even with the literal left intact. Kept as its own
                case so the two rules are separately visible.""")
            .isEmpty();
        assertThat(namedPlaceholders("SELECT CAST(e.a AS INT), e.b::text FROM X e"))
            .as("a `::` cast is not two placeholders — that is what the lookbehind buys").isEmpty();
        assertThat(namedPlaceholders("SELECT e FROM #{#entityName} e WHERE e.a = :#{#x}"))
            .as("""
                SpEL is not a name this detector can settle, and `#` is not an identifier start so
                it cannot match. Measured: this corpus contains NO SpEL query, which is the only
                reason the dead-@Param leg is safe as a hard zero — a @Param used solely through
                `:#{#x}` would read as dead.""")
            .isEmpty();
        assertThat(namedPlaceholders(null, "", "  "))
            .as("an absent countQuery is the ordinary case and must not throw").isEmpty();

        List<NamedBinding> synth = namedBindings(List.of(SyntheticQueryHolder.class));
        assertThat(synth).as("every fixture method binds by name").hasSize(5);
        assertThat(synth.stream().filter(b -> !b.unbound().isEmpty())
            .map(NamedBinding::site).toList())
            .as("""
                Exactly TWO fixture methods are unbindable, and the second is the interesting one:
                `:orgId` against a parameter named `organisationId`, and `:orgId` against a
                parameter named `orgId` that carries `@Param("nope")` — because the annotation
                WINS, that one is unbindable too.

                ⚠ A first draft of this leg claimed ONE and contradicted the assertion two blocks
                below it, which asserts that very method is unbound. The detector was right and
                the claim was wrong (F2-206, applied to an assertion rather than a mutation).

                The other three are shapes that must NOT be reported, asserted here rather than
                left to the corpus, where every leg's answer is an empty list either way
                (F2-235).""")
            .containsExactlyInAnyOrder("SyntheticQueryHolder#unbindable",
                "SyntheticQueryHolder#annotationWinsOverTheCompiledName");
        assertThat(synth.stream().filter(b -> !b.deadParams().isEmpty())
            .map(b -> b.site() + b.deadParams()).toList())
            .as("""
                `@Param` WINS over the compiled name: a parameter named `orgId` carrying
                `@Param("nope")` supplies `nope` and NOT `orgId`, so the query is unbound AND the
                annotation is dead. Pinned because reading it the other way round — compiled name
                first — makes the detector clear the exact site it exists to catch.""")
            .containsExactly("SyntheticQueryHolder#annotationWinsOverTheCompiledName[nope]");
        assertThat(synth.stream()
            .filter(b -> b.site().endsWith("#annotationWinsOverTheCompiledName"))
            .flatMap(b -> b.unbound().stream()).toList())
            .as("and that same method's query is therefore unbindable").containsExactly("orgId");
        assertThat(synth.stream().filter(b -> b.site().endsWith("#countQueryBindsToo"))
            .flatMap(b -> b.needed().stream()).toList())
            .as("""
                a `countQuery` carries its own placeholders, bound from the SAME method — so both
                halves are unioned. Six of the corpus's queries declare one; reading only `value()`
                leaves those bindings unchecked.""")
            .containsExactlyInAnyOrder("orgId", "onlyInTheCount");

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

    /**
     * Fixture for the named-binding self-test (F2-454). Never in the corpus: the walk is rooted at
     * {@code target/classes} and this compiles to {@code target/test-classes}, so it is excluded
     * STRUCTURALLY rather than by a name ending in {@code Test} (F2-301 — a name rule lets any
     * member escape by being renamed).
     */
    interface SyntheticQueryHolder {
        @Query("SELECT e FROM X e WHERE e.org = :orgId")
        Object unbindable(Long organisationId);

        @Query("SELECT e FROM X e WHERE e.org = :orgId")
        Object boundByAnnotation(@Param("orgId") Long whateverThisIsCalled);

        @Query("SELECT e FROM X e WHERE e.org = :orgId")
        Object boundByCompiledName(Long orgId);

        @Query("SELECT e FROM X e WHERE e.org = :orgId")
        Object annotationWinsOverTheCompiledName(@Param("nope") Long orgId);

        @Query(value = "SELECT e FROM X e WHERE e.org = :orgId",
               countQuery = "SELECT COUNT(e) FROM X e WHERE e.org = :orgId AND e.k = :onlyInTheCount")
        Object countQueryBindsToo(Long orgId, Long onlyInTheCount);
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

        // ── F2-454: the named-binding population, and the reliance inside it ──
        List<NamedBinding> bindings = namedBindings(classes);
        assertThat(bindings)
            .as("""
                @Query sites that bind by name — 224 of 253 measured, native ones INCLUDED,
                because a native query's `:name` is resolved by the same machinery. The wrong
                answer if NAMED_PARAM stops matching is 0, and 0 is what the two legs above then
                report as a clean tree (F2-235), so the floor is what makes their zeros mean
                anything.""")
            .hasSizeGreaterThan(180);
        assertThat(bindings.stream().filter(NamedBinding::nativeQuery).count())
            .as("""
                ⚠ NATIVE queries are in this population on purpose: a native `:name` is resolved
                by exactly the same machinery, so excluding them would leave that share
                unchecked. 43 of the 224 are native — MEASURED, because a first draft of this
                comment predicted the arithmetic and got it wrong (F2-195).

                Asserted separately because the site floor above cannot see it: dropping native
                queries takes the count from 224 to 181, which clears the floor of 180 BY ONE,
                so the floor reads that mutation as progress. The margin being one is the whole
                argument for this leg existing rather than for raising the floor.""")
            .isGreaterThan(20L);
        assertThat(bindings.stream().mapToInt(b -> b.needed().size()).sum())
            .as("""
                distinct placeholders across those sites — 445 measured. Floored separately from
                the site count because a pattern that matches the FIRST placeholder of each query
                and no others leaves the site count untouched.""")
            .isGreaterThan(380);

        long reliance = bindings.stream().filter(NamedBinding::reliesOnCompiledNames).count();
        long halfAnnotated = bindings.stream()
            .filter(b -> !b.byAnnotation().isEmpty() && b.reliesOnCompiledNames()).count();
        assertThat(reliance)
            .as("""
                ⚠ MEASURED, NOT A CEILING — the count of @Query methods binding at least one
                placeholder ONLY by its compiled parameter name. Those bootstrap only because
                `spring-boot-starter-parent` compiles with `-parameters`; without it Spring Data
                refuses the repository at startup, in these words: "For queries with named
                parameters you need to provide names for method parameters".

                That flag IS asserted — by `RequestMappingBindabilityContractTest`
                #theBuildCompilesWithParameterNamesSoTheFirstLegIsSound — whose name says it
                protects THAT gate's first leg. It protects these 45 repositories too, and
                nothing said so, so a reader retiring the leg it names would take the platform's
                startup with it (F2-215's two-of-N at a gate's own justification).

                Asserted EXACTLY rather than decrease-only: a new method joining this population
                is not a defect, and it should be visible rather than absorbed into a tolerance.""")
            .isEqualTo(45L);
        assertThat(halfAnnotated)
            .as("""
                Every method annotates ALL its bindings or NONE — measured 45 with none, 179 with
                all, 0 in between. Worth asserting because a HALF-annotated method is the shape
                that reads as covered: a reader who sees one `@Param` has no reason to check the
                parameter beside it.""")
            .isZero();

        Map<String, Class<?>> byName = entityNames(classes);
        int checked = 0;
        for (Jpql q : jpqlQueries(classes)) checked += resolve(q.site(), q.query(), byName).checkedPaths();
        assertThat(checked)
            .as("""
                Property paths actually CHECKED against an entity — the POSITIVE claim, without
                which this gate can degrade in silence. Every alias-binding branch (FROM, UPDATE,
                a fully-qualified reference, a comma-continued root) that stops resolving pushes
                its query's paths into the untyped set instead, and an empty report is what a
                working resolution and a dead one both produce (F2-235).

                ⚠ Measured 883. The first draft floored this at 900 from an ARITHMETIC guess of
                1050 and failed on a correct tree — F2-438's lesson in the very slice that quotes
                it. The largest WRONG answer is 0 (a broken walk), so 800 separates them
                (F2-215).

                ⚠ This floor is GROSS by construction: reverting fully-qualified resolution
                costs ONE query's two paths, 883 → 881, which no floor can distinguish from a
                new query being added. The assertion that catches a NARROW degradation is the
                per-shape `checkedPaths` claim in the self-test below — that mutation SURVIVED
                until it existed.""")
            .isGreaterThan(800);
    }
}
