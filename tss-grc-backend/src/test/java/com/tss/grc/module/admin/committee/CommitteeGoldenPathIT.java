package com.tss.grc.module.admin.committee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.tss.grc.common.model.CommitteeTypes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <h2>W1-5 / F2-18 — one committee decision, carried from constitution to consequence, through the
 * real HTTP stack and a real database.</h2>
 *
 * <h3>The gap this closes, measured</h3>
 * Before this class the backend suite held <b>2,807 tests in 292 classes and not one
 * {@code @SpringBootTest}</b> — the Spring context had never been booted by a test, and the single
 * {@code MockMvc} in the tree was a {@code standaloneSetup} (a bare dispatcher round a controller
 * instance, with no filter chain, no security, no JPA). The committee engine specifically had 99
 * unit tests across 8 classes, every one of them handing mocked repositories inputs it chose itself.
 *
 * <p>That is not a coverage complaint. It is the reason a recurring defect shape in this programme
 * reads <i>"the mechanism was right and its data never existed"</i> — F2-23, F2-25, F2-26 and F2-28
 * in a row, and F2-28's headline was that {@code committee_meeting_attendee} held <b>zero rows</b>,
 * so CH-14's deterministic quorum path had never once executed. A mocked repository cannot notice
 * that nothing ever calls it. Neither can it notice a JSON contract that does not round-trip, a
 * {@code @PreAuthorize} that never fires because the filter chain is absent, a constraint the
 * database would reject, or a transaction boundary that commits half a decision.
 *
 * <h3>What makes this a golden path rather than a smoke test</h3>
 * The walk is deliberately built to be <em>refusable at every gate</em>, and each gate is one this
 * codebase has actually been wrong about:
 * <ul>
 *   <li><b>Two real principals, no {@code PLATFORM_ADMIN}.</b> {@code compliance} holds
 *       {@code ADMIN:WRITE}+{@code CHANGE:WRITE}; {@code so_officer} holds {@code ADMIN:APPROVE}+
 *       {@code CHANGE:APPROVE} and <em>not</em> {@code ADMIN:WRITE}. Probing as {@code admin} would
 *       prove nothing — {@code PLATFORM_ADMIN} bypasses in code (F2-29), so every guard on the path
 *       would be satisfied by the bypass rather than by the grant.</li>
 *   <li><b>The quorum denominator is read back and asserted against the roster</b>, not against the
 *       attendance sheet (F2-28/DF-5).</li>
 *   <li><b>The member with a linked platform user casts their own vote</b> ({@code onBehalf=0}) —
 *       the path F2-26 opened and which was a 403 before it.</li>
 *   <li><b>A different principal approves the minutes than recorded the attendance</b> — F2-39 makes
 *       that a 409, so a single-actor walk would fail here and prove the separation is live.</li>
 *   <li><b>The change advances only because the committee decided</b>, and the negative test drives
 *       the same path with a non-quorate meeting to show the gate refuses.</li>
 * </ul>
 *
 * <h3>Why it is bound to failsafe rather than surefire</h3>
 * It needs a reachable SQL Server plus {@code JWT_SECRET} and {@code DB_PASSWORD}. Making the
 * documented full build ({@code mvn -o clean package}) require a database would be a worse defect
 * than the one being fixed. Run it with:
 *
 * <pre>{@code JAVA_HOME=/c/Program\ Files/Java/jdk-17 \
 *   JWT_SECRET=... DB_PASSWORD=... mvn -o verify -Pit}</pre>
 *
 * <p>⚠ Not {@code -DskipTests}: failsafe honours that flag too, so it skips the integration tests
 * along with the unit ones and the build goes green having run neither. The first attempt at this
 * slice did exactly that and reported BUILD SUCCESS with the golden path never executed — which is
 * the same failure mode as a skipped test, arrived at from the other direction.
 *
 * <p>See the {@code it} profile in {@code pom.xml}. Missing environment is a <b>hard failure
 * with instructions</b>, never a skip: a green build containing a silently-skipped integration test
 * is the A17-D2 defect in test form — an accurate signal nothing consumes.
 *
 * <h3>Data policy</h3>
 * Every artefact is tagged {@link CommitteeGoldenPath#EVIDENCE_TAG} — <b>not</b> {@code [REVIEW-TEST]},
 * which the repo's cleanup scripts delete. W1-5 asks for the worked decision to be <em>retained</em>
 * as demo evidence, so the walk cleans up <b>its own previous run</b> on the way in and leaves the
 * current one standing: exactly one complete, genuine, e-signed committee decision persists in dev,
 * and repeated runs do not accumulate. Teardown is scoped to
 * {@link CommitteeGoldenPath#CODE_PREFIX}, so the predicate cannot reach a seeded or real row.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // F2-74. A booted context starts the audit-sealing scheduler, and this test shares
        // TSS_GRC with whatever dev backend a sibling session is running. A previous session
        // sealed 5,000 audit rows with no integrity key that way. The ledger assertions below
        // read rows; they do not need them sealed.
        "grc.audit.sealing-enabled=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.com.tss.grc=WARN"
    })
@ActiveProfiles("dev")
// PER_CLASS so @BeforeAll can be an instance method and use the injected JdbcTemplate.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("W1-5 — committee golden path (real HTTP + real DB)")
class CommitteeGoldenPathIT {

