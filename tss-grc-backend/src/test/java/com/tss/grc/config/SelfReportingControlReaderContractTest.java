package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-437 — a control that exists to BE READ, with nothing that reads it.
 *
 * <p>{@code NotificationReachabilityService} was written because the automation engine "had been
 * reporting its own inertness for as long as it had been running: {@code platform.audit_log} held
 * 143 rows in dev, and 164 in production, whose message was 'has no role configured'. <b>Nobody
 * read them.</b> That is the third time in this programme a control announced its own failure into
 * a channel with no reader — after F2-87's boot WARN and F2-92's javadoc. A better-worded WARN
 * would have been the fourth." Its remedy was an endpoint — and the frontend has <b>zero</b>
 * references to it.
 *
 * <h3>🔑 The count was right and it stopped one short of itself</h3>
 *
 * An unread JSON report is a channel with no reader exactly as an unread WARN is; what changed was
 * the format, not the readership. Three surfaces are in that state today, and the shape of each
 * claim differs, which is worth keeping distinct rather than lumping:
 *
 * <ul>
 *   <li><b>notification-reachability</b> — claimed "a report an operator can <em>open</em>". That
 *       was FALSE: no screen opens it. Corrected by F2-437.</li>
 *   <li><b>governance-health</b> — named "the platform-admin / production-readiness surface", and
 *       {@code pages/production-readiness} has been UNROUTED since Sprint 4 §3.3 (the folder is on
 *       disk; {@code App.tsx} carries only two comments recording the cull). It pointed a reader at
 *       a page nobody can open.</li>
 *   <li><b>sod-coverage</b> — says {@code enabledButBlind} is "the row worth alerting on". Nothing
 *       alerts. That is a recommendation rather than a false claim, and it is the artefact F2-251
 *       found publishing four gates while the module enforced six — which is easier to miss when
 *       nobody can look at it.</li>
 * </ul>
 *
 * <h3>Why a MAP and not a hard zero</h3>
 *
 * Building the console is product work, so a zero would fail on a tree nobody has had the chance to
 * fix (F2-214). The population is asserted <b>exactly and in both directions</b>: wiring one must
 * delete its entry in the same commit, and a fourth cannot join a count silently — house rule 3's
 * warning, which is why {@code FrontendEndpointContractTest}'s platform-wide 417 clientless
 * endpoints cannot serve here. 🔑 <b>A decrease-only count of clientless endpoints cannot tell an
 * unbuilt feature from a control that only works if somebody reads it.</b>
 *
 * <p>⚙ Hermetic, and it reads the frontend tree from disk the way {@code
 * ConnectorBackingContractTest} (F2-193) and {@code FullReplaceUpdateContractTest} (F2-213) do:
 * this is one question asked across two codebases, so it cannot be answered from either alone.
 */
@DisplayName("F2-437 — a self-reporting control must have a reader, or say that it has none")
class SelfReportingControlReaderContractTest {

    private static final Path BE = Path.of("src/main/java");
    private static final Path FE = Path.of("..", "tss-grc-ui-v2", "src");

    /**
     * Path segments that mean "this endpoint exists to be looked at".
     *
     * <p>⚠ It over-matches, deliberately: {@code gaps?} pulls in register lists and per-record
     * lookups. That is the safe direction — a candidate is adjudicated below rather than assumed —
     * and the two known over-matches are named in {@link #NOT_A_REPORT} so the noise is recorded
     * rather than silently filtered (F2-252: a scan locates, the adjudication is the measurement).
     */
    private static final Pattern REPORT_SHAPED = Pattern.compile(
        "coverage|reachability|integrity|provenance|readiness|posture|gaps?|orphan|census|"
        + "health|freshness|drift|completeness|parity|conformance", Pattern.CASE_INSENSITIVE);

    /** Candidates the vocabulary catches that are not reports. Excluded WITH a reason. */
    private static final Map<String, String> NOT_A_REPORT = Map.of(
        "/api/ndi/gaps/{id}/linked-risk",
        "Matched on 'gaps' in the collection segment. It is a per-record lookup returning one "
        + "gap's linked risk, not a report about the platform's own state — nothing about it is "
        + "diminished by having no dedicated screen.",
        "/api/v1/bcm/gaps",
        "Matched on 'gaps'. It is the BCM gap REGISTER's list endpoint — a register, not a "
        + "self-report — and it is read by the frontend anyway.");

