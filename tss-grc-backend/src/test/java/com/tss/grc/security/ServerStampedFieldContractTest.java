package com.tss.grc.security;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-218 — a service may not accept from the caller a field its own guarded routes stamp.
 *
 * <h2>Why this is derived and not a list of field names</h2>
 *
 * <p>F2-217 found {@link MassAssignmentRatchetTest}'s authority vocabulary was a literal list of
 * names, written from the four bypasses that class was built after — so it held {@code approvedBy}
 * and {@code reviewedBy} while the platform's columns are also {@code acApprovedBy},
 * {@code reviewer}, {@code approverName}, {@code publishedAt}. <b>A census keyed on a list of names
 * measures the list.</b> The lesson generalises past widening the list: the authority a field
 * carries is a fact about <i>the service that owns it</i>, and the service already states it — in
 * the methods that stamp the field after checking a state and recording who acted.
 *
 * <p>So this gate asks each service two questions about itself and compares the answers:
 * <ul>
 *   <li><b>STAMPED</b> — which fields does a <i>guarded</i> route assign from a server-derived
 *       value? A guarded route loads a row, refuses a wrong current state, and writes a literal,
 *       a clock reading or the authenticated principal.</li>
 *   <li><b>ACCEPTED</b> — which fields does a create or update path write from the <i>request</i>?
 *       Either explicitly ({@code e.setStatus(body.getStatus())}) or wholesale, by persisting the
 *       request object itself without overwriting the field first.</li>
 * </ul>
 *
 * <p>A field in both means the service both owns and accepts it — the guard is then not a control,
 * it is a route that happens to be guarded, beside one that is not.
 *
 * <h2>The defect it was written for, probed live 2026-08-15</h2>
 *
 * <p>{@code NdiEvidenceItemService.approve()} enforces {@code NDI:APPROVE} at the controller and a
 * maker/checker SoD check in the service (the evidence owner may not approve their own item), and
 * stamps {@code reviewerName} from the principal. As {@code ranalyst} — {@code NDI:WRITE}, no
 * {@code NDI:APPROVE} — {@code POST /{id}/approve} answered <b>403</b>; {@code POST /api/ndi/evidence}
 * with {@code status=ACTIVE}, {@code reviewerName="Nobody Who Reviewed This"} and
 * {@code reviewedAt=2001-01-01} answered <b>200</b> and stored all three; and {@code PUT /{id}} with
 * {@code {"status":"ACTIVE"}} performed the PENDING → ACTIVE approval the guard had just refused.
 * No audit transition row in either case.
 *
 * <p>⚠ The update carried a comment saying {@code reviewerName} <i>"is stamped server-side from the
 * authenticated principal at approve/reject, never bound from the request body"</i> — correct, and
 * directly above the two lines that copied {@code status} (the decision) and {@code reviewedAt}
 * (when) straight from the request. Removing the attribution while leaving the decision meant a
 * WRITE-only caller could approve evidence; the record simply did not say who.
 *
 * <h2>Three detector bugs this class exists to not repeat</h2>
 *
 * <p>The scratch census that located this population, and this gate's own first three runs, got
 * the discriminator wrong three times:
 * <ol>
 *   <li>It treated {@code if (body.getStatus() == null) body.setStatus("DRAFT")} as a guard. It is
 *       a <b>default</b> — the caller's value survives whenever they send one. <b>A default and a
 *       guard are indistinguishable to any scan that only asks whether the setter is present.</b></li>
 *   <li>It then treated {@code if (body.getStatus() != null) e.setStatus(body.getStatus())} as an
 *       unconditional server write, because the null test is inverted.</li>
 *   <li>This gate treated a <b>scalar method parameter</b> as a server value, so
 *       {@code markUnderReview(Long id, String reviewerName)} doing
 *       {@code cur.setReviewerName(reviewerName)} read as a stamp — and the update that also
 *       accepts a reviewer then read as a contradiction on code with nothing wrong with it.</li>
 * </ol>
 * The first two shrank the population, which reads as a clean tree; the third grew it, which is
 * worse — a gate that fails on correct code trains the next reader to edit the assertion rather
 * than the code (F2-214). 11 raw sites, 3 refuted by the gate's own corrections.
 * <b>The discriminator is not the guard, it is the VALUE.</b> A literal, a clock or the principal
 * means the server owns the field; an expression reading the request means the caller does. That is
 * what {@link #writesFromRequest} keys on, and {@link #theValueNotTheGuardIsTheDiscriminator} pins
 * all four shapes so the two mistakes above fail rather than shrink the population.
 */
class ServerStampedFieldContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path REPORT = Path.of("target", "server-stamped-fields.txt");

    /**
     * Services whose create/update paths accept nothing their guarded routes stamp. A <b>hard
     * zero</b> each: these have been read and fixed, so a regression is a build failure and not a
     * number that drifts up.
     */
    private static final Set<String> CLOSED = Set.of(
        "AuditLifecycleService",           // F2-217
        "NdiEvidenceItemService",          // F2-218 — the SoD bypass above
        "NdiImprovementPlanService",       // F2-218
        "NdiAssessmentCycleService",       // F2-218
        "PolicyManagementService",         // F2-219 — the SEC-CMP-004 attestation forgery
        "DataSharingAgreementService",     // F2-219 — created ACTIVE and signed
        "CommitteeActionItemService",      // F2-219 — created COMPLETED
        "ClimateRiskOpportunityService",   // F2-219
        "TcfdItemService");                // F2-219 — closed at the wire, see TcfdItem#approvalStatus
    // ⚙ ProcessorAssessmentService is deliberately NOT here. It was on F2-218's list and needed no
    // change: all seven fields already carry @JsonProperty(READ_ONLY). Listing it would make a
    // service that was never open indistinguishable from one that was read and fixed — house rule
    // 3's warning about a ceiling left standing after a sweep, applied to a set of names.

    /**
     * Sites where the write path SHOULD accept the field — read in F2-219, one at a time.
     *
     * <p>⚠ Deliberately tiny, and it stays that way. F2-188 measured five reasons in a predecessor
     * artefact and <b>all five were false</b>; what made that possible was writing reasons at scale
     * for a population nobody had opened. These three were each read in the slice that added them,
     * and the map is enforced in <b>both</b> directions — an entry that stops contradicting must be
     * deleted in the same commit, so it cannot quietly outlive its subject.
     *
     * <p>The honest artefact for an <i>unread</i> population is still {@link #REPORT} plus
     * {@link #BASELINE_CONTRADICTED_SERVICES}, which is why that figure survives at zero rather than
     * being replaced by this map.
     */
    private static final Map<String, String> BY_DESIGN = Map.of(
        "NdiSettingService",
        "resetToDefault() restores value_text FROM THE ROW's own default_value, which is a server "
        + "value only because the row already holds it. Supplying the value is the entire point of "
        + "creating a setting; there is no decision, no attribution and no state being reached.",

        "SgiCommitmentService",
        "refreshValue() pulls current_value from a linked ESG_METRIC or GHG_TOTAL source, and gates "
        + "on value_source != MANUAL. For a MANUAL commitment the class javadoc states that a human "
        + "owns the number, so create/update writing it is the declared design. The APPROVE-guarded "
        + "achieve() reads it, and is defended separately: Rule C refuses an achiever who is the "
        + "last editor, and Rule D requires attached evidence.",

        "NdiComplianceAssessmentService",
        "update() is a null-guarded partial update on the same NDI:WRITE authority as setStatus() "
        + "and attachEvidence(); recording compliance_status and evidence_status is what an assessor "
        + "does on this register. The approval that IS guarded is lifecycle_status, and both paths "
        + "refuse an APPROVED row outright. complianceStatus additionally goes through "
        + "ClosedVocabulary#require on both routes, so the vocabularies cannot diverge.");

    /**
     * Everything else, as a decrease-only figure rather than a set of hand-written verdicts.
     *
     * <p>⚠ Deliberately NOT a named map with a reason each. F2-188 measured five such reasons in a
     * predecessor artefact and <b>all five were false</b>; the substantiality check made it worse,
     * because rewriting them as fluent prose made them read as verified. The honest artefact for a
     * population nobody has read yet is a work list with the evidence attached — {@link #REPORT}
     * prints every site with the fields that put it there and which of the service's own routes
     * stamps them — and a figure that may only go down.
     *
     * <p>Lower this in the commit that closes a service, having re-derived it from the gate's own
     * output rather than subtracting from the number written here (F2-207's rule: a ceiling left in
     * place after a sweep makes a tolerated member indistinguishable from a reviewed one).
     */
    private static final int BASELINE_CONTRADICTED_SERVICES = 0;

    // ── source plumbing ──────────────────────────────────────────────────────

    /**
     * ⚠ One left-to-right alternation. Two sequential passes delete 45% of a file whose {@code //}
     * prose contains a literal {@code /*} (F2-198), and the services this reads are heavily
     * commented — including comments that quote the very setter calls being counted.
     */
    private static final Pattern COMMENT = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    static String stripComments(String src) {
        return COMMENT.matcher(src).replaceAll("");
    }

    private static List<Path> serviceSources() {
        try (var s = Files.walk(MAIN_JAVA)) {
            return s.filter(p -> p.getFileName().toString().endsWith("Service.java"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Brace-balanced body starting at the first '{' at or after {@code from}. */
    static String bodyAt(String src, int from) {
        int i = src.indexOf('{', from);
        if (i < 0) return "";
        int depth = 0;
        for (int j = i; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(i, j + 1);
        }
        return src.substring(i);
    }

    private record Method(String name, String params, String body) { }

    static List<Method> publicMethods(String src) {
        List<Method> out = new ArrayList<>();
        Matcher m = Pattern.compile(
            "\\bpublic\\s+(?!class|record|interface|enum)[\\w<>,\\[\\]. ]+?\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{")
            .matcher(src);
        while (m.find()) {
            out.add(new Method(m.group(1), m.group(2), bodyAt(src, m.end() - 1)));
        }
        return out;
    }

    // ── the two questions ────────────────────────────────────────────────────

    /** A setter call: group(1) = the target local, group(2) = the field, group(3) = the value. */
    private static final Pattern SETTER = Pattern.compile(
        "\\b(\\w+)\\s*\\.\\s*set([A-Z]\\w*)\\s*\\(([^;]*?)\\)\\s*;");

    private static String lower(String field) {
        return Character.toLowerCase(field.charAt(0)) + field.substring(1);
    }

    /** {@code Type#field} — the key both halves are measured in. */
    private static String key(String type, String field) {
        return type + "#" + field;
    }

    /**
     * ⚠ F2-219 — the {@code (?s)} is load-bearing and its absence was invisible. {@code String#matches}
     * anchors the whole value, and without DOTALL a {@code .} cannot cross a newline — so <b>every
     * setter whose argument wraps</b> read as not mentioning the request at all. The house idiom
     * {@code setStatus(ServerOwnedState.requireInitial(\n    body.getStatus(), …))} wraps by width,
     * so the four services F2-218 had just closed were passing the hard zero for the wrong reason.
     *
     * <p>🔑 <b>A line-blind matcher UNDER-reports, which reads exactly like a clean tree</b>, and here
     * it was cancelled out by a second bug in the opposite direction ({@link #refusesTheRequestValue}
     * did not exist, so a refusal wrapper would have read as an acceptance). Two errors agreeing
     * produce a green that neither one earns.
     */
    private static boolean readsRequest(String value, Set<String> requestLocals) {
        return requestLocals.stream().anyMatch(
            r -> value.matches("(?s).*\\b" + Pattern.quote(r) + "\\s*\\.\\s*(get|is)\\w+\\s*\\(.*"));
    }

    /**
     * A value that passes the caller's own value through the platform's refusal primitive:
     * {@code setStatus(ServerOwnedState.requireInitial(body.getStatus(), INITIAL, …))} <em>reads</em>
     * the request in order to <b>reject</b> anything but the initial state, so the caller's value
     * cannot survive unless it already equals the server's.
     *
     * <p>Without this the fix above would fail on correct code — the shape F2-217/F2-218 introduced
     * as the house answer to this whole family would be reported as the defect it prevents, which is
     * the one failure a gate must never have (F2-214).
     *
     * <p>⚠ This keys on a <b>declared refusal primitive</b>, not on a field name — F2-217's lesson is
     * that a census keyed on names measures the names. {@link #theRefusalPrimitiveIsResolvedNotAssumed}
     * checks the class it names still exists and still throws, so deleting the guard inside
     * {@code ServerOwnedState} cannot leave this exemption standing over it.
     */
    private static boolean refusesTheRequestValue(String value) {
        return value.matches("(?s).*\\bServerOwnedState\\s*\\.\\s*require\\w*\\s*\\(.*");
    }

    /** Every parameter name of a method, whatever its type. */
    static Set<String> allParamNames(String params) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : params.split(",")) {
            String[] parts = p.trim().split("\\s+");
            if (parts.length >= 2) out.add(parts[parts.length - 1]);
        }
        return out;
    }

    /**
     * Whether this assigned value came from the caller — through the request body, or through a
     * plain parameter of the method.
     *
     * <p>⚠ The second half was missing on the gate's third run and produced a false positive on
     * correct code: {@code NdiMaturityAssessmentService#markUnderReview(Long id, String
     * reviewerName)} does {@code cur.setReviewerName(reviewerName)}, and because {@code String} is
     * deliberately not treated as a payload type, the assignment read as a <i>server</i> stamp —
     * making the update that also accepts {@code reviewerName} look like a contradiction. It is
     * not: on that register the reviewer is an <b>assignment</b> (who should review), supplied by
     * the caller on both routes, not an attribution derived from the principal.
     *
     * <p>🔑 <b>A scalar method parameter is still caller data.</b> Third shape in the same family as
     * the two census bugs this class documents, and the only one that pushed the population UP —
     * i.e. towards failing on code with nothing wrong with it, which is the failure a gate must
     * never have (F2-214).
     */
    private static boolean callerSupplied(String value, Set<String> requestLocals,
                                          Set<String> paramNames) {
        if (readsRequest(value, requestLocals)) return true;
        return paramNames.stream().anyMatch(
            p -> value.matches("(?s).*\\b" + Pattern.quote(p) + "\\b.*"));
    }

    /**
     * ⚠ F2-219 — caller data reaching a setter through a LOCAL, which {@link #callerSupplied} could
     * not see because the value is then a bare identifier naming neither the request nor a parameter.
     *
     * <p>{@code NdiComplianceAssessmentService#setStatus} does
     * {@code String j = naJustification != null ? naJustification : cur.getNaJustification();
     * cur.setNaJustification(j);} — so {@code j} read as a server stamp and the register's own
     * (correct, null-guarded) update then read as a contradiction. F2-197 recorded this exact shape
     * in the other direction: 🔑 <b>a rule keyed on a name cannot see the local alias.</b>
     *
     * <p>Deliberately narrow: only a value that IS a bare identifier is resolved, and only against
     * assignments in the same body. Anything larger is a dataflow question a regex cannot answer
     * (F2-182), and guessing would push the population up onto correct code.
     */
    private static boolean aliasesCallerData(String value, String body, Set<String> requestLocals,
                                             Set<String> paramNames) {
        if (!value.matches("\\w+")) return false;
        Matcher m = Pattern.compile(
            "\\b(?:[A-Za-z_][\\w<>,.\\[\\] ]*\\s+)?" + Pattern.quote(value) + "\\s*=([^;]*);")
            .matcher(body);
        while (m.find()) {
            if (callerSupplied(m.group(1).trim(), requestLocals, paramNames)) return true;
        }
        return false;
    }

    /**
     * Entity fields the wire already refuses per-field, via
     * {@code @JsonProperty(access = JsonProperty.Access.READ_ONLY)}.
     *
     * <p>⚠ F2-219 — {@link #CLOSED_AT_THE_WIRE} names {@code BaseEntity}'s nine, and a reader who
     * finds that subtraction present does not then ask which OTHER fields are closed the same way.
     * {@code DataSharingAgreement#signedAt} carries the annotation on the field itself and was on
     * F2-218's work list as open. 🔑 <b>Generalising an exclusion one mechanism at a time leaves the
     * remainder looking covered, because the mechanism that covers them is visibly present</b>
     * (F2-215's headline, here on the gate rather than on a fix). Derived from the entity source,
     * never a second list of names.
     */
    static Map<String, Set<String>> readOnlyOnTheWire() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        try (var s = Files.walk(MAIN_JAVA)) {
            for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".java")).toList()) {
                String src = read(p);
                if (!src.contains("JsonProperty.Access.READ_ONLY")) continue;
                String type = p.getFileName().toString().replace(".java", "");
                Matcher m = Pattern.compile(
                    "JsonProperty\\.Access\\.READ_ONLY\\s*\\)[\\s\\S]{0,400}?"
                    + "\\b\\w[\\w<>,.\\[\\]]*\\s+(\\w+)\\s*[;=]").matcher(src);
                while (m.find()) {
                    out.computeIfAbsent(type, k -> new TreeSet<>()).add(m.group(1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /**
     * Fields this method assigns a SERVER-derived value: a literal, a clock reading, the
     * authenticated principal, or a constant. The value is what decides, never the guard around it.
     */
    static Set<String> stampsServerValue(String body, Map<String, String> types,
                                         Set<String> requestLocals, Set<String> paramNames) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String value = m.group(3).trim();
            if (value.isEmpty() || value.equals("null")) continue;
            if (!refusesTheRequestValue(value)
                && (callerSupplied(value, requestLocals, paramNames)
                    || aliasesCallerData(value, body, requestLocals, paramNames))) continue;
            String type = types.get(m.group(1));
            if (type != null) out.add(key(type, lower(m.group(2))));
        }
        return out;
    }

    /** Fields this method writes FROM the request object, on any target. */
    static Set<String> writesFromRequest(String body, Map<String, String> types,
                                         Set<String> requestLocals) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String value = m.group(3).trim();
            if (refusesTheRequestValue(value)) continue;
            if (!readsRequest(value, requestLocals)) continue;
            String type = types.get(m.group(1));
            if (type != null) out.add(key(type, lower(m.group(2))));
        }
        return out;
    }

    // ── F2-219 — a DEFAULT is not a settlement ───────────────────────────────

    /**
     * The source span from the previous statement boundary back to this setter — the guard around
     * it, if there is one.
     *
     * <p>Stops at {@code ;} and {@code }} but deliberately <b>not</b> at <code>{</code>, so a
     * block-bodied guard ({@code if (…) {\n  x.setA(LIT);\n}}) is seen exactly like the inline form.
     * Stopping at the brace would return whitespace and every block-bodied default would read as an
     * unconditional stamp.
     */
    static String guardBefore(String body, int setterStart) {
        int i = setterStart;
        while (i > 0) {
            char c = body.charAt(i - 1);
            if (c == ';' || c == '}') break;
            i--;
        }
        return body.substring(i, setterStart);
    }

    /** Index just past the {@code )} matching the {@code (} at {@code open}; -1 if unbalanced. */
    private static int pastParen(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i + 1;
        }
        return -1;
    }

    /** Index just past one statement starting at {@code from} — a {@code {…}} block, or up to {@code ;}. */
    private static int pastStatement(String src, int from) {
        int i = from;
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
        if (i < src.length() && src.charAt(i) == '{') {
            int depth = 0;
            for (int j = i; j < src.length(); j++) {
                char c = src.charAt(j);
                if (c == '{') depth++;
                else if (c == '}' && --depth == 0) return j + 1;
            }
            return src.length();
        }
        int semi = src.indexOf(';', i);
        return semi < 0 ? src.length() : semi + 1;
    }

    /**
     * The whole {@code if … else if … else …} chain beginning at {@code ifAt}.
     *
     * <p>The chain is what decides whether a conditional literal is a default: its {@code else}
     * branches are the alternative the caller's value takes. A {@code throw} in a <i>separate,
     * following</i> statement is not part of it — and that distinction is the finding, not an
     * implementation detail (see {@link #aDefaultIsNotASettlement}).
     */
    static String ifChainAt(String src, int ifAt) {
        int i = src.indexOf('(', ifAt);
        if (i < 0) return "";
        i = pastParen(src, i);
        if (i < 0) return "";
        while (true) {
            i = pastStatement(src, i);
            int j = i;
            while (j < src.length() && Character.isWhitespace(src.charAt(j))) j++;
            if (!src.startsWith("else", j)) return src.substring(ifAt, i);
            int k = j + "else".length();
            while (k < src.length() && Character.isWhitespace(src.charAt(k))) k++;
            if (src.startsWith("if", k)) {
                int p = src.indexOf('(', k);
                if (p < 0) return src.substring(ifAt, i);
                int q = pastParen(src, p);
                if (q < 0) return src.substring(ifAt, i);
                i = q;
            } else {
                i = k;
            }
        }
    }

    /**
     * Fields this method assigns a literal <b>only when the caller left them absent</b>.
     *
     * <p>🔑 <b>{@code if (x == null) x = INITIAL} is a DEFAULT, not a guard — the caller's value
     * survives whenever they send one.</b> F2-218 named that as its headline and then this gate
     * counted such a write as having settled the field, so it dropped out of the ACCEPTED set. The
     * result was an under-report of exactly the shape the gate was built to find: F2-218's own work
     * list showed {@code DataSharingAgreement#signedAt} and not {@code #status}, although
     * {@code submit()} and {@code activate()} both stamp the status behind a state refusal.
     *
     * <p>Two narrowings, both deliberate, both measured:
     * <ul>
     *   <li>The guard must test <b>this field, on the request</b>. {@code if (isNew) e.setStatus(L)}
     *       is some other condition; widening to "any conditional" would grow the population onto
     *       correct code, which is the direction that trains a reader to edit the assertion (F2-214).</li>
     *   <li>A chain whose own {@code else} refuses is not a default —
     *       {@code ProcessorAssessmentService#create}'s {@code else if (!"IN_PROGRESS".equals(…))
     *       throw} closes the field, and it is excluded for that reason, verified by reading.</li>
     * </ul>
     *
     * <p>⚠ The refusal must come from the chain, <b>not merely from a {@code throw} somewhere in the
     * method</b>. {@code DataSharingAgreementService#create} follows its default with
     * {@code if (!STATUSES.contains(req.getStatus())) throw …} — a <i>vocabulary membership</i> test
     * that admits {@code ACTIVE}. 🔑 <b>Checking that a value is in the vocabulary is not checking
     * that it is a value a record may START in</b> (F2-217's sentence about {@code audit_lc}), so a
     * detector cannot infer "settled" from the presence of a throw.
     */
    static Set<String> defaults(String body, Map<String, String> types, Set<String> requestLocals) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String type = types.get(m.group(1));
            if (type == null) continue;
            String field = m.group(2);
            String guard = guardBefore(body, m.start());
            int rel = guard.lastIndexOf("if");
            if (rel < 0) continue;
            String cond = guard.substring(rel);
            if (!cond.matches("(?s)\\s*if\\s*\\(.*")) continue;
            boolean testsOwnField = requestLocals.stream().anyMatch(r -> cond.matches(
                "(?s).*\\b" + Pattern.quote(r) + "\\s*\\.\\s*(get|is)" + Pattern.quote(field)
                + "\\s*\\(.*"));
            if (!testsOwnField) continue;
            if (ifChainAt(body, m.start() - guard.length() + rel).contains("throw new")) continue;
            out.add(key(type, lower(field)));
        }
        return out;
    }

    /** Fields this method explicitly clears — closed without claiming to have derived a value. */
    static Set<String> clears(String body, Map<String, String> types) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            if (!m.group(3).trim().equals("null")) continue;
            String type = types.get(m.group(1));
            if (type != null) out.add(key(type, lower(m.group(2))));
        }
        return out;
    }

    /**
     * {@code BaseEntity}'s nine server-owned fields, which no caller can bind at all.
     *
     * <p>⚠ Without this the gate reports {@code isDeleted} and {@code updatedAt} sites as open —
     * 7 of its first 24 findings — because a service that soft-deletes in {@code delete()} and
     * persists a request object in {@code create()} matches the shape exactly. They are not open:
     * F2-206/F2-215 close them at the <b>wire</b>, in {@code common/json/ServerOwnedFieldsModule},
     * an introspector that reports READ_ONLY for these names on every {@code @Entity} Jackson binds.
     *
     * <p>🔑 <b>A gate measuring the service layer cannot see a fix made at the wire layer</b>, and
     * the failure direction is the dangerous one: it manufactures findings on code that is already
     * defended, which trains the next reader to edit the assertion rather than the code (F2-214).
     * Kept as an explicit, reasoned subtraction rather than a silent filter, and pinned by
     * {@link #theBaseEntityFieldsAreExcludedBecauseTheWireAlreadyRefusesThem} against the
     * introspector's own list so the two cannot drift.
     */
    static final Set<String> CLOSED_AT_THE_WIRE = Set.of(
        "id", "createdAt", "createdBy", "updatedAt", "updatedBy",
        "isDeleted", "deletedAt", "deletedBy", "rowVersion");

    private static final Set<String> NOT_A_PAYLOAD = Set.of(
        "Long", "String", "Integer", "Boolean", "Pageable", "LocalDate", "LocalDateTime",
        "BigDecimal", "Double", "Short", "Map", "List", "Set", "Optional", "MultipartFile");

    /** Locals naming a request-bound entity/DTO parameter of this method, with their types. */
    static Map<String, String> requestParamTypes(String params) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String p : params.split(",")) {
            String[] parts = p.trim().split("\\s+");
            if (parts.length < 2) continue;
            String type = parts[parts.length - 2];
            String name = parts[parts.length - 1];
            if (type.matches("[A-Z]\\w*") && !NOT_A_PAYLOAD.contains(type)) out.put(name, type);
        }
        return out;
    }

    static Set<String> requestParams(String params) {
        return requestParamTypes(params).keySet();
    }

    /**
     * Every local in this method with a resolvable type — parameters plus {@code Foo e = get(id);}
     * declarations.
     *
     * <p>⚠ This exists because the gate's first run invented a finding. Both sides were keyed by
     * SERVICE, so {@code NdiImprovementPlanService#addItem} — which persists an
     * {@code NdiImprovementPlanItem} — was charged with accepting {@code status},
     * {@code approverName} and {@code approvedAt}, three fields of the <i>plan</i> that
     * {@code approve()} stamps. <b>A service that owns two entities has two field vocabularies, and
     * mixing them manufactures findings on correct code</b> — F2-191 recorded the same shape one
     * level up (a same-package proxy is not same-entity); same-service is not same-entity either.
     * A gate that fails on correct code is worse than no gate (F2-214).
     */
    static Map<String, String> localTypes(String params, String body) {
        Map<String, String> out = new LinkedHashMap<>(requestParamTypes(params));
        Matcher m = Pattern.compile("\\b([A-Z]\\w*)\\s+(\\w+)\\s*=").matcher(body);
        while (m.find()) {
            if (!NOT_A_PAYLOAD.contains(m.group(1))) out.putIfAbsent(m.group(2), m.group(1));
        }
        return out;
    }

    private static boolean isWritePath(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("create") || n.startsWith("update") || n.startsWith("save")
            || n.startsWith("add") || n.startsWith("record") || n.startsWith("upsert")
            || n.startsWith("ingest");
    }

    /**
     * A guarded route: it loads a row and refuses a wrong current state. Both halves matter — the
     * load is what makes it a transition rather than a create, and the refusal is what makes it a
     * control rather than a setter with a longer name.
     */
    private static boolean isGuardedRoute(String name, String body) {
        if (isWritePath(name)) return false;
        boolean loads = body.matches("(?s).*\\b(get|find\\w*)\\s*\\(.*");
        boolean refuses = body.matches("(?s).*\\bthrow new (IllegalState|IllegalTransition)\\w*Exception.*");
        return loads && refuses;
    }

    private record Contradiction(String service, String writeMethod, Set<String> fields,
                                 Set<String> stampedBy) { }

    /** {@code Type#field} → the guarded routes that stamp it. */
    static Map<String, Set<String>> stampedBy(List<Method> methods,
                                              Map<String, Set<String>> readOnly) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Method m : methods) {
            if (!isGuardedRoute(m.name(), m.body())) continue;
            Map<String, String> types = localTypes(m.params(), m.body());
            Set<String> params = allParamNames(m.params());
            for (String f : stampsServerValue(m.body(), types, requestParams(m.params()), params)) {
                String type = f.substring(0, f.indexOf('#'));
                String field = f.substring(f.indexOf('#') + 1);
                if (CLOSED_AT_THE_WIRE.contains(field)) continue;
                if (readOnly.getOrDefault(type, Set.of()).contains(field)) continue;
                out.computeIfAbsent(f, k -> new TreeSet<>()).add(m.name());
            }
        }
        return out;
    }

    /** The {@code Type#field}s this write path takes from the caller AND the service stamps. */
    static Set<String> acceptedAndStamped(Method m, Map<String, Set<String>> stampedBy) {
        if (!isWritePath(m.name())) return Set.of();
        Map<String, String> paramTypes = requestParamTypes(m.params());
        if (paramTypes.isEmpty()) return Set.of();
        Map<String, String> types = localTypes(m.params(), m.body());
        Set<String> req = paramTypes.keySet();

        Set<String> accepted = new TreeSet<>(writesFromRequest(m.body(), types, req));
        // Persisting the request object accepts every field of THAT TYPE it did not first
        // overwrite or clear — scoped to the persisted parameter's own type, never the service's.
        for (var e : paramTypes.entrySet()) {
            if (!m.body().matches("(?s).*\\.save\\s*\\(\\s*" + Pattern.quote(e.getKey()) + "\\s*\\).*")) {
                continue;
            }
            Set<String> settled = new TreeSet<>(
                stampsServerValue(m.body(), types, req, allParamNames(m.params())));
            settled.addAll(clears(m.body(), types));
            // F2-219 — a literal written only when the caller sent nothing has settled nothing.
            settled.removeAll(defaults(m.body(), types, req));
            String prefix = e.getValue() + "#";
            for (String f : stampedBy.keySet()) {
                if (f.startsWith(prefix) && !settled.contains(f)) accepted.add(f);
            }
        }
        accepted.retainAll(stampedBy.keySet());
        return accepted;
    }

    static List<Contradiction> measure() {
        List<Contradiction> out = new ArrayList<>();
        Map<String, Set<String>> readOnly = readOnlyOnTheWire();
        for (Path p : serviceSources()) {
            String cls = p.getFileName().toString().replace(".java", "");
            String src = stripComments(read(p));
            List<Method> methods = publicMethods(src);

            Map<String, Set<String>> stampedBy = stampedBy(methods, readOnly);
            if (stampedBy.isEmpty()) continue;

            for (Method m : methods) {
                Set<String> hit = acceptedAndStamped(m, stampedBy);
                if (!hit.isEmpty()) {
                    Set<String> routes = new TreeSet<>();
                    hit.forEach(f -> routes.addAll(stampedBy.get(f)));
                    out.add(new Contradiction(cls, m.name(), hit, routes));
                }
            }
        }
        return out;
    }

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    void aClosedServiceAcceptsNothingItsOwnGuardedRoutesStamp() {
        Map<String, List<Contradiction>> byService = measure().stream()
            .filter(c -> CLOSED.contains(c.service()))
            .collect(Collectors.groupingBy(Contradiction::service));

        assertThat(byService)
            .as("""
                These services were read and fixed, so this is a hard zero, not a ceiling. A site \
                here means a create or update writes a field the same service stamps only after \
                checking the state and recording who acted — i.e. the guarded route's control can \
                be reached without it. Fix the write path; do not add the service to a tolerated \
                set.""")
            .isEmpty();
    }

    @Test
    void theRemainingPopulationIsMeasuredAndOnlyEverShrinks() throws IOException {
        List<Contradiction> all = measure();
        Set<String> services = all.stream().map(Contradiction::service)
            .filter(s -> !CLOSED.contains(s) && !BY_DESIGN.containsKey(s))
            .collect(Collectors.toCollection(TreeSet::new));

        Map<String, List<Contradiction>> grouped = new TreeMap<>(all.stream()
            .filter(c -> !CLOSED.contains(c.service()))
            .collect(Collectors.groupingBy(Contradiction::service)));

        List<String> lines = new ArrayList<>();
        lines.add("# F2-218 — write paths accepting fields their own service stamps after a guard.");
        lines.add("#");
        lines.add("# Derived, not a name list: for each service, the fields its guarded routes");
        lines.add("# assign from a server value (a literal, a clock, the principal), intersected");
        lines.add("# with the fields its create/update paths write from the request.");
        lines.add("#");
        lines.add("# This is a WORK LIST. Read the service, decide whether the field is genuinely");
        lines.add("# the caller's (an M&A letter-of-intent signing date is; an approval stamp is");
        lines.add("# not), fix or record it, and lower BASELINE_CONTRADICTED_SERVICES in the same");
        lines.add("# commit. Do NOT write a reason you have not verified — F2-188 measured five");
        lines.add("# such reasons in a predecessor artefact and all five were false.");
        lines.add("#");
        lines.add("# CLOSED (hard zero, asserted separately): " + new TreeSet<>(CLOSED));
        lines.add("");
        grouped.forEach((svc, cs) -> {
            lines.add(svc);
            cs.forEach(c -> lines.add(String.format("    %-28s %-46s stamped by %s",
                c.writeMethod(), String.join(",", c.fields()), String.join(",", c.stampedBy()))));
        });
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);

        assertThat(services.size())
            .as("""
                %d services still accept a field their own guarded routes stamp (see %s). The \
                figure is decrease-only. If it has to go UP, ask whether the QUESTION was wrong \
                before raising it — F2-204's rule — because raising a ceiling is indistinguishable \
                from tolerating a regression.""".formatted(services.size(), REPORT))
            .isLessThanOrEqualTo(BASELINE_CONTRADICTED_SERVICES);

        // ⚠ F2-219 — anti-vacuity, and the shape had to change with the number. The old floor was
        // `>= BASELINE - 3`; with the figure at zero that reads `>= -3` and can never fail. 🔑 A
        // floor is only worth having if it sits above the largest WRONG answer (F2-215), and here
        // the wrong answer IS zero, because a rotted detector reports an empty population and that
        // is indistinguishable from a finished sweep.
        //
        // So the floor is no longer a number: the three BY_DESIGN services are known to contradict
        // — they were read, and the reason each is correct is recorded above — so the detector must
        // still SEE them. That check is the reverse leg below, and it doubles as the anti-vacuity
        // guard, which a bare count could not.
        assertThat(BY_DESIGN).as("the adjudicated set is the anti-vacuity floor; do not empty it")
            .isNotEmpty();
    }

    /**
     * The adjudicated set, enforced the other way round: a service recorded as accepting a field
     * BY DESIGN must still be doing so.
     *
     * <p>🔑 Without this the map is write-only — an entry outlives the code it describes and reads,
     * to the next person, as a reviewed decision about something that is no longer true. It is also
     * this class's anti-vacuity guard: a detector that stops matching empties the population, and
     * an empty population looks exactly like a finished sweep.
     */
    @Test
    void everyByDesignEntryStillDescribesSomethingTheScanFinds() {
        Set<String> measured = measure().stream().map(Contradiction::service)
            .collect(Collectors.toCollection(TreeSet::new));

        assertThat(measured)
            .as("""
                A BY_DESIGN entry names a service whose write path accepts a field its own guarded \
                routes stamp, WITH a reason that was verified by reading. If the scan no longer \
                finds it, either the code changed (delete the entry in the same commit) or a \
                detector rotted (fix the detector — a shrinking population is not progress).""")
            .containsAll(BY_DESIGN.keySet());

        assertThat(BY_DESIGN.keySet()).doesNotContainAnyElementsOf(CLOSED);
        BY_DESIGN.forEach((svc, why) -> assertThat(why.length())
            .as("%s's reason must say why the field is genuinely the caller's, not label it", svc)
            .isGreaterThan(120));
    }

    /**
     * The two mistakes the scratch census made, both of which shrank the population.
     *
     * <p>Written as a positive control over literal source, because a detector that cannot match
     * reads exactly like a clean tree and the corpus assertions above pass either way.
     */
    @Test
    void theValueNotTheGuardIsTheDiscriminator() {
        Map<String, String> types = Map.of("body", "Foo", "e", "Foo");
        Set<String> req = Set.of("body");

        String aDefault = """
            { if (body.getStatus() == null) body.setStatus("DRAFT"); repo.save(body); }""";
        assertThat(stampsServerValue(aDefault, types, req, Set.of()))
            .as("a default assigns a literal, so the field reads as server-stamped here...")
            .contains("Foo#status");
        assertThat(writesFromRequest(aDefault, types, req))
            .as("...and it does NOT read as a caller write either, because the caller's value never "
                + "appears on the right-hand side")
            .doesNotContain("Foo#status");
        // ⚠⚠ F2-219 — and THAT is why the create-side check cannot stop at "was it overwritten".
        // This assertion used to read "...which is why the create-side check must be 'did anything
        // overwrite it before the save'" — and the answer here is yes, so `status` was counted as
        // SETTLED and dropped out of the accepted set. 🔑 F2-218's own headline, inside the gate
        // F2-218 built to enforce it: a default and a guard are indistinguishable to any scan that
        // only asks whether the setter is present. The control below asserted the defect as correct
        // — F2-159's shape, a test that is hardest to spot because it describes the code accurately.
        assertThat(defaults(aDefault, types, req))
            .as("a literal written ONLY when the caller sent nothing has settled nothing")
            .contains("Foo#status");

        String aCopy = """
            { if (body.getStatus() != null) e.setStatus(body.getStatus()); return repo.save(e); }""";
        assertThat(writesFromRequest(aCopy, types, req))
            .as("an inverted null test is a COPY, not a guard — the census read this as an "
                + "unconditional server write and cleared 7 sites that were wide open")
            .contains("Foo#status");
        assertThat(stampsServerValue(aCopy, types, req, Set.of())).doesNotContain("Foo#status");

        String stamped = """
            { e.setReviewerName(currentUsername()); e.setReviewedAt(LocalDate.now()); }""";
        assertThat(stampsServerValue(stamped, types, req, Set.of()))
            .as("the principal and the clock are server values")
            .contains("Foo#reviewerName", "Foo#reviewedAt");

        String cleared = "{ body.setReviewerName(null); repo.save(body); }";
        assertThat(stampsServerValue(cleared, types, req, Set.of()))
            .as("clearing is not stamping — it is counted separately, because a create that nulls a "
                + "field has closed it without claiming to have derived a value for it")
            .doesNotContain("Foo#reviewerName");
    }

    /**
     * F2-219's five corrections, each driven over literal source. Three of them SHRANK the
     * population and two GREW it onto correct code, and both directions had to be pinned: a
     * detector that cannot match reads exactly like a clean tree, and one that over-matches trains
     * the next reader to edit the assertion rather than the code (F2-214).
     */
    @Test
    void theFiveWaysThisDetectorMisreadTheTree() {
        Map<String, String> types = Map.of("body", "Foo", "e", "Foo");
        Set<String> req = Set.of("body");

        // (1) A value that WRAPS. String#matches anchors the whole input and `.` does not cross a
        // newline without (?s), so every multi-line setter argument read as mentioning nothing.
        String wrapped = """
            { e.setStatus(pick(
                  body.getStatus(), OTHER)); }""";
        assertThat(writesFromRequest(wrapped, types, req))
            .as("a wrapped argument still mentions the request — without (?s) this was invisible, "
                + "and the house idiom setStatus(ServerOwnedState.requireInitial(\\n body.getX()…)) "
                + "wraps by width, so the four services F2-218 closed passed for the wrong reason")
            .contains("Foo#status");

        // (2) ...but the house REFUSAL primitive reads the request precisely in order to reject it.
        String refused = """
            { body.setStatus(ServerOwnedState.requireInitial(
                  body.getStatus(), INITIAL_STATUS, "a thing", "POST /{id}/approve")); }""";
        assertThat(writesFromRequest(refused, types, req))
            .as("requireInitial() lets the caller's value through only when it already equals the "
                + "server's, so this is a settlement, not an acceptance — without it, correcting (1) "
                + "would report the fix F2-217/F2-218 introduced as the defect it prevents")
            .doesNotContain("Foo#status");
        assertThat(stampsServerValue(refused, types, req, Set.of())).contains("Foo#status");

        // (3) A DEFAULT whose own else-chain refuses IS settled — ProcessorAssessmentService#create.
        String defaultThenRefuse = """
            { if (body.getStatus() == null) body.setStatus("IN_PROGRESS");
              else if (!"IN_PROGRESS".equals(body.getStatus())) { throw new IllegalArgumentException("no"); }
              repo.save(body); }""";
        assertThat(defaults(defaultThenRefuse, types, req))
            .as("the else branch is the alternative the caller's value takes, and it throws")
            .doesNotContain("Foo#status");

        // ...whereas a following, separate vocabulary test is NOT part of the chain, and admits
        // every member of the vocabulary. 🔑 Checking a value is IN the vocabulary is not checking
        // it is one a record may START in (F2-217). This is DataSharingAgreementService#create.
        String defaultThenVocabulary = """
            { if (body.getStatus() == null) body.setStatus("DRAFT");
              if (!STATUSES.contains(body.getStatus())) throw new IllegalArgumentException("bad");
              repo.save(body); }""";
        assertThat(defaults(defaultThenVocabulary, types, req))
            .as("ACTIVE is in STATUSES, so this create could file an agreement live and signed")
            .contains("Foo#status");

        // (4) Caller data arriving through a LOCAL — NdiComplianceAssessmentService#setStatus.
        String aliased = """
            { String j = note != null ? note : cur.getNaJustification();
              e.setNaJustification(j); }""";
        assertThat(stampsServerValue(aliased, types, req, Set.of("id", "note")))
            .as("`j` names neither the request nor a parameter, so it read as a server value and "
                + "the register's own correct, null-guarded update then read as a contradiction")
            .doesNotContain("Foo#naJustification");

        // (5) A field the wire refuses per-field. Derived from the entity, not a second name list.
        Map<String, Set<String>> readOnly = readOnlyOnTheWire();
        assertThat(readOnly.getOrDefault("DataSharingAgreement", Set.of()))
            .as("DataSharingAgreement#signedAt carries @JsonProperty(READ_ONLY) and was on F2-218's "
                + "work list as open — CLOSED_AT_THE_WIRE knows BaseEntity's nine and no others")
            .contains("signedAt");
        assertThat(readOnly.getOrDefault("TcfdItem", Set.of()))
            .as("all three TCFD approval fields are closed at the wire as of F2-219")
            .contains("approvedBy", "approvedAt", "approvalStatus");
    }

    /**
     * {@link #refusesTheRequestValue} exempts a value on the strength of a named class. That class
     * must exist and must actually refuse, or the exemption is an assumption wearing a citation.
     */
    @Test
    void theRefusalPrimitiveIsResolvedNotAssumed() {
        String src = read(Path.of("src", "main", "java", "com", "tss", "grc", "common",
                                  "validation", "ServerOwnedState.java"));
        assertThat(src).contains("requireInitial").contains("throw new IllegalArgumentException");
        assertThat(refusesTheRequestValue("ServerOwnedState.requireInitial(b.getStatus(), I, \"x\")"))
            .isTrue();
        assertThat(refusesTheRequestValue("VOCAB.require(b.getStatus())"))
            .as("a ClosedVocabulary check narrows the value; it does NOT pin it to the initial "
                + "state, so it must not buy the same exemption")
            .isFalse();
    }

    /**
     * The detector must see a wholesale persist, not just explicit setters — {@code repo.save(req)}
     * with no overwrite accepts every field the entity has, which is how three of the four services
     * in {@link #CLOSED} were open.
     */
    @Test
    void persistingTheRequestObjectCountsAsAcceptingEverythingNotOverwritten() {
        String open = """
            public Foo create(Foo body) { body.setId(null); return repo.save(body); }
            public Foo approve(Long id) { Foo e = get(id);
              if (!"PENDING".equals(e.getStatus())) throw new IllegalStateException("no");
              e.setStatus("ACTIVE"); e.setReviewerName(currentUsername()); return repo.save(e); }
            """;
        assertThat(contradictionsIn(open))
            .as("create persists the request and never overwrites status or reviewerName, both of "
                + "which approve() stamps behind a state check")
            .contains("Foo#status", "Foo#reviewerName");

        String closedSrc = """
            public Foo create(Foo body) { body.setId(null); body.setStatus("PENDING");
              body.setReviewerName(null); return repo.save(body); }
            public Foo approve(Long id) { Foo e = get(id);
              if (!"PENDING".equals(e.getStatus())) throw new IllegalStateException("no");
              e.setStatus("ACTIVE"); e.setReviewerName(currentUsername()); return repo.save(e); }
            """;
        assertThat(contradictionsIn(closedSrc))
            .as("overwriting the state and clearing the stamp closes it — this is exactly the "
                + "shape F2-218 applied to the three NDI services")
            .isEmpty();

        // ⚠ F2-219 — the DEFAULT, driven through the SHIPPED predicate rather than through
        // defaults() directly. Without this the subtraction could be deleted from
        // acceptedAndStamped and the corpus would not notice: the three BY_DESIGN services do not
        // reach the gate through a default, so the population would be unchanged and the mutation
        // would survive. 🔑 Asserting a helper is not asserting that the helper is CONSULTED.
        String defaulted = """
            public Foo create(Foo body) { body.setId(null);
              if (body.getStatus() == null) body.setStatus("PENDING"); return repo.save(body); }
            public Foo approve(Long id) { Foo e = get(id);
              if (!"PENDING".equals(e.getStatus())) throw new IllegalStateException("no");
              e.setStatus("ACTIVE"); return repo.save(e); }
            """;
        assertThat(contradictionsIn(defaulted))
            .as("a default leaves the caller's status intact, so the create still accepts a field "
                + "approve() stamps behind a state check — this is DataSharingAgreementService, "
                + "CommitteeActionItemService and PolicyManagementService#createAttestation, none "
                + "of which appeared on F2-218's work list")
            .contains("Foo#status");
    }

    /**
     * Runs the SHIPPED predicate over a source fragment — {@link #stampedBy} and
     * {@link #acceptedAndStamped}, the same two calls {@link #measure} makes. A control that
     * reimplements the logic it is controlling proves only that the copy agrees with itself.
     */
    private static Set<String> contradictionsIn(String src) {
        List<Method> methods = publicMethods(src);
        Map<String, Set<String>> stamped = stampedBy(methods, Map.of());
        Set<String> hits = new TreeSet<>();
        for (Method m : methods) hits.addAll(acceptedAndStamped(m, stamped));
        return hits;
    }

    /**
     * The subtraction above must name exactly what the wire already refuses — no more (which would
     * hide real findings) and no less (which would manufacture them on defended code).
     *
     * <p>Read from {@code ServerOwnedFieldsModule}'s own source rather than restated, because a
     * second copy of a list is this codebase's most repeated defect and the whole argument for the
     * subtraction is that the other artefact is authoritative.
     */
    @Test
    void theBaseEntityFieldsAreExcludedBecauseTheWireAlreadyRefusesThem() throws IOException {
        Path introspector = MAIN_JAVA.resolve(
            Path.of("com", "tss", "grc", "common", "json", "ServerOwnedFieldsModule.java"));
        assertThat(introspector).as("F2-206/F2-215's introspector is what makes the subtraction "
            + "sound; if it has moved, this gate is over-subtracting").exists();

        String src = stripComments(Files.readString(introspector, StandardCharsets.UTF_8));
        Set<String> declared = new TreeSet<>();
        Matcher m = Pattern.compile("\"(id|createdAt|createdBy|updatedAt|updatedBy|isDeleted"
            + "|deletedAt|deletedBy|rowVersion)\"").matcher(src);
        while (m.find()) declared.add(m.group(1));

        assertThat(declared)
            .as("every field this gate declines to report must be one the introspector actually "
                + "marks READ_ONLY — otherwise the exclusion is an assumption wearing a citation")
            .containsExactlyInAnyOrderElementsOf(CLOSED_AT_THE_WIRE);
    }

    /**
     * A guarded route must both load and refuse. Without the refusal every setter-bearing method
     * would count as a control, and the population would balloon with methods that guard nothing —
     * the mirror of F2-217's masking problem, in the other direction.
     */
    @Test
    void aRouteThatLoadsButRefusesNothingIsNotAGuard() {
        String noRefusal = """
            public Foo touch(Long id) { Foo e = get(id); e.setStatus("ACTIVE"); return repo.save(e); }
            public Foo create(Foo body) { return repo.save(body); }
            """;
        assertThat(contradictionsIn(noRefusal))
            .as("touch() checks nothing, so it is not evidence that status is server-owned")
            .isEmpty();
    }
}
