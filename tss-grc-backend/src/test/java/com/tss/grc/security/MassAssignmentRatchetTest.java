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
        lines.add("# ADJUDICATED: verdict recorded from reading the service (F2-22b iv-vii)");
        lines.add("# (blank)    : not yet adjudicated — read the service before annotating anything");
        lines.add("#");
        for (Ranked r : ranked) {
            String verdict = ADJUDICATED.getOrDefault(r.site, "");
            lines.add(String.format("%-6s %-64s %-46s %s",
                r.tier, r.site, String.join(",", r.fields), verdict));
        }
        Files.write(out, lines);

        long unadjudicatedCrit = ranked.stream()
            .filter(r -> "CRIT".equals(r.tier) && !ADJUDICATED.containsKey(r.site))
            .count();
        System.out.println("[F2-22b risk profile] CRIT rows with no recorded verdict = " + unadjudicatedCrit);
        assertThat(unadjudicatedCrit)
            .as("Every CRIT endpoint has been read service-by-service across tranches (iv)-(vii). "
                + "A new unadjudicated CRIT row means a new entity-bound write appeared — read its "
                + "service and record the verdict here rather than annotating the entity blind, "
                + "which is the F2-40 trap that would break a designed assertion.")
            .isZero();

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
            "BY DESIGN — F2-40 settled it: the minute-taking form. Provenance (tally_source), not a DTO")
    );

    /** Field names that decide tenancy, state or authority — never a caller's to set. */
    private static final List<String> TENANT = List.of("organisationId");
    private static final List<String> AUTHORITY = List.of(
        "approvedBy", "approvedAt", "approverId", "rejectedBy", "decidedBy", "decidedAt",
        "signedBy", "signedAt", "verifiedBy", "verifiedAt", "reviewedBy", "recordedBy",
        "recordedByUserId", "recordedByName", "onBehalf", "closedBy", "publishedBy",
        "authorisedBy", "authorizedBy", "certifiedBy", "attestedBy", "escalatedBy");
    private static final List<String> OUTCOME = List.of(
        "voteFor", "voteAgainst", "voteAbstain", "votesFor", "votesAgainst", "votesAbstain",
        "quorumMet", "quorumSource", "tallySource", "outcome", "verdict", "residualScore",
        "riskScore", "complianceScore", "maturityLevel");
    private static final List<String> STATE = List.of(
        "status", "state", "workflowStatus", "approvalStatus", "lifecycleStatus", "isApproved",
        "isActive", "isLocked", "isPublished", "isVerified", "isSigned", "isClosed");

    /** One endpoint plus the risky fields its body type still exposes. */
    record Ranked(String tier, String site, List<String> fields) { }

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
                            else if (AUTHORITY.contains(n)) { hits.add(n); authority = true; }
                            else if (OUTCOME.contains(n)) { hits.add(n); outcome = true; }
                            else if (STATE.contains(n)) { hits.add(n); state = true; }
                        }
                    }
                    String tier = tenant && (authority || outcome) ? "CRIT"
                                : (authority || outcome) ? "HIGH"
                                : tenant ? "TENANT"
                                : state ? "STATE" : "LOW";
                    Collections.sort(hits);
                    out.add(new Ranked(tier, c.getSimpleName() + "#" + m.getName()
                                             + "(" + type.getSimpleName() + ")", hits));
                }
            }
        }
        List<String> order = List.of("CRIT", "HIGH", "TENANT", "STATE", "LOW");
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
