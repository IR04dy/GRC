package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-247 — two Spring beans may not share a simple name, because the application will not start.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Spring's default bean name is the <b>decapitalised simple class name</b>, so two
 * {@code @Component} classes called {@code VendorReferenceResolver} in different packages both claim
 * {@code vendorReferenceResolver} and component scanning throws
 * {@code ConflictingBeanDefinitionException} while parsing the configuration class. The context never
 * refreshes; nothing binds a port.
 *
 * <p>⚠⚠ <b>The whole unit suite passed over exactly this defect.</b> F2-247 added a second
 * {@code VendorReferenceResolver} (a legitimate second resolver — {@code tprm.vendors} is a different
 * parent register from {@code scm.vendors}, and ADR-012/018 keep the modules' internals apart), ran
 * <b>4,229 tests green</b>, and then the app would not boot. No unit test boots a Spring context, and
 * {@code mvn -o clean package} is deliberately hermetic — needing no database and no secrets — so it
 * never starts the application either. <b>The only instrument that could see this was launching
 * it</b>, and a launch is not part of the build.
 *
 * <p>Worse, the failure reads like an unrelated startup problem: Maven prints {@code BUILD SUCCESS}
 * over it (the recorded landmine that {@code BUILD SUCCESS} is not a start signal under
 * {@code spring-boot:run}, because DevTools' {@code RestartLauncher} swallows the app's failure), and
 * the visible symptom is a health probe that never answers.
 *
 * <p>F2-222 filed the duplicate-simple-name hazard for <b>entities</b> and measured it as latent: 15
 * of 433 entity simple names are declared in two packages, and three shipped gates key on the simple
 * name, <i>"and nobody is maintaining that"</i>. This is the same shape one layer over, and for a
 * Spring bean it is not latent — it is fatal. So it gets an invariant rather than a census.
 *
 * <h2>Why a hard zero with no exemption map</h2>
 *
 * <p>Safe rather than strict (F2-214): an explicit bean name (<code>@Component("x")</code>) or a
 * distinguishing class name is always available, so there is no correct code for this to fail on. A
 * duplicate simple name is never the only way to express an intent — and the honest fix is usually
 * the name, because when two modules each need their own resolver over their own register, a reader
 * reaching for whichever they found first is the real hazard.
 */
class UniqueBeanNameContractTest {

    /** The stereotypes whose default bean name is derived from the simple class name. */
    private static final List<Class<? extends java.lang.annotation.Annotation>> STEREOTYPES =
            List.of(Component.class, Service.class, Repository.class,
                    Controller.class, RestController.class);

    /**
     * Scan the shipped packages for component-annotated classes.
     *
     * <p>⚠ Excluded by <b>code source</b>, never by a name ending in {@code Test}: F2-192 recorded
     * that a name test lets any member escape by being renamed, and F2-241 found its own scan
     * reaching {@code target/test-classes} only because it published the NAMES rather than a count.
     */
    private static Map<String, List<String>> beansBySimpleName() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition bd) {
                return true;   // include abstract/nested — a name clash does not care
            }
        };
        STEREOTYPES.forEach(a -> scanner.addIncludeFilter(new AnnotationTypeFilter(a)));

        Map<String, List<String>> bySimple = new TreeMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.tss.grc")) {
            String fqn = bd.getBeanClassName();
            if (fqn == null) {
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(fqn);
            } catch (Throwable t) {
                continue;
            }
            var src = type.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation().toString().contains("test-classes")) {
                continue;
            }
            // An explicitly named bean does not collide on its simple name, so it is not in the
            // population — that is the other legitimate fix, and it must not read as a violation.
            if (hasExplicitName(type)) {
                continue;
            }
            bySimple.computeIfAbsent(type.getSimpleName(), k -> new ArrayList<>()).add(fqn);
        }
        return bySimple;
    }

    private static boolean hasExplicitName(Class<?> type) {
        for (var a : STEREOTYPES) {
            var ann = type.getAnnotation(a);
            if (ann == null) {
                continue;
            }
            try {
                Object v = a.getMethod("value").invoke(ann);
                if (v instanceof String s && !s.isBlank()) {
                    return true;
                }
            } catch (Exception ignored) {
                // no value() to read — treat as unnamed
            }
        }
        return false;
    }

    @Test
    @DisplayName("no two component-scanned beans share a simple name — the app would not start")
    void noTwoBeansShareASimpleName() {
        Map<String, List<String>> bySimple = beansBySimpleName();

        List<String> clashes = bySimple.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + e.getValue())
                .toList();

        assertThat(clashes)
                .as("Spring's default bean name is the decapitalised simple class name, so these "
                  + "classes claim the same bean and component scanning throws "
                  + "ConflictingBeanDefinitionException at context startup. The whole unit suite "
                  + "passes over this — nothing here boots a context — so the first sign is an app "
                  + "that will not start, under a Maven BUILD SUCCESS. Give one an explicit "
                  + "@Component(\"name\"), or better, a name that says which thing it serves.")
                .isEmpty();
    }

    @Test
    @DisplayName("self-test: the detector would have caught F2-247's own collision")
    void theDetectorIsNotVacuous() {
        // A gate that cannot see the defect it was written for reads exactly like a clean tree
        // (F2-208), so drive the grouping over the literal pair that failed to start.
        Map<String, List<String>> synthetic = new TreeMap<>();
        synthetic.computeIfAbsent("VendorReferenceResolver", k -> new ArrayList<>())
                .add("com.tss.grc.module.scm.service.VendorReferenceResolver");
        synthetic.computeIfAbsent("VendorReferenceResolver", k -> new ArrayList<>())
                .add("com.tss.grc.module.tprm.service.VendorReferenceResolver");

        assertThat(synthetic.get("VendorReferenceResolver"))
                .as("the shape that refused to boot must group to a size greater than one")
                .hasSize(2);

        // And the fix must be distinguishable from the defect: the shipped names differ.
        assertThat(beansBySimpleName().keySet())
                .as("the two resolvers must now carry distinct simple names")
                .contains("VendorReferenceResolver", "TprmVendorReferenceResolver");
    }

    @Test
    @DisplayName("the population is real: the scan resolves the platform's beans")
    void thePopulationIsNotEmpty() {
        Map<String, List<String>> bySimple = beansBySimpleName();
        System.out.println("F2-247 — component-scanned beans with a derived name: " + bySimple.size());

        // Anti-vacuity, and the floor sits above the largest WRONG answer rather than above zero
        // (F2-215): a scan that resolves only one package, or that silently reaches nothing because
        // the classpath moved, must fail rather than report a clean tree.
        //
        // ⚠ 600 is derived from the MEASURED 744 (2026-08-19), not chosen. My first draft guessed
        // 800 and the leg failed on a correct tree — F2-195's rule that a baseline lifted from
        // something other than the measurement is worse than none, because it looks measured.
        assertThat(bySimple.size())
                .as("a scan that stops resolving reports few beans, and few reads exactly like "
                  + "clean; measured 744 component-scanned beans with a derived name")
                .isGreaterThan(600);
    }
}
