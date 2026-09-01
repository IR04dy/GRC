package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
}