    /**
     * Runs <em>before</em> the context is built, which is the only place a missing-environment
     * message can be legible — a {@code JwtTokenProvider} {@code @PostConstruct} failure arrives as
     * a context-load stack trace that says nothing about what the operator forgot.
     */
    @DynamicPropertySource
    static void requireALocalStack(DynamicPropertyRegistry registry) {
        String dbPassword = System.getenv("DB_PASSWORD");
        String jwtSecret  = System.getenv("JWT_SECRET");
        if (isBlank(dbPassword) || isBlank(jwtSecret)) {
            throw new IllegalStateException("""

                W1-5 integration test needs the local stack. Set both, then re-run:

                  DB_PASSWORD  — dev SQL Server 'sa' password (see CLAUDE.md)
                  JWT_SECRET   — >=32 chars, no 'change-this'/'placeholder'

                  JWT_SECRET=... DB_PASSWORD=... mvn -o verify -Pit -DskipTests

                This is a failure and not a skip on purpose: an integration test that reports
                SKIPPED inside a green build is an accurate signal nobody consumes, which is
                the defect A17-D2/F2-87 closed elsewhere in this platform.
                """);
        }
        registry.add("spring.datasource.password", () -> dbPassword);
        registry.add("grc.jwt.secret", () -> jwtSecret);
    }

    private static final String TAG    = CommitteeGoldenPath.EVIDENCE_TAG;
    private static final String PREFIX = CommitteeGoldenPath.CODE_PREFIX;

    /** Secretariat: ADMIN:WRITE + CHANGE:WRITE. Constitutes, appoints, records, minutes. */
    private static final String SECRETARIAT = "compliance";
    /** Approver: ADMIN:APPROVE + CHANGE:APPROVE, and deliberately NOT ADMIN:WRITE (F2-39). */
    private static final String APPROVER    = "so_officer";
    private static final String PASSWORD    = "Admin@12345";

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private static final Set<String> COMPLETED_STEPS = new LinkedHashSet<>();

    private String secretariatToken;
    private String approverToken;
    private Long   approverUserId;

    // ── lifecycle ────────────────────────────────────────────────────────────────

    /**
     * Purge once for the class, before any test, rather than inside the golden path.
     *
     * <p>The first version purged inside the walk, so the second test constituted a committee whose
     * code the previous run still held — and {@code POST /api/admin/committees} answers a duplicate
     * code with <b>500 "An unexpected database error occurred."</b> rather than a 409 naming the
     * field. That is <b>F2-88</b>, filed while closing A17-D2 and reproduced here without trying:
     * it is not an edge case, it is what the second run of anything looks like.
     */
    @BeforeAll
    void purgeAndAnnounce() {
        COMPLETED_STEPS.clear();
        purgePreviousRun();
    }

    @AfterAll
    static void reportSteps() {
        // Not an assertion — the per-test assertion below is the gate. This is so a failing run
        // says how far the decision got, which is the first question anyone asks.
        System.out.println("[W1-5] golden-path steps completed: " + COMPLETED_STEPS);
    }

