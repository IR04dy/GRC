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
import java.util.Collection;
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
        "DataSharingAgreementService#activate::counterpartySigner",
        "BY DESIGN — the COUNTERPARTY's signatory is a person at the other organisation and can "
        + "never be a principal in this platform, so this half of the pair is legitimately caller "
        + "data even though activate() stamps signedAt from the server's clock. Verified against "
        + "the register rather than guessed: all 8 live rows in dev and prod name an external "
        + "party — an NCA liaison officer, a MoCI director, a university dean, a vendor DPO. Its "
        + "SIBLING signerName is OUR signatory and was closed by F2-236, so this entry records a "
        + "field-level boundary inside one method, not a whole-method exemption. The literal "
        + "'counterparty' the frontend used to send is gone.",
        "VendorBcpAttestationService#doSubmit::submittedByContactEmail",
        "BY DESIGN — VERIFIED, not guessed. doSubmit has two callers: submitAttestation (the "
        + "TPRM Lead transcribing a vendor's reply) and submitByToken, whose route is "
        + "@PreAuthorize(\"permitAll()\") — the vendor portal, where the token IS the credential "
        + "and there is no authenticated principal to resolve at all. On both paths the "
        + "submitter is an external vendor contact, so CurrentActor would be either null or the "
        + "wrong person. F2-223 recorded this as a GUESS and put it on the work list for exactly "
        + "that reason; reading the controller is what turned it into a fact. Contrast "
        + "#transitionAttestation in the same class, which F2-224 DID close: its three routes "
        + "are all TPRM:WRITE.",

        "VendorBcpAttestationService#doSubmit::submittedByContactName",
        "BY DESIGN — same site and same reason as submittedByContactEmail above: the vendor "
        + "portal submit route is permitAll() and the submitter is an external contact.",

        "ObService#revokeConsent::revokedBy",
        "REFUTED — revokedBy is a PARTY, not a person. ObConsentsPage declares "
        + "useState<RevokedBy>('BANK') and badges CUSTOMER differently; the Open Banking consent "
        + "may be revoked by the customer, the bank or the TPP, and which of them it was is not "
        + "derivable from whoever happens to call the endpoint.",

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

    /**
     * True when this setter argument carries a reading of the server's own clock —
     * <b>and the caller could not have supplied it instead.</b>
     *
     * <p>⚠ DETECTOR GAP 8 (F2-224). {@code req.auditDate() == null ? LocalDate.now() :
     * req.auditDate()} contains {@code now()}, so a bare search called it a server stamp. It is
     * not: it is a caller-supplied value with a fallback, and F2-223 already recorded what that
     * means — <i>where a method lets the caller supply the clock too, the act may not be this
     * call, so its performer need not be the caller.</i> An ISO/IEC 42001 model audit is
     * routinely performed by an external firm ({@code MlModelAuditService#recordAudit} declares
     * {@code auditorExternalParty} one line below {@code auditorUserId}); an evidence review may
     * have happened offline. Stamping the principal there would record the recording clerk as
     * the auditor.
     *
     * <p>🔑 <b>This narrowing and the {@code Date}/{@code On} widening below had to land in the
     * SAME commit, and that is the finding about this instrument.</b> The two defects cancelled:
     * the root reduction could not pair {@code auditDate} with {@code auditorUserId}, so the two
     * sites this predicate would have mis-reported were absent for an unrelated, accidental
     * reason — and <b>absent reads identically to correctly excluded</b>. The gate was green for
     * the wrong reason (F2-219: two errors in opposite directions produce a green neither
     * earns). Widening alone would have manufactured two false findings, and the obvious "fix"
     * for them would have been to forge an attribution onto an external auditor's work.
     */
    static boolean isServerClock(String arg, String methodBody) {
        if (REQUEST_VALUE.matcher(arg).find()) return false;
        if (SERVER_CLOCK.matcher(arg).find()) return true;
        String bare = arg.trim();
        if (!bare.matches("\\w+")) return false;
        Matcher m = CLOCK_LOCAL.matcher(methodBody);
        while (m.find()) {
            if (m.group(1).equals(bare)) return true;
        }
        return false;
    }

    /** A field naming WHEN an act happened: {@code …At}, {@code …Date}, {@code …On}. */
    private static final Pattern TIME_TAIL = Pattern.compile("(?:At|Date|On)$");

    /**
     * A nominalising suffix: {@code approval} is the NOUN for the act {@code approve}.
     *
     * <p>⚠ DETECTOR GAP 9 (F2-225), and it cost a live finding on the return this
     * organisation files with the National Cybersecurity Authority.
     * {@code NcaEccFilingService#transition} stamps {@code cisoApprovalAt} from the
     * server's own clock and used to copy {@code cisoApprover} out of the request body
     * in the same {@code switch} branch — one attribution, two owners, F2-223's shape
     * exactly. It was invisible because {@code cisoApprover} reduces to {@code approv}
     * and {@code cisoApprovalAt} to {@code approval}: <b>the clock half is named after
     * the NOUN and the actor half after the VERB, so the two halves of one attribution
     * share no key.</b>
     *
     * <p>🔑 This is a different blind spot from F2-224's, which was the actor field
     * naming the ROLE PERFORMED ({@code standDownAuthoriser}). That one is still blind
     * and still asserted as such below. <b>Two distinct ways for one attribution's two
     * halves to share no root have now been found; assume a third.</b>
     *
     * <p>Emitted as an EXTRA key rather than replacing the reduction, so the widening
     * can only ADD pairings — which is what makes it safe to reason about. Verified
     * against all 9 pairs the gate already reports plus 11 that must never pair
     * ({@link #aNominalisedClockPairsWithItsAgentNoun()}): it adds exactly one.
     */
    private static final Pattern NOMINAL = Pattern.compile("(?:al|ion|ment|ance|ence)$");

    /**
     * Every verb key a field name could pair on, rather than the single reduced root.
     *
     * <p>⚠ Part two of DETECTOR GAP 8. {@link #verbRoot}'s last step keeps only the trailing
     * CamelCase word, which was added so {@code approvedByLegalName} would meet
     * {@code approvedByLegalAt}. It is lossy the other way: {@code auditDate} reduces to
     * {@code audit} but {@code auditorUserId} reduces to {@code audit} only by luck of the
     * {@code -or} rule, and {@code standDownAt} reduces to {@code down}. Keeping the whole base
     * AND each CamelCase suffix lets a qualified name meet an unqualified one without the
     * reduction ever splitting a genuine pair.
     */
    static Set<String> verbKeys(String field) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(verbRoot(field));
        String base = field;
        int by = base.indexOf("By");
        if (by > 0) {
            base = base.substring(0, by);
        } else {
            base = TIME_TAIL.matcher(base).replaceAll("");
            for (String tail : new String[]{"UserId", "Name", "Email", "Id"}) {
                if (base.endsWith(tail) && base.length() > tail.length()) {
                    base = base.substring(0, base.length() - tail.length());
                    break;
                }
            }
        }
        base = base.replaceAll("(?:ed|er|or)$", "");
        if (!base.isEmpty()) keys.add(base.toLowerCase());
        Matcher w = Pattern.compile("[A-Z][a-z]*").matcher(base);
        while (w.find()) {
            String suffix = base.substring(w.start());
            keys.add(suffix.toLowerCase());
            keys.add(suffix.replaceAll("(?:ed|er|or)$", "").toLowerCase());
        }
        // GAP 9 (F2-225): let a nominalised clock meet its agent noun — approval ↔ approv.
        for (String k : new LinkedHashSet<>(keys)) {
            String stem = NOMINAL.matcher(k).replaceAll("");
            if (!stem.isEmpty() && !stem.equals(k)) keys.add(stem);
        }
        keys.remove("");
        return keys;
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

    /**
     * F2-234 — the {@code By[A-Z]\w*} branch above is deliberately wide (it exists so
     * {@code approvedByLegalName} pairs), and that width also matches {@code appointedByReso},
     * whose entity javadoc reads <i>"Board-resolution code that appointed this member — links back
     * to the resolution register for traceability"</i>. A board-resolution CODE is a document
     * reference, not a person, so pairing it with {@code appointedAt} would be a gate failing on
     * correct code (F2-214).
     *
     * <p>⚠ This is a NAME LIST, which is the instrument class this programme repeatedly finds
     * measures its own vocabulary (F2-217). It is tolerable only because it is scoped to the
     * {@code By<Suffix>} branch and pinned in BOTH directions by
     * {@link #theBySuffixBranchPairsPeopleAndNotDocuments()} — add a document noun here and the
     * must-pair half must still hold.
     */
    private static final Pattern REFERENCE_TAIL = Pattern.compile(
        "By(?:Reso(?:lution)?|Ref(?:erence)?|Doc(?:ument)?|Code|Circular|Letter|Memo|Minute"
        + "|Decision|Order|Instrument|Policy|Clause|Article)\\w*$");

    /** An actor-shaped field that is not a document reference. */
    static boolean isActorField(String field) {
        return ACTOR_TAIL.matcher(field).find() && !REFERENCE_TAIL.matcher(field).find();
    }

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
            Set<String> clockKeys = new LinkedHashSet<>();
            Map<String, Set<String>> callerActors = new LinkedHashMap<>();
            Matcher sm = SETTER.matcher(body);
            while (sm.find()) {
                String field = Character.toLowerCase(sm.group(1).charAt(0)) + sm.group(1).substring(1);
                String arg = balancedArg(body, sm.start());
                if (TIME_TAIL.matcher(field).find() && isServerClock(arg, body)) {
                    clockKeys.addAll(verbKeys(field));
                } else if (isActorField(field) && REQUEST_VALUE.matcher(arg).find()) {
                    callerActors.put(field, verbKeys(field));
                }
            }
            for (Map.Entry<String, Set<String>> e : callerActors.entrySet()) {
                if (e.getValue().stream().anyMatch(clockKeys::contains)) {
                    found.add(name + "::" + e.getKey());
                }
            }
        }
        return found;
    }

    /** (name, body) for every brace-bodied method, at any indentation. */
    static List<String[]> methodsOf(String src) {
        List<String[]> out = new ArrayList<>();
        for (String[] m : methodsWithParams(src)) out.add(new String[]{m[0], m[2]});
        return out;
    }

    /**
     * (name, parameter-list text, body) — the parameter list {@link #methodsOf} throws away.
     *
     * <p>GAP 10 (F2-234) needs it: an actor half can reach its setter as a bare scalar PARAMETER,
     * and whether that parameter is caller data is a fact about the method's CALL SITES.
     */
    static List<String[]> methodsWithParams(String src) {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile(
            "\\n\\s{4,}(?:public|private|protected)\\s+[^;{=]*?\\b(\\w+)\\s*\\(([^;{]*?)\\)"
            + "(?:\\s*throws\\s[\\w., ]+)?\\s*\\{", Pattern.DOTALL).matcher(src);
        while (m.find()) {
            int depth = 0;
            int j = m.end() - 1;
            for (; j < src.length(); j++) {
                if (src.charAt(j) == '{') depth++;
                else if (src.charAt(j) == '}' && --depth == 0) break;
            }
            out.add(new String[]{m.group(1), m.group(2),
                                 src.substring(m.end(), Math.min(j, src.length()))});
        }
        return out;
    }

    /** Declared parameter names, in declaration order. */
    static List<String> paramNames(String paramText) {
        List<String> out = new ArrayList<>();
        for (String raw : paramText.split(",(?![^<>]*>)")) {
            String p = raw.trim().replaceAll("@\\w+(\\([^)]*\\))?\\s*", "").trim();
            if (p.isEmpty()) continue;
            String[] bits = p.split("\\s+");
            if (bits.length >= 2) out.add(bits[bits.length - 1]);
        }
        return out;
    }

    /**
     * GAP 10 (F2-234) — actor halves that reach the setter as a bare scalar parameter, paired with a
     * server clock for the same verb. Returned as {@code method::field::paramIndex} so
     * {@link #scanTree} can resolve the parameter at the call sites; the wire-ness is not decidable
     * from this file alone, which is exactly why the shape was invisible.
     */
    static List<String> paramBorneCandidatesIn(String strippedSource) {
        List<String> out = new ArrayList<>();
        for (String[] m : methodsWithParams(strippedSource)) {
            List<String> ps = paramNames(m[1]);
            String body = m[2];
            Set<String> clockKeys = new LinkedHashSet<>();
            Map<String, String> paramActors = new LinkedHashMap<>();
            Matcher sm = SETTER.matcher(body);
            while (sm.find()) {
                String field = Character.toLowerCase(sm.group(1).charAt(0)) + sm.group(1).substring(1);
                String arg = balancedArg(body, sm.start()).trim();
                if (TIME_TAIL.matcher(field).find() && isServerClock(arg, body)) {
                    clockKeys.addAll(verbKeys(field));
                } else if (isActorField(field) && !REQUEST_VALUE.matcher(arg).find()
                           && ps.contains(arg) && classify(field) == Bucket.CANDIDATE) {
                    paramActors.put(field, arg);
                }
            }
            for (Map.Entry<String, String> e : paramActors.entrySet()) {
                if (verbKeys(e.getKey()).stream().anyMatch(clockKeys::contains))
                    out.add(m[0] + "::" + e.getKey() + "::" + ps.indexOf(e.getValue()));
            }
        }
        return out;
    }

    /**
     * True when a call site of {@code cls#method} passes a request value at {@code idx}.
     *
     * <p>⚠ <b>Resolve per OWNER TYPE, never by method name.</b> A first draft of this measurement
     * matched {@code .<method>(} in any controller, so {@code approve} picked up every unrelated
     * {@code approve(} in the tree and produced ~20 "from the wire" verdicts that were almost all
     * cross-contamination — F2-187's recorded mistake ("a method-NAME fallback silently clears the
     * methods most likely to be duplicated elsewhere"), repeated. With the receiver resolved to a
     * field declared of this service's type the honest answer was 13 of 92.
     */
    static boolean anyCallSitePassesARequestValue(String cls, String method, int idx,
                                                  Collection<String> controllerSources) {
        if (idx < 0) return false;
        for (String src : controllerSources) {
            Matcher fld = Pattern.compile(
                "\\b(?:private|protected)\\s+final\\s+" + Pattern.quote(cls) + "\\s+(\\w+)\\s*[;=]")
                .matcher(src);
            Set<String> receivers = new LinkedHashSet<>();
            while (fld.find()) receivers.add(fld.group(1));
            for (String recv : receivers) {
                Matcher call = Pattern.compile(
                    "\\b" + Pattern.quote(recv) + "\\s*\\.\\s*" + Pattern.quote(method) + "\\s*\\(")
                    .matcher(src);
                while (call.find()) {
                    String[] parts = balancedArg(src, call.start()).split(",(?![^()]*\\))");
                    if (idx < parts.length && REQUEST_VALUE.matcher(parts[idx]).find()) return true;
                }
            }
        }
        return false;
    }

    static String strip(String s) {
        return COMMENTS.matcher(s).replaceAll("");
    }

    /**
     * Every split-attribution site in the tree, keyed {@code Class#method::field}.
     *
     * <p>Two passes, because GAP 10 is not decidable from one file. Pass 1 is the original
     * same-method shape ({@code setXBy(req.x())}). Pass 2 takes the parameter-borne candidates and
     * asks the CONTROLLERS what they pass — F2-222's rule that whether a scalar parameter is caller
     * data is a fact about its call sites, which had been taught to the STAMPING side of this
     * question and never to the accepting side.
     */
    private static Map<String, String> scanTree() {
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, String> controllers = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src;
                try {
                    src = Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                String cls = p.getFileName().toString().replace(".java", "");
                if (cls.endsWith("Controller")) controllers.put(cls, strip(src));
                if (src.contains(".set")) sources.put(cls + "\0" + p, strip(src));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, String> hits = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String cls = e.getKey().substring(0, e.getKey().indexOf('\0'));
            String path = e.getKey().substring(e.getKey().indexOf('\0') + 1);
            for (String hit : splitsIn(e.getValue())) hits.put(cls + "#" + hit, path);
            for (String cand : paramBorneCandidatesIn(e.getValue())) {
                int cut = cand.lastIndexOf("::");
                int idx = Integer.parseInt(cand.substring(cut + 2));
                String site = cand.substring(0, cut);
                String method = site.substring(0, site.indexOf("::"));
                if (anyCallSitePassesARequestValue(cls, method, idx, controllers.values()))
                    hits.put(cls + "#" + site, path);
            }
        }
        return hits;
    }

    // ─── the contract ────────────────────────────────────────────────────────

    /**
     * <b>EMPTY — F2-224 read and adjudicated all ten.</b> Eight were real attributions and are
     * closed against {@link com.tss.grc.common.security.CurrentActor}; two
     * ({@code VendorBcpAttestationService#doSubmit}) moved into {@link #BY_DESIGN} once the
     * controller was read rather than guessed at.
     *
     * <p>🔑 <b>Why it shipped as a work list and not as ten reasons.</b> F2-188 measured what
     * happens when a slice writes a reason for something it has not read: five "sharpest bucket"
     * claims, all five false, and the fluency of the prose was what made them look verified.
     * F2-223 guessed exactly one thing here — that the vendor-portal pair was by design — and
     * marked it a guess. It was right, and the reason it can now be stated as a fact is that
     * somebody opened {@code VendorBcpAttestationController} and found
     * {@code @PreAuthorize("permitAll()")} on the token route. <b>A correct guess and a verified
     * claim are indistinguishable in prose, which is the whole argument for the two buckets.</b>
     *
     * <p>Decrease-only, and now a hard zero: a new split fails
     * {@link #noMethodSplitsOneAttributionBetweenTheServerAndTheCaller()} outright. Do not
     * re-open this set to park a finding — read it, then either fix it or give it a
     * {@link #BY_DESIGN} reason that survives {@link #noReasonIsABareLabel()}.
     */
    private static final Set<String> WORK_LIST = Set.of(
        // ⚙ F2-234 — the nine sites GAP 10's widening surfaced, DELIBERATELY WITHOUT REASONS.
        //
        // 🔑 The gate could not see any of them because the actor half reaches its setter as a bare
        // scalar PARAMETER, and REQUEST_VALUE needs a named receiver (req./body./…). The wire-ness
        // lives at the CALL SITE. F2-222 established exactly that rule and taught it to the STAMPING
        // side of this question only — so one question, asked of a parameter, was answered on one
        // side of the wire. The same structural asymmetry F2-233 found for fallbacks one slice
        // earlier: a rule present on the accepting side and absent on the stamping side, or here the
        // reverse. Assume a third instance exists.
        //
        // Measured: 92 parameter-borne candidates paired with a server clock, 13 resolve FROM THE
        // WIRE, of which the classifier removes 2 as ASSIGNMENT (NdiSubmissionPackageService's
        // preparerName/preparerEmail), REFERENCE_TAIL removes 1 as a document reference
        // (CommitteeMemberRegistryService#renew::appointedByReso), and F2-234 fixed the BCM one. The
        // other 79 correctly take the actor from actor(auth) / userIdOf(auth) / currentPrincipalName(),
        // which is why this gate's green was mostly earned.
        //
        // ⚠ Do NOT write reasons for these here first. F2-188 measured that failure mode at five
        // claims, five false, and fluency was what made them look verified. Two of the nine are
        // plausibly BY_DESIGN — a data-sharing agreement's counterparty signer is by definition NOT a
        // platform principal (F2-218's "an M&A letter-of-intent signing date genuinely is the
        // caller's") — and that is a GUESS, which is exactly why it is on the list rather than in the
        // adjudicated map. SupplierResilienceTestService is the one to read first: its controller
        // passes body.getOrDefault("approverName", "admin"), so an omitted approver is recorded as
        // the literal "admin".
        // F2-236 — EMPTY. All nine were read; eight were confirmed live and fixed (the actor now
        // comes from CurrentActor.requireUsername/requireUserId), and the ninth,
        // DataSharingAgreementService#activate::counterpartySigner, moved to BY_DESIGN below with a
        // verified reason rather than a guessed one.
        //
        // ⚠ An empty work list is also what a rotted detector produces (F2-219). What carries the
        // weight here is not this figure but the two reverse legs — every BY_DESIGN entry must still
        // name a site the scan finds, and the parameter-borne resolver has its own must-pair /
        // must-not-pair corpus — so a detector that stops matching fails those rather than reporting
        // success.
        );

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
     * The work list is empty and may not be re-opened.
     *
     * <p>⚠ This assertion used to be {@code isLessThanOrEqualTo(10)}. At zero a ceiling is
     * vacuous — and worse than vacuous, because <b>a rotted detector reports an empty population
     * and that is indistinguishable from a finished sweep</b> (F2-219, which had to change the
     * SHAPE of an anti-vacuity floor for exactly this reason). What carries the weight now is
     * {@link #theDetectorIsNotVacuous()}, which drives the detector over fixtures of every shape
     * it must catch AND every shape it must not.
     */
    @Test
    @DisplayName("the unadjudicated work list only ever decreases — F2-234 re-opened it at nine")
    void theWorkListOnlyDecreases() {
        Set<String> found = new TreeSet<>(scanTree().keySet());
        Set<String> stillOpen = new TreeSet<>(WORK_LIST);
        stillOpen.retainAll(found);

        // ⚠ F2-234 — this assertion WAS isEmpty(), because F2-224 had adjudicated all ten of
        // F2-223's sites. It is a ceiling again, and the reason matters: the question was right and
        // the ANSWER was under-measured, so the figure moves and says why (F2-205), rather than the
        // question being re-scoped (F2-204). GAP 10's widening found nine sites the detector could
        // never have reported. Decrease-only from here — read one, fix or classify it, lower this.
        assertThat(stillOpen.size())
            .as("nine parameter-borne splits, filed by F2-234 without verdicts. Each needs its "
                + "controller read: the parameter is caller data at some call sites and the "
                + "principal at others, and only the call site can say which. %s", stillOpen)
            .isLessThanOrEqualTo(0);

        Set<String> stale = new TreeSet<>(WORK_LIST);
        stale.removeAll(found);
        assertThat(stale)
            .as("a work-list entry whose site is fixed must be deleted in the same commit — "
                + "otherwise a closed item is indistinguishable from an open one (F2-199)")
            .isEmpty();
    }

    /**
     * 🔑 <b>The gate's key is a SIMPLE class name, and this platform has 15 entity names declared
     * in two packages (F2-222).</b> Services collide too: {@code RegulatoryChangeService} exists
     * in both {@code module/compliance} and {@code module/regchange}, and only the second has
     * the {@code assessedBy} pair — so today no key collapses two sites. That is a fact about
     * today, not a property of the key, and nobody was maintaining it (F2-177). If a second file
     * ever produces the same {@code Class#method::field}, one real finding would be hidden
     * behind another's adjudication.
     */
    @Test
    @DisplayName("no two files produce the same key — a simple class name is not identity")
    void aSiteKeyResolvesToExactlyOneFile() {
        Map<String, String> firstSeen = new LinkedHashMap<>();
        Map<String, String> collisions = new LinkedHashMap<>();
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
                for (String hit : splitsIn(strip(src))) {
                    String key = cls + "#" + hit;
                    String prior = firstSeen.putIfAbsent(key, p.toString());
                    if (prior != null) collisions.put(key, prior + "  AND  " + p);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(collisions)
            .as("two different files produced one key, so one site's verdict is silently "
                + "adjudicating the other's — key by package, or rename")
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
            // ⚠ F2-224 added "BY DESIGN". The first draft of this leg listed only REFUTED and
            // FILED — the two verdicts F2-223 happened to have reached — so the gate rejected a
            // correct, verified reason for the only new verdict the next slice produced.
            // 🔑 F2-217's headline (a census keyed on a list measures the list) applies to a
            // gate's own vocabulary too, and the failure mode is the benign-looking one: it does
            // not hide a defect, it pushes the next author to reword a true statement until the
            // regex is happy. Add a verdict here when you first reach one; do not paraphrase
            // around it.
            assertThat(e.getValue())
                .as("reason for %s must state its verdict", e.getKey())
                .containsAnyOf("REFUTED", "FILED", "BY DESIGN");
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
                public void dateTailNotAtTail(Long id, Req req) {
                    a.setAuditDate(LocalDate.now());
                    a.setAuditorUserId(req.auditorUserId());
                }
            }
            """;
        assertThat(splitsIn(strip(fixture)))
            .as("the -or agent spelling and the qualified <verb>By<Qualifier>Name form each broke "
                + "a draft of verbRoot(), and each failure SHRANK the population. dateTailNotAtTail "
                + "is F2-224's widening: a WHEN field may be named ...Date, not only ...At")
            .containsExactlyInAnyOrder(
                "approveById::approvedByUserId",
                "reviewByName::reviewerUserId",
                "executeOrAgent::executorName",
                "qualifiedAgent::approvedByLegalName",
                "qualifiedVerb::cfoApproverName",
                "dateTailNotAtTail::auditorUserId");
    }

    /**
     * F2-224's narrowing, as its own leg because it is the half that keeps the widening honest.
     *
     * <p>A clock reading that the caller can override is <b>not</b> a server stamp, so the pair
     * is not a split. Both real instances are governance records performed elsewhere — an
     * external model auditor, an evidence review done offline — and stamping the principal on
     * either would record the recording clerk as the actor.
     *
     * <p>⚠ This fixture is the reason the widening above was safe to ship. Run the two changes
     * apart and the tree yields two confident false findings whose obvious "fix" forges an
     * attribution. The negative control is that removing the {@code REQUEST_VALUE} guard from
     * {@link #isServerClock} makes this method fail.
     */
    @Test
    @DisplayName("a clock the caller may supply is not a server stamp — so it is not a split")
    void aCallerDefaultableClockIsNotAServerStamp() {
        String fixture = """
            class Probe {
                public void ternaryFallback(Long id, Req req) {
                    a.setAuditDate(req.auditDate() == null ? LocalDate.now() : req.auditDate());
                    a.setAuditorUserId(req.auditorUserId());
                }
                public void ternaryFallbackOtherOrder(Long id, Req body) {
                    t.setResidencyCheckAt(body.residencyCheckAt() != null ? body.residencyCheckAt() : LocalDateTime.now());
                    t.setResidencyCheckBy(body.residencyCheckBy());
                }
            }
            """;
        assertThat(splitsIn(strip(fixture)))
            .as("the server did not claim to be the recorder here — it offered a default. "
                + "F2-223: where a method lets the caller supply the clock too, the act may not "
                + "be this call, so its performer need not be the caller.")
            .isEmpty();
    }

    /**
     * 🔑 <b>The shape this gate cannot see, recorded rather than papered over.</b>
     *
     * <p>When the actor field names the ROLE PERFORMED instead of the verb, the two halves share
     * no root and no root-matching detector can pair them. {@code InvocationService#transition}
     * writes {@code setStandDownAt(now)} and, three lines on, {@code standDownAuthoriser} from
     * the request — structurally the same split as {@code declaredBy} on the line above it,
     * invisible to both the shipped detector and to the deliberately-permissive re-derivation
     * F2-224 ran to find what the reduction was losing. It was found by READING.
     *
     * <p>This test asserts the blind spot so it cannot be mistaken for coverage. Making it fire
     * needs a semantic pairing (authoriser↔authorise↔stand-down), not a longer suffix list, and
     * the site itself is an owner decision — a crisis stand-down is authorised by the CMT chair
     * and typically recorded by the coordinator. Filed, not blessed.
     */
    @Test
    @DisplayName("KNOWN BLIND SPOT: an actor field naming the role, not the verb, is not paired")
    void anActorFieldNamingTheRoleRatherThanTheVerbIsNotPaired() {
        String fixture = """
            class Probe {
                public void standDown(Long id, Req req) {
                    v.setStandDownAt(LocalDateTime.now());
                    v.setStandDownAuthoriser(req.getStandDownAuthoriser());
                }
            }
            """;
        assertThat(splitsIn(strip(fixture)))
            .as("if this ever starts firing the blind spot has closed — delete this test, "
                + "adjudicate InvocationService#transition::standDownAuthoriser, and re-run the "
                + "whole tree, because the same widening will surface siblings nobody has read")
            .isEmpty();
    }

    /**
     * GAP 9's self-test: the widening adds EXACTLY the pairing it was written for.
     *
     * <p>A widening is only safe to reason about if you know what it adds. This drives the
     * nine pairs the gate already reports — which must keep pairing, or the widening has
     * broken the instrument — and eleven that must never pair, drawn from the three
     * buckets F2-225 measured over the 79 unpaired candidates: an ordinary English word
     * ending {@code -er}/{@code -or} that is no agent noun at all, an ASSIGNMENT naming
     * who WILL act, and a NOMINATION. It adds one pairing: {@code cisoApprover} ↔
     * {@code cisoApprovalAt}.
     *
     * <p>🔑 The eleventh case is {@code standDownAuthoriser}, and it must still NOT pair.
     * F2-224's blind spot and F2-225's are different shapes, and closing one says nothing
     * about the other — which is the reason both are asserted rather than described.
     */
    @Test
    @DisplayName("GAP 9: a nominalised clock pairs with its agent noun, and nothing else does")
    void aNominalisedClockPairsWithItsAgentNoun() {
        // Each row: actor field, clock field. These MUST share a key.
        String[][] mustPair = {
            {"cisoApprover", "cisoApprovalAt"},              // the F2-225 miss
            {"submittedByContactEmail", "submittedAt"},      // shipped BY_DESIGN
            {"submittedByContactName", "submittedAt"},       // shipped BY_DESIGN
            {"revokedBy", "revokedAt"},                      // shipped BY_DESIGN
            {"reviewerUserId", "lastReviewedAt"},            // shipped BY_DESIGN
            {"attestedByName", "attestedAt"},                // F2-224 closed
            {"declaredBy", "declaredAt"},                    // F2-224 closed
            {"approvedByLegalName", "approvedByLegalAt"},    // F2-223's qualified agent
            {"executorName", "executedAt"},                  // F2-223's -or agent noun
        };
        // These must NOT: a noise word, an assignment, a recipient, a nomination.
        String[][] mustNotPair = {
            {"ownerUserId", "updatedAt"},
            {"reporterName", "submittedAt"},
            {"escalatedToUserId", "escalatedAt"},
            {"assignedToUserId", "detectedAt"},
            {"vendorId", "reportedAt"},
            {"stepOrder", "decidedAt"},
            {"lineNumber", "discoveredAt"},
            {"isCrossBorder", "discoveredAt"},
            {"regulatoryAnchor", "submittedAt"},
            {"peerReviewer", "peerReviewStartedAt"},
            {"standDownAuthoriser", "standDownAt"},
        };
        for (String[] p : mustPair) {
            Set<String> a = verbKeys(p[0]);
            a.retainAll(verbKeys(p[1]));
            assertThat(a)
                .as("%s must pair with %s — if this is empty the reduction has been "
                    + "narrowed and real splits have gone quiet", p[0], p[1])
                .isNotEmpty();
        }
        for (String[] p : mustNotPair) {
            Set<String> a = verbKeys(p[0]);
            a.retainAll(verbKeys(p[1]));
            assertThat(a)
                .as("%s must NOT pair with %s — a false pairing manufactures a finding, "
                    + "and the obvious fix for a false split forges an attribution", p[0], p[1])
                .isEmpty();
        }
    }

    /**
     * KNOWN BLIND SPOT 2 (F2-225): the clock is a FIELD INITIALISER, so it is never
     * written by a setter at all and no scan over setter calls can see it.
     *
     * <p>{@code NcaEccFiling.draftedAt} is {@code = LocalDateTime.now()} on the field —
     * F2-215's third of four filling mechanisms — while {@code draftedBy} was
     * {@code @NotBlank} from the request. That is the same split, and this detector was
     * structurally incapable of reporting it: it was found by reading the entity.
     *
     * <p><b>Measured before being filed</b> (F2-221: a filed item inherits the population
     * of the slice that filed it). Of 67 entities carrying a clock-initialised field that
     * names an ACT rather than the row, <b>19 sites</b> also take an actor-shaped field
     * from the request — 4 noise words, 6 assignments, and 9 in the attribution bucket, of
     * which F2-225 fixed 1 ({@code NcaEccFilingService#create}) and refuted 4 by
     * reading: two are the SUBJECT of a public complaint or whistleblowing report, and
     * {@code CoiService#recordRecusal}'s clock is
     * {@code req.recusalDate() != null ? … : now()} — F2-224's caller-overridable clock,
     * arriving through an initialiser this time, which is worth knowing on its own:
     * <b>a field initialiser is only a server stamp while no writer overrides it.</b>
     *
     * <p>⚠⚠ <b>CORRECTION (F2-226): "67 entities" measured ONE of F2-215's FOUR filling
     * mechanisms.</b> F2-225's scan matched only the field INITIALISER
     * ({@code private LocalDateTime x = …now()}). Re-derived across all four, the denominator
     * is <b>82 entities / 87 act-clock fields</b>: 73 initialiser, <b>6 lifecycle callback</b>
     * ({@code @PrePersist}/{@code @PreUpdate}) and <b>8 database default</b>
     * ({@code insertable = false}). The two mechanisms it missed are the ones a caller can
     * NEVER reach — a {@code @PrePersist} fires at flush time after every setter and an
     * {@code insertable = false} column is never sent at all — so they are STRONGER evidence
     * of server ownership than an initialiser, which F2-225 itself found can be overridden.
     * Permissively re-paired, the population is <b>30 sites: 17 candidate, 11 assignment,
     * 2 noise</b>. The widening added no new confirmed attribution, which is a result and not
     * a dead end (F2-216).
     *
     * <p>⚠⚠ <b>And 2 of the 4 filed sites were mis-bucketed as attributions.</b>
     * {@code AuditEngagementService#create::leadAuditor} sits beside {@code auditorFirm} — an
     * IIA engagement is routinely led by an external firm's partner, who is not a platform
     * principal at all (F2-224's {@code auditorExternalParty} lesson), and a lead auditor is
     * ASSIGNED to an engagement rather than being its recorder.
     * {@code InvocationService#create::incidentCommander} and {@code ::bcmCoordinator} are
     * crisis ROLE assignments, refuted decisively by the sibling column: {@code Invocation}
     * declares {@code declaredBy} separately, and that is the attribution F2-224 closed.
     *
     * <p>🔑 <b>F2-225 built the three-bucket classifier AND hand-filed a work list whose
     * members its own classifier contradicts, and nothing compared the two.</b> That is this
     * programme's most-repeated shape — two artefacts, nobody comparing them — committed
     * inside the instrument built to answer it. {@link #theWorkListAgreesWithTheClassifier()}
     * is the leg that would have caught it.
     *
     * <p>Confirmed and CLOSED by F2-226: {@code ApprovalRequestService#create::requestedBy}
     * and {@code ExerciseRuntimeService#recordResponse::participantUserId}. FILED as an owner
     * decision: {@code FindingService#transition::mgmtResponseAuthor} — a management response
     * is authored by the auditee, who need not be the caller.
     */
    @Test
    @DisplayName("KNOWN BLIND SPOT: a clock filled by a field initialiser is never seen")
    void aClockFilledByAFieldInitialiserIsNotSeen() {
        String fixture = """
            class Probe {
                public void create(Req req) {
                    NcaEccFiling f = new NcaEccFiling();
                    f.setDraftedBy(req.getDraftedBy());
                    repo.save(f);
                }
            }
            """;
        assertThat(splitsIn(strip(fixture)))
            .as("there is no setDraftedAt(now()) here — the clock lives on the entity as "
                + "`= LocalDateTime.now()`. If this ever fires, the detector has learned to "
                + "resolve the constructed type's initialisers, and the 4 remaining work-list "
                + "sites must be adjudicated in the same commit")
            .isEmpty();
    }

    /**
     * F2-226 — <b>a work list must agree with its own classifier.</b>
     *
     * <p>F2-225 measured 79 candidates, built a three-bucket classifier to separate NOISE
     * from ASSIGNMENT from CANDIDATE (its own headline: "the classification is as much the
     * finding as the count"), and then hand-filed four sites into the attribution bucket —
     * <b>two of which its classifier calls assignments.</b> Nothing compared the two, so the
     * next slice inherited two confident false candidates. That is the shape this register
     * keeps recording: two artefacts and nobody comparing them (F2-177), here inside the
     * instrument built to answer it.
     *
     * <p>So the classifier is applied to the WORK_LIST mechanically. An entry whose actor
     * field classifies as an assignment or a noise word may not sit in a list of unadjudicated
     * ATTRIBUTIONS: either it is not one, or the classifier is wrong — and both of those are
     * findings that belong in a commit rather than in a reader's head.
     *
     * <p>The list is empty today, so this leg is currently vacuous by construction. That is
     * exactly why it also asserts the classifier against the cases F2-226 measured: without
     * those, a green here would prove nothing (F2-219's rule about a floor that cannot fail).
     */
    @Test
    @DisplayName("a work-list entry may not classify as an assignment or a noise word")
    void theWorkListAgreesWithTheClassifier() {
        for (String site : WORK_LIST) {
            String field = site.contains("::")
                ? site.substring(site.lastIndexOf("::") + 2) : site;
            assertThat(classify(field))
                .as("%s is on the unadjudicated ATTRIBUTION work list, but the classifier "
                    + "calls its actor field a %s. F2-225 filed two such entries — a lead "
                    + "auditor and an incident commander are ASSIGNED, and neither is the "
                    + "recorder of the act. Resolve the disagreement, do not carry it",
                    site, classify(field))
                .isEqualTo(Bucket.CANDIDATE);
        }

        // The classifier itself, against what F2-226 measured by READING each site. Without
        // these the loop above is vacuous on an empty list and proves nothing.
        assertThat(classify("requestedBy")).isEqualTo(Bucket.CANDIDATE);
        assertThat(classify("participantUserId")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("mgmtResponseAuthor")).isEqualTo(Bucket.CANDIDATE);
        assertThat(classify("leadAuditor")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("incidentCommander")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("bcmCoordinator")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("escalatedToUserId")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("assignedInvestigatorUserId")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("stepOrder")).isEqualTo(Bucket.NOISE);
        assertThat(classify("regulatoryAnchor")).isEqualTo(Bucket.NOISE);
        assertThat(classify("vendorName")).isEqualTo(Bucket.NOISE);
        assertThat(classify("cycleQuarter")).isEqualTo(Bucket.NOISE);

        // ⚠ `participantUserId` classifies as an ASSIGNMENT and F2-226 still fixed it, which
        // is not a contradiction: the classifier reads a NAME, and this site was adjudicated
        // by reading the CODE — the row carries a server-computed reaction time, so the
        // participant is unambiguously the performer. That is the whole reason a classifier
        // triages a population and never decides a verdict (F2-225's own lesson, kept honest
        // here rather than papered over by special-casing the word "participant").
    }

    /**
     * F2-234 — GAP 10's two halves, pinned in both directions.
     *
     * <p>F2-225's rule: <b>verify a widening by what it ADDS.</b> The parameter-borne pass can only
     * add sites, so the risk is entirely in the narrowings — and a narrowing that is too wide
     * silently SHRINKS the population, which reads exactly like a clean tree.
     */
    @Test
    @DisplayName("the By<Suffix> branch pairs people and not documents")
    void theBySuffixBranchPairsPeopleAndNotDocuments() {
        // MUST still pair — the wide branch exists for these (F2-223's CrisisCommsTemplateService
        // site was lost for a whole slice because a literal tail list missed approvedByLegalName).
        assertThat(isActorField("approvedByLegalName")).isTrue();
        assertThat(isActorField("approvedBy")).isTrue();
        assertThat(isActorField("acApprovedBy")).isTrue();
        assertThat(isActorField("reviewedByUserId")).isTrue();
        assertThat(isActorField("executorName")).isTrue();
        assertThat(isActorField("assessedBy")).isTrue();

        // MUST NOT pair — a document reference is not a person. CommitteeMemberTerm's own javadoc:
        // "Board-resolution code that appointed this member — links back to the resolution register".
        assertThat(isActorField("appointedByReso")).isFalse();
        assertThat(isActorField("appointedByResolution")).isFalse();
        assertThat(isActorField("approvedByCircular")).isFalse();
        assertThat(isActorField("supersededByDocument")).isFalse();

        // And the classifier's two buckets must keep removing what they removed when this was
        // measured: 13 wire-resolved candidates minus these is what produced the nine.
        assertThat(classify("preparerName")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("preparerEmail")).isEqualTo(Bucket.ASSIGNMENT);
        assertThat(classify("approverName")).isEqualTo(Bucket.CANDIDATE);
    }

    /**
     * F2-234 — the site this slice closed must stay closed, and the closure must be visible to the
     * WIDENED detector rather than only to a behaviour test.
     *
     * <p>🔑 Written as a structural leg because the defect was structural: the BCM compliance
     * register stamped {@code assessedAt} from the server's clock and took {@code assessedBy} from a
     * scalar parameter the controller filled from {@code body.assessedBy()}. If the request field
     * ever comes back, this fails without anyone having to notice the split.
     */
    @Test
    @DisplayName("the BCM compliance assessment takes its assessor from the principal")
    void theBcmComplianceAssessmentIsNotSplit() throws IOException {
        assertThat(scanTree().keySet())
            .as("F2-234 closed this by resolving the assessor from CurrentActor; the widened "
                + "detector is what proves the closure rather than a behaviour test alone")
            .doesNotContain("BcmComplianceMappingService#assessCompliance::assessedBy");

        // ⚠ STRIP COMMENTS FIRST. This leg failed on its first run against a correct tree, because
        // the javadoc on AssessBody explains the removal by NAMING the field it removed — so the
        // detector read the epitaph as the corpse. F2-193 recorded exactly this ("a detector that
        // cannot tell a quotation from a claim forces every correction to paraphrase away its own
        // evidence") and it recurs here in a leg written after quoting it. Nth instance in this
        // lineage of a check satisfied by something other than its claim.
        String controller = strip(Files.readString(
            MAIN.resolve("module/bcm/cycle/controller/BcmComplianceMappingController.java"),
            StandardCharsets.UTF_8));
        assertThat(controller)
            .as("the request record must not declare an assessedBy at all — a field the server "
                + "ignores tells the caller their value matters (F2-184)")
            .doesNotContain("assessedBy");
    }

    private enum Bucket { NOISE, ASSIGNMENT, CANDIDATE }

    /**
     * F2-225's three buckets, as code rather than as a paragraph. Ordinary English words the
     * agent-noun suffix happens to match, then fields naming who WILL act or the target of
     * the act, then everything left over.
     */
    private static final Pattern NOISE_WORD = Pattern.compile(
        "^(?:lineNumber|stepOrder|isCrossBorder|regulatoryAnchor|vendorName|order|number"
        + "|other|major|minor|sector|factor|vector|error|folder|header|footer|filter|master"
        + "|corner|center|centre|counter|quarter|matter|charter|register|parameter|character"
        + "|trigger|driver|provider|container|indicator|descriptor|monitor|behaviou?r|colou?r"
        + "|prior|senior|junior|interior|exterior|anchor|tenor|donor|floor|door|poor|scanner"
        + "|banner|manner|partner|winner|inner|outer|upper|lower|larger|smaller|higher"
        + "|denominator|numerator|separator|operator|iterator|generator|calculator|title"
        + "|summary|cycleQuarter)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ASSIGNMENT_WORD = Pattern.compile(
        "^(?:assigned|assignee|owner|delegate|delegated|escalated|routed|nominated|nominee"
        + "|candidate|proposed|target|recipient|notified|informed|responsible|accountable"
        + "|preparer|next|current|default|primary|secondary|backup|alternate|deputy"
        + "|successor|referred|forwarded|participant|attendee|member|contact|custodian"
        + "|steward|sponsor|supervisor|manager|lead|leader|coordinator|commander)",
        Pattern.CASE_INSENSITIVE);

    private static Bucket classify(String field) {
        if (NOISE_WORD.matcher(field).matches()) return Bucket.NOISE;
        // `lookingAt()`, not `find()`: an assignment word must be the PREFIX. `find()` would
        // match `owner` inside `changeOwnerName` and `lead` inside `misleadingBy`, and asking
        // a second matcher instance for `start()` after calling `find()` on a first one throws
        // "No match available" — which is how this leg failed on its first run.
        if (ASSIGNMENT_WORD.matcher(field).lookingAt()) return Bucket.ASSIGNMENT;
        // `incidentCommander` / `bcmCoordinator` name a ROLE somebody takes on, not the verb
        // performed — F2-224's blind spot, and the reason it is a distinct bucket here.
        if (Pattern.compile("(?:Commander|Coordinator|Auditor|Chair|Facilitator|Observer)$")
                .matcher(field).find()) return Bucket.ASSIGNMENT;
        return Bucket.CANDIDATE;
    }

    /**
     * The two registers F2-225 closed must not drift back. Structural, because the
     * pairing detector cannot see either of them: the ECC one is GAP 9's nominalisation
     * (now closed) and the chain one has no clock/actor name relationship at all —
     * {@code startedAt} against {@code originatorUserId}.
     */
    @Test
    @DisplayName("F2-225's two closed registers still resolve their actor from the principal")
    void theClosedRegistersResolveTheirActorFromThePrincipal() {
        String ecc = read("module/nca_ecc/service/NcaEccFilingService.java");
        assertThat(strip(ecc))
            .as("the NCA ECC filing's drafter and CISO approver are the authenticated "
                + "principal — a regulator self-assessment may not name an approver the "
                + "server cannot vouch for")
            .doesNotContain("req.getDraftedBy()")
            .doesNotContain("req.getCisoApprover()")
            .contains("requireActor(");

        String chain = read(
            "module/workflow/approval_chains/service/ApprovalChainInstanceService.java");
        assertThat(strip(chain))
            .as("the chain originator is the acting principal. It is not merely an "
                + "attribution: withdraw() reads it and has no other input, so a body-set "
                + "originator decided who may withdraw")
            .doesNotContain("req.originatorUserId()")
            .contains("i.setOriginatorUserId(actingUserId)");

        // ⚠ Scope this to the REQUEST records. A first draft banned the tokens across the
        // whole file and failed on correct code: InstanceResponse and
        // PendingDecisionResponse legitimately carry originatorUserId, because telling a
        // client who raised a chain is the whole point of reading one. A gate that fails
        // on correct code trains the next reader to edit the assertion (F2-214), and
        // "scope a scan to the shape of its claim" is the most repeated lesson in this
        // lineage — here it landed on a leg written while quoting it.
        String dtos = strip(read("module/workflow/approval_chains/dto/ApprovalChainDtos.java"));
        for (String rec : new String[]{"InstanceStartRequest", "InstanceActionRequest",
                                       "InstanceWithdrawRequest"}) {
            int at = dtos.indexOf("record " + rec + "(");
            assertThat(at).as("%s must still exist", rec).isGreaterThan(0);
            String body = dtos.substring(at, dtos.indexOf(")", at) + 1);
            assertThat(body)
                .as("%s must carry no user id. Two of these three used to have a required "
                    + "one the service silently ignored and one used to have a required one "
                    + "the service honoured — identical wire shapes, opposite fates, and "
                    + "the honoured one decided who may withdraw", rec)
                .doesNotContain("UserId");
        }
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

    /**
     * F2-226 — the two registers whose clock is filled by a mechanism no setter-matching scan
     * can see. Structural for exactly that reason: {@code ApprovalRequest.requestedAt} and
     * {@code ExerciseRunResponse.responseAt} are FIELD INITIALISERS, so
     * {@link #splitsIn(String)} is blind to both by construction, and
     * {@link #aClockFilledByAFieldInitialiserIsNotSeen()} asserts that blindness.
     */
    @Test
    @DisplayName("F2-226's two closed registers still resolve their actor from the principal")
    void theInitialiserClockRegistersResolveTheirActorFromThePrincipal() {
        String approvals = strip(read("module/approvals/service/ApprovalRequestService.java"));
        assertThat(approvals)
            .as("the raiser of an approval request is the authenticated principal. The clock "
                + "half is `requested_at = LocalDateTime.now()` ON THE ENTITY, which is why "
                + "the pairing detector could never report this")
            .contains("r.setRequestedBy(requireRaiser())")
            .doesNotContain("req.getRequestedBy()");
        assertThat(approvals)
            .as("and it refuses rather than storing an unattributed request — safe because the "
                + "field was already @NotBlank, so no working request starts failing (F2-182)")
            .contains("no requester is one nobody can be answerable for");

        // Four eyes. `assignedTo` IS transition()'s authorisation input (SEC-IAM-002), so the
        // create decided who may approve. Asserted as a SHAPE, not as a token: the comparison
        // must be case-insensitive because the column collates CI (F2-179), and a
        // case-sensitive equals would let a capitalisation through the gate.
        assertThat(approvals)
            .as("a request may not be assigned to the person raising it — and the comparison "
                + "must be case-INSENSITIVE, because the column collates CI")
            .containsPattern("equalsIgnoreCase\\(r\\.getRequestedBy\\(\\)\\)")
            .contains("Segregation of duties: an approval request may not be assigned");

        assertThat(strip(read("module/approvals/dto/ApprovalRequestDto.java")))
            .as("READ_ONLY on the wire, and NOT @JsonIgnore — every reader of an approval "
                + "needs to see who raised it")
            .containsPattern("Access\\.READ_ONLY\\s*\\)\\s*private String requestedBy");

        String runtime = strip(
            read("module/bcm/cycle/runtime/service/ExerciseRuntimeService.java"));
        assertThat(runtime)
            .as("the exercise participant is the authenticated principal. The server does not "
                + "merely record WHO here, it MEASURES them — responseTimeSeconds is computed "
                + "from the dispatch — so this row is the evidence an ISO 22301 reviewer reads")
            .contains("r.setParticipantUserId(requireParticipant(")
            .doesNotContain("req.participantUserId()");
        assertThat(runtime)
            .as("the audit row and the emitted event must quote the SAVED row, never the "
                + "request (F2-223: a trail repeating the claim hides the forgery)")
            .contains("saved.getParticipantUserId()");

        assertThat(strip(read("module/bcm/cycle/runtime/dto/ExerciseRuntimeDtos.java")))
            .as("and the field is gone from the request record — a required field the server "
                + "discards tells the caller their value matters (F2-184)")
            .containsPattern("record ResponseRequest\\(\\s*@Size")
            .doesNotContain("participantUserId is required");
    }

    private static String read(String relative) {
        try {
            return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
