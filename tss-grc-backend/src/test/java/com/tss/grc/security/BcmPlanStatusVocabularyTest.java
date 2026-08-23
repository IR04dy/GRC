package com.tss.grc.security;

import com.tss.grc.module.bcm.service.BcmService;
import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-299 — {@code CK_bcm_plans_status} must admit <b>exactly</b> what {@code BcmService} can write.
 *
 * <p>{@code bcm.bcm_plans} had zero CHECK constraints, so this column was narrowed only by that
 * service's five writers. F2-183's rule is that a constraint and its writers land together, and
 * V395 is the second half of a sequence rather than a fresh claim: [[F2-296]] gave the register an
 * echo-tolerant / change-refusing guard on the entity-bound PUT, and [[F2-298]] removed the
 * {@code = "DRAFT"} initialiser that made that guard fire on every non-DRAFT plan.
 *
 * <p>⚠⚠ <b>The vocabulary is FOUR tokens and the entity's own javadoc said five.</b> It read
 * {@code DRAFT | UNDER_REVIEW | APPROVED | INVOKED | ARCHIVED (see BcmService)} — and ARCHIVED
 * appears nowhere in {@code BcmService}, its controller, or {@code BcmPage}. It was copied from the
 * CANONICAL register ({@code bcm.bcp_plans}, which really does have ARCHIVED / PUBLISHED /
 * SUPERSEDED / CANCELLED), so [[F2-234]]'s two-registers-for-one-concept hazard bit the
 * DOCUMENTATION first. 🔑 <b>The citation is what made it credible — "see BcmService" invites the
 * reader not to look</b> — and it would have been load-bearing: writing the CHECK from that line
 * would have blessed a state no route can reach, which is worse than blessing today's values,
 * because it makes the vocabulary look like five.
 *
 * <p>So this gate does not hold a typed-out list. It <b>derives</b> the writable set from the
 * service's own literals and compares it with the CHECK parsed out of the migration corpus, in
 * both directions:
 *
 * <ul>
 *   <li>a token the CHECK admits that no writer produces is a <b>blessed unreachable state</b> —
 *       the ARCHIVED defect, and the reason a hand-written list would not have caught it;</li>
 *   <li>a token a writer produces that the CHECK refuses turns a legal ISO 22301 transition into a
 *       547 at run time, which surfaces as a 409 naming no field ([[F2-230]]).</li>
 * </ul>
 *
 * <p>⚠ The scan is scoped by the RECEIVER's declared type, because {@code BcmService} also writes
 * {@code e.setStatus("COMPLETED")} on a {@code BcmExercise} and copies a caller's status onto a
 * {@code BiaRecord} — a bare {@code setStatus\("([A-Z_]+)"\)} sweep of this file would put
 * COMPLETED in the plan's vocabulary and the assertion would then demand the CHECK admit it.
 */
class BcmPlanStatusVocabularyTest {

    private static final Path SERVICE =
        Paths.get("src/main/java/com/tss/grc/module/bcm/service/BcmService.java");

    private static final String CONSTRAINT = "CK_bcm_plans_status";

    /** Locals declared of a given entity type, so a setter call can be attributed to it. */
    private static Set<String> localsOfType(String src, String type) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("\\b" + Pattern.quote(type) + "\\s+(\\w+)\\s*=").matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Every status literal written onto a local of {@code type}, plus the declared initial state. */
    static Set<String> writableStatuses(String src, String type) {
        Set<String> out = new TreeSet<>();
        for (String local : localsOfType(src, type)) {
            Matcher m = Pattern.compile("\\b" + Pattern.quote(local)
                + "\\s*\\.\\s*setStatus\\s*\\(\\s*\"([A-Z_]+)\"\\s*\\)").matcher(src);
            while (m.find()) out.add(m.group(1));
        }
        return out;
    }

