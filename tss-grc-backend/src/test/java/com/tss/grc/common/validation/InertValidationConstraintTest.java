package com.tss.grc.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;

/**
 * F2-184 — a bean-validation constraint on a type no endpoint ever validates enforces nothing,
 * and is <b>worse than no constraint at all</b>, because it answers the question a reader was
 * about to ask.
 *
 * <p>The confirmed instance (found while working F2-182): {@code CalendarEventDto} declared
 * {@code @Pattern(regexp = "NONE|DAILY|…")} on {@code recurrence}. That class is the
 * <b>outbound</b> payload built by {@code fromEntity()}; both endpoints bind the nested
 * {@code CalendarEventDto.CreateRequest}, whose {@code recurrence} carried nothing. A lower-case
 * {@code "monthly"} was therefore stored verbatim and {@code IcsBuilder.rruleFor}'s
 * {@code default -> null} emitted <b>no RRULE at all</b> — exporting a quarterly DR test to the
 * subscriber's calendar as a single event. It failed closed and silently, which is why it survived.
 *
 * <p><b>⚠ This test is reflection on purpose, and the reason is on the record.</b> F2-184 was
 * <i>filed rather than swept</i> because a source-regex sweep reported <b>75</b> dead types and the
 * first one checked was a false positive: annotations on nested records were attributed to their
 * container class, because a brace-depth walk could not track the innermost declared type through a
 * multi-line record header. That was the third phantom number from a regex over Java in three
 * findings (F2-170's 1,763 endpoints from a javadoc sentence, F2-179's 0-of-389 from {@code \b=\b},
 * this). Reflection cannot make that mistake: {@code getDeclaringClass()} is the answer, not a guess.
 *
 * <p><b>Every ambiguity here resolves toward "treat as validated."</b> A false positive invites
 * deleting a live guard, which is strictly worse than tolerating an inert one, so:
 * a generic body parameter counts <i>all</i> its type arguments as validated; a {@code @Valid} field
 * cascades to every type its generics mention; and the whole superclass chain of a validated type is
 * validated too, since Hibernate Validator walks the hierarchy. Anything this test reports as inert
 * has survived all of that.
 */
class InertValidationConstraintTest {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /** The parameter annotations through which a whole object arrives from the wire. */
    private static final List<Class<? extends Annotation>> BODY_BINDINGS = List.of(
            RequestBody.class, ModelAttribute.class, RequestPart.class);

    private static final String BASE = "com.tss.grc";

    // Anti-vacuity floors. Each one exists because a collapsed scan reports zero violations.
    private static final int MIN_TYPES_SCANNED = 2_000;
    private static final int MIN_CONTROLLERS = 200;
    private static final int MIN_HANDLERS = 800;
    private static final int MIN_CONSTRAINED_TYPES = 100;
    private static final int MIN_VALIDATED_TYPES = 100;

    /**
     * Where an inert constraint sits, which decides how it is fixed.
     *
     * <p>{@code NEVER_REFERENCED} is the calendar shape — delete it, or move the vocabulary to the
     * service. {@code FIELD_LACKS_VALID} is the one a reader must not "fix" by deleting: a validated
     * type reaches this one through a field, so the constraint was meant to be enforced and the
     * missing piece is {@code @Valid} on that field.
     */
    private enum Shape { NEVER_REFERENCED, FIELD_LACKS_VALID, BOUND_WITHOUT_VALID }

