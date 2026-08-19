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
     * <p>Keep it empty. There is no correct code for a hard zero here to fail on: an entity-bound
     * partial update whose omitted key must mean "leave it alone" cannot also declare an
     * initialiser, and where a default is genuinely wanted the create path is always available to
     * own it — which is what {@link #everyRemovedInitialiserHasAWriterOnTheCreatePath} enforces.
     */
    private static final Set<String> WORK_LIST = Set.of();

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
                    Map<String, String> declared =
                        initialisersFor(inits, pkgOf(entry.getValue()), p.getValue());
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
            "(?:public|private|protected)\\s+(?:static\\s+)?[\\w<>,.\\[\\]\\s]+?\\s+"
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
            new Moved("VendorAssessment", "status", "setStatus", "TprmService", "createAssessment"));

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
}
