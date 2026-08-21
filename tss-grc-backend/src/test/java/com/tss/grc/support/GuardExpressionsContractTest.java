package com.tss.grc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-262 — {@link GuardExpressions} is the platform's only answer to "which role names does this
 * guard recite", and this class is what keeps it that way.
 *
 * <h2>Why a hard zero is safe here</h2>
 *
 * <p>A gate that fails on correct code is worse than no gate: it trains the next reader to edit the
 * assertion rather than the code (F2-214). This one cannot, because there is always a correct
 * alternative — {@link GuardExpressions} answers both the extraction question
 * ({@link GuardExpressions#rolesRecitedBy}) and the classification question
 * ({@link GuardExpressions#hasRoleLeg}, {@link GuardExpressions#isPureRoleGuard}), so no test ever
 * needs its own role-call regex. That is why the ban below has no exemption map.
 *
 * <h2>The two files the ban cannot cover, and why</h2>
 *
 * <p>{@code GuardExpressions.java} declares the patterns by definition. <b>This file</b> carries the
 * literal pre-fix regexes as positive-control strings — a detector for the shape has to contain the
 * shape — which is the same reason F2-193's claim detector had to strip quotations before reading
 * prose: a detector that cannot tell a specimen from an instance forces every control to paraphrase
 * away its own evidence. Both exclusions are asserted in <b>both</b> directions
 * ({@link #theTwoExclusionsAreExactlyTheTwoFilesThatNeedThem}), so a third cannot be added quietly.
 */
class GuardExpressionsContractTest {

    private static final Path TEST_ROOT = Path.of("src", "test", "java", "com", "tss", "grc");

    /**
     * The only two sources permitted to write a guard regex — of either question. Not an exemption
     * map: an exemption map is a list somebody remembered, and F2-198's whole lineage is about a
     * population narrowed by one. These two are structural: the declaration site, and the file that
     * must quote it.
     *
     * <p>F2-263 added the permission-pair ban and read it off <b>this</b> set rather than declaring a
     * second one naming the same two files. Two lists that must agree, with nobody maintaining the
     * agreement, is the defect this whole class exists to remove (F2-177) — committing it inside the
     * class would be one level up from the eleven extractors it replaced.
     */
    private static final Set<String> MAY_DECLARE_A_GUARD_REGEX =
        Set.of("GuardExpressions.java", "GuardExpressionsContractTest.java");

    /** Where a {@code Pattern.compile(...)} call begins. */
    private static final Pattern COMPILE_CALL = Pattern.compile("Pattern\\s*\\.\\s*compile\\s*\\(");

    /** A Java string literal, escapes included. */
    private static final Pattern JAVA_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * A regex that describes a SpEL <b>role call</b>: it mentions {@code has} and it mentions
     * {@code Role}. Both halves are load-bearing.
     *
     * <p>⚠⚠ <b>{@code Role} alone is not the discriminator, and the first draft of this gate proved
     * it by failing on correct code.</b> {@code RoleNameSurfaceRatchetTest} declares
     * {@code ParentGuard\.(?:roles|permOrRoles)\s*\(} — which contains {@code Roles} — and that is
     * the platform's <b>second, independent role-name surface</b>: the Java role lists in
     * {@code AttachmentAccessPolicy} that no {@code @PreAuthorize} scan sees, and whose drift F2-52
     * and F2-177 each had to find by hand. It asks a different question about a different artefact
     * and must keep its own extractor. Requiring {@code has} as well separates a SpEL guard call
     * from a Java role array, and {@link #theDetectorDoesNotFireOnLegitimateGuardAssertions} pins
     * that in the direction that matters.
     */
    private static final Pattern ROLE_CALL_REGEX = Pattern.compile("(?s)has.*Role|Role.*has");

    /**
     * A regex that describes a SpEL <b>permission-pair</b> extraction: it mentions
     * {@code hasPermission} and captures <b>both</b> arguments.
     *
     * <p>Recognised by shape, never by name, and the two clauses are what keep four legitimate
     * neighbours out of the population:
     *
     * <ul>
     *   <li><b>Two capture groups.</b> Excludes {@code hasPermission\(} (a presence count),
     *       {@code hasPermission\(\s*'} (a prefix test), and
     *       {@code hasPermission\(\s*'[A-Z_]+'\s*,\s*'EXPORT'\s*\)} (a specific action, no
     *       capture) — as well as {@code ChangeDocumentationFreshnessTest}'s
     *       {@code hasPermission\('CHANGE'…'([A-Z_]+)'\)}, which hard-codes the module and so asks
     *       the narrower question "which CHANGE actions".</li>
     *   <li><b>Exactly one separator comma.</b> This is the whole three-argument exclusion.
     *       {@code CommitteeSeatGuardTest} extracts {@code (type, relation)} out of
     *       {@code hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')} — <em>two</em> capture groups,
     *       and a different question about a different mechanism (F2-177's committee seat, whose
     *       {@code targetId} is a committee TYPE rather than a permission module). Counting the
     *       comma is what tells them apart; a name-based exclusion would let the next one escape by
     *       being spelled differently (F2-192).</li>
     * </ul>
     *
     * <p>Both clauses are pinned in both directions by
     * {@link #thePairClassifierIsPinnedInBothDirections} — a narrowing that is too wide silently
     * re-tiers the tree, and one that is too narrow silently shrinks the population, which reads
     * exactly like a complete sweep.
     */
    private static final Pattern PERMISSION_PAIR_REGEX = Pattern.compile("hasPermission");

    /** Java comments, one left-to-right alternation (see {@link GuardExpressions#stripComments}). */
    private static final Pattern COMMENTS = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  1. the semantics, pinned in both directions
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Every shape that MUST yield role names. Six spellings existed across the eight hand-rolled
     * copies and each was narrower than this on at least one axis; the helper takes the widest
     * correct reading of all of them, so consolidating can only add a name.
     */
    @Test
    @DisplayName("every guard shape that names a role yields that role")
    void everyRoleBearingShapeIsExtracted() {
        Map<String, Set<String>> cases = new TreeMap<>();
        cases.put("hasAnyRole('CISO','PLATFORM_ADMIN')", Set.of("CISO", "PLATFORM_ADMIN"));
        // 67 guards in the tree use the singular form; one repaired copy could not see any of them.
        cases.put("hasRole('PLATFORM_ADMIN')", Set.of("PLATFORM_ADMIN"));
        // SpEL permits whitespace; two of the six spellings could not.
        cases.put("hasAnyRole ('CISO')", Set.of("CISO"));
        cases.put("hasRole ( 'CISO' )", Set.of("CISO"));
        // A role said longhand is still a role; five of the six spellings returned nothing here.
        cases.put("hasAnyAuthority('ROLE_CISO')", Set.of("CISO"));
        cases.put("hasAuthority('ROLE_BOARD_SECRETARY')", Set.of("BOARD_SECRETARY"));
        // The composed shape F2-159/F2-161 build ownership controls from: the role leg must survive.
        cases.put("hasPermission('BCM','APPROVE') or hasAnyRole('CRISIS_MANAGER')",
            Set.of("CRISIS_MANAGER"));
        cases.put("hasPermission('AI','EXPORT') and hasAnyRole('AUDITOR','CISO')",
            Set.of("AUDITOR", "CISO"));
        // A name outside [A-Z_] — one spelling's inner token could not see it. Latent in the tree
        // (all 38 recited names are upper snake case) and pinned so a future name is not dropped.
        cases.put("hasAnyRole('SOC_TIER_2')", Set.of("SOC_TIER_2"));
        // Multi-line, because the compiler assembles an annotation value however it was formatted.
        cases.put("hasAnyRole('A',\n   'B')", Set.of("A", "B"));

        Map<String, Set<String>> wrong = new TreeMap<>();
        cases.forEach((expr, expected) -> {
            Set<String> actual = GuardExpressions.rolesRecitedBy(expr);
            if (!actual.equals(new TreeSet<>(expected))) {
                wrong.put(expr, actual);
            }
        });

        assertThat(wrong).as("""
            %s did not yield the roles it recites. An extractor that cannot see a name reports a \
            SMALLER audience, and a smaller audience reads exactly like a guard that recites \
            nothing (F2-198) — which is the quiet direction for this whole family to be wrong \
            in.""".formatted(wrong)).isEmpty();
    }

    /**
     * Every shape that must yield NOTHING. This is the direction the eight hand-rolled copies got
     * wrong, and it is the half that makes a gate fail on correct code.
     */
    @Test
    @DisplayName("no permission form is ever mistaken for a role name")
    void noPermissionFormYieldsARoleName() {
        List<String> notRoles = List.of(
            // The two-argument module permission. A bare quoted-token scan reports MODULE and ACTION
            // as ghost roles, which is what F2-254, F2-259 and F2-260 each had to repair.
            "hasPermission('BCM','READ')",
            "hasPermission('AUDIT','WRITE')",
            "hasPermission( 'RRP' , 'APPROVE' )",
            // F2-159/F2-161's three-argument entity-ownership form: the FIRST argument is not quoted,
            // so only the closing paren distinguishes it from a two-argument call.
            "hasPermission(#externalId,'ML_MODEL','OWNER')",
            "hasPermission(#runId,'EXERCISE_RUN','OWNER')",
            // F2-177's committee-seat form, whose first argument IS quoted — the shape F2-255
            // recorded as reportable as a ghost by any pattern that matches either arity.
            "hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')",
            // A permission spelled as its raw granted authority. Not a role: it belongs to
            // rawAuthoritiesIn, and folding it in would hide an evaluator bypass inside a role count.
            "hasAnyAuthority('RISK_READ','INCIDENT_READ')",
            "hasAuthority('KB_WRITE')",
            // Not a guard at all.
            "isAuthenticated()",
            "permitAll()");

        Map<String, Set<String>> leaked = new TreeMap<>();
        for (String expr : notRoles) {
            Set<String> got = GuardExpressions.rolesRecitedBy(expr);
            if (!got.isEmpty()) {
                leaked.put(expr, got);
            }
        }

        assertThat(leaked).as("""
            %s was read as reciting a role name. This is the defect the whole class exists to \
            prevent: a quoted-token scan has no notion of which function a token is an argument to, \
            so a ghost-role detector built on one flags EVERY role-to-permission conversion — the \
            change F2-05 exists to make — as a pair of ghosts. Five slices have been bitten by it, \
            one per converting module.""".formatted(leaked)).isEmpty();
    }

    @Test
    @DisplayName("a raw granted authority is reported as itself, never as a role")
    void rawAuthoritiesAreTheirOwnFinding() {
        assertThat(GuardExpressions.rawAuthoritiesIn("hasAnyAuthority('RISK_READ','INCIDENT_READ')"))
            .containsExactly("INCIDENT_READ", "RISK_READ");
        assertThat(GuardExpressions.rawAuthoritiesIn("hasAnyAuthority('ROLE_CISO')"))
            .as("ROLE_CISO is a role said longhand; it belongs to rolesRecitedBy and must not be "
              + "double-counted as an evaluator bypass")
            .isEmpty();
        assertThat(GuardExpressions.rolesRecitedBy("hasAnyAuthority('ROLE_CISO','RISK_READ')"))
            .containsExactly("CISO");
    }

    @Test
    @DisplayName("the leg predicates agree with the extraction")
    void legPredicatesAgreeWithExtraction() {
        assertThat(GuardExpressions.hasRoleLeg("hasAnyRole('CISO')")).isTrue();
        assertThat(GuardExpressions.hasRoleLeg("hasRole('CISO')")).isTrue();
        assertThat(GuardExpressions.hasRoleLeg("hasPermission('BCM','READ')")).isFalse();
        assertThat(GuardExpressions.hasPermissionLeg("hasPermission(#id,'ML_MODEL','OWNER')")).isTrue();
        assertThat(GuardExpressions.hasPermissionLeg("hasAnyRole('CISO')")).isFalse();

        assertThat(GuardExpressions.isPureRoleGuard("hasAnyRole('CISO','PLATFORM_ADMIN')")).isTrue();
        assertThat(GuardExpressions.isPureRoleGuard(
            "hasPermission('BCM','APPROVE') or hasAnyRole('CRISIS_MANAGER')")).isFalse();
        assertThat(GuardExpressions.isPureRoleGuard("hasPermission('BCM','READ')")).isFalse();

        assertThat(GuardExpressions.rolesRecitedBy(null)).isEmpty();
        assertThat(GuardExpressions.hasRoleLeg(null)).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  2. the two properties every source-text caller inherits
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Anti-vacuity, and it is the property {@code ObLifecycleTest}'s javadoc says belongs here:
     * <i>"a regex that matches nothing satisfies every downstream assertion forever, so the
     * anti-vacuity check belongs in the helper, where every caller inherits it."</i> Written down in
     * the tree for four slices with no helper to belong to.
     */
    @Test
    @DisplayName("an absent guard constant throws rather than returning an empty set")
    void anAbsentConstantIsAHardFailureAndNotAnEmptyAnswer() {
        String src = "class C { private static final String READ_GUARD = \"hasAnyRole('CISO')\"; }";

        assertThat(GuardExpressions.rolesRecitedByConstant(src, "READ_GUARD")).containsExactly("CISO");

        assertThatThrownBy(() -> GuardExpressions.guardConstant(src, "WRITE_GUARD"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("WRITE_GUARD")
            .hasMessageContaining("passes forever");
    }

    /**
     * Tolerance, which is the half two of the eight copies did not have: they required the
     * initialiser to BE a {@code hasAnyRole} literal and asserted {@code find()}, so converting
     * their own module would have failed the build with "must be a hasAnyRole string".
     *
     * <p>🔑 Not hypothetical. Those two copies are {@code dataclass} and {@code outsourcing}, two of
     * the four modules F2-257 filed as having no catalogued permissions — i.e. two of the next
     * conversions in this programme's own backlog. The fourth failure was scheduled, not possible.
     */
    @Test
    @DisplayName("a CONVERTED guard constant yields no roles instead of refusing to parse")
    void aConvertedConstantReportsDriftRatherThanRefusingToParse() {
        String converted =
            "class C { private static final String READ_GUARD = \"hasPermission('RRP','READ')\"; }";

        assertThat(GuardExpressions.rolesRecitedByConstant(converted, "READ_GUARD")).as("""
            A converted guard must contribute NO role names — not RRP and READ, and not an \
            exception. Reporting the pair is a false ghost accusation; throwing is a gate refusing \
            the change F2-05 exists to make. Both were live in this tree.""").isEmpty();

        // and the constant is still resolvable, so the anti-vacuity half did not go with it
        assertThat(GuardExpressions.guardConstant(converted, "READ_GUARD"))
            .contains("hasPermission");
    }

    /**
     * A name is matched on a word boundary, because one copy's subject declares its constants as
     * bare {@code READ} / {@code WRITE} / {@code APPROVE} and its sibling's as {@code READ_GUARD} —
     * both spellings are live in this tree.
     *
     * <p>⚠ <b>This assertion is protected by a different part of the pattern than the part it is
     * named after, and the mutation harness is what said so.</b> Removing the trailing {@code \b}
     * from {@code guardConstant}'s regex leaves every answer unchanged — the character after the
     * name in {@code READ_GUARD} is {@code _}, which is neither {@code \s} nor {@code =}, so
     * {@code \s*=} is doing the work. Recorded as an EQUIVALENT mutant rather than a hole: a
     * surviving mutant is a hole only if the mutated code is actually wrong (F2-206). 🔑 The reason
     * to write it down is that a reader tidying {@code \s*} away would break this test while the
     * word boundary the test is named for sat there looking responsible — the same shape F2-235
     * found, where blocked-vector assertions did not depend on the mechanisms they appeared to test.
     */
    @Test
    @DisplayName("asking for READ does not match READ_GUARD")
    void constantNamesAreMatchedOnAWordBoundary() {
        String src = """
            class C {
              private static final String READ_GUARD = "hasAnyRole('WRONG')";
              private static final String READ = "hasAnyRole('RIGHT')";
            }""";
        assertThat(GuardExpressions.rolesRecitedByConstant(src, "READ")).containsExactly("RIGHT");
        assertThat(GuardExpressions.rolesRecitedByConstant(src, "READ_GUARD")).containsExactly("WRONG");
    }

    /**
     * Comment stripping, which <b>none</b> of the four source-text copies had (one stripped inline at
     * a single call site, which is the divergence rather than the fix).
     *
     * <p>The hazard is real in this tree: {@code RrpController}'s javadoc says
     * <i>"APPROVE_GUARD was hasAnyRole('PLATFORM_ADMIN','CRO','CFO')"</i> — F2-193's
     * epitaph-as-corpse, one javadoc rewording away from a scan reading the retired guard as the
     * live one.
     */
    @Test
    @DisplayName("a javadoc quoting a RETIRED guard is not read as the live one")
    void theEpitaphIsNotMistakenForTheCorpse() {
        String src = """
            class C {
              /** APPROVE_GUARD was: private static final String APPROVE_GUARD = "hasAnyRole('GHOST')"; */
              private static final String APPROVE_GUARD = "hasPermission('RRP','APPROVE')";
            }""";
        assertThat(GuardExpressions.rolesRecitedByConstant(src, "APPROVE_GUARD")).as("""
            The commented-out declaration was read as the live one. A detector that reads a \
            codebase's own record of its deletions treats the epitaph as the corpse (F2-193), and \
            here it would resurrect a ghost role onto a converted guard.""").isEmpty();
    }

    /**
     * The comment stripper uses ONE left-to-right alternation. Two sequential passes delete live
     * code whenever a {@code //} comment's prose contains a literal {@code /*} with a closing
     * delimiter downstream — F2-198 measured that at 45% of {@code App.tsx}, and the corrupted
     * source yields a SMALLER population, which reads exactly like a clean tree.
     */
    @Test
    @DisplayName("the comment stripper survives a line comment whose prose opens a block")
    void theStripperIsOneAlternationAndNotTwoPasses() {
        String src = "// see the /* form below\n"
            + "private static final String G = \"hasAnyRole('KEPT')\";\n"
            + "/* a real block */\n";

        assertThat(GuardExpressions.rolesRecitedByConstant(src, "G")).as("""
            The declaration was eaten. Written as two passes — block first, then line — the block \
            pattern runs from the /* inside the line comment to the next closing delimiter and takes \
            the code between them (F2-198). Within ONE alternation the branch order is a no-op \
            (F2-211): the defect is the second pass.""").containsExactly("KEPT");

        assertThat(GuardExpressions.stripComments("a // b\nc")).doesNotContain("b");
        assertThat(GuardExpressions.stripComments("x /* y */ z")).doesNotContain("y");
        assertThat(GuardExpressions.stripComments("String u = \"https://x.test/a\";"))
            .as("(?<!:) keeps a URL's // out of the line-comment branch")
            .contains("https://x.test/a");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  3. the hard zero
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * ⚠⚠ <b>This zero cannot see the one member that still had the live defect, and that is worth
     * stating rather than letting the zero imply otherwise.</b> Run against master, this detector
     * finds <b>8</b> hand-rolled role-call regexes; the population is <b>9</b>.
     * {@code CrisisRoomGuardTest} is invisible to it <em>by construction</em> — it had no scoping at
     * all, so there was no role-call regex to ban — and it was precisely the copy that would have
     * reported {@code BCM} and {@code READ} as ghost roles on a correct conversion.
     *
     * <p>🔑 <b>A ban on hand-rolled extractors cannot see the copy that never wrote one.</b> The
     * complement is {@link #everyFormerCopyCallsTheHelper}, which names all nine. Neither leg is
     * sufficient and the pair is the contract — a hard zero over a population the instrument cannot
     * see is the most reassuring possible artefact (F2-205).
     */
    @Test
    @DisplayName("no test source hand-rolls a role-name extractor")
    void thereIsExactlyOneRoleNameExtractorInTheTestTree() {
        Map<String, List<String>> offenders = new TreeMap<>();
        int scanned = 0;

        for (Path p : testSources()) {
            String name = p.getFileName().toString();
            if (MAY_DECLARE_A_GUARD_REGEX.contains(name)) {
                continue;
            }
            scanned++;
            List<String> hits = roleCallRegexesIn(COMMENTS.matcher(read(p)).replaceAll(" "));
            if (!hits.isEmpty()) {
                offenders.put(name, hits);
            }
        }

        // A floor above the largest WRONG answer (F2-215). The wrong answer for a filesystem walk is
        // zero files, which would make every assertion above it vacuous.
        assertThat(scanned).as("the test-source walk resolved nothing — TEST_ROOT is wrong")
            .isGreaterThanOrEqualTo(400);

        assertThat(offenders).as("""
            %s declares its own role-call regex. GuardExpressions answers both the extraction \
            question (rolesRecitedBy) and the classification question (hasRoleLeg / \
            isPureRoleGuard), so there is never a reason to write one — which is why this zero has \
            no exemption map. Eight copies in six spellings existed before F2-262 and five slices \
            were bitten by one, ONE PER CONVERTING MODULE: F2-145(e) twice, then F2-254, F2-259 and \
            F2-260.""".formatted(offenders)).isEmpty();
    }

    /**
     * The detector must fire on all three mechanisms that existed, or the zero above is a statement
     * about the detector rather than about the tree (F2-244). These are the literal shapes, copied
     * from the eight copies before they were consolidated.
     */
    @Test
    @DisplayName("the detector fires on all three pre-fix mechanisms")
    void theDetectorFiresOnEveryShapeThatExisted() {
        Map<String, String> preFix = new TreeMap<>();
        // mechanism 1 — reflective, quoted-token, scoped (four of the six spellings)
        preFix.put("F2-254 BiaApprovalChainGuardTest",
            "private static final Pattern HAS_ANY_ROLE = Pattern.compile(\"hasAnyRole\\\\(([^)]*)\\\\)\");");
        preFix.put("F2-259 AuditCommitteeGuardTest",
            "private static final Pattern ROLE_CALL = Pattern.compile(\"has(?:Any)?Role\\\\s*\\\\(([^)]*)\\\\)\");");
        preFix.put("canonical RoleGuardConversionRatchetTest",
            "private static final Pattern ROLE_CALL = Pattern.compile(\"has(?:AnyRole|Role)\\\\(([^)]*)\\\\)\");");
        // mechanism 2 — source-text, quoted-token, inline pattern
        preFix.put("F2-260 RrpLifecycleTest",
            "Pattern.compile(\"has(?:Any)?Role\\\\s*\\\\(([^)]*)\\\\)\").matcher(m.group(1));");
        // mechanism 3 — source-text, requires the initialiser to BE a role guard, split on comma
        preFix.put("DataclassLifecycleTest",
            "Pattern.compile(\"String \" + constant + \"\\\\s*=\\\\s*\\\"hasAnyRole\\\\(([^)]*)\\\\)\\\"\")");
        preFix.put("OutsourcingLifecycleTest",
            "Pattern.compile(constant + \"\\\\s*=\\\\s*\\\\n?\\\\s*\\\"hasAnyRole\\\\(([^\\\\)]*)\\\\)\\\"\")");

        List<String> missed = preFix.entrySet().stream()
            .filter(e -> roleCallRegexesIn(e.getValue()).isEmpty())
            .map(Map.Entry::getKey)
            .toList();

        assertThat(missed).as("""
            %s is a shape that existed in this tree and the detector cannot see it. A detector that \
            cannot match reads exactly like a clean tree, and it UNDER-reports — the dangerous \
            direction (F2-208).""".formatted(missed)).isEmpty();
    }

    /**
     * And it must NOT fire on the legitimate things a guard test does, or it is a gate failing on
     * correct code (F2-214). A plain string assertion about a guard is not an extractor.
     */
    @Test
    @DisplayName("the detector does not fire on a plain assertion about a guard string")
    void theDetectorDoesNotFireOnLegitimateGuardAssertions() {
        List<String> legitimate = List.of(
            "assertThat(guard).doesNotContain(\"hasAnyRole(\");",
            "assertThat(expr).contains(\"hasAnyRole('CISO')\");",
            "private static final String READ = \"hasAnyRole('CISO','PLATFORM_ADMIN')\";",
            "// F2-52: was hasAnyRole('COMPANY_SECRETARY','PLATFORM_ADMIN')",
            "assertThat(GuardExpressions.rolesRecitedBy(guard)).containsExactly(\"CISO\");",
            "Pattern.compile(\"hasPermission\\\\('([A-Z_]+)'\\\\s*,\\\\s*'([A-Z_]+)'\\\\)\");");

        List<String> falsePositives = legitimate.stream()
            .filter(s -> !roleCallRegexesIn(s).isEmpty())
            .toList();

        assertThat(falsePositives).as("""
            %s is correct code and the detector flagged it. A gate that fails on correct code trains \
            the next reader to edit the assertion rather than the code (F2-214) — and the last entry \
            is deliberate: a hasPermission PAIR extractor is a DIFFERENT question, hand-rolled in \
            seven classes, and consolidating it is a separate slice.""".formatted(falsePositives))
            .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  4. the consolidation is real, and the exclusions are exactly two
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The nine classes that used to hand-roll this now call the helper. Named individually rather
     * than counted, because a count is satisfied by the wrong files (F2-241) — and because these
     * nine are the measured population, so a member that stops calling the helper has either been
     * fixed or has quietly grown its own extractor back.
     *
     * <p>⚠ <b>Nine, not eight.</b> My hand-written census put it at eight; the hard zero below found
     * {@code DataOfficerLifecycleTest} on its first run. A detector scoped to the shapes already seen
     * returns a smaller population, and a smaller population reads exactly like a complete sweep
     * (F2-198). The gate is the measurement; the census was the guess.
     */
    @Test
    @DisplayName("all nine former copies now call the shared helper")
    void everyFormerCopyCallsTheHelper() {
        Map<String, Path> expected = new TreeMap<>();
        expected.put("CrisisRoomGuardTest", TEST_ROOT.resolve(
            Path.of("module", "bcm", "cycle", "CrisisRoomGuardTest.java")));
        expected.put("BiaApprovalChainGuardTest", TEST_ROOT.resolve(
            Path.of("module", "bcm", "BiaApprovalChainGuardTest.java")));
        expected.put("AuditCommitteeGuardTest", TEST_ROOT.resolve(
            Path.of("module", "audit_committee", "AuditCommitteeGuardTest.java")));
        expected.put("RrpLifecycleTest", TEST_ROOT.resolve(
            Path.of("module", "rrp", "service", "RrpLifecycleTest.java")));
        expected.put("DataclassLifecycleTest", TEST_ROOT.resolve(
            Path.of("module", "dataclass", "service", "DataclassLifecycleTest.java")));
        expected.put("OutsourcingLifecycleTest", TEST_ROOT.resolve(
            Path.of("module", "outsourcing", "service", "OutsourcingLifecycleTest.java")));
        expected.put("RoleGuardConversionRatchetTest", TEST_ROOT.resolve(
            Path.of("security", "RoleGuardConversionRatchetTest.java")));
        expected.put("IntegrationsAuthorizationTest", TEST_ROOT.resolve(
            Path.of("security", "IntegrationsAuthorizationTest.java")));
        // Found by the hard zero below, not by the census that preceded it.
        expected.put("DataOfficerLifecycleTest", TEST_ROOT.resolve(
            Path.of("module", "data_officer", "service", "DataOfficerLifecycleTest.java")));

        Map<String, String> notCalling = new TreeMap<>();
        expected.forEach((name, path) -> {
            if (!Files.exists(path)) {
                notCalling.put(name, "source no longer at " + path);
                return;
            }
            String code = COMMENTS.matcher(read(path)).replaceAll(" ");
            // Assert the CALL SHAPE, never the token: a bare contains() is satisfied by the import
            // statement alone, which is how F2-194's and F2-205's gates were defeated.
            if (!Pattern.compile("GuardExpressions\\s*\\.\\s*\\w+\\s*\\(").matcher(code).find()) {
                notCalling.put(name, "imports or mentions GuardExpressions but never calls it");
            }
        });

        assertThat(notCalling).as("""
            %s was one of the eight hand-rolled copies and no longer calls the shared helper. Either \
            it grew its own extractor back, or it stopped asking the question — and the second is \
            fine only if this list is updated to say so.""".formatted(notCalling)).isEmpty();
    }

    @Test
    @DisplayName("the ban's two exclusions are exactly the two files that need them")
    void theTwoExclusionsAreExactlyTheTwoFilesThatNeedThem() {
        // Forward: each exclusion is load-bearing — the file really does declare a role-call regex,
        // so removing it from the set would make the hard zero fail on correct code.
        Map<String, Boolean> declares = new TreeMap<>();
        for (String name : MAY_DECLARE_A_GUARD_REGEX) {
            Path p = TEST_ROOT.resolve(Path.of("support", name));
            assertThat(Files.exists(p)).as("%s is excluded from the ban and does not exist", name)
                .isTrue();
            declares.put(name, !roleCallRegexesIn(read(p)).isEmpty());
        }
        assertThat(declares).as("""
            an exclusion whose file does NOT declare a role-call regex is an exclusion for something \
            that was never in the population, which reads exactly like a correct exclusion (F2-199). \
            %s""".formatted(declares)).containsOnly(
                Map.entry("GuardExpressions.java", true),
                Map.entry("GuardExpressionsContractTest.java", true));

        // Reverse: both live in support/, so a third file cannot be added by name alone without
        // also appearing here.
        assertThat(MAY_DECLARE_A_GUARD_REGEX).hasSize(2);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  5. F2-263 — the same argument, one question over: the permission-pair extractor
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The eleven files that hand-rolled a {@code (MODULE, ACTION)} extractor, in four spellings.
     *
     * <p>F2-262 filed this population at <b>seven</b>. Re-derived (F2-221) with the concatenation
     * walk above it is <b>eleven regexes in ten files</b>, and over the whole guard corpus all four
     * spellings return the <b>identical 131 pairs</b> — so, exactly as with the role question, every
     * divergence between them was latent and what made them harmless was that they happened to agree
     * (F2-177).
     */
    @Test
    @DisplayName("no test source hand-rolls a permission-pair extractor")
    void thereIsExactlyOnePermissionPairExtractorInTheTestTree() {
        Map<String, List<String>> offenders = new TreeMap<>();
        int scanned = 0;

        for (Path p : testSources()) {
            String name = p.getFileName().toString();
            if (MAY_DECLARE_A_GUARD_REGEX.contains(name)) {
                continue;
            }
            scanned++;
            List<String> hits = permissionPairRegexesIn(COMMENTS.matcher(read(p)).replaceAll(" "));
            if (!hits.isEmpty()) {
                offenders.put(name, hits);
            }
        }

        assertThat(scanned).as("the test-source walk resolved nothing — TEST_ROOT is wrong")
            .isGreaterThanOrEqualTo(400);

        assertThat(offenders).as("""
            %s declares its own permission-pair regex. GuardExpressions#permissionPairsIn answers \
            this, and #permissionPairOfConstant answers it for a named guard constant. The four \
            spellings this replaced diverged on whitespace tolerance and token shape and agreed on \
            the answer, which is not a property anybody was maintaining.""".formatted(offenders))
            .isEmpty();
    }

    /**
     * A composed guard constant resolves to its pair instead of refusing to parse.
     *
     * <p>⚠⚠ <b>This is the leg F2-263 exists for.</b> {@code ObLifecycleTest.permissionIn} and its
     * {@code sharia} twin required the constant's initialiser to <em>be</em> a bare
     * {@code "hasPermission('X','Y')"} literal, so a guard that gained a second leg failed the build
     * saying <i>the constant must be a hasPermission string</i> — a gate refusing correct code
     * (F2-214), blaming the guard. <b>12 of the platform's 94 permission-bearing guard constants are
     * already that composed shape</b>, every one of them
     * {@code "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')"}.
     *
     * <p>And note which {@code hasRole} that is: the <em>singular</em> form, which F2-262 measured as
     * invisible to a {@code hasAnyRole\(}-only spelling at 67 sites. The composed shape carries both
     * of this class's questions at once.
     */
    @Test
    @DisplayName("a composed guard constant still resolves — the shape 12 NDI controllers already use")
    void aComposedGuardConstantResolvesRatherThanRefusingToParse() {
        String composed =
            "class X { private static final String APPROVE = "
            + "\"hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')\"; }";

        assertThat(GuardExpressions.permissionPairOfConstant(composed, "APPROVE"))
            .as("the pre-F2-263 pattern required the string to END after the pair, so this shape "
                + "failed the build with 'must be a hasPermission string'")
            .isEqualTo("NDI:APPROVE");

        // Both questions, one constant: the role leg is still visible, and it is the singular form.
        assertThat(GuardExpressions.rolesRecitedByConstant(composed, "APPROVE"))
            .containsExactly("PLATFORM_ADMIN");

        // An ownership leg is the other documented direction of travel (F2-159/F2-161).
        String owned = "class X { static final String W = "
            + "\"hasAnyRole('BCM_MANAGER') or hasPermission(#id,'EXERCISE_RUN','OWNER') \"\n"
            + "    + \"or hasPermission('BCM','WRITE')\"; }";
        assertThat(GuardExpressions.permissionPairsInConstant(owned, "W"))
            .as("the three-argument ownership relation is not a permission pair, and the two-argument "
                + "leg beside it is")
            .containsExactly("BCM:WRITE");

        // ⚠ The anti-vacuity half, added because the mutation harness proved it untested: removing
        // the size check left `pairs.iterator().next()` throwing NoSuchElementException, and no test
        // reached that branch — on a clean tree every constant asked about cites exactly one pair.
        // This is the property the whole consolidation rests on, so it cannot be the untested one.
        String noPair = "class X { static final String G = \"hasAnyRole('PLATFORM_ADMIN')\"; }";
        assertThatThrownBy(() -> GuardExpressions.permissionPairOfConstant(noPair, "G"))
            .as("a constant that has been converted AWAY from a permission must report drift, "
                + "legibly — not throw out of an iterator")
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("exactly one MODULE:ACTION pair")
            .hasMessageContaining("cites 0");

        String twoPairs = "class X { static final String G = "
            + "\"hasPermission('A','READ') or hasPermission('B','WRITE')\"; }";
        assertThatThrownBy(() -> GuardExpressions.permissionPairOfConstant(twoPairs, "G"))
            .as("a caller asking for THE pair of a constant citing two must be told, not handed "
                + "whichever came first")
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("cites 2");
    }

    /**
     * The three-argument relations are excluded, and the reason each is excluded differs.
     *
     * <p>F2-255 recorded that matching either of them publishes a false ghost on correct code:
     * {@code ML_MODEL:OWNER} or {@code AUDIT_COMMITTEE:COMMITTEE} would be reported as a catalogued
     * permission pair the catalogue does not define. {@code #externalId} is refused at the opening
     * quote; the committee form's first argument <b>is</b> quoted, so only the closing paren tells
     * them apart — which is why this leg asserts both rather than one exemplar.
     */
    @Test
    @DisplayName("neither three-argument relation is read as a permission pair")
    void theThreeArgumentRelationsAreNotPermissionPairs() {
        assertThat(GuardExpressions.permissionPairsIn(
            "hasPermission(#externalId, 'ML_MODEL', 'OWNER')")).isEmpty();
        assertThat(GuardExpressions.permissionPairsIn(
            "hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')")).isEmpty();
        assertThat(GuardExpressions.permissionPairsIn(
            "hasPermission('AUDIT_COMMITTEE', 'COMMITTEE', 'SEAT')")).isEmpty();

        // …and the two-argument reading is not lost when a relation sits in the same expression.
        assertThat(GuardExpressions.permissionPairsIn(
            "hasPermission('AI','WRITE') or hasPermission(#externalId,'ML_MODEL','OWNER')"))
            .containsExactly("AI:WRITE");
    }

    /**
     * Every axis on which the four replaced spellings were narrower than the widest correct reading.
     *
     * <p>Each divergence is latent today — 0 sites write {@code hasPermission (}, 0 tokens carry a
     * digit, and no guard splits a call across a concatenation — which is the same position the role
     * question was in, and the same argument: what made four regexes harmless was that they agreed.
     */
    @Test
    @DisplayName("the widest correct reading: whitespace anywhere, tokens of any shape")
    void theSharedExtractorTakesTheWidestCorrectReading() {
        // The five-copy spelling had no tolerance after the opening paren or before the closing one.
        assertThat(GuardExpressions.permissionPairsIn("hasPermission( 'RISK' , 'READ' )"))
            .containsExactly("RISK:READ");
        // Nor before the paren itself.
        assertThat(GuardExpressions.permissionPairsIn("hasPermission ('RISK','READ')"))
            .containsExactly("RISK:READ");
        // [A-Z_]+ cannot see a digit; the widest reading can, and judging the shape is a separate
        // question that PermissionCatalogueUsageTest owns.
        assertThat(GuardExpressions.permissionPairsIn("hasPermission('ISO27001','READ')"))
            .containsExactly("ISO27001:READ");
        // Several legs, in written order, deduplicated.
        assertThat(GuardExpressions.permissionPairsIn(
            "hasPermission('A','READ') or hasPermission('B','WRITE') or hasPermission('A','READ')"))
            .containsExactly("A:READ", "B:WRITE");
        assertThat(GuardExpressions.permissionPairsIn(null)).isEmpty();
        assertThat(GuardExpressions.permissionPairsIn("hasAnyRole('X')")).isEmpty();
    }

    /**
     * The pair classifier fires on all four replaced spellings and on none of the five neighbours.
     *
     * <p>Pinned in <b>both</b> directions: a narrowing that is too wide silently re-tiers the tree,
     * and one that is too narrow silently shrinks the population — and a smaller population reads
     * exactly like a complete sweep (F2-198). The must-not-fire half is the one nobody writes first
     * (F2-199), and it is where the three-argument committee extractor sits.
     */
    @Test
    @DisplayName("the pair classifier: four must fire, five must not")
    void thePairClassifierIsPinnedInBothDirections() {
        Map<String, Boolean> fires = new TreeMap<>();
        // ── the four replaced spellings ──
        fires.put("A tolerant", isPermissionPairRegex(
            "hasPermission\\(\\s*'([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\s*\\)"));
        fires.put("B tight", isPermissionPairRegex(
            "hasPermission\\('([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\)"));
        fires.put("C constant-initialiser", isPermissionPairRegex(
            "\\s*=\\s*\\n?\\s*\"hasPermission\\('([A-Z_]+)'\\s*,\\s*'([A-Z_]+)'\\)\""));
        fires.put("D loose", isPermissionPairRegex(
            "hasPermission\\(\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\)"));
        // ── the five legitimate neighbours, each asking a narrower or different question ──
        fires.put("bare presence (MeasuredBaseline)", isPermissionPairRegex("hasPermission\\("));
        fires.put("prefix test (ExerciseRunOwnership)", isPermissionPairRegex(
            "hasPermission\\(\\s*'"));
        fires.put("fixed action (ExportAuthorizationRatchet)", isPermissionPairRegex(
            "hasPermission\\(\\s*'[A-Z_]+'\\s*,\\s*'EXPORT'\\s*\\)"));
        fires.put("fixed module (ChangeDocumentationFreshness)", isPermissionPairRegex(
            "hasPermission\\('CHANGE'\\s*,\\s*'([A-Z_]+)'\\)"));
        fires.put("three-arg seat (CommitteeSeatGuard)", isPermissionPairRegex(
            "hasPermission\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]COMMITTEE['\"]"
            + "\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)"));
        // ⚠ The class-stripping clause, asserted directly because the mutation harness showed it was
        // never exercised: no spelling in the tree happens to put a comma inside a character class,
        // so deleting the strip changed no answer here — while `[\w<>,.\[\]]` does appear in this
        // tree, and a pair extractor written with one would silently stop being counted.
        fires.put("pair spelling with a comma inside a character class", isPermissionPairRegex(
            "hasPermission\\(\\s*'([A-Z_,]+)'\\s*,\\s*'([A-Z_]+)'\\s*\\)"));

        assertThat(fires).as("""
            the pair classifier disagreed with its own specification: %s. The four spellings above \
            were the measured population; the five below are live regexes in this tree that ask a \
            different question, and the committee one has two capture groups — only the comma count \
            separates it.""".formatted(fires)).containsOnly(
                Map.entry("A tolerant", true),
                Map.entry("B tight", true),
                Map.entry("C constant-initialiser", true),
                Map.entry("D loose", true),
                Map.entry("bare presence (MeasuredBaseline)", false),
                Map.entry("prefix test (ExerciseRunOwnership)", false),
                Map.entry("fixed action (ExportAuthorizationRatchet)", false),
                Map.entry("fixed module (ChangeDocumentationFreshness)", false),
                Map.entry("three-arg seat (CommitteeSeatGuard)", false),
                Map.entry("pair spelling with a comma inside a character class", true));
    }

    /**
     * The ten former pair copies now call the helper — named, not counted (F2-241).
     *
     * <p>Two of them are the reason this slice happened at all, and they are the two F2-262 could not
     * see: {@code ObLifecycleTest} and {@code ShariaLifecycleTest} had <b>already replaced</b> their
     * role extractor with a permission one when their modules converted, so they were never members
     * of the role-name population. F2-262 read {@code ObLifecycleTest}'s javadoc, quoted its rule as
     * its headline lesson, and did not convert the method the rule is written on.
     */
    @Test
    @DisplayName("all ten former pair copies now call the shared helper")
    void everyFormerPairCopyCallsTheHelper() {
        Map<String, Path> expected = new TreeMap<>();
        expected.put("MeasuredBaselineFreshnessTest", TEST_ROOT.resolve(
            Path.of("config", "MeasuredBaselineFreshnessTest.java")));
        expected.put("SanctionsScreeningControllerGuardTest", TEST_ROOT.resolve(
            Path.of("module", "aml", "SanctionsScreeningControllerGuardTest.java")));
        expected.put("CptyControllerGuardTest", TEST_ROOT.resolve(
            Path.of("module", "cpty", "CptyControllerGuardTest.java")));
        expected.put("EthicsGuardConversionTest", TEST_ROOT.resolve(
            Path.of("module", "ethics", "EthicsGuardConversionTest.java")));
        expected.put("IntakePromotionContractTest", TEST_ROOT.resolve(
            Path.of("module", "intake", "spi", "IntakePromotionContractTest.java")));
        expected.put("ObControllerGuardTest", TEST_ROOT.resolve(
            Path.of("module", "openbanking", "ObControllerGuardTest.java")));
        expected.put("ShariaControllerGuardTest", TEST_ROOT.resolve(
            Path.of("module", "sharia", "ShariaControllerGuardTest.java")));
        expected.put("PermissionCatalogueUsageTest", TEST_ROOT.resolve(
            Path.of("security", "PermissionCatalogueUsageTest.java")));
        // The two that carried the fail-on-correct-code shape.
        expected.put("ObLifecycleTest", TEST_ROOT.resolve(
            Path.of("module", "openbanking", "service", "ObLifecycleTest.java")));
        expected.put("ShariaLifecycleTest", TEST_ROOT.resolve(
            Path.of("module", "sharia", "service", "ShariaLifecycleTest.java")));

        Map<String, String> notCalling = new TreeMap<>();
        expected.forEach((name, path) -> {
            if (!Files.exists(path)) {
                notCalling.put(name, "source no longer at " + path);
                return;
            }
            String code = COMMENTS.matcher(read(path)).replaceAll(" ");
            // The call SHAPE, never the token: a bare contains() is satisfied by the import alone,
            // which is how F2-194's and F2-205's gates were defeated.
            if (!Pattern.compile("GuardExpressions\\s*\\.\\s*\\w+\\s*\\(").matcher(code).find()) {
                notCalling.put(name, "imports or mentions GuardExpressions but never calls it");
            }
        });

        assertThat(notCalling).as("""
            %s was one of the ten hand-rolled pair copies and no longer calls the shared helper. \
            Either it grew its own extractor back, or it stopped asking the question — and the \
            second is fine only if this list is updated to say so.""".formatted(notCalling))
            .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    /** Every {@code Pattern.compile(...)} in this source whose regex describes a SpEL role call. */
    private static List<String> roleCallRegexesIn(String source) {
        return compiledRegexesIn(source).stream()
            .filter(r -> ROLE_CALL_REGEX.matcher(r).find())
            .toList();
    }

    /**
     * Every {@code Pattern.compile(...)} in this source whose regex extracts a
     * {@code (MODULE, ACTION)} pair — see {@link #PERMISSION_PAIR_REGEX} for the two clauses.
     *
     * <p>F2-263 shares {@link #compiledRegexesIn} with the role question rather than writing a
     * second walker. That is not tidiness: the first draft of the role walker read only the FIRST
     * string literal after the paren and so could not see mechanism 3 at all, and a fresh copy for
     * the pair question would have had a fresh chance to repeat it — in the gate built to close it.
     */
    private static List<String> permissionPairRegexesIn(String source) {
        return compiledRegexesIn(source).stream()
            .filter(GuardExpressionsContractTest::isPermissionPairRegex)
            .toList();
    }

    /**
     * Two capture groups and exactly one separator comma.
     *
     * <p>⚠ <b>An ESCAPED paren is not a capture group, and the first draft of this method counted it
     * — inverting the classifier completely.</b> Every one of the four real spellings opens with
     * {@code hasPermission\\(}, so counting bare {@code (} scored them at <b>3</b> and the only
     * neighbour it admitted was {@code ChangeDocumentationFreshnessTest}'s one-group pattern, at 2.
     * The gate reported the four members as clean and the one non-member as an offender. 🔑 And my own
     * reconnaissance scan had <em>printed</em> {@code groups=3} beside each spelling; I read past it
     * because the number was not the answer I was looking for. {@code (?<!\\\\)} is the fix.
     *
     * <p>Commas inside a character class are not separators — {@code [\\w<>,.\\[\\]]} appears in this
     * tree — so classes are removed before counting. That clause is the entire three-argument
     * exclusion: {@code CommitteeSeatGuardTest} has two capture groups too.
     */
    private static boolean isPermissionPairRegex(String regex) {
        if (!PERMISSION_PAIR_REGEX.matcher(regex).find()) {
            return false;
        }
        long groups = Pattern.compile("(?<!\\\\)\\((?!\\?)").matcher(regex).results().count();
        String outsideClasses = regex.replaceAll("\\[[^\\]]*\\]", "");
        long commas = outsideClasses.chars().filter(ch -> ch == ',').count();
        return groups == 2 && commas == 1;
    }

    /**
     * Every regex a {@code Pattern.compile(...)} statement builds, with all of its concatenated
     * literals joined.
     *
     * <p>⚠⚠ <b>Its first draft read only the FIRST string literal after the paren, and the gate's own
     * positive-control leg caught that on its first run: it could not see mechanism 3 at all.</b>
     * {@code Pattern.compile("String " + constant + "\\s*=\\s*\"hasAnyRole\\(…")} puts {@code Role}
     * in the <em>third</em> concatenated literal, so a detector anchored on the first one reports the
     * two copies that carried it as absent. 🔑 <b>A concatenated regex defeats a detector anchored on
     * the first literal, and it UNDER-reports</b> — the same direction, and the same shape, as the
     * extractors this class exists to replace.
     *
     * <p>The window is the enclosing statement (to the next {@code ;}), not a paren-balanced walk:
     * a Java regex literal contains real {@code (} and {@code )} characters, so counting parens over
     * raw source miscounts. Overshooting a statement over-reports, which fails loudly — the safe
     * direction to be wrong in (F2-205).
     */
    private static List<String> compiledRegexesIn(String source) {
        List<String> out = new java.util.ArrayList<>();
        Matcher call = COMPILE_CALL.matcher(source);
        while (call.find()) {
            int end = source.indexOf(';', call.end());
            String stmt = source.substring(call.end(), end < 0 ? source.length() : end);
            StringBuilder regex = new StringBuilder();
            Matcher lit = JAVA_LITERAL.matcher(stmt);
            while (lit.find()) {
                regex.append(lit.group(1));
            }
            out.add(regex.toString().trim());
        }
        return out;
    }

    private static List<Path> testSources() {
        try (Stream<Path> s = Files.walk(TEST_ROOT)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + TEST_ROOT.toAbsolutePath(), e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p, e);
        }
    }
}
