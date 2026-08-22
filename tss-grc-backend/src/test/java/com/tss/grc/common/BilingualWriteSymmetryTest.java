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

    /** simple entity name -> the En/Ar bases it declares as String fields. */
    private static Map<String, Set<String>> entityPairs() {
        Map<String, Set<String>> out = new TreeMap<>();
        for (Map.Entry<Path, String> e : sources().entrySet()) {
            String body = e.getValue();
            if (!body.contains("@Entity")) continue;
            Set<String> fields = new LinkedHashSet<>();
            Matcher m = FIELD.matcher(body);
            while (m.find()) fields.add(m.group(1));
            String name = e.getKey().getFileName().toString().replace(".java", "");
            Set<String> bases = new LinkedHashSet<>();
            for (String f : fields) {
                if (f.endsWith("En") && fields.contains(f.substring(0, f.length() - 2) + "Ar")) {
                    bases.add(f.substring(0, f.length() - 2));
                }
            }
            if (!bases.isEmpty()) out.put(name, bases);
        }
        return out;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Does anything write {@code set<Field>} on THIS entity from a caller payload? A source file
     * counts only if it also names the entity — see the class javadoc on F2-187.
     */
    private static boolean writtenFromPayload(String entity, String field) {
        Pattern call = Pattern.compile(
            "\\.set" + cap(field) + "\\(\\s*" + PAYLOAD + "\\b[^;]{0,200}?\\)");
        for (Map.Entry<Path, String> e : sources().entrySet()) {
            String body = e.getValue();
            if (!body.contains(entity)) continue;
            if (call.matcher(body).find()) return true;
        }
        return false;
    }

    @Test
    void anArabicSiblingIsWritableWhereverItsEnglishHalfIs() {
        Map<String, Set<String>> pairs = entityPairs();
        int total = pairs.values().stream().mapToInt(Set::size).sum();
        assertThat(total)
            .as("anti-vacuity: the En/Ar pair scan found almost nothing, so this gate would pass "
              + "over any asymmetry. A pattern that stops matching reports a clean tree.")
            .isGreaterThan(500);

        List<String> asymmetric = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : pairs.entrySet()) {
            for (String base : e.getValue()) {
                boolean en = writtenFromPayload(e.getKey(), base + "En");
                boolean ar = writtenFromPayload(e.getKey(), base + "Ar");
                if (en && !ar) asymmetric.add(e.getKey() + "." + base);
            }
        }

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
