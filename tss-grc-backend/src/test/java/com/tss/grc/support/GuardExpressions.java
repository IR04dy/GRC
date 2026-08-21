package com.tss.grc.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F2-262 — the one answer to <i>"which ROLE NAMES does this guard expression recite?"</i>
 *
 * <h2>Why this class exists</h2>
 *
 * <p>Eight test classes asked that question and each answered it with its own regex. Five slices
 * have now been bitten by one of those copies, <b>one per converting slice</b>, because a
 * quoted-token scan over a guard expression has no notion of which function a token is an argument
 * to — so {@code hasPermission('BCM','DELETE')} yields two "role names" called {@code BCM} and
 * {@code DELETE}, and a ghost-role detector built on one flags <em>every</em> role-to-permission
 * conversion, the change F2-05 exists to make, as a pair of ghosts. That is a gate failing on
 * correct code, which is the one failure a gate must never have (F2-214), and it trains the next
 * reader to edit the assertion rather than the code.
 *
 * <p>The history, because it is the argument for this class rather than a footnote to it:
 *
 * <ul>
 *   <li>F2-145(e) converted {@code sharia} and {@code openbanking}. Both modules' extractors broke;
 *       both were repointed at the permission form and the role question stopped being asked.</li>
 *   <li>F2-254 repaired {@code BiaApprovalChainGuardTest}.</li>
 *   <li>F2-259 repaired {@code AuditCommitteeGuardTest.namesIn}.</li>
 *   <li>F2-260 repaired {@code RrpLifecycleTest.rolesIn} and filed the extraction of this class,
 *       measuring "5 hand-rolled copies". Re-derived (F2-221) the population is <b>8</b>, in
 *       <b>three</b> mechanisms and <b>six</b> spellings, and three still carried a defect.</li>
 * </ul>
 *
 * <p>🔑 <b>The rule this class implements was already written down in the tree.</b>
 * {@code ObLifecycleTest}'s javadoc, explaining why its own conversion produced a legible failure,
 * says: <i>"a regex that matches nothing satisfies every downstream assertion forever, so the
 * anti-vacuity check belongs in the <b>helper</b>, where every caller inherits it."</i> Correct, and
 * for four more slices there was no helper for it to belong to.
 *
 * <h2>The six spellings, and what each could not see</h2>
 *
 * <p>Every divergence below was measured against the real corpus of <b>1,721 guard expressions</b>
 * in {@code src/main}. All are latent <em>today</em>; reachability is a fact about today, not a
 * property of a control (F2-182), and what made them harmless was six independent regexes happening
 * to agree — which nobody was maintaining (F2-177).
 *
 * <ol>
 *   <li>{@code has(?:AnyRole|Role)\(} — no whitespace tolerance. <b>0</b> sites write
 *       {@code hasAnyRole (}, so latent.</li>
 *   <li>{@code hasAnyRole\(} — cannot see the singular {@code hasRole(} at all. <b>67</b> sites in
 *       the tree use it; <b>0</b> in the subject of the copy that carried this spelling, so latent
 *       there and live the moment that copy is pointed anywhere else.</li>
 *   <li>Inner token {@code '([A-Z_]+)'} — cannot see a role name carrying a digit or a lower-case
 *       letter. All <b>38</b> recited names are {@code [A-Z_]+}, so latent.</li>
 *   <li>No {@code hasAuthority} handling — a role said longhand as {@code ROLE_X} is invisible.
 *       <b>2</b> authority sites exist and <b>0</b> name a {@code ROLE_*}, so latent; the two are
 *       permissions said longhand, which is a different finding and has its own bucket here.</li>
 *   <li>No scoping at all — miscounts the arguments of every {@code hasPermission(} in the same
 *       expression as role names. <b>1,392</b> guards contain one.</li>
 *   <li>No comment stripping — reads a javadoc quoting a <em>retired</em> guard as if it were the
 *       live one (F2-193's epitaph-as-corpse). <b>0</b> of the four source-text readers stripped;
 *       one stripped inline at a single call site, which is the divergence rather than the fix.</li>
 * </ol>
 *
 * <p>This class takes the <b>widest correct</b> reading of every one of those axes, so consolidating
 * onto it can only ever <em>add</em> a name, never drop one. That is why the verification for the
 * consolidation is that {@code RoleGuardConversionRatchetTest}'s published census figures do not
 * move: a helper that changed them would be changing findings, not sharing code.
 *
 * <h2>What is deliberately NOT here</h2>
 *
 * <p>A {@code hasPermission('MODULE','ACTION')} <b>pair</b> extractor. Seven test classes hand-roll
 * one of those and it answers a <em>different</em> question; consolidating it is the same argument
 * and a separate slice, filed. Shipping an unused method here would be an inert construct that
 * answers the question a reader was about to ask (F2-184), and an unused helper should not ship
 * (F2-201).
 */
public final class GuardExpressions {

    private GuardExpressions() {
    }

    /**
     * A {@code hasAnyRole(...)} / {@code hasRole(...)} call's argument list.
     *
     * <p>{@code has(?:Any)?Role} rather than {@code has(?:AnyRole|Role)} so the singular form is
     * never missed, and {@code \s*} before the paren because SpEL permits it and a guard that a
     * formatter has touched must not silently drop out of a census.
     */
    private static final Pattern ROLE_CALL = Pattern.compile("has(?:Any)?Role\\s*\\(([^)]*)\\)");

    /**
     * A {@code hasAnyAuthority(...)} / {@code hasAuthority(...)} call's argument list.
     *
     * <p>{@code hasAuthority} splits by prefix: {@code ROLE_*} is a role said longhand and belongs
     * in {@link #rolesRecitedBy}; anything else is a <em>permission</em> said longhand, which
     * bypasses {@code ModulePermissionEvaluator} entirely and is its own finding — see
     * {@link #rawAuthoritiesIn}. Same call, two different answers.
     */
    private static final Pattern AUTHORITY_CALL =
        Pattern.compile("has(?:Any)?Authority\\s*\\(([^)]*)\\)");

    /**
     * A single-quoted token inside an argument list.
     *
     * <p>{@code [^']*} and not {@code [A-Z_]+}: narrowing this to upper snake case would make a role
     * name carrying a digit invisible, and an extractor that cannot see a name reports a
     * <em>smaller</em> population, which reads exactly like a clean tree (F2-198).
     */
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    /**
     * A two-argument {@code hasPermission('MODULE','ACTION')} call.
     *
     * <p>The trailing {@code \)} is the whole of the three-argument exclusion, and it is the only
     * thing that separates this from a guard about a <em>relation</em>. This platform has two
     * three-argument forms and each is refused for its own reason:
     *
     * <ul>
     *   <li>{@code hasPermission(#id,'EXERCISE_RUN','OWNER')} — entity ownership (F2-159/F2-161).
     *       Refused at {@code \(\s*'} because the first argument is not quoted.</li>
     *   <li>{@code hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')} — a committee seat
     *       (F2-177). Its first argument <b>is</b> quoted, so only the closing paren tells them
     *       apart; matching it would publish {@code AUDIT_COMMITTEE:COMMITTEE} as a catalogued
     *       permission pair, which F2-255 recorded as a false ghost on correct code.</li>
     * </ul>
     *
     * <p>{@code [^']*} and not {@code [A-Z_]+}, for the reason on {@link #QUOTED}: an extractor
     * that cannot see a token reports a <em>smaller</em> population, which reads exactly like a
     * clean tree. Judging whether a token is well-formed is a separate question, and
     * {@code PermissionCatalogueUsageTest} owns it — a narrow extractor answers it by silently
     * dropping the evidence.
     */
    private static final Pattern PERMISSION_PAIR =
        Pattern.compile("hasPermission\\s*\\(\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\)");

    /** The granted-authority prefix Spring Security gives a role. */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Java line and block comments, in <b>one</b> left-to-right alternation.
     *
     * <p>⚠ Written as two sequential passes this deletes live code: at a {@code //} comment whose
     * prose contains a literal {@code /*}, the block pattern runs from there to the next closing
     * delimiter. One alternation cannot do that, because at a {@code //} an inner {@code /*} can
     * never open a block and at a {@code /*} a URL cannot truncate it. The {@code (?<!:)} guard
     * keeps {@code https://} out of it. (F2-198, corrected by F2-211: within one alternation the
     * branch order is a no-op — the defect is the second pass, not the ordering.)
     */
    private static final Pattern COMMENTS = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  The question
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The role names a guard expression recites, from role-call argument lists only.
     *
     * <p>A converted guard contributes <b>nothing</b>, and that is the whole point: a caller
     * asserting an audience then reports drift rather than accusing {@code MODULE} and
     * {@code ACTION} of being ghost roles.
     *
     * <p>Includes {@code hasAuthority('ROLE_X')} as {@code X} — a role said longhand is still a
     * role. Excludes {@code hasPermission(...)} in every arity, including the three-argument
     * entity-ownership form {@code hasPermission(#id,'ML_MODEL','OWNER')} and the committee-seat
     * form {@code hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')}, whose first argument is
     * quoted and which a looser pattern therefore reports as a ghost role.
     *
     * @param expr a SpEL guard expression; {@code null} yields an empty set
     */
    public static Set<String> rolesRecitedBy(String expr) {
        Set<String> out = new TreeSet<>();
        if (expr == null) {
            return out;
        }
        collect(ROLE_CALL.matcher(expr), out);
        for (String a : authorities(expr)) {
            if (a.startsWith(ROLE_PREFIX)) {
                out.add(a.substring(ROLE_PREFIX.length()));
            }
        }
        return out;
    }

    /**
     * The authorities a guard recites that are <b>not</b> roles — a permission spelled as its
     * granted-authority string, which gets no {@code PLATFORM_ADMIN} bypass and no evaluator.
     *
     * <p>Separate from {@link #rolesRecitedBy} because it is a separate finding: the same call
     * produces a role in one case and an evaluator bypass in the other, and reporting them as one
     * total would hide both.
     */
    public static Set<String> rawAuthoritiesIn(String expr) {
        Set<String> out = new TreeSet<>();
        for (String a : authorities(expr)) {
            if (!a.startsWith(ROLE_PREFIX)) {
                out.add(a);
            }
        }
        return out;
    }

    /**
     * Does this guard have a role leg at all?
     *
     * <p>Exists so a caller that only needs to <em>classify</em> a guard never has to declare its
     * own role-call regex — which is what {@code GuardExpressionsContractTest}'s hard zero requires,
     * and the reason that zero can have no exemptions: there is always a correct alternative here.
     */
    public static boolean hasRoleLeg(String expr) {
        return expr != null && ROLE_CALL.matcher(expr).find();
    }

    /** Does this guard consult {@code ModulePermissionEvaluator}, in any arity? */
    public static boolean hasPermissionLeg(String expr) {
        return expr != null && expr.contains("hasPermission(");
    }

    /**
     * The {@code MODULE:ACTION} pairs a guard expression cites, in the order written.
     *
     * <p>F2-262 shipped this class for the role question and deliberately left the pair question
     * out, because a helper method with no caller is an inert construct that answers the question a
     * reader was about to ask (F2-184, F2-201). It has eleven callers, in ten files and four
     * spellings, and F2-263 measured them: over the whole guard corpus all four return the
     * <b>identical 131 pairs</b>, and no guard in the tree splits a {@code hasPermission} call
     * across a Java string concatenation. So every divergence between them is latent — which is
     * F2-177's shape exactly: <b>what made nine independent regexes harmless was that they happened
     * to agree, and nobody was maintaining that.</b>
     *
     * <p>The four spellings and what each could not see, measured against 1,314 guard expressions
     * carrying a two-argument call:
     *
     * <ul>
     *   <li>{@code hasPermission\(\s*'([A-Z_]+)'…} — no tolerance for {@code hasPermission (}
     *       (0 sites write it) and blind to a token carrying a digit (0 tokens do).</li>
     *   <li>{@code hasPermission\('([A-Z_]+)'…\)} — <b>no whitespace tolerance after the opening
     *       paren or before the closing one.</b> Five copies. A formatter run is all it takes.</li>
     *   <li>{@code hasPermission\(\s*'([^']*)'…} — the widest, and the one this method takes.</li>
     *   <li>{@code CONST\s*=\s*\n?\s*"hasPermission\('…'\)"} — see
     *       {@link #permissionPairOfConstant}, which is where the sharp edge was.</li>
     * </ul>
     */
    public static Set<String> permissionPairsIn(String expr) {
        Set<String> out = new LinkedHashSet<>();
        if (expr == null) {
            return out;
        }
        Matcher m = PERMISSION_PAIR.matcher(expr);
        while (m.find()) {
            out.add(m.group(1) + ":" + m.group(2));
        }
        return out;
    }

    /**
     * A <em>pure</em> role guard: it recites a role and consults no permission.
     *
     * <p>⚠ Note this is presence-based on the permission side, exactly as
     * {@code RoleGuardConversionRatchetTest} measures it — and F2-253 recorded that the census
     * prints that bucket as <i>"role leg ANDed onto a permission"</i> while <b>96 of 99</b> are
     * in fact ORs. That gap is a filed finding about the census, not about this predicate, and it
     * is named here so a reader of this method does not have to rediscover it.
     */
    public static boolean isPureRoleGuard(String expr) {
        return hasRoleLeg(expr) && !hasPermissionLeg(expr);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    //  Reading a guard out of source text
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The initialiser of a {@code String} guard constant, with comments already stripped.
     *
     * <p>Two properties every caller inherits, and which the hand-rolled copies had between zero
     * and one of:
     *
     * <ul>
     *   <li><b>Anti-vacuity.</b> An absent constant throws rather than returning empty. A pattern
     *       that matches nothing satisfies every downstream assertion forever.</li>
     *   <li><b>Tolerance.</b> It does <em>not</em> require the initialiser to be a
     *       {@code hasAnyRole} literal. Two copies did, so the conversion of their own module would
     *       have failed the build with <i>"guard constant X must be a hasAnyRole string"</i> — a
     *       gate refusing the change F2-05 exists to make.</li>
     * </ul>
     *
     * <p>The name is matched on a word boundary, so asking for {@code READ} does not match
     * {@code READ_GUARD} — one copy's subject declares its constants as bare {@code READ} /
     * {@code WRITE} / {@code APPROVE} and its sibling's as {@code READ_GUARD}, so both spellings are
     * live in this tree.
     *
     * <p>⚠ <b>The TRAILING {@code \b} is redundant and is kept as documentation of intent, not as a
     * guard.</b> The mutation harness proved it: removing it changes no answer, because the character
     * after the name in {@code READ_GUARD} is {@code _}, which is neither {@code \s} nor {@code =},
     * so <b>{@code \s*=} is what actually rejects the longer name</b>. 🔑 A reader who deletes
     * {@code \s*=} believing the word boundary protects them would break
     * {@code constantNamesAreMatchedOnAWordBoundary} — the assertion is named after the half that is
     * decorative. (The LEADING {@code \b} is load-bearing: it stops a match inside {@code MyString}.)
     *
     * @throws AssertionError if the constant is not declared in this source
     */
    public static String guardConstant(String javaSource, String constantName) {
        String code = stripComments(javaSource);
        Matcher m = Pattern.compile(
            "\\bString\\s+" + Pattern.quote(constantName) + "\\b\\s*=\\s*([^;]*);",
            Pattern.DOTALL).matcher(code);
        if (!m.find()) {
            throw new AssertionError(
                "guard constant " + constantName + " is not declared as a String in this source. "
                + "A regex that matches nothing passes forever, so this is a hard failure rather "
                + "than an empty result: either the constant was renamed, or this test is now "
                + "asserting nothing at all.");
        }
        return m.group(1);
    }

    /**
     * The role names recited by a named guard constant in a Java source — the composition of
     * {@link #guardConstant} and {@link #rolesRecitedBy}, which is what every source-text caller
     * wanted and three of them spelled differently.
     */
    public static Set<String> rolesRecitedByConstant(String javaSource, String constantName) {
        return rolesRecitedBy(guardConstant(javaSource, constantName));
    }

    /** The {@code MODULE:ACTION} pairs a named guard constant cites. */
    public static Set<String> permissionPairsInConstant(String javaSource, String constantName) {
        return permissionPairsIn(guardConstant(javaSource, constantName));
    }

    /**
     * The one {@code MODULE:ACTION} a guard constant resolves to.
     *
     * <p>⚠⚠ <b>This is the method F2-263 exists for, and the file that coined the rule it breaks is
     * one of the two that had it.</b> {@code ObLifecycleTest.permissionIn} and its {@code sharia}
     * twin compiled {@code CONST\s*=\s*\n?\s*"hasPermission\('([A-Z_]+)'\s*,\s*'([A-Z_]+)'\)"} and
     * asserted {@code m.find()} — so the constant's initialiser had to <b>be</b> a bare
     * {@code hasPermission} literal, the closing {@code \)"} requiring the string to end right after
     * the pair. Give either guard a second leg and {@code find()} answers false and the build fails
     * with <i>"guard constant X must be a hasPermission string"</i>: <b>a gate refusing correct
     * code</b> (F2-214), blaming the guard rather than the test.
     *
     * <p>That is F2-262's mechanism 3, mirrored. There the pattern required a {@code hasAnyRole}
     * literal and broke when a module <em>converted</em>; here it requires a bare permission literal
     * and breaks when a guard <em>gains a leg</em> — and gaining a leg is the documented direction of
     * travel for this platform's guards (F2-159/F2-161 ownership, F2-177 seats, admin overrides).
     * Not hypothetical: <b>12 of the 94 guard constants carrying a permission are already composed</b>
     * — every one of them {@code "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')"},
     * written with the <em>singular</em> {@code hasRole} that F2-262 recorded as invisible to a
     * {@code hasAnyRole\(}-only spelling. The shape exists; it is simply not in those two modules yet.
     *
     * <p>🔑 And the reason the two copies survived F2-262: they had <b>already replaced</b> their role
     * extractor with a permission one when their modules converted, so they were not members of the
     * role-name population at all. F2-262 read this very javadoc, quoted its rule as its headline
     * lesson — <i>"a regex that matches nothing satisfies every downstream assertion forever, so the
     * anti-vacuity check belongs in the helper"</i> — and did not convert the method the rule is
     * written on, because it asks a different question. <b>The anti-vacuity check they built to be
     * safe is the same {@code find()} that makes them fail on correct code.</b>
     *
     * <p>Tolerant, therefore: it extracts the pair from whatever the constant holds and refuses only
     * when there is no single pair to name — so a composed guard reports its pair, a converted-away
     * guard reports drift, and neither refuses to parse.
     *
     * @throws AssertionError if the constant is absent, cites no pair, or cites more than one
     */
    public static String permissionPairOfConstant(String javaSource, String constantName) {
        Set<String> pairs = permissionPairsInConstant(javaSource, constantName);
        if (pairs.size() != 1) {
            throw new AssertionError(
                "guard constant " + constantName + " must cite exactly one MODULE:ACTION pair; it "
                + "cites " + pairs.size() + " " + pairs + ". A composed guard (a permission ORed "
                + "with a role or an ownership leg) is fine and still resolves here — this failure "
                + "means the constant names no permission at all, or names several and the caller "
                + "asked for 'the' one.");
        }
        return pairs.iterator().next();
    }

    /**
     * Java comments removed. Public because a caller asserting anything about guard <em>source</em>
     * needs it for the same reason this class does, and a second inline copy is how the divergence
     * above began.
     */
    public static String stripComments(String javaSource) {
        return javaSource == null ? "" : COMMENTS.matcher(javaSource).replaceAll(" ");
    }

    /** A source file under {@code src/main/java/com/tss/grc/...}, read as UTF-8. */
    public static String mainSource(String... pathUnderGrc) {
        Path p = Paths.get("src", "main", "java", "com", "tss", "grc");
        for (String seg : pathUnderGrc) {
            p = p.resolve(seg);
        }
        try {
            return Files.readString(p.toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    private static Set<String> authorities(String expr) {
        Set<String> out = new LinkedHashSet<>();
        if (expr != null) {
            collect(AUTHORITY_CALL.matcher(expr), out);
        }
        return out;
    }

    private static void collect(Matcher call, Set<String> into) {
        while (call.find()) {
            Matcher q = QUOTED.matcher(call.group(1));
            while (q.find()) {
                into.add(q.group(1));
            }
        }
    }
}
