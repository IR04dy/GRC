package com.tss.grc.common.validation;

import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-179 — every {@link ClosedVocabulary} declared anywhere in main sources must match the
 * {@code CHECK} constraint it claims to mirror.
 *
 * <p>This is the durable half of the finding. The fix itself is a handful of normalising call sites;
 * what stops the next one is that a vocabulary <b>names the constraint it mirrors</b>, so this test
 * can check them mechanically — with no hand-maintained registry to forget to update. Declare a new
 * {@code ClosedVocabulary} anywhere and it is covered from the commit that adds it.
 *
 * <p>⚠ <b>The truth is read by reflection, not by a source regex.</b> CLAUDE.md records why:
 * {@code FrontendEndpointContractTest}'s backend half, written as a regex, reported 1,763 of 1,809
 * endpoints missing because {@code CyberController}'s javadoc says <i>"god-class was split"</i> and
 * {@code \bclass\s+\w+} matched the prose. A regex is used here only to <b>narrow the candidate
 * list</b> — the assertions read the real objects out of the real fields, so a token added in code
 * cannot pass by being absent from the text a pattern happened to match.
 *
 * <p>⚙ Why not scan every class in {@code target/classes}: reading a static field's value requires
 * running the class's static initialiser, and doing that to 1,800 classes for a vocabulary check is
 * both slow and a way to execute things a unit test has no business executing. Narrow first, reflect
 * second, and assert the narrowing worked.
 */
class ClosedVocabularyPinTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String DECLARATION = "ClosedVocabulary.of(";

    /**
     * The vocabularies expected to exist, so a regex that stops matching fails loudly rather than
     * passing on an empty set. Deliberately a <b>floor, not an equality</b>: adding a vocabulary
     * elsewhere must not fail this test, or the guard becomes a reason not to use the primitive.
     */
    private static final Set<String> MUST_BE_FOUND = Set.of(
        "CK_ml_drift_sev", "CK_ml_drift_type",
        "CK_wb_reporter_type", "CK_wb_channel", "CK_wb_category", "CK_wb_severity");

    @Test
    @DisplayName("every declared vocabulary matches the CHECK constraint it names")
    void declaredVocabulariesMatchTheirConstraints() throws Exception {
        Map<String, ClosedVocabulary> declared = findDeclarations();

        assertThat(declared.keySet())
            .as("anti-vacuity: the candidate scan must find the vocabularies this finding shipped. "
              + "An empty or shrunken map would make the comparison below pass while comparing "
              + "nothing — which is exactly how a guard becomes a decoration.")
            .containsAll(MUST_BE_FOUND);

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, ClosedVocabulary> e : declared.entrySet()) {
            ClosedVocabulary v = e.getValue();
            Set<String> sql = MigrationVocabularies.valuesOf(v.constraint());
            if (!sql.equals(v.tokens())) {
                mismatches.add(String.format(
                    "%s (%s)%n    declared in Java : %s%n    in the migration : %s%n"
                    + "    only in Java     : %s%n    only in the SQL  : %s",
                    e.getKey(), v.field(), new TreeSet<>(v.tokens()), new TreeSet<>(sql),
                    minus(v.tokens(), sql), minus(sql, v.tokens())));
            }
        }

        assertThat(mismatches).as(
            "A ClosedVocabulary that disagrees with its column's CHECK is the F2-180 defect waiting "
            + "to happen from the other direction: the Java set decides what the writer normalises "
            + "to, and a token the column does not accept becomes a 409 the caller cannot act on. "
            + "Either widen the CHECK by migration or correct the declaration — do not delete the "
            + "vocabulary, which just restores the open writer.%n%n%s",
            String.join("\n\n", mismatches))
            .isEmpty();
    }

    @Test
    @DisplayName("a vocabulary cannot be declared without naming a constraint, or in lower case")
    void theDeclarationItselfRefusesTheTwoWaysItCouldBecomeUncheckable() {
        // No constraint name → nothing can check the set against the schema, which is the whole
        // reason a bare Set.of(...) drifted three times in AttachmentAccessPolicy.
        assertThatThrownBy(() -> ClosedVocabulary.of("severity", "", "LOW"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CHECK constraint");
        assertThatThrownBy(() -> ClosedVocabulary.of("severity", null, "LOW"))
            .isInstanceOf(IllegalArgumentException.class);

        // Lower-case tokens → the canonical form would no longer be upper case, and every
        // comparison in the platform assumes it is.
        assertThatThrownBy(() -> ClosedVocabulary.of("severity", "CK_x", "low"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("upper case");

        // An empty vocabulary accepts nothing and would refuse every request.
        assertThatThrownBy(() -> ClosedVocabulary.of("severity", "CK_x"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a misspelled constraint name fails loudly rather than as an empty vocabulary")
    void aMisspelledConstraintIsNotSilentlyEmpty() {
        // F2-177's 'AUDIT_COMMITTE' shape — one letter short fails exactly like a ghost name, and an
        // empty Set would have made the pin above pass for every vocabulary at once.
        assertThatThrownBy(() -> MigrationVocabularies.valuesOf("CK_ml_drift_se"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("CK_ml_drift_se");
        assertThat(MigrationVocabularies.valuesOf("CK_ml_drift_sev")).isNotEmpty();
    }

    // ── candidate discovery ──────────────────────────────────────────────────────────

    /** constraint name -> the live object, for every {@code ClosedVocabulary} in main sources. */
    private static Map<String, ClosedVocabulary> findDeclarations() throws Exception {
        Map<String, ClosedVocabulary> out = new LinkedHashMap<>();
        for (Class<?> c : candidateClasses()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                    || !ClosedVocabulary.class.equals(f.getType())) {
                    continue;
                }
                f.setAccessible(true);
                ClosedVocabulary v = (ClosedVocabulary) f.get(null);
                ClosedVocabulary prior = out.put(v.constraint(), v);
                // Two declarations of one constraint are allowed only if they agree; if they differ,
                // one of them is already drifting and the pin below would report whichever won.
                if (prior != null && !prior.tokens().equals(v.tokens())) {
                    throw new AssertionError(
                        "two ClosedVocabulary declarations for " + v.constraint()
                        + " disagree: " + prior.tokens() + " vs " + v.tokens()
                        + " — declare it once and share it.");
                }
            }
        }
        return out;
    }

    private static List<Class<?>> candidateClasses() throws IOException {
        List<Class<?>> classes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            for (Path p : walk.filter(x -> x.getFileName().toString().endsWith(".java")).toList()) {
                if (!Files.readString(p).contains(DECLARATION)) continue;
                String name = MAIN_SOURCES.relativize(p).toString()
                    .replace('\\', '.').replace('/', '.').replaceAll("\\.java$", "");
                if (!seen.add(name)) continue;
                try {
                    classes.add(Class.forName(name));
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    // A source file whose class is not on the test classpath cannot declare a
                    // vocabulary that ships; skipping it is correct, and MUST_BE_FOUND is what
                    // guarantees the skip did not swallow one that matters.
                }
            }
        }
        return classes;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

}
