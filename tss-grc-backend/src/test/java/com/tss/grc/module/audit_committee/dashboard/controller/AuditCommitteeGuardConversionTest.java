package com.tss.grc.module.audit_committee.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tss.grc.module.audit_eng.controller.AuditEngagementController;
import com.tss.grc.module.audit_lc.controller.AuditLifecycleController;
import com.tss.grc.module.audit_lc.controller.FindingController;
import com.tss.grc.module.audit_lc.ia_plan.controller.IaController;

/**
 * F2-259 — the Audit Committee dashboard's three snapshot-mutating routes were
 * {@code hasAnyRole('BOARD_SECRETARY','PLATFORM_ADMIN')} with {@code BOARD_SECRETARY} held by no
 * user in dev or prod, so they admitted the platform administrator alone. Probed live: two personas
 * holding {@code AUDIT:WRITE} answered 403 on all three.
 *
 * <p>Three things this class exists to hold, and the reasons are asymmetric on purpose:
 * <ol>
 *   <li>the three WRITE routes ask for {@code hasPermission('AUDIT','WRITE')};</li>
 *   <li>the READ tier stays a role guard — converting it admits {@code VIEWER} and {@code AUDITOR}
 *       to a governance dashboard, which is §B3's {@code BoardPackController} defect;</li>
 *   <li>the ATTEST tier stays a role guard — converting it drops {@code EXTERNAL_AUDITOR}, which
 *       holds no {@code AUDIT} grant and is the one signatory whose independence is the point.</li>
 * </ol>
 *
 * <p>⚠ Asserted by <b>claim</b> — "this route cites the permission" — and never by banning
 * {@code hasAnyRole}: F2-254 had two pre-existing gates fail on a <em>correct</em> conversion for
 * exactly that reason, and a gate that fails on correct code trains the next reader to edit the
 * assertion rather than the code (F2-214).
 *
 * <p>⚠ Read by <b>reflection over {@code @PreAuthorize}</b>, never a source regex. The annotation
 * takes a compile-time constant, so a renamed or relocated constant cannot fool it (F2-187).
 */
@DisplayName("F2-259 · Audit Committee dashboard — the converted write tier, and the two tiers that stay")
class AuditCommitteeGuardConversionTest {

    private static final String WRITE_PERMISSION = "hasPermission('AUDIT','WRITE')";
    private static final String UNGUARDED = "<<UNGUARDED>>";

    /** The whole mapped surface, by name. A route absent here is a route nobody reviewed. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // READ tier — deliberately NOT converted (see the class javadoc)
        EXPECTED.put("live", "ROLE");
        EXPECTED.put("list", "ROLE");
        EXPECTED.put("get", "ROLE");
        // ATTEST tier — deliberately NOT converted
        EXPECTED.put("attest", "ROLE");
        // WRITE tier — the conversion
        EXPECTED.put("generate", WRITE_PERMISSION);
        EXPECTED.put("recordResolution", WRITE_PERMISSION);
        EXPECTED.put("finalizeSnapshot", WRITE_PERMISSION);
    }

    private static final Path MIGRATIONS =
            Path.of("src", "main", "resources", "db", "migration", "sqlserver");
    private static final Path V382 = MIGRATIONS.resolve(
            "V382__f259_audit_write_for_the_board_secretary_the_converted_guard_named.sql");

    /**
     * A mapped, unguarded route that exists ONLY so {@link #theReaderReportsAnUnguardedRoute()} can
     * prove {@link #guardsOf} REPORTS what it cannot explain instead of silently dropping it.
     *
     * <p>F2-258's harness produced this: the mutation making the reader {@code continue} past an
     * unguarded method SURVIVED, because on a corpus where every route is guarded the mutated reader
     * returns the identical map — so the mutant was not wrong, and there was nothing to kill it with
     * (F2-206). 🔑 A reader that omits what it cannot explain shrinks its own population, and a
     * smaller population reads exactly like a cleaner tree.
     */
    @SuppressWarnings("unused")
    @RequestMapping("/api/v1/test-only/f259-synthetic")
    private static final class SyntheticUnguardedController {
        @PostMapping("/unguarded")
        void unguarded() {
            // deliberately carries no @PreAuthorize
        }
    }

    private static Map<String, String> guards() {
        return guardsOf(AuditCommitteeDashboardController.class);
    }

    private static Map<String, String> guardsOf(Class<?> controller) {
        Map<String, String> found = new LinkedHashMap<>();
        for (Method m : controller.getDeclaredMethods()) {
            if (m.isSynthetic() || !isMapped(m)) {
                continue;
            }
            PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
            found.put(m.getName(), pa == null ? UNGUARDED : pa.value());
        }
        return found;
    }

    private static boolean isMapped(Method m) {
        return Stream.of(m.getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().endsWith("Mapping"));
    }

