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
 * The backend half of F2-202's lifecycle-store gate: <b>every endpoint the frontend registry
 * names must resolve to a real controller mapping in this codebase.</b>
 *
 * <h3>Why a cross-codebase gate, and why this direction</h3>
 * {@code tss-grc-ui-v2/src/lib/lifecycleStores.ts} declares, for each lifecycle workspace,
 * whether its state lives in the browser or on the server, which REST base serves the real
 * register, and how many rows that register held when it was measured. It is the replacement
 * for a decoy: five lifecycle libraries used to call {@code mirrorSaveToBackend(...)}, which
 * read a persistence-mode switch that nothing in the product could set and therefore returned
 * without sending anything on every path. Deleting those calls was right; deleting them and
 * recording nothing would have left five workspaces looking like ordinary local state with a
 * real, populated register behind each.
 *
 * <p>A declaration is only worth what verifies it. The frontend can check its own shape — that
 * a BROWSER_LOCAL entry names a localStorage key, that the counts are numbers, that nobody
 * quietly flips an entry to SERVER_BACKED without wiring it — but it cannot know whether
 * {@code /api/v1/privacy/dpias} exists. This test can.
 *
 * <p><b>⚠ This leg is not decoration, and the evidence is the commit that introduced it.</b>
 * Writing that registry I inferred three of the eight endpoint paths from the schema the table
 * sits in, and <em>all three were wrong, each differently</em>:
 * <ul>
 *   <li>{@code /api/privacy/ropa} — really {@code /api/v1/privacy/ropa}</li>
 *   <li>{@code /api/privacy/dpia} — really {@code /api/v1/privacy/dpias}, plural, on its own
 *       controller class rather than a sub-path of the privacy controller</li>
 *   <li>{@code /api/privacy/cross-border} — really
 *       {@code /api/privacy/cross-border-transfers}, with <em>no</em> {@code v1} segment</li>
 * </ul>
 * Each looked entirely plausible in a reviewed file. <b>A schema name does not predict a path,
 * and a plausible endpoint string in a declared registry is worth nothing until something
 * resolves it.</b>
 *
 * <h3>Hermetic</h3>
 * Reads source files only — no database, no context, no secrets — so it belongs in the normal
 * {@code mvn -o clean package} suite. Same approach as {@code ConnectorBackingContractTest},
 * which reads {@code lib/connectors.ts} for the same kind of claim.
 */
class LifecycleStoreBackingContractTest {

    private static final Path FE_STORES =
        Path.of("..", "tss-grc-ui-v2", "src", "lib", "lifecycleStores.ts");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    /** One entry of the frontend registry, as parsed out of the TypeScript literal. */
    private record Store(String id, String state, String endpoint, String table,
                         int rowsDev, int rowsProd, String localKey, String note) { }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * ⚠ Anchored on {@code id:} so a field name appearing inside a {@code note} string cannot
     * open a new record. F2-196 lost a whole vocabulary to a parse that matched a TypeScript
     * type annotation instead of the value it was aiming at, so this parse is deliberately
     * driven off the record boundary rather than off any single field.
     */
    private static List<Store> parseStores() {
        String src = read(FE_STORES);
        // Strip comments in ONE left-to-right alternation. Order is load-bearing: doing blocks
        // first lets a `/*` inside a `//` prose comment run away and swallow real declarations
        // (F2-198 lost 45% of App.tsx that way, and reported a SMALLER population, which reads
        // exactly like "those entries are fine").
        String bare = src.replaceAll("(?<!:)//[^\\n]*|/\\*(?s:.*?)\\*/", "");

        List<Store> out = new ArrayList<>();
        Matcher rec = Pattern.compile("\\{\\s*\\n\\s*id:\\s*'([^']+)'(?s:(.*?))\\n\\s*\\},")
            .matcher(bare);
        while (rec.find()) {
            String id = rec.group(1);
            String body = rec.group(2);
            out.add(new Store(
                id,
                str(body, "state"),
                str(body, "endpoint"),
                str(body, "table"),
                num(body, "rowsDev"),
                num(body, "rowsProd"),
                str(body, "localKey"),
                // `note` is a concatenated multi-line string; keep it all for the length check.
                body.replaceAll("(?s).*note:\\s*", "").replaceAll("[\\n\\s]+", " ")));
        }
        return out;
    }