    // ── the golden path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a committee decision carries a change from DRAFT to CAB_APPROVED, and only the committee can")
    void aCommitteeDecisionCarriesTheChange() {
        secretariatToken = login(SECRETARIAT);
        approverToken    = login(APPROVER);
        approverUserId   = userIdOf(APPROVER);

        // 1 ── constitute the committee ------------------------------------------------
        long committeeId = constituteCommittee("CAB");
        step("constituteCommittee");

        // 2 ── appoint the voting roster ------------------------------------------------
        // Three voting seats. One is linked to a real platform user, so that member can cast
        // their OWN vote — F2-26's path, and a 403 before it existed. The other two are
        // external directors with no login, which is the normal shape of a Saudi board seat.
        long ownSeat      = appoint(committeeId, "SO Officer " + TAG, approverUserId, "Chair");
        long externalSeat = appoint(committeeId, "External Director A " + TAG, null, "Member");
        long recusedSeat  = appoint(committeeId, "External Director B " + TAG, null, "Member");
        assertThat(activeRoster(committeeId)).hasSize(3);
        step("appointVotingRoster");

        // 3 ── raise the change ---------------------------------------------------------
        // NORMAL/LOW routes to CAB with minApprovals=1 in this tenant's authorisation matrix.
        long changeId = raiseChange();
        step("raiseChange");

        // 4 ── refer it to the committee ------------------------------------------------
        // committeeId travels in the BODY. Passing it as a query parameter — the first draft here —
        // is silently ignored and the gate map resolves instead, so the referral came back pointing
        // at the tenant's REAL CAB committee with a 201 and no complaint. Ordinary Spring behaviour,
        // but it is why the assertion below checks which committee was bound rather than trusting
        // that the call succeeded.
        JsonNode referral = post(secretariatToken,
            "/api/v1/changes/" + changeId + "/refer-to-committee",
            Map.of("committeeId", committeeId), HttpStatus.CREATED);
        assertThat(referral.path("status").asText()).isEqualTo("PENDING");
        assertThat(referral.path("gate").asText())
            .as("a NORMAL/LOW change routes to CAB in this tenant's authorisation matrix")
            .isEqualTo("CAB");
        assertThat(referral.path("committeeId").asLong())
            .as("the explicitly named committee must be the one bound, not the gate-map default")
            .isEqualTo(committeeId);
        step("referChangeToCommittee");

        // 5 ── open the meeting ---------------------------------------------------------
        long meetingId = openMeeting(committeeId);
        step("openMeeting");

        // 6 ── record attendance against roster SEATS, not free text --------------------
        JsonNode quorum = post(secretariatToken,
            "/api/admin/committees/meetings/" + meetingId + "/attendance",
            Map.of("marks", List.of(
                Map.of("memberTermId", ownSeat,      "present", true),
                Map.of("memberTermId", externalSeat, "present", true),
                Map.of("memberTermId", recusedSeat,  "present", true))),
            HttpStatus.OK);
        step("recordSeatBoundAttendance");

        // 7 ── the denominator is the roster ---------------------------------------------
        // F2-28/DF-5: before V319 the caller supplied both numerator and denominator, so three
        // invented attendees made a committee quorate. Read it back from the server and assert
        // the arithmetic came from the roster.
        assertThat(quorum.path("totalVoting").asInt()).isEqualTo(3);
        assertThat(quorum.path("presentVoting").asInt()).isEqualTo(3);
        assertThat(quorum.path("quorumMet").asBoolean()).isTrue();
        assertThat(quorum.path("quorumSource").asText()).isEqualTo("COMPUTED");
        assertThat(quorum.path("rosterUnlinked").asInt())
            .as("two external directors have no platform user")
            .isEqualTo(2);
        step("assertQuorumIsTheRosterNotTheSheet");

        // 8 ── raise the motion and bind it to the referral ------------------------------
        long resolutionId = raiseMotion(committeeId, meetingId);
        JsonNode bound = post(secretariatToken,
            "/api/v1/changes/" + changeId + "/committee/resolution",
            Map.of("resolutionId", resolutionId), HttpStatus.OK);
        assertThat(bound.path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(bound.path("resolutionId").asLong()).isEqualTo(resolutionId);
        step("raiseMotionAndBindItToTheReferral");

        // 9 ── vote: own vote, then two on behalf, one of them a recusal -----------------
        // The approver holds ADMIN:APPROVE but NOT ADMIN:WRITE, so mayRecordOnBehalf is false
        // for them — they can cast their own seat's vote and nobody else's. That asymmetry is
        // the point: it is what "voting" versus "recording a vote" means, persisted.
        post(approverToken, "/api/v1/committees/resolutions/" + resolutionId + "/votes",
            Map.of("vote", "FOR", "rationale", "Own vote " + TAG), HttpStatus.OK);
        post(secretariatToken, "/api/v1/committees/resolutions/" + resolutionId + "/votes",
            Map.of("memberName", "External Director A " + TAG, "vote", "FOR",
                   "rationale", "Recorded on behalf " + TAG), HttpStatus.OK);
        JsonNode afterVotes = post(secretariatToken,
            "/api/v1/committees/resolutions/" + resolutionId + "/votes",
            Map.of("memberName", "External Director B " + TAG, "vote", "RECUSED",
                   "rationale", "Conflict of interest declared " + TAG), HttpStatus.OK);

        // A recusal is not an abstention: it leaves the tally, it does not join it.
        assertThat(afterVotes.path("voteFor").asInt()).isEqualTo(2);
        assertThat(afterVotes.path("voteAgainst").asInt()).isZero();
        assertThat(afterVotes.path("voteAbstain").asInt()).isZero();
        assertThat(afterVotes.path("outcome").asText()).isEqualTo("APPROVED");
        assertThat(afterVotes.path("tallySource").asText())
            .as("F2-40 — a tally derived from vote rows is COMPUTED, never ASSERTED")
            .isEqualTo("COMPUTED");

        JsonNode voteRows = get(secretariatToken,
            "/api/v1/committees/resolutions/" + resolutionId + "/votes", HttpStatus.OK);
        assertThat(voteRows).hasSize(3);
        JsonNode ownVote = firstWhere(voteRows, "memberName", "SO Officer " + TAG);
        assertThat(ownVote.path("onBehalf").asInt())
            .as("F2-26 — a member with a linked user casts their own vote")
            .isZero();
        step("castOwnVoteThenRecordTwoOnBehalfIncludingARecusal");

        // 10 ── complete the meeting -----------------------------------------------------
        JsonNode completed = post(secretariatToken,
            "/api/admin/committees/meetings/" + meetingId + "/complete",
            Map.of("minutesEn", "Minutes of the golden-path CAB " + TAG,
                   "minutesAr", "محضر اجتماع لجنة التغيير " + TAG),
            HttpStatus.OK);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        step("completeMeeting");

        // 11 ── approve the minutes as a SECOND principal ---------------------------------
        // F2-39: whoever recorded the attendance may not approve the minutes resting on it.
        // Prove the separation is live before relying on it, so this step cannot pass by luck.
        ResponseEntity<String> selfApproval = exchange(secretariatToken, HttpMethod.POST,
            "/api/admin/committees/meetings/" + meetingId + "/approve-minutes", null);
        assertThat(selfApproval.getStatusCode())
            .as("F2-39 — the attendance recorder must not be the minutes approver")
            .isEqualTo(HttpStatus.CONFLICT);

        JsonNode approved = post(approverToken,
            "/api/admin/committees/meetings/" + meetingId + "/approve-minutes", null, HttpStatus.OK);
        assertThat(approved.path("status").asText()).isEqualTo("MINUTES_APPROVED");
        step("approveMinutesAsASecondPrincipal");

        // 11b ── F2-100: approving the minutes is what announces the decision -------------
        // Measured before the fix: resolutions 69 and 70 reached APPROVED through counted votes on
        // 2026-08-03, and AR-147 (COMMITTEE_RESOLUTION_APPROVED) last fired 2026-07-26 — so the
        // module's one publishEvent, in create(), never fired for either. Announcement is asserted
        // through the real HTTP stack here because no unit test can see the ordering: the vote
        // happened while the meeting was IN_PROGRESS, so the decision could only become binding
        // at this step.
        JsonNode decided = get(approverToken,
            "/api/v1/committees/resolutions/" + resolutionId, HttpStatus.OK);
        assertThat(decided.path("outcome").asText())
            .as("the counted votes decided the motion")
            .isEqualTo("APPROVED");
        assertThat(decided.path("decisionPublishedAt").isNull()
                || decided.path("decisionPublishedAt").asText().isEmpty())
            .as("F2-100 — approving the minutes must stamp the decision as announced; a null here "
                + "means the outcome event still reaches no automation")
            .isFalse();
        step("assertTheDecisionWasAnnouncedOnApprovedMinutes");

        // 12 ── the e-signature over the minutes -----------------------------------------
        JsonNode signatures = get(approverToken,
            "/api/v1/signatures/committee_meeting/" + meetingId, HttpStatus.OK);
        assertThat(signatures).as("approving minutes e-signs them").isNotEmpty();
        JsonNode signature = signatures.get(0);
        assertThat(signature.path("intent").asText()).isEqualTo("APPROVE");
        assertThat(signature.path("signerId").asLong())
            .as("the signer is the authenticated approver, never a body field")
            .isEqualTo(approverUserId);
        assertThat(signature.path("payloadHash").asText())
            .as("SHA-256 hex over the signed payload")
            .matches("[0-9a-f]{64}");
        step("verifyTheESignatureOverTheMinutes");

        // 13 ── and only now may the change advance ---------------------------------------
        JsonNode advanced = post(approverToken, "/api/v1/changes/" + changeId + "/transition",
            Map.of("target", "CAB_APPROVED",
                   "notes", "Carried by the committee resolution " + TAG),
            HttpStatus.OK);
        assertThat(advanced.path("status").asText()).isEqualTo("CAB_APPROVED");
        assertThat(advanced.path("cabDecisionAt").isNull())
            .as("a CAB decision must be stamped when a CAB actually took it")
            .isFalse();
        step("advanceTheChangeOnTheCommitteeDecision");

        // 14 ── the ledger recorded it ------------------------------------------------------
        // Named actions, not merely "not empty": F2-90 was found because the meeting had audit rows
        // (MEETING_ATTENDANCE) while the approval resting on them had none, so a non-emptiness
        // assertion would have passed over exactly the gap it was supposed to catch.
        assertThat(auditActionsFor("ChangeRequest", changeId))
            .as("the change's whole journey must be on the ledger, decision included")
            .contains("CREATE", "TRANSITION", "REFER_TO_COMMITTEE", "REFER_LINK_RESOLUTION");
        assertThat(auditActionsFor("CommitteeMeeting", meetingId))
            .as("F2-90 — MEETING_ATTENDANCE was the only meeting action the ledger had ever seen")
            .contains("MEETING_START", "MEETING_ATTENDANCE", "MEETING_COMPLETE", "MINUTES_APPROVE");

        // And the two principals are distinguishable in the ledger, which is what makes the
        // separation auditable rather than merely enforced.
        assertThat(auditActorsFor("ChangeRequest", changeId))
            .as("the raiser advanced it; a different principal decided it")
            .contains(SECRETARIAT, APPROVER);
        step("assertTheAuditLedgerRecordedTheDecision");

        // The walk itself is asserted, so a step skipped by an early return cannot pass.
        assertThat(COMPLETED_STEPS)
            .as("every declared golden-path step must have executed")
            .containsExactlyElementsOf(CommitteeGoldenPath.STEPS);
    }

    // ── the vocabulary, against the database that defines it ─────────────────────

    /**
     * The half of the committee-type vocabulary check that <b>only</b> a real database can make.
     *
     * <p>{@code CommitteeTypeVocabularyTest} ties every Java copy of the vocabulary together; nothing
     * in the unit suite can tie Java to {@code CK_com_type}, and that is the gap the drift lived in
     * for 177 migrations. A widened CHECK with an un-widened DTO silently refuses legal values (the
     * board-pack {@code audience} defect); a widened DTO with an un-widened CHECK turns a 400 into a
     * 500 (the defect this slice's first run hit). Both directions are asserted here.
     */
    @Test
    @DisplayName("the Java committee-type vocabulary equals CK_com_type exactly")
    void theJavaVocabularyEqualsTheDatabaseCheckConstraint() {
        String definition = jdbc.queryForObject("""
            SELECT cc.definition
              FROM sys.check_constraints cc
              JOIN sys.tables   t ON t.object_id = cc.parent_object_id
              JOIN sys.schemas  s ON s.schema_id = t.schema_id
             WHERE s.name = 'admin' AND t.name = 'committees' AND cc.name = 'CK_com_type'
            """, String.class);

        Set<String> inDatabase = new LinkedHashSet<>();
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("N'([A-Z_]+)'").matcher(definition);
        while (m.find()) inDatabase.add(m.group(1));

        assertThat(inDatabase)
            .as("parsed CK_com_type: %s", definition)
            .isNotEmpty();
        assertThat(inDatabase)
            .as("""
                CommitteeTypes.VOCABULARY and CK_com_type have diverged.
                  only in the database: %s   -> the DTO will refuse a legal type
                  only in Java:         %s   -> the DTO will accept a type the INSERT rejects with a 500
                Widen the CHECK in a migration and the constant in the same commit.""",
                minus(inDatabase, CommitteeTypes.VALUES),
                minus(CommitteeTypes.VALUES, inDatabase))
            .containsExactlyInAnyOrderElementsOf(CommitteeTypes.VALUES);

        // And the decision the vocabulary encodes, asserted against the database rather than a
        // constant: ADR-017 D-2 makes the CAB a role a tenant assigns, not a type it constitutes.
        assertThat(inDatabase).doesNotContain("CAB", "ECAB");
    }

    /**
     * An off-vocabulary committee type must be a 400 that names the field, not a 500. W1-5's first
     * run got {@code 500 "An unexpected database error occurred."} here, because the only statement
     * of what was legal lived in {@code CK_com_type}.
     */
    @Test
    @DisplayName("an off-vocabulary committee type is a 400 naming the field, not a 500")
    void anOffVocabularyCommitteeTypeIsRefusedAtTheBoundary() {
        secretariatToken = login(SECRETARIAT);
        ResponseEntity<String> res = exchange(secretariatToken, HttpMethod.POST,
            "/api/admin/committees", new LinkedHashMap<>(Map.of(
                "code", PREFIX + "-REJECT",
                "nameEn", "Should never be created " + TAG,
                "nameAr", "لا ينبغي إنشاؤها " + TAG,
                "committeeType", "CAB",              // a role, not a type — ADR-017 D-2
                "meetingCadence", "AD_HOC")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody())
            .as("the 400 must name the offending field, which is what @Valid's errors map is for")
            .contains("committeeType");

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin.committees WHERE code = ?", Integer.class, PREFIX + "-REJECT"))
            .as("a refused create must leave nothing behind")
            .isZero();
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    // ── the refusal path ─────────────────────────────────────────────────────────

    /**
     * F2-18 asks for a second test on the rejection / no-quorum path, and it is where the quorum
     * denominator earns its keep: one member present out of three voting seats is 33% against a 60%
     * charter, so the meeting is not quorate and the change must not move — even though a majority
     * of the votes that <em>were</em> cast said yes.
     */
    @Test
    @DisplayName("a non-quorate meeting cannot carry a change, however the votes fell")
    void aNonQuorateMeetingCannotCarryTheChange() {
        String token = login(SECRETARIAT);
        String approve = login(APPROVER);
        Long approverId = userIdOf(APPROVER);
        secretariatToken = token;
        approverToken = approve;
        approverUserId = approverId;

        long committeeId = constituteCommittee("CAB-NQ");
        long seatA = appoint(committeeId, "NQ Chair " + TAG, approverId, "Chair");
        appoint(committeeId, "NQ Director A " + TAG, null, "Member");
        appoint(committeeId, "NQ Director B " + TAG, null, "Member");

        long changeId = raiseChange();
        post(token, "/api/v1/changes/" + changeId + "/refer-to-committee",
            Map.of("committeeId", committeeId), HttpStatus.CREATED);

        long meetingId = openMeeting(committeeId);

        // One of three voting seats present. The other two are simply unmarked — absence needs
        // no mark, and it still counts in the denominator. That is the whole meaning of quorum,
        // and it is the assertion a caller-authored sheet could defeat before V319.
        JsonNode quorum = post(token,
            "/api/admin/committees/meetings/" + meetingId + "/attendance",
            Map.of("marks", List.of(Map.of("memberTermId", seatA, "present", true))),
            HttpStatus.OK);
        assertThat(quorum.path("totalVoting").asInt()).isEqualTo(3);
        assertThat(quorum.path("presentVoting").asInt()).isEqualTo(1);
        assertThat(quorum.path("unrecordedVoting").asInt())
            .as("an unmarked voting seat is absent, not excluded")
            .isEqualTo(2);
        assertThat(quorum.path("quorumMet").asBoolean()).isFalse();

        long resolutionId = raiseMotion(committeeId, meetingId);
        post(token, "/api/v1/changes/" + changeId + "/committee/resolution",
            Map.of("resolutionId", resolutionId), HttpStatus.OK);
        JsonNode voted = post(approve, "/api/v1/committees/resolutions/" + resolutionId + "/votes",
            Map.of("vote", "FOR", "rationale", "Sole attendee " + TAG), HttpStatus.OK);
        assertThat(voted.path("outcome").asText())
            .as("the motion carried on the votes cast")
            .isEqualTo("APPROVED");

        // The minutes are never approved, so the referral gate has nothing quorate to rest on.
        ResponseEntity<String> refused = exchange(approve, HttpMethod.POST,
            "/api/v1/changes/" + changeId + "/transition",
            Map.of("target", "CAB_APPROVED", "notes", "Should be refused " + TAG));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody())
            .as("the refusal must say which precondition failed")
            .contains("minutes");

        assertThat(statusOfChange(changeId))
            .as("a refused decision leaves the change where it was")
            .isNotEqualTo("CAB_APPROVED");
    }

    // ── golden-path building blocks ──────────────────────────────────────────────

    /**
     * ⚠ <b>The committee is constituted as {@code IT_STEERING}, not as a "CAB", and that is the
     * design rather than a workaround.</b>
     *
     * <p>This method's first draft posted {@code committeeType: "CAB"} and got a 500 from
     * {@code CK_com_type}. The obvious reading — "the vocabulary is missing CAB" — is wrong, and
     * ADR-017 D-2 says so in as many words: <i>"ITIL 4 supports this: the CAB is a role, not
     * necessarily a standing committee, so the platform must not assume a committee exists."</i> A
     * tenant nominates one of its real committees as the CAB through {@code committee_gate_map};
     * org 1's live CAB gate points at exactly this type, {@code COM-IT}. Adding {@code CAB} to the
     * vocabulary would have made the test pass by reversing the decision that unblocked the epic —
     * which is why {@code CommitteeTypeVocabularyTest} now pins its absence.
     *
     * <p>What the 500 <em>did</em> expose is a real defect, fixed in this slice: the DTO stated only
     * {@code @Size(max = 30)}, so the database CHECK was the sole enforcement and an off-vocabulary
     * type became "An unexpected database error occurred." rather than a 400 naming the field.
     */
    private long constituteCommittee(String suffix) {
        String code = PREFIX + "-" + suffix;
        JsonNode c = post(secretariatToken, "/api/admin/committees", new LinkedHashMap<>(Map.of(
            "code", code,
            "nameEn", "Golden-path change authority " + TAG,
            "nameAr", "سلطة اعتماد التغييرات — مسار مرجعي " + TAG,
            "committeeType", "IT_STEERING",
            "chairpersonName", "SO Officer " + TAG,
            "secretaryName", "Compliance Officer " + TAG,
            "meetingCadence", "AD_HOC",
            "quorumPct", 60)), HttpStatus.CREATED);
        return c.path("id").asLong();
    }

    private long appoint(long committeeId, String memberName, Long memberUserId, String role) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("committeeId", committeeId);
        body.put("memberName", memberName);
        body.put("memberUserId", memberUserId);
        body.put("role", role);
        body.put("isVotingMember", 1);
        body.put("isIndependent", 0);
        body.put("appointedAt", LocalDate.now().minusDays(30).toString());
        body.put("termStartsAt", LocalDate.now().minusDays(30).toString());
        body.put("termEndsAt", LocalDate.now().plusYears(2).toString());
        JsonNode t = post(secretariatToken, "/api/v1/committees/registry/terms", body, HttpStatus.OK);
        return t.path("id").asLong();
    }

    private JsonNode activeRoster(long committeeId) {
        return get(secretariatToken,
            "/api/v1/committees/registry/committees/" + committeeId + "/active-members",
            HttpStatus.OK);
    }

    /**
     * Raise a NORMAL/LOW change and walk it to {@code CAB_PENDING} — the state where it is genuinely
     * awaiting a CAB, which is the only honest starting point for testing whether a CAB decision
     * carries it.
     *
     * <p>All three hops here are <em>operational</em> ({@code CHANGE:WRITE}), taken by the raiser. The
     * fourth — {@code CAB_PENDING → CAB_APPROVED} — is a decision and needs {@code CHANGE:APPROVE},
     * so the approver takes it and nobody on this path ever holds both.
     *
     * <p>⚠ The walk matters for a second reason. F2-29 measured that a {@code WRITE}-only persona
     * could raise an EMERGENCY change and reach {@code EMERGENCY_APPROVED} <b>in one hop from
     * {@code DRAFT}</b>. This is the NORMAL branch of the same map, and it is the branch where the
     * state machine does hold: {@code DRAFT → CAB_APPROVED} is refused with a 400. Asserting the
     * long way round is what keeps the two branches distinguishable.
     */
    private long raiseChange() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("changeCode", PREFIX + "-" + System.nanoTime());
        body.put("titleEn", "Golden-path change " + TAG);
        body.put("titleAr", "تغيير المسار المرجعي " + TAG);
        body.put("descriptionEn", "Raised by the W1-5 golden-path integration test.");
        body.put("descriptionAr", "أُنشئ بواسطة اختبار التكامل للمسار المرجعي.");
        body.put("changeType", "NORMAL");
        body.put("riskLevel", "LOW");
        body.put("category", "APPLICATION");
        JsonNode c = post(secretariatToken, "/api/v1/changes", body, HttpStatus.CREATED);
        long changeId = c.path("id").asLong();
        assertThat(c.path("status").asText())
            .as("a change is born a draft — PM-C2's rule, and CM-C3's status-bypass fix")
            .isEqualTo("DRAFT");

        // A decision target cannot be reached by skipping the queue.
        ResponseEntity<String> shortcut = exchange(secretariatToken, HttpMethod.POST,
            "/api/v1/changes/" + changeId + "/transition", Map.of("target", "CAB_APPROVED"));
        assertThat(shortcut.getStatusCode())
            .as("DRAFT -> CAB_APPROVED must be refused; the CAB queue is not optional")
            .isEqualTo(HttpStatus.BAD_REQUEST);

        for (String target : List.of("SUBMITTED", "IMPACT_ASSESSED", "CAB_PENDING")) {
            JsonNode moved = post(secretariatToken, "/api/v1/changes/" + changeId + "/transition",
                Map.of("target", target, "notes", "Golden path " + TAG), HttpStatus.OK);
            assertThat(moved.path("status").asText()).isEqualTo(target);
        }
        return changeId;
    }

