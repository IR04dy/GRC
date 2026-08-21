package com.tss.grc.security;

import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-13a — emits the authoritative list of {@code MODULE:ACTION} pairs the code actually
 * guards on, so grant tightness can be measured against the catalogue in both directions.
 *
 * <p><b>Why this exists as a test rather than a grep.</b> The two directions of grant
 * tightness are:
 * <ol>
 *   <li><b>Code guards on a permission nobody holds</b> — fail-closed dead code. This is
 *       <b>F2-23</b> ({@code ADMIN:APPROVE} guarded 59 committee methods while not existing in
 *       {@code iam.permissions} at all) and <b>F2-27</b>'s {@code AI:EXPORT}. Both were found
 *       by hand, months apart.</li>
 *   <li><b>A permission is granted so widely that guarding on it is cosmetic</b> —
 *       {@code WORKFLOW:WRITE} is held by 55 of 58 roles <em>including VIEWER</em>, so
 *       {@code ApprovalRequestController#create} counts as "guarded" in the authorization
 *       ratchet while a read-only viewer still passes it (live-verified 200). The
 *       {@link ControllerAuthorizationRatchetTest} javadoc records this caveat explicitly:
 *       "guarded" is not "restricted".</li>
 * </ol>
 *
 * <p>Measuring either direction needs the guard set from the <em>code</em> joined to the grant
 * counts from the <em>database</em>. The suite has no database, so this half runs here — by
 * reflection, because a source regex has twice under-counted a security surface in this
 * codebase (F2-27 missed 8 constant-valued guards and a 40th endpoint; F2-30 missed 54 field
 * pairs). It writes {@code target/permission-usage.txt} for
 * {@code Appendix/scripts/grant_tightness.py} to join against {@code iam.role_permissions}.
 *
 * <p>The assertions here are the ones that need no database: the extraction still works, and
 * every guard is well-formed. The tightness verdict itself is the script's job.
 */
class PermissionCatalogueUsageTest {

    /**
     * The well-formed shape of a joined {@code MODULE:ACTION} pair.
     *
     * <p>F2-263 — this class used to hold <b>two</b> pair regexes: a strict {@code [A-Z_]+} one
     * for extraction and a loose {@code [^']*} one for the well-formedness leg below. The
     * contrast was the design (extract widest, then judge), and it did not need two patterns:
     * {@link GuardExpressions#permissionPairsIn} already takes the widest reading, so the
     * judgement moves here and both legs now consult <b>one</b> population. That matters beyond
     * tidiness — with two, the extraction silently dropped a malformed pair from
     * {@code target/permission-usage.txt} while only the other leg could see it.
     *
     * <p>Judging the joined string is equivalent to judging the halves: a token containing a
     * colon fails this pattern too, so nothing escapes by being split differently.
     */
    private static final Pattern WELL_FORMED_PAIR = Pattern.compile("[A-Z][A-Z_]*:[A-Z][A-Z_]*");

    /** Where the report script picks up the code-side half: the distinct pairs. */
    private static final Path OUT = Path.of("target", "permission-usage.txt");

    /**
     * And the guard sites behind them, {@code CLASS#member MODULE:ACTION} per line.
     *
     * <p>The pair list alone answers "is this permission guarded anywhere", which is enough to
     * find a dead permission but not enough to act on one: a report that says
     * {@code REPORTING:WRITE} is held by no role is a curiosity until it also says which three
     * endpoints that closes. Emitted from the same reflection pass so the two files cannot
     * disagree.
     */
    private static final Path SITES = Path.of("target", "permission-usage-sites.txt");

    /**
     * The subset of sites whose guard ANDs a {@code hasAnyRole} leg onto the permission.
     *
     * <p>This is the report's honesty margin. The tightness join answers "who holds this
     * permission", which equals "who gets through this endpoint" only when the permission is the
     * whole guard. Where a second leg is ANDed on, holding the permission is necessary but not
     * sufficient, and the report must not claim otherwise.
     *
     * <p>Emitted rather than remembered, and the reason is a third repeat of the same lesson:
     * grepping for the two calls on one line returns <b>3</b> files, because a guard that spans
     * two source lines is invisible to a line-oriented match. Reflection over the assembled
     * annotation value says <b>73 sites</b> — 5% of the guard set, not 0.2%.
     */
    private static final Path COMPOSED = Path.of("target", "permission-usage-composed.txt");

    /**
     * Distinct MODULE:ACTION pairs referenced by any {@code @PreAuthorize} in the platform.
     *
     * <p><b>91 distinct pairs across 1,432 guard sites</b>, measured 2026-07-26. The floor is
     * set slightly below that (85) rather than at it: F2-22b will legitimately remove a guard
     * here and there as endpoints move to DTOs, and a floor that tracks the exact number would
     * fail on honest work. It is still tight enough to catch what it exists to catch — a broken
     * extraction returning near-nothing, which would make the tightness report cheerfully
     * declare every catalogued permission unused.
     */
    private static final int BASELINE_DISTINCT_PAIRS = 85;

    @Test
    void everyGuardedPermissionIsRecordedForTheTightnessReport() throws Exception {
        Set<String> pairs = new TreeSet<>();
        List<String> sites = new ArrayList<>();
        List<String> composed = new ArrayList<>();

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            collect(c.getAnnotation(PreAuthorize.class), c.getSimpleName(), "<class>",
                    pairs, sites, composed);
            for (Method m : c.getDeclaredMethods()) {
                collect(m.getAnnotation(PreAuthorize.class), c.getSimpleName(), m.getName(),
                        pairs, sites, composed);
            }
        }

        Files.createDirectories(OUT.getParent());
        Files.write(OUT, pairs);
        sites.sort(String::compareTo);
        Files.write(SITES, sites);
        composed.sort(String::compareTo);
        Files.write(COMPOSED, composed);

        System.out.println("[F2-13a] distinct MODULE:ACTION pairs guarded in code = " + pairs.size());
        System.out.println("[F2-13a] guard sites = " + sites.size());
        System.out.println("[F2-13a] sites ANDing a hasAnyRole leg = " + composed.size());
        System.out.println("[F2-13a] wrote " + OUT.toAbsolutePath());
        System.out.println("[F2-13a] wrote " + SITES.toAbsolutePath());
        System.out.println("[F2-13a] wrote " + COMPOSED.toAbsolutePath());
        pairs.forEach(p -> System.out.println("  " + p));

        assertThat(pairs)
            .as("The permission-usage extraction found %d distinct MODULE:ACTION pairs; %d were "
                + "measured when F2-13a shipped. A sharp drop means the reflection scan broke, "
                + "not that the guards left — and the grant-tightness report would then wrongly "
                + "report catalogued permissions as unused.", pairs.size(), BASELINE_DISTINCT_PAIRS)
            .hasSizeGreaterThanOrEqualTo(BASELINE_DISTINCT_PAIRS);
    }

    /**
     * A guard that names a module or action the catalogue cannot express is dead on arrival —
     * {@code ModulePermissionEvaluator} matches the granted authority {@code MODULE + "_" +
     * ACTION} exactly, so a lower-case or hyphenated name can never match anything. Cheap to
     * check here and impossible to see by reading one annotation at a time.
     */
    @Test
    void noGuardNamesAPermissionTheEvaluatorCanNeverMatch() {
        List<String> malformed = new ArrayList<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) {
                    continue;
                }
                for (String pair : GuardExpressions.permissionPairsIn(pa.value())) {
                    if (!WELL_FORMED_PAIR.matcher(pair).matches()) {
                        malformed.add(c.getSimpleName() + "#" + m.getName()
                            + " -> hasPermission " + pair);
                    }
                }
            }
        }
        System.out.println("[F2-13a] malformed guards = " + malformed);
        assertThat(malformed)
            .as("ModulePermissionEvaluator matches the granted authority MODULE_ACTION exactly, "
                + "so a module or action that is not UPPER_SNAKE can never be granted — the "
                + "guard is permanently closed and PLATFORM_ADMIN's code bypass hides it.")
            .isEmpty();

        // F2-263 — a synthetic self-test, because the assertion above is a "collect the violations,
        // assert the list empty" shape and on a clean tree an empty list is what BOTH a working
        // detector and a dead one produce. Widening WELL_FORMED_PAIR to .* would leave every
        // assertion above green; these four cases are what makes that a failure instead.
        assertThat(WELL_FORMED_PAIR.matcher("RISK:READ").matches()).isTrue();
        assertThat(WELL_FORMED_PAIR.matcher("risk:read").matches())
            .as("a lower-case pair can never match the granted authority").isFalse();
        assertThat(WELL_FORMED_PAIR.matcher("RIS:K:READ").matches())
            .as("judging the JOINED pair is equivalent to judging the halves only if a token "
                + "carrying a colon still fails — that is the claim this pattern rests on")
            .isFalse();
        assertThat(WELL_FORMED_PAIR.matcher("REG-CHANGE:READ").matches())
            .as("hyphens are the other shape ModulePermissionEvaluator cannot match").isFalse();
    }

    // ══════════════════════════════════════════════════════════════════════════════════════════
    // F2-255 — the half that was deferred out of the build, and the premise that deferred it.
    // ══════════════════════════════════════════════════════════════════════════════════════════

    /**
     * The {@code (MODULE, ACTION)} pairs the packaged migrations INSERT into {@code iam.permissions}.
     *
     * <h3>Why this belongs in the build, when the class javadoc above says it cannot</h3>
     * <p>That javadoc reads: "Measuring either direction needs the guard set from the <em>code</em>
     * joined to the grant counts from the <em>database</em>. The suite has no database, so this half
     * runs here… The tightness verdict itself is the script's job." And
     * {@code grant_tightness.py}'s own header names the failure mode this class exists to catch:
     * "<b>GUARDED, UNGRANTABLE</b> — the code demands a permission no role holds <b>(or that the
     * catalogue does not define)</b>… Found by hand twice: F2-23 (ADMIN:APPROVE, 59 committee
     * methods) and F2-27 (AI:EXPORT)."
     *
     * <p>🔑 <b>Those are two questions joined by an "or", and only one of them needs a database.</b>
     * <em>Who holds this permission</em> is a fact about {@code iam.role_permissions}. <em>Does this
     * permission exist at all</em> is a fact about the migrations, which sit in
     * {@code src/main/resources} and are read hermetically by a dozen tests in this suite already.
     * Bundling both under "tightness" carried the harder one's constraint over to the cheaper one,
     * so the build-time half asserted only that its own extraction still worked — and the comparison
     * lived in a two-step manual script. F2-255 is the third instance of the defect: {@code
     * AUDIT_LOG:WRITE} was cited by {@code POST /api/v1/audit-log/integrity/seal} and created by no
     * migration and present in no database, so the F2-31 tamper-evidence seal admitted the
     * PLATFORM_ADMIN code bypass and nobody else — and unlike a role nobody holds, no administrator
     * could grant it, because there was nothing in the catalogue to grant.
     *
     * <p>Two insert idioms exist and both are parsed: V272's
     * {@code … SELECT v.module, v.action FROM (VALUES ('A','READ',…), …)} and V362-onward's
     * {@code … VALUES (N'A', N'READ', …)}. Scoped to the statement rather than the file, because
     * {@code iam.role_permissions} inserts in the same file carry triples like
     * {@code ('ORG_ADMIN','AUDIT_LOG','READ')} whose first two elements would read as a pair — the
     * detector-satisfied-by-something-else trap, pinned below in both directions.
     */
    private static Set<String> cataloguedByMigrations() throws Exception {
        Set<String> out = new TreeSet<>();
        try (var files = Files.list(MIGRATIONS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                out.addAll(pairsInserted(Files.readString(p)));
            }
        }
        return out;
    }

    private static final Path MIGRATIONS =
        Path.of("src", "main", "resources", "db", "migration", "sqlserver");

    private static final Pattern INSERT_PERMS =
        Pattern.compile("INSERT\\s+INTO\\s+iam\\.permissions\\b", Pattern.CASE_INSENSITIVE);

    /** A VALUES tuple whose first two elements are quoted UPPER_SNAKE tokens, {@code N'…'} or not. */
    private static final Pattern VALUES_PAIR =
        Pattern.compile("\\(\\s*N?'([A-Z_0-9]+)'\\s*,\\s*N?'([A-Z_0-9]+)'");

    /** Extracted as a static helper so the self-test can drive it over synthetic SQL. */
    static Set<String> pairsInserted(String sql) {
        Set<String> out = new TreeSet<>();
        Matcher ins = INSERT_PERMS.matcher(sql);
        while (ins.find()) {
            int end = sql.indexOf(';', ins.end());
            String stmt = sql.substring(ins.end(), end > 0 ? end : sql.length());
            int v = stmt.toUpperCase(Locale.ROOT).indexOf("VALUES");
            if (v < 0) {
                continue;
            }
            Matcher t = VALUES_PAIR.matcher(stmt.substring(v + "VALUES".length()));
            while (t.find()) {
                out.add(t.group(1) + ":" + t.group(2));
            }
        }
        return out;
    }

    /**
     * Cited by a guard, created by no migration, and present in both live databases anyway.
     *
     * <h3>Why a hard zero would fail on correct code, which is the one thing a gate must not do</h3>
     * <p>The BASE catalogue predates the migration set. {@code V1} only {@code CREATE}s
     * {@code iam.permissions} — it seeds no rows — and {@code V239}'s own header says the BCM module
     * "shipped with permission rows (BCM READ/WRITE/DELETE)". Measured 2026-08-20: guards cite
     * <b>128</b> distinct pairs, the migrations create <b>93</b>, and the 36 below are present in
     * <b>both</b> dev (141 pairs) and prod (114) while appearing in no migration at all. They are
     * therefore not defects in either running database, and asserting a bare
     * {@code cited ⊆ migrations} would fail over most of the platform's core modules.
     *
     * <p>⚠⚠ <b>Declaring them is the point, because the same measurement says a fresh Flyway install
     * from V1 would create 93 catalogue rows against 128 the guards demand.</b> On such an install
     * {@code RISK:READ}, {@code BCM:WRITE}, {@code COMPLIANCE:READ}, {@code CYBER:*},
     * {@code PRIVACY:*}, {@code SCM:*}, {@code ADMIN:READ/WRITE}, {@code REPORTING:*} and eight
     * EXPORT pairs have no catalogue row, so every guard citing them is closed to everyone but the
     * platform administrator — and a missing catalogue row cannot be fixed from the role-admin
     * screen the way a missing grant can. Same family as the recorded fresh-install grant gap ("225
     * grants short of dev"), one table up. <b>Filed, not fixed here: back-filling 36 catalogue rows
     * and their grants is a migration with a per-tenant impact question, not a side effect of a
     * guard fix.</b> The list makes the gap countable instead of invisible.
     */
    private static final Set<String> CREATED_BEFORE_THE_MIGRATION_SET = Set.of(
        "ADMIN:EXPORT", "ADMIN:READ", "ADMIN:WRITE",
        "BCM:DELETE", "BCM:EXPORT", "BCM:READ", "BCM:WRITE",
        "CHANGE:APPROVE", "CHANGE:DELETE", "CHANGE:EXPORT",
        "COMPLIANCE:EXPORT", "COMPLIANCE:READ", "COMPLIANCE:WRITE",
        "CYBER:DELETE", "CYBER:EXPORT", "CYBER:READ", "CYBER:WRITE",
        "IAM:EXPORT", "NDI:EXPORT", "PLATFORM:EXPORT",
        "PRIVACY:READ", "PRIVACY:WRITE", "REGULATORY:EXPORT",
        "REPORTING:EXPORT", "REPORTING:READ", "REPORTING:WRITE",
        "RISK:DELETE", "RISK:EXPORT", "RISK:READ", "RISK:WRITE",
        "SCM:DELETE", "SCM:READ", "SCM:WRITE",
        "SUST:APPROVE", "SUST:EXPORT", "TPRM:APPROVE");

    /**
     * The F2-255 invariant: no guard may demand a permission nothing creates.
     *
     * <p>No exemption map, and that is safe rather than strict (F2-214): cataloguing an action is
     * always available — a five-line migration — so there is no correct code for this to fail on.
     * A pair that is neither created by a migration nor declared above is a guard that can never be
     * satisfied by anyone except through {@code ModulePermissionEvaluator}'s PLATFORM_ADMIN bypass,
     * which is what hides it from whoever tests as admin.
     */
    @Test
    void noGuardCitesAPermissionNothingCreates() throws Exception {
        Set<String> cited = citedPairs();
        Set<String> catalogued = cataloguedByMigrations();

        List<String> ungrantable = cited.stream()
            .filter(p -> !catalogued.contains(p))
            .filter(p -> !CREATED_BEFORE_THE_MIGRATION_SET.contains(p))
            .sorted()
            .toList();

        System.out.println("[F2-255] cited=" + cited.size() + " createdByMigrations="
            + catalogued.size() + " declaredPreMigration=" + CREATED_BEFORE_THE_MIGRATION_SET.size()
            + " ungrantable=" + ungrantable.size());

        assertThat(ungrantable).as("""
            These guards demand a (MODULE, ACTION) pair that NO migration creates and that is not \
            declared as pre-migration BASE. ModulePermissionEvaluator matches the granted authority \
            MODULE_ACTION exactly, so nothing can ever satisfy them: the endpoint answers 403 to \
            every caller and 200 to PLATFORM_ADMIN through the code bypass, which is why this class \
            of defect has been found by hand three times (F2-23 ADMIN:APPROVE, F2-27 AI:EXPORT, \
            F2-255 AUDIT_LOG:WRITE) and never by a build.

            Fix by cataloguing the action in a migration and granting it to a considered set of \
            roles — V380 is the worked example, including why granting the whole READ tier is the \
            V362 mistake. Do NOT add an exemption: a permission nobody can hold is not a design.

            Offenders: %s""".formatted(ungrantable)).isEmpty();
    }

    /**
     * The reverse direction, which is the one nobody writes first (F2-199).
     *
     * <p>An entry in {@link #CREATED_BEFORE_THE_MIGRATION_SET} is a claim about two things, and both
     * decay. If a migration starts creating the pair, the entry is stale and hides the fact that the
     * fresh-install gap shrank. If no guard cites it any more, the entry is dead weight that would
     * silently absorb a future ghost of the same name.
     */
    @Test
    void everyPreMigrationDeclarationIsStillTrueInBothDirections() throws Exception {
        Set<String> cited = citedPairs();
        Set<String> catalogued = cataloguedByMigrations();

        List<String> nowInAMigration = CREATED_BEFORE_THE_MIGRATION_SET.stream()
            .filter(catalogued::contains).sorted().toList();
        assertThat(nowInAMigration).as("""
            A migration now creates these, so they are no longer pre-migration BASE. Delete the \
            entries — leaving them in place means the fresh-install catalogue gap reads as larger \
            than it is, and a stale entry would absorb a genuine ghost of the same name. \
            Offenders: %s""".formatted(nowInAMigration)).isEmpty();

        List<String> noLongerCited = CREATED_BEFORE_THE_MIGRATION_SET.stream()
            .filter(p -> !cited.contains(p)).sorted().toList();
        assertThat(noLongerCited).as("""
            No @PreAuthorize cites these any more, so the declaration is dead weight. Delete the \
            entries: the list exists to count the fresh-install gap for guards that are LIVE, and \
            an entry nothing cites would silently exempt a future guard that reintroduces the name. \
            Offenders: %s""".formatted(noLongerCited)).isEmpty();
    }

    /**
     * The parser's own controls, positive and negative, because a migration scan that quietly stops
     * matching reports an EMPTY catalogue — and an empty catalogue makes every cited pair look like
     * a ghost, which fails loudly, while an over-matching one makes every ghost look catalogued,
     * which does not.
     */
    @Test
    void theMigrationParserReadsBothIdiomsAndNeitherRolePermissionsNorThreeArgGuards()
            throws Exception {
        // Positive: V272's multi-tuple SELECT-FROM-VALUES form.
        assertThat(pairsInserted("""
            INSERT INTO iam.permissions (module, action, permission_name, description_en)
            SELECT v.module, v.action, v.module + '_' + v.action, v.descr
            FROM (VALUES
                ('AUDIT', 'READ', 'x'),
                ('AUDIT_LOG', 'READ', 'y')
            ) AS v(module, action, descr)
            WHERE NOT EXISTS (SELECT 1 FROM iam.permissions p WHERE p.module = v.module);
            """)).containsExactlyInAnyOrder("AUDIT:READ", "AUDIT_LOG:READ");

        // Positive: V362-onward's single-tuple N'…' form.
        assertThat(pairsInserted("""
            IF NOT EXISTS (SELECT 1 FROM iam.permissions WHERE module = N'SHARIA' AND action = N'READ')
                INSERT INTO iam.permissions (module, action, permission_name, description_en)
                VALUES (N'SHARIA', N'READ', N'SHARIA_READ', N'View the Sharia register.');
            """)).containsExactly("SHARIA:READ");

        // Negative: a role_permissions triple must NOT read as a permission pair. Its first two
        // elements are (role_name, module), so a file-scoped parse would invent ORG_ADMIN:AUDIT_LOG.
        assertThat(pairsInserted("""
            INSERT INTO iam.role_permissions (role_id, permission_id)
            SELECT r.id, p.id FROM iam.roles r JOIN iam.permissions p ON 1 = 1
            WHERE (r.role_name, p.module, p.action) IN (VALUES
                ('ORG_ADMIN','AUDIT_LOG','READ'), ('CISO','AUDIT_LOG','READ'));
            """)).isEmpty();

        // The real file must yield the F2-255 pair, or V380 has been reverted.
        assertThat(cataloguedByMigrations())
            .as("V380 catalogues AUDIT_LOG:WRITE, the permission POST /audit-log/integrity/seal has "
                + "always demanded. If this is absent the seal control is administrator-only again.")
            .contains("AUDIT_LOG:WRITE");

        // The two 3-argument guard forms are relations on a record, not module-level pairs. If the
        // citation regex ever matched them, ML_MODEL:OWNER and COMMITTEE:SEAT would be reported as
        // ghosts on entirely correct code (F2-159, F2-177).
        Set<String> cited = citedPairs();
        assertThat(cited).as("hasPermission(#externalId,'ML_MODEL','OWNER') is an ownership relation")
            .doesNotContain("ML_MODEL:OWNER");
        assertThat(cited).as("hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT') is a seat relation; "
                + "its first argument IS quoted, so only the closing paren distinguishes it")
            .doesNotContain("AUDIT_COMMITTEE:COMMITTEE");
    }

    /**
     * Anti-vacuity, and the floors sit above the largest WRONG answer rather than above zero
     * (F2-215). A broken statement scan returns 0 catalogued pairs; a broken reflection pass returns
     * 0 cited pairs. Measured 2026-08-20: 93 catalogued, 128 cited.
     *
     * <p>Also pins the parser's soundness premise: the catalogue is the UNION of every insert, which
     * is only true while no migration removes or renames a pair. Every {@code DELETE FROM
     * iam.permissions} in the tree today sits inside a {@code --} rollback comment, and the two
     * {@code UPDATE iam.permissions} statements (V368, V369, V370) edit descriptions only.
     */
    @Test
    void thePopulationsAreLargeAndNoMigrationRemovesACataloguedPair() throws Exception {
        assertThat(cataloguedByMigrations()).as("migration scan collapsed").hasSizeGreaterThan(85);
        assertThat(citedPairs()).as("reflection scan collapsed").hasSizeGreaterThan(120);

        List<String> destructive = new ArrayList<>();
        try (var files = Files.list(MIGRATIONS)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                for (String line : Files.readAllLines(p)) {
                    String t = line.trim();
                    if (t.startsWith("--")) {
                        continue;
                    }
                    if (t.toUpperCase(Locale.ROOT).contains("DELETE FROM IAM.PERMISSIONS")) {
                        destructive.add(p.getFileName() + ": " + t);
                    }
                }
            }
        }
        assertThat(destructive).as("""
            A migration removes a row from iam.permissions, so the catalogue is no longer the union \
            of every insert and cataloguedByMigrations() over-reports — which would make a genuine \
            ghost read as catalogued. Teach the parser about the removal before allowing this. \
            Offenders: %s""".formatted(destructive)).isEmpty();
    }

    /** Reflection over the assembled annotation values, shared by the F2-255 legs. */
    private static Set<String> citedPairs() {
        Set<String> pairs = new TreeSet<>();
        List<String> sites = new ArrayList<>();
        List<String> composed = new ArrayList<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {                                                       // NOPMD
                continue;
            }
            collect(c.getAnnotation(PreAuthorize.class), c.getSimpleName(), "<class>",
                    pairs, sites, composed);
            for (Method m : c.getDeclaredMethods()) {
                collect(m.getAnnotation(PreAuthorize.class), c.getSimpleName(), m.getName(),
                        pairs, sites, composed);
            }
        }
        return pairs;
    }

    // ══════════════════════════════════════════════════════════════════════════════════════════
    // F2-264 — "MODULE has no catalogued permission" has TWO consequences and they are opposites.
    // ══════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Module packages whose guards cite <b>no</b> {@code (MODULE, ACTION)} pair at all.
     *
     * <p>For these the catalogue is <b>inert at run time</b>: {@code hasAnyRole} never reaches
     * {@code ModulePermissionEvaluator}, so a module with no row in {@code iam.permissions} costs
     * nothing — the guards are satisfiable by whoever holds the recited role. They are F2-05's
     * conversion backlog, not a lockout.
     *
     * <p>⚠⚠ <b>F2-257 filed four of them as *reachable only through the PLATFORM_ADMIN code
     * bypass*, and F2-258, F2-259 and F2-260 each carried that forward as the next work.</b>
     * Probed live 2026-08-21, all four, read tier and write tier: 4–5 non-admin personas answer 200
     * on every read, and on the writes {@code compliance} (COMPLIANCE_OFFICER) or {@code rmanager}
     * (RISK_MANAGER) answered <b>200/201</b> while an off-tier persona answered <b>403</b>. Nothing
     * was administrator-only. 🔑 <b>The filing's sentence was true about the CATALOGUE and read as
     * a claim about REACHABILITY, and those coincide only when the guard is a permission guard.</b>
     *
     * <p>⚠ <b>And the list was half the population.</b> F2-257 named {@code dataclass},
     * {@code outsourcing}, {@code reginspect}, {@code regreporting}; the derivation finds
     * <b>eight</b>, adding {@code auth}, {@code ccm}, {@code governance} and {@code opres} — the
     * same shape, never named, and F2-198's narrowing one more time.
     *
     * <p>Asserted <b>exactly</b> rather than as a ceiling (F2-204): a decrease-only count cannot
     * express "the population was undercounted", and raising it would be indistinguishable from
     * tolerating a regression. Converting a module to {@code hasPermission} removes it from this set
     * and the assertion must be edited in the same commit, which is the point.
     */
    private static final Set<String> GUARDS_CITE_NO_PERMISSION_PAIR = Set.of(
        // 3 isAuthenticated() self-service routes + 1 role guard. Mixed, and correctly so.
        "auth",
        "ccm", "dataclass", "governance", "opres", "outsourcing", "reginspect", "regreporting");

    @Test
    @DisplayName("the catalogue gap has two kinds, and only one of them is a lockout")
    void theCatalogueGapHasTwoKindsAndOnlyOneIsALockout() throws Exception {
        Map<String, Set<String>> pairsByModulePackage = new TreeMap<>();

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc.module")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            String pkg = c.getPackageName();
            int at = pkg.indexOf(".module.");
            if (at < 0) {
                continue;
            }
            String module = pkg.substring(at + ".module.".length()).split("\\.")[0];
            Set<String> pairs = pairsByModulePackage.computeIfAbsent(module, k -> new TreeSet<>());
            addPairs(c.getAnnotation(PreAuthorize.class), pairs);
            for (Method m : c.getDeclaredMethods()) {
                addPairs(m.getAnnotation(PreAuthorize.class), pairs);
            }
        }

        // Anti-vacuity: a walk that resolves nothing would make every assertion below pass.
        assertThat(pairsByModulePackage).as("the controller scan resolved no module packages")
            .hasSizeGreaterThanOrEqualTo(40);

        Set<String> citeNoPair = pairsByModulePackage.entrySet().stream()
            .filter(e -> e.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        System.out.println("[F2-264] guarded module packages = " + pairsByModulePackage.size()
            + " · citing no permission pair = " + citeNoPair.size() + " -> " + citeNoPair);

        assertThat(citeNoPair).as("""
            The set of module packages whose guards cite no (MODULE, ACTION) pair has changed. For \
            these the permission catalogue is INERT — hasAnyRole never reaches \
            ModulePermissionEvaluator, so an uncatalogued module costs nothing at run time and the \
            guards are satisfiable by whoever holds the recited role. That is the OPPOSITE of a \
            guard citing an uncatalogued pair, which can only ever answer false and has no \
            administrator remedy (noGuardCitesAPermissionNothingCreates, above, holds that at zero).
            F2-257 conflated the two and three slices carried the conflation forward.
            If a module left this set, it was converted to hasPermission — delete its entry here. If \
            one joined it, a permission guard became a role guard, which is F2-05 in reverse.
            got: %s""".formatted(citeNoPair)).isEqualTo(new TreeSet<>(GUARDS_CITE_NO_PERMISSION_PAIR));

        // The two kinds are disjoint BY CONSTRUCTION, and saying so is the leg's whole point: a
        // module citing no pair cannot cite an uncatalogued one.
        Set<String> catalogued = cataloguedByMigrations();
        Set<String> ungrantableModules = citedPairs().stream()
            .filter(p -> !catalogued.contains(p))
            .filter(p -> !CREATED_BEFORE_THE_MIGRATION_SET.contains(p))
            .map(p -> p.substring(0, p.indexOf(':')))
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertThat(ungrantableModules).as("""
            A module cannot be in both kinds at once, so if this is ever non-empty the two \
            populations must still not intersect — otherwise a reader would have one module \
            described as inert and as a lockout at the same time.""")
            .doesNotContainAnyElementsOf(GUARDS_CITE_NO_PERMISSION_PAIR);
    }

    private static void addPairs(PreAuthorize pa, Set<String> into) {
        if (pa != null) {
            into.addAll(GuardExpressions.permissionPairsIn(pa.value()));
        }
    }

    private static void collect(PreAuthorize pa, String cls, String member,
                                Set<String> pairs, List<String> sites, List<String> composed) {
        if (pa == null) {
            return;
        }
        boolean andsARole = GuardExpressions.hasRoleLeg(pa.value());
        for (String pair : GuardExpressions.permissionPairsIn(pa.value())) {
            pairs.add(pair);
            sites.add(cls + "#" + member + " " + pair);
            if (andsARole) {
                composed.add(cls + "#" + member + " " + pair);
            }
        }
    }
}
