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
        "AuditLifecycleService",        // F2-217
        "NdiEvidenceItemService",       // F2-218 — the SoD bypass above
        "NdiImprovementPlanService",    // F2-218
        "NdiAssessmentCycleService");   // F2-218

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
     */
    private static final int BASELINE_CONTRADICTED_SERVICES = 8;

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

    private record Method(String name, String params, String body) { }

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

    private static boolean readsRequest(String value, Set<String> requestLocals) {
        return requestLocals.stream().anyMatch(
            r -> value.matches(".*\\b" + Pattern.quote(r) + "\\s*\\.\\s*(get|is)\\w+\\s*\\(.*"));
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
        return paramNames.stream().anyMatch(p -> value.matches(".*\\b" + Pattern.quote(p) + "\\b.*"));
    }

    /**
     * Fields this method assigns a SERVER-derived value: a literal, a clock reading, the
     * authenticated principal, or a constant. The value is what decides, never the guard around it.
     */
    static Set<String> stampsServerValue(String body, Map<String, String> types,
                                         Set<String> requestLocals, Set<String> paramNames) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            String value = m.group(3).trim();
            if (value.isEmpty() || value.equals("null")) continue;
            if (callerSupplied(value, requestLocals, paramNames)) continue;
            String type = types.get(m.group(1));
            if (type != null) out.add(key(type, lower(m.group(2))));
        }
        return out;
    }

    /** Fields this method writes FROM the request object, on any target. */
    static Set<String> writesFromRequest(String body, Map<String, String> types,
                                         Set<String> requestLocals) {
        Set<String> out = new TreeSet<>();
        Matcher m = SETTER.matcher(body);
        while (m.find()) {
            if (!readsRequest(m.group(3).trim(), requestLocals)) continue;
            String type = types.get(m.group(1));
            if (type != null) out.add(key(type, lower(m.group(2))));
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
     */
    private static boolean isGuardedRoute(String name, String body) {
        if (isWritePath(name)) return false;
        boolean loads = body.matches("(?s).*\\b(get|find\\w*)\\s*\\(.*");
        boolean refuses = body.matches("(?s).*\\bthrow new (IllegalState|IllegalTransition)\\w*Exception.*");
        return loads && refuses;
    }

    private record Contradiction(String service, String writeMethod, Set<String> fields,
                                 Set<String> stampedBy) { }

    /** {@code Type#field} → the guarded routes that stamp it. */
    static Map<String, Set<String>> stampedBy(List<Method> methods) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Method m : methods) {
            if (!isGuardedRoute(m.name(), m.body())) continue;
            Map<String, String> types = localTypes(m.params(), m.body());
            Set<String> params = allParamNames(m.params());
            for (String f : stampsServerValue(m.body(), types, requestParams(m.params()), params)) {
                if (CLOSED_AT_THE_WIRE.contains(f.substring(f.indexOf('#') + 1))) continue;
                out.computeIfAbsent(f, k -> new TreeSet<>()).add(m.name());
            }
        }
        return out;
    }

    /** The {@code Type#field}s this write path takes from the caller AND the service stamps. */
    static Set<String> acceptedAndStamped(Method m, Map<String, Set<String>> stampedBy) {
        if (!isWritePath(m.name())) return Set.of();
        Map<String, String> paramTypes = requestParamTypes(m.params());
        if (paramTypes.isEmpty()) return Set.of();
        Map<String, String> types = localTypes(m.params(), m.body());
        Set<String> req = paramTypes.keySet();

        Set<String> accepted = new TreeSet<>(writesFromRequest(m.body(), types, req));
        // Persisting the request object accepts every field of THAT TYPE it did not first
        // overwrite or clear — scoped to the persisted parameter's own type, never the service's.
        for (var e : paramTypes.entrySet()) {
            if (!m.body().matches("(?s).*\\.save\\s*\\(\\s*" + Pattern.quote(e.getKey()) + "\\s*\\).*")) {
                continue;
            }
            Set<String> settled = new TreeSet<>(
                stampsServerValue(m.body(), types, req, allParamNames(m.params())));
            settled.addAll(clears(m.body(), types));
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
        for (Path p : serviceSources()) {
            String cls = p.getFileName().toString().replace(".java", "");
            String src = stripComments(read(p));
            List<Method> methods = publicMethods(src);

            Map<String, Set<String>> stampedBy = stampedBy(methods);
            if (stampedBy.isEmpty()) continue;

            for (Method m : methods) {
                Set<String> hit = acceptedAndStamped(m, stampedBy);
                if (!hit.isEmpty()) {
                    Set<String> routes = new TreeSet<>();
                    hit.forEach(f -> routes.addAll(stampedBy.get(f)));
                    out.add(new Contradiction(cls, m.name(), hit, routes));
                }
            }
        }
        return out;
    }

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    void aClosedServiceAcceptsNothingItsOwnGuardedRoutesStamp() {
        Map<String, List<Contradiction>> byService = measure().stream()
            .filter(c -> CLOSED.contains(c.service()))
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
        Set<String> services = all.stream().map(Contradiction::service)
            .filter(s -> !CLOSED.contains(s)).collect(Collectors.toCollection(TreeSet::new));

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

        // Anti-vacuity. A rotted SETTER pattern or a stricter isGuardedRoute reports a SMALLER
        // population, which reads exactly like progress — and the corpus assertions pass either
        // way. The floor is deliberately shallow (a real slice closes one or two services and must
        // not have to touch this), because the load-bearing rot protection is the pair of positive
        // controls below: they drive the shipped predicate over literal source, so a detector that
        // cannot match fails there rather than quietly reporting a clean tree.
        assertThat(services.size())
            .as("the scan found almost nothing — check the detectors before believing the fix")
            .isGreaterThanOrEqualTo(BASELINE_CONTRADICTED_SERVICES - 3);
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
            .as("...and crucially it does NOT read as a caller write, which is why the create-side "
                + "check must be 'did anything overwrite it before the save', not 'is a setter present'")
            .doesNotContain("Foo#status");

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
    }

    /**
     * Runs the SHIPPED predicate over a source fragment — {@link #stampedBy} and
     * {@link #acceptedAndStamped}, the same two calls {@link #measure} makes. A control that
     * reimplements the logic it is controlling proves only that the copy agrees with itself.
     */
    private static Set<String> contradictionsIn(String src) {
        List<Method> methods = publicMethods(src);
        Map<String, Set<String>> stamped = stampedBy(methods);
        Set<String> hits = new TreeSet<>();
        for (Method m : methods) hits.addAll(acceptedAndStamped(m, stamped));
        return hits;
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
}
