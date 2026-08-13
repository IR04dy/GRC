package com.tss.grc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.common.json.ServerOwnedIdentityModule;
import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-206 — no JPA entity may take its primary key from a request body. A hard zero over
 * <b>every</b> {@code @Entity}, with no exemption map.
 *
 * <h2>Why this exists beside {@link MassAssignmentRatchetTest}</h2>
 *
 * <p>That class already asserts <em>"id must not bind — a create could retarget an existing
 * row"</em>, and it passed while the defect was live on 177 of 214 endpoints. It is not wrong:
 * it proves the assertion through {@code class Probe extends BaseEntity}, and
 * {@code BaseEntity} does carry {@code @JsonProperty(access = READ_ONLY)} on {@code id}. But
 * <b>174 of the platform's 432 entities extend that mapped superclass and 258 do not</b>, and
 * none of the 258 annotated its own {@code id}.
 *
 * <p>⚠ Those three figures come from the classpath scan in this class, not from a source sweep.
 * A source regex over {@code @Entity} answered 407/155/252 — 25 entities short, because an
 * entity can be declared somewhere a filename-shaped scan does not look. The reflection count
 * is the authority, which is the rule this repo has recorded four times over Java corpora.
 *
 * <p>🔑 <b>An invariant enforced by a superclass covers exactly its subclasses, and a probe
 * that extends that superclass can only ever measure the covered half.</b> The sibling gate's
 * risk profile could not see it either: it stops walking fields at {@code BaseEntity} because
 * everything there is already {@code READ_ONLY}, and {@code id} appears in none of its
 * TENANT/AUTHORITY/OUTCOME/STATE vocabularies, so {@code id} was structurally unrankable.
 *
 * <p>This class therefore does not pick a probe at all. It enumerates every {@code @Entity} on
 * the classpath and round-trips hostile JSON through each one, so there is no population to
 * choose and nothing to keep in step.
 *
 * <h2>The live defect this replaces</h2>
 *
 * <p>Probed 2026-08-13 as {@code compliance} (not {@code admin} — PLATFORM_ADMIN bypasses in
 * code): {@code POST /api/v1/privacy/ropa} with {@code {"id": <n>}} answered <b>201 Created</b>
 * and overwrote processing activity {@code n}, nulling every column the body omitted, moving
 * the PDPL lawful basis from {@code LEGAL_OBLIGATION} to {@code CONSENT} and clearing the
 * Article 25 special-category flag. Cross-tenant reach was refuted (500, row untouched).
 */
class ServerOwnedIdentityInvariantTest {

    /**
     * The id leg's body carries ONLY {@code id}, and that is deliberate.
     *
     * <p>⚠ The first draft used one combined body for both legs and 34 entities came back
     * UNTESTABLE: they map {@code isDeleted} as {@code Short}, not {@code Boolean}, so
     * {@code "isDeleted": true} raises {@code MismatchedInputException} before Jackson ever
     * reaches {@code id} — and the list of casualties was `TreatmentPlan`, `Vulnerability`,
     * `DsrCase`, `Finding`, `CrossBorderTransfer`… i.e. precisely the statutory registers this
     * slice is about. 🔑 <b>A probe body that asserts more than the claim can fail for a reason
     * unrelated to the claim, and on this corpus that reason correlated with the records that
     * matter most.</b> One field per question.
     */
    private static final String HOSTILE_ID = """
        { "id": 999999 }
        """;

    /** F2-22a's fields. Only ever applied to {@code BaseEntity} subclasses, which own them. */
    private static final String HOSTILE_AUDIT = """
        {
          "id": 999999,
          "createdBy": 1,
          "updatedBy": 1,
          "isDeleted": true
        }
        """;

