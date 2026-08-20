package com.tss.grc.module.rrp.controller;

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

/**
 * F2-260 — {@code RrpController}'s three tiers were pure role guards, and the APPROVE one
 * admitted the platform administrator alone: {@code CRO} and {@code CFO} are held by no user in
 * dev or prod, and {@code CRO} is absent from prod's {@code iam.roles} entirely because V373 is
 * pending. So {@code POST /cycles/{id}/submit} — the route that lodges the Recovery &amp;
 * Resolution Plan with SAMA — was administrator-only, and unlike F2-253's attrition sites there
 * was no tenant remedy: {@code RRP} had <b>no catalogued permission of any kind</b>, so nothing
 * could be granted because nothing existed to grant (F2-257's filed gap, first of five closed).
 *
 * <p>What this class holds:
 * <ol>
 *   <li>all three tiers now ask for the permission, and the inventory is exhaustive;</li>
 *   <li>{@code #submit} is the <b>only</b> APPROVE route — a second one needs a decision;</li>
 *   <li>V383 catalogues exactly the three actions the controller expresses, as declared
 *       literals, with its self-checks intact;</li>
 *   <li>{@code RISK_MANAGER} is <b>not</b> granted {@code RRP:APPROVE}, so the preparer cannot
 *       lodge — the one separation the shipped guard expressed;</li>
 *   <li>V383 asserts none of its preconditions against <b>tenant data</b>.</li>
 * </ol>
 *
 * <p>⚠ Asserted by <b>claim</b> and never by banning {@code hasAnyRole} (F2-254; F2-214), and read
 * by <b>reflection over {@code @PreAuthorize}</b> rather than a source regex, since the annotation
 * takes a compile-time constant and a renamed constant cannot fool it (F2-187).
 */
@DisplayName("F2-260 · RRP — the SAMA filing route, and the permission module that had to exist first")
class RrpGuardConversionTest {

    private static final String READ = "hasPermission('RRP','READ')";
    private static final String WRITE = "hasPermission('RRP','WRITE')";
    private static final String APPROVE = "hasPermission('RRP','APPROVE')";
    private static final String UNGUARDED = "<<UNGUARDED>>";

    /** Route -> tier. Exhaustive: a route absent here is a route nobody reviewed. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("createCycle", WRITE);
        EXPECTED.put("updateCycle", WRITE);
        EXPECTED.put("listCycles", READ);
        EXPECTED.put("getCycle", READ);
        EXPECTED.put("transition", WRITE);
        EXPECTED.put("submit", APPROVE);
        EXPECTED.put("acknowledge", WRITE);
        EXPECTED.put("listOptions", READ);
        EXPECTED.put("addOption", WRITE);
        EXPECTED.put("updateOption", WRITE);
        EXPECTED.put("listCriticalFunctions", READ);
        EXPECTED.put("addCriticalFunction", WRITE);
        EXPECTED.put("listIndicators", READ);
        EXPECTED.put("addIndicator", WRITE);
        EXPECTED.put("recordMeasurement", WRITE);
        EXPECTED.put("listEvents", READ);
        EXPECTED.put("dashboard", READ);
    }

    private static final Path MIGRATIONS =
            Path.of("src", "main", "resources", "db", "migration", "sqlserver");
    private static final Path V383 =
            MIGRATIONS.resolve("V383__f260_catalogue_the_rrp_permission_module.sql");

    /**
     * A mapped, unguarded route existing only to prove {@link #guardsOf} REPORTS what it cannot
     * explain rather than silently dropping it — F2-258's harness produced this exact hole, where
     * the mutation making the reader skip an unguarded method survived because on a fully guarded
     * corpus the mutated reader returns the identical map (F2-206). 🔑 A reader that omits what it
     * cannot explain shrinks its own population, and a smaller population reads exactly like a
     * cleaner tree.
     */
    @SuppressWarnings("unused")
    @RequestMapping("/api/v1/test-only/f260-synthetic")
    private static final class SyntheticUnguardedController {
        @PostMapping("/unguarded")
        void unguarded() {
            // deliberately carries no @PreAuthorize
        }
    }

