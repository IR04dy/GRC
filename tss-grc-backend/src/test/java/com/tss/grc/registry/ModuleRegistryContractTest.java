package com.tss.grc.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinTable;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import com.tss.grc.module.platform.licensing.SkuRegistry;

/**
 * F2-01 — the module registry, enforced.
 *
 * <p>This platform's own documents have simultaneously claimed <b>24</b>, <b>57</b>, <b>58</b> and
 * <b>59</b> backend module packages. That is not a documentation nit: every cross-cutting programme
 * — the test bar, the RBAC catalogue, the boundary ratchet, i18n coverage, migration ownership —
 * was sized against whichever number its author had read, and the 24 figure silently scoped 34
 * packages out of all of them. {@code module-registry.yaml} is the one enumeration, and this class
 * is the thing that fails when the enumeration, the packages on disk, {@link SkuRegistry} or the
 * authoritative documents disagree.
 *
 * <h3>Why the registry does not re-declare the SKU homing</h3>
 * ADR-018 homed all 59 packages, and {@link SkuRegistry} already holds that map in <em>main</em>
 * code because three consumers need it. Copying it into the YAML would create a second authority
 * that can drift from the first — <b>RC-A</b>, the root cause behind the largest cluster of
 * findings on this programme. So the YAML states the homing and
 * {@link #theHomingAgreesWithSkuRegistryInBothDirections()} asserts the two are identical in both
 * directions. Set containment has two directions and checking one is how ADR-017 D-10c was got
 * wrong.
 *
 * <h3>⚠ The measured negative result: a prose count-gate is not implementable</h3>
 * §3's row asks CI to fail on "any document stating a contradicting module count". That was
 * attempted and <b>abandoned on measurement</b>, which is recorded here so the next reader does not
 * "finish" it badly. A regex for a count near the word <em>module</em>/<em>package</em> matches
 * <b>138</b> lines; tightened to unambiguous-total forms it still matches <b>69</b> in 26 files —
 * and almost all of them are <em>correct sentences</em>:
 * <ul>
 *   <li>"<b>19 packages</b> / 20,503 LoC have zero tests" — a subset, and true</li>
 *   <li>"Catalogue permissions for <b>all 21 modules</b>" — a subset, and true</li>
 *   <li>"a real request engine adopted across <b>all 14 modules</b>" — a different total, and true</li>
 *   <li>"Replace current 8 nav items with <b>all 30 modules</b>" — a 2025 sprint doc, historically true</li>
 * </ul>
 * Free prose does not distinguish "the platform has N packages" from "N packages have no tests",
 * and a gate with 69 false positives is disabled within a day — §1c's own rule that <i>a ratchet
 * nobody runs is a comment</i>. What <em>is</em> enforceable is the count where it is machine-
 * readable (this registry, the directory listing, {@code SkuRegistry}) plus the two documents that
 * state it in a fixed, parseable sentence — see
 * {@link #theTwoAuthoritativeDocumentCountStatementsAgreeWithTheRegistry()}. Prose elsewhere is
 * governed by review, not by a pattern pretending to be a gate.
 *
 * <h3>Measured by reflection, never by grep</h3>
 * {@code SchemaContractRatchetTest.mappedTables()} records why: source regexes have reported this
 * repository's entity count as 418, 417 and 413, because {@code @EntityListeners},
 * {@code @EntityGraph} and Javadoc prose all look like entities to a pattern. A Python pass written
 * while authoring this registry reported 423 and silently missed multi-entity files (13 of
 * {@code data_officer}'s entities share one), {@code @JoinTable} and {@code @CollectionTable}.
 * Three separate regex errors were made and caught in one sitting on this slice alone.
 */
class ModuleRegistryContractTest {

    private static final String REGISTRY = "module-registry.yaml";
    private static final String MODULE_ROOT = "com.tss.grc.module";

