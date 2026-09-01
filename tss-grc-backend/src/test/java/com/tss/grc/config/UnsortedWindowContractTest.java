package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-422 — no figure may be derived from an UNSORTED capped page.
 *
 * <p>{@code Pageable.ofSize(N)} is {@code PageRequest.of(0, N, Sort.unsorted())}. F2-420
 * established that a cap is a defect when the ORDER it caps under is <b>orthogonal</b> to the
 * question the figure asks, and F2-421 sharpened that to a third relation, <b>adverse</b>, where
 * the two keys are negatively correlated so the window discards the answer rather than a random
 * slice of it. An unsorted cap is the sharpest of the three: <b>there is no order at all</b>, so
 * which rows the window holds is a function of the query plan — the same dashboard, loaded twice,
 * may report different figures.
 *
 * <p>That matters most where it was found. {@code AuditCommitteeDashboardService} derived every
 * figure of three sections from such a window, {@code generateSnapshot} serialises the whole live
 * dashboard into {@code kpis_json}, and {@code POST /snapshots/&#123;id&#125;/attest} hashes and signs it
 * for a named Audit Committee member under CMA Art. 54 / SAMA CG. F2-199's harm ladder gains a
 * rung: <b>a governance figure that is a function of a query plan is not reproducible, so it
 * cannot honestly be attested.</b>
 *
 * <h2>Why this is a HARD ZERO with an empty exemption map</h2>
 *
 * <p>A gate that fails on correct code trains the next reader to edit the assertion rather than
 * the code (F2-214), so a hard zero needs an always-available alternative. There is one, and it is
 * strictly more informative: {@code PageRequest.of(0, N, Sort.by(...))} caps <em>and states which
 * rows it keeps</em>. For a bounded preview that is what you want; for a figure you want no cap at
 * all. {@code Pageable.ofSize(N)} says "some N rows, I am not telling you which", which is never
 * the right thing to say to a caller — so there is no correct code for this leg to fail on.
 *
 * <h2>F2-423 — the same question, asked of a single-row PICK</h2>
 *
 * <p>A window with no key is a function of the query plan; so is a PICK with no key.
 * {@code CptyService.upsertLimit} identified the SAMA large-exposure limit row it was
 * about to overwrite with {@code hits.get(0)} of {@code findByCounterpartyIdAndOrganisationId}
 * — no order, and no scope either, while the GROUP branch four lines below it filtered
 * {@code "GROUP".equals(getScope())}. Two sibling branches of one method, one applying the
 * scope half of the key and one not (F2-215). Because the body then rewrites
 * {@code setScope} / {@code setGroupReference} on whatever row it picked, a COUNTERPARTY
 * upsert that captured a GROUP row carrying a {@code counterparty_id} turned a consolidated
 * group limit into one counterparty's limit and nulled its group reference.
 *
 * <p>Measured 2026-09-01, dev and prod byte-identical: 33 counterparty keys and 4 group
 * references already hold two rows each, so the pick is exercised today; the plan returns
 * id ASC on every sampled key, so stating that order changes no working outcome.
 *
 * <p>F2-415 swept this same population — {@code .get(0)} / {@code .findFirst()} /
 * {@code Optional.get()} — for EMPTINESS and found 0 unguarded. It never asked which row
 * the pick returns. An instrument keyed on one question is blind to the next one.
 *
 * <p>Scoped to {@code src/main/java} deliberately: two test classes construct an unsorted
 * {@code Pageable} to hand to a mocked paged finder, which is legitimate — a mock has no page
 * size, so the value is inert there. (That inertness is the other half of this finding: see
 * {@code AuditCommitteeDashboardServiceTest}.)
 */
class UnsortedWindowContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** The literal shape retired by F2-422, and the two forms that must NOT be flagged. */
    private static final String PRE_FIX =
            "var page = riskRepo.findByOrganisationId(orgId, Pageable.ofSize(1000));";
    private static final String SORTED_ALTERNATIVE =
            "riskRepo.findByOrganisationId(orgId, PageRequest.of(0, 1000, Sort.by(\"id\")));";
    private static final String UNPAGED =
            "riskRepo.findByOrganisationId(orgId, Pageable.unpaged());";

    private record Site(String file, int line, String text) {
        @Override public String toString() { return file + ":" + line + "  " + text.trim(); }
    }

    private static boolean isUnsortedCap(String strippedLine) {
        return strippedLine.contains("Pageable.ofSize(");
    }

    // ── the claim first, floors after it (F2-414 / F2-417) ───────────────────────────────

    @Test
    @DisplayName("F2-422: no main-source fetch caps a population without saying which rows it keeps")
    void noFetchCapsAPopulationWithoutStatingItsOrder() throws IOException {
        List<Site> sites = scan();
        assertThat(sites)
                .as("`Pageable.ofSize(N)` keeps N rows and does not say WHICH N, so anything derived "
                        + "from the result is a function of the query plan. F2-422 retired the five "
                        + "sites that fed governance figures — three Audit Committee dashboard "
                        + "sections, ThreeLinesService#openRisksFor and RiskAppetiteService#dashboard. "
                        + "There is no exemption map on purpose: if you want a bounded preview write "
                        + "PageRequest.of(0, N, Sort.by(...)), which caps AND states the order; if you "
                        + "want a figure, do not cap at all.%n  found: %s", sites)
                .isEmpty();
    }

    @Test
    @DisplayName("F2-422: the four uncapped overloads the fix routes through still exist")
    void theUncappedOverloadsExistAndReturnAList() throws Exception {
        record Overload(String repo, String finder) {}
        List<Overload> expected = List.of(
                new Overload("com.tss.grc.module.risk.repository.RiskRepository",
                        "findByOrganisationId"),
                new Overload("com.tss.grc.module.compliance.repository.AuditFindingRepository",
                        "findByOrganisationId"),
                new Overload("com.tss.grc.module.incident.repository.IncidentRepository",
                        "findByOrganisationId"),
                new Overload("com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository",
                        "findByOrganisationIdOrderByCreatedAtDesc"));

        for (Overload o : expected) {
            Class<?> repo = Class.forName(o.repo());
            Method m = repo.getMethod(o.finder(), Long.class);
            assertThat(List.class)
                    .as("%s#%s(Long) must return a List — it is the uncapped overload every "
                            + "derived figure reads. Deleting it is how F2-422 comes back.",
                            repo.getSimpleName(), o.finder())
                    .isAssignableFrom(m.getReturnType());
        }
    }

    @Test
    @DisplayName("F2-422 self-test: the detector fires on the retired shape, not on the alternatives")
    void theDetectorFiresOnTheRetiredShapeAndNotOnTheAlternatives() {
        assertThat(isUnsortedCap(LocaleSensitiveFormatContractTest.strip(PRE_FIX)))
                .as("must flag the literal pre-fix expression, or the corpus leg above is vacuous")
                .isTrue();
        assertThat(isUnsortedCap(LocaleSensitiveFormatContractTest.strip(SORTED_ALTERNATIVE)))
                .as("must NOT flag PageRequest.of(0, N, Sort.by(...)) — that is the always-available "
                        + "alternative, and a gate that refused it would fail on correct code")
                .isFalse();
        assertThat(isUnsortedCap(LocaleSensitiveFormatContractTest.strip(UNPAGED)))
                .as("must NOT flag Pageable.unpaged() — that is genuinely uncapped")
                .isFalse();
        assertThat(isUnsortedCap(LocaleSensitiveFormatContractTest.strip("// " + PRE_FIX)))
                .as("must NOT flag the shape inside a comment — this codebase documents a retirement "
                        + "by quoting what it replaced, and F2-422's own fix does exactly that, so a "
                        + "raw scan would read the epitaph as the corpse (F2-193)")
                .isFalse();
    }

    @Test
    @DisplayName("F2-422 anti-vacuity: the scan reached the whole main tree")
    void theScanReachedTheWholeMainTree() throws IOException {
        int files;
        try (Stream<Path> walk = Files.walk(MAIN)) {
            files = (int) walk.filter(p -> p.toString().endsWith(".java")).count();
        }
        assertThat(files)
                .as("a hard zero over a tree the walk never reached is indistinguishable from a "
                        + "clean tree (F2-235). Measured 1,895 at the time of writing; the floor "
                        + "sits above the largest WRONG answer, which is a walk that resolves "
                        + "nothing (0) or one module (a few dozen).")
                .isGreaterThan(1_500);
    }

    // ── F2-423: a single-row PICK needs a key too ────────────────────────────────────────

    /**
     * Every {@code findFirstBy} / {@code findTopBy} derived finder in the platform that declares
     * no {@code OrderBy}, with a verdict each — read, not inferred.
     *
     * <p>A hard zero here would fail on correct code, and the reason IS the discriminator for this
     * whole shape: {@code findFirstBy} with no order is correct exactly when something guarantees
     * the result cannot hold two rows — a unique index, or a caller that only asks whether a row
     * exists at all.
     */
    private static final Map<String, String> UNORDERED_FIRST_FINDERS = new LinkedHashMap<>() {{
        put("findFirstByIntegrationAndExternalNumber",
                "DARK — zero callers anywhere in main or test, and the only one of the six that is "
                + "not tenant-scoped. Whether it should exist at all is F2-187's question.");
        put("findFirstByOrganisationIdAndFactorCodeAndIsActiveTrue",
                "GENUINE PICK, LATENT — FILED. Its own javadoc asserts 'there is at most one' and "
                + "nothing enforces that: sust.emission_factor carries 0 CHECK constraints and 0 "
                + "unique indexes, and two concurrent create() calls both deactivate the same prior "
                + "row and both insert with isActive=true. resolveActive feeds the GHG calculator, so "
                + "the chosen factor_value multiplies activity data into tCO2e for the ISAE 3410 "
                + "lineage. Measured 2026-09-01: 6 rows, 0 LIVE in dev and prod, so no duplicate can "
                + "exist today — the register is empty and resolveActive throws for every code.");
        put("findFirstByOrganisationIdAndFalconHostId",
                "EXISTENCE CHECK — its only caller is `.isEmpty()`, so which row comes back cannot "
                + "matter. `existsBy` would say so more plainly; that is style, not a defect.");
        put("findFirstByOrganisationIdAndIntegrationAndExternalNumber",
                "GENUINE PICK, UNMEASURED — FILED. platform.integration_external_link has no unique "
                + "index and the ServiceNow webhook dereferences the match to attach an inbound "
                + "update to a GRC record. Not measured in this slice; saying so is the point.");
        put("findFirstByOrganisationIdAndTenablePluginId",
                "EXISTENCE CHECK — its only caller is `.isPresent()`, so the row is never "
                + "dereferenced and the pick cannot reach a caller. Same verdict as the "
                + "CrowdStrike finder above, and reached the same way: by reading the caller.");
        put("findFirstByOrganisationIdAndUserIdAndPageKeyAndIsDefaultTrue",
                "CORRECT BY INDEX — V160 creates UNIQUE INDEX UX_saved_views_default_per_user_page, "
                + "so the result is size <= 1 by construction and First is defensive rather than a "
                + "pick. This is the discriminator the other five are judged against.");
    }};

    @Test
    @DisplayName("F2-423: the limit upsert names both halves of the key and an order")
    void theLimitFindersNameTheWholeKeyAndAnOrder() throws Exception {
        Class<?> repo = Class.forName(
                "com.tss.grc.module.cpty.repository.LargeExposureLimitRepository");

        Method byCounterparty = repo.getMethod(
                "findFirstByOrganisationIdAndScopeAndCounterpartyIdOrderByIdAsc",
                Long.class, String.class, Long.class);
        Method byGroup = repo.getMethod(
                "findFirstByOrganisationIdAndScopeAndGroupReferenceOrderByIdAsc",
                Long.class, String.class, String.class);
        for (Method m : List.of(byCounterparty, byGroup)) {
            assertThat(Optional.class)
                    .as("%s must return Optional — the query picks the row, so there is no list "
                            + "for a caller to pick out of", m.getName())
                    .isAssignableFrom(m.getReturnType());
        }

        // The scope-blind finder is gone; its replacement declares the order both consumers needed.
        assertThat(repo.getMethod("findByCounterpartyIdAndOrganisationIdOrderByIdAsc",
                        Long.class, Long.class))
                .as("listLimitsForCounterparty rendered this list in plan order too")
                .isNotNull();
        assertThat(List.of(repo.getMethods()).stream()
                .anyMatch(m -> m.getName().equals("findByCounterpartyIdAndOrganisationId")))
                .as("the unordered finder must not come back: it is the only one of four sibling "
                        + "findByCounterpartyIdAndOrganisationId* finders in module/cpty that "
                        + "declared no order, and BOTH its consumers depended on one")
                .isFalse();
    }

    @Test
    @DisplayName("F2-423: upsertLimit picks no row by hand")
    void theLimitUpsertDoesNotPickARowByHand() throws IOException {
        String method = methodBody(
                MAIN.resolve(Path.of("com", "tss", "grc", "module", "cpty", "service",
                        "CptyService.java")),
                "public LimitResponse upsertLimit(");
        assertThat(method)
                .as("`hits.get(0)` over an unordered finder is how a COUNTERPARTY upsert captured a "
                        + "GROUP row and how the choice between two rows for one key became a "
                        + "function of the query plan")
                .doesNotContain(".get(0)");
        assertThat(method)
                .as("both branches must address the whole key through the query")
                .contains("findFirstByOrganisationIdAndScopeAndCounterpartyIdOrderByIdAsc")
                .contains("findFirstByOrganisationIdAndScopeAndGroupReferenceOrderByIdAsc");
        assertThat(method)
                .as("the GROUP branch used to fetch the tenant's whole limit register ordered by "
                        + "utilisation_pct — a key orthogonal to identity, and a TIE on all four "
                        + "duplicated group references in dev and prod")
                .doesNotContain("findByOrganisationIdOrderByUtilisationPctDesc");
    }

    @Test
    @DisplayName("F2-423 census: every unordered findFirstBy/findTopBy finder is adjudicated")
    void everyUnorderedFirstFinderIsAdjudicated() throws IOException {
        assertThat(unorderedFirstFinders())
                .as("a findFirstBy/findTopBy with no OrderBy is SELECT TOP 1 with no key. Each is "
                        + "correct only because something guarantees the result cannot hold two rows "
                        + "(a unique index) or because the caller only asks whether one exists. A new "
                        + "one must be read and given a verdict — do not add it to the map "
                        + "unexamined.")
                .containsExactlyElementsOf(new TreeSet<>(UNORDERED_FIRST_FINDERS.keySet()));

        for (Map.Entry<String, String> e : UNORDERED_FIRST_FINDERS.entrySet()) {
            assertThat(e.getValue())
                    .as("%s's verdict must say WHY, not just label it", e.getKey())
                    .hasSizeGreaterThan(60);
        }
    }

    @Test
    @DisplayName("F2-423 anti-vacuity: the finder scan sees the ORDERED population too")
    void theFinderScanSeesTheOrderedPopulationToo() throws IOException {
        assertThat(orderedFirstFinders())
                .as("measured 27 findFirstBy/findTopBy finders that DO declare an order; a scan "
                        + "that stopped matching would report an empty unordered census, which is "
                        + "indistinguishable from a clean tree (F2-235)")
                .hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("F2-423 self-test: the finder detector splits on OrderBy, not on the verb")
    void theFinderDetectorSplitsOnOrderBy() {
        assertThat(isUnorderedFirstFinder("findFirstByOrganisationIdAndCode")).isTrue();
        assertThat(isUnorderedFirstFinder("findTop5ByOrganisationId")).isTrue();
        assertThat(isUnorderedFirstFinder("findFirstByOrganisationIdOrderByIdAsc")).isFalse();
        assertThat(isUnorderedFirstFinder("findByOrganisationId"))
                .as("a plain findBy returns a list; whether its CALLER picks one row out of it is "
                        + "the question this class's other legs ask, per method")
                .isFalse();
    }

    // ── scanner ─────────────────────────────────────────────────────────────────────────

    /**
     * Comments blanked by {@link LocaleSensitiveFormatContractTest#strip} — a CHARACTER SCANNER,
     * reused rather than copied. That is not tidiness: the one-alternation regex F2-198 / F2-211
     * recommend for a Python census throws {@code StackOverflowError} on this tree's larger files
     * (Java's engine recurses once per iteration of {@code [\s\S]*?}), and three {@code config}
     * gates already keep private copies of a scanner for that reason. Consolidating them into
     * {@code support/} is filed; this slice widened one to package-private instead of adding a
     * fourth, which makes that consolidation cheaper rather than harder.
     */
    private static List<Site> scan() throws IOException {
        List<Site> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MAIN)) {
            List<Path> javas = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            for (Path p : javas) {
                String code = LocaleSensitiveFormatContractTest.strip(
                        Files.readString(p).replace("\r\n", "\n"));
                String rel = MAIN.relativize(p).toString().replace('\\', '/');
                String[] lines = code.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (isUnsortedCap(lines[i])) {
                        out.add(new Site(rel, i + 1, lines[i]));
                    }
                }
            }
        }
        return out;
    }

    /** The brace-balanced body of the one method whose signature starts with {@code needle}. */
    private static String methodBody(Path file, String needle) throws IOException {
        String code = LocaleSensitiveFormatContractTest.strip(
                Files.readString(file).replace("\r\n", "\n"));
        int at = code.indexOf(needle);
        assertThat(at).as("%s not found in %s", needle, file).isNotNegative();
        int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            if (code.charAt(i) == '{') depth++;
            else if (code.charAt(i) == '}' && --depth == 0) return code.substring(open, i + 1);
        }
        throw new IllegalStateException("unbalanced braces after " + needle);
    }

    /**
     * A {@code findFirstBy} / {@code findTopBy} derived finder that declares no {@code OrderBy}:
     * {@code SELECT TOP 1} with no key. Split on the ORDER, never on the verb — a plain
     * {@code findBy} returns a list and its caller's pick is a different (per-method) question.
     */
    private static boolean isUnorderedFirstFinder(String name) {
        return (name.startsWith("findFirstBy") || name.matches("findTop\\d*By.*"))
                && !name.contains("OrderBy");
    }

    private static List<String> unorderedFirstFinders() throws IOException {
        return firstFinders(true);
    }

    private static List<String> orderedFirstFinders() throws IOException {
        return firstFinders(false);
    }

    private static List<String> firstFinders(boolean unordered) throws IOException {
        TreeSet<String> out = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(MAIN)) {
            for (Path p : walk.filter(x -> x.toString().endsWith("Repository.java")).toList()) {
                String code = LocaleSensitiveFormatContractTest.strip(
                        Files.readString(p).replace("\r\n", "\n"));
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\b(find(?:First|Top\\d*)By[A-Za-z0-9_]*)\\s*\\(").matcher(code);
                while (m.find()) {
                    if (isUnorderedFirstFinder(m.group(1)) == unordered) out.add(m.group(1));
                }
            }
        }
        return List.copyOf(out);
    }
}
