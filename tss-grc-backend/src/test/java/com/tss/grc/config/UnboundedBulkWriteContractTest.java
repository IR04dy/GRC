package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * F2-460 — a {@code @Modifying} bulk write that binds NO parameter is the whole table by
 * construction, so no caller can scope it and no reading of its callers can make it safe.
 *
 * <p>The defect this closes: {@code AccessRequestRepository.expireAssignments()} was
 * {@code DELETE FROM iam.user_roles WHERE valid_to IS NOT NULL AND valid_to < SYSUTCDATETIME()}
 * with an empty parameter list, twelve lines above the {@code expireAssignmentsForOrg(:org)} that
 * V294 introduced to replace it, under a javadoc reading <em>"Expiry sweep: remove every
 * assignment whose validity window has passed"</em> — which reads like THE expiry sweep. It had
 * zero callers in main and zero mentions in the test tree, so it was latent; what a caller
 * reaching for it would have got is every tenant's expired role assignments deleted on the first
 * tick of a per-tenant loop, attributed to whichever org that iteration was on, with a count that
 * is other tenants' work. {@code iam.user_roles} rows are HARD-deleted — the platform's one
 * documented exception to the soft-delete convention — so there is nothing to recover.
 *
 * <h2>Why the question is "binds anything", not "filters by tenant"</h2>
 *
 * "No tenant predicate" is the weaker question and it would make this gate fail on correct code
 * (F2-214), which is the one failure a gate must never have. Measured over the same corpus, it
 * flags roughly fifteen correct methods: {@code iam.user_roles} has no {@code organisation_id}
 * column at all, so {@code DELETE ... WHERE user_id = :userId} is tenant-correct transitively —
 * the caller resolved the user in-tenant — and {@code iam.roles} is a global catalogue where every
 * row is {@code organisation_id IS NULL}.
 *
 * <p>Worse, that weaker question would have EXCLUDED the defect: the table it deletes from carries
 * no tenant column, so a tenant-predicate rule has nothing to demand of it. The statement crossed
 * tenants through the user, which is exactly the reachability a column-shaped rule cannot see.
 *
 * <h2>Why F2-454's gate could not see it</h2>
 *
 * {@link StartupResolutionContractTest#namedBindings} is the platform's instrument for
 * {@code @Query} parameter binding, and its builder reads
 * {@code if (needed.isEmpty()) continue;} — so a query that binds NOTHING is dropped from its
 * population by construction. Its legs ask whether every placeholder a query names is supplied;
 * a query naming none satisfies that vacuously. F2-455 found one gap in that class's family (the
 * absence of {@code @Query} entirely, where the method NAME is parsed instead); this is a second,
 * and it sits at a {@code continue} rather than at an annotation. F2-454's own closing lesson —
 * that a gate's enumeration of what it asserts is what stops anybody asking what else belongs in
 * it — landing on the gate that recorded it.
 *
 * <p>This is therefore a separate class rather than a leg there: that class's family is
 * <em>fatal at context refresh</em>, and an unbounded write starts perfectly well. It reuses that
 * class's corpus walk and its placeholder extraction rather than copying either (F2-190).
 *
 * <h2>Read from bytecode, never from source</h2>
 *
 * My own throwaway source scan reported FOUR candidates and three were the detector: all three
 * build their {@code @Query} value by string CONCATENATION and the regex captured only the first
 * literal, so the later {@code :param} was invisible. That is F2-454's recorded lesson verbatim
 * ("a concatenated regex defeats a detector anchored on the first literal"), and the same scan
 * also matched {@code @Modifying} inside javadoc in an entity and three schedulers — F2-193's
 * epitaph-as-corpse. {@code @Modifying} and {@code @Query} are both
 * {@code @Retention(RUNTIME)} (verified with {@code javap}), so reflection sees the joined value
 * and no source spelling — concatenated, fully qualified, or quoted in a comment — can hide a
 * member or invent one.
 *
 * <p>It failed a third way that only reflection exposed: over `ChangeRequestRepository` the
 * comment-stripping pass blanked the REAL annotation along with a javadoc mention of it, so
 * the file read as comment-only and a live member vanished from the population — F2-198's
 * stripper trap, in the under-reporting direction, which is the one that looks like a clean
 * tree. Reflection reports 19 members before this fix and 18 after; the source scan reported
 * 18 before, and got three of its four verdicts wrong.
 *
 * <p>DB-free and infrastructure-free: reflection over {@code target/classes}.
 */
class UnboundedBulkWriteContractTest {

    /** A positional binding. Blanked SQL literals first, so a `?` inside one is data. */
    private static final Pattern POSITIONAL = Pattern.compile("\\?\\d+");

