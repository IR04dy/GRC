package com.tss.grc.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
     * {@link #BASELINE_CONTRADICTED_SERVICES}, which is why that figure survives as a work list
     * rather than being replaced by this map.
     *
     * <p>⚠⚠ <b>F2-231 re-keyed this map from a SERVICE to a {@code Service::Type#field}.</b> A
     * per-service exemption cannot say "closed except for this one field, for this reason", so the
     * moment a CLOSED service turned out to have one genuinely-by-design field the model forced a
     * false choice: drop the whole service out of the hard zero, or refuse a correct design. That is
     * F2-185b's headline one level up — <b>the granularity of the verdict has to match the
     * granularity of the finding</b> — and it also means a service already carrying an exemption no
     * longer hides a NEW contradiction on a different field of the same type.
     */
    private static final Map<String, String> BY_DESIGN = Map.ofEntries(
        // F2-236 — this entry exists because F2-236's OWN FIX made the site visible to this gate:
        // markUnderReview() now stamps reviewerName from the principal, which turned update()'s
        // acceptance of the same field into a contradiction this gate could report for the first
        // time. 🔑 Closing a split at the guarded route makes the ordinary update route a bypass of
        // the same attribution, and only the OTHER gate can see that — two instruments in series.
        //
        // The verdict is BY DESIGN per field and per window, not per method. reviewer_name carries
        // TWO meanings: before the review it is an ASSIGNMENT (measured, dev = prod: 8 of 16 live
        // rows name an office — "Chief Data Officer" — with reviewed_at NULL, so no review has
        // occurred), and from markUnderReview() onwards it is the ATTRIBUTION of who reviewed.
        // update() therefore keeps the assignment and REFUSES a change once reviewedAt is set, with
        // an echo of the stored value still accepted so a form that round-trips the row keeps
        // working (F2-231). Refusing assignment outright would be a gate driving a fix that fails on
        // correct code (F2-214); tolerating the change would have made the F2-236 fix pointless.
        Map.entry("NdiMaturityAssessmentService::NdiMaturityAssessment#reviewerName",
        "reviewer_name is an ASSIGNMENT before the review and the ATTRIBUTION after it. "
        + "markUnderReview() stamps it from the authenticated principal beside reviewedAt; update() "
        + "may still set it while reviewedAt IS NULL — which is what 8 of 16 live rows in both "
        + "databases are, an office name with no review recorded — and throws on any CHANGE once "
        + "reviewedAt is stamped, accepting an echo of the stored value. So the caller owns the "
        + "field only in the window where it does not mean who reviewed."),

        Map.entry("NdiSettingService::NdiSetting#valueText",
        "resetToDefault() restores value_text FROM THE ROW's own default_value, which is a server "
        + "value only because the row already holds it. Supplying the value is the entire point of "
        + "creating a setting; there is no decision, no attribution and no state being reached."),

        // F2-234 — both BCM CAPA sites, and the narrowing that keeps this gate off correct code.
        Map.entry("BcmRemediationService::BcmCorrectiveAction#progressPercent",
        "transitionCapa() stamps progressPercent = 100 on CLOSE, but that is a COMPLETION side "
        + "effect, not ownership of the field: the rest of the time it is the action owner's own "
        + "progress report, validated 0..100 by both createCapa() and updateCapa() and defaulted to "
        + "0 on create. Reporting progress is exactly what the owner of a corrective action does, so "
        + "refusing it would be a gate failing on correct code (F2-214) — and the completion stamp "
        + "is still unreachable from either write path, because F2-234 made the create land at OPEN "
        + "and updateCapa() refuses to edit a CLOSED CAPA at all. The dangerous halves of the same "
        + "intersection WERE closed: status now goes through ServerOwnedState.requireInitial and "
        + "closureDate is cleared on create."),
        Map.entry("SgiCommitmentService::SgiCommitment#currentValue",
        "refreshValue() pulls current_value from a linked ESG_METRIC or GHG_TOTAL source, and gates "
        + "on value_source != MANUAL. For a MANUAL commitment the class javadoc states that a human "
        + "owns the number, so create/update writing it is the declared design. The APPROVE-guarded "
        + "achieve() reads it, and is defended separately: Rule C refuses an achiever who is the "
        + "last editor, and Rule D requires attached evidence."),

        Map.entry("SgiCommitmentService::SgiCommitment#currentValueAsOf",
        "The as-of date of the number above, and it travels with it: for a MANUAL commitment the "
        + "human supplying current_value is the only source of when that value was true. "
        + "refreshValue() stamps both together for a LINKED commitment, which is why the pair "
        + "appears here as a pair rather than one field with a clock the caller must not name."),

        Map.entry("NdiComplianceAssessmentService::NdiComplianceAssessment#complianceStatus",
        "update() is a null-guarded partial update on the same NDI:WRITE authority as setStatus() "
        + "and attachEvidence(); recording compliance_status is what an assessor does on this "
        + "register. The approval that IS guarded is lifecycle_status, and both paths refuse an "
        + "APPROVED row outright. It additionally goes through ClosedVocabulary#require on both "
        + "routes, so the vocabularies cannot diverge."),

        Map.entry("NdiComplianceAssessmentService::NdiComplianceAssessment#evidenceStatus",
        "The sibling of the field above, on the same route, under the same NDI:WRITE authority and "
        + "the same refusal for an APPROVED row: recording whether the evidence for a control is "
        + "present is the assessor's own observation, not the outcome of a guarded decision."),

        // ── F2-231 — read in this slice, both halves, both services ────────────────────────
        Map.entry("TcfdItemService::TcfdItem#disclosureStatus",
        "The entity declares `= \"UNDER_REVIEW\"` as a FIELD INITIALISER, so an omitted key "
        + "deserialises to that value and update()'s null-guard cannot tell absence from a real "
        + "UNDER_REVIEW. EsgTcfdTab therefore echoes the current status back on every save and says "
        + "so in a comment; refusing the field would silently downgrade every reviewer's "
        + "classification, and refusing it on create would break a form that sends it explicitly. "
        + "What F2-231 DID fix is the part that was wrong: update() now routes a CHANGED status "
        + "through markReviewed(), the one writer of the (status, last_reviewed_at) pair that "
        + "transition() uses, and logs a TRANSITION row naming from and to — so the two routes no "
        + "longer differ on the review clock and the audit trail (F2-188/F2-191's shape). An echo is "
        + "not a review and is left alone."),

        Map.entry("CmaDisclosureService::CmaDisclosure#disclosureStatus",
        "The same field, the same initialiser, the same echoing tab comment, and the same F2-231 fix "
        + "in the service TcfdItemService's javadoc says it mirrors. Recorded separately rather than "
        + "as one entry for both, because a shared reason is how two registers drift while looking "
        + "reviewed — and CMA's create additionally carries the SEC-ESG-003 scrub that the TCFD "
        + "mirror was missing, which is what F2-231 found."));

    /**
     * Fields this gate reports that are ALREADY closed, at a layer it cannot see.
     *
     * <p>⚠⚠ F2-233 — <b>a fourth wire-closing mechanism, and the strongest of the four.</b> This class
     * subtracts {@code BaseEntity}'s nine ({@link #CLOSED_AT_THE_WIRE}, F2-218) and per-field
     * {@code @JsonProperty(READ_ONLY)} ({@link #readOnlyOnTheWire}, F2-219). Neither knows about a
     * create whose ENDPOINT binds a purpose-built request record and hands the service an entity it
     * built itself: {@code SustainabilityAnnexController} binds {@code ReportingYearCreateRequest} and
     * calls {@code service.createYear(req.toEntity())}, so the service's parameter is a
     * {@code ReportingYear} and a service-layer scan can only conclude that the create accepts every
     * column. A field the bound type does not declare can never arrive — which makes this stronger than
     * an annotation, not weaker.
     *
     * <p>🔑 F2-215's headline for the fifth time on this class: <b>generalising an exclusion one
     * mechanism at a time leaves the remainder looking covered, because the mechanisms that cover them
     * are visibly present.</b> A reader who finds two subtractions here does not ask what the third is.
     *
     * <p>The other member is a RETIRED ROUTE, which is the same shape one level out: the code is
     * reachable from no endpoint at all, so there is nothing for the create to accept.
     *
     * <p>Each entry is verified mechanically, never by its prose, by
     * {@code everyClosedElsewhereEntryNamesSomethingThatReallyClosesIt} — reflection over the request
     * record's components, or the absence of a write mapping on the controller. An exclusion nobody can
     * audit is an assumption wearing a citation.
     */
    private static final Map<String, String> CLOSED_ELSEWHERE = Map.ofEntries(
        Map.entry("SustainabilityReportService::ReportingYear#approvedAt",
        "CLOSED AT THE REQUEST DTO. POST /api/v1/sust/annex/years binds ReportingYearCreateRequest — a "
        + "validated record of seven components (year, periodStart, periodEnd, reportingEntityEn/Ar, "
        + "preparedBy, notes) — and the controller calls service.createYear(req.toEntity()). The record "
        + "declares no approvedAt, so the field cannot arrive however the service is written. Its own "
        + "javadoc states the rule: \"Status + approval / publish stamps + computed readiness/gap counts "
        + "are server-owned and NOT bindable (a year can never be born APPROVED/PUBLISHED)\". Adding a "
        + "scrub in the service would be decoration over a field the wire cannot supply, and an inert "
        + "construct is worse than none because it answers the question a reader was about to ask "
        + "(F2-184/F2-219)."),

        Map.entry("SustainabilityReportService::ReportingYear#publishedAt",
        "The sibling of the field above, closed by the same absence on the same bound record, and "
        + "recorded separately because a shared entry is how one of a pair later drifts while the other "
        + "still reads as covered (F2-215's shape, which is what put this whole family on the register). "
        + "Note what is NOT claimed here: ReportingYear#status is stamped by transitionYear() through a "
        + "SCALAR PARAMETER, so it never enters the stamped set at all and this gate cannot see it "
        + "either way — F2-220's filed transition-by-parameter blind spot, not a verdict."),

        Map.entry("PolicyService::Policy#approvedAt",
        "RETIRED ROUTE. RegChangePolicyController's write mappings for /policies were removed at CH-6 / "
        + "PM-C1 — the comment where they were says policy authoring lives solely on the canonical "
        + "register (POST/PUT/DELETE /api/policy/documents + /transition) and that only list/get/stats "
        + "remain — so PolicyService#create is reachable from no endpoint. DarkServiceMethodTest holds "
        + "all nine of its write-lifecycle methods with that reason, and the whole legacy service exits "
        + "at CH-9. Adding a guard to a class scheduled for deletion is code on a corpse; a verdict is "
        + "the honest artefact."),

        Map.entry("PolicyService::Policy#legalReviewedAt",
        "Same retired route, same CH-9 exit, recorded per field so a future re-routing of the legacy "
        + "register cannot silently inherit four verdicts written for a class nobody could reach. If "
        + "these endpoints ever come back, every one of these entries must be re-adjudicated — which is "
        + "exactly what the reverse leg forces, because the scan will still find the fields."),

        Map.entry("PolicyService::Policy#publishedAt",
        "Same retired route (CH-6 / PM-C1, exits CH-9). Worth stating plainly that this is a "
        + "reachability verdict and NOT a claim that the create is correct: it accepts four transition "
        + "stamps and would be a live defect the moment a route pointed at it. Reachability is a fact "
        + "about today, not a property of a control (F2-182)."),

        Map.entry("PolicyService::Policy#retiredAt",
        "Same retired route. The fourth of the four, listed rather than folded in, because the point of "
        + "a per-field key is that a verdict covers exactly what it was written for (F2-231's lesson "
        + "about a per-service key hiding every other field of the same type)."));

    /**
     * Fields the caller must NOT own, which cannot be closed without an owner decision.
     *
     * <p>⚠⚠ F2-233 — this bucket exists because filing them under {@link #BY_DESIGN} would <b>assert
     * they are correct</b>, and they are not. F2-197's rule one level up: an invented figure invites a
     * question, an invented all-clear closes one. A verdict vocabulary limited to "the caller legitimately
     * owns this" cannot express "the caller must not own this and closing it rewrites live rows", so the
     * defect would have been recorded as a design.
     *
     * <p>🔑 F2-224's lesson, which landed on this gate's own verdict vocabulary once already: <b>a verdict
     * must NAME ITS EVIDENCE, not be one of the verdicts an earlier slice happened to reach.</b>
     */
    private static final Map<String, String> FILED_OWNER_DECISION = Map.ofEntries(
        Map.entry("PrivacyService::DataBreach#status",
        "FILED — OWNER DECISION. closeBreach()/containBreach() stamp this and createBreach() accepts it, "
        + "so a PDPL Article 20 breach can be filed already CLOSED. It is NOT fixed here because the "
        + "column has no CHECK, no declared state machine and seven stored values across dev and prod "
        + "(F2-209 filed the vocabulary itself), so naming a creatable set is a decision about live rows "
        + "in two real databases — F2-183/F2-196's owner boundary, not a side effect of closing a stamp. "
        + "F2-231 closed the four SDAIA stamps on the same create and left this for the same reason."),

        Map.entry("PrivacyService::DataSubjectRequest#status",
        "FILED — OWNER DECISION. Same shape on the Article 23 register: fulfilDsr()/rejectDsr()/"
        + "startDsr() stamp it, createDsr() accepts it. DsrRequestLifecycle declares only a TERMINAL "
        + "set, so requireInitial has no initial state to enforce and would refuse the NEW/OPEN/PENDING "
        + "creates the register already holds (F2-220's measured lesson, where the same reasoning made "
        + "requireCreatable necessary for the authority-document library). Note that dueDate — the "
        + "Article 4 statutory deadline, and the sharper half of F2-231's filing — is no longer reported "
        + "by this gate at all: startDsr() only supplies it as a FALLBACK, so under F2-233's narrowing "
        + "it is correctly not a stamp. The filing stands on F2-202's rule (only the server may issue a "
        + "statutory deadline) and does not depend on this instrument."));

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
     *
     * <p>⚠⚠ <b>F2-231 raised this from 0 to 13, and the reason is the point.</b> The figure was a hard
     * zero over a population {@code isGuardedRoute} could see 280 of 409 members of — so it was
     * green over three real contradictions that F2-218/219/220 never had a chance to adjudicate.
     * Widening the predicate moved the ANSWER, not the question, so the number moves and says why
     * (F2-205's rule, and the opposite of F2-204's, where a ratchet's question was wrong and it was
     * re-scoped instead). It stays decrease-only and may never be raised again for any other reason.
     *
     * <p>⚙ <b>All five sites the widening exposed were adjudicated in F2-231 itself</b> — and the
     * gate's own output said <b>five in three services</b> where my prose had said three, which is why
     * the run is the authority and not the note. Two were REFUTED by narrowing the detector
     * ({@code NdiImprovementPlanService#addItem/#updateItem} :: {@code sequenceOrder} — see
     * {@link #IDENTITY_ACCESSORS}), one was FIXED and probed live
     * ({@code PolicyManagementService#createException}), and two are BY DESIGN with the residual
     * defect inside them fixed ({@code TcfdItemService#create/#update} :: {@code disclosureStatus}).
     *
     * <p>13 → 11, re-derived from the gate's own printed figure rather than subtracted from the number
     * that was here (F2-207). The remaining eleven are a WORK LIST with <b>no verdicts</b> (F2-188):
     * read the service, record CLOSED / INERT / BY DESIGN, lower this in the same commit. Do not write
     * reasons for them here first — that is the failure mode F2-188 measured at five claims, five false.
     *
     * <p>⚙ <b>F2-233: 11 → 4, and roughly half of that came from the INSTRUMENT rather than from
     * fixes.</b> All eleven were read. Four detector narrowings removed fields no guarded route owns —
     * a comma in {@link #readOnlyOnTheWire}'s type-token class, {@code instanceof} pattern variables,
     * lambda parameters, and fallbacks/derivations over a field the same method also accepts — which
     * alone retired {@code NdiGapService} and {@code ProcessorAssessmentService} and cut
     * {@code DpiaService} from five reported fields to two. Two live creates were fixed and probed
     * ({@code DpiaService}, {@code RegulatoryChangeService}); three services got verdicts in the two new
     * buckets ({@code SustainabilityReportService}, {@code PolicyService},
     * {@code PrivacyService}).
     *
     * <p>🔑 <b>Roughly a third of what an instrument like this reports is the instrument.</b> The
     * corrected figure is not "we fixed seven services", and saying so would be the more flattering and
     * less true story — F2-228's headline, where two detector errors in opposite directions produced a
     * green neither earned.
     *
     * <p>⚙ <b>F2-234: 4 → 0, and the work list is EMPTY for the first time.</b> All four BCM services
     * were read and probed. Three are gone from the population outright — {@code BcmService}'s legacy
     * plan create and update, {@code BcmComplianceMappingService}'s create, and
     * {@code BcpPlanService}'s {@code currentVersion} and {@code addSection} edit stamps. The fourth,
     * {@code BcmRemediationService}, keeps a {@link #BY_DESIGN} verdict for {@code progressPercent}
     * while the dangerous halves of the same intersection ({@code status}, {@code closureDate}) were
     * closed.
     *
     * <p>⚠ <b>An empty work list is exactly what a rotted detector also produces</b> (F2-219: an
     * anti-vacuity floor of {@code >= BASELINE - n} can never fail once the baseline is 0). What keeps
     * this honest is not the number but the three reverse legs — every {@link #BY_DESIGN},
     * {@link #CLOSED_ELSEWHERE} and {@link #FILED_OWNER_DECISION} entry must still name something the
     * scan finds, so a detector that stops matching fails 15 assertions rather than reporting success.
     * Do not "simplify" those away on the grounds that the count is zero.
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

    record Method(String name, String params, String body) { }

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
        return aliasesCallerData(value, body, requestLocals, paramNames, new LinkedHashSet<>());
    }

    /**
     * ⚠⚠ F2-233 — the resolver was ONE HOP and the house idiom is TWO, so it could not see the
     * commonest way caller data reaches a setter in this platform.
     *
     * <p>Every {@code /{id}/action} route whose payload is a {@code Map<String, Object> body} writes
     * its fields like this:
     * <pre>  Object lvl = body.get("impactLevel");
     *  if (lvl instanceof String s) e.setImpactLevel(s);</pre>
     * so the value at the setter is {@code s}, bound by an {@code instanceof} PATTERN — <b>which is
     * not an assignment at all</b> — to a local which is itself an alias of the request. The old
     * resolver looked for {@code s = …} and found nothing, so the caller's own value read as a server
     * stamp. Measured 2026-08-18: <b>12 setter sites across 6 services</b>, including
     * {@code Dpia#conclusion} and {@code #rejectionReason} (the substance of a PDPL Article 25
     * assessment) and {@code RegulatoryChange#impactLevel}/{@code #impactSummary}.
     *
     * <p>🔑 <b>The direction is what makes it serious: a value the caller supplied, read as a value
     * the server owns, reports a create that legitimately accepts the field as contradicting itself</b>
     * — a gate failing on correct code (F2-214), inflating the very work list this gate publishes.
     * F2-219 named four ways caller data reaches a setter and every one is visibly handled here, which
     * is exactly why nobody asked what the fifth was (F2-215's headline, third time on this class).
     *
     * <p>Now resolves transitively over both bindings, with a {@code seen} set so an
     * {@code a = b; b = a;} cycle terminates, and over <b>every identifier in the value</b> rather
     * than only a value that IS one — because {@code e.setControlsUpdatedCount(num.intValue())} is a
     * call ON caller data and the old "must be a bare identifier" rule could not see it either.
     *
     * <p>⚠ The widening is bounded on purpose. It resolves only the <b>immediate operand</b> of
     * {@code instanceof} — an identifier or one simple call — where a first draft took 160 characters
     * of preceding context and would have matched a {@code body} mentioned anywhere in the condition.
     * Its error direction is the opposite of the bug it fixes (a real stamp read as caller data, which
     * HIDES a defect), so it must not guess: a pure server value mentions no caller-bound local, and
     * anything that does mention one is caller-influenced by construction.
     */
    private static boolean aliasesCallerData(String value, String body, Set<String> requestLocals,
                                             Set<String> paramNames, Set<String> seen) {
        Matcher id = Pattern.compile("\\b[a-z_]\\w*\\b").matcher(value);
        while (id.find()) {
            String local = id.group();
            if (!seen.add(local)) continue;
            if (bindsCallerData(local, body, requestLocals, paramNames, seen)) return true;
        }
        return false;
    }

    /**
     * Is {@code local} bound — by assignment or by an {@code instanceof} pattern — to caller data?
     *
     * <p>⚠⚠ F2-233 — <b>the recursion must stop at a store read, and my own gate's reverse leg is the
     * only thing that said so.</b> Following every identifier reached {@code c} in
     * {@code SgiCommitment c = get(id);} and {@link #callerSupplied} answers TRUE for {@code get(id)}
     * because {@code id} is a parameter — so a row LOADED FROM THE DATABASE read as caller data, and
     * {@code refreshValue}'s {@code c.setCurrentValue(sv.value())} stopped counting as a stamp.
     * {@code everyByDesignEntryStillDescribesSomethingTheScanFinds} failed, naming
     * {@code SgiCommitment#currentValue}.
     *
     * <p>🔑 <b>The id is the caller's; the row it names is the server's.</b> And the error direction is
     * the opposite of the bug this widening fixes — a real stamp read as caller data HIDES a
     * contradiction rather than inventing one — which is exactly why the leg that caught it is the
     * reverse one, the direction nobody writes first (F2-199). A shrinking population is not progress.
     */
    private static boolean bindsCallerData(String local, String body, Set<String> requestLocals,
                                          Set<String> paramNames, Set<String> seen) {
        Matcher a = Pattern.compile(
            "\\b(?:[A-Za-z_][\\w<>,.\\[\\] ]*\\s+)?" + Pattern.quote(local) + "\\s*=([^;]*);")
            .matcher(body);
        while (a.find()) {
            String rhs = a.group(1).trim();
            if (loadsARow(rhs)) continue;
            if (callerSupplied(rhs, requestLocals, paramNames)) return true;
            if (aliasesCallerData(rhs, body, requestLocals, paramNames, seen)) return true;
        }
        Matcher pv = Pattern.compile(
            "([\\w.]+(?:\\s*\\(\\s*[^()]*\\))?)\\s*\\binstanceof\\b\\s+[\\w<>\\[\\].]+\\s+"
            + Pattern.quote(local) + "\\b").matcher(body);
        while (pv.find()) {
            String tested = pv.group(1);
            if (loadsARow(tested)) continue;
            if (callerSupplied(tested, requestLocals, paramNames)) return true;
            if (aliasesCallerData(tested, body, requestLocals, paramNames, seen)) return true;
        }
        return bindsCallerDataAsALambdaParameter(local, body, requestLocals, paramNames, seen);
    }

    /**
     * ⚠⚠ F2-233 — the THIRD binding form, after the assignment and the {@code instanceof} pattern: a
     * LAMBDA PARAMETER over caller data.
     *
     * <p>{@code ProcessorAssessmentService#scoreAndComplete} does
     * {@code controls.forEach((k, v) -> { case "c1WrittenInstructions" -> a.setC1WrittenInstructions(v); … })},
     * so the value at each setter is {@code v} — bound by neither an assignment nor a pattern, and
     * {@link #allParamNames} parses the METHOD's parameters and never a lambda's. All eight PDPL
     * Article 28 control answers therefore read as server stamps, and the create that legitimately
     * pre-fills them read as contradicting itself eight times over.
     *
     * <p>Measured 2026-08-18: <b>8 setter sites in exactly 1 service</b> — so this is narrow, and that
     * is the argument for fixing the detector rather than writing eight near-identical BY_DESIGN
     * reasons. 🔑 <b>A verdict says the code is right; a narrowing says the instrument was wrong, and
     * eight fluent reasons for one detector bug is exactly the artefact F2-188 measured at five claims,
     * five false.</b> The eight controls ARE the assessor's — {@code /score} derives the outcome and the
     * risk score FROM them, and both of those carry {@code @JsonProperty(READ_ONLY)}.
     *
     * <p>Resolves the receiver the lambda iterates, so {@code someServerList.forEach(x -> …)} still
     * reads as a stamp, and requires {@code local} to be one of the lambda's declared parameter names
     * rather than merely to appear in the header.
     */
    private static boolean bindsCallerDataAsALambdaParameter(
            String local, String body, Set<String> requestLocals, Set<String> paramNames,
            Set<String> seen) {
        Matcher lam = Pattern.compile("([\\w.]+)\\s*\\.\\s*\\w+\\s*\\(\\s*\\(?([\\w,\\s]+?)\\)?\\s*->")
            .matcher(body);
        while (lam.find()) {
            boolean declaresIt = Arrays.stream(lam.group(2).split(","))
                .map(String::trim).anyMatch(local::equals);
            if (!declaresIt) continue;
            String receiver = lam.group(1);
            if (loadsARow(receiver)) continue;
            if (callerSupplied(receiver, requestLocals, paramNames)) return true;
            if (aliasesCallerData(receiver, body, requestLocals, paramNames, seen)) return true;
        }
        return false;
    }

    /**
     * Does this right-hand side READ A ROW rather than carry the caller's value?
     *
     * <p>Anchored at the start on purpose, which is the whole discriminator: an unqualified
     * {@code get(id)} / {@code mustGet(id)} or a {@code …Repo.findBy…(…)} is a store read, while
     * {@code body.get("reason")} — the Map payload idiom this slice exists to resolve — is qualified
     * by the request itself and must NOT read as a load. The two differ only in what precedes
     * {@code get}, so testing anywhere in the string would silently undo the fix.
     */
    private static boolean loadsARow(String rhs) {
        return rhs.matches(
            "(?s)(?:[\\w.]*[Rr]epo\\w*\\s*\\.\\s*)?"
            + "(?:get|mustGet|find\\w*|load\\w*|fetch\\w*)\\s*\\(.*");
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
     *
     * <p>⚠⚠ F2-233 — the capture was <b>wrong in both directions at once, from a single character in
     * one character class.</b> It read {@code \b\w[\w<>,.\[\]]*\s+(\w+)\s*[;=]} — a "type token then
     * field name" — and that class contains a COMMA, so in
     * {@code @Column(name = "risk_score", precision = 4, scale = 1) private BigDecimal riskScore;}
     * the token {@code 4,} parsed as the type and <b>{@code scale} as the field name.</b> Measured
     * tree-wide: <b>4 phantom names</b> ({@code scale} ×3, {@code length} ×1) and <b>4 genuinely
     * closed fields never subtracted</b> — {@code ProcessorAssessment#riskScore},
     * {@code NdiAssessmentCycle#complianceScore}, {@code VendorAssessment#workflowStatus} and
     * {@code VendorAssessment#computedScorePct}.
     *
     * <p>🔑 <b>One regex, two opposite errors.</b> Losing the real field OVER-reports — a field the
     * wire already refuses is published as an open contradiction, which is noise on a work list and
     * F2-214's failure. Inventing {@code scale} UNDER-reports — a phantom entry in the closed set
     * would silently subtract a real field of that name, the quiet direction. The second half is
     * latent only because none of those three entities declares a field called {@code scale} or
     * {@code length}, which is precisely why it was invisible: <b>a phantom exclusion that matches
     * nothing scores exactly like a correct one.</b>
     *
     * <p>Fixed by anchoring on the access modifier and forbidding the window to cross a {@code ;}, so
     * it cannot leave the declaration it started in. {@code everyDerivedReadOnlyNameIsARealField}
     * closes it permanently by REFLECTION — the self-check the derivation never had, and the only
     * reason this was findable at all was reading its output against the entity rather than trusting
     * the regex (my own reading of it said {@code riskScore}, twice).
     */
    static Map<String, Set<String>> readOnlyOnTheWire() {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        try (var s = Files.walk(MAIN_JAVA)) {
            for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".java")).toList()) {
                String src = read(p);
                if (!src.contains("JsonProperty.Access.READ_ONLY")) continue;
                String type = p.getFileName().toString().replace(".java", "");
                Matcher m = Pattern.compile(
                    "JsonProperty\\.Access\\.READ_ONLY\\s*\\)[^;]{0,400}?"
                    + "\\b(?:private|protected|public)\\s+[\\w<>,.\\[\\] ]*?(\\w+)\\s*[;=]")
                    .matcher(src);
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
        return stampsServerValue(body, types, requestLocals, paramNames, Map.of());
    }

    /**
     * As above, following a one-hop call to a private helper that does the stamping.
     *
     * <p>F2-231 — the mirror of {@link #writesFromRequest(String, Map, Set, Map)}, and it was found by
     * a corpus rather than by reasoning: a test written for the write half returned an empty set,
     * because the fixture relocated <b>both</b> setters into the helper and nothing was left for the
     * stamp half to see. 🔑 The two halves have the same blind spot, and the stamp half is the worse
     * one — a service that extracts its stamping into a helper does not merely lose a field, it drops
     * out of the population entirely, because {@code measure()} skips a source whose stamped map is
     * empty. One instrument, both directions.
     */
    static Set<String> stampsServerValue(String body, Map<String, String> types,
                                         Set<String> requestLocals, Set<String> paramNames,
                                         Map<String, Set<String>> helperStamps) {
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
        for (var e : helperStamps.entrySet()) {
            if (Pattern.compile("\\b" + Pattern.quote(e.getKey()) + "\\s*\\(").matcher(body).find()) {
                out.addAll(e.getValue());
            }
        }
        return out;
    }

    /**
     * The {@code Type#field}s this guarded route assigns a server value AND actually OWNS.
     *
     * <p>⚠⚠ F2-233 — {@link #stampsServerValue} answers "does a server-derived value reach this
     * setter", which is the right question on the ACCEPTING side (it is how {@code acceptedAndStamped}
     * decides what a create settled) and <b>not sufficient on the stamping side.</b> Two shapes assign
     * a server value without claiming the field, and both were being read as ownership:
     *
     * <ul>
     *   <li><b>A fallback for an absent caller value.</b>
     *       {@code if (p.getEffectiveFrom() == null) p.setEffectiveFrom(LocalDate.now());} supplies a
     *       value only when the caller did not, so the field is still the caller's.</li>
     *   <li><b>A derivation over a field the same method also accepts.</b>
     *       {@code NdiGapService#updateCapa} writes {@code progressPct} from the request AND forces
     *       {@code 100} when the status is COMPLETED. Ownership is a fact about the METHOD, not about
     *       one site, so a per-site scan reports the literal as ownership and never sees the copy.</li>
     * </ul>
     *
     * <p>🔑 <b>F2-218's headline — {@code if (x == null) x = INITIAL} is a default, not a guard — read
     * on the STAMPING side.</b> The accepting side has carried the mirror of this since F2-219
     * ({@code settled.removeAll(defaults(...))}); the stamping side never did, and <b>that asymmetry
     * is the finding</b>: one question, asked of the request on one side and of the loaded row on the
     * other, answered on only one. The fields it hit are exactly the ones a professional is supposed
     * to fill in — {@code Dpia#nextReviewDate} (the PDPL Article 25 review cadence),
     * {@code Policy#effectiveFrom}, {@code NdiCorrectiveAction#completionDate} — so every one of them
     * was a gate failing on correct code (F2-214).
     *
     * <p>Both halves share {@link #guardedByNullTestOnOwnField} with {@link #defaults}, so there is
     * one predicate and not a second opinion nobody reads (F2-190). Pinned in both directions by
     * {@code aFallbackForAnAbsentCallerValueIsNotAnOwnershipClaim}.
     */
    static Set<String> stampsAndOwns(String body, Map<String, String> types,
                                     Set<String> requestLocals, Set<String> paramNames,
                                     Map<String, Set<String>> helperStamps) {
        Set<String> out = new TreeSet<>(
            stampsServerValue(body, types, requestLocals, paramNames, helperStamps));
        out.removeAll(callerWrittenAnywhere(body, types, requestLocals, paramNames));
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String type = types.get(m.group(1));
            if (type == null) continue;
            if (guardedByNullTestOnOwnField(body, m.start(), m.group(2), Set.of(m.group(1)))) {
                out.remove(key(type, lower(m.group(2))));
            }
        }
        return out;
    }

    /** Every {@code Type#field} this body writes from the caller at ANY site, however many others. */
    private static Set<String> callerWrittenAnywhere(String body, Map<String, String> types,
                                                     Set<String> requestLocals,
                                                     Set<String> paramNames) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String value = m.group(3).trim();
            if (value.isEmpty() || value.equals("null") || refusesTheRequestValue(value)) continue;
            String type = types.get(m.group(1));
            if (type == null) continue;
            if (callerSupplied(value, requestLocals, paramNames)
                || aliasesCallerData(value, body, requestLocals, paramNames)) {
                out.add(key(type, lower(m.group(2))));
            }
        }
        return out;
    }

    /**
     * Is the setter at {@code at} guarded by {@code if (<one of targets>.get<Field>() == null)}?
     *
     * <p>One predicate for the same question on both sides of the contract (F2-190): {@link #defaults}
     * asks it of the <b>request</b> — "the caller sent nothing, so this literal settled nothing" — and
     * {@link #stampsAndOwns} asks it of the <b>loaded row</b> — "the row held nothing, so this literal
     * claimed nothing". Same shape, different target, and only one of the two had it (F2-233).
     */
    private static boolean guardedByNullTestOnOwnField(String body, int at, String field,
                                                       Set<String> targets) {
        for (String target : targets) {
            Matcher g = Pattern.compile(
                "if\\s*\\(\\s*" + Pattern.quote(target) + "\\s*\\.\\s*(?:get|is)"
                + Pattern.quote(field) + "\\s*\\(\\s*\\)\\s*==\\s*null\\s*\\)").matcher(body);
            while (g.find()) {
                if (g.end() <= at && at - g.end() <= 200) return true;
            }
        }
        return false;
    }

    /** Fields this method writes FROM the request object, on any target. */
    static Set<String> writesFromRequest(String body, Map<String, String> types,
                                         Set<String> requestLocals) {
        return writesFromRequest(body, types, requestLocals, Map.of());
    }

    /**
     * As above, following a one-hop call to a private helper that does the setting.
     *
     * <p>⚠⚠ <b>F2-231 — this gate followed a private helper for the REFUSAL half
     * ({@link #refusingHelperNames}) and not for the WRITE half, and my own fix in the same slice
     * walked into the gap.</b> Extracting {@code markReviewed(cur, req.getDisclosureStatus())} — the
     * right design, one writer of the (status, review-stamp) pair — moved the {@code setDisclosureStatus}
     * call out of the route's own body, so {@link #SETTER} stopped matching and
     * {@code TcfdItemService#update} left the population <b>looking closed</b>. 🔑 A refactor that
     * relocates a write into a helper is indistinguishable, to a setter-site scan, from a fix that
     * removed it — and the direction is the quiet one: the population shrinks.
     *
     * <p>Same shape as F2-182's rule ("a guard sits in at least four places a setter-site regex
     * cannot see", one of them a shared validate), now on the write side. The helper's fields are
     * keyed by its own <b>declared parameter types</b>, and it counts only when the call passes
     * something that reads the request — a helper called with two server values writes nothing of the
     * caller's.
     */
    static Set<String> writesFromRequest(String body, Map<String, String> types,
                                         Set<String> requestLocals,
                                         Map<String, Set<String>> helperWrites) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String value = m.group(3).trim();
            if (refusesTheRequestValue(value)) continue;
            if (!readsRequest(value, requestLocals)) continue;
            String type = types.get(m.group(1));
            if (type != null) out.add(key(type, lower(m.group(2))));
        }
        for (var e : helperWrites.entrySet()) {
            Matcher c = Pattern.compile("\\b" + Pattern.quote(e.getKey()) + "\\s*\\(([^;]*?)\\)\\s*;")
                .matcher(body);
            while (c.find()) {
                if (readsRequest(c.group(1), requestLocals)) out.addAll(e.getValue());
            }
        }
        return out;
    }

    /**
     * Private helpers in this source, and the {@code Type#field}s each writes on its own parameters.
     *
     * <p>Only setters whose value comes from one of the helper's <b>scalar parameters</b> count: that
     * is the shape that forwards caller data (F2-218's fourth detector correction — a scalar method
     * parameter is still caller data). A helper writing a literal or a clock reading of its own is
     * stamping, not forwarding, and must not be attributed to whoever called it.
     */
    static Map<String, Set<String>> helperWrittenFields(String src) {
        return helperFields(src, true);
    }

    /**
     * The mirror: private helpers and the {@code Type#field}s each stamps with a value of the
     * <b>server's</b> own (a literal, a clock reading, the principal) rather than forwarding a
     * parameter. See {@link #stampsServerValue(String, Map, Set, Set, Map)} for why both are needed.
     */
    static Map<String, Set<String>> helperStampedFields(String src) {
        return helperFields(src, false);
    }

    /**
     * One walk, two answers, so the forwarding and stamping halves cannot disagree about what a
     * helper does — {@code markReviewed(cur, to)} does BOTH (it forwards {@code to} into
     * {@code setDisclosureStatus} and stamps {@code setLastReviewedAt(now())}), which is why the
     * classification is per SETTER and never per helper.
     *
     * @param forwarding {@code true} for setters whose value is one of the helper's scalar parameters
     *                   (caller data — F2-218's fourth correction), {@code false} for the rest.
     */
    private static Map<String, Set<String>> helperFields(String src, boolean forwarding) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile(
            "(?m)^\\s*private\\s+(?:static\\s+)?[\\w.<>\\[\\], ]+?\\s+([a-z]\\w*)\\s*\\(([^)]*)\\)")
            .matcher(src);
        while (m.find()) {
            int brace = src.indexOf('{', m.end());
            if (brace < 0) continue;
            String body = balancedBlock(src, brace);
            Map<String, String> types = localTypes(m.group(2), body);
            Set<String> scalars = new TreeSet<>();
            for (String p : m.group(2).split(",")) {
                String[] parts = p.trim().split("\\s+");
                if (parts.length == 2 && SCALAR_PARAM_TYPES.contains(parts[0])) scalars.add(parts[1]);
            }
            Set<String> fields = new TreeSet<>();
            Matcher s = SETTER.matcher(body);
            while (s.find()) {
                String type = types.get(s.group(1));
                if (type == null) continue;
                String value = s.group(3).trim();
                boolean isForward = scalars.contains(value);
                if (isForward != forwarding) continue;
                if (!forwarding && (value.isEmpty() || value.equals("null"))) continue;
                fields.add(key(type, lower(s.group(2))));
            }
            if (!fields.isEmpty()) out.put(m.group(1), fields);
        }
        return out;
    }

    /** Parameter types that carry a single caller value rather than a payload object. */
    private static final Set<String> SCALAR_PARAM_TYPES = Set.of(
        "String", "Long", "Integer", "Short", "Boolean", "BigDecimal", "LocalDate", "LocalDateTime");

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
     *
     * <p>⚠⚠ <b>F2-231 — this predicate recognised 280 of 409 server-stamped field pairs, and the
     * hard zero below was green over the 32% it could not see.</b> Two clauses were narrower than
     * their intent, and both fired on {@code PrivacyService}, hiding <em>all five</em> guarded routes
     * in the PDPL module at once:
     * <ol>
     *   <li>{@code if (isWritePath(name)) return false;} — {@code isWritePath} matches
     *       {@code record|save|add|upsert|ingest}, so a guarded TRANSITION named with a write verb
     *       was excluded. {@code PrivacyService#recordSdaiaSubmission} throws
     *       {@code IllegalStateException} on the terminal ACKNOWLEDGED state and stamps
     *       {@code sdaiaSubmittedAt} — the discharge of the PDPL Article 31 clock — and never
     *       reached line two of this method.</li>
     *   <li>{@code refuses} named exactly two exception families. {@code containBreach},
     *       {@code closeBreach}, {@code startDsr}, {@code fulfilDsr} and {@code rejectDsr} all refuse
     *       with {@code IllegalArgumentException}, and {@code DpiaService#approve} refuses through a
     *       shared {@code require(d, "IN_REVIEW")} helper whose body throws it — F2-182's rule ("a
     *       guard sits in at least four places a setter-site regex cannot see", one of them a shared
     *       validate) applied to refusal detection rather than to setter detection.</li>
     * </ol>
     *
     * <p>🔑 <b>The name test was never load-bearing.</b> A {@code create} cannot refuse on a state it
     * has not loaded, because the row does not exist yet — so "loads a row AND refuses on that row's
     * own state" is the whole discriminator, and the name clause only ever removed transitions.
     *
     * <p>The corrected predicate is a <b>UNION</b> with the original, deliberately: replacing it
     * outright LOST 96 pairs the original finds (it counts a stamp on any type once the method loads
     * and refuses, where the corrected one requires the stamped type to be the one it loaded). F2-225:
     * verify a widening by what it ADDS, in both directions. 280 + 129 = 409.
     */
    static boolean isGuardedRoute(String name, String body, Set<String> refusingHelpers) {
        return isLegacyGuardedRoute(name, body) || !guardedTypes(body, refusingHelpers).isEmpty();
    }

    /** Every refusal convention this codebase uses — F2-226 measured three, not one. */
    private static final Pattern ANY_REFUSAL = Pattern.compile(
        "\\bthrow new (IllegalState|IllegalTransition|IllegalArgument|BusinessRule"
        + "|DomainAccessDenied|Conflict)\\w*Exception");
    /** {@code Foo f = repo.findByIdAnd…(…)} / {@code mustGet(…)} — a row loaded, with its type. */
    private static final Pattern LOAD_ASSIGN = Pattern.compile(
        "\\b([A-Z]\\w*)\\s+([a-z]\\w*)\\s*=\\s*[\\w.]*\\b(?:mustGet|get\\w*|find\\w*)\\s*\\(");
    private static final Pattern CALL_NAME = Pattern.compile("\\b([a-z]\\w*)\\s*\\(");

    /**
     * The entity types this body loaded a row of AND then refused on, by declared type.
     *
     * <p>Returning types rather than a boolean is what keeps the widening honest: the row a method
     * refused on must be the row it stamps. Without that clause a {@code create} that loads a PARENT
     * and validates it reads as a transition of its child, which put
     * {@code AuthorityDocumentService#createCitation} and {@code QuestionnaireService#createQuestion}
     * in the population "stamped by themselves".
     */
    private static Set<String> guardedTypes(String body, Set<String> refusingHelpers) {
        Set<String> out = new TreeSet<>();
        Map<String, String> loaded = new LinkedHashMap<>();
        Matcher lm = LOAD_ASSIGN.matcher(body);
        while (lm.find()) loaded.put(lm.group(2), lm.group(1));
        if (loaded.isEmpty()) return out;
        for (Map.Entry<String, String> e : loaded.entrySet()) {
            String local = e.getKey();
            boolean readsOwnState = readsAStateOf(local, body);
            if (readsOwnState && ANY_REFUSAL.matcher(body).find()) {
                out.add(e.getValue());
                continue;
            }
            for (String h : refusingHelpers) {
                if (Pattern.compile("\\b" + Pattern.quote(h) + "\\s*\\(\\s*" + Pattern.quote(local)
                        + "\\b").matcher(body).find()) {
                    out.add(e.getValue());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Accessors that identify a row rather than describe its state.
     *
     * <p>F2-231 — reading a primary key, a tenant or an external reference tells you nothing about
     * the value already there, so a refusal that follows is about <b>existence or membership</b>, not
     * about the row's state. {@code NdiImprovementPlanService#reorder} was classified as a guarded
     * transition of {@code sequenceOrder} on exactly that evidence: it does
     * {@code byId.put(r.getId(), r)} and {@code if (r == null) throw new IllegalArgumentException(
     * "Plan item " + id + " not in plan " + planId)} — a membership check — and then renumbers every
     * item from a counter <b>without ever reading the previous value</b>. F2-220's discriminator is
     * that the value a route accepts depends on the value already there; a bulk renumber is the
     * opposite of that, and {@code sequence_order} is display order (no CHECK, 1–4 and 10–40 across
     * 15 rows, dev = prod), which {@code addItem} openly defaults when absent.
     *
     * <p>⚠ A narrowing is a claim too (F2-222). This one is asserted in both directions by
     * {@link #anIdentityReadIsNotAStateRead()}: deleting it puts {@code sequenceOrder} back in the
     * population, and it must not remove any pair the gate reports today.
     */
    private static final Set<String> IDENTITY_ACCESSORS =
        Set.of("getId", "getOrganisationId", "getExternalId");

    /** True when {@code local} has a getter read that is not merely an identity read. */
    static boolean readsAStateOf(String local, String body) {
        Matcher m = Pattern.compile(Pattern.quote(local) + "\\s*\\.\\s*((?:get|is)[A-Z]\\w*)\\s*\\(")
            .matcher(body);
        while (m.find()) {
            if (!IDENTITY_ACCESSORS.contains(m.group(1))) return true;
        }
        return false;
    }

    /**
     * Names of private helpers declared in the same source whose own body refuses.
     *
     * <p>Resolved per SOURCE, not per method, because the helper is declared elsewhere in the class —
     * which is exactly why an inline-throw regex could not see {@code DpiaService#approve}'s guard.
     */
    static Set<String> refusingHelperNames(String src) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile(
            "(?m)^\\s*private\\s+(?:static\\s+)?[\\w.<>\\[\\], ]+?\\s+([a-z]\\w*)\\s*\\(").matcher(src);
        while (m.find()) {
            int brace = src.indexOf('{', m.end());
            if (brace < 0) continue;
            if (ANY_REFUSAL.matcher(balancedBlock(src, brace)).find()) out.add(m.group(1));
        }
        return out;
    }

    private static String balancedBlock(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) return src.substring(open, i + 1);
            }
        }
        return src.substring(open);
    }

    private record Contradiction(String service, String writeMethod, Set<String> fields,
                                 Set<String> stampedBy) { }

    /**
     * {@code Type#field} → the guarded routes that stamp it.
     *
     * <p>F2-231 — {@code src} is a REQUIRED parameter, not a convenience: the refusing-helper names
     * are declared elsewhere in the class, so a caller that could not supply the source would get a
     * silently narrower answer, which is the exact failure mode this finding is about. Making it a
     * parameter turns forgetting it into a compile error rather than an under-report.
     */
    static Map<String, Set<String>> stampedBy(String src, List<Method> methods,
                                              Map<String, Set<String>> readOnly) {
        // F2-231 — Map.of(), NOT helperStampedFields(src): following helper STAMPS is a measured
        // blind spot that is deliberately left open. See
        // followingHelperStampsIsAMeasuredBlindSpotAndNotCoverage() for the numbers and the
        // demonstrated false positive that stopped it shipping.
        return stampedBy(src, methods, readOnly, Map.of());
    }

    /**
     * As above, with the helper-stamp map the caller considers VISIBLE.
     *
     * <p>F2-231 — {@code measure()} removes the helpers the examined write path calls itself, because
     * a shared invariant is not a control that path bypasses. Every other caller passes the full map.
     */
    static Map<String, Set<String>> stampedBy(String src, List<Method> methods,
                                              Map<String, Set<String>> readOnly,
                                              Map<String, Set<String>> visibleHelperStamps) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Set<String> refusingHelpers = refusingHelperNames(src);
        for (Method m : methods) {
            boolean legacyMatch = isLegacyGuardedRoute(m.name(), m.body());
            Set<String> guarded = guardedTypes(m.body(), refusingHelpers);
            if (!legacyMatch && guarded.isEmpty()) continue;
            Map<String, String> types = localTypes(m.params(), m.body());
            Set<String> params = allParamNames(m.params());
            for (String f : stampsAndOwns(m.body(), types, requestParams(m.params()), params,
                                          visibleHelperStamps)) {
                String type = f.substring(0, f.indexOf('#'));
                String field = f.substring(f.indexOf('#') + 1);
                if (CLOSED_AT_THE_WIRE.contains(field)) continue;
                if (readOnly.getOrDefault(type, Set.of()).contains(field)) continue;
                // The UNION, decided per FIELD rather than per method: the original predicate's
                // answer, OR the corrected one's. The corrected arm additionally requires that the
                // row refused on is the row stamped, and that the method is not BUILDING that row —
                // `generateFromBia` and `clone` load a source, refuse on it and INSERT, which is a
                // second create path rather than a control, and one create may legitimately supply
                // whatever another create supplies.
                boolean correctedMatch = guarded.contains(type)
                    && !Pattern.compile("new\\s+" + Pattern.quote(type) + "\\s*\\(")
                               .matcher(m.body()).find();
                if (!legacyMatch && !correctedMatch) continue;
                out.computeIfAbsent(f, k -> new TreeSet<>()).add(m.name());
            }
        }
        return out;
    }

    /** The original F2-218 predicate, kept intact so the widening is provably additive. */
    private static boolean isLegacyGuardedRoute(String name, String body) {
        if (isWritePath(name)) return false;
        return body.matches("(?s).*\\b(get|find\\w*)\\s*\\(.*")
            && body.matches("(?s).*\\bthrow new (IllegalState|IllegalTransition)\\w*Exception.*");
    }

    /** The {@code Type#field}s this write path takes from the caller AND the service stamps. */
    static Set<String> acceptedAndStamped(Method m, Map<String, Set<String>> stampedBy) {
        return acceptedAndStamped(m, stampedBy, Map.of(), Map.of());
    }

    /**
     * As above, with the source's helper-write map so a write relocated into a private helper is
     * still attributed to the route that passed the caller's value (F2-231).
     */
    static Set<String> acceptedAndStamped(Method m, Map<String, Set<String>> stampedBy,
                                          Map<String, Set<String>> helperWrites,
                                          Map<String, Set<String>> helperStamps) {
        if (!isWritePath(m.name())) return Set.of();
        Map<String, String> paramTypes = requestParamTypes(m.params());
        if (paramTypes.isEmpty()) return Set.of();
        Map<String, String> types = localTypes(m.params(), m.body());
        Set<String> req = paramTypes.keySet();

        Set<String> accepted = new TreeSet<>(writesFromRequest(m.body(), types, req, helperWrites));
        // Persisting the request object accepts every field of THAT TYPE it did not first
        // overwrite or clear — scoped to the persisted parameter's own type, never the service's.
        for (var e : paramTypes.entrySet()) {
            if (!m.body().matches("(?s).*\\.save\\s*\\(\\s*" + Pattern.quote(e.getKey()) + "\\s*\\).*")) {
                continue;
            }
            Set<String> settled = new TreeSet<>(
                stampsServerValue(m.body(), types, req, allParamNames(m.params()), helperStamps));
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

            Map<String, Set<String>> stampedBy = stampedBy(src, methods, readOnly);
            if (stampedBy.isEmpty()) continue;
            Map<String, Set<String>> helperWrites = helperWrittenFields(src);

            for (Method m : methods) {
                Set<String> hit = new TreeSet<>(
                    acceptedAndStamped(m, stampedBy, helperWrites, Map.of()));
                // ⚠⚠ F2-231 — A ROUTE CANNOT CONTRADICT ITSELF, and following helpers on the stamp
                // half resurrected exactly that. `maybeAutoCapa(TcfdItem t)` back-links the CAPA it
                // opens, and every route calls it — so every route read as "stamping" linkedCapaId,
                // and update() was reported as its own stamping route. 🔑 A helper called from the
                // write path makes the write path its own stamper: the "stamped by themselves" false
                // positive this gate already had a clause for, re-entering through the widening.
                // F2-228's rule — a detector wrong in both directions produces a green neither half
                // earns — so the widening and this narrowing land together.
                Set<String> routes = new TreeSet<>();
                hit.removeIf(f -> {
                    Set<String> others = new TreeSet<>(stampedBy.get(f));
                    others.remove(m.name());
                    if (others.isEmpty()) return true;
                    routes.addAll(others);
                    return false;
                });
                if (!hit.isEmpty()) out.add(new Contradiction(cls, m.name(), hit, routes));
            }
        }
        return out;
    }

    // ── the assertions ───────────────────────────────────────────────────────

    /**
     * The fields of this contradiction that carry no verified BY_DESIGN reason.
     *
     * <p>F2-231 — per FIELD, not per service: a contradiction whose every field is adjudicated drops
     * out, and one that gains a new field on the same type does not.
     */
    private static Set<String> unexplained(Contradiction c) {
        Set<String> out = new TreeSet<>(c.fields());
        out.removeIf(f -> adjudicated(c.service() + "::" + f));
        return out;
    }

    /**
     * Has this exact {@code Service::Type#field} been read and given a verdict?
     *
     * <p>F2-233 — three buckets, because one could not say what needed saying. {@link #BY_DESIGN} means
     * the caller legitimately owns the field; {@link #CLOSED_ELSEWHERE} means something outside this
     * gate's reach already refuses it; {@link #FILED_OWNER_DECISION} means the caller must NOT own it
     * and closing it is an owner's call about live rows. 🔑 Folding the third into the first would have
     * recorded a live statutory defect as a design — F2-197's rule that an invented all-clear closes a
     * question, and F2-224's that a verdict vocabulary must not be limited to the verdicts an earlier
     * slice happened to reach.
     */
    private static boolean adjudicated(String site) {
        return BY_DESIGN.containsKey(site)
            || CLOSED_ELSEWHERE.containsKey(site)
            || FILED_OWNER_DECISION.containsKey(site);
    }

    @Test
    void aClosedServiceAcceptsNothingItsOwnGuardedRoutesStamp() {
        Map<String, List<Contradiction>> byService = measure().stream()
            .filter(c -> CLOSED.contains(c.service()))
            .filter(c -> !unexplained(c).isEmpty())
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
        Set<String> services = all.stream()
            .filter(c -> !unexplained(c).isEmpty())
            .map(Contradiction::service)
            .filter(s -> !CLOSED.contains(s))
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
        // F2-231 — the measured figure is printed here so the next slice can RE-DERIVE the ceiling
        // instead of subtracting from the constant (F2-207: a ceiling left above the truth makes a
        // tolerated member indistinguishable from a reviewed one, and F2-195: a baseline copied from
        // a measurement of something else looks measured).
        lines.add("# MEASURED unexplained services: " + services.size()
                  + "  (ceiling " + BASELINE_CONTRADICTED_SERVICES + ")   " + services);
        lines.add("# BY_DESIGN field verdicts (the caller legitimately owns these): "
                  + new TreeSet<>(BY_DESIGN.keySet()));
        lines.add("# CLOSED_ELSEWHERE (already refused at a layer this gate cannot see): "
                  + new TreeSet<>(CLOSED_ELSEWHERE.keySet()));
        lines.add("# FILED_OWNER_DECISION (LIVE defects, deliberately left open — not health): "
                  + new TreeSet<>(FILED_OWNER_DECISION.keySet()));
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
        Set<String> measured = new TreeSet<>();
        for (Contradiction c : measure()) {
            c.fields().forEach(f -> measured.add(c.service() + "::" + f));
        }

        assertThat(measured)
            .as("""
                A BY_DESIGN entry names a Service::Type#field whose write path accepts a field the \
                same service's guarded routes stamp, WITH a reason that was verified by reading. If \
                the scan no longer finds it, either the code changed (delete the entry in the same \
                commit) or a detector rotted (fix the detector — a shrinking population is not \
                progress). F2-231 is why the key is a field: a per-service key could not express \
                "closed except for this one field", and it hid every other field of the same type.""")
            .containsAll(BY_DESIGN.keySet());

        BY_DESIGN.forEach((site, why) -> assertThat(why.length())
            .as("%s's reason must say why the field is genuinely the caller's, not label it", site)
            .isGreaterThan(120));
        assertThat(BY_DESIGN.keySet()).allSatisfy(k -> assertThat(k)
            .as("a BY_DESIGN key is Service::Type#field, so the verdict is at the finding's own "
                + "granularity and cannot silently cover a sibling")
            .matches("\\w+::\\w+#\\w+"));
    }

    /**
     * F2-233 — the reverse leg for the two new buckets. Same argument as the BY_DESIGN one: a verdict
     * written for something the scan no longer finds is either a fix whose entry outlived it or a
     * detector that rotted, and both must be resolved rather than left standing.
     *
     * <p>🔑 This is the leg that matters most for {@link #CLOSED_ELSEWHERE}, because "closed at a layer
     * I cannot see" is unfalsifiable from inside the gate: if the retired {@code /policies} write routes
     * ever come back, the scan will still report those four fields and this forces them to be
     * re-adjudicated rather than silently inheriting a verdict written when nothing could reach them.
     */
    @Test
    void everyNewVerdictBucketStillDescribesSomethingTheScanFinds() {
        Set<String> measured = new TreeSet<>();
        for (Contradiction c : measure()) {
            c.fields().forEach(f -> measured.add(c.service() + "::" + f));
        }

        assertThat(measured)
            .as("a CLOSED_ELSEWHERE verdict names a field this gate DOES report and something outside "
                + "its reach already refuses. If the scan stops finding it, the layer that closed it may "
                + "have moved — re-read, do not delete the entry to make the build green")
            .containsAll(CLOSED_ELSEWHERE.keySet());
        assertThat(measured)
            .as("a FILED_OWNER_DECISION verdict names a LIVE defect deliberately left open. It vanishing "
                + "from the scan is the one case that must never pass unnoticed: it would mean the "
                + "instrument stopped seeing a statutory hole nobody decided to close")
            .containsAll(FILED_OWNER_DECISION.keySet());

        for (Map<String, String> bucket : List.of(CLOSED_ELSEWHERE, FILED_OWNER_DECISION)) {
            bucket.forEach((site, why) -> {
                assertThat(site).matches("\\w+::\\w+#\\w+");
                assertThat(why.length())
                    .as("%s's reason must carry its evidence, not label the field", site)
                    .isGreaterThan(120);
            });
        }
        assertThat(FILED_OWNER_DECISION.values())
            .as("""
                A FILED entry must say out loud that the field is NOT correct and what an owner has to \
                decide. Without that it reads as a by-design verdict, which is F2-197's defect one level \
                up: an invented figure invites a question, an invented all-clear closes one.""")
            .allSatisfy(why -> assertThat(why).contains("OWNER DECISION"));
        assertThat(BY_DESIGN.keySet()).doesNotContainAnyElementsOf(CLOSED_ELSEWHERE.keySet());
        assertThat(BY_DESIGN.keySet()).doesNotContainAnyElementsOf(FILED_OWNER_DECISION.keySet());
        assertThat(CLOSED_ELSEWHERE.keySet()).doesNotContainAnyElementsOf(FILED_OWNER_DECISION.keySet());
    }

    /**
     * F2-233 — {@link #CLOSED_ELSEWHERE}'s claims, resolved rather than read.
     *
     * <p>Two mechanisms, two checks. A field closed AT THE REQUEST DTO is verified by <b>reflection</b>:
     * the bound record must exist, must not declare the field, and the controller must actually bind it
     * and call the service. A field closed BY A RETIRED ROUTE is verified by the absence of a write
     * mapping for that path plus the method's presence in {@code DarkServiceMethodTest}'s named map.
     *
     * <p>🔑 F2-215's rule about exclusions: <b>an exclusion nobody can audit is an assumption wearing a
     * citation.</b> The prose in the map is for a human; this is what makes it true.
     */
    @Test
    void everyClosedElsewhereEntryNamesSomethingThatReallyClosesIt() throws Exception {
        Class<?> yearRequest =
            Class.forName("com.tss.grc.module.sust.annex.dto.ReportingYearCreateRequest");
        Set<String> declared = Arrays.stream(yearRequest.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toCollection(TreeSet::new));
        assertThat(declared)
            .as("the bound record's own components — a field absent from this list cannot arrive at "
                + "createYear() however the service is written, which is the whole verdict")
            .doesNotContain("status", "approvedAt", "publishedAt")
            .contains("year", "reportingEntityEn");

        String annexController = read(MAIN_JAVA.resolve(
            "com/tss/grc/module/sust/annex/controller/SustainabilityAnnexController.java"));
        assertThat(annexController)
            .as("the verdict rests on THIS record being what the endpoint binds — a plausible DTO name "
                + "is worth nothing until something resolves it (F2-202)")
            .contains("@RequestBody ReportingYearCreateRequest")
            .containsPattern("createYear\\s*\\(\\s*req\\.toEntity\\s*\\(\\s*\\)\\s*\\)");

        String policyController = read(MAIN_JAVA.resolve(
            "com/tss/grc/module/compliance/controller/RegChangePolicyController.java"));
        for (String verb : List.of("PostMapping", "PutMapping", "DeleteMapping")) {
            assertThat(policyController)
                .as("a @%s for /policies would make PolicyService's create reachable again and every "
                    + "PolicyService verdict here would need re-adjudicating (CH-6 / PM-C1 retired them)",
                    verb)
                .doesNotContainPattern("@" + verb + "\\(\"/policies");
        }
        String darkMethods = read(Path.of("src", "test", "java", "com", "tss", "grc", "security",
            "DarkServiceMethodTest.java"));
        assertThat(darkMethods)
            .as("the other half of the reachability claim: the legacy write lifecycle is held as dark "
                + "by name, so this verdict is not a second, unmaintained opinion (F2-190)")
            .contains("com.tss.grc.module.compliance.service.PolicyService#create");
    }

    /**
     * F2-233 — the derivation's own self-check, and the reason the comma bug was findable.
     *
     * <p>{@link #readOnlyOnTheWire} parses field names out of entity source. A capture that is not a
     * field of that entity is a parse error in both directions at once: the REAL field it should have
     * returned is never subtracted (a closed field published as an open contradiction), and the phantom
     * name enters the closed set where it would silently subtract any real field that happens to share
     * it. 🔑 <b>A phantom exclusion that matches nothing scores exactly like a correct one</b>, which is
     * why nothing caught {@code scale} for two slices.
     */
    @Test
    void everyDerivedReadOnlyNameIsARealField() {
        Map<String, Set<String>> derived = readOnlyOnTheWire();
        assertThat(derived).as("anti-vacuity: the derivation must still find the per-field annotations "
            + "F2-219 added it for").hasSizeGreaterThan(10);

        Map<String, Set<String>> phantom = new LinkedHashMap<>();
        derived.forEach((type, fields) -> {
            Set<String> real = entityFieldNames(type);
            if (real.isEmpty()) return;              // type not resolvable from source — not this claim
            Set<String> bogus = new TreeSet<>(fields);
            bogus.removeAll(real);
            if (!bogus.isEmpty()) phantom.put(type, bogus);
        });

        assertThat(phantom)
            .as("""
                Every name this derivation returns must be a field the entity declares. Before F2-233 \
                the type-token character class contained a comma, so \
                `@Column(precision = 4, scale = 1) private BigDecimal riskScore;` parsed as type "4," \
                and field "scale" — losing ProcessorAssessment#riskScore, \
                NdiAssessmentCycle#complianceScore, VendorAssessment#workflowStatus and \
                VendorAssessment#computedScorePct from the closed set, and injecting `scale` and \
                `length` into it.""")
            .isEmpty();

        assertThat(derived.getOrDefault("ProcessorAssessment", Set.of()))
            .as("the demonstrating instance, held by name so a re-broken regex fails here first")
            .contains("riskScore", "overallOutcome", "approverName");
    }

    /**
     * Field names declared by an entity, read from its own source.
     *
     * <p>⚠ The UNION across every file with that simple name, not the first one found — because
     * {@link #readOnlyOnTheWire} keys by filename and therefore merges duplicates under one key, and a
     * comparison between a merged set and a single file's set invents a discrepancy. This fired on its
     * first run: {@code VendorAssessment} is declared in BOTH {@code module/scm/entity} and
     * {@code module/tprm/entity}, so {@code workflowStatus}, {@code answeredCount} and
     * {@code computedScorePct} — all real {@code @JsonProperty(READ_ONLY)} fields of the tprm one —
     * read as phantoms. F2-222 filed exactly this hazard (15 entity simple names declared twice, three
     * shipped gates keyed on the simple name); 🔑 <b>a keying ambiguity is harmless until two things
     * key on it differently.</b>
     */
    private static Set<String> entityFieldNames(String type) {
        try (var s = Files.walk(MAIN_JAVA)) {
            Set<String> out = new TreeSet<>();
            for (Path p : s.filter(f -> f.getFileName().toString().equals(type + ".java")).toList()) {
                Matcher m = Pattern.compile(
                    "(?m)^\\s*(?:@\\w+(?:\\([^\\n]*\\))?\\s*)*(?:private|protected|public)\\s+"
                    + "(?:static\\s+|final\\s+|transient\\s+)*[\\w<>,.\\[\\] ]*?(\\w+)\\s*[;=]")
                    .matcher(read(p));
                while (m.find()) out.add(m.group(1));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * F2-233 — the three binding forms and the fallback rule, pinned in both directions over literal
     * source, because a detector that cannot match reads exactly like a clean tree.
     */
    @Test
    void callerDataReachesASetterInMoreWaysThanAnAssignment() {
        Map<String, String> types = Map.of("body", "Foo", "e", "Foo", "cur", "Foo");
        Set<String> req = Set.of();                       // Map payloads are params, not request DTOs
        Set<String> params = Set.of("id", "body", "controls");

        String twoHopPattern = """
            { Foo cur = get(id); Object lvl = body.get("impactLevel");
              if (lvl instanceof String s) cur.setImpactLevel(s); }""";
        assertThat(stampsAndOwns(twoHopPattern, types, req, params, Map.of()))
            .as("the house Map-payload idiom: a pattern variable bound to a local that aliases the "
                + "request. Two hops, no assignment to `s` at all — 12 sites across 6 services read as "
                + "server stamps before F2-233")
            .doesNotContain("Foo#impactLevel");

        String throughACall = """
            { Foo cur = get(id); Object n = body.get("count");
              if (n instanceof Number num) cur.setCount(num.intValue()); }""";
        assertThat(stampsAndOwns(throughACall, types, req, params, Map.of()))
            .as("and the value need not BE the bound identifier — `num.intValue()` is a call ON caller "
                + "data, which the old must-be-a-bare-identifier rule could not see either")
            .doesNotContain("Foo#count");

        String lambda = """
            { Foo cur = get(id); controls.forEach((k, v) -> cur.setC1(v)); }""";
        assertThat(stampsAndOwns(lambda, types, req, params, Map.of()))
            .as("a lambda parameter over caller data: allParamNames parses the METHOD's parameters and "
                + "never a lambda's, so all eight PDPL Art.28 control answers read as stamps")
            .doesNotContain("Foo#c1");

        String fallback = """
            { Foo cur = get(id); cur.setStatus("APPROVED");
              if (cur.getReviewDate() == null) cur.setReviewDate(LocalDate.now()); }""";
        Set<String> owned = stampsAndOwns(fallback, types, req, params, Map.of());
        assertThat(owned)
            .as("a value supplied ONLY when the row held none is a fallback, not an ownership claim — "
                + "F2-218's headline on the stamping side")
            .doesNotContain("Foo#reviewDate");
        assertThat(owned)
            .as("...and the unconditional stamp beside it must survive, or the narrowing has eaten the "
                + "findings it was supposed to sharpen")
            .contains("Foo#status");

        String alsoAccepted = """
            { Foo cur = get(id); if (body.getPct() != null) cur.setPct(body.getPct());
              if ("DONE".equals(cur.getState())) cur.setPct(100); }""";
        assertThat(stampsAndOwns(alsoAccepted, types, Set.of("body"), params, Map.of()))
            .as("ownership is a fact about the METHOD: a literal at one site is a derivation over a "
                + "figure the same method takes from the caller at another")
            .doesNotContain("Foo#pct");

        String loadedRow = """
            { Foo cur = get(id); Sourced sv = compute(cur); cur.setValue(sv.value()); }""";
        assertThat(stampsAndOwns(loadedRow, types, req, params, Map.of()))
            .as("""
                THE NARROWING'S OWN LIMIT, and the reverse leg of this gate is what found it: the \
                recursion must stop at a store read. `callerSupplied` answers true for `get(id)` \
                because `id` is a parameter, so following every identifier made a row LOADED FROM THE \
                DATABASE read as caller data and SgiCommitment#currentValue stopped being a stamp. The \
                id is the caller's; the row it names is the server's.""")
            .contains("Foo#value");
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
    /**
     * The corpus helper, resolving EXACTLY as {@link #measure()} does.
     *
     * <p>⚠ F2-231 — it has to, and getting that wrong cost an iteration: with the per-method
     * helper filtering in {@code measure()} and not here, {@code aRouteIsNotItsOwnStampingRoute} kept
     * reporting a field the build no longer reported. 🔑 A test helper that resolves more loosely than
     * the gate proves nothing about the gate — and one that resolves more tightly hides its holes.
     */
    private static Set<String> contradictionsIn(String src) {
        List<Method> methods = publicMethods(src);
        Map<String, Set<String>> helperWrites = helperWrittenFields(src);
        Map<String, Set<String>> stamped = stampedBy(src, methods, Map.of());
        Set<String> hits = new TreeSet<>();
        for (Method m : methods) {
            for (String f : acceptedAndStamped(m, stamped, helperWrites, Map.of())) {
                Set<String> others = new TreeSet<>(stamped.get(f));
                others.remove(m.name());
                if (!others.isEmpty()) hits.add(f);
            }
        }
        return hits;
    }

    /**
     * F2-231 — an IDENTITY read is not a state read, asserted in both directions.
     *
     * <p>The narrowing is a claim about the codebase (F2-222), so it owes both legs: deleting it must
     * change an answer, and it must not remove a pair the gate reports today. The first corpus is
     * {@code NdiImprovementPlanService#reorder} reduced to its load-refuse-write shape — the site that
     * put {@code sequenceOrder} in the population — and the second is the same shape with one real
     * state read added, which must still count.
     */
    @Test
    void anIdentityReadIsNotAStateRead() {
        String membershipCheckOnly = """
            public List<Item> reorder(Long planId, List<Long> ordered) {
              List<Item> all = items.findByPlanId(planId);
              Map<Long, Item> byId = new HashMap<>();
              for (Item r : all) byId.put(r.getId(), r);
              int seq = 10;
              for (Long id : ordered) { Item r = byId.get(id);
                if (r == null) throw new IllegalArgumentException("not in plan");
                r.setSequenceOrder(seq); items.save(r); seq += 10; }
              return items.findByPlanId(planId); }
            public Item addItem(Long planId, Item body) { body.setId(null); return items.save(body); }
            """;
        assertThat(contradictionsIn(membershipCheckOnly))
            .as("""
                reorder() reads only r.getId() and refuses on MEMBERSHIP, then renumbers from a \
                counter without ever reading the previous value — F2-220's discriminator (the value \
                accepted depends on the value already there) is not satisfied, so this is a bulk \
                renumber and not a guarded transition of sequenceOrder.""")
            .isEmpty();

        String realStateRead = membershipCheckOnly.replace(
            "if (r == null) throw new IllegalArgumentException(\"not in plan\");",
            "if (r == null || \"LOCKED\".equals(r.getPriority())) throw new IllegalArgumentException(\"no\");");
        assertThat(contradictionsIn(realStateRead))
            .as("add one read of a real state field and the same shape IS a guarded route — the "
                + "narrowing must not be a blanket exemption for methods that call getId()")
            .contains("Item#sequenceOrder");

        assertThat(readsAStateOf("r", "byId.put(r.getId(), r); r.setSequenceOrder(seq);")).isFalse();
        assertThat(readsAStateOf("r", "if (r.getOrganisationId() == null) throw new X();")).isFalse();
        assertThat(readsAStateOf("r", "if (\"X\".equals(r.getStatus())) throw new X();")).isTrue();
    }

    /**
     * F2-231 — a write relocated into a private helper is still the caller's write.
     *
     * <p>⚠⚠ This is the gap my own fix in the same slice fell into. The gate followed a private helper
     * for the REFUSAL half and not for the WRITE half, so extracting {@code markReviewed(cur, to)} —
     * the correct design, one writer of the (status, review-clock) pair — took the setter out of the
     * route's body and {@code TcfdItemService#update} left the population <b>looking closed</b>.
     * 🔑 To a setter-site scan, a refactor that relocates a write is indistinguishable from a fix that
     * removed it, and the population shrinks either way — the quiet direction.
     */
    @Test
    void aWriteRelocatedIntoAPrivateHelperIsStillTheCallersWrite() {
        // Faithful to the real shape: `attach` stamps the status with a LITERAL behind a state check
        // (TcfdItemService#attachEvidence), and `update` forwards the caller's status through the
        // extracted helper. ⚠ My first draft asserted this on a corpus where BOTH setters lived in the
        // helper and nothing stamped anything — it returned empty, and the honest reading was that the
        // FIXTURE was wrong, not the detector: `transition(id, to)` writes a scalar PARAMETER, which is
        // caller data (F2-218's fourth correction), so it never was a server stamp. That is F2-219's
        // filed blind spot — a transition whose target state arrives as a parameter — and bending the
        // detector to make the test pass would have re-broken it.
        String viaHelper = """
            public Foo update(Long id, Foo req) { Foo cur = get(id);
              if ("APPROVED".equals(cur.getStatus())) throw new IllegalStateException("locked");
              if (req.getTitle() != null) cur.setTitle(req.getTitle());
              mark(cur, req.getStatus());
              return repo.save(cur); }
            public Foo attach(Long id, String ref) { Foo cur = get(id);
              if ("APPROVED".equals(cur.getStatus())) throw new IllegalStateException("locked");
              cur.setEvidenceRef(ref);
              if ("EVIDENCE_REQUIRED".equals(cur.getStatus())) cur.setStatus("UNDER_REVIEW");
              return repo.save(cur); }
            private void mark(Foo cur, String to) { cur.setStatus(to); }
            """;
        assertThat(contradictionsIn(viaHelper))
            .as("update() passes the caller's status into a helper that sets it; the field is still "
                + "accepted from the request even though no setter appears in update()'s own body")
            .contains("Foo#status");

        assertThat(helperWrittenFields(viaHelper))
            .as("the helper's forwarded fields are keyed by its own declared parameter type")
            .containsEntry("mark", new TreeSet<>(Set.of("Foo#status")));

        String literalArg = viaHelper.replace("mark(cur, req.getStatus());", "mark(cur, \"DRAFT\");");
        assertThat(contradictionsIn(literalArg))
            .as("a helper called with a LITERAL forwards nothing of the caller's — attributing the "
                + "helper's write to every caller would fail on correct code (F2-214)")
            .doesNotContain("Foo#status");
    }

    /**
     * ⚠⚠ F2-231 — <b>the mirror of the write-half widening is a MEASURED BLIND SPOT, not coverage</b>,
     * and this test exists so it cannot be mistaken for one (F2-224's shape).
     *
     * <p>{@link #writesFromRequest(String, Map, Set, Map)} follows a one-hop private helper because a
     * relocated write is still the caller's write. The symmetric question — a relocated <b>stamp</b> —
     * was built, measured and <b>rejected</b>. Enabling it moved the unexplained population from
     * <b>11 services to 15</b>, and it needed two narrowings to get back to 13 (a route may not be its
     * own stamping route; a helper the write path itself calls is not a control it bypasses). At 13,
     * one of the two net-new findings was still a demonstrated false positive:
     * {@code ChangeManagementService#addLink} was reported as accepting
     * {@code ChangeLink#changeRequestId, entityId, entityType, linkType, notes} "stamped by
     * transition" — the link's own data fields, on the route whose entire purpose is to supply them.
     *
     * <p>🔑 <b>F2-214 settles it: a gate that fails on correct code is worse than no gate</b>, and
     * raising a decrease-only ceiling to accommodate noise is house rule 3's own warning. So the write
     * half ships and the stamp half is recorded here with its numbers.
     *
     * <p>The consequence is worth stating plainly, because it is the sharper half: a service that
     * extracts its stamping into a private helper <b>drops out of the population entirely</b> — not
     * one field, the whole service — because {@code measure()} skips a source whose stamped map is
     * empty.
     *
     * <p>⚠⚠ <b>CORRECTION (F2-232) — both net-new findings are false positives, so the count is
     * 2 of 2 and not 1 of 2.</b> This javadoc said of the second one: <i>"The one lead worth reading
     * first is {@code CommitteeResolutionService#create}, reported as accepting {@code voteFor /
     * voteAgainst / voteAbstain} against {@code recordVote}: a committee resolution created with its
     * vote tallies already filled in. Unread — no verdict is offered here (F2-188)."</i> Read, it is
     * <b>BY DESIGN with provenance, and F2-40 built the mechanism</b>: the secretariat form posts the
     * tally straight from the minutes for a committee that votes in the room, which is the normal
     * path, and the row records <em>which kind of tally it is</em> — {@code ASSERTED} with the
     * recorder taken from {@code Authentication} (never the body — the DF-3 rule, and
     * {@code tallyRecordedBy*} is explicitly nulled before being set), versus {@code COMPUTED} for a
     * resolution created empty and decided through {@code recordVote}. Measured 2026-08-17: 22 live
     * rows in dev and 20 in prod, of which {@code ASSERTED_LEGACY} 20/20 — F2-40's honest label for
     * rows that predate the mechanism — and <b>0</b> carrying {@code ASSERTED} with a recorder, so
     * the provenance path is latent and correct rather than bypassed.
     *
     * <p>🔑 <b>That is the argument for the rejection, not a footnote to it.</b> A population produced
     * by a detector already judged unsafe refuted at 2 of 2, where this programme's usual rate against
     * a predecessor's claims is about 2 in 10 (F2-188, F2-192, F2-204, F2-211, F2-228). <b>Do not
     * re-enable the stamp half expecting a defect behind either name.</b> If it is ever revisited, the
     * missing distinction is between a helper that implements a GUARDED ROUTE's stamp and one that
     * implements an invariant every path shares — and neither refuted site turned on that distinction,
     * which is why two narrowings did not rescue it.
     */
    @Test
    void followingHelperStampsIsAMeasuredBlindSpotAndNotCoverage() {
        int helpersThatStamp = 0;
        for (Path p : serviceSources()) {
            helpersThatStamp += helperStampedFields(stripComments(read(p))).size();
        }
        assertThat(helpersThatStamp)
            .as("""
                The blind spot is real and sizeable — this many private helpers in shipped services \
                write a field with a value of the server's own, and no route calling one is credited \
                with that stamp. If this reaches zero the measurement has rotted, not the codebase.""")
            .isGreaterThan(50);

        // The write half IS followed. Asserted here as well as in its own test, because the pair is
        // the claim: one direction closed, the other measured and named.
        String relocatedWrite = """
            public Foo update(Long id, Foo req) { Foo cur = get(id);
              if ("APPROVED".equals(cur.getStatus())) throw new IllegalStateException("locked");
              mark(cur, req.getStatus()); return repo.save(cur); }
            public Foo attach(Long id, String ref) { Foo cur = get(id);
              if ("APPROVED".equals(cur.getStatus())) throw new IllegalStateException("locked");
              cur.setEvidenceRef(ref);
              if ("EVIDENCE_REQUIRED".equals(cur.getStatus())) cur.setStatus("UNDER_REVIEW");
              return repo.save(cur); }
            private void mark(Foo cur, String to) { cur.setStatus(to); }
            """;
        assertThat(contradictionsIn(relocatedWrite)).contains("Foo#status");

        String relocatedStamp = """
            public Foo update(Long id, Foo req) { Foo cur = get(id);
              if ("APPROVED".equals(cur.getStatus())) throw new IllegalStateException("locked");
              if (req.getStatus() != null) cur.setStatus(req.getStatus()); return repo.save(cur); }
            public Foo close(Long id) { Foo cur = get(id);
              if ("CLOSED".equals(cur.getStatus())) throw new IllegalStateException("already");
              stampClosed(cur); return repo.save(cur); }
            private void stampClosed(Foo cur) { cur.setStatus("CLOSED"); }
            """;
        assertThat(contradictionsIn(relocatedStamp))
            .as("""
                The blind spot, demonstrated rather than described: close() stamps the status with a \
                literal through a helper, update() takes it from the caller, and the gate reports \
                nothing. This assertion is the record — do not "fix" it by enabling the stamp half \
                without re-measuring the four services that widening adds.""")
            .isEmpty();
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

    // ── F2-220 — the second question: a machine whose TARGET STATE is a parameter ────────────

    /*
     * ⚠⚠ Everything above asks: does a create accept a field a guarded route STAMPS with a
     * server-derived value? A transition route written as `transition(Long id, String to)` writes
     * `cur.setStatus(to)` — and a scalar method parameter is caller data (F2-218's fourth detector
     * bug, the only one that pushed the population onto correct code). So `status` never enters the
     * STAMPED set, and a create accepting `status` is not a contradiction by that question.
     *
     * F2-219 hit this as a SURVIVING MUTANT and recorded the right verdict: a surviving mutant the
     * gate legitimately cannot see is an unasserted behaviour, not a loose instrument. It answered
     * with one test and filed the population as unmeasured. F2-220 measured it: NINE registers, and
     * every field was a `status`.
     *
     * 🔑 The two questions are complements, which is why this lives in the same class rather than a
     * second instrument (two artefacts answering one question is this programme's own most repeated
     * defect). `stampsServerValue` EXCLUDES caller-supplied values; this catches exactly what that
     * exclusion drops, in the case where the exclusion is a state machine.
     *
     * The discriminator is structural and carries NO list of field names (F2-217: a census keyed on
     * a list of names measures the list). A method is a TRANSITION ROUTE for field F when it
     *
     *   (1) loads a row,        (2) READS F on that row,
     *   (3) throws conditionally,   and   (4) writes F on that row from a scalar parameter.
     *
     * (2) and (4) together are what make it a machine rather than a setter with a longer name: the
     * value it will accept depends on the value already there.
     */

    /** Scalar parameters — caller data that is not a payload object. */
    private static final Set<String> SCALARS = Set.of(
        "String", "Long", "Integer", "Boolean", "Short", "Double", "BigDecimal",
        "LocalDate", "LocalDateTime");

    /**
     * The nine registers F2-220 measured and closed, as (type#field → the route that owns it).
     *
     * <p>This is the anti-vacuity guard, and it is deliberately not a count. F2-219's lesson: a
     * floor is only worth having if it sits above the largest WRONG answer, and the wrong answer
     * for a detector that has stopped matching is an EMPTY population — indistinguishable from a
     * finished sweep. A bare {@code machineOwned.size() >= N} would catch total rot and miss a
     * detector that quietly stopped recognising, say, block-bodied guards. Naming the nine means a
     * register whose machine is deleted, renamed or rewritten fails here and says which.
     */
    private static final Map<String, String> MEASURED_MACHINES = Map.ofEntries(
        Map.entry("AuthorityDocument#status", "transition"),
        Map.entry("BcmGap#status", "transitionGap"),
        Map.entry("BcmRisk#status", "transition"),
        Map.entry("BcpPlan#status", "transition"),
        Map.entry("ContinuityStrategy#approvalStatus", "transitionApproval"),
        Map.entry("ControlAssessment#workflowStatus", "transitionAssessment"),
        Map.entry("CrisisEvent#status", "transition"),
        Map.entry("Issue#status", "transition"),
        Map.entry("QuestionnaireTemplate#status", "transitionTemplate"));

    /** {@code Type#field} → the routes that transition it from a caller-supplied target state. */
    static Map<String, Set<String>> machineOwned(List<Method> methods) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Method m : methods) {
            if (isWritePath(m.name())) continue;
            String body = m.body();
            if (!body.matches("(?s).*\\b(get|find\\w*)\\s*\\(.*")) continue;
            if (!body.matches("(?s).*\\bthrow new \\w*(IllegalState|IllegalArgument"
                              + "|IllegalTransition)\\w*Exception.*")) continue;

            Map<String, String> ptypes = requestParamTypes(m.params());
            Set<String> scalarParams = new LinkedHashSet<>();
            for (String p : m.params().split(",")) {
                String[] parts = p.trim().split("\\s+");
                if (parts.length >= 2 && SCALARS.contains(parts[parts.length - 2])) {
                    scalarParams.add(parts[parts.length - 1]);
                }
            }
            if (scalarParams.isEmpty()) continue;
            Map<String, String> types = localTypes(m.params(), body);

            Matcher s = SETTER.matcher(body);
            while (s.find()) {
                String target = s.group(1);
                String field = s.group(2);
                if (!scalarParams.contains(s.group(3).trim())) continue;
                String type = types.get(target);
                // A payload parameter is the caller's whole object; writing one of its fields from
                // another of the caller's own parameters is not a transition of a loaded row.
                if (type == null || ptypes.containsKey(target)) continue;
                if (!body.matches("(?s).*\\b" + Pattern.quote(target) + "\\s*\\.\\s*(get|is)"
                                  + Pattern.quote(field) + "\\s*\\(.*")) continue;
                out.computeIfAbsent(key(type, lower(field)), k -> new TreeSet<>()).add(m.name());
            }
        }
        return out;
    }

    /** As {@link #acceptedAndStamped}, against the machine-owned map instead of the stamped one. */
    static Set<String> acceptedAndMachineOwned(Method m, Map<String, Set<String>> owned) {
        return acceptedAndMachineOwned(m, owned, Map.of());
    }

    /**
     * As above, following the same one-hop helper as the stamped leg (F2-231).
     *
     * <p>Threaded here too because the blindness is a property of the SCAN, not of the question: a
     * create that scrubs its state through a private helper would otherwise read as accepting it.
     */
    static Set<String> acceptedAndMachineOwned(Method m, Map<String, Set<String>> owned,
                                               Map<String, Set<String>> helperStamps) {
        if (!isWritePath(m.name())) return Set.of();
        Map<String, String> paramTypes = requestParamTypes(m.params());
        if (paramTypes.isEmpty()) return Set.of();
        Map<String, String> types = localTypes(m.params(), m.body());
        Set<String> req = paramTypes.keySet();

        Set<String> accepted = new TreeSet<>(writesFromRequest(m.body(), types, req));
        for (var e : paramTypes.entrySet()) {
            if (!m.body().matches("(?s).*\\.save\\s*\\(\\s*" + Pattern.quote(e.getKey()) + "\\s*\\).*")) {
                continue;
            }
            Set<String> settled = new TreeSet<>(
                stampsServerValue(m.body(), types, req, allParamNames(m.params()), helperStamps));
            settled.addAll(clears(m.body(), types));
            settled.removeAll(defaults(m.body(), types, req));   // F2-219 — a default settles nothing
            String prefix = e.getValue() + "#";
            for (String f : owned.keySet()) {
                if (f.startsWith(prefix) && !settled.contains(f)) accepted.add(f);
            }
        }
        accepted.retainAll(owned.keySet());
        return accepted;
    }

    private record MachineBypass(String service, String writeMethod, Set<String> fields,
                                 Set<String> routes) { }

    static List<MachineBypass> measureMachineBypasses() {
        List<MachineBypass> out = new ArrayList<>();
        for (Path p : serviceSources()) {
            String src = stripComments(read(p));
            List<Method> methods = publicMethods(src);
            Map<String, Set<String>> owned = machineOwned(methods);
            if (owned.isEmpty()) continue;
            for (Method m : methods) {
                Set<String> hit = acceptedAndMachineOwned(m, owned, helperStampedFields(src));
                if (!hit.isEmpty()) {
                    Set<String> routes = new TreeSet<>();
                    hit.forEach(f -> routes.addAll(owned.get(f)));
                    out.add(new MachineBypass(p.getFileName().toString().replace(".java", ""),
                                              m.name(), hit, routes));
                }
            }
        }
        return out;
    }

    /**
     * A create may not name a state that a transition route adjudicates.
     *
     * <p>A hard zero with no exemption map, because the whole measured population was read and
     * closed in one slice. Every one of the nine had a precondition on the far side of the machine
     * that the create simply skipped — a successor document, a linked corrective action, an RTO or
     * RPO, at least one question, a resolution code, a root cause and a lessons-learned record.
     * <b>The create did not merely bypass the machine; it bypassed the machine's evidence
     * requirements.</b>
     */
    @Test
    void noCreateMayNameAStateATransitionRouteAdjudicates() {
        assertThat(measureMachineBypasses())
            .as("""
                A create writing this field lands the record in a state the register's own \
                transition route decides — with none of the checks that route makes, no audit row \
                naming the from- and to-states, and none of the events it publishes. Route the \
                value through ServerOwnedState.requireInitial (or requireCreatable, which owes a \
                recorded reason); do not add an exemption.""")
            .isEmpty();
    }

    /**
     * The nine machines this gate was built over must still be visible to it.
     *
     * <p>🔑 Anti-vacuity. The assertion above is a hard zero, and a hard zero over a population the
     * instrument can no longer see is the most reassuring possible artefact — F2-198's shape, which
     * this programme has now met at a filename, a three-entry allow-list, a module name, a
     * controller-bearing table, an {@code extends} clause and a field-name list. Enforced both
     * ways: a machine that disappears fails here, and a machine this map does not know about is
     * reported so the map cannot silently stop describing the tree.
     */
    @Test
    void theMeasuredMachinesAreStillRecognised() {
        Map<String, Set<String>> found = new TreeMap<>();
        for (Path p : serviceSources()) {
            machineOwned(publicMethods(stripComments(read(p))))
                .forEach((f, routes) -> found.computeIfAbsent(f, k -> new TreeSet<>()).addAll(routes));
        }

        assertThat(found.keySet())
            .as("""
                F2-220 measured these nine parameter-driven machines and closed the create that \
                went around each. If one is missing, either its transition route changed (update \
                the map in the same commit, having re-read it) or a detector rotted — and a \
                shrinking population is not progress.""")
            .containsAll(MEASURED_MACHINES.keySet());

        MEASURED_MACHINES.forEach((field, route) -> assertThat(found.get(field))
            .as("%s is adjudicated by %s", field, route)
            .contains(route));
    }

    /**
     * The pre-fix shape, as literal source, run through the SHIPPED predicates.
     *
     * <p>Every detector in this class was also re-run against {@code git show master:} before the
     * fix landed; this is the part of that control that survives into the build. 🔑 A detector that
     * cannot match reads exactly like a clean tree, and the corpus assertion above passes either
     * way.
     */
    @Test
    void thePreFixShapeIsDetectedAndTheFixedOneIsNot() {
        String broken = """
            public Foo transition(Long id, String to) {
                Foo e = get(id);
                if (!ALLOWED.getOrDefault(e.getStatus(), Set.of()).contains(to))
                    throw new IllegalStateException("no");
                e.setStatus(to);
                return repo.save(e);
            }
            public Foo create(Foo body) {
                if (body.getStatus() == null) body.setStatus("DRAFT");
                if (!STATUSES.contains(body.getStatus()))
                    throw new IllegalArgumentException("bad");
                return repo.save(body);
            }
            """;
        List<Method> brokenMethods = publicMethods(broken);
        Map<String, Set<String>> brokenOwned = machineOwned(brokenMethods);
        assertThat(brokenOwned).as("the transition writes status from a scalar parameter after "
            + "reading it — that is a machine").containsKey("Foo#status");
        Set<String> brokenHits = new TreeSet<>();
        for (Method m : brokenMethods) brokenHits.addAll(acceptedAndMachineOwned(m, brokenOwned));
        assertThat(brokenHits)
            .as("a DEFAULT plus a membership test is exactly what nine registers had, and it is "
                + "not a settlement — checking a value is IN the vocabulary is not checking it is "
                + "one a record may START in")
            .contains("Foo#status");

        String fixed = """
            public Foo transition(Long id, String to) {
                Foo e = get(id);
                if (!ALLOWED.getOrDefault(e.getStatus(), Set.of()).contains(to))
                    throw new IllegalStateException("no");
                e.setStatus(to);
                return repo.save(e);
            }
            public Foo create(Foo body) {
                body.setStatus(ServerOwnedState.requireInitial(
                    body.getStatus(), INITIAL_STATUS, "a foo", "POST /{id}/transition"));
                return repo.save(body);
            }
            """;
        List<Method> fixedMethods = publicMethods(fixed);
        Map<String, Set<String>> fixedOwned = machineOwned(fixedMethods);
        assertThat(fixedOwned).as("the machine is unchanged by the fix").containsKey("Foo#status");
        Set<String> fixedHits = new TreeSet<>();
        for (Method m : fixedMethods) fixedHits.addAll(acceptedAndMachineOwned(m, fixedOwned));
        assertThat(fixedHits).as("the refusal primitive settles it").isEmpty();
    }

    /**
     * A method taking the caller's payload AND a scalar is not thereby a machine.
     *
     * <p>Without the {@code ptypes.containsKey(target)} narrowing, {@code update(Long id, Foo body,
     * String note)} writing {@code body.setNote(note)} would register {@code Foo#note} as
     * machine-owned and every create would then "bypass" it. A gate that fails on correct code is
     * the one failure a gate must never have (F2-214), and this is the exact shape that produced
     * F2-218's fourth detector bug one question over.
     */
    @Test
    void writingTheCallersOwnPayloadFromAnotherParameterIsNotATransition() {
        // ⚠ The fixture reads `body.getNote()` on purpose, and that is the whole control. Its
        // first draft read the field on the LOADED row (`e.getNote()`) instead, so the
        // read-the-same-field requirement already excluded it and the payload narrowing was never
        // exercised — the mutation deleting that narrowing SURVIVED. 🔑 A negative control that
        // does not actually reach the code it is controlling proves nothing, and its passing reads
        // exactly like the narrowing being sound (F2-197: a control that does not break the thing
        // is not a control). Every other clause here must hold, so that the narrowing is the only
        // thing standing between this fixture and a false finding.
        String notAMachine = """
            public Foo edit(Long id, Foo body, String note) {
                Foo e = get(id);
                if (e.getNote() == null) throw new IllegalStateException("no");
                if (body.getNote() != null) body.setNote(note);
                return repo.save(body);
            }
            """;
        assertThat(machineOwned(publicMethods(notAMachine)))
            .as("body is the caller's whole object, not a loaded row: writing one of its fields "
                + "from another of the caller's own parameters is a partial update, not a "
                + "transition. Without this narrowing every create would then 'bypass' it, and a "
                + "gate that fails on correct code is the one failure a gate must never have.")
            .doesNotContainKey("Foo#note");
    }
}
