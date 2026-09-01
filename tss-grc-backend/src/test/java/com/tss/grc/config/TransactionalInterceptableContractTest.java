package com.tss.grc.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-411 - <b>{@code @Transactional} written ABOVE a javadoc attaches to the wrong method, and the
 * compiler cannot see it.</b>
 *
 * <p>Java treats a comment as whitespace, so
 * <pre>
 *     &#64;Transactional
 *     /** what applyEditable does *&#47;
 *     private void applyEditable(...)
 * </pre>
 * puts the annotation on the <b>private</b> method. Spring's proxy-based transaction management
 * only considers <b>public</b> methods, and a proxy cannot intercept a private one at all, so the
 * annotation is inert - while a reader who asks "is this path transactional?" sees the word twelve
 * lines above the handler and concludes that it is. That is F2-184's decoy shape, at an annotation.
 *
 * <p>F2-213 introduced four of these in one commit (`f46eb191`), in the four `module/cyber`
 * services whose data-erasure defect it was fixing: the `applyEditable` extraction is what
 * stranded the annotation, and the public `updateXxx` handler it was meant for was left as the
 * <b>only</b> non-transactional write method in each class - the lone-outlier tell (F2-205/F2-282).
 * F2-215's two-of-N, with the mechanism that fixed the first defect causing the second.
 *
 * <p>{@code NdiScheduler#distinctOrgIds} was the fifth, inert for <b>two</b> independent reasons:
 * {@code protected}, and self-invoked from {@code evidenceExpirySweep()}, so it would still have
 * been bypassed had it been public. It was deleted rather than promoted - a scheduler sweep must
 * not hold one transaction across every tenant (F2-322/F2-323), and its single {@code findAll()} is
 * self-transactional through {@code SimpleJpaRepository}.
 *
 * <p><b>This was a latent-hazard closure, and saying which you have is the point (F2-283.)</b> All
 * four handlers are read-mutate-single-save, and {@code save} carries its own transaction, so
 * nothing was observably broken. What the fix removes is the hazard: the moment a second write is
 * added to one of those handlers it would have been silently non-atomic.
 *
 * <p>Measured from <b>reflection, never a source regex</b>, and that is not a style preference -
 * a regex sweep of the same tree returned 6 sites of which <b>3 were wrong</b>: it attributed an
 * annotation to the next signature it could parse (two false positives) and missed the
 * {@code protected} one entirely. The recorded rule, earning its place again.
 *
 * <p>A hard zero with <b>no exemption map</b> is safe here because there is no correct code for it
 * to fail on: a method that needs a transaction can always be public, and one that cannot be
 * public can never be intercepted, so the annotation on it is never doing work.
 */
class TransactionalInterceptableContractTest {

