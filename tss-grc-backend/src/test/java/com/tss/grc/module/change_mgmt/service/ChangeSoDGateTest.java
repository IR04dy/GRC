package com.tss.grc.module.change_mgmt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CE-15 — the two remaining §10 SoD gates, and the reason the first draft of them would have been
 * worthless.
 *
 * <h3>What these tests exist to hold</h3>
 * A self-review gate has three possible answers, not two. Collapsing "cannot tell" into "different" is
 * how the platform ended up with a shipped refusal that had never been able to fire (F2-112):
 * {@code assertNotSelfWaiver} compares the principal to the change's requester and owner, and in dev
 * <b>every</b> comparison is between a username and a team label, so it always answered "different".
 * Its own unit tests were green the whole time — they proved the rule, and nothing asked whether the
 * rule could run.
 *
 * <p>So {@link ChangeSoD.Verdict#UNVERIFIABLE} is a first-class outcome here, and the coverage report
 * is tested as carefully as the refusals.
 */
class ChangeSoDGateTest {

    private static final Path SRC = Path.of("src/main/java/com/tss/grc/module/change_mgmt");

    @AfterEach
    void clearAuth() { SecurityContextHolder.clearContext(); }

    private static ChangeSoD.Actor withId(long id, String name) { return new ChangeSoD.Actor(id, name); }
    private static ChangeSoD.Actor nameOnly(String name) { return new ChangeSoD.Actor(null, name); }
    private static ChangeSoD.Party party(Long id, String name) {
        return new ChangeSoD.Party("f", id, name);
    }

    // ── the three answers ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ids decide when both sides have one")
    void idsDecide() {
        assertThat(ChangeSoD.compare(withId(7, "admin"), party(7L, "Someone Else")))
            .as("the same user, whatever the labels say — the label is not the identity")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compare(withId(7, "admin"), party(8L, "admin")))
            .as("different users, even sharing a display name")
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("a record with no captured ids still falls back to names — even when the actor has one")
    void theFallbackIsKeyedOnThePartyNotOnBothSides() {
        // ⚠ THE REGRESSION THIS SLICE ALMOST SHIPPED. The first draft keyed the name fallback on BOTH
        // sides lacking an id, so a signed-in user (id 999) whose name matches a legacy change's
        // requester (no id) got UNVERIFIABLE and was NOT refused. S7e's own
        // ChangeConditionLifecycleTest.theLegacyFallbackIsAFallbackNotThePrimaryTest caught it —
        // DF-4/F2-24's scenario exactly. A refactor that stops refusing something is not a refactor.
        assertThat(ChangeSoD.compare(withId(999, "requester"), party(null, "requester")))
            .as("the record has no ids, so the name is all there is — and it matches")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compare(withId(7, "admin"), party(null, "CloudOps")))
            .as("a real answer to a weak question: it is DIFFERENT, and `strength` says it rested on "
              + "a label. Weakening the verdict to describe its own weakness would be the worst of both")
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("a known-user party can only be answered by an id")
    void aKnownPartyNeedsAnId() {
        assertThat(ChangeSoD.compare(nameOnly("admin"), party(7L, "Real Person")))
            .as("the party IS a user; a name could belong to anyone, so nothing here can decide")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("strength reports what the answer rested on, and never changes it")
    void strengthIsReportedNotEnforced() {
        assertThat(ChangeSoD.strength(withId(7, "admin"), party(9L, "x"))).isEqualTo(ChangeSoD.Strength.BY_ID);
        assertThat(ChangeSoD.strength(withId(7, "admin"), party(null, "CloudOps")))
            .as("this is the F2-112 population: deciding, but on a team label")
            .isEqualTo(ChangeSoD.Strength.BY_LABEL);
        assertThat(ChangeSoD.strength(nameOnly("admin"), party(7L, "x"))).isEqualTo(ChangeSoD.Strength.NONE);
        assertThat(ChangeSoD.strength(withId(7, "admin"), party(null, null))).isEqualTo(ChangeSoD.Strength.NONE);
        assertThat(ChangeSoD.strength(new ChangeSoD.Actor(null, null), party(1L, "x"))).isEqualTo(ChangeSoD.Strength.NONE);
    }

    @Test
    @DisplayName("names decide only when neither side has an id — preserved from the waiver rule")
    void namesAreTheFallbackNotThePreference() {
        assertThat(ChangeSoD.compare(nameOnly("admin"), party(null, "ADMIN")))
            .as("case-insensitive, exactly as assertNotSelfWaiver did — nothing that used to be "
              + "refused may stop being refused by this extraction")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compare(nameOnly("admin"), party(null, "  admin  ")))
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compare(nameOnly("admin"), party(null, "CloudOps")))
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
    }

    @Test
    @DisplayName("no principal, or no party, cannot decide")
    void missingSidesCannotDecide() {
        assertThat(ChangeSoD.compare(new ChangeSoD.Actor(null, null), party(7L, "x")))
            .as("a scheduler or an unauthenticated path must not be refused for being nobody, and "
              + "must not be waved through as verified either")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
        assertThat(ChangeSoD.compare(withId(7, "admin"), party(null, null)))
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
        assertThat(ChangeSoD.compare(withId(7, "admin"), null)).isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("only SAME refuses; UNVERIFIABLE is not a refusal")
    void onlyAMatchRefuses() {
        assertThat(ChangeSoD.Verdict.SAME.refuses()).isTrue();
        assertThat(ChangeSoD.Verdict.DIFFERENT.refuses()).isFalse();
        // Refusing on UNVERIFIABLE would block every review of every change that exists today: 17 of 17
        // carry team labels. Same reasoning as S6c's opt-in chair clause and S8e's inert flag.
        assertThat(ChangeSoD.Verdict.UNVERIFIABLE.refuses()).isFalse();
        assertThat(ChangeSoD.Verdict.UNVERIFIABLE.verified()).isFalse();
        assertThat(ChangeSoD.Verdict.DIFFERENT.verified()).isTrue();
    }

    @Test
    @DisplayName("compareAny reports UNVERIFIABLE when no party could be checked at all")
    void anyOfNothingCheckableIsUnverifiable() {
        // The waiver gate's real dev state: two parties, both team labels, principal has an id.
        assertThat(ChangeSoD.compareAny(withId(7, "admin"), List.of(
            party(null, "AppSec"), party(null, "IAM Team"))))
            .as("both parties are team labels: a real DIFFERENT resting on nothing anyone should trust")
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
        // F2-112 is visible HERE instead — in what the answer rested on, not in the answer.
        assertThat(ChangeSoD.strength(withId(7, "admin"), party(null, "AppSec")))
            .isEqualTo(ChangeSoD.Strength.BY_LABEL);
        assertThat(ChangeSoD.compareAny(new ChangeSoD.Actor(null, null), List.of(
            party(null, "AppSec"), party(9L, "Someone"))))
            .as("no principal at all — nothing to compare against, on any party")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
        assertThat(ChangeSoD.compareAny(withId(7, "admin"), List.of(
            party(null, "AppSec"), party(7L, "Someone"))))
            .isEqualTo(ChangeSoD.Verdict.SAME);
    }

    // ── F2-113: the committee force-recusal, whose actor is a seat and not a principal ──────────

    /**
     * <b>The finding, as a comparison.</b> A change's requester is a real user (id 8) whose recorded
     * label is a team ('CloudOps'); the CAB seat that user occupies is named 'SO Test Officer'. All the
     * committee gate ever had was the name comparison, and those two strings never match — which is
     * why Track D's bar on the deciding vote had never engaged for any change in either database.
     *
     * <p>⚠ <b>And the rule that makes the fix work is that a referral is a list of BARS, not of
     * people.</b> The parties below are built one per bar — an id with no name, a name with no id —
     * because that is all V358's two unpaired CSVs can express, and because it is what keeps
     * {@link ChangeSoD#compareAny}'s existing rule 2 in play for the name half. My first draft of this
     * slice instead added a whole new comparison method, {@code compareUnlinkedActor}, keyed on
     * "ids decide when BOTH sides carry one" — and a mutation test proved it <b>indistinguishable</b>
     * from {@code compare} for every party shape this caller produces, because no party here ever
     * carries an id and a name at once. It was a second copy of one rule wearing a javadoc claiming a
     * difference it did not have: the exact defect class F2-112 and F2-113 are instances of, added by
     * the fix for them. Deleted.
     */
    @Test
    @DisplayName("F2-113 — an id bar recuses the seat it names even though the labels disagree")
    void theSeatIsRecusedByIdWhenTheNamesCannotMatch() {
        ChangeSoD.Actor seat = withId(8, "SO Test Officer");

        assertThat(ChangeSoD.compare(seat, party(8L, null)))
            .as("dev's actual shape: user 8 raised the change and the seat is called 'SO Test "
              + "Officer'. Only the id can answer, and it does")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compare(seat, party(null, "CloudOps")))
            .as("and this is everything the gate used to compute — the name bar the CSV gave it, "
              + "against a label that is a team")
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
    }

    /**
     * ⚠ THE WEAKENING THIS SLICE HAD TO AVOID — CE-15's lesson, arriving from the far side.
     *
     * <p>29 of dev's 42 active committee seats carry no {@code member_user_id}, and
     * {@code committee_member_terms.no_platform_user} exists because an external director legitimately
     * never gets one. Rule 1 says a party's id supersedes its name, so a bar that carried
     * <em>both</em> — {@code Party(8L, "External Director B")} — would answer UNVERIFIABLE against
     * every unlinked seat and stop refusing what the name list refuses today. Keeping the halves
     * separate is what prevents that, and it is the only reason the split matters.
     */
    @Test
    @DisplayName("F2-113 — a name bar still decides against an UNLINKED seat, whatever ids exist")
    void addingIdsMustNotRetireTheNameComparisonForUnlinkedSeats() {
        ChangeSoD.Actor unlinkedSeat = nameOnly("External Director B");

        assertThat(ChangeSoD.compare(unlinkedSeat, party(null, "External Director B")))
            .as("the seat has no user id, so the name bar is all there is — and it matches, so it "
              + "refuses, exactly as it did before F2-113")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compare(unlinkedSeat, party(8L, "External Director B")))
            .as("⚠ and this is what MERGING the two halves into one party would compute instead: the "
              + "id wins, the seat has none, and the refusal disappears")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("F2-113 — an id bar cannot be satisfied by a name, so a homonym is not recused by it")
    void anIdBarIsAnsweredOnlyByAnId() {
        assertThat(ChangeSoD.compare(withId(2, "Khalid Al-Rashidi"), party(8L, null)))
            .as("two different people: user 2's seat is not barred by a bar on user 8")
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
        assertThat(ChangeSoD.compare(nameOnly("Khalid Al-Rashidi"), party(8L, null)))
            .as("an unlinked seat against an id bar: nothing comparable, and UNVERIFIABLE never "
              + "refuses — so the name bar beside it is what has to catch this")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("F2-113 — compareAny over the real dev shape: any one bar refuses")
    void anyBarMatchingRecuses() {
        // Exactly how recordVote builds the list from V358's two unpaired CSVs.
        List<ChangeSoD.Party> bars = List.of(
            party(8L, null), party(null, "CloudOps"), party(null, "IAM Team"));

        assertThat(ChangeSoD.compareAny(withId(8, "SO Test Officer"), bars))
            .as("the linked seat belongs to the requester — the id bar matches")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compareAny(nameOnly("IAM Team"), bars))
            .as("an unlinked seat labelled with one of the barred labels — the fallback matches")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compareAny(withId(2, "Khalid Al-Rashidi"), bars))
            .as("a seated member who neither raised nor owns the change votes normally")
            .isEqualTo(ChangeSoD.Verdict.DIFFERENT);
        assertThat(ChangeSoD.compareAny(withId(2, "Khalid"), List.of()))
            .as("an empty bar list bars nobody — dev's state on 4 of 4 live referrals, and "
              + "UNVERIFIABLE must not refuse or every referral in existence is trapped")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("the actor comes from the principal, and a plain token yields a name with no id")
    void actorReadsThePrincipal() {
        assertThat(ChangeSoD.actor().isPresent())
            .as("no security context — nobody is acting")
            .isFalse();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("compliance", "x", List.of()));
        ChangeSoD.Actor a = ChangeSoD.actor();
        assertThat(a.name()).isEqualTo("compliance");
        assertThat(a.id()).as("a non-GrcUserDetails principal carries no user id").isNull();
        assertThat(a.isPresent()).isTrue();
    }

    // ── coverage: the instrument F2-112 needed ──────────────────────────────────────────────────

    @Test
    @DisplayName("coverage names the blind case explicitly")
    void coverageNamesTheBlindCase() {
        ChangeSoD.Coverage blind = new ChangeSoD.Coverage(
            ChangeSoD.GATE_SELF_WAIVER, 17, 0, 17, 0);
        assertThat(blind.enabledButBlind())
            .as("dev's actual state on 2026-08-05: a gate switched on over 17 changes it cannot "
              + "decide for one of them")
            .isTrue();
        assertThat(blind.coveragePct()).isZero();

        ChangeSoD.Coverage fine = new ChangeSoD.Coverage(ChangeSoD.GATE_SELF_WAIVER, 4, 3, 1, 0);
        assertThat(fine.enabledButBlind()).isFalse();
        assertThat(fine.coveragePct()).isEqualTo(75);

        ChangeSoD.Coverage empty = new ChangeSoD.Coverage(ChangeSoD.GATE_CONDITION_NOT_OWNER, 0, 0, 0, 0);
        assertThat(empty.enabledButBlind())
            .as("an empty population is not a blind gate — there is nothing for it to decide, which "
              + "is the S7e state the conditions register deliberately ships in")
            .isFalse();
        assertThat(empty.coveragePct()).isZero();
    }

    @Test
    @DisplayName("every gate is named in one place, so the report cannot drift from the refusals")
    void gateNamesAreShared() {
        assertThat(ChangeSoD.GATES).containsExactly(
            "CONDITION_WAIVER_NOT_SELF", "PIR_REVIEWER_NOT_IMPLEMENTER", "CONDITION_SATISFIER_NOT_OWNER",
            // F2-113 — the fourth gate, enforced in the committee engine over data this module hands it.
            "COMMITTEE_VOTE_NOT_REQUESTER");

        // ⚠ The previous form of this loop asserted `svc.contains("ChangeSoD.GATE_")` once per gate —
        // true for all four the moment ANY one of them is referenced, so it could not fail. Named
        // constants, so a gate added to GATES and forgotten in the report is a red build.
        String svc = strip(read(SRC.resolve("service/ChangeManagementService.java")));
        for (String constant : List.of("GATE_SELF_WAIVER", "GATE_PIR_NOT_IMPLEMENTER",
                                       "GATE_CONDITION_NOT_OWNER", "GATE_COMMITTEE_VOTE_NOT_REQUESTER")) {
            assertThat(svc)
                .as("ChangeSoD.%s must appear in the coverage report built by the service — a gate in "
                  + "GATES and absent from /sod-coverage is a control nobody can see the reach of, "
                  + "which is the state F2-112 and F2-113 were both found in", constant)
                .contains("ChangeSoD." + constant);
        }
        assertThat(ChangeSoD.GATES)
            .as("one constant per gate, no duplicates")
            .doesNotHaveDuplicates()
            .hasSize(4);
    }

    // ── the structural claims ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("one comparison, three callers — the waiver no longer has its own copy")
    void onePredicateNotThree() {
        String svc = strip(read(SRC.resolve("service/ChangeManagementService.java")));

        assertThat(svc)
            .as("all three refusals go through ChangeSoD")
            .contains("assertNotSelfWaiver")
            .contains("assertPirReviewerIsNotImplementer")
            .contains("assertNotSelfSatisfaction");
        assertThat(count(svc, "ChangeSoD.compare"))
            .as("one call per gate and no fourth copy of the rule")
            .isGreaterThanOrEqualTo(3);
        assertThat(svc)
            .as("the inline id-then-name comparison must not come back — that copy is what made the "
              + "waiver's blindness invisible")
            .doesNotContain("equalsIgnoreCase(c.getRequestedByName())")
            .doesNotContain("equalsIgnoreCase(c.getChangeOwnerName())");
    }

    /**
     * F2-113 — <b>the fourth self-review refusal is the committee vote, and it must not be a fourth
     * copy of the comparison.</b>
     *
     * <p>It lives one module across, in {@code CommitteeResolutionService.recordVote}, and it had its
     * own inline rule: {@code sameName(member, nm)} over a CSV. That is how it came to compare a change's
     * team label against a committee member's name and match nothing, while three copies of the same
     * question in this module were being consolidated for exactly that reason. A private copy in a peer
     * module is the worst version of the mirror-maintained-by-assertion problem — the two rules disagree
     * and nothing anywhere fails.
     */
    @Test
    @DisplayName("F2-113 — the committee's recusal goes through ChangeSoD, not a local name loop")
    void theCommitteeRecusalHasNoCopyOfTheComparison() {
        Path engine = Path.of("src/main/java/com/tss/grc/module/admin/committee/service",
                              "CommitteeResolutionService.java");
        String code = strip(read(engine));

        String vote = methodBody(engine, "public CommitteeResolution recordVote(");
        assertThat(vote)
            .as("the recusal must resolve through the one comparison the platform has")
            .contains("ChangeSoD.compareAny");
        assertThat(vote)
            .as("the pre-F2-113 loop must not come back: it walked recusal_names inline and compared "
              + "each entry with sameName, so a referral carrying captured identity and no label — "
              + "the shape every change raised after CE-15 produces — barred nobody at all")
            .doesNotContain("getRecusalNames()");
        assertThat(vote)
            .as("the seat's own user id is what the comparison now rests on, and it must come from "
              + "the resolved roster seat rather than from anything the caller supplied")
            .contains("seat.getMemberUserId()");

        String body = methodBody(engine, "private static List<ChangeSoD.Party> recusedParties(");
        assertThat(body)
            .as("both halves are read — dropping the names would remove the only comparison the 29 "
              + "of 42 unlinked seats have")
            .contains("getRecusalUserIds()")
            .contains("getRecusalNames()");
        // ⚠ The bars must stay unpaired. Party(id, label) re-engages ChangeSoD rule 1 and silently
        // retires the name fallback for every unlinked seat — see the test named below, which is what
        // caught that mutation.
        assertThat(body)
            .as("one party per BAR, not per person: an id bar carries no name and a name bar no id")
            .contains("Long.valueOf(norm(raw)), null")
            .contains("null, norm(nm)");
    }

    @Test
    @DisplayName("the PIR reviewer is the principal, not a string from the body")
    void thePirReviewerIsTheSignedInUser() {
        String svc = strip(read(SRC.resolve("service/ChangeManagementService.java")));
        assertThat(svc).contains("capturePirReviewer");
        // ⚠ The gate is only meaningful because of this. While the reviewer came from the request body,
        // "PIR approver != implementer" compared two strings the same caller supplied.
        assertThat(svc)
            .as("the reviewer must no longer be taken straight from the body into the field")
            .doesNotContain("c.setPirReviewedBy(reviewer)");
        String body = methodBody(SRC.resolve("service/ChangeManagementService.java"),
                                 "private void capturePirReviewer(");
        assertThat(body).contains("ChangeSoD.actor()");
        assertThat(body).contains("setPirReviewedById");
        assertThat(body)
            .as("a claimed name may still be recorded when there is NO principal, but it must never "
              + "override one — the override would be the bypass")
            .contains("strFromBody");
    }

    @Test
    @DisplayName("create captures the raiser's id, because nothing did (F2-112's root cause)")
    void createCapturesTheRaiser() {
        String body = methodBody(SRC.resolve("service/ChangeManagementService.java"),
                                 "public ChangeRequest create(ChangeRequest req)");
        assertThat(body).contains("ChangeSoD.actor()");
        assertThat(body).contains("setRequestedById");
        assertThat(body)
            .as("only the id: the NAME stays whatever the tenant typed, because a team is a legitimate "
              + "requesting party even though it is not a person")
            .doesNotContain("setRequestedByName");
    }

    @Test
    @DisplayName("the independence qualifier is refused in code, not silently ignored")
    void theIndependenceQualifierIsAddressed() {
        String svc = read(SRC.resolve("service/ChangeManagementService.java"));
        // §10 says the closure gate applies "where the model demands independence". No such flag exists
        // anywhere for a change model, so the gate is unconditional and the javadoc has to say why —
        // otherwise the next reader assumes the qualifier was overlooked rather than measured.
        assertThat(svc).contains("where the model demands independence");
        assertThat(svc).contains("no such flag exists");
    }

    @Test
    @DisplayName("V357 adds capacity and writes no attribution")
    void theMigrationWritesNoAttribution() {
        String sql = read(Path.of("src/main/resources/db/migration/sqlserver/V357__change_sod_identity.sql"));
        assertThat(sql).contains("pir_reviewed_by_id BIGINT NULL");
        assertThat(sql).contains("owner_id BIGINT NULL");
        assertThat(sql)
            .as("a self-check must prove no row was populated — the migration must never invent the "
              + "attribution these gates exist to check")
            .contains("this migration must write no attribution");
        assertThat(sql)
            .as("and no DEFAULT, for the same reason")
            .contains("a default id is invented attribution");
        assertThat(sql.toUpperCase())
            .as("no UPDATE anywhere: backfilling 'CloudOps' to a user id is the owner's call (F2-109 "
              + "took the same position on change_code)")
            .doesNotContain("UPDATE CHANGE_MGMT.CHANGE_REQUESTS SET");
    }

    @Test
    @DisplayName("F2-113 / V358 adds capacity, writes no attribution, and keeps the name fallback")
    void theCommitteeRecusalMigrationWritesNoAttribution() {
        String sql = read(Path.of("src/main/resources/db/migration/sqlserver",
                                  "V358__committee_referral_recusal_identity.sql"));
        assertThat(sql).contains("recusal_user_ids NVARCHAR(200) NULL");
        assertThat(sql)
            .as("no backfill: there is no correct mapping from 'CloudOps' to a user id, and inventing "
              + "one would fabricate the attribution this gate exists to check (V357's stance)")
            .contains("this migration must write no attribution");
        assertThat(sql.toUpperCase())
            .as("and no UPDATE anywhere")
            .doesNotContain("UPDATE ADMIN.COMMITTEE_REFERRAL SET");
        assertThat(sql)
            .as("⚠ a self-check must refuse to let recusal_names be dropped as 'superseded' — it is "
              + "what recuses the seats that carry no user id, and dropping it would re-introduce "
              + "the very failure this migration fixes")
            .contains("unlinked committee seats have nothing left to compare");
        assertThat(sql)
            .as("and self-checks on both sides of the id comparison, or only names can ever match")
            .contains("member_user_id is gone")
            .contains("requested_by_id is gone");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static String read(Path p) {
        try { return Files.readString(p, StandardCharsets.UTF_8); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Comments stripped: a scan a javadoc can satisfy is a test that cannot fail (S8h, by mutation). */
    private static String strip(Path p) { return strip(read(p)); }

    private static String strip(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** One method's body, comments stripped, so a claim about a method is scoped to that method. */
    private static String methodBody(Path p, String signature) {
        String code = strip(read(p));
        int start = code.indexOf(signature);
        if (start < 0) throw new AssertionError("method not found: " + signature);
        int brace = code.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return code.substring(brace, i + 1);
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