    private static String source(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("all three snapshot-mutating routes ask for hasPermission('AUDIT','WRITE')")
    void everyWriteRouteAsksForThePermission() {
        Map<String, String> actual = guards();
        List<String> converted = new ArrayList<>();
        EXPECTED.forEach((name, want) -> {
            if (WRITE_PERMISSION.equals(want)) {
                converted.add(name);
                assertThat(actual)
                        .as("F2-259: %s must be guarded by the permission, not by a role name. "
                                + "BOARD_SECRETARY exists in iam.roles and is held by nobody in "
                                + "either database, so the role guard admitted PLATFORM_ADMIN alone.",
                                name)
                        .containsEntry(name, WRITE_PERMISSION);
            }
        });
        assertThat(converted)
                .as("the write tier is exactly generate / recordResolution / finalizeSnapshot — "
                        + "if a fourth mutating route appears it needs a decision, not a default")
                .containsExactlyInAnyOrder("generate", "recordResolution", "finalizeSnapshot");
        assertThat(actual.values().stream().filter(WRITE_PERMISSION::equals).count())
                .as("exactly three routes cite the permission; a count, not a contains, so one "
                        + "route reverting cannot hide behind the other two (F2-258)")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("the READ tier is deliberately still a role guard — converting it admits VIEWER")
    void theReadTierIsDeliberatelyStillARoleGuard() {
        Map<String, String> actual = guards();
        for (String route : List.of("live", "list", "get")) {
            String guard = actual.get(route);
            assertThat(guard)
                    .as("F2-259: %s must stay a ROLE guard. hasPermission('AUDIT','READ') is held "
                            + "by 7 live roles in both databases including VIEWER and AUDITOR, so "
                            + "converting it would widen a governance dashboard's read to any "
                            + "authenticated reader — §B3's BoardPackController defect. Four live "
                            + "non-administrator roles already read this dashboard.", route)
                    .startsWith("hasAnyRole(")
                    .doesNotContain("hasPermission(");
            assertThat(guard)
                    .as("RISK_MANAGER is one of the four live readers; dropping it would be the "
                            + "narrowing this slice exists to avoid elsewhere")
                    .contains("'RISK_MANAGER'");
        }
    }

    @Test
    @DisplayName("the ATTEST tier is deliberately still a role guard — converting it drops EXTERNAL_AUDITOR")
    void theAttestTierIsDeliberatelyStillARoleGuard() {
        String guard = guards().get("attest");
        assertThat(guard)
                .as("F2-259: attest must stay a ROLE guard. EXTERNAL_AUDITOR holds no AUDIT grant "
                        + "at all, so hasPermission('AUDIT','WRITE') would drop the one signatory "
                        + "whose independence is the point — and who may not be a platform "
                        + "principal at all (F2-256's EXTERNAL_AUDIT rule).")
                .startsWith("hasAnyRole(")
                .doesNotContain("hasPermission(")
                .contains("'EXTERNAL_AUDITOR'");
    }

    @Test
    @DisplayName("the inventory is exhaustive — every mapped route is accounted for and guarded")
    void theInventoryIsExhaustive() {
        Map<String, String> actual = guards();
        assertThat(actual.keySet())
                .as("F2-259: a route missing from EXPECTED is a route nobody reviewed. Add it here "
                        + "with a verdict in the same commit that adds the route.")
                .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        assertThat(actual.values())
                .as("no mapped route on this controller may be unguarded")
                .doesNotContain(UNGUARDED);
        EXPECTED.forEach((name, want) -> {
            if (!WRITE_PERMISSION.equals(want)) {
                assertThat(actual.get(name))
                        .as("%s is recorded as a role guard and must remain one", name)
                        .startsWith("hasAnyRole(");
            }
        });
    }

    @Test
    @DisplayName("AUDIT:WRITE is catalogued by a migration — a guard citing an absent pair can only answer false")
    void theTargetPermissionIsCatalogued() {
        // Whitespace-tolerant: V-series files write ('AUDIT','WRITE', with no space, and an
        // assertion that depends on a formatting choice fails the day somebody reformats the
        // file (F2-258).
        Pattern pair = Pattern.compile("\\(\\s*N?'AUDIT'\\s*,\\s*N?'WRITE'");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            files.filter(p -> p.getFileName().toString().endsWith(".sql")).forEach(p -> {
                Matcher m = pair.matcher(source(p));
                if (m.find()) {
                    hits.add(p.getFileName().toString());
                }
            });
        } catch (IOException e) {
            throw new AssertionError("cannot walk " + MIGRATIONS.toAbsolutePath(), e);
        }
        assertThat(hits)
                .as("F2-255: ModulePermissionEvaluator string-matches MODULE_ACTION, so a guard "
                        + "citing a pair no migration creates reduces to the PLATFORM_ADMIN code "
                        + "bypass. AUDIT:WRITE must be catalogued somewhere in the migration set.")
                .isNotEmpty();
    }

    @Test
    @DisplayName("V382 grants the converted guard's named role, so the conversion drops nobody")
    void theConvertedGuardsNamedRoleKeepsItsClaim() {
        assertThat(Files.exists(V382))
                .as("F2-259: V382 is the grant that stops this conversion narrowing the shipped "
                        + "audience. BOARD_SECRETARY held AUDIT:READ and AUDIT:EXPORT and not "
                        + "AUDIT:WRITE, so converting alone would have retired the persona the "
                        + "guard was written for.")
                .isTrue();
        String sql = source(V382);
        assertThat(sql)
                .as("the (role, module, action) triple must be a DECLARED literal — deriving it "
                        + "from iam.role_permissions cannot tell 'already granted' from 'nothing "
                        + "matched', so it succeeds silently (the grant landmine)")
                .contains("N'BOARD_SECRETARY', N'AUDIT', N'WRITE'");
        // A COUNT, not a contains: V382 carries six THROWs, so asserting the literal would be
        // satisfied by any one of the other five (F2-258's own gate hole).
        assertThat(sql.split("THROW 51382", -1).length - 1)
                .as("all six self-checks must still be present: 0 role seated, 0b pair "
                        + "catalogued, 1 the exact grant set, 2 containment, 3 anti-vacuity, "
                        + "4 no DELETE")
                .isEqualTo(6);
        assertThat(sql)
                .as("check 0 is the load-bearing pre-flight — without a seated role the grant "
                        + "chain matches nothing and reports success")
                .contains("The grant chain would match nothing and report success");
    }

    @Test
    @DisplayName("the peer controllers under this SKU use the same permission — the target is the module's own convention")
    void thePeerControllersUseTheSamePermission() {
        // This is the justification for choosing AUDIT:WRITE rather than inventing a permission,
        // so it is asserted rather than left in a comment. ⚠ The SKU code in @RequireModule is
        // AUDIT_LC and the *permission* module is AUDIT — taking the module name from the SKU
        // annotation is what made this look like F2-255's catalogue gap, since neither AUDIT_LC
        // nor AUDIT_COMMITTEE has any catalogued permission at all.
        List<Class<?>> peers = List.of(AuditLifecycleController.class, FindingController.class,
                IaController.class, AuditEngagementController.class);
        int citing = 0;
        for (Class<?> peer : peers) {
            Map<String, String> g = guardsOf(peer);
            assertThat(g)
                    .as("%s should expose mapped routes", peer.getSimpleName())
                    .isNotEmpty();
            assertThat(g.values().stream().anyMatch(v -> v.contains("hasPermission('AUDIT',")))
                    .as("F2-253: the module's own convention settles the conversion target. %s "
                            + "guards with hasPermission('AUDIT', …); if that stops being true "
                            + "the target for AuditCommitteeDashboardController needs revisiting, "
                            + "not this assertion relaxing.", peer.getSimpleName())
                    .isTrue();
            // ⚠ The anyMatch above is too weak on its own, and the mutation harness proved it:
            // moving TWO of FindingController's five routes off the permission left three still
            // citing it, so the leg SURVIVED while the convention it certifies was eroding.
            // Measured 55 of 55 guarded peer routes cite the pair, so the claim that actually
            // holds is the NEGATIVE one — and stating it that way tolerates a legitimate composed
            // or record-ownership guard (F2-254: never ban hasAnyRole outright; a gate that fails
            // on correct code trains the next reader to edit the assertion).
            List<String> pureRoleGuards = g.values().stream()
                    .filter(v -> v.startsWith("hasAnyRole(") && !v.contains("hasPermission("))
                    .toList();
            assertThat(pureRoleGuards)
                    .as("F2-259: %s must have NO pure role guard. All 55 guarded routes across "
                            + "these four peers cite hasPermission('AUDIT', …), and that unanimity "
                            + "is the whole justification for converting "
                            + "AuditCommitteeDashboardController's write tier to the same pair. A "
                            + "pure role guard appearing here means the justification has moved — "
                            + "go and re-read it. A COMPOSED guard is fine and deliberately still "
                            + "passes.", peer.getSimpleName())
                    .isEmpty();
            citing += (int) g.values().stream().filter(v -> v.contains("hasPermission('AUDIT',")).count();
        }
        assertThat(citing)
                .as("anti-vacuity: a peer class renamed, emptied or no longer resolving would make "
                        + "every assertion above pass over nothing. Measured 55; the floor sits "
                        + "well below that and far above the largest WRONG answer, which is a "
                        + "handful of stray routes surviving a convention that has moved (F2-215).")
                .isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("the guard reader REPORTS an unguarded route rather than skipping it")
    void theReaderReportsAnUnguardedRoute() {
        Map<String, String> synthetic = guardsOf(SyntheticUnguardedController.class);
        assertThat(synthetic)
                .as("F2-258: a reader that skips what it cannot explain shrinks its own population, "
                        + "and a smaller population reads exactly like a cleaner tree. The mutation "
                        + "making this reader `continue` past an unguarded method is only killable "
                        + "against a corpus that HAS one.")
                .containsExactly(Map.entry("unguarded", UNGUARDED));
    }
}
