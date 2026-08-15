package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-213 — an update may not write a field its own client cannot send.
 *
 * <p><b>What went wrong.</b> {@code PUT} here is a full replace: the service writes the
 * request's value into every column it touches, unconditionally. That is only safe while the
 * payload can carry every one of those columns. Five services in {@code module/cyber} went
 * further and persisted the <b>request object itself</b> — {@code repo.save(req)} — keeping
 * only id / organisationId / externalId / createdAt. Everything else became whatever a
 * freshly-deserialised entity holds: null, or the field's own initialiser.
 *
 * <p>Probed live 2026-08-15 against a planted {@code [REVIEW-TEST]} row: editing the title of
 * a cyber incident nulled {@code incident_number}, {@code affected_asset_name},
 * {@code detected_at}, {@code reported_at} and {@code resolved_at}, reset both link counts to
 * 0, and flipped {@code notification_sent} from TRUE to <b>false</b> — the flag recording that
 * an authority had been told. The reset is worse than the null: a false boolean reads as a
 * fact, a null reads as missing.
 *
 * <p><b>Why nothing caught it.</b> {@link WireContractTest} (F2-211/F2-212) proves the read
 * types agree with what the wire carries, and F2-212 added a leg asserting the payload
 * <i>builder</i> assigns each at-risk field. Both are about fields somebody remembered. Here
 * the service's written set is not a list anyone wrote down — it is <i>every property the
 * entity has</i>, so no amount of checking the declared fields could see it.
 *
 * <p><b>The two legs.</b>
 * <ul>
 *   <li><b>Structural, hard zero</b> — no method may load an entity of type {@code T} and then
 *       persist a request parameter of the same type {@code T}. Same-type is the discriminator
 *       that separates this defect from a legitimate create: {@code NdiGapService#createCapa}
 *       loads an {@code NdiGap} and saves an {@code NdiCorrectiveAction}, which is a parent
 *       lookup, not a replace. Eight such creates exist and none is a violation.</li>
 *   <li><b>Coverage, hard zero</b> — for each declared site, every field the update writes from
 *       its request must be declarable by the frontend type that feeds it, read out of
 *       {@code tss-grc-ui-v2/src/lib/api.ts}.</li>
 * </ul>
 *
 * <p>⚠ <b>What this does NOT cover, stated so a green run is not read as more than it is.</b>
 * 83 PUT handlers bind a JPA {@code @Entity} as their request body (F2-22's population). The
 * structural leg covers all of them; the coverage leg covers only the sites whose frontend
 * client this test names. A field is not proven safe by this test passing — it is proven that
 * no site <i>listed here</i> writes something its client cannot send.
 *
 * <p>Hermetic: reads Java sources and one {@code .ts} file from disk, exactly as
 * {@link ConnectorBackingContractTest} does. No database, no secrets, no running server.
 */
class FullReplaceUpdateContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path FE_API = Path.of("..", "tss-grc-ui-v2", "src", "lib", "api.ts");

    /**
     * The number of PUT handlers that bind a JPA entity directly, measured 2026-08-15.
     * Decrease-only: binding an entity is F2-22's finding and the direction of travel is down.
     * It is asserted so that "the structural leg is green" cannot quietly come to mean
     * "over a population that shrank because the scanner stopped resolving them".
     */
    private static final int ENTITY_BOUND_PUT_HANDLERS = 83;

    /**
     * A site where a full-replace update is fed by a named frontend request type.
     *
     * @param service    simple class name of the service
     * @param method     the method holding the request-derived writes
     * @param feType     the TypeScript interface the only client sends
     * @param endpoint   for the failure message — which screen breaks
     */
    private record Site(String service, String method, String feType, String endpoint) { }

    /**
     * Every site this slice fixed. Adding a row is how a new full-replace update gets covered;
     * the structural leg below already refuses the shape that caused the defect, so this list
     * is about the milder residue — a field the server writes and the client cannot express.
     */
    private static final List<Site> SITES = List.of(
        new Site("CyberIncidentService", "applyEditable", "CyberIncidentRequest",
            "PUT /api/v1/cyber/incidents/{id}"),
        new Site("CyberVulnerabilityService", "applyEditable", "CyberVulnRequest",
            "PUT /api/v1/cyber/vulnerabilities/{id}"),
        new Site("CyberActionPlanService", "applyEditable", "CyberActionPlanRequest",
            "PUT /api/v1/cyber/action-plans/{id}"),
        new Site("CyberAssessmentService", "applyEditable", "CyberAssessmentRequest",
            "PUT /api/v1/cyber/assessments/{id}"),
        new Site("CyberControlService", "applyEditable", "CyberControlRequest",
            "PUT /api/v1/cyber/controls/{id}"),
        new Site("CyberAssetService", "applyEditable", "CyberAssetRequest",
            "PUT /api/v1/cyber/assets/{id}"),
        new Site("CyberEvidenceService", "applyEditable", "CyberEvidenceRequest",
            "PUT /api/v1/cyber/evidence/{id}"),
        new Site("CyberSubControlService", "applyEditable", "CyberSubControlRequest",
            "PUT /api/v1/cyber/sub-controls/{id}"),
        new Site("CyberVulnExceptionService", "applyEditable", "CyberExceptionRequest",
            "PUT /api/v1/cyber/exceptions/{id}"),
        new Site("TprmService", "updateVendor", "TprmVendorRequest",
            "PUT /api/v1/tprm/vendors/{id}"),
        new Site("PrivacyService", "updateDsr", "PrivacyDsrRequest",
            "PUT /api/v1/privacy/dsrs/{id}"),
        new Site("RegulatoryChangeService", "update", "RegulatoryChangeRequest",
            "PUT /api/v1/compliance/regulatory-changes/{id}")
    );

    // ── source access ─────────────────────────────────────────────────────────────────────

    /**
     * One left-to-right alternation, never two passes. CLAUDE.md records the two-pass form
     * deleting 45% of a file because a {@code //} comment contained a literal block-open
     * marker; a corrupted source yields a SMALLER population, which reads as a clean tree.
     */
    private static final Pattern COMMENT = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    static String stripComments(String src) {
        return COMMENT.matcher(src).replaceAll("");
    }

    private static List<Path> javaSources() {
        try (Stream<Path> s = Files.walk(MAIN_JAVA)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The brace-balanced body starting at the first '{' at or after {@code from}. */
    static String bodyAt(String src, int from) {
        int i = src.indexOf('{', from);
        if (i < 0) {
            return "";
        }
        int depth = 0;
        for (int j = i; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return src.substring(i, j + 1);
            }
        }
        return src.substring(i);
    }

    private static Set<String> entitySimpleNames() {
        Set<String> out = new LinkedHashSet<>();
        for (Path p : javaSources()) {
            String raw = read(p);
            if (raw.contains("@Entity")) {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - ".java".length()));
            }
        }
        return out;
    }

    // ── leg 1: the structural ban ─────────────────────────────────────────────────────────

    /** A method that loads a {@code T} and then persists its own {@code T} parameter. */
    static List<String> fullReplaceSaves(String cls, String src, Set<String> entities) {
        List<String> hits = new ArrayList<>();
        Matcher m = Pattern.compile(
            "(?:public|protected|private)\\s+[\\w<>,\\s\\[\\]]+?\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{")
            .matcher(src);
        while (m.find()) {
            String method = m.group(1);
            String body = bodyAt(src, m.end() - 1);
            for (String param : m.group(2).split(",")) {
                String[] parts = param.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                String type = parts[parts.length - 2];
                String name = parts[parts.length - 1];
                if (!entities.contains(type)) {
                    continue;
                }
                boolean persistsRequest = Pattern
                    .compile("\\.\\s*(?:save|saveAndFlush)\\s*\\(\\s*" + Pattern.quote(name) + "\\s*\\)")
                    .matcher(body).find();
                // A same-type local declared from something that is NOT a save => an existing
                // row of the very type being saved was loaded first.
                //
                // ⚠ The obvious spelling of this is wrong and it fired on 41 sites, nearly all
                // of them legitimate creates: `CyberIncident saved = incidentRepo.save(inc);`
                // declares a same-type local, so the save's OWN RESULT counted as evidence that
                // a row had been loaded. A detector satisfied by something other than its claim
                // — the failure this codebase keeps re-finding. It failed loudly here only
                // because the violations were creates; had it over-narrowed instead, the leg
                // would have passed and said nothing.
                boolean loadsSameType = false;
                Matcher local = Pattern
                    .compile("\\b" + Pattern.quote(type) + "\\s+(\\w+)\\s*=\\s*([^;]+);")
                    .matcher(body);
                while (local.find()) {
                    if (!local.group(2).contains("save")) {
                        loadsSameType = true;
                        break;
                    }
                }
                // ⚠ A create-or-update method contains BOTH branches in one body, so "saves the
                // request" and "has a loaded same-type local" are both true for perfectly correct
                // code. Four sites were flagged on that basis and all four were refuted by
                // reading — DetectionRuleService#save, ThreatIntelService#save,
                // AppsecFindingService#ingest and LicensingService#upsertInternal each mutate the
                // loaded row on the update branch and only save the request on the CREATE branch.
                // The discriminator is what the method does to the request's id first:
                // `setId(null)` means "this is a create, the row is new"; `setId(existing.getId())`
                // means "aim this payload at that row", which is the replace.
                boolean createsFreshRow = Pattern
                    .compile("\\b" + Pattern.quote(name) + "\\s*\\.\\s*setId\\s*\\(\\s*null\\s*\\)")
                    .matcher(body).find();
                if (persistsRequest && loadsSameType && !createsFreshRow) {
                    hits.add(cls + "#" + method + " persists its own " + type + " parameter");
                }
            }
        }
        return hits;
    }

    @Test
    @DisplayName("no update loads an existing row and then persists the request object over it")
    void noUpdatePersistsTheRequestObject() {
        Set<String> entities = entitySimpleNames();
        assertThat(entities).as("no @Entity classes found — the scan is vacuous").hasSizeGreaterThan(300);

        List<String> violations = new ArrayList<>();
        for (Path p : javaSources()) {
            String n = p.getFileName().toString();
            violations.addAll(fullReplaceSaves(
                n.substring(0, n.length() - ".java".length()), stripComments(read(p)), entities));
        }

        assertThat(violations)
            .as("""
                A method loads an existing row and then saves the REQUEST object in its place, so \
                every column the caller did not send is replaced by null or by the entity's own \
                field initialiser. Probed live in F2-213: editing a cyber incident's title erased \
                its detection, report and resolution timestamps and reset notification_sent to \
                false. Mutate the loaded row instead — NdiGapService#updateCapa is the idiom.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the structural ban detects the shape it was written for (positive control)")
    void theStructuralBanIsNotVacuous() {
        String preFix = """
            public class Sample {
                public Vulnerability updateVulnerability(Long id, Vulnerability req) {
                    Vulnerability existing = getVulnerability(id);
                    req.setId(existing.getId());
                    return vulnerabilityRepo.save(req);
                }
            }
            """;
        assertThat(fullReplaceSaves("Sample", preFix, Set.of("Vulnerability")))
            .as("the detector no longer fires on the exact pre-fix code — it has rotted")
            .hasSize(1);

        String fixed = """
            public class Sample {
                public Vulnerability updateVulnerability(Long id, Vulnerability req) {
                    Vulnerability existing = getVulnerability(id);
                    applyEditable(existing, req);
                    return vulnerabilityRepo.save(existing);
                }
            }
            """;
        assertThat(fullReplaceSaves("Sample", fixed, Set.of("Vulnerability")))
            .as("the detector fires on the corrected shape — it would fail on a clean tree")
            .isEmpty();

        String create = """
            public class Sample {
                public NdiCorrectiveAction createCapa(Long gapId, NdiCorrectiveAction body) {
                    NdiGap parent = getGap(gapId);
                    body.setId(null);
                    return capas.save(body);
                }
            }
            """;
        assertThat(fullReplaceSaves("Sample", create, Set.of("NdiCorrectiveAction", "NdiGap")))
            .as("a create that looks up a PARENT of a different type is not a full replace")
            .isEmpty();
    }

    // ── leg 2: the client must be able to send what the server writes ──────────────────────

    /** Fields written from the request inside {@code service#method}. */
    static Set<String> writtenFromRequest(String src, String method) {
        Matcher m = Pattern.compile(
            "(?:public|protected|private)\\s+[\\w<>,\\s\\[\\]]+?\\s+" + Pattern.quote(method)
                + "\\s*\\(([^)]*)\\)\\s*\\{").matcher(src);
        if (!m.find()) {
            return Set.of();
        }
        List<String> reqVars = new ArrayList<>();
        for (String param : m.group(1).split(",")) {
            String[] parts = param.trim().split("\\s+");
            if (parts.length >= 2) {
                reqVars.add(parts[parts.length - 1]);
            }
        }
        String body = bodyAt(src, m.end() - 1);
        Set<String> out = new LinkedHashSet<>();
        Matcher s = Pattern.compile("\\.\\s*set([A-Z]\\w*)\\s*\\(([^;]*?)\\)\\s*;").matcher(body);
        while (s.find()) {
            String field = s.group(1);
            String arg = s.group(2);
            for (String v : reqVars) {
                if (Pattern.compile("\\b" + Pattern.quote(v) + "\\s*[.(]").matcher(arg).find()) {
                    out.add(Character.toLowerCase(field.charAt(0)) + field.substring(1));
                    break;
                }
            }
        }
        return out;
    }

    /** The property names a frontend interface declares, following {@code extends}. */
    static Set<String> feKeys(String api, String type) {
        Matcher m = Pattern.compile(
            "export interface " + Pattern.quote(type)
                + "\\s*(?:extends\\s+([\\w,\\s]+?))?\\s*\\{([^}]*)\\}").matcher(api);
        if (!m.find()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        Matcher k = Pattern.compile("(\\w+)\\s*\\??\\s*:").matcher(m.group(2));
        while (k.find()) {
            keys.add(k.group(1));
        }
        if (m.group(1) != null) {
            for (String parent : m.group(1).split("[,\\s]+")) {
                if (!parent.isBlank() && !parent.equals(type)) {
                    keys.addAll(feKeys(api, parent));
                }
            }
        }
        return keys;
    }

    @Test
    @DisplayName("every field a full-replace update writes can be sent by its own client")
    void theServerNeverWritesWhatTheClientCannotSend() {
        String api = stripComments(read(FE_API));
        Map<String, Set<String>> unsendable = new LinkedHashMap<>();

        for (Site site : SITES) {
            Path svc = javaSources().stream()
                .filter(p -> p.getFileName().toString().equals(site.service() + ".java"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("service source not found: " + site.service()));

            Set<String> written = writtenFromRequest(stripComments(read(svc)), site.method());
            assertThat(written)
                .as("parsed no request-derived writes from %s#%s — the parser has rotted, and an "
                    + "empty written set makes this whole check vacuously green",
                    site.service(), site.method())
                .isNotEmpty();

            Set<String> declared = feKeys(api, site.feType());
            assertThat(declared)
                .as("frontend type %s not found in api.ts (renamed? then rename it here too)",
                    site.feType())
                .isNotEmpty();

            Set<String> missing = new LinkedHashSet<>(written);
            missing.removeAll(declared);
            if (!missing.isEmpty()) {
                unsendable.put(site.endpoint(), missing);
            }
        }

        assertThat(unsendable)
            .as("""
                These updates write a column their only client cannot put on the wire, so the \
                field arrives null on every call and is written null. Either stop writing it \
                (it belongs to another path) or declare it on the request type and have the \
                form collect it — F2-213 chose per field, and the choice is recorded beside \
                each setter.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the coverage leg detects a field the client cannot send (positive control)")
    void theCoverageLegIsNotVacuous() {
        String preFix = """
            public class Sample {
                public DataSubjectRequest updateDsr(Long id, DataSubjectRequest req) {
                    DataSubjectRequest existing = getDsr(id);
                    existing.setStatus(req.getStatus());
                    existing.setCompletedAt(req.getCompletedAt());
                    return dsrRepo.save(existing);
                }
            }
            """;
        Set<String> written = writtenFromRequest(preFix, "updateDsr");
        assertThat(written).contains("status", "completedAt");

        String api = "export interface PrivacyDsrRequest { requestType: string; status?: string; }";
        Set<String> declared = feKeys(api, "PrivacyDsrRequest");
        assertThat(declared).containsExactlyInAnyOrder("requestType", "status");

        Set<String> missing = new LinkedHashSet<>(written);
        missing.removeAll(declared);
        assertThat(missing)
            .as("the coverage detector no longer sees a field the client cannot send")
            .containsExactly("completedAt");
    }

    // ── leg 3: say what the population is ─────────────────────────────────────────────────

    @Test
    @DisplayName("the entity-bound PUT population is measured, not assumed")
    void theEntityBoundPutPopulationIsMeasured() {
        Set<String> entities = entitySimpleNames();
        int handlers = 0;
        for (Path p : javaSources()) {
            if (!p.getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            String src = stripComments(read(p));
            Matcher m = Pattern.compile("@PutMapping\\s*\\(([^)]*)\\)([\\s\\S]{0,1500}?)\\)\\s*\\{")
                .matcher(src);
            while (m.find()) {
                Matcher b = Pattern.compile(
                    "@RequestBody\\s+(?:@Valid\\s+)?([A-Z]\\w*)|@Valid\\s+@RequestBody\\s+([A-Z]\\w*)")
                    .matcher(m.group(2));
                if (b.find()) {
                    String t = b.group(1) != null ? b.group(1) : b.group(2);
                    if (entities.contains(t)) {
                        handlers++;
                    }
                }
            }
        }
        assertThat(handlers)
            .as("""
                PUT handlers binding a JPA entity directly. Decrease-only: this is F2-22's \
                population and the direction of travel is down. If it rose, a new endpoint took \
                an entity as its request body; if it fell to something implausible, the scanner \
                stopped resolving them and the structural leg above is greener than the tree.""")
            .isLessThanOrEqualTo(ENTITY_BOUND_PUT_HANDLERS)
            .isGreaterThan(60);
    }
}