    /**
     * Two classes of false positive the first run of this test produced, both excluded here with the
     * reason stated rather than silently filtered — a filter without a reason is how a real finding
     * gets hidden.
     *
     * <p><b>(a) Test scaffolding.</b> {@code classpath*:} reaches {@code target/test-classes}, so
     * {@code StrongPasswordValidatorTest$Holder} was reported. Its {@code @StrongPassword} is
     * enforced — by a programmatic {@code Validator} in the test that owns it. Fixtures are not the
     * request surface.
     *
     * <p><b>(b) JPA entities.</b> {@code spring-boot-starter-validation} puts a provider on the
     * classpath and the JPA validation mode defaults to {@code AUTO}, so Hibernate's
     * {@code BeanValidationEventListener} validates entities on <b>pre-insert/pre-update</b>. A
     * constraint on an entity field is therefore <b>not inert</b> — it fires at flush. It is still
     * worth knowing about, because it fires as a {@code ConstraintViolationException} → <b>500</b>
     * rather than the 400-naming-the-field a bound DTO would produce, so the caller is told "server
     * error" about their own malformed input. That is a weaker defect than an unenforced constraint,
     * and conflating the two would have overstated this finding by six sites.
     */
    private static boolean isExcludedFromInertness(Class<?> c) {
        return c.isAnnotationPresent(jakarta.persistence.Entity.class);
    }

    /**
     * The constrained-but-unvalidated types this slice deliberately left, each with the reason.
     *
     * <p><b>A named list, not a count.</b> A decrease-only ceiling of "12" would let a new inert
     * constraint be swapped in for a fixed one and still pass — which is the failure mode CLAUDE.md
     * records for {@code BARE_EXPORT_CEILING}: once a sweep lands, a tolerated entry becomes
     * indistinguishable from a reviewed one. Both directions are enforced below, so an entry that is
     * fixed must be <b>removed</b> from this list in the same commit.
     */
    private static final Map<String, String> TOLERATED = Map.ofEntries(
        // ── Dark types. The defect is not the annotation; it is that nothing reaches the type. ──
        Map.entry("com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos$RetaliationTransitionRequest",
            "F2-185(a): the only consumer, WhistleblowerRetaliationService#transitionTo, has NO "
            + "caller — the retaliation terminal transition (VALIDATED/UPHELD/DISMISSED) has no "
            + "endpoint, so the complaint lifecycle cannot be closed through the API. Deleting the "
            + "@NotBlank would hide that; the fix is to wire the endpoint or delete the dead path, "
            + "and both are scope decisions."),
        Map.entry("com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos$EscalateRequest",
            "F2-185(a): no usage anywhere in main sources — dead DTO. Same call as above."),

        // ── Bound as a @RequestBody, but the parameter lacks @Valid. Every one is a @Size cap on a
        //    narrative field, and the fix is NOT simply to add @Valid: the cap has never been
        //    compared against its column, so enabling it blindly either imposes a limit nobody
        //    chose (column is NVARCHAR(MAX)) or still fails to protect it (cap looser than the
        //    column). Measured 2026-08-11: closing_summary_* and coi_note are MAX; exception_note,
        //    reviewer_comment_* and action_taken_* are exactly 1000, matching their caps. ──
        Map.entry("com.tss.grc.module.compliance.obligation.dto.ObligationDtos$CampaignCloseRequest",
            "F2-185(b): @Size(1000) vs compliance.obligation_attestation_campaign.closing_summary_* "
            + "NVARCHAR(MAX) — enabling would cap a closing summary the schema leaves unbounded."),
        Map.entry("com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos$CampaignCloseRequest",
            "F2-185(b): same shape against tprm.vendor_bcp_campaign.closing_summary_* NVARCHAR(MAX)."),
        Map.entry("com.tss.grc.module.iam.governance.dto.GovernanceDtos$CloseCampaignRequest",
            "F2-185(b): @Size(1000) == iam.access_review_campaigns.exception_note 1000, so enabling "
            + "is safe and converts a truncation 500 into a 400. Grouped with its siblings rather "
            + "than fixed alone, so one probe covers the whole shape."),
        Map.entry("com.tss.grc.module.iam.governance.dto.GovernanceDtos$ApproveAccessRequest",
            "F2-185(b): @Size(1000) on overrideJustification. ⚠ Note for whoever picks this up: it "
            + "is @Size, NOT @NotBlank — an unenforced length cap, not a missing justification. No "
            + "column of that name exists, so the target is unconfirmed."),
        Map.entry("com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos$AttestationReviewRequest",
            "F2-185(b): reviewer_comment_* are 1000 == the cap; reviewed_by_name 255 needs checking."),
        Map.entry("com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos$MonitoringEventResolveRequest",
            "F2-185(b): action_taken_* are 1000 == the cap."),
        Map.entry("com.tss.grc.module.privacy.extensions.dto.PdplExtensionDtos$NoticeArchiveRequest",
            "F2-185(b): privacy.privacy_notices.archive_reason_en is 500 — if the cap is 1000 the "
            + "annotation is LOOSER than the column and enabling it protects nothing."),
        Map.entry("com.tss.grc.module.risk.bowtie.dto.BowTieDtos$ApproveRequest",
            "F2-185(b): approverName cap vs its column unconfirmed (no bowtie approver_name found)."),
        Map.entry("com.tss.grc.module.cyber.asm.dto.AsmDtos$ShadowItBlockRequest",
            "F2-185(b): `note` — column not located by name; needs the entity read."),
        Map.entry("com.tss.grc.module.cyber.vuln_sla.dto.VulnSlaDtos$RemediatedRequest",
            "F2-185(b): `note` — same as above.")
    );

