package com.tss.grc.module.reporting.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.reporting.entity.ReportSchedule;
import com.tss.grc.module.reporting.repository.ReportRunRepository;
import com.tss.grc.module.reporting.repository.ReportScheduleRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ⚠⚠ F2-315 — the format a caller asks for, the bytes produced, the file extension and the MIME
 * type are decided in <b>three independent switches</b>, and they agree only where they happen to
 * line up.
 *
 * <pre>
 *   renderBody   branches on the REPORT TYPE  (PDF ? …Pdf : …Xlsx; BOARD_PACK: PPTX ? pptx : pdf)
 *   filenameFor  branches on the FORMAT       (PPTX -> pptx, XLSX -> xlsx, default -> pdf)
 *   mimeFor      branches on the FORMAT       (PPTX, XLSX, default -> application/pdf)
 * </pre>
 *
 * <p>So {@code DOCX} + {@code RISK_REGISTER} produced <b>XLSX bytes</b> named {@code .pdf} with an
 * {@code application/pdf} MIME type, and {@code XLSX} + {@code BOARD_PACK} produced <b>PDF bytes</b>
 * named {@code .xlsx} with an Excel MIME type. 🔑 <b>[[F2-293]] filed the first and missed the
 * second, because it looked for {@code DOCX}</b> — the defect is not a format, it is two switches
 * over one pair with nobody comparing them ([[F2-177]]).
 *
 * <p>These legs are hermetic: they read the two services and the page from disk, the way
 * {@code ConnectorBackingContractTest} reads {@code connectors.ts}.
 */
class ReportFormatContractTest {

    private static final Path MAIN = Paths.get("src", "main", "java", "com", "tss", "grc",
                                               "module", "reporting", "service");
    private static final Path PAGE = Paths.get("..", "tss-grc-ui-v2", "src", "pages", "reporting",
                                               "ReportSchedulesPage.tsx");

    /** A {@code case "X"} label inside a switch. */
    private static final Pattern CASE_LABEL = Pattern.compile("case\\s+\"([A-Z_]+)\"");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** Comments stripped in ONE left-to-right alternation — two passes corrupt the source. */
    private static String strip(String s) {
        return Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/")
            .matcher(s).replaceAll(m -> " ".repeat(m.group().length()));
    }

