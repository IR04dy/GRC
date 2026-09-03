package com.tss.grc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-450 — a set built from {@code getAuthorities()} is not a set of ROLES.
 *
 * <p>{@code CustomUserDetailsService} grants <b>two</b> kinds of authority: {@code ROLE_<roleName>}
 * for each role, and each <b>permission name</b> verbatim ({@code ADMIN_APPROVE}, {@code AI_WRITE},
 * {@code BCM_EXPORT} …). Measured 2026-09-03: <b>144 permission strings in dev, 114 in prod</b>.
 * So a "roles" set built by stripping the prefix <i>where present</i> and keeping everything else
 * carries every permission the principal holds.
 *
 * <p>🔑 That mattered because {@code ApprovalChainController#acting}'s set is the input to
 * {@code ApprovalChainInstanceService#matchesActor} — <b>who may sign an approval step</b>. A
 * step's {@code required_role} is free text a tenant admin types, and permission names look
 * exactly like the role names this platform already ships ({@code DLP_INGEST},
 * {@code SANCTIONS_CHECK}), so a step configured to require one would have been satisfied by
 * every holder of that permission rather than by the intended job family.
 *
 * <p>⚠ <b>Latent, and say which you have.</b> Measured in both databases: the role-name /
 * permission-name intersection is <b>0</b>, and none of the 15 configured {@code required_role}
 * values is a permission string — so nothing was mis-authorised, and the narrowing provably
 * cannot refuse a step that works today (F2-214). What it buys is that the hazard is <b>absent</b>
 * rather than latent (F2-213).
 *
 * <p>🔑🔑 <b>Three other sites in the platform strip {@code ROLE_} and all three FILTER on the
 * prefix first</b> — {@code NdiAuditService#firstRole}, {@code SignatureService}, and
 * {@code WorkflowRequestService} in a <b>sibling service of the same module</b>. One outlier of
 * four, and the outlier was the one feeding an authorisation decision (F2-205/F2-282's
 * lone-outlier tell; F2-215's two-of-N with the correct idiom present three times over).
 *
 * <p>This gate is a <b>hard zero with no exemption map</b>, and that is safe rather than strict:
 * the filtered form is always available and is strictly more precise, so there is no correct code
 * for it to fail on.
 */
class ActingRoleSetContractTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "tss", "grc");

    /** The defect: keep the authority when it is NOT {@code ROLE_}-prefixed. */
    private static final Pattern KEEPS_NON_PREFIXED = Pattern.compile(
        "startsWith\\(\\s*\"ROLE_\"\\s*\\)\\s*\\?[^;]{0,120}:\\s*\\w+\\s*\\)");

    /** Every site that derives a role name from an authority string. */
    private static final Pattern STRIPS_PREFIX = Pattern.compile(
        "substring\\(\\s*(?:5|\"ROLE_\"\\.length\\(\\))\\s*\\)");

    private static final Pattern COMMENT = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String stripped(Path p) {
        try {
            Matcher m = COMMENT.matcher(Files.readString(p));
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, m.group().replaceAll("[^\\n]", " "));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> javaFiles() {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("F2-450 — no site keeps a non-ROLE_ authority as if it were a role name")
    void noSiteKeepsANonRolePrefixedAuthorityAsARole() {
        List<String> offenders = new ArrayList<>();
        for (Path p : javaFiles()) {
            String src = stripped(p);
            if (KEEPS_NON_PREFIXED.matcher(src).find()) {
                offenders.add(p.getFileName().toString());
            }
        }
        assertThat(offenders)
            .as("""
                `name.startsWith("ROLE_") ? name.substring(5) : name` keeps every non-prefixed
                authority, and CustomUserDetailsService grants each PERMISSION name verbatim —
                144 of them in dev. Where such a set feeds an authorisation decision (approval-step
                matching), a step whose required_role is spelled like a permission is satisfied by
                holding that permission. Filter on the prefix, as the platform's other three
                ROLE_-stripping sites do.
                ⚠ Comments are stripped, so the javadoc that quotes the retired expression to
                record what it replaced is not read as the expression (F2-193).""")
            .isEmpty();
    }

    @Test
    @DisplayName("F2-450 — every ROLE_-stripping site filters on the prefix first")
    void everyPrefixStrippingSiteFiltersOnThePrefix() {
        List<String> unfiltered = new ArrayList<>();
        int sites = 0;
        for (Path p : javaFiles()) {
            String src = stripped(p);
            Matcher m = STRIPS_PREFIX.matcher(src);
            while (m.find()) {
                sites++;
                // the guard may be a stream .filter(...) above, or an if on the same statement
                String window = src.substring(Math.max(0, m.start() - 400),
                    Math.min(src.length(), m.end() + 60));
                if (!window.contains("startsWith(\"ROLE_\")")) {
                    unfiltered.add(p.getFileName() + " @" + m.start());
                }
            }
        }
        assertThat(sites)
            .as("the detector must still be finding the ROLE_-stripping sites at all")
            .isGreaterThanOrEqualTo(4);
        assertThat(unfiltered)
            .as("""
                A substring past the ROLE_ prefix is only a role name when something has
                established the prefix is there. Three of the platform's four sites already
                filter; the fourth kept everything else and fed an authorisation decision.""")
            .isEmpty();
    }

    @Test
    @DisplayName("F2-450 — the acting set is upper-cased once, so both readers agree")
    void theActingSetIsUpperCasedWhereItIsBuilt() {
        String ctrl = stripped(MAIN.resolve(Path.of("module", "workflow", "approval_chains",
            "controller", "ApprovalChainController.java")));
        assertThat(ctrl)
            .as("""
                matchesActor's parameter is named rolesUpper and only ONE of its two callers used
                to supply one: pendingForUser built it explicitly, recordAction passed the
                controller's raw set. Everything is upper-case in both databases today, so the
                divergence was latent — but its failure is asymmetric and legible: a lower-case
                role name would make pendingForUser list a step in the user's inbox that
                recordAction then refuses with 403. Normalise where the set is built, so the
                parameter name is a claim about both callers.""")
            .contains("toUpperCase(Locale.ROOT)");
    }
}
