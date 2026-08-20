package com.tss.grc.module.change_mgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-251 — every self-review refusal must be on the report built to say whether such a refusal can
 * fire, and the population must come from the refusals rather than from a list somebody wrote.
 *
 * <h2>The finding this exists for</h2>
 * {@code GET /api/v1/changes/sod-coverage} was built by CE-15 to answer <i>"which of these gates can
 * actually decide anything?"</i> — the question F2-112 needed asked, after a shipped refusal turned
 * out never to have been able to fire. It published four gates. This module enforced <b>six</b>
 * self-review refusals, and the two that were absent were <b>the two that could not fire</b>: the CAB
 * vote (CM-C2) and the retrospective ratification (ADR-017 D-3), the two whose comparison was
 * hand-written instead of routed through {@link ChangeSoD}.
 *
 * <p>🔑 <b>The instrument was real; its POPULATION was {@code ChangeSoD.GATES}.</b> Both missing
 * gates were enforced, unit-tested and shipped, so nothing looked unfinished — and the one artefact
 * that would have surfaced them had them outside its scope by construction. This codebase has been
 * caught by that narrowing repeatedly (a filename, a three-entry allow-list, a module name, an
 * {@code extends} clause, a field-name list inside a fix, a claim shape in a gate); a gate list is
 * simply the next spelling of it.
 *
 * <h2>How the population is derived</h2>
 * A self-review refusal has a structural signature: a method that asks {@link ChangeSoD} whether the
 * acting principal is a named party and <b>throws</b> on the answer. {@link #REFUSALS} maps each such
 * method to the gate it is reported under, and the derivation is enforced in <b>both</b> directions —
 * a new refusal fails the build until it is declared and reported, and a declared entry that no
 * longer names a real refusal fails too. The map is bookkeeping the build checks, not a second
 * source of truth: it cannot grow a member the scan does not find.
 */
class ChangeSodGateCoverageContractTest {

    private static final Path SVC =
        Path.of("src/main/java/com/tss/grc/module/change_mgmt/service/ChangeManagementService.java");

    /**
     * The methods that refuse a self-review, and the gate each is reported under.
     *
     * <p>Not a list of gates — a list of <b>refusals</b>, keyed on the method that throws. That is
     * the direction that made the two missing gates visible: {@code recordApproval} and
     * {@code requireRatificationSeparation} were always here, and only their reporting was absent.
     */
    private static final Map<String, List<String>> REFUSALS = new LinkedHashMap<>(Map.of(
        "assertNotSelfWaiver", List.of("GATE_SELF_WAIVER"),
        "assertPirReviewerIsNotImplementer", List.of("GATE_PIR_NOT_IMPLEMENTER"),
        "assertNotSelfSatisfaction", List.of("GATE_CONDITION_NOT_OWNER"),
        "recordApproval", List.of("GATE_CAB_VOTE_NOT_REQUESTER"),
        "requireRatificationSeparation",
            List.of("GATE_RATIFIER_NOT_REQUESTER", "GATE_RATIFIER_NOT_APPROVER")));

    /**
     * Gates on the report whose refusal is enforced somewhere this scan cannot reach, with the reason
     * — never a bare label (F2-228), and verified rather than reasoned.
     *
     * <p>⚠ <b>This map had three entries on its first run and two of them were wrong.</b> I wrote
     * fluent reasons for {@code GATE_PIR_NOT_IMPLEMENTER} and {@code GATE_CONDITION_NOT_OWNER} saying
     * their refusals sit outside {@code selfReviewBars} — <b>true, and not the question the scan
     * asks</b>. The derivation is keyed on <em>throwing on a {@link ChangeSoD} verdict</em>, and both
     * of those methods do exactly that, so both were in the population all along and my exemptions
     * would have removed them from scrutiny. 🔑 <b>An exemption that answers a different question
     * from the one the scan asks reads exactly like a correct exemption</b> — roughly two in ten of a
     * new instrument's own claims fall on first run (F2-188, F2-192, F2-204, F2-211, F2-250), and
     * here it was two in three. The direction that caught it is the reverse leg, which nobody writes
     * first (F2-199).
     *
     * <p>⚙ And correcting it paid immediately: promoting {@code assertNotSelfSatisfaction} into the
     * population is what surfaced a <b>fourth</b> merged-party site, which no reading of this module
     * had found.
     */
    private static final Map<String, String> REPORTED_ELSEWHERE = new LinkedHashMap<>(Map.of(
        "GATE_COMMITTEE_VOTE_NOT_REQUESTER",
            "ENFORCED IN ANOTHER MODULE — CommitteeResolutionService.recordVote applies it over data "
          + "this module hands the engine at referral time, and its reach depends on the committee's "
          + "roster as much as on the change's captured people, so the count comes back through "
          + "CommitteePort.recusalCoverage rather than being computed here (F2-113 records why "
          + "computing it locally would have overstated it)."));

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Strip comments in ONE left-to-right alternation.
     *
     * <p>⚠ Two sequential passes corrupt the source: a {@code //} prose comment whose text contains a
     * literal {@code /*} makes the block pattern run to the next closing delimiter and swallow real
     * declarations, and the result is a SMALLER population — which reads exactly like a clean tree
     * (F2-198, corrected by F2-211: the defect is two passes, not the branch order). Comments here are
     * load-bearing in the other direction too: every prose paragraph in the service quotes the shapes
     * this class bans, so a comment-blind scan reads the epitaph as the corpse (F2-193).
     */
    private static String strip(String s) {
        return s.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", "");
    }

    /** The brace-balanced body of a named method, or empty when absent. */
    private static String body(String src, String signatureFragment) {
        int at = src.indexOf(signatureFragment);
        if (at < 0) return "";
        int open = src.indexOf('{', at);
        if (open < 0) return "";
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        return "";
    }

    /** Every method in the file whose body throws on a {@link ChangeSoD} verdict. */
    private static List<String> derivedRefusals(String stripped) {
        // A method declaration this codebase writes: modifiers, a return type, a name, a paren.
        Pattern decl = Pattern.compile(
            "(?m)^ {4}(?:public|private|protected)[^\\n(){}=;]{0,120}?\\b(\\w+)\\s*\\(");
        List<String> out = new ArrayList<>();
        Matcher m = decl.matcher(stripped);
        while (m.find()) {
            String name = m.group(1);
            String b = body(stripped.substring(m.start()), name + "(");
            if (b.isEmpty()) continue;
            if (b.contains("ChangeSoD.compareAny") || b.contains("ChangeSoD.compare(")) {
                boolean throwsOnIt = b.contains("throw ") && b.contains(".refuses()");
                if (throwsOnIt && !out.contains(name)) out.add(name);
            }
        }
        return out;
    }

    @Test
    @DisplayName("every self-review refusal is reported, and the population comes from the refusals")
    void everyRefusalIsOnTheCoverageReport() {
        String stripped = strip(read(SVC));
        List<String> found = derivedRefusals(stripped);

        System.out.println("F2-251 — derived self-review refusals: " + found);

        // Anti-vacuity, and the floor sits above the largest WRONG answer rather than above zero
        // (F2-215): a detector that stops matching reports an empty list, which is indistinguishable
        // from a module with no refusals. Pre-fix the honest answer was 1 of 3, so a floor of 2 is
        // what discriminates a rotted detector from the defect this slice closed.
        // Pre-fix the honest answer was 3 — recordApproval and requireRatificationSeparation had
        // hand-written copies and so were invisible to a detector keyed on ChangeSoD. A floor of 4 is
        // therefore what discriminates a rotted detector from the tree this slice fixed.
        assertThat(found)
            .as("a detector that stops resolving reports few refusals, and few reads exactly like "
              + "a module that routes them all through one predicate")
            .hasSizeGreaterThanOrEqualTo(4);

        assertThat(found)
            .as("every method that throws on a ChangeSoD verdict must be declared with the gate it "
              + "is reported under — a refusal nothing reports is the F2-251 defect exactly")
            .containsExactlyInAnyOrderElementsOf(REFUSALS.keySet());

        // And the reverse: a declared entry must still name a refusal the scan finds. This is the
        // direction nobody writes first (F2-199), and it is what stops a map outliving its subject.
        for (String method : REFUSALS.keySet()) {
            assertThat(found)
                .as("REFUSALS declares '%s', which the scan does not find as a refusal. A reason "
                  + "written for something not in the population reads exactly like a reason for "
                  + "something that is.", method)
                .contains(method);
        }
    }

    @Test
    @DisplayName("every declared gate is actually built into the coverage report")
    void everyDeclaredGateIsBuiltIntoTheReport() {
        String stripped = strip(read(SVC));
        String coverage = body(stripped, "public List<ChangeSoD.Coverage> sodCoverage(");
        assertThat(coverage).as("sodCoverage must be resolvable for this leg to mean anything")
            .isNotEmpty();

        for (List<String> gates : REFUSALS.values()) {
            for (String gate : gates) {
                assertThat(coverage)
                    .as("ChangeSoD.%s names a refusal this module enforces, so the report must "
                      + "publish a row for it — a gate enforced and unreported is precisely how the "
                      + "CAB vote and the ratification separation stayed invisible", gate)
                    .contains("ChangeSoD." + gate);
            }
        }

        // Every gate the constant declares is either derived here or carries a verified reason.
        TreeSet<String> declared = new TreeSet<>();
        REFUSALS.values().forEach(declared::addAll);
        for (String constant : List.of("GATE_SELF_WAIVER", "GATE_PIR_NOT_IMPLEMENTER",
                "GATE_CONDITION_NOT_OWNER", "GATE_COMMITTEE_VOTE_NOT_REQUESTER",
                "GATE_CAB_VOTE_NOT_REQUESTER", "GATE_RATIFIER_NOT_REQUESTER",
                "GATE_RATIFIER_NOT_APPROVER")) {
            boolean accounted = declared.contains(constant) || REPORTED_ELSEWHERE.containsKey(constant);
            assertThat(accounted)
                .as("ChangeSoD.%s is on the report and is neither derived from a refusal in this "
                  + "file nor explained — an unexplained row is a coverage claim nothing backs",
                    constant)
                .isTrue();
        }
        for (Map.Entry<String, String> e : REPORTED_ELSEWHERE.entrySet()) {
            assertThat(e.getValue())
                .as("a reason must name its evidence, not wear a label (F2-228)")
                .hasSizeGreaterThan(140);
            assertThat(declared)
                .as("%s carries a reason for being outside the derivation AND is derived from a "
                  + "refusal here — one of the two is wrong", e.getKey())
                .doesNotContain(e.getKey());
        }
    }

    @Test
    @DisplayName("no hand-written copy of the comparison against a change's people")
    void noHandWrittenCopyOfTheComparison() {
        String stripped = strip(read(SVC));

        // Scoped to the SHAPE of the claim (F2-192): not "these getters may not be read" — the
        // coverage counts, the export map and firstNonBlank all read them legitimately — but "no
        // equality comparison may be written against them", which is the pre-fix signature.
        Pattern comparison = Pattern.compile(
            "\\.equals(?:IgnoreCase)?\\s*\\([^;]{0,200}?"
          + "get(?:RequestedBy(?:Id|Name)|ChangeOwner(?:Id|Name)|CabDecisionBy(?:Id|Name))\\s*\\(");
        Matcher m = comparison.matcher(stripped);
        List<String> hits = new ArrayList<>();
        while (m.find()) hits.add(m.group());

        assertThat(hits)
            .as("a hand-written comparison against a change's people is a second copy of "
              + "ChangeSoD's rule, and the class's own javadoc says a second copy 'lies the first "
              + "time one copy learns something the others do not' — it did, twice, in the two "
              + "copies that were never converted. Build parties instead.")
            .isEmpty();

        // ...and the positive control, so a rotted regex cannot pass as a clean tree. These are the
        // literal pre-fix lines, and the detector must fire on each.
        for (String preFix : List.of(
                "boolean selfById = actorUserId != null\n"
              + "            && (actorUserId.equals(c.getRequestedById()) "
              + "|| actorUserId.equals(c.getChangeOwnerId()));",
                "boolean nameFallback = actorUserId == null && ratifier != null\n"
              + "            && (ratifier.equalsIgnoreCase(nullSafeStr(c.getCabDecisionByName()))",
                "&& (approver.equalsIgnoreCase(nullSafeStr(c.getRequestedByName()).trim())")) {
            assertThat(comparison.matcher(preFix).find())
                .as("the detector must fire on the literal pre-fix shape: %s", preFix)
                .isTrue();
        }
        // And must NOT fire on a legitimate read that is not a comparison.
        assertThat(comparison.matcher(
                "long n = all.stream().filter(c -> c.getRequestedById() != null).count();").find())
            .as("counting a captured id is not a comparison against the actor — a detector that "
              + "flags it would fail on correct code, the one failure a gate must never have")
            .isFalse();
    }

    /**
     * One party per bar, in <b>every</b> bar builder — and the population is DERIVED.
     *
     * <p>⚠ This leg's first draft iterated {@code List.of("selfReviewBars", "cabDecisionBars")}, and
     * the mutation harness proved it: re-merging {@code conditionOwnerBars} <b>SURVIVED</b> the whole
     * build. 🔑 <b>That is this slice's own finding committed inside the instrument written to answer
     * it</b> — a real rule over a population that is a list somebody wrote, and the list was two
     * names long because those were the two builders I had written by then. The third was surfaced by
     * this very class's derivation leg one test method above, and the per-bar leg still could not see
     * it. Assume a fourth builder will be added by somebody who does not read this comment.
     *
     * <p>So the builders are found by their signature — every method in the file returning
     * {@code List<ChangeSoD.Party>} — which makes membership a measurement rather than a
     * recollection (F2-204's rule, applied to a gate leg).
     */
    @Test
    @DisplayName("one party per bar, in every DERIVED bar builder")
    void everyBarBuilderKeepsOnePartyPerBar() {
        String stripped = strip(read(SVC));

        Matcher builders = Pattern.compile(
            "List<ChangeSoD\\.Party>\\s+(\\w+)\\s*\\(").matcher(stripped);
        List<String> found = new ArrayList<>();
        while (builders.find()) if (!found.contains(builders.group(1))) found.add(builders.group(1));

        System.out.println("F2-251 — derived party builders: " + found);
        assertThat(found)
            .as("a detector that stops resolving finds no builders, and none reads exactly like a "
              + "module with nothing to check")
            .hasSizeGreaterThanOrEqualTo(3);

        for (String builder : found) {
            String b = body(stripped, "List<ChangeSoD.Party> " + builder + "(");
            assertThat(b).as("%s must be resolvable", builder).isNotEmpty();

            int parties = b.split("new ChangeSoD\\.Party\\(", -1).length - 1;
            // ⚠ The receiver is NOT part of the claim. The first draft matched `c.get…Name()`, and
            // conditionOwnerBars binds `cond`, so the leg failed on correct code the moment the
            // population became derived — a rule keyed on an identifier cannot see the local alias
            // (F2-197), and a gate that fails on correct code trains the next reader to edit the
            // assertion rather than the code (F2-214).
            long ids = Pattern.compile("get\\w+Id\\(\\)\\s*,\\s*null").matcher(b).results().count();
            long names = Pattern.compile("null\\s*,\\s*\\w+\\.get\\w+Name\\(\\)").matcher(b)
                .results().count();

            assertThat(parties)
                .as("%s must build an id bar and a name bar per person, so parties = 2 x people",
                    builder)
                .isEqualTo((int) (ids + names));
            assertThat(ids).as("%s: every person needs an id bar", builder).isEqualTo(names);
            assertThat(ids).as("%s: at least one person", builder).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("the comment stripper is load-bearing and does not eat declarations")
    void theStripperKeepsTheCodeItIsScanning() {
        String raw = read(SVC);
        String stripped = strip(raw);

        // Anchored to line starts, because the words `public`/`private`/`return` occur inside the
        // service's prose paragraphs and counting them unanchored "loses" declarations that were
        // never code (F2-199's measurement of exactly this).
        Pattern anchored = Pattern.compile("(?m)^ {4}(?:public|private|protected)\\s");
        long before = anchored.matcher(raw).results().count();
        long after = anchored.matcher(stripped).results().count();
        assertThat(after)
            .as("stripping comments must not remove a single anchored declaration — the two-pass "
              + "form removed 45%% of App.tsx and reported a SMALLER population, which reads "
              + "exactly like a clean tree")
            .isEqualTo(before);

        // ⚠ The negative control is SYNTHETIC, and its first draft was not — it ran the two-pass form
        // over this very file and asserted declarations were lost. They were not: the corruption needs
        // a `//` comment whose text contains a literal `/*`, and this file happens to have none. So
        // the leg failed on correct code, which is the one failure a gate must never have (F2-214),
        // and a red leg is a finding only if the code is actually wrong (F2-206). The requirement is
        // real and this file is simply not where it bites — so the control constructs the shape that
        // does, exactly as F2-211 established.
        //
        // The control is the TWO-PASS form, never a reordered alternation: within one alternation the
        // branch order is a no-op (`//` and `/*` can never match at the same position), so a reordered
        // control passes on a rotted tree too — an equivalent mutant dressed as a proof.
        // ⚠ The delimiter inside the line comment must be UNTERMINATED. My first synthetic input used
        // a complete `/* ... */` inside the `//` comment, and the two-pass form handled it correctly
        // — the block pass simply removed that self-contained span and both declarations survived, so
        // the control asserted 2 < 2 and failed. The corruption needs the block pattern to run PAST
        // the declarations to a closer somewhere later in the file, which is exactly what a real
        // javadoc downstream provides.
        String pathological = ""
            + "    // prose mentioning /* an unterminated block delimiter\n"
            + "    public void survivesTheStripper() { }\n"
            + "    private void alsoSurvives() { }\n"
            + "    /** a real javadoc, whose closer the two-pass form pairs with the line comment. */\n"
            + "    protected void third() { }\n";
        assertThat(anchored.matcher(strip(pathological)).results().count())
            .as("the single alternation keeps all three: at a `//` an inner `/*` can never open a "
              + "block, so nothing downstream is swallowed")
            .isEqualTo(3);
        String twoPass = pathological
            .replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("(?<!:)//[^\\n]*", "");
        assertThat(anchored.matcher(twoPass).results().count())
            .as("and the two-pass form must LOSE declarations, or this requirement is decorative "
              + "and the leg above proves nothing")
            .isLessThan(3);
    }
}