    /**
     * The report-shaped endpoints with no frontend reader. Exact, both directions, and each reason
     * names the consequence rather than labelling the row (F2-188).
     */
    private static final Map<String, String> NO_READER = Map.of(
        "/api/v1/automation/notification-reachability",
        "F2-94's remedy for three counted instances of 'a control announced its own failure into a "
        + "channel with no reader' — and it is the fourth. It reports which NOTIFY/ESCALATE rules "
        + "reach nobody, separating NO_ACTIVE_HOLDER (grantable) from NOT_A_ROLE (not), and "
        + "measured 69 of 84 addressed role names are not rows in iam.roles. No client, no page, "
        + "no link, so an operator cannot learn that an escalation reaches nobody. Wiring it needs "
        + "an automation console, which does not exist: GET /api/v1/automation/rules has no client "
        + "either, so all 214 rules are administered only by SQL. FILED as product work.",

        "/api/v1/changes/sod-coverage",
        "CE-15's answer to F2-112: per self-review gate, the population it can decide versus the "
        + "part it cannot, with enabledButBlind flagging a gate switched on over a population it "
        + "has never refused anything in. Unread, which is how F2-251 found it publishing four "
        + "gates while the module enforced six. The refusals themselves are live and "
        + "ChangeSodGateCoverageContractTest derives its population from them rather than from "
        + "this report, so the gap is reporting rather than enforcement. FILED.",

        "/api/v1/changes/governance-health",
        "S4/W1-4 finding F-4: whether this tenant's committee routing is configured at all. Its "
        + "service javadoc named 'the platform-admin / production-readiness surface', and that "
        + "page has been unrouted since Sprint 4 §3.3. ADR-017 D-2 made the gap ENFORCE (a 409 "
        + "rather than a silent downgrade), so what is unread is the diagnostic that would tell an "
        + "administrator which cells to fill — the same class's javadoc says 'a WARN is not a "
        + "control', and with this endpoint unread the WARN is all the reporting there is. FILED.");

    // ─── the census ───────────────────────────────────────────────────

