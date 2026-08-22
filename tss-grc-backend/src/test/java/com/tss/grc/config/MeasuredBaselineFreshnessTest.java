package com.tss.grc.config;

import org.junit.jupiter.api.Assumptions;
import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-69 — the measurement document must be measured too.
 *
 * <h3>What happened</h3>
 * <p>{@code TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md} is the artefact that enforces this
 * project's standing rule: <i>no claim enters any document unless it is reproducible from this
 * baseline</i>. Every finding in the register cites a row of it. It was the one document in the
 * repository whose own numbers nothing re-measured, and in six days it accumulated:
 *
 * <ul>
 *   <li><b>staleness</b> — 304 migration files / {@code V314} on a tree holding 332 / {@code V344};</li>
 *   <li><b>a substring bug</b> — entities counted with {@code "@Entity" in src}, so
 *       {@code @EntityListeners}, {@code @EntityGraph} and Javadoc prose all counted (417 published,
 *       413 real). This is RC-20, the regex-over-Javadoc failure, <em>inside</em> the document whose
 *       rule is that nothing enters unmeasured;</li>
 *   <li><b>a units bug</b> — one {@code @Entity} per <em>file</em> is not one entity per file.
 *       {@code data_officer/entity/Entities.java} declares 14, so the census showed the platform's
 *       largest untested regulatory module as owning a single entity;</li>
 *   <li><b>three figures that were never reproducible at all</b> — established by exporting the very
 *       commit the document's header names and re-running the census against it. 17 of 20 figures
 *       reproduced to the digit; {@code @PreAuthorize} (2,268), write endpoints (1,151) and mock-file
 *       lines (3,650) reproduced under no counting rule tried;</li>
 *   <li><b>a load-bearing error</b> — "prod runs Flyway from baseline V312" when the deployed baseline
 *       is {@code V292}. The baseline decides which migrations can <em>never</em> run, which is the
 *       whole of RC-A.</li>
 * </ul>
 *
 * <h3>What this test gates, and what it deliberately does not</h3>
 * <p>Only figures that are <b>load-bearing</b> and <b>slow-moving</b>: package count, entity files and
 * classes, JPA schemas, migration count and head, permission modules, packages with no
 * {@code hasPermission}, frontend pages and routes, and the deployed Flyway baseline (checked
 * <em>arithmetically</em> — see {@link #theFlywayBaselineAndItsConsequenceAgree()}).
 *
 * <p>LoC, test counts and coupling edges move on almost every commit and are <b>reported but not
 * gated</b>. Gating them would make this the test everybody disables, which is the lesson F2-70 paid
 * for: a red build nobody can fix gets switched off, and then nothing is enforced at all.
 *
 * <p>Reading across the module boundary — into the Bible and into the frontend — is unusual and
 * deliberate, following {@code FrontendSkuVocabularyTest} and {@code AttachmentEntityTypeCoverageTest}:
 * the invariant genuinely spans the artefacts, and the alternative is the drift. Every read skips when
 * the file is absent, and every count carries an anti-vacuity floor so a skip or a broken scan cannot
 * be mistaken for a pass.
 *
 * <h3>How to fix a failure</h3>
 * <p>Do not edit the expected value to match the tree without looking. Run
 * {@code py TSS-GRC-Platform-Bible/Appendix/scripts/baseline_census.py}, then update <b>both</b> the
 * document row and the {@code PUBLISHED} dict at the top of that script in the same commit — the last
 * test here asserts those two never disagree.
 */
class MeasuredBaselineFreshnessTest {

    private static final Path DOC =
        Path.of("..", "TSS-GRC-Platform-Bible", "Appendix", "MEASURED_BASELINE.md");
    private static final Path CENSUS =
        Path.of("..", "TSS-GRC-Platform-Bible", "Appendix", "scripts", "baseline_census.py");
    private static final Path MODULES = Path.of("src", "main", "java", "com", "tss", "grc", "module");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MIGRATIONS =
        Path.of("src", "main", "resources", "db", "migration", "sqlserver");
    private static final Path FE_PAGES = Path.of("..", "tss-grc-ui-v2", "src", "pages");
    private static final Path FE_APP = Path.of("..", "tss-grc-ui-v2", "src", "App.tsx");

    /** {@code @Entity} with no identifier character after it — not {@code @EntityListeners}. */
    private static final Pattern ENTITY = Pattern.compile("@Entity(?![A-Za-z0-9_])");
    private static final Pattern TABLE_ANNOTATION = Pattern.compile("@Table\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern SCHEMA_ATTR = Pattern.compile("schema\\s*=\\s*\"([^\"]+)\"");
    // F2-263 — the pair extractor moved to GuardExpressions#permissionPairsIn (eleven copies,
    // four spellings). ⚠ Only the EXTRACTION moved: this class keeps its own character-scanner
    // stripComments, because the javadoc there records that the equivalent regex alternation
    // throws StackOverflowError on this codebase's larger files — the helper's stripper is the
    // regex form, so it is not a drop-in replacement here.
    private static final Pattern HAS_PERMISSION_BARE = Pattern.compile("hasPermission\\(");
    private static final Pattern MIGRATION_VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");
    private static final Pattern ROUTE = Pattern.compile("<Route[^>]*path=");

    /**
     * Remove comments, keep literals — a string may legitimately contain {@code @Entity}.
     *
     * <p>A single ordered scan, so a construct can never be entered from inside another. Order is the
     * whole point: strip block comments first and a {@code //} line containing a block-OPEN marker
     * swallows the file to the next CLOSE marker anywhere later, which is exactly the unsoundness
     * F2-68(c) found in the frontend scanner (23 files mangled, {@code App.tsx} 87,393 → 47,438 bytes).
     *
     * <p>⚠ Written as a character scanner rather than the equivalent regex alternation <b>because the
     * regex version threw {@code StackOverflowError}</b> on this codebase's larger files: Java's engine
     * recurses per iteration of a {@code [\s\S]*?} loop, so a block comment spanning a few thousand
     * characters is enough to exhaust the stack. The Python census can use the regex; the JVM cannot.
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;      // leave the newline for the main path
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i = Math.min(i + 2, n);
                continue;
            }
            if (c == '"' && src.startsWith("\"\"\"", i)) {        // text block
                out.append("\"\"\"");
                i += 3;
                while (i < n && !src.startsWith("\"\"\"", i)) {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        out.append(src, i, i + 2);
                        i += 2;
                        continue;
                    }
                    out.append(src.charAt(i++));
                }
                if (i < n) {
                    out.append("\"\"\"");
                    i += 3;
                }
                continue;
            }
            if (c == '"' || c == '\'') {                          // string / char literal
                out.append(c);
                i++;
                while (i < n && src.charAt(i) != c && src.charAt(i) != '\n') {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        out.append(src, i, i + 2);
                        i += 2;
                        continue;
                    }
                    out.append(src.charAt(i++));
                }
                if (i < n && src.charAt(i) == c) out.append(src.charAt(i++));
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + root.toAbsolutePath(), e);
        }
    }

    // ── the document ─────────────────────────────────────────────────────────────────────────────

    private static String doc() {
        Assumptions.assumeTrue(Files.isRegularFile(DOC),
            "the Bible is not checked out next to the backend — skipping the baseline freshness check");
        return read(DOC);
    }

    /** The table row whose label cell contains {@code needle}; fails loudly if the row moved. */
    private static String row(String needle) {
        for (String line : doc().split("\n")) {
            if (line.startsWith("|") && line.contains(needle)) return line;
        }
        throw new AssertionError(
            "MEASURED_BASELINE.md has no table row containing \"" + needle + "\". The row was renamed or "
          + "deleted — this test cannot silently stop checking a figure, so restore the row (or update "
          + "this needle deliberately, in the same commit).");
    }

    /**
     * The row's <b>value</b> cell — the first cell after the label. Deliberately not "everything after
     * the needle": §0 carries a 2026-07-25 column beside the measured one, and reading across the cell
     * boundary would let this test assert against last week's figure and pass.
     */
    private static String valueCell(String needle) {
        String[] cells = row(needle).split("\\|");
        assertThat(cells.length)
            .as("row \"%s\" has no value cell: %s", needle, row(needle))
            .isGreaterThan(2);
        return cells[2];
    }

    /**
     * The n-th number that appears inside a <b>bold span</b> of the row's value cell. Bold is the
     * convention this document uses for "the measured figure", and the surrounding prose is not always
     * outside it — {@code **16 of 58**} and {@code **280 of 332**} are single spans, so the number is
     * extracted from within the span rather than by requiring the whole span to be digits.
     */
    private static int docNumber(String needle, int occurrence) {
        String cell = valueCell(needle);
        List<Integer> found = new ArrayList<>();
        Matcher span = Pattern.compile("\\*\\*([^*]+)\\*\\*").matcher(cell);
        while (span.find()) {
            Matcher num = Pattern.compile("(\\d[\\d,]*)").matcher(span.group(1));
            if (num.find()) found.add(Integer.parseInt(num.group(1).replace(",", "")));
        }
        assertThat(found.size())
            .as("row \"%s\" has fewer than %d bolded numbers in its value cell: %s",
                needle, occurrence, cell)
            .isGreaterThanOrEqualTo(occurrence);
        return found.get(occurrence - 1);
    }

    private static int docNumber(String needle) {
        return docNumber(needle, 1);
    }

    // ── the gated figures ────────────────────────────────────────────────────────────────────────

    @Test
    void modulePackageCountMatches() {
        try (Stream<Path> s = Files.list(MODULES)) {
            long packages = s.filter(Files::isDirectory).count();
            assertThat(packages).as("anti-vacuity: the module tree was not found").isGreaterThan(40);
            assertThat((int) packages)
                .as("MEASURED_BASELINE.md §0 states the backend module-package count, and it is the "
                  + "number every scope, licensing and test-bar statement is sized against — it was "
                  + "documented as 24 for months while the tree held 58. Re-measure with "
                  + "baseline_census.py and update §0 plus the script's PUBLISHED dict.")
                .isEqualTo(docNumber("Backend module packages"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void entityFilesAndEntityClassesBothMatch() {
        int files = 0;
        int classes = 0;
        List<String> phantoms = new ArrayList<>();
        for (Path p : javaFiles(MODULES)) {
            String raw = read(p);
            String code = stripComments(raw);
            int strict = count(ENTITY, code);
            if (strict > 0) files++;
            classes += strict;
            if (strict == 0 && raw.contains("@Entity")) phantoms.add(MODULES.relativize(p).toString());
        }
        assertThat(files).as("anti-vacuity: no entity files found — the scan is broken").isGreaterThan(300);

        assertThat(files)
            .as("MEASURED_BASELINE.md §0 'Files declaring a JPA entity'. Counted strictly and over "
              + "comment-stripped source: @EntityListeners, @EntityGraph and Javadoc prose must NOT "
              + "count. Files whose only '@Entity' is a phantom: %s", phantoms)
            .isEqualTo(docNumber("Files declaring a JPA entity"));

        assertThat(classes)
            .as("MEASURED_BASELINE.md §0 'JPA entity classes declared'. This is deliberately a second, "
              + "larger number than the file count: one @Entity per file is not one entity per file, "
              + "and publishing only the file count understated data_officer 14-fold.")
            .isEqualTo(docNumber("JPA entity **classes** declared"));
    }

    @Test
    void jpaSchemaCountMatches() {
        Set<String> schemas = new TreeSet<>();
        for (Path p : javaFiles(MODULES)) {
            String code = stripComments(read(p));
            if (!ENTITY.matcher(code).find()) continue;
            Matcher t = TABLE_ANNOTATION.matcher(code);
            while (t.find()) {
                Matcher s = SCHEMA_ATTR.matcher(t.group(1));
                while (s.find()) schemas.add(s.group(1));
            }
        }
        assertThat(schemas).as("anti-vacuity: no @Table(schema=…) found").hasSizeGreaterThan(30);
        assertThat(schemas.size())
            .as("MEASURED_BASELINE.md §0 'Distinct JPA schemas'. Note this is the CODE census; the "
              + "database holds one more (`audit`, which JPA does not know) and §7-A records why both "
              + "are needed. Schemas found: %s", schemas)
            .isEqualTo(docNumber("Distinct **JPA** schemas"));
    }

    @Test
    void migrationCountAndHeadMatch() {
        List<Integer> versions = migrationVersions();
        assertThat(versions).as("anti-vacuity: no migrations found").hasSizeGreaterThan(250);

        assertThat(versions.size())
            .as("MEASURED_BASELINE.md §0/§7 states the number of migration files. This is the figure "
              + "that went stale by 28 in six days and produced F2-69. One line to update; the census "
              + "prints it.")
            .isEqualTo(docNumber("Flyway migrations on disk"));

        int head = versions.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(head)
            .as("MEASURED_BASELINE.md §0 states the migration head as V…; the tree's highest version is "
              + "V%d. The ledger in UNIFIED_IMPLEMENTATION_PLAN.md §4 must name the next free version "
              + "as V%d.", head, head + 1)
            .isEqualTo(docHead());
    }

    /**
     * The deployed Flyway baseline cannot be read from a test — it lives in
     * {@code platform.flyway_schema_history} on the deployed server. What CAN be checked is that the
     * document's baseline and the consequence it draws from it are arithmetically consistent: at
     * baseline V<i>b</i>, exactly the files with version ≤ <i>b</i> are recorded as applied without
     * ever executing. Publishing V312 instead of V292 silently moved 20 versions across that line, and
     * this assertion is what makes such an edit fail rather than merely be wrong.
     */
    @Test
    void theFlywayBaselineAndItsConsequenceAgree() {
        int baseline = docBaselineVersion();
        assertThat(baseline)
            .as("§7-B must state the deployed Flyway baseline as a V-number")
            .isBetween(1, 100_000);

        long neverExecuted = migrationVersions().stream().filter(v -> v <= baseline).count();
        assertThat((int) neverExecuted)
            .as("§7-B says %d of the migration files sit at or below baseline V%d and therefore NEVER "
              + "execute against production, but %d files on disk are ≤ V%d. These two numbers are the "
              + "same fact — RC-A's whole question is which migrations can never run — so if you change "
              + "the baseline you must re-derive this count, and if you change this count you must say "
              + "why the baseline moved.",
                docNumber("Files at or below the baseline"), baseline, neverExecuted, baseline)
            .isEqualTo(docNumber("Files at or below the baseline"));

        // Third source, and the only other place in the repository that hard-codes this number.
        // Three artefacts must agree on it — the deployed flyway_schema_history, this document, and
        // SchemaContractRatchetTest — and two of them are checkable from here.
        Path contract = Path.of("src", "test", "java", "com", "tss", "grc", "security",
                                "SchemaContractRatchetTest.java");
        if (Files.isRegularFile(contract)) {
            Matcher m = Pattern.compile("FLYWAY_BASELINE\\s*=\\s*(\\d+)").matcher(read(contract));
            assertThat(m.find()).as("SchemaContractRatchetTest no longer declares FLYWAY_BASELINE").isTrue();
            assertThat(Integer.parseInt(m.group(1)))
                .as("SchemaContractRatchetTest.FLYWAY_BASELINE and MEASURED_BASELINE.md §7-B disagree "
                  + "about the deployed Flyway baseline. Both describe the same production server; if it "
                  + "was genuinely re-baselined, change both and say so in the plan's migration ledger.")
                .isEqualTo(baseline);
        }

        int head = docAppliedHead();
        assertThat(head).as("§7-B must state the applied head").isGreaterThanOrEqualTo(baseline);
        long pending = migrationVersions().stream().filter(v -> v > head).count();
        assertThat((int) pending)
            .as("§7-B says %d migrations are pending the next jar; %d files on disk are above the "
              + "applied head V%d. A pending set that is understated is how a deployment ships code "
              + "whose migration has not landed.",
                docNumber("Pending the next jar"), pending, head)
            .isEqualTo(docNumber("Pending the next jar"));
    }

    @Test
    void permissionModuleCensusMatches() {
        Set<String> modules = new TreeSet<>();
        for (Path p : javaFiles(MAIN_JAVA)) {
            for (String pair : GuardExpressions.permissionPairsIn(stripComments(read(p)))) {
                modules.add(pair.substring(0, pair.indexOf(':')));
            }
        }
        assertThat(modules).as("anti-vacuity: no hasPermission guards found").hasSizeGreaterThan(20);
        assertThat(modules.size())
            .as("MEASURED_BASELINE.md §0/§4 'Distinct permission modules used by guards'. Modules "
              + "named by guards: %s", modules)
            .isEqualTo(docNumber("Distinct permission modules used by guards"));
    }

    /**
     * Counted <b>negatively</b> — a package is role-guarded when it contains no {@code hasPermission(}
     * of any shape — because a positive scan for {@code hasAnyRole('…')} misses {@code sharia}
     * entirely: its guards are {@code @PreAuthorize(READ_GUARD)} where {@code READ_GUARD} is a
     * {@code static final String} concatenated across a line break. Any single-line regex census of
     * guard style reports such a module as having no role guard at all.
     */
    @Test
    void packagesWithNoHasPermissionMatch() {
        List<String> roleOnly = new ArrayList<>();
        try (Stream<Path> s = Files.list(MODULES)) {
            for (Path pkg : s.filter(Files::isDirectory).toList()) {
                boolean any = javaFiles(pkg).stream()
                    .anyMatch(p -> HAS_PERMISSION_BARE.matcher(stripComments(read(p))).find());
                if (!any) roleOnly.add(pkg.getFileName().toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(roleOnly.size())
            .as("MEASURED_BASELINE.md §0/§4 'Module packages with zero hasPermission usage' — the "
              + "F2-05/F2-52 progress metric. A tenant cannot delegate access to these packages at "
              + "all, because the code compares role NAMES. Packages: %s", roleOnly)
            .isEqualTo(docNumber("Module packages with **zero**"));
    }

    @Test
    void frontendPageAndRouteCountsMatch() {
        Assumptions.assumeTrue(Files.isDirectory(FE_PAGES) && Files.isRegularFile(FE_APP),
            "frontend sources not present next to the backend — skipping the FE half");

        List<Path> pages;
        try (Stream<Path> s = Files.walk(FE_PAGES)) {
            // ⚠ A colocated `*.test.tsx` is not a page. F2-16 tranche 1 added the first one under
            // `pages/` and this count went 340 → 342, firing the gate — correctly, but the fix is to
            // narrow the scan rather than raise the number: this figure's job is to be compared with
            // the 329 registered routes (F2-07's finding), and counting tests as pages would widen
            // that gap with files that were never reachable in the first place. `baseline_census.py`
            // carries the same exclusion, and `theCensusScriptAgreesWithTheDocument` keeps them equal.
            pages = s.filter(Files::isRegularFile)
                     .filter(p -> p.getFileName().toString().endsWith(".tsx"))
                     .filter(p -> !p.getFileName().toString().endsWith(".test.tsx"))
                     .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(pages).as("anti-vacuity: no .tsx pages found").hasSizeGreaterThan(200);
        assertThat(pages.size())
            .as("MEASURED_BASELINE.md §0/§5 'Frontend pages'")
            .isEqualTo(docNumber("Frontend pages"));

        Set<String> folders = new HashSet<>();
        for (Path p : pages) {
            Path rel = FE_PAGES.relativize(p).getParent();
            folders.add(rel == null ? "<root>" : rel.toString().replace('\\', '/'));
        }
        assertThat(folders.size())
            .as("MEASURED_BASELINE.md §0/§5 'in N folders'")
            .isEqualTo(docNumber("Frontend pages", 2));

        int routes = count(ROUTE, read(FE_APP));
        assertThat(routes).as("anti-vacuity: no <Route path= found in App.tsx").isGreaterThan(200);
        assertThat(routes)
            .as("MEASURED_BASELINE.md §0/§5 'Routes registered in App.tsx'. The gap between routes and "
              + "sidebar entries is the finding (~135 pages reachable only by URL), so both halves must "
              + "stay honest.")
            .isEqualTo(docNumber("Routes registered in"));
    }

    /**
     * The document and the census script must never disagree. The script's {@code PUBLISHED} dict is
     * what {@code --check} compares against; if only one of the two is updated, the other silently
     * becomes the stale copy — which is the defect this whole test exists to prevent, reintroduced one
     * level down.
     */
    @Test
    void theCensusScriptAgreesWithTheDocument() {
        Assumptions.assumeTrue(Files.isRegularFile(CENSUS),
            "baseline_census.py not checked out — skipping the doc/script agreement check");
        String py = read(CENSUS);
        Matcher dict = Pattern.compile("PUBLISHED\\s*=\\s*\\{(.*?)\\n\\}", Pattern.DOTALL).matcher(py);
        assertThat(dict.find())
            .as("baseline_census.py no longer has a PUBLISHED dict — it is the script's own record of "
              + "what this document claims, and --check is meaningless without it")
            .isTrue();

        Map<String, Integer> published = new HashMap<>();
        Matcher entry = Pattern.compile("\"([a-z_]+)\"\\s*:\\s*([\\d_]+)").matcher(dict.group(1));
        while (entry.find()) published.put(entry.group(1), Integer.parseInt(entry.group(2).replace("_", "")));
        assertThat(published).as("anti-vacuity: PUBLISHED parsed empty").hasSizeGreaterThan(10);

        Map<String, Integer> fromDoc = new HashMap<>();
        fromDoc.put("module_packages", docNumber("Backend module packages"));
        fromDoc.put("entities_strict", docNumber("Files declaring a JPA entity"));
        fromDoc.put("entity_decls", docNumber("JPA entity **classes** declared"));
        fromDoc.put("jpa_schemas", docNumber("Distinct **JPA** schemas"));
        fromDoc.put("migration_files", docNumber("Flyway migrations on disk"));
        fromDoc.put("migration_head", docHead());
        fromDoc.put("flyway_baseline", docBaselineVersion());
        fromDoc.put("perm_modules", docNumber("Distinct permission modules used by guards"));
        fromDoc.put("packages_no_haspermission", docNumber("Module packages with **zero**"));
        fromDoc.put("fe_pages", docNumber("Frontend pages"));
        fromDoc.put("fe_routes", docNumber("Routes registered in"));

        List<String> mismatched = new ArrayList<>();
        fromDoc.forEach((k, v) -> {
            Integer p = published.get(k);
            if (p == null) mismatched.add(k + ": absent from PUBLISHED (doc says " + v + ")");
            else if (!p.equals(v)) mismatched.add(k + ": doc " + v + " vs script " + p);
        });
        assertThat(mismatched)
            .as("MEASURED_BASELINE.md and baseline_census.py's PUBLISHED dict disagree. Update both in "
              + "the same commit — that pairing is the mechanism, not a formality.")
            .isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static List<Integer> migrationVersions() {
        try (Stream<Path> s = Files.list(MIGRATIONS)) {
            List<Integer> out = new ArrayList<>();
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Matcher m = MIGRATION_VERSION.matcher(p.getFileName().toString());
                if (m.matches()) out.add(Integer.parseInt(m.group(1)));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The range end in the "Flyway migrations on disk" row's value cell. Taken as the largest V-number
     * in <b>that cell only</b>: the row also carries the previous baseline's range and a "next free
     * V<i>n+1</i>" note, either of which a whole-line scan would pick up instead.
     */
    private static int docHead() {
        String cell = valueCell("Flyway migrations on disk");
        Matcher m = Pattern.compile("V(\\d+)").matcher(cell);
        int max = -1;
        while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
        assertThat(max).as("no V-number found in the value cell: %s", cell).isGreaterThan(0);
        return max;
    }

    /** `**baseline `V292`**` in the §7-B table. */
    private static int docBaselineVersion() {
        Matcher m = Pattern.compile("\\|\\s*Baseline\\s*\\|.*?V(\\d+)").matcher(doc());
        assertThat(m.find()).as("§7-B has no `| Baseline | … V<n>` row").isTrue();
        return Integer.parseInt(m.group(1));
    }

    /** `**`V336`**` in the §7-B "Applied head" row. */
    private static int docAppliedHead() {
        Matcher m = Pattern.compile("\\|\\s*Applied head\\s*\\|.*?V(\\d+)").matcher(doc());
        assertThat(m.find()).as("§7-B has no `| Applied head | … V<n>` row").isTrue();
        return Integer.parseInt(m.group(1));
    }

    /**
     * F2-266 — {@code CLAUDE.md}'s migration line said it was gated, and it was not.
     *
     * <p>The line reads <i>"V1→V385 packaged; next free V386 (373 files; … read the directory, not
     * this line: `ls .../sqlserver | tail -3`. `Appendix/scripts/baseline_census.py` prints it and
     * `MeasuredBaselineFreshnessTest` gates it)"</i>. The <b>figure</b> was gated — in
     * {@code MEASURED_BASELINE.md} and in the census script's {@code PUBLISHED} dict, both asserted
     * above. <b>This line was not.</b> F2-266 proved it by hand: adding {@code V385} and updating
     * only the other two artefacts leaves this suite green with {@code CLAUDE.md} still reading
     * {@code V384} and {@code 372 files}.
     *
     * <p>🔑 That is F2-265's finding — <b>a count in prose is a copy of a measurement, and an
     * instruction not to let it rot is not an enforcement</b> — inside the very file that records
     * F2-265, one paragraph after a warning that this exact line went stale by 54 versions. The
     * cheap fix is not to delete the figure (a reader of the house rules needs the next free
     * version to hand) but to make the copy checkable.
     *
     * <p>Anti-vacuity: the line must be FOUND. A pattern that stops matching would otherwise
     * report a clean tree, which is the failure mode this whole class exists for.
     */
    @Test
    void claudeMdMigrationLineMatchesTheTree() {
        Path claude = Path.of("..", "CLAUDE.md");
        Assumptions.assumeTrue(Files.isRegularFile(claude), "CLAUDE.md not reachable");
        String text = read(claude);

        Matcher m = Pattern.compile(
            "V1\u2192V(\\d+) packaged; next free V(\\d+)\\*\\*\\s*\\((\\d+) files")
            .matcher(text);
        assertThat(m.find())
            .as("CLAUDE.md's migration line was not found in the shape this leg parses "
                + "('V1\u2192V<head> packaged; next free V<next>** (<n> files'). If the line was "
                + "reworded, update this pattern — do NOT delete the assertion, because an "
                + "unparsed line reports a clean tree (F2-69), which is exactly how this figure "
                + "went stale by 54 versions before.")
            .isTrue();

        List<Integer> versions = migrationVersions();
        int head = versions.stream().mapToInt(Integer::intValue).max().orElseThrow();

        assertThat(Integer.parseInt(m.group(1)))
            .as("CLAUDE.md says the packaged head is V%s; the tree's highest version is V%d.",
                m.group(1), head)
            .isEqualTo(head);
        assertThat(Integer.parseInt(m.group(2)))
            .as("CLAUDE.md says the next free version is V%s; it is V%d. Reusing a taken version "
                + "is a Flyway checksum failure on the deployed server, which is what that line "
                + "itself warns about.", m.group(2), head + 1)
            .isEqualTo(head + 1);
        assertThat(Integer.parseInt(m.group(3)))
            .as("CLAUDE.md says there are %s migration files; there are %d.",
                m.group(3), versions.size())
            .isEqualTo(versions.size());
    }
    // ─────────────────────────────────────────────────────────────────────────────────────
    // F2-273 — three MORE restatements of the migration census, all ungated, all measured stale
    // ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * F2-273 — F2-266 gated the migration figure it was <i>reading</i> and left two others in the
     * same file, and they then drifted by exactly the migrations added since.
     *
     * <p>Measured 2026-08-22, before the repair: the gated line
     * ({@link #claudeMdMigrationLineMatchesTheTree}) read {@code V1→V387 … 375 files} and was
     * <b>current</b>. {@code CLAUDE.md}'s two other statements of the same fact — the pending range
     * (<i>"head V344, so V345–V384 are pending the next jar — 40 files"</i>) and the architecture
     * table's <i>"Flyway V1–V384 (OFF in dev)"</i> — both read <b>V384</b>, three behind, i.e. stale
     * by exactly V385/V386/V387: the three migrations added after F2-266's leg landed.
     *
     * <p>🔑 <b>A gate whose population is one spelling of a fact leaves every other spelling free
     * to rot, and the gated copy being current is what makes the stale ones credible</b> — a reader
     * who finds the packaged-head line correct has no reason to distrust the sentence forty lines up.
     * F2-198's shape at a new depth, and F2-215's two-of-N: F2-266's own javadoc says <i>"CLAUDE.md's
     * migration line said it was gated, and it was not"</i>, having looked at one line.
     *
     * <p>Anti-vacuity: both lines must be FOUND, for the reason the sibling leg gives — an unparsed
     * line reports a clean tree, which is the failure mode this whole class exists for.
     */
    @Test
    void claudeMdRestatesTheMigrationHeadTwiceMoreAndBothAreNowGated() {
        Path claude = Path.of("..", "CLAUDE.md");
        Assumptions.assumeTrue(Files.isRegularFile(claude), "CLAUDE.md not reachable");
        String text = read(claude);

        List<Integer> versions = migrationVersions();
        int head = versions.stream().mapToInt(Integer::intValue).max().orElseThrow();
        int applied = docAppliedHead();

        int[] range = claudePendingRange(text);
        assertThat(range)
            .as("CLAUDE.md's pending-range sentence was not found in the shape this leg parses "
              + "('head **V<applied>**, so **V<lo>" + EN_LIT + "V<hi> are pending** the next jar "
              + EM_LIT + " <n> files'). If it was reworded, update the pattern — do NOT delete "
              + "the assertion, because an unparsed line reports a clean tree.")
            .isNotNull();

        assertThat(range[0])
            .as("CLAUDE.md says the deployed applied head is V%d; MEASURED_BASELINE.md §7-B says "
              + "V%d. Both describe the same production server.", range[0], applied)
            .isEqualTo(applied);
        assertThat(range[1])
            .as("CLAUDE.md says the pending set starts at V%d; the first version above the applied "
              + "head V%d is V%d.", range[1], applied, applied + 1)
            .isEqualTo(applied + 1);
        assertThat(range[2])
            .as("CLAUDE.md says the pending set ends at V%d; the tree's highest version is V%d. This "
              + "is the figure that was three behind while the gated line one page up was current "
              + "(F2-273).", range[2], head)
            .isEqualTo(head);
        assertThat(range[3])
            .as("CLAUDE.md says %d migrations are pending; %d files on disk are above the applied "
              + "head V%d. An understated pending set is how a deployment ships code whose migration "
              + "has not landed.", range[3], head - applied, applied)
            .isEqualTo(head - applied);

        Integer archRow = claudeFlywayArchitectureRow(text);
        assertThat(archRow)
            .as("CLAUDE.md's architecture table has no 'Flyway V1" + EN_LIT + "V<head> (OFF in "
              + "dev)' cell in the shape this leg parses. Same warning as above: do not delete the "
              + "assertion.")
            .isNotNull();
        assertThat(archRow)
            .as("CLAUDE.md's architecture table says the packaged range ends at V%d; it ends at V%d.",
                archRow, head)
            .isEqualTo(head);
    }

    /**
     * F2-273 — and the leg that DID cover the pending row gated its <b>count</b> and not its
     * <b>membership</b>, so the number was right while the list it summarises was six short.
     *
     * <p>{@link #flywayBaselineArithmeticHolds} asserts {@code docNumber("Pending the next jar")}
     * equals the number of files above the applied head, and on 2026-08-22 that was correctly 43.
     * The same cell enumerates the pending migrations one by one — and named 37 of them:
     * {@code V359}, {@code V360}, {@code V365}, {@code V380}, {@code V386} and {@code V387} were
     * absent.
     *
     * <p>🔑🔑 <b>A count and the enumeration it summarises are two artefacts, and gating the
     * count makes the gap invisible precisely because the number is right.</b> A reader checking
     * whether the pending set is described reads the total, finds it correct, and stops.
     *
     * <p>Subset, not equality: the cell legitimately mentions versions outside the pending range when
     * one migration's description refers to another's self-check, so extra V-numbers are fine and a
     * missing one is not.
     */
    @Test
    void thePendingCellNamesEveryPendingMigrationAndNotJustHowMany() {
        int applied = docAppliedHead();
        List<Integer> pending = migrationVersions().stream()
                .filter(v -> v > applied).sorted().toList();
        assertThat(pending).as("anti-vacuity: no pending migrations found above V%d", applied)
                .isNotEmpty();

        // — the ROW, not valueCell(). This document's prose contains literal pipe
        // characters (vocabulary listings such as "HIGH | MEDIUM | LOW"), so splitting the
        // row on "|" truncates this logical cell at 15,754 of ~34,000 characters and the leg
        // reported 14 missing where 4 were. It OVER-reports, so it fails loudly rather than
        // hiding an omission — but a gate that fails on correct code is the one
        // failure a gate must never have (F2-214). Reading the whole row is deliberately
        // permissive: a version named in a neighbouring column would count. That is
        // acceptable here because the claim is only that a pending migration is NAMED
        // SOMEWHERE in this row, and a newly added version will not appear by accident.
        String cell = row("Pending the next jar");
        Set<Integer> named = new TreeSet<>();
        Matcher m = Pattern.compile("V(\\d+)").matcher(cell);
        while (m.find()) named.add(Integer.parseInt(m.group(1)));
        assertThat(named)
            .as("anti-vacuity: the 'Pending the next jar' cell names no versions at all, so this leg "
              + "would pass over any omission.")
            .hasSizeGreaterThan(10);

        List<Integer> missing = pending.stream().filter(v -> !named.contains(v)).toList();
        assertThat(missing)
            .as("§7-B's 'Pending the next jar' cell enumerates the pending migrations, and these "
              + "are pending but never named: %s. The cell's COUNT is gated by "
              + "flywayBaselineArithmeticHolds and was correct while six members were missing — "
              + "a count and its enumeration are two artefacts (F2-273).", missing)
            .isEmpty();
    }

    /**
     * F2-273 — the positive control. Both new detectors are run against the literal wording that
     * was on {@code master} when the drift was measured, so a pattern that stops matching fails here
     * instead of reporting a clean tree.
     *
     * <p>This is the leg that distinguishes "the figures agree" from "the parse returned nothing",
     * which on a green tree look identical.
     */
    @Test
    void theRestatementDetectorsFireOnTheDriftTheyWereWrittenFor() {
        String stale =
            "Flyway (baseline 292 on the deployed server; head **V344**, so **V345" + EN_LIT
          + "V384 are pending** the\nnext jar " + EM_LIT + " 40 files). `flyway.clean` is disabled.\n"
          + "| DB | SQL Server 2019+, UTF-8 collation, 50+ schemas, Flyway V1" + EN_LIT
          + "V384 (OFF in dev) |\n";

        int[] range = claudePendingRange(stale);
        assertThat(range).as("the pending-range detector no longer matches master's wording")
                .isNotNull();
        assertThat(range[0]).isEqualTo(344);
        assertThat(range[1]).isEqualTo(345);
        assertThat(range[2]).as("the stale upper bound must be read as V384, not the tree's head")
                .isEqualTo(384);
        assertThat(range[3]).isEqualTo(40);

        assertThat(claudeFlywayArchitectureRow(stale))
            .as("the architecture-row detector no longer matches master's wording")
            .isEqualTo(384);

        // And neither may match text that merely talks about Flyway.
        assertThat(claudePendingRange("Flyway is on in prod and off in dev.")).isNull();
        assertThat(claudeFlywayArchitectureRow("| DB | SQL Server, Flyway (OFF in dev) |")).isNull();
    }

    /** The en dash and em dash CLAUDE.md uses, as literals for pattern and message building. */
    private static final String EN_LIT = "\u2013";
    private static final String EM_LIT = "\u2014";

    /**
     * {@code head **V<applied>**, so **V<lo>–V<hi> are pending** the next jar — <n> files}.
     * Returns {@code {applied, lo, hi, n}}, or null if the sentence is not present.
     *
     * <p>The sentence wraps in the source, so {@code \s+} spans the line break rather than a literal
     * {@code \n} — CLAUDE.md is CRLF and an embedded newline could never match (F2-212).
     */
    private static int[] claudePendingRange(String text) {
        Matcher m = Pattern.compile(
            "head \\*\\*V(\\d+)\\*\\*, so \\*\\*V(\\d+)[" + EN_LIT + "-]V(\\d+) "
          + "are pending\\*\\*\\s+the\\s+next jar " + EM_LIT + " (\\d+) files")
            .matcher(text);
        if (!m.find()) return null;
        return new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                           Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)) };
    }

    /** {@code Flyway V1–V<head> (OFF in dev)} in the architecture table. Null if absent. */
    private static Integer claudeFlywayArchitectureRow(String text) {
        Matcher m = Pattern.compile("Flyway V1[" + EN_LIT + "-]V(\\d+) \\(OFF in dev\\)")
                .matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }
}
