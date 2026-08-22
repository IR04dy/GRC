package com.tss.grc.security;

import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.governance.dto.GovernanceDtos.RoleSummary;
import com.tss.grc.module.iam.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Privileged" is one question, and this platform answered it twice.
 *
 * <h2>F2-280 — the defect this closes</h2>
 * <p>Two native queries in one package, both feeding the access-governance surface:
 * <ul>
 *   <li>{@code BreakGlassEventRepository#countPrivilegedHolders} read the <b>stored</b> flag
 *       {@code iam.roles.is_privileged}. It is the <i>"Privileged holders"</i> KPI rendered on
 *       {@code AccessAssuranceTabs}.</li>
 *   <li>{@code AccessReviewItemRepository#generateItems} with scope {@code PRIVILEGED}
 *       <b>derived</b> it from the grants ({@code p.action IN ('APPROVE','DELETE')}) and said
 *       so in its own javadoc. It populates the auto-scheduled quarterly NCA ECC 2-2 / SAMA
 *       CSF 3.3.5 privileged access review.</li>
 * </ul>
 * <p>Measured, live: <b>12 roles disagreed in dev and 7 in prod</b>. {@code CISO} — held,
 * granting {@code IAM:BREAK_GLASS}, {@code ADMIN:APPROVE} and {@code AUDIT_LOG:WRITE} — was
 * inside the review and absent from the KPI; {@code EMERGENCY_RESPONDER}, the break-glass role,
 * was the reverse. And the stored half had <b>no writer of any kind</b>: {@code Role} did not
 * map the column and {@code RoleUpsertRequest} has no such field, so every role created through
 * the Access Governance console is 0 by database default and the flagged set is byte-identical
 * in dev and prod — the fourteen V272 seeded.
 *
 * <p>🔑 F2-196's question (<i>how many things read this status, and do they agree?</i>) with
 * F2-205's follow-up (<i>and which of them is the server?</i>) — and here the answer is
 * <b>neither, so it is their union</b>. The stored flag carries a judgement the grants cannot
 * express (break-glass is privileged although it grants no APPROVE or DELETE) and the derived
 * half covers roles a tenant creates, which the flag never could. F2-251/F2-257's rule: ask
 * whether the label is a copy before reaching for the derive-from-the-parent fix.
 *
 * <p>🔑 {@code RoleRepository}'s own javadoc already carried the correct rule, about a
 * <em>different</em> predicate: F2-62's holder test is <i>"deliberately identical to
 * UserRepository#findActiveByOrganisationIdAndRoleName … F2-62's defect was a report and an
 * enforcement point computing access separately and drifting apart, each half defensible
 * alone."</i> That is this finding's shape, written on this very interface, one predicate over.
 * F2-195 again — the codebase can hold the correct rule, in prose, beside the place it was not
 * applied.
 *
 * <h2>What this gate is, and is not</h2>
 * <p>It is a <b>hard zero on the clause being written twice</b>, the
 * {@code SeatWindowPredicateSharingTest} precedent, with no exemption map: sharing the constant
 * is always available, so there is no correct code for it to fail on (F2-214). It is
 * <em>not</em> a claim that the union is the right policy — that is recorded on the constant and
 * is an owner's to revise; what it enforces is that revising it moves every reader at once.
 */
@DisplayName("F2-280 — one predicate decides whether a role is privileged")
class PrivilegedRolePredicateSharingTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** The declaring file, which is the one place the clause may be spelled out. */
    private static final String DECLARING =
        "src/main/java/com/tss/grc/module/iam/repository/RoleRepository.java";

    /** Files that must consume the constant rather than restating the clause. */
    private static final List<String> READERS = List.of(
        "src/main/java/com/tss/grc/module/iam/governance/repository/BreakGlassEventRepository.java",
        "src/main/java/com/tss/grc/module/iam/governance/repository/AccessReviewItemRepository.java");

    /**
     * The two halves, as they would be written by hand.
     *
     * <p>⚠ The derived half is deliberately the <b>two-element</b> list. {@code
     * SodViolationRepository} asks {@code p.action IN ('WRITE','APPROVE','DELETE')} — "a
     * read-only persona that can write", a different question about a different control — and a
     * pattern loose enough to match it would fail on correct code, which is the one failure a
     * gate must never have (F2-214).
     */
    /**
     * ⚠ A COMPARISON, not a mention. {@code Role} declares
     * {@code @Column(name = "is_privileged")} and that is a <b>mapping</b>, not a second
     * predicate — a pattern matching the bare column name fires on it and fails on correct
     * code, which is what this leg's first draft did. Scope a scan to the shape of its claim
     * (F2-192), and note the claim here is "nothing else <em>tests</em> the flag".
     */
    private static final Pattern STORED_FLAG = Pattern.compile(
        "\\bis_privileged\\b\\s*(?:=|<>|!=|>|<|IN\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DERIVED_GRANT = Pattern.compile(
        "\\baction\\s+IN\\s*\\(\\s*'APPROVE'\\s*,\\s*'DELETE'\\s*\\)", Pattern.CASE_INSENSITIVE);

    /**
     * One left-to-right alternation, newlines preserved so reported line numbers stay true
     * (F2-198's stripper bug, and F2-269's line-number correction). Two sequential passes is
     * the defect, not the branch order (F2-211).
     */
    private static final Pattern COMMENT =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    static String stripComments(String src) {
        return COMMENT.matcher(src).replaceAll(m -> m.group().replaceAll("[^\\n]", " "));
    }

    private record Src(String rel, String code) {}

    static List<Src> mainSources() {
        List<Src> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String raw = Files.readString(p, StandardCharsets.UTF_8);
                out.add(new Src(p.toString().replace('\\', '/'), stripComments(raw)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    // ── the invariant ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("the privilege clause is spelled out in exactly one place")
    void theClauseIsWrittenOnce() {
        var storedIn = new TreeMap<String, Integer>();
        var derivedIn = new TreeMap<String, Integer>();
        for (Src s : mainSources()) {
            int stored = count(STORED_FLAG, s.code());
            int derived = count(DERIVED_GRANT, s.code());
            if (stored > 0) storedIn.put(s.rel(), stored);
            if (derived > 0) derivedIn.put(s.rel(), derived);
        }
        assertThat(storedIn)
            .as("""
                The stored privilege flag `iam.roles.is_privileged` is referenced outside
                RoleRepository.PRIVILEGED_ROLE. F2-280: a second reader of this column is how
                the break-glass "Privileged holders" KPI and the quarterly NCA ECC privileged
                access review came to disagree on 12 roles in dev and 7 in prod. Splice the
                shared constant in instead — `"… WHERE" + RoleRepository.PRIVILEGED_ROLE`.
                Found in: %s""".formatted(storedIn))
            .containsOnlyKeys(DECLARING);
        assertThat(derivedIn)
            .as("""
                The derived privilege clause `action IN ('APPROVE','DELETE')` is written outside
                RoleRepository.PRIVILEGED_ROLE. Same finding from the other side: the derived
                half and the stored half are one question. Found in: %s""".formatted(derivedIn))
            .containsOnlyKeys(DECLARING);
        // Anti-vacuity: a renamed constant or a moved file must fail loudly rather than report
        // a clean tree (F2-69 — a detector that stops matching looks exactly like success).
        assertThat(storedIn.getOrDefault(DECLARING, 0))
            .as("the declaring file must still contain the stored-flag test")
            .isEqualTo(1);
        assertThat(derivedIn.getOrDefault(DECLARING, 0))
            .as("the declaring file must still contain the derived-grant test")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("both readers consume the constant")
    void bothReadersConsumeTheConstant() {
        for (String rel : READERS) {
            String code = stripComments(read(rel));
            assertThat(code)
                .as("%s must splice RoleRepository.PRIVILEGED_ROLE rather than restating the "
                    + "clause — a reader that stops consuming it is how the two definitions "
                    + "drifted in the first place", rel)
                .contains("RoleRepository.PRIVILEGED_ROLE");
        }
    }

    @Test
    @DisplayName("the constant leads with a newline, so a spliced host cannot fuse tokens")
    void theConstantLeadsWithANewline() {
        String sql = RoleRepository.PRIVILEGED_ROLE;
        assertThat(sql)
            .as("""
                Java strips TRAILING whitespace in a text block, so a host writing
                `"… WHERE" + PRIVILEGED_ROLE` produces `WHERE(r.is_privileged` unless this
                constant leads with a newline. CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW
                records the same trap killing the app at context startup, not at one endpoint.""")
            .startsWith("\n");
        assertThat(sql.strip()).as("and it must not be empty").isNotEmpty();
    }

    @Test
    @DisplayName("the constant is a balanced, self-contained clause over the alias r")
    void theConstantIsSelfContained() {
        String sql = RoleRepository.PRIVILEGED_ROLE;
        int depth = 0;
        int min = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
            min = Math.min(min, depth);
        }
        assertThat(depth).as("parentheses must balance, or every host query is malformed")
            .isZero();
        assertThat(min).as("it must never close a paren the host opened").isZero();
        assertThat(sql).as("the host supplies iam.roles as `r`").contains("r.is_privileged");
        assertThat(sql).as("the host supplies iam.roles as `r`").contains("rp_priv.role_id = r.id");
        // Inner aliases must not be the bare rp/p that host queries already use, or splicing it
        // into AccessReviewItemRepository's MODULE branch would collide.
        assertThat(sql)
            .as("inner aliases are rp_priv/p_priv so a host that already joins rp/p is safe")
            .doesNotContain(" rp ").doesNotContain(" p ")
            .contains("rp_priv").contains("p_priv");
    }

    @Test
    @DisplayName("it is the UNION of both halves, not either one")
    void itIsAUnion() {
        String sql = RoleRepository.PRIVILEGED_ROLE.replaceAll("\\s+", " ");
        assertThat(sql)
            .as("""
                Collapsing to either half is the plausible simplification and it discards real
                classification: the stored flag carries a judgement the grants cannot express
                (EMERGENCY_RESPONDER is the break-glass role and grants no APPROVE or DELETE),
                and the derived half is the only one that covers a role a tenant creates.""")
            .containsPattern("r\\.is_privileged\\s*=\\s*1")
            .contains(" OR EXISTS")
            .containsPattern("action IN \\( ?'APPROVE', ?'DELETE' ?\\)");
    }

    /**
     * The stripper is load-bearing here, and this slice's own javadoc is why: {@code
     * RoleRepository}'s comment quotes {@code p.action IN ('APPROVE','DELETE')} and {@code
     * BreakGlassEventRepository}'s quotes {@code r.is_privileged = 1}, both explaining the
     * defect they replaced. Reading raw source counts the epitaph as the corpse (F2-193) and
     * this file's own corrections would fail it.
     *
     * <p>One left-to-right alternation, and the negative control is the <b>two-pass</b> form,
     * not a reordered one: within one alternation the branch order is a no-op (F2-211).
     */
    @Test
    @DisplayName("the comment stripper blanks comments, keeps newlines, and survives // with /*")
    void theStripperIsSound() {
        String hostile = String.join("\n",
            "int a = 1;                      // see /* the note below",
            "String q = \"is_privileged = 1\";",
            "/* block\n   spanning */ int b = 2;");
        String stripped = stripComments(hostile);
        assertThat(stripped.chars().filter(c -> c == '\n').count())
            .as("line numbers must survive, or every reported location is wrong (F2-269)")
            .isEqualTo(hostile.chars().filter(c -> c == '\n').count());
        assertThat(stripped).as("the code either side of a comment must survive")
            .contains("int a = 1;").contains("int b = 2;")
            .contains("String q = \"is_privileged = 1\";");
        assertThat(stripped).as("comment prose must not survive")
            .doesNotContain("see").doesNotContain("the note below").doesNotContain("spanning");
        // The two-pass form: block comments first, then line comments. At the `//` above, the
        // inner `/*` opens a block that runs to the next `*/`, eating the declarations between.
        String twoPass = hostile.replaceAll("/\\*[\\s\\S]*?\\*/", " ").replaceAll("//[^\\n]*", " ");
        assertThat(twoPass)
            .as("""
                the two-pass form must lose code the one-alternation form keeps — if this ever
                stops holding, the negative control has gone vacuous and the stripper is
                unproven (F2-274)""")
            .doesNotContain("String q =");
    }

    // ── the mapping and the wire ────────────────────────────────────────────────

    @Test
    @DisplayName("Role maps is_privileged, so the classification is reachable from Java")
    void roleMapsThePrivilegeFlag() {
        Field f = Arrays.stream(Role.class.getDeclaredFields())
            .filter(x -> "isPrivileged".equals(x.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("""
                Role no longer maps is_privileged. The column is NOT NULL DEFAULT 0, so
                unmapping it puts the database default back in charge of a privileged-access
                classification and takes the flag out of reach of every Java reader — F2-279's
                mechanism, on a security column."""));
        assertThat(f.getType()).isEqualTo(Boolean.class);
        var col = f.getAnnotation(jakarta.persistence.Column.class);
        assertThat(col).as("it must name the column explicitly").isNotNull();
        assertThat(col.name()).isEqualTo("is_privileged");
    }

    @Test
    @DisplayName("both halves of the wire declare the classification")
    void bothHalvesOfTheWireDeclareIt() {
        assertThat(Arrays.stream(RoleSummary.class.getRecordComponents())
                       .map(RecordComponent::getName))
            .as("the server must report the classification, or the console cannot show it")
            .contains("privileged");
        // The frontend half is read from disk, as ConnectorBackingContractTest reads api.ts:
        // a response field the client type never declares is invisible to every screen
        // (F2-198's crossBorderTransfer shape).
        Path api = Path.of("..", "tss-grc-ui-v2", "src", "lib", "api.ts");
        assertThat(Files.exists(api)).as("api.ts must be resolvable from the backend module").isTrue();
        // ⚠ Strip the comments FIRST and assert the DECLARATION SHAPE, not the token. The first
        // draft was `contains("privileged")` over raw source, and the JSDoc above the field says
        // "privileged-access classification" — so deleting the field left the leg green. F2-194's
        // substring-satisfied-by-something-else, in a leg written after quoting it.
        String ts = stripComments(readAll(api));
        int start = ts.indexOf("export interface GovRoleDto");
        assertThat(start).as("GovRoleDto must still exist in api.ts").isGreaterThan(-1);
        int end = ts.indexOf('}', start);
        assertThat(end).as("GovRoleDto's body must be delimited").isGreaterThan(start);
        assertThat(ts.substring(start, end))
            .as("""
                GovRoleDto must DECLARE `privileged: boolean`, or the server reports a
                classification no screen can render (F2-198's crossBorderTransfer shape).""")
            .containsPattern("\\bprivileged\\s*:\\s*boolean\\b");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String read(String rel) {
        return readAll(Path.of(rel));
    }

    private static String readAll(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
