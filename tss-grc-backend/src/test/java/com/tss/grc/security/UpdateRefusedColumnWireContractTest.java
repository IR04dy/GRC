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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-287 — a gate's population can be scoped by the HTTP <b>verb</b>, and [[F2-286]]'s was.
 *
 * <h2>The finding</h2>
 *
 * <p>{@code RefusedColumnWireContractTest.population()} reads
 * {@code if (col == null || col.insertable()) continue;} over entities bound by a
 * {@code @PostMapping @RequestBody}. Its claim — <i>"a column the mapping refuses must not be
 * accepted at the wire"</i> — is therefore scoped to the INSERT half. The mirror is
 * {@code updatable = false} on an entity bound by a {@code @PutMapping}/{@code @PatchMapping}:
 * the caller sends the field, Hibernate omits the column from the UPDATE, and nothing happens.
 *
 * <p>🔑 That is F2-198's shape again — the invariant is real, and its population is a
 * mechanism. Here the narrowing is neither a filename, an allow-list, a module name nor an
 * {@code extends} clause: it is <b>which verb the slice was looking at</b>.
 *
 * <h2>And F2-286 filed its own remaining member on a premise that was wrong twice</h2>
 *
 * <p>It recorded {@code ethics.aml_alerts.first_detected_at} as <i>"not the same defect …
 * enabling it means widening applyEditable, a product call"</i>.
 *
 * <ul>
 *   <li><b>The mechanism was wrong.</b> {@code AmlAlertService.applyEditable} is called by
 *       {@code create} <em>and</em> {@code update}, and the column is
 *       {@code updatable = false} — so a write there runs on the update path and changes
 *       nothing. Measured over the whole tree: <b>no service writes an
 *       {@code updatable = false} column on a row it loaded</b>, so the filed fix would have
 *       created the first instance of that shape. {@link
 *       #theSharedHelperDoesNotWriteAColumnTheMappingRefusesOnUpdate()} pins it.</li>
 *   <li><b>The ownership was wrong.</b> The alert "surfaces from the bank's TM engine", so the
 *       detection time is received from outside — [[F2-268]]'s third kind, which F2-209 already
 *       settles: received and recorded, or absent. Not a product decision.</li>
 * </ul>
 *
 * <p>🔑 <b>The slice that discovers a rule is the worst-placed slice to apply it to its own
 * leftovers</b>, because the leftover gets filed with the reasoning that was current while the
 * rule was still forming.
 *
 * <h2>Why the census carries no verdicts</h2>
 *
 * <p>The residue is a field the caller may send on an update and the server discards
 * ([[F2-214]]'s shape) — mild, and dominated by {@code external_id}, which is deliberately
 * <em>not</em> one of {@code ServerOwnedFieldsModule}'s nine. Writing a reason for each without
 * reading it is [[F2-188]]'s measured failure mode, so this holds the split as a measurement
 * and files the list.
 */
class UpdateRefusedColumnWireContractTest {

    /**
     * Measured 2026-08-23 <b>by this gate</b>, never predicted (F2-279: a baseline lifted from
     * a prediction is worse than none, because it looks measured).
     *
     * <p>Population 40; Jackson accepts 28 of them on an update-bound entity and refuses 12.
     * 🔑 <b>27 of the 28 are {@code externalId}</b>, which is deliberately not one of
     * {@code ServerOwnedFieldsModule}'s nine — so the same field is <em>insertable</em> on the
     * POST those entities also bind, which is [[F2-172]](d)'s family and is filed as its own
     * census. The 28th is {@code AmlAlert#firstDetectedAt}, which this slice made insertable on
     * purpose and closed in the <em>service</em> with an echo guard; this gate asks Jackson, so
     * it cannot see that, which is why the figure is decrease-only rather than a hard zero.
     */
    private static final int BASELINE_ACCEPTED_ON_UPDATE = 28;

    private static final Path AML_ALERT = Path.of(
            "src", "main", "java", "com", "tss", "grc", "module", "ethics",
            "entity", "AmlAlert.java");
    private static final Path AML_SERVICE = Path.of(
            "src", "main", "java", "com", "tss", "grc", "module", "ethics",
            "service", "AmlAlertService.java");

    private static String code(Path p) throws IOException {
        return GuardExpressions.stripComments(Files.readString(p, StandardCharsets.UTF_8));
    }

    // ─── the population, derived ────────────────────────────────────────────

    private static Set<Class<?>> scan(Class<? extends Annotation> ann) {
        ClassPathScanningCandidateComponentProvider p =
                new ClassPathScanningCandidateComponentProvider(false);
        p.addIncludeFilter(new AnnotationTypeFilter(ann));
        Set<Class<?>> out = new LinkedHashSet<>();
        for (BeanDefinition bd : p.findCandidateComponents("com.tss.grc")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                java.security.CodeSource cs = c.getProtectionDomain().getCodeSource();
                if (cs != null && cs.getLocation() != null
                        && cs.getLocation().toString().contains("test-classes")) {
                    continue;
                }
                out.add(c);
            } catch (ClassNotFoundException ignored) {
                // named by the scanner and unresolvable by the loader is not a finding
            }
        }
        return out;
    }

    /** Entities bound by a {@code @PutMapping} or {@code @PatchMapping} {@code @RequestBody}. */
    private static Set<Class<?>> entityBoundOnUpdate() {
        Set<Class<?>> out = new LinkedHashSet<>();
        for (Class<?> ctl : scan(RestController.class)) {
            for (Method m : ctl.getDeclaredMethods()) {
                if (m.getAnnotation(PutMapping.class) == null
                        && m.getAnnotation(PatchMapping.class) == null) continue;
                for (Parameter prm : m.getParameters()) {
                    if (prm.getAnnotation(RequestBody.class) == null) continue;
                    if (prm.getType().getAnnotation(Entity.class) != null) out.add(prm.getType());
                }
            }
        }
        return out;
    }

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
        Set<String> owned = ServerOwnedFieldsModule.SERVER_OWNED_FIELDS;
        List<Site> out = new ArrayList<>();
        for (Class<?> e : entityBoundOnUpdate()) {
            for (Class<?> c = e; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    Column col = f.getAnnotation(Column.class);
                    if (col == null || col.updatable()) continue;
                    if (owned.contains(f.getName())) continue;
                    out.add(new Site(e, f));
                }
            }
        }
        out.sort((a, b) -> a.toString().compareTo(b.toString()));
        return out;
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(new ServerOwnedFieldsModule());
    }

    /**
     * Asks <b>Jackson</b>, by identity and never by presence: an initialised field is non-null
     * whether the hostile value arrived or not ([[F2-215]]). Immune to annotation position, to
     * which annotation was used, and to a mixin ([[F2-286]]).
     */
    private static boolean acceptedAtTheWire(ObjectMapper om, Site s) throws Exception {
        Object sent = hostile(s.field().getType());
        String json = om.writeValueAsString(java.util.Map.of(s.field().getName(), sent));
        Object bound = om.readValue(json, s.entity());
        Field f = s.field();
        f.setAccessible(true);
        Object got = f.get(bound);
        return got != null && String.valueOf(got).contains(
                String.valueOf(sent).replace("\"", "").split("T")[0].replace("[REVIEW-TEST] ", ""));
    }

    // ─── the census ─────────────────────────────────────────────────────────

    @Test
    void theMirrorPopulationIsMeasuredAndSplitByWhatJacksonDoes() throws Exception {
        ObjectMapper om = mapper();
        List<String> accepted = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (Site s : population()) {
            (acceptedAtTheWire(om, s) ? accepted : refused).add(s.toString());
        }
        System.out.println("F2-287 · updatable=false on an entity bound by PUT/PATCH: "
                + population().size() + "  (accepted at the wire " + accepted.size()
                + ", refused " + refused.size() + ")");
        accepted.forEach(a -> System.out.println("   ACCEPTED-AND-DISCARDED  " + a));

        assertThat(accepted.size())
                .as("A column mapped updatable = false is omitted from Hibernate's UPDATE, so a "
                    + "value the caller sends on a PUT/PATCH is silently discarded. That is "
                    + "F2-214's shape and this figure may only ever DECREASE — close a member "
                    + "with @JsonProperty(access = READ_ONLY), or make it echo-only in the "
                    + "service the way AmlAlertService does for firstDetectedAt.")
                .isLessThanOrEqualTo(BASELINE_ACCEPTED_ON_UPDATE);

        // Anti-vacuity by MEASUREMENT rather than by a floor on the total: a detector that
        // stopped resolving reports a smaller, cleaner-looking population (F2-69), and the
        // shape of this one is that it is overwhelmingly a single field name.
        assertThat(accepted.stream().filter(a -> a.endsWith("#externalId")).count())
                .as("externalId is NOT one of ServerOwnedFieldsModule's nine, which is why it "
                    + "dominates this census — and why the same field being INSERTABLE on the "
                    + "POST these entities also bind is filed as its own finding")
                .isGreaterThanOrEqualTo(25);
    }

    /**
     * Anti-vacuity. The wrong answers this guards against are a scan that resolves no
     * controllers and one that resolves no entities — both report a SMALLER population, which
     * reads exactly like a clean tree.
     */
    @Test
    void thePopulationIsRealAndNonTrivial() {
        assertThat(entityBoundOnUpdate())
                .as("entities bound by a PUT/PATCH @RequestBody")
                .hasSizeGreaterThan(40);
        assertThat(population())
                .as("updatable=false fields on them, outside ServerOwnedFieldsModule's nine")
                .hasSizeGreaterThan(30);
    }

    // ─── the closed member ──────────────────────────────────────────────────

    /**
     * F2-287's fix. {@code insertable = false} refuses Hibernate as well as the caller, so the
     * column could only ever hold {@code sysutcdatetime()} at the instant the row was inserted
     * — and the FIU reporting clock starts when the TM engine detected the activity.
     */
    @Test
    void theDetectionTimeIsInsertableAndStillNotUpdatable() throws Exception {
        Column col = Class.forName("com.tss.grc.module.ethics.entity.AmlAlert")
                .getDeclaredField("firstDetectedAt").getAnnotation(Column.class);
        assertThat(col.insertable())
                .as("insertable = false meant NOTHING could state when the TM engine detected "
                    + "the activity: the column could only hold its insert instant")
                .isTrue();
        assertThat(col.updatable())
                .as("a detection time is stated once — F2-287 widened the insert, not the update")
                .isFalse();
        assertThat(col.nullable())
                .as("NOT NULL, which is why the create must supply a default")
                .isFalse();
    }

    /**
     * 🔑 The refuted mechanism, pinned. {@code applyEditable} is shared by {@code create} and
     * {@code update}; a write to an {@code updatable = false} column there would compile, run
     * and change nothing.
     */
    @Test
    void theSharedHelperDoesNotWriteAColumnTheMappingRefusesOnUpdate() throws IOException {
        String src = code(AML_SERVICE);
        int at = src.indexOf("private void applyEditable(");
        assertThat(at).as("applyEditable must still exist to be checked").isGreaterThan(0);
        int end = src.indexOf("\n    }", at);
        assertThat(src.substring(at, end))
                .as("F2-286 filed 'widen applyEditable' as the fix. That helper is called by "
                    + "create AND update, and first_detected_at is updatable = false — so the "
                    + "write would be a no-op on the update path, the first instance in this "
                    + "tree of a shape whose census is zero.")
                .doesNotContain("setFirstDetectedAt");
        assertThat(src)
                .as("the create path is where the detection time is recorded")
                .containsPattern("setFirstDetectedAt\\s*\\(\\s*detectionTime\\s*\\(");
    }

    /**
     * Because the mapping refuses a change, the wire must not appear to accept one. An echo
     * keeps a round-tripping form working; a different value is refused rather than dropped
     * ([[F2-231]]/[[F2-234]]).
     */
    @Test
    void theUpdatePathEchoGuardsTheDetectionTime() throws IOException {
        assertThat(code(AML_SERVICE))
                .containsPattern(
                        "requireEchoInstant\\s*\\(\\s*\"firstDetectedAt\"\\s*,\\s*"
                        + "req\\.getFirstDetectedAt\\(\\)\\s*,\\s*a\\.getFirstDetectedAt\\(\\)");
        assertThat(code(AML_SERVICE))
                .as("a future detection time is the one value that could only ever be forged")
                .contains("cannot be in the future");
    }

    /**
     * The stripper is load-bearing: the new entity javadoc and the service comments both quote
     * the {@code insertable = false} mapping they replaced, so a raw read would count the
     * epitaph as the corpse ([[F2-193]]).
     */
    @Test
    void theStripperIsSound() throws IOException {
        String raw = Files.readString(AML_ALERT, StandardCharsets.UTF_8);
        // ⚠ The first draft of this leg was copied from F2-286's and asserted the stripped
        // source contains NO "insertable = false" — which fails on correct code, because
        // AmlAlert.externalId is still mapped that way. A gate that fails on correct code
        // trains the next reader to edit the assertion rather than the code (F2-214). The
        // claim is that the stripper removes the QUOTED one and keeps the real mapping.
        assertThat(occurrences(raw, "insertable = false"))
                .as("the javadoc quotes the mapping it replaced, plus externalId's real one")
                .isGreaterThan(occurrences(code(AML_ALERT), "insertable = false"));
        // ⚠ I predicted 1 here and the gate measured 2 — externalId AND createdAt are both
        // really mapped that way. Set a figure from the instrument's own output, never from a
        // prediction (F2-279), even when the prediction is about a file you have just edited.
        assertThat(occurrences(code(AML_ALERT), "insertable = false"))
                .as("two survive stripping: externalId and createdAt really are mapped so")
                .isEqualTo(2);
        // GuardExpressions.stripComments REMOVES comment lines rather than blanking them
        // (F2-286), so an offset from its output does not refer to the file. Every assertion
        // here is a contains or a count, which is why that is safe.
        assertThat(code(AML_ALERT).length()).isLessThan(raw.length());
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }
}
