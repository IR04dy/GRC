package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-466 — <b>an empty Optional whose failure nobody named is an opaque 500, and this platform's
 * handler maps every other member of that family.</b>
 *
 * <p><b>The mechanism.</b> {@code Optional.orElseThrow()} with no argument throws
 * {@link java.util.NoSuchElementException}, which is a plain {@code RuntimeException}.
 * {@code GlobalExceptionHandler} declares <b>32</b> {@code @ExceptionHandler} methods and none of
 * them is for that type, so a bare {@code orElseThrow()} falls to
 * {@code @ExceptionHandler(Exception.class)} and answers <b>500</b> — "An unexpected error
 * occurred", with the class stripped in production by SEC-CFG-001. This is the sixth member of the
 * opaque-500 family after F2-227 (optimistic lock), F2-339 ({@code NoResultException}), F2-348
 * ({@code DateTimeParseException}), F2-349 (upload size) and F2-448 (sort property), and F2-339's
 * own note said to ask F2-227's question of the others.
 *
 * <p><b>The defect.</b> {@code NdiSubmissionPackageService} resolves a cycle four times.
 * {@code get()}, {@code create()} and {@code regenerate()} all name the failure
 * ({@code EntityNotFoundException} → <b>404</b> naming the cycle); {@code finalize()} was the
 * fourth and the only bare one — F2-215's two-of-N at N=4, in one class, on one finder. It is
 * <b>live-reachable</b>: {@code NdiAssessmentCycle} carries {@code @Where(is_deleted = 0)} and
 * {@code DELETE /api/ndi/cycles/{id}} soft-deletes, and the {@code preflight()} guard above cannot
 * refuse it — its only <em>blocker</em> is {@code criticalNoCapa} (everything else is a warning)
 * and every child query returns empty for a deleted cycle, so {@code canFinalize} passes
 * <b>vacuously</b> and the next line is the one that fails.
 *
 * <p>The other two were read rather than counted (F2-182 — reachability is a dataflow question):
 * {@code PolicyManagementService#newVersion} is provably non-empty, because its own
 * {@code existing.isEmpty()} refusal is four lines above; {@code WorkflowEngineService} is
 * <b>latent by construction</b>, because {@code workflow.transitions} carries no FK on
 * {@code to_state_id} and nothing in the platform deletes a {@code WorkflowState} — no delete
 * route, no repository delete call — so only a migration could dangle that reference. Say which
 * you have (F2-283): one live, one unreachable, one latent.
 *
 * <p><b>Why the population is exactly three.</b> Four spellings end in
 * {@code NoSuchElementException}: a bare {@code orElseThrow()}, {@code orElseThrow(NoSuchElement…)}
 * (<b>0</b>), {@code iterator().next()} (<b>3</b>) and {@code .get()} on an Optional (<b>39</b>).
 * All 42 of the latter were checked mechanically and all 42 are guarded — re-deriving F2-415's
 * recorded clean sweep rather than trusting it (F2-221). ⚠ Two of the 42 first read as unguarded
 * because the window was nine lines and their {@code isEmpty()} early-returns sit 10 and 22 lines
 * above; that over-reports, which is the loud direction.
 *
 * <p><b>What this asserts</b> — stated so the next reader can ask what is NOT here (F2-454):
 * <ol>
 *   <li>no bare {@code orElseThrow()} in {@code src/main/java} — a <b>hard zero with no exemption
 *       map</b>, safe by F2-214's test because a named exception is always available and strictly
 *       more informative, so there is no correct code for it to fail on;</li>
 *   <li>{@code NoSuchElementException} is <b>deliberately unmapped</b>, which is the hard zero's
 *       whole rationale — asserted so that adding a handler is a decision somebody takes on
 *       purpose rather than a change that quietly retires this leg;</li>
 *   <li>the detector separates the bare form from a named one, in both directions;</li>
 *   <li>the comment stripping is load-bearing, measured rather than claimed;</li>
 *   <li>the corpus and the named form are floored above the largest <em>wrong</em> answer
 *       (F2-215), so a detector that stops matching fails instead of reporting a clean tree.</li>
 * </ol>
 *
 * <p><b>Scoped to {@code src/main/java} on purpose.</b> In a test an unexpected empty <em>is</em>
 * the failure and the stack trace is immediate, so a bare {@code orElseThrow()} there is
 * defensible; in main it reaches a caller as a 500 that names nothing.
 */
@DisplayName("F2-466 — no repository lookup fails with an exception no handler maps")
class UnnamedOptionalFailureContractTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path HANDLER = SOURCES.resolve(
        Path.of("com", "tss", "grc", "common", "exception", "GlobalExceptionHandler.java"));
    private static final Path NDI = SOURCES.resolve(Path.of("com", "tss", "grc", "module", "ndi",
        "submission", "service", "NdiSubmissionPackageService.java"));

    /** {@code .orElseThrow()} — an EMPTY argument list, the form that throws NoSuchElementException. */
    static final Pattern BARE = Pattern.compile("\\.orElseThrow\\s*\\(\\s*\\)");

    /** {@code .orElseThrow(<anything>)} — the named form, lambda or constructor reference alike. */
    static final Pattern NAMED = Pattern.compile("\\.orElseThrow\\s*\\(\\s*[^\\s)]");

    /** Any {@code @ExceptionHandler} whose type mentions NoSuchElementException. */
    private static final Pattern NSE_HANDLER =
        Pattern.compile("@ExceptionHandler\\s*\\([^)]*NoSuchElementException");

    private record Site(String file, int line, String text) {}

    // ─── legs ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no lookup in main sources throws an exception the handler does not map")
    void noBareOrElseThrowInMainSources() {
        List<Site> bare = new ArrayList<>();
        for (Path p : javaFiles()) {
            String rel = SOURCES.relativize(p).toString().replace('\\', '/');
            String src = RouteCitationContractTest.stripComments(read(p));
            Matcher m = BARE.matcher(src);
            while (m.find()) {
                int line = (int) src.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                bare.add(new Site(rel, line, lineAt(src, line)));
            }
        }
        assertThat(bare)
            .as("`orElseThrow()` with no argument throws NoSuchElementException, which no "
                + "@ExceptionHandler maps, so the caller gets an opaque 500 naming nothing. "
                + "There is no exemption map because a named exception is always available and "
                + "strictly more informative — pass one:%n%s",
                bare.stream().map(s -> "    " + s.file() + ":" + s.line() + "  " + s.text())
                    .reduce((a, b) -> a + System.lineSeparator() + b).orElse(""))
            .isEmpty();
    }

    @Test
    @DisplayName("NoSuchElementException is deliberately unmapped — that is the hard zero's reason")
    void noSuchElementExceptionIsDeliberatelyUnmapped() {
        String handler = RouteCitationContractTest.stripComments(read(HANDLER));
        assertThat(NSE_HANDLER.matcher(handler).find())
            .as("A handler for NoSuchElementException would be wrong, and mapping it is not the "
                + "fix for the leg above. The type cannot distinguish Iterator.next() on an "
                + "exhausted iterator — a genuine server bug a 404 would hide — from "
                + "Optional.orElseThrow(), which means not-found. F2-348 drew exactly this line "
                + "when it handled DateTimeParseException and deliberately left DateTimeException "
                + "alone. If you add one, the leg above needs re-arguing, not deleting.")
            .isFalse();
        // Anti-vacuity: the file really is the handler, so a moved/renamed class fails loudly
        // rather than making the absence above true for the wrong reason.
        assertThat(handler).contains("@ExceptionHandler(NoResultException.class)");
    }

    @Test
    @DisplayName("the detector separates the bare form from a named one, in both directions")
    void theDetectorSeparatesTheBareFormFromANamedOne() {
        for (String mustFlag : List.of(
                "repo.findById(id).orElseThrow();",
                "repo.findById(id).orElseThrow( );",
                "repo.findById(id)\n            .orElseThrow();")) {
            assertThat(BARE.matcher(mustFlag).find())
                .as("must flag the bare form: %s", mustFlag).isTrue();
        }
        for (String mustNotFlag : List.of(
                "repo.findById(id).orElseThrow(() -> new EntityNotFoundException(\"x\"));",
                "repo.findById(id).orElseThrow(EntityNotFoundException::new);",
                "repo.findById(id).orElseThrow(() -> new IllegalStateException(\n    \"x\"));")) {
            assertThat(BARE.matcher(mustNotFlag).find())
                .as("must NOT flag a named form — a gate that fails on correct code trains the "
                    + "next reader to edit the assertion (F2-214): %s", mustNotFlag).isFalse();
            assertThat(NAMED.matcher(mustNotFlag).find())
                .as("the named counter must see it: %s", mustNotFlag).isTrue();
        }
        assertThat(NAMED.matcher("repo.findById(id).orElseThrow();").find())
            .as("the named counter must not count the bare form").isFalse();
    }

    @Test
    @DisplayName("the comment stripping is load-bearing, measured rather than claimed")
    void theCommentStrippingIsLoadBearing() {
        String raw = read(NDI);
        assertThat(BARE.matcher(raw).find())
            .as("NdiSubmissionPackageService quotes the retired `.orElseThrow()` verbatim, because "
                + "this register's convention is that a fix quotes what it replaced (F2-193). That "
                + "is what makes the stripping necessary rather than decorative — if this ever "
                + "goes false, the leg below proves nothing and control F would survive.")
            .isTrue();
        assertThat(BARE.matcher(RouteCitationContractTest.stripComments(raw)).find())
            .as("...and with comments stripped the quotation is gone, so the hard zero above is "
                + "reading code rather than the epitaph (F2-193)")
            .isFalse();
    }

    @Test
    @DisplayName("the corpus and the named form are measured, above the largest wrong answer")
    void theCorpusAndTheNamedFormAreMeasured() {
        List<Path> files = javaFiles();
        int named = 0;
        for (Path p : files) {
            Matcher m = NAMED.matcher(RouteCitationContractTest.stripComments(read(p)));
            while (m.find()) named++;
        }
        // Measured 2026-09-05: 1,898 files, 586 named orElseThrow. The floors sit above the
        // largest WRONG answer, which for a walk is zero files and for a rotted pattern is zero
        // matches — a hard zero over a corpus the detector cannot see is the most reassuring
        // possible artefact (F2-205).
        assertThat(files).as("the source walk must reach the tree").hasSizeGreaterThan(1_500);
        assertThat(named)
            .as("the named form is the house idiom; if this collapses the hard zero above is "
                + "passing because the detector stopped matching, not because the tree is clean")
            .isGreaterThan(500);
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private static List<Path> javaFiles() {
        try (Stream<Path> s = Files.walk(SOURCES)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lineAt(String src, int line) {
        String[] rows = src.split("\n", -1);
        return line - 1 < rows.length ? rows[line - 1].trim() : "";
    }
}
