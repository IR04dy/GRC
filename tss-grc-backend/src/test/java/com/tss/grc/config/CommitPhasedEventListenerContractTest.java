package com.tss.grc.config;

import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-438 — a listener that acts on a domain event must run when the event's transaction COMMITS.
 *
 * <h3>The rule was already written down, and followed nowhere</h3>
 *
 * {@code AccessRequestService} applies it by hand with
 * {@code TransactionSynchronizationManager.registerSynchronization}, and states it in one sentence:
 *
 * <blockquote>"Engine wiring runs AFTER COMMIT: outside the governance TX it can neither poison it
 * nor race the row's ROWVERSION."</blockquote>
 *
 * <p>Both failure modes, named together — and the tree held <b>zero</b>
 * {@code @TransactionalEventListener}. So the rule was applied at one service and at neither of the
 * two listeners that consume {@link DomainEvent}, while <b>136 of 242</b> {@code publishEvent} call
 * sites sit inside a transaction.
 *
 * <h3>🔑 The two listeners failed in OPPOSITE directions</h3>
 *
 * <ul>
 *   <li>{@code AutomationRuleEngine#onDomainEvent} is {@code @Async}, so it could never poison a
 *       publisher — and it fired before the commit. Its conditions read {@code event.payload()},
 *       never the subject row, so what was wrong was the ACTIONS: a notification sent, a record
 *       created by a real {@code RecordCreator}, a run row written — all of it standing after a
 *       publisher rollback, because the engine's own transaction commits independently.</li>
 *   <li>{@code WorkflowEvidenceBridge#onEvidenceOutcome} is synchronous, so it JOINED the
 *       publisher's transaction: it saw uncommitted state correctly and could <b>poison the
 *       commit</b> — a bridge failure rolled back the evidence decision that triggered it — and
 *       saving the request there while the publisher also saves it is the double-flush shape.</li>
 * </ul>
 *
 * <p>🔑 <b>Neither fix could have been reasoned from the other</b>, which is why this gate asserts
 * the phase for both and the propagation for the synchronous one separately.
 *
 * <h3>Two flags that are load-bearing rather than decorative</h3>
 *
 * <p>{@code fallbackExecution = true} — a {@code @TransactionalEventListener} does <b>not</b>
 * receive an event published outside a transaction. The schedulers publish outside one
 * ({@code SocOpsScheduledSweep} alone has 12 such sites, a {@code @Component} with no class-level
 * {@code @Transactional}), so without the flag this fix would have silently stopped delivering
 * every swept event — a worse defect than the one it closes.
 *
 * <p>{@code REQUIRES_NEW} on a SYNCHRONOUS after-commit listener — that phase runs with the
 * completed transaction still bound, so a plain {@code @Transactional} would join it and write
 * without a live transaction. {@code AccessRequestService} records the live symptom: <i>"the created
 * request came back with a null id"</i>. An {@code @Async} listener is on another thread and creates
 * its own transaction regardless, so it does not need the propagation — and asserting it there
 * would be a gate demanding something pointless.
 */
@DisplayName("F2-438 — a domain-event listener must run after the publisher commits")
class CommitPhasedEventListenerContractTest {

    private static final Path CLASSES = Path.of("target", "classes");
    private static final Path SRC = Path.of("src", "main", "java");

    /** Every {@code @Bean}-managed method that takes a {@link DomainEvent} and listens. */
    private record Listener(Method method, boolean async) {}

    /** Top-level {@code .class} files on disk — the denominator {@link #shippedClasses()} must meet. */
    private static long topLevelClassFiles() {
        try (Stream<Path> files = Files.walk(CLASSES)) {
            return files.filter(x -> x.toString().endsWith(".class"))
                .filter(x -> !x.getFileName().toString().contains("$")).count();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + CLASSES, e);
        }
    }

    private static List<Class<?>> shippedClasses() {
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CLASSES)) {
            for (Path p : files.filter(x -> x.toString().endsWith(".class")).sorted().toList()) {
                String fqcn = CLASSES.relativize(p).toString()
                    .replace(java.io.File.separatorChar, '.').replaceAll("\\.class$", "");
                if (fqcn.contains("$")) continue;
                try {
                    out.add(ClassUtils.forName(fqcn, CommitPhasedEventListenerContractTest
                        .class.getClassLoader()));
                } catch (Throwable ignored) {
                    // Swallowed HERE and caught THERE: theCorpusWasActuallyRead asserts this list
                    // equals the top-level class files on disk, so an unloadable class fails that
                    // leg rather than quietly shrinking the population the other legs measure.
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + CLASSES, e);
        }
        return out;
    }

    /**
     * ⚠ Reflection over the shipped bytecode, never a source regex. F2-411 measured what the regex
     * costs on exactly this question: it reported 6 sites of which 3 were wrong — it attributed an
     * annotation to the next signature it could parse and missed a {@code protected} one — while
     * reflection says 5 of 2,014. And F2-424: an annotation separated from its method by a long
     * javadoc is invisible to a fixed lookback window, in the quiet direction.
     */
    private static Map<String, Listener> domainEventListeners() {
        Map<String, Listener> out = new LinkedHashMap<>();
        for (Class<?> c : shippedClasses()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 1) continue;
                if (!DomainEvent.class.equals(m.getParameterTypes()[0])) continue;
                boolean listens = AnnotationUtils.findAnnotation(m, EventListener.class) != null
                    || AnnotationUtils.findAnnotation(m, TransactionalEventListener.class) != null;
                if (!listens) continue;
                boolean async = AnnotationUtils.findAnnotation(
                    m, org.springframework.scheduling.annotation.Async.class) != null;
                out.put(c.getSimpleName() + "#" + m.getName(), new Listener(m, async));
            }
        }
        return out;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p, e);
        }
    }

    // ─── the legs ─────────────────────────────────────────────────────

    @Test
    void everyDomainEventListenerIsCommitPhased() {
        Map<String, Listener> listeners = domainEventListeners();
        List<String> notPhased = listeners.entrySet().stream()
            .filter(e -> AnnotationUtils.findAnnotation(
                e.getValue().method(), TransactionalEventListener.class) == null)
            .map(Map.Entry::getKey).toList();
        assertThat(notPhased).as("""
            A plain @EventListener on a DomainEvent fires when publishEvent is CALLED, not when the \
            publisher commits — so it either acts on a change that then rolls back (if @Async) or \
            joins the publisher's transaction and can poison its commit (if not). \
            AccessRequestService states the rule and applies it by hand; use \
            @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true). Found: %s"""
            .formatted(notPhased)).isEmpty();
    }

    @Test
    void everyCommitPhasedListenerRunsAfterCommitAndStillReceivesNonTransactionalEvents() {
        Map<String, Listener> listeners = domainEventListeners();
        assertThat(listeners).as("the reflective scan found no DomainEvent listener at all")
            .isNotEmpty();
        listeners.forEach((name, l) -> {
            TransactionalEventListener a = AnnotationUtils.findAnnotation(
                l.method(), TransactionalEventListener.class);
            assertThat(a).as("%s", name).isNotNull();
            assertThat(a.phase()).as(
                "%s: BEFORE_COMMIT can still poison the commit and AFTER_COMPLETION runs on "
                + "rollback too — only AFTER_COMMIT means 'this really happened'", name)
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(a.fallbackExecution()).as(
                "%s: without fallbackExecution the schedulers' events are never delivered — "
                + "SocOpsScheduledSweep alone publishes 12 times outside any transaction, so this "
                + "flag is the difference between a fix and a silent regression", name)
                .isTrue();
        });
    }

    @Test
    void aSynchronousAfterCommitListenerUsesRequiresNew() {
        // The half that is easy to get wrong and expensive: AFTER_COMMIT runs with the COMPLETED
        // transaction still bound, so REQUIRED would join it and write without a live transaction —
        // AccessRequestService's live-found "the created request came back with a null id".
        // Asserted only for the synchronous ones: an @Async listener is on another thread and gets
        // its own transaction regardless, so demanding it there would be a pointless gate.
        Map<String, Listener> listeners = domainEventListeners();
        List<String> sync = listeners.entrySet().stream()
            .filter(e -> !e.getValue().async()).map(Map.Entry::getKey).toList();
        assertThat(sync).as("if no listener is synchronous any more, this leg is asserting nothing "
            + "— re-derive it rather than deleting it").isNotEmpty();
        for (String name : sync) {
            Transactional t = AnnotationUtils.findAnnotation(
                listeners.get(name).method(), Transactional.class);
            assertThat(t).as("%s: a synchronous after-commit listener that writes needs its own "
                + "transaction", name).isNotNull();
            assertThat(t.propagation()).as(
                "%s: REQUIRED joins the COMPLETED transaction and writes without a live one", name)
                .isEqualTo(Propagation.REQUIRES_NEW);
        }
    }

    @Test
    void theRuleIsStillStatedWhereItWasFirstApplied() {
        // The sentence this whole finding rests on. If it is ever reworded, the next reader loses
        // the one place both failure modes are named together — and the hand-rolled afterCommit at
        // that service is the working exemplar the two annotations above replace.
        String svc = read(SRC.resolve(
            "com/tss/grc/module/iam/governance/service/AccessRequestService.java"));
        assertThat(svc).as("AccessRequestService is where this rule is recorded")
            .contains("AFTER COMMIT").contains("afterCommit()").contains("REQUIRES_NEW");
    }

    @Test
    void theNonTransactionalPublishersThatTheFallbackFlagProtectsStillExist() {
        // fallbackExecution is only load-bearing while something publishes outside a transaction.
        // If that ever stops being true the flag becomes cosmetic and this leg says so, rather than
        // leaving a reader to assume it is still doing work (F2-184).
        String sweep = read(SRC.resolve(
            "com/tss/grc/module/socops/service/SocOpsScheduledSweep.java"));
        assertThat(sweep).as("the exemplar non-transactional publisher").contains("publishEvent(");
        assertThat(sweep).as("""
            SocOpsScheduledSweep must still be a @Component with no class-level @Transactional — \
            that is what makes fallbackExecution load-bearing rather than defensive. If it gains \
            one, re-measure the population before trusting the flag's justification.""")
            .doesNotContain("@Transactional\n@Component")
            .doesNotContain("@Component\n@Transactional");
    }

    @Test
    void theCorpusWasActuallyRead() {
        // A hard zero over a collapsed corpus is the most reassuring possible artefact (F2-205).
        //
        // ⚠ The first draft of this leg asserted `> 3_000` from arithmetic rather than from a
        // measurement, and FAILED ON A CORRECT TREE (measured: 1,895) — F2-430's rule and F2-214's
        // one-thing-a-gate-must-never-do, caught only by running it. So the denominator is DERIVED:
        // every top-level .class file on disk must have loaded, which means a forName that starts
        // throwing fails this leg instead of silently shrinking the population everything else is
        // measured over.
        long onDisk = topLevelClassFiles();
        assertThat(onDisk).as("""
            The floor sits above the largest WRONG answer rather than above zero (F2-215). The wrong \
            answers here are: a walk that resolves nothing (0), and a walk whose nested-class skip \
            is inverted, which yields the 1,076 inner classes instead of the 1,895 top-level ones. \
            Measured 2026-09-02: 1,895 top-level of 2,971 class files.""")
            .isGreaterThan(1_500);
        assertThat((long) shippedClasses().size()).as(
            "every top-level class file must resolve — an unloadable class leaves the population "
            + "smaller than the tree, and a smaller population reads exactly like a cleaner tree")
            .isEqualTo(onDisk);
        assertThat(domainEventListeners().keySet())
            .as("the two known DomainEvent listeners must both be found, or the scan is measuring "
              + "something other than what it claims")
            .contains("AutomationRuleEngine#onDomainEvent",
                      "WorkflowEvidenceBridge#onEvidenceOutcome");
    }
}
