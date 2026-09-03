package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * F2-451 — a capped fetch is fine to RENDER from and wrong to COUNT, and on a document that leaves
 * the platform the count is the half a regulator's tooling reads.
 *
 * <p>Every emitter in this platform caps its fetch so the document cannot grow without bound, which
 * is deliberate and correct. What was not correct is publishing {@code list.size()} of the capped
 * page as though it were the register's size. Measured 2026-09-03,
 * {@code RegulatorPackageService}'s manifest published {@code "auditEntries": 200} against
 * {@code platform.audit_log} holding <b>12,470</b> rows for organisation 1 in dev and <b>5,133</b>
 * in prod — a <b>62x</b> under-report — and its README rendered {@code Audit / 200 / Trail
 * entries}.
 *
 * <p>⚠ That 200 is itself the second half of the same shape, and it cost this finding its first
 * set of numbers: {@code MAX_AUDIT} read <b>1000</b>, and {@code AuditLogService.list} clamps every
 * caller with {@code Math.min(size, 200)}. So the constant overstated the package's audit coverage
 * 5x to any reader of that file (F2-184 at a constant — it answers the question a reader was about
 * to ask), and reading the constant rather than the wire is how the first draft of this finding
 * claimed a 12x under-report. <b>The live probe refuted it, not a reading.</b>
 *
 * <h2>Why the population is keyed on a KPI TILE</h2>
 *
 * Keying on the emitter API was tried and abandoned: {@code SocOpsExportService} emits through
 * {@code RegisterExport.pdf(...)} rather than touching {@code PdfWriter} or {@code XSSFWorkbook},
 * so a detector keyed on the emitters already seen reported a SMALLER population — which reads
 * exactly like a cleaner tree (F2-422). A KPI tile is the artefact that makes the claim, and
 * {@code PdfTheme.Kpi} / {@code kpiStrip} are two stable symbols rather than a growing list of
 * emitter helpers.
 *
 * <p>Both complements are asserted, because they are what makes the population trustworthy: <b>11
 * emitters build a KPI tile and never cap</b> (their figures genuinely are totals — including three
 * of the four ZIP package builders, which fetch uncapped), and 59 services cap without emitting a
 * tile. A detector whose cap pattern or tile pattern stopped matching would report an empty
 * population, and an empty population is indistinguishable from a swept one (F2-235).
 *
 * <h2>Why the four unread emitters carry no verdict</h2>
 *
 * They are a WORK LIST, not a set of judgements. F2-188 measured what happens when a slice writes
 * a reason for code it has not read: five claims, five false, and the fluency was what made them
 * look verified. Three of the four have <b>no test of any kind</b> (F2-248), so the honest artefact
 * is a count that cannot grow silently, printed with its figures to
 * {@code target/capped-export-figures.txt}.
 */
class CappedExportDisclosureContractTest {

    private static final Path MAIN = Paths.get("src/main/java/com/tss/grc");

    /** A capped fetch, in every spelling this platform uses (F2-422's five). */
    private static final Pattern CAP = Pattern.compile(
        "PageRequest\\.of\\(\\s*0\\s*,|findTop\\d+|\\.limit\\(\\s*\\d+\\s*\\)|Pageable\\.ofSize\\(");

    /** The artefact that publishes a figure to a human: a KPI tile. */
    private static final Pattern KPI = Pattern.compile("PdfTheme\\.Kpi\\(|kpiStrip\\(");

    /** The emitter this slice read and closed. */
    private static final String FIXED = "module/reporting/service/RegulatorPackageService.java";