    private static String str(String body, String field) {
        Matcher m = Pattern.compile(field + ":\\s*'((?:[^'\\\\]|\\\\.)*)'").matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static int num(String body, String field) {
        Matcher m = Pattern.compile(field + ":\\s*(-?\\d+)").matcher(body);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // ── The endpoints this codebase actually serves ───────────────────────────

    /**
     * Every request path the Java sources map, as {@code classMapping + methodMapping} pairs.
     *
     * <p>Source-scanned rather than reflected because this test must stay context-free, and
     * unlike {@code FrontendEndpointContractTest} it only needs to answer "does any controller
     * mount this base?" — a prefix question, not a per-endpoint one. The regexes are anchored
     * on the annotation, so a javadoc sentence cannot contribute a mapping (the failure mode
     * that made a source-regex scan report 1,763 missing endpoints in F2-170).
     */
    private static Set<String> mappedBases() {
        Set<String> bases = new LinkedHashSet<>();
        Pattern classMap = Pattern.compile("@RequestMapping\\(\\s*\"([^\"]+)\"");
        try (Stream<Path> s = Files.walk(MAIN_JAVA)) {
            for (Path p : s.filter(f -> f.toString().endsWith("Controller.java")).toList()) {
                Matcher m = classMap.matcher(read(p));
                while (m.find()) bases.add(m.group(1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + MAIN_JAVA.toAbsolutePath(), e);
        }
        return bases;
    }

    /** True when some controller is mounted at, or above, this endpoint base. */
    private static boolean isServed(String endpoint, Set<String> bases) {
        for (String b : bases) {
            if (endpoint.equals(b) || endpoint.startsWith(b + "/")) return true;
        }
        return false;
    }

    // ── Legs ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the frontend lifecycle-store registry parses, and is not empty")
    void theRegistryParses() {
        assertThat(Files.exists(FE_STORES))
            .as("frontend not checked out at " + FE_STORES.toAbsolutePath())
            .isTrue();
        List<Store> stores = parseStores();
        // Anti-vacuity. If the parse silently returns nothing, every assertion below passes
        // over an empty list — the quietest way for this gate to stop working.
        assertThat(stores).as("parsed no entries — the parse has rotted, not the registry")
            .hasSizeGreaterThanOrEqualTo(8);
        assertThat(stores).allSatisfy(st -> {
            // ⚠ The state vocabulary is pinned HERE as well as in the TypeScript union, and that
            // is deliberate: adding a state is a claim about how a workspace persists, so it must
            // be made in a commit that also updates this mirror. It fired on its own author —
            // `SERVER_READ_LOCAL_WRITE` was added to the registry after this list was written,
            // for `auditLifecycle.ts`, which reads four real endpoints and drops every write.
            assertThat(st.state())
                .as("unknown lifecycle-store state '%s' on %s — add it here in the same commit "
                    + "that adds it to LifecycleStoreState, or the vocabularies drift",
                    st.state(), st.id())
                .isIn("SERVER_BACKED", "BROWSER_LOCAL", "SERVER_READ_LOCAL_WRITE",
                      "NO_SERVER_SURFACE", "PARTIALLY_SERVER_BACKED");
            // ⚠⚠ F2-204: a blank endpoint is now legal, and ONLY for NO_SERVER_SURFACE.
            // That is not a loosening — it is the difference between "names a path nothing
            // serves" (still a hard zero below) and "makes no claim, because nothing serves
            // this table at all". Conflating them is what let F2-202's registry declare 6
            // corpora when the modules own 16: every corpus without an endpoint was simply
            // left out, and a registry that omits what it cannot resolve reads exactly like a
            // complete one.
            if (st.endpoint().isEmpty()) {
                assertThat(st.state())
                    .as("%s declares no endpoint, so it must declare NO_SERVER_SURFACE — a "
                        + "blank path on any other state hides a wireable store", st.id())
                    .isEqualTo("NO_SERVER_SURFACE");
            } else {
                assertThat(st.endpoint()).startsWith("/api/");
            }
            // One store legitimately has no table: `cyber-filings` has no register anywhere.
            if (!st.table().isEmpty()) {
                assertThat(st.table()).matches("[a-z_]+\\.[a-z_]+");
            }
        });
    }

    @Test
    @DisplayName("every endpoint the registry names resolves to a real controller mapping")
    void everyDeclaredEndpointIsServed() {
        Set<String> bases = mappedBases();
        assertThat(bases).as("scanned no controller mappings — the scan has rotted")
            .hasSizeGreaterThan(100);

        Map<String, String> unresolved = new LinkedHashMap<>();
        for (Store st : parseStores()) {
            // A NO_SERVER_SURFACE store makes no endpoint claim; the leg below checks the
            // stronger property instead — that no entity even maps to its table, so no
            // controller could serve it.
            if (st.endpoint().isEmpty()) continue;
            if (!isServed(st.endpoint(), bases)) unresolved.put(st.id(), st.endpoint());
        }
        assertThat(unresolved)
            .as("""
                A lifecycle store names a REST base that no controller in this codebase mounts.

                This is a HARD ZERO with no exemption map, because the whole point of the entry
                is that a real register exists behind the browser-local one. An endpoint nothing
                serves makes the declaration worse than an absence: it tells the next reader the
                wiring job is smaller than it is.

                Resolve the path from the controller's @RequestMapping, never from the schema the
                table lives in — inferring it from the schema got three of eight wrong when this
                registry was first written.""")
            .isEmpty();
    }

    @Test
    @DisplayName("a NO_SERVER_SURFACE store's table really has no entity mapped to it")
    void noServerSurfaceStoresReallyHaveNone() throws IOException {
        // 🔑 "Nothing serves this table" is the least-verified kind of statement a registry
        // can carry — F2-188's lesson that a named-map gate proves a reason EXISTS and nothing
        // proves it is TRUE. So it is verified structurally: a controller cannot serve a table
        // that no @Entity maps to, and @Table(schema=…, name=…) is resolvable from source.
        //
        // It also fails in the useful direction. If somebody builds the entity, service and
        // controller for `bcm_lc.drills`, this leg fires and says the store is now wireable —
        // which is exactly the signal a slice planning that work needs.
        Set<String> mapped = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(Path.of("src", "main", "java"))) {
            for (Path f : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = Pattern.compile(
                    "@Table\\(\\s*schema\\s*=\\s*\"([a-z_]+)\"\\s*,\\s*name\\s*=\\s*\"([a-z_]+)\"")
                    .matcher(Files.readString(f));
                while (m.find()) mapped.add(m.group(1) + "." + m.group(2));
            }
        }
        assertThat(mapped).as("scanned no @Table mappings — the scan has rotted")
            .hasSizeGreaterThan(100);

        Map<String, String> nowMapped = new LinkedHashMap<>();
        for (Store st : parseStores()) {
            if (!"NO_SERVER_SURFACE".equals(st.state()) || st.table().isEmpty()) continue;
            if (mapped.contains(st.table())) nowMapped.put(st.id(), st.table());
        }
        assertThat(nowMapped)
            .as("A store declares NO_SERVER_SURFACE, but an @Entity maps to its table — so a "
                + "controller could serve it, and the declaration understates what is possible. "
                + "Either the entity is new (in which case this store is now wireable: change "
                + "the state, name the endpoint, and everyDeclaredEndpointIsServed will resolve "
                + "it), or the claim was wrong when it was written.")
            .isEmpty();
    }

    @Test
    @DisplayName("a BROWSER_LOCAL store names its localStorage key; a SERVER_BACKED one has none")
    void stateAndLocalKeyAgree() {
        for (Store st : parseStores()) {
            switch (st.state()) {
                case "BROWSER_LOCAL" -> assertThat(st.localKey())
                    .as(st.id() + " is BROWSER_LOCAL but names no localStorage key — the key is "
                        + "how anyone finds the corpus it is warning about")
                    .startsWith("grc.");
                case "SERVER_BACKED" -> assertThat(st.localKey())
                    .as(st.id() + " is SERVER_BACKED and still names a localStorage key. Either "
                        + "the rewire is incomplete or the entry is stale; both are worth failing.")
                    .isEmpty();
                // SERVER_READ_LOCAL_WRITE says nothing either way: `auditLifecycle.ts` caches in a
                // module-level array and keeps no key at all, but another store in this state could
                // legitimately cache to localStorage. The frontend gate checks the stronger claim —
                // that a declared key is one the module really writes, and that a store declaring
                // none really keeps none.
                default -> { }
            }
        }
    }

    @Test
    @DisplayName("measured row counts are real numbers, and dev/prod are both stated")
    void countsAreMeasured() {
        for (Store st : parseStores()) {
            assertThat(st.rowsDev())
                .as(st.id() + " has no measured dev row count. A store claiming a real register "
                    + "behind it without a number is an assertion nobody checked.")
                .isGreaterThanOrEqualTo(0);
            assertThat(st.rowsProd())
                .as(st.id() + " has no measured prod row count. Both databases matter: F2-183's "
                    + "rule is to measure prod before believing a dev measurement generalises.")
                .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("every store's note names a consequence, not a label")
    void notesAreSubstantial() {
        for (Store st : parseStores()) {
            // F2-188's lesson: a named map proves a reason EXISTS and is substantial; nothing
            // proves it is TRUE, so the reasons are the least-verified part of the most
            // authoritative-looking artefact. This is the weakest of the five legs here and it
            // is stated as such — the endpoint resolution above is the one that has teeth.
            assertThat(st.note().length())
                .as(st.id() + "'s note is too short to say what is actually true about the store")
                .isGreaterThan(80);
        }
    }

    @Test
    @DisplayName("the deleted persistence-mode switch has not come back")
    void theModeSwitchStaysDeleted() {
        Path deleted = Path.of("..", "tss-grc-ui-v2", "src", "lib", "platformPersistence.ts");
        assertThat(Files.exists(deleted))
            .as("""
                lib/platformPersistence.ts is back.

                It held a three-mode persistence switch ('demo' | 'rest' | 'mixed') whose setter
                had zero callers, whose localStorage key nothing wrote, and which took no Vite
                env input — so `currentPersistenceMode()` answered 'demo' in every build, and the
                three mirror*ToBackend helpers it exported were unconditional no-ops called from
                five modules. Its own header claimed it drove the topbar mode badge; the badge
                reads lib/productionMode.ts, a different switch with a different key.

                If a mirror-to-backend mechanism is genuinely wanted, it needs a reachable setter
                and a read path that consults it — the read path here never did, so a write that
                reached the server could not have been displayed.""")
            .isFalse();
    }
}