    /**
     * The claim, as a predicate, so the synthetic control below can exercise the same code the
     * corpus assertion uses. A "collect the violations, assert the list is empty" leg is vacuous
     * on a clean tree - an empty list is what a correct detector and a dead one both produce
     * (F2-250/F2-295).
     */
    static boolean cannotBeIntercepted(Method m) {
        if (!m.isAnnotationPresent(Transactional.class)) return false;
        return !Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers());
    }

    private record Scan(List<String> violations, int publicTransactional, int beans) { }

    /** Every Spring bean, by the house idiom (BilingualInvariantRatchetTest, UniqueBeanNameContractTest). */
    private static Scan scan() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<String> bad = new ArrayList<>();
        int ok = 0, beans = 0;
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable ignored) {
                continue;                      // a class that cannot load cannot be asserted on
            }
            beans++;
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable ignored) {
                continue;
            }
            for (Method m : ms) {
                if (!m.isAnnotationPresent(Transactional.class)) continue;
                if (cannotBeIntercepted(m)) {
                    bad.add("%s#%s [%s]".formatted(
                            c.getName().replace("com.tss.grc.", ""), m.getName(),
                            Modifier.toString(m.getModifiers())));
                } else {
                    ok++;
                }
            }
        }
        return new Scan(bad, ok, beans);
    }

    @Test
    void noTransactionalSitsOnAMethodASpringProxyCannotIntercept() {
        assertThat(scan().violations())
                .as("""
                    A @Transactional on a non-public or static method of a Spring bean is INERT: the
                    proxy-based transaction manager considers public methods only, and a proxy cannot
                    intercept a private one at all. It is worse than absent, because it answers the
                    question a reader was about to ask (F2-184).

                    The usual cause is the annotation being written ABOVE a javadoc rather than below
                    it, which silently attaches it to the next method - as F2-213 did four times in
                    one commit. Put the annotation on the public method that needs it, or delete it.
                    Do not add an exemption: there is no correct code for this leg to fail on.""")
                .isEmpty();
    }

    @Test
    void theScanIsNotVacuous() {
        Scan s = scan();
        // The largest WRONG answer is zero - a scanner that resolves nothing, or a package rename -
        // and a floor must sit above it rather than above "obviously not broken" (F2-215).
        assertThat(s.beans())
                .as("the bean scan resolved almost nothing, so the zero above means nothing")
                .isGreaterThan(400);
        assertThat(s.publicTransactional())
                .as("no public @Transactional methods were found, so the detector is not reading "
                    + "annotations at all and its empty violation list proves nothing")
                .isGreaterThan(1_200);
    }

    /**
     * Without this the hard zero is indistinguishable from a detector that has stopped matching.
     * F2-235's rule: a check that cannot fire reads exactly like a clean tree, and it under-reports.
     */
    @Test
    void theDetectorFiresOnEachNonInterceptableShapeAndOnNoneOfTheLegitimateOnes() throws Exception {
        assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod("privateTx")))
                .as("a private @Transactional must be reported").isTrue();
        assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod("protectedTx")))
                .as("a protected @Transactional must be reported - the shape the source regex "
                    + "missed entirely").isTrue();
        assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod("staticTx")))
                .as("a static @Transactional must be reported").isTrue();

        assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod("publicTx")))
                .as("a public @Transactional is the legitimate shape and must NOT be reported - a "
                    + "gate that fails on correct code trains the next reader to edit the "
                    + "assertion rather than the code (F2-214)").isFalse();
        assertThat(cannotBeIntercepted(Fixture.class.getDeclaredMethod("privatePlain")))
                .as("a private method with no annotation must NOT be reported").isFalse();
    }

    /**
     * The four handlers F2-411 repointed, named individually so a revert fails here rather than
     * silently shrinking a population. A count could not see one of the four losing its annotation.
     */
    @Test
    void theFourCyberUpdateHandlersCarryTheirOwnTransactional() throws Exception {
        String[][] sites = {
                {"com.tss.grc.module.cyber.service.CyberActionPlanService", "updateActionPlan"},
                {"com.tss.grc.module.cyber.service.CyberAssessmentService", "updateAssessment"},
                {"com.tss.grc.module.cyber.service.CyberControlService", "updateControl"},
                {"com.tss.grc.module.cyber.service.CyberVulnerabilityService", "updateVulnerability"},
        };
        for (String[] s : sites) {
            Class<?> c = Class.forName(s[0]);
            Method m = null;
            for (Method cand : c.getDeclaredMethods()) {
                if (cand.getName().equals(s[1])) {
                    m = cand;
                    break;
                }
            }
            assertThat(m).as("%s#%s must exist", s[0], s[1]).isNotNull();
            assertThat(Modifier.isPublic(m.getModifiers()))
                    .as("%s#%s must be public for the annotation to apply", s[0], s[1]).isTrue();
            assertThat(m.isAnnotationPresent(Transactional.class))
                    .as("""
                        %s#%s lost its @Transactional. F2-213 stranded it on the private
                        applyEditable helper above, where a proxy cannot see it, leaving this
                        handler the only non-transactional write method in its class.""",
                            s[0], s[1])
                    .isTrue();
        }
    }

    /** Synthetic corpus for the control - never scanned, only inspected by name. */
    @SuppressWarnings("unused")
    static class Fixture {
        @Transactional
        public void publicTx() { }

        @Transactional
        private void privateTx() { }

        @Transactional
        protected void protectedTx() { }

        @Transactional
        static void staticTx() { }

        private void privatePlain() { }
    }
}
