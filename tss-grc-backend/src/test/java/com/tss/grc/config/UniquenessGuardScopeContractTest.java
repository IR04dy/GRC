package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-418 — <b>a uniqueness guard that asks a narrower question than the index enforces passes for
 * exactly the rows the database is about to reject.</b>
 *
 * <p>{@code UserService} refused a duplicate with
 * {@code existsByUsernameAndOrganisationId(username, orgId)}. That is a derived finder, so it runs
 * against the entity and inherits {@code User}'s {@code @Where(clause = "is_deleted = 0")}; and it
 * is tenant-scoped. {@code ix_users_username} is neither — V227 creates it GLOBAL and UNFILTERED
 * and states both as deliberate:
 *
 * <blockquote>"login authenticates by username with no tenant context, so the uniqueness here is
 * GLOBAL (username/email unique across the platform), NOT per-organisation" … "keeps username/email
 * globally unique even across soft-deleted rows"</blockquote>
 *
 * <p>So the guard was narrower on <b>two axes at once</b> — the tenant and the soft-delete flag —
 * which is F2-374's shape (there the narrowing was the soft-delete flag alone) with a second axis.
 * Both are reachable: {@code UserService#delete} is a soft delete, and
 * {@code TenantOnboardingService} creates a new tenant's first administrator, so onboarding a
 * second tenant whose administrator is named {@code admin} — the username this platform's own
 * documentation uses — hit the global index and, because the onboarding is one transaction,
 * discarded the organisation, its licences and its administrator.
 *
 * <p>🔑 <b>Two artefacts stated opposite things about the same index and nobody was maintaining the
 * agreement</b> (F2-177). V227's comment says the scope is global; {@code UserServiceTest}'s class
 * javadoc listed <em>"Username/email uniqueness checked per-org (not global)"</em> among the
 * invariants it locks, and the guard followed the test. That leg below is the comparison nobody was
 * making.
 *
 * <p>⚠ The index is <b>not</b> the half that is wrong and this gate must not be read as saying so.
 * V227's reason holds — {@code findByUsername} takes no tenant, so login requires global
 * uniqueness. What changed is the guard, and the drift guard here pins the two together so that a
 * migration which later tenant-scopes or filters either index fails the build and forces the guard
 * to be revisited with it (F2-299).
 *
 * <p>Hermetic: a source read plus a migration read. No database, no Spring context.
 */
@DisplayName("F2-418 — the uniqueness guard asks the question the index enforces")
class UniquenessGuardScopeContractTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");
    private static final Path MAIN = Path.of("src/main/java");

    private static final Path USER_SERVICE =
        MAIN.resolve("com/tss/grc/module/iam/service/UserService.java");
    private static final Path USER_REPOSITORY =
        MAIN.resolve("com/tss/grc/module/iam/repository/UserRepository.java");
    private static final Path HANDLER =
        MAIN.resolve("com/tss/grc/common/exception/GlobalExceptionHandler.java");

    // ── F2-426: the mirror question — is a check-then-insert backed by an index at all? ──
    private static final Path WF_ENGINE =
        MAIN.resolve("com/tss/grc/module/workflow/service/WorkflowEngineService.java");
    private static final Path WF_INSTANCE_REPO =
        MAIN.resolve("com/tss/grc/module/workflow/repository/WorkflowInstanceRepository.java");
    private static final Path V397 =
        MIGRATIONS.resolve("V397__workflow_instance_target_unique.sql");
    private static final Path V1 = MIGRATIONS.resolve("V1__core_schemas.sql");

    private static final List<String> INSTANCE_TARGET_KEY = List.of(
        "organisation_id", "target_module", "target_entity", "target_entity_id");

    /** The clause the 2601/2627 arm may not carry: it is false for every global unique guarantee. */
    private static final String ORG_SCOPE_CLAIM = "unique within your organisation";

    // ── leg 1: the index's scope, which is what the guard has to mirror ───────────────

    /**
     * The drift guard, and the load-bearing leg. If a later migration tenant-scopes or filters
     * either index, the guard in {@code UserService} becomes stricter than the database — refusing
     * a username another tenant legitimately holds — so this must fail and be read rather than
     * silently diverge. It is asserted from the migration SQL because the migration is the only
     * artefact that decides the scope.
     */
    @Test
    @DisplayName("ix_users_username / ix_users_email are GLOBAL and UNFILTERED — the guard mirrors this")
    void theUsernameAndEmailIndexesAreGlobalAndUnfiltered() throws IOException {
        String sql = stripSqlComments(read(MIGRATIONS.resolve("V227__iam_users_indexes.sql")));

        for (String[] pair : new String[][] {
                {"ix_users_username", "username"},
                {"ix_users_email", "email"}}) {
            String index = pair[0];
            String column = pair[1];

            Matcher m = Pattern.compile(
                "CREATE\\s+UNIQUE\\s+(?:NONCLUSTERED\\s+)?INDEX\\s+" + index
                + "\\s+ON\\s+iam\\.users\\s*\\(([^)]*)\\)([^;]*)", Pattern.CASE_INSENSITIVE)
                .matcher(sql);

            assertThat(m.find())
                .as("V227 must still create %s as a UNIQUE index on iam.users — if it has been "
                  + "dropped or made non-unique, UserService's guard is now enforcing a rule the "
                  + "database does not", index)
                .isTrue();

            List<String> key = new ArrayList<>();
            for (String c : m.group(1).split(",")) {
                String t = c.trim().replace("[", "").replace("]", "")
                            .replaceAll("(?i)\\s+(ASC|DESC)$", "").trim();
                if (!t.isEmpty()) {
                    key.add(t.toLowerCase(java.util.Locale.ROOT));
                }
            }
            assertThat(key)
                .as("%s's key decides the SCOPE of the uniqueness. It is [%s] alone — global, no "
                  + "organisation_id — which is why the guard is org-agnostic. Adding "
                  + "organisation_id here makes the guard too strict and this assertion is where "
                  + "that has to be noticed", index, column)
                .containsExactly(column);

            String tail = m.group(2);
            assertThat(tail.toUpperCase(java.util.Locale.ROOT))
                .as("%s is UNFILTERED, so it counts soft-deleted rows too — which is why the "
                  + "guard is deleted-inclusive and native (a derived finder inherits User's "
                  + "@Where and cannot see them). A WHERE clause here inverts that", index)
                .doesNotContain(" WHERE ");
        }
    }

    // ── leg 2: the guard, in both directions ─────────────────────────────────────────

    /**
     * Both directions, because either alone is satisfiable by an accident. The create and update
     * paths must consult the index-mirroring counts, <b>and</b> the tenant-scoped finders they
     * replaced must not exist for a later reader to reach for — a finder that is not declared
     * cannot be called, which is a stronger closure than a comment saying not to (F2-233). They had
     * exactly one production caller each, so deleting them costs nothing.
     */
    @Test
    @DisplayName("the create and update guards consult the index's own question, and the narrower finders are gone")
    void theCreateAndUpdateGuardsAskTheQuestionTheIndexEnforces() throws IOException {
        String service = stripJavaComments(read(USER_SERVICE));
        String repository = stripJavaComments(read(USER_REPOSITORY));

        assertThat(service)
            .as("the create path must ask whether the username is held ANYWHERE, including "
              + "soft-deleted rows — that is what ix_users_username enforces")
            .contains("countByUsernameIncludingDeleted(");
        assertThat(service)
            .as("and the same for email, on create")
            .contains("countByEmailIncludingDeleted(");
        assertThat(service)
            .as("the update path writes email unconditionally and had NO uniqueness guard at all, "
              + "so it needs the self-excluding count: a user keeping their own address must not "
              + "collide with themselves")
            .contains("countByEmailIncludingDeletedExcludingId(");

        assertThat(service)
            .as("no path may go back to the tenant-scoped, @Where-filtered question — it answers "
              + "'available' for exactly the rows the index rejects")
            .doesNotContain("existsByUsernameAndOrganisationId")
            .doesNotContain("existsByEmailAndOrganisationId");
        assertThat(repository)
            .as("and the finders themselves are gone, so the next reader cannot reach for one. A "
              + "finder that is not declared cannot be called")
            .doesNotContain("existsByUsernameAndOrganisationId")
            .doesNotContain("existsByEmailAndOrganisationId");

        assertThat(repository)
            .as("the counts must be NATIVE: a derived finder runs against the entity and inherits "
              + "User's @Where(is_deleted = 0), which is the narrowing being removed")
            .contains("nativeQuery = true");
    }

    // ── leg 3: the diagnostic ────────────────────────────────────────────────────────

    /**
     * The 2601/2627 arm may not assert organisation scope. Measured over the packaged migration
     * chain: of the 260 unique guarantees that sit on a table an {@code @Entity} maps, <b>48 omit
     * {@code organisation_id}</b> — so the retired clause was false for 18% of the population by
     * construction, and guaranteed false on {@code iam.users}, which leg 1 proves.
     *
     * <p>⚠ Comments are stripped first, because the fix's own comment quotes the sentence it
     * retired in order to record what it replaced — a scan that reads the epitaph as the corpse
     * fails on a correct tree (F2-193).
     */
    @Test
    @DisplayName("the duplicate-key diagnostic does not assert that uniqueness is organisation-scoped")
    void theDuplicateKeyMessageDoesNotAssertOrganisationScope() throws IOException {
        String handler = stripJavaComments(read(HANDLER));

        assertThat(handler)
            .as("48 of the platform's 260 mapped-table unique guarantees are global, iam.users' "
              + "username and email among them. Telling that caller to make the field unique "
              + "'within your organisation' names the one thing that is already true and denies "
              + "the one fact that explains the refusal — F2-180's rule, one case label up in the "
              + "same switch")
            .doesNotContain(ORG_SCOPE_CLAIM);

        assertThat(handler)
            .as("and it must still tell the caller what to do")
            .contains("must be unique");
    }

    /**
     * The detector, over synthetic input, in both directions — a scan that cannot fire reads exactly
     * like a clean tree (F2-235), and this one has to survive a comment that quotes the very
     * sentence it bans.
     */
    @Test
    @DisplayName("the scope-claim detector fires on the retired sentence and not on a comment quoting it")
    void theScopeClaimDetectorFiresOnTheClaimAndNotOnAQuotationOfIt() {
        String claiming = "detail = \"One of the fields must be unique within your organisation.\";";
        String quoting  = "// F2-418: this used to read \"unique within your organisation\", which was false.\n"
                        + "detail = \"One of the fields must be unique.\";";

        assertThat(stripJavaComments(claiming))
            .as("a live claim must be seen")
            .contains(ORG_SCOPE_CLAIM);
        assertThat(stripJavaComments(quoting))
            .as("a comment quoting the retired claim must NOT be — otherwise the gate fails on a "
              + "correct tree and the next reader deletes the evidence rather than the defect")
            .doesNotContain(ORG_SCOPE_CLAIM);
    }

    // ══ F2-426 — a check-then-insert with no unique index is a guard that breaks itself ══════
    //
    // F2-418 above asks whether a uniqueness GUARD matches what the index enforces. This is the
    // same question from the other end: `WorkflowEngineService.startInstance` refuses a second
    // instance for one target by asking an Optional-returning finder `isPresent()`, and
    // workflow.instances carried NO unique index on those columns. Two concurrent starts both see
    // nothing and both insert.
    //
    // ⚠⚠ The consequence is not a duplicate row, it is that THE GUARD ITSELF THEN FAILS: from the
    // moment two rows exist the finder throws IncorrectResultSizeDataAccessException, so
    // startInstance can neither succeed nor correctly refuse, and findInstanceForTarget — what
    // every screen asking for a record's workflow state calls — 500s too. WorkflowInstance has no
    // is_deleted column and there is no delete route for an instance anywhere, so it is permanent.
    //
    // 🔑 The tell was in the schema: V1 creates IX_wf_inst_target on the same three target columns
    // and leaves it NON-unique, and omits the tenant. This platform's own idiom says what was
    // missing — F2-267 recorded that SubprocessorService's check-then-insert is safe *because*
    // UQ_subp_code backstops it.

    /**
     * Leg 1 — the backstop exists, and is the exact shape the guard needs.
     *
     * <p>Asserted from the migration SQL because the migration is the only artefact that decides
     * it. ⚠ UNFILTERED is not a style note here: all four columns are {@code nullable = false}, so
     * unlike V385's filtered index on a nullable code there is no NULL-equality trap to work
     * around, and a {@code WHERE} clause would be a statement about absence the schema forbids.
     */
    @Test
    @DisplayName("F2-426 — workflow.instances has a UNIQUE index on the four columns startInstance guards")
    void theInstanceTargetGuardIsBackedByAUniqueIndex() throws IOException {
        Matcher m = uniqueIndexOn(stripSqlComments(read(V397)),
                                 "uq_wf_instance_target", "workflow\\.instances");

        assertThat(m.find())
            .as("V397 must create uq_wf_instance_target as a UNIQUE index on workflow.instances. "
              + "Without it startInstance is a check-then-insert whose own guard 500s the moment "
              + "the race it cannot prevent happens — and there is no delete route to repair it")
            .isTrue();

        assertThat(keyColumnsOf(m.group(1)))
            .as("the key must be exactly the four columns the guard reads, LEADING with the "
              + "tenant. A subset leaves the race open; dropping organisation_id would let one "
              + "tenant's instance stop another starting a workflow on the same target ids, which "
              + "is a multi-tenancy defect dressed as strictness (F2-309)")
            .containsExactlyElementsOf(INSTANCE_TARGET_KEY);

        assertThat(m.group(2).toUpperCase(java.util.Locale.ROOT))
            .as("uq_wf_instance_target is UNFILTERED. All four columns are NOT NULL, so there is "
              + "no NULL-equality trap for a filter to solve — a WHERE here would exempt rows the "
              + "schema does not permit to exist")
            .doesNotContain(" WHERE ");
    }

    /**
     * Leg 2 — the pair. The index and the guard have to keep reading the same columns, and the
     * guard has to keep running: either half alone is satisfiable by an accident, which is F2-418's
     * own precedent for pinning a guard and its index together (F2-299).
     *
     * <p>The finder's column list is DERIVED from its method name rather than declared, so adding a
     * fifth predicate to the finder — or renaming one — fails here instead of silently widening the
     * query past what the index makes unique.
     */
    @Test
    @DisplayName("F2-426 — the guard and the index read the same four columns, and the guard still runs")
    void theGuardAndTheIndexReadTheSameFourColumns() throws IOException {
        String repo = stripJavaComments(read(WF_INSTANCE_REPO));
        Matcher f = Pattern.compile(
            "Optional\\s*<\\s*WorkflowInstance\\s*>\\s+(findBy\\w+)\\s*\\(").matcher(repo);
        assertThat(f.find())
            .as("WorkflowInstanceRepository must still declare an Optional finder for the target — "
              + "it is the guard's own query")
            .isTrue();
        String finder = f.group(1);

        List<String> fromName = new ArrayList<>();
        for (String part : finder.substring("findBy".length()).split("(?<=[a-z0-9])And(?=[A-Z])")) {
            fromName.add(part.replaceAll("(?<!^)(?=[A-Z])", "_").toLowerCase(java.util.Locale.ROOT));
        }
        assertThat(fromName)
            .as("%s reads these columns; uq_wf_instance_target makes THESE unique: %s. A finder "
              + "that asks a WIDER question than the index answers can still match two rows, "
              + "which is exactly the state that breaks it", finder, INSTANCE_TARGET_KEY)
            .containsExactlyInAnyOrderElementsOf(INSTANCE_TARGET_KEY);

        String engine = stripJavaComments(read(WF_ENGINE));
        assertThat(engine)
            .as("startInstance must still CALL the finder — asserted as a call shape rather than "
              + "as a token, because an import or a comment satisfies a bare substring (F2-194)")
            .containsPattern(finder + "\\s*\\(");
        assertThat(engine)
            .as("and must still refuse on it. The index turns the concurrent loser into a 409; the "
              + "guard is what gives the single-threaded caller a message naming the reason")
            .contains("Workflow instance already exists for this target entity");
    }

    /**
     * Leg 3 — the self-test, and it is load-bearing rather than decorative.
     *
     * <p>V1 declares {@code IX_wf_inst_target} on the same target columns, NON-unique and without
     * the tenant — sitting in the corpus this gate reads. A pattern loose enough to match it would
     * report the backstop as present while nothing enforced anything, so the detector is run
     * against that declaration and must NOT accept it. The same leg pins V397's additivity claim:
     * the seek index has to survive.
     */
    @Test
    @DisplayName("F2-426 — the detector rejects the non-unique seek index on the same columns")
    void theUniqueIndexDetectorRejectsTheNonUniqueSeekIndexOnTheSameColumns() throws IOException {
        String v1 = stripSqlComments(read(V1));

        assertThat(v1)
            .as("V397 claims to be ADDITIVE and its check 5 asserts IX_wf_inst_target survives, so "
              + "V1 must still declare it")
            .containsPattern("CREATE\\s+INDEX\\s+\\[?IX_wf_inst_target");

        assertThat(uniqueIndexOn(v1, "IX_wf_inst_target", "workflow\\.instances").find())
            .as("IX_wf_inst_target is NOT unique and omits the tenant — it is the index somebody "
              + "built for this very lookup and left non-unique. A detector that accepted it would "
              + "report the backstop as present over a table that enforces nothing, so this is the "
              + "control that makes leg 1 mean something")
            .isFalse();

        // and the detector must still accept the real thing, or leg 1 could be passing vacuously
        assertThat(uniqueIndexOn(stripSqlComments(read(V397)),
                                 "uq_wf_instance_target", "workflow\\.instances").find())
            .as("the same detector accepts the UNIQUE declaration — without this the leg above "
              + "would pass just as well over a pattern that matches nothing (F2-235)")
            .isTrue();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    /**
     * {@code CREATE UNIQUE [NONCLUSTERED] INDEX <name> ON <table> (<key>)<tail up to ;>}.
     * Group 1 is the key list, group 2 the tail — so a caller can assert the columns and the
     * presence or absence of a filter separately.
     */
    private static Matcher uniqueIndexOn(String sql, String index, String tableRegex) {
        return Pattern.compile(
            "CREATE\\s+UNIQUE\\s+(?:NONCLUSTERED\\s+)?INDEX\\s+\\[?" + index + "\\]?"
            + "\\s+ON\\s+" + tableRegex + "\\s*\\(([^)]*)\\)([^;]*)",
            Pattern.CASE_INSENSITIVE).matcher(sql);
    }

    /** Key columns of an index, brackets and ASC/DESC stripped, lower-cased, in declared order. */
    private static List<String> keyColumnsOf(String keyList) {
        List<String> key = new ArrayList<>();
        for (String c : keyList.split(",")) {
            String t = c.trim().replace("[", "").replace("]", "")
                        .replaceAll("(?i)\\s+(ASC|DESC)$", "").trim();
            if (!t.isEmpty()) {
                key.add(t.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return key;
    }

    private static String read(Path p) throws IOException {
        assertThat(Files.exists(p)).as("%s must exist", p).isTrue();
        return Files.readString(p);
    }

    /**
     * Blank out Java comments with a single left-to-right CHARACTER SCAN, preserving newlines and
     * every string/char literal.
     *
     * <p>⚠ Not a regex, and that is measured rather than stylistic: {@code /*[\s\S]*?*<!-- -->/}
     * recurses once per character consumed on the JVM and throws {@link StackOverflowError} over
     * whole-file input, which is why {@code MeasuredBaselineFreshnessTest} keeps a scanner too —
     * {@code GlobalExceptionHandler} is over a thousand lines. A scanner also buys what the regex
     * could not: a {@code //} inside a string literal (this handler builds {@code URI.create(
     * "https://…")}) can never open a comment, and the self-test's fixture deliberately puts the
     * banned phrase inside a literal, which must survive.
     *
     * <p>One pass, not two: two sequential passes corrupt the source whenever a {@code //}
     * comment's text contains a block delimiter (F2-198). Within one left-to-right scan that is
     * unrepresentable, and the branch order is a no-op (F2-211).
     */
    private static String stripJavaComments(String code) {
        return strip(code, "//", "/*", "*/", true);
    }

    /** SQL comments, same scan; {@code --} opens a line comment and literals are single-quoted. */
    private static String stripSqlComments(String sql) {
        return strip(sql, "--", "/*", "*/", false);
    }

    private static String strip(String src, String line, String open, String close, boolean java) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (src.startsWith(line, i)) {                       // line comment
                while (i < src.length() && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (src.startsWith(open, i)) {                 // block comment
                int end = src.indexOf(close, i + open.length());
                int stop = (end < 0) ? src.length() : end + close.length();
                while (i < stop) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
            } else if (c == '"' || (java && c == '\'') || (!java && c == '\'')) {
                char q = c;                                        // literal — copied verbatim
                out.append(c);
                i++;
                while (i < src.length()) {
                    char d = src.charAt(i);
                    out.append(d);
                    i++;
                    if (java && d == '\\' && i < src.length()) {     // escape
                        out.append(src.charAt(i));
                        i++;
                    } else if (d == q) {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