    /**
     * {@code spring.jpa.properties.hibernate.default_schema} in {@code application.yml}. A
     * {@code @Table} that omits {@code schema=} resolves to this at runtime — four entities do
     * ({@code file_metadata}, {@code persistence_row}, {@code sso_provider_config},
     * {@code nafath_transaction}), and treating them as schema-less rather than resolving the
     * default is what once made {@code SchemaContractRatchetTest} blind to its own subject.
     */
    private static final String DEFAULT_SCHEMA = "platform";

    /** Floors, so a scan that silently found nothing cannot read as a clean pass. */
    private static final int MIN_PACKAGES = 55;
    private static final int MIN_ENTITY_CLASSES = 400;

    /** The blueprint's workspace ids. 13 exists because §6d caught that a 12-workspace nav would
     *  have re-filed Change Enablement under "Governance & Administration", reversing CE-0. */
    private static final Set<Integer> WORKSPACE_IDS =
        Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    void everyPackageOnDiskHasExactlyOneRegistryEntryAndViceVersa() throws IOException {
        Set<String> disk = packagesOnDisk();
        Set<String> registry = new TreeSet<>(modules().keySet());

        assertThat(disk).as(
            "Found %d module packages on disk. A near-empty scan would turn every check in this "
            + "class green while verifying nothing.", disk.size())
            .hasSizeGreaterThanOrEqualTo(MIN_PACKAGES);

        assertThat(registry).as(
            "%s must enumerate every package under %s exactly once.%n"
            + "  on disk but NOT in the registry : %s%n"
            + "  in the registry but NOT on disk : %s%n"
            + "A new package needs three things: an entry here, a homing in SkuRegistry, and a "
            + "workspace decision (or an explicit note that there is none). Seven packages once "
            + "became unsold revenue by being quietly filed under Base.",
            REGISTRY, MODULE_ROOT, minus(disk, registry), minus(registry, disk))
            .isEqualTo(disk);
    }

    @Test
    void theHomingAgreesWithSkuRegistryInBothDirections() throws IOException {
        Map<String, Map<String, Object>> mods = modules();

        Map<String, String> fromYaml = new TreeMap<>();
        Map<String, String> fromCode = new TreeMap<>();

        for (var e : mods.entrySet()) {
            String pkg = e.getKey();
            Map<String, Object> row = e.getValue();
            String homing = str(row.get("homing"));
            String declared = switch (homing) {
                case "sku" -> "sku:" + str(row.get("sku"));
                case "base" -> "base";
                case "unassigned" -> "unassigned";
                default -> "??" + homing;
            };
            fromYaml.put(pkg, declared);

            String sku = SkuRegistry.PACKAGE_TO_SKU.get(pkg);
            String actual = sku != null ? "sku:" + sku
                : SkuRegistry.BASE_PACKAGES.contains(pkg) ? "base"
                : SkuRegistry.UNASSIGNED_PACKAGES.contains(pkg) ? "unassigned"
                : "NOT-HOMED";
            fromCode.put(pkg, actual);
        }

        assertThat(fromYaml).as(
            "%s and SkuRegistry must agree on every package's homing, in BOTH directions — the "
            + "registry must never become a second authority that drifts from the code that "
            + "enforces entitlement (RC-A). Differences:%n%s",
            REGISTRY, diff(fromYaml, fromCode))
            .isEqualTo(fromCode);

        // And the code's own view must be total: nothing homed in code may be missing from the YAML.
        Set<String> homedInCode = new TreeSet<>(SkuRegistry.PACKAGE_TO_SKU.keySet());
        homedInCode.addAll(SkuRegistry.BASE_PACKAGES);
        homedInCode.addAll(SkuRegistry.UNASSIGNED_PACKAGES);
        assertThat(new TreeSet<>(mods.keySet())).as(
            "SkuRegistry homes packages the registry does not list: %s",
            minus(homedInCode, mods.keySet()))
            .containsAll(homedInCode);
    }

