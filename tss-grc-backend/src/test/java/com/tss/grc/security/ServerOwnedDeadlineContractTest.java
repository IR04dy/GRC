package com.tss.grc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-268 — the OTHER half of F2-202's rule, which had never been counted.
 *
 * <p>F2-202 established that <b>only the server may issue a tracking code or a statutory
 * deadline</b>. F2-266 and F2-267 gave the <i>code</i> half a primitive
 * ({@code common.reference.ReferenceCodeMint}) and a census
 * ({@link ClientSuppliedReferenceRatchetTest}). The <i>deadline</i> half had neither, and it has
 * been applied one instance at a time: F2-202 (the PDPL Article 4 30-day DSR clock, minted in the
 * browser), F2-172 (the intake SLA), F2-196 (the DSR sweep excluding {@code EXTENDED}), F2-250
 * ({@code aml_alerts.sla_due_at}, filed and unresolved until now). <b>Six findings, no count</b> —
 * which is what a population looks like when nothing counts it (F2-208).
 *
 * <p><b>This is a CENSUS, not a ban, and the reason is the same as the reference gate's.</b> A
 * deadline the caller supplies is not automatically wrong: three kinds live in this population and
 * only one of them is F2-202's defect.
 * <ol>
 *   <li><b>Policy- or statute-derived</b> — the platform, not the caller, knows the interval. The
 *       server computes it: {@code DataSubjectRightsService#createDsr} does
 *       {@code receivedAt.plusDays(30)} (PDPL Art. 4), {@code PrivacyAssuranceService#createBreach}
 *       does {@code detectedAt().plusHours(72)} (PDPL Art. 20), {@code IntakeService} does
 *       {@code now.plusHours(routing.slaHours())}. <b>This is the only defect bucket.</b></li>
 *   <li><b>A plan the operator sets</b> — {@code ObligationService#create}'s
 *       {@code firstDueDate}, {@code ChangeManagementService#addCondition}'s {@code dueAt}. There
 *       is no interval to derive from: the first occurrence of a recurring obligation is a date
 *       somebody chooses, and refusing it would fail on correct code (F2-214).</li>
 *   <li><b>An outside party's date</b> — {@code ObService#createTpp}'s {@code certExpiresAt} is on
 *       the TPP's certificate. Same rule as F2-209's for references: a date only an outside
 *       authority can set is <i>received and recorded, or absent</i>, and the server may not
 *       compute it either.</li>
 * </ol>
 *
 * <p>So this class records <b>no verdicts</b> (F2-188 measured the alternative: five claims, five
 * false). What it holds is the count, decrease-only, and the intersection below.
 *
 * <p><b>🔑 The intersection is the interesting set</b>
 * ({@link #propertiesWithBothAServerComputationAndACallerWrittenSite()}): a property the platform
 * already knows how to compute <i>and</i> takes from the caller somewhere else. The sharpest member
 * is inside <b>one class</b> — {@code OutsourcingArrangementService} computes
 * {@code nextReviewDueDate} as {@code today.plusMonths(12)} in {@code recordReview} and takes it
 * from the request in {@code create} and {@code update}. F2-215's two-of-N at its most literal, on
 * a SAMA outsourcing review cadence that {@code OutsourcingArrangementRepository} really does
 * consume ({@code nextReviewDueDate <= :asOf}, 14 rows in dev and prod with 2 already past due).
 *
 * <p>⚠ <b>Filed rather than fixed, and the reason is a judgement this test must not pre-empt.</b>
 * On {@code create} there is no prior review for a cadence to anchor to, so the first date is
 * arguably kind 2; but {@code update} then lets it be pushed out indefinitely, which can empty the
 * reviews-due list. Whether an operator may exceed the cadence is a product decision about a
 * customer's SAMA obligations, and inventing an answer to make the register look enforced is
 * F2-196's defect. What the tree does record is that the cadence is a <b>literal</b> in one method
 * where the sibling {@code SubprocessorService} names its own
 * ({@code REVIEW_CADENCE_MONTHS}) — so nothing else can see it.
 *
 * <p>⚙ And it resolves F2-250's recorded uncertainty about {@code ethics.aml_alerts.sla_due_at},
 * which read <i>"the deadline is the caller's by design rather than by oversight"</i>. Measured: no
 * AML SLA is declared anywhere in the backend or the frontend, the only statement of one is a
 * comment in a <b>seed DDL</b> ({@code -- usually +30 days from detection}), and <b>nothing reads
 * the column</b> — no sweep, no query, no export, no rendered surface, where {@code socops} has
 * {@code SECURITY_EVENT_SLA_BREACHED} and a scheduled sweep. So it is inert rather than by design,
 * and building the SLA is a product decision.
 */
class ServerOwnedDeadlineContractTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** Where the census lands, so no document has to restate it (F2-265). */
    private static final Path CENSUS = Path.of("target", "caller-supplied-deadlines.txt");

    /**
     * A deadline-shaped property.
     *
     * <p>⚠ Deliberately <b>not</b> {@code *At} alone and <b>not</b> {@code *Date} alone:
     * {@code createdAt}, {@code decidedAt} and {@code assessmentDate} stamp a past act, and
     * counting them would make this gate fire on correct code — the one failure a gate must never
     * have (F2-214). Pinned in both directions by
     * {@link #theDeadlineShapeIsPinnedInBothDirections()}.
     */
    private static final Pattern DEADLINE = Pattern.compile(
        "^(?:[a-z]\\w*)?"
        + "(?:Due|DueAt|DueDate|DueOn|Deadline|ExpiresAt|ExpiryDate|ValidUntil|TargetDate)$"
        + "|^(?:due|dueAt|dueDate|dueOn|deadline|expiresAt|expiryDate|validUntil|targetDate)$");

    private static final Pattern SETTER = Pattern.compile("\\.set([A-Z]\\w*)\\s*\\(");

    private static final Pattern CREATE_ISH = Pattern.compile(
        "\\b(?:public|protected|private)\\s+[\\w<>,.\\[\\]?]+\\s+"
        + "(create\\w*|submit\\w*|start\\w*|open\\w*|record\\w*|register\\w*|import\\w*|add\\w*)"
        + "\\s*\\(");

    /** The caller's value reached the setter. */
    private static final Pattern FROM_REQUEST =
        Pattern.compile("\\b(?:req|request|in|dto|body|cmd)\\b\\s*\\.");

    /** The server computed it — a clock reading, an interval, or a named compute helper. */
    /**
     * The server computed it — a clock reading, an interval, or a named compute helper.
     *
     * <p>⚠ <b>Tested BEFORE {@link #FROM_REQUEST}, and that ordering is load-bearing.</b> The house
     * fallback shape is {@code req.x() != null ? req.x() : LocalDate.now().plusMonths(6)}, which
     * reads the request <i>and</i> computes — and for this census the interesting fact is that the
     * platform <b>can</b> compute the interval, so it counts as server-computed. Breaking this
     * pattern moves those sites into the caller-supplied bucket and the count goes 22 → 27, which
     * is how the census leg catches a broken classifier rather than only the pin doing so.
     */
    private static final Pattern FROM_SERVER = Pattern.compile(
        "\\b(?:LocalDate(?:Time)?|Instant|OffsetDateTime)\\s*\\.\\s*now\\s*\\("
        + "|plus(?:Days|Hours|Months|Weeks)\\s*\\("
        + "|compute\\w*(?:Sla|Due|Deadline)\\w*\\s*\\(");

    /** ONE left-to-right alternation: two sequential passes corrupt a file (F2-198/F2-211). */
    private static final Pattern COMMENTS =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    /**
     * Measured by this gate on this tree. Decrease-only: closing one lowers it, and a new
     * caller-supplied deadline has to be a deliberate raise whose commit message says which of the
     * three kinds it is.
     */
    private static final int BASELINE_CALLER_SUPPLIED_DEADLINES = 22;

    /** Anti-vacuity: a collapsed scan must fail loudly rather than report a clean zero (F2-69). */
    private static final int MIN_SOURCES_SCANNED = 400;

    private record Site(String key, String argument) { }

    private static String stripComments(String src) {
        return COMMENTS.matcher(src).replaceAll("");
    }

    private static String balancedFrom(String src, int from, char open, char close) {
        int i = src.indexOf(open, from);
        if (i < 0) {
            return null;
        }
        int depth = 0;
        for (int j = i; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return src.substring(i + 1, j);
                }
            }
        }
        return null;
    }

    /** (caller-written sites, server-computed sites), keyed by property name. */
    private static TreeMap<String, List<Site>>[] measure() throws IOException {
        TreeMap<String, List<Site>> req = new TreeMap<>();
        TreeMap<String, List<Site>> srv = new TreeMap<>();
        int scanned = 0;
        List<Path> sources;
        try (var walk = Files.walk(MAIN)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path p : sources) {
            String name = p.getFileName().toString();
            scanned++;
            if (!name.endsWith("Service.java") && !name.endsWith("Controller.java")) {
                continue;
            }
            String src = stripComments(Files.readString(p, StandardCharsets.UTF_8));
            Matcher cm = CREATE_ISH.matcher(src);
            while (cm.find()) {
                String body = balancedFrom(src, cm.end(), '{', '}');
                if (body == null) {
                    continue;
                }
                Matcher sm = SETTER.matcher(body);
                while (sm.find()) {
                    String prop = sm.group(1);
                    String low = Character.toLowerCase(prop.charAt(0)) + prop.substring(1);
                    if (!DEADLINE.matcher(low).matches()) {
                        continue;
                    }
                    String arg = balancedFrom(body, sm.end() - 1, '(', ')');
                    if (arg == null) {
                        continue;
                    }
                    arg = arg.replaceAll("\\s+", " ").trim();
                    String key = name.replace(".java", "") + "#" + cm.group(1) + " :: set" + prop;
                    if (FROM_SERVER.matcher(arg).find()) {
                        srv.computeIfAbsent(low, k -> new ArrayList<>()).add(new Site(key, arg));
                    } else if (FROM_REQUEST.matcher(arg).find()) {
                        req.computeIfAbsent(low, k -> new ArrayList<>()).add(new Site(key, arg));
                    }
                }
            }
        }
        assertThat(scanned)
            .as("only %d java sources walked under %s; the scan collapsed and a low number here "
                + "reports a clean tree (F2-69)", scanned, MAIN)
            .isGreaterThanOrEqualTo(MIN_SOURCES_SCANNED);
        @SuppressWarnings("unchecked")
        TreeMap<String, List<Site>>[] out = new TreeMap[] { req, srv };
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F2-268: caller-supplied deadlines are COUNTED, since they cannot all be banned")
    void callerSuppliedDeadlinesAreCounted() throws IOException {
        var m = measure();
        var req = m[0];
        var srv = m[1];

        List<String> out = new ArrayList<>();
        out.add("# Caller-supplied deadlines on create paths - GENERATED by");
        out.add("# ServerOwnedDeadlineContractTest. Do not hand-edit, and do not copy the count");
        out.add("# into prose (F2-265). NO VERDICTS: three kinds live here and only one is");
        out.add("# F2-202's defect - see the class javadoc.");
        int n = req.values().stream().mapToInt(List::size).sum();
        out.add("caller_supplied_sites=" + n);
        out.add("server_computed_sites=" + srv.values().stream().mapToInt(List::size).sum());
        out.add("");
        out.add("## caller-supplied");
        req.forEach((prop, sites) -> {
            out.add("  " + prop + (srv.containsKey(prop)
                ? "   <-- the server computes this same property elsewhere" : ""));
            sites.stream().map(Site::key).distinct().sorted().forEach(k -> out.add("      " + k));
        });
        out.add("");
        out.add("## server-computed (the pattern to copy)");
        srv.forEach((prop, sites) -> {
            out.add("  " + prop);
            sites.forEach(s -> out.add("      " + s.key() + "   " + s.argument()));
        });
        Files.deleteIfExists(CENSUS);
        String bodyText = String.join(System.lineSeparator(), out);
        Files.writeString(CENSUS, bodyText, StandardCharsets.UTF_8);
        assertThat(Files.readString(CENSUS, StandardCharsets.UTF_8))
            .as("the report on disk must be the one this run produced, not a leftover")
            .isEqualTo(bodyText);

        assertThat(n)
            .as("%d create-path sites take a deadline from the request; the measured ceiling is "
                + "%d. This is a CENSUS, not a ban: a caller-set deadline is only F2-202's defect "
                + "when the platform - not the caller - knows the interval. If you closed one, "
                + "lower the number in the same commit; if you added one, say in the commit "
                + "message which of the three kinds it is. Full list: %s",
                n, BASELINE_CALLER_SUPPLIED_DEADLINES, CENSUS)
            .isLessThanOrEqualTo(BASELINE_CALLER_SUPPLIED_DEADLINES);
    }

    @Test
    @DisplayName("F2-268: the intersection — the platform can compute it and asks the caller anyway")
    void propertiesWithBothAServerComputationAndACallerWrittenSite() throws IOException {
        var m = measure();
        Set<String> both = new TreeSet<>(m[0].keySet());
        both.retainAll(m[1].keySet());

        assertThat(both)
            .as("the properties the platform already knows how to compute AND takes from a caller "
                + "somewhere else. This is the set worth reading first, and it is asserted "
                + "EXACTLY rather than as a ceiling, because a decrease-only count cannot express "
                + "'the population was undercounted' (F2-204). Sharpest member: "
                + "OutsourcingArrangementService computes nextReviewDueDate as "
                + "today.plusMonths(12) in recordReview and takes it from the request in create "
                + "and update - one class, F2-215's two-of-N. And nextTestDue is the same shape in "
                + "SupplierResilienceTestService, which F2-267 edited one slice earlier to fix "
                + "testCode and left this: a class you have already edited reads as one you have "
                + "already checked (F2-185b).")
            .containsExactly("dueDate", "nextReviewDue", "nextReviewDueDate", "nextTestDue",
                             "slaDeadline", "targetDate");
    }

    @Test
    @DisplayName("F2-268: the deadline shape is pinned in both directions")
    void theDeadlineShapeIsPinnedInBothDirections() {
        // ⚠ This pin caught a real gap in the pattern above before it shipped as the census. The
        // first draft required a CAPITALISED suffix, so bare `targetDate`, `validUntil` and
        // `nextTestDue` never matched — and setTargetDate exists in 7 files, setExpiryDate in 8,
        // setExpiresAt in 6. The throwaway Python locator that used the same regex therefore
        // UNDER-reported, which reads exactly like a smaller population.
        for (String yes : List.of("dueDate", "dueAt", "dueOn", "slaDueAt", "slaDeadline", "deadline",
                                  "submissionDueDate", "nextReviewDueDate", "nextReviewDueAt",
                                  "nextReviewDue", "certExpiresAt", "expiresAt", "expiryDate",
                                  "dpaExpiryDate", "targetDate", "internalTargetDate",
                                  "nextTestDue", "validUntil", "ackSlaDueOn", "renewalDueAt",
                                  "ratificationDueAt", "remediationDueAt")) {
            assertThat(DEADLINE.matcher(yes).matches())
                .as("'%s' names a deadline and must be counted", yes).isTrue();
        }
        // A stamp of a PAST act is not a deadline, and neither is a COUNT of days, a SCORE, a
        // boolean or a collection. Counting these would make the census fire on correct code,
        // which trains the next reader to edit the assertion (F2-214). Every entry below after
        // contractEndDate is a real name in this tree that the widened pattern must still reject.
        for (String no : List.of("createdAt", "updatedAt", "decidedAt", "approvedAt", "reviewedAt",
                                 "assessmentDate", "detectedAt", "receivedAt", "occurredAt",
                                 "signedAt", "completedAt", "startDate", "contractEndDate",
                                 "dueOffsetBusinessDays", "evidenceExpiryWarningDays",
                                 "avgTargetScore", "expiredEvidence", "expiringContracts",
                                 "hasNetZeroTarget")) {
            assertThat(DEADLINE.matcher(no).matches())
                .as("'%s' stamps a past act, not a deadline, and must NOT be counted", no)
                .isFalse();
        }
    }
}