    private long openMeeting(long committeeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("committeeId", committeeId);
        body.put("titleEn", "Golden-path CAB session " + TAG);
        body.put("titleAr", "جلسة لجنة التغيير — مسار مرجعي " + TAG);
        body.put("scheduledAt", LocalDateTime.now().withNano(0).toString());
        body.put("locationOrLink", "Integration test");
        body.put("chairName", "SO Officer " + TAG);
        body.put("secretaryName", "Compliance Officer " + TAG);
        JsonNode m = post(secretariatToken, "/api/admin/committees/meetings", body, HttpStatus.CREATED);
        long meetingId = m.path("id").asLong();
        JsonNode started = post(secretariatToken,
            "/api/admin/committees/meetings/" + meetingId + "/start", null, HttpStatus.OK);
        assertThat(started.path("status").asText()).isEqualTo("IN_PROGRESS");
        return meetingId;
    }

    /**
     * Raise the motion — <b>undecided</b>, which S7a/V349 made possible.
     *
     * <p>⚠ <b>This method used to carry F2-89 as a comment and a workaround.</b>
     * {@code CK_resolution_outcome} permitted only four <em>decided</em> values, so to vote on a
     * motion in-platform you first had to record a decision for it and then hold the vote that
     * determined that decision; omitting the field was a 500. This test posted
     * {@code outcome: "DEFERRED"} as "the least-wrong placeholder" — and it was worse than
     * least-wrong, because {@code AR-152 (COMMITTEE_RESOLUTION_DEFERRED → re-table at the next
     * meeting)} is <b>enabled</b>, so the platform's own reference walkthrough for correct
     * committee governance emitted a false deferral on every run. Measured before the fix:
     * AR-152 {@code fire_count = 38} with <b>zero</b> deferred resolutions in the database.
     *
     * <p>The field is now omitted entirely and the motion is {@code PROPOSED}. The two assertions
     * below are what the workaround made unassertable: that a raised motion is not decided, and
     * that raising it announces nothing.
     */
    private long raiseMotion(long committeeId, long meetingId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("committeeId", committeeId);
        body.put("meetingId", meetingId);
        body.put("resolutionCode", PREFIX + "-RES-" + System.nanoTime());
        body.put("seq", 1);
        body.put("titleEn", "Approve the golden-path change " + TAG);
        body.put("titleAr", "الموافقة على تغيير المسار المرجعي " + TAG);
        body.put("bodyEn", "That the change be approved subject to the recorded conditions.");
        body.put("bodyAr", "الموافقة على التغيير وفق الشروط المسجلة.");
        body.put("confidentiality", "INTERNAL");
        // no `outcome` — F2-89/V349: a motion may be raised without a verdict
        JsonNode r = post(secretariatToken, "/api/v1/committees/resolutions", body, HttpStatus.OK);
        assertThat(r.path("outcome").asText())
            .as("F2-89 — a motion raised through the real HTTP stack must come back PROPOSED, not "
                + "carrying a verdict nobody voted on")
            .isEqualTo("PROPOSED");
        assertThat(r.path("decisionPublishedAt").isNull() || r.path("decisionPublishedAt").asText().isEmpty())
            .as("F2-100 — raising a motion must not announce a decision")
            .isTrue();
        assertThat(r.path("tallySource").asText())
            .as("F2-40 — a motion raised with no asserted numbers is COMPUTED, and only a COMPUTED "
                + "or unlabelled-and-empty tally may be voted on; ASSERTED would 409 on the first vote")
            .isEqualTo("COMPUTED");
        return r.path("id").asLong();
    }

