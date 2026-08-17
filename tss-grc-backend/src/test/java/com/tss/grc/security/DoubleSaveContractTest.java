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
     *
     * <p>⚠ F2-228 corrected this twice more, in OPPOSITE directions — the combination is why
     * F2-227's green build measured the wrong population.
     *
     * <p><b>It over-matched.</b> The receiver was unconstrained, so {@code all.stream()},
     * {@code gates.count()} and {@code x.findFirst()} — Java Stream API calls on an in-memory
     * List — read as database queries. That is the whole reason
     * {@code WorkflowRequestService#advanceRouting} was on the work list: it touches no
     * repository at all. The receiver must be a repository field the class DECLARES, resolved
     * from source. ⚠ Keying that on the field NAME is also wrong — {@code
     * PolicyManagementService} calls its repository {@code docs}, so a {@code *repo*} rule
     * cannot see it (F2-197: a rule keyed on a name cannot see the local alias).
     *
     * <p><b>It under-matched.</b> {@code findById(} is NOT a flusher: {@code EntityManager#find}
     * raises a LoadEvent, not an AutoFlushEvent, and returns the managed instance straight from
     * the persistence context. A DERIVED query method ({@code findByIdAndOrganisationId})
     * compiles to JPQL and does auto-flush, which is exactly what made F2-227's site fire. And
     * the flush may be TRANSITIVE — one level deeper, inside a helper the callee calls.
     *
     * <p>🔑 The two errors cancelled: the loose {@code find\\w*} matched
     * {@code assetRepo.findById(} and so put {@code recomputeRiskScore} in the population for a
     * reason that is not true, while the real flush sat in {@code pickHighestOpenSeverity}.
     * Fixing only the over-match would have DELETED a site from the population; fixing only the
     * under-match would have kept a phantom. Both land here together (F2-224).
     */
    private static final Pattern REPO_FIELD = Pattern.compile(
        "(?:private|protected)\\s+final\\s+\\w*Repository(?:<[^>]*>)?\\s+(\\w+)\\s*[;=]");

    private static final Pattern QUERY_VERB = Pattern.compile(
        "(?:find(?!ById\\s*\\()\\w*|count\\w*|exists\\w*|search\\w*|get[A-Z]\\w*|query\\w*"
        + "|saveAndFlush)\\s*\\(");

    private static final Pattern EM_FLUSH = Pattern.compile(
        "entityManager\\s*\\.\\s*(?:flush|createQuery|createNativeQuery)"
        + "|\\bem\\s*\\.\\s*(?:flush|createQuery)");

    /** Does {@code body} flush, judged against the repository fields the class declares? */
    private static boolean flushesVia(String body, Set<String> repoFields) {
        if (EM_FLUSH.matcher(body).find()) return true;
        for (String f : repoFields) {
            Matcher m = Pattern.compile("\\b" + Pattern.quote(f) + "\\s*\\.\\s*").matcher(body);
            while (m.find()) {
                if (QUERY_VERB.matcher(body).region(m.end(), body.length()).lookingAt()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A local of the form {@code T x = new T()} — the INSERT discriminator's input. */
    private static final Pattern NEW_LOCAL =
        Pattern.compile("\\b(\\w+)\\s+(\\w+)\\s*=\\s*new\\s+\\1\\s*\\(");

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

    /** Every {@code .java} under the working tree's main sources. */
    private static List<Path> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> repoFieldsOf(String src) {
        Set<String> out = new TreeSet<>();
        Matcher m = REPO_FIELD.matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /**
     * {@code Class#callee} for every cross-method re-save of a row the caller already saved.
     *
     * <p>⚠ F2-228 measured this population LIVE and it is <b>harmless</b>: a row that was LOADED
     * carries a real {@code row_version}, and two UPDATEs of it in one transaction both succeed —
     * probed twice each on the ASM asset update and the Open Banking TPP revocation, 200 every
     * time. So this is a CENSUS, not a defect gate. See {@link #insertThenResave()} for the shape
     * that is actually fatal, and {@code LOADED_ROW_RESAVES} for why the difference matters.
     */
    private static Set<String> crossMethodDoubleSaves() {
        Set<String> hits = new TreeSet<>();
        try {
            for (Path p : sources()) {
                String cls = p.getFileName().toString().replace(".java", "");
                String src = strip(Files.readString(p, StandardCharsets.UTF_8));
                Set<String> repoFields = repoFieldsOf(src);
                if (repoFields.isEmpty()) continue;
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
                        if (!repoFields.contains(sm.group(1))) continue;
                        outerRepos.add(sm.group(1));
                        if (firstSaveEnd < 0) firstSaveEnd = sm.end();
                    }
                    if (firstSaveEnd < 0) continue;

                    Matcher cm = SIBLING.matcher(body.substring(firstSaveEnd));
                    while (cm.find()) {
                        String callee = cm.group(1);
                        if (callee.equals(name) || !byName.containsKey(callee)) continue;
                        String cbody = byName.get(callee);
                        boolean flushes = flushesVia(cbody, repoFields);
                        if (!flushes) {                       // transitive: one level deeper
                            Matcher deep = SIBLING.matcher(cbody);
                            while (deep.find() && !flushes) {
                                String inner = deep.group(1);
                                if (inner.equals(callee) || !byName.containsKey(inner)) continue;
                                flushes = flushesVia(byName.get(inner), repoFields);
                            }
                        }
                        if (!flushes) continue;
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

    /** Entities that inherit {@code @Version rowVersion} — only these can fail this way. */
    private static Set<String> versionedEntities() {
        Set<String> out = new TreeSet<>();
        try {
            for (Path p : sources()) {
                Matcher m = Pattern.compile("class\\s+(\\w+)\\s+extends\\s+BaseEntity\\b")
                    .matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) out.add(m.group(1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /**
     * <b>THE FATAL SHAPE</b> — a freshly-constructed versioned entity is saved, and that same row
     * is saved again in the one transaction.
     *
     * <p>F2-228's live probe forced this discriminator. Two sites the reading had "confirmed" both
     * answered HTTP 200 twice; F2-227's site differed in exactly one way — its first save was of a
     * {@code new} entity. Because {@code @GeneratedValue(IDENTITY)} makes Hibernate execute that
     * INSERT immediately to obtain the id, and {@code row_version} is
     * {@code insertable = false, updatable = false}, the in-memory version is <b>null</b>
     * afterwards, so the follow-up {@code UPDATE ... WHERE row_version = null} matches zero rows.
     * That is precisely what Hibernate's own hint — <i>"or unsaved-value mapping was
     * incorrect"</i> — has been saying all along.
     *
     * <p>🔑 <b>For an INSERT the first save IS the flush</b>, so no intervening query is needed and
     * the flush-between requirement that F2-227's gate was built on does not apply here.
     *
     * <p>Restricted to {@code BaseEntity} subclasses because only they carry {@code @Version} at
     * all — 172 of the 432 entities. {@code FourthPartyExposure} is one of the 260 that do not,
     * which is why its create was never at risk however many times it saved the row.
     */
    private static Set<String> insertThenResave() {
        Set<String> versioned = versionedEntities();
        Set<String> hits = new TreeSet<>();
        try {
            for (Path p : sources()) {
                String cls = p.getFileName().toString().replace(".java", "");
                String src = strip(Files.readString(p, StandardCharsets.UTF_8));
                Set<String> repoFields = repoFieldsOf(src);
                if (repoFields.isEmpty()) continue;
                List<String[]> ms = methodsOf(src);
                Map<String, String> byName = new LinkedHashMap<>();
                for (String[] nm : ms) byName.putIfAbsent(nm[0], nm[1]);

                for (String[] nm : ms) {
                    hits.addAll(insertThenResaveIn(cls, nm[0], nm[1], byName, repoFields,
                                                   versioned));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    /** One method's worth of the fatal shape — shared with the self-test below. */
    private static Set<String> insertThenResaveIn(String cls, String name, String body,
                                                  Map<String, String> byName,
                                                  Set<String> repoFields,
                                                  Set<String> versioned) {
        Set<String> hits = new TreeSet<>();
        Map<String, String> fresh = new LinkedHashMap<>();
        Matcher nl = NEW_LOCAL.matcher(body);
        while (nl.find()) {
            if (versioned.contains(nl.group(1))) fresh.put(nl.group(2), nl.group(1));
        }
        for (Map.Entry<String, String> e : fresh.entrySet()) {
            Matcher sv = Pattern.compile(
                "\\b(\\w+)\\s*\\.\\s*save\\s*\\(\\s*" + Pattern.quote(e.getKey()) + "\\s*\\)")
                .matcher(body);
            if (!sv.find()) continue;
            String repo = sv.group(1);
            if (!repoFields.contains(repo)) continue;
            String after = body.substring(sv.end());
            Pattern againOnThisRepo =
                Pattern.compile("\\b" + Pattern.quote(repo) + "\\s*\\.\\s*save\\s*\\(");

            boolean second = againOnThisRepo.matcher(after).find();      // same method
            if (!second) {                                              // or via a sibling
                Matcher cm = SIBLING.matcher(after);
                while (cm.find() && !second) {
                    String callee = cm.group(1);
                    if (callee.equals(name) || !byName.containsKey(callee)) continue;
                    String cb = byName.get(callee);
                    second = againOnThisRepo.matcher(cb).find() && flushesVia(cb, repoFields);
                }
            }
            if (second) hits.add(cls + "#" + name + " :: " + e.getValue());
        }
        return hits;
    }

    /**
     * F2-227's six-entry WORK LIST, <b>adjudicated</b>. All six are refuted — 2 by live probe, 1
     * because its entity carries no {@code @Version} at all, 3 by reading — so this is no longer
     * a work list but a CENSUS of the harmless population, asserted exactly.
     *
     * <p>🔑 <b>F2-204's question, and the answer was yes: the gate's QUESTION was wrong.</b> A
     * cross-method re-save of a LOADED row is not a defect, so a hard zero over this population
     * would be a gate failing on correct code — the one failure a gate must never have (F2-214).
     * Asserting the set EXACTLY is what keeps it useful: a new member forces the next reader to
     * ask the only question that matters, <i>is the first save an INSERT?</i>
     */
    private static final Map<String, String> LOADED_ROW_RESAVES = new LinkedHashMap<>();

    static {
        LOADED_ROW_RESAVES.put("AttackSurfaceAssetService#recomputeRiskScore",
            "REFUTED BY LIVE PROBE — PUT /api/v1/asm/assets/{externalId} as so_officer, with "
            + "exposure and criticality changed so the score really does differ and the early "
            + "return is not taken: HTTP 200, twice. The row is LOADED by mustGet, so its "
            + "row_version is populated and both UPDATEs match. ⚠ Note this site is in the "
            + "population for a DIFFERENT reason than F2-227 thought: its own body only calls "
            + "assetRepo.findById, which does not flush; the real flush is one level deeper in "
            + "pickHighestOpenSeverity's derived findingRepo query.");
        LOADED_ROW_RESAVES.put("FourthPartyExposureService#recomputeConcentration",
            "REFUTED BY THE ENTITY — FourthPartyExposure does NOT extend BaseEntity, so it "
            + "carries no @Version and no row_version column at all. Two UPDATEs of it in one "
            + "transaction are simply two UPDATEs. 🔑 This is the load-bearing premise the reading "
            + "never checked: only 172 of 432 entities inherit the version, so for 260 of them "
            + "this whole family is unrepresentable. Its create was probed at HTTP 201 even when "
            + "the recompute really did re-save the just-inserted row. ⚠ Do NOT probe this site "
            + "casually — recomputeConcentration is authoritative over the whole tenant and reset "
            + "9 of 13 real seeded rows to LOW, which is filed separately as its own defect.");
        LOADED_ROW_RESAVES.put("ObService#recountTpp",
            "REFUTED BY LIVE PROBE — POST /api/v1/openbanking/tpps/{id}/transition to REVOKED as "
            + "compliance, which is the branch that queries consents and then calls recountTpp on "
            + "the same TPP row: HTTP 200. Loaded row, real version. Only 1 of recountTpp's 5 "
            + "call sites even shares a repository with its caller — the other four save a TPP "
            + "after their caller saved a consent or an incident, which are different rows.");
        LOADED_ROW_RESAVES.put("PolicyManagementService#supersedePriorPublished",
            "REFUTED BY READING — the loop body skips the caller's row explicitly: "
            + "if (other.getId().equals(published.getId())) continue. So it saves strictly OTHER "
            + "versions of the same policy code, and they are loaded by the query that runs after "
            + "the caller's flush. A second shape of the same refutation as "
            + "ApprovalChainTemplateService, where the exclusion is in the finder's NAME rather "
            + "than the loop body — neither is visible to this detector.");
        LOADED_ROW_RESAVES.put("WorkflowRequestService#notifyStep",
            "REFUTED BY READING — it re-loads every recipient of the request but saves only those "
            + "with stepNo == the NEXT step and status PENDING. The caller's row (mine) is at the "
            + "current step and was just set to RESPONDED, so it is excluded twice over. The "
            + "class already knew this rule: notifyStep carries the comment 'so the recipient row "
            + "is UPDATEd exactly once (token + NOTIFIED together — ROWVERSION rule)'.");
        LOADED_ROW_RESAVES.put("WorkflowRequestService#applyBranch",
            "REFUTED BY READING — GOTO_STEP parks only the recipients strictly between the "
            + "caller's step and the branch target; RETURN, CLOSE and ESCALATE save no recipient "
            + "at all. The request row itself is saved once, and its caller "
            + "(completeRecipientResponse) returns straight into this method without saving it. "
            + "⚠ This site is INVISIBLE to F2-227's detector and appeared only once the flush "
            + "test became transitive.");
        LOADED_ROW_RESAVES.put("WorkflowRequestService#saveRecipients",
            "REFUTED BY READING — every save here is of a `new WorkflowRequestRecipient()`, so it "
            + "is an INSERT of a row that did not exist when the caller ran. 🔑 A callee that only "
            + "ever saves freshly-constructed entities cannot re-save the caller's row, which is "
            + "the cheapest refutation in this family. WorkflowRecipientListService#saveMembers is "
            + "the same shape and is excluded for the same reason.");
    }

    /**
     * <b>The gate.</b> No method may save a freshly-constructed versioned entity and then save
     * that row again — the shape probed live at HTTP 409 on two governance controls.
     */
    @Test
    @DisplayName("no row is INSERTed and then saved again in the same transaction")
    void noFreshlyInsertedRowIsSavedTwice() {
        assertThat(insertThenResave())
            .as("""
                Each of these saves a `new` BaseEntity subclass and then saves that row again. \
                The IDENTITY insert executes immediately, row_version is insertable=false so \
                Hibernate holds null, and the follow-up UPDATE ... WHERE row_version = null \
                matches zero rows -> ObjectOptimisticLockingFailureException -> 409, whole \
                transaction rolled back. Probed live pre-fix: a COI declaration disclosing any \
                item, and the 3rd consecutive failure of a CRITICAL CCM control test. \
                Fix by doing the other work FIRST and saving ONCE (F2-175) - not by adding an \
                exemption, because nothing here is ever correct.""")
            .isEmpty();
    }

    /** The harmless population is asserted EXACTLY, in both directions. */
    @Test
    @DisplayName("the loaded-row re-save census matches the code exactly")
    void theLoadedRowCensusIsExact() {
        Set<String> found = new TreeSet<>(crossMethodDoubleSaves());
        found.removeAll(BY_DESIGN.keySet());

        assertThat(found)
            .as("""
                A cross-method re-save appeared or disappeared. This is a CENSUS of a population \
                measured harmless, not a defect list, so the fix is to read the new site and \
                answer one question: is the first save an INSERT of a `new` entity? If yes it is \
                the fatal shape and noFreshlyInsertedRowIsSavedTwice will say so. If it re-saves \
                a LOADED row, add it here with the predicate that makes it safe. If it vanished, \
                delete its entry in the same commit - or the detector has gone quiet, which is \
                worse (F2-205).""")
            .containsExactlyInAnyOrderElementsOf(LOADED_ROW_RESAVES.keySet());

        // ⚠ The vocabulary is OPEN on purpose. A first draft enumerated the two verdicts this
        // slice happened to reach first — LIVE PROBE and READING — and then rejected a correct,
        // verified "REFUTED BY THE ENTITY", which is the strongest refutation of the seven.
        // That is F2-224's own lesson (noReasonIsABareLabel rejecting a true BY DESIGN) recurring
        // inside a gate written after quoting it. 🔑 The rule is that a verdict must NAME its
        // evidence source, not that it must be one of the sources already used.
        LOADED_ROW_RESAVES.forEach((site, reason) -> assertThat(reason)
            .as("%s needs a verdict naming the evidence that refuted it — 'REFUTED BY <source>' "
                + "where the source is something a reader can go and check (F2-188)", site)
            .matches("(?s)^REFUTED BY [A-Z][A-Z ]+ [—-] .*")
            .hasSizeGreaterThan(160));
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

    /**
     * The cross-method detector, over one source string — used by the self-tests.
     *
     * <p>A fixture declares no fields, so every receiver of a {@code .save(} is taken to be a
     * repository. That is the same assumption the real scan resolves from
     * {@code private final …Repository}, narrowed there so {@code all.stream()} cannot pass for a
     * query.
     */
    private static Set<String> siblingDoubleSavesIn(String src) {
        Set<String> hits = new TreeSet<>();
        List<String[]> ms = methodsOf(src);
        Map<String, String> byName = new LinkedHashMap<>();
        for (String[] nm : ms) byName.putIfAbsent(nm[0], nm[1]);
        Set<String> repoFields = new TreeSet<>();
        Matcher all = SAVE.matcher(src);
        while (all.find()) repoFields.add(all.group(1));

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
                if (!flushesVia(cbody, repoFields)) continue;
                Matcher im = SAVE.matcher(cbody);
                while (im.find()) {
                    if (outerRepos.contains(im.group(1))) { hits.add(callee); break; }
                }
            }
        }
        return hits;
    }

    /** The INSERT detector over one source string, with the versioned types named explicitly. */
    private static Set<String> insertThenResaveIn(String src, String... versionedTypes) {
        List<String[]> ms = methodsOf(src);
        Map<String, String> byName = new LinkedHashMap<>();
        for (String[] nm : ms) byName.putIfAbsent(nm[0], nm[1]);
        Set<String> repoFields = new TreeSet<>();
        Matcher all = SAVE.matcher(src);
        while (all.find()) repoFields.add(all.group(1));
        Set<String> versioned = new TreeSet<>(Set.of(versionedTypes));

        Set<String> hits = new TreeSet<>();
        for (String[] nm : ms) {
            hits.addAll(insertThenResaveIn("Probe", nm[0], nm[1], byName, repoFields, versioned));
        }
        return hits;
    }

    /**
     * ⚠ Anti-vacuity for the INSERT detector, carrying the two literal pre-fix shapes.
     *
     * <p>The population is a hard zero, and a hard zero is exactly what a rotted regex reports
     * (F2-205). So the detector is run against the code as it stood before this slice, and against
     * the code as it stands after — and against the one shape that must NOT count, a callee whose
     * every save is of a {@code new} entity of its own.
     */
    @Test
    @DisplayName("the INSERT detector fires on both pre-fix shapes and not on the fixed ones")
    void theInsertDetectorIsNotVacuous() {
        String coiBefore = """
            class Probe {
                public Dto submitDeclaration(Req req) {
                    CoiDeclaration d = new CoiDeclaration();
                    d.setReviewOutcome("PENDING");
                    CoiDeclaration savedDecl = declarationRepo.save(d);
                    for (ItemDto dto : req.disclosedItems()) {
                        CoiDisclosedItem item = new CoiDisclosedItem();
                        item.setDeclarationId(savedDecl.getId());
                        savedItems.add(itemRepo.save(item));
                    }
                    savedDecl.setReviewOutcome(computeAutoOutcome(savedItems));
                    CoiDeclaration finalDecl = declarationRepo.save(savedDecl);
                    return Dto.from(finalDecl);
                }
            }
            """;
        assertThat(insertThenResaveIn(strip(coiBefore), "CoiDeclaration"))
            .as("the COI shape probed at 409 — a declaration disclosing any item")
            .isNotEmpty();

        String coiAfter = """
            class Probe {
                public Dto submitDeclaration(Req req) {
                    CoiDeclaration d = new CoiDeclaration();
                    for (ItemDto dto : req.disclosedItems()) {
                        CoiDisclosedItem item = new CoiDisclosedItem();
                        items.add(item);
                    }
                    d.setReviewOutcome(computeAutoOutcome(items));
                    CoiDeclaration finalDecl = declarationRepo.save(d);
                    for (CoiDisclosedItem item : items) {
                        item.setDeclarationId(finalDecl.getId());
                        savedItems.add(itemRepo.save(item));
                    }
                    return Dto.from(finalDecl);
                }
            }
            """;
        assertThat(insertThenResaveIn(strip(coiAfter), "CoiDeclaration"))
            .as("the fixed COI shape decides the outcome first and saves the declaration once")
            .isEmpty();

        String ccmBefore = """
            class Probe {
                private Res recordRunInternal(Def d, Result result) {
                    ControlTestRun run = new ControlTestRun();
                    run.setStatus(result.status());
                    run = runRepo.save(run);
                    d = defRepo.save(d);
                    if (isFailureLike && "CRITICAL".equals(d.getSeverity())) {
                        Long issueId = openConsecutiveFailureIssue(d, run);
                        if (issueId != null) {
                            run.setCapaTaskId(issueId);
                            run = runRepo.save(run);
                        }
                    }
                    return Res.from(run, d);
                }
            }
            """;
        assertThat(insertThenResaveIn(strip(ccmBefore), "ControlTestRun"))
            .as("the CCM shape probed at 409 — the 3rd consecutive CRITICAL failure")
            .isNotEmpty();

        String ccmAfter = """
            class Probe {
                private Res recordRunInternal(Def d, Result result) {
                    ControlTestRun run = new ControlTestRun();
                    run.setStatus(result.status());
                    d = defRepo.save(d);
                    if (isFailureLike && "CRITICAL".equals(d.getSeverity())) {
                        Long issueId = openConsecutiveFailureIssue(d, run);
                        if (issueId != null) {
                            run.setCapaTaskId(issueId);
                        }
                    }
                    run = runRepo.save(run);
                    return Res.from(run, d);
                }
            }
            """;
        assertThat(insertThenResaveIn(strip(ccmAfter), "ControlTestRun"))
            .as("the fixed CCM shape opens the Issue first and saves the run once")
            .isEmpty();

        // F2-227's own site, which the INSERT discriminator subsumes: its ExerciseRunInject was
        // `new` too, which is why THAT one fired live while the loaded-row sites do not.
        String f227 = """
            class Probe {
                public View addCustomInject(Long runId, Req req) {
                    ExerciseRunInject ri = new ExerciseRunInject();
                    ExerciseRunInject saved = runInjectRepo.save(ri);
                    return dispatchInject(runId, saved.getId());
                }
                public View dispatchInject(Long runId, Long injectId) {
                    ExerciseRunInject inj = runInjectRepo
                        .findByIdAndOrganisationId(injectId, orgId).orElseThrow();
                    inj.setStatus("DISPATCHED");
                    return toView(runInjectRepo.save(inj));
                }
            }
            """;
        assertThat(insertThenResaveIn(strip(f227), "ExerciseRunInject"))
            .as("F2-227's site was ALSO an insert-then-resave — the discriminator subsumes it")
            .isNotEmpty();

        // ⚠ And the refutation that must not count: a callee that only ever saves rows of its own.
        String freshOnly = """
            class Probe {
                public void createRequest(Req req) {
                    WorkflowRequest r = new WorkflowRequest();
                    requestRepo.save(r);
                    saveRecipients(r, req.recipients());
                }
                private void saveRecipients(WorkflowRequest r, List<Dto> in) {
                    for (Dto d : in) {
                        WorkflowRequestRecipient rec = new WorkflowRequestRecipient();
                        rec.setRequestId(r.getId());
                        recipientRepo.save(rec);
                    }
                }
            }
            """;
        assertThat(insertThenResaveIn(strip(freshOnly), "WorkflowRequest",
                                      "WorkflowRequestRecipient"))
            .as("saveRecipients inserts its OWN new rows on a different repository — counting "
                + "that would fail on correct code, which is the one thing a gate must not do")
            .isEmpty();
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
