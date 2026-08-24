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
        // ⚠ UserService#update is FIXED by this slice (it was nulling job titles) but is
        // deliberately NOT listed. The coverage leg compares property NAMES, and that endpoint
        // transforms two of them: `password` becomes `setPasswordHash(encoder.encode(...))` and
        // `roles` becomes rows in the iam.user_roles join table, with no property on User at all.
        // Listing it made the leg report `passwordHash` as a field the client cannot send — a
        // false positive on correct code, which is the one thing a gate must never do, because it
        // trains the next reader to edit the assertion instead of the code. The structural leg
        // still covers the class. A name-mapping leg would be a different instrument.
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

    /**
     * Fields written from the request inside {@code service#method}, <b>following one hop into a
     * private helper</b> the method hands a request-derived argument.
     *
     * <p>⚠ F2-252 added the hop, and the reason is F2-231's rule arriving here: <b>to a
     * setter-site scan, a refactor that RELOCATES a write is indistinguishable from a fix that
     * REMOVED it, and the population shrinks either way — the quiet direction.</b>
     * {@code CyberControlService} moved its {@code (frameworkId, frameworkName)} writes out of
     * {@code applyEditable} into {@code applyFrameworkReference}, the pair's single writer, so the
     * reverse leg below reported both fields as declared-but-never-written — the shape that means
     * <i>a form input silently became decorative</i>. They are written; they moved. The answer is to
     * <b>follow the delegation, never to relax the assertion</b> (F2-231) and never to record a
     * {@code DECLARED_BUT_NOT_WRITTEN} entry that would be false.
     *
     * <p>One hop only, and only for a {@code private} helper declared in the same file whose call
     * passes something that reads the request. Two hops would start resolving arbitrary call graphs
     * with a regex, which is the mistake F2-187 recorded (measure from bytecode, or stay shallow and
     * say so).
     */
    static Set<String> writtenFromRequest(String src, String method) {
        Set<String> all = new LinkedHashSet<>(writtenDirectly(src, method));
        for (String helper : helperClosure(src, method)) {
            all.addAll(writtenDirectly(src, helper));
        }
        return all;
    }

    /** As {@link #writtenFromRequest}, but only the writes with no null guard on the accessor. */
    static Set<String> unguardedWritesInClosure(String src, String method) {
        Set<String> all = new LinkedHashSet<>(unguardedWritesFromRequest(src, method));
        for (String helper : helperClosure(src, method)) {
            all.addAll(unguardedWritesFromRequest(src, helper));
        }
        return all;
    }

    /**
     * Every private helper reachable from {@code method} by passing the request along, to a fixed
     * point within the same file.
     *
     * <p>⚠⚠ F2-278 — the single hop below is <b>why {@code SITES} named helper methods rather than
     * the methods its own controllers call</b>, and nothing said so. {@code CyberControlService}'s
     * chain is {@code updateControl → applyEditable → applyFrameworkReference}: two hops. Resolving
     * the population from the controllers (which name {@code updateControl}) therefore lost
     * {@code frameworkId}, {@code frameworkName} and {@code frameworkNameAr} — and lost them into
     * the REVERSE leg, where they read as <i>three fields the client sends and the server ignores</i>,
     * i.e. as three inert form inputs. 🔑 <b>A hand-written population can encode a detector's limit
     * as a naming convention, and then the limit is invisible because the list looks like a
     * choice.</b>
     *
     * <p>Bounded by the fixed point rather than by a depth number: a cycle terminates because the
     * set only ever grows, and a helper that adds nothing is free.
     */
    static Set<String> helperClosure(String src, String method) {
        Set<String> closure = new LinkedHashSet<>();
        Set<String> frontier = new LinkedHashSet<>(List.of(method));
        while (!frontier.isEmpty()) {
            Set<String> next = new LinkedHashSet<>();
            for (String m : frontier) {
                for (String helper : privateHelpersCalledWithRequest(src, m)) {
                    if (closure.add(helper)) {
                        next.add(helper);
                    }
                }
            }
            frontier = next;
        }
        return closure;
    }

    /**
     * Private helpers {@code method} calls with an argument that reads one of its request-typed
     * parameters. Returns helper names, never signatures — one hop, same file.
     */
    static Set<String> privateHelpersCalledWithRequest(String src, String method) {
        Matcher m = Pattern.compile(
            "(?:public|protected|private)\\s+[\\w<>,\\s\\[\\]]+?\\s+" + Pattern.quote(method)
                + "\\s*\\(([^)]*)\\)\\s*\\{").matcher(src);
        if (!m.find()) {
            return Set.of();
        }
        List<String> reqVars = paramNames(m.group(1));
        String body = bodyAt(src, m.end() - 1);
        Set<String> helpers = new LinkedHashSet<>();
        Matcher call = Pattern.compile("(?<![.\\w])(\\w+)\\s*\\(([^;]*?)\\)\\s*;").matcher(body);
        while (call.find()) {
            String name = call.group(1);
            String args = call.group(2);
            if (name.equals(method) || !Pattern.compile(
                    "private\\s+[\\w<>,\\s\\[\\]]+?\\s+" + Pattern.quote(name) + "\\s*\\(")
                    .matcher(src).find()) {
                continue;
            }
            for (String v : reqVars) {
                if (Pattern.compile("\\b" + Pattern.quote(v) + "\\b").matcher(args).find()) {
                    helpers.add(name);
                    break;
                }
            }
        }
        return helpers;
    }

    private static List<String> paramNames(String params) {
        List<String> names = new ArrayList<>();
        for (String param : params.split(",")) {
            String[] parts = param.trim().split("\\s+");
            if (parts.length >= 2) {
                names.add(parts[parts.length - 1]);
            }
        }
        return names;
    }

    /**
     * Names in {@code body} that carry request data: the method's own parameters, plus any local
     * assigned from an expression that already mentions one, to a fixed point.
     *
     * <p>⚠ F2-252 added this, and F2-231 had already recorded the hole in the sibling gate: <b>a
     * bare-identifier local aliasing caller data reads as a server stamp.</b> Here it read as
     * <i>nothing written at all</i>. {@code CyberControlService} does
     * {@code Long sentId = req.getFrameworkId();} then
     * {@code var f = frameworks.require(sentId, orgId);} then {@code target.setFrameworkId(f.getId())}
     * — three steps, none of which mentions {@code req} at the setter, and the field is written from
     * the request at every one of them. Without the propagation the reverse leg reports the field as
     * declared-but-never-written, which is the shape that means <i>a form input silently became
     * decorative</i> — the opposite of what the code does.
     *
     * <p>Deliberately within ONE body: this is not a dataflow engine, and F2-182's rule stands that
     * reachability is not a regex question. What it is, is the smallest widening that stops the gate
     * being wrong about a resolved-then-derived reference — the shape F2-245 onwards keeps producing.
     */
    static Set<String> requestTaintedNames(String params, String body) {
        Set<String> tainted = new LinkedHashSet<>(paramNames(params));
        Pattern assign = Pattern.compile(
            "(?:^|[;{}])\\s*(?:final\\s+)?(?:[\\w<>,.\\[\\]?]+\\s+)?(\\w+)\\s*=\\s*([^;]+);");
        boolean grew = true;
        while (grew) {
            grew = false;
            Matcher a = assign.matcher(body);
            while (a.find()) {
                String name = a.group(1);
                String expr = a.group(2);
                if (tainted.contains(name)) {
                    continue;
                }
                for (String t : tainted) {
                    if (Pattern.compile("\\b" + Pattern.quote(t) + "\\b").matcher(expr).find()) {
                        tainted.add(name);
                        grew = true;
                        break;
                    }
                }
            }
        }
        return tainted;
    }

    /** Fields written from the request inside {@code service#method}'s own body. */
    static Set<String> writtenDirectly(String src, String method) {
        Matcher m = Pattern.compile(
            "(?:public|protected|private)\\s+[\\w<>,\\s\\[\\]]+?\\s+" + Pattern.quote(method)
                + "\\s*\\(([^)]*)\\)\\s*\\{").matcher(src);
        if (!m.find()) {
            return Set.of();
        }
        String body = bodyAt(src, m.end() - 1);
        // F2-252 — the method's own parameters PLUS locals assigned from them, to a fixed point.
        Set<String> reqVars = requestTaintedNames(m.group(1), body);
        Set<String> out = new LinkedHashSet<>();
        Matcher s = Pattern.compile("\\.\\s*set([A-Z]\\w*)\\s*\\(([^;]*?)\\)\\s*;").matcher(body);
        while (s.find()) {
            String field = s.group(1);
            String arg = s.group(2);
            for (String v : reqVars) {
                // A tainted BARE identifier counts too (`setX(sentName)`), not only `v.` / `v(`.
                if (Pattern.compile("\\b" + Pattern.quote(v) + "\\b").matcher(arg).find()) {
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

    /**
     * The other direction: a field the client sends that the server does not write.
     *
     * <p>Not automatically a defect — some are deliberate (an immutable username; a status the
     * workflow owns) and some are inert form inputs that quietly do nothing. Either way it is a
     * decision, so it is recorded rather than tolerated silently. Enforced EXACTLY, in both
     * directions: a new one fails, and an entry whose field starts being written must be deleted
     * in the same commit.
     *
     * <p>⚠ This leg exists because the mutation harness found the gap: deleting a write from an
     * {@code applyEditable} shrinks the written set, which is still a valid SUBSET of what the
     * client declares — so the coverage leg above stayed green while a field silently became
     * uneditable. A one-directional check over a two-directional contract.
     */
    private static final Map<String, String> DECLARED_BUT_NOT_WRITTEN = new LinkedHashMap<>() {{
        put("PUT /api/v1/privacy/dsrs/{id} :: receivedAt",
            "When the request arrived. Stamped once at creation; a later edit must not restyle "
            + "the start of the PDPL Article 23 clock.");
        // ⚠ `PUT /api/v1/privacy/dsrs/{id} :: description` was the second entry here and is
        // CLOSED by F2-214: `DataSubjectRequest` has fourteen properties and not one is free
        // text, so the DSR fulfilment wizard's composed narrative — its identity-verification
        // evidence included — was discarded on every submission, and PrivacyPage offered a
        // "Description" textarea that stored nothing. The field is gone from the request type,
        // both senders and the form. Its entry is deleted rather than left as tolerated, which
        // is what this leg's exact assertion is for: a ceiling would have let the closure pass
        // unnoticed and made a tolerated field indistinguishable from a reviewed one.
        //
        // ⚠ I predicted a third entry here — regulatory-changes :: status — and this leg refuted
        // it on its first run. `RegulatoryChangeRequest` never declared `status` at all, which is
        // exactly WHY the server writing it was the defect: there was nothing on the wire to
        // write. A field the client cannot send cannot also be a field the client sends and the
        // server ignores. Predicting a gate's output and being wrong is the cheapest possible
        // version of this programme's recurring lesson about unverified claims.
    }};

    @Test
    @DisplayName("a field the client sends and the server ignores is recorded, not silent")
    void everyIgnoredClientFieldIsAccountedFor() {
        String api = stripComments(read(FE_API));
        Map<String, String> found = new LinkedHashMap<>();
        for (Site site : SITES) {
            Path svc = javaSources().stream()
                .filter(p -> p.getFileName().toString().equals(site.service() + ".java"))
                .findFirst().orElseThrow();
            Set<String> written = writtenFromRequest(stripComments(read(svc)), site.method());
            for (String key : feKeys(api, site.feType())) {
                if (!written.contains(key)) {
                    found.put(site.endpoint() + " :: " + key, "");
                }
            }
        }
        assertThat(found.keySet())
            .as("""
                Either the server stopped writing a field its form still collects — which is how \
                an input silently becomes decorative — or a new optional field was added to the \
                request type. Say which, in DECLARED_BUT_NOT_WRITTEN, with a reason.""")
            .containsExactlyInAnyOrderElementsOf(DECLARED_BUT_NOT_WRITTEN.keySet());
        assertThat(DECLARED_BUT_NOT_WRITTEN.values())
            .as("a reason must actually give one — a bare label is not an explanation")
            .allSatisfy(r -> assertThat(r).hasSizeGreaterThan(40));
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

    // ── leg 3b: the same question over a DERIVED population ───────────────────────────────

    /**
     * ⚠⚠ F2-278 — {@link #SITES} is <b>twelve hand-listed sites</b>, and
     * {@link #theEntityBoundPutPopulationIsMeasured} — a leg in this same class — measures
     * <b>83 entity-bound PUT handlers</b>. Its own comment said <i>"Adding a row is how a new
     * full-replace update gets covered"</i>: an instruction with no enforcement, which is F2-52's
     * {@code AttachmentAccessPolicy} shape and F2-265's (an instruction not to copy a figure is not
     * an enforcement). A reader who finds a hard-zero coverage leg beside a measured 83 reads the
     * zero as covering the 83.
     *
     * <p>Derived, the joinable population is <b>30</b> — an entity-bound PUT handler whose path also
     * has an {@code api.put} with a named payload type. The other 52 have no typed frontend client
     * at all, so the coverage question has nothing to compare and they are counted rather than
     * claimed.
     */
    private record Derived(String path, Path service, String serviceName, String method,
                           String feType) { }

    /**
     * Every entity-bound PUT handler joined to the frontend type its own client sends.
     *
     * <p>⚠⚠ Resolves the service source by the controller's <b>import</b> and never by filename.
     * There are two {@code AuditEngagementService.java} ({@code module/audit_eng} and
     * {@code module/compliance}) and the {@code audit_eng} one has no {@code update} method, so a
     * {@code findFirst()} on the file name picked the wrong twin and the site read as
     * <i>"parsed nothing"</i> — the quiet direction, and indistinguishable from a clean site.
     * F2-276 found exactly this with a {@code LinkedHashMap} keyed on an entity's simple name
     * silently overwriting one of a pair; F2-222 measured 15 such duplicate simple names and filed
     * it as latent because <i>"no key collapses two registers today"</i>. A derived population is
     * where it stops being latent. Ambiguity that survives both the import and the longest shared
     * package prefix <b>throws</b>, because a silently wrong pick is the failure mode.
     */
    static List<Derived> derivedSites() {
        Set<String> entities = entitySimpleNames();
        List<Path> sources = javaSources();
        String api = stripComments(read(FE_API));
        Map<String, String> fePayloadType = fePutPayloadTypes(api);

        List<Derived> out = new ArrayList<>();
        for (Path p : sources) {
            if (!p.getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            String src = stripComments(read(p));
            Map<String, String> serviceFields = new LinkedHashMap<>();
            Matcher f = Pattern.compile(
                "private\\s+final\\s+([A-Z]\\w*Service)\\s+(\\w+)\\s*;").matcher(src);
            while (f.find()) {
                serviceFields.put(f.group(2), f.group(1));
            }
            Matcher base = Pattern.compile("@RequestMapping\\s*\\(\\s*\"([^\"]*)\"").matcher(src);
            String prefix = base.find() ? base.group(1) : "";

            Matcher m = Pattern.compile("@PutMapping\\s*\\(([^)]*)\\)([\\s\\S]{0,1500}?)\\)\\s*\\{")
                .matcher(src);
            while (m.find()) {
                Matcher b = Pattern.compile(
                    "@RequestBody\\s+(?:@Valid\\s+)?([A-Z]\\w*)|@Valid\\s+@RequestBody\\s+([A-Z]\\w*)")
                    .matcher(m.group(2));
                if (!b.find()) {
                    continue;
                }
                String bound = b.group(1) != null ? b.group(1) : b.group(2);
                if (!entities.contains(bound)) {
                    continue;
                }
                Matcher sub = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
                String path = normalisePath(prefix + (sub.find() ? sub.group(1) : ""));
                String feType = fePayloadType.get(path);
                if (feType == null) {
                    continue;
                }
                String handlerBody = bodyAt(src, m.end() - 1);
                Matcher call = Pattern.compile("(\\w+)\\s*\\.\\s*(\\w+)\\s*\\(").matcher(handlerBody);
                while (call.find()) {
                    String svcName = serviceFields.get(call.group(1));
                    if (svcName == null) {
                        continue;
                    }
                    Path svc = resolveServiceSource(p, src, svcName, sources);
                    out.add(new Derived(path, svc, svcName, call.group(2), feType));
                    break;
                }
            }
        }
        return out;
    }

    /** Normalised path -> the frontend type its {@code api.put} payload parameter declares. */
    static Map<String, String> fePutPayloadTypes(String api) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile(
            "api\\.put\\s*(?:<[^>]*>)?\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]\\s*,\\s*(\\w+)\\s*[,)]")
            .matcher(api);
        while (m.find()) {
            String before = api.substring(Math.max(0, m.start() - 600), m.start());
            Matcher t = Pattern.compile(
                Pattern.quote(m.group(2)) + "\\s*:\\s*(?:Partial\\s*<\\s*)?([A-Z]\\w*)")
                .matcher(before);
            String type = null;
            while (t.find()) {
                type = t.group(1);       // the nearest declaration before the call
            }
            if (type != null) {
                out.put(normalisePath(m.group(1)), type);
            }
        }
        return out;
    }

    /** See {@link #derivedSites()} — import first, then longest shared package prefix, then throw. */
    private static Path resolveServiceSource(Path controller, String controllerSrc,
                                             String simpleName, List<Path> sources) {
        List<Path> candidates = sources.stream()
            .filter(p -> p.getFileName().toString().equals(simpleName + ".java"))
            .toList();
        if (candidates.isEmpty()) {
            throw new AssertionError("no source for service " + simpleName);
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        Matcher imp = Pattern.compile(
            "import\\s+([\\w.]*\\." + Pattern.quote(simpleName) + ")\\s*;").matcher(controllerSrc);
        if (imp.find()) {
            String rel = imp.group(1).replace('.', '/') + ".java";
            for (Path p : candidates) {
                if (p.toString().replace('\\', '/').endsWith(rel)) {
                    return p;
                }
            }
        }
        String home = controller.getParent().toString().replace('\\', '/');
        Path best = null;
        int bestShared = -1;
        int ties = 0;
        for (Path p : candidates) {
            String dir = p.getParent().toString().replace('\\', '/');
            int shared = 0;
            while (shared < Math.min(home.length(), dir.length())
                    && home.charAt(shared) == dir.charAt(shared)) {
                shared++;
            }
            if (shared > bestShared) {
                bestShared = shared;
                best = p;
                ties = 1;
            } else if (shared == bestShared) {
                ties++;
            }
        }
        if (ties > 1) {
            throw new AssertionError(
                "ambiguous service simple name " + simpleName + " for controller " + controller
                + " — resolve it by import rather than letting a walk order decide (F2-222)");
        }
        return best;
    }

    /**
     * ⚠⚠ F2-278 — the coverage leg's harm sentence is <b>"the field arrives null on every call and
     * is written null"</b>, and that is FALSE when the write is null-guarded: an omitted field then
     * keeps its stored value. So one question was doing the work of two, and switching it to a
     * derived population without splitting it would have failed on
     * {@code PolicyManagementService#update} — <b>eleven undeclarable fields, every one guarded</b>,
     * i.e. a gate failing on correct code, the one failure a gate must never have (F2-214).
     *
     * <p>🔑 <b>That is why the population was a hand-written list: the leg's claim was narrower than
     * its detector, and nobody wrote that down, so the list read as laziness rather than as a
     * necessary narrowing.</b> The discriminator already existed in this file —
     * {@link #unguardedWritesFromRequest}, leg 4's detector — and the two legs never met.
     *
     * <p>RESET is the hard zero and has no exemption map: an unconditional write of a field the
     * only client cannot put on the wire destroys a stored value on every edit, and the fix is
     * always available (declare it, or stop writing it). FROZEN is the milder half and is recorded.
     */
    @Test
    @DisplayName("no derived update NULLS a column its own client cannot send (hard zero)")
    void noUpdateResetsAnUndeclarableField() {
        String api = stripComments(read(FE_API));
        Map<String, Set<String>> reset = new LinkedHashMap<>();
        for (Derived d : derivedSites()) {
            String src = stripComments(read(d.service()));
            Set<String> declared = feKeys(api, d.feType());
            if (declared.isEmpty()) {
                continue;                       // an unresolvable type is the census leg's problem
            }
            Set<String> offending = new LinkedHashSet<>(unguardedWritesInClosure(src, d.method()));
            offending.removeAll(declared);
            if (!offending.isEmpty()) {
                reset.put(d.path() + " :: " + d.serviceName() + "#" + d.method(), offending);
            }
        }
        assertThat(reset)
            .as("""
                These updates write a column UNCONDITIONALLY from a request type that cannot \
                declare it, so the value arrives null and is written null on every edit — a \
                stored value destroyed by an edit that never mentioned it. Either declare the \
                field on the request type (F2-214's fix for crossBorderTransfer on this very \
                method) or stop writing it. A null guard is the other honest answer and moves \
                the field to FROZEN_FIELDS, which is a recorded decision rather than a reset.""")
            .isEmpty();
    }

    /**
     * The milder half: a write the client cannot reach, but guarded, so the stored value survives.
     *
     * <p>Keyed {@code endpoint :: field} so a twelfth frozen field on a recorded site still fails,
     * and the reasons repeat where the finding is genuinely per-site — {@code CyberPolicyRequest}
     * is a narrow ten-field form against a service that writes about twenty columns, so eleven
     * near-identical per-field reasons would be eleven fluent unverified claims (F2-188) where the
     * fact is one: <b>this form is narrower than this service's write set</b>. F2-185b's rule cuts
     * the other way for RESET, which is per field and has no map at all.
     */
    private static final Map<String, String> FROZEN_FIELDS = new LinkedHashMap<>() {{
        // ⚠ This entry exists BECAUSE of the import-based resolution above, and it is the proof
        // that "parsed nothing" was hiding something: with the service picked by filename the
        // audit_eng twin won, that class has no `update` method, and the site read as clean.
        put("/api/v1/compliance/audit-engagements/{} :: findingsCount",
            "An aggregate the caller supplies, guarded, and AuditEngagementRequest cannot send — "
            + "so nothing sets it today. Its sibling register derives the same field from the "
            + "linked finding ids (audit_eng/AuditEngagementService:122) and this twin does not, "
            + "which is F2-197's 'a computed count is not a measured one' across two registers "
            + "for one concept. Deriving it here would change what an IIA engagement reports, so "
            + "it is filed rather than taken inside a slice about the instrument.");
        String narrowPolicyForm =
            "PolicyManagementService#update is reached by TWO routes — /api/v1/cyber/policies/{} "
            + "with this narrow ten-field form, and /api/policy/documents/{} with no typed "
            + "frontend client at all — so 'the client cannot send it' is a fact about this form "
            + "and not about the column. Guarded, so nothing is destroyed. Widening the form is a "
            + "product decision about the policy register, filed rather than taken here.";
        for (String field : List.of("notes", "retiredReason", "summaryAr", "bodyUrl", "contentMd",
                "applicabilityScope", "approverName", "approverEmail", "expiryDate",
                "relatedFramework", "relatedControls")) {
            put("/api/v1/cyber/policies/{} :: " + field, narrowPolicyForm);
        }
    }};

    @Test
    @DisplayName("a guarded write the client cannot reach is recorded, not silent")
    void everyFrozenFieldIsAccountedFor() {
        String api = stripComments(read(FE_API));
        Set<String> frozen = new LinkedHashSet<>();
        for (Derived d : derivedSites()) {
            String src = stripComments(read(d.service()));
            Set<String> declared = feKeys(api, d.feType());
            if (declared.isEmpty()) {
                continue;
            }
            Set<String> undeclarable = new LinkedHashSet<>(writtenFromRequest(src, d.method()));
            undeclarable.removeAll(declared);
            undeclarable.removeAll(unguardedWritesInClosure(src, d.method()));
            for (String field : undeclarable) {
                frozen.add(d.path() + " :: " + field);
            }
        }
        assertThat(frozen)
            .as("""
                A field this service writes from the request that its own client cannot put on \
                the wire. Guarded, so the stored value survives — but it can only be set by a \
                seed or a direct API call, never corrected from the screen that edits the \
                register (F2-275). Say which, in FROZEN_FIELDS, with a reason; a fixed one must \
                lose its entry in the same commit.""")
            .containsExactlyInAnyOrderElementsOf(FROZEN_FIELDS.keySet());
        assertThat(FROZEN_FIELDS.values())
            .as("a reason must actually give one — a bare label is not an explanation")
            .allSatisfy(r -> assertThat(r).hasSizeGreaterThan(40));
    }

    /**
     * The reverse direction over the derived population, restricted to <b>inbound-only</b> request
     * types.
     *
     * <p>⚠ A round-trip {@code *Dto} MUST declare the fields it RETURNS, so "declared but not
     * written" is the normal case for one and says nothing — F2-248 recorded exactly this when its
     * own census conflated inbound-only types with round-trip ones and would have shipped a zero
     * that failed on correct code. Ten of the thirty derived sites send a {@code *Dto}; asking this
     * question of them would demand roughly sixty reasons for a contract that does not exist.
     */
    /**
     * ⚠⚠ F2-311 — this was {@code DERIVED_IGNORED_FIELDS}, and two of its four entries are not
     * ignored at all: they are <b>consulted and refused</b>. [[F2-296]] wrote that gap into one of
     * the entries itself — <em>"calling it 'ignored' understates what the route does"</em> — which is
     * [[F2-224]]'s shape, a verdict vocabulary limited to the verdicts an earlier slice happened to
     * reach.
     *
     * <p>{@code unwritten} is accurate for both kinds: neither is written <b>from the request</b>.
     * The verdict token then says <em>why</em>, and it is <b>derived and checked</b> rather than
     * trusted ({@link #everyUnwrittenVerdictMatchesWhatTheServiceDoes}), because the distinction is
     * mechanical — a consulted field is compared by a refusal primitive, an ignored one is not.
     *
     * <ul>
     *   <li><b>CONSULTED</b> — the field arrives, the server compares it against the stored value and
     *       refuses a change while tolerating an echo. A caller can observe this: sending a different
     *       value is a 400. Calling it "ignored" would tell a reader the opposite.</li>
     *   <li><b>IGNORED</b> — the field arrives and is silently dropped. A form input that does
     *       nothing, or an immutable the server never looks at.</li>
     * </ul>
     */
    private static final Map<String, String> DERIVED_UNWRITTEN_FIELDS = new LinkedHashMap<>() {{
        // /api/v1/bcm/bia/{} :: linkedRiskId was here and is CLOSED by F2-279 —
        // BcmService#updateBia writes it now, so this map must no longer carry it (the
        // forward leg enforces that direction). Its entry read "settable on create and
        // frozen thereafter"; the reason the freeze protected nothing is that the create
        // already accepted the value unvalidated, and it still does — the reference cannot
        // be verified from bcm without a new module edge on a decrease-only graph ratchet.
        put("/api/v1/bcm/legacy-plans/{} :: status",
            "CONSULTED. F2-234 made this route accept an ECHO of the stored status and refuse a "
            + "CHANGE, because bcm.bcm_plans has no CHECK on status and every transition guard is "
            + "an equality test, so a PUT-driven DRAFT -> APPROVED skipped the machine entirely. "
            + "The field is on the wire so a form that round-trips the row keeps working.");
        put("/api/v1/compliance/audit-engagements/{} :: status",
            "CONSULTED. F2-296 — the same shape as bcm/legacy-plans above, and finding the two "
            + "described identically in this map WITHOUT either naming the other is what showed "
            + "the rule had four hand-rolled sites across three modules (F2-215's two-of-N, in the "
            + "artefact that documents them). `apply()` copied the caller's status with no "
            + "vocabulary check and no direction check, past an `advance()` that is explicitly "
            + "forward-only, so the PUT was this register's only reverse edge; F2-294 closed the "
            + "create and filed the register as having 'closed its only open writers'. It now goes "
            + "through ServerOwnedState.requireUnchanged: an ECHO is accepted because "
            + "engagementToReq always sends one, a CHANGE is refused naming /advance. ⚙ F2-311 "
            + "closed the gap this entry used to record: the map had no bucket for a field that is "
            + "CONSULTED and never written, so 'ignored' understated the route. The verdict token "
            + "above is now derived from the service and checked both ways.");
        put("/api/v1/cyber/policies/{} :: policyCode",
            "IGNORED. The document-control code an auditor cites; CH-10 fixes it at creation and "
            + "PolicyManagementService writes it only when superseding a document into a new "
            + "version. Populated on 41 of 41 live rows in dev and 37 of 37 in prod.");
        put("/api/v1/privacy/dsrs/{} :: receivedAt",
            "IGNORED. When the request arrived. Stamped once at creation; a later edit must not restyle "
            + "the start of the PDPL Article 23 clock.");
    }};

    /**
     * F2-311 — the verdict is derived from the service, not trusted from the prose.
     *
     * <p>A <b>CONSULTED</b> field is compared against the stored value by one of this platform's
     * refusal primitives ({@code ServerOwnedState.requireUnchanged}, {@code sameState},
     * {@code requireImmutableReference}); an <b>IGNORED</b> one is not looked at. So the two kinds
     * are mechanically separable, and holding the token as prose alone would be the artefact
     * [[F2-188]] measured — a reason that reads verified because it reads fluently.
     *
     * <p>⚠ Enforced in BOTH directions. A field declared IGNORED whose service consults it is the
     * understatement F2-296 found; a field declared CONSULTED whose service does not is a claim that
     * a caller's value is checked when it is silently dropped — and that is the more dangerous half,
     * because it tells a reader a guard exists.
     */
    @Test
    @DisplayName("every unwritten field's verdict matches what its service actually does")
    void everyUnwrittenVerdictMatchesWhatTheServiceDoes() throws IOException {
        Set<String> primitives = Set.of("requireUnchanged", "sameState", "requireImmutableReference");
        List<String> wrong = new ArrayList<>();

        for (Map.Entry<String, String> e : DERIVED_UNWRITTEN_FIELDS.entrySet()) {
            String key = e.getKey();
            String verdict = e.getValue().split("\\.", 2)[0].trim();
            if (!verdict.equals("CONSULTED") && !verdict.equals("IGNORED")) {
                wrong.add(key + " — reason must begin with CONSULTED or IGNORED, not '" + verdict
                          + "'. The verdict is the part a reader acts on.");
                continue;
            }
            String field = key.substring(key.indexOf(":: ") + 3).trim();
            String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);

            // does ANY service consult that field through a refusal primitive?
            boolean consulted = false;
            for (Path svc : javaSources()) {
                if (!svc.getFileName().toString().endsWith("Service.java")) { continue; }
                String code = stripComments(Files.readString(svc));
                for (String prim : primitives) {
                    int at = code.indexOf(prim);
                    while (at >= 0) {
                        String window = code.substring(at, Math.min(code.length(), at + 260));
                        if (window.contains(getter + "()")) {
                            consulted = true;
                            break;
                        }
                        at = code.indexOf(prim, at + 1);
                    }
                    if (consulted) { break; }
                }
                if (consulted) { break; }
            }

            if (consulted && verdict.equals("IGNORED")) {
                wrong.add(key + " — declared IGNORED, but a refusal primitive compares " + getter
                          + "(). That is F2-296's understatement: a caller sending a different value "
                          + "gets a 400, which 'ignored' tells them the opposite of.");
            }
            if (!consulted && verdict.equals("CONSULTED")) {
                wrong.add(key + " — declared CONSULTED, but no refusal primitive compares " + getter
                          + "(). This is the dangerous direction: the entry tells a reader a guard "
                          + "exists where the value is silently dropped.");
            }
        }

        assertThat(wrong).as("""
            An unwritten field's declared verdict disagrees with what its service does.

            CONSULTED means the server compares the caller's value and refuses a change; IGNORED             means it is silently dropped. Both are 'not written from the request', which is why they             share this map — but they are different things to a caller, and only one of them is a             guard.

            %s""".formatted(String.join("\n            ", wrong)))
            .isEmpty();

        // anti-vacuity: at least one of each kind, or the leg is asserting over a single bucket
        long consultedCount = DERIVED_UNWRITTEN_FIELDS.values().stream()
            .filter(v -> v.startsWith("CONSULTED")).count();
        long ignoredCount = DERIVED_UNWRITTEN_FIELDS.values().stream()
            .filter(v -> v.startsWith("IGNORED")).count();
        assertThat(consultedCount).as("no CONSULTED entry — the distinction is untested").isPositive();
        assertThat(ignoredCount).as("no IGNORED entry — the distinction is untested").isPositive();
    }

    @Test
    @DisplayName("over the derived population, an ignored inbound field is recorded")
    void everyIgnoredFieldOnAnInboundTypeIsAccountedFor() {
        String api = stripComments(read(FE_API));
        Set<String> ignored = new LinkedHashSet<>();
        for (Derived d : derivedSites()) {
            if (!d.feType().endsWith("Request")) {
                continue;
            }
            String src = stripComments(read(d.service()));
            Set<String> written = writtenFromRequest(src, d.method());
            if (written.isEmpty()) {
                continue;
            }
            for (String key : feKeys(api, d.feType())) {
                if (!written.contains(key)) {
                    ignored.add(d.path() + " :: " + key);
                }
            }
        }
        assertThat(ignored)
            .as("""
                A field an inbound-only request type declares that the server never writes: \
                either a form input that silently does nothing, or a deliberate immutable. Say \
                which, in DERIVED_UNWRITTEN_FIELDS, with a reason.""")
            .containsExactlyInAnyOrderElementsOf(DERIVED_UNWRITTEN_FIELDS.keySet());
        assertThat(DERIVED_UNWRITTEN_FIELDS.values())
            .allSatisfy(r -> assertThat(r).hasSizeGreaterThan(40));
    }

    @Test
    @DisplayName("the derived population is measured, and it covers every hand-listed site")
    void theDerivedPopulationSubsumesTheHandListedOne() {
        List<Derived> derived = derivedSites();
        Set<String> paths = new LinkedHashSet<>();
        derived.forEach(d -> paths.add(d.path()));

        assertThat(derived)
            .as("""
                Entity-bound PUT handlers joined to a frontend api.put with a named payload type. \
                Asserted EXACTLY, not as a ceiling: a decrease-only count cannot express "the \
                population was undercounted", which is the finding this leg exists to close \
                (F2-204). If a new typed client appears, or the resolver stops resolving, this \
                number moves and the reason has to be said out loud.""")
            .hasSize(30);

        assertThat(derived.stream().filter(d -> d.feType().endsWith("Request")).count())
            .as("inbound-only request types among the derived sites — the reverse leg's population")
            .isEqualTo(20L);

        // Anti-rot: every endpoint a human had enumerated must still be found by the derivation.
        for (Site s : SITES) {
            String path = normalisePath(s.endpoint().replaceAll("^\\w+\\s+", ""));
            assertThat(paths)
                .as("the derivation lost a site the hand-written list had: %s. A derived "
                    + "population that silently shrinks reads exactly like a clean tree", path)
                .contains(path);
        }
    }

    @Test
    @DisplayName("the derived-population detectors are not vacuous (positive control)")
    void theDerivedDetectorsAreNotVacuous() {
        // A RESET: unconditional write, field absent from the client type.
        String resetting = """
            public class Sample {
                public Ropa updateRopa(Long id, Ropa req) {
                    Ropa existing = getRopa(id);
                    existing.setPurposeEn(req.getPurposeEn());
                    existing.setPurposeAr(req.getPurposeAr());
                    return repo.save(existing);
                }
            }
            """;
        String api = "export interface RopaRequest { purposeEn?: string; }";
        Set<String> unguarded = unguardedWritesInClosure(resetting, "updateRopa");
        assertThat(unguarded).contains("purposeEn", "purposeAr");
        Set<String> offending = new LinkedHashSet<>(unguarded);
        offending.removeAll(feKeys(api, "RopaRequest"));
        assertThat(offending)
            .as("the RESET detector no longer sees an unconditional write of an undeclarable field")
            .containsExactly("purposeAr");

        // The same shape GUARDED is frozen, never a reset — the distinction the split rests on.
        String guarded = """
            public class Sample {
                public Sched update(Long id, Sched req) {
                    Sched t = get(id);
                    if (req.getName()   != null) t.setName(req.getName());
                    if (req.getNameAr() != null) t.setNameAr(req.getNameAr());
                    return repo.save(t);
                }
            }
            """;
        assertThat(unguardedWritesInClosure(guarded, "update"))
            .as("a null-guarded write must never read as a reset — this is the whole split")
            .isEmpty();
        assertThat(writtenFromRequest(guarded, "update")).contains("name", "nameAr");

        // The transitive hop: two levels of helper, which one hop cannot reach.
        String twoHops = """
            public class Sample {
                public Ctl updateControl(Long id, Ctl req) {
                    Ctl existing = get(id);
                    applyEditable(existing, req);
                    return repo.save(existing);
                }
                private void applyEditable(Ctl target, Ctl req) {
                    target.setTitleEn(req.getTitleEn());
                    applyFrameworkReference(target, req);
                }
                private void applyFrameworkReference(Ctl target, Ctl req) {
                    target.setFrameworkName(req.getFrameworkName());
                }
            }
            """;
        assertThat(helperClosure(twoHops, "updateControl"))
            .containsExactlyInAnyOrder("applyEditable", "applyFrameworkReference");
        assertThat(writtenFromRequest(twoHops, "updateControl"))
            .as("a single helper hop loses the second level, and it loses it into the REVERSE leg")
            .contains("titleEn", "frameworkName");
        assertThat(privateHelpersCalledWithRequest(twoHops, "updateControl"))
            .as("the single hop reaches only the first level — the limit the closure removes")
            .containsExactly("applyEditable");

        // ⚠ A RESET two helpers deep. Without this the mutation that drops the closure from
        // unguardedWritesInClosure is invisible: on a clean corpus the reset set is empty either
        // way, so the leg above passes and the harness reports a survivor as a gate hole.
        String deepReset = """
            public class Sample {
                public Ctl updateControl(Long id, Ctl req) {
                    Ctl existing = get(id);
                    applyEditable(existing, req);
                    return repo.save(existing);
                }
                private void applyEditable(Ctl target, Ctl req) {
                    applyFrameworkReference(target, req);
                }
                private void applyFrameworkReference(Ctl target, Ctl req) {
                    target.setFrameworkNameAr(req.getFrameworkNameAr());
                }
            }
            """;
        assertThat(unguardedWritesInClosure(deepReset, "updateControl"))
            .as("an unguarded write two helpers down is still a reset")
            .containsExactly("frameworkNameAr");
    }

    // ── leg 4: a client typed to send a subset needs a server that tolerates one ───────────

    /**
     * F2-214 — every entity-bound PUT whose client sends a {@code Partial<T>}, and the method
     * that answers it. Enforced in BOTH directions by the two tests below: a new such endpoint
     * must be added here, and an entry that stops being one must be removed.
     *
     * <p>Measured 2026-08-15. All three wrote every field unconditionally.
     */
    private static final Map<String, String> PARTIAL_PAYLOAD_ENTITY_PUTS = Map.ofEntries(
        Map.entry("/api/v1/privacy/dpias/{}",       "DpiaService#update"),
        Map.entry("/api/v1/bcm/comms/templates/{}", "CrisisCommsService#updateTemplate"),
        Map.entry("/api/v1/bcm/comms/audiences/{}", "CrisisCommsService#updateAudience"),
        Map.entry("/api/v1/ethics/cases/{}",        "EthicsCaseService#update"),
        Map.entry("/api/v1/ethics/disclosures/{}",  "DisclosureService#update"),
        Map.entry("/api/v1/ethics/kyc/{}",          "KycService#update"),
        Map.entry("/api/v1/ethics/aml-alerts/{}",   "AmlAlertService#update"),
        Map.entry("/api/v1/changes/{}",             "ChangeManagementService#update"),
        Map.entry("/api/v1/changes/tasks/{}",       "ChangeManagementService#updateTask"),
        Map.entry("/api/v1/reporting/schedules/{}", "ReportScheduleService#update"),
        Map.entry("/api/v1/cyber/policies/{}",      "PolicyManagementService#update"));

    /**
     * Fields written from the request WITHOUT a null guard on the same accessor.
     *
     * <p>This distinction is the whole finding, and a setter-site scan cannot make it — F2-182
     * recorded that a guard hides in at least four places a regex cannot see, and it cost this
     * slice a false alarm: {@code PolicyManagementService#update} reads as twenty unconditional
     * writes and is in fact twenty guarded ones, i.e. a correct partial update. A gate that
     * failed on it would have been worse than no gate.
     */
    static Set<String> unguardedWritesFromRequest(String src, String method) {
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
        for (String v : reqVars) {
            Matcher s = Pattern.compile(
                "\\.\\s*set([A-Z]\\w*)\\s*\\(\\s*" + Pattern.quote(v)
                    + "\\s*\\.\\s*(get|is)?(\\w+)\\s*\\(\\s*\\)").matcher(body);
            while (s.find()) {
                String accessor = (s.group(2) == null ? "" : s.group(2)) + s.group(3);
                String before = body.substring(Math.max(0, s.start() - 240), s.start());
                boolean guarded = Pattern.compile(
                    "\\b" + Pattern.quote(v) + "\\s*\\.\\s*" + Pattern.quote(accessor)
                        + "\\s*\\(\\s*\\)\\s*(?:!=|==)\\s*null").matcher(before).find();
                if (!guarded) {
                    out.add(Character.toLowerCase(s.group(1).charAt(0)) + s.group(1).substring(1));
                }
            }
        }
        return out;
    }

    /** Normalised paths of every PUT handler that binds a JPA entity. */
    static Set<String> entityBoundPutPaths() {
        Set<String> entities = entitySimpleNames();
        Set<String> paths = new LinkedHashSet<>();
        for (Path p : javaSources()) {
            if (!p.getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            String src = stripComments(read(p));
            Matcher base = Pattern.compile("@RequestMapping\\s*\\(\\s*\"([^\"]*)\"").matcher(src);
            String prefix = base.find() ? base.group(1) : "";
            Matcher m = Pattern.compile("@PutMapping\\s*\\(([^)]*)\\)([\\s\\S]{0,1500}?)\\)\\s*\\{")
                .matcher(src);
            while (m.find()) {
                Matcher b = Pattern.compile(
                    "@RequestBody\\s+(?:@Valid\\s+)?([A-Z]\\w*)|@Valid\\s+@RequestBody\\s+([A-Z]\\w*)")
                    .matcher(m.group(2));
                if (!b.find()) {
                    continue;
                }
                String t = b.group(1) != null ? b.group(1) : b.group(2);
                if (!entities.contains(t)) {
                    continue;
                }
                Matcher sub = Pattern.compile("\"([^\"]*)\"").matcher(m.group(1));
                paths.add(normalisePath(prefix + (sub.find() ? sub.group(1) : "")));
            }
        }
        return paths;
    }

    /** {@code /a/{id}} and {@code /a/${id}} both become {@code /a/{}} so the two sides compare. */
    static String normalisePath(String p) {
        String out = p.replaceAll("\\$\\{[^}]*}|\\{[^}]*}", "{}").replace("//", "/");
        return out.endsWith("/") ? out.substring(0, out.length() - 1) : out;
    }

    /** Normalised paths of every {@code api.put} whose payload parameter is a {@code Partial<>}. */
    static Set<String> partialPayloadPutPaths(String api) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile(
            "api\\.put\\s*(?:<[^>]*>)?\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]\\s*,\\s*(\\w+)\\s*[,)]")
            .matcher(api);
        while (m.find()) {
            String before = api.substring(Math.max(0, m.start() - 400), m.start());
            if (Pattern.compile(Pattern.quote(m.group(2)) + "\\s*:\\s*Partial<").matcher(before).find()) {
                out.add(normalisePath(m.group(1)));
            }
        }
        return out;
    }

    @Test
    @DisplayName("an update whose client sends a Partial<> may not write any field unconditionally")
    void aPartialClientRequiresAGuardedServerWrite() throws IOException {
        Map<String, String> offenders = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : PARTIAL_PAYLOAD_ENTITY_PUTS.entrySet()) {
            String[] parts = e.getValue().split("#");
            Path svc = javaSources().stream()
                .filter(p -> p.getFileName().toString().equals(parts[0] + ".java"))
                .findFirst().orElseThrow(() ->
                    new AssertionError("declared service does not exist: " + e.getValue()));
            Set<String> bad = unguardedWritesFromRequest(stripComments(read(svc)), parts[1]);
            if (!bad.isEmpty()) {
                offenders.put(e.getValue() + "  (" + e.getKey() + ")", String.join(", ", bad));
            }
        }
        assertThat(offenders)
            .as("""
                The client for this endpoint is typed `Partial<T>` — it positively permits sending \
                a subset — and the server writes these fields unconditionally, so every field the \
                caller omits is written null. Measured 2026-08-15: DpiaService#update would erase a \
                PDPL Article 25 assessment's title in both languages, its trigger, its residual-risk \
                rating and its dates (5/5, 5/5, 5/5, 4/5 live rows, dev = prod). Guard each write — \
                PolicyManagementService#update is the idiom — or change the client off Partial<>.""")
            .isEmpty();
    }

    @Test
    @DisplayName("the Partial-payload entity-PUT population is exactly what is declared")
    void thePartialPayloadPopulationIsExactlyDeclared() throws IOException {
        String api = stripComments(Files.readString(FE_API));
        Set<String> computed = new LinkedHashSet<>(partialPayloadPutPaths(api));
        computed.retainAll(entityBoundPutPaths());

        assertThat(partialPayloadPutPaths(api))
            .as("no api.put takes a Partial<> payload — the scan has rotted, not the tree")
            .hasSizeGreaterThan(10);
        assertThat(computed)
            .as("""
                Entity-bound PUTs whose client sends a Partial<>. Asserted EXACTLY, in both \
                directions: a new one must be declared in PARTIAL_PAYLOAD_ENTITY_PUTS and checked \
                by the leg above, and one that stops being Partial<> must lose its entry. A \
                decrease-only ceiling would let a new offender hide behind a removed one.""")
            .containsExactlyInAnyOrderElementsOf(PARTIAL_PAYLOAD_ENTITY_PUTS.keySet());
    }

    @Test
    @DisplayName("the guard detector separates a partial update from a full replace (positive control)")
    void theGuardDetectorIsNotVacuous() {
        String preFix = """
            public class Sample {
                public Dpia update(Long id, Dpia req) {
                    Dpia d = mustGet(id);
                    d.setTitleEn(req.getTitleEn());
                    d.setConclusion(req.getConclusion());
                    return dpiaRepo.save(d);
                }
            }
            """;
        assertThat(unguardedWritesFromRequest(preFix, "update"))
            .as("the detector no longer fires on the exact pre-fix code — it has rotted")
            .containsExactlyInAnyOrder("titleEn", "conclusion");

        String fixed = """
            public class Sample {
                public Dpia update(Long id, Dpia req) {
                    Dpia d = mustGet(id);
                    if (req.getTitleEn() != null) d.setTitleEn(req.getTitleEn());
                    if (req.getConclusion() != null) d.setConclusion(req.getConclusion());
                    return dpiaRepo.save(d);
                }
            }
            """;
        assertThat(unguardedWritesFromRequest(fixed, "update"))
            .as("the detector fires on the corrected shape — it would fail on a clean tree")
            .isEmpty();

        // ⚠ The false alarm this leg had to survive: a block-bodied guard, and a guard on a
        // BOOLEAN accessor spelled `isX()` rather than `getX()`. Both are correct code.
        String blockGuard = """
            public class Sample {
                public Audience update(Long id, Audience req) {
                    Audience a = mustGet(id);
                    if (req.getContactsCsv() != null) {
                        a.setContactsCsv(req.getContactsCsv());
                        a.setRecipientCount(count(req.getContactsCsv()));
                    }
                    if (req.isActive() != null) { a.setActive(req.isActive()); }
                    return repo.save(a);
                }
            }
            """;
        assertThat(unguardedWritesFromRequest(blockGuard, "update"))
            .as("a block-bodied guard, and an `isX()` boolean accessor, are guards")
            .isEmpty();
    }
}
