package com.tss.grc.security;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-170 — does the endpoint the frontend calls actually EXIST?
 *
 * <p>Three instruments already ask a reachability question and none of them can ask this one:
 * <ul>
 *   <li>F2-68(c) {@code apiClientReachability} — "does any screen import this client export?"</li>
 *   <li>F2-165 {@code apiClientMethodReachability} — the same, one granularity down, per method.</li>
 *   <li>F2-163 {@code routeReachability} — "can the user navigate to this screen?"</li>
 * </ul>
 * All three look only at the frontend. None crosses the wire. So the failure mode they cannot see
 * is a client that is imported, called from a live screen, reached by navigation — and pointed at a
 * path no controller serves.
 *
 * <h3>Why that is worth a hard zero rather than a report</h3>
 * <b>F2-158</b> is the precedent: {@code RoleController#list} had answered <b>500 to every caller
 * since it was written</b> — the entire Role Management API — and nobody noticed, because
 * {@code AdminPage.tsx} wired mock roles into all three failure paths and the screen always
 * rendered plausible rows. The mock fallback was load-bearing. <b>F2-169 has just deleted those
 * fallbacks</b> (19 undisclosed substitutions across 9 files), which is exactly why this gate is
 * worth having now: a broken path no longer renders fabricated data, it renders a failure toast.
 * The instrument that could have caught F2-158 on day one is this one, and it did not exist.
 *
 * <h3>Reading across the module boundary</h3>
 * Deliberate, following {@code FrontendSkuVocabularyTest} and {@code AttachmentEntityTypeCoverage
 * Test}: the invariant genuinely spans the two codebases and the alternative is the drift. When the
 * frontend is not checked out the test skips, and anti-vacuity floors make sure a skip — or a
 * degraded parser — cannot be mistaken for a pass.
 *
 * <h3>⚠ The backend side is REFLECTION, not a source scan, and that is not a style preference</h3>
 * The measurement behind this class was first written as a regex over controller source. It
 * reported <b>1,763 of 1,809 frontend endpoints as nonexistent</b>. Two separate bugs, each of
 * which produced a plausible catastrophe:
 * <ol>
 *   <li>The class-keyword search cut the header before the annotations, so no class-level
 *       {@code @RequestMapping} base path was ever found and nothing could match.</li>
 *   <li>After fixing that, 160 remained — and 48 of them were the whole of {@code /api/v1/cyber},
 *       because {@code CyberController}'s javadoc contains the phrase <em>"the former CyberService
 *       god-class was split"</em>. {@code \bclass\s+\w+} matched the prose <b>"class was"</b> at
 *       offset 1719, before the real annotation at 1934. A live probe answered <b>200 for every one
 *       of the 48</b>.</li>
 * </ol>
 * 🔑 <b>An extractor that finds nothing looks exactly like a catastrophic defect</b>, and prose in a
 * comment is the third scanner in this programme to be mistaken for code (F2-165's own first draft
 * did it with a regex literal; F2-169's did it with comment text describing the fix). Reflection
 * cannot make either mistake: it reads merged annotations off compiled classes, so javadoc is
 * invisible and {@code @GetMapping} arrives already merged into {@code @RequestMapping}.
 *
 * <h3>Deliberately conservative in ONE direction</h3>
 * A frontend {@code {}} means "a value the client substitutes" — which may be an id
 * ({@code /risks/${id}}) or a <em>literal name</em>. {@code integrationsApi} writes
 * {@code `${BASE}/${key}/config`} where {@code key} is the union
 * {@code 'tenable' | 'crowdstrike' | 'splunk' | 'servicenow'} and there is one controller per
 * adapter with the provider baked into its base path; {@code workflowRequests} writes
 * {@code `/requests/${id}/${action}`} against literal {@code /approve}, {@code /reject}, … So a
 * {@code {}} on either side matches a {@code {}} <em>or</em> a literal segment. That relaxation is
 * why this can be a <b>hard zero with no allow-list at all</b> — and an allow-list avoided is worth
 * more than a tighter match, because an allow-list is where a real defect goes to be tolerated
 * (the {@code BARE_EXPORT_CEILING} lesson, F2-61(c)).
 */
class FrontendEndpointContractTest {

    private static final Path FE_LIB = Path.of("..", "tss-grc-ui-v2", "src", "lib");

    /**
     * Backend endpoints no frontend client calls — a decrease-only ceiling.
     *
     * <p>Measured {@value} on 2026-08-09. This is the same question F2-165 asks, at <b>endpoint</b>
     * granularity instead of <b>method</b> granularity, and it comes out <b>2.2x larger</b> than the
     * 189 that finding publishes (421 vs 189): one client method can serve several endpoints, and a whole
     * controller with no client at all has no method to be dark. ⚙ That is F2-165's own lesson
     * turned on F2-165 — <em>an instrument's granularity is its blind spot, and the blind spot is
     * invisible from inside the instrument.</em>
     *
     * <p>A ceiling and not a list, deliberately: 421 cannot be wired in one slice, and an
     * exhaustive red list of that size becomes an allow-list nobody reads. The per-endpoint
     * adjudication — which of these is a gap in a live workflow rather than an unbuilt feature —
     * lives in F2-165's {@code KNOWN_DARK}, annotated. This number exists so the population cannot
     * grow silently, and so it cannot go stale unnoticed the way an ungateable figure in prose does
     * (F2-162).
     *
     * <p><b>421 -> 417 on 2026-08-09 (F2-172).</b> The intake substrate was the largest single
     * clientless block with a real workflow behind it: {@code POST /api/v1/intake},
     * {@code GET /api/v1/intake}, {@code GET /api/v1/intake/'{'}id{'}'} and
     * {@code PUT /api/v1/intake/'{'}id{'}'}/triage} had existed since DD-82 and no product code
     * called any of them, because the frontend filed every submission into {@code localStorage}.
     * ⚙ Note which direction this test fired in: it <em>refused the commit</em> that added
     * {@code GET /'{'}id{'}'} with no caller, which is what made the client rewire part of the same
     * slice rather than a follow-up. A ceiling that only ever complains upward would have let a
     * fifth dark endpoint land beside the four it was replacing.
     */
    private static final int BASELINE_CLIENTLESS_ENDPOINTS = 410;

    /**
     * Of those, the ones that are guarded WRITES — a governed capability with no caller at all.
     *
     * <p>⚠ Measured <b>208</b>, and the first draft of this measurement said <b>163</b>. The
     * difference is a class-level {@code @PreAuthorize}: a source-regex looking backwards from the
     * mapping for the nearest annotation never sees a guard declared on the controller, so 45
     * governed endpoints read as ungoverned. Reflection resolves both, which is the other reason
     * this side is not a text scan.
     *
     * <p>Checked against the two things a count cannot tell you (2026-08-09, dev): none of these is
     * locked to a nonexistent role — <b>0 ghost names</b> and <b>0 endpoints reachable only by
     * PLATFORM_ADMIN</b> — so this is unbuilt UI, not an F2-161-style lockout. That check needed
     * correcting too: scraping every quoted uppercase token off a guard reads the MODULE argument of
     * {@code hasPermission('AUDIT','DELETE')} as a role absent from {@code iam.roles}, which
     * invented 21 ghost roles and 126 admin-only endpoints before being scoped to
     * {@code hasAnyRole}/{@code hasRole} arguments. The module vocabulary and the role vocabulary are
     * different namespaces that look identical.
     *
     * <p><b>208 -> 206 on 2026-08-09 (F2-172):</b> intake submit and intake triage. Both were
     * governed writes with no caller, and submit was governed by a permission held by 3 of 69
     * roles — so the pair is a worked example of what this number is for: not unbuilt UI here, but
     * a built workflow whose front door nobody could reach.
     */
    private static final int BASELINE_CLIENTLESS_GUARDED_WRITES = 200;

    // ── the two populations ────────────────────────────────────────────────────────────────────

    private record Handler(String endpoint, String declaredBy, String guard) { }

    /** Every {@code verb /path} a controller serves, keyed with path variables collapsed. */
    private static List<Handler> backendHandlers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Handler> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            var onClass = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
            String[] bases = onClass == null || onClass.path().length == 0
                ? new String[] { "" } : onClass.path();
            for (Method m : c.getMethods()) {
                var ann = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (ann == null) {
                    continue;
                }
                RequestMethod[] verbs = ann.method();
                if (verbs.length == 0) {
                    continue;     // no verb to key on — not a concrete handler
                }
                String[] paths = ann.path().length == 0 ? new String[] { "" } : ann.path();
                var pre = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
                if (pre == null) {
                    pre = AnnotatedElementUtils.findMergedAnnotation(c, PreAuthorize.class);
                }
                String guard = pre == null ? "" : pre.value();
                for (String b : bases) {
                    for (String p : paths) {
                        for (RequestMethod v : verbs) {
                            out.add(new Handler(norm(v.name(), b + p), c.getSimpleName(), guard));
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * `verb /path` for every backend call the frontend's API clients make.
     *
     * <p>Module-level path constants are folded ({@code `${BASE}/${id}/approve`} is the dominant
     * idiom, so without folding almost nothing resolves and every assertion here goes vacuous).
     */
    private static Map<String, String> frontendEndpoints() {
        Map<String, String> out = new TreeMap<>();     // endpoint -> first module that calls it
        for (Path f : frontendClientFiles()) {
            String body = stripComments(read(f));
            Map<String, String> consts = new HashMap<>();
            Matcher pc = PATH_CONST.matcher(body);
            while (pc.find()) {
                consts.put(pc.group(1), pc.group(2));
            }
            Matcher call = API_CALL.matcher(body);
            while (call.find()) {
                String verb = "del".equals(call.group(1)) ? "delete" : call.group(1);
                String raw = call.group(2);
                String path;
                if (raw.startsWith("`") || raw.startsWith("'") || raw.startsWith("\"")) {
                    path = foldConstants(raw.substring(1, raw.length() - 1), consts);
                    path = TEMPLATE_ANY.matcher(path).replaceAll("{}");
                } else {
                    path = consts.get(raw);
                }
                if (path == null || !path.contains("/api/")) {
                    continue;
                }
                // A residual `$` is a literal the API_CALL group truncated at a NESTED backtick
                // (`${q ? `?x=${q}` : ''}`). Not an endpoint; counting one would invent a defect.
                if (path.contains("$")) {
                    continue;
                }
                out.putIfAbsent(norm(verb, path), FE_LIB.relativize(f).toString().replace('\\', '/'));
            }
        }
        return out;
    }

    // ── assertions ─────────────────────────────────────────────────────────────────────────────

    @Test
    void every_endpoint_the_frontend_calls_is_served_by_a_controller() {
        Assumptions.assumeTrue(Files.isDirectory(FE_LIB),
            "frontend not checked out at " + FE_LIB.toAbsolutePath() + " — nothing to contract-check");

        var backend = backendHandlers();
        var frontend = frontendEndpoints();

        // Anti-vacuity FIRST. Every assertion below is meaningless until both sides resolved, and
        // the whole reason this class exists is that a collapsed extractor reads as a catastrophe.
        assertThat(backend.size())
            .as("the classpath scan resolved almost no handlers — the SCAN is broken, not the "
                + "codebase empty. Everything below is vacuous until this passes.")
            .isGreaterThanOrEqualTo(1_800);
        assertThat(frontend.size())
            .as("the frontend endpoint parse collapsed (path-constant folding or the api.* pattern "
                + "stopped matching). A parser that finds nothing passes the contract trivially.")
            .isGreaterThanOrEqualTo(1_500);

        var byShape = shapeIndex(backend.stream().map(Handler::endpoint).toList());
        var orphans = new LinkedHashMap<String, String>();
        frontend.forEach((endpoint, module) -> {
            if (!resolves(endpoint, byShape)) {
                orphans.put(endpoint, module);
            }
        });

        assertThat(orphans)
            .as("These frontend calls have no controller. Each one is a screen that fails for every "
                + "user — the F2-158 shape, which went unnoticed for months because a mock fallback "
                + "rendered plausible rows over it. Fix the path or build the endpoint; do NOT add an "
                + "allow-list, because this population is currently zero and a zero is only worth "
                + "having while it stays one:\n  "
                + orphans.entrySet().stream().map(e -> e.getKey() + "   ← " + e.getValue()).toList())
            .isEmpty();
    }

    @Test
    void the_matcher_recognises_a_real_endpoint_and_rejects_a_fabricated_one() {
        Assumptions.assumeTrue(Files.isDirectory(FE_LIB), "frontend not checked out");
        var byShape = shapeIndex(backendHandlers().stream().map(Handler::endpoint).toList());

        // Positive controls: a plain collection, and — the one that matters — a path variable with a
        // literal tail after it, which is the idiom the relaxed matcher has to get right.
        for (String live : List.of("get /api/v1/risk/risks",
                                   "get /api/v1/compliance/findings",
                                   "get /api/v1/bcm/exercise-runs/{}/timeline")) {
            assertThat(resolves(live, byShape)).as("%s should resolve — repoint this control", live).isTrue();
        }
        // Negative controls. The second is the real one: `POST /risks/{id}/accept` is the ungoverned
        // one-click acceptance bypass **F2-164 retired**. A matcher that cannot notice a deliberate
        // retirement cannot notice a typo either — and unlike a fabricated path, this one was really
        // there, really called from a screen, and is really gone.
        for (String dead : List.of("get /api/v1/definitely/not/a/real/endpoint",
                                   "post /api/v1/risks/{}/accept",
                                   "post /api/v1/risk/risks/{}/definitely-not-an-action")) {
            assertThat(resolves(dead, byShape)).as("%s must NOT resolve — the matcher is too loose", dead).isFalse();
        }
    }

    @Test
    void backend_endpoints_with_no_client_only_decrease() {
        Assumptions.assumeTrue(Files.isDirectory(FE_LIB), "frontend not checked out");
        var backend = backendHandlers();
        var frontend = frontendEndpoints();
        assertThat(frontend.size()).as("frontend parse collapsed — the count below would read as progress")
            .isGreaterThanOrEqualTo(1_500);

        var feShape = shapeIndex(frontend.keySet());
        var clientless = new TreeSet<String>();
        var clientlessGuardedWrites = new TreeSet<String>();
        for (Handler h : backend) {
            if (resolves(h.endpoint(), feShape)) {
                continue;
            }
            clientless.add(h.endpoint());
            boolean write = Stream.of("post ", "put ", "patch ", "delete ").anyMatch(v -> h.endpoint().startsWith(v));
            boolean guarded = h.guard().contains("hasPermission") || h.guard().contains("hasAnyRole");
            if (write && guarded) {
                clientlessGuardedWrites.add(h.endpoint());
            }
        }

        assertThat(clientless.size())
            .as("Backend endpoints no frontend client calls went UP. Either wire the new endpoint "
                + "from a screen, or lower/raise this baseline deliberately and say why — an "
                + "endpoint with no caller is capability the buyer paid for and cannot reach.")
            .isLessThanOrEqualTo(BASELINE_CLIENTLESS_ENDPOINTS);
        assertThat(clientlessGuardedWrites.size())
            .as("Guarded WRITE endpoints with no client went up. These are the sharp ones: a "
                + "permission or role is spent guarding a door with no handle on the outside.")
            .isLessThanOrEqualTo(BASELINE_CLIENTLESS_GUARDED_WRITES);

        // Both directions, so the figure cannot rot into a blanket suppression the way a ceiling
        // does when the population it measures quietly shrinks (F2-61(c)).
        assertThat(clientless.size())
            .as("Fewer clientless endpoints than the baseline — progress, not a failure. Lower "
                + "BASELINE_CLIENTLESS_ENDPOINTS to %d and BASELINE_CLIENTLESS_GUARDED_WRITES to %d.",
                clientless.size(), clientlessGuardedWrites.size())
            .isEqualTo(BASELINE_CLIENTLESS_ENDPOINTS);
    }

    // ── mechanics ──────────────────────────────────────────────────────────────────────────────

    private static final Pattern PATH_CONST = Pattern.compile(
        "(?:const|let)\\s+([A-Za-z_$][\\w$]*)\\s*(?::\\s*string\\s*)?=\\s*[`'\"]([^`'\"]*/api/[^`'\"]*)[`'\"]");
    private static final Pattern API_CALL = Pattern.compile(
        "\\bapi\\.(get|post|put|patch|del|delete)\\s*(?:<[^()]*?>)?\\s*\\(\\s*"
            + "([`'\"][^`'\"]*[`'\"]|[A-Za-z_$][\\w$]*)");
    private static final Pattern TEMPLATE_CONST = Pattern.compile("\\$\\{\\s*([A-Za-z_$][\\w$]*)\\s*\\}");
    private static final Pattern TEMPLATE_ANY = Pattern.compile("\\$\\{[^}]*\\}");

    private static String foldConstants(String body, Map<String, String> consts) {
        Matcher m = TEMPLATE_CONST.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = consts.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "{}" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String norm(String verb, String path) {
        String p = path.replaceAll("\\{[^}]*\\}", "{}");
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        // A trailing `{}` NOT preceded by `/` is a folded QUERY STRING, not a path segment:
        // `` `/api/v1/compliance/findings${pageQ(page, size)}` `` folds to `findings{}`. Reading it
        // as a segment invented ~60 orphans across 12 families on the first run.
        p = p.replaceAll("(?<!/)\\{\\}$", "");
        p = p.replaceAll("/{2,}", "/");
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return verb.toLowerCase(Locale.ROOT) + " " + p;
    }

    /** (verb, segment count) -> the segment arrays, so the relaxed match stays linear per lookup. */
    private static Map<String, List<String[]>> shapeIndex(Iterable<String> endpoints) {
        Map<String, List<String[]>> out = new HashMap<>();
        for (String e : endpoints) {
            String[] vp = e.split(" ", 2);
            String[] segs = vp[1].split("/", -1);
            out.computeIfAbsent(vp[0] + " " + segs.length, k -> new ArrayList<>()).add(segs);
        }
        return out;
    }

    /** Same verb, same segment count, and every segment equal or either side a {@code {}}. */
    private static boolean resolves(String endpoint, Map<String, List<String[]>> index) {
        String[] vp = endpoint.split(" ", 2);
        String[] segs = vp[1].split("/", -1);
        for (String[] cand : index.getOrDefault(vp[0] + " " + segs.length, List.of())) {
            boolean all = true;
            for (int i = 0; i < segs.length && all; i++) {
                all = segs[i].equals(cand[i]) || "{}".equals(segs[i]) || "{}".equals(cand[i]);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> frontendClientFiles() {
        try (Stream<Path> s = Files.walk(FE_LIB)) {
            return s.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".ts"))
                .filter(p -> !p.toString().endsWith(".d.ts"))
                .filter(p -> !p.getFileName().toString().contains(".test."))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    /**
     * Line and block comments out; string and template bodies preserved.
     *
     * <p>Bounded to the same limitation the frontend's own {@code stripComments} documents: a regex
     * literal containing a quote can desynchronise the string state. That is why this degrades
     * toward keeping text rather than dropping it — an over-kept comment can only ADD a candidate
     * endpoint, and a fabricated candidate fails loudly here instead of hiding a real one.
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'' || c == '`') {
                char q = c;
                out.append(c);
                i++;
                while (i < n && src.charAt(i) != q) {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        out.append(src.charAt(i++));
                    }
                    out.append(src.charAt(i++));
                }
                if (i < n) {
                    out.append(src.charAt(i++));
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
