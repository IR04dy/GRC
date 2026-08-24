package com.tss.grc.security;

import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.compliance.service.AuditEngagementService;
import com.tss.grc.support.GuardExpressions;
import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-296 — one question, four mechanisms: <b>may the caller name the lifecycle state?</b>
 *
 * <p>The CREATE half has had a shared answer since F2-218 ({@link ServerOwnedState#requireInitial}),
 * with 28 call sites. The UPDATE half had none, and the consequence was three different answers:
 *
 * <ul>
 *   <li>{@code DisclosureService} hand-rolled it and compared through a {@code normalise} helper,
 *       with a paragraph explaining why a bare {@code equals} is not a comparison against a
 *       CI-collated column with no CHECK (F2-179);</li>
 *   <li>{@code EthicsCaseService}, in the <b>same package</b>, hand-rolled it with a bare
 *       {@code equals} on both {@code status} and {@code outcome} — the reasoning was reached,
 *       written down and not carried next door (F2-215's two-of-N);</li>
 *   <li>{@code compliance.audit_engagements} had <b>no copy at all</b>: {@code apply()} copied the
 *       caller's status with no vocabulary check and no direction check, beside an {@code advance()}
 *       that is explicitly forward-only, so the entity-bound PUT was the register's only reverse
 *       edge. F2-294 closed that register's CREATE and filed it as having <i>"closed its only open
 *       writers"</i>.</li>
 * </ul>
 *
 * <p>And a FOURTH, on the create side: {@code BcmCycleService.requireInitialState}, which existed
 * for a reason its own javadoc recorded — <i>"the field name, so the message names what the caller
 * actually sent"</i> — because the shared primitive hard-coded {@code "status: "}. 🔑 <b>A primitive
 * that cannot express what a caller needs is a primitive callers route around</b> (F2-190), and
 * three of its own callers were meanwhile emitting a 400 naming a field the request never carried
 * ({@code complianceStatus}, {@code approvalStatus}, {@code workflowStatus} — F2-210's shape).
 *
 * <p>⚠ The legs below assert the <b>shape</b> of each claim rather than a population count, because
 * a count cannot tell a fixed site from a detector that stopped matching (F2-69), and every detector
 * carries a positive control over the literal pre-fix source so a green reading cannot be confused
 * with a rotted regex (F2-195).
 */
class ServerOwnedStateContractTest {

    private static final Path MAIN = Paths.get("src/main/java/com/tss/grc");

    /**
     * The services whose entity-bound update guards a lifecycle field through the shared rule.
     *
     * <p>Asserted EXACTLY, in both directions: a register that stops calling it fails, and a new one
     * has to be added here, which is the moment a reader is forced to ask whether its own update is
     * a transition route nobody guarded. A decrease-only count could not do either.
     */
    private static final Set<String> GUARDS_ITS_UPDATE = Set.of(
        "AuditEngagementService",   // F2-296 — the live one: the register's only reverse edge
        "DisclosureService",        // F2-292 — NAZAHA conflict-of-interest disclosures
        "EthicsCaseService",        // F2-291 — whistleblowing / misconduct cases (status + outcome)
        // F2-297 — the four hand-rolled copies F2-296's own census listed by name and left unread.
        // Each carried the correct RULE (tolerate an echo, refuse a change) with three defects: a
        // bare equals against a CI-collated column, a message naming a Java method a caller cannot
        // call, and — on BcpPlanService — an entity initialiser that made the guard fire on every
        // row not already in the initial state.
        "BcpPlanService",           // F2-297 — the live one: BCP Builder could not save a plan
        "CrisisEventService",       // F2-297 — NCEMA crisis events
        "IssueService",             // F2-297 — the platform issue register
        "BcmBiaAssessmentService"   // F2-297 — approvalStatus, and the message said "status"
    );

    // ── the shared rule is shared ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("exactly the three registers with an entity-bound lifecycle update call the shared rule")
    void exactlyTheThreeRegistersCallTheSharedRule() {
        Map<String, String> services = serviceSources();
        Set<String> callers = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : services.entrySet()) {
            if (Pattern.compile("ServerOwnedState\\s*\\.\\s*requireUnchanged\\s*\\(")
                    .matcher(GuardExpressions.stripComments(e.getValue())).find()) {
                callers.add(simpleName(e.getKey()));
            }
        }
        assertThat(callers)
            .as("F2-296 — a register whose entity-bound update writes a lifecycle field must guard "
                + "it through ServerOwnedState.requireUnchanged. Adding a name here is how the next "
                + "reader is made to ask whether that update is an unguarded transition route; "
                + "removing one silently reopens the defect this slice closed.")
            .containsExactlyInAnyOrderElementsOf(GUARDS_ITS_UPDATE);
    }

    /**
     * ⚠⚠ <b>The first draft of this leg banned a {@code private normalise*()} helper containing
     * {@code toUpperCase} anywhere in a service, and it fired on FIVE files, 5 of 5 false
     * positives.</b> Every one was the platform's <i>recommended</i> normalise-on-write idiom —
     * {@code ExerciseObserverService}'s {@code normaliseRole}/{@code normaliseType}/
     * {@code normaliseSeverity} are named in CLAUDE.md as <i>"the pattern to copy"</i> for F2-179,
     * and {@code BcpBinderService.normaliseLanguage} validates an input against a vocabulary and
     * returns the canonical member. 🔑 <b>Those helpers canonicalise an INPUT; the defect is
     * comparing a REQUEST value against a STORED one.</b> A leg keyed on the helper measured the
     * house idiom rather than the defect, and a gate that fails on correct code trains the next
     * reader to edit the assertion (F2-214, F2-206: a red leg is a finding only if the code is
     * actually wrong).
     *
     * <p>So the ban is scoped to the shape of its claim: <b>inside a service that guards a lifecycle
     * field on its update, the comparison must be the primitive's.</b> The tree-wide question ships
     * as the census below, with no verdicts (F2-188).
     */
    @Test
    @DisplayName("the three registers compare through the primitive, never a hand-rolled equals")
    void theGuardingServicesDoNotHandRollTheComparison() {
        Map<String, String> services = serviceSources();
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : services.entrySet()) {
            if (!GUARDS_ITS_UPDATE.contains(simpleName(e.getKey()))) continue;
            String src = GuardExpressions.stripComments(e.getValue());
            if (handRolledLifecycleComparison(src)) offenders.add(e.getKey());
        }
        assertThat(offenders)
            .as("F2-296 — ServerOwnedState.sameState and requireUnchanged are both available, so "
                + "there is no correct code here for this to fail on. Two of these three services "
                + "hand-rolled the comparison and DISAGREED about whether it sees case; the third "
                + "had no comparison at all.")
            .isEmpty();
    }

    @Test
    @DisplayName("census: services still comparing a request lifecycle value against a stored one")
    void censusOfHandRolledLifecycleComparisons() {
        Map<String, String> services = serviceSources();
        List<String> sites = new ArrayList<>();
        for (Map.Entry<String, String> e : services.entrySet()) {
            if (handRolledLifecycleComparison(GuardExpressions.stripComments(e.getValue()))) {
                sites.add(simpleName(e.getKey()));
            }
        }
        // A work list with NO verdicts: some of these will be legitimate branch tests inside a
        // transition, which is a reading job and not something a regex can adjudicate (F2-182).
        // Decrease-only, so a new hand-rolled comparison fails rather than joining a tolerated total.
        // ⚠ This number is the DETECTOR'S OWN OUTPUT, not a prediction. The first draft of this leg
        // asserted 6 — a number I expected — and failed at 8: a baseline lifted from a guess is
        // worse than none, because it looks measured (F2-195). It then fell to 7 when BcmService's
        // comparison was converted, which is the fourth site and the one F2-234 had documented in
        // FullReplaceUpdateContractTest's map in the same terms as this slice's register without
        // either entry naming the other. Lowered in the same commit as the sweep (house rule 3: a
        // ceiling left high after a fix makes a tolerated member indistinguishable from a reviewed
        // one). None of the services in GUARDS_ITS_UPDATE is in this list, which is the ban working.
        // ⚠ F2-297 read all seven and took this 7 → 1. The reading is the whole value, because the
        // seven split THREE ways and a count could not rank any of them: FOUR were hand-rolled
        // copies of the rule this class shares (converted, and now in GUARDS_ITS_UPDATE above); TWO
        // are deliberate F2-231 change-detection branches that must KEEP accepting the field and so
        // must never be converted — they took `sameState` instead, because there a case-variant echo
        // read as a CHANGE and stamped a review that never happened, the opposite failure direction
        // from the four; and ONE is NdiGapService#updateGap, which writes the caller's status
        // unconditionally and has no transition route and no machine to bypass — F2-221's shape,
        // narrowed today only by a CHECK on ndi.gap.status, so the remaining question is whether
        // that vocabulary permits a JUMP past the service's three derivations, which is a direction
        // a CHECK cannot see (F2-296) and a reading nobody has done.
        assertThat(sites)
            .as("F2-296 census — each is a service comparing a request-side lifecycle getter "
                + "against a non-literal. Read before closing: a comparison against the CURRENT row "
                + "is this slice's defect, and a comparison against a target inside a transition is "
                + "the machine doing its job. Sites: %s", sites)
            .hasSizeLessThanOrEqualTo(1);
        assertThat(sites)
            .as("and none of them may be a service that already guards its update through the "
                + "primitive — if one appears here it has grown a second, disagreeing comparison")
            .doesNotContainAnyElementsOf(GUARDS_ITS_UPDATE);
    }

    @Test
    @DisplayName("the retired fourth copy of the create-side rule is gone and no service declares another")
    void noServiceDeclaresItsOwnCreateSideInitialStateGuard() {
        Map<String, String> services = serviceSources();
        List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : services.entrySet()) {
            if (Pattern.compile("(?:private|static)[\\w\\s]*\\s+requireInitialState\\s*\\(")
                    .matcher(GuardExpressions.stripComments(e.getValue())).find()) {
                offenders.add(e.getKey());
            }
        }
        assertThat(offenders)
            .as("F2-296 retired BcmCycleService.requireInitialState, the fourth mechanism answering "
                + "this question. It existed because the shared primitive could not name the field; "
                + "it can now, so a fresh copy has no remaining excuse.")
            .isEmpty();
    }

    // ── the primitive can name the field, and its callers use that ───────────────────────────────

    @Test
    @DisplayName("every requireInitial call whose setter is not setStatus names its own field")
    void everyRequireInitialCallForANonStatusFieldNamesItsField() {
        Map<String, String> services = serviceSources();
        List<String> unnamed = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, String> e : services.entrySet()) {
            for (String[] site : requireInitialSites(GuardExpressions.stripComments(e.getValue()))) {
                checked++;
                if (!"setStatus".equals(site[0]) && Integer.parseInt(site[1]) < 5) {
                    unnamed.add(simpleName(e.getKey()) + " :: " + site[0]
                                + " (" + site[1] + " args)");
                }
            }
        }
        assertThat(checked)
            .as("anti-vacuity: the site scan must still resolve the requireInitial population. "
                + "0 means the detector stopped matching, which reads exactly like a clean tree.")
            .isGreaterThanOrEqualTo(20);
        assertThat(unnamed)
            .as("F2-296 — the 4-argument form hard-codes \"status: \", so a caller writing "
                + "complianceStatus / approvalStatus / workflowStatus emitted a 400 naming a field "
                + "the request does not carry (F2-210's shape). That is the recorded reason the "
                + "fourth copy of this rule existed. Pass the field name.")
            .isEmpty();
    }

    /**
     * F2-312 — the population of comparing entry points is <b>derived</b>, so the leg above cannot
     * silently stop covering one.
     *
     * <p>It went stale once already: named for three, exercising four, missing the fifth. Reflection
     * over the public static methods that take a candidate value and compare it means a new primitive
     * fails here until somebody exercises it — which is the same rule this class applies to its
     * subject ([[F2-299]]: derive the vocabulary, do not hold a list).
     */
    @Test
    void everyComparingEntryPointIsExercisedHere() throws IOException {
        Set<String> comparing = new java.util.TreeSet<>();
        for (java.lang.reflect.Method m : ServerOwnedState.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())
                || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            // a comparing primitive takes at least two Strings (the candidate and what it is
            // measured against) or a Set of legal values
            long strings = java.util.Arrays.stream(m.getParameterTypes())
                .filter(t -> t == String.class).count();
            boolean takesSet = java.util.Arrays.stream(m.getParameterTypes())
                .anyMatch(t -> t == Set.class);
            if (strings >= 2 || takesSet) {
                comparing.add(m.getName());
            }
        }

        // this class reads its OWN source, so the question is "is it exercised here"
        String body = Files.readString(Paths.get("src", "test", "java", "com", "tss", "grc",
                                                 "security",
                                                 "ServerOwnedStateContractTest.java"));
        var unexercised = comparing.stream()
            .filter(n -> !body.contains("ServerOwnedState." + n + "("))
            .sorted()
            .toList();

        assertThat(unexercised).as("""
            A comparing entry point on ServerOwnedState is never exercised by this class.

            This leg exists because the case-agreement leg was named for THREE while it exercised             FOUR and missed the fifth (requireImmutableReference, added by F2-306). A name that             reads like a complete population hides the gap, so the population is derived.

            Exercise it above, or — if it genuinely does not compare a candidate against anything —             narrow the derivation and say why.

            found %d comparing entry points: %s
            unexercised: %s""".formatted(comparing.size(), comparing, unexercised))
            .isEmpty();

        assertThat(comparing.size()).as(
            "Comparing entry points found by reflection: %d. If this collapsed, the derivation broke "
            + "and the leg above is asserting over nothing.", comparing.size())
            .isGreaterThanOrEqualTo(4);
    }

    /**
     * ⚠⚠ F2-312 — {@code requireUnchanged} and {@code requireImmutableReference} differ in exactly
     * ONE branch, and <b>nothing pinned it</b>.
     *
     * <p>When the stored value is <b>absent</b>:
     *
     * <ul>
     *   <li>{@code requireUnchanged} <b>refuses</b> — its message says <em>"has no X yet and an
     *       update cannot set one"</em>. Right for a lifecycle state: a row always has one, so an
     *       absent stored value means something is wrong.</li>
     *   <li>{@code requireImmutableReference} <b>permits</b> — settable while absent. Right for a
     *       reference: a record may legitimately be created without one and given it later, which is
     *       exactly what makes a UI-created row with no code repairable.</li>
     * </ul>
     *
     * <p>⚠⚠ <b>My hypothesis was that this branch was unpinned, and it is REFUTED: both halves are
     * asserted — in two different classes, neither of which names the other.</b>
     * {@code requireUnchangedRefusesAValueForAnAbsentField} (below) pins the refusal;
     * {@code ReferenceRenameContractTest.aReferenceIsSettableWhileAbsent} pins the permission. So the
     * contrast holds by two artefacts happening to agree, with nobody maintaining the agreement
     * ([[F2-177]]).
     *
     * <p>🔑 <b>What that costs is legibility at the moment it matters.</b> A reader who merges the two
     * primitives makes ONE of those tests fail, in a class they are not editing, with a message about
     * an ethics outcome or a reference — which reads as <em>"that test is now wrong"</em> rather than
     * <em>"you have just broken the other primitive."</em> This leg asserts the two behaviours
     * side by side so the failure names the contrast itself. It is deliberately a THIRD assertion of
     * facts already covered: the duplication is the point, because the thing being protected is that
     * they differ ([[F2-190]] from the other side — sharing is right until the thing being shared is
     * not the same question).
     */
    @Test
    void theTwoUpdateSidePrimitivesDivergeOnAnAbsentStoredValueAndThatIsDeliberate() {
        // a lifecycle state: an absent stored value is refused
        assertThatThrownBy(() -> ServerOwnedState.requireUnchanged(
                "APPROVED", null, "status", "Plan 7", "/transition", null))
            .as("a row always has a status, so an absent one means something is wrong — if this "
                + "fails, requireUnchanged has been merged with requireImmutableReference and "
                + "lifecycle states are now settable by an update")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("has no status yet");

        assertThatThrownBy(() -> ServerOwnedState.requireUnchanged(
                "APPROVED", "   ", "status", "Plan 7", "/transition", null))
            .as("blank is absent for this purpose too")
            .isInstanceOf(IllegalArgumentException.class);

        // a business reference: an absent stored value is SETTABLE, and that is the whole difference.
        // If either of these throws, requireImmutableReference has inherited requireUnchanged's
        // refusal and every create that names its own code now fails.
        ServerOwnedState.requireImmutableReference("POL-1", null, "code", "Policy 7", "Omit it.");
        ServerOwnedState.requireImmutableReference("POL-1", "   ", "code", "Policy 7", "Omit it.");
    }

    // ── one question, one answer: the three comparisons agree ────────────────────────────────────

    /**
     * ⚠⚠ F2-312 — this was named {@code allThreeEntryPointsAgreeOnCase} while it exercised
     * <b>four</b>, and it did not touch {@code requireImmutableReference} at all, which [[F2-306]]
     * added. 🔑 <b>A leg named for a count goes stale the moment the class grows, and the miss is
     * invisible because the name reads like a complete population</b> — in the class whose whole
     * subject is one-question-one-answer. The population is derived by reflection now
     * ({@link #everyComparingEntryPointIsExercisedHere}), so a new comparing primitive fails the
     * build until it is added here.
     */
    @Test
    @DisplayName("every comparing entry point is case-insensitive and returns the canonical spelling")
    void everyEntryPointAgreesOnCase() {
        assertThat(ServerOwnedState.sameState("planned", "PLANNED")).isTrue();
        assertThat(ServerOwnedState.sameState("  Planned ", "PLANNED")).isTrue();
        assertThat(ServerOwnedState.sameState("ENGAGED", "PLANNED")).isFalse();
        assertThat(ServerOwnedState.sameState(null, "")).isTrue();

        assertThat(ServerOwnedState.requireInitial("planned", "PLANNED", "an engagement", "/advance"))
            .as("a case-variant echo of the initial state is accepted and stored CANONICALLY — the "
                + "column collates CI, so refusing a spelling the database would store is not a "
                + "comparison (F2-179), and coercing the casing is what ClosedVocabulary.require "
                + "already does")
            .isEqualTo("PLANNED");

        assertThat(ServerOwnedState.requireCreatable(
                "active", Set.of("DRAFT", "ACTIVE"), "DRAFT", "a document", "/transition"))
            .as("requireCreatable had a case-SENSITIVE Set.contains until F2-296. Leaving it that "
                + "way while its two siblings normalise would have shipped F2-215's two-of-N inside "
                + "the class whose whole subject is one-question-one-answer.")
            .isEqualTo("ACTIVE");

        // requireUnchanged: a case-variant ECHO is tolerated, a real CHANGE is refused.
        ServerOwnedState.requireUnchanged("closed", "CLOSED", "status", "Engagement 1", "/advance",
                                          null);
        assertThatThrownBy(() -> ServerOwnedState.requireUnchanged(
                "PLANNED", "CLOSED", "status", "Engagement 1",
                "POST /api/v1/compliance/audit-engagements/1/advance", "Allowed from CLOSED: []."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status:")
            .hasMessageContaining("cannot be moved to PLANNED by an update")
            .hasMessageContaining("/advance");

        // F2-312 — the FIFTH entry point, absent from this leg until now. Same case rule, because
        // the column collates CI and V396's unique index treats AC-2 and ac-2 as one value: a
        // case-variant is an echo, not a rename, and refusing it would refuse a spelling the
        // database would happily store (F2-179).
        ServerOwnedState.requireImmutableReference("pol-info-001", "POL-INFO-001", "code",
                                                   "Policy 1", "Omit it.");
        assertThatThrownBy(() -> ServerOwnedState.requireImmutableReference(
                "POL-INFO-999", "POL-INFO-001", "code", "Policy 1", "Omit it."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("code:")
            .hasMessageContaining("POL-INFO-001");
    }

    @Test
    @DisplayName("requireUnchanged refuses a value for a field the record has none of")
    void requireUnchangedRefusesAValueForAnAbsentField() {
        assertThatThrownBy(() -> ServerOwnedState.requireUnchanged(
                "SUBSTANTIATED", null, "outcome", "Case 7", "the REMEDIATION transition", null))
            .as("the ethics outcome case: a create has no outcome, so ANY supplied one is refused — "
                + "an outcome that did not come from an investigation is not a finding")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outcome:")
            .hasMessageContaining("has no outcome yet");
    }

    // ── the migration and the machine are one vocabulary ─────────────────────────────────────────

    @Test
    @DisplayName("V394's CHECK admits exactly the ladder advance() walks")
    void theMigrationVocabularyIsTheServicesOwnLadder() throws Exception {
        Field f = AuditEngagementService.class.getDeclaredField("STATUS_ORDER");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> ladder = (List<String>) f.get(null);

        Set<String> sql = MigrationVocabularies.valuesOf("CK_audit_engagements_status");
        assertThat(sql)
            .as("F2-296 — the CHECK is the service's own STATUS_ORDER, not a list assembled for the "
                + "migration. Constraining to the 7 states that happen to be STORED would refuse "
                + "FOLLOW_UP, which scheduleFollowUp() writes — F2-180's defect, a column refusing "
                + "a value its own screen offers.")
            .containsExactlyInAnyOrderElementsOf(ladder);
        assertThat(ladder).hasSize(8);
    }

    @Test
    @DisplayName("a CHECK cannot see direction, so the Java guard is what closes the reverse edge")
    void theCheckNarrowsTheVocabularyAndTheGuardNarrowsTheDirection() {
        String src = GuardExpressions.stripComments(read(
            MAIN.resolve("module/compliance/service/AuditEngagementService.java")));

        assertThat(src)
            .as("apply() must NOT copy the status: that line was the whole defect, and every token "
                + "in a CLOSED -> PLANNED move is a legal member of the CHECK added by V394, so the "
                + "database cannot refuse it")
            .doesNotContain("target.setStatus(src.getStatus())");
        assertThat(Pattern.compile("requireUnchanged\\s*\\(").matcher(src).results().count())
            .as("the update path must call the shared guard exactly once")
            .isEqualTo(1L);
        assertThat(Pattern.compile("requireInitial\\s*\\(").matcher(src).results().count())
            .as("and the create path exactly once")
            .isEqualTo(1L);
    }

    // ── self-tests: the detectors fire on the literal pre-fix shapes ─────────────────────────────

    @Test
    @DisplayName("the detectors fire on the shapes this slice removed and not on the shapes it wrote")
    void theDetectorsAreSound() {
        assertThat(handRolledLifecycleComparison(
                "if (normalise(src.getStatus()).equals(normalise(cur))) return;"))
            .as("DisclosureService's retired comparison — the positive control")
            .isTrue();
        assertThat(handRolledLifecycleComparison(
                "if (src.getStatus() != null && !src.getStatus().equals(curStatus)) {"))
            .as("EthicsCaseService's retired bare-equals comparison — the second positive control, "
                + "and the one whose case-sensitivity disagreed with the first")
            .isTrue();
        assertThat(handRolledLifecycleComparison(
                "if (src.getOutcome() != null && !src.getOutcome().equals(curOutcome)) {"))
            .as("and its outcome half")
            .isTrue();

        assertThat(handRolledLifecycleComparison(
                "if (ServerOwnedState.sameState(src.getStatus(), curStatus)) return;"))
            .as("must NOT fire on the post-fix shape")
            .isFalse();
        assertThat(handRolledLifecycleComparison(
                "if (\"CLOSED\".equals(req.getStatus())) { … }"))
            .as("must NOT fire on a comparison against a STRING LITERAL — that is a vocabulary or "
                + "branch test, not a request-versus-stored comparison, and it is the exclusion "
                + "that stops this leg measuring the whole tree (F2-192: scope a scan to the shape "
                + "of its claim)")
            .isFalse();
        assertThat(handRolledLifecycleComparison(
                "private static String normaliseSeverity(String s) {\n"
                + "    return s == null ? \"INFO\" : s.trim().toUpperCase(Locale.ROOT);\n}"))
            .as("must NOT fire on the platform's recommended normalise-on-write helper — the first "
                + "draft of this detector banned exactly this and reported 5 of 5 false positives, "
                + "one of them the idiom CLAUDE.md names as 'the pattern to copy'")
            .isFalse();

        // The 4-arg / 5-arg discrimination, over the literal pre-fix and post-fix call sites.
        String fourArg =
            "body.setApprovalStatus(ServerOwnedState.requireInitial(\n"
            + "    body.getApprovalStatus(), INITIAL_APPROVAL_STATUS, \"a continuity strategy\",\n"
            + "    \"POST /api/v1/bcm/strategies/{id}/transition\"));";
        String fiveArg =
            "body.setApprovalStatus(ServerOwnedState.requireInitial(\n"
            + "    body.getApprovalStatus(), INITIAL_APPROVAL_STATUS, \"a continuity strategy\",\n"
            + "    \"POST /api/v1/bcm/strategies/{id}/transition\", \"approvalStatus\"));";
        assertThat(requireInitialSites(fourArg)).hasSize(1);
        assertThat(requireInitialSites(fourArg).get(0))
            .as("the pre-fix shape: setApprovalStatus with 4 arguments, i.e. a 400 naming 'status'")
            .containsExactly("setApprovalStatus", "4");
        assertThat(requireInitialSites(fiveArg).get(0))
            .containsExactly("setApprovalStatus", "5");

        // A route string containing a comma must not be counted as an extra argument.
        String commaInRoute =
            "e.setStatus(ServerOwnedState.requireInitial(req.getStatus(), INITIAL, \"a thing\",\n"
            + "    \"POST /a/{id}/advance, which walks the ladder\"));";
        assertThat(requireInitialSites(commaInRoute).get(0))
            .as("a comma inside a string literal is not an argument separator — counting it would "
                + "report a 4-arg call as compliant, which is the quiet direction")
            .containsExactly("setStatus", "4");
    }

    // ── detectors ───────────────────────────────────────────────────────────────────────────────

    /**
     * A request-side lifecycle getter compared with {@code equals} against a non-literal.
     *
     * <p>Both pre-fix shapes match: the bare {@code src.getStatus().equals(curStatus)} and the
     * normalised {@code normalise(src.getStatus()).equals(normalise(cur))}. A comparison against a
     * STRING LITERAL does not, because that is a vocabulary or branch test rather than a
     * request-versus-stored comparison — excluding it is what stops this firing on the whole tree.
     */
    private static boolean handRolledLifecycleComparison(String src) {
        Matcher bare = Pattern.compile(
            "\\w+\\.get(?:\\w*Status|Outcome)\\s*\\(\\s*\\)\\s*\\.\\s*equals\\s*\\(\\s*(.)")
            .matcher(src);
        while (bare.find()) {
            if (!"\"".equals(bare.group(1))) return true;
        }
        return Pattern.compile(
            "normalis\\w*\\s*\\(\\s*\\w+\\.get(?:\\w*Status|Outcome)\\s*\\(\\s*\\)\\s*\\)"
            + "\\s*\\.\\s*equals\\s*\\(").matcher(src).find();
    }

    /**
     * Every {@code setX(ServerOwnedState.requireInitial(...))} site, as {@code [setter, argCount]}.
     *
     * <p>Arguments are counted at the TOP LEVEL of the call — string literals and nested parens are
     * skipped — because this codebase's route arguments legitimately contain commas, and counting
     * one as a separator would report a 4-argument call as a 5-argument one, i.e. would report the
     * defect as fixed.
     */
    private static List<String[]> requireInitialSites(String src) {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("(set\\w+)\\s*\\(\\s*ServerOwnedState\\s*\\.\\s*requireInitial\\s*\\(")
            .matcher(src);
        while (m.find()) {
            out.add(new String[] { m.group(1), String.valueOf(topLevelArgs(src, m.end())) });
        }
        return out;
    }

    /** Counts top-level comma-separated arguments starting just inside an open paren. */
    private static int topLevelArgs(String src, int from) {
        int depth = 0;
        int args = 1;
        boolean inStr = false;
        for (int i = from; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    return args;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                args++;
            }
        }
        return args;
    }

    // ── corpus ──────────────────────────────────────────────────────────────────────────────────

    /** Every shipped {@code *Service.java}, keyed by fully-qualified path (F2-295). */
    private static Map<String, String> serviceSources() {
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(MAIN)) {
            walk.filter(p -> p.getFileName().toString().endsWith("Service.java"))
                .sorted()
                .forEach(p -> out.put(MAIN.relativize(p).toString().replace('\\', '/'), read(p)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(out.size())
            .as("anti-vacuity: the service corpus must resolve")
            .isGreaterThanOrEqualTo(300);
        return out;
    }

    private static String simpleName(String relPath) {
        return relPath.substring(relPath.lastIndexOf('/') + 1).replace(".java", "");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
