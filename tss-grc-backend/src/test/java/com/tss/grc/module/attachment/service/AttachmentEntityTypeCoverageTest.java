package com.tss.grc.module.attachment.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SCR-004's completeness ratchet — {@link AttachmentAccessPolicy} fails closed, so every
 * {@code entityType} the SPA can send has to resolve or a working attachment panel answers 403.
 *
 * <p>That risk is not hypothetical. Fail-closed authorization plus a hand-maintained registry is
 * exactly the shape that falsified six of seven prescriptions in this programme: the fix looks right
 * in isolation and breaks a real screen. So the set is not trusted to a code review — it is
 * extracted from the frontend source on every build. Adding
 * {@code <AttachmentsField entityType="NEW_THING">} without a registry entry fails here, in seconds,
 * instead of in a user's face.
 *
 * <p>This test reads across the module boundary into {@code ../tss-grc-ui-v2/src}. That is unusual
 * and deliberate: the invariant genuinely spans the two, and the alternative — a second hand-kept
 * list on the frontend side — is the thing that drifts. When the directory is absent (a
 * backend-only checkout or artifact build) the test skips rather than failing for the wrong reason,
 * and an anti-vacuity assertion below makes sure a skip is never mistaken for a pass.
 */
class AttachmentEntityTypeCoverageTest {

    private static final Path UI = Path.of("..", "tss-grc-ui-v2", "src");

    private static void requireUiSources() {
        Assumptions.assumeTrue(Files.isDirectory(UI),
            "frontend sources not present next to the backend — skipping the FE coverage scan");
    }

    /** {@code <AttachmentsField …>} / {@code <EntityDocsModal …>}, tags may span lines. */
    private static final Pattern TAG = Pattern.compile(
        "<(?:AttachmentsField|EntityDocsModal)\\b([^>]*?)/?>", Pattern.DOTALL);
    private static final Pattern PROP_LITERAL = Pattern.compile(
        "entityType\\s*=\\s*[\"']([^\"']+)[\"']");
    /** FormModal field definitions: {@code {kind:'attachments', entityType:'X'}} (either order). */
    private static final Pattern FIELD_FORWARD = Pattern.compile(
        "kind\\s*:\\s*['\"]attachments['\"].{0,300}?entityType\\s*:\\s*['\"]([^'\"]+)['\"]",
        Pattern.DOTALL);
    private static final Pattern FIELD_REVERSE = Pattern.compile(
        "entityType\\s*:\\s*['\"]([^'\"]+)['\"].{0,300}?kind\\s*:\\s*['\"]attachments['\"]",
        Pattern.DOTALL);
    /** Direct calls: {@code attachmentsApi.list('X', …)} / {@code .upload('X', …)}. */
    private static final Pattern DIRECT_CALL = Pattern.compile(
        "attachmentsApi\\.(?:list|upload)\\(\\s*['\"]([^'\"]+)['\"]");

    @Test
    void everyEntityTypeTheFrontendCanSendResolvesInTheRegistry() throws IOException {
        requireUiSources();
        TreeMap<String, String> found = new TreeMap<>();          // entityType -> first file seen in

        try (Stream<Path> files = Files.walk(UI)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".tsx") || p.toString().endsWith(".ts"))
                 .forEach(p -> {
                     String text = read(p);
                     String rel = UI.relativize(p).toString();
                     Matcher tag = TAG.matcher(text);
                     while (tag.find()) {
                         Matcher lit = PROP_LITERAL.matcher(tag.group(1));
                         while (lit.find()) found.putIfAbsent(lit.group(1), rel);
                     }
                     for (Pattern pattern : new Pattern[]{FIELD_FORWARD, FIELD_REVERSE, DIRECT_CALL}) {
                         Matcher m = pattern.matcher(text);
                         while (m.find()) found.putIfAbsent(m.group(1), rel);
                     }
                 });
        }

        // Anti-vacuity: if the scan silently stops matching (a renamed component, a JSX style
        // change), it would "pass" by finding nothing. The measured count at the time of writing
        // was 56 across 40-odd pages.
        assertThat(found)
            .as("the scan found almost nothing — the patterns have gone stale, not the codebase")
            .hasSizeGreaterThan(45);

        Set<String> unresolved = new TreeSet<>();
        found.forEach((type, file) -> {
            if (AttachmentAccessPolicy.guardFor(type).isEmpty()) {
                unresolved.add(type + "  (" + file + ")");
            }
        });

        assertThat(unresolved)
            .as("these entityTypes reach /api/v1/attachments from the SPA but have no entry in "
                + "AttachmentAccessPolicy, so the panel will 403. Add each one with the parent "
                + "record's OWN read guard — read it off the parent controller, do not infer it "
                + "from the name")
            .isEmpty();
    }

    @Test
    void everyEntityTypeTheBackendItselfPassesResolvesToo() throws IOException {
        // ObligationController and PolicyController call AttachmentService.list directly with
        // literal types ("Obligation", "ObligationAttestation", "PolicyDocument"). Those calls do
        // not go through the policy — but the same records are reachable through the generic
        // endpoints, where they do, so a missing entry would split one record's files into
        // readable-by-one-route and 403-by-the-other.
        Path backend = Path.of("src", "main", "java");
        Pattern internal = Pattern.compile(
            "attachments?\\s*\\.\\s*list\\(\\s*\"([^\"]+)\"", Pattern.DOTALL);

        Set<String> types = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(backend)) {
            files.filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> {
                     Matcher m = internal.matcher(read(p));
                     while (m.find()) types.add(m.group(1));
                 });
        }

        assertThat(types)
            .as("the internal callers stopped using literal entity types — re-check this pattern "
                + "before trusting the assertion below")
            .isNotEmpty();
        assertThat(types.stream().filter(t -> AttachmentAccessPolicy.guardFor(t).isEmpty()).toList())
            .as("backend-side entity types missing from the registry")
            .isEmpty();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