    private static Map<String, String> guards() {
        return guardsOf(RrpController.class);
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

    /**
     * SQL with its comments removed.
     *
     * <p>⚠ Required, and it fired on the first run: {@link #theMigrationDoesNotAssertTenantData()}
     * bans {@code iam.user_roles} from V383, and V383's own header <b>names that table</b> in the
     * paragraph explaining why the check it replaced was removed. A token scan then reads the
     * epitaph as the corpse (F2-193, F2-202, F2-205 — prose about code, stored as code, invents its
     * own subject). One left-to-right alternation, never two sequential passes (F2-198/F2-211): at a
     * {@code --} an inner {@code /*} can never open a block, and at a block start a {@code --}
     * cannot truncate it.
     */
    private static final Pattern SQL_COMMENT =
            Pattern.compile("--[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String stripped(Path p) {
        return SQL_COMMENT.matcher(source(p)).replaceAll(" ");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every route asks for its RRP permission tier, and the inventory is exhaustive")
    void everyRouteAsksForItsPermissionTier() {
        Map<String, String> actual = guards();
        assertThat(actual.keySet())
                .as("F2-260: a route missing from EXPECTED is a route nobody reviewed. Add it with "
                        + "a tier in the same commit that adds the route.")
                .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
        assertThat(actual.values())
                .as("no mapped route on this controller may be unguarded")
                .doesNotContain(UNGUARDED);
        EXPECTED.forEach((name, tier) ->
                assertThat(actual)
                        .as("F2-260: %s must ask for %s. The old guard was a role list, and its "
                                + "APPROVE tier named CRO and CFO — held by no user in either "
                                + "database.", name, tier)
                        .containsEntry(name, tier));
        // Counts, not merely contains: one route reverting must not hide behind the others (F2-258).
        assertThat(actual.values().stream().filter(READ::equals).count()).as("READ routes").isEqualTo(7L);
        assertThat(actual.values().stream().filter(WRITE::equals).count()).as("WRITE routes").isEqualTo(9L);
        assertThat(actual.values().stream().filter(APPROVE::equals).count()).as("APPROVE routes").isEqualTo(1L);
    }

    @Test
    @DisplayName("submit is the only APPROVE route — the SAMA filing is one decision, not a tier")
    void submitIsTheOnlyApproveRoute() {
        List<String> approvers = new ArrayList<>();
        guards().forEach((name, guard) -> {
            if (APPROVE.equals(guard)) {
                approvers.add(name);
            }
        });
        assertThat(approvers)
                .as("F2-260: RRP:APPROVE guards exactly one route, #submit — lodging the plan with "
                        + "SAMA. RrpService.transition explicitly refuses the SUBMITTED edge (\"Use "
                        + "the dedicated /submit endpoint\"), so this is the single forward exit from "
                        + "APPROVED. A second APPROVE route is a governance decision about what else "
                        + "the filing authority may do, not a tier to grow by default.")
                .containsExactly("submit");
        assertThat(guards().get("transition"))
                .as("F2-260: /transition must stay on WRITE. It drives DRAFT -> IN_PREPARATION -> "
                        + "BOARD_REVIEW -> APPROVED; moving it to APPROVE would take preparation "
                        + "away from RISK_MANAGER, which is the audience that actually holds it.")
                .isEqualTo(WRITE);
    }

    @Test
    @DisplayName("V383 catalogues exactly the three tiers this controller expresses")
    void theCatalogueMigrationDeclaresExactlyTheThreeTiers() {
        assertThat(Files.exists(V383))
                .as("F2-260: RRP had no catalogued permission of any kind, so a guard citing "
                        + "hasPermission('RRP',…) could only ever answer false — the route would "
                        + "reduce to the PLATFORM_ADMIN code bypass (F2-255). V383 is what makes the "
                        + "conversion mean anything.")
                .isTrue();
        String sql = source(V383);
        for (String action : List.of("READ", "WRITE", "APPROVE")) {
            assertThat(sql)
                    .as("V383 must catalogue ('RRP','%s') as a declared literal", action)
                    .containsPattern(Pattern.compile(
                            "N?'RRP'\\s*,\\s*N?'" + action + "'\\s*,\\s*N?'RRP_" + action + "'"));
        }
        assertThat(sql)
                .as("V362's landmine: it classified actions by METHOD SHAPE and promoted whole "
                        + "WRITE tiers to APPROVE in four of six modules. V383's check 1b refuses a "
                        + "fourth RRP action so a later tier is a decision rather than a default.")
                .contains("action NOT IN (N'READ', N'WRITE', N'APPROVE')");
        // A COUNT, not a contains: V383 carries several THROWs, so asserting one literal would be
        // satisfied by any of the others (F2-258's own gate hole).
        assertThat(sql.split("THROW 51383", -1).length - 1)
                .as("all seven self-checks must remain: 0 roles seated, 1 exactly three actions, "
                        + "1b no fourth action, 2 grant count, 2b APPROVE count, 3 containment, "
                        + "4 the WRITE/APPROVE separation, 6 catalogue anti-vacuity")
                .isEqualTo(8);
        assertThat(sql)
                .as("check 0 is the load-bearing pre-flight — without the roles seated the grant "
                        + "chain matches nothing and reports success (the grant landmine)")
                .contains("The grant chain would match nothing and report success");
    }

    @Test
    @DisplayName("the preparer may not lodge — RISK_MANAGER is not granted RRP:APPROVE")
    void thePreparerIsNotGrantedTheFilingAuthority() {
        String sql = source(V383);
        // The grant block is a VALUES list of (role, action) pairs. Extract it and check the
        // APPROVE pairs by name, so a role added to that tier fails the build.
        Matcher block = Pattern.compile(
                "INSERT INTO iam\\.role_permissions[\\s\\S]*?\\)\\s*AS t\\(role_name, action\\)")
                .matcher(sql);
        assertThat(block.find()).as("V383's declared grant block must be findable").isTrue();
        String grants = block.group();
        List<String> approveRoles = new ArrayList<>();
        Matcher pair = Pattern.compile("\\(\\s*N'([A-Z_]+)'\\s*,\\s*N'([A-Z]+)'\\s*\\)").matcher(grants);
        while (pair.find()) {
            if ("APPROVE".equals(pair.group(2))) {
                approveRoles.add(pair.group(1));
            }
        }
        assertThat(approveRoles)
                .as("F2-260: RRP:APPROVE is granted to CRO and CFO (the shipped audience, so a "
                        + "tenant administrator assigning either gets the capability without a jar) "
                        + "plus GRC_ADMINISTRATOR (the unlock — the only live role, and it holds "
                        + "APPROVE on BCM, RISK and ADMIN alike). RISK_MANAGER is deliberately "
                        + "ABSENT: it holds RISK:APPROVE so convention argues for it, and the "
                        + "shipped guard pointedly did not name it. Granting it would let the "
                        + "persona that prepares and board-approves the cycle also lodge it with "
                        + "SAMA, retiring the one separation this controller expresses.")
                .containsExactlyInAnyOrder("CRO", "CFO", "GRC_ADMINISTRATOR")
                .doesNotContain("RISK_MANAGER");
        assertThat(sql)
                .as("and the separation is enforced in the database too, not only by this list")
                .contains("V383 check 4");
    }

    @Test
    @DisplayName("V383 asserts none of its preconditions against tenant data")
    void theMigrationDoesNotAssertTenantData() {
        String executable = stripped(V383);
        String sql = source(V383);
        assertThat(executable)
                .as("self-test: the comment stripper must be load-bearing here — V383's own header "
                        + "names iam.user_roles while explaining why the removed check is gone, so a "
                        + "raw scan reads the epitaph as the corpse (F2-193)")
                .doesNotContain("MUST NOT ASSERT TENANT DATA");
        // ⚠⚠ The rule this leg encodes, learned by writing the check and removing it: A MIGRATION
        // MAY ASSERT ITS OWN WRITES AND MUST NOT ASSERT TENANT DATA. The natural fifth check here
        // was "does any ACTIVE user now hold a role granted RRP:APPROVE?" — F2-253's satisfiable-
        // versus-satisfied distinction, and the whole point of the file. It would THROW on a
        // brand-new deployment that has a bootstrap administrator and no GRC_ADMINISTRATOR seated,
        // blocking V383 and every migration after it: a self-check that fails on correct usage,
        // which is the one failure a check must never have (F2-214, at the schema). The roles this
        // file GRANTS are safe to require because migrations before it create them (V362 seats
        // BOARD_SECRETARY and CFO, V373 seats CRO — verified). Who HOLDS them is not.
        assertThat(executable)
                .as("V383 must not read iam.user_roles or iam.users: role assignments are tenant "
                        + "data, and a fresh Flyway install has none. 'Did this unlock anybody?' is "
                        + "proved by the live probe, not by the migration.")
                .doesNotContain("iam.user_roles")
                .doesNotContain("iam.users");
        assertThat(sql)
                .as("and the reason is recorded where the removed check used to be, so the next "
                        + "author does not re-add it")
                .contains("MUST NOT ASSERT TENANT DATA");
        // ⚠ Scoped to V383 on purpose. 10 migrations reference iam.user_roles and most are DDL —
        // creating the table, indexing it, adding a foreign key — so a tree-wide ban would fail on
        // correct code. The general rule is narrower than a token ban ("no migration may THROW
        // conditioned on iam.user_roles contents") and needs dataflow to detect, so the population
        // is filed rather than asserted (F2-188).
    }

    @Test
    @DisplayName("the guard reader REPORTS an unguarded route rather than skipping it")
    void theReaderReportsAnUnguardedRoute() {
        assertThat(guardsOf(SyntheticUnguardedController.class))
                .as("F2-258: the mutation making this reader skip an unguarded method is only "
                        + "killable against a corpus that HAS one.")
                .containsExactly(Map.entry("unguarded", UNGUARDED));
    }
}