    /**
     * The population, with a state each. {@code false} = not yet read: the emitter caps a fetch and
     * publishes a figure from it, and nobody has decided whether each figure is a claim about the
     * register or about the document. Enforced in BOTH directions, so fixing one forces its entry
     * to change rather than letting it pass under a tolerated total (F2-199).
     */
    private static final Map<String, Boolean> DISCLOSES = new LinkedHashMap<>();
    static {
        DISCLOSES.put(FIXED, true);
        // Six `Kpi("Total", rows.size())` tiles over one `private static final Pageable CAP`.
        DISCLOSES.put("module/cyber/service/CyberExportService.java", false);
        // Three `Total` tiles over three 1000-row caps, and no cap note anywhere in the file.
        DISCLOSES.put("module/reporting/service/DocxExportService.java", false);
        // Three `Total` tiles whose subtitle reads "All states", with a static cap note ("All risks
        // (max 1000)") in a ReportMetadata scope row elsewhere on the same page -- which names the
        // CAP rather than the TRUNCATION, so it cannot say whether anything was withheld.
        DISCLOSES.put("module/reporting/service/ExportService.java", false);
        // One `Kpi("Total events", rows.size(), "")` over a 5000-row cap.
        DISCLOSES.put("module/socops/service/SocOpsExportService.java", false);
    }

    // ── the invariant on the emitter this slice closed ─────────────────────

    @Test void noKpiSubtitleOnTheRegulatorPackageIsABareLiteral() {
        String src = LocaleSensitiveFormatContractTest.strip(read(MAIN.resolve(FIXED)));
        List<String> bare = new ArrayList<>();
        for (String args : kpiArgumentLists(src)) {
            List<String> parts = splitTopLevel(args);
            if (parts.size() < 3) {
                bare.add("(only " + parts.size() + " args) " + args.trim());
                continue;
            }
            String subtitle = parts.get(2).trim();
            if (!subtitle.contains("ExportTruncation.")) {
                bare.add(subtitle);
            }
        }
        assertThat(bare)
            .as("F2-451: every KPI subtitle on the regulator package must come from the truncation "
                + "primitive. A hard zero with no exemption map is safe here because the primitive "
                + "returns the original subtitle when nothing was withheld, so there is no correct "
                + "code for this leg to fail on (F2-214). Bare subtitles found: " + bare)
            .isEmpty();
        assertThat(kpiArgumentLists(src))
            .as("anti-vacuity: the three README tiles must still be found, or the leg above is "
                + "asserting emptiness over a detector that stopped matching (F2-235)")
            .hasSize(3);
    }

    @Test void theManifestPublishesTheRegisterTotalAndTheIncludedCountSeparately() {
        String src = LocaleSensitiveFormatContractTest.strip(read(MAIN.resolve(FIXED)));

        assertThat(src)
            .as("the total comes from the SAME Page the capped content came from, so the two cannot "
                + "disagree about scope")
            .contains("controlPage.getTotalElements()")
            .contains("evidencePage.getTotalElements()")
            .contains("auditPage.getTotalElements()");

        for (String total : List.of("controlTotal", "evidenceTotal", "auditTotal")) {
            assertThat(src)
                .as("\"counts\" must be fed by the register total, not the capped page size: " + total)
                .contains("append(" + total + ")");
        }
        for (String included : List.of("controls", "evidence", "auditEntries")) {
            assertThat(src)
                .as("\"includedRows\" must still publish what the sheets hold: " + included)
                .contains("append(" + included + ")");
        }
        assertThat(src)
            .as("and the subtraction is done for a consumer's tooling")
            .contains("\\\"truncated\\\"");
    }

    // ── the census ────────────────────────────────────────────────────────

    @Test void everyEmitterThatCapsAFetchAndPublishesAFigureIsDeclared() {
        Map<String, Boolean> measured = measure();
        publish(measured);

        assertThat(measured.keySet())
            .as("F2-451: a new emitter that caps a fetch and publishes a figure from it must be "
                + "READ and declared -- not absorbed into a tolerated total. Declared: "
                + DISCLOSES.keySet() + "; measured: " + measured.keySet())
            .containsExactlyInAnyOrderElementsOf(DISCLOSES.keySet());

        assertThat(measured)
            .as("and the state must be the measured one, in both directions: an emitter recorded as "
                + "not-yet-read that has since started calling the primitive must lose its entry, "
                + "and one recorded as disclosing must still call it (F2-199)")
            .containsExactlyInAnyOrderEntriesOf(DISCLOSES);
    }

