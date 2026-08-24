package com.tss.grc.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-22a mass-assignment RATCHET — a write endpoint that binds a JPA entity straight from the
 * request body lets the caller set every field the entity has, including the ones the server
 * is supposed to own.
 *
 * <p><b>Why this is a security ratchet and not a style one.</b> The platform has found this
 * defect by hand four separate times and each instance was a real authority bypass: <b>DF-3 /
 * F2-21</b> (committee vote provenance — the caller chose the voter), <b>DF-4 / F2-24</b> (CAB
 * approval provenance — a requester could approve their own change by submitting another
 * name), <b>F2-25</b> (committee appointment bound the entity, so {@code organisationId} came
 * from the body), and the roster seat link. Four hand-found instances in one class is the
 * signature of a systemic problem, which is what this test measures.
 *
 * <p><b>What the caller could set on EVERY entity-bound endpoint before this slice.</b>
 * {@link BaseEntity} is the mapped superclass nearly every entity extends, and none of its
 * nine server-owned fields carried any Jackson protection:
 * <ul>
 *   <li>{@code id} — retarget a "create" at an existing row</li>
 *   <li>{@code createdBy} / {@code updatedBy} — <b>forge audit attribution</b>, the same
 *       defect as DF-3/DF-4 but on every entity rather than two</li>
 *   <li>{@code createdAt} / {@code updatedAt} — forge timestamps, including backdating</li>
 *   <li>{@code isDeleted} / {@code deletedAt} / {@code deletedBy} — soft-delete or resurrect
 *       a record through an ordinary update, bypassing whatever guards the delete endpoint
 *       has, and forge who did it</li>
 *   <li>{@code rowVersion} — defeat optimistic locking</li>
 * </ul>
 *
 * <p><b>The mitigation is NOT {@code @JsonIgnore}, and that correction matters.</b> The plan
 * specified "{@code @JsonIgnore} on {@code id}/{@code organisationId} in a mapped superclass".
 * {@code @JsonIgnore} suppresses a property in <em>both</em> directions, so it would have
 * stripped {@code id} from every API <em>response</em> — every list, every detail page and
 * every row action in the frontend resolves records by id, so the platform's entire UI would
 * have broken. {@link com.fasterxml.jackson.annotation.JsonProperty} with
 * {@code access = READ_ONLY} is the correct tool: serialise as before, never deserialise.
 * {@link #serverOwnedFieldsAreStillSerialised()} pins that distinction so nobody "simplifies"
 * it back to {@code @JsonIgnore}.
 *
 * <h2>⚠⚠ F2-206 — the SCOPE of everything above, corrected</h2>
 *
 * <p>The paragraphs above say the nine fields "can no longer arrive from a caller". That was
 * true of <b>{@link BaseEntity} subclasses only</b>, and this class could not tell, because it
 * proves the claim through {@code class Probe extends BaseEntity}. Measured 2026-08-13:
 * <b>252 of 407 entities do not extend {@code BaseEntity}, none of them annotated its own
 * {@code id}, and that is 177 of the 214 endpoints this ratchet counts.</b>
 *
 * <p>It was live, not theoretical. {@code POST /api/v1/privacy/ropa} carrying {@code {"id": n}}
 * answered <b>201 Created</b> and overwrote processing activity {@code n} — a PDPL Article 31
 * record — nulling every column the body omitted, moving the lawful basis from
 * {@code LEGAL_OBLIGATION} to {@code CONSENT} and clearing the Article 25 special-category flag.
 *
 * <p>🔑 <b>An invariant enforced by a superclass covers exactly its subclasses, and a probe that
 * extends that superclass can only ever measure the covered half.</b> {@link #riskProfileIsRecordedForF2_22b()}
 * could not surface it either: it breaks its field walk at {@code BaseEntity} precisely because
 * those fields are {@code READ_ONLY}, and {@code id} is in none of its risk vocabularies — so
 * {@code id} was structurally unrankable by the very scan built to rank this defect.
 *
 * <p>{@code ServerOwnedFieldsModule} generalises the fix to every mapped type and
 * {@link ServerOwnedFieldsInvariantTest} asserts it over the whole population with a negative
 * control, so nothing here needs a per-entity change. <b>The three tests below are unchanged and
 * still correct — read them as claims about {@code BaseEntity}, which is what they test.</b>
 *
 * <h2>⚠⚠ F2-215 — and that correction was itself only one ninth of the way</h2>
 *
 * <p>F2-206 widened the population from "{@code BaseEntity} subclasses" to "every mapped type"
 * <b>for {@code id}</b>. The other eight fields listed above stayed exactly as scoped as the
 * paragraph it was correcting. Measured 2026-08-15: of the 259 entities outside
 * {@code BaseEntity}, 209 declare their own {@code isDeleted}, 222 their own {@code createdAt},
 * 111 their own {@code createdBy} — <b>960 declarations, none annotated</b>, reachable on
 * <b>170 of the 214 endpoints this ratchet counts</b>. Proved live on
 * {@code POST /api/v1/controls}: {@code createdBy} stored as the caller sent it,
 * {@code createdAt} backdated to 2001, and {@code isDeleted:true} creating a row invisible to
 * every query while the UNIQUE index still saw it.
 *
 * <p>🔑 <b>Generalising an invariant one field at a time leaves the remainder looking covered,
 * because the mechanism that covers them is visibly present.</b> The module now mirrors
 * {@code BaseEntity}'s field list and a gate asserts the two are equal in both directions, so
 * the next field cannot drift apart silently.
 *
 * @see ServerOwnedFieldsInvariantTest F2-206/F2-215 — all nine fields over all 433 entities
 * @see ControllerAuthorizationRatchetTest sibling ratchet — whether a write is guarded at all
 * @see ExportAuthorizationRatchetTest sibling ratchet — whether a file export requires EXPORT
 */
class MassAssignmentRatchetTest {

    /**
     * Write endpoints binding a JPA entity directly from the request body.
     *
     * <p>**MUST only ever decrease.** Measured 2026-07-26. The plan's figure was 216, counted
     * by grep over {@code @RequestBody} occurrences; reflection over the resolved parameter
     * types is the authority and it counts endpoints whose body type is genuinely an
     * {@code @Entity}. Lower this as F2-22b converts endpoints to DTOs — do not raise it to
     * make a new endpoint pass. A new write endpoint should take a request record with only
     * the fields the caller may legitimately set (see {@code MemberTermRequest} from F2-25 and
     * {@code CabVoteRequest} from DF-4 as the worked examples).
     *
     * <p>This count is deliberately NOT zero-target in this slice. Converting every endpoint
     * is F2-22b (sized L); the {@link BaseEntity} annotations shipped here remove the
     * platform-wide field exposure underneath all of them in one place, which is the cheap
     * 90% — but a per-entity field like {@code organisationId} or {@code status} still binds,
     * so the count still matters.
     */
    private static final int BASELINE_ENTITY_BOUND_WRITES = 214;

    /**
     * F2-217 — CRIT endpoints the widened authority vocabulary surfaced, still unadjudicated.
     *
     * <p><b>MUST only ever decrease.</b> Measured 2026-08-15, after this slice adjudicated the six
     * it proved live. The number went UP from an apparent zero and that is the point: the previous
     * zero was over the population a literal name list could see, and
     * <b>a hard zero over a population the instrument cannot see is the most reassuring possible
     * artefact</b> (F2-205). The question the assertion asks — has every authority-bearing
     * entity-bound write been read? — was right; the ANSWER was under-measured, so the figure moves
     * and says so, rather than the assertion being re-scoped to keep it at zero.
     *
     * <p>Each row is printed to {@code target/mass-assignment-risk.txt} marked NEEDS-VERDICT with
     * the fields that put it there, so this is a work list rather than a count. Read the service,
     * record CLOSED / INERT / BY DESIGN in {@link #ADJUDICATED}, lower this in the same commit.
     *
     * <p>⚠ F2-330 — this sat at 48 while NINE counted rows were already CLOSED or INERT
     * service-side (F2-220 / F2-231 / F2-233 / F2-297 / F2-328 / F2-329), because a CEILING
     * does not force the census update its own contract asks for. A closed defect printing
     * NEEDS-VERDICT is worse than a missing row (F2-250): it sends the next reviewer to
     * re-read a guarded path. Audited and adjudicated the nine plus F2-330's own BcpPlan
     * fix, re-derived 48 → 38. Read each service before adding a verdict (F2-188).
     */
    private static final int BASELINE_UNADJUDICATED_NEW_CRIT = 29;

    @Test
    void noNewEntityBoundWriteEndpoints() {
        List<String> bound = scanEntityBoundWrites();
        Collections.sort(bound);
        System.out.println("[F2-22a mass-assignment ratchet] entity-bound write endpoints = " + bound.size());
        bound.forEach(s -> System.out.println("  - " + s));

        assertThat(bound.size())
            .as("A write endpoint binds a JPA entity straight from the request body, so the "
                + "caller can set any field the entity has. Take a request DTO carrying only "
                + "the fields a caller may set — do not raise this baseline. This class has "
                + "produced four separate authority bypasses by hand (DF-3, DF-4, F2-25, the "
                + "roster link). Offenders are printed above.")
            .isLessThanOrEqualTo(BASELINE_ENTITY_BOUND_WRITES);
    }

    /**
     * The server-owned fields on {@link BaseEntity} must not be settable from JSON.
     *
     * <p>Asserted through a real {@link ObjectMapper} round-trip rather than by looking for the
     * annotation, because what matters is Jackson's behaviour, not which annotation produced it.
     *
     * <p>⚠ F2-206: this is the {@code BaseEntity} half of F2-22a, not a platform-wide half. The
     * previous wording here — "whatever any individual endpoint does with its body, these nine
     * fields can no longer arrive from a caller" — was the claim a reader would have relied on,
     * and it did not hold for the 259 entities that do not extend {@code BaseEntity}. The probe
     * below extends it, so this test can only ever speak for the types that do.
     * {@link ServerOwnedFieldsInvariantTest} makes the platform-wide claim and, since F2-215,
     * proves it for all nine fields over every {@code @Entity} rather than for {@code id} alone.
     */
    @Test
    void serverOwnedFieldsCannotBeSetFromJson() throws Exception {
        // JavaTimeModule mirrors what Spring Boot auto-registers; a bare mapper cannot read
        // LocalDateTime at all, which would mask the very binding this test is about.
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String hostile = """
            {
              "id": 999999,
              "createdBy": 1,
              "updatedBy": 1,
              "createdAt": "2001-01-01T00:00:00",
              "updatedAt": "2001-01-01T00:00:00",
              "isDeleted": true,
              "deletedAt": "2001-01-01T00:00:00",
              "deletedBy": 1
            }
            """;

        Probe p = mapper.readValue(hostile, Probe.class);

        assertThat(p.getId()).as("id must not bind — a create could retarget an existing row").isNull();
        assertThat(p.getCreatedBy()).as("createdBy must not bind — that is forged audit attribution").isNull();
        assertThat(p.getUpdatedBy()).as("updatedBy must not bind — that is forged audit attribution").isNull();
        assertThat(p.getCreatedAt()).as("createdAt must not bind — backdating a record").isNull();
        assertThat(p.getUpdatedAt()).as("updatedAt must not bind").isNull();
        assertThat(p.getDeletedAt()).as("deletedAt must not bind — forged deletion provenance").isNull();
        assertThat(p.getDeletedBy()).as("deletedBy must not bind — forged deletion provenance").isNull();
        assertThat(p.getIsDeleted())
            .as("isDeleted must not bind — an ordinary update could soft-delete or resurrect a "
                + "record, bypassing the delete endpoint's own guard")
            .isFalse();
    }

    /**
     * ...and they must still be SERIALISED. This is the test that stops the fix regressing
     * into {@code @JsonIgnore}: the whole frontend resolves records by {@code id}, so a
     * mitigation that hid it from responses would be far worse than the vulnerability.
     */
    @Test
    void serverOwnedFieldsAreStillSerialised() throws Exception {
        Probe p = new Probe();
        p.setId(42L);
        p.setCreatedBy(7L);
        p.setIsDeleted(false);

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(p);

        assertThat(json).as("id must still be serialised — the UI resolves every record by it")
            .contains("\"id\":42");
        assertThat(json).as("createdBy must still be readable — it is audit evidence")
            .contains("\"createdBy\":7");
    }

    /**
     * F2-22b's work list: for each entity-bound endpoint, which <em>authority-bearing</em> fields
     * are still settable from the body now that {@link BaseEntity}'s nine are not.
     *
     * <p>F2-22a closed the fields every entity shares. What remains is per-entity and is where the
     * four hand-found bypasses actually lived: {@code organisationId} (cross-tenant write),
     * {@code status} (state-machine bypass — reach an approved state without the approval
     * endpoint's guard), {@code approvedBy}/{@code signedBy}/{@code verifiedBy} (forged authority,
     * DF-3 and DF-4 exactly), and {@code vote*}/{@code quorum*} (forged governance outcome, F2-28's
     * class). Ranking 214 endpoints by hand is how the first four were found — one at a time, over
     * months — so this emits the ranking instead.
     *
     * <p>Written to {@code target/mass-assignment-risk.txt}, highest-risk first. It asserts only
     * that the profile is non-empty: the count that must not grow is
     * {@link #noNewEntityBoundWriteEndpoints()}, and which endpoints get DTOs first is a judgement
     * this file informs rather than makes.
     */
    @Test
    void riskProfileIsRecordedForF2_22b() throws Exception {
        List<Ranked> ranked = rankEntityBoundWrites();
        Path out = Path.of("target", "mass-assignment-risk.txt");
        Files.createDirectories(out.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# F2-22b mass-assignment risk profile — regenerated by this test.");
        lines.add("#");
        lines.add("# READ THE ADJUDICATION COLUMN BEFORE ACTING ON A TIER.");
        lines.add("# This scan measures what JACKSON WILL BIND, which is not the same as what the");
        lines.add("# service will WRITE. Tranches (ii)-(iv) fixed fields with @JsonProperty(READ_ONLY),");
        lines.add("# which this scan can see, so those rows left CRIT (54 -> 18). Tranches (v)-(vii)");
        lines.add("# fixed theirs in the SERVICE — a whitelist, a scrub, or a reject — which this scan");
        lines.add("# CANNOT see, so those rows still read CRIT while being closed. The tier count is");
        lines.add("# therefore NOT a progress metric; the adjudication column is.");
        lines.add("#");
        lines.add("# ADJUDICATED: verdict recorded from reading the service (F2-22b iv-vii, F2-217)");
        lines.add("# (blank)    : not yet adjudicated — read the service before annotating anything");
        lines.add("#");
        lines.add("# F2-217 widened the authority vocabulary from a literal name list to a shape, and");
        lines.add("# stopped `tenant` outranking `state`. Rows marked NEEDS-VERDICT below are the ones");
        lines.add("# that surfaced: real authority fields (acApprovedBy, reviewer, reviewedAt, closedAt,");
        lines.add("# approverName, publishedAt, ratifiedAt ...) that the old list did not spell. They are");
        lines.add("# a WORK LIST, not a tolerated set — BASELINE_UNADJUDICATED_NEW_CRIT holds the count.");
        lines.add("#");
        for (Ranked r : ranked) {
            String verdict = ADJUDICATED.getOrDefault(r.site,
                "CRIT".equals(r.tier) ? "NEEDS-VERDICT (F2-217 surfaced) — read the service" : "");
            lines.add(String.format("%-6s %-64s %-46s %s",
                r.tier, r.site, String.join(",", r.fields), verdict));
        }
        Files.write(out, lines);

        // ── Leg 1: the population the PRE-F2-217 instrument could see stays at a hard zero. ──
        List<String> unadjudicatedLegacy = ranked.stream()
            .filter(r -> r.visibleBeforeF2_217 && !ADJUDICATED.containsKey(r.site))
            .map(Ranked::site).sorted().toList();
        System.out.println("[F2-22b risk profile] pre-F2-217 CRIT rows with no verdict = " + unadjudicatedLegacy);
        assertThat(unadjudicatedLegacy)
            .as("Every endpoint the pre-F2-217 ladder ranked CRIT has been read service-by-service "
                + "across tranches (iv)-(vii). A new one here means a new entity-bound write "
                + "appeared — read its service and record the verdict rather than annotating the "
                + "entity blind, which is the F2-40 trap that would break a designed assertion.")
            .isEmpty();

        // ── Leg 2: what widening the vocabulary surfaced. Decrease-only, with the reason. ──
        long newCrit = ranked.stream()
            .filter(r -> "CRIT".equals(r.tier) && !r.visibleBeforeF2_217 && !ADJUDICATED.containsKey(r.site))
            .count();
        System.out.println("[F2-217] CRIT rows surfaced by the widened vocabulary, still unadjudicated = " + newCrit);
        assertThat(newCrit)
            .as("F2-217 surfaced authority-bearing entity-bound writes the literal name list could "
                + "not see, and adjudicated the six it proved live (audit_lc). The rest are a "
                + "measured work list in target/mass-assignment-risk.txt — read the service, record "
                + "the verdict, LOWER this number in the same commit. It may never be raised: a "
                + "ceiling left in place after a sweep makes a tolerated row indistinguishable from "
                + "a reviewed one (house rule 3).")
            .isLessThanOrEqualTo(BASELINE_UNADJUDICATED_NEW_CRIT);

        Map<String, Long> byTier = new TreeMap<>();
        for (Ranked r : ranked) {
            byTier.merge(r.tier, 1L, Long::sum);
        }
        System.out.println("[F2-22b risk profile] endpoints by tier = " + byTier);
        System.out.println("[F2-22b risk profile] wrote " + out.toAbsolutePath());
        ranked.stream().filter(r -> !"LOW".equals(r.tier)).limit(60)
              .forEach(r -> System.out.println("  " + r.tier + " " + r.site + " -> " + r.fields));

        assertThat(ranked)
            .as("The risk profile is empty, which means the scan broke — not that 214 "
                + "entity-bound endpoints expose nothing.")
            .isNotEmpty();
    }

    /**
     * F2-217 — the vocabulary recognises the names the literal list did not spell.
     *
     * <p>⚠ This leg exists because {@link #BASELINE_UNADJUDICATED_NEW_CRIT} is a CEILING, and a
     * ceiling cannot see a detector that stopped matching: narrowing {@link #AUTHORITY_SHAPE} back
     * to its lower-case-only first draft makes the count go DOWN, which a {@code <=} assertion
     * reads as progress. <b>An anti-vacuity guard has to know what the wrong answer looks like</b>
     * (F2-215). The 24 names below are the measured population, so a pattern that drops any of
     * them fails here rather than quietly shrinking the work list.
     *
     * <p>The negative half matters as much: a shape wide enough to catch {@code createdAt} or
     * {@code lastUpdated} would re-tier most of the platform and make the profile useless, which is
     * a different way of not being read.
     */
    @Test
    void theAuthorityVocabularyRecognisesTheNamesTheLiteralListMissed() {
        List<String> measuredOnRealEntities = List.of(
            "acApprovedAt", "acApprovedBy", "approverName", "attestedAt", "closedAt",
            "counterpartySigner", "escalatedAt", "integrityVerifiedAt", "lastAttestedAt",
            "lastReviewedAt", "lastReviewedBy", "loiSignedAt", "pirReviewedAt", "pirReviewedBy",
            "publishedAt", "ratificationEscalatedAt", "ratifiedAt", "rejectedAt", "reviewedAt",
            "reviewer", "reviewerId", "reviewerName", "reviewerUserId", "signerName",
            // F2-248 — the two agent nouns F2-217's own shape did not spell. Both are the sole
            // record of who performed an act, and both sat in the LOW tier because of it.
            // The camelCase-suffixed forms are here too: a rule anchored only at the start of the
            // name would miss `lastAuthorId`, and missing one silently shrinks the work list.
            "author", "authorId", "authorName", "actor", "actorId", "actorName",
            "lastAuthorId", "caseActorId", "reviewActorName",
            // ⚠ The mutation harness found this omission: I added the two agent nouns and forgot
            // the VERB, so deleting `[Cc]irculated` from AUTHORITY_SHAPE dropped the committee-paper
            // row out of CRIT — which lowers the count, and a `<=` ceiling reads that as progress.
            // That is the exact failure mode F2-217 wrote this leg for, reintroduced one stem later.
            "circulatedBy", "circulatedAt");

        assertThat(measuredOnRealEntities).allSatisfy(n ->
            assertThat(isAuthorityField(n))
                .as("'%s' is an authority act or actor on a real entity in this codebase, and the "
                    + "pre-F2-217 literal list did not spell it. If this fails the pattern has "
                    + "narrowed and the CRIT population is silently shrinking.", n)
                .isTrue());

        assertThat(AUTHORITY_PRE_F2_217).allSatisfy(n ->
            assertThat(isAuthorityField(n))
                .as("'%s' was already in the vocabulary; widening must not drop it", n).isTrue());

        List<String> notAuthority = List.of(
            "createdAt", "updatedAt", "lastUpdated", "deletedAt", "titleEn", "status",
            "organisationId", "targetDate", "hours", "externalId", "engagementCode", "severity",
            // F2-248 — the near-misses that keep `author`/`actor` from being a blanket widening.
            // `authority`/`actionType` share a prefix; `authorRole`/`actorRole` name a role rather
            // than an identity, so the tail group excludes them. The last three are the reason
            // SHORT_AGENT_SHAPE exists at all: `actor` is a SUBSTRING of `factor`, `contractor` and
            // `benefactor`, and the first draft — which reused AGENT_SHAPE's `(?:.*[a-z0-9])?`
            // prefix — paired `factorName` on its very first run. If any of these starts pairing,
            // the camelCase anchor has been dropped and the profile is about to re-tier on nothing.
            "authority", "authorityName", "authorRole", "actorRole", "actionType",
            "factorName", "contractorName", "benefactorId");
        assertThat(notAuthority).allSatisfy(n ->
            assertThat(isAuthorityField(n))
                .as("'%s' is not an authority field. A shape this wide would re-tier the platform "
                    + "and make the profile unreadable, which is its own failure mode.", n)
                .isFalse());
    }

    /**
     * F2-217 — {@code organisationId} may never be the reason a bindable state field is hidden.
     *
     * <p>The old ladder put {@code tenant} above {@code state}, so 145 of the 214 endpoints ranked
     * TENANT while also binding {@code status}/{@code approvalStatus}/{@code workflowStatus}, and
     * the STATE tier read <b>4</b>. Reverting the ladder lowers the CRIT count too, so the ceiling
     * above cannot catch it either — this leg asserts the SHAPE instead of a number.
     */
    /**
     * F2-217 — binding an authority or outcome field is sufficient for CRIT, on its own.
     *
     * <p>⚠ Found by the mutation harness: restoring only the {@code tenant &&} half of the old
     * ladder SURVIVED every other leg. It lowers the CRIT count, which the {@code <=} ceiling reads
     * as progress; it leaves no TENANT row binding a state field, so the masking leg is satisfied;
     * and it quietly demotes the three endpoints that bind an approval field on a body with no
     * {@code organisationId} — {@code DpiaController#addMitigation}/{@code #addReview} and
     * {@code KycController#recordHit}, the old HIGH tier. 🔑 <b>Two shape assertions that each look
     * complete can still leave the predicate between them free to move.</b>
     */
    @Test
    void anEndpointBindingAnAuthorityOrOutcomeFieldIsAlwaysRankedCrit() {
        List<String> demoted = rankEntityBoundWrites().stream()
            .filter(r -> !"CRIT".equals(r.tier))
            .filter(r -> r.fields.stream().anyMatch(f -> isAuthorityField(f) || OUTCOME.contains(f)))
            .map(Ranked::site).sorted().toList();

        assertThat(demoted)
            .as("These endpoints let a caller set who approved / reviewed / signed a record, or the "
                + "outcome itself — DF-3 and DF-4's defect exactly — and are ranked below CRIT. "
                + "Nothing else about the body reduces that: not a stamped tenant, not a whitelist "
                + "in the service. The verdict for a service that overwrites the field is INERT, "
                + "recorded in ADJUDICATED; it is not a lower tier.")
            .isEmpty();
    }

    /**
     * F2-248 — a purpose-built request type may not declare who performed the act either.
     *
     * <h2>Why this leg exists beside the entity-bound ranking</h2>
     *
     * <p>Everything above measures endpoints that bind an {@code @Entity}. The fix for such an
     * endpoint is to bind a narrow record instead (F2-213's structural direction, and what F2-248
     * did for the change-comment and ethics-action creates) — which moves it <b>out of that
     * population entirely</b>. So without this leg, the strongest available fix is also the one that
     * makes the endpoint invisible: re-add {@code authorName} to {@code ChangeCommentRequest} and
     * nothing above would notice. 🔑 <b>A gate whose population is "endpoints that bind an entity"
     * cannot see a DTO that reintroduces the defect, and the recommended fix is what moves an
     * endpoint across that boundary.</b>
     *
     * <h2>Why the vocabulary is not copied</h2>
     *
     * <p>This leg calls {@link #isAuthorityField(String)} directly. Writing a second agent-noun list
     * in a new gate class would commit the exact defect F2-248 is fixing — two vocabularies, nobody
     * maintaining the agreement (F2-177) — and the whole finding is that F2-217's shape was still a
     * list. One declaration, two readers.
     *
     * <h2>⚠⚠ Why this is a CENSUS and not a hard zero — my own first draft got it wrong</h2>
     *
     * <p>I wrote it as a hard zero with an empty exemption map. It reported <b>28</b> types, and
     * reading the list showed the assertion was <b>wrong rather than the code</b>: a large share are
     * <b>round-trip</b> DTOs — {@code BoardPackDto}, {@code CapaTaskDto}, {@code DsrCaseDto},
     * {@code NcaEccFilingDto} — used for the response as well as the request. Such a type <b>must</b>
     * declare {@code decidedBy}/{@code verifiedBy} in order to RETURN it, and whether the write path
     * honours the inbound value is a question about the SERVICE, which
     * {@code ServerStampedFieldContractTest} already asks. 🔑 <b>The question "does a bound type
     * declare an actor field?" conflates inbound-only types with round-trip ones, so a zero over it
     * would fail on correct code — the one thing a gate must never do (F2-214).</b>
     *
     * <p>So this is F2-204's rule applied to my own leg: when a figure cannot honestly be zero, ask
     * whether the QUESTION was wrong. It is a <b>decrease-only census with no verdicts</b> (F2-188 —
     * twenty-eight fluent reasons for unread code is the artefact that measured five-of-five false),
     * and the enforceable half is per-type, in the behaviour tests beside each fix:
     * {@code ChangeCommentProvenanceTest#theRequestTypeCannotCarryAnAuthorAtAll} and
     * {@code EthicsCaseServiceTest} assert by reflection that the two types F2-248 built declare no
     * actor at all — which is the shape a future reader would otherwise undo without any gate
     * noticing.
     */
    @Test
    void noRequestDtoDeclaresWhoPerformedTheAct() {
        Map<String, String> found = new TreeMap<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!isWriteMapping(m)) {
                    continue;
                }
                for (Parameter param : m.getParameters()) {
                    Class<?> type = param.getType();
                    // Entity-bound writes are the ranking's job, above. A Map body carries no
                    // declared field to inspect, and F2-236 closed that shape by reading the call
                    // sites instead — a scalar or Map parameter is measured there, not here.
                    if (!hasRequestBody(param) || type.isAnnotationPresent(Entity.class)
                        || Map.class.isAssignableFrom(type) || type.getName().startsWith("java.")) {
                        continue;
                    }
                    List<String> actorFields = declaredNamesOf(type).stream()
                        .filter(MassAssignmentRatchetTest::isAuthorityField)
                        .sorted().toList();
                    if (!actorFields.isEmpty()) {
                        found.put(type.getSimpleName(), String.join(",", actorFields));
                    }
                }
            }
        }

        System.out.println("[F2-248] bound request types declaring an authority/actor field = "
            + found.size() + "  (a WORK LIST — read the service, then lower the figure)");
        found.forEach((k, v) -> System.out.println("    NEEDS-READING  " + k + " -> " + v));

        assertThat(found.size())
            .as("A bound request type declaring who approved / reviewed / authored / performed the "
              + "act is a LOCATOR, not yet a verdict: some of these are round-trip DTOs that must "
              + "declare the field in order to RETURN it, and whether the write path honours the "
              + "inbound value is ServerStampedFieldContractTest's question. Read the service, then "
              + "either take the actor from the principal and delete the field — a field the bound "
              + "type does not declare can never arrive (F2-233) — or record why the named party is "
              + "genuinely a third party. Lower this figure in the same commit; it may never be "
              + "raised (house rule 3).")
            .isLessThanOrEqualTo(BASELINE_ACTOR_BEARING_REQUEST_TYPES);

        // Anti-vacuity, and the floor sits above the largest WRONG answer rather than above zero
        // (F2-215). The wrong answers here are: the scan resolves no controllers (0), or the
        // vocabulary stops matching (which would ALSO look like progress against the ceiling above).
        assertThat(found.size())
            .as("a scan that stops resolving reports few types, and few reads exactly like clean")
            .isGreaterThan(15);
    }

    /**
     * F2-248 — bound request types that let the caller name the actor.
     *
     * <p>Decrease-only. Measured 28 on 2026-08-19, immediately after this slice took the two it
     * created out of the population by construction. Deliberately NOT accompanied by 28 reasons
     * (F2-188).
     */
    private static final int BASELINE_ACTOR_BEARING_REQUEST_TYPES = 28;

    /**
     * Field/record-component names a bound type declares.
     *
     * <p>⚠ A record's {@code @Size}/{@code @NotBlank} live on the backing field, not the component
     * (F2-185b), but NAMES are the same either way — so this walks fields, which covers records and
     * classes alike without the {@code RECORD_COMPONENT} trap.
     */
    private static List<String> declaredNamesOf(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!f.isSynthetic()) {
                    names.add(f.getName());
                }
            }
        }
        return names;
    }

    @Test
    void aBodyBindingBothATenantAndAStateFieldIsNeverRankedTenant() {
        List<String> masked = rankEntityBoundWrites().stream()
            .filter(r -> "TENANT".equals(r.tier))
            .filter(r -> r.fields.stream().anyMatch(STATE::contains))
            .map(Ranked::site).sorted().toList();

        assertThat(masked)
            .as("These endpoints bind a state field AND organisationId, and are ranked as though "
                + "only the tenant field were at risk. F2-216 measured organisationId as defended "
                + "at every reachable point, so ranking it above state hides the live risk behind "
                + "a closed one.")
            .isEmpty();
    }

    /**
     * The census produced by F2-22b tranches (iv)–(vii): every CRIT endpoint, read one service at a
     * time, with the verdict that reading produced. It exists because <b>the scan cannot tell a
     * designed assertion from an unguarded one</b> — that distinction lives in the service, and
     * guessing it wrong breaks working features (the F2-40 trap, which cost four near-misses here).
     *
     * <p>Three verdicts:
     * <ul>
     *   <li><b>CLOSED</b> — was a real bypass, fixed; the row still ranks because the fix is
     *       service-side and Jackson still binds the field.</li>
     *   <li><b>INERT</b> — the field binds but the service overwrites or ignores it (a recompute, or
     *       a whitelist that omits it). Nothing to fix; annotating would claim a fix that never was.</li>
     *   <li><b>BY DESIGN</b> — the caller is *supposed* to set it. Annotating breaks the feature.</li>
     * </ul>
     */
    private static final Map<String, String> ADJUDICATED = Map.ofEntries(
        // ── F2-248: the one row the author/actor/circulated widening surfaced ─────────────
        //
        // Three fields, three different answers, which is why a per-endpoint verdict has to say
        // which is which (F2-231's per-field lesson):
        //   · circulatedBy — CLOSED. A user id, bound from the request, never written by the
        //     service, NULL on all 21 live rows in dev and prod. Now stamped from the principal.
        //   · authorName   — BY DESIGN, verified rather than reasoned: there is no author_id
        //     column, `author_role` holds an office, and all 21 rows in both databases name a BODY
        //     ('COM-BOARD Secretariat') resolving to no iam.users row. A committee paper's author is
        //     its PREPARER — the CFO, a department, an external advisor — and the schema's own
        //     separation of circulated_by from author_name is what settles it. F2-236 verified the
        //     same shape for counterpartySigner.
        //   · circulatedAt — TOLERATED as a fallback, stated rather than glossed: back-dating a
        //     circulation that happened last week is legitimate, and F2-233(d) established that a
        //     fallback is not an ownership claim. So this endpoint is NOT the split-attribution
        //     shape; it was a column with no writer at all.
        Map.entry("CommitteeMeetingPapersController#circulate(CommitteeMeetingPaper)",
            "CLOSED F2-248 (circulatedBy) — was bound from the body and NULL on 21/21 live rows in "
          + "both databases; now stamped from the principal. authorName is BY DESIGN (the paper's "
          + "preparer: no author_id column, author_role holds an office, 21/21 name a body). "
          + "circulatedAt is deliberately a fallback so a past circulation can be recorded"),

        // ── CLOSED: real bypasses, fixed service-side (so still ranked here) ──────────────
        Map.entry("EthicsCaseController#create(EthicsCase)",
            "CLOSED F2-22b(v) — apply() no longer copies status/outcome; create rejects non-initial"),
        Map.entry("EthicsCaseController#update(EthicsCase)",
            "CLOSED F2-22b(v) — update rejects a CHANGED status/outcome, tolerates the UI's echo"),
        Map.entry("TprmController#createAssessment(VendorAssessment)",
            "CLOSED F2-22b(iv) — engine fields READ_ONLY + create forces initial; riskScore BY DESIGN"),
        Map.entry("TprmController#updateAssessment(VendorAssessment)",
            "CLOSED F2-22b(iv) — refuses a manual score while a questionnaire owns the row"),

        // ── INERT: binds, but the service overwrites or ignores it ───────────────────────
        Map.entry("ChangeManagementController#create(ChangeRequest)",
            "INERT — CM-C3: ChangeRequest#apply deliberately omits status; outcome not copied either"),
        Map.entry("ChangeManagementController#update(ChangeRequest)",
            "INERT — same whitelist; status/outcome belong to transition()/guard()"),
        Map.entry("ClimateRiskOpportunityController#update(ClimateRiskOpportunity)",
            "INERT — recomputeScore() runs on create AND update: riskScore = likelihood x impact"),
        Map.entry("ThirdPartyCyberAssessmentController#create(ThirdPartyCyberAssessment)",
            "INERT — recompute() runs on create and on every recordPosture()"),
        Map.entry("NdiComplianceAssessmentController#update(NdiComplianceAssessment)",
            "INERT — update's whitelist omits complianceScore/lifecycleStatus; applyRules derives them"),

        // ── BY DESIGN: the caller is supposed to set it ──────────────────────────────────
        Map.entry("CyberController#saveControl(CyberControl)",
            "BY DESIGN — maturityLevel is an assessor's NCA ECC rating; no service derives it, SPA posts it"),
        Map.entry("CyberController#updateControl(CyberControl)",
            "BY DESIGN — as saveControl"),
        Map.entry("CyberController#saveSubControl(CyberSubControl)",
            "BY DESIGN — as saveControl"),
        Map.entry("CyberController#updateSubControl(CyberSubControl)",
            "BY DESIGN — as saveControl"),
        Map.entry("IamJmlEventController#create(IamJmlEvent)",
            "BY DESIGN — JML ingestion: the source IAM system reports outcome; service reacts to it"),
        Map.entry("IamJmlEventController#update(IamJmlEvent)",
            "BY DESIGN — as create; priorOutcome snapshot exists precisely to react to the reported value"),
        Map.entry("NdiGapController#createGap(NdiGap)",
            "BY DESIGN — gap status is cooperative: CAPA roll-up advances it, respects operator CLOSED/ACCEPTED_RISK"),
        Map.entry("NdiGapController#updateGap(NdiGap)",
            "BY DESIGN — as createGap. NOTE riskScore is never computed OR updated anywhere: stale by construction"),
        Map.entry("CommitteeResolutionController#create(CommitteeResolution)",
            "BY DESIGN — F2-40 settled it: the minute-taking form. Provenance (tally_source), not a DTO"),

        // ── F2-217: the six the widened vocabulary surfaced first, read and probed live ────
        // The three creates persisted the request entity, so the caller chose both the state and
        // the attribution; the three updates are field whitelists that never mention either.
        // ── F2-218 — the NDI registers: a WRITE-permission caller reached an APPROVE gate ────
        //
        // Probed live as `ranalyst` (NDI:WRITE, no NDI:APPROVE): POST /api/ndi/evidence/1/approve
        // answered 403, while the create stored status=ACTIVE with a chosen reviewerName and a
        // 2001 reviewedAt, and PUT /{id} moved a PENDING row to ACTIVE. NdiEvidenceItemService's
        // approve() enforces a maker/checker SoD check (owner != reviewer); neither write path did.
        //
        // ⚠ The update carried a comment saying reviewerName "is stamped server-side ... never
        // bound from the request body" — correct, one line above the two that copied `status` (the
        // decision) and `reviewedAt` (when). Fixing the field you noticed leaves the rest looking
        // covered, because the mechanism that covers them is visibly present (F2-215's headline).
        Map.entry("NdiEvidenceItemController#create(NdiEvidenceItem)",
            "CLOSED F2-218 — create forces PENDING and clears reviewerName/reviewedAt; ACTIVE is "
            + "approve()'s, behind NDI:APPROVE and the SoD check"),
        Map.entry("NdiEvidenceItemController#update(NdiEvidenceItem)",
            "CLOSED F2-218 — the whitelist no longer copies status or reviewedAt; PUT was "
            + "performing the PENDING -> ACTIVE approval that POST /{id}/approve had just refused"),
        Map.entry("NdiImprovementPlanController#create(NdiImprovementPlan)",
            "CLOSED F2-218 — create forces DRAFT (the only state approve() acts on, one constant) "
            + "and clears approverName/approvedAt; was 200 for status=COMPLETED"),
        Map.entry("NdiImprovementPlanController#update(NdiImprovementPlan)",
            "CLOSED F2-218 — update no longer copies status or approverName; both are approve()'s "
            + "and transition()'s, which record the change"),
        Map.entry("NdiAssessmentCycleController#create(NdiAssessmentCycle)",
            "CLOSED F2-218 — `if (status == null) status = DRAFT` was a DEFAULT, not a guard, so a "
            + "cycle could start SUBMITTED or CLOSED; approvedAt/submittedAt/closedAt now cleared"),

        Map.entry("NdiAssessmentCycleController#update(NdiAssessmentCycle)",
            "INERT F2-218 — the whitelist omits status and closedAt entirely, and END_STATUSES "
            + "refuses editing a closed cycle; both are transition()'s"),
        Map.entry("NdiMaturityAssessmentController#update(NdiMaturityAssessment)",
            "BY DESIGN F2-218 — reviewerName here is an ASSIGNMENT (who should review), taken from "
            + "the caller by markUnderReview() too, not an attribution derived from the principal; "
            + "status is not copied, and an APPROVED row is read-only"),

        Map.entry("AuditLifecycleController#createAnnualPlan(AnnualPlan)",
            "CLOSED F2-217 — was 200 for status=AC_APPROVED + a chosen acApprovedBy while /transition "
            + "answered 409; create now refuses a non-initial status and clears the AC stamp"),
        Map.entry("AuditLifecycleController#createWorkingPaper(WorkingPaper)",
            "CLOSED F2-217 — was 200 for status=APPROVED + a chosen reviewer/reviewedAt on an IIA 2330 "
            + "working paper, beside the author F2-203 had already fixed; both now cleared"),
        Map.entry("AuditLifecycleController#createCapa(CapaTask)",
            "CLOSED F2-217 — was 200 for status=CLOSED + a backdated closedAt, i.e. a corrective action "
            + "closed without verification; closedAt now stamped only by transitionCapa"),
        Map.entry("AuditLifecycleController#updateAnnualPlan(AnnualPlan)",
            "INERT — updateAnnualPlan is a whitelist (year/q*Codes/budgetHours/acNotes*); it never "
            + "mentions status, acApprovedBy or acApprovedAt"),
        Map.entry("AuditLifecycleController#updateWorkingPaper(WorkingPaper)",
            "INERT — updateWorkingPaper is a whitelist (title*/type/procedure*/conclusion*/"
            + "controlsCovered/hours); it never mentions status, reviewer or reviewedAt"),
        Map.entry("AuditLifecycleController#updateCapa(CapaTask)",
            "INERT — updateCapa is a whitelist (title*/ownerRole/targetDate/evidenceRef/"
            + "verificationNotes*); closedAt and status are transitionCapa's and applyOverdue's"),


        // ── F2-330: census drift — closed/inert rows a CEILING let sit mislabelled NEEDS-VERDICT.
        // Each verdict names the finding whose service-side fix closed it; every one was read
        // this slice (guard present, or whitelist confirmed) rather than trusted from prose (F2-188).
        Map.entry("BcpPlanController#create(BcpPlan)",
            "CLOSED F2-330 — was born with a forged last-attestation trail (a BCM:WRITE holder forging "
            + "what /attest reserves for BCM:APPROVE); create now scrubs lastAttestedAt / _by_name / "
            + "_by_id beside the five F2-220 / F2-234 already cleared"),
        Map.entry("CyberController#saveException(VulnException)",
            "CLOSED F2-328 — a vuln exception could be born APPROVED with a chosen approvedAt; "
            + "saveException refuses APPROVED and scrubs approvedAt (approval is /exceptions/{id}/approve's)"),
        Map.entry("CyberController#saveActionPlan(CyberActionPlan)",
            "CLOSED F2-329 — a plan born with escalatedAt set reads to the overdue sweep as "
            + "already-escalated; create scrubs escalatedAt / lastReminderAt / reminderCount / completedAt "
            + "and refuses born-COMPLETED"),
        Map.entry("PolicyController#createException(PolicyException)",
            "CLOSED F2-231 — a policy waiver could be filed already APPROVED with an invented approver; "
            + "createException forces the initial state and clears every review / approval / rejection column"),
        Map.entry("DpiaController#create(Dpia)",
            "CLOSED F2-233 — a PDPL Art.25 DPIA could be filed already APPROVED; create forces the "
            + "initial status and scrubs rejectedAt / rejectionReason (approvedAt is READ_ONLY)"),
        Map.entry("DpiaController#update(Dpia)",
            "INERT F2-214 — the update whitelist copies ten data fields and never mentions status or "
            + "rejectedAt; the submit-for-review transition owns those"),
        Map.entry("IssueController#create(Issue)",
            "CLOSED F2-220 — an issue could be born CLOSED skipping the resolution code and both "
            + "timestamps; create forces the initial status and scrubs resolution / resolvedAt / closedAt"),
        Map.entry("IssueController#update(Issue)",
            "CLOSED F2-297 — update refuses a CHANGED status (requireUnchanged) and its whitelist never "
            + "copies closedAt; both belong to transition()"),
        Map.entry("RegChangePolicyController#createChange(RegulatoryChange)",
            "CLOSED F2-233 — a regulatory change could be filed already CLOSED; create forces the "
            + "initial status and scrubs closedAt"),
        Map.entry("RegChangePolicyController#updateChange(RegulatoryChange)",
            "INERT F2-213 — the update whitelist copies source / title / citation / summary / dates / "
            + "applicability / impact and never mentions status or closedAt"),

        // ── F2-331: this slice's forensics fix + more census drift a CEILING let sit mislabelled.
        // Each verdict was read against its service this slice (F2-188): a scrub/guard present, or a
        // whitelist confirmed not to write the field. reviewerId on the two BCM registers is an
        // ASSIGNMENT (no reviewedAt/reviewerName column, no route stamps it) — legitimately caller-set.
        Map.entry("ForensicsArtefactController#create(ForensicsArtefact)",
            "CLOSED F2-331 — a chain-of-custody artefact was born VERIFIED (entity initialiser, F2-238) "
            + "and could name a forged integrityVerifiedAt; save() now owns UNVERIFIED and scrubs the "
            + "stamp (the attestation is /{id}/verify-integrity's)"),
        Map.entry("ForensicsArtefactController#update(ForensicsArtefact)",
            "CLOSED F2-331 — copyMutable no longer copies integrityStatus / integrityVerifiedAt, so a PUT "
            + "can no longer flip a CORRUPTED artefact back to VERIFIED past verifyIntegrity()"),
        Map.entry("BcmBiaAssessmentController#create(BcmBiaAssessment)",
            "CLOSED F2-22b — create rejects a non-DRAFT approvalStatus and forces DRAFT, stamps org from "
            + "TenantContext; reviewerId is an assignment"),
        Map.entry("BcmBiaAssessmentController#update(BcmBiaAssessment)",
            "CLOSED F2-297 — update guards approvalStatus with requireUnchanged and its patch whitelist "
            + "never copies organisationId; reviewerId is an assignment"),
        Map.entry("BcmRemediationController#createCapa(BcmCorrectiveAction)",
            "CLOSED F2-234 — createCapa forces the initial status via requireInitial, stamps org and clears "
            + "closureDate; reviewerId is an assignment"),
        Map.entry("BcmRemediationController#updateCapa(BcmCorrectiveAction)",
            "INERT — updateCapa is a whitelist (title*/actionPlan/ownerId/priority/dueDate/progressPercent/"
            + "evidenceId/reviewerId); it never writes status or organisationId (transitionCapa's)"),
        Map.entry("PolicyController#create(PolicyDocument)",
            "CLOSED F2-219 — create forces status=DRAFT and scrubs publishedAt, stamps org and createdBy; "
            + "approverName is F2-222's deliberately pre-approval-editable field (offices, stamped by "
            + "transition() on APPROVED)"),
        Map.entry("CyberPolicyController#create(PolicyDocument)",
            "CLOSED F2-219 — routes to the same PolicyManagementService.create as PolicyController#create"),
        Map.entry("PolicyController#createAttestation(PolicyAttestation)",
            "CLOSED F2-219 — createAttestation forces the initial status via requireInitial and scrubs "
            + "attestedAt / declinedAt / declinedReason (completion belongs to /attest and /decline)")
    );

    /** Field names that decide tenancy, state or authority — never a caller's to set. */
    private static final List<String> TENANT = List.of("organisationId");

    /**
     * Authority-bearing names the {@link #AUTHORITY_SHAPE} pattern below cannot express — an
     * id/name suffix on a bare agent noun, or a phrase.
     *
     * <p>⚠⚠ <b>F2-217 — this list used to be the WHOLE authority vocabulary, and a literal list
     * can only ever find the spellings whoever wrote it happened to think of.</b> It was written
     * from the four bypasses this class was built after (DF-3, DF-4, F2-25, the roster link), so
     * it holds {@code approvedBy}, {@code reviewedBy} and {@code closedBy} — and the platform's
     * columns are also called {@code acApprovedBy}, {@code reviewer}, {@code reviewedAt},
     * {@code closedAt}, {@code approverName}, {@code publishedAt}, {@code ratifiedAt},
     * {@code integrityVerifiedAt}. Measured 2026-08-15 from the regenerated profile: <b>24 distinct
     * authority-shaped names on 64 of the 214 endpoints</b>, nearly all of them ranked below CRIT
     * and therefore never adjudicated. With the vocabulary widened and the ladder corrected the
     * tiers move <b>CRIT 18 → 79, HIGH 3 → merged, TENANT 184 → 29, STATE 4 → 101</b>.
     *
     * <p>🔑 <b>A census keyed on a list of names measures the list, not the codebase</b> — the
     * eighth appearance of F2-198's shape here (the invariant is real, its POPULATION is narrower
     * than it looks), and the sharpest, because this instrument's own javadoc says it exists
     * <em>because</em> "ranking 214 endpoints by hand is how the first four were found".
     */
    private static final List<String> AUTHORITY_EXTRA = List.of(
        "approverId", "recordedByUserId", "recordedByName", "onBehalf");

    /**
     * The literal list exactly as it stood before F2-217 widened it. Kept — and only used — to
     * split the CRIT population into the half the old instrument could see and the half it could
     * not, so {@link #riskProfileIsRecordedForF2_22b()} can hold the first at a hard zero while
     * the second decreases. Never consult it for ranking; {@link #isAuthorityField} is the rule.
     */
    private static final List<String> AUTHORITY_PRE_F2_217 = List.of(
        "approvedBy", "approvedAt", "approverId", "rejectedBy", "decidedBy", "decidedAt",
        "signedBy", "signedAt", "verifiedBy", "verifiedAt", "reviewedBy", "recordedBy",
        "recordedByUserId", "recordedByName", "onBehalf", "closedBy", "publishedBy",
        "authorisedBy", "authorizedBy", "certifiedBy", "attestedBy", "escalatedBy");

    /**
     * An authority ACT: a concept verb plus {@code By} (who did it) or {@code At} (when).
     * Matched as a suffix so a qualified column — {@code acApprovedBy}, {@code pirReviewedAt},
     * {@code lastAttestedAt}, {@code loiSignedAt} — is caught with the bare form.
     *
     * <p>⚠ The verb alternation spells both cases ({@code [Aa]pproved}) rather than using
     * {@code CASE_INSENSITIVE}, and the {@code By|At} suffix stays case-SENSITIVE, because Java
     * fields are camelCase and {@code ...approvedat} is not a name this codebase produces. The
     * first draft of this pattern was lower-case-only and therefore did not match
     * {@code acApprovedBy} — so the two {@code AnnualPlan} endpoints whose live probe motivated
     * this whole slice ranked STATE rather than CRIT, and the build was green. 🔑 <b>A detector
     * that cannot match reads exactly like a clean tree, and it UNDER-reports</b> — caught only by
     * diffing against the throwaway census that had used {@code CASE_INSENSITIVE}, which is the
     * argument for keeping a second measurement until the gate reproduces it.
     */
    private static final Pattern AUTHORITY_SHAPE = Pattern.compile(
        "(?:^|[a-z0-9])(?:[Aa]pproved|[Rr]ejected|[Ss]igned|[Vv]erified|[Rr]eviewed|[Dd]ecided"
        + "|[Cc]ertified|[Aa]ttested|[Aa]uthorised|[Aa]uthorized|[Cc]losed|[Pp]ublished"
        + "|[Ee]ndorsed|[Aa]cknowledged|[Rr]atified|[Ww]itnessed|[Ee]scalated|[Aa]ccepted"
        + "|[Vv]alidated|[Ss]anctioned|[Cc]ountersigned|[Dd]ispositioned|[Aa]djudicated"
        + "|[Cc]onfirmed|[Cc]leared|[Ww]aived|[Ee]xempted|[Dd]elegated|[Rr]ecorded"
        // F2-248 — a THIRD unspelled name, and this one is an act VERB rather than an agent noun.
        // It was found by READING the single row the author/actor widening surfaced, not by the
        // gate: `admin.committee_meeting_papers.circulated_by` is a user id, was bound straight from
        // the request, never written by the service, and NULL on all 21 live rows in dev and prod.
        // 🔑 Two vocabularies, two blind spots, one slice — so assume a fourth spelling exists.
        + "|[Cc]irculated)(?:By|At)$");

    /** An authority ACTOR: the agent noun rather than the act. {@code reviewer}, {@code signerName}. */
    /**
     * ⚠⚠ <b>F2-248 — F2-217 replaced a literal field-name list with a SHAPE, and the shape's stems
     * are themselves a literal list.</b>
     *
     * <p>F2-217's own headline is that <i>"a risk ranking built from a literal field-name list finds
     * exactly the spellings its author had already seen"</i>, and its remedy was this pattern. But an
     * alternation of twelve agent nouns is still a vocabulary — it just looks like a rule. The two it
     * did not spell are {@code author} and {@code actor}, both ordinary English agent nouns ending in
     * {@code -or}, and both the sole record of who performed an act in a governance register:
     *
     * <ul>
     *   <li>{@code change_mgmt.change_comments.author_id/author_name} — who wrote a comment on a
     *       change request, i.e. part of the CAB deliberation trail. {@code author_id} was NULL on
     *       <b>45 of 45</b> dev rows and <b>46 of 46</b> prod rows, so nothing had ever written it,
     *       and a {@code RISK_ANALYST} could store a comment as the CISO (probed, HTTP 201).</li>
     *   <li>{@code ethics.case_actions.actor_id/actor_name} — who conducted an interview, made a
     *       finding or closed a whistleblowing case.</li>
     * </ul>
     *
     * <p>🔑 <b>Both endpoints ranked LOW.</b> Of 214 entity-bound writes only five were in that tier,
     * and these were two of them — so the ranking filed the two forgeable attributions it could not
     * spell into its smallest and least-attended bucket. F2-198's shape (the invariant is real, its
     * population is narrower than it looks) at the level of a pattern rather than a list.
     *
     * <p>⚠⚠ <b>A SHORT agent-noun stem cannot share the prefix rule of a long one, and my first
     * draft put both new stems into {@link #AGENT_SHAPE} where they did not belong.</b> That
     * pattern's prefix is {@code (?:.*[a-z0-9])?}, which happily matches a single lower-case letter
     * — so {@code actor} matched inside {@code f·actor·Name}, and the negative half of
     * {@link #theAuthorityVocabularyRecognisesTheNamesTheLiteralListMissed()} caught it on the first
     * run. {@code contractorName} and {@code benefactorId} are the same trap. The twelve original
     * stems are long and distinctive enough never to have hit it, which is exactly why the rule was
     * safe to write that way and is not safe to extend.
     *
     * <p>So the two short stems get their own pattern demanding a <b>camelCase word boundary</b>:
     * the stem starts the name, or begins with a capital. {@code authorId}/{@code actorName} pair;
     * {@code lastAuthorId}/{@code caseActorId} pair; {@code factorName}/{@code contractorName} do
     * not. Both directions are asserted, because a widening that is too wide silently RE-TIERS the
     * platform and one that is too narrow silently shrinks the work list.
     */
    private static final Pattern AGENT_SHAPE = Pattern.compile(
        "^(?:.*[a-z0-9])?(?:[Rr]eviewer|[Aa]pprover|[Ss]ignatory|[Ss]igner|[Vv]erifier|[Cc]ertifier"
        + "|[Aa]ttester|[Ww]itness|[Ee]ndorser|[Aa]uthoriser|[Aa]uthorizer|[Aa]djudicator)"
        + "(?:Id|Name|UserId)?$");

    /** F2-248 — {@code author} and {@code actor}, anchored to a camelCase boundary. See above. */
    private static final Pattern SHORT_AGENT_SHAPE = Pattern.compile(
        "^(?:[Aa]uthor|[Aa]ctor|.*[a-z0-9](?:Author|Actor))(?:Id|Name|UserId)?$");

    /** True when a field name names an authority act or the actor who performed it. */
    static boolean isAuthorityField(String n) {
        return AUTHORITY_EXTRA.contains(n)
            || AUTHORITY_SHAPE.matcher(n).find()
            || AGENT_SHAPE.matcher(n).matches()
            || SHORT_AGENT_SHAPE.matcher(n).matches();
    }
    private static final List<String> OUTCOME = List.of(
        "voteFor", "voteAgainst", "voteAbstain", "votesFor", "votesAgainst", "votesAbstain",
        "quorumMet", "quorumSource", "tallySource", "outcome", "verdict", "residualScore",
        "riskScore", "complianceScore", "maturityLevel");
    private static final List<String> STATE = List.of(
        "status", "state", "workflowStatus", "approvalStatus", "lifecycleStatus", "isApproved",
        "isActive", "isLocked", "isPublished", "isVerified", "isSigned", "isClosed");

    /**
     * One endpoint plus the risky fields its body type still exposes.
     *
     * @param visibleBeforeF2_217 true when the pre-F2-217 ladder also ranked this row CRIT — i.e.
     *        its authority field is a literal member of {@link #AUTHORITY_PRE_F2_217} and it also
     *        binds {@code organisationId}. That population is adjudicated and must stay so; the
     *        complement is what widening the vocabulary surfaced.
     */
    record Ranked(String tier, String site, List<String> fields, boolean visibleBeforeF2_217) { }

    static List<Ranked> rankEntityBoundWrites() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Ranked> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!isWriteMapping(m)) {
                    continue;
                }
                for (Parameter param : m.getParameters()) {
                    if (!hasRequestBody(param) || !param.getType().isAnnotationPresent(Entity.class)) {
                        continue;
                    }
                    Class<?> type = param.getType();
                    List<String> hits = new ArrayList<>();
                    boolean tenant = false, authority = false, outcome = false, state = false;
                    boolean literalAuthority = false;
                    for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
                        // BaseEntity's fields are READ_ONLY platform-wide (F2-22a) — skip them so
                        // the ranking shows only what is still bindable.
                        if (k == BaseEntity.class) {
                            break;
                        }
                        for (Field f : k.getDeclaredFields()) {
                            String n = f.getName();
                            // F2-22b(ii) — a field already marked READ_ONLY cannot arrive from a
                            // caller, so it is not part of this endpoint's risk. Without this the
                            // ranking is a static census and cannot show the tranches making
                            // progress: it would still list approvedBy on 21 entities that no
                            // longer bind it.
                            JsonProperty jp = f.getAnnotation(JsonProperty.class);
                            if (jp != null && jp.access() == JsonProperty.Access.READ_ONLY) {
                                continue;
                            }
                            if (TENANT.contains(n)) { hits.add(n); tenant = true; }
                            else if (isAuthorityField(n)) {
                                hits.add(n);
                                authority = true;
                                if (AUTHORITY_PRE_F2_217.contains(n)) literalAuthority = true;
                            }
                            else if (OUTCOME.contains(n)) { hits.add(n); outcome = true; }
                            else if (STATE.contains(n)) { hits.add(n); state = true; }
                        }
                    }
                    // ⚠⚠ F2-217 — this ladder used to read
                    //   tenant && (authority||outcome) ? CRIT : (authority||outcome) ? HIGH
                    //     : tenant ? TENANT : state ? STATE : LOW
                    // which is wrong in two ways that reinforced each other.
                    //
                    // (a) `tenant` sat ABOVE `state`, so a body binding organisationId AND status
                    //     ranked TENANT and its status never showed in the tier at all. That was
                    //     145 of the 214 endpoints — and it is why the STATE tier read FOUR while
                    //     149 bodies bind a state field.
                    // (b) `tenant` was also required to reach CRIT, on the premise that a bindable
                    //     organisationId is an unmitigated cross-tenant write. F2-216 measured that
                    //     premise and it no longer holds: all 202 entity-bound writes stamp the
                    //     tenant from TenantContext on their create path, and
                    //     TenantWriteGuardInterceptor is wired and enabled in dev AND prod with
                    //     both legs (probed: a body naming org 2 from an org-1 caller stores org 1).
                    //
                    // 🔑 A ranking whose top discriminator has been CLOSED no longer ranks risk —
                    // and while it stays on top it MASKS whatever it outranks. So tenant drops to
                    // the bottom of the ladder (it is still reported, because Jackson still binds
                    // it), and authority/outcome — the DF-3/DF-4 forged-authority class — is CRIT
                    // on its own. That merges the old HIGH tier in, which is the same question
                    // once tenant stops multiplying it.
                    String tier = (authority || outcome) ? "CRIT"
                                : state ? "STATE"
                                : tenant ? "TENANT" : "LOW";
                    Collections.sort(hits);
                    out.add(new Ranked(tier, c.getSimpleName() + "#" + m.getName()
                                             + "(" + type.getSimpleName() + ")", hits,
                                       tenant && (literalAuthority || outcome)));
                }
            }
        }
        List<String> order = List.of("CRIT", "STATE", "TENANT", "LOW");
        out.sort(Comparator.<Ranked, Integer>comparing(r -> order.indexOf(r.tier))
                          .thenComparing(Ranked::site));
        return out;
    }

    /** Concrete stand-in for "any entity", so the round-trip exercises BaseEntity itself. */
    static class Probe extends BaseEntity { }

    /** @return {@code Controller#method(EntityType)} for each entity-bound write endpoint. */
    static List<String> scanEntityBoundWrites() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> bound = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!isWriteMapping(m)) {
                    continue;
                }
                for (Parameter param : m.getParameters()) {
                    if (!hasRequestBody(param)) {
                        continue;
                    }
                    Class<?> type = param.getType();
                    if (type.isAnnotationPresent(Entity.class)) {
                        bound.add(c.getSimpleName() + "#" + m.getName() + "(" + type.getSimpleName() + ")");
                    }
                }
            }
        }
        return bound;
    }

    private static boolean hasRequestBody(Parameter p) {
        for (Annotation a : p.getAnnotations()) {
            if (a instanceof RequestBody) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWriteMapping(Method m) {
        if (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
            || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(DeleteMapping.class)) {
            return true;
        }
        RequestMapping rm = m.getAnnotation(RequestMapping.class);
        if (rm != null) {
            for (RequestMethod meth : rm.method()) {
                if (meth == RequestMethod.POST || meth == RequestMethod.PUT
                    || meth == RequestMethod.PATCH || meth == RequestMethod.DELETE) {
                    return true;
                }
            }
        }
        return false;
    }
}
