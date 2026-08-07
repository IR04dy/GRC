package com.tss.grc.security;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-158 — no HTTP handler may return a JPA entity that owns a lazily-fetched association.
 *
 * <h3>The failure this forbids</h3>
 * <p>Jackson serialises the handler's return value <b>after</b> the transaction closes. An entity with
 * a {@code LAZY} collection therefore throws {@code LazyInitializationException} inside the message
 * converter, which surfaces as {@code HttpMessageNotWritableException} and a <b>500</b> — for every
 * caller, on every call, forever. It is landmine 9 in {@code CLAUDE.md} in as many words: <i>"lazy
 * collection entities 500 outside the session — return DTOs"</i>.
 *
 * <h3>⚠ Why an invariant and not a ratchet, which is the interesting part</h3>
 * <p>Measured 2026-08-07: <b>883</b> handlers across <b>122</b> controllers return a JPA entity, so the
 * obvious instrument is a ceiling on that number. It would be the wrong instrument. Only <b>8 of 407</b>
 * entities own a collection or a {@code LAZY} association — {@code Role}, {@code User},
 * {@code IntakeRecord}, {@code IntakeEvent}, {@code TreatmentPlan}, {@code TreatmentAction},
 * {@code BiaQuantitative}, {@code BiaQuantitativeLossLine} — and returning any of the other 399 is
 * merely unfashionable, not broken. A ceiling at 883 would tolerate the next {@code Role} while
 * generating 883 units of noise about handlers that work; this refuses the eight and says nothing about
 * the rest. Per {@code CLAUDE.md}: a ceiling tolerates the population it measures, so it cannot tell a
 * reviewed guard from an unreviewed one.
 *
 * <h3>How the one violation stayed invisible</h3>
 * <p>{@code RoleController#list} returned {@code List<Role>} and had 500'd since it was written — the
 * only endpoint in Role Management. It survived because {@code AdminPage.tsx} carried a hardcoded
 * seven-role dropdown and a mock {@code ADMIN_ROLES} fallback on all three failure paths, so the screen
 * always rendered something. <b>The client-side fallback was load-bearing</b>, and F2-52 only found the
 * 500 because correcting that dropdown to read the real catalogue made the page depend on the call for
 * the first time. A workaround indistinguishable from success is why nobody filed it.
 *
 * <p>Reflection, not a source regex, for the reason its siblings give: a regex has under-counted an
 * authorisation or serialisation surface in this repository repeatedly, and a generic return type
 * spanning source lines is invisible to a line-oriented match.
 */
class LazyEntitySerialisationInvariantTest {

    @SuppressWarnings("unchecked")
    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
        RequestMapping.class, GetMapping.class, PostMapping.class,
        PutMapping.class, PatchMapping.class, DeleteMapping.class);

    /** A scan that finds nothing proves nothing; these floors are what the build measures today. */
    private static final int MIN_CONTROLLERS = 200;
    private static final int MIN_HANDLERS = 800;
    private static final int MIN_LAZY_ENTITIES = 5;

    private record Finding(String where, String entity, String association) { }

    @Test
    void noHandlerReturnsAnEntityThatOwnsALazyAssociation() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        int controllers = 0, handlers = 0;
        List<Finding> findings = new ArrayList<>();
        Set<String> lazyEntitiesSeen = new TreeSet<>();

        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {                                              // NOPMD
                continue;
            }
            controllers++;
            for (Method m : c.getDeclaredMethods()) {
                if (MAPPINGS.stream().noneMatch(m::isAnnotationPresent)) continue;
                handlers++;
                for (Class<?> ret : unwrap(m.getGenericReturnType())) {
                    if (!ret.isAnnotationPresent(Entity.class)) continue;
                    String assoc = lazyAssociationOf(ret);
                    if (assoc == null) continue;
                    lazyEntitiesSeen.add(ret.getSimpleName());
                    findings.add(new Finding(
                        c.getSimpleName() + "#" + m.getName(), ret.getSimpleName(), assoc));
                }
            }
        }

        System.out.println("[F2-158] @RestController classes = " + controllers
            + " ; handlers = " + handlers
            + " ; handlers returning a lazy-association entity = " + findings.size()
            + (lazyEntitiesSeen.isEmpty() ? "" : " -> " + lazyEntitiesSeen));

        // Anti-vacuity 1 and 2: the scan reached the controllers and their handlers at all.
        assertThat(controllers).as("scanned %d @RestController classes; the platform has well over %d. "
            + "A collapsed scan reports zero violations.", controllers, MIN_CONTROLLERS)
            .isGreaterThanOrEqualTo(MIN_CONTROLLERS);
        assertThat(handlers).as("found %d handler methods across %d controllers — too few to be the "
            + "real surface.", handlers, controllers).isGreaterThanOrEqualTo(MIN_HANDLERS);

        // Anti-vacuity 3, and the one that actually matters here: this test can only fire if
        // `lazyAssociationOf` still recognises a lazy association. If an annotation package moved or
        // Lombok stopped generating what it reads, every entity would look safe and the invariant would
        // pass on a platform full of 500s. So prove the DETECTOR works, independently of the scan.
        assertThat(lazyEntityCensus()).as(
            "the lazy-association detector recognises %d entities; measured 2026-08-07 there are 8 "
            + "(Role, User, IntakeRecord, IntakeEvent, TreatmentPlan, TreatmentAction, "
            + "BiaQuantitative, BiaQuantitativeLossLine). Near zero means the detector broke, not that "
            + "the platform got safer — and a broken detector passes this test.", lazyEntityCensus())
            .isGreaterThanOrEqualTo(MIN_LAZY_ENTITIES);

        assertThat(findings).as("""
            These handlers return a JPA entity that owns a lazily-fetched association. Jackson \
            serialises after the transaction closes, so each one answers 500 for every caller — \
            CLAUDE.md landmine 9. Return a DTO/record projection instead; see RoleController.RoleSummary.

            ⚠ Do not "fix" this by making the association EAGER. That trades a 500 for an unbounded \
            join on every read, and F2-158's own case (Role.permissions) would have fetched the whole \
            permission catalogue per row.

            %s""".formatted(findings)).isEmpty();
    }

    /** {@code ResponseEntity<List<X>>}, {@code Page<X>}, {@code X[]} → {@code X}. */
    private static Set<Class<?>> unwrap(Type t) {
        Set<Class<?>> out = new LinkedHashSet<>();
        collect(t, out, 0);
        return out;
    }

    private static void collect(Type t, Set<Class<?>> out, int depth) {
        if (t == null || depth > 6) return;
        if (t instanceof Class<?> c) {
            if (c.isArray()) {
                collect(c.getComponentType(), out, depth + 1);
            } else {
                out.add(c);
            }
        } else if (t instanceof ParameterizedType p) {
            collect(p.getRawType(), out, depth + 1);
            for (Type a : p.getActualTypeArguments()) collect(a, out, depth + 1);
        } else if (t instanceof WildcardType w) {
            for (Type a : w.getUpperBounds()) collect(a, out, depth + 1);
        }
    }

    /** The association that makes this entity unsafe to serialise, or {@code null} if there is none. */
    private static String lazyAssociationOf(Class<?> entity) {
        for (Class<?> k = entity; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                OneToMany o2m = f.getAnnotation(OneToMany.class);
                if (o2m != null) return "@OneToMany " + f.getName();
                ManyToMany m2m = f.getAnnotation(ManyToMany.class);
                if (m2m != null) return "@ManyToMany " + f.getName();
                ManyToOne m2o = f.getAnnotation(ManyToOne.class);
                if (m2o != null && m2o.fetch() == FetchType.LAZY) {
                    return "@ManyToOne(LAZY) " + f.getName();
                }
                OneToOne o2o = f.getAnnotation(OneToOne.class);
                if (o2o != null && o2o.fetch() == FetchType.LAZY) {
                    return "@OneToOne(LAZY) " + f.getName();
                }
            }
        }
        return null;
    }

    /** How many @Entity classes the detector currently flags — the detector's own liveness proof. */
    private static int lazyEntityCensus() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        int n = 0;
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                if (lazyAssociationOf(Class.forName(bd.getBeanClassName())) != null) n++;
            } catch (Throwable ignored) {                                        // NOPMD
                // A class that will not load cannot be returned by a handler either.
            }
        }
        return n;
    }
}
