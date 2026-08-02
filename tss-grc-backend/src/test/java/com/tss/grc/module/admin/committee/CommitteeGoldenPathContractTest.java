package com.tss.grc.module.admin.committee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * W1-5 / F2-18 — the ratchet that keeps the golden-path integration test from becoming decorative.
 *
 * <h3>Why this exists</h3>
 * {@link CommitteeGoldenPathIT} needs a database, so it lives behind the {@code it} Maven profile
 * and does not run in {@code mvn -o clean package}. That is the right call for the build contract
 * and the wrong shape for assurance: something that only runs when someone remembers to run it is
 * the exact defect this programme closed twice already — F-4 shipped a boot {@code WARN} describing
 * a fail-open and the fail-open survived it for a week (F2-87), and F2-62 shipped a report that
 * agreed with an enforcement point it did not share a predicate with.
 *
 * <p>So the parts that <em>can</em> be checked without a database are checked here, in the ordinary
 * suite, on every build: that the IT exists, that it still goes through real HTTP, that it still
 * covers every declared step, and that the Maven wiring which makes it runnable is still present.
 * A deleted, renamed, gutted or unwired golden path fails a normal {@code mvn test}.
 *
 * <p>What this deliberately does <b>not</b> claim: that the golden path passes. Only running it
 * proves that. This asserts it is still there to be run.
 */
@DisplayName("W1-5 — golden-path integration test contract")
class CommitteeGoldenPathContractTest {

    private static final Path IT_SOURCE = Path.of(
        "src/test/java/com/tss/grc/module/admin/committee/CommitteeGoldenPathIT.java");
    private static final Path POM = Path.of("pom.xml");

    @Test
    @DisplayName("the golden path boots a real context and serves real HTTP")
    void theGoldenPathUsesTheRealStack() {
        SpringBootTest ann = CommitteeGoldenPathIT.class.getAnnotation(SpringBootTest.class);
        assertThat(ann)
            .as("CommitteeGoldenPathIT must be a @SpringBootTest — a standaloneSetup MockMvc has no "
                + "filter chain, no security and no JPA, which is the gap F2-18 names")
            .isNotNull();
        assertThat(ann.webEnvironment())
            .as("the path must cross a real socket: MOCK would skip the servlet container and "
                + "NONE would skip HTTP altogether")
            .isEqualTo(SpringBootTest.WebEnvironment.RANDOM_PORT);
    }

    @Test
    @DisplayName("every declared golden-path step is exercised by the IT")
    void everyDeclaredStepIsExercised() {
        String source = read(IT_SOURCE);
        List<String> missing = new ArrayList<>();
        for (String stepName : CommitteeGoldenPath.STEPS) {
            if (!source.contains("step(\"" + stepName + "\")")) missing.add(stepName);
        }
        assertThat(missing)
            .as("declared in CommitteeGoldenPath.STEPS but never recorded by the IT — either the "
                + "step stopped being exercised or the list grew without the walk")
            .isEmpty();
    }

    @Test
    @DisplayName("the IT records no step the contract does not declare")
    void theItDeclaresNoUndeclaredStep() {
        String source = read(IT_SOURCE);
        List<String> undeclared = new ArrayList<>();
        int from = 0;
        while (true) {
            int at = source.indexOf("step(\"", from);
            if (at < 0) break;
            int start = at + "step(\"".length();
            int end = source.indexOf('"', start);
            String name = source.substring(start, end);
            if (!CommitteeGoldenPath.STEPS.contains(name)) undeclared.add(name);
            from = end;
        }
        assertThat(undeclared)
            .as("the IT records these but CommitteeGoldenPath.STEPS does not list them, so the "
                + "coverage assertion above would not notice if they disappeared")
            .isEmpty();
    }

    @Test
    @DisplayName("Maven can actually run it — the it profile still wires failsafe for *IT.java")
    void mavenStillWiresFailsafe() {
        String pom = read(POM);
        int profile = pom.indexOf("<id>it</id>");
        assertThat(profile)
            .as("pom.xml has no <profile><id>it</id> — the golden path became unrunnable, which is "
                + "indistinguishable from deleting it")
            .isGreaterThan(-1);

        String tail = pom.substring(profile);
        int end = tail.indexOf("</profile>");
        String itProfile = end > 0 ? tail.substring(0, end) : tail;

        assertThat(itProfile).as("the it profile must configure failsafe")
            .contains("maven-failsafe-plugin");
        assertThat(itProfile).as("failsafe must include *IT.java")
            .contains("**/*IT.java");
        assertThat(itProfile).as("failsafe must be bound to a goal, or configuring it does nothing")
            .contains("<goal>integration-test</goal>");
    }

    @Test
    @DisplayName("worked evidence is retained, not tagged for deletion")
    void theEvidenceTagIsNotOneTheCleanupScriptsDelete() {
        // W1-5 is explicit: retain the worked decision as [DEMO-EVIDENCE], never [REVIEW-TEST],
        // because the repo's cleanup scripts delete the latter — so a review sweep would silently
        // erase the platform's one end-to-end proven committee decision.
        assertThat(CommitteeGoldenPath.EVIDENCE_TAG).isEqualTo("[DEMO-EVIDENCE]");

        // Match the tag as DATA, not as prose. The first version of this assertion searched the
        // source for the bare text and failed on the javadoc sentence explaining why the tag is
        // wrong — a scanner that matches too much invents defects just as surely as one that
        // matches too little invents clean bills of health.
        assertThat(read(IT_SOURCE))
            .as("the IT must not write [REVIEW-TEST] into any row it creates")
            .doesNotContain("\"[REVIEW-TEST]\"")
            .doesNotContain("[REVIEW-TEST] \"")
            .doesNotContain("\"[REVIEW-TEST]");
    }

    private static String read(Path relative) {
        Path p = relative.toAbsolutePath();
        if (!Files.exists(p)) {
            fail("Expected file is missing: " + p
                + " (surefire runs with basedir = the module directory)");
        }
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Could not read " + p, e);
        }
    }
}