    /** A SQL string literal — the same span F2-454 blanks, for the same reason. */
    private static final Pattern SQL_STRING = Pattern.compile("'(?:[^']|'')*'");

    /**
     * One {@code @Modifying} statement and what it binds.
     *
     * @param site        {@code Repository#method}
     * @param query       the joined {@code @Query} value, or blank for a derived modifier
     * @param named       named bindings ({@code :x})
     * @param positional  positional bindings ({@code ?1})
     */
    record BulkWrite(String site, String query, Set<String> named, Set<String> positional) {

        boolean bindsNothing() {
            return named.isEmpty() && positional.isEmpty();
        }
    }

    /**
     * Extracted so {@link #theDetectorFiresOnTheShapeItClaims()} can drive it over synthetic
     * input: on a clean tree the hard zero below asserts an empty list, and an empty list is what
     * a correct detector and a dead one both produce (F2-235).
     */
    static Set<String> positionalBindings(String query) {
        String blanked = SQL_STRING.matcher(query).replaceAll(m -> " ".repeat(m.group().length()));
        Set<String> out = new LinkedHashSet<>();
        Matcher m = POSITIONAL.matcher(blanked);
        while (m.find()) out.add(m.group());
        return out;
    }

    static List<BulkWrite> bulkWrites(List<Class<?>> classes) {
        List<BulkWrite> out = new ArrayList<>();
        for (Class<?> c : classes) {
            for (var m : c.getDeclaredMethods()) {
                if (m.getAnnotation(Modifying.class) == null) continue;
                Query q = m.getAnnotation(Query.class);
                String site = c.getSimpleName() + "#" + m.getName();
                if (q == null) {
                    // A derived modifier (`deleteByOrganisationId`) narrows through its own name,
                    // and its parameters are the narrowing. Recorded with a blank query so the
                    // census cannot silently lose it, and never counted as unbound.
                    //
                    // ⚠ Measured: this platform has ZERO derived modifiers, so this branch is
                    // unreachable from the live corpus and the census asserts that. Stated
                    // rather than left to read as covered — a branch nothing reaches is a
                    // branch nothing checks, and the day a `deleteByX` appears the census
                    // fails until somebody decides whether a method name is narrowing enough.
                    out.add(new BulkWrite(site, "", Set.of("<derived>"), Set.of()));
                    continue;
                }
                String query = q.value();
                out.add(new BulkWrite(site, query,
                    StartupResolutionContractTest.namedPlaceholders(query, q.countQuery()),
                    positionalBindings(query)));
            }
        }
        return out;
    }

    @Test
    void noBulkWriteBindsNothing() {
        List<String> unbounded = new ArrayList<>();
        for (BulkWrite w : bulkWrites(StartupResolutionContractTest.shippedClasses())) {
            if (w.bindsNothing()) unbounded.add(w.site() + "   " + oneLine(w.query()));
        }
        assertThat(unbounded)
            .as("""
                A `@Modifying` statement that binds no parameter is the WHOLE TABLE by
                construction: no caller can scope it, and no reading of its callers can make it
                narrower. On a multi-tenant platform that is a cross-tenant write whether or not
                the table itself carries `organisation_id` — F2-460's own member deleted from
                `iam.user_roles`, which has no tenant column and reaches every tenant through the
                user.

                Hard zero with an EMPTY exemption map, which is safe rather than strict: binding
                the thing you mean to narrow by is always available, so an unbounded bulk write is
                never the only way to express an intent (F2-214's test). If a genuinely global
                maintenance write is ever needed, add it here with a reason naming what it may
                touch — do not delete the assertion.""")
            .isEmpty();
    }

