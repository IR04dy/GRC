package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-344 — the 72-hour SDAIA personal-data-breach notification is PDPL <b>Article 20</b>
 * (the canonical {@code lib/moduleAnchors.ts} records it, and the privacy / data_officer
 * modules cite it). Article 23 is the data-subject-request 30-day article. The crisis
 * ({@code module/bcm/cycle}) module and several UI surfaces mis-cited the 72-hour breach as
 * Article 23 — F2-214 fixed one wizard and left the family (F2-215's two-of-N); F2-344 swept
 * the rest (a regulator-countdown legal basis, an After-Action Report line, an exception
 * message, an audit-log line, a workflow-catalogue entry, a badge and a card title, EN + AR).
 *
 * <p>This gate is the regression guard: no source line may tie an "Article 23" citation to a
 * 72-hour / breach context, and the canonical Article 20 ↔ breach / Article 23 ↔ DSR mapping
 * in {@code moduleAnchors.ts} must stand. Hermetic — reads both source trees; a wrong citation
 * is text, and only a live probe could see it rendered, so it is pinned structurally
 * (F2-320 / F2-325). Line-scoped with a DSR disambiguator, so a line explaining "Article 23 is
 * the 30-day data-subject article" (as opposed to the 72-hour breach) is not a violation.
 */
class PdplBreachArticleCitationTest {

    private static final Path BACKEND = Path.of("src/main/java/com/tss/grc/module");
    private static final Path FRONTEND = Path.of("../tss-grc-ui-v2/src");

    /** An "Article 23" citation token — Latin or Arabic (المادة ٢٣ / م.23 / م.٢٣). */
    private static final Pattern ART23 = Pattern.compile(
        "(?:Article|Art\\.?)\\s*23\\b"
        + "|المادة\\s*٢٣"
        + "|م\\.\\s*(?:23|٢٣)",
        Pattern.CASE_INSENSITIVE);

    /** A 72-hour / personal-data-breach context on the same line. */
    private static final Pattern BREACH = Pattern.compile(
        "breach|خرق|72-hour|72h|72 hours|(?<!\\d)72(?!\\d)|٧٢",
        Pattern.CASE_INSENSITIVE);

    /** A data-subject-request / 30-day marker that disambiguates a legitimate Article 23 line. */
    private static final Pattern DSR = Pattern.compile(
        "30-day|30 day|data.subject|response window|fulfil|DSR|٣٠ يوم",
        Pattern.CASE_INSENSITIVE);

    private static boolean isViolation(String line) {
        return ART23.matcher(line).find()
            && BREACH.matcher(line).find()
            && !DSR.matcher(line).find();
    }

    @Test
    void noSourceCitesArticle23ForTheBreachNotification() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : List.of(BACKEND, FRONTEND)) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(Files::isRegularFile).toList()) {
                    String name = p.getFileName().toString();
                    if (!(name.endsWith(".java") || name.endsWith(".ts") || name.endsWith(".tsx"))) continue;
                    if (name.equals("PdplBreachArticleCitationTest.java")) continue;   // this file quotes the shape
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        if (isViolation(lines.get(i))) {
                            hits.add(p + ":" + (i + 1) + "  " + lines.get(i).trim());
                        }
                    }
                }
            }
        }
        assertThat(hits)
            .as("PDPL breach 72h is Article 20, not 23 — mis-citations:\n%s", String.join("\n", hits))
            .isEmpty();
    }

    @Test
    void moduleAnchorsKeepsTheCanonicalArticle20BreachMapping() throws IOException {
        Path anchors = FRONTEND.resolve("lib/moduleAnchors.ts");
        assertThat(Files.isRegularFile(anchors)).as("moduleAnchors.ts present").isTrue();
        String src = Files.readString(anchors);
        // Article 20 is the breach window; Article 23 is the DSR 30-day SLA.
        assertThat(src)
            .as("Article 20 must be the breach citation")
            .containsPattern("Article 20'[^\\n]*breach");
        assertThat(src)
            .as("Article 23 must remain the data-subject/30-day citation")
            .containsPattern("Article 23'[^\\n]*(?:Data-subject|30-day)");
    }

    @Test
    void selfTest_theDetectorFiresOnAMisCitationAndSparesTheDsrArticle() {
        assertThat(isViolation("descEn: 'PDPL Article 23 — 72-hour breach notification.'")).isTrue();
        assertThat(isViolation("anchor: 'PDPL Art. 23' // 72h breach")).isTrue();
        assertThat(isViolation("المادة ٢٣ — إشعار خرق")).isTrue();
        // Article 23 legitimately used for the DSR 30-day window — NOT a violation.
        assertThat(isViolation("PDPL Article 23 (30-day response window) — data-subject request")).isFalse();
        // Article 20 for the breach — the correct citation — NOT a violation.
        assertThat(isViolation("SDAIA PDPL Article 20 — 72h personal data breach")).isFalse();
    }
}
