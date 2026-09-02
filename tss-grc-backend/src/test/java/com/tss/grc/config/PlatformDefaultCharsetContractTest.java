package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
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
 * F2-430 — <b>a text-to-bytes conversion with no charset is platform-dependent, and on this
 * JVM the platform is {@code Cp1252}, which cannot represent Arabic at all.</b>
 *
 * <p>{@link String#getBytes()} and {@code new String(byte[])} use
 * {@link java.nio.charset.Charset#defaultCharset()}. On JDK 18+ that is UTF-8 (JEP 400); this
 * platform is pinned to <b>JDK 17</b>, where it is the host's ANSI code page. Measured on this
 * machine — {@code file.encoding=Cp1252}, {@code native.encoding=Cp1252} — and not reasoned
 * about:
 *
 * <pre>
 *   "مخاطر الأمن".getBytes()        -> 3f 3f 3f 3f 3f 20 3f 3f 3f 3f 3f   (eleven '?')
 *   "مخاطر الأمن".getBytes(UTF_8)   -> 21 bytes, lossless
 * </pre>
 *
 * <p>This is the sibling of F2-416/F2-417's family: there the ambient setting is
 * {@link java.util.Locale#getDefault()}, here it is
 * {@link java.nio.charset.Charset#defaultCharset()}. The two share a shape and nothing else —
 * a different JDK mechanism, a different remedy vocabulary, and a different <b>direction of
 * harm</b>: a locale mangles digits and case reversibly, a charset destroys bytes.
 *
 * <p><b>What it cost.</b> {@code BcmEvidencePackageService.writeAuditCsv} ended
 * {@code sb.toString().getBytes()}, and that CSV is the {@code audit_log.csv} of the BCM audit
 * evidence package — the ZIP a SAMA/NCA reviewer imports into their own SIEM (the method's own
 * comment says so). Of the <b>1,488</b> BCM-scoped audit rows the package exports in dev (387 in
 * prod), <b>137</b> (11 in prod) carry a character Cp1252 cannot represent, in two kinds:
 * <ul>
 *   <li>the platform's own transition arrow {@code U+2192}, 136 occurrences — so
 *       {@code "BIA BIA-2026-000007 DRAFT → UNDER_REVIEW"} reaches the regulator as
 *       {@code "DRAFT ? UNDER_REVIEW"}, which no longer says which state it came from; and</li>
 *   <li>an Arabic personal name — the crisis <b>communication contact</b>, the person a BCM plan
 *       calls during an incident — as {@code "????? ?????????"}.</li>
 * </ul>
 *
 * <p>🔑 <b>The convention already existed one module away, and this file was the outlier.</b>
 * {@code CalendarController} — the platform's other text-download path — declares
 * {@code charset=UTF-8} on its {@code text/calendar} response <em>and</em> encodes with
 * {@code StandardCharsets.UTF_8}. Measured: <b>27</b> sites in main name a charset explicitly,
 * including every hash chain (F2-31's audit seal, the evidence chain, the CCM and signature
 * digests). So the discipline is the house rule and the byte-emission site was the exception —
 * F2-215's two-of-N, with the mechanism visibly present at every digest.
 *
 * <p>🔑 <b>And the test that should have caught it could not, for two independent reasons.</b>
 * F2-325's {@code auditCsv_carriesTheActorAndRoleAndBlanksItHonestlyWhenAbsent} drives the real
 * service, reads the real ZIP entry and asserts the CSV's contents — with a helper that decoded
 * via {@code new String(zin.readAllBytes())}, the <em>same</em> platform default the writer
 * encoded with. <b>Encoding and decoding through one charset round-trips losslessly whatever
 * that charset is, so a round-trip test is structurally blind to a charset defect.</b> Its
 * fixture was also entirely ASCII, so even a UTF-8 reader would have passed over it.
 *
 * <p><b>Why every leg here is a hard zero with no exemption map.</b> There is no site where
 * "whatever charset the deployment happens to be configured with" is the answer: a
 * String↔bytes conversion is either an encoding of text (→ {@code UTF_8}, or a named charset the
 * consumer requires) or a byte scan (→ {@code ISO_8859_1}, the one charset that maps every byte
 * to exactly one code point). There is no third case, so there is no correct code for these legs
 * to fail on (F2-214) and nothing to exempt.
 *
 * <p>⚠ The two surviving bare {@code .getBytes()} calls are <b>{@code MultipartFile.getBytes()}</b>
 * — a different method on a different type, returning the raw upload with no charset involved.
 * They are spared <b>structurally</b>, by resolving the receiver's declared type, and never by
 * name: a named exemption would let the next {@code String.getBytes()} escape by being called on
 * a variable somebody happened to list. A receiver whose type cannot be resolved <b>fails</b>,
 * because "unresolved" reads identically to "clean" (F2-206).
 */
class PlatformDefaultCharsetContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** {@code .getBytes()} with EMPTY parens — the platform-default overload. */
    private static final Pattern BARE_GET_BYTES = Pattern.compile("\\.getBytes\\s*\\(\\s*\\)");

    private static final Pattern NEW_STRING = Pattern.compile("\\bnew\\s+String\\s*\\(");

    /** A local, parameter or field declaration: captures the declared type and the name. */
    private static final Pattern DECL = Pattern.compile(
        "\\b(?:final\\s+)?((?:byte|char|int)\\s*\\[\\s*\\]|[A-Z][\\w.]*(?:\\s*\\[\\s*\\])?|String)"
        + "\\s+([a-z_]\\w*)\\s*(?:=|[,;)])");

    /** Any explicitly-named charset, in the two shapes this codebase uses. */
    private static final Pattern NAMES_A_CHARSET =
        Pattern.compile("StandardCharsets\\s*\\.|Charset\\s*\\.\\s*forName");

    /**
     * APIs whose no-charset overload is the platform default. Each must be <b>absent</b> — all
     * nine measure 0 today, so pinning them costs nothing and stops the next one arriving.
     * {@code Files.readString}/{@code writeString}/{@code newBufferedReader} are deliberately
     * <em>not</em> here: their no-charset overloads are specified as UTF-8, not as the default.
     */
    private static final Map<String, Pattern> ALWAYS_DEFAULT = new LinkedHashMap<>();
    static {
        ALWAYS_DEFAULT.put("new FileWriter(", Pattern.compile("\\bnew\\s+FileWriter\\s*\\("));
        ALWAYS_DEFAULT.put("new FileReader(", Pattern.compile("\\bnew\\s+FileReader\\s*\\("));
        ALWAYS_DEFAULT.put("new PrintWriter(", Pattern.compile("\\bnew\\s+PrintWriter\\s*\\("));
        ALWAYS_DEFAULT.put("new Scanner(", Pattern.compile("\\bnew\\s+Scanner\\s*\\("));
        ALWAYS_DEFAULT.put("Charset.defaultCharset()",
            Pattern.compile("Charset\\s*\\.\\s*defaultCharset\\s*\\("));
        ALWAYS_DEFAULT.put("getProperty(\"file.encoding\")",
            Pattern.compile("getProperty\\s*\\(\\s*\"file\\.encoding\""));
        ALWAYS_DEFAULT.put("IOUtils.toString(",
            Pattern.compile("IOUtils\\s*\\.\\s*toString\\s*\\("));
        ALWAYS_DEFAULT.put("1-arg new InputStreamReader(",
            Pattern.compile("\\bnew\\s+InputStreamReader\\s*\\((?:[^,()]|\\([^()]*\\))*\\)"));
        ALWAYS_DEFAULT.put("1-arg new OutputStreamWriter(",
            Pattern.compile("\\bnew\\s+OutputStreamWriter\\s*\\((?:[^,()]|\\([^()]*\\))*\\)"));
    }

    // ── Leg 1: nothing encodes a String with the platform default ────────────────────────────

    @Test
    void noStringIsEncodedWithThePlatformDefaultCharset() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        Map<String, String> spared = new TreeMap<>();

        for (Path p : mainSources()) {
            String code = strippedCode(p);
            String rel = rel(p);
            Map<String, Set<String>> types = declaredTypes(code);
            Matcher m = BARE_GET_BYTES.matcher(code);
            while (m.find()) {
                String site = rel + ":" + lineOf(code, m.start());
                String receiver = receiverBefore(code, m.start());
                Verdict v = classify(receiver, types);
                switch (v.kind) {
                    case STRING -> violations.add(site + "  " + receiver + ".getBytes()"
                        + "   [" + v.why + "]");
                    case OTHER -> spared.put(site, receiver + " : " + v.why);
                    case UNKNOWN -> unresolved.add(site + "  receiver=" + receiver);
                }
            }
        }

        assertThat(unresolved)
            .as("""
                F2-430 — a .getBytes() receiver whose declared type this gate cannot resolve must
                fail, not be skipped: an unresolved site reads identically to a clean one (F2-206).
                Declare the receiver's type in the same file, or hoist the expression into a local.""")
            .isEmpty();

        assertThat(violations)
            .as("""
                F2-430 — String.getBytes() with no argument encodes with Charset.defaultCharset(),
                measured Cp1252 on this JDK 17. Every Arabic character becomes '?', irreversibly,
                and the platform's own transition arrow U+2192 with it.

                Pass the charset the CONSUMER requires: StandardCharsets.UTF_8 for text that leaves
                the platform (the house convention — see CalendarController's ICS download), or
                ISO_8859_1 for a byte scan. There is no third answer, which is why this leg has no
                exemption map.

                Offending sites:""")
            .isEmpty();

        // For the reader: the receivers this gate spares, and why it may.
        assertThat(spared.values())
            .as("a spared receiver must have been resolved to a non-String type, never named")
            .allSatisfy(why -> assertThat(why).doesNotContain("String"));
    }

    // ── Leg 2: nothing decodes bytes with the platform default ───────────────────────────────

    @Test
    void noByteArrayIsDecodedWithThePlatformDefaultCharset() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        for (Path p : mainSources()) {
            String code = strippedCode(p);
            String rel = rel(p);
            Map<String, Set<String>> types = declaredTypes(code);
            Matcher m = NEW_STRING.matcher(code);
            while (m.find()) {
                int open = code.indexOf('(', m.start());
                String args = balanced(code, open);
                String site = rel + ":" + lineOf(code, m.start());
                String first = firstArgument(args);
                if (first.isBlank()) {
                    continue;                            // new String() — nothing to decode
                }
                Verdict v = classifyBytes(first, types);
                if (v.kind == Kind.UNKNOWN) {
                    unresolved.add(site + "  first arg=" + first);
                } else if (v.kind == Kind.STRING && !NAMES_A_CHARSET.matcher(args).find()) {
                    violations.add(site + "  new String(" + args.trim() + ")   [" + v.why + "]");
                }
            }
        }

        assertThat(unresolved)
            .as("""
                F2-430 — a new String(...) whose first argument's type cannot be resolved must
                fail rather than be skipped (F2-206). Hoist it into a declared local.""")
            .isEmpty();

        assertThat(violations)
            .as("""
                F2-430 — new String(byte[]) with no charset decodes with Charset.defaultCharset().
                Name the charset: UTF_8 to read text, ISO_8859_1 to scan bytes (which is what
                FileUploadController's EICAR probe does, deliberately — 8859-1 maps every byte to
                exactly one code point, so no byte can be lost to a replacement character).

                Offending sites:""")
            .isEmpty();
    }

    // ── Leg 3: the APIs that have no safe no-arg form at all ─────────────────────────────────

    @Test
    void noApiWithoutACharsetParameterIsReached() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        for (Path p : mainSources()) {
            String code = strippedCode(p);
            for (Map.Entry<String, Pattern> e : ALWAYS_DEFAULT.entrySet()) {
                Matcher m = e.getValue().matcher(code);
                while (m.find()) {
                    found.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                         .add(rel(p) + ":" + lineOf(code, m.start()));
                }
            }
        }
        assertThat(found)
            .as("""
                F2-430 — each of these reads or writes text through Charset.defaultCharset() and
                has a charset-taking overload. All nine measured ZERO when this gate was written,
                so the zero is free; the point is that the next one cannot arrive unnoticed.

                Note Files.readString / writeString / newBufferedReader are deliberately NOT in
                this family: their no-charset overloads are SPECIFIED as UTF-8, not defaulted.""")
            .isEmpty();
    }

    // ── Leg 4: anti-vacuity — the disciplined population is real ─────────────────────────────

    @Test
    void theDisciplinedPopulationIsMeasured() throws IOException {
        int named = 0;
        for (Path p : mainSources()) {
            Matcher m = NAMES_A_CHARSET.matcher(strippedCode(p));
            while (m.find()) {
                named++;
            }
        }
        // Measured 27 at F2-430. The largest WRONG answer is 0 — a detector that has stopped
        // matching — so a floor of 20 sits above it while tolerating ordinary churn (F2-215).
        assertThat(named)
            .as("""
                F2-430 — if this collapses, NAMES_A_CHARSET has stopped matching and every hard
                zero above is passing vacuously over a tree full of platform-default conversions.
                A hard zero over a population the instrument cannot see is the most reassuring
                possible artefact (F2-205). Re-derive before lowering it.""")
            .isGreaterThanOrEqualTo(20);
    }

    // ── Leg 5: the detector itself, in both directions ───────────────────────────────────────

    @Test
    void theDetectorFiresOnEveryShapeAndSparesTheReceiversItMust() {
        // must FIRE: every way this codebase spells "encode a String with the default"
        assertThat(verdictFor("String s = x;\n byte[] b = s.getBytes();"))
            .isEqualTo(Kind.STRING);
        assertThat(verdictFor("byte[] b = sb.toString().getBytes();"))
            .as("a .toString() receiver is a String however its own type is declared")
            .isEqualTo(Kind.STRING);
        assertThat(verdictFor("byte[] b = (\"a\" + n).getBytes();"))
            .as("a parenthesised concatenation is a String")
            .isEqualTo(Kind.STRING);
        assertThat(verdictFor("byte[] b = \"literal\".getBytes();"))
            .as("a string literal survives strip() and must still be seen")
            .isEqualTo(Kind.STRING);

        // must be SPARED: a different method on a different type
        assertThat(verdictFor("MultipartFile file = f;\n byte[] b = file.getBytes();"))
            .as("MultipartFile.getBytes() returns the raw upload — no charset is involved")
            .isEqualTo(Kind.OTHER);

        // must FAIL rather than pass: an unresolvable receiver
        assertThat(verdictFor("byte[] b = whoKnows.getBytes();"))
            .as("an unresolved receiver must be reported, never skipped")
            .isEqualTo(Kind.UNKNOWN);

        // and the always-default family must not fire on the charset-taking forms
        assertThat(ALWAYS_DEFAULT.get("1-arg new InputStreamReader(")
                .matcher("new InputStreamReader(in, cs)").find())
            .as("a two-argument InputStreamReader names its charset and must be spared")
            .isFalse();
        assertThat(ALWAYS_DEFAULT.get("1-arg new InputStreamReader(")
                .matcher("new InputStreamReader(in)").find())
            .isTrue();

        // a comment quoting the retired shape must not read as a live site (F2-193)
        String stripped = strippedSource("/* was: s.getBytes(); */\nString x = y;\n");
        assertThat(BARE_GET_BYTES.matcher(stripped).find())
            .as("""
                F2-193 — this file's own javadoc quotes the shape it retired, and so does
                BcmEvidencePackageServiceTest's. A detector that reads the epitaph as the corpse
                fails on a CORRECT tree. (It bit while this very slice was being written: a
                post-condition asserting the retired text was absent fired on the javadoc
                explaining its removal.)""")
            .isFalse();
    }

    // ── Leg 6: the fixed sites and the exemplar, pinned in both directions ───────────────────

    @Test
    void theRegulatorTextIsUtf8AndTheByteScanIsLatin1() throws IOException {
        String pkg = read("com/tss/grc/module/bcm/cycle/service/BcmEvidencePackageService.java");
        assertThat(pkg)
            .as("the regulator's audit_log.csv must be UTF-8 — it is the entry that carries the "
                + "audit messages, and 137 of 1,488 exported dev rows hold a character Cp1252 "
                + "cannot represent")
            .contains("return sb.toString().getBytes(StandardCharsets.UTF_8);");
        assertThat(pkg)
            .as("MANIFEST.txt is the same shape in the same file — F2-215's sibling, benign on "
                + "Cp1252 (its em-dash is 0x97 there) and not on ISO-8859-1 or ASCII")
            .contains("manifest.getBytes(StandardCharsets.UTF_8)");

        String cal = read("com/tss/grc/module/platform/calendar/controller/CalendarController.java");
        assertThat(cal)
            .as("the exemplar: the platform's other text download encodes with UTF-8 AND declares "
                + "charset=UTF-8 on the response. If this changes, the convention this fix "
                + "followed no longer exists and F2-430's reasoning must be re-read.")
            .contains("body.getBytes(StandardCharsets.UTF_8)")
            .contains("charset=UTF-8");

        String upload = read("com/tss/grc/module/platform/files/controller/FileUploadController.java");
        assertThat(upload)
            .as("""
                the EICAR probe is a BYTE SCAN, so ISO-8859-1 is correct and UTF-8 is not:
                8859-1 maps every byte to exactly one code point, so no byte can be merged into a
                replacement character. Pinned in both directions so a later "make it consistent"
                sweep to UTF_8 fails here and forces this paragraph to be re-read.""")
            .contains("new String(bytes, 0, probeLen, StandardCharsets.ISO_8859_1)")
            .doesNotContain("new String(bytes, 0, probeLen, StandardCharsets.UTF_8)");
    }

    // ── Resolution ───────────────────────────────────────────────────────────────────────────

    private enum Kind { STRING, OTHER, UNKNOWN }

    private record Verdict(Kind kind, String why) {}

    /** Is the receiver of a bare {@code .getBytes()} a String? */
    private static Verdict classify(String receiver, Map<String, Set<String>> types) {
        if (receiver.isEmpty()) {
            return new Verdict(Kind.UNKNOWN, "no receiver");
        }
        if (receiver.endsWith(".toString()")) {
            return new Verdict(Kind.STRING, "expression: .toString()");
        }
        if (receiver.startsWith("\"") || receiver.startsWith("(")) {
            return new Verdict(Kind.STRING, "expression: literal or concatenation");
        }
        String base = receiver.split("\\.")[0];
        Set<String> declared = types.getOrDefault(base, Set.of());
        if (declared.size() != 1) {
            return new Verdict(Kind.UNKNOWN,
                declared.isEmpty() ? "no declaration of '" + base + "'"
                                   : "ambiguous: " + new TreeMap<>(
                                       Map.of("kinds", declared)).get("kinds"));
        }
        String only = declared.iterator().next();
        return "String".equals(only)
            ? new Verdict(Kind.STRING, "declared String")
            : new Verdict(Kind.OTHER, "declared " + only);
    }

    /** Is the first argument of a {@code new String(...)} a byte array? */
    private static Verdict classifyBytes(String first, Map<String, Set<String>> types) {
        if (first.endsWith(".readAllBytes()") || first.endsWith(".toByteArray()")
                || first.contains(".getBytes(")) {
            return new Verdict(Kind.STRING, "expression yielding byte[]");
        }
        String base = first.split("[.\\[(]")[0].trim();
        Set<String> declared = types.getOrDefault(base, Set.of());
        if (declared.size() != 1) {
            return new Verdict(Kind.UNKNOWN, "cannot resolve '" + base + "'");
        }
        String only = declared.iterator().next();
        if ("byte[]".equals(only)) {
            return new Verdict(Kind.STRING, "declared byte[]");
        }
        return new Verdict(Kind.OTHER, "declared " + only);
    }

    /** Every {@code Type name} declaration in the file, name -> the set of types seen. */
    private static Map<String, Set<String>> declaredTypes(String code) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Matcher m = DECL.matcher(code);
        while (m.find()) {
            out.computeIfAbsent(m.group(2), k -> new LinkedHashSet<>())
               .add(m.group(1).replaceAll("\\s+", ""));
        }
        return out;
    }

    /** The expression immediately left of index {@code at}. */
    private static String receiverBefore(String code, int at) {
        int j = at;
        if (j > 0 && code.charAt(j - 1) == ')') {
            int depth = 0;
            while (j > 0) {
                j--;
                char c = code.charAt(j);
                if (c == ')') {
                    depth++;
                } else if (c == '(') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
            }
            int k = j;
            while (k > 0 && (Character.isLetterOrDigit(code.charAt(k - 1))
                             || "._$".indexOf(code.charAt(k - 1)) >= 0)) {
                k--;
            }
            return code.substring(k, at).trim();
        }
        if (j > 0 && code.charAt(j - 1) == '"') {
            return "\"...\"";
        }
        int k = j;
        while (k > 0 && (Character.isLetterOrDigit(code.charAt(k - 1))
                         || "._$".indexOf(code.charAt(k - 1)) >= 0)) {
            k--;
        }
        return code.substring(k, at).trim();
    }

    /** The paren-balanced argument list beginning at the {@code (} at {@code open}. */
    private static String balanced(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return code.substring(open + 1, i);
                }
            }
        }
        return code.substring(open + 1);
    }

    private static String firstArgument(String args) {
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                return args.substring(0, i).trim();
            }
        }
        return args.trim();
    }

    // ── Harness ──────────────────────────────────────────────────────────────────────────────

    /** Drive the resolver over a synthetic snippet — the self-test's only entry point. */
    private static Kind verdictFor(String snippet) {
        String code = strippedSource(snippet);
        Matcher m = BARE_GET_BYTES.matcher(code);
        assertThat(m.find()).as("the synthetic snippet must contain a bare .getBytes()").isTrue();
        return classify(receiverBefore(code, m.start()), declaredTypes(code)).kind();
    }

    private static String strippedSource(String src) {
        return LocaleSensitiveFormatContractTest.strip(src.replace("\r\n", "\n"));
    }

    private static String strippedCode(Path p) throws IOException {
        return strippedSource(Files.readString(p));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative)).replace("\r\n", "\n");
    }

    private static List<Path> mainSources() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String rel(Path p) {
        return MAIN.relativize(p).toString().replace('\\', '/');
    }

    private static int lineOf(String code, int at) {
        return (int) code.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
    }
}
