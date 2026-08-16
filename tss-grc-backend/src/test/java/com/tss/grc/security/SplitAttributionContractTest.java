package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-223 — <b>the server stamps WHEN, the caller says WHO.</b>
 *
 * <p>When one method writes {@code set<verb>At(<the server's own clock>)} and, for the SAME verb,
 * writes {@code set<verb>By…} / {@code set<verb>erUserId(…)} <b>from the request</b>, it has split
 * one attribution across two owners. The clock half is the proof: by stamping the time itself the
 * server has already declared that it — not the caller — is the recorder of this act.
 *
 * <p>🔑 <b>Why a relationship and not a list of names.</b> F2-217 proved that a census keyed on a
 * literal field-name vocabulary measures the vocabulary: {@code MassAssignmentRatchetTest}'s
 * authority list was written from the four bypasses that class was built after, so it held
 * {@code approvedBy} while the platform's columns were also {@code cfoApproverName},
 * {@code verifierName}, {@code dispatchedByUserId}, {@code approvedByLegalName}. This gate names
 * no field. The verb root is taken from the code, and the pairing is the finding.
 *
 * <p><b>What it found (2026-08-16), all proved live against PLANTED rows (F2-206):</b>
 * <pre>
 *   IaAnnualPlanService#transition     approvedByUserId  — IIA Std 2010 annual audit plan.
 *                                      The server REQUIRED the caller to name the approver and
 *                                      threw if absent, while stamping approvedAt itself.
 *                                      Probed: actor=compliance(3), stored approver=4.
 *   SanctionsHitService#reviewHit      reviewerUserId    — AML/SAMA sanctions hit adjudication on
 *                                      an AML:APPROVE route; the audit row quoted the CLAIM.
 *   DisposalCertificateService#verify  verifierName      — PDPL Art.18 disposal evidence. The
 *                                #complete executorName    controller already resolved the
 *                                      principal and passed it in for the *_by ID column.
 *   CptyService#assignRating           approvedByUserId  — SAMA large exposure. One line below
 *                        #upsertLimit  approvedByUserId    setAssignedByUserId(currentUserId()).
 *                                      upsertLimit also took approvedAt from the BROWSER.
 *   AmlAlertService#transition         decidedByName     — STR filed with the Saudi FIU, from a
 *   DisclosureService#transition       decidedByName       window.prompt(); `?? ''` on Cancel.
 *   BowTieAnalysisService#approve      approvedByName
 *   QuantitativeBiaService#cfoApprove  cfoApproverName
 *   StrategyRoiService#recommend       decidedByName/UserId
 *   CrisisCommsDispatchService#dispatch dispatchedByName/UserId — NCEMA cascade.
 *   CrisisCommsTemplateService#transition approvedByLegalName / approvedByCisoName — the very
 *                                      state CrisisCommsDispatchService refuses to send without.
 * </pre>
 *
 * <p>🔑 <b>The fix already existed in the tree.</b> {@code KycService#disposition} resolves the
 * decider from {@link com.tss.grc.common.security.CurrentActor} and refuses to proceed without
 * one — <i>"An AML disposition with no decider is not a disposition."</i> It sits in the same
 * package as {@code AmlAlertService}, over the same concept, and was never copied.
 *
 * <p>⚠ <b>This gate is deliberately blind to one case, and that is recorded rather than papered
 * over:</b> where the caller supplies BOTH halves there is no asymmetry to detect.
 * {@code CptyService#upsertLimit} took {@code approvedAt} from the body as well, so it was
 * invisible here and was found by the complementary question — <i>which routes are guarded by a
 * permission that NAMES AN ACT ({@code MODULE:APPROVE}) and let the body say who performed it?</i>
 * {@link #anActGuardedRouteMayNotTakeItsOwnActorFromTheBody()} keeps that half.
 */
class SplitAttributionContractTest {

    private static final Path MAIN = Paths.get("src/main/java/com/tss/grc");

    /**
     * The survivors, each read in this slice. Enforced in BOTH directions: a new split fails, and
     * an entry whose site is fixed must be deleted in the same commit (F2-218).
     */
    private static final Map<String, String> BY_DESIGN = Map.of(
        "ObService#revokeConsent::revokedBy",
        "REFUTED — revokedBy is a PARTY, not a person. ObConsentsPage declares "
        + "useState<RevokedBy>('BANK') and badges CUSTOMER differently; the Open Banking consent "
        + "may be revoked by the customer, the bank or the TPP, and which of them it was is not "
        + "derivable from whoever happens to call the endpoint.",

        "CrossBorderTransferService#assess::residencyCheckBy",
        "FILED — the method also lets the caller supply residencyCheckAt (defaulting to now), and "
        + "that is evidence the PDPL Art.29 residency check may have been performed earlier and "
        + "elsewhere, so its performer need not be the caller. Where a method accepts the clock "
        + "too, the act may not be this call. Deciding whether this register wants an external "
        + "assessor's name or the principal is an owner decision, not a side effect of this fix.",

        "ControlLibraryService#updateMapping::reviewerUserId",
        "FILED — ambiguous between attribution and ASSIGNMENT, and the site has a second defect "
        + "that has to be settled first: setLastReviewedAt(now()) fires on EVERY update, so "
        + "editing a mapping's rationale already records it as reviewed. Until that is decided, "
        + "stamping the principal would attribute a review nobody performed."
    );

    // ─── the detector ────────────────────────────────────────────────────────

    /** F2-198/F2-211: ONE left-to-right alternation, never two sequential passes. */
    private static final Pattern COMMENTS = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    /** A server clock: now(), now(clock), Instant.now(), System.currentTimeMillis(). */
    private static final Pattern SERVER_CLOCK =
        Pattern.compile("\\b(?:now\\s*\\(|Instant\\.now|System\\.currentTimeMillis)");

    /**
     * A local holding a clock reading: {@code LocalDateTime now = LocalDateTime.now();}
     *
     * <p>⚠ DETECTOR GAP 7, and the only reason it was found is that the pre-fix CONTROL ran the
     * shipped detector over {@code git show master:} and reported
     * {@code CrisisCommsTemplateService} SILENT — a file this slice had already fixed by hand.
     * There the clock reaches the setter through a variable ({@code setApprovedByLegalAt(now)}),
     * so an argument-only test for {@code now(} sees nothing. Seventh gap in this slice and the
     * seventh to SHRINK the population. 🔑 <b>A green scan on the fixed tree cannot tell you the
     * detector works; only running it against the defect can.</b>
     */
    private static final Pattern CLOCK_LOCAL = Pattern.compile(
        "\\b\\w+\\s+(\\w+)\\s*=\\s*[^;]*?(?:now\\s*\\(|Instant\\.now|System\\.currentTimeMillis)");

    /** True when this setter argument carries a reading of the server's own clock. */
    static boolean isServerClock(String arg, String methodBody) {
        if (SERVER_CLOCK.matcher(arg).find()) return true;
        String bare = arg.trim();
        if (!bare.matches("\\w+")) return false;
        Matcher m = CLOCK_LOCAL.matcher(methodBody);
        while (m.find()) {
            if (m.group(1).equals(bare)) return true;
        }
        return false;
    }

    /** A value that came off the wire. */
    private static final Pattern REQUEST_VALUE =
        Pattern.compile("\\b(?:req|request|body|dto|in|payload)\\s*\\.\\s*(?:get)?\\w+\\s*\\(");

    /**
     * An actor-shaped field: the agent noun, or the act plus who did it.
     *
     * <p>⚠ The {@code By[A-Z]\w*} branch is load-bearing and its absence cost this slice a site.
     * A first draft listed the tails literally ({@code ByName}, {@code ByUserId}, …), so
     * {@code approvedByLegalName} — which ends in {@code LegalName} — matched none of them and
     * {@code CrisisCommsTemplateService#transition} never appeared in the population at all. It
     * was found by a residual grep, not by the instrument, and it is the CISO sign-off that
     * {@code CrisisCommsDispatchService} refuses to send a crisis cascade without. Same shape as
     * the {@code -or} bug in {@link #verbRoot}: <b>a qualifier sitting between the verb and the
     * tail defeats every suffix rule, and the miss SHRINKS the population — which reads exactly
     * like a clean tree.</b>
     */
    private static final Pattern ACTOR_TAIL =
        Pattern.compile("(?:By[A-Z]\\w*|By|erUserId|erName|erEmail|erId|er"
                        + "|orUserId|orName|orEmail|orId|or)$");

    private static final Pattern SETTER = Pattern.compile("\\.\\s*set([A-Z]\\w*)\\s*\\(");

    /**
     * The verb both halves of an attribution share.
     *
     * <p>⚠ Two spellings broke the first draft of this and both SHRANK the population, which reads
     * exactly like a clean tree. (1) The agent noun also takes {@code -or}: {@code executorName}
     * matched no tail, the trailing {@code Name} was taken as the qualifier, and the root became
     * {@code name} — so it could never pair with {@code executedAt}. Its sibling
     * {@code verifierName} DID pair, so {@code DisposalCertificateService} looked measured while
     * one of its two identical sites was invisible. (2) A qualified agent puts the qualifier
     * BETWEEN the verb and the tail — {@code approvedByLegalName} — which no suffix rule reaches.
     * Taking everything before {@code By} handles both.
     */
    static String verbRoot(String field) {
        String s = field;
        int by = s.indexOf("By");
        if (by > 0) {
            s = s.substring(0, by);
        } else {
            for (String tail : new String[]{"UserId", "Name", "Email", "Id", "At", "Date", "On"}) {
                if (s.endsWith(tail) && s.length() > tail.length()) {
                    s = s.substring(0, s.length() - tail.length());
                    break;
                }
            }
        }
        s = s.replaceAll("(?:ed|er|or)$", "");
        Matcher q = Pattern.compile("([A-Z][a-z]+)$").matcher(s);
        if (q.find() && s.length() > q.group(1).length()) {
            s = q.group(1);
        }
        return s.toLowerCase();
    }

    /** From the '(' at or after {@code from}, the balanced argument text. */
    static String balancedArg(String body, int from) {
        int open = body.indexOf('(', from);
        if (open < 0) return "";
        int depth = 0;
        for (int j = open; j < body.length(); j++) {
            if (body.charAt(j) == '(') depth++;
            else if (body.charAt(j) == ')' && --depth == 0) return body.substring(open + 1, j);
        }
        return "";
    }

    /** Every split-attribution site in a single compilation unit's (comment-stripped) source. */
    static List<String> splitsIn(String strippedSource) {
        List<String> found = new ArrayList<>();
        for (String[] m : methodsOf(strippedSource)) {
            String name = m[0];
            String body = m[1];
            Map<String, String> clockRoots = new LinkedHashMap<>();
            Map<String, Set<String>> callerActors = new LinkedHashMap<>();
            Matcher sm = SETTER.matcher(body);
            while (sm.find()) {
                String field = Character.toLowerCase(sm.group(1).charAt(0)) + sm.group(1).substring(1);
                String arg = balancedArg(body, sm.start());
                String root = verbRoot(field);
                if (field.endsWith("At") && isServerClock(arg, body)) {
                    clockRoots.putIfAbsent(root, field);
                } else if (ACTOR_TAIL.matcher(field).find() && REQUEST_VALUE.matcher(arg).find()) {
                    callerActors.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(field);
                }
            }
            for (Map.Entry<String, Set<String>> e : callerActors.entrySet()) {
                if (clockRoots.containsKey(e.getKey())) {
                    for (String f : e.getValue()) found.add(name + "::" + f);
                }
            }
        }
        return found;
    }

    /** (name, body) for every brace-bodied method, at any indentation. */
    static List<String[]> methodsOf(String src) {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile(
            "\\n\\s{4,}(?:public|private|protected)\\s+[^;{=]*?\\b(\\w+)\\s*\\([^;{]*?\\)"
            + "(?:\\s*throws\\s[\\w., ]+)?\\s*\\{", Pattern.DOTALL).matcher(src);
        while (m.find()) {
            int depth = 0;
            int j = m.end() - 1;
            for (; j < src.length(); j++) {
                if (src.charAt(j) == '{') depth++;
                else if (src.charAt(j) == '}' && --depth == 0) break;
            }
            out.add(new String[]{m.group(1), src.substring(m.end(), Math.min(j, src.length()))});
        }
        return out;
    }

    static String strip(String s) {
        return COMMENTS.matcher(s).replaceAll("");
    }

    private static Map<String, String> scanTree() {
        Map<String, String> hits = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src;
                try {
                    src = Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (!src.contains(".set")) continue;
                String cls = p.getFileName().toString().replace(".java", "");
                for (String hit : splitsIn(strip(src))) hits.put(cls + "#" + hit, p.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    // ─── the contract ────────────────────────────────────────────────────────

    /**
     * Sites surfaced by the CLOCK-LOCAL widening (detector gap 7) after F2-223 had already read and
     * closed its eleven. They are <b>NOT adjudicated</b>: nobody has yet read whether each is an
     * attribution to close, an assignment, or genuinely the caller's.
     *
     * <p>🔑 <b>They are a work list and deliberately NOT a map of reasons.</b> F2-188 measured what
     * happens when a slice writes a reason for something it has not read: five "sharpest bucket"
     * claims, all five false, and the fluency of the prose was what made them look verified.
     * Writing ten reasons here would produce ten plausible sentences and no knowledge.
     *
     * <p>⚠ At least one is very likely BY DESIGN — {@code VendorBcpAttestationService#doSubmit}
     * takes {@code submittedByContactName/Email} on a VENDOR PORTAL submission, where the
     * submitter is an external contact and may not be an authenticated platform principal at all.
     * That is a guess, which is exactly why it is on this list rather than in {@link #BY_DESIGN}.
     *
     * <p>Decrease-only. Read one, fix or classify it, and take it out in the same commit.
     */
    private static final Set<String> WORK_LIST = Set.of(
        "ErmGapService#attestRisk::attestedByName",
        "InvocationService#transition::declaredBy",
        "MtpdInheritanceService#publishAssessment::publishedByUserId",
        "ObligationAttestationService#recordAttestation::attestedByUserId",
        "ObligationAttestationService#reviewAttestation::reviewerUserId",
        "RegulatoryChangeService#transition::assessedBy",
        "VendorBcpAttestationService#doSubmit::submittedByContactEmail",
        "VendorBcpAttestationService#doSubmit::submittedByContactName",
        "VendorBcpAttestationService#transitionAttestation::reviewedByName",
        "VendorBcpAttestationService#transitionAttestation::reviewedByUserId");

    @Test
    @DisplayName("no NEW method stamps WHEN from the server's clock and takes WHO from the request")
    void noMethodSplitsOneAttributionBetweenTheServerAndTheCaller() {
        Set<String> found = new TreeSet<>(scanTree().keySet());
        Set<String> unexplained = new TreeSet<>(found);
        unexplained.removeAll(BY_DESIGN.keySet());
        unexplained.removeAll(WORK_LIST);

        assertThat(unexplained)
            .as("Each of these stamps a <verb>At from the server's own clock and then takes the "
                + "person who performed that same act from the request body. The clock proves the "
                + "server considers itself the recorder; take the actor from CurrentActor too. "
                + "KycService#disposition is the pattern to copy.")
            .isEmpty();
    }

    /**
     * The work list may only shrink. F2-205's rule rather than F2-204's: the QUESTION was right
     * and the ANSWER was under-measured, so the figure moves and says so — it is not re-scoped to
     * hide the growth.
     */
    @Test
    @DisplayName("the unadjudicated work list only decreases")
    void theWorkListOnlyDecreases() {
        Set<String> found = new TreeSet<>(scanTree().keySet());
        Set<String> stillOpen = new TreeSet<>(WORK_LIST);
        stillOpen.retainAll(found);

        assertThat(stillOpen.size())
            .as("F2-223 surfaced these 10 after widening the detector; each must be read and "
                + "removed, never added to. Currently open:%n  %s",
                String.join("\n  ", stillOpen))
            .isLessThanOrEqualTo(10);

        Set<String> stale = new TreeSet<>(WORK_LIST);
        stale.removeAll(found);
        assertThat(stale)
            .as("a work-list entry whose site is fixed must be deleted in the same commit — "
                + "otherwise a closed item is indistinguishable from an open one (F2-199)")
            .isEmpty();
    }

    @Test
    @DisplayName("every BY_DESIGN entry still names a real site — the reverse direction")
    void aSplitThatHasBeenFixedMustLoseItsEntryInTheSameCommit() {
        Set<String> found = new TreeSet<>(scanTree().keySet());
        Set<String> stale = new TreeSet<>(BY_DESIGN.keySet());
        stale.removeAll(found);

        assertThat(stale)
            .as("F2-199: a reason written for something that is NOT in the population reads "
                + "exactly like a reason for something that is, and the forward assertion passes "
                + "either way. If one of these has been fixed, delete its entry.")
            .isEmpty();
    }

    @Test
    @DisplayName("each surviving reason is substantial and says WHY, not just THAT")
    void noReasonIsABareLabel() {
        for (Map.Entry<String, String> e : BY_DESIGN.entrySet()) {
            assertThat(e.getValue().length())
                .as("reason for %s is too short to be a judgement (F2-188)", e.getKey())
                .isGreaterThan(140);
            assertThat(e.getValue())
                .as("reason for %s must state its verdict", e.getKey())
                .containsAnyOf("REFUTED", "FILED");
        }
    }

    /**
     * ⚠ At zero, a rotted detector and a finished sweep are indistinguishable (F2-219). Every
     * shape this slice actually fixed is replayed here as a synthetic positive; if the detector
     * stops matching any of them the hard zero above has stopped meaning anything.
     */
    @Test
    @DisplayName("the detector still fires on every shape this slice fixed")
    void theDetectorIsNotVacuous() {
        String fixture = """
            class Probe {
                public void approveById(Long id, Req req) {
                    p.setApprovedAt(LocalDateTime.now());
                    p.setApprovedByUserId(req.approverUserId());
                }
                public void reviewByName(Long id, Req req) {
                    h.setReviewedAt(LocalDateTime.now(clock));
                    h.setReviewerUserId(req.reviewerUserId());
                }
                public void executeOrAgent(Long id, Req body) {
                    d.setExecutedAt(LocalDateTime.now());
                    d.setExecutorName(body.executorName());
                }
                public void qualifiedAgent(Long id, Req req) {
                    LocalDateTime now = LocalDateTime.now();
                    t.setApprovedByLegalAt(now);
                    t.setApprovedByLegalName(req.approverName());
                }
                public void qualifiedVerb(Long id, Req req) {
                    h.setCfoApprovedAt(LocalDateTime.now());
                    h.setCfoApproverName(req.approverName());
                }
            }
            """;
        assertThat(splitsIn(strip(fixture)))
            .as("the -or agent spelling and the qualified <verb>By<Qualifier>Name form each broke "
                + "a draft of verbRoot(), and each failure SHRANK the population")
            .containsExactlyInAnyOrder(
                "approveById::approvedByUserId",
                "reviewByName::reviewerUserId",
                "executeOrAgent::executorName",
                "qualifiedAgent::approvedByLegalName",
                "qualifiedVerb::cfoApproverName");
    }

    /**
     * The negative half: correct code must not be reported. A gate that fails on correct code is
     * worse than no gate — it trains the next reader to edit the assertion rather than the code
     * (F2-214).
     */
    @Test
    @DisplayName("the detector does NOT fire on a server-stamped actor, nor on an unpaired field")
    void theDetectorDoesNotFireOnCorrectCode() {
        String fixture = """
            class Probe {
                public void serverStamped(Long id, Req req) {
                    h.setDecidedByName(CurrentActor.username());
                    h.setDecidedAt(LocalDateTime.now());
                }
                public void assignmentWithNoClockForThatVerb(Long id, Req req) {
                    c.setReviewerUserId(req.reviewerUserId());
                    c.setUpdatedAt(LocalDateTime.now());
                }
                public void differentVerbs(Long id, Req req) {
                    x.setApprovedAt(LocalDateTime.now());
                    x.setSignedByName(req.signerName());
                }
            }
            """;
        assertThat(splitsIn(strip(fixture)))
            .as("a server-stamped actor is the fix, not the defect; an actor whose verb has no "
                + "server clock is an assignment, not an attribution; and two different verbs in "
                + "one method are two different acts")
            .isEmpty();
    }

    /**
     * The complementary question, kept because the split detector is structurally blind to it:
     * where the caller supplies the clock TOO there is no asymmetry to see. A permission whose
     * ACTION names an act — {@code MODULE:APPROVE} — is the platform's own statement that it
     * controls who may perform that act, so the body may not also say who performed it.
     */
    @Test
    @DisplayName("an ACT-guarded route may not take its own actor from the request body")
    void anActGuardedRouteMayNotTakeItsOwnActorFromTheBody() {
        String cpty = read("module/cpty/service/CptyService.java");

        assertThat(cpty)
            .as("assignRating is guarded by CPTY:APPROVE — this class's own javadoc says "
                + "'deciding the rating and the limit is the credit authority's' — so the caller "
                + "IS the approver and currentUserId() is resolved on the line above")
            .contains("r.setApprovedByUserId(currentUserId())")
            .doesNotContain("setApprovedByUserId(req.getApprovedByUserId())");

        assertThat(cpty)
            .as("upsertLimit is the site the split detector cannot see: the client was minting "
                + "approvedAt in the BROWSER, so BOTH halves were the caller's")
            .contains("l.setApprovedByUserId(currentUserId())")
            .doesNotContain("setApprovedAt(req.getApprovedAt())");

        assertThat(read("module/aml/sanctions/service/SanctionsHitService.java"))
            .as("the AML:APPROVE hit review, and its audit row must quote the SAVED row rather "
                + "than the caller's claim — the trail quoting the claim is what makes a forged "
                + "attribution invisible to whoever audits it")
            .contains("h.setReviewerUserId(CurrentActor.userId())")
            .contains("saved.getReviewerUserId()")
            .doesNotContain("req.reviewerUserId()");
    }

    /**
     * The IIA annual plan is called out separately because its old form did not merely ACCEPT an
     * approver, it <b>required</b> one — the server insisting on an attribution it never verified.
     * F2-217 closed the CREATE path on this same register and so funnelled every approval through
     * this method, which had the same defect.
     */
    @Test
    @DisplayName("the annual-plan approval no longer demands a name it cannot verify")
    void theAnnualPlanApprovalResolvesItsOwnApprover() {
        String src = read("module/audit_lc/ia_plan/service/IaAnnualPlanService.java");
        assertThat(src)
            .contains("Long approver = CurrentActor.userId()")
            .contains("p.setApprovedByUserId(approver)")
            .doesNotContain("approverUserId() == null")
            .doesNotContain("setApprovedByUserId(req.approverUserId())");
        assertThat(src)
            .as("and it must still refuse to record an approval with no approver at all — "
                + "KycService#disposition's rule, which is the reason the throw is kept rather "
                + "than deleted with the field")
            .contains("An audit-plan approval with no approver is not an approval");
    }

    private static String read(String relative) {
        try {
            return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
