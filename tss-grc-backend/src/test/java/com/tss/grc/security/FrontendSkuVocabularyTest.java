package com.tss.grc.security;

import com.tss.grc.module.platform.licensing.service.LicensingService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-04b — the frontend's SKU vocabulary must be the server's.
 *
 * <p>The frontend licence gate needs the 15 ratified codes as a compile-time union
 * ({@code RatifiedSku} in {@code lib/licensing.ts}) so that a typo in its
 * path→SKU map fails {@code tsc} rather than silently hiding a module from every
 * tenant. That means a second copy of a list the server owns, and an
 * uncontrolled second copy is exactly how <b>F2-35</b> happened: the SKU
 * catalogue existed in three contradictory versions at once, disagreeing on a
 * commercial question and on grain by roughly threefold.
 *
 * <p>So the copy is held to the original from this side. Adding a SKU to
 * {@code SELLABLE_MODULES} without adding it to the frontend fails the backend
 * build, which is the only place a Java developer will notice.
 *
 * <h3>Why each direction matters, concretely</h3>
 * <ul>
 *   <li><b>In Java, missing from TS</b> — the new SKU is sellable and the UI never
 *       gates it. Every tenant keeps seeing its nav entries, including the ones
 *       who did not buy it. The server refuses the APIs, so the screens are
 *       present and broken: the worst of both.</li>
 *   <li><b>In TS, missing from Java</b> — the frontend hides routes against a code
 *       no licence row can ever carry, so the module is invisible to
 *       <em>everyone</em>, permanently, with no error anywhere. This is the F2-23
 *       shape (a guard nobody can satisfy reading as a working control) pointed
 *       at revenue.</li>
 * </ul>
 *
 * <p>Reading across the module boundary is unusual and deliberate, following
 * {@code AttachmentEntityTypeCoverageTest}: the invariant genuinely spans the two
 * codebases, and the alternative is the drift. When the frontend is not checked
 * out the test skips, and an anti-vacuity assertion makes sure a skip cannot be
 * mistaken for a pass.
 *
 * @see com.tss.grc.security.SkuCatalogueRatchetTest which holds ADR-018 ↔ Java.
 *      With this class the chain closes: ADR-018 ↔ SELLABLE_MODULES ↔ SkuRegistry
 *      ↔ 197 annotations ↔ RATIFIED_SKUS ↔ the frontend gate.
 */
class FrontendSkuVocabularyTest {

    private static final Path LICENSING_TS =
        Path.of("..", "tss-grc-ui-v2", "src", "lib", "licensing.ts");

    /** `export const RATIFIED_SKUS = [ 'A', 'B', … ] as const;` */
    private static final Pattern BLOCK = Pattern.compile(
        "export\\s+const\\s+RATIFIED_SKUS\\s*=\\s*\\[(.*?)]\\s*as\\s+const", Pattern.DOTALL);
    private static final Pattern CODE = Pattern.compile("'([A-Z0-9_]+)'");

    private static String source() {
        Assumptions.assumeTrue(Files.isRegularFile(LICENSING_TS),
            "frontend sources not present next to the backend — skipping the SKU vocabulary check");
        try {
            return Files.readString(LICENSING_TS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + LICENSING_TS.toAbsolutePath(), e);
        }
    }

    private static Set<String> frontendSkus() {
        Matcher m = BLOCK.matcher(source());
        assertThat(m.find())
            .as("`export const RATIFIED_SKUS = [...] as const` not found in %s. If it was renamed "
                + "or moved, update this test — do NOT delete it, or the two lists are free to "
                + "diverge again (F2-35).", LICENSING_TS)
            .isTrue();
        Set<String> out = new TreeSet<>();
        Matcher c = CODE.matcher(m.group(1));
        while (c.find()) {
            out.add(c.group(1));
        }
        return out;
    }

    @Test
    void theFrontendSellsExactlyWhatTheServerSells() {
        assertThat(frontendSkus())
            .as("lib/licensing.ts RATIFIED_SKUS must equal LicensingService.SELLABLE_MODULES. "
                + "A code only in Java is sold but never gated in the UI; a code only in "
                + "TypeScript hides routes against a licence row that can never exist.")
            .isEqualTo(new TreeSet<>(LicensingService.SELLABLE_MODULES));
    }

    /**
     * Anti-vacuity. Every assertion above is satisfiable by an empty set on both
     * sides, and the most likely way this file stops working is the regex ceasing
     * to match — which yields an empty parse, not an error.
     */
    @Test
    void bothListsAreNonTriviallyPopulated() {
        assertThat(frontendSkus()).hasSize(15);
        assertThat(LicensingService.SELLABLE_MODULES).hasSize(15);
        // One spot-check that the parse produced real codes rather than, say, a
        // single empty string that happened to compare equal to another one.
        assertThat(frontendSkus()).contains("BCM", "PDPL", "SAUDI_FS", "DATA_AI_GOV");
    }

    /**
     * The frontend must not gate against Base. Base Platform is mandatory and
     * carries no licence row (ADR-018 R4), so a `@RequireModule("BASE")`-shaped
     * entry on either side denies every tenant — the mistake this programme has
     * made twice by checking only one direction of set containment (ADR-017
     * D-10c, then F2-61(a)).
     */
    @Test
    void neitherSideSellsBase() {
        assertThat(frontendSkus()).doesNotContain("BASE", "BASE_PLATFORM", "PLATFORM");
        assertThat(LicensingService.SELLABLE_MODULES).doesNotContain("BASE", "BASE_PLATFORM");
    }
}