    private static String methodBody(String src, String signatureFragment) {
        int at = src.indexOf(signatureFragment);
        assertThat(at).as("could not find %s — the gate is reading the wrong file", signatureFragment)
            .isGreaterThanOrEqualTo(0);
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') { depth++; } else if (c == '}') { depth--; if (depth == 0) {
                return src.substring(open, i + 1); } }
        }
        throw new AssertionError("unbalanced braces after " + signatureFragment);
    }

    // ── the declaration is the renderer's own vocabulary ────────────────────────────────────────

    /**
     * F2-315 — a new report type cannot ship without declaring which formats it can produce.
     *
     * <p>{@code SUPPORTED_FORMATS}'s keys must be exactly {@code renderBody}'s {@code case} labels.
     * Asserted in <b>both</b> directions: a declared type the renderer does not handle is a promise
     * nothing keeps, and a handled type with no declaration is a pair nothing validates, which is
     * how the mislabelled file arose in the first place.
     */
    @Test
    @DisplayName("every report type the renderer handles declares its formats, and vice versa")
    void theDeclarationMatchesTheRenderersOwnCaseLabels() throws IOException {
        String body = methodBody(strip(read(MAIN.resolve("ReportSchedulerService.java"))),
                                 "private Rendered render(");
        Set<String> handled = new TreeSet<>();
        Matcher m = CASE_LABEL.matcher(body);
        while (m.find()) {
            handled.add(m.group(1));
        }

        assertThat(handled).as("""
            render's case labels could not be parsed. If that returned nothing, every \
            assertion below is vacuous.""").hasSizeGreaterThanOrEqualTo(5);

        assertThat(new TreeSet<>(ReportSchedulerService.SUPPORTED_FORMATS.keySet())).as("""
            SUPPORTED_FORMATS and renderBody disagree about which report types exist.

            A type renderBody handles with no declaration is a pair nothing validates — that is how \
            XLSX + BOARD_PACK came to return PDF bytes named .xlsx. A declared type renderBody does \
            not handle is a promise nothing keeps: the schedule fails with "Unknown report type".

            renderBody handles : %s
            declared : %s""".formatted(handled,
                new TreeSet<>(ReportSchedulerService.SUPPORTED_FORMATS.keySet())))
            .isEqualTo(handled);
    }

    /**
     * F2-315 — {@code renderBody} defaults every type's format to non-PDF, and the readers default a
     * null format to {@code PDF}, so {@code PDF} must be renderable for every declared type or an
     * omitted format silently produces the wrong bytes. {@code requireRenderableFormat} leaves a
     * null format alone on that basis; this asserts the basis.
     */
    @Test
    void pdfIsRenderableForEveryDeclaredTypeBecauseAnOmittedFormatBecomesPdf() {
        List<String> missing = ReportSchedulerService.SUPPORTED_FORMATS.entrySet().stream()
            .filter(e -> !e.getValue().contains("PDF"))
            .map(Map.Entry::getKey)
            .toList();
        assertThat(missing).as("""
            A declared report type does not list PDF, but every reader treats a null format as PDF \
            (renderBody, filenameFor and mimeFor all do). requireRenderableFormat therefore lets a \
            null format through — which would now produce exactly the mislabelling this slice \
            closed.""").isEmpty();
    }

    /**
     * ⚠⚠ F2-316 — the bytes and <b>both</b> labels come from one switch, so a mismatch is not a bug
     * to catch: it cannot be expressed.
     *
     * <p>This replaces [[F2-315]]'s leg, which asserted that every declared non-PDF format had an
     * explicit {@code case} in {@code filenameFor} <i>and</i> {@code mimeFor}. Those two methods are
     * gone — they branched on the <b>format</b> while the renderer branched on the <b>type</b>, and
     * their shared {@code default -> pdf} is what labelled every unrecognised format a PDF whatever
     * bytes it held. ⚠ The old leg is not weakened, it is <b>obsolete</b>: its subject no longer
     * exists, and this leg fails if either switch comes back.
     *
     * <p>🔑 The idiom is the platform's own: {@code NdiReportController#streamBytes} assigns the
     * bytes, the MIME type and the extension in the same {@code case} arm, and
     * {@code MlModelController#export} derives all three from one {@code boolean}. Every other
     * download handler puts the format in the <b>route</b>. The report scheduler was the only one
     * that put it in a <b>column</b> and read it from three places ([[F2-195]]).
     */
    @Test
    @DisplayName("the bytes and both labels are produced together, by three factories and no other")
    void aMismatchedFormatLabelIsUnrepresentable() throws IOException {
        String src = strip(read(MAIN.resolve("ReportSchedulerService.java")));

        assertThat(src).as("""
            The two label switches F2-316 deleted are back. They branched on the format while the renderer branched on the type, so their shared `default -> pdf` labelled every unrecognised format a PDF whatever bytes it held.""")
            .doesNotContain("filenameFor")
            .doesNotContain("mimeFor");

        // only the three factories may build a Rendered, so an arm cannot invent a mismatched pair
        List<String> constructors = new ArrayList<>();
        Matcher ctor = Pattern.compile("new Rendered\\(").matcher(src);
        while (ctor.find()) {
            constructors.add(src.substring(Math.max(0, src.lastIndexOf("private static Rendered",
                                                                      ctor.start())),
                                           ctor.start()).lines().findFirst().orElse("?"));
        }
        assertThat(constructors).as("""
            A Rendered is built somewhere other than the three factories. Each factory pairs one extension with one MIME type; anything else can pair them freely, which is exactly the state F2-316 removed.""")
            .hasSize(3);

        // and each factory pairs the right extension with the right MIME type
        record Pair(String factory, String ext, String mime) { }
        List<Pair> expected = List.of(
            new Pair("pdf",  ".pdf",  "application/pdf"),
            new Pair("xlsx", ".xlsx", "spreadsheetml.sheet"),
            new Pair("pptx", ".pptx", "presentationml.presentation"));
        List<String> wrong = new ArrayList<>();
        for (Pair p : expected) {
            String body = methodBody(src, "private static Rendered " + p.factory() + "(");
            if (!body.contains(p.ext())) {
                wrong.add(p.factory() + " does not append " + p.ext());
            }
            if (!body.contains(p.mime())) {
                wrong.add(p.factory() + " does not declare " + p.mime());
            }
        }
        assertThat(wrong).as("""
            A factory pairs an extension with the wrong MIME type. These three pairings are the whole of the contract now: everything upstream chooses WHICH factory, and none of them can choose a label.""").isEmpty();
    }

    /**
     * ⚠⚠ F2-316 — the residual the collapse does <b>not</b> close, said out loud.
     *
     * <p>Three factories make a mismatched <em>label pair</em> unrepresentable. They do not stop an
     * arm handing XLSX bytes to {@code pdf(...)} — the original defect, moved upstream. The
     * mutation harness found exactly that surviving, and a survivor is a hole only when the mutated
     * code is genuinely wrong ([[F2-206]]): this one is.
     *
     * <p>So every factory call inside {@code render} must be handed bytes from a generator that names
     * the same format — either in its method-name suffix ({@code riskRegisterXlsx},
     * {@code generatePptx}) or as a string literal argument ({@code export("XLSX")}).
     *
     * <p>⚠ That is a <b>naming</b> rule, which [[F2-217]] warns measures the list its author knew.
     * Stated as a limitation rather than hidden: a generator naming no format at all is reported as
     * <em>unresolvable</em> rather than passed, so the answer cannot quietly shrink, and the count
     * below fails if the population stops resolving.
     */
    @Test
    @DisplayName("each arm hands its bytes to the factory matching the generator own format")
    void noArmRoutesBytesToTheWrongFactory() throws IOException {
        String body = methodBody(strip(read(MAIN.resolve("ReportSchedulerService.java"))),
                                 "private Rendered render(");
        Matcher call = Pattern.compile("\\b(pdf|xlsx|pptx)\\(([^;]*?), stem\\)").matcher(body);
        List<String> wrong = new ArrayList<>();
        int seen = 0;
        while (call.find()) {
            seen++;
            String factory = call.group(1);
            String arg = call.group(2).trim();
            String lower = arg.toLowerCase();
            boolean namesOwn = lower.contains(factory);
            boolean namesAnother = List.of("pdf", "xlsx", "pptx").stream()
                .filter(f -> !f.equals(factory))
                .anyMatch(lower::contains);
            if (namesAnother) {
                wrong.add(factory + "(...) is handed " + arg + " — the bytes are one format and "
                          + "the label is another. That is the F2-315 defect moved upstream of the "
                          + "factories, which is why this leg exists.");
            } else if (!namesOwn) {
                wrong.add(factory + "(...) is handed " + arg + " — the generator names no "
                          + "format, so nothing can check the pairing. Give it an explicit format "
                          + "argument rather than leaving it unresolvable.");
            }
        }
        assertThat(seen).as("No factory calls were found inside render, so this leg is vacuous and "
                            + "the residual it covers is unguarded.")
            .isGreaterThanOrEqualTo(8);
        assertThat(wrong).as("An arm bytes and its factory disagree about the format.").isEmpty();
    }

    // ── the frontend offers exactly what the platform can emit ──────────────────────────────────

    /**
     * ⚠⚠ F2-315 — {@code DOCX} was offered by the form and <b>no report type can emit it</b>
     * ([[F2-193]]: a catalogue must not claim a capability that does not exist). The options must
     * therefore be exactly the union of the declared sets — no wider, and no narrower either, since
     * a format the platform can produce and the form hides is a capability nobody can reach.
     */
    @Test
    @DisplayName("the form offers exactly the formats some report type can produce")
    void theFormatOptionsAreExactlyTheDeclaredUnion() throws IOException {
        String page = strip(read(PAGE));
        int at = page.indexOf("const FORMAT_OPTIONS");
        assertThat(at).as("FORMAT_OPTIONS not found — the gate is reading the wrong page")
            .isGreaterThanOrEqualTo(0);
        String block = page.substring(at, page.indexOf("];", at));

        Set<String> offered = new LinkedHashSet<>();
        Matcher m = Pattern.compile("value:\\s*'([A-Z]+)'").matcher(block);
        while (m.find()) {
            offered.add(m.group(1));
        }

        Set<String> declared = new TreeSet<>();
        ReportSchedulerService.SUPPORTED_FORMATS.values().forEach(declared::addAll);

        assertThat(new TreeSet<>(offered)).as("""
            The format dropdown and what the platform can render disagree.

            Offering more is F2-193's defect — DOCX was offered and no type emits it, so the \
            recipient received XLSX bytes named .pdf. Offering fewer hides a capability nobody can \
            then reach.

            offered  : %s
            declared : %s""".formatted(offered, declared))
            .isEqualTo(declared);

        assertThat(offered).as("DOCX is not producible by any report type").doesNotContain("DOCX");
    }

    /**
     * F2-315 — a format only <b>some</b> report types can produce must say so in its own label.
     *
     * <p>The form is a flat select: it cannot narrow by the selected report type, so the label is the
     * only thing telling an operator that Excel is not available for the board pack and PowerPoint is
     * only available for it. The server refuses the pair either way — but a label that has gone stale
     * against the matrix sends the operator to a 400 they cannot predict, which is [[F2-212]]'s
     * label-versus-predicate drift with the predicate on the other side of the wire.
     *
     * <p>⚠ Checked per language is out of scope here: these three labels are literals in the page
     * rather than translation keys, which is a pre-existing gap (house rule 7) and is filed.
     */
    @Test
    @DisplayName("a format not every report type supports carries a qualifier in its label")
    void aPartiallySupportedFormatSaysSoInItsLabel() throws IOException {
        String page = strip(read(PAGE));
        int at = page.indexOf("const FORMAT_OPTIONS");
        String block = page.substring(at, page.indexOf("];", at));

        int typeCount = ReportSchedulerService.SUPPORTED_FORMATS.size();
        List<String> unqualified = new ArrayList<>();
        Matcher m = Pattern.compile("value:\\s*'([A-Z]+)',\\s*label:\\s*'([^']*)'").matcher(block);
        int seen = 0;
        while (m.find()) {
            seen++;
            String fmt = m.group(1);
            String label = m.group(2);
            long supporting = ReportSchedulerService.SUPPORTED_FORMATS.values().stream()
                .filter(s -> s.contains(fmt)).count();
            boolean universal = supporting == typeCount;
            boolean qualified = label.contains("(");
            if (!universal && !qualified) {
                unqualified.add(fmt + " — supported by " + supporting + " of " + typeCount
                                + " report types, labelled '" + label + "' with no qualifier");
            }
            if (universal && qualified) {
                unqualified.add(fmt + " — supported by EVERY report type, yet its label '" + label
                                + "' carries a restriction that no longer exists");
            }
        }
        assertThat(seen).as("the label pattern matched nothing — this leg would be vacuous")
            .isEqualTo(ReportSchedulerService.SUPPORTED_FORMATS.values().stream()
                .flatMap(Set::stream).distinct().toList().size());
        assertThat(unqualified).as("""
            A format option's label disagrees with which report types can actually produce it.

            The select is flat, so the label is the only thing an operator can read before the \
            server refuses the pair. Enforced in BOTH directions: an unqualified partial format \
            invites a 400 nobody could predict, and a qualifier left on a format that became \
            universal tells the operator a restriction that is gone.""")
            .isEmpty();
    }

    // ── the behavioural half: does the SERVICE actually consult the declaration? ────────────────
    //
    // Every leg above is structural, so deleting `requireRenderableFormat(t)` from `apply` would
    // leave all four green — F2-274's rule, that a leg exercising a primitive says nothing about
    // whether the code under review calls it.

    private ReportScheduleRepository scheduleRepo;
    private ReportScheduleService service;

    @BeforeEach
    void wireTheRealService() {
        scheduleRepo = mock(ReportScheduleRepository.class);
        service = new ReportScheduleService(scheduleRepo, mock(ReportRunRepository.class),
                                            mock(ReportSchedulerService.class));
        when(scheduleRepo.save(any(ReportSchedule.class))).thenAnswer(i -> i.getArgument(0));
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static ReportSchedule req(String type, String format) {
        ReportSchedule r = new ReportSchedule();
        r.setName("probe");
        r.setReportType(type);
        r.setFormat(format);
        r.setRecipients("board@example.com");
        return r;
    }

    @Test
    @DisplayName("a create naming a format its report type cannot produce is refused, not mislabelled")
    void theCreateRefusesAnUnrenderablePair() {
        assertThatThrownBy(() -> service.create(req("BOARD_PACK", "XLSX")))
            .as("PDF bytes named .xlsx with an Excel MIME type was the pre-fix behaviour")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("format")
            .hasMessageContaining("BOARD_PACK")
            .hasMessageContaining("PPTX");

        assertThatThrownBy(() -> service.create(req("RISK_REGISTER", "DOCX")))
            .as("no report type can emit DOCX at all")
            .isInstanceOf(IllegalArgumentException.class);

        // and the pairs that ARE renderable still work
        assertThat(service.create(req("BOARD_PACK", "PPTX")).getFormat()).isEqualTo("PPTX");
        assertThat(service.create(req("RISK_REGISTER", "XLSX")).getFormat()).isEqualTo("XLSX");
        // an omitted format is tolerated by the check and then defaulted by create(), in that
        // order — apply() runs before the DEFAULT_FORMAT block, so the null branch really is
        // reachable rather than decoration (F2-184). PDF is renderable for every declared type,
        // which pdfIsRenderableForEveryDeclaredType... asserts rather than assumes.
        assertThat(service.create(req("RISK_REGISTER", null)).getFormat()).isEqualTo("PDF");
    }

    /**
     * ⚠⚠ The leg the whole fix turns on. {@code apply} copies {@code reportType} and {@code format}
     * independently, so an update changing <b>only the format</b> must be validated against the
     * <b>stored</b> type. Checking the request alone would pass exactly the update that produces a
     * mislabelled file.
     */
    @Test
    @DisplayName("an update changing only the format is checked against the STORED report type")
    void thePairIsValidatedOnTheResultingRowRatherThanOnTheRequest() {
        ReportSchedule stored = new ReportSchedule();
        stored.setId(7L);
        stored.setOrganisationId(1L);
        stored.setName("Board pack");
        stored.setReportType("BOARD_PACK");
        stored.setFormat("PDF");
        when(scheduleRepo.findById(7L)).thenReturn(Optional.of(stored));

        ReportSchedule formatOnly = new ReportSchedule();
        formatOnly.setFormat("XLSX"); // no reportType — it comes from storage

        assertThatThrownBy(() -> service.update(7L, formatOnly))
            .as("""
                the request names no report type, so a check against the REQUEST would see null and wave it through — and the board pack cannot be rendered as XLSX""")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BOARD_PACK");
    }

    /**
     * F2-315 — a report type this platform cannot render at all must stay <b>editable</b>. Seven of
     * the eleven live schedules hold such a type ([[F2-293]]), one of them with format {@code XLSX};
     * refusing here would turn a rendering gap into a data-entry lockout ([[F2-183]]).
     */
    @Test
    @DisplayName("a report type with no generator is left alone rather than made uneditable")
    void anUnrenderableReportTypeStaysEditable() {
        ReportSchedule stored = new ReportSchedule();
        stored.setId(9L);
        stored.setOrganisationId(1L);
        stored.setReportType("PDPL_DSR_BREACH_TRACKER");   // real live row; no generator
        stored.setFormat("XLSX");
        when(scheduleRepo.findById(9L)).thenReturn(Optional.of(stored));

        ReportSchedule rename = new ReportSchedule();
        rename.setName("renamed");

        assertThat(service.update(9L, rename).getFormat())
            .as("the seven un-rendered schedules must remain editable")
            .isEqualTo("XLSX");
    }
}
