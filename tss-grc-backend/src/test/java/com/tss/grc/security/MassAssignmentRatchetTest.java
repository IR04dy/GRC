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
     * <p>This is the platform-wide half of F2-22a: whatever any individual endpoint does with
     * its body, these nine fields can no longer arrive from a caller. Asserted through a real
     * {@link ObjectMapper} round-trip rather than by looking for the annotation, because what
     * matters is Jackson's behaviour, not which annotation produced it.
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
        for (Ranked r : ranked) {
            lines.add(String.format("%-6s %-64s %s", r.tier, r.site, String.join(",", r.fields)));
        }
        Files.write(out, lines);

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
