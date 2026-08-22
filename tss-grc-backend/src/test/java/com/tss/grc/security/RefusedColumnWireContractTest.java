package com.tss.grc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.json.ServerOwnedFieldsModule;
import com.tss.grc.support.GuardExpressions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-286 — a column the mapping refuses must not be accepted at the wire.
 *
 * <h2>The finding</h2>
 *
 * <p>[[F2-285]] fixed five columns on {@code privacy.data_breaches} that the database
 * refused and the create response echoed, because {@code createBreach} returns
 * {@code breachRepo.save(req)} — the caller's own deserialised instance. Its headline was
 * <i>"unwritable to the database is not absent from the response."</i>
 *
 * <p>Measured the next day, that was five members of a fourteen-member shape:
 * {@code insertable = false} fields, outside {@code ServerOwnedFieldsModule}'s nine, on an
 * entity a {@code @PostMapping @RequestBody} binds. F2-215's two-of-N landing on the slice
 * that had just shipped.
 *
 * <p>🔑 <b>The discriminator is not "does the endpoint bind the entity" — it is "does the
 * handler return the instance Jackson bound".</b> Eight of the nine survivors are immune
 * because their service builds its own target and copies a whitelist
 * ({@code AmlAlert a = new AmlAlert(); applyEditable(a, req); return repo.save(a);}), which
 * is the shape [[F2-213]] drove this platform toward — a second dividend from that slice.
 * The one that was not immune was {@code KycService#recordHit}, which does
 * {@code return hitRepo.save(body)}.
 *
 * <h2>What this gate claims, and what it deliberately does not</h2>
 *
 * <p>The claim is narrow and does not presume who <i>should</i> own a fact: <b>if the
 * mapping refuses the value, the wire must not accept it. If the caller should be able to
 * state it, make the column insertable.</b> That is what this slice did for
 * {@code ethics.screening_hits.screened_at} — {@code insertable = false} meant no API caller
 * and no Hibernate write could <em>ever</em> state when a screening ran, so the column could
 * only hold the instant its row was inserted. Measured: all 12 live rows in both databases
 * are microsecond-precision instants in three seed-insert batches on one day, while
 * {@code decided_at} beside them holds round business times — and two hits read as decided
 * <b>370 days before they were screened</b>. The precision is the tell.
 *
 * <p>⚠ It asks <b>Jackson</b>, not the annotations. A first draft of the measurement read the
 * annotation block <i>between</i> {@code @Column} and the field and reported eight already-fixed
 * fields as open, because the annotation sits one line <i>above</i> {@code @Column} — and
 * annotation order is meaningless to Java. A detector keyed on a position cannot see a claim.
 * Deserialising a hostile body is also immune to which annotation was used, and to a mixin.
 *
 * <p>⚠ {@code ServerOwnedFieldsModule}'s nine are excluded and the exclusion is read from the
 * module rather than retyped. They are a <i>policy</i> about server-owned identity, already
 * gated by {@code ServerOwnedFieldsInvariantTest}; this is a claim about a <i>column</i>. Two
 * instruments answering one question is this programme's own recurring defect ([[F2-190]]), so
 * the populations are kept distinct rather than overlapping.
 */
class RefusedColumnWireContractTest {

    private static final Path SCREENING_HIT = Path.of(
            "src", "main", "java", "com", "tss", "grc", "module", "ethics",
            "entity", "ScreeningHit.java");
    private static final Path KYC_SERVICE = Path.of(
            "src", "main", "java", "com", "tss", "grc", "module", "ethics",
            "service", "KycService.java");

    private static String code(Path p) throws IOException {
        return GuardExpressions.stripComments(Files.readString(p, StandardCharsets.UTF_8));
    }

    // ─── the population, derived ────────────────────────────────────────────

    /** Read from the module, never retyped — F2-219's rule for a subtraction. */
    private static Set<String> serverOwnedNames() {
        return ServerOwnedFieldsModule.SERVER_OWNED_FIELDS;
    }

    private static Set<Class<?>> scan(Class<? extends Annotation> ann) {
        ClassPathScanningCandidateComponentProvider p =
                new ClassPathScanningCandidateComponentProvider(false);
        p.addIncludeFilter(new AnnotationTypeFilter(ann));
        Set<Class<?>> out = new LinkedHashSet<>();
        for (BeanDefinition bd : p.findCandidateComponents("com.tss.grc")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                // Exclude anything whose code source is the TEST tree: F2-241 found a
                // deliberately-unprobeable synthetic fixture this way, and excluding by a
                // name ending in "Test" would let any member escape by being renamed.
                java.security.CodeSource cs = c.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null
                        && cs.getLocation().toString().contains("test-classes")) {
                    continue;
                }
                out.add(c);
            } catch (ClassNotFoundException ignored) {
                // a class the scanner named and the loader cannot resolve is not a finding
            }
        }
        return out;
    }

    /** Entities bound by a {@code @PostMapping} {@code @RequestBody}. */
    private static Set<Class<?>> entityBoundOnPost() {
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Class<?> ctl : scan(RestController.class)) {
            for (Method m : ctl.getDeclaredMethods()) {
                if (m.getAnnotation(PostMapping.class) == null) continue;
                for (Parameter prm : m.getParameters()) {
                    if (prm.getAnnotation(RequestBody.class) == null) continue;
                    if (prm.getType().getAnnotation(Entity.class) != null) out.add(prm.getType());
                }
            }
        }
        return out;
    }

    /** A value of the field's own declared type. Throws rather than skipping (F2-215). */
    private static Object hostile(Class<?> t) {
        if (t == String.class) return "[REVIEW-TEST] forged";
        if (t == UUID.class) return "11111111-2222-3333-4444-555555555555";
        if (t == LocalDateTime.class) return "2001-01-01T00:00:00";
        if (t == LocalDate.class) return "2001-01-01";
        if (t == Short.class || t == short.class) return 7;
        if (t == Integer.class || t == int.class) return 999;
        if (t == Long.class || t == long.class) return 999999L;
        if (t == Boolean.class || t == boolean.class) return true;
        if (t == BigDecimal.class) return 42;
        throw new IllegalStateException(
                "no hostile value for " + t.getName() + " — add one rather than skipping the "
                + "field, because a skipped field scores exactly like a clean one");
    }

    private record Site(Class<?> entity, Field field) {
        @Override public String toString() {
            return entity.getSimpleName() + "#" + field.getName();
        }
    }

    private static List<Site> population() {
        Set<String> owned = serverOwnedNames();
        List<Site> out = new ArrayList<>();
        for (Class<?> e : entityBoundOnPost()) {
            for (Class<?> c = e; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    Column col = f.getAnnotation(Column.class);
                    if (col == null || col.insertable()) continue;
                    if (owned.contains(f.getName())) continue;
                    out.add(new Site(e, f));
                }
            }
        }
        out.sort((a, b) -> a.toString().compareTo(b.toString()));
        return out;
    }

    /** Mirrors Spring Boot's configuration, not a bare mapper (F2-206's lesson). */
    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(new ServerOwnedFieldsModule());
    }

    // ─── the invariant ──────────────────────────────────────────────────────

    /**
     * Hard zero, no exemption map. There is always {@code @JsonProperty(access = READ_ONLY)},
     * and where the caller genuinely should state the value the column can be made insertable
     * — so there is no correct code for this to fail on ([[F2-214]]).
     */
    @Test
    void noColumnTheMappingRefusesIsAcceptedAtTheWire() throws Exception {
        ObjectMapper om = mapper();
        List<String> open = new ArrayList<>();
        for (Site s : population()) {
            Object sent = hostile(s.field().getType());
            String json = om.writeValueAsString(java.util.Map.of(s.field().getName(), sent));
            Object bound = om.readValue(json, s.entity());
            s.field().setAccessible(true);
            Object got = s.field().get(bound);
            // Identity, never presence: an initialised field is non-null either way (F2-215).
            if (got != null && String.valueOf(got).equals(String.valueOf(
                    om.convertValue(sent, s.field().getType())))) {
                open.add(s + " (" + s.field().getType().getSimpleName() + ")");
            }
        }
        assertThat(open)
                .as("A column mapped insertable = false is refused by the DATABASE and not by "
                    + "Jackson, and a create that returns the instance it bound then echoes a "
                    + "value the row does not carry — only a re-fetch shows the truth (F2-285). "
                    + "Close it with @JsonProperty(access = READ_ONLY), or, if the caller should "
                    + "be able to state it, make the column insertable and have the create "
                    + "supply a default (F2-286 did that for screened_at).")
                .isEmpty();
    }

    /** Anti-vacuity: a floor above the largest WRONG answer, not above zero (F2-215). */
    @Test
    void thePopulationIsRealAndNonTrivial() {
        List<Site> pop = population();
        Set<String> entities = new TreeSet<>();
        for (Site s : pop) entities.add(s.entity().getSimpleName());
        // Measured 2026-08-22: 13 fields on 8 entities (14 before screened_at became
        // insertable). A wrong answer here is a scan that resolves no controllers, or one
        // that stops matching @Column's attributes -- both of which report ZERO, which is
        // exactly what a finished sweep reports.
        assertThat(pop).as("population of insertable=false fields on entity-bound POST bodies")
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(entities).hasSizeGreaterThanOrEqualTo(5);
        assertThat(entityBoundOnPost())
                .as("entities bound by a @PostMapping @RequestBody")
                .hasSizeGreaterThanOrEqualTo(40);
    }

    /** The exclusion has to be the module's own list, or it is an assumption with a citation. */
    @Test
    void theServerOwnedExclusionComesFromTheModule() {
        Set<String> owned = serverOwnedNames();
        assertThat(owned).contains("id", "createdAt", "updatedAt", "isDeleted", "rowVersion");
        assertThat(owned).doesNotContain("externalId");   // per-service by decision (F2-216)
        assertThat(owned).hasSize(9);
    }

    // ─── the member this slice moved the other way ──────────────────────────

    /**
     * {@code screened_at} became insertable so the screening run can state when it ran. The
     * default is not tidiness: the column is NOT NULL, so an omitted value would now reach the
     * INSERT as null. The pair has to hold together, which is why one test asserts both.
     */
    @Test
    void screenedAtIsInsertableAndTheCreateSuppliesItWhenTheCallerDoesNot() throws IOException {
        Field f;
        try {
            f = Class.forName("com.tss.grc.module.ethics.entity.ScreeningHit")
                    .getDeclaredField("screenedAt");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ethics ScreeningHit#screenedAt is gone", e);
        }
        Column col = f.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.insertable())
                .as("insertable = false meant NOTHING could state when a screening ran: the "
                    + "column could only ever hold the instant its row was inserted")
                .isTrue();
        assertThat(col.updatable())
                .as("when a screening ran is a fact about a past event, not a field to edit")
                .isFalse();
        assertThat(col.nullable()).isFalse();

        String svc = code(KYC_SERVICE);
        assertThat(svc)
                .as("the column is NOT NULL and now insertable, so an omitted value would reach "
                    + "the INSERT as null — recordHit MUST supply the clock")
                .containsPattern(Pattern.compile(
                        "body\\.setScreenedAt\\(\\s*LocalDateTime\\.now\\(\\)\\s*\\)"));
        assertThat(svc)
                .as("a screening cannot have happened in the future, and refusing that is what "
                    + "makes the widening safe — it is the one value that could only be forged")
                .containsPattern(Pattern.compile("getScreenedAt\\(\\)\\.isAfter\\("));
    }

    /**
     * Comment-stripping is load-bearing: this entity's new javadoc and {@code recordHit}'s
     * comment both quote the {@code insertable = false} mapping they replaced, so a raw read
     * counts the epitaph as the corpse ([[F2-193]]).
     */
    @Test
    void theStripperIsSound() throws IOException {
        String raw = Files.readString(SCREENING_HIT, StandardCharsets.UTF_8);
        assertThat(raw)
                .as("the javadoc explains the change by quoting what it replaced")
                .contains("insertable = false");
        String stripped = code(SCREENING_HIT);
        assertThat(stripped)
                .as("and after stripping, the retired mapping is gone from the code")
                .doesNotContain("insertable = false");
        // ⚠ GuardExpressions.stripComments REMOVES the comment's lines rather than blanking
        // them — measured here, 76 lines become 59 — so a line number taken from its output
        // does not refer to the file. Every assertion in this class is a `contains`, which is
        // why that is safe; a scan that reports positions must blank in place instead.
        assertThat(stripped.split("\n", -1).length)
                .as("the helper collapses comment lines, so its offsets are not the file's")
                .isLessThan(raw.split("\n", -1).length);
    }
}
