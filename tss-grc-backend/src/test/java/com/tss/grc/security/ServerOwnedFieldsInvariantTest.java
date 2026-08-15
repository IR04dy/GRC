package com.tss.grc.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.common.json.ServerOwnedFieldsModule;
import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-206 / F2-215 — no JPA entity may take a SERVER-OWNED field from a request body. A hard
 * zero over <b>every</b> {@code @Entity} and <b>every one of the nine fields</b>, with no
 * exemption map.
 *
 * <h2>Why this exists beside {@link MassAssignmentRatchetTest}</h2>
 *
 * <p>That class already asserts <em>"id must not bind — a create could retarget an existing
 * row"</em>, and it passed while the defect was live on 177 of 214 endpoints. It is not wrong:
 * it proves the assertion through {@code class Probe extends BaseEntity}, and
 * {@code BaseEntity} does carry {@code @JsonProperty(access = READ_ONLY)}. But <b>174 of the
 * platform's 433 entities extend that mapped superclass and 259 do not</b>, and none of the
 * 259 annotated anything.
 *
 * <p>🔑 <b>An invariant enforced by a superclass covers exactly its subclasses, and a probe
 * that extends that superclass can only ever measure the covered half.</b>
 *
 * <h2>⚠⚠ F2-215 — and an invariant generalised ONE FIELD AT A TIME covers exactly that field</h2>
 *
 * <p>F2-206 widened the population from "{@code BaseEntity} subclasses" to "every mapped type"
 * and this class asserted it — <b>for {@code id}.</b> The audit leg below then narrowed the
 * population straight back for the other three fields it checked, with this comment:
 *
 * <blockquote>{@code createdBy/updatedBy/isDeleted are BaseEntity's own columns; a type that
 * does not extend it has no such property for a caller to set.}</blockquote>
 *
 * <p><b>That premise was false.</b> Measured 2026-08-15: of the 259 entities outside
 * {@code BaseEntity}, 209 declare their own {@code isDeleted}, 111 their own {@code createdBy},
 * 104 their own {@code updatedBy} — 960 declarations across the nine fields, <b>none of them
 * annotated</b>. The {@code continue} was written to avoid a vacuous pass and instead created
 * the blind spot, one test method below the leg that had just widened the same population.
 *
 * <p>🔑 <b>The comment names the right worry and reaches the wrong conclusion, because it
 * assumed rather than measured — and it is the kind of assumption nothing can contradict,
 * since a skipped entity and a clean entity produce the same green.</b> Same family as
 * F2-184's inert constraint and F2-199's comment describing a check that does not exist:
 * <b>an artefact that answers the question a reader was about to ask is worse than silence
 * when the answer is wrong.</b>
 *
 * <p>The audit leg is now the same shape as the id leg — full population, positively counted —
 * and {@link #theModuleMirrorsBaseEntitysOwnFieldList()} is the leg that would have caught the
 * original gap: it fails if the module's list and {@code BaseEntity}'s {@code READ_ONLY} set
 * ever differ, in either direction.
 *
 * <h2>The live defects this replaces</h2>
 *
 * <p>Probed as {@code compliance} (not {@code admin} — PLATFORM_ADMIN bypasses in code), each
 * against a row the probe planted for itself:
 * <ul>
 *   <li>2026-08-13 — {@code POST /api/v1/privacy/ropa} with {@code {"id": n}} answered
 *       <b>201 Created</b> and overwrote processing activity {@code n}.</li>
 *   <li>2026-08-15 — {@code POST /api/v1/controls} stored {@code "createdBy": "ceo@tss.sa"}
 *       verbatim, stored a {@code createdAt} of 2001, and with {@code "isDeleted": true}
 *       created a row that is invisible to every query (the entity carries
 *       {@code @Where(is_deleted = 0)}) while the UNIQUE index still sees it — permanently
 *       burning that control code, with a duplicate-key error the register contradicts.</li>
 * </ul>
 */
class ServerOwnedFieldsInvariantTest {

    /**
     * The id leg's body carries ONLY {@code id}, and that is deliberate.
     *
     * <p>⚠ F2-206's first draft used one combined body and 34 entities came back UNTESTABLE:
     * they map {@code isDeleted} as {@code Short}, not {@code Boolean}, so
     * {@code "isDeleted": true} raises {@code MismatchedInputException} before Jackson ever
     * reaches {@code id} — and the casualties were {@code TreatmentPlan},
     * {@code Vulnerability}, {@code DsrCase}, {@code Finding}, {@code CrossBorderTransfer},
     * i.e. precisely the statutory registers. 🔑 <b>A probe body that asserts more than the
     * claim can fail for a reason unrelated to the claim.</b> One field per question.
     *
     * <p>⚠⚠ F2-215: that observation has a second edge nobody drew. The {@code Short} typing
     * did not make those entities SAFE, only unprobeable <em>by that body shape</em> —
     * {@code {"isDeleted": 1}} binds on all 209. The per-field bodies below are therefore
     * built from each field's declared TYPE rather than from one literal, so an entity can
     * never drop out of the population for being typed unusually.
     */
    private static final String HOSTILE_ID = """
        { "id": 999999 }
        """;

    private static ObjectMapper appMapper() {
        return withModules(new ObjectMapper().registerModule(new ServerOwnedFieldsModule()));
    }

    /**
     * A mapper configured the way Spring Boot configures the app's.
     *
     * <p>⚠ Two settings are load-bearing and F2-206's first draft had neither, which made its
     * central assertion VACUOUS — it reported "0 entities bind id" while most round-trips were
     * throwing:
     * <ul>
     *   <li>{@code JavaTimeModule} — a bare mapper cannot read {@code LocalDateTime} at all.</li>
     *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} — <b>Jackson defaults this to TRUE and
     *       Spring Boot turns it off.</b> With it on, a hostile body naming a property the
     *       entity does not have raises {@code UnrecognizedPropertyException}, the
     *       deserialisation never reaches the field under test, and the entity is scored as
     *       "did not bind". 🔑 <b>A test that mirrors the framework must mirror the framework's
     *       CONFIGURATION, not just its modules</b> — and an exception during a probe is
     *       indistinguishable from the probe passing unless something counts it.</li>
     * </ul>
     */
    private static ObjectMapper withModules(ObjectMapper m) {
        return m.registerModule(new JavaTimeModule())
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
            // ⚠ Excluded BY IDENTITY, not by a name pattern. This class's own self-test fixture
            // is an @Entity and the scanner finds it. A rule like "skip anything called
            // Synthetic*" would let any real entity be hidden from this gate by renaming it —
            // the exact trap F2-192 walked into with a `setClock`-shaped exemption, three lines
            // under its own warning about it.
            if (c == SyntheticUnprobeableEntity.class) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    // ── the id leg (F2-206, unchanged) ───────────────────────────────────────────────

    /**
     * Reads {@code id} back after deserialising {@code body} into {@code entity}.
     *
     * <p>⚠⚠ There is deliberately NO try/catch here, and that is the third revision of this
     * method. The first swallowed every failure into a bare {@code "UNTESTABLE"} string; the
     * second returned a {@code Probed(ok, id, why)} record so a failure could be counted. The
     * mutation harness killed neither idea: flipping that {@code ok} flag scored every failed
     * probe as a verified pass, and <b>the count added to catch exactly that was derived from
     * the same flag</b>.
     *
     * <p>🔑 <b>A probe cannot certify its own success — if it reports "I worked", something has
     * to be able to disagree, and nothing inside the probe can.</b>
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
        // an empty set is what a broken probe produces.
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
                + "ServerOwnedFieldsModule, which needs no per-entity change; do not add an "
                + "exemption.")
            .isEmpty();
    }

    // ── the other eight, over the SAME population (F2-215) ───────────────────────────

    /** The eight non-{@code id} server-owned fields, checked one at a time on every entity. */
    private static final List<String> AUDIT_FIELDS = List.of(
        "createdAt", "updatedAt", "createdBy", "updatedBy",
        "isDeleted", "deletedAt", "deletedBy", "rowVersion");

    /**
     * A hostile JSON literal a field of this type can accept, and the value that literal
     * produces if it binds.
     *
     * <p>🔑 <b>The test is "does the field hold WHAT I SENT", never "is the field set".</b> The
     * first draft asked the second question and reported <b>161 false positives</b> — almost
     * every {@code createdAt} — because those fields carry a {@code = LocalDateTime.now()}
     * initialiser, so they are non-null whether the hostile value bound or not. An initialised
     * field cannot be probed by presence; only by identity. Same family as every other
     * instrument error on this register: <b>a check satisfied by something other than its
     * claim</b>, here in the direction that manufactures findings rather than hiding them.
     *
     * <p>The literal is also built from the field's DECLARED TYPE rather than from one constant,
     * which is F2-206's {@code Short}-typed {@code isDeleted} lesson: a body built from a single
     * literal silently excludes every entity that types the field unusually, and an excluded
     * entity is indistinguishable from a clean one.
     */
    private record Hostile(String json, Object expected) { }

    private static Hostile hostileFor(Class<?> type) {
        if (type == Boolean.class || type == boolean.class) {
            return new Hostile("1", Boolean.TRUE);
        }
        if (type == Short.class || type == short.class) {
            return new Hostile("1", (short) 1);
        }
        if (type == Integer.class || type == int.class) {
            return new Hostile("1", 1);
        }
        if (type == Long.class || type == long.class) {
            return new Hostile("999999", 999999L);
        }
        if (type == String.class) {
            return new Hostile("\"forged-by-the-caller\"", "forged-by-the-caller");
        }
        if (type == LocalDateTime.class) {
            return new Hostile("\"2001-01-01T00:00:00\"", LocalDateTime.of(2001, 1, 1, 0, 0));
        }
        if (type == LocalDate.class) {
            return new Hostile("\"2001-01-01\"", LocalDate.of(2001, 1, 1));
        }
        if (type == byte[].class) {
            return new Hostile("\"AAAAAAAAB9E=\"", Base64.getDecoder().decode("AAAAAAAAB9E="));
        }
        // No hostile literal for this type. Refuse rather than skip: a silently skipped field
        // is how this whole finding formed. Add the type here when a new one appears.
        throw new AssertionError("No hostile JSON value defined for type " + type.getName()
            + ". Add one — do not let the field drop out of the population, which is exactly "
            + "how F2-206's Short-typed isDeleted came to read as safe.");
    }

    /** True when the field came back holding exactly the value the hostile body carried. */
    private static boolean bound(Object actual, Object expected) {
        if (actual instanceof byte[] a && expected instanceof byte[] b) {
            return Arrays.equals(a, b);
        }
        return Objects.equals(actual, expected);
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking — the field may be on a mapped superclass
            }
        }
        return null;
    }

    /**
     * The eight audit / soft-delete / locking fields, over EVERY entity that declares one.
     *
     * <p>⚠ The population is derived — "every entity that declares this field" — not listed.
     * The previous version of this method restricted itself to {@code BaseEntity} subclasses on
     * the stated assumption that nothing else declares them, and 960 declarations disagreed.
     */
    @Test
    void noEntityAcceptsAnAuditOrSoftDeleteFieldFromARequestBody() throws Exception {
        ObjectMapper mapper = appMapper();
        List<Class<?>> entities = allEntities();
        Set<String> leaks = new TreeSet<>();
        int verified = 0;

        for (String fieldName : AUDIT_FIELDS) {
            for (Class<?> e : entities) {
                Field f = findField(e, fieldName);
                if (f == null) {
                    continue;
                }
                Hostile h = hostileFor(f.getType());
                String body = "{ \"" + fieldName + "\": " + h.json() + " }";
                // No try/catch, for the reason recorded on boundId: an entity that cannot be
                // deserialised must fail this test rather than be scored as clean.
                Object o = mapper.readValue(body, e);
                f.setAccessible(true);
                if (bound(f.get(o), h.expected())) {
                    leaks.add(e.getSimpleName() + "." + fieldName);
                } else {
                    verified++;
                }
            }
        }

        System.out.println("[F2-215] (entity, server-owned field) pairs verified = " + verified
            + " / still bind = " + leaks.size());
        leaks.forEach(s -> System.out.println("  - " + s));

        // ⚠⚠ Anti-vacuity, and the FLOOR IS CHOSEN TO CATCH THIS SLICE'S OWN DEFECT, not merely
        // a broken scan. Measured 2,352 pairs today. Restoring the `if (!BaseEntity.class
        // .isAssignableFrom(e)) continue;` that this slice deleted leaves 174 subclasses x 8
        // fields = ~1,392 — so a floor of 1,000 would have SURVIVED the mutation that recreates
        // the original blind spot, and read as coverage.
        //
        // 🔑 A floor set to "obviously not broken" cannot see a population narrowed to a real
        // subset. It has to sit above the largest wrong answer, which means knowing what the
        // wrong answer would be.
        assertThat(verified + leaks.size())
            .as("The (entity, field) population fell below the ~2,352 pairs measured for the "
                + "full corpus. Anything near 1,392 means the leg has been narrowed back to "
                + "BaseEntity subclasses — the exact F2-215 defect. 🔑 A narrowed population "
                + "reads exactly like a clean one.")
            .isGreaterThan(2_000);

        assertThat(leaks)
            .as("An entity accepted a server-owned field from JSON. Proven live 2026-08-15 on "
                + "POST /api/v1/controls: createdBy was stored as the caller sent it (forged "
                + "audit attribution — the DF-3/DF-4 defect), createdAt was backdated to 2001, "
                + "and isDeleted:true created a row invisible to every query while the UNIQUE "
                + "index still saw it, permanently burning that control code. Fix it in "
                + "ServerOwnedFieldsModule; do not add an exemption.")
            .isEmpty();
    }

    /**
     * ⚠⚠ THE LEG THAT WOULD HAVE CAUGHT F2-206's GAP: the module's list must EQUAL
     * {@link BaseEntity}'s own {@code READ_ONLY} set, in both directions.
     *
     * <p>F2-206 generalised {@code BaseEntity}'s protection to every mapped type for one of the
     * nine fields it declares. Nothing could tell, because the mechanism was visibly present and
     * a reader who finds it does not then go and check which fields it names.
     *
     * <p>Both directions matter and they fail for different reasons. <b>Module ⊂ BaseEntity</b>
     * is the F2-215 defect itself: a field the superclass protects for its 174 subclasses and
     * the module does not protect for the other 259. <b>BaseEntity ⊂ Module</b> is the reverse
     * drift: a field this module protects platform-wide that {@code BaseEntity} has stopped
     * protecting, which would mean somebody deleted an annotation and only half the platform
     * noticed.
     */
    @Test
    void theModuleMirrorsBaseEntitysOwnFieldList() {
        Set<String> baseEntityReadOnly = new TreeSet<>();
        for (Field f : BaseEntity.class.getDeclaredFields()) {
            JsonProperty jp = f.getAnnotation(JsonProperty.class);
            if (jp != null && jp.access() == JsonProperty.Access.READ_ONLY) {
                baseEntityReadOnly.add(f.getName());
            }
        }
        System.out.println("[F2-215] BaseEntity READ_ONLY fields = " + baseEntityReadOnly);
        System.out.println("[F2-215] module SERVER_OWNED_FIELDS  = "
            + new TreeSet<>(ServerOwnedFieldsModule.SERVER_OWNED_FIELDS));

        assertThat(baseEntityReadOnly)
            .as("BaseEntity stopped marking fields READ_ONLY, so this mirror has nothing to "
                + "check against and the module's list became an unanchored opinion.")
            .isNotEmpty();

        assertThat(ServerOwnedFieldsModule.SERVER_OWNED_FIELDS)
            .as("ServerOwnedFieldsModule and BaseEntity disagree about which fields the server "
                + "owns. Whichever way round, the platform is protecting the 174 BaseEntity "
                + "subclasses and the other 259 entities differently — which is precisely the "
                + "F2-215 defect: F2-206 generalised ONE of BaseEntity's nine fields and "
                + "nothing could tell for two months. Change both, in the same commit.")
            .isEqualTo(baseEntityReadOnly);
    }

    /**
     * NEGATIVE CONTROL: without the module, a non-{@code BaseEntity} entity DOES bind them.
     *
     * <p>Without this leg the assertions above are indistinguishable from vacuous ones — Jackson
     * might be refusing these fields for some unrelated reason (a missing setter, a visibility
     * rule), in which case the module could be deleted and the gate would stay green. This is
     * the leg that proves the module is what does the work, and it is exactly the check whose
     * absence let F2-205 ship two gate holes.
     *
     * <p>⚠ It asserts EVERY field, not one. A control that exercises a single field would have
     * passed on F2-206's tree, where {@code id} was covered and the other eight were not — so a
     * per-field control is the only shape that can see this slice's own defect.
     */
    @Test
    void theModuleIsWhatBlocksThem_notSomethingElse() throws Exception {
        ObjectMapper withoutModule = withModules(new ObjectMapper());

        // A real entity, chosen because it is the one the live probe destroyed, and because it
        // does NOT extend BaseEntity — the whole point of the population this class widens.
        Class<?> ropa = Class.forName("com.tss.grc.module.privacy.entity.ProcessingActivity");
        assertThat(BaseEntity.class.isAssignableFrom(ropa))
            .as("ProcessingActivity was chosen for this control BECAUSE it sits outside "
                + "BaseEntity. If it is ever made to extend it, this control stops testing the "
                + "uncovered population and must be repointed at another entity that does not.")
            .isFalse();

        assertThat(boundId(withoutModule, ropa, HOSTILE_ID))
            .as("Without the module the id binds — that is the defect, and it is what makes the "
                + "positive assertion meaningful rather than vacuous.")
            .isEqualTo(999999L);
        assertThat(boundId(appMapper(), ropa, HOSTILE_ID))
            .as("With the module the same hostile body leaves id null.")
            .isNull();

        // ⚠ ProcessingActivity declares only 3 of the 8, so controlling on it alone would leave
        // 5 of this slice's own fields uncontrolled — the very shape being fixed. The second
        // subject is DERIVED: whichever entity outside BaseEntity declares the most of them, so
        // the control always exercises the widest case available rather than a name chosen once
        // and never revisited.
        Class<?> widest = allEntities().stream()
            .filter(c -> !BaseEntity.class.isAssignableFrom(c))
            .max(java.util.Comparator.comparingLong(c ->
                AUDIT_FIELDS.stream().filter(n -> findField(c, n) != null).count()))
            .orElseThrow();

        int controlled = 0;
        for (Class<?> subject : List.of(ropa, widest)) {
            for (String fieldName : AUDIT_FIELDS) {
                Field f = findField(subject, fieldName);
                if (f == null) {
                    continue;
                }
                f.setAccessible(true);
                Hostile h = hostileFor(f.getType());
                String body = "{ \"" + fieldName + "\": " + h.json() + " }";

                assertThat(bound(f.get(withoutModule.readValue(body, subject)), h.expected()))
                    .as("Without the module, %s.%s must bind — otherwise something else is "
                        + "refusing it and this gate proves nothing about the module.",
                        subject.getSimpleName(), fieldName)
                    .isTrue();
                assertThat(bound(f.get(appMapper().readValue(body, subject)), h.expected()))
                    .as("With the module, %s.%s must not bind.",
                        subject.getSimpleName(), fieldName)
                    .isFalse();
                controlled++;
            }
        }
        long widestFields = AUDIT_FIELDS.stream()
            .filter(n -> findField(widest, n) != null).count();
        System.out.println("[F2-215] negative control covered " + controlled + " (entity, field) "
            + "pairs across ProcessingActivity and " + widest.getSimpleName()
            + " (" + widestFields + " of " + AUDIT_FIELDS.size() + " fields)");

        assertThat(widestFields)
            .as("No entity outside BaseEntity declares all eight audit fields any more, so this "
                + "control can no longer exercise every field the module protects. Say which "
                + "fields are now uncontrolled rather than lowering this number.")
            .isEqualTo(AUDIT_FIELDS.size());
        assertThat(controlled)
            .as("The negative control silently covered fewer pairs than the two subjects "
                + "declare, which would make it partly decorative — and a control that covers "
                + "SOME fields is exactly what let F2-206's id-only fix read as complete.")
            .isGreaterThanOrEqualTo(AUDIT_FIELDS.size() + 3);
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
     * something that genuinely cannot be verified and watch it refuse.
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
     * SECOND SELF-TEST (F2-215): the value builder must REFUSE an unknown type, not skip it.
     *
     * <p>The bug this prevents is the one that produced the finding: F2-206's single-literal
     * body silently dropped 35 entities whose {@code isDeleted} is a {@code Short}, and a
     * dropped entity scores exactly like a clean one. If {@link #hostileFor} ever returns
     * something harmless for a type it does not know, the population shrinks in silence.
     */
    @Test
    void theHostileValueBuilderRefusesATypeItDoesNotKnow() {
        Throwable thrown = null;
        try {
            hostileFor(java.util.UUID.class);
        } catch (Throwable t) {
            thrown = t;
        }
        assertThat(thrown)
            .as("hostileFor must throw on a type it has no literal for. Returning null or "
                + "a placeholder would drop every entity declaring that type out of the "
                + "population, and a dropped entity reads exactly like a verified one — which "
                + "is how the Short-typed isDeleted came to look safe.")
            .isInstanceOf(AssertionError.class);
        assertThat(thrown).hasMessageContaining("No hostile JSON value");
    }

    /**
     * The module must be a Spring bean, or none of the above protects the running application.
     *
     * <p>⚠ Every other leg registers the module <b>by hand</b> on a local {@link ObjectMapper},
     * so they prove what the module DOES and say nothing about whether the app ever loads it.
     * Deleting {@code @Component} would leave this whole class green and the platform exactly as
     * exposed as before — the same shape as F2-202's finding that a reachability scan sees a
     * symbol being called but not whether calling it does anything.
     */
    @Test
    void theModuleIsRegisteredWithSpringAndNotOnlyWithThisTest() {
        assertThat(com.fasterxml.jackson.databind.Module.class
                .isAssignableFrom(ServerOwnedFieldsModule.class))
            .as("Spring Boot only auto-registers beans that ARE a Jackson Module. If this stops "
                + "being one, it must be wired into Jackson2ObjectMapperBuilder explicitly.")
            .isTrue();

        assertThat(ServerOwnedFieldsModule.class
                .isAnnotationPresent(org.springframework.stereotype.Component.class))
            .as("ServerOwnedFieldsModule is not a Spring bean, so the running application does "
                + "NOT have it and every other assertion in this class — all of which register "
                + "it by hand — would stay green over a fully exposed platform.")
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
