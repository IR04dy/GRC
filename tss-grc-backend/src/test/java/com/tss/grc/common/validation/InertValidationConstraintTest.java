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
        //
        // F2-185's retaliation half was resolved 2026-08-11 and its entry here is GONE. The FINDING
        // stays open on the vendor-BCP half below, so this comment deliberately does not claim the
        // row is closed — `FindingsRegisterConsistencyTest.closureClaimedByATestIsHonoured` failed
        // when an earlier draft did, and on this occasion the REGISTER was right and my javadoc was
        // premature. (F2-185b hit the same check from the opposite direction: there the test's claim
        // was true and the register cell was stale, and the fix was to split the row. 🔑 The check
        // does not tell you which side is wrong — it tells you to go and decide.)
        //
        // What the removed entry used to say is worth keeping, as a warning about this list: it read
        // "the retaliation terminal transition has no endpoint, so the complaint lifecycle cannot be
        // closed through the API", and that was **false** — /retaliation/{id}/validate|uphold|dismiss
        // are all wired, guarded ETHICS:PROTECT, and rendered as buttons. `transitionTo` was a
        // redundant FOURTH path, so the answer was neither of the two options that entry offered.
        // 🔑 A reason recorded next to a tolerated entry is still only a claim. Mine was written by
        // reading the service and not the controller beside it, which is the same one-file-short
        // mistake as F2-179's "ExerciseObserverService is open" — and both were mine.
        Map.entry("com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos$EscalateRequest",
            "F2-185(a): STILL OPEN, and re-measured 2026-08-11 rather than carried over. No usage "
            + "anywhere in main sources — but unlike its retired sibling this one has no live twin. "
            + "Campaign-level POST /bcp-campaigns/{id}/escalate is a BULK sweep over everything past "
            + "the threshold, with no reason and no named recipient; there is no way to escalate one "
            + "vendor's attestation to a person. Corroborated by the schema: "
            + "vendor_bcp_attestation.escalated_to_user_id is declared in V177, mapped on the entity, "
            + "and written by NOTHING, while aml's ScreeningHit writes its identically-named column "
            + "from HitEscalateRequest. So the DTO, the column and a working precedent all exist and "
            + "the endpoint does not. Adding a write surface is the owner's call, not a repair.")

        // ── F2-185b CLOSED 2026-08-11: the ten BOUND_WITHOUT_VALID entries that used to sit here
        //    are gone, resolved per site rather than as a batch, because the ten did not share an
        //    answer. Two caps DELETED (closing_summary_* are NVARCHAR(MAX) — and both were @Size
        //    (8000), not the 1000 recorded here, so the filing's own numbers were wrong); seven
        //    ENABLED against columns they match exactly, now held by RequestSizeCapMirrorTest; one
        //    needed a service change first (the SoD override prefix made cap == column a lie — see
        //    SodOverrideNoteTest); and one, VulnSlaDtos$RemediatedRequest, turned out not to be a
        //    cap problem at all: the controller bound the note and threw it away.
        //    🔑 The reason each entry carried a reason is that the reasons disagreed. A count of
        //    "ten dormant caps" would have been closed with one @Valid sweep and would have left
        //    two new limits nobody chose, one 500 still live, and one silently discarded field.
    );

    /**
     * F2-185b — the hole its own mutation harness found, and the reason it is a separate assertion.
     *
     * <p>The test below is per-TYPE: it subtracts everything the cascade validates, so a DTO that is
     * {@code @Valid} at one binding site drops out of the population <b>at every</b> binding site.
     * That is correct for "is this constraint ever enforced" and wrong for "is it enforced where I am
     * about to send a request". Measured by removing {@code @Valid} from one of the three routes that
     * bind {@code AttestationReviewRequest}: the suite stayed green, because the other two still had
     * it. 🔑 <b>A per-type gate over a per-site defect reports the population it can see, and one
     * unvalidated site out of three looks exactly like none.</b>
     *
     * <p>Entries are types where that is deliberate, with the reason. Empty is the goal.
     */
    private static final Map<String, String> PARTIALLY_VALIDATED = Map.of();

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

    /**
     * F2-185b — per SITE, not per type. See {@link #PARTIALLY_VALIDATED} for why this cannot be folded
     * into the test above: that one asks whether a constraint is <i>ever</i> enforced, and answers
     * "yes" for a DTO validated on one route and bound bare on another. This asks the question a
     * caller actually cares about — will my request to <b>this</b> endpoint be checked.
     */
    @Test
    void aConstrainedRequestTypeIsValidatedAtEverySiteThatBindsIt() throws Exception {
        Set<Class<?>> all = allTypesUnder(BASE);

        Map<String, Set<String>> bareSites = new TreeMap<>();
        Set<Class<?>> constrainedBound = new LinkedHashSet<>();
        int controllers = 0;
        int bindings = 0;

        for (Class<?> c : all) {
            if (!isController(c)) continue;
            controllers++;
            for (Method m : declaredMethodsOf(c)) {
                if (MAPPINGS.stream().noneMatch(m::isAnnotationPresent)) continue;
                for (Parameter p : m.getParameters()) {
                    if (BODY_BINDINGS.stream().noneMatch(p::isAnnotationPresent)) continue;
                    bindings++;
                    if (marksForValidation(p)) continue;
                    for (Class<?> bound : unwrap(p.getParameterizedType())) {
                        // Only a type that DECLARES a constraint can lose one here. A bare body of a
                        // constraint-free DTO is a design choice, not a defect — and the cascade
                        // closure is included so a wrapper whose @Valid children carry the
                        // constraints still counts.
                        boolean carries = cascadeClosure(Set.of(bound)).stream()
                                .anyMatch(t -> !isExcludedFromInertness(t)
                                        && !constrainedMembersOf(t).isEmpty());
                        if (!carries) continue;
                        constrainedBound.add(bound);
                        bareSites.computeIfAbsent(bound.getName(), k -> new TreeSet<>())
                                .add(c.getSimpleName() + "#" + m.getName());
                    }
                }
            }
        }

        // Anti-vacuity: this walk must actually reach the controller surface. If reflection silently
        // returned nothing, every assertion below would pass on an empty population.
        assertThat(controllers).as("controllers scanned").isGreaterThanOrEqualTo(MIN_CONTROLLERS);
        assertThat(bindings).as("body-bound parameters scanned").isGreaterThanOrEqualTo(100);

        List<String> unexpected = bareSites.entrySet().stream()
                .filter(e -> !PARTIALLY_VALIDATED.containsKey(e.getKey()))
                .filter(e -> !TOLERATED.containsKey(e.getKey()))   // already named by the test above
                .map(e -> "  %s   bare at: %s".formatted(e.getKey(), String.join(", ", e.getValue())))
                .toList();

        assertThat(unexpected).as("""
            These request types declare a validation constraint and are bound as a body WITHOUT \
            @Valid at the sites listed. The constraint does not run for a caller hitting those \
            routes, even if a sibling route validates the same type — which is precisely how one of \
            three review routes on VendorBcpAttestationController could have lost its @Valid without \
            failing the build (F2-185b's own surviving mutation).

            Add @Valid at the named site, or add the type to PARTIALLY_VALIDATED with the reason. \
            ⚠ Before adding @Valid, check each @Size cap against its column — RequestSizeCapMirrorTest \
            explains why the number itself is a decision.

            %s""".formatted(String.join("\n", unexpected))).isEmpty();

        // Reverse direction, same rule as everywhere else in this file.
        List<String> stale = PARTIALLY_VALIDATED.keySet().stream()
                .filter(t -> !bareSites.containsKey(t)).sorted().toList();
        assertThat(stale).as(
            "PARTIALLY_VALIDATED entries that are now validated everywhere — delete them: %s", stale)
                .isEmpty();
    }

    /**
     * F2-413 — <b>the third depth of the same question, and the two legs above are blind to it for
     * complementary reasons.</b>
     *
     * <p>{@link #everyValidationConstraintSitsOnATypeSomeEndpointActuallyValidates()} is per-TYPE: it
     * asks whether a constrained type is validated <i>somewhere</i>.
     * {@link #aConstrainedRequestTypeIsValidatedAtEverySiteThatBindsIt()} is per-SITE, and its
     * population is <b>body parameters</b>. So a constrained type reached at one site through the
     * cascade and at another through a <b>nested member that does not cascade</b> sits in neither:
     * the first says "validated", the second sees only {@code @RequestBody} parameters.
     *
     * <p>That is [[F2-185b]]'s own headline one level deeper — <i>a per-type gate over a per-site
     * defect reports the population it can see</i> — where "site" now means a field or record
     * component rather than a parameter.
     *
     * <p>The defect it found: {@code IaDtos.UniverseBulkImportRequest.entries} was
     * {@code @NotNull List<UniverseCreateRequest>} with <b>no {@code @Valid}</b>, so on
     * {@code POST /universe/bulk-import} — a route whose own body IS {@code @Valid} — none of
     * {@code UniverseCreateRequest}'s constraints ran: {@code @NotBlank entryCode},
     * {@code @NotBlank titleEn}, {@code @NotBlank titleAr} (house rule 7), six {@code @Size} caps and
     * the {@code @Pattern} on {@code plannedQuarter}. The single-entry {@code POST /universe} route is
     * {@code @Valid}, so the <b>same type was validated at one site and not the other</b>.
     *
     * <p>🔑 The population is <b>three</b> and two of them were already right —
     * {@code SanctionsDtos.entries} (fixed by [[F2-185]], which is what makes this [[F2-215]]'s
     * two-of-N: the mechanism was visibly present twice) and {@code ApprovalChainDtos.steps}. A lone
     * outlier out of three is the [[F2-205]] / [[F2-282]] tell.
     *
     * <p><b>Hard zero with no exemption map.</b> There is no correct code for it to fail on: if the
     * element type carries constraints, either they are meant to run at this site — add
     * {@code @Valid} — or they are inert, which the per-type leg above already refuses. ⚠ Enabling
     * the cascade is only safe because every cap mirrors its column ([[F2-184]]'s <i>cap == column →
     * enable</i>): it converts a truncation 8152 into a 400 naming the field and cannot break a
     * request that works today.
     */
    @Test
    void everyNestedMemberOfAValidatedTypeCascadesWhenItsElementIsConstrained() throws Exception {
        Set<Class<?>> all = allTypesUnder(BASE);
        Set<Class<?>> validated = cascadeClosure(validatedRoots(all));

        List<String> gaps = new ArrayList<>();
        int membersExamined = 0;
        for (Class<?> owner : validated) {
            if (owner.getName().startsWith("java.") || owner.isEnum()) continue;
            for (Map.Entry<String, Type> member : nestedMembersOf(owner).entrySet()) {
                membersExamined++;
                for (Class<?> element : unwrap(member.getValue())) {
                    if (element == owner || element.isEnum()) continue;
                    if (element.getName().startsWith("java.")) continue;
                    Set<String> constrained = constrainedMembersOf(element);
                    if (constrained.isEmpty()) continue;
                    gaps.add("%s#%s -> %s declares %s".formatted(
                            owner.getSimpleName(), member.getKey(),
                            element.getSimpleName(), constrained));
                }
            }
        }

        // Anti-vacuity: the largest WRONG answer is zero — a closure that resolves nothing, or a
        // member walk that returns nothing — and a floor must sit above it (F2-215).
        assertThat(validated.size())
                .as("the cascade closure resolved almost nothing, so the emptiness below means nothing")
                .isGreaterThan(MIN_VALIDATED_TYPES);
        assertThat(membersExamined)
                .as("no NON-cascading nested members were examined at all, so this leg cannot fire — "
                    + "a detector that cannot match reads exactly like a clean tree (F2-235)")
                .isGreaterThan(200);

        assertThat(gaps.stream().sorted().toList()).as("""
            A nested member of a validated type does NOT cascade, and its element type declares
            constraints — so at THIS site those constraints do not run, however @Valid the enclosing
            @RequestBody is. Bean validation cascades only where it is told to.

            This is invisible to both legs above: the per-TYPE leg says the element is "validated
            somewhere" (it is — at another site), and the per-SITE leg's population is @RequestBody
            PARAMETERS, one level up from a field.

            Add @Valid to the member (or `List<@Valid Foo>`). Do not add an exemption: if the
            constraints are not meant to run here they are inert, which the per-type leg refuses.
            ⚠ Check each @Size cap against its column first — F2-184: cap == column means enabling
            only turns a truncation 500 into a 400 naming the field; a cap LOOSER than its column
            means the annotation is what is wrong.

            %s""".formatted(String.join("\n", gaps.stream().sorted().toList()))).isEmpty();
    }

    /**
     * F2-414 — <b>the fourth carrier of the same question, and the one where the annotation is
     * inert unless the CLASS opts in.</b>
     *
     * <p>A constraint on a method <b>parameter</b> is not enforced by the bean-validation provider
     * on its own: Spring runs it through {@code MethodValidationPostProcessor}, which advises only
     * classes annotated {@code @Validated}. So {@code @RequestParam @Min(1) @Max(365) int leadDays}
     * on a class without {@code @Validated} accepts <b>any</b> int — no error, no log, the bounds
     * visibly present. [[F2-184]]'s decoy shape at a parameter.
     *
     * <p>The defect: {@code EsgHorizonController.permitSweep} bounded {@code leadDays} to 1..365 and
     * the class carried no {@code @Validated}, so the NCEC environmental-permit expiry sweep could be
     * driven over an arbitrary window — raising a premature {@code HIGH} renewal CAPA for every
     * permit in the register, whatever its expiry. Bounded rather than unbounded only because the
     * sweep dedups on {@code sourceRef}, so a repeat call adds nothing; the first call is the one
     * that files the CAPAs.
     *
     * <p>🔑 The population is <b>four parameters</b> and <b>three were already right</b>: all three
     * live on {@code TenableController}, the one class in the platform that carries
     * {@code @Validated}. A lone outlier out of four is the [[F2-205]] / [[F2-282]] tell, and the
     * mechanism being visibly present on a sibling is [[F2-215]]'s two-of-N.
     *
     * <p>⚠⚠ This leg matters more than its population suggests, because [[F2-393]] <b>filed</b>
     * <i>"add {@code @Min(0)} to the page parameter"</i> as a follow-up after a negative page index
     * crashed a manual {@code subList}. Applied naively that filed remedy ships another inert
     * annotation — and an inert bound on a paging parameter reads exactly like a fixed one. The gate
     * is what turns that into a build failure.
     *
     * <p><b>Hard zero, no exemption map.</b> Either the constraint is meant to run — add
     * {@code @Validated} to the class or the method — or it is not, in which case it should be
     * deleted rather than left answering a reader's question ([[F2-214]]: there is no correct code
     * for this leg to fail on). ⚙ Activating it is safe here for a measured reason:
     * {@code GlobalExceptionHandler.handleConstraint} already maps an unwrapped
     * {@code ConstraintViolationException} to a <b>400</b> whose {@code errors} map is keyed by
     * property path, so a violation names the parameter rather than surfacing as a 500.
     *
     * <p>⚙ Constructors are excluded deliberately, and not as a convenience: a record's canonical
     * constructor carries its components' constraints, and those are enforced by the cascade when the
     * record is validated as a body — nothing to do with {@code @Validated}. Counting them would
     * report every constrained request DTO in the platform as a defect, which is the opposite of
     * this leg's claim.
     */
    @Test
    void everyParameterConstraintSitsOnAClassSpringMethodValidates() throws Exception {
        List<String> inert = new ArrayList<>();
        int parameters = 0;
        int constrainedParameters = 0;
        int validatedClasses = 0;

        for (Class<?> c : allTypesUnder(BASE)) {
            boolean classOptsIn = c.isAnnotationPresent(Validated.class);
            if (classOptsIn) validatedClasses++;
            for (Method m : declaredMethodsOf(c)) {
                boolean enforced = classOptsIn || m.isAnnotationPresent(Validated.class);
                for (Parameter p : m.getParameters()) {
                    parameters++;
                    if (!hasConstraint(p)) continue;
                    constrainedParameters++;
                    if (enforced) continue;
                    inert.add("%s#%s(%s %s)".formatted(
                            c.getName().replace("com.tss.grc.", ""), m.getName(),
                            p.getType().getSimpleName(), p.getName()));
                }
            }
        }

        // Floors above the largest WRONG answer, which for a scan that resolves nothing is zero
        // (F2-215). The constrained count is deliberately tight: it is the whole population, so a
        // detector that stops reading parameter annotations fails here rather than reporting clean.
        assertThat(parameters)
                .as("almost no method parameters were examined, so the emptiness below means nothing")
                .isGreaterThan(3_000);
        assertThat(constrainedParameters)
                .as("no CONSTRAINED parameter was found at all, so this leg cannot fire — a detector "
                    + "that cannot match reads exactly like a clean tree (F2-235)")
                .isGreaterThanOrEqualTo(4);
        // ⚠ Deliberately >= 1, not >= 2. After the fix there are exactly two @Validated classes, so
        // a floor of 2 would fail the moment either loses the annotation — which is what the inert
        // list below already says, more precisely. An anti-vacuity floor must assert that the
        // DETECTOR works, never re-assert the fix: at 1 the classOptsIn branch is still exercised.
        assertThat(validatedClasses)
                .as("no @Validated class was found, so the enforcement side of the check is dead "
                    + "and every parameter would read as inert")
                .isGreaterThanOrEqualTo(1);

        assertThat(inert.stream().sorted().toList()).as("""
            A bean-validation constraint on a method PARAMETER is enforced by Spring's
            MethodValidationPostProcessor, which advises only classes annotated @Validated. Without
            it the constraint is INERT — the value is accepted and the annotation answers the
            question a reader was about to ask (F2-184).

            Add @Validated to the declaring class (TenableController is the model), or delete the
            constraint. Do not add an exemption: there is no correct code for this leg to fail on.

            ⚠ Note for whoever executes F2-393's filed `@Min(0)` on a paging parameter: adding the
            bound WITHOUT @Validated ships an inert annotation, and an inert bound on a page index
            reads exactly like a fixed one.

            %s""".formatted(String.join("\n", inert.stream().sorted().toList()))).isEmpty();
    }

    /** The body-bound roots marked for validation — the same derivation the per-type leg uses. */
    private static Set<Class<?>> validatedRoots(Set<Class<?>> all) {
        Set<Class<?>> roots = new LinkedHashSet<>();
        for (Class<?> c : all) {
            if (!isController(c)) continue;
            for (Method m : declaredMethodsOf(c)) {
                if (MAPPINGS.stream().noneMatch(m::isAnnotationPresent)) continue;
                for (Parameter p : m.getParameters()) {
                    if (BODY_BINDINGS.stream().noneMatch(p::isAnnotationPresent)) continue;
                    if (marksForValidation(p)) roots.addAll(unwrap(p.getParameterizedType()));
                }
            }
        }
        return roots;
    }

    /**
     * Fields and record components of {@code owner} that do <b>not</b> cascade, by name → generic
     * type. Both shapes are needed: this codebase's request DTOs are overwhelmingly records, and
     * {@code cascades(Field)} is the field half of the check the closure already performs.
     */
    private static Map<String, Type> nestedMembersOf(Class<?> owner) {
        Map<String, Type> out = new java.util.LinkedHashMap<>();
        for (Field f : declaredFieldsOf(owner)) {
            if (f.isSynthetic() || cascades(f)) continue;
            out.put(f.getName(), f.getGenericType());
        }
        if (owner.isRecord()) {
            for (RecordComponent rc : owner.getRecordComponents()) {
                boolean cascades = rc.isAnnotationPresent(Valid.class)
                        || String.valueOf(rc.getAnnotatedType()).contains(Valid.class.getName());
                if (cascades) {
                    out.remove(rc.getName());       // the backing field read as non-cascading
                } else {
                    out.put(rc.getName(), rc.getGenericType());
                }
            }
        }
        return out;
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