    // ── HTTP plumbing ────────────────────────────────────────────────────────────

    private String login(String username) {
        ResponseEntity<String> res = rest.postForEntity("/api/auth/login",
            new HttpEntity<>(Map.of("username", username, "password", PASSWORD), jsonHeaders(null)),
            String.class);
        assertThat(res.getStatusCode())
            .as("login as %s — a 401 here usually means a lockout counter, not a wrong password",
                username)
            .isEqualTo(HttpStatus.OK);
        return readTree(res.getBody()).path("accessToken").asText();
    }

    private ResponseEntity<String> exchange(String token, HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, jsonHeaders(token)), String.class);
    }

    private JsonNode post(String token, String path, Object body, HttpStatus expected) {
        ResponseEntity<String> res = exchange(token, HttpMethod.POST, path, body);
        assertThat(res.getStatusCode()).as("POST %s -> %s", path, res.getBody()).isEqualTo(expected);
        return readTree(res.getBody());
    }

    private JsonNode get(String token, String path, HttpStatus expected) {
        ResponseEntity<String> res = exchange(token, HttpMethod.GET, path, null);
        assertThat(res.getStatusCode()).as("GET %s -> %s", path, res.getBody()).isEqualTo(expected);
        return readTree(res.getBody());
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    private JsonNode readTree(String body) {
        try {
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (Exception e) {
            throw new AssertionError("Response was not JSON: " + body, e);
        }
    }

    private static JsonNode firstWhere(JsonNode array, String field, String value) {
        for (JsonNode n : array) if (value.equals(n.path(field).asText())) return n;
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }

    // ── database reads (assertions the API does not expose) ──────────────────────

    private Long userIdOf(String username) {
        return jdbc.queryForObject(
            "SELECT id FROM iam.users WHERE username = ? AND is_deleted = 0", Long.class, username);
    }

    private String statusOfChange(long changeId) {
        return jdbc.queryForObject(
            "SELECT status FROM change_mgmt.change_requests WHERE id = ?", String.class, changeId);
    }

    /**
     * Read through the audit-log API rather than the table where possible; the ledger's entity
     * columns are not exposed as query parameters, so this one assertion goes to SQL. It is still
     * a real-database assertion, which is the point of the test.
     */
    private List<String> auditActionsFor(String entityType, long entityId) {
        // entity_type is the Java simple name — 'ChangeRequest', 'CommitteeMeeting' — not a
        // screaming-snake constant. Filtering on it is not optional: ids collide across entity
        // types, so entity_id alone matches unrelated rows and would make any assertion here pass.
        return jdbc.queryForList(
            "SELECT action_type FROM platform.audit_log WHERE entity_type = ? AND entity_id = ?",
            String.class, entityType, entityId);
    }

    private List<String> auditActorsFor(String entityType, long entityId) {
        return jdbc.queryForList(
            "SELECT user_username FROM platform.audit_log WHERE entity_type = ? AND entity_id = ?",
            String.class, entityType, entityId);
    }

    // ── teardown of the PREVIOUS run ─────────────────────────────────────────────

    /**
     * Deletes what a prior run of this class left, scoped to {@link CommitteeGoldenPath#CODE_PREFIX}
     * so it cannot reach a seeded committee or a real change. Runs on the way IN, not out, because
     * W1-5 asks for the worked decision to be retained as demo evidence — so dev ends up holding
     * exactly one complete e-signed committee decision rather than one per run.
     *
     * <p>{@code CHARINDEX}, never {@code LIKE '[...]%'}: brackets are a character class in T-SQL and
     * the repo has been bitten by that deleting real rows.
     */
    private void purgePreviousRun() {
        List<Long> committees = jdbc.queryForList(
            "SELECT id FROM admin.committees WHERE code LIKE ? + '%'", Long.class, PREFIX);
        List<Long> changes = jdbc.queryForList(
            "SELECT id FROM change_mgmt.change_requests WHERE change_code LIKE ? + '%'",
            Long.class, PREFIX);
        if (committees.isEmpty() && changes.isEmpty()) return;

        List<String> statements = new ArrayList<>();
        if (!committees.isEmpty()) {
            String ids = inList(committees);
            statements.add("DELETE FROM admin.committee_resolution_vote WHERE resolution_id IN "
                + "(SELECT id FROM admin.committee_resolutions WHERE committee_id IN " + ids + ")");
            statements.add("DELETE FROM admin.committee_resolutions WHERE committee_id IN " + ids);
            // committee_meeting_id, not meeting_id — this table names its FK differently from the
            // four beside it (resolution_vote, papers and action_items all say meeting_id).
            statements.add("DELETE FROM admin.committee_meeting_attendee WHERE committee_meeting_id IN "
                + "(SELECT id FROM admin.committee_meetings WHERE committee_id IN " + ids + ")");
            statements.add("DELETE FROM admin.committee_meeting_papers WHERE meeting_id IN "
                + "(SELECT id FROM admin.committee_meetings WHERE committee_id IN " + ids + ")");
            statements.add("DELETE FROM admin.committee_action_items WHERE committee_id IN " + ids);
            statements.add("DELETE FROM platform.signatures WHERE entity_type = 'committee_meeting' "
                + "AND entity_id IN (SELECT id FROM admin.committee_meetings WHERE committee_id IN "
                + ids + ")");
            statements.add("DELETE FROM admin.committee_meetings WHERE committee_id IN " + ids);
            statements.add("DELETE FROM admin.committee_referral WHERE committee_id IN " + ids);
            statements.add("DELETE FROM admin.committee_member_terms WHERE committee_id IN " + ids);
            statements.add("DELETE FROM admin.committees WHERE id IN " + ids);
        }
        if (!changes.isEmpty()) {
            String ids = inList(changes);
            for (String t : List.of("change_approvals", "change_tasks", "change_links", "change_comments")) {
                statements.add("DELETE FROM change_mgmt." + t + " WHERE change_request_id IN " + ids);
            }
            statements.add("DELETE FROM change_mgmt.change_requests WHERE id IN " + ids);
        }
        statements.forEach(jdbc::update);
    }

    private static String inList(List<Long> ids) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i).longValue());   // longs only — never user input
        }
        return sb.append(')').toString();
    }

    private void step(String name) {
        assertThat(CommitteeGoldenPath.STEPS)
            .as("undeclared step '%s' — add it to CommitteeGoldenPath.STEPS", name)
            .contains(name);
        COMPLETED_STEPS.add(name);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
