package com.tss.grc.module.ai.lifecycle.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-258 — the AI model-lifecycle write and approve tiers admitted the platform administrator and
 * (for creates) one other role, on three routes wired to three routed pages.
 *
 * <h3>What was wrong</h3>
 * <p>The creates were {@code hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','CIO','DPO')} and the three
 * {@code /transition} routes {@code hasAnyRole('PLATFORM_ADMIN','CRO','CIO','DPO')}. {@code CIO},
 * {@code CRO} and {@code DPO} are held by <b>no user</b> in dev or prod, so the transitions were
 * administrator-only. Probed live before the change: {@code rmanager} and {@code compliance} both
 * hold {@code AI:WRITE} and both answered <b>403</b> on all three transitions, over 16 validations,
 * 11 retraining events and 13 recertifications identical in both databases.
 *
 * <h3>Why this is not the ghost-role defect, and why the previous remedy did not move it</h3>
 * <p>V373 was written for these routes — it names {@code AiLifecycleController#transition} — and
 * chose to SEAT {@code CIO} and {@code CRO} rather than convert, on the measured ground that four of
 * the five modules reciting them have no catalogued permissions at all. True of those four; not of
 * this one, where {@code AI:READ}/{@code WRITE}/{@code EXPORT} have been catalogued since V313. Its
 * closing line says the 79 sites are now "satisfiable" — which is exactly F2-253's distinction:
 * {@code hasAnyRole} tests for a <i>granted authority</i>, so a role assigned to nobody still admits
 * nobody, and seating without holders left the route as closed as it found it.
 *
 * <h3>Why reflective rather than a source scan or a `@WebMvcTest`</h3>
 * <p>{@code @PreAuthorize} takes a compile-time constant, so reflection sees the resolved expression
 * and cannot be fooled by a constant being renamed, re-spelled or moved — the failure mode a source
 * regex has (F2-187). Same instrument as the sibling {@code AiSuggestionGuardTest}.
 *
 * <h3>The leg that matters most is the one that refuses to finish the sweep</h3>
 * <p>{@link #theReadTierIsDeliberatelyStillARoleGuard()} fails the build if the READ tier is
 * converted too. {@code AUDITOR} is named there, is held, and holds {@code AI:EXPORT} but
 * <b>not</b> {@code AI:READ} — the only role in either database with that shape — so
 * {@code hasPermission('AI','READ')} would drop the examiner. F2-254 added the same kind of leg for
 * the BCM read guards after making exactly this measurement, and F2-253's rule is the general form:
 * correcting a role guard on the write side widens access and doing it on the read side can narrow
 * it, so the two halves are not one sweep.
 */
class AiLifecycleGuardConversionTest {

    private static final String WRITE_PERMISSION = "hasPermission('AI','WRITE')";

    /**
     * Every mapped method and the guard it must carry, listed exhaustively rather than
     * pattern-matched so that adding a route forces an edit here — the inventory leg is what makes
     * that mandatory.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // reads: role-guarded ON PURPOSE (see theReadTierIsDeliberatelyStillARoleGuard)
        EXPECTED.put("listValidations", "ROLE");
        EXPECTED.put("listRetrainings", "ROLE");
        EXPECTED.put("listRecerts", "ROLE");
        EXPECTED.put("timeline", "ROLE");
        EXPECTED.put("dashboard", "ROLE");
        // creates
        EXPECTED.put("create", WRITE_PERMISSION);
        EXPECTED.put("createRetraining", WRITE_PERMISSION);
        EXPECTED.put("createRec", WRITE_PERMISSION);
        // decisions
        EXPECTED.put("transition", WRITE_PERMISSION);
        EXPECTED.put("transitionRetraining", WRITE_PERMISSION);
        EXPECTED.put("transitionRec", WRITE_PERMISSION);
    }

    /**
     * A mapped route with no {@code @PreAuthorize} at all, so {@link #guards} can be shown to
     * REPORT that rather than skip it.
     *
     * <p>Why this fixture exists: the mutation that made the reader skip unguarded methods SURVIVED
     * the whole gate, because every route on the real controller is guarded — so the mutated reader
     * returns the identical map and is not wrong on this corpus (F2-206). It is only wrong the day an
     * unguarded route appears, which is the day the gate is needed. A synthetic corpus is what turns
     * an equivalent mutant into a killed one (F2-195).
     */
    @SuppressWarnings("unused")
    private static final class SyntheticUnguardedController {
        @PostMapping("/synthetic")
        void unguarded() { }
    }

    @Test
    @DisplayName("F2-258 self-test: the reader REPORTS an unguarded route rather than skipping it")
    void theReaderReportsAnUnguardedRoute() {
        assertThat(guardsOf(SyntheticUnguardedController.class))
            .as("""
                A reader that omits what it cannot explain shrinks its own population, and a smaller \
                population reads exactly like a cleaner tree -- the quietest way for a scan to be \
                wrong. Nothing else in this class can detect that, because the real controller has no \
                unguarded route for it to hide.""")
            .containsEntry("unguarded", "<<UNGUARDED>>");
    }

    private static Map<String, String> guards() {
        return guardsOf(AiLifecycleController.class);
    }

    private static Map<String, String> guardsOf(Class<?> controller) {
        Map<String, String> found = new TreeMap<>();
        for (Method m : controller.getDeclaredMethods()) {
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                          || m.isAnnotationPresent(PostMapping.class);
            if (!mapped) {
                continue;
            }
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? "<<UNGUARDED>>" : pa.value());
        }
        return found;
    }

    @Test
    @DisplayName("F2-258: every create and every transition asks for AI:WRITE, not a role name")
    void everyWriteAsksForThePermission() {
        Map<String, String> found = guards();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            if (!WRITE_PERMISSION.equals(e.getValue())) {
                continue;
            }
            String actual = found.get(e.getKey());
            if (!WRITE_PERMISSION.equals(actual)) {
                wrong.add(e.getKey() + " -> " + actual);
            }
        }
        assertThat(wrong)
            .as("""
                Each of these six routes must ask the catalogue. Before F2-258 the three transitions \
                were hasAnyRole('PLATFORM_ADMIN','CRO','CIO','DPO') — three names held by nobody — so \
                a live probe as rmanager and compliance, both holding AI:WRITE, got 403 on all three. \
                A role guard gets no PLATFORM_ADMIN bypass either, so the administrator passed only \
                because the expression happens to name PLATFORM_ADMIN.""")
            .isEmpty();
        assertThat(found.values().stream().filter(WRITE_PERMISSION::equals).count())
            .as("exactly six routes on this controller are permission-guarded writes")
            .isEqualTo(6);
    }

    @Test
    @DisplayName("F2-258: the READ tier stays a role guard — converting it would DROP the examiner")
    void theReadTierIsDeliberatelyStillARoleGuard() {
        Map<String, String> found = guards();
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            if (!"ROLE".equals(e.getValue())) {
                continue;
            }
            String actual = found.get(e.getKey());
            assertThat(actual)
                .as("""
                    %s must still be the role guard. AUDITOR is named on it, IS held, and holds \
                    AI:EXPORT and NOT AI:READ — measured in dev and prod, the only role in either \
                    database with that shape, and deliberate: MlModelController's EXPORT_GUARD ANDs \
                    its own role list naming AUDITOR. So hasPermission('AI','READ') here would take \
                    this tier from five live roles to four and drop the examiner. This leg exists to \
                    fail on somebody "finishing" F2-258's conversion; if the read audience is meant \
                    to change, decide whether an examiner may read every AI model record and grant \
                    AI:READ in a migration first.""", e.getKey())
                .startsWith("hasAnyRole(")
                .contains("'AUDITOR'")
                .doesNotContain("hasPermission(");
        }
    }

    @Test
    @DisplayName("F2-258: no route is left unguarded, and the inventory is exhaustive")
    void theInventoryIsExhaustive() {
        Map<String, String> found = guards();
        assertThat(found.keySet())
            .as("""
                A route added to this controller must appear in EXPECTED with a decision about which \
                tier it belongs to. Without this leg the two assertions above would silently certify \
                a shrinking population — the failure mode where a detector that stops matching reads \
                exactly like a clean tree.""")
            .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        assertThat(found.size())
            .as("anti-vacuity: reflection must resolve all eleven mapped methods")
            .isEqualTo(11);
        assertThat(found.values()).doesNotContain("<<UNGUARDED>>");
    }

    @Test
    @DisplayName("F2-258: AI:WRITE is created by a migration — the conversion's target must exist")
    void theTargetPermissionIsCatalogued() throws IOException {
        String all = migrations();
        // Whitespace-tolerant on purpose: V313 writes ('AI','WRITE', with no space, and an
        // assertion that depends on a formatting choice fails on a correct tree the day somebody
        // reformats the file -- the one failure a gate must never have (F2-214).
        assertThat(java.util.regex.Pattern
                .compile("\\(\\s*N?'AI'\\s*,\\s*N?'WRITE'")
                .matcher(all).find())
            .as("AI:WRITE must be created by a migration in the packaged set")
            .isTrue();
        assertThat(all)
            .as("""
                The authority for "does this permission exist in the platform" is the MIGRATION SET, \
                never either database — F2-255's rule, which found AUDIT_LOG:WRITE cited by a live \
                guard and created by no migration at all, so the evaluator could only ever answer \
                false and the route reduced to the PLATFORM_ADMIN code bypass. If AI:WRITE were in \
                that state this conversion would have CLOSED the six routes rather than opened them.""")
            .contains("'AI','WRITE'");
    }

    @Test
    @DisplayName("F2-258: V381 keeps CIO and CRO in the audience, as declared literals")
    void theConvertedGuardsNamedRolesKeepTheirClaim() throws IOException {
        Path v381 = migrations(true).stream()
            .filter(p -> p.getFileName().toString().startsWith("V381__"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "V381 is missing. F2-258's conversion drops CIO and CRO — the two names the retired "
                + "hasAnyRole lists carried that hold no AI permission — unless that migration "
                + "grants them AI:READ + AI:WRITE. Narrowing a shipped audience is a policy change, "
                + "not a refactor."));
        String sql = Files.readString(v381);
        for (String triple : List.of("N'CIO', N'AI', N'READ'", "N'CIO', N'AI', N'WRITE'",
                                     "N'CRO', N'AI', N'READ'", "N'CRO', N'AI', N'WRITE'")) {
            assertThat(sql)
                .as("""
                    V381 must DECLARE (%s) as a literal. The repo's grant landmine is that the \
                    INSERT ... SELECT ... JOIN iam.roles JOIN iam.permissions WHERE NOT EXISTS idiom \
                    grants nothing when either side is catalogued later in the chain, and NOT EXISTS \
                    cannot tell "already granted" from "nothing matched" — so it succeeds silently. \
                    Literals plus a THROWing count check are what close that.""", triple)
                .contains(triple);
        }
        // Count, not contains. The first draft asserted the token "THROW 51381" and the mutation
        // harness showed that SURVIVED: V381 carries five of them, so deleting the one that matters
        // -- check 0, the pre-flight that refuses when CIO/CRO are not seated -- left the other four
        // satisfying the assertion. F2-194's substring-satisfied-by-something-else, in a gate written
        // after quoting it. Both the population and the load-bearing member are asserted.
        long throws_ = sql.lines().filter(l -> l.contains("THROW 51381")).count();
        assertThat(throws_)
            .as("""
                V381's five self-checks: the two pre-flights (roles seated, permissions catalogued), \
                the grant count, containment, and the anti-vacuity floor under the AI:WRITE tier. A \
                guarded insert that silently did nothing looks identical to one that worked, which is \
                the whole reason this file THROWs instead of trusting its own INSERT.""")
            .isEqualTo(5);
        assertThat(sql)
            .as("""
                and check 0 specifically -- the pre-flight that refuses when V373 has not run. \
                Without it the grant chain cannot resolve N'CIO' and inserts nothing while reporting \
                success, which is the repo's grant landmine exactly.""")
            .contains("V381 check 0: CIO and CRO are not both seated");
    }

    private static final Path MIGRATIONS =
        Path.of("src", "main", "resources", "db", "migration", "sqlserver");

    private static List<Path> migrations(boolean paths) throws IOException {
        try (var files = Files.list(MIGRATIONS)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList();
        }
    }

    private static String migrations() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path p : migrations(true)) {
            sb.append(Files.readString(p)).append('\n');
        }
        return sb.toString();
    }
}
