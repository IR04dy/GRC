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
 * F2-222 — an ATTRIBUTION reaches a service as a scalar parameter, and F2-218's gate reads a scalar
 * parameter as caller data.
 *
 * <p><b>Why this exists.</b> {@link ServerStampedFieldContractTest} asks whether a create ACCEPTS a
 * field that a guarded route on the same service STAMPS. Its fourth detector correction — <i>"a
 * scalar method parameter is still caller data"</i> — was added because
 * {@code markUnderReview(Long id, String reviewerName)} was being read as a server stamp when the
 * controller passes a body field. That correction is right at the <b>declaration</b> and wrong at
 * the <b>call site</b>: {@code PolicyManagementService#transition(Long id, String to, String actor)}
 * receives {@code actor} from {@code actor(auth)} at every call site, so
 * {@code p.setApproverName(actor)} <i>is</i> a server stamp — and because the gate could not see
 * that, {@code PolicyDocument#approverName} never entered the stamped set and
 * {@code update()} writing it from the request was not a contradiction by that gate's question.
 *
 * <p>🔑 <b>Whether a scalar parameter is caller data or a server value is a fact about its CALL
 * SITES, and the gate that had to decide it only had the declaration.</b>
 *
 * <p>So this gate resolves the parameter where the answer lives. A field written from a parameter
 * that <i>every</i> controller call site fills from the authenticated principal is an attribution;
 * no write path on the same service may then write it from the request.
 *
 * <p><b>Hermetic.</b> Source only, like {@code ConnectorBackingContractTest} and
 * {@link StateMachineColumnMirrorTest} — no database, no Spring context.
 */
class PrincipalStampedFieldContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    /**
     * Expressions a controller uses to name the authenticated caller. Deliberately a closed list:
     * anything else is treated as caller data, so an unrecognised shape narrows this gate rather
     * than inventing an attribution for it.
     */
    private static final Pattern PRINCIPAL = Pattern.compile(
        "\\bactor\\s*\\(|\\bauth\\s*\\.\\s*getName\\s*\\(|\\bprincipal\\s*\\.\\s*getName\\s*\\(|"
        + "\\bSecurityContextHolder\\b|\\bcurrentUsername\\s*\\(|\\bcurrentUser\\s*\\(|"
        + "\\busername\\s*\\(\\s*auth|^\\s*actor\\s*$|^\\s*principal\\s*$");

    /**
     * Attributions this gate has resolved, and the write path that must not overwrite them.
     *
     * <p>Empty is the passing state. Anything here is a live bypass: the guarded route establishes
     * who acted, and a same-permission write path lets the caller replace it.
     */
    private static final int BASELINE_OVERWRITABLE_ATTRIBUTIONS = 0;

    // ── plumbing ─────────────────────────────────────────────────────────────

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> sources(String suffix) {
        try (var s = Files.walk(MAIN_JAVA)) {
            return s.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Split a call's argument list on top-level commas — nested calls and generics survive. */
    static List<String> splitArgs(String args) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(' || c == '<' || c == '[') depth++;
            else if (c == ')' || c == '>' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                out.add(args.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < args.length()) out.add(args.substring(start).trim());
        return out;
    }

    private static String balanced(String src, int openParen) {
        int depth = 0;
        for (int j = openParen; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return src.substring(openParen + 1, j);
        }
        return "";
    }

    /**
     * {@code methodName/arity → the argument expressions seen at controller call sites}, one list
     * per call site. Keyed by name and arity rather than by owning class on purpose: a name that
     * two services share simply merges their call sites, which can only make this gate narrower.
     */
    static Map<String, List<List<String>>> controllerCallSites() {
        Map<String, List<List<String>>> out = new TreeMap<>();
        for (Path p : sources("Controller.java")) {
            String src = ServerStampedFieldContractTest.stripComments(read(p));
            Matcher m = Pattern.compile("\\b(?:service|svc|\\w+Service)\\s*\\.\\s*(\\w+)\\s*\\(")
                               .matcher(src);
            while (m.find()) {
                List<String> args = splitArgs(balanced(src, m.end() - 1));
                out.computeIfAbsent(m.group(1) + "/" + args.size(), k -> new ArrayList<>())
                   .add(args);
            }
        }
        return out;
    }

    /** A scalar (non-payload) parameter: {@code String actor}, {@code Long id}, … */
    private static final Set<String> SCALARS =
        Set.of("String", "Long", "Integer", "Boolean", "int", "long", "boolean");

    private static Map<String, Integer> scalarParamIndex(String params) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> parts = splitArgs(params);
        for (int i = 0; i < parts.size(); i++) {
            String[] tok = parts.get(i).trim().split("\\s+");
            if (tok.length >= 2 && SCALARS.contains(tok[tok.length - 2])) {
                out.put(tok[tok.length - 1], i);
            }
        }
        return out;
    }

    /** Is this parameter filled from the authenticated principal at EVERY controller call site? */
    static boolean principalAtEveryCallSite(String method, String params, int index,
                                            Map<String, List<List<String>>> callSites) {
        int arity = splitArgs(params).size();
        List<List<String>> sites = callSites.get(method + "/" + arity);
        if (sites == null || sites.isEmpty()) return false;   // unresolved is never an attribution
        for (List<String> args : sites) {
            if (index >= args.size()) return false;
            if (!PRINCIPAL.matcher(args.get(index)).find()) return false;
        }
        return true;
    }

    private record Attribution(String service, String field, String route, String param) { }

    /** Every {@code Type#field} a guarded route stamps from a principal-derived scalar parameter. */
    static List<Attribution> attributions() {
        Map<String, List<List<String>>> callSites = controllerCallSites();
        List<Attribution> out = new ArrayList<>();

        for (Path p : sources("Service.java")) {
            String cls = p.getFileName().toString().replace(".java", "");
            String src = ServerStampedFieldContractTest.stripComments(read(p));
            for (var m : ServerStampedFieldContractTest.publicMethods(src)) {
                if (isWritePath(m.name())) continue;                    // a create is not a route
                if (!m.body().contains("throw new")) continue;          // nor is a bare setter
                Map<String, String> types =
                    ServerStampedFieldContractTest.localTypes(m.params(), m.body());
                Map<String, Integer> scalars = scalarParamIndex(m.params());

                Matcher w = Pattern.compile("\\b(\\w+)\\s*\\.\\s*set(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)")
                                   .matcher(m.body());
                while (w.find()) {
                    String target = w.group(1), field = lower(w.group(2)), value = w.group(3);
                    Integer idx = scalars.get(value);
                    if (idx == null) continue;
                    String type = types.get(target);
                    if (type == null) continue;
                    if (!principalAtEveryCallSite(m.name(), m.params(), idx, callSites)) continue;
                    out.add(new Attribution(cls, type + "#" + field, m.name(), value));
                }
            }
        }
        return out;
    }

    /**
     * Is EVERY write of this field in this body conditioned on the loaded row's own state?
     *
     * <p>An attribution may legitimately be set before the act it attributes — that is how the
     * intended approving body gets recorded on a draft — so the rule is not "never written from the
     * request", it is "never written once the act has happened". A write guarded by the row's
     * status is that; a bare {@code if (body.getX() != null)} is not.
     *
     * <p>Uses {@link ServerStampedFieldContractTest#guardBefore}, which F2-219 already corrected to
     * see block-bodied guards — writing a second one here is how two instruments end up
     * disagreeing about the same question.
     */
    static boolean stateGuarded(String body, String field) {
        String setter = "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1) + "(";
        int at = body.indexOf(setter), seen = 0;
        while (at >= 0) {
            seen++;
            if (!ServerStampedFieldContractTest.guardBefore(body, at).contains("getStatus()")) {
                return false;
            }
            at = body.indexOf(setter, at + 1);
        }
        return seen > 0;
    }

    private static boolean isWritePath(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("create") || n.startsWith("update") || n.startsWith("save")
            || n.startsWith("add") || n.startsWith("record") || n.startsWith("upsert")
            || n.startsWith("ingest");
    }

    private static String lower(String setterSuffix) {
        return Character.toLowerCase(setterSuffix.charAt(0)) + setterSuffix.substring(1);
    }

    // ── the assertions ───────────────────────────────────────────────────────

    /**
     * The gate. A field established by a guarded route from the acting principal may not be
     * rewritten by a write path on the same service.
     */
    @Test
    void noWritePathOverwritesAnAttributionItsOwnServiceStamps() {
        Map<String, Set<String>> readOnly = ServerStampedFieldContractTest.readOnlyOnTheWire();
        Map<String, Set<String>> byService = new TreeMap<>();
        for (Attribution a : attributions()) {
            byService.computeIfAbsent(a.service(), k -> new TreeSet<>()).add(a.field());
        }

        List<String> bypasses = new ArrayList<>();
        for (Path p : sources("Service.java")) {
            String cls = p.getFileName().toString().replace(".java", "");
            Set<String> stamped = byService.get(cls);
            if (stamped == null) continue;
            String src = ServerStampedFieldContractTest.stripComments(read(p));
            for (var m : ServerStampedFieldContractTest.publicMethods(src)) {
                if (!isWritePath(m.name())) continue;
                Map<String, String> paramTypes =
                    ServerStampedFieldContractTest.requestParamTypes(m.params());
                if (paramTypes.isEmpty()) continue;
                Map<String, String> types =
                    ServerStampedFieldContractTest.localTypes(m.params(), m.body());
                Set<String> written = ServerStampedFieldContractTest.writesFromRequest(
                    m.body(), types, paramTypes.keySet());
                for (String f : written) {
                    String field = f.substring(f.indexOf('#') + 1);
                    String type = f.substring(0, f.indexOf('#'));
                    if (ServerStampedFieldContractTest.CLOSED_AT_THE_WIRE.contains(field)) continue;
                    if (readOnly.getOrDefault(type, Set.of()).contains(field)) continue;
                    if (!stamped.contains(f)) continue;
                    if (stateGuarded(m.body(), field)) continue;
                    bypasses.add(cls + "#" + m.name() + " -> " + f);
                }
            }
        }

        assertThat(bypasses)
            .as("A guarded route on this service establishes who acted, from the authenticated "
                + "principal. A write path guarded by the SAME permission then lets the caller "
                + "replace that answer — probed live on PolicyDocument#approverName, where the "
                + "author C-6 had just refused with a 403 wrote their own name with a 200.")
            .hasSizeLessThanOrEqualTo(BASELINE_OVERWRITABLE_ATTRIBUTIONS);
    }

    /**
     * Anti-vacuity. At zero, a rotted detector reports an empty population and that is
     * indistinguishable from a finished sweep (F2-219), so the population this gate RESOLVES is
     * asserted too — not the bypasses it finds.
     */
    @Test
    void theGateActuallyResolvesAttributionsToCheck() {
        List<Attribution> found = attributions();

        assertThat(found)
            .as("if this is empty the gate above passes for free — the call-site resolution, the "
                + "principal vocabulary or the guarded-route discriminator has rotted")
            .isNotEmpty();
        assertThat(found).extracting(Attribution::field)
            .as("PolicyDocument#approverName is the instance this gate was built from: "
                + "transition(id, to, actor) is called as service.transition(id, body.get(\"to\"), "
                + "actor(auth), ...) at its only call site")
            .contains("PolicyDocument#approverName");
    }

    /**
     * The narrowing that stops this gate firing on correct code: a scalar parameter filled from the
     * request body at any call site is caller data, exactly as F2-218's fourth correction says.
     */
    @Test
    void aScalarFilledFromTheBodyAtAnyCallSiteIsNotAnAttribution() {
        Map<String, List<List<String>>> sites = new TreeMap<>();
        sites.put("markUnderReview/2",
                  List.of(List.of("id", "req.getReviewerName()")));
        assertThat(principalAtEveryCallSite("markUnderReview", "Long id, String reviewerName", 1,
                                            sites))
            .as("F2-218's fourth correction, kept: the controller passes a body field, so the "
                + "parameter is caller data and the service is not stamping anything")
            .isFalse();

        sites.put("transition/3", List.of(List.of("id", "body.get(\"to\")", "actor(auth)")));
        assertThat(principalAtEveryCallSite("transition", "Long id, String to, String actor", 2,
                                            sites))
            .as("and the shape it could not see: the same declaration, filled from the principal")
            .isTrue();

        sites.put("mixed/2", List.of(List.of("id", "actor(auth)"), List.of("id", "req.getWho()")));
        assertThat(principalAtEveryCallSite("mixed", "Long id, String who", 1, sites))
            .as("EVERY call site, not any — one body-filled caller makes the field caller data")
            .isFalse();

        assertThat(principalAtEveryCallSite("neverCalled", "Long id, String who", 1, sites))
            .as("unresolved must never read as an attribution — 'no call site' and 'a principal "
                + "at every call site' are the error directions this programme keeps confusing")
            .isFalse();
    }

    /**
     * The other narrowing, in both directions. Without a positive control here, a
     * {@link #stateGuarded} that answered {@code true} for everything would make the gate above
     * vacuous and still pass.
     */
    @Test
    void onlyAWriteConditionedOnTheRowsOwnStateIsExemptFromTheGate() {
        String bare = "if (body.getApproverName() != null) p.setApproverName(body.getApproverName());";
        assertThat(stateGuarded(bare, "approverName"))
            .as("the pre-fix shape: a null check is not a state guard, it only asks whether the "
                + "caller sent the field")
            .isFalse();

        String guarded = "if (BEFORE_APPROVAL.contains(p.getStatus())) {\n"
            + "  if (body.getApproverName() != null) p.setApproverName(body.getApproverName());\n}";
        assertThat(stateGuarded(guarded, "approverName")).isTrue();

        String half = guarded + "\np.setApproverName(body.getApproverName());";
        assertThat(stateGuarded(half, "approverName"))
            .as("EVERY write, not the first — one unguarded site out of two is the whole defect, "
                + "and a first-occurrence check reports it as closed")
            .isFalse();

        assertThat(stateGuarded("p.setTitleEn(body.getTitleEn());", "approverName"))
            .as("a field this body never writes is not vacuously guarded")
            .isFalse();
    }

    /** The argument splitter must survive nested calls, generics and string literals with commas. */
    @Test
    void argumentSplittingSurvivesNestingAndGenerics() {
        assertThat(splitArgs("id, body.get(\"to\"), actor(auth), isComplianceAdmin(auth)"))
            .containsExactly("id", "body.get(\"to\")", "actor(auth)", "isComplianceAdmin(auth)");
        assertThat(splitArgs("id, List.<String>of(a, b), c"))
            .as("a top-level split that counts commas inside a nested call shifts every index "
                + "after it, and an index shift silently resolves the wrong parameter")
            .containsExactly("id", "List.<String>of(a, b)", "c");
    }
}
