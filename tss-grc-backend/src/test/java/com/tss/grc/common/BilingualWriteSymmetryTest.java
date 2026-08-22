package com.tss.grc.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-275 — an Arabic COLUMN that exists is not an Arabic column that can be WRITTEN.
 *
 * <p>{@link BilingualInvariantRatchetTest} (F2-30/F2-271) asks two questions about house rule 7:
 * is the {@code *_ar} sibling <b>declared</b>, and is it <b>annotated</b> symmetrically. Both are
 * questions about the entity. Neither can see the half F2-272 found on {@code purpose_ar}: a column
 * fillable on create and silently unwritable afterwards, because the update path copied a whitelist
 * that named the English field and not the Arabic one. The edit was accepted with HTTP 200 and
 * discarded.
 *
 * <p>🔑 <b>The gap between the two gates is the WIRE AND THE WRITER.</b> So this class asks the
 * complementary question: if a service writes {@code setXEn(...)} on some entity, does anything
 * write {@code setXAr(...)} on that same entity? An Arabic column nothing writes is inert
 * (F2-198's shape), and one written on fewer paths than its English sibling is F2-272's.
 *
 * <p>⚠ <b>Resolved PER OWNER TYPE, never by field name.</b> The reconnaissance pass for this gate
 * matched the setter anywhere in the tree and reported {@code BcmExerciseScenario.successCriteria}
 * as a violation — but that call writes {@code SupplierResilienceTest}, a different entity that
 * happens to share the field name, and {@code BcmExerciseScenario} has no writer for <i>either</i>
 * half. F2-187's recorded mistake ("a field-NAME fallback silently clears the methods most likely
 * to be duplicated elsewhere — resolve per owner type"), and the tell was that the reported entity
 * had no writer at all. A file counts as a writer for an entity only if it also names that entity.
 *
 * <p>Hard zero with an <b>empty exemption map</b>, and that is safe rather than strict: writing the
 * Arabic half beside the English one is always available, so there is no correct code for this to
 * fail on (F2-214).
 */
class BilingualWriteSymmetryTest {

    /** Anything that reads like a caller-supplied payload. */
    private static final String PAYLOAD = "(?:req|body|dto|request|in|patch|payload)";

    private static final Pattern FIELD = Pattern.compile("private\\s+String\\s+(\\w+)\\s*;");
    private static final Pattern STRIP =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static Map<Path, String> sources;

    private static Map<Path, String> sources() {
        if (sources != null) return sources;
        Map<Path, String> out = new LinkedHashMap<>();
        Path root = Path.of("src", "main", "java");
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                out.put(p, stripComments(Files.readString(p, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        sources = out;
        return out;
    }

    /**
     * ONE left-to-right alternation, newlines preserved. Written as two sequential passes it
     * deletes from a line comment containing a block-open delimiter to the next block-close, which
     * cost F2-198 45% of a file and reported a SMALLER population — the quiet direction.
     */
    private static String stripComments(String src) {
        Matcher m = STRIP.matcher(src);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(src, last, m.start());
            for (char c : src.substring(m.start(), m.end()).toCharArray()) {
                out.append(c == '\n' ? '\n' : ' ');
            }
            last = m.end();
        }
        return out.append(src.substring(last)).toString();
    }

    /**
     * One bilingual pair on one entity. {@code bareStem} records which branch found it, because
     * the whole of F2-276 is that the two branches have different populations and only one of them
     * was ever being scanned.
     */
    private record Pair(String entity, String en, String ar, boolean bareStem) {
        /** {@code entity} is a FULLY-QUALIFIED name; print the readable tail. */
        @Override public String toString() { return simple(entity) + "." + en + "/" + ar; }
        String id() { return entity + "#" + en; }
    }

    /**
     * Every English/Arabic String-field pair on every entity, by BOTH spellings of the English half.
     *
     * <p>⚠⚠ <b>F2-276 — the `En` suffix was a third narrowing, and it hid 13 complete pairs.</b>
     * The first version of this method paired {@code xEn} with {@code xAr}. So did
     * {@link BilingualInvariantRatchetTest}, whose two buckets (ASYMMETRIC, EN_ONLY) are both keyed
     * on the English half carrying the suffix. A pair spelled {@code evidenceGuidance} /
     * {@code evidenceGuidanceAr} is therefore in <b>neither</b> bucket and outside <b>both</b>
     * gates — not tolerated, not exempted, simply never asked about. F2-198's shape after a
     * filename, a three-entry allow-list, a module name, an {@code extends} clause, a field-name
     * list inside a fix and a claim shape in a gate; here the narrowing is <b>a naming
     * convention</b>.
     *
     * <p>🔑 <b>It was found by executing F2-275's own filing and disbelieving it.</b> That filing
     * called {@code evidence.sub_controls.evidence_guidance_ar} <i>"an *_ar with no English sibling
     * — the mirror case"</i>. Measured across both databases, columns with no English half of any
     * spelling number <b>0</b>: the English half is {@code evidence_guidance}, populated on 654 of
     * 654 rows against the Arabic half's 610. So the pair is complete, the data is fine, and the
     * defect was in the instrument.
     *
     * <p>The bare-stem branch fires only when {@code xEn} does <b>not</b> exist, so it can only ever
     * ADD pairs — verified in both directions by
     * {@link #theBareStemBranchAddsExactlyTheHiddenPopulationAndNothingElse()} (F2-225: check a
     * widening by what it adds, because one too wide silently re-tiers the platform and one too
     * narrow silently reports a clean tree).
     */
    private static List<Pair> entityPairs() {
        List<Pair> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : new TreeMap<>(byEntityName()).entrySet()) {
            String entity = e.getKey();
            Set<String> fields = e.getValue();
            for (String f : fields) {
                if (f.endsWith("En") && fields.contains(f.substring(0, f.length() - 2) + "Ar")) {
                    out.add(new Pair(entity, f, f.substring(0, f.length() - 2) + "Ar", false));
                }
            }
            for (String f : fields) {
                if (!f.endsWith("Ar")) continue;
                String stem = f.substring(0, f.length() - 2);
                if (stem.isEmpty()) continue;
                if (fields.contains(stem + "En")) continue;   // ordinary pair, found above
                if (fields.contains(stem)) out.add(new Pair(entity, stem, f, true));
            }
        }
        return out;
    }

    /**
     * FULLY-QUALIFIED entity name -> its String field names.
     *
     * <p>⚠⚠ <b>F2-276 — keying this by SIMPLE name silently dropped one twin of every
     * duplicate-named entity.</b> The first version put into a map keyed on the file's simple name,
     * so for the 15 simple names this platform declares in two packages the second file visited
     * <b>overwrote</b> the first — not a union, a replacement — and every bilingual pair on the
     * losing twin left the population. F2-222 filed that hazard as latent on the ground that "no
     * key collapses two registers today"; a census keyed on the same name is exactly where it stops
     * being latent, and it fails in the quiet direction: fewer pairs reads as a cleaner tree.
     *
     * <p>Surfaced by an annotation sweep printing {@code !! 2 entity files named Vendor.java} —
     * {@code scm.Vendor} and {@code tprm.Vendor}, two registers for one concept (F2-247's filing).
     */
    private static Map<String, Set<String>> byEntityName() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<Path, String> e : sources().entrySet()) {
            String body = e.getValue();
            if (!body.contains("@Entity")) continue;
            Set<String> fields = new LinkedHashSet<>();
            Matcher m = FIELD.matcher(body);
            while (m.find()) fields.add(m.group(1));
            if (!fields.isEmpty()) out.put(fqn(e.getKey()), fields);
        }
        return out;
    }

    /** {@code src/main/java/com/tss/grc/module/tprm/entity/Vendor.java} -> its dotted class name. */
    private static String fqn(Path p) {
        String s = p.toString().replace('\\', '/');
        int i = s.indexOf("/com/tss/grc/");
        return s.substring(i + 1, s.length() - ".java".length()).replace('/', '.');
    }

    private static String simple(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Does anything write {@code set<Field>} on THIS entity from a caller payload?
     *
     * <p>Narrowed PER OWNER TYPE, never by field name — F2-187's recorded mistake, which the
     * reconnaissance pass for this gate walked into by reporting
     * {@code BcmExerciseScenario.successCriteria} for a call that writes
     * {@code SupplierResilienceTest}.
     *
     * <p>⚠ F2-276 — the narrowing is now the <b>import</b>, not the simple name. A file counts as a
     * writer for {@code tprm.entity.Vendor} only if it imports that class or lives in its package.
     * With the simple name, a service writing {@code scm.entity.Vendor}'s Arabic half made the
     * {@code tprm} twin's pair read as symmetric — and the direction matters: over-approximating the
     * ARABIC half HIDES an asymmetry, which is the whole defect this gate exists to catch.
     */
    private static boolean writtenFromPayload(String entity, String field) {
        Pattern call = Pattern.compile(
            "\\.set" + cap(field) + "\\(\\s*" + PAYLOAD + "\\b[^;]{0,200}?\\)");
        String pkg = entity.substring(0, entity.lastIndexOf('.'));
        for (Map.Entry<Path, String> e : sources().entrySet()) {
            String body = e.getValue();
            boolean owns = body.contains("import " + entity + ";")
                        || body.contains("import " + pkg + ".*;")
                        || fqn(e.getKey()).startsWith(pkg + ".");
            if (!owns) continue;
            if (call.matcher(body).find()) return true;
        }
        return false;
    }

    @Test
    void anArabicSiblingIsWritableWhereverItsEnglishHalfIs() {
        List<Pair> pairs = entityPairs();
        int total = pairs.size();
        assertThat(total)
            .as("anti-vacuity: the En/Ar pair scan found almost nothing, so this gate would pass "
              + "over any asymmetry. A pattern that stops matching reports a clean tree.")
            .isGreaterThan(500);

        // ⚠⚠ F2-276 — THE FLOOR HAS TO SIT ABOVE THE LARGEST *WRONG* ANSWER, NOT ABOVE ZERO
        // (F2-215). This gate shipped keyed on the entity's SIMPLE name, which silently dropped
        // one twin of every duplicate-named entity: 568 pairs where 585 exist. Adding the
        // bare-stem branch to that broken keying gives ~580, so a floor of 500 — or of 570 —
        // cannot tell the fix from its absence. 590 sits between the wrong answer and the truth,
        // so reverting either half of the F2-276 widening fails here rather than passing quietly.
        // Measured 597 = 585 suffix + 12 bare-stem; see target/bilingual-write-symmetry.txt.
        assertThat(total)
            .as("The pair population fell below the F2-276 measurement. The two ways that happens "
              + "are a REVERTED widening (keying entities by simple name loses 17 pairs; dropping "
              + "the bare-stem branch loses 12) and a genuinely deleted entity. Both deserve a "
              + "reader — do not lower this to make a build pass. Measured %d.", total)
            .isGreaterThanOrEqualTo(590);

        List<String> asymmetric = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (Pair p : pairs) {
            boolean en = writtenFromPayload(p.entity(), p.en());
            boolean ar = writtenFromPayload(p.entity(), p.ar());
            if (en && !ar) asymmetric.add(p.toString());
            lines.add(String.format("%-6s en=%-5s ar=%-5s %s#%s/%s",
                p.bareStem() ? "BARE" : "SUFFIX", en, ar, p.entity(), p.en(), p.ar()));
        }
        publish(pairs, asymmetric, lines);

        assertThat(asymmetric)
            .as("These entities let a caller write the ENGLISH half of a bilingual pair and not the "
              + "ARABIC half, so the Arabic column is inert or writable on fewer paths — F2-272's "
              + "defect, where an edit to a PDPL Article 31 purpose was accepted with HTTP 200 and "
              + "silently discarded because an update whitelist named one half. Write the Arabic "
              + "half beside the English one, under its own null guard so an omitted Arabic value "
              + "leaves the stored one alone rather than clearing it. Measured %d pairs.", total)
            .isEmpty();
    }

    /**
     * F2-276 — the bare-stem branch is verified by WHAT IT ADDS, in both directions.
     *
     * <p>A widening that is too wide silently re-tiers the platform; one that is too narrow
     * silently reports a clean tree, which is the quiet direction and the one that let these pairs
     * sit outside two gates. So the count is asserted <b>exactly</b> rather than as a floor: a
     * decrease-only figure cannot express <i>the population was undercounted</i> (F2-204), and that
     * is precisely the failure being closed here. If a new bare-stem pair appears, this leg fails
     * and a reader has to decide whether the pair should be renamed to the {@code *_en} convention
     * or left and counted.
     */
    @Test
    void theBareStemBranchAddsExactlyTheHiddenPopulationAndNothingElse() {
        List<Pair> pairs = entityPairs();
        List<Pair> bare = pairs.stream().filter(Pair::bareStem).toList();

        assertThat(bare)
            .as("The bare-stem branch found nothing, so the F2-276 widening has stopped matching "
              + "and this gate has silently narrowed back to the population it had before.")
            .isNotEmpty();
        assertThat(bare.size())
            .as("Bare-stem pairs (an Arabic field whose English half carries no `En` suffix, so "
              + "neither this gate nor BilingualInvariantRatchetTest could see it before F2-276). "
              + "Measured %s. If this moved, say which way and why: a NEW pair of this shape is "
              + "worth a decision, not a number bump.", bare)
            .isEqualTo(BARE_STEM_PAIRS);

        // must-pair: the member that produced the finding
        assertThat(bare.stream().map(Pair::toString))
            .as("The exemplar that produced F2-276 must stay in the population — it is the only "
              + "reason anybody knows this shape exists.")
            .contains("EvidenceSubControl.evidenceGuidance/evidenceGuidanceAr");

        // must-not-pair 1: an ordinary suffixed pair may never be counted twice. Keyed on the
        // FULLY-QUALIFIED id, so two same-named entities each owning the same pair is legal.
        List<String> dupes = pairs.stream().map(Pair::id)
            .filter(s -> pairs.stream().map(Pair::id).filter(s::equals).count() > 1)
            .distinct().toList();
        assertThat(dupes)
            .as("A pair counted by both branches would inflate every figure this gate publishes.")
            .isEmpty();

        // ⚠⚠ The IMPORT narrowing is load-bearing and nothing else here can show it. The mutation
        // harness reverted `writtenFromPayload` to matching the entity's SIMPLE name and every leg
        // stayed green, because with `asymmetric = 0` the two resolvers agree on this corpus — an
        // equivalent mutant (F2-206) that is nonetheless the shipped defect. What discriminates is
        // that the two twins of a duplicate-named entity have DIFFERENT writer sets: resolve by
        // simple name and one twin inherits the other's writes, which for the Arabic half HIDES an
        // asymmetry. Measured rather than argued.
        Map<String, List<String>> bySimple = new TreeMap<>();
        byEntityName().keySet().forEach(f -> bySimple
            .computeIfAbsent(simple(f), k -> new ArrayList<>()).add(f));
        int divergent = 0;
        for (List<String> twins : bySimple.values()) {
            if (twins.size() < 2) continue;
            for (String field : byEntityName().getOrDefault(twins.get(0), Set.of())) {
                boolean a = writtenFromPayload(twins.get(0), field);
                boolean b = writtenFromPayload(twins.get(1), field);
                if (a != b) divergent++;
            }
        }
        assertThat(divergent)
            .as("No field on any duplicate-named entity has a different writer answer for its two "
              + "twins, which means resolving writers by SIMPLE name would give the same result as "
              + "resolving them by import — so this gate's per-owner narrowing is no longer doing "
              + "anything and an asymmetry on one twin can be hidden by the other twin's writes.")
            .isGreaterThan(0);

        // must-not-pair 2: where BOTH spellings exist the ordinary branch owns the pair, so no
        // bare-stem entry may name an entity/field that also declares the `En` spelling.
        Map<String, Set<String>> fields = byEntityName();
        List<String> shadowed = bare.stream()
            .filter(p -> fields.getOrDefault(p.entity(), Set.of()).contains(p.en() + "En"))
            .map(Pair::toString).toList();
        assertThat(shadowed)
            .as("These bare-stem pairs sit on entities that ALSO declare the `En` spelling, so the "
              + "ordinary branch already owns them and the bare field is something else.")
            .isEmpty();
    }

    /**
     * Measured 2026-08-22 over both databases and the entity tree; see
     * {@link #theBareStemBranchAddsExactlyTheHiddenPopulationAndNothingElse()} for why this is an
     * equality rather than a ceiling.
     *
     * <p>The database shows <b>13</b> such columns and this is 12; the difference is honest and
     * worth knowing. {@code privacy.privacy_assessments.title / title_ar} belongs to a V1 table
     * with <b>no {@code @Entity}, no repository and no controller</b> — F2-204's
     * {@code NO_SERVER_SURFACE} shape — so there is no entity field for any entity-side gate to
     * pair. Its module's {@code package-info.java} still advertises {@code PrivacyAssessment} as a
     * module type, which is F2-184's shape. Filed rather than mapped: {@code privacy.dpia} is the
     * real assessment register, so adding a second is F2-196's disjoint-register defect.
     */
    private static final int BARE_STEM_PAIRS = 12;

    /**
     * Publish the census rather than restating its numbers in prose (F2-265: a count in prose is a
     * copy of a measurement, and an instruction not to copy it is not an enforcement). The tree's
     * convention — {@code permission-usage.txt}, {@code server-stamped-fields.txt},
     * {@code bilingual-en-only.txt}.
     */
    private static void publish(List<Pair> pairs, List<String> asymmetric, List<String> lines) {
        long bare = pairs.stream().filter(Pair::bareStem).count();
        StringBuilder sb = new StringBuilder()
            .append("# Bilingual WRITE symmetry - GENERATED by BilingualWriteSymmetryTest.\n")
            .append("# A pair is listed with whether a caller payload can write each half. The\n")
            .append("# question is F2-272's: an Arabic column that EXISTS is not one that can be\n")
            .append("# WRITTEN, and frozen is indistinguishable from protected at the caller.\n")
            .append("#\n")
            .append("# SUFFIX = the English half is spelled `xEn`. BARE = it is spelled `x`, which\n")
            .append("# is invisible to BilingualInvariantRatchetTest's pair scan (F2-276).\n")
            .append("pairs_total=").append(pairs.size()).append('\n')
            .append("pairs_suffix=").append(pairs.size() - bare).append('\n')
            .append("pairs_bare_stem=").append(bare).append('\n')
            .append("asymmetric=").append(asymmetric.size()).append("\n\n");
        lines.forEach(l -> sb.append(l).append('\n'));
        try {
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target", "bilingual-write-symmetry.txt"), sb,
                              StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The six columns V389 added, asserted concretely: entity field, response DTO, transition
     * request DTO, and a guarded write in the owning service. The leg above is the general
     * invariant; this one pins the slice, because a general zero over a population is satisfied by
     * a member that was never in it (F2-199's reverse-direction rule).
     */
    @Test
    void theSixStoppedReasonColumnsAreCarriedByTheWireAndTheWriter() {
        // module, entity, dto, service, camelCase base
        String[][] targets = {
            {"audit_eng",  "AuditEngagement",  "AuditEngagementDto",  "AuditEngagementService",
             "cancellationReason"},
            {"board_pack", "BoardPack",        "BoardPackDto",        "BoardPackService",
             "cancellationReason"},
            {"capa",       "CapaTask",         "CapaTaskDto",         "CapaTaskService",
             "cancellationReason"},
            {"nca_ecc",    "NcaEccFiling",     "NcaEccFilingDto",     "NcaEccFilingService",
             "cancellationReason"},
            {"regchange",  "RegulatoryChange", "RegulatoryChangeDto", "RegulatoryChangeService",
             "deprioritisationReason"},
            {"approvals",  "ApprovalRequest",  "ApprovalRequestDto",  "ApprovalRequestService",
             "delegationReason"},
        };

        List<String> problems = new ArrayList<>();
        for (String[] t : targets) {
            String mod = t[0], entity = t[1], dto = t[2], svc = t[3], base = t[4];
            String ar = base + "Ar";

            String entitySrc = sourceEndingWith(mod, entity + ".java");
            if (!entitySrc.contains("private String " + ar + ";")) {
                problems.add(entity + " has no " + ar + " field");
            }
            if (!entitySrc.contains("\"" + snake(base) + "_ar\"")) {
                problems.add(entity + " does not map " + snake(base) + "_ar");
            }

            String dtoSrc = sourceEndingWith(mod, dto + ".java");
            long declared = dtoSrc.lines()
                    .filter(l -> l.contains("private String " + ar + ";")).count();
            if (declared < 2) {
                problems.add(dto + " declares " + ar + " " + declared
                             + " times; it must be on BOTH the response type and TransitionRequest "
                             + "(a response that cannot carry it makes the column invisible, and a "
                             + "request that cannot carry it makes it unwritable)");
            }
            if (!dtoSrc.contains("." + ar + "(e.get" + cap(ar) + "())")) {
                problems.add(dto + " does not map " + ar + " on the way out");
            }

            String svcSrc = sourceEndingWith(mod, svc + ".java");
            Pattern guarded = Pattern.compile(
                "if \\(req\\.get" + cap(ar) + "\\(\\) != null\\)\\s*\\w+\\.set" + cap(ar)
                + "\\(req\\.get" + cap(ar) + "\\(\\)\\);");
            if (!guarded.matcher(svcSrc).find()) {
                problems.add(svc + " has no NULL-GUARDED write of " + ar
                             + " — an unguarded write would clear the stored Arabic reason on any "
                             + "transition that omits it, which is worse than not having it");
            }
        }

        assertThat(problems)
            .as("V389 added six Arabic reason columns; each needs the entity field, the mapping, "
              + "BOTH DTO declarations, the outbound mapper and a null-guarded write. A column no "
              + "write path fills is inert (F2-198), and one the response cannot carry is invisible.")
            .isEmpty();
    }

    private static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String sourceEndingWith(String module, String fileName) {
        for (Map.Entry<Path, String> e : sources().entrySet()) {
            String p = e.getKey().toString().replace('\\', '/');
            if (p.contains("/module/" + module + "/") && p.endsWith("/" + fileName)) {
                return e.getValue();
            }
        }
        throw new AssertionError("source not found: module/" + module + "/**/" + fileName
                                 + " — if it moved, repoint this leg rather than deleting it, "
                                 + "because an unresolved file reports a clean tree (F2-69).");
    }
}