    @Test
    void everySchemaAnEntityNamesIsDeclaredByItsOwnPackage() throws IOException {
        Map<String, Map<String, Object>> mods = modules();
        Map<String, Set<String>> measured = measuredSchemasByPackage();

        List<String> problems = new ArrayList<>();
        for (var e : measured.entrySet()) {
            String pkg = e.getKey();
            Map<String, Object> row = mods.get(pkg);
            if (row == null) {
                problems.add(pkg + ": maps entities but has no registry entry");
                continue;
            }
            Set<String> declared = new TreeSet<>(strings(row.get("schemas")));
            Set<String> undeclared = minus(e.getValue(), declared);
            if (!undeclared.isEmpty()) {
                problems.add(pkg + ": entities map into " + undeclared
                    + " which the registry does not declare (declared: " + declared + ")");
            }
        }
        // The other direction: a declared schema no entity uses is stale bookkeeping.
        for (var e : mods.entrySet()) {
            Set<String> declared = new TreeSet<>(strings(e.getValue().get("schemas")));
            Set<String> unused = minus(declared, measured.getOrDefault(e.getKey(), Set.of()));
            if (!unused.isEmpty()) {
                problems.add(e.getKey() + ": registry declares " + unused
                    + " but no entity in the package maps there");
            }
        }

        assertThat(problems).as(
            "Registry `schemas:` must exactly match the schemas the package's JPA mappings name — "
            + "@Table, @SecondaryTable, @JoinTable, @CollectionTable, with a bare @Table resolved "
            + "to '%s'.%n%n⚠ The enforceable property is CONTAINMENT PER PACKAGE, not one schema "
            + "per module. Co-location is deliberate and correct for eight packages: controlmap→"
            + "evidence, incident→cyber, knowledge→kb, workflow_submission→workflow, and "
            + "attachment/auth/governance/integrations→platform. F2-06a's row says \"every @Entity "
            + "schema == its registry schema\"; measured, that equality does not hold and should "
            + "not.%n%nProblems:%n  %s",
            DEFAULT_SCHEMA, String.join("\n  ", problems))
            .isEmpty();
    }

    @Test
    void everyWorkspaceIsFromTheBlueprintOrExplicitlyNullWithAStatedReason() throws IOException {
        List<String> problems = new ArrayList<>();
        for (var e : modules().entrySet()) {
            Object ws = e.getValue().get("workspace");
            boolean hasNote = e.getValue().get("workspace_note") != null;
            if (ws == null) {
                // Base/kernel packages have no screen of their own, so a bare null is fine there.
                // A SKU'd package with no workspace is a paid module with nowhere to render, which
                // Wave 4's generated navigation would turn into an absent screen — say why.
                if ("sku".equals(str(e.getValue().get("homing"))) && !hasNote) {
                    problems.add(e.getKey() + ": sold under a SKU with workspace: null and no "
                        + "workspace_note. Wave 4 generates navigation from this file, so a silent "
                        + "null is the difference between a screen and no screen for a paid module.");
                }
            } else if (!(ws instanceof Integer) || !WORKSPACE_IDS.contains(ws)) {
                problems.add(e.getKey() + ": workspace " + ws + " is not one of " + WORKSPACE_IDS);
            }
        }
        assertThat(problems).as("Workspace assignments must come from UX_REDESIGN_BLUEPRINT's "
            + "closed set, or be null with a stated reason:%n  %s",
            String.join("\n  ", problems)).isEmpty();
    }

    @Test
    void theTwoAuthoritativeDocumentCountStatementsAgreeWithTheRegistry() throws IOException {
        int n = modules().size();

        // CLAUDE.md is the operating-instruction file — the one that said 24 for months and
        // silently scoped 34 packages out of every programme. Its sentence has a fixed shape.
        assertDocCount(Paths.get("..", "CLAUDE.md"),
            Pattern.compile("Backend modules under `module/` — \\*\\*(\\d+) packages\\*\\*"), n);

        // MEASURED_BASELINE.md's census row is generated by baseline_census.py and gated by
        // MeasuredBaselineFreshnessTest; this pins the module row to the registry specifically.
        assertDocCount(Paths.get("..", "TSS-GRC-Platform-Bible", "Appendix", "MEASURED_BASELINE.md"),
            Pattern.compile("\\|\\s*Backend module packages\\s*\\|\\s*\\*\\*(\\d+)\\*\\*"), n);
    }