    /**
     * A mapper configured the way Spring Boot configures the app's.
     *
     * <p>⚠ Two settings here are load-bearing and the first draft of this class had neither,
     * which made its central assertion VACUOUS — it reported "0 entities bind id" while most
     * round-trips were throwing:
     * <ul>
     *   <li>{@code JavaTimeModule} — a bare mapper cannot read {@code LocalDateTime} at all.</li>
     *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} — <b>Jackson defaults this to TRUE and
     *       Spring Boot turns it off.</b> With it on, the hostile body's {@code createdBy} etc.
     *       raise {@code UnrecognizedPropertyException} on the 258 entities that have no such
     *       property, so the deserialisation never reaches {@code id} and the entity is scored
     *       as "did not bind". 🔑 <b>A test that mirrors the framework must mirror the
     *       framework's CONFIGURATION, not just its modules</b> — and an exception during the
     *       probe is indistinguishable from the probe passing unless something counts it, which
     *       is what {@code untestable} below is for. It fired.</li>
     * </ul>
     */
    private static ObjectMapper appMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ServerOwnedIdentityModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature
                .FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static List<Class<?>> allEntities() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable ignored) {
                // A type that cannot be loaded cannot be bound by a controller either.
                continue;
            }
            // ⚠ Excluded BY IDENTITY, not by a name pattern. This class's own self-test fixture is
            // an @Entity and the scanner finds it (the population went 432 -> 433 the moment it was
            // added). A rule like "skip anything called Synthetic*" would let any real entity be
            // hidden from this gate by renaming it — the exact trap F2-192 walked into with a
            // `setClock`-shaped exemption, three lines under its own warning about it.
            if (c == SyntheticUnprobeableEntity.class) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /**
     * Reads {@code id} back after deserialising {@code body} into {@code entity}.
     *
     * <p>⚠⚠ There is deliberately NO try/catch here, and that is the third revision of this
     * method. The first swallowed every failure into a bare {@code "UNTESTABLE"} string; the
     * second returned a {@code Probed(ok, id, why)} record so a failure could be counted. The
     * mutation harness killed neither idea: flipping that {@code ok} flag to {@code true} scored
     * every failed probe as a verified pass, and <b>the count I had added to catch exactly that
     * was derived from the same flag</b>.
     *
     * <p>🔑 <b>A probe cannot certify its own success — if it reports "I worked", something has to
     * be able to disagree, and nothing inside the probe can.</b> So the probe no longer reports:
     * an entity that cannot be deserialised throws out of the test with its real stack trace,
     * which is both stricter and unfakeable. All 432 entities probe cleanly today, so nothing is
     * being tolerated by this being strict.
     *
     * <p>(The {@code Short}-typed {@code isDeleted} problem that motivated the original catch is
     * gone for a better reason: {@link #HOSTILE_ID} asks about one field.)
     */
    private static Object boundId(ObjectMapper mapper, Class<?> entity, String body)
            throws Exception {
        Object o = mapper.readValue(body, entity);
        Method getter = findGetter(entity);
        if (getter == null) {
            throw new AssertionError("No getId() on " + entity.getName() + " or any supertype, so "
                + "this test cannot say anything about it. Give it one, or record here why an "
                + "entity whose id cannot be read back is safe.");
        }
        getter.setAccessible(true);
        return getter.invoke(o);
    }

    private static Method findGetter(Class<?> entity) {
        for (Class<?> k = entity; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod("getId");
            } catch (NoSuchMethodException ignored) {
                // keep walking — Lombok puts it on the declaring class of the field
            }
        }
        return null;
    }

    @Test
    void noEntityAcceptsItsPrimaryKeyFromARequestBody() throws Exception {
        ObjectMapper mapper = appMapper();
        List<Class<?>> entities = allEntities();
        Set<String> bound = new TreeSet<>();
        // ⚠ Counted, not inferred: `verified` must equal the population. A negative assertion
        // ("nothing bound an id") passes just as well over an empty set as over a clean one, and
        // an empty set is what a broken probe produces. 🔑 A gate whose subject can silently
        // shrink needs a POSITIVE count — the same shape as F2-198's stripper bug, where a corrupted
        // input produced a smaller population that read exactly like "those are fine".
        int verified = 0;

        for (Class<?> e : entities) {
            Object id = boundId(mapper, e, HOSTILE_ID);
            if (id != null) {
                bound.add(e.getSimpleName() + " -> id=" + id);
            } else {
                verified++;
            }
        }

        System.out.println("[F2-206] @Entity types round-tripped = " + entities.size());
        System.out.println("[F2-206] entities verified = " + verified
            + " / still bind id = " + bound.size());
        bound.forEach(s -> System.out.println("  - " + s));

        assertThat(entities.size())
            .as("The entity scan returned almost nothing, so a green result here would mean the "
                + "scan broke rather than that no entity binds its id.")
            .isGreaterThan(350);

        assertThat(verified)
            .as("Every entity must have been positively verified — deserialised, id read back, "
                + "found null. This count is what stops the assertions below passing over a "
                + "population that silently became empty.")
            .isEqualTo(entities.size());

        assertThat(bound)
            .as("A JPA entity accepted its primary key from JSON. CrudRepository#save() calls "
                + "merge() rather than persist() when the id is non-null, so a POST that is "
                + "supposed to create a record OVERWRITES the row named by the body — nulling "
                + "every column the body omits — and answers 201 Created. Fix it in "
                + "ServerOwnedIdentityModule, which needs no per-entity change; do not add an "
                + "exemption.")
            .isEmpty();
    }

    /**
     * The F2-22a fields, asserted over the same full population rather than one probe.
     *
     * <p>Kept separate from the id leg so a failure says which mechanism broke: F2-22a's
     * annotations on {@code BaseEntity}, or F2-206's introspector.
     */
    @Test
    void noEntityAcceptsForgedAuditAttributionFromARequestBody() throws Exception {
        ObjectMapper mapper = appMapper();
        Set<String> leaks = new TreeSet<>();
        for (Class<?> e : allEntities()) {
            if (!BaseEntity.class.isAssignableFrom(e)) {
                // createdBy/updatedBy/isDeleted are BaseEntity's own columns; a type that does
                // not extend it has no such property for a caller to set. Asserting over the
                // whole 407 would pass vacuously on 252 of them and read as coverage.
                continue;
            }
            BaseEntity o = (BaseEntity) mapper.readValue(HOSTILE_AUDIT, e);
            if (o.getCreatedBy() != null) leaks.add(e.getSimpleName() + ".createdBy");
            if (o.getUpdatedBy() != null) leaks.add(e.getSimpleName() + ".updatedBy");
            if (Boolean.TRUE.equals(o.getIsDeleted())) leaks.add(e.getSimpleName() + ".isDeleted");
        }
        System.out.println("[F2-206] BaseEntity subclasses checked for forged attribution = "
            + allEntities().stream().filter(BaseEntity.class::isAssignableFrom).count());
        assertThat(leaks)
            .as("Forged audit attribution — the DF-3/DF-4 defect. BaseEntity's READ_ONLY "
                + "annotations must not be removed.")
            .isEmpty();
    }

    /**
     * NEGATIVE CONTROL: without the module, a non-{@code BaseEntity} entity DOES bind its id.
     *
     * <p>Without this leg the assertion above is indistinguishable from a vacuous one — Jackson
     * might be refusing {@code id} for some unrelated reason (a missing setter, a visibility
     * rule), in which case the module could be deleted and the gate would stay green. This is
     * the leg that proves the module is what is doing the work, and it is exactly the check
     * whose absence let F2-205 ship two gate holes.
     */
    @Test
    void theModuleIsWhatBlocksIt_notSomethingElse() throws Exception {
        ObjectMapper withoutModule = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature
                .FAIL_ON_UNKNOWN_PROPERTIES, false);

        // A real entity, chosen because it is the one the live probe destroyed, and because it
        // does NOT extend BaseEntity — the whole point of the population this class widens.
        Class<?> ropa = Class.forName("com.tss.grc.module.privacy.entity.ProcessingActivity");
        assertThat(BaseEntity.class.isAssignableFrom(ropa))
            .as("ProcessingActivity was chosen for this control BECAUSE it sits outside "
                + "BaseEntity. If it is ever made to extend it, this control stops testing the "
                + "uncovered population and must be repointed at another entity that does not.")
            .isFalse();

        assertThat(boundId(withoutModule, ropa, HOSTILE_ID))
            .as("Without ServerOwnedIdentityModule the id binds — that is the defect, and it is "
                + "what makes the positive assertion meaningful rather than vacuous.")
            .isEqualTo(999999L);

        assertThat(boundId(appMapper(), ropa, HOSTILE_ID))
            .as("With the module the same hostile body leaves id null.")
            .isNull();
    }

    /** A mapped type this test cannot read an id back from. Exists only for the self-test below. */
    @Entity
    static class SyntheticUnprobeableEntity {
        @jakarta.persistence.Id
        Long identity;
    }

    /**
     * SYNTHETIC SELF-TEST: prove the probe REFUSES a type it cannot verify.
     *
     * <p>⚠ This leg exists because a mutation could not be killed without it. The harness tried
     * three ways to make a failed probe read as a pass; the third — wrapping the loop in
     * {@code catch (Throwable) { continue; }} — <b>survived, and correctly so: on a corpus where
     * nothing fails, a tolerance branch is unreachable, so adding one changes no behaviour.</b>
     *
     * <p>🔑 <b>A corpus assertion passes both when the platform is clean and when the instrument
     * has rotted, and no mutation of the instrument can tell them apart while the corpus is
     * clean.</b> The only way to show the strictness is load-bearing is to hand the probe
     * something that genuinely cannot be verified and watch it refuse — which is the same reason
     * F2-195's {@code kpiProvenance} carries a synthetic self-test.
     */
    @Test
    void theProbeRefusesATypeItCannotVerify() {
        ObjectMapper mapper = appMapper();
        Throwable thrown = null;
        try {
            boundId(mapper, SyntheticUnprobeableEntity.class, HOSTILE_ID);
        } catch (Throwable t) {
            thrown = t;
        }
        assertThat(thrown)
            .as("A mapped type whose id cannot be read back must make this test FAIL, not be "
                + "quietly skipped. If this stops throwing, the population assertion above can be "
                + "satisfied by an empty set and the whole class becomes decorative.")
            .isInstanceOf(AssertionError.class);
        assertThat(thrown).hasMessageContaining("No getId()");
    }

    /**
     * The module must be a Spring bean, or none of the above protects the running application.
     *
     * <p>⚠ Every other leg in this class registers the module <b>by hand</b> on a local
     * {@link ObjectMapper}, so they prove what the module DOES and say nothing about whether the
     * app ever loads it. Deleting {@code @Component} would leave this whole class green and the
     * platform exactly as exposed as it was before the slice — the same shape as F2-202's
     * finding that a reachability scan sees a symbol being called but not whether calling it does
     * anything, and F2-205's that a substring check is satisfied by an import.
     *
     * <p>Spring Boot's {@code JacksonAutoConfiguration} registers every
     * {@code com.fasterxml.jackson.databind.Module} bean into the auto-configured mapper, so
     * "is a Module" + "is a bean" is the whole contract, and both halves are asserted.
     */
    @Test
    void theModuleIsRegisteredWithSpringAndNotOnlyWithThisTest() {
        assertThat(com.fasterxml.jackson.databind.Module.class
                .isAssignableFrom(ServerOwnedIdentityModule.class))
            .as("Spring Boot only auto-registers beans that ARE a Jackson Module. If this stops "
                + "being one, it must be wired into Jackson2ObjectMapperBuilder explicitly.")
            .isTrue();

        assertThat(ServerOwnedIdentityModule.class
                .isAnnotationPresent(org.springframework.stereotype.Component.class))
            .as("ServerOwnedIdentityModule is not a Spring bean, so the running application does "
                + "NOT have it and every other assertion in this class — all of which register it "
                + "by hand — would stay green over a fully exposed platform.")
            .isTrue();
    }

    /**
     * The measured shape of the blind spot, so the next reader does not have to re-derive it.
     *
     * <p>Decrease-only figures would be wrong here: this is not a population being worked down,
     * it is a fact about the class hierarchy that the fix makes irrelevant. What matters is that
     * the two numbers are still ASSERTED, because the moment {@code BaseEntity} covers
     * everything, {@link MassAssignmentRatchetTest}'s single probe becomes sufficient again and
     * a reader should be told rather than left guessing.
     */
    @Test
    void theBaseEntityPopulationIsStillTheMinority() {
        List<Class<?>> all = allEntities();
        long covered = all.stream().filter(BaseEntity.class::isAssignableFrom).count();
        long outside = all.size() - covered;
        System.out.println("[F2-206] entities extending BaseEntity = " + covered
            + " / outside = " + outside + " / total = " + all.size());

        assertThat(outside)
            .as("Every entity now extends BaseEntity, so the superclass annotations cover the "
                + "whole population on their own. That is a good change — but it means "
                + "MassAssignmentRatchetTest's Probe is sufficient again and this class's "
                + "framing is stale. Update both rather than deleting this assertion.")
            .isPositive();
        assertThat(covered)
            .as("No entity extends BaseEntity any more, which would mean F2-22a's nine "
                + "READ_ONLY fields protect nothing.")
            .isPositive();
    }
}