    private static String serviceSource() {
        try {
            return Files.readString(SERVICE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String initialPlanStatus() {
        try {
            Field f = BcmService.class.getDeclaredField("INITIAL_PLAN_STATUS");
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                "BcmService.INITIAL_PLAN_STATUS is gone — the create path's declared initial state "
                + "is what ServerOwnedState.requireInitial enforces, and this gate reads it rather "
                + "than assuming DRAFT", e);
        }
    }

    /** The four the service can put in the column: the transition literals plus the initial state. */
    private static Set<String> declaredByTheWriters() {
        Set<String> out = new TreeSet<>(writableStatuses(serviceSource(), "BcmPlan"));
        out.add(initialPlanStatus());
        return out;
    }

    @Test
    @DisplayName("the CHECK admits exactly the states BcmService can write — no more, no fewer")
    void theCheckMirrorsTheWriters() {
        Set<String> writers = declaredByTheWriters();
        Set<String> check = new TreeSet<>(MigrationVocabularies.valuesOf(CONSTRAINT));

        assertThat(writers)
            .as("anti-vacuity: the derivation must find the machine's own states, or every "
                + "assertion below passes over an empty set")
            .containsExactlyInAnyOrder("DRAFT", "UNDER_REVIEW", "APPROVED", "INVOKED");

        assertThat(check)
            .as("F2-299 — %s must admit exactly what BcmService writes. A token the CHECK admits "
                + "and no writer produces is a BLESSED UNREACHABLE STATE (the ARCHIVED defect this "
                + "finding started from); a token a writer produces and the CHECK refuses turns a "
                + "legal ISO 22301 transition into a 547 that surfaces as a 409 naming no field. "
                + "writers=%s check=%s", CONSTRAINT, writers, check)
            .containsExactlyInAnyOrderElementsOf(writers);
    }

    @Test
    @DisplayName("ARCHIVED is not admitted, and is not written — the phantom stays out of both")
    void theArchivedPhantomIsInNeitherTheCheckNorTheWriters() {
        assertThat(MigrationVocabularies.valuesOf(CONSTRAINT))
            .as("ARCHIVED belongs to the CANONICAL register (bcm.bcp_plans), not this one. It "
                + "reached BcmPlan#status's javadoc by being copied from there, and admitting it "
                + "here would make the vocabulary look like five (F2-299)")
            .doesNotContain("ARCHIVED");

        assertThat(declaredByTheWriters())
            .as("and no writer produces it either — if one ever does, the mirror above fails "
                + "first and this leg says which direction moved")
            .doesNotContain("ARCHIVED");
    }

    @Test
    @DisplayName("the derivation is scoped by receiver type, so a sibling entity's literal stays out")
    void theDerivationIsScopedByReceiverType() {
        // ⚠ BcmService really does write e.setStatus("COMPLETED") on a BcmExercise. An unscoped
        // sweep of this file would put COMPLETED in the plan's vocabulary and then demand the
        // CHECK admit it — a gate failing on correct code, the one thing a gate must never do
        // (F2-214). Pinned against the live file, not a fixture, because that literal is really
        // there and its presence is the whole reason the scoping exists.
        String src = serviceSource();
        assertThat(src)
            .as("if this literal moves, the scoping test below stops proving anything")
            .contains("setStatus(\"COMPLETED\")");

        assertThat(writableStatuses(src, "BcmPlan"))
            .as("COMPLETED is an EXERCISE state and must not be attributed to the plan")
            .doesNotContain("COMPLETED");

        assertThat(writableStatuses(src, "BcmExercise"))
            .as("and the same derivation, pointed at the exercise, must find it — otherwise the "
                + "scoping could be excluding everything and this would still look correct")
            .contains("COMPLETED");
    }

    @Test
    @DisplayName("the detector finds a literal on a plan local, and not one on another type")
    void theDetectorIsSound() {
        String fixture = "class S {\n"
            + "  void a(Long id) { BcmPlan p = getPlan(id); p.setStatus(\"APPROVED\"); }\n"
            + "  void b(Long id) { BcmExercise e = getEx(id); e.setStatus(\"COMPLETED\"); }\n"
            + "}\n";
        assertThat(writableStatuses(fixture, "BcmPlan")).containsExactly("APPROVED");
        assertThat(writableStatuses(fixture, "BcmExercise")).containsExactly("COMPLETED");

        // A status copied from a REQUEST is not a literal and must not enter the vocabulary: it is
        // the caller's value, which is exactly what the CHECK exists to narrow.
        String fromRequest = "class S {\n"
            + "  void c(Long id, BcmPlan req) { BcmPlan x = getPlan(id); x.setStatus(req.getStatus()); }\n"
            + "}\n";
        assertThat(writableStatuses(fromRequest, "BcmPlan"))
            .as("a request-sourced status is not a declared state")
            .isEmpty();
    }
}
