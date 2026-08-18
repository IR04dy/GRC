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
 *       #coveragePercent} resets to <b>100.00</b> (an ISAE 3410 assurance input),
 *       {@code CloudService#encryptionInTransit} to <b>true</b>, while
 *       {@code CrisisEvent#severity} resets to <b>1</b> and {@code AuthorityDocument#isPlaceholder}
 *       to <b>true</b>, marking a real regulatory instrument a placeholder.</li>
 * </ul>
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
     * of {@code MaterialTopic}, every one initialised, and no longer writes any of them.
     */
    private static final Set<String> WORK_LIST = Set.of(
        "AuthorityDocumentService#update :: AuthorityDocument#isPlaceholder",
        "AuthorityDocumentService#update :: AuthorityDocument#translationStatus",
        "AuthorityDocumentService#updateCitation :: AuthorityDocumentCitation#isPlaceholder",
        "AuthorityDocumentService#updateCitation :: AuthorityDocumentCitation#mandatory",
        "AuthorityDocumentService#updateCitation :: AuthorityDocumentCitation#sequenceOrder",
        "AuthorityDocumentService#updateCitation :: AuthorityDocumentCitation#translationStatus",
        "AuthorityDocumentService#updateCitation :: AuthorityDocumentCitation#weight",
        "BcmBiaAssessmentService#update :: BcmBiaAssessment#manualWorkaroundAvailable",
        "BcmCommunicationContactService#update :: BcmCommunicationContact#availabilityStatus",
        "BcmCommunicationContactService#update :: BcmCommunicationContact#escalationLevel",
        "BcmCommunicationContactService#update :: BcmCommunicationContact#status",
        "BcmComplianceMappingService#update :: BcmComplianceMapping#isPlaceholder",
        "ChangeManagementService#updateTask :: ChangeTask#sortOrder",
        "ChangeManagementService#updateTask :: ChangeTask#status",
        "ClimateRiskOpportunityService#update :: ClimateRiskOpportunity#impact",
        "ClimateRiskOpportunityService#update :: ClimateRiskOpportunity#likelihood",
        "ClimateRiskOpportunityService#update :: ClimateRiskOpportunity#timeHorizon",
        "CloudServiceService#update :: CloudService#criticality",
        "CloudServiceService#update :: CloudService#deploymentModel",
        "CloudServiceService#update :: CloudService#encryptionAtRest",
        "CloudServiceService#update :: CloudService#encryptionInTransit",
        "CloudServiceService#update :: CloudService#kingdomDataResident",
        "CloudServiceService#update :: CloudService#mfaEnforced",
        "CloudServiceService#update :: CloudService#serviceModel",
        "CloudServiceService#update :: CloudService#ssoEnabled",
        "CloudServiceService#update :: CloudService#status",
        "ControlLibraryService#updateLink :: RiskControlLink#linkType",
        "ControlLibraryService#updateMapping :: ControlMapping#coverage",
        "CorrectiveActionService#update :: CorrectiveAction#actionType",
        "CorrectiveActionService#update :: CorrectiveAction#priority",
        "CrisisEventService#update :: CrisisEvent#cyberRelated",
        "CrisisEventService#update :: CrisisEvent#personalDataAffected",
        "CrisisEventService#update :: CrisisEvent#severity",
        "CrisisEventService#update :: CrisisEvent#supplierInvolved",
        "DepartmentService#update :: Department#status",
        "GhgEmissionRecordService#update :: GhgEmissionRecord#coveragePercent",
        "IssueService#update :: Issue#priority",
        "IssueService#update :: Issue#severity",
        "NdiComplianceAssessmentService#update :: NdiComplianceAssessment#complianceStatus",
        "NdiFrameworkItemService#update :: NdiFrameworkItem#mandatory",
        "NdiFrameworkItemService#update :: NdiFrameworkItem#priority",
        "NdiFrameworkItemService#update :: NdiFrameworkItem#status",
        "NdiFrameworkItemService#update :: NdiFrameworkItem#weight",
        "SgiCommitmentService#update :: SgiCommitment#valueSource",
        "SustainabilityEvidenceService#update :: SustainabilityEvidence#confidentiality",
        "TprmService#updateAssessment :: VendorAssessment#riskRating");

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
     * {@code Entity -> field -> initialiser}, over @Entity sources only.
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
            String type = t.group(1);
            Matcher f = field.matcher(ServerStampedFieldContractTest.stripComments(raw));
            while (f.find()) {
                String init = f.group(3).trim();
                if (init.equals("null")) continue;
                out.computeIfAbsent(type, k -> new TreeMap<>()).put(f.group(2), init);
            }
        }
        return out;
    }

    /** {@code Service#method :: Entity#field} for every never-firing guard. */
    static Set<String> measure(Map<String, String> serviceSources,
                               Map<String, Map<String, String>> inits) {
        Set<String> out = new TreeSet<>();
        for (var entry : serviceSources.entrySet()) {
            String service = entry.getKey();
            String src = ServerStampedFieldContractTest.stripComments(entry.getValue());
            for (ServerStampedFieldContractTest.Method m
                     : ServerStampedFieldContractTest.publicMethods(src)) {
                Map<String, String> ptypes =
                    ServerStampedFieldContractTest.requestParamTypes(m.params());
                for (var p : ptypes.entrySet()) {
                    Map<String, String> declared = inits.get(p.getValue());
                    if (declared == null) continue;
                    // It must LOAD a row of that type. On a create, writing the initialiser is
                    // exactly what is meant to happen, so the guard being decorative costs nothing.
                    if (!m.body().matches("(?s).*\\b(get|find\\w*)\\s*\\(.*")) continue;
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
            }
        }
        return out;
    }

    private static Map<String, String> liveServices() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Path p : sources("Service.java")) {
            out.put(p.getFileName().toString().replace(".java", ""), read(p));
        }
        return out;
    }

    private static Map<String, Map<String, String>> liveInitialisers() {
        return initialisedFields(sources(".java").stream().map(
            NeverFiringNullGuardContractTest::read).collect(Collectors.toList()));
    }

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    void theWorkListIsExactlyWhatTheTreeContains() {
        Set<String> measured = measure(liveServices(), liveInitialisers());

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
        Set<String> measured = measure(liveServices(), liveInitialisers());
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
        assertThat(inits.get("MaterialTopic"))
            .as("the register F2-237 was found on must still declare the initialisers that made the "
                + "guard decorative — if these go away, re-read update() before relaxing anything")
            .containsKeys("impactMateriality", "financialMateriality",
                          "stakeholderImportance", "businessImpact");
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
}