    private record Inert(Shape shape, String type, String members, String reachedFrom) {
        @Override public String toString() {
            return "  [%-17s] %s  {%s}%s".formatted(
                    shape, type, members,
                    reachedFrom == null ? "" : "   <- referenced by " + reachedFrom);
        }
    }

    @Test
    void everyValidationConstraintSitsOnATypeSomeEndpointActuallyValidates() throws Exception {
        Set<Class<?>> all = allTypesUnder(BASE);

        // ---- 1. the validated roots: a whole object arriving from the wire, marked for validation.
        Set<Class<?>> roots = new LinkedHashSet<>();
        Set<Class<?>> boundWithoutValid = new LinkedHashSet<>();
        int controllers = 0;
        int handlers = 0;
        for (Class<?> c : all) {
            if (!isController(c)) continue;
            controllers++;
            for (Method m : declaredMethodsOf(c)) {
                if (MAPPINGS.stream().noneMatch(m::isAnnotationPresent)) continue;
                handlers++;
                for (Parameter p : m.getParameters()) {
                    if (BODY_BINDINGS.stream().noneMatch(p::isAnnotationPresent)) continue;
                    Set<Class<?>> bound = unwrap(p.getParameterizedType());
                    if (marksForValidation(p)) {
                        roots.addAll(bound);
                    } else {
                        boundWithoutValid.addAll(bound);
                    }
                }
            }
        }

        // ---- 2. closure: @Valid fields cascade, and the superclass chain rides along.
        Map<String, String> referencedBy = new TreeMap<>();
        Set<Class<?>> validated = cascadeClosure(roots);

        // A type bound WITHOUT @Valid is still validated if the cascade reaches it another way.
        boundWithoutValid.removeAll(validated);

        // ---- 3. every type that declares a constraint, attributed to its DECLARING class.
        Map<Class<?>, Set<String>> constrained = new java.util.LinkedHashMap<>();
        for (Class<?> c : all) {
            Set<String> members = constrainedMembersOf(c);
            if (!members.isEmpty()) constrained.put(c, members);
        }

        // ---- 4. which constrained types does a validated type merely *reference*? (diagnostic)
        for (Class<?> v : validated) {
            for (Field f : declaredFieldsOf(v)) {
                if (cascades(f)) continue;
                for (Class<?> t : unwrap(f.getGenericType())) {
                    if (constrained.containsKey(t) && !validated.contains(t)) {
                        referencedBy.putIfAbsent(t.getName(),
                                v.getSimpleName() + "#" + f.getName());
                    }
                }
            }
        }

        // ---- 5. inert = declares a constraint, and nothing validates it.
        List<Inert> inert = new ArrayList<>();
        List<String> flushTimeOnly = new ArrayList<>();
        for (var e : constrained.entrySet()) {
            Class<?> c = e.getKey();
            if (validated.contains(c)) continue;
            if (isExcludedFromInertness(c)) {
                flushTimeOnly.add(c.getSimpleName() + " {" + String.join(", ", e.getValue()) + "}");
                continue;
            }
            String from = referencedBy.get(c.getName());
            Shape shape = boundWithoutValid.contains(c) ? Shape.BOUND_WITHOUT_VALID
                    : from != null ? Shape.FIELD_LACKS_VALID
                    : Shape.NEVER_REFERENCED;
            inert.add(new Inert(shape, c.getName(), String.join(", ", e.getValue()), from));
        }
        inert.sort((a, b) -> a.shape() != b.shape()
                ? a.shape().compareTo(b.shape()) : a.type().compareTo(b.type()));

        System.out.println("[F2-184] types scanned under " + BASE + " = " + all.size()
                + " ; controllers = " + controllers + " ; handlers = " + handlers);
        System.out.println("[F2-184] body-bound roots marked @Valid = " + roots.size()
                + " ; validated after @Valid/superclass closure = " + validated.size()
                + " ; bound but NOT marked @Valid = " + boundWithoutValid.size());
        System.out.println("[F2-184] types declaring a validation constraint = " + constrained.size()
                + " ; of those, NOTHING validates = " + inert.size()
                + " ; entity-only (enforced at flush, 500 not 400) = " + flushTimeOnly.size());
        for (Inert i : inert) System.out.println(i);
        for (String s : flushTimeOnly) System.out.println("  [entity @flush    ] " + s);

        // ---- anti-vacuity. A collapsed scan reports zero violations, so prove it ran.
        assertThat(all.size()).as("scanned %d types under %s; a collapsed classpath walk reports "
                + "no inert constraints because it finds no constraints at all.", all.size(), BASE)
                .isGreaterThanOrEqualTo(MIN_TYPES_SCANNED);
        assertThat(controllers).as("found %d controllers; the platform has 255.", controllers)
                .isGreaterThanOrEqualTo(MIN_CONTROLLERS);
        assertThat(handlers).as("found %d handler methods across %d controllers — too few to be the "
                + "real surface.", handlers, controllers).isGreaterThanOrEqualTo(MIN_HANDLERS);

        // The two that matter: prove BOTH detectors work, independently of each other. If the
        // constraint detector breaks, everything looks unconstrained and this test passes over a
        // platform full of inert annotations. If the *validation* detector breaks, everything looks
        // inert and the failure message is noise. Neither half can be trusted from the other's number.
        assertThat(constrained.size()).as("the constraint detector recognises %d constrained types. "
                + "Near zero means jakarta.validation moved or @Constraint stopped being a "
                + "meta-annotation — not that the platform got cleaner. A broken detector passes.",
                constrained.size()).isGreaterThanOrEqualTo(MIN_CONSTRAINED_TYPES);
        assertThat(validated.size()).as("the validation detector reaches %d validated types. Near "
                + "zero means the @RequestBody/@Valid walk broke, and then EVERY constrained type "
                + "looks inert.", validated.size()).isGreaterThanOrEqualTo(MIN_VALIDATED_TYPES);

        // ---- the gate, in both directions.
        List<String> unexpected = inert.stream()
                .filter(i -> !TOLERATED.containsKey(i.type()))
                .map(Inert::toString).sorted().toList();
        assertThat(unexpected).as("""
            These types declare a bean-validation constraint that NOTHING enforces. A constraint \
            nobody runs is worse than no constraint at all, because it answers the question the \
            next reader was about to ask — F2-184.

            Fix by SHAPE, and do not pick the wrong one:
              FIELD_LACKS_VALID   a validated type reaches this one through a field, so the \
            constraint was meant to run: add @Valid to that field. Do NOT delete it.
              BOUND_WITHOUT_VALID the type IS a @RequestBody but the parameter has no @Valid. \
            ⚠ Before adding it, compare each @Size cap against its column: the number has never \
            been tested, so enabling it blindly either imposes a limit nobody chose (column is \
            NVARCHAR(MAX)) or still fails to protect it (cap looser than the column).
              NEVER_REFERENCED    nothing binds the type at all. Usually an outbound/internal DTO \
            whose constraints outlived a demotion — delete them. But check first whether the type \
            is dead because its ENDPOINT is missing, which is a different and larger defect.

            %s""".formatted(String.join("\n", unexpected))).isEmpty();

        // The reverse direction, which is what stops the list above from rotting into a comment:
        // an entry that has been fixed must be REMOVED, or the next reader cannot tell which of
        // these twelve are still real. Same rule as HandRolledVocabularyMirrorTest's directionality.
        Set<String> stillInert = new TreeSet<>();
        for (Inert i : inert) stillInert.add(i.type());
        List<String> stale = TOLERATED.keySet().stream()
                .filter(t -> !stillInert.contains(t)).sorted().toList();
        assertThat(stale).as("""
            These types are on this test's TOLERATED list but are no longer inert — something \
            validates them now. Delete their entries: a tolerance that no longer describes \
            anything is indistinguishable from a reviewed decision, which is exactly why \
            CLAUDE.md records BARE_EXPORT_CEILING being swept to an invariant rather than left \
            as a ceiling.

            %s""".formatted(stale)).isEmpty();
    }