    @Test
    void theRegistryIsWellFormedAndTheScanCannotSilentlyFindNothing() throws IOException {
        Map<String, Object> root = load();
        assertThat(root.get("version")).as("registry needs a version").isEqualTo(1);

        Map<String, Map<String, Object>> mods = modules();
        for (var e : mods.entrySet()) {
            Map<String, Object> row = e.getValue();
            String homing = str(row.get("homing"));
            assertThat(homing).as("%s: homing must be sku|base|unassigned", e.getKey())
                .isIn("sku", "base", "unassigned");
            if ("sku".equals(homing)) {
                assertThat(str(row.get("sku"))).as("%s: homing sku needs a sku", e.getKey())
                    .isNotBlank();
            } else {
                assertThat(row.get("sku")).as("%s: only homing:sku may name a sku", e.getKey())
                    .isNull();
            }
            assertThat(row).as("%s: schemas must be present (use [] when there are no entities)",
                e.getKey()).containsKey("schemas");
            for (String s : strings(row.get("schemas"))) {
                assertThat(s).as("%s: schema names are lowercase in this database", e.getKey())
                    .isEqualTo(s.toLowerCase());
            }
        }

        // The carve-outs are entitlement units that are not packages; they must not collide with
        // one, or the same code would be sold twice.
        @SuppressWarnings("unchecked")
        Map<String, Object> carve = (Map<String, Object>) root.get("carve_outs");
        assertThat(carve.keySet()).as("carve_outs must match SkuRegistry.CARVE_OUTS")
            .isEqualTo(SkuRegistry.CARVE_OUTS.keySet());

        assertThat(measuredSchemasByPackage().values().stream().mapToInt(Set::size).sum())
            .as("Reflection found almost no schemas — the scan broke, and every schema check "
                + "above would be vacuously green.")
            .isGreaterThan(40);
        assertThat(entityClassCount()).as(
            "Reflection found %d @Entity classes; the platform has 430+. A collapsed scan makes "
            + "the schema check meaningless.", entityClassCount())
            .isGreaterThanOrEqualTo(MIN_ENTITY_CLASSES);
    }

    // ── evidence gathering ───────────────────────────────────────────────────

    private static void assertDocCount(Path relative, Pattern pattern, int expected)
            throws IOException {
        Path p = relative.toAbsolutePath().normalize();
        assertThat(Files.exists(p)).as("%s must exist to be checked", p).isTrue();
        String text = Files.readString(p, StandardCharsets.UTF_8);
        Matcher m = pattern.matcher(text);
        assertThat(m.find()).as(
            "Could not find the module-count sentence in %s using /%s/. Either the sentence was "
            + "reworded — in which case update this pattern, because a check that matches nothing "
            + "passes forever — or the statement was deleted.", p.getFileName(), pattern)
            .isTrue();
        assertThat(Integer.parseInt(m.group(1))).as(
            "%s states %s backend module packages; the registry enumerates %d. The registry is the "
            + "authority. This file's own history is the argument: it read 24 for months, which "
            + "scoped 34 packages out of the RBAC catalogue, the test bar and every remediation "
            + "plan.", p.getFileName(), m.group(1), expected)
            .isEqualTo(expected);
    }

    /** Directory listing, because a package with no compiled classes is still a package. */
    private static Set<String> packagesOnDisk() throws IOException {
        Path root = Paths.get("src", "main", "java", "com", "tss", "grc", "module")
            .toAbsolutePath().normalize();
        assertThat(Files.isDirectory(root)).as("module root %s must exist", root).isTrue();
        try (Stream<Path> s = Files.list(root)) {
            Set<String> out = new TreeSet<>();
            s.filter(Files::isDirectory).forEach(p -> out.add(p.getFileName().toString()));
            return out;
        }
    }

