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
    @DisplayName("all three gates are named in one place, so the report cannot drift from the refusals")
    void gateNamesAreShared() {
        assertThat(ChangeSoD.GATES).containsExactly(
            "CONDITION_WAIVER_NOT_SELF", "PIR_REVIEWER_NOT_IMPLEMENTER", "CONDITION_SATISFIER_NOT_OWNER");
        String svc = strip(read(SRC.resolve("service/ChangeManagementService.java")));
        for (String gate : ChangeSoD.GATES) {
            assertThat(svc)
                .as("gate %s must appear in the coverage report built by the service", gate)
                .contains("ChangeSoD.GATE_");
        }
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