    @Test
    void theBulkWritePopulationIsMeasured() {
        List<BulkWrite> all = bulkWrites(StartupResolutionContractTest.shippedClasses());
        long withQuery = all.stream().filter(w -> !w.query().isBlank()).count();

        // An anti-vacuity floor, because a hard zero over a population the instrument cannot see
        // is the most reassuring possible artefact (F2-205). The number sits above the largest
        // WRONG answer rather than above zero (F2-215): a corpus walk that resolves nothing gives
        // 0, and a reflection call that stops seeing @Modifying gives 0 too.
        assertThat(all)
            .as("`@Modifying` members found by reflection over target/classes. If this collapses, "
                + "the hard zero above is passing over nothing rather than over a clean tree.")
            .hasSizeGreaterThan(12);

        assertThat(withQuery)
            .as("""
                `@Modifying` members carrying an explicit `@Query`: 19 before F2-460 removed the
                unbounded one, 18 after. Every one is explicit — see the derived-modifier note in
                `bulkWrites`.""")
            .isEqualTo(all.size());

        // The UPPER bound is what catches an over-counting detector, which no floor can see: a
        // mutation dropping the `@Modifying` filter turns this population into every `@Query`
        // in the tree (F2-454 measured 253), and almost all of those bind something, so the
        // hard zero above would stay green over a gate that had stopped asking its question.
        assertThat(all)
            .as("`@Modifying` members. An answer in the hundreds means the annotation filter "
                + "is gone and this gate is measuring every query in the platform.")
            .hasSizeLessThan(40);

        assertThat(all.stream().filter(w -> w.query().isBlank()).toList())
            .as("This platform declares no derived modifier, so `bulkWrites`'s `q == null` "
                + "branch is unreachable from the live corpus. Measured, not assumed.")
            .isEmpty();

        // The tree binds by NAME only. Asserted rather than assumed, so the day a positional
        // binding appears somebody reads it -- `?1` is legal and this gate counts it, but nothing
        // else in the platform uses one and a first instance is worth noticing.
        List<String> withPositional = all.stream()
            .filter(w -> !w.positional().isEmpty())
            .map(BulkWrite::site).toList();
        assertThat(withPositional)
            .as("No `@Modifying` query in this platform binds positionally. Not a rule — a "
                + "measurement, so a first `?1` surfaces instead of blending in.")
            .isEmpty();
    }

    @Test
    void theDetectorFiresOnTheShapeItClaims() {
        // MUST flag: the literal statement F2-460 removed.
        String retired = "DELETE FROM iam.user_roles WHERE valid_to IS NOT NULL "
            + "AND valid_to < SYSUTCDATETIME()";
        assertThat(StartupResolutionContractTest.namedPlaceholders(retired, "")).isEmpty();
        assertThat(positionalBindings(retired)).isEmpty();

        // MUST NOT flag: the scoped twin that replaced it.
        String scoped = """
            DELETE ur FROM iam.user_roles ur
            JOIN iam.users u ON u.id = ur.user_id
            WHERE u.organisation_id = :org AND ur.valid_to IS NOT NULL
              AND ur.valid_to < SYSUTCDATETIME()
            """;
        assertThat(StartupResolutionContractTest.namedPlaceholders(scoped, ""))
            .as("the scoped twin binds :org")
            .containsExactly("org");

        // MUST NOT flag: a colon inside a SQL string literal is data, not a binding — and the
        // mirror, that a real binding beside such a literal is still seen. F2-454 recorded the
        // first half; the second is what stops the blanking swallowing the binding with it.
        assertThat(StartupResolutionContractTest.namedPlaceholders(
                "UPDATE t SET at = '12:30' WHERE id = 1", ""))
            .as("a time literal is not a binding")
            .isEmpty();
        assertThat(StartupResolutionContractTest.namedPlaceholders(
                "UPDATE t SET at = '12:30' WHERE id = :id", ""))
            .as("blanking the literal must not blank the binding beside it")
            .containsExactly("id");

        // MUST flag / MUST NOT flag, positional half, including the literal trap.
        assertThat(positionalBindings("DELETE FROM t WHERE id = ?1")).containsExactly("?1");
        assertThat(positionalBindings("UPDATE t SET note = 'what?1 is this' WHERE id = 5"))
            .as("a `?1` inside a SQL literal is data")
            .isEmpty();
    }

    @Test
    void theSurvivingExpirySweepStillNarrowsByTenant() {
        List<BulkWrite> all = bulkWrites(StartupResolutionContractTest.shippedClasses());

        BulkWrite scoped = all.stream()
            .filter(w -> w.site().equals("AccessRequestRepository#expireAssignmentsForOrg"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "AccessRequestRepository#expireAssignmentsForOrg is gone. It is the ONLY expiry "
                + "sweep for time-bound role assignments; F2-460 removed the unscoped sibling "
                + "precisely because this one exists."));

        assertThat(scoped.named())
            .as("""
                The surviving sweep must narrow by tenant, which is a stronger claim than the
                hard zero above can make: that leg only asks whether SOMETHING is bound, and this
                statement deletes IAM role assignments, so which thing is bound is the whole
                question. `scheduledExpirySweep` loops every organisation and gives each its own
                transaction, audit row and IAM_LEAD notification (F2-322) — all three are wrong
                for every tenant but one if this stops being per-org.""")
            .contains("org");

        assertThat(all.stream().filter(w -> w.site().startsWith("AccessRequestRepository#expire")))
            .as("there must be exactly one expiry sweep, and it is the scoped one")
            .hasSize(1);
    }

    private static String oneLine(String s) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() > 180 ? flat.substring(0, 180) + "…" : flat;
    }
}