    /**
     * Package → every schema its JPA mappings name, by reflection over compiled annotations.
     * Mirrors {@code SchemaContractRatchetTest.mappedTables()} deliberately: same mechanism, same
     * default-schema resolution, so the two cannot disagree about what the mapping layer says.
     */
    private static Map<String, Set<String>> measuredSchemasByPackage() {
        Map<String, Set<String>> out = new TreeMap<>();
        forEachEntity((pkg, c) -> {
            Table t = c.getAnnotation(Table.class);
            if (t != null) {
                add(out, pkg, t.schema());
            }
            for (SecondaryTable st : c.getAnnotationsByType(SecondaryTable.class)) {
                add(out, pkg, st.schema());
            }
            for (Field f : c.getDeclaredFields()) {
                JoinTable jt = f.getAnnotation(JoinTable.class);
                if (jt != null) {
                    add(out, pkg, jt.schema());
                }
                CollectionTable ct = f.getAnnotation(CollectionTable.class);
                if (ct != null) {
                    add(out, pkg, ct.schema());
                }
            }
        });
        return out;
    }

    private static int entityClassCount() {
        int[] n = {0};
        forEachEntity((pkg, c) -> n[0]++);
        return n[0];
    }

    private interface EntityVisitor {
        void visit(String modulePackage, Class<?> type);
    }

    private static void forEachEntity(EntityVisitor v) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            String pkg = topLevelModulePackage(c.getPackageName());
            if (pkg != null) {
                v.visit(pkg, c);
            }
        }
    }

    private static String topLevelModulePackage(String packageName) {
        String prefix = MODULE_ROOT + ".";
        if (packageName == null || !packageName.startsWith(prefix)) {
            return null;
        }
        return packageName.substring(prefix.length()).split("\\.")[0];
    }

    private static void add(Map<String, Set<String>> out, String pkg, String schema) {
        String resolved = (schema == null || schema.isBlank()) ? DEFAULT_SCHEMA : schema;
        out.computeIfAbsent(pkg, k -> new TreeSet<>()).add(resolved);
    }

    // ── the registry file ────────────────────────────────────────────────────

    private static Map<String, Object> load() throws IOException {
        LoaderOptions opts = new LoaderOptions();
        // A duplicate package key would otherwise be silently overwritten, and the parity check
        // would still pass while one of the two entries was never read.
        opts.setAllowDuplicateKeys(false);
        try (InputStream in = ModuleRegistryContractTest.class.getClassLoader()
                .getResourceAsStream(REGISTRY)) {
            assertThat(in).as("%s must be on the classpath (src/main/resources)", REGISTRY)
                .isNotNull();
            Map<String, Object> root = new Yaml(opts).load(in);
            assertThat(root).as("%s parsed empty", REGISTRY).isNotNull();
            return root;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> modules() throws IOException {
        Object m = load().get("modules");
        assertThat(m).as("%s needs a `modules:` map", REGISTRY).isInstanceOf(Map.class);
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        ((Map<String, Object>) m).forEach((k, v) -> {
            assertThat(v).as("module %s must be a mapping", k).isInstanceOf(Map.class);
            out.put(k, (Map<String, Object>) v);
        });
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        if (o == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        ((List<Object>) o).forEach(x -> out.add(String.valueOf(x)));
        return out;
    }

    private static <T extends Comparable<T>> Set<T> minus(Set<T> a, Set<T> b) {
        Set<T> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static String diff(Map<String, String> yaml, Map<String, String> code) {
        StringBuilder sb = new StringBuilder();
        for (String k : new TreeSet<>(yaml.keySet())) {
            String a = yaml.get(k);
            String b = code.get(k);
            if (!java.util.Objects.equals(a, b)) {
                sb.append("    ").append(k).append(": yaml=").append(a)
                  .append(" code=").append(b).append('\n');
            }
        }
        return sb.length() == 0 ? "    (none)" : sb.toString();
    }
}