    private record Endpoint(String path, String owner) {}

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + p, e);
        }
    }

    /** One left-to-right alternation, newline-preserving — F2-198/F2-211. */
    private static String stripComments(String src) {
        Matcher m = Pattern.compile("(?<!:)//[^\n]*|/\\*[\\s\\S]*?\\*/").matcher(src);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(src, last, m.start());
            out.append(m.group().replaceAll("[^\n]", " "));
            last = m.end();
        }
        return out.append(src.substring(last)).toString();
    }

    private static List<Endpoint> reportShapedEndpoints() {
        List<Endpoint> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(BE)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                               .sorted().toList()) {
                String src = stripComments(read(f));
                Matcher base = Pattern.compile(
                    "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"").matcher(src);
                String prefix = base.find() ? base.group(1) : "";
                Matcher get = Pattern.compile(
                    "@GetMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"").matcher(src);
                while (get.find()) {
                    String p = get.group(1);
                    String full = p.startsWith("/api") ? p
                        : (prefix.replaceAll("/$", "") + "/" + p.replaceAll("^/", ""));
                    if (REPORT_SHAPED.matcher(full).find()) {
                        out.add(new Endpoint(full, f.getFileName().toString()));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + BE, e);
        }
        return out;
    }

    private static String frontendText() {
        StringBuilder sb = new StringBuilder(1 << 22);
        try (Stream<Path> files = Files.walk(FE)) {
            for (Path f : files.filter(Files::isRegularFile)
                               .filter(p -> {
                                   String n = p.getFileName().toString();
                                   return n.endsWith(".ts") || n.endsWith(".tsx");
                               }).sorted().toList()) {
                sb.append(read(f)).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + FE, e);
        }
        return sb.toString();
    }

    /** The endpoint's own last segment, which is what a client names. */
    private static String tail(String path) {
        String[] parts = path.replaceAll("/$", "").split("/");
        return parts[parts.length - 1];
    }

    private static Map<String, Endpoint> unread() {
        String fe = frontendText();
        Map<String, Endpoint> out = new TreeMap<>();
        for (Endpoint e : reportShapedEndpoints()) {
            if (NOT_A_REPORT.containsKey(e.path())) continue;
            String t = tail(e.path());
            if (t.contains("{")) continue;                      // a path variable names nothing
            if (fe.contains(t) || fe.contains(e.path())) continue;
            out.put(e.path(), e);
        }
        return out;
    }

    // ─── the legs ─────────────────────────────────────────────────────

    @Test
    void everyUnreadSelfReportIsNamedAndExplained() {
        Map<String, Endpoint> unread = unread();
        assertThat(unread.keySet())
            .as("""
                A report-shaped endpoint with no frontend reader is a control that cannot do the \
                one thing it exists for. Wire it to a screen, or add it here with a reason that \
                names the consequence — never leave it to be counted among \
                FrontendEndpointContractTest's clientless endpoints, which cannot tell an unbuilt \
                feature from an unreadable control. Found: %s"""
                .formatted(unread))
            .containsExactlyInAnyOrderElementsOf(NO_READER.keySet());
    }

    @Test
    void everyNamedEntryIsStillUnread() {
        // The REVERSE direction, and the one nobody writes first (F2-199). An entry for an endpoint
        // that has since GAINED a reader reads exactly like an entry for one that has not — so
        // wiring a console must delete its entry in the same commit.
        Map<String, Endpoint> unread = unread();
        for (String path : NO_READER.keySet()) {
            assertThat(unread).as(
                "%s now has a frontend reader (or no longer exists): delete its NO_READER entry "
                + "in the same commit that wired it", path).containsKey(path);
        }
    }

    @Test
    void everyReasonNamesAConsequenceRatherThanLabellingTheRow() {
        // F2-188 measured what a map of fluent labels is worth: five claims, five false. A reason
        // has to say what is lost, which is what makes it checkable by the next reader.
        NO_READER.forEach((path, reason) -> {
            assertThat(reason.length()).as("%s: a bare label is not a reason", path)
                .isGreaterThan(160);
            assertThat(reason).as("%s: a reason must say it is filed rather than accepted", path)
                .contains("FILED");
        });
        NOT_A_REPORT.forEach((path, reason) ->
            assertThat(reason.length()).as("%s: say WHY it is not a report", path)
                .isGreaterThan(80));
    }

    @Test
    void theClaimsThatAssertedAReaderStayCorrected() {
        // The half that keeps the fix. Each of these sentences told the next reader the control was
        // delivered; a comment that answers the question a reader was about to ask is worse than
        // no comment (F2-184), and on an assurance control it is worse again.
        String reach = read(BE.resolve(
            "com/tss/grc/module/platform/automation/service/NotificationReachabilityService.java"));
        assertThat(reach).as("F2-94's javadoc must not claim an operator can open this")
            .doesNotContain("a report an operator can open");
        assertThat(reach).as("and it must say where the state actually is")
            .contains("/api/v1/automation/notification-reachability");

        String health = read(BE.resolve(
            "com/tss/grc/module/change_mgmt/service/ChangeGovernanceHealthService.java"));
        assertThat(health).as("must not point a reader at the unrouted production-readiness page")
            .doesNotContain("production-readiness surface can poll");
    }

    @Test
    void theProductionReadinessPageIsReallyUnrouted() {
        // ⚠⚠ The claim above rests on this, and re-deriving it is where I got it WRONG: a bare
        // `grep -c production-readiness App.tsx` answers 2 and reads as "routed", because both hits
        // are the comments recording the Sprint 4 §3.3 cull. F2-193's epitaph-as-the-corpse, in the
        // check written to verify the claim. The route SHAPE is the honest instrument.
        String app = read(FE.resolve("App.tsx"));
        assertThat(app).as("the folder is on disk and the route is gone — comments do not route")
            .doesNotContain("path=\"/production-readiness\"");
        assertThat(app).as("and the cull really is recorded there, so a bare token scan finds it")
            .contains("production-readiness");
    }

    @Test
    void theDetectorActuallyRanOverBothTrees() {
        // A hard-asserted map over a population the instrument cannot see is the most reassuring
        // possible artefact (F2-205). If the frontend walk failed, EVERY endpoint would read as
        // unread and leg 1 would fail loudly — but if it silently returned something huge and
        // irrelevant, members would drop out quietly. So: prove a known-read report resolves.
        String fe = frontendText();
        assertThat(fe.length()).as("the frontend tree was not read").isGreaterThan(2_000_000);
        assertThat(fe).as("a known-read self-report must resolve, or 'read' means nothing")
            .contains("integrity");

        List<Endpoint> candidates = reportShapedEndpoints();
        assertThat(candidates).as("the controller walk found no report-shaped endpoint")
            .hasSizeGreaterThan(12);
        assertThat(candidates).extracting(Endpoint::path)
            .as("the two known over-matches must still BE candidates, or NOT_A_REPORT is stale")
            .containsAll(NOT_A_REPORT.keySet());
    }

    @Test
    void theCommentStripperIsSoundInBothDirections() {
        // Load-bearing: ChangeManagementController's own javadoc now quotes "sod-coverage" to
        // record this finding, and NotificationReachabilityService quotes its retired sentence. A
        // raw scan of the controllers would read those as mappings.
        String stripped = stripComments("""
            /** see @GetMapping("/retired-coverage") */
            @GetMapping("/live-coverage")
            """);
        assertThat(stripped).doesNotContain("retired-coverage");
        assertThat(stripped).contains("live-coverage");
        assertThat(stripped.lines().count())
            .as("newlines must survive, or every reported line number is wrong").isEqualTo(2);
    }

    @Test
    void theSiblingsThatDoHaveAReaderAreNotInTheMap() {
        // The boundary, asserted so a later sweep cannot quietly widen the map: these are read, and
        // an entry for one of them would be a reason for something not in the population — F2-199's
        // own first-draft defect.
        Map<String, Endpoint> unread = unread();
        for (String read : List.of("/api/v1/audit-log/integrity",
                                   "/api/v1/regulatory/posture",
                                   "/api/v1/committees/registry/linkage-coverage")) {
            assertThat(unread).as("%s has a reader and must not be filed as unread", read)
                .doesNotContainKey(read);
        }
    }

    @Test
    void theMapIsOrderedForAReaderRatherThanForTheCompiler() {
        // Map.of has no order, so print it sorted — a three-entry map read in hash order is a
        // three-entry map somebody has to sort by hand every time they open the file.
        Map<String, String> sorted = new LinkedHashMap<>(new TreeMap<>(NO_READER));
        assertThat(sorted.keySet()).containsExactly(
            "/api/v1/automation/notification-reachability",
            "/api/v1/changes/governance-health",
            "/api/v1/changes/sod-coverage");
    }
}