    @Test void bothComplementsAreNonEmptySoAnEmptyPopulationCannotReadAsSwept() {
        int capOnly = 0;
        int kpiOnly = 0;
        for (Path p : javaFiles()) {
            String s = LocaleSensitiveFormatContractTest.strip(read(p));
            boolean c = CAP.matcher(s).find();
            boolean k = KPI.matcher(s).find();
            if (c && !k) {
                capOnly++;
            } else if (k && !c) {
                kpiOnly++;
            }
        }
        // Measured 2026-09-03: 59 cap-only, 11 kpi-only. The floors sit above the largest WRONG
        // answer, which for both is ZERO -- if either pattern stops matching the population empties
        // and every assertion above passes over nothing (F2-215).
        assertThat(capOnly)
            .as("services that cap a fetch without publishing a tile -- if this collapses, the cap "
                + "pattern has stopped matching and the population is empty for that reason")
            .isGreaterThan(40);
        assertThat(kpiOnly)
            .as("emitters that publish a tile and never cap, so their figures genuinely are totals "
                + "-- three of the four ZIP package builders are in here, fetching uncapped")
            .isGreaterThan(8);
    }

    // ── the detector, driven over synthetic input in both directions ───────

    @Test void theDetectorFiresOnACappedEmitterAndOnNothingElse() {
        String both = "class A { void x() { var r = repo.find(o, PageRequest.of(0, 5000)).getContent();"
            + " k = new PdfTheme.Kpi(\"Total\", String.valueOf(r.size()), \"\"); } }";
        String capOnly = "class A { void x() { var r = repo.find(o, PageRequest.of(0, 5000)); } }";
        String kpiOnly = "class A { void x() { k = new PdfTheme.Kpi(\"Total\", \"1\", \"\"); } }";
        String commented = "class A { void x() { // new PdfTheme.Kpi(\"Total\", \"1\", \"\")\n"
            + " var r = repo.find(o, PageRequest.of(0, 5000)); } }";
        String docxStrip = "class A { void x() { var r = repo.find(o, PageRequest.of(0, 1000))"
            + ".getContent(); kpiStrip(doc, ar, new String[][]{{\"Total\", \"\" + r.size()}}); } }";

        assertThat(isMember(both)).as("cap + tile is the population").isTrue();
        assertThat(isMember(docxStrip))
            .as("the private String[][] kpiStrip shape counts too -- DocxExportService uses it, and "
                + "keying only on PdfTheme.Kpi would have dropped three figures")
            .isTrue();
        assertThat(isMember(capOnly)).as("a cap with no published figure is not a member").isFalse();
        assertThat(isMember(kpiOnly)).as("a tile over an uncapped fetch is not a member").isFalse();
        assertThat(isMember(commented))
            .as("a tile that exists only in a comment is not a tile -- this platform documents its "
                + "retirements by quoting what they replaced (F2-193)")
            .isFalse();
    }

