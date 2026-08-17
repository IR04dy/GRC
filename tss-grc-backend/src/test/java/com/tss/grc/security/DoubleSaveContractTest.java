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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-227 — <b>the ROWVERSION double save, which nothing has ever measured.</b>
 *
 * <p>CLAUDE.md has carried this landmine since before F2-175: <i>"never save an entity twice
 * around a native {@code @Modifying} on the same table"</i>, widened by F2-175 to <i>"ANY flush
 * between the two saves does it"</i>. F2-175 fixed one instance. F2-226 then tripped over a LIVE
 * one by accident while planting a probe fixture. Nobody had measured the population, and the
 * reason is recorded in CLAUDE.md itself: <b>"No unit test can see this. Mocked repositories have
 * no {@code row_version}, so the suite was green over it."</b> That is an argument for a
 * STRUCTURAL gate, not for no gate.
 *
 * <h3>Why the shape is fatal here</h3>
 * {@code BaseEntity.rowVersion} is
 * {@code @Version @Column(name = "row_version", insertable = false, updatable = false) byte[]} —
 * a SQL Server ROWVERSION the <b>database</b> generates and Hibernate never re-reads. So the
 * instant a flush reaches the database, the in-memory version is stale, and the next
 * {@code UPDATE … WHERE row_version = ?} matches zero rows →
 * {@code ObjectOptimisticLockingFailureException}.
 *
 * <h3>⚠ The discriminator, corrected — and this is the part a scan gets wrong</h3>
 * A first derivation treated any intervening repository write as the flusher and reported 18
 * candidates. That is wrong in the direction that manufactures findings, and the tell was
 * {@code PolicyManagementService#update}: it has the shape and F2-222 probed it <b>13/13
 * working</b>. Reading {@code AuditLogService.log} settled it — it is {@code @Transactional}
 * (joining the caller's transaction) and saves an IDENTITY-generated row, so Hibernate executes
 * that one insert immediately to obtain the id <b>without flushing the caller's other pending
 * changes</b>.
 *
 * <p>🔑 <b>So {@code audit.log} and {@code emit} are not flushers, and a QUERY is.</b>
 * Flush-before-query sends everything pending. That is the whole difference between the 21 latent
 * sites and the live one, and it is why this platform works despite carrying the shape in 39
 * places.
 *
 * <h3>What this gate does</h3>
 * It bans the one shape that is unambiguous and was actually live: <b>a method that saves a row
 * and then calls a sibling which re-loads that same repository's row and saves it again.</b> That
 * cannot be anything but two writes to one row in one transaction. The same-method variant and the
 * "sibling re-saves a DIFFERENT row" variant are measured and filed rather than asserted, because
 * the scan cannot tell them apart — {@code ApprovalChainTemplateService#clearOtherDefaults} queries
 * {@code …AndIdNot(}, so it saves other rows on purpose, and a gate that failed on that would be a
 * gate failing on correct code (F2-214).
 */
class DoubleSaveContractTest {

    private static final Path MAIN = Paths.get("src/main/java/com/tss/grc");

    /** F2-198/F2-211: ONE left-to-right alternation, never two sequential passes. */
    private static final Pattern COMMENTS = Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static final Pattern METHOD = Pattern.compile(
        "\\n    (?:@[\\w.]+(?:\\([^)]*\\))?\\s*\\n    )*"
        + "(?:public|private|protected)\\s+[\\w<>,\\[\\]\\s\\.\\?]+?\\s(\\w+)\\s*\\(([^)]*)\\)\\s*\\{");

    private static final Pattern SAVE = Pattern.compile("\\b(\\w+)\\s*\\.\\s*save\\s*\\(\\s*([\\w.]+)");

    /** A call on {@code this} — a bare identifier followed by an open paren. */
    private static final Pattern SIBLING = Pattern.compile("(?<![.\\w])(\\w+)\\s*\\(");

    /**
     * A repository READ, i.e. flush-before-query. Deliberately excludes {@code save}: an
     * IDENTITY insert goes out alone and does not flush the caller's pending changes, which is
     * the correction that took the population from "18 live" to one.
     */
    private static final Pattern QUERY = Pattern.compile(
        "\\.\\s*(?:find\\w*|count\\w*|exists\\w*|search\\w*|stream\\w*)\\s*\\("
        + "|entityManager\\s*\\.\\s*(?:flush|createQuery)|\\.\\s*flush\\s*\\(");

    /**
     * Sibling calls that re-load and re-save the SAME repository's row, but were READ and are
     * correct. Enforced in both directions: an entry that stops matching must be deleted.
     *
     * <p>Each of these queries with an explicit "not this id" predicate, so the row it saves is
     * provably not the row the caller just saved.
     */
    private static final Map<String, String> BY_DESIGN = new LinkedHashMap<>();

    static {
        BY_DESIGN.put("ApprovalChainTemplateService#clearOtherDefaults",
            "REFUTED BY READING — the query is "
            + "findByOrganisationIdAndAppliesToModuleAndIsDefaultTrueAndIsDeletedFalseAndIdNot(...), "
            + "so it selects every OTHER default template and clears those. The row the caller "
            + "just saved is excluded by the id predicate in the finder's own name, which is the "
            + "only reason this is decidable without running it. Three callers (create, update, "
            + "setDefault) all go through it.");
    }

    private static String strip(String src) {
        return COMMENTS.matcher(src).replaceAll("");
    }

    private static List<String[]> methodsOf(String src) {
        List<String[]> out = new ArrayList<>();
        List<int[]> marks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher m = METHOD.matcher(src);
        while (m.find()) {
            marks.add(new int[]{m.start(), m.end()});
            names.add(m.group(1));
        }
        for (int i = 0; i < marks.size(); i++) {
            int end = i + 1 < marks.size() ? marks.get(i + 1)[0] : src.length();
            out.add(new String[]{names.get(i), src.substring(marks.get(i)[1], end)});
        }
        return out;
    }

    /** {@code Class#caller -> callee} for every cross-method double save of one row. */
    private static Set<String> crossMethodDoubleSaves() {
        Set<String> hits = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java"))
                              .filter(f -> f.toString().contains("service"))
                              .toList()) {
                String cls = p.getFileName().toString().replace(".java", "");
                String src = strip(Files.readString(p, StandardCharsets.UTF_8));
                List<String[]> ms = methodsOf(src);
                Map<String, String> byName = new LinkedHashMap<>();
                for (String[] nm : ms) byName.putIfAbsent(nm[0], nm[1]);

                for (String[] nm : ms) {
                    String name = nm[0];
                    String body = nm[1];
                    Set<String> outerRepos = new TreeSet<>();
                    int firstSaveEnd = -1;
                    Matcher sm = SAVE.matcher(body);
                    while (sm.find()) {
                        outerRepos.add(sm.group(1));
                        if (firstSaveEnd < 0) firstSaveEnd = sm.end();
                    }
                    if (firstSaveEnd < 0) continue;

                    Matcher cm = SIBLING.matcher(body.substring(firstSaveEnd));
                    while (cm.find()) {
                        String callee = cm.group(1);
                        if (callee.equals(name) || !byName.containsKey(callee)) continue;
                        String cbody = byName.get(callee);
                        if (!QUERY.matcher(cbody).find()) continue;   // no flush → not this shape
                        Matcher im = SAVE.matcher(cbody);
                        boolean sharesRepo = false;
                        while (im.find()) {
                            if (outerRepos.contains(im.group(1))) { sharesRepo = true; break; }
                        }
                        if (sharesRepo) hits.add(cls + "#" + callee);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    /**
     * The six sites this slice measured and did <b>not</b> read. A decrease-only WORK LIST, and
     * deliberately not reasons.
     *
     * <p>F2-188 measured what happens when a slice writes a reason for something it has not read:
     * five claims, five false, and the fluency was what made them look verified. So these carry no
     * verdict. Each needs the same question asked by reading: <b>does the sibling save the row the
     * caller just saved, or a different one?</b> {@code ApprovalChainTemplateService} shows both
     * answers are real — its finder ends {@code …AndIdNot(}, so it provably saves other rows.
     *
     * <p>⚠ A hard zero here would be a gate failing on code this slice never examined, which is
     * the one failure a gate must not have (F2-214). The figure may only decrease, and the entry
     * for the site F2-227 fixed ({@code ExerciseRuntimeService#dispatchInject}) is already gone —
     * which is what makes the decrease honest rather than a re-scoping.
     */
    private static final Set<String> WORK_LIST = Set.of(
        "AttackSurfaceAssetService#recomputeRiskScore",
        "FourthPartyExposureService#recomputeConcentration",
        "ObService#recountTpp",
        "PolicyManagementService#supersedePriorPublished",
        "WorkflowRequestService#advanceRouting",
        "WorkflowRequestService#notifyStep"
    );

    /**
     * A method may not save a row and then hand it to a sibling that re-loads and saves it — the
     * shape that answered HTTP 500 on {@code POST /api/v1/bcm/exercise-runs/{id}/custom-inject},
     * whose {@code dispatchNow} DEFAULTS TO TRUE.
     */
    @Test
    @DisplayName("no NEW site saves a row and then has a sibling re-load and save it again")
    void noRowIsWrittenTwiceInOneTransactionViaASibling() {
        Set<String> open = new TreeSet<>(crossMethodDoubleSaves());
        open.removeAll(BY_DESIGN.keySet());
        open.removeAll(WORK_LIST);
        assertThat(open)
            .as("each of these re-loads and re-saves a row its caller already saved, in one "
                + "transaction. BaseEntity.rowVersion is a database-generated SQL Server "
                + "ROWVERSION that Hibernate never re-reads, so the query's flush-before-query "
                + "leaves the in-memory version stale and the second UPDATE matches zero rows. "
                + "Do the other work FIRST and save ONCE (F2-175); if the sibling genuinely "
                + "saves a DIFFERENT row, say so in BY_DESIGN and name the predicate that "
                + "excludes the caller's id")
            .isEmpty();
    }

    /** The work list may only shrink, and an entry that is fixed must lose its place. */
    @Test
    @DisplayName("the unadjudicated work list only ever decreases")
    void theWorkListOnlyDecreases() {
        Set<String> found = crossMethodDoubleSaves();
        Set<String> stillOpen = new TreeSet<>(WORK_LIST);
        stillOpen.retainAll(found);
        assertThat(stillOpen.size())
            .as("%d of the work list's %d sites still show the shape. This figure may only go "
                + "down: read the site, then either fix it or move it to BY_DESIGN with the "
                + "predicate that proves it saves a different row",
                stillOpen.size(), WORK_LIST.size())
            .isLessThanOrEqualTo(WORK_LIST.size());

        Set<String> stale = new TreeSet<>(WORK_LIST);
        stale.removeAll(found);
        assertThat(stale)
            .as("these are on the work list but the scan no longer reports them — if they were "
                + "fixed, delete the entries in the same commit; if the detector stopped seeing "
                + "the shape, this gate has gone quiet, which is worse")
            .isEmpty();
    }

    /** The reverse direction: an exemption whose site is gone must lose its entry (F2-218). */
    @Test
    @DisplayName("every BY_DESIGN entry still names a site the scan finds")
    void anExemptionThatNoLongerAppliesMustBeDeleted() {
        Set<String> found = crossMethodDoubleSaves();
        Set<String> stale = new TreeSet<>(BY_DESIGN.keySet());
        stale.removeAll(found);
        assertThat(stale)
            .as("these are exempted but the scan no longer reports them, so either the code was "
                + "fixed — delete the entry in the same commit — or the detector stopped seeing "
                + "the shape, which is worse and means this whole gate has gone quiet")
            .isEmpty();
    }

    /**
     * ⚠ Anti-vacuity. A hard zero over a population the detector can no longer see is the most
     * reassuring possible artefact (F2-205), and the failure mode here is silent: a regex that
     * stops matching reports an empty population, which is indistinguishable from a clean tree.
     * So the detector is exercised against the exact pre-fix and post-fix shapes.
     */
    @Test
    @DisplayName("the detector fires on the pre-fix shape and not on the fixed one")
    void theDetectorIsNotVacuous() {
        String prefix = """
            class Probe {
                public View addCustomInject(Long runId, Req req) {
                    Inject ri = new Inject();
                    Inject saved = runInjectRepo.save(ri);
                    audit.log("CREATE", "Inject", saved.getId(), "added");
                    if (dispatchNow) {
                        return dispatchInject(runId, saved.getId());
                    }
                    return toView(saved);
                }
                public View dispatchInject(Long runId, Long injectId) {
                    Inject inj = runInjectRepo.findByIdAndOrganisationId(injectId, orgId)
                        .orElseThrow();
                    inj.setStatus("DISPATCHED");
                    Inject saved = runInjectRepo.save(inj);
                    return toView(saved);
                }
            }
            """;
        assertThat(siblingDoubleSavesIn(strip(prefix)))
            .as("the exact shape that answered 500 must be detected")
            .contains("dispatchInject");

        String fixed = """
            class Probe {
                public View addCustomInject(Long runId, Req req) {
                    Inject ri = new Inject();
                    if (dispatchNow) {
                        stampDispatched(ri);
                    }
                    Inject saved = runInjectRepo.save(ri);
                    audit.log("CREATE", "Inject", saved.getId(), "added");
                    announceDispatched(orgId, run, saved);
                    return toView(saved);
                }
                public View dispatchInject(Long runId, Long injectId) {
                    Inject inj = runInjectRepo.findByIdAndOrganisationId(injectId, orgId)
                        .orElseThrow();
                    inj.setStatus("DISPATCHED");
                    Inject saved = runInjectRepo.save(inj);
                    announceDispatched(orgId, run, saved);
                    return toView(saved);
                }
            }
            """;
        assertThat(siblingDoubleSavesIn(strip(fixed)))
            .as("the fixed shape must NOT be detected — addCustomInject no longer calls "
                + "dispatchInject, it stamps the row before its single save")
            .isEmpty();

        // ⚠ And the corrected discriminator itself: `audit.log` must NOT count as the flusher,
        // or PolicyManagementService#update (probed 13/13 working by F2-222) reads as a defect.
        String auditOnly = """
            class Probe {
                public Dto update(Long id, Dto body) {
                    Doc p = docs.findById(id).orElseThrow();
                    docs.save(p);
                    audit.log("UPDATE", "Doc", p.getId(), "edited");
                    p.setTitle(body.getTitle());
                    docs.save(p);
                    return Dto.from(p);
                }
            }
            """;
        assertThat(siblingDoubleSavesIn(strip(auditOnly)))
            .as("audit.log saves an IDENTITY-generated row, which goes out alone without "
                + "flushing the caller's pending changes. Treating it as a flusher is how a "
                + "first draft reported 18 live sites where there was one")
            .isEmpty();
    }

    /** The cross-method detector, over one source string — used by the self-test above. */
    private static Set<String> siblingDoubleSavesIn(String src) {
        Set<String> hits = new TreeSet<>();
        List<String[]> ms = methodsOf(src);
        Map<String, String> byName = new LinkedHashMap<>();
        for (String[] nm : ms) byName.putIfAbsent(nm[0], nm[1]);
        for (String[] nm : ms) {
            String name = nm[0];
            String body = nm[1];
            Set<String> outerRepos = new TreeSet<>();
            int firstSaveEnd = -1;
            Matcher sm = SAVE.matcher(body);
            while (sm.find()) {
                outerRepos.add(sm.group(1));
                if (firstSaveEnd < 0) firstSaveEnd = sm.end();
            }
            if (firstSaveEnd < 0) continue;
            Matcher cm = SIBLING.matcher(body.substring(firstSaveEnd));
            while (cm.find()) {
                String callee = cm.group(1);
                if (callee.equals(name) || !byName.containsKey(callee)) continue;
                String cbody = byName.get(callee);
                if (!QUERY.matcher(cbody).find()) continue;
                Matcher im = SAVE.matcher(cbody);
                while (im.find()) {
                    if (outerRepos.contains(im.group(1))) { hits.add(callee); break; }
                }
            }
        }
        return hits;
    }

    /**
     * The fix must stay a single write, and the two fields must have one writer.
     *
     * <p>Structural because no mock can reproduce the failure — the whole reason this family went
     * unmeasured. {@code ExerciseRuntimeServiceTest} asserts the behavioural half
     * ({@code times(1)} on save, {@code never()} on the re-read).
     */
    @Test
    @DisplayName("the exercise custom-inject stamps the dispatch before its one save")
    void theCustomInjectPathWritesTheRowOnce() {
        String src = strip(read("module/bcm/cycle/runtime/service/ExerciseRuntimeService.java"));

        assertThat(src)
            .as("addCustomInject must not hand its freshly-saved row to dispatchInject — that "
                + "re-loads and re-saves it, which is the 500")
            .doesNotContain("return dispatchInject(run.getId(), saved.getId())");

        assertThat(src)
            .as("the dispatch stamp is applied in memory before the single save")
            .contains("stampDispatched(ri)")
            .contains("ExerciseRunInject saved = runInjectRepo.save(ri)");

        assertThat(src)
            .as("both entry points share ONE announcement rather than duplicating it — F2-188 "
                + "measured what a duplicated transition does to an audit trail")
            .containsPattern("private void announceDispatched\\(")
            .containsPattern("announceDispatched\\(orgId, run, saved\\)");

        int stampWriters = src.split("setActualDispatchedAt\\(", -1).length - 1;
        assertThat(stampWriters)
            .as("setActualDispatchedAt must have exactly one writer — stampDispatched. Two "
                + "writers is how the two paths drift (F2-189/F2-191)")
            .isEqualTo(1);
    }

    /**
     * F2-227 — the handler that used to swallow this exception class.
     *
     * <p>{@code OptimisticLockingFailureException} is a {@code DataAccessException}, so every one
     * of them fell into {@code handleMissingDbObject} and came back as a bare <i>"An unexpected
     * database error occurred."</i> with no exception class — <b>even in dev with
     * {@code grc.errors.verbose=true}</b>, because that handler built its own ProblemDetail
     * instead of delegating to {@code handleGeneral}, which is where the verbose diagnostics live.
     * Its comment said <i>"let the catch-all handle it"</i>. It did not.
     *
     * <p>🔑 The handler intercepting the exception class this landmine produces was the one
     * handler that discarded the diagnostic verbose mode exists to provide. That is why a
     * thrice-recorded landmine had never been diagnosed from a response.
     */
    @Test
    @DisplayName("a stale row is a 409 that names the entity, not an anonymous 500")
    void anOptimisticLockFailureIsDiagnosable() {
        String src = strip(read("common/exception/GlobalExceptionHandler.java"));

        assertThat(src)
            .as("there must be a handler for the exception class the ROWVERSION landmine throws")
            .contains("@ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)")
            .contains("HttpStatus.CONFLICT");

        assertThat(src)
            .as("and it must name the entity, because 'retry?' is not a diagnosis — "
                + "ObjectOptimisticLockingFailureException carries the type and identifier")
            .contains("getPersistentClassName()")
            .contains("o.getIdentifier()");

        assertThat(src)
            .as("the fall-through must delegate to handleGeneral so SEC-CFG-001's verbose "
                + "diagnostics actually reach a developer, which is what its comment always "
                + "claimed and never did")
            .contains("return handleGeneral(ex);")
            .doesNotContain("\"An unexpected database error occurred.\"");
    }

    private static String read(String relative) {
        try {
            return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
