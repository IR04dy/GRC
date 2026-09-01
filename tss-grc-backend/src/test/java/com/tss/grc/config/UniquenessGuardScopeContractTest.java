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

    // ── helpers ─────────────────────────────────────────────────────────────────────

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
