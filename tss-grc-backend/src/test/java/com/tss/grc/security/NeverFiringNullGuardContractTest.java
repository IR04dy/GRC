package com.tss.grc.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-237 — {@code if (req.getX() != null)} on a field whose ENTITY declares a non-null initialiser.
 *
 * <p>⚠⚠ <b>THE GUARD CAN NEVER FIRE, SO THE LINE READS AS A PARTIAL UPDATE AND BEHAVES AS A FULL
 * REPLACE.</b> An entity-bound {@code PUT} deserialises an omitted key to the field's initialiser,
 * not to null — {@code private BigDecimal impactMateriality = BigDecimal.ZERO;} means
 * {@code req.getImpactMateriality()} is ZERO on a body that never mentioned it. The null test is
 * true on every request, and the stored value is overwritten with the initialiser.
 *
 * <p>Found on {@code MaterialTopicService#update} by a unit test failing on a fixture built the way
 * Jackson builds a request: {@code new MaterialTopic()}. Probed live — a PUT carrying only
 * {@code topicEn} and {@code category} zeroed a CRITICAL topic's two double-materiality axes, and
 * {@code recomputeScore} then took {@code materiality_score} to 0.0 and {@code priority_tier} to
 * LOW: a CMA Article 92(a) disclosure input destroyed by an edit to an unrelated field.
 *
 * <p>🔑 <b>The frontend was carrying the workaround.</b> {@code EsgMaterialityTab}'s edit handler
 * echoes both axes back with a comment explaining that <i>"an omitted value would zero out the score
 * + tier"</i>. A defence in one client is not a defence: the next caller does not have it, and this
 * whole family is reachable exactly where machine callers live (F2-179).
 *
 * <p>🔑 <b>Why no gate in this tree could already see it.</b> {@code FullReplaceUpdateContractTest}
 * asks whether a method persists its own request parameter — this one persists the loaded row.
 * Its coverage leg asks whether every field an update writes from the request is DECLARABLE by the
 * frontend type, and with a {@code Partial<MaterialTopic>} client every field is (F2-214's recorded
 * reason). And {@code ServerStampedFieldContractTest} subtracts anything a null-guard protects —
 * which is precisely the subtraction this defect defeats, because the guard is decorative.
 *
 * <p><b>This ships as a WORK LIST with NO verdicts</b> (F2-188: five reasons written for code nobody
 * had read were five false claims, and fluency read as verification). Each entry is the site and the
 * initialiser that would be written; whether it is live depends on what the route's callers actually
 * omit, which is a fact to be established per site by reading and probing. Two things are already
 * clear from the initialisers alone and are why this is filed rather than shrugged at:
 *
 * <ul>
 *   <li>a RESET is worse than a null (F2-213) — {@code false}, {@code 0}, {@code "MEDIUM"} and
 *       {@code new BigDecimal("100.00")} all read as facts rather than as missing data;</li>
 *   <li>several are flattering in the direction that matters — {@code GhgEmissionRecord
 *       #coveragePercent} resets to <b>100.00</b> (an ISAE 3410 assurance input) and
 *       {@code AuthorityDocument#isPlaceholder} to <b>true</b>, marking a real regulatory
 *       instrument a placeholder.</li>
 * </ul>
 *
 * <h2>F2-238 — the six PATCH routes</h2>
 *
 * <p>The slice boundary was the HTTP <b>verb</b>, and it is derived rather than chosen. A
 * {@code PUT} has full-replace semantics as a defensible reading, so a caller who omits a field
 * there arguably asked for the default. A {@code PATCH}'s whole contract is that omitted fields are
 * left alone, so writing the entity's initialiser over a stored value is a contract violation.
 * Six of the twenty routes on the original list were {@code @PatchMapping}; all six are closed, and
 * the list fell 46 → 28.
 *
 * <p>⚠⚠ <b>TWO CONSEQUENCES OF ONE SHAPE, and which you get depends only on whether the
 * never-firing guard's body is a WRITE or a THROW.</b> Three of the six carried
 * {@code if (patch.getStatus() != null && !patch.getStatus().equals(existing.getStatus())) throw}
 * — the "use transition() to change status" guard — over a status the entity initialises. The null
 * test can never be false, so the <i>comparison</i> fires for every row not in the initial state:
 * measured in dev and prod, <b>5 of 5</b> crisis events, <b>4 of 7</b> BIA assessments and
 * <b>6 of 13</b> issues could not be PATCHed at all.
 *
 * <p>🔑 <b>And in those three the THROW was holding the WRITE shut.</b> A row past the initial
 * state got a 400 and was therefore safe from the resets; a row still in the initial state was
 * reset. Fixing only the throw would have converted a broken route into a data-destroying one
 * (F2-203: fixing one defect is what detonates the next). Removing the initialiser fixes both
 * halves at once, which is why that is the shape of the fix rather than a guard rewrite.
 *
 * <h2>F2-239 — the register is the boundary, and the third refusing mechanism is {@code @Valid}</h2>
 *
 * <p>F2-238 took the six PATCH routes; this took the two REGISTERS whose live callers actually omit
 * an initialised field — the regulatory library (document + citations) and the change implementation
 * checklist. The ordering that found them is <b>which work-list field the live caller OMITS</b>, not
 * the caller's body width and not what the initialiser asserts. Of the 28 residual entries, 10 of 14
 * routes have a frontend write client and exactly <b>4 sites are live</b>: three on
 * {@code AuthorityDocumentCitation} and {@code ChangeTask#sortOrder}. List 28 → 19.
 *
 * <p>⚠ <b>F2-238's filing ranked the residual by what the initialiser ASSERTS</b> and named
 * {@code GhgEmissionRecord#coveragePercent} (100.00) and {@code AuthorityDocument#isPlaceholder}
 * (true) as the two sharpest. By reachability, {@code coveragePercent} is SENT by its only caller,
 * and the live {@code isPlaceholder} is the CITATION's — a different entity, edited by a second form
 * on the same page that has inputs for three fewer fields. 🔑 <b>Harm-if-reached and reach are two
 * orderings and they disagree; a work list has to carry both.</b>
 *
 * <p>⚠⚠ <b>And the change-task half was not a reset at all — both its write routes REFUSED.</b>
 * {@code ChangeTask.titleAr} is {@code @NotBlank} (F2-30) on an entity bound with {@code @Valid}, so
 * the exact bodies {@code ChangeDetailPanel} sends answered <b>400 "titleAr: Field must not be
 * blank"</b>: neither "Add task" nor the status toggle had ever worked. 🔑 <b>A reachability
 * derivation that stops at the caller's body cannot see that the route refuses it</b> — F2-238's
 * lesson from the other side, in a mechanism no hand-written guard is involved in. See
 * {@link #noPartialUpdateRouteBindsAnEntityWithValid}.
 *
 * <h2>F2-240 — the residual 19, and why a whole-population sweep was the honest move</h2>
 *
 * <p>The list is now <b>0</b>, and {@link #WORK_LIST} is an invariant rather than a decreasing
 * count. Re-derived per F2-221: 11 routes resolved from their controllers, <b>all binding the
 * entity</b>, none carrying {@code @Valid}, 7 with a frontend write client, and every update method
 * having exactly <b>one</b> Java caller — its own controller, so no second producer (F2-238's
 * question). Then the reach ordering: <b>every one of the six live callers echoes back every
 * initialised field it could omit</b>, so all 19 sites were latent-by-caller and reach could no
 * longer rank them.
 *
 * <p>🔑 <b>That is a reason to sweep the population, not to close the file.</b> F2-237's own
 * headline is that a defence in one client is not a defence, and these nineteen were defended by
 * nothing except the shape of one page's payload. Six of them additionally had <em>no frontend write
 * client at all</em>, so their only protection was that nobody has built the screen yet — which is
 * F2-214's invitation-to-build shape. Removing the initialisers makes the hazard absent rather than
 * latent (F2-213), and takes the gate from "this many known" to "none possible".
 *
 * <p>⚠ <b>Sort by what the initialiser ASSERTS and four are flattering</b>, measured identical in
 * dev and prod: {@code VendorAssessment#riskRating} would take <b>13 of 19</b> vendor assessments
 * from HIGH to MEDIUM; {@code GhgEmissionRecord#coveragePercent} would raise 8 of 18 rows sitting
 * between 48% and 89% to <b>100.00</b>, an ISAE 3410 assurance input; {@code NdiFrameworkItem
 * #mandatory} would make <b>13 of 20</b> mandatory NDMO requirements optional; and
 * {@code ControlMapping#coverage} would report PARTIAL control coverage as FULL. A reset that
 * MANUFACTURES an assurance does not lose a fact, it asserts one.
 *
 * <p>⚠⚠ <b>The fix needed a fifth writer kind, and the gate's own {@code Moved} leg would have
 * failed on the correct fix without it.</b> Two of the nineteen defaults belong in a purpose-built
 * request record's {@code toEntity()} rather than in a service, because those two creates bind the
 * record (F2-233's fourth wire-closing mechanism, appearing on the writer side). See
 * {@link #liveWriters} and {@link #theWriterScopeReallyIncludesNonServiceWriters}.
 *
 * <p>⚠ <b>Four detector errors while deriving this, in both directions</b> (F2-228: two errors in
 * opposite directions produce an answer neither earns). The body-type regex demanded
 * {@code @Valid @RequestBody} — a literal {@code @} before an optional group — and reported
 * {@code body=None} on all 11 routes; the frontend path match was a literal while the house idiom is
 * {@code const BASE = '/api/…'} plus a template, so it reported <b>0 frontend clients</b>, the
 * flattering direction for this family. Then the omit check read only the call ARGUMENT, so an
 * opaque {@code updateFrameworkItem(id, body)} read as sending nothing; and once locals were
 * resolved it still missed {@code weight,} because an <b>ES6 shorthand property</b> does not match
 * {@code field\s*:}.
 */
class NeverFiringNullGuardContractTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * Every site the derivation finds, as {@code Service#method :: Entity#field}. Exactly asserted
     * in BOTH directions, with no reasons attached, so a new one fails the build and says which
     * rather than shrinking a count (F2-204: when a ratchet's number has to go up, ask whether its
     * question was wrong — here the question is right and the number must only ever fall).
     *
     * <p>It was 50 before F2-237: {@code MaterialTopicService#update} guarded all four score columns
     * of {@code MaterialTopic}, every one initialised, and no longer writes any of them. F2-238 took
     * it 46 → 28 by closing the six {@code @PatchMapping} routes — 17 entries fixed by moving the
     * initialiser onto the create path, and one, {@code CloudService#status}, that was never real:
     * it was a phantom produced by this class keying entities on their SIMPLE name, so the
     * {@code module/outsourcing} twin's initialiser was attributed to the {@code module/cyber}
     * service (see {@link #initialisedFields}). F2-239 took it 28 → 19.
     *
     * <p><b>F2-240 took it 19 → 0, and this is now an INVARIANT rather than a decreasing work
     * list.</b> The previous four slices each ordered the population and took the reachable end
     * first. F2-240 re-derived it the same way — all 11 routes resolved from their controllers, all
     * binding the entity, 7 with a frontend write client, every update method having exactly ONE
     * Java caller (its own controller, so no second producer) — and every remaining site measured
     * <em>latent-by-caller</em>: each of the six live callers echoes back every initialised field it
     * could omit. With nothing reachable, reach could no longer order the work, so the whole
     * population was swept in one slice. That is not a shortcut: F2-237 established that a defence
     * in one client is not a defence, so nineteen fields whose only protection is the payload one
     * page happens to build are nineteen defects waiting for a second caller, and the fix makes the
     * hazard <em>absent</em> rather than latent (F2-213's rule).
     *
     * <p>⚠⚠ <b>F2-291 — IT WAS NOT ZERO. TWO STACKED NARROWINGS IN THIS CLASS'S OWN DERIVATION WERE
     * HIDING 42 SITES, AND THE EMPTY INVARIANT ABOVE READ AS A FINISHED SWEEP.</b>
     * <ol>
     *   <li>{@link #measure} walked {@link ServerStampedFieldContractTest#publicMethods} only, and
     *       the house idiom is {@code update(id, body)} delegating to a private
     *       {@code apply(target, src)} that holds every null guard — so the whole idiom was outside
     *       the population by construction. F2-198's shape at an access modifier, for the third time
     *       in this family: F2-237 recorded it against the SIBLING gate, F2-231 built that gate a
     *       one-hop private-helper follow, and this gate never inherited it (F2-190) although it
     *       already imports three helpers from the same class.</li>
     *   <li>The "did it load a row?" test was the name vocabulary {@code get|find\w*}, and the
     *       ethics and KYC services call their loader {@code require(id)} — so every update method
     *       in those modules read as a create. F2-217's headline, and F2-278 had already measured
     *       this exact limitation on the sibling gate's {@code loadsARow} and found it harmless
     *       THERE, so the limit was known, filed against one gate, and never asked of this one.</li>
     * </ol>
     *
     * <p>What they hid, both probed live at HTTP 200: {@code EthicsCaseService#apply} resetting a
     * whistleblowing case's severity, intake channel and <b>anonymity flag</b>, and
     * {@code KycService#apply} resetting {@code isSanctioned} / {@code riskRating} /
     * {@code eddRequired} — the flags F2-288 armed one slice earlier so that {@code approve()} could
     * refuse a sanctioned subject, which it then did not.
     *
     * <p>So this is F2-205's case and not F2-204's: <b>the question was right and the ANSWER was
     * under-measured, so the number moves and says so.</b> A hard zero over a population the
     * instrument cannot see is the most reassuring possible artefact. The nine pairs F2-291 fixed
     * are gone; the remaining 33 ride here as a decrease-only work list with <b>no reasons
     * attached</b> (F2-188 measured what happens otherwise: five claims, five false), to be read and
     * closed the way F2-238/239/240 closed the first population — ordered by reach, entity by
     * entity. Do not write 33 reasons.
     */
    private static final Set<String> WORK_LIST = Set.of();

    /**
     * F2-294 — the remaining 27 sites, swept in one slice for the reason F2-240 recorded when
     * it did the same thing: <b>reach could no longer order them.</b>
     *
     * <p>Re-derived per F2-221 before executing, and F2-293's own finding made that mandatory
     * — a filing ranks by what the initialiser ASSERTS, because that is all it can see without
     * reading the callers, and three consecutive filings' rankings have now been corrected on
     * measurement. The divergence ordering is real and identical in dev and prod:
     * {@code Control#controlType} 15 of 32, {@code #automationLevel} 12 of 32,
     * {@code BcmCorrectiveAction#progressPercent} <b>10 of 10</b>, {@code #priority} 6 of 10,
     * {@code PolicyDocumentFrameworkMapping#satisfaction} 10 of 15,
     * {@code AuditEngagement#status} 10 of 12, {@code AmlAlert#severity} 7 of 10,
     * {@code WorkingPaper#hours} <b>12 of 12</b>, {@code ChangeRequest#changeType} 6 of 17 dev
     * and 6 of 15 prod, {@code AnnualPlan#budgetHours} 2 of 2, and the eleven
     * {@code BcmSettings} tolerances 0 of 1.
     *
     * <p>⚠⚠ <b>But every call site of every one of the eleven routes was checked, and not one
     * sends a body that omits an initialised field.</b> Five of the seven highest-divergence
     * sites have <em>no frontend caller at all</em> — {@code lib/controlLibrary.ts#updateControl},
     * {@code lib/bcmCycle.ts#updateCapa}, {@code frameworkApi.updateMapping} and
     * {@code amlApi.update} are dark clients — and the rest echo the whole record back
     * ({@code engagementToReq} names every field including {@code status};
     * {@code toBackendPlan}/{@code toBackendWp} name {@code budgetHours}/{@code hours}; both
     * {@code changeApi.update} callers spread {@code {...c}}). So the population was
     * latent-by-caller across the board, exactly as F2-240 found, and F2-237's rule applies
     * with more force here than it did there: <b>a defence in one client is not a defence, and
     * five of these had no client to be defended by.</b> Removing the initialiser makes the
     * hazard ABSENT rather than latent (F2-213).
     *
     * <p>🔑🔑 <b>The reusable finding is that the remedy was ALREADY WRITTEN at nine of the 27
     * sites and could not run.</b> {@code AmlAlertService#create} ends
     * {@code if (a.getCurrency() == null) a.setCurrency("SAR")};
     * {@code AuditEngagementService#create} had {@code if (e.getStatus() == null)
     * e.setStatus("PLANNED")}; {@code createCapa} defaulted AND range-checked both its fields;
     * {@code createControl} defaulted AND vocabulary-checked both of its. Every one of those
     * guards was dead, because {@code apply} copies the request's value and the request's own
     * initialiser made it non-null. F2-292 found this on one register and called it "the
     * create's own default was dead too"; measured across a population it is a third of the
     * sites. <b>A reader auditing such a create finds the default present and correct, and has
     * no reason to ask whether its guard can fire.</b>
     *
     * <p>⚠⚠ <b>And the 13 settings sites are a THIRD kind, where deleting the initialiser
     * would have been wrong.</b> On {@code BcmSettings} and {@code OrganisationSettings} the
     * initialisers were the register's declared FACTORY DEFAULTS: {@code getOrInit()} relied on
     * the constructor to populate a brand-new row, and {@code BcmSettingsService#reset()} read
     * {@code new BcmSettings()} field by field as its source of truth. So the declaration MOVED
     * to a {@code defaults()} factory both readers name. 🔑 That relocation closed a drift the
     * initialisers were hiding: {@code OrganisationSettingsService#resetToDefaults} writes its
     * defaults as literals and never touched {@code carbonOffsetCapPct} at all, so "reset to
     * defaults" left the carbon-offset cap wherever it happened to be — two copies of one fact
     * with nobody maintaining the agreement (F2-177), visible only once the third copy went.
     *
     * <p>⚠ <b>Directions are MIXED, which is worth stating because this register's recent
     * history says otherwise.</b> Five resets are flattering (a CRITICAL AML alert reading
     * MEDIUM; a CRITICAL ISO 22301 corrective action reading MEDIUM; the one EMERGENCY change
     * reading NORMAL, which removes it from the population an ADR-017 D-3 reviewer examines; 9
     * DETECTIVE, 4 CORRECTIVE and 2 RECOVERY controls reading PREVENTIVE; a CLOSED audit
     * engagement reading PLANNED) and four are pessimistic (10 FULL framework satisfactions
     * reading PARTIAL; 4 AUTOMATED and 8 SEMI controls reading MANUAL; every working paper's
     * hours and both annual plans' budgets reading 0). F2-193/F2-195/F2-205 were uniformly
     * flattering and F2-210 was pessimistic; F2-196's rule is that a reset has no direction
     * until you look.
     *
     * <p>⚠ Those two enumerations are spelled out rather than summarised, because the first
     * draft of this javadoc said "9 DETECTIVE and 2 RECOVERY" against a measured 15 of 32 —
     * F2-273's shape (a count and the enumeration it summarises are two artefacts, and the
     * count being right is what stops anybody checking the list) inside the comment recording
     * the measurement.
     */
    private static final Set<String> CLOSED_BY_F2_294 = Set.of(
        "AmlAlertService#update :: AmlAlert#currency",
        "AmlAlertService#update :: AmlAlert#severity",
        "AuditEngagementService#update :: AuditEngagement#findingsCount",
        "AuditEngagementService#update :: AuditEngagement#status",
        "AuditLifecycleService#updateAnnualPlan :: AnnualPlan#budgetHours",
        "AuditLifecycleService#updateWorkingPaper :: WorkingPaper#hours",
        "BcmCycleService#updateGovernance :: BcmGovernanceRecord#policyVersion",
        "BcmRemediationService#updateCapa :: BcmCorrectiveAction#priority",
        "BcmRemediationService#updateCapa :: BcmCorrectiveAction#progressPercent",
        "BcmSettingsService#update :: BcmSettings#capaOverdueGraceDays",
        "BcmSettingsService#update :: BcmSettings#evidenceExpiryWarningDays",
        "BcmSettingsService#update :: BcmSettings#exerciseFrequencyMonthsCritical",
        "BcmSettingsService#update :: BcmSettings#exerciseFrequencyMonthsDefault",
        "BcmSettingsService#update :: BcmSettings#regulatoryImportStatus",
        "BcmSettingsService#update :: BcmSettings#riskMatrixImpactMax",
        "BcmSettingsService#update :: BcmSettings#riskMatrixLikelihoodMax",
        "BcmSettingsService#update :: BcmSettings#rpoCriticalHours",
        "BcmSettingsService#update :: BcmSettings#rpoWarningHours",
        "BcmSettingsService#update :: BcmSettings#rtoCriticalHours",
        "BcmSettingsService#update :: BcmSettings#rtoWarningHours",
        "ChangeManagementService#update :: ChangeRequest#changeType",
        "ControlLibraryService#updateControl :: Control#automationLevel",
        "ControlLibraryService#updateControl :: Control#controlType",
        "CrisisCommsService#updateTemplate :: CrisisCommsTemplate#version",
        "FrameworkAnchorService#updateMapping :: PolicyDocumentFrameworkMapping#satisfaction",
        "OrganisationSettingsService#update :: OrganisationSettings#carbonOffsetCapPct",
        "OrganisationSettingsService#update :: OrganisationSettings#climateScenarioWeightsStrict");

    /**
     * The nine pairs F2-291 closed. Held by name so a revert cannot pass as a shrinking work list:
     * the members of {@link #WORK_LIST} are tolerated, and one of these coming back must FAIL.
     */
    private static final Set<String> CLOSED_BY_F2_291 = Set.of(
        "EthicsCaseService#update :: EthicsCase#severity",
        "EthicsCaseService#update :: EthicsCase#intakeChannel",
        "EthicsCaseService#update :: EthicsCase#isAnonymous",
        "KycService#update :: KycSubject#riskRating",
        "KycService#update :: KycSubject#isPep",
        "KycService#update :: KycSubject#isSanctioned",
        "KycService#update :: KycSubject#cddLevel",
        "KycService#update :: KycSubject#eddRequired",
        "KycService#update :: KycSubject#status");

    /**
     * The two pairs F2-292 closed, held separately from F2-291's for the same reason: a slice's
     * closure has to be legible on its own, or a revert of one reads as a revert of the other.
     */
    private static final Set<String> CLOSED_BY_F2_292 = Set.of(
        "DisclosureService#update :: Disclosure#status",
        "DisclosureService#update :: Disclosure#currency");

    /**
     * The five pairs F2-293 closed — the first member of {@link #WORK_LIST} that was reachable from
     * the platform's own screen rather than only through the API.
     *
     * <p>{@code ReportSchedulesPage} holds two update call sites: {@code submitForm}, whose body is
     * {@code {...values}} seeded from {@code reqFromDto}'s {@code {...s}} and therefore echoes every
     * field back, and {@code togglePause}, whose body is {@code { isActive: !s.isActive }} and
     * nothing else. So one page was simultaneously safe and destructive, and clicking Pause or
     * Resume on a scheduled regulatory report rewrote its format, language, cadence and delivery
     * time. 🔑 F2-283's screen-reachable / API-reachable distinction is per CALL SITE, not per page.
     */
    private static final Set<String> CLOSED_BY_F2_293 = Set.of(
        "ReportScheduleService#update :: ReportSchedule#cadence",
        "ReportScheduleService#update :: ReportSchedule#format",
        "ReportScheduleService#update :: ReportSchedule#isActive",
        "ReportScheduleService#update :: ReportSchedule#language",
        "ReportScheduleService#update :: ReportSchedule#timeOfDay");

    // ── the derivation ───────────────────────────────────────────────────────

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> sources(String suffix) {
        try (var s = Files.walk(MAIN)) {
            return s.filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The package an entity or service source declares, or {@code ""} for a synthetic source.
     *
     * <p>Load-bearing, see {@link #initialisedFields}: without it two classes of the same simple
     * name collapse into one key.
     */
    static String pkgOf(String src) {
        Matcher m = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(src);
        return m.find() ? m.group(1) : "";
    }

    /**
     * {@code package.Entity -> field -> initialiser}, over @Entity sources only.
     *
     * <p>An explicit {@code = null} is deliberately excluded: it is honest, and a null-guard over it
     * behaves exactly as it reads.
     */
    static Map<String, Map<String, String>> initialisedFields(List<String> entitySources) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        Pattern field = Pattern.compile(
            // ⚠ the type may be fully qualified — a detector that cannot match reads exactly like a
            // clean tree, and it UNDER-reports, which is the quiet direction (F2-208).
            "\\bprivate\\s+(?:final\\s+)?([A-Za-z][\\w.]*(?:<[^>;]*>)?)\\s+(\\w+)\\s*=\\s*([^;]+);");
        for (String raw : entitySources) {
            if (!raw.contains("@Entity")) continue;
            Matcher t = Pattern.compile("\\b(?:class)\\s+(\\w+)").matcher(raw);
            if (!t.find()) continue;
            // ⚠⚠ F2-238 — KEYED BY FULLY-QUALIFIED NAME, and that is a correction to this very
            // method. Keyed by simple name, `computeIfAbsent` MERGED the two classes both called
            // CloudService (module/cyber/entity and module/outsourcing/entity), so the outsourcing
            // entity's `status = "ONBOARDING"` was attributed to the cyber service and this gate
            // reported `CloudService#status` after the cyber initialiser had been removed. It
            // OVER-reports, so it fails loudly rather than hiding a site — but a phantom entry in a
            // hard-asserted list is worse than a missing one, because it looks measured (F2-233's
            // keying-ambiguity lesson, in an instrument written one slice before it).
            String pkg = pkgOf(raw);
            String key = pkg.isEmpty() ? t.group(1) : pkg + "." + t.group(1);
            Matcher f = field.matcher(ServerStampedFieldContractTest.stripComments(raw));
            while (f.find()) {
                String init = f.group(3).trim();
                if (init.equals("null")) continue;
                out.computeIfAbsent(key, k -> new TreeMap<>()).put(f.group(2), init);
            }
        }
        return out;
    }

    /**
     * The initialisers of the {@code simpleType} a service in {@code servicePkg} most plausibly
     * writes: among same-named entities, the one whose package shares the longest prefix.
     *
     * <p>`module.cyber.service` shares `module.cyber` with `module.cyber.entity.CloudService` and
     * only `module` with the outsourcing twin, so the pairing is decided by the module rather than
     * by whichever file the walk happened to visit last.
     */
    static Map<String, String> initialisersFor(Map<String, Map<String, String>> inits,
                                               String servicePkg, String simpleType) {
        String best = null;
        int bestShared = -1;
        for (String fqn : inits.keySet()) {
            String simple = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
            if (!simple.equals(simpleType)) continue;
            String[] a = servicePkg.split("\\."), b = fqn.split("\\.");
            int shared = 0;
            while (shared < a.length && shared < b.length && a[shared].equals(b[shared])) shared++;
            if (shared > bestShared) { bestShared = shared; best = fqn; }
        }
        return best == null ? null : inits.get(best);
    }

    /**
     * {@code Service#method :: Entity#field} for every never-firing guard.
     *
     * <p>⚠⚠ <b>F2-291 — the guard is usually not IN the public method.</b> This walked
     * {@link ServerStampedFieldContractTest#publicMethods} only, and the house idiom in this
     * codebase is {@code update(id, body)} delegating to a private {@code apply(target, src)} that
     * holds all twenty-odd null guards — so the entire idiom sat outside the population by
     * construction and {@link #WORK_LIST} was an empty invariant over it. F2-198's shape at an
     * access modifier, and the third time this family has been bitten by exactly that narrowing:
     * F2-237 recorded it for {@code EvidenceItemService#transitionStatus} against the SIBLING gate,
     * which then grew a one-hop follow in F2-231 that this gate never inherited (F2-190) — although
     * it already imports three other helpers from that same class.
     *
     * <p>What it hid: {@code EthicsCaseService#apply} resetting a whistleblowing case's severity,
     * intake channel and <b>anonymity flag</b>, and {@code KycService#apply} resetting
     * {@code isSanctioned} / {@code riskRating} / {@code eddRequired} — the flags F2-288 had armed
     * one slice earlier so that {@code approve()} could refuse a sanctioned subject. Both probed
     * live at HTTP 200.
     */
    static Set<String> measure(Map<String, String> serviceSources,
                               Map<String, Map<String, String>> inits) {
        Set<String> out = new TreeSet<>();
        for (var entry : serviceSources.entrySet()) {
            // F2-295 — the map is keyed by fully-qualified name so no file is dropped; the LABEL is
            // the simple name, so the held sets keep their established form. A synthetic corpus
            // keyed by bare simple names passes through unchanged.
            String service = label(entry.getKey(), serviceSources.keySet());
            String src = ServerStampedFieldContractTest.stripComments(entry.getValue());
            String pkg = pkgOf(entry.getValue());
            Map<String, Set<String>> helpers =
                ServerStampedFieldContractTest.helperNullGuardedFields(src);
            for (ServerStampedFieldContractTest.Method m
                     : ServerStampedFieldContractTest.publicMethods(src)) {
                Map<String, String> ptypes =
                    ServerStampedFieldContractTest.requestParamTypes(m.params());
                for (var p : ptypes.entrySet()) {
                    Map<String, String> declared = initialisersFor(inits, pkg, p.getValue());
                    if (declared == null) continue;
                    // It must LOAD a row of that type. On a create, writing the initialiser is
                    // exactly what is meant to happen, so the guard being decorative costs nothing.
                    if (!loadsARowOf(m.body(), p.getValue())) continue;
                    Matcher g = Pattern.compile("if\\s*\\(\\s*" + Pattern.quote(p.getKey())
                        + "\\s*\\.\\s*(?:get|is)([A-Z]\\w*)\\s*\\(\\s*\\)\\s*!=\\s*null\\s*\\)")
                        .matcher(m.body());
                    while (g.find()) {
                        String f = Character.toLowerCase(g.group(1).charAt(0)) + g.group(1).substring(1);
                        if (declared.containsKey(f)) {
                            out.add(service + "#" + m.name() + " :: " + p.getValue() + "#" + f);
                        }
                    }
                }
                // ── the one-hop private helper (F2-291) ────────────────────────────────────────
                // Attributed to the PUBLIC method, because that is the route a caller reaches and
                // the scope a fix has to reason about. Only a helper this method actually calls
                // with one of its own request-typed parameters counts: a helper called with two
                // server values guards nothing of the caller's (F2-231's rule on the write side).
                for (var h : helpers.entrySet()) {
                    if (!callsHelperWithRequest(m.body(), h.getKey(), ptypes.keySet())) continue;
                    for (String tf : h.getValue()) {
                        String type = tf.substring(0, tf.indexOf('#'));
                        String f = tf.substring(tf.indexOf('#') + 1);
                        if (!loadsARowOf(m.body(), type)) continue;
                        if (ServerStampedFieldContractTest.CLOSED_AT_THE_WIRE.contains(f)) continue;
                        Map<String, String> declared = initialisersFor(inits, pkg, type);
                        if (declared != null && declared.containsKey(f)) {
                            out.add(service + "#" + m.name() + " :: " + type + "#" + f);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Does {@code body} declare a local of {@code type} that came from somewhere other than
     * {@code new} — i.e. did this method LOAD a row?
     *
     * <p>⚠⚠ <b>F2-291 — this was a NAME vocabulary and it was the second of two stacked narrowings.</b>
     * The test was {@code body.matches(".*\\b(get|find\\w*)\\s*\\(.*")}, and the ethics and KYC
     * services name their loader {@code require(id)} — so every one of their update methods read as
     * a create and the whole module was outside the population. F2-217's headline: a census keyed on
     * a list of names measures the list. ⚠ F2-278 had already recorded this exact limitation for the
     * sibling gate's {@code loadsARow} ({@code get|mustGet|find*|load*|fetch*}, blind to
     * {@code frameworks.require(...)}) and MEASURED that porting it changed nothing THERE — so the
     * limit was known, filed against one gate, and never asked of this one.
     *
     * <p>Structural instead of lexical, and the discriminator is F2-213's: a create builds its row
     * ({@code new T()}) and an update loads one, so "declares a local of T not from a constructor"
     * needs no vocabulary and cannot be defeated by naming the loader something new.
     *
     * <p>⚠⚠ <b>And the save's OWN RESULT is not a load — F2-213 recorded that exact detector bug and
     * this method's first draft reproduced it.</b> {@code ChangeRequest saved = changeRepo.save(c);}
     * made {@code ChangeManagementService#create} read as a method that had loaded a row, so a
     * create appeared in the population; F2-213's first draft counted the same shape and reported
     * 41 sites, nearly all creates. Naming a trap does not exempt you from it. Both shapes are
     * must-not-match cases in {@link #theLoadTestIsStructuralAndNotAVocabulary}.
     */
    static boolean loadsARowOf(String body, String type) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(type) + "\\s+\\w+\\s*=\\s*([^;]*);")
            .matcher(body);
        while (m.find()) {
            String rhs = m.group(1).trim();
            if (rhs.startsWith("new ") || rhs.startsWith("new\t")) continue;
            if (rhs.matches("(?s).*\\.\\s*save(All)?\\s*\\(.*")) continue;
            return true;
        }
        return false;
    }

    /**
     * Does {@code body} call {@code helper} passing one of {@code requestParams}?
     *
     * <p>The argument test is what keeps a shared helper attributed to the routes that actually
     * hand it caller data. Written as a scan of the call's own argument list rather than of the
     * whole body, because {@code apply(} appears in more than one method of most of these services.
     */
    static boolean callsHelperWithRequest(String body, String helper, Set<String> requestParams) {
        Matcher call = Pattern.compile("\\b" + Pattern.quote(helper) + "\\s*\\(([^;]*?)\\)\\s*;")
            .matcher(body);
        while (call.find()) {
            for (String arg : call.group(1).split(",")) {
                if (requestParams.contains(arg.trim())) return true;
            }
        }
        return false;
    }

    /**
     * ⚠⚠ <b>F2-292 tried to make this MERGE same-named files and the measurement REFUTED the fix.</b>
     *
     * <p>The reasoning was sound as far as it went: this is {@code put} into a
     * {@code LinkedHashMap}, so where two files share a simple name the SECOND visited replaces the
     * first — F2-276's exact defect, which failed in the quiet direction there (568 pairs reported
     * where 585 exist). Of 37 duplicated simple names in {@code src/main}, <b>3</b> are
     * {@code *Service} files under a {@code /service/} package: {@code AuditEngagementService}
     * (audit_eng | compliance), {@code BoardPackService} (board_pack | reporting),
     * {@code RegulatoryChangeService} (compliance | regchange).
     *
     * <p>🔑🔑 <b>But merging surfaced exactly ONE new site and it was a PHANTOM.</b>
     * {@code AuditEngagementService#update :: AuditEngagement#auditType} appeared, and neither half
     * of it is a defect: {@code audit_eng}'s entity initialises {@code auditType = "INTERNAL"} and
     * its service has <em>no</em> {@code update} at all, while {@code compliance}'s service guards
     * {@code auditType} and its entity declares <em>no</em> initialiser — so that guard CAN fire.
     * Two different classes, two different tables, one manufactured finding.
     *
     * <p>🔑 <b>So the duplicate-name hazard here is CROSS-ATTRIBUTION, not omission — and the
     * mechanism is the entity map's pre-existing {@code computeIfAbsent} union</b> (see
     * {@link #initialisedFields}), which pools the initialisers of two unrelated entities that
     * merely share a simple name. With {@code put} on this side the phantom cannot form, because
     * only one twin's service is ever present; merging supplied the missing half. F2-287's rule
     * verbatim: a fallback for an unresolved type reintroduces the ambiguity the resolution removed,
     * and <b>it fails in the confident direction — a finding naming the wrong table</b>.
     *
     * <p>A gate that reports a defect where none exists is worse than one that misses (F2-214), so
     * the merge is reverted and the phantom is PINNED by
     * {@link #theDuplicateNameHazardIsCrossAttributionNotOmission}, which fails if anybody
     * reintroduces it.
     *
     * <p>⚠ <b>F2-295 closed the half this paragraph filed.</b> It read: <i>"The real fix is
     * module-aware pairing on BOTH maps — key by fully-qualified name and pair a service with the
     * entity in its own module — which is a change to a shipped security gate and is filed rather
     * than smuggled into a slice about a different register."</i> Half of it was already true when
     * that was written: the ENTITY side has paired by longest shared package prefix since F2-238,
     * because {@link #measure} derives {@code pkgOf} per entry. What was missing was the service
     * side, and its hazard was not the one this paragraph names — see {@link #liveServicesByFqn}.
     *
     * <p>⚠ What made the merge look safe was my own measurement: {@code f292_dupsvc.py} looked for
     * guards inside a <em>private helper</em>, which is narrower than this gate's population, and
     * concluded "no pair collapses today". <b>A hazard measured with a narrower population than the
     * instrument's reads as latent when it is not.</b>
     */
    private static Map<String, String> liveServices() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path p : sources("Service.java")) {
            out.put(p.getFileName().toString().replace(".java", ""), read(p));
        }
        return out;
    }

    /**
     * The same sources keyed by FULLY-QUALIFIED name — F2-295, and the population {@link #measure}
     * scans.
     *
     * <p>⚠⚠ <b>{@link #liveServices} drops a file rather than mis-attributing one, and that is a
     * different hazard from the one F2-292 measured.</b> It keys on the file's simple name over a
     * {@code .sorted()} walk, so where two files share a simple name the LAST path wins the
     * {@code put} and <b>the other file is never scanned at all</b> — four files today:
     * {@code module/audit_eng/service/AuditEngagementService}, {@code module/board_pack/service/
     * BoardPackService}, {@code module/compliance/service/RegulatoryChangeService} and
     * {@code module/cyber/entity/CloudService} (which the {@code Service.java} suffix catches even
     * though it is an entity). F2-292 tried the MERGE variant, found it produced a phantom finding
     * naming the wrong table, reverted it and filed module-aware pairing — and its conclusion, <i>"the
     * hazard is cross-attribution, not omission"</i>, is right about merging and silent about the
     * status quo, where three whole services sit outside a hard-zero invariant's population.
     *
     * <p>🔑 <b>Keying by FQN is the fix that has neither failure mode:</b> no file is dropped, and
     * the entity pairing was already module-aware, because {@link #measure} derives {@code pkgOf} per
     * entry and {@link #initialisersFor} picks the same-named entity whose package shares the longest
     * prefix. A merged value would have defeated exactly that, since {@code pkgOf} of two
     * concatenated files returns whichever {@code package} line comes first.
     *
     * <p>⚠ The reported LABEL stays the simple name (see {@link #label}), so every entry in
     * {@link #WORK_LIST} and the {@code CLOSED_BY_*} sets keeps its established
     * {@code Service#method :: Entity#field} form — a keying fix that renamed 27 held entries would
     * be indistinguishable from a slice that changed what they mean.
     */
    private static Map<String, String> liveServicesByFqn() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path p : sources("Service.java")) {
            String raw = read(p);
            String simple = p.getFileName().toString().replace(".java", "");
            String pkg = pkgOf(raw);
            out.put(pkg.isEmpty() ? simple : pkg + "." + simple, raw);
        }
        return out;
    }

    /**
     * How a site is named in the measured set: the simple name, qualified by its owning module only
     * when that simple name is declared more than once — F2-295.
     *
     * <p>Unqualified, two services with one name would produce one label and a reader could not tell
     * which module a finding is in; qualified unconditionally, all 27 held entries would be renamed
     * by a change that alters no behaviour. No ambiguous name produces a site today, which is why
     * this can be both stable and unambiguous — and {@link #anAmbiguousServiceNameIsLabelledByModule}
     * pins the ambiguous branch against a synthetic corpus, because a branch nothing reaches is a
     * branch nothing checks.
     */
    /**
     * {@code src} with every double-quoted span blanked, newlines preserved — F2-295.
     *
     * <p>Blanked rather than deleted, so any offset taken from the result still refers to the file
     * (F2-286 recorded that {@code stripComments} removes comment LINES, so offsets into its output
     * do not). Escaped quotes are honoured, because a Java assertion message routinely contains one.
     *
     * <p>⚠⚠ <b>CHAR LITERALS HAVE TO BE SKIPPED, and the first draft did not skip them.</b> This
     * method's own body contains {@code '"'} — a single-quoted double-quote — so the scanner toggled
     * {@code in} on it and <b>everything after that point was inverted</b>: real code blanked,
     * string literals preserved. The tell was the wrong half of a pair of assertions failing, and
     * the leg's own count is what caught it. Same family as F2-198's comment stripper and F2-211's
     * branch order: <b>a stripper that mis-identifies one span does not degrade, it inverts.</b>
     */
    static String withoutStringLiterals(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inStr = false;
        boolean inChr = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if ((inStr || inChr) && c == '\\' && i + 1 < src.length()) {
                out.append("  ");
                i++;
                continue;
            }
            if (c == '"' && !inChr) {
                inStr = !inStr;
                out.append(' ');
            } else if (c == '\'' && !inStr) {
                inChr = !inChr;
                out.append(' ');
            } else if (inStr || inChr) {
                out.append(c == '\n' ? '\n' : ' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Every by-name lookup in {@code code} that names a simple name declared more than once —
     * F2-295.
     *
     * <p>Extracted rather than inlined so the leg can drive it over a SYNTHETIC corpus as well as
     * over the tree. Inlined, the assertion was *"collect the violations, assert the list empty"* and
     * the mutation harness blinded it and SURVIVED — on a clean corpus an empty list is what a
     * correct detector and a dead one both return (F2-250).
     */
    static List<String> ambiguousLookupsIn(String code, Set<String> ambiguousNames) {
        List<String> out = new java.util.ArrayList<>();
        Matcher byName = Pattern.compile(
            "(?:services|writers)\\s*\\.\\s*get\\s*\\(\\s*\"(\\w+)\"\\s*\\)").matcher(code);
        while (byName.find()) {
            if (ambiguousNames.contains(byName.group(1))) out.add(byName.group(1));
        }
        return out;
    }

    static String label(String fqn, Set<String> allKeys) {
        String simple = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
        long sameName = allKeys.stream()
            .map(k -> k.contains(".") ? k.substring(k.lastIndexOf('.') + 1) : k)
            .filter(simple::equals).count();
        if (sameName < 2) return simple;
        String[] bits = fqn.split("\\.");
        for (int i = 0; i + 1 < bits.length; i++) {
            if (bits[i].equals("module")) return bits[i + 1] + "." + simple;
        }
        return fqn;
    }

    private static Map<String, Map<String, String>> liveInitialisers() {
        return initialisedFields(sources(".java").stream().map(
            NeverFiringNullGuardContractTest::read).collect(Collectors.toList()));
    }

    /**
     * Everything that can OWN a create-path default — F2-240.
     *
     * <p>{@link #liveServices} is the population of the measurement: a never-firing guard lives in a
     * service, so that is the right scope for finding one. It is the WRONG scope for the fix, and
     * F2-240 is where that showed: two of its nineteen defaults belong in a purpose-built request
     * record's {@code toEntity()}, because those two creates bind the record rather than the entity
     * (F2-233's fourth wire-closing mechanism, arriving on the writer side). Resolving the writer
     * through {@code liveServices()} would have failed the assertion on a correct fix — so the
     * writer scope is widened here, and {@link #theWriterScopeReallyIncludesNonServiceWriters}
     * asserts the widening is load-bearing rather than decorative.
     */
    /**
     * The brace-balanced body of {@code name} in {@code src}, or {@code null} if it has no such
     * method — F2-240.
     *
     * <p>Exists because {@code source.contains("setX(")} over a whole writer file is satisfied by
     * the partial-update method itself, or by an unrelated one: the mutation harness proved it by
     * deleting {@code NdiFrameworkItemService#create}'s default and watching the gate stay green,
     * because {@code importJson} and {@code update} both hold that setter.
     */
    static String methodBody(String src, String name) {
        Matcher sig = Pattern.compile(
            // ⚠⚠ F2-294 — `static` is in this alternation because it was NOT, and the omission
            // was a real narrowing: a package-private `static` factory (which is what a
            // relocated set of factory defaults naturally is) never matched, so the Moved leg
            // reported eleven writers as "no such method". It failed loudly, which is the only
            // reason it cost nothing — a resolver that silently returns null here would have
            // reported the create-path default as PRESENT (F2-291's shape, in this gate's own
            // resolver). The trailing `(?:static\\s+)?` still allows `public static`.
            "(?:public|private|protected|static)\\s+(?:static\\s+)?[\\w<>,.\\[\\]\\s]+?\\s+"
            + Pattern.quote(name) + "\\s*\\([^)]*\\)\\s*\\{").matcher(src);
        if (!sig.find()) return null;
        int open = src.indexOf('{', sig.end() - 1);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i);
        }
        return src.substring(open);
    }

    private static Map<String, String> liveWriters() {
        Map<String, String> out = new LinkedHashMap<>(liveServices());
        for (Path p : sources("Request.java")) {
            out.put(p.getFileName().toString().replace(".java", ""), read(p));
        }
        return out;
    }

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    void theWorkListIsExactlyWhatTheTreeContains() {
        Set<String> measured = measure(liveServicesByFqn(), liveInitialisers());

        assertThat(measured)
            .as("""
                A NEW never-firing null-guard. `if (req.getX() != null)` cannot be false when the \
                entity declares an initialiser for X, so this line reads as a partial update and \
                behaves as a full replace: a caller that omits the key overwrites the stored value \
                with the initialiser. Either stop writing the field on this route (the axes belong \
                to the routes that own them), or give the request a type whose absent fields really \
                are absent. Do NOT add an entry here without reading the route and probing it.""")
            .containsExactlyInAnyOrderElementsOf(WORK_LIST);
    }

    /**
     * The reverse leg — the one nobody writes first (F2-199), and the only thing that can tell a
     * fixed site from a detector that stopped matching.
     */
    @Test
    void everyWorkListEntryStillNamesSomethingTheDerivationFinds() {
        Set<String> measured = measure(liveServicesByFqn(), liveInitialisers());
        Set<String> stale = new TreeSet<>(WORK_LIST);
        stale.removeAll(measured);
        assertThat(stale)
            .as("""
                These entries name a site the derivation no longer finds. If the route was fixed, \
                delete the entry in the SAME commit — a work list that keeps describing code that \
                has changed is how a reader comes to trust it without checking (F2-188).""")
            .isEmpty();
    }

    /**
     * Anti-vacuity, and it is not a count of the work list: the derivation has two halves and either
     * can rot silently. A floor must sit above the largest WRONG answer (F2-215), and the wrong
     * answer for a stopped scan is a small number, not zero — the entity half alone is 364.
     */
    @Test
    void theDerivationStillResolvesBothHalvesOfTheTree() {
        Map<String, Map<String, String>> inits = liveInitialisers();
        assertThat(inits.size())
            .as("entities declaring at least one non-null field initialiser")
            .isGreaterThan(300);
        assertThat(liveServices().size())
            .as("service sources — measured 327, so the floor sits above a walk that resolved only "
                + "part of module/ rather than merely above zero (F2-215)")
            .isGreaterThan(300);
        assertThat(initialisersFor(inits, "com.tss.grc.module.sust.materiality.service", "MaterialTopic"))
            .as("the register F2-237 was found on must still declare the initialisers that made the "
                + "guard decorative — if these go away, re-read update() before relaxing anything")
            .containsKeys("impactMateriality", "financialMateriality",
                          "stakeholderImportance", "businessImpact");

        // ⚠⚠ F2-238 — THE KEYING, PINNED IN BOTH DIRECTIONS. Two live entities are called
        // CloudService, and until this slice `initialisedFields` merged them under one simple-name
        // key, so `module/outsourcing`'s `status = "ONBOARDING"` was attributed to the cyber
        // service and the gate reported a site whose initialiser had already been removed.
        // A count cannot see this — both keyings return a map — so the two twins are named.
        assertThat(inits)
            .as("both CloudService entities must be present under DISTINCT fully-qualified keys; "
                + "a simple-name key merges them and invents a finding on whichever module the "
                + "walk visited last")
            .containsKey("com.tss.grc.module.outsourcing.entity.CloudService")
            .doesNotContainKey("CloudService");
        assertThat(initialisersFor(inits, "com.tss.grc.module.cyber.service", "CloudService"))
            .as("the cyber entity's initialisers were removed by F2-238, so the cyber service must "
                + "resolve to an entity that declares NO status initialiser — if this starts "
                + "reporting one, the resolution has fallen back to the outsourcing twin")
            .satisfiesAnyOf(m -> assertThat(m).isNull(),
                            m -> assertThat(m).doesNotContainKey("status"));
        assertThat(initialisersFor(inits, "com.tss.grc.module.outsourcing.service", "CloudService"))
            .as("and the outsourcing service must still resolve to its OWN entity, which does "
                + "declare one — the narrowing must not simply stop resolving (F2-229)")
            .containsEntry("status", "\"ONBOARDING\"");
    }

    /**
     * F2-238 — the default MOVED, it did not vanish.
     *
     * <p>The fix shape is "remove the initialiser, own the default on the create path", and its one
     * real hazard is the opposite defect: every column here is NOT NULL, so a create path that
     * forgets its default stores null and answers 500. Nothing else in the tree can see that —
     * {@code ddl-auto=none} means no test touches the schema, and the live probe only exercises the
     * routes it happens to call.
     *
     * <p>Asserted in BOTH directions per field: the entity must NOT initialise it (or the guard goes
     * back to being decorative) and the owning writer must set it (or a create 500s). The writer is
     * the service's create path except for {@code CrisisEvent}'s two flags, where
     * {@code BcmAuditCompletionService} saves straight to the repository and so needs its own.
     */
    @Test
    void everyRemovedInitialiserHasAWriterOnTheCreatePath() {
        record Moved(String entity, String field, String setter, String writer, String method) { }
        List<Moved> moved = List.of(
            new Moved("CloudService", "serviceModel", "setServiceModel", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "deploymentModel", "setDeploymentModel", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "kingdomDataResident", "setKingdomDataResident", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "criticality", "setCriticality", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "ssoEnabled", "setSsoEnabled", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "mfaEnforced", "setMfaEnforced", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "encryptionAtRest", "setEncryptionAtRest", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "encryptionInTransit", "setEncryptionInTransit", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CloudService", "status", "setStatus", "CloudServiceService", "applyCreateDefaults"),
            new Moved("CrisisEvent", "severity", "setSeverity", "CrisisEventService", "create"),
            new Moved("CrisisEvent", "status", "setStatus", "CrisisEventService", "create"),
            new Moved("CrisisEvent", "personalDataAffected", "setPersonalDataAffected", "CrisisEventService", "create"),
            new Moved("CrisisEvent", "cyberRelated", "setCyberRelated", "CrisisEventService", "create"),
            new Moved("CrisisEvent", "supplierInvolved", "setSupplierInvolved", "CrisisEventService", "create"),
            // ⚠ the SIEM auto-escalation builds the entity itself and calls crisisEvents.save(),
            // so CrisisEventService.create's defaults never run for it (F2-238).
            new Moved("CrisisEvent", "personalDataAffected", "setPersonalDataAffected", "BcmAuditCompletionService", "ingestSiemEvent"),
            new Moved("CrisisEvent", "supplierInvolved", "setSupplierInvolved", "BcmAuditCompletionService", "ingestSiemEvent"),
            new Moved("BcmBiaAssessment", "manualWorkaroundAvailable", "setManualWorkaroundAvailable", "BcmBiaAssessmentService", "create"),
            new Moved("BcmBiaAssessment", "opresCriticalOperationFlag", "setOpresCriticalOperationFlag", "BcmBiaAssessmentService", "create"),
            new Moved("BcmBiaAssessment", "nationalCriticalTier", "setNationalCriticalTier", "BcmBiaAssessmentService", "create"),
            new Moved("BcmBiaAssessment", "approvalStatus", "setApprovalStatus", "BcmBiaAssessmentService", "create"),
            new Moved("BcmComplianceMapping", "isPlaceholder", "setIsPlaceholder", "BcmComplianceMappingService", "create"),
            new Moved("BcmComplianceMapping", "complianceStatus", "setComplianceStatus", "BcmComplianceMappingService", "create"),
            new Moved("Department", "status", "setStatus", "DepartmentService", "create"),
            // F2-250 — the AML alert register, and here the initialiser was doing TWO wrong things.
            // It was a second literal for the state AmlAlertService.TRANSITIONS already declares as
            // its root, so the value the server picks and the value it accepts could drift (F2-217);
            // and it made an OMITTED status indistinguishable from a supplied "OPEN" on the
            // entity-bound PUT, so the echo rule that stops that route filing a Suspicious
            // Transaction Report would have refused every partial update. The create now derives the
            // default from the machine rather than repeating it.
            new Moved("AmlAlert", "status", "setStatus", "AmlAlertService", "create"),
            new Moved("Issue", "severity", "setSeverity", "IssueService", "create"),
            new Moved("Issue", "priority", "setPriority", "IssueService", "create"),
            new Moved("Issue", "status", "setStatus", "IssueService", "create"),
            // F2-239 — the regulatory library. `createCitation` already owned four of the five and
            // already INHERITED isPlaceholder from the parent document, which is why the create was
            // the right shape all along: it was `updateCitation` that discarded the reasoning.
            new Moved("AuthorityDocument", "isPlaceholder", "setIsPlaceholder", "AuthorityDocumentService", "create"),
            new Moved("AuthorityDocument", "translationStatus", "setTranslationStatus", "AuthorityDocumentService", "create"),
            new Moved("AuthorityDocumentCitation", "sequenceOrder", "setSequenceOrder", "AuthorityDocumentService", "createCitation"),
            new Moved("AuthorityDocumentCitation", "weight", "setWeight", "AuthorityDocumentService", "createCitation"),
            new Moved("AuthorityDocumentCitation", "mandatory", "setMandatory", "AuthorityDocumentService", "createCitation"),
            new Moved("AuthorityDocumentCitation", "isPlaceholder", "setIsPlaceholder", "AuthorityDocumentService", "createCitation"),
            new Moved("AuthorityDocumentCitation", "translationStatus", "setTranslationStatus", "AuthorityDocumentService", "createCitation"),
            // F2-239 — the change implementation checklist. `addTask` owned status and not sortOrder.
            new Moved("ChangeTask", "sortOrder", "setSortOrder", "ChangeManagementService", "addTask"),
            new Moved("ChangeTask", "status", "setStatus", "ChangeManagementService", "addTask"),

            // F2-240 — the residual 19, swept in one slice because every one was latent-by-caller
            // and so none of them could be ordered above the others by reach.
            new Moved("BcmCommunicationContact", "availabilityStatus", "setAvailabilityStatus", "BcmCommunicationContactService", "create"),
            new Moved("BcmCommunicationContact", "escalationLevel", "setEscalationLevel", "BcmCommunicationContactService", "create"),
            new Moved("BcmCommunicationContact", "status", "setStatus", "BcmCommunicationContactService", "create"),
            // the writer is a request record, not a service — see liveWriters()
            new Moved("ClimateRiskOpportunity", "impact", "setImpact", "ClimateRiskCreateRequest", "toEntity"),
            new Moved("ClimateRiskOpportunity", "likelihood", "setLikelihood", "ClimateRiskCreateRequest", "toEntity"),
            new Moved("ClimateRiskOpportunity", "timeHorizon", "setTimeHorizon", "ClimateRiskCreateRequest", "toEntity"),
            new Moved("RiskControlLink", "linkType", "setLinkType", "ControlLibraryService", "linkRiskToControl"),
            new Moved("ControlMapping", "coverage", "setCoverage", "ControlLibraryService", "createMapping"),
            new Moved("CorrectiveAction", "actionType", "setActionType", "CorrectiveActionService", "create"),
            new Moved("CorrectiveAction", "priority", "setPriority", "CorrectiveActionService", "create"),
            new Moved("GhgEmissionRecord", "coveragePercent", "setCoveragePercent", "GhgCreateRequest", "toEntity"),
            new Moved("NdiComplianceAssessment", "complianceStatus", "setComplianceStatus", "NdiComplianceAssessmentService", "startForCycle"),
            // F2-297 / F2-298 — the two initialisers F2-238's sweep did not reach, because their
            // guard is CONJUNCTIVE and measure()'s pattern needs the null test to be the whole
            // condition (see noConjunctiveRefusalSitsOverAnInitialisedField). ⚠ F2-297 removed the
            // BcpPlan one and did NOT register it here, which is exactly what this map exists to
            // catch: a removed initialiser whose create path does not own the default turns a
            // silent refusal into a 500 on a NOT NULL column, and nothing else in the tree would
            // notice under ddl-auto=none. Both creates own it through ServerOwnedState.requireInitial.
            new Moved("BcpPlan", "status", "setStatus", "BcpPlanService", "create"),
            new Moved("BcmPlan", "status", "setStatus", "BcmService", "createPlan"),
            // F2-300 — the sixth. Unlike BcpPlan/BcmPlan this one was a RESET, not a refusal:
            // updateExercise wrote the status unconditionally, so an omitted status arrived as the
            // entity's "SCHEDULED" and silently moved a COMPLETED exercise back. The create owns
            // the default as a DEFAULT rather than through requireInitial, because recording an
            // exercise run offline — created straight into COMPLETED with its date — is legitimate.
            new Moved("BcmExercise", "status", "setStatus", "BcmService", "createExercise"),
            new Moved("NdiFrameworkItem", "mandatory", "setMandatory", "NdiFrameworkItemService", "create"),
            new Moved("NdiFrameworkItem", "priority", "setPriority", "NdiFrameworkItemService", "create"),
            new Moved("NdiFrameworkItem", "status", "setStatus", "NdiFrameworkItemService", "create"),
            new Moved("NdiFrameworkItem", "weight", "setWeight", "NdiFrameworkItemService", "create"),
            new Moved("SgiCommitment", "valueSource", "setValueSource", "SgiCommitmentService", "create"),
            new Moved("SustainabilityEvidence", "confidentiality", "setConfidentiality", "SustainabilityEvidenceService", "create"),
            new Moved("VendorAssessment", "riskRating", "setRiskRating", "TprmService", "createAssessment"),
            // The 55th, and the gate found it by refusing F2-240's own fix: closing
            // `updateAssessment`'s unconditional writes added `if (req.getStatus() != null)` over a
            // field the entity initialised, i.e. a NEW never-firing guard. Roughly two in ten of a
            // slice's own claims fall on first run (F2-188/F2-192/F2-204/F2-211); this one fell to
            // the instrument the same slice was extending.
            new Moved("VendorAssessment", "status", "setStatus", "TprmService", "createAssessment"),
            // ── F2-291 — the population the two narrowings above were hiding ─────────────────
            // Both registers are entity-bound on POST and PUT, both name their loader require(id),
            // and both hold every null guard in a private apply(target, src). The ethics three were
            // probed resetting a CRITICAL, in-person, ANONYMOUS whistleblowing case to
            // MEDIUM/HOTLINE/attributable; the KYC five were probed clearing a confirmed OFAC_SDN
            // match so that approve() — which refuses a sanctioned subject, and had just refused
            // this one — answered 200.
            new Moved("EthicsCase", "severity", "setSeverity", "EthicsCaseService", "create"),
            new Moved("EthicsCase", "intakeChannel", "setIntakeChannel", "EthicsCaseService", "create"),
            new Moved("EthicsCase", "isAnonymous", "setIsAnonymous", "EthicsCaseService", "create"),
            // `status` is not copied by apply (F2-22b(v) moved it to transition), but its
            // initialiser made requireLifecycleIsNotBodySet read an OMITTED status as a request to
            // move the case back to INTAKE — a 400 naming a state the caller never sent, and the
            // refusal that held the three resets above shut for the 28 of 30 cases past INTAKE.
            new Moved("EthicsCase", "status", "setStatus", "EthicsCaseService", "create"),
            new Moved("KycSubject", "riskRating", "setRiskRating", "KycService", "create"),
            new Moved("KycSubject", "isPep", "setIsPep", "KycService", "create"),
            new Moved("KycSubject", "isSanctioned", "setIsSanctioned", "KycService", "create"),
            new Moved("KycSubject", "cddLevel", "setCddLevel", "KycService", "create"),
            new Moved("KycSubject", "eddRequired", "setEddRequired", "KycService", "create"),
            new Moved("KycSubject", "status", "setStatus", "KycService", "create"),
            // ── F2-292 — the sharpest member of F2-291's own work list ──────────────────
            // Ranked by what the initialiser ASSERTS rather than by count (F2-239), and the
            // measurement INVERTED the ranking: `BcmSettings` contributes 11 of the 34 entries and
            // is 0 of 1 rows on ALL ELEVEN, i.e. entirely latent, while this one differs on 18 of
            // 18 live rows in both databases — APPROVED 11, REVIEW 4, SUBMITTED 2, REJECTED 1. So
            // every conflict-of-interest disclosure in the register was DESTROYABLE by a body
            // carrying only titleEn -- API-reachable, not screen-reachable, because EthicsPage's
            // reqFromDto spreads the whole DTO and so echoed the status (F2-283: say which).
            // APPROVED / RECUSED / MITIGATED are TERMINAL in
            // DisclosureService.TRANSITIONS, so the reset escaped a terminal state by a route the
            // machine cannot see.
            //
            // `currency` is the mirror case and was closed anyway: 0 of 18 rows differ from "SAR",
            // so what made it harmless was the DATA and not the code (F2-182).
            new Moved("Disclosure", "status", "setStatus", "DisclosureService", "create"),
            new Moved("Disclosure", "currency", "setCurrency", "DisclosureService", "create"),
            // ── F2-293 — the first SCREEN-reachable member of this population ────────────
            // `ReportSchedulesPage#togglePause` sends `{ isActive: !s.isActive }`, so one click on
            // Pause or Resume rewrote four columns of a scheduled regulatory report. Probed live as
            // `compliance`: XLSX/ar/QUARTERLY/22:00 became PDF/en/WEEKLY/06:00, while cadenceAnchor
            // and nameAr survived — the two guarded fields with no initialiser, i.e. the control.
            // Measured over 11 live rows, identical in dev and prod: cadence differs on 10,
            // language on 9 (nine of these reports are Arabic — house rule 7), timeOfDay on 9,
            // format on 1. isActive differs on 0, so the field the filing ranked sharpest was the
            // one the screen's own caller always sends — and a machine caller omitting it RESUMED a
            // paused report, which manufactures an assurance rather than losing a fact.
            new Moved("ReportSchedule", "format", "setFormat", "ReportScheduleService", "create"),
            new Moved("ReportSchedule", "language", "setLanguage", "ReportScheduleService", "create"),
            new Moved("ReportSchedule", "cadence", "setCadence", "ReportScheduleService", "create"),
            new Moved("ReportSchedule", "timeOfDay", "setTimeOfDay", "ReportScheduleService", "create"),
            new Moved("ReportSchedule", "isActive", "setIsActive", "ReportScheduleService", "create"),
            // ── F2-294 — the whole remaining population, in three kinds ──────────────────
            // (a) NINE where the create-path default already existed and was DEAD CODE, so
            //     the fix is purely removing the initialiser and the default starts working.
            // (b) FIVE that had no create-path default at all; every one of those columns is
            //     NOT NULL, so these five entries are the load-bearing ones — without them a
            //     silent reset becomes a 500 on create and nothing else in the tree notices.
            // (c) THIRTEEN settings fields whose initialisers WERE the register's declared
            //     factory defaults, read through the constructor by getOrInit()/getOrCreate()
            //     and field by field by reset(). Their writer is a `defaults()` factory, which
            //     is why the method column below reads `defaults` rather than a create name.
            new Moved("AmlAlert", "severity", "setSeverity", "AmlAlertService", "create"),
            new Moved("AmlAlert", "currency", "setCurrency", "AmlAlertService", "create"),
            new Moved("AuditEngagement", "status", "setStatus", "AuditEngagementService", "create"),
            new Moved("AuditEngagement", "findingsCount", "setFindingsCount", "AuditEngagementService", "create"),
            new Moved("AnnualPlan", "budgetHours", "setBudgetHours", "AuditLifecycleService", "createAnnualPlan"),
            new Moved("WorkingPaper", "hours", "setHours", "AuditLifecycleService", "createWorkingPaper"),
            new Moved("BcmGovernanceRecord", "policyVersion", "setPolicyVersion", "BcmCycleService", "createGovernance"),
            new Moved("BcmCorrectiveAction", "priority", "setPriority", "BcmRemediationService", "createCapa"),
            new Moved("BcmCorrectiveAction", "progressPercent", "setProgressPercent", "BcmRemediationService", "createCapa"),
            new Moved("BcmSettings", "rtoWarningHours", "setRtoWarningHours", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "rtoCriticalHours", "setRtoCriticalHours", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "rpoWarningHours", "setRpoWarningHours", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "rpoCriticalHours", "setRpoCriticalHours", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "exerciseFrequencyMonthsDefault", "setExerciseFrequencyMonthsDefault", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "exerciseFrequencyMonthsCritical", "setExerciseFrequencyMonthsCritical", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "evidenceExpiryWarningDays", "setEvidenceExpiryWarningDays", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "capaOverdueGraceDays", "setCapaOverdueGraceDays", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "riskMatrixLikelihoodMax", "setRiskMatrixLikelihoodMax", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "riskMatrixImpactMax", "setRiskMatrixImpactMax", "BcmSettingsService", "defaults"),
            new Moved("BcmSettings", "regulatoryImportStatus", "setRegulatoryImportStatus", "BcmSettingsService", "defaults"),
            new Moved("ChangeRequest", "changeType", "setChangeType", "ChangeManagementService", "create"),
            new Moved("Control", "controlType", "setControlType", "ControlLibraryService", "createControl"),
            new Moved("Control", "automationLevel", "setAutomationLevel", "ControlLibraryService", "createControl"),
            new Moved("CrisisCommsTemplate", "version", "setVersion", "CrisisCommsService", "createTemplate"),
            new Moved("PolicyDocumentFrameworkMapping", "satisfaction", "setSatisfaction", "FrameworkAnchorService", "addMapping"),
            new Moved("OrganisationSettings", "climateScenarioWeightsStrict", "setClimateScenarioWeightsStrict", "OrganisationSettingsService", "defaults"),
            new Moved("OrganisationSettings", "carbonOffsetCapPct", "setCarbonOffsetCapPct", "OrganisationSettingsService", "defaults"));

        Map<String, String> services = liveWriters();
        Map<String, Map<String, String>> inits = liveInitialisers();
        List<String> stillInitialised = new java.util.ArrayList<>();
        List<String> unwritten = new java.util.ArrayList<>();
        List<String> unresolved = new java.util.ArrayList<>();

        for (Moved m : moved) {
            String svc = services.get(m.writer());
            assertThat(svc).as("writer source " + m.writer() + " must exist").isNotNull();
            Map<String, String> declared = initialisersFor(inits, pkgOf(svc), m.entity());
            if (declared != null && declared.containsKey(m.field())) {
                stillInitialised.add(m.entity() + "#" + m.field());
            }
            // ⚠⚠ SCOPED TO THE NAMED METHOD, and that is a correction to this leg. F2-238 and
            // F2-239 asked whether the writer FILE contains `setX(` anywhere, which is satisfied by
            // the partial-update method itself or by any unrelated path — F2-194's
            // substring-satisfied-by-something-else, and it had been asserting this since F2-238.
            // The mutation harness proved it: deleting NdiFrameworkItemService#create's
            // `setMandatory(false)` default left the gate GREEN, because `importJson` and `update`
            // both contain that setter. Naming the method is self-verifying — a method that does not
            // exist, or that does not hold the setter, fails below.
            String body = methodBody(ServerStampedFieldContractTest.stripComments(svc), m.method());
            if (body == null) {
                unresolved.add(m.writer() + "#" + m.method() + " (no such method)");
            } else if (!body.contains(m.setter() + "(")) {
                unwritten.add(m.writer() + "#" + m.method() + " :: " + m.setter());
            }
        }

        assertThat(stillInitialised)
            .as("F2-238 removed these initialisers so the PATCH null-guards could fire. One is back, "
                + "which silently restores the full-replace behaviour on a route whose HTTP verb "
                + "promises the opposite")
            .isEmpty();
        assertThat(unresolved)
            .as("F2-240 — every moved default names the METHOD that owns it, and naming it is what "
                + "makes this leg mean anything. A method that does not exist means the entry is "
                + "stale, not that the default is fine")
            .isEmpty();
        assertThat(unwritten)
            .as("the default MOVED to the create path — every one of these columns is NOT NULL, so a "
                + "writer that stops setting it turns a silent reset into a 500 on create. That is "
                + "the one way this fix can go wrong and nothing else in the tree would notice")
            .isEmpty();
    }

    /**
     * F2-240 — {@link #methodBody} must really scope, or the leg above is the whole-file substring
     * check it replaced.
     *
     * <p>The synthetic corpus below is the exact shape that defeated the old check: a class where
     * the create path does NOT set the field and two other methods do. If {@code methodBody} stops
     * narrowing, {@code create} appears to hold {@code setMandatory(} and the fix's one real hazard
     * — a NOT NULL column with no create-path default — goes unnoticed again.
     */
    @Test
    void theCreatePathCheckIsScopedToItsMethod() {
        String corpus = """
            public class Fake {
                public Foo create(Foo req) {
                    req.setId(null);
                    return repo.save(req);
                }
                public Foo importJson(Foo req) {
                    req.setMandatory(false);
                    return repo.save(req);
                }
                public Foo update(Long id, Foo req) {
                    if (req.getMandatory() != null) cur.setMandatory(req.getMandatory());
                    return repo.save(cur);
                }
                static Foo defaults() {
                    Foo d = new Foo();
                    d.setMandatory(true);
                    return d;
                }
                public Foo callsDefaults() {
                    return defaults();
                }
            }
            """;

        assertThat(corpus)
            .as("the positive control: a whole-file substring check DOES find the setter here, which "
                + "is exactly why it passed over a create path that never sets it")
            .contains("setMandatory(");

        assertThat(methodBody(corpus, "create"))
            .as("scoped to create, the setter is absent — that is the finding the old check missed")
            .isNotNull()
            .doesNotContain("setMandatory(");
        assertThat(methodBody(corpus, "importJson")).contains("setMandatory(");
        assertThat(methodBody(corpus, "update")).contains("setMandatory(");
        assertThat(methodBody(corpus, "noSuchMethod"))
            .as("a name that does not resolve must be null so the leg can report it as stale rather "
                + "than silently treating the default as present")
            .isNull();

        // ⚠⚠ F2-294 — a package-private `static` factory. The pattern used to require an
        // access modifier, so this shape resolved to null and the Moved leg reported every
        // relocated settings default as "no such method". Both directions matter: the
        // declaration must resolve, and the CALL SITE in `callsDefaults` must not be mistaken
        // for one — `return defaults();` has no brace, so a resolver that matched it would be
        // reading an expression as a method body.
        assertThat(methodBody(corpus, "defaults"))
            .as("a package-private static factory must resolve — a relocated set of factory "
                + "defaults is exactly that shape, and F2-294 moved thirteen of them")
            .isNotNull()
            .contains("setMandatory(true)");
        assertThat(methodBody(corpus, "callsDefaults"))
            .as("the call site resolves as its OWN method, so the widening cannot make an "
                + "expression read as a declaration")
            .isNotNull()
            .contains("return defaults();");
    }

    /**
     * F2-240 — the widened writer scope must actually be carrying something.
     *
     * <p>{@link #liveWriters} adds request records to {@link #liveServices} so a default owned by a
     * {@code toEntity()} resolves. A widening that turns out to match nothing is indistinguishable
     * from a correct narrow scope, and the next reader deletes it — so assert that at least one
     * entry in the moved set names a writer which is NOT a service, and that it resolves. Two do
     * today ({@code ClimateRiskCreateRequest}, {@code GhgCreateRequest}); the assertion is "at least
     * one" because the count is not the point, the mechanism is.
     */
    @Test
    void theWriterScopeReallyIncludesNonServiceWriters() {
        Map<String, String> services = liveServices();
        Map<String, String> writers = liveWriters();

        assertThat(writers.keySet())
            .as("liveWriters must be a strict superset of liveServices, or the widening removed "
                + "something instead of adding")
            .containsAll(services.keySet());

        List<String> nonService = List.of("ClimateRiskCreateRequest", "GhgCreateRequest");
        for (String w : nonService) {
            assertThat(services)
                .as(w + " is deliberately NOT a *Service.java — that is the whole reason the writer "
                    + "scope had to be widened, so if it starts resolving as a service this "
                    + "assertion is measuring the wrong thing")
                .doesNotContainKey(w);
            assertThat(writers.get(w))
                .as(w + " owns a create-path default this gate asserts, so it must resolve through "
                    + "liveWriters(); if it cannot, everyRemovedInitialiserHasAWriterOnTheCreatePath "
                    + "fails on a CORRECT fix, which is the one failure a gate must never have")
                .isNotNull()
                .contains("toEntity");
        }
    }

    /**
     * F2-239 — the OTHER mechanism that makes an entity-bound partial update refuse, and it holds the
     * reset shut so the two must be fixed together.
     *
     * <p>F2-238 found one shape with two consequences, decided by whether the never-firing guard's
     * body was a write or a {@code throw}. This is the third: {@code @Valid} on a bound ENTITY whose
     * class declares a bean-validated mandatory field. Bean validation runs before the service is
     * entered and cannot tell "the caller omitted this field" from "the caller wants it blank", so
     * every legitimately partial body is refused with a 400 — while the service behind it is a
     * partial merge by construction.
     *
     * <p>Measured: <b>2</b> such routes existed, both {@code change_mgmt}, both from F2-30's
     * {@code @NotBlank titleAr}. Probed live before the fix: {@code POST /changes/{id}/tasks} and
     * {@code PUT /changes/tasks/{taskId}} answered <b>400 "titleAr: Field must not be blank"</b> to
     * the exact bodies {@code ChangeDetailPanel} sends, so neither the "Add task" button nor the
     * status toggle on the change implementation checklist had ever worked.
     *
     * <p>🔑 <b>F2-105 diagnosed this exact mechanism, wrote eleven lines explaining it, and fixed it
     * in the FRONTEND</b> — the PIR card resends {@code {...change}}. That closes one caller. It is
     * not a defence (F2-237), and the write-up is what made the two siblings in the same controller
     * look covered (F2-215). The invariant belongs on the ROW: the routes dropped {@code @Valid} and
     * the services refuse a BLANK mandatory field explicitly.
     *
     * <p>A hard zero with an EMPTY exemption map, because the fix has a better form available in every
     * case — bind a purpose-built request record (F2-213's structural direction), which puts the route
     * outside this population by construction. An exemption exists for a genuine full-replace PUT, and
     * the reverse leg refuses one that has gone stale.
     */
    @Test
    void noPartialUpdateRouteBindsAnEntityWithValid() {
        /** route -> why @Valid over a bound entity is correct there. Empty: there is no such case. */
        Map<String, String> allowed = Map.of();

        Pattern mapping = Pattern.compile("@(Put|Patch)Mapping\\b");
        // ⚠ `@Valid @RequestBody T x` is the house idiom, so the annotation sits BEFORE the one being
        // anchored on. A first draft only matched the other order and reported 0 of 2 — and 0 reads
        // exactly like a clean tree (F2-198's lineage, the quiet direction).
        Pattern body = Pattern.compile(
            "(@Valid\\s+)?@RequestBody\\s+(@Valid\\s+)?([\\w.<>]+)\\s+(\\w+)");
        Pattern mandatory = Pattern.compile(
            "@(?:NotBlank|NotNull|NotEmpty)\\b[^;]*?private\\s+[\\w.<>\\[\\]]+\\s+(\\w+)\\s*[;=]");

        // entity simple name -> the mandatory fields it declares
        Map<String, Set<String>> entityMandatory = new TreeMap<>();
        for (Path p : sources(".java")) {
            String raw = read(p);
            if (!raw.contains("@Entity")) continue;
            String src = ServerStampedFieldContractTest.stripComments(raw);
            Matcher cm = Pattern.compile("\\bclass\\s+(\\w+)").matcher(src);
            if (!cm.find()) continue;
            Set<String> fields = new TreeSet<>();
            Matcher fm = mandatory.matcher(src);
            while (fm.find()) fields.add(fm.group(1));
            if (!fields.isEmpty()) entityMandatory.merge(cm.group(1), fields, (a, b) -> {
                Set<String> u = new TreeSet<>(a); u.addAll(b); return u;
            });
        }

        List<String> offenders = new java.util.ArrayList<>();
        int entityBound = 0;
        for (Path p : sources("Controller.java")) {
            String src = ServerStampedFieldContractTest.stripComments(read(p));
            Matcher base = Pattern.compile("@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"")
                .matcher(src);
            String prefix = base.find() ? base.group(1) : "";
            List<Integer> marks = new java.util.ArrayList<>();
            Matcher mm = mapping.matcher(src);
            while (mm.find()) marks.add(mm.start());
            for (int i = 0; i < marks.size(); i++) {
                String seg = src.substring(marks.get(i),
                    i + 1 < marks.size() ? marks.get(i + 1) : src.length());
                Matcher bm = body.matcher(seg);
                if (!bm.find()) continue;
                String bound = bm.group(3).substring(bm.group(3).lastIndexOf('.') + 1);
                if (!entityMandatory.containsKey(bound)) continue;
                entityBound++;
                if (bm.group(1) == null && bm.group(2) == null) continue;
                Matcher rm = Pattern.compile("@\\w+Mapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"")
                    .matcher(seg);
                String route = prefix + (rm.lookingAt() ? rm.group(1) : "");
                if (!allowed.containsKey(route)) {
                    offenders.add(route + "  binds " + bound + " (mandatory: "
                        + entityMandatory.get(bound) + ")  [" + p.getFileName() + "]");
                }
            }
        }

        // Anti-vacuity. The wrong answers are BOTH regexes silently matching nothing, and each
        // returns 0 — indistinguishable from a clean tree. The floor sits above zero and below the
        // measured population (F2-215: it has to sit above the largest WRONG answer).
        assertThat(entityMandatory).as(
            "the entity walk must find the types that declare a mandatory field — 9 of 411 do, and a "
            + "regex that stops matching reports none of them")
            .hasSizeGreaterThanOrEqualTo(6);
        assertThat(entityBound).as(
            "the controller walk must find PUT/PATCH routes binding one of those entities; 94 of 172 "
            + "body-taking PUT/PATCH routes bind an entity at all, so zero here means the scan broke")
            .isGreaterThanOrEqualTo(2);

        assertThat(offenders).as(
            "⚠⚠ @Valid on a bound ENTITY refuses every partial body, because bean validation cannot "
            + "tell an OMITTED mandatory field from a BLANK one — and the service behind these routes "
            + "is a partial merge. F2-105 hit this on ChangeRequest and fixed it in one frontend "
            + "caller; the two siblings in the same controller then answered 400 to every request the "
            + "UI made, for months. Fix it by binding a purpose-built request record (best — the field "
            + "then cannot arrive at all), or by dropping @Valid and refusing a BLANK value in the "
            + "service. Do not fix it by making every client resend the mandatory fields.%n%n%s",
            String.join("\n", offenders))
            .isEmpty();

        assertThat(allowed).as(
            "the reverse leg: an exemption that no longer names a route in the population is a reason "
            + "for something that is not there, which reads exactly like a reason for something that "
            + "is (F2-199)")
            .allSatisfy((route, why) -> assertThat(why).isNotBlank());
    }

    /**
     * The self-test for the leg above (F2-195). It passed on its first run, and that is also what a
     * rotted regex looks like — so the two patterns are driven against the literal PRE-FIX shapes and
     * against the two forms that must NOT fire.
     *
     * <p>⚠ The one that matters is the annotation ORDER. `@Valid @RequestBody T x` is this codebase's
     * idiom; a pattern anchored on `@RequestBody` and looking only forwards reported 0 of 2, which is
     * indistinguishable from a clean tree.
     */
    @Test
    void theValidOverAPartialMergeDetectorFiresOnThePreFixShape() {
        Pattern body = Pattern.compile(
            "(@Valid\\s+)?@RequestBody\\s+(@Valid\\s+)?([\\w.<>]+)\\s+(\\w+)");
        Pattern mandatory = Pattern.compile(
            "@(?:NotBlank|NotNull|NotEmpty)\\b[^;]*?private\\s+[\\w.<>\\[\\]]+\\s+(\\w+)\\s*[;=]");

        // ── the mandatory-field pattern ───────────────────────────────────────
        String preFixEntity = """
            @Entity
            public class ChangeTask {
                @NotBlank
                @Column(name = "title_ar", length = 500)
                private String titleAr;
                @Column(name = "owner_name") private String ownerName;
            }
            """;
        Matcher fm = mandatory.matcher(preFixEntity);
        assertThat(fm.find()).as("the detector must see @NotBlank separated from its field by a "
            + "@Column annotation — the shape every entity in this tree uses").isTrue();
        assertThat(fm.group(1)).isEqualTo("titleAr");
        assertThat(mandatory.matcher("private String ownerName;").find())
            .as("and must NOT fire on an unannotated field").isFalse();

        // ── the @Valid position, in BOTH orders and in neither ────────────────
        Matcher house = body.matcher("public ChangeTask updateTask("
            + "@PathVariable Long taskId, @Valid @RequestBody ChangeTask body) {");
        assertThat(house.find()).isTrue();
        assertThat(house.group(1)).as("⚠ the house idiom puts @Valid BEFORE @RequestBody, and a "
            + "pattern that only looks after it reports zero — the quiet direction").isNotNull();
        assertThat(house.group(3)).isEqualTo("ChangeTask");

        Matcher other = body.matcher("update(@RequestBody @Valid ChangeRequest body)");
        assertThat(other.find()).isTrue();
        assertThat(other.group(2)).as("the other order must pair too").isNotNull();

        Matcher fixed = body.matcher("public ChangeTask updateTask("
            + "@PathVariable Long taskId, @RequestBody ChangeTask body) {");
        assertThat(fixed.find()).isTrue();
        assertThat(fixed.group(1)).as("the POST-FIX shape must report no @Valid").isNull();
        assertThat(fixed.group(2)).isNull();
        assertThat(fixed.group(3)).as("while still resolving the bound type, or the population "
            + "silently shrinks instead of the offender list").isEqualTo("ChangeTask");
    }

    /**
     * The synthetic self-test (F2-195): the corpus assertions above pass both when the tree is clean
     * and when the detector has rotted, so the detector is driven against the literal pre-fix shape
     * and against three shapes it must NOT flag.
     */
    @Test
    void theDetectorFiresOnThePreFixShapeAndNotOnItsNeighbours() {
        String entity = """
            @Entity
            public class Widget {
                private java.math.BigDecimal score = java.math.BigDecimal.ZERO;
                private String note = null;
                private String owner;
            }
            """;
        Map<String, Map<String, String>> inits = initialisedFields(List.of(entity));
        assertThat(inits.get("Widget"))
            .as("an initialised field is in; an explicit null and a bare declaration are not")
            .containsOnlyKeys("score");

        // (a) the pre-fix shape: loads a row, guards an initialised field, writes it
        String broken = """
            public class WidgetService {
                public Widget update(Long id, Widget req) {
                    Widget cur = get(id);
                    if (req.getScore() != null) cur.setScore(req.getScore());
                    return repo.save(cur);
                }
            }
            """;
        assertThat(measure(Map.of("WidgetService", broken), inits))
            .containsExactly("WidgetService#update :: Widget#score");

        // (b) the fixed shape: the route does not write the field at all
        String fixed = """
            public class WidgetService {
                public Widget update(Long id, Widget req) {
                    Widget cur = get(id);
                    if (req.getOwner() != null) cur.setOwner(req.getOwner());
                    return repo.save(cur);
                }
            }
            """;
        assertThat(measure(Map.of("WidgetService", fixed), inits)).isEmpty();

        // (c) a CREATE: no row is loaded, so writing the initialiser is the intent
        String create = """
            public class WidgetService {
                public Widget create(Widget req) {
                    if (req.getScore() != null) req.setScore(req.getScore());
                    return repo.save(req);
                }
            }
            """;
        assertThat(measure(Map.of("WidgetService", create), inits))
            .as("a create has nothing to overwrite")
            .isEmpty();

        // (d) an explicitly-null field: the guard reads exactly as it behaves
        String honest = """
            public class WidgetService {
                public Widget update(Long id, Widget req) {
                    Widget cur = get(id);
                    if (req.getNote() != null) cur.setNote(req.getNote());
                    return repo.save(cur);
                }
            }
            """;
        assertThat(measure(Map.of("WidgetService", honest), inits)).isEmpty();
    }

    // ── F2-291 ───────────────────────────────────────────────────────────────

    /**
     * The load test must be a SHAPE, not a list of loader names — and a save's own result is not a
     * load.
     *
     * <p>Both halves are here because both were wrong once. The vocabulary {@code get|find\w*} put
     * every {@code require(id)}-shaped service outside the population (F2-217), and the first
     * structural draft counted {@code T saved = repo.save(c);} as a load, which is the detector bug
     * F2-213 recorded and put a create into the work list.
     */
    @Test
    void theLoadTestIsStructuralAndNotAVocabulary() {
        assertThat(loadsARowOf("EthicsCase c = require(id); apply(c, req);", "EthicsCase"))
            .as("the loader is named `require` in two whole modules — a name vocabulary cannot see it")
            .isTrue();
        assertThat(loadsARowOf("Widget w = mustGetOrThrow(id);", "Widget")).isTrue();
        assertThat(loadsARowOf("Widget w = this.repo.findByIdAndOrganisationId(id, org).get();", "Widget"))
            .isTrue();
        assertThat(loadsARowOf("EthicsCase c = new EthicsCase(); apply(c, req);", "EthicsCase"))
            .as("a create builds its row, so writing the initialiser there is the point")
            .isFalse();
        assertThat(loadsARowOf("ChangeRequest c = new ChangeRequest();\n"
                             + "ChangeRequest saved = changeRepo.save(c);", "ChangeRequest"))
            .as("⚠ F2-213's detector bug: the save's OWN RESULT is not evidence a row was loaded, "
                + "and counting it reported ChangeManagementService#create as a defect site")
            .isFalse();
        assertThat(loadsARowOf("List<Widget> all = repo.saveAll(xs);", "Widget")).isFalse();
    }

    /**
     * The one-hop private-helper follow is load-bearing, not decorative.
     *
     * <p>Asserted by MEASUREMENT rather than by argument (F2-192): the guards for these members are
     * not in the public method at all, so if the follow were removed the population would collapse
     * back to the empty invariant that read as a finished sweep for three slices.
     */
    @Test
    void theOneHopHelperFollowIsLoadBearing() {
        Map<String, String> services = liveServices();
        Map<String, Map<String, String>> inits = liveInitialisers();

        // ⚠⚠ F2-294 re-scoped this population from WORK_LIST to the CLOSED_BY_* union, which is
        // what the comment below always said would have to happen. Those members' NULL GUARDS are
        // untouched by the fix — only their initialisers went — so "can the one-hop follow see this
        // guard?" is still a real question about every one of them, and the answer no longer depends
        // on there being unfixed work left. A member whose guard was inlined away, or a service that
        // stopped existing, fails here rather than silently lowering a count.
        Set<String> guardsStillPresent = new java.util.TreeSet<>(CLOSED_BY_F2_291);
        guardsStillPresent.addAll(CLOSED_BY_F2_292);
        guardsStillPresent.addAll(CLOSED_BY_F2_293);
        guardsStillPresent.addAll(CLOSED_BY_F2_294);
        guardsStillPresent.addAll(WORK_LIST);

        int helperOnly = 0;
        for (String member : guardsStillPresent) {
            String service = member.substring(0, member.indexOf('#'));
            String method = member.substring(member.indexOf('#') + 1, member.indexOf(" :: "));
            String field = member.substring(member.lastIndexOf('#') + 1);
            String src = services.get(service);
            assertThat(src).as("work-list member names a service that must exist: " + member).isNotNull();
            String stripped = ServerStampedFieldContractTest.stripComments(src);
            String body = methodBody(stripped, method);
            assertThat(body).as("work-list member names a method that must exist: " + member).isNotNull();
            boolean inline = body.matches("(?s).*\\.\\s*(?:get|is)"
                + Character.toUpperCase(field.charAt(0)) + field.substring(1)
                + "\\s*\\(\\s*\\)\\s*!=\\s*null.*");
            if (!inline) helperOnly++;
        }
        // ⚠ The history of this number is worth keeping, because every move had a different
        // cause. F2-291 opened it at 8; F2-293 re-measured it to 5 and the reason mattered — all
        // five members it closed had their guards in a private `apply`, so the population fell
        // BECAUSE of the fix rather than because the detector rotted (F2-213). F2-294 then emptied
        // WORK_LIST entirely, which would have taken this to 0 and made the leg vacuous — the exact
        // failure this comment used to predict. Re-scoped, not lowered.
        assertThat(helperOnly)
            .as("members whose guard is NOT in the public method, i.e. members only the one-hop "
                + "helper follow can see. Delete the follow and every one of these disappears, "
                + "which is what made this gate's hard zero look like a finished sweep")
            // Measured 21 of the 44 closed members on the day this was re-scoped. The floor is
            // deliberately BELOW that and far above the largest wrong answer, which is ZERO —
            // deleting the one-hop follow, or a service file failing to resolve, collapses this
            // to nothing, and nothing is indistinguishable from a finished sweep (F2-215: a
            // floor must sit above the largest WRONG answer, and you have to know what that is).
            // 15 leaves room for a few legitimate inlinings without tolerating a dead detector.
            .isGreaterThanOrEqualTo(15);

        // And the mechanism itself resolves, in the two shapes the tree uses.
        assertThat(ServerStampedFieldContractTest.helperNullGuardedFields(
                ServerStampedFieldContractTest.stripComments(services.get("DisclosureService"))))
            .as("`apply` is the house name")
            .containsKey("apply");
        assertThat(ServerStampedFieldContractTest.helperNullGuardedFields(
                ServerStampedFieldContractTest.stripComments(services.get("AmlAlertService"))))
            .as("`applyEditable` is the other one, and a helper-NAME list would have missed it")
            .containsKey("applyEditable");
        assertThat(callsHelperWithRequest("apply(c, req);", "apply", Set.of("req")))
            .as("attribution needs the CALL to pass caller data — a helper called with two server "
                + "values guards nothing of the caller's (F2-231's rule on the write side)")
            .isTrue();
        assertThat(callsHelperWithRequest("apply(c, existing);", "apply", Set.of("req"))).isFalse();
    }

    /**
     * The nine pairs F2-291 closed must stay closed, in BOTH directions.
     *
     * <p>A tolerated {@link #WORK_LIST} entry and a fixed field are indistinguishable from a count,
     * so these are named: the entity must declare no initialiser for them (or the guard in
     * {@code apply} goes back to firing on every request and resetting a live row), and the measured
     * population must not contain them (or the fix has been reverted while the work list absorbed it).
     */
    /**
     * F2-292 — the duplicate-simple-name hazard in this gate is CROSS-ATTRIBUTION, and the obvious
     * fix for the omission half manufactures a finding.
     *
     * <p>Three {@code *Service} simple names are declared twice under a {@code /service/} package,
     * and {@link #liveServices} keys on the simple name, so only one twin of each is ever measured.
     * Making it merge looks like the fix and produces
     * {@code AuditEngagementService#update :: AuditEngagement#auditType} — a phantom, because
     * {@code audit_eng} owns the initialiser and has no {@code update}, while {@code compliance}
     * owns the guard and has no initialiser. The entity map's {@code computeIfAbsent} union is what
     * supplies the other half.
     *
     * <p>This leg is a TRIPWIRE, not a coverage claim: it fails if the phantom reappears, so a
     * future reader cannot "fix" the omission without also fixing the pairing. It deliberately
     * asserts a duplicate still EXISTS, because without that floor it passes vacuously the day
     * somebody renames the last colliding pair — and a green assertion and a dead one are
     * indistinguishable from outside (F2-219).
     */
    @Test
    void theDuplicateNameHazardIsCrossAttributionNotOmission() {
        Map<String, Long> perName = new java.util.LinkedHashMap<>();
        for (Path p : sources("Service.java")) {
            perName.merge(p.getFileName().toString().replace(".java", ""), 1L, Long::sum);
        }
        List<String> dups = perName.entrySet().stream().filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey).sorted().collect(Collectors.toList());

        assertThat(dups)
            .as("this leg is only meaningful while the tree still declares a colliding *Service "
                + "name; if it ever empties, delete the leg rather than let it pass over nothing")
            .isNotEmpty()
            .contains("AuditEngagementService");

        assertThat(measure(liveServicesByFqn(), liveInitialisers()))
            .as("a merge across same-named services pairs one module's initialiser with another "
                + "module's guard, and this is the pair it invents: audit_eng initialises "
                + "auditType and has no update; compliance guards it and has no initialiser. A "
                + "gate that names a table where no defect exists is worse than one that misses "
                + "(F2-214). F2-295 keyed the service map by fully-qualified name, so this pin now "
                + "asserts the phantom stays absent under the WIDER population as well — which is "
                + "the claim, since the merge variant is what invented it.")
            .doesNotContain("AuditEngagementService#update :: AuditEngagement#auditType");
    }
    /**
     * F2-295 — the population {@link #measure} scans is EVERY service source, and that is now a
     * measured number rather than an implication.
     *
     * <p>{@link #liveServices} keys on the file's simple name over a {@code .sorted()} walk, so the
     * last path of a colliding pair wins the {@code put} and the other file is <b>never scanned</b>.
     * Four files today, three of them real services:
     * {@code module/audit_eng/service/AuditEngagementService},
     * {@code module/board_pack/service/BoardPackService},
     * {@code module/compliance/service/RegulatoryChangeService}, and
     * {@code module/cyber/entity/CloudService} — an ENTITY, which the {@code Service.java} suffix
     * catches anyway, and whose presence in a map called "services" is its own small finding.
     *
     * <p>🔑 <b>This is the assertion the fix needs, and the site set is not.</b> None of the three
     * dropped services holds a member of this family today, so the measured set is empty either way
     * and a leg that only checked {@link #WORK_LIST} would pass over the defect — F2-205's rule that
     * a hard zero over a population the instrument cannot see is the most reassuring possible
     * artefact. What changed is the DENOMINATOR, so the denominator is what is asserted.
     *
     * <p>⚠ Both numbers are asserted, not just the good one: the FQN map must equal the file count
     * (nothing dropped) and the simple-name map must be exactly four short (the drop is real, so a
     * reader cannot mistake this leg for decoration). Reverting {@link #measure}'s argument to
     * {@link #liveServices} is what the second assertion catches.
     *
     * <p>⚠ It is deliberately a latent-hazard closure and says so: F2-283's rule is that reporting
     * one as a defect discovery is the error this register keeps warning about.
     */
    @Test
    void theServiceMapScansEveryServiceSource() {
        List<Path> paths = sources("Service.java");
        Map<String, Long> perName = new java.util.LinkedHashMap<>();
        for (Path p : paths) {
            perName.merge(p.getFileName().toString().replace(".java", ""), 1L, Long::sum);
        }
        long shadowed = perName.values().stream().mapToLong(v -> v - 1).sum();

        assertThat(paths.size())
            .as("service-source floor — measured 327, so this sits above a walk that resolved only "
                + "part of module/ rather than merely above zero (F2-215)")
            .isGreaterThan(300);

        assertThat(liveServicesByFqn())
            .as("keyed by fully-qualified name, so no file can be shadowed out of the population of "
                + "a hard-zero invariant")
            .hasSize(paths.size());

        assertThat(shadowed)
            .as("the drop the simple-name keying causes is REAL and this is its size; if it ever "
                + "reaches zero the tree has stopped colliding and this leg should be deleted "
                + "rather than left passing over nothing")
            .isEqualTo(4L);

        assertThat(liveServices())
            .as("...and the old keying is exactly that many short, which is the assertion a revert "
                + "of measure()'s argument fails. The site set cannot catch it: none of the three "
                + "dropped services holds a member of this family today, so this is a latent-hazard "
                + "closure and not a defect discovery (F2-283)")
            .hasSize(paths.size() - (int) shadowed);

        List<String> dropped = new java.util.ArrayList<>();
        for (Path p : paths) {
            String simple = p.getFileName().toString().replace(".java", "");
            if (perName.get(simple) > 1
                && !p.equals(paths.stream().filter(
                    q -> q.getFileName().toString().replace(".java", "").equals(simple))
                    .reduce((a, b) -> b).orElseThrow())) {
                dropped.add(p.toString().replace('\\', '/').replaceAll(".*com/tss/grc/", ""));
            }
        }
        assertThat(dropped)
            .as("named exactly, so a NEW collision fails this leg and forces the next reader to ask "
                + "whether the newly shadowed file holds a guard — which is the question nobody was "
                + "in a position to ask while three files were silently outside the population")
            .containsExactlyInAnyOrder(
                "module/audit_eng/service/AuditEngagementService.java",
                "module/board_pack/service/BoardPackService.java",
                "module/compliance/service/RegulatoryChangeService.java",
                "module/cyber/entity/CloudService.java");

        // ⚠⚠ AND THE CALL SITES, because everything above tests the two ACCESSORS and none of it
        // tests which one the measurement calls. F2-274's rule verbatim: a leg that exercises a
        // primitive says nothing about whether the code under review CALLS it, so the production
        // path can stop using the thing under test with every assertion green. That mutation is
        // invisible to the site set as well, because the measured set is empty either way — this
        // is the only leg that sees it.
        // ⚠⚠ STRING LITERALS ARE STRIPPED AS WELL AS COMMENTS, and the first draft of this leg is
        // why: it read comment-stripped source, and the two assertions BELOW quote the very call
        // shapes they are about, so the ban matched its own message and the leg failed on a correct
        // tree. F2-193's rule — a detector that cannot tell a quotation from a claim forces every
        // correction to paraphrase away its own evidence — landing on a leg written after quoting it.
        String code = withoutStringLiterals(
            ServerStampedFieldContractTest.stripComments(read(java.nio.file.Path.of(
                "src/test/java/com/tss/grc/security/NeverFiringNullGuardContractTest.java"))));
        assertThat(code)
            .as("no measurement may be taken over the simple-name map — that is the shadowing this "
                + "slice removed, and reverting one argument restores it silently")
            .doesNotContain("measure(liveServices()");
        assertThat(code.split(Pattern.quote("measure(liveServicesByFqn()"), -1).length - 1)
            .as("every measure() call site reads the fully-qualified map — measured 4")
            .isEqualTo(4);
        assertThat(withoutStringLiterals("a = \"measure(liveServices()\"; b = c;"))
            .as("the stripper is load-bearing and pinned: without it the ban above matches its own "
                + "message, and a gate that fails on correct code trains the next reader to edit "
                + "the assertion rather than the code (F2-214)")
            .doesNotContain("measure(liveServices()")
            .contains("b = c;");
        assertThat(withoutStringLiterals("char q = '\"'; keep(); String s = \"drop\";"))
            .as("and a CHAR literal holding a double quote must not toggle the scanner — the first "
                + "draft inverted every span after this method's own quote character, so a stripper "
                + "that mis-identifies one span does not degrade, it inverts")
            .contains("keep();")
            .doesNotContain("drop");

        // ⚠⚠ THE OTHER KEYED MAP, because shipping a slice about keying ambiguity that closes one of
        // two maps is F2-215's two-of-N in exactly the shape this lineage keeps finding.
        // liveWriters() is a LOOKUP map and simple-name keying is right for it — `Moved` records name
        // a writer by simple name — but a lookup of an AMBIGUOUS name silently returns whichever
        // path sorted last, which is F2-287's rule: an unresolved ambiguity must never fall back to
        // something confident, because an entry resolved against the wrong module reads exactly like
        // one resolved against the right one. So no held record may name a colliding simple name.
        Set<String> ambiguousNames = perName.entrySet().stream()
            .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toSet());
        assertThat(ambiguousNames)
            .as("the collision set this leg is about — if it empties, the tree has stopped colliding "
                + "and both this and the assertion below should go rather than pass over nothing")
            .contains("AuditEngagementService", "BoardPackService", "RegulatoryChangeService");

        assertThat(ambiguousLookupsIn(code, ambiguousNames))
            .as("a by-name lookup of a colliding service resolves to whichever path sorted LAST. "
                + "Nothing does that today — 58 *Request.java files carry 58 distinct names and no "
                + "Moved record names a colliding service — so this is a tripwire, and what it "
                + "protects is the direction that fails confidently rather than loudly (F2-287)")
            .isEmpty();
        // ⚠⚠ AND THE POSITIVE, because the assertion above is VACUOUS on a clean corpus: the
        // mutation harness blinded the detector and SURVIVED, since a "collect the violations,
        // assert the list empty" leg produces an empty list whether it is working or dead (F2-250).
        // An empty list is what both a correct gate and a rotted one return, so the detector has to
        // prove it can fire.
        assertThat(ambiguousLookupsIn("String svc = services.get(\"BoardPackService\");",
                                      ambiguousNames))
            .as("the detector must be able to see the thing it forbids, or its emptiness above is "
                + "evidence about the corpus and not about the code")
            .containsExactly("BoardPackService");
        assertThat(ambiguousLookupsIn("String svc = services.get(\"KycService\");", ambiguousNames))
            .as("...and must not fire on a name that is declared once — a tripwire that flags an "
                + "unambiguous lookup fails on correct code (F2-214)")
            .isEmpty();
    }

    /**
     * F2-295 — {@link #label} in both directions, over a synthetic corpus.
     *
     * <p>The ambiguous branch is unreachable from the live tree today, because no colliding service
     * name produces a site — so without a synthetic corpus it would be a branch nothing checks, and
     * "it passed" would mean "it never ran" (F2-195's rule about a scan that is green because the
     * app is clean being indistinguishable from one whose regex has rotted).
     *
     * <p>⚠ The unambiguous direction matters as much: labelling every site by module would rename
     * all 27 {@code CLOSED_BY_F2_294} entries and every {@code CLOSED_BY_F2_291/292/293} entry, and
     * a keying fix that renames what a reader has been told is held is indistinguishable from a
     * slice that changed what those entries mean.
     */
    @Test
    void anAmbiguousServiceNameIsLabelledByModule() {
        Set<String> unique = Set.of(
            "com.tss.grc.module.ethics.service.KycService",
            "com.tss.grc.module.compliance.service.AuditEngagementService");
        assertThat(label("com.tss.grc.module.ethics.service.KycService", unique))
            .as("one declaration of the name — the label stays the simple name, so every held entry "
                + "keeps its established Service#method :: Entity#field form")
            .isEqualTo("KycService");

        Set<String> ambiguous = Set.of(
            "com.tss.grc.module.audit_eng.service.AuditEngagementService",
            "com.tss.grc.module.compliance.service.AuditEngagementService");
        assertThat(label("com.tss.grc.module.audit_eng.service.AuditEngagementService", ambiguous))
            .isEqualTo("audit_eng.AuditEngagementService");
        assertThat(label("com.tss.grc.module.compliance.service.AuditEngagementService", ambiguous))
            .as("two files, two labels — F2-287's rule that an unresolved ambiguity must never fall "
                + "back to something confident, because a finding naming the wrong module reads "
                + "exactly like a finding naming the right one")
            .isEqualTo("compliance.AuditEngagementService");

        assertThat(label("SyntheticService", Set.of("SyntheticService")))
            .as("a synthetic corpus keyed by bare simple names must pass through unchanged, or every "
                + "self-test in this class starts asserting a different label from the live tree")
            .isEqualTo("SyntheticService");
    }

    // ── F2-298: the CONJUNCTIVE form, which this class's own pattern cannot see ──────────────

    /**
     * A conjunctive null guard whose other conjunct compares the request against the LOADED ROW.
     *
     * <p>⚠⚠ {@link #measure} requires a {@code )} immediately after {@code null} — the null test
     * must be the <b>entire</b> condition — so
     * {@code if (req.getStatus() != null && !sameState(req.getStatus(), existing.getStatus()))}
     * is outside its population <b>by construction</b>. That is not a bug in {@code measure}: it
     * is why F2-238's member list did not name {@code BcpPlan} (F2-297) or {@code BcmPlan}
     * (F2-298), and it is F2-198's shape where the narrowing is <b>how much of the {@code if} the
     * null test occupies</b>. This class was a hard zero over both of them.
     *
     * <p>🔑 <b>The discriminator is what the OTHER conjunct compares against, and it had to be
     * read rather than guessed.</b> F2-297 measured the raw conjunctive shape at 4 sites; reading
     * them gives 1 real and 3 refuted, and a widening that ignored the difference would have
     * failed on correct code — the one thing a gate must never do (F2-214):
     *
     * <ul>
     *   <li><b>real</b> — the other conjunct reads the loaded row, so an initialiser makes the
     *       REFUSAL fire on every row not already in the initial state, and the 400 names a
     *       transition the caller never asked for ({@code BcmService#updatePlan}, F2-298);</li>
     *   <li><b>refuted</b> — no row is loaded at all, so it is a CREATE, and there an initialiser
     *       makes a vocabulary VALIDATION always <i>run</i>, which is stronger rather than weaker
     *       ({@code ConsentRecordService#grant}, two fields);</li>
     *   <li><b>refuted</b> — the other conjunct compares against a CONSTANT vocabulary rather than
     *       the row, and the service normalises to upper case before testing it — the idiom
     *       CLAUDE.md names as the pattern to copy — with no guarded lifecycle route anywhere on
     *       the register to bypass ({@code DetectionRuleService#save}).</li>
     * </ul>
     *
     * <p>So the leg is scoped to the row-comparing form. It ships as a <b>hard zero with no
     * exemption map</b>, which is safe for the same reason {@code measure}'s is: removing the
     * initialiser and letting the create path own the default is always available, so there is no
     * correct code for it to fail on.
     */
    static Set<String> conjunctiveRowComparisons(Map<String, String> serviceSources,
                                                 Map<String, Map<String, String>> inits) {
        Set<String> out = new TreeSet<>();
        for (var entry : serviceSources.entrySet()) {
            String service = label(entry.getKey(), serviceSources.keySet());
            String src = ServerStampedFieldContractTest.stripComments(entry.getValue());
            String pkg = pkgOf(entry.getValue());
            for (ServerStampedFieldContractTest.Method m
                     : ServerStampedFieldContractTest.publicMethods(src)) {
                Map<String, String> ptypes =
                    ServerStampedFieldContractTest.requestParamTypes(m.params());
                for (var p : ptypes.entrySet()) {
                    Map<String, String> declared = initialisersFor(inits, pkg, p.getValue());
                    if (declared == null) continue;
                    // Same narrowing as measure(): on a create, writing the initialiser is what is
                    // meant to happen, and a validation that always runs is not a defect.
                    if (!loadsARowOf(m.body(), p.getValue())) continue;
                    String row = loadedLocalOf(m.body(), p.getValue());
                    if (row == null) continue;
                    Matcher g = Pattern.compile("if\\s*\\(\\s*" + Pattern.quote(p.getKey())
                        + "\\s*\\.\\s*(?:get|is)([A-Z]\\w*)\\s*\\(\\s*\\)\\s*!=\\s*null\\s*&&"
                        + "([^{;]*)").matcher(m.body());
                    while (g.find()) {
                        String f = Character.toLowerCase(g.group(1).charAt(0))
                            + g.group(1).substring(1);
                        if (!declared.containsKey(f)) continue;
                        // the other conjunct must read the LOADED ROW, not a constant vocabulary
                        if (!Pattern.compile("\\b" + Pattern.quote(row)
                                + "\\s*\\.\\s*(?:get|is)[A-Z]").matcher(g.group(2)).find()) continue;
                        out.add(service + "#" + m.name() + " :: " + p.getValue() + "#" + f);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Name of the local this method loaded a row of {@code type} into, or null.
     *
     * <p>⚠⚠ The receiver is OPTIONAL, and getting that wrong made this whole leg vacuous on the
     * live tree. The first draft demanded {@code \w+\s*\.\s*(?:get|find|…)} — a receiver dot —
     * and {@code BcmService#updatePlan} loads its row with a bare {@code getPlan(id)}, so
     * {@link #loadedLocalOf} returned null, the site was skipped, and the hard zero above passed
     * <b>with the defect still in the tree</b>. 🔑 <b>The synthetic self-test passed too, because
     * I had written its fixture to match my regex rather than the real code</b> — a positive
     * control written against the shape you imagine certifies nothing (F2-208). Only restoring the
     * initialiser and re-running against the real tree found it. The bare-call form is a
     * must-match case in the self-test below for exactly that reason.
     */
    static String loadedLocalOf(String body, String type) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(type) + "\\s+(\\w+)\\s*=\\s*"
            + "(?:\\w+\\s*\\.\\s*)?(?:get|find|require|load|fetch)\\w*\\s*\\(").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    @Test
    void noConjunctiveRefusalSitsOverAnInitialisedField() {
        assertThat(conjunctiveRowComparisons(liveServicesByFqn(), liveInitialisers()))
            .as("F2-298 — a conjunctive null guard that compares the request against the LOADED ROW, "
                + "over a field the entity INITIALISES, refuses every row that is not already in "
                + "the initial state and answers 400 naming a transition the caller never asked "
                + "for. measure() cannot see this shape (its pattern needs the null test to be the "
                + "whole condition), which is why F2-238's sweep missed BcpPlan and BcmPlan. Remove "
                + "the initialiser and let the create path own the default.")
            .isEmpty();
    }

    @Test
    void theConjunctiveDetectorFiresOnThePreFixShapeAndNotOnItsNeighbours() {
        // A positive control carrying the literal pre-fix shape, and the two refuted neighbours,
        // because a green reading over a fixed tree is indistinguishable from a rotted regex
        // (F2-195). All three are synthetic: the live tree is expected to be empty.
        Map<String, Map<String, String>> inits = Map.of(
            "p.Thing", Map.of("status", "\"DRAFT\""));

        // ⚠ The row is loaded with a BARE call and the guard SPANS TWO LINES, because that is what
        // BcmService#updatePlan actually looks like. The first draft of this fixture used
        // `repo.getThing(id)` on one line — a shape I invented — and it passed while the leg was
        // vacuous against the live tree (see loadedLocalOf). A control has to carry the shape that
        // EXISTS.
        String preFix = "package p; class S {\n"
            + "  public Thing update(Long id, Thing req) {\n"
            + "    Thing existing = getThing(id);\n"
            + "    if (req.getStatus() != null\n"
            + "            && !ServerOwnedState.sameState(req.getStatus(), existing.getStatus()))\n"
            + "      throw new IllegalArgumentException(\"no\");\n"
            + "    return repo.save(existing);\n"
            + "  }\n"
            + "}\n";
        assertThat(conjunctiveRowComparisons(Map.of("S", preFix), inits))
            .as("the literal pre-fix shape — bare loader call, guard wrapped across two lines — "
                + "must be found, or this leg is decoration")
            .containsExactly("S#update :: Thing#status");

        String withReceiver = preFix.replace("getThing(id)", "repo.getThing(id)");
        assertThat(conjunctiveRowComparisons(Map.of("S", withReceiver), inits))
            .as("and the receiver-bearing form must be found too — both spellings occur in the tree")
            .containsExactly("S#update :: Thing#status");

        String vocabulary = "package p; class S {\n"
            + "  public Thing update(Long id, Thing req) {\n"
            + "    Thing existing = repo.getThing(id);\n"
            + "    if (req.getStatus() != null && !STATUSES.contains(req.getStatus().toUpperCase()))\n"
            + "      throw new IllegalArgumentException(\"no\");\n"
            + "    return repo.save(existing);\n"
            + "  }\n"
            + "}\n";
        assertThat(conjunctiveRowComparisons(Map.of("S", vocabulary), inits))
            .as("a conjunct comparing against a CONSTANT vocabulary is a validation, not a "
                + "refusal-against-stored — DetectionRuleService#save is exactly this and is "
                + "correct, so flagging it would fail on working code (F2-214)")
            .isEmpty();

        String create = "package p; class S {\n"
            + "  public Thing grant(Thing body) {\n"
            + "    if (body.getStatus() != null && !STATUSES.contains(body.getStatus()))\n"
            + "      throw new IllegalArgumentException(\"no\");\n"
            + "    body.setId(null);\n"
            + "    return repo.save(body);\n"
            + "  }\n"
            + "}\n";
        assertThat(conjunctiveRowComparisons(Map.of("S", create), inits))
            .as("a CREATE loads no row, and there an initialiser makes a validation always RUN — "
                + "ConsentRecordService#grant is exactly this and is correct")
            .isEmpty();
    }


    @Test
    void theEthicsAndKycConsequenceFlagsStayClosed() {
        Set<String> closed = new TreeSet<>(CLOSED_BY_F2_291);
        closed.addAll(CLOSED_BY_F2_292);
        closed.addAll(CLOSED_BY_F2_293);
        closed.addAll(CLOSED_BY_F2_294);
        assertThat(measure(liveServicesByFqn(), liveInitialisers()))
            .as("a reverted fix must FAIL rather than be absorbed by the work list")
            .doesNotContainAnyElementsOf(closed);

        Map<String, Map<String, String>> inits = liveInitialisers();
        Map<String, String> ethics =
            initialisersFor(inits, "com.tss.grc.module.ethics.service", "EthicsCase");
        Map<String, String> kyc =
            initialisersFor(inits, "com.tss.grc.module.ethics.service", "KycSubject");
        if (ethics != null) {
            assertThat(ethics.keySet())
                .as("EthicsCase must not initialise these: on an entity-bound PUT an initialiser "
                    + "makes an omitted key indistinguishable from a stated value, and `isAnonymous` "
                    + "is what protects a whistleblower")
                .doesNotContain("severity", "intakeChannel", "isAnonymous", "status");
        }
        if (kyc != null) {
            assertThat(kyc.keySet())
                .as("KycSubject must not initialise the flags F2-288 armed — an omitted key cleared "
                    + "a confirmed OFAC match and approve() then permitted the subject")
                .doesNotContain("riskRating", "isPep", "isSanctioned", "cddLevel", "eddRequired",
                                "status");
        }
    }
}