    // ------------------------------------------------------------------ the two detectors

    /** Constraint members this class <b>declares</b> — {@code getDeclaringClass()}, never a guess. */
    private static Set<String> constrainedMembersOf(Class<?> c) {
        Set<String> out = new TreeSet<>();
        for (Field f : declaredFieldsOf(c)) {
            if (hasConstraint(f)) out.add(f.getName());
        }
        if (c.isRecord()) {
            for (RecordComponent rc : c.getRecordComponents()) {
                if (hasConstraint(rc)) out.add(rc.getName());
            }
        }
        for (Method m : declaredMethodsOf(c)) {
            if (m.getParameterCount() == 0 && hasConstraint(m)) out.add(m.getName() + "()");
        }
        for (Executable ctor : c.getDeclaredConstructors()) {
            for (Parameter p : ctor.getParameters()) {
                if (hasConstraint(p)) out.add(p.getName());
            }
        }
        return out;
    }

    private static boolean hasConstraint(AnnotatedElement e) {
        for (Annotation a : safeAnnotations(e)) {
            if (isConstraint(a.annotationType())) return true;
            // @Pattern.List / @Size.List and friends: a container whose value() is constraints.
            if (a.annotationType().getSimpleName().equals("List")
                    && a.annotationType().getDeclaringClass() != null
                    && isConstraint(a.annotationType().getDeclaringClass())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConstraint(Class<?> annotationType) {
        if (annotationType.getName().startsWith("jakarta.validation.constraints.")) return true;
        return annotationType.isAnnotation()
                && annotationType.isAnnotationPresent(Constraint.class);
    }

    /** {@code @Valid} on the parameter, or Spring's {@code @Validated}. */
    private static boolean marksForValidation(Parameter p) {
        return p.isAnnotationPresent(Valid.class) || p.isAnnotationPresent(Validated.class);
    }

    private static boolean cascades(Field f) {
        if (f.isAnnotationPresent(Valid.class)) return true;
        // List<@Valid Foo> — @Valid as a TYPE_USE annotation inside the generic signature.
        return String.valueOf(f.getAnnotatedType()).contains(Valid.class.getName());
    }

    // ------------------------------------------------------------------ the closure

    /**
     * From the body-bound roots, everything Hibernate Validator will actually walk: {@code @Valid}
     * fields (cascading through generics) and the superclass chain of anything validated.
     */
    private static Set<Class<?>> cascadeClosure(Set<Class<?>> roots) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (c == null || !seen.add(c)) continue;
            if (c.getName().startsWith("java.") || c.isPrimitive() || c.isEnum()) continue;
            // Constraints declared on a supertype ARE enforced on a validated subtype.
            for (Class<?> k = c.getSuperclass(); k != null && k != Object.class; k = k.getSuperclass()) {
                if (seen.add(k)) queue.add(k);
            }
            for (Field f : declaredFieldsOf(c)) {
                if (!cascades(f)) continue;
                queue.addAll(unwrap(f.getGenericType()));
            }
            if (c.isRecord()) {
                for (RecordComponent rc : c.getRecordComponents()) {
                    if (rc.isAnnotationPresent(Valid.class)
                            || String.valueOf(rc.getAnnotatedType()).contains(Valid.class.getName())) {
                        queue.addAll(unwrap(rc.getGenericType()));
                    }
                }
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ plumbing

    private static boolean isController(Class<?> c) {
        for (Annotation a : safeAnnotations(c)) {
            String n = a.annotationType().getName();
            if (n.equals("org.springframework.web.bind.annotation.RestController")
                    || n.equals("org.springframework.stereotype.Controller")) {
                return true;
            }
        }
        return false;
    }

    /** Every type under the base package, <b>including nested ones</b> — where the finding lives. */
    private static Set<Class<?>> allTypesUnder(String base) throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var factory = new CachingMetadataReaderFactory(resolver);
        var loader = InertValidationConstraintTest.class.getClassLoader();
        Set<Class<?>> out = new LinkedHashSet<>();
        for (var r : resolver.getResources(
                "classpath*:" + base.replace('.', '/') + "/**/*.class")) {
            // `classpath*:` reaches target/test-classes too. A fixture is not the request surface —
            // see isExcludedFromInertness(a) for the false positive this removes.
            try {
                String url = r.getURL().toString().replace('\\', '/');
                if (url.contains("/test-classes/")) continue;
            } catch (Throwable t) {                                                      // NOPMD
                continue;
            }
            String name;
            try {
                name = factory.getMetadataReader(r).getClassMetadata().getClassName();
            } catch (Throwable t) {                                                      // NOPMD
                continue;
            }
            try {
                // initialize=false: enumerating the platform must not run static initialisers.
                out.add(Class.forName(name, false, loader));
            } catch (Throwable t) {                                                      // NOPMD
                // A type whose dependency is not on the test classpath cannot be a request body.
            }
        }
        return out;
    }

    private static Field[] declaredFieldsOf(Class<?> c) {
        try {
            return c.getDeclaredFields();
        } catch (Throwable t) {                                                          // NOPMD
            return new Field[0];
        }
    }

    private static Method[] declaredMethodsOf(Class<?> c) {
        try {
            return c.getDeclaredMethods();
        } catch (Throwable t) {                                                          // NOPMD
            return new Method[0];
        }
    }

    private static Annotation[] safeAnnotations(AnnotatedElement e) {
        try {
            return e.getAnnotations();
        } catch (Throwable t) {                                                          // NOPMD
            return new Annotation[0];
        }
    }

    /** {@code List<X>}, {@code Map<K,V>}, {@code X[]} → the classes mentioned. */
    private static Set<Class<?>> unwrap(Type t) {
        Set<Class<?>> out = new LinkedHashSet<>();
        collect(t, out, 0);
        return out;
    }

    private static void collect(Type t, Set<Class<?>> out, int depth) {
        if (t == null || depth > 6) return;
        if (t instanceof Class<?> c) {
            if (c.isArray()) collect(c.getComponentType(), out, depth + 1);
            else out.add(c);
        } else if (t instanceof ParameterizedType p) {
            collect(p.getRawType(), out, depth + 1);
            for (Type a : p.getActualTypeArguments()) collect(a, out, depth + 1);
        } else if (t instanceof WildcardType w) {
            for (Type a : w.getUpperBounds()) collect(a, out, depth + 1);
        }
    }
}