    @Test void theSubtitleSplitterReadsTheThirdArgumentAcrossNestedCallsAndCommas() {
        String src = "new PdfTheme.Kpi(\"Audit\", String.valueOf(n),"
            + " ExportTruncation.hint(a, b, false, \"Trail entries\"))";
        List<String> args = splitTopLevel(kpiArgumentLists(src).get(0));
        assertThat(args).hasSize(3);
        assertThat(args.get(2).trim())
            .as("a comma inside the nested call must not split the argument list")
            .startsWith("ExportTruncation.hint(");

        String bare = "new PdfTheme.Kpi(\"Audit\", String.valueOf(n), \"Trail entries\")";
        assertThat(splitTopLevel(kpiArgumentLists(bare).get(0)).get(2).trim())
            .isEqualTo("\"Trail entries\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static boolean isMember(String rawSource) {
        String s = LocaleSensitiveFormatContractTest.strip(rawSource);
        return CAP.matcher(s).find() && KPI.matcher(s).find();
    }

    private static Map<String, Boolean> measure() {
        Map<String, Boolean> out = new TreeMap<>();
        for (Path p : javaFiles()) {
            String s = LocaleSensitiveFormatContractTest.strip(read(p));
            if (CAP.matcher(s).find() && KPI.matcher(s).find()) {
                out.put(rel(p), s.contains("ExportTruncation."));
            }
        }
        return out;
    }

    /** Argument lists of every {@code new PdfTheme.Kpi(...)}, paren-balanced. */
    private static List<String> kpiArgumentLists(String src) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("new\\s+PdfTheme\\.Kpi\\(").matcher(src);
        while (m.find()) {
            int depth = 1;
            int i = m.end();
            while (i < src.length() && depth > 0) {
                char c = src.charAt(i);
                if (c == '"') {
                    i++;
                    while (i < src.length()) {
                        if (src.charAt(i) == '\\') { i += 2; continue; }
                        if (src.charAt(i) == '"') { break; }
                        i++;
                    }
                } else if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) { break; }
                }
                i++;
            }
            out.add(src.substring(m.end(), Math.min(i, src.length())));
        }
        return out;
    }

    /** Split on top-level commas only, so a nested call's commas do not split the list. */
    private static List<String> splitTopLevel(String args) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                i++;
                while (i < args.length()) {
                    if (args.charAt(i) == '\\') { i += 2; continue; }
                    if (args.charAt(i) == '"') { break; }
                    i++;
                }
            } else if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(args.substring(start, i));
                start = i + 1;
            }
        }
        out.add(args.substring(start));
        return out;
    }

    private static void publish(Map<String, Boolean> measured) {
        StringBuilder sb = new StringBuilder();
        sb.append("F2-451 - emitters that cap a fetch and publish a figure from it\n");
        sb.append("=".repeat(78)).append('\n');
        sb.append("A capped list is fine to RENDER and wrong to COUNT. Each entry below caps its\n");
        sb.append("fetch and publishes a figure derived from it. DISCLOSED = read, and every figure\n");
        sb.append("routed through common/export/ExportTruncation. NOT YET READ = a work list with\n");
        sb.append("no verdict: whether each figure is a claim about the register or about the\n");
        sb.append("document is a question for a reader, and three of these have no test at all.\n\n");
        for (Map.Entry<String, Boolean> e : measured.entrySet()) {
            sb.append(e.getValue() ? "  DISCLOSED     " : "  NOT YET READ  ").append(e.getKey())
              .append('\n');
        }
        sb.append("\nmeasured 2026-09-03: 16 figures across these ")
          .append(measured.size()).append(" emitters;\n");
        sb.append("exactly one truncates today - platform.audit_log, 12,470 rows for organisation 1\n");
        sb.append("in dev and 5,133 in prod, against an EFFECTIVE cap of 200 - a 62x under-report.\n");
        sb.append("MAX_AUDIT read 1000; AuditLogService.list clamps it with Math.min(size, 200),\n");
        sb.append("so the constant overstated the package's audit coverage 5x, and reading it\n");
        sb.append("instead of the wire is how the first draft of this finding claimed 12x.\n");
        sb.append("It is the only register of the seven that grows with every WRITE in the\n");
        sb.append("platform rather than with the organisation's own governance work, and being\n");
        sb.append("append-only and hash-chained it can never shrink - so which cap bites first\n");
        sb.append("was predictable without measuring.\n");
        try {
            Path out = Paths.get("target");
            Files.createDirectories(out);
            Files.writeString(out.resolve("capped-export-figures.txt"), sb.toString(),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<Path> javaFiles() {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String rel(Path p) {
        return MAIN.relativize(p).toString().replace('\\', '/');
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p, e);
        }
    }
}
