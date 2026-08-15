package com.tss.grc.common.tenant;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-216 — the property that makes {@link TenantWriteGuardInterceptor}'s NULL-tenant
 * exemption safe, which nobody had written down.
 *
 * <p>{@code decideInsert} passes a row whose {@code organisationId} is null. Its stated
 * reason was that this is <em>"a NOT NULL violation about to happen, and the database says
 * so far more clearly than a SecurityException would."</em> Measured from {@code sys.columns}
 * in both databases: <b>11 of 427 org-bearing tables declare the column NULLABLE</b> (421 in
 * prod, same 11), so for those the database says nothing — and on most of them NULL is not an
 * absence but a <b>value meaning "every tenant"</b>, read back by repositories asking
 * {@code organisationId IS NULL OR organisationId = :orgId}.
 *
 * <p>🔑 <b>So the guard declines to judge exactly the value that publishes a row to every
 * tenant on the platform.</b> That it has never mattered is a fact about today's endpoints,
 * not a property of the control (F2-182's rule) — and the property it depends on is what this
 * file turns into a build failure.
 *
 * <h2>Why this asserts a population rather than changing the guard</h2>
 *
 * <p>Making {@code decideInsert} throw on a null tenant is F2-183's defect — a constraint added
 * while its writers are open. 169 rows in dev and 158 in prod are legitimately platform-global,
 * tenant onboarding creates them off-request, and "null now, stamped a moment later" is the
 * ordinary shape of a service that builds an entity before stamping it. The exemption is right.
 * What was missing is the reason and the guarantee.
 *
 * <h2>The population is DERIVED, never listed</h2>
 *
 * <p>F2-204: a registry is only as honest as the rule that decided its membership, and that
 * rule is usually implicit. Membership here is read from the repositories themselves — an
 * entity is platform-global if some repository queries it with {@code organisationId IS NULL} —
 * so a ninth such entity is covered on the day its repository is written, with nothing to
 * remember. Comments are stripped before matching, because the prose that <em>explains</em>
 * this convention contains the very phrase that detects it (F2-205's "prose about code, stored
 * as data, invents callers"); only a real query counts.
 */
@DisplayName("F2-216 · the tenant guard's NULL exemption rests on a property, and it holds")
class TenantGuardNullExemptionContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path GUARD =
        MAIN_JAVA.resolve("com/tss/grc/common/tenant/TenantWriteGuardInterceptor.java");
    private static final Path CONTEXT =
        MAIN_JAVA.resolve("com/tss/grc/common/tenant/TenantContext.java");
    private static final Path CONFIG =
        MAIN_JAVA.resolve("com/tss/grc/config/TenantWriteGuardConfig.java");
    private static final Path DEV_YML = Path.of("src", "main", "resources", "application-dev.yml");
    private static final Path PROD_YML = Path.of("src", "main", "resources", "application-prod.yml");

    /**
     * The entities measured platform-global on 2026-08-15, asserted EXACTLY rather than as a
     * floor. F2-204: when a population is asserted with {@code >=}, an omission is
     * indistinguishable from a shrinking count — so a repository that stops being globally
     * visible must edit this list deliberately, and a new one must be considered rather than
     * silently absorbed.
     */
    private static final Set<String> PLATFORM_GLOBAL_ENTITIES = new TreeSet<>(Set.of(
        "FraudTypology",
        "HelpTopic",
        "OpResPillar",
        "Role",
        "SanctionsList",
        "SanctionsListEntry",
        "SodRule",
        "ThreeLinesRoleMapping"));

    // ─────────────────────────────────────────────────────────────────────────
    // 1. The load-bearing invariant
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no write endpoint binds an entity whose rows can be platform-global")
    void noEntityBoundWriteBindsAPlatformGlobalEntity() {
        Set<String> global = derivePlatformGlobalEntities().keySet();
        List<String> reachable = new ArrayList<>();

        for (Class<?> controller : restControllers()) {
            for (Method m : controller.getDeclaredMethods()) {
                for (Parameter p : m.getParameters()) {
                    if (!p.isAnnotationPresent(RequestBody.class)) {
                        continue;
                    }
                    if (!p.getType().isAnnotationPresent(Entity.class)) {
                        continue;
                    }
                    if (global.contains(p.getType().getSimpleName())) {
                        reachable.add(controller.getSimpleName() + "#" + m.getName()
                                      + "(" + p.getType().getSimpleName() + ")");
                    }
                }
            }
        }

        assertThat(reachable)
            .as("""
                F2-216 — a write endpoint binds an entity whose table treats a NULL \
                organisation_id as PLATFORM-GLOBAL, and TenantWriteGuardInterceptor's insert \
                leg deliberately does not judge a null tenant. A caller who omits or nulls \
                organisationId on this endpoint does not trigger a NOT NULL violation: they \
                publish the row to every tenant, silently. Either take a request DTO that \
                cannot carry organisationId, or stamp the tenant unconditionally before the \
                save — do not widen the interceptor, which would break the 169 legitimately \
                global rows.""")
            .isEmpty();
    }

    @Test
    @DisplayName("...and that invariant is not vacuous — the global population is real and exact")
    void thePlatformGlobalPopulationIsDerivedFromRepositoriesAndNonEmpty() {
        Map<String, List<String>> derived = derivePlatformGlobalEntities();
        derived.forEach((entity, repos) ->
            System.out.println("[F2-216] platform-global: " + entity + "  <- " + repos));

        assertThat(derived.keySet())
            .as("""
                The globally-visible set is DERIVED from repositories that query \
                `organisationId IS NULL`, so it cannot silently empty out and leave the \
                invariant above passing over nothing. If this fails because a repository was \
                added, decide whether the new entity is reachable from a write endpoint before \
                adding it here — that decision is the whole point of the list being exact \
                rather than a floor (F2-204).""")
            .isEqualTo(PLATFORM_GLOBAL_ENTITIES);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. The claims — a stale one about a security control is an input to a threat model
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the exemption does not claim the database will refuse a null tenant")
    void theNullExemptionDoesNotClaimTheColumnIsAlwaysNotNull() throws IOException {
        // Read the PROSE, not the file. The claim under review lives in a comment, and a
        // whole-file scan cannot be unquoted correctly: `"..."` pairs left to right, so the
        // quotes around a javadoc citation pair with the quotes of a Java string literal
        // further up and leave the citation exposed. That is what this leg failed on first
        // run — the detector was satisfied by something other than its claim, again.
        String prose = unquoted(commentsOnly(read(GUARD)));

        assertThat(prose)
            .as("""
                F2-216 — the exemption's reason may not assert that a null organisationId is \
                "a NOT NULL violation about to happen". 11 of 427 org-bearing tables allow \
                NULL, so for those the database refuses nothing.

                Quoted spans are stripped first, so a correction may keep quoting the sentence \
                it replaces (F2-193: a detector that cannot tell a quotation from a claim \
                forces every correction to paraphrase away its own evidence). That convention \
                extends to DENIAL, which is the shape this leg fired on first: "would not be \
                committing the NOT NULL violation the comment predicted" is a refutation, and \
                a phrase ban cannot tell it from an assertion. Cite the retired wording in \
                quotes even when you are denying it — the alternative is a detector that has \
                to parse negation, and this repository has eight recorded instances of a \
                detector satisfied by something other than its claim.""")
            .doesNotContain("NOT NULL violation");

        // ⚠ Banning the false reason is only half the contract: a file with the sentence simply
        // deleted also passes, and leaves the next reader with an unexplained `return false`.
        // So require the true reason in BOTH places a reader looks, by strings that appear
        // nowhere else in the file (F2-194: assert a retained explanation by a string that
        // cannot be satisfied by an import, a type name or an unrelated sentence). The first
        // draft of this leg asserted only the token PLATFORM-GLOBAL against the whole file,
        // and a mutation that gutted the javadoc SURVIVED on the inline comment's copy.
        String guard = read(GUARD);
        assertThat(guard)
            .as("the branch itself must say what a null tenant MEANS — this is the comment "
                + "somebody reads when they are standing on the `return false`")
            .contains("NULL means PLATFORM-GLOBAL");
        // ⚠ Asserted against FLOWED javadoc, not the raw file. A claim in prose wraps across
        // lines, so a raw `contains` can only anchor on whichever line-fragment you happened to
        // pick — and the mutation that gutted the CONSEQUENCE ("a row to every tenant on the
        // platform" -> "a row to some tenant somewhere") survived twice, because both drafts
        // anchored on the fragment before it. A multi-line expected string is not the fix
        // either: this tree is CRLF, so an embedded \n can never match and the leg would pass
        // vacuously (F2-212). Flowing the comment is what lets the assertion be the sentence.
        assertThat(flowed(guard))
            .as("the javadoc must state the CONSEQUENCE, not merely the vocabulary: the value "
                + "the guard declines to judge is the one that publishes a row platform-wide")
            .contains("declines to judge is precisely the value that publishes "
                      + "a row to every tenant on the platform");
    }

    @Test
    @DisplayName("no comment describes the guard as unwired while the profiles enable it")
    void theWiringStatusClaimsMatchTheProfilesThatSetTheFlag() throws IOException {
        String dev = read(DEV_YML);
        String prod = read(PROD_YML);

        // Direction 1: the profiles really do turn it on. If someone turns dev off, the
        // comments claiming it runs become false and this fails — which is the point.
        assertThat(dev.replaceAll("\\s+", ""))
            .as("application-dev.yml must enable the guard — a security control that runs only "
                + "in production is tested only by customers")
            .contains("tenant-write-guard:enabled:true");
        assertThat(prod.replaceAll("\\s+", ""))
            .as("application-prod.yml must enable the guard, and as a ${PLACEHOLDER} (F2-149)")
            .contains("tenant-write-guard:enabled:${TENANT_WRITE_GUARD_ENABLED:true}");

        // Direction 2: no source comment may describe it as unbuilt or unwired.
        for (Path p : List.of(CONTEXT, CONFIG)) {
            String text = unquoted(commentsOnly(read(p)));
            assertThat(text)
                .as(p.getFileName() + " — F2-216: this file described the platform-wide "
                    + "cross-tenant backstop as not wired while both profiles enabled it. A "
                    + "reader who opens the class that defines the mechanism and is told the "
                    + "control does not run will build a redundant guard or trust a weaker one.")
                .doesNotContain("not yet wired in")
                .doesNotContain("intentionally not connected")
                .doesNotContain("Default-off because");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** entity simple name -> the repositories that read it with a null-tenant clause. */
    private static Map<String, List<String>> derivePlatformGlobalEntities() {
        Map<String, List<String>> out = new TreeMap<>();
        Pattern repoDecl = Pattern.compile(
            "interface\\s+(\\w+)\\s+extends\\s+[\\w.]*Repository\\s*<\\s*(\\w+)\\s*,");

        for (Path p : javaSources()) {
            String raw = read(p);
            String code = stripComments(raw);
            Matcher m = repoDecl.matcher(code);
            if (!m.find()) {
                continue;
            }
            String repo = m.group(1);
            String entity = m.group(2);
            // Only a real query counts. The convention is also EXPLAINED in prose in several of
            // these files, and a comment-blind scan would credit the explanation as a query.
            if (code.contains("organisationId IS NULL") || code.contains("organisation_id IS NULL")) {
                out.computeIfAbsent(entity, k -> new ArrayList<>()).add(repo);
            }
        }
        return out;
    }

    private static List<Class<?>> restControllers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                out.add(Class.forName(bd.getBeanClassName()));
            } catch (Throwable ignored) {
                // a controller that will not load cannot bind anything either
            }
        }
        return out;
    }

    private static List<Path> javaSources() {
        try (Stream<Path> s = Files.walk(MAIN_JAVA)) {
            return s.filter(p -> p.toString().endsWith("Repository.java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + MAIN_JAVA.toAbsolutePath(), e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p.toAbsolutePath(), e);
        }
    }

    /**
     * Strips line and block comments in ONE left-to-right alternation. Two sequential passes
     * corrupt the source, because a {@code //} prose comment containing a literal {@code /*}
     * makes the block pattern run to the next closing delimiter and delete real code — which
     * reports a SMALLER population, reading exactly like "those are fine" (CLAUDE.md, F2-198).
     */
    static String stripComments(String s) {
        return s.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", "");
    }

    /**
     * The inverse of {@link #stripComments} — everything a human reads and no code.
     *
     * <p>⚠ Needed because {@link #unquoted} cannot be applied to a whole Java file: quotes pair
     * left to right, so a javadoc citation's quotes pair with a string literal's quotes
     * elsewhere in the file and the citation survives the stripping. Isolating the prose first
     * makes the pairing correct by construction.
     */
    static String commentsOnly(String s) {
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/").matcher(s);
        while (m.find()) {
            out.append(m.group()).append('\n');
        }
        return out.toString();
    }

    /**
     * Strips javadoc line-leading {@code *} and collapses whitespace, so a prose claim can be
     * asserted as the sentence a human reads rather than as whichever line-fragment the
     * formatter happened to produce. Line breaks in a comment are a formatting accident; an
     * assertion anchored on one is testing the formatter.
     */
    static String flowed(String s) {
        return s.replaceAll("(?m)^\\s*\\*\\s?", " ").replaceAll("\\s+", " ");
    }

    /**
     * Removes double-quoted spans so a correction may quote the sentence it replaces without
     * the quotation reading as a fresh assertion (F2-193). Apply to {@link #commentsOnly}
     * output, never to a raw source file — see that method.
     */
    static String unquoted(String s) {
        return s.replaceAll("\"[\\s\\S]*?\"", "\"\"");
    }
}
