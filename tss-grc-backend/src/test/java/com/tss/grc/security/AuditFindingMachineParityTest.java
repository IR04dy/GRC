package com.tss.grc.security;

import com.tss.grc.module.audit_lc.service.AuditLifecycleService;
import com.tss.grc.module.audit_lc.service.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-203 — <b>the frontend's copy of the IIA finding machine must equal the server's, edge for
 * edge</b>, and the states it calls "unmanaged" must genuinely have no edge on the server.
 *
 * <h3>Why this gate exists</h3>
 * {@link FindingService#ALLOWED} is the authority: {@code transition} answers 409 outside it.
 * Before F2-203 the frontend held <em>three</em> further copies of the same lifecycle — an
 * {@code onAdvance} lookup map, four hard-coded {@code if (f.status === …)} buttons, and a
 * {@code transitionFinding} that accepted any target string at all — over a store that wrote
 * nothing, so none of them could ever be contradicted by a server response. Wiring the writes
 * makes every divergence reachable: the UI offered {@code OPEN → CLOSED}, which the server
 * refuses.
 *
 * <p>This is the fifth writer-vs-table finding in this programme (F2-175, F2-189, F2-190,
 * F2-191), and the first where the extra writers were in the other codebase. The rule that
 * produced all five: <b>when a module has a declared state machine, ask not "is it correct?"
 * but "how many things write this status, and how many of them ask?"</b>
 *
 * <h3>The second half is the one that can rot silently</h3>
 * {@code audit_lc} has <b>zero CHECK constraints</b> — not a subset, none — so
 * {@code findings.status} has no constrained vocabulary and 7 of the 20 rows in dev <em>and</em>
 * prod carry {@code IN_PROGRESS}, {@code REMEDIATED} or {@code VERIFIED}: tokens the machine
 * declares no edge out of, so those findings cannot be advanced or closed through any route.
 * The frontend declares them as {@code FINDING_UNMANAGED_STATUSES} so it can label and disclose
 * them. If someone later adds one of them to {@code ALLOWED} here, that declaration becomes a
 * lie — the screen would keep saying "no declared transition" over a row the server would now
 * happily advance. {@link #noStateIsBothManagedAndDeclaredUnmanaged} is that check, and it is
 * the direction nobody writes first (F2-199).
 *
 * <h3>Hermetic</h3>
 * Reflection over {@code FindingService} plus a read of two source files. No database, no
 * Spring context, no secrets — it belongs in {@code mvn -o clean package}. The server side is
 * read by <b>reflection, never a regex</b>: this register records four pattern mistakes over
 * Java source in one slice and three phantom counts in three consecutive findings.
 */
class AuditFindingMachineParityTest {

    private static final Path UI = Path.of("..", "tss-grc-ui-v2", "src");
    private static final Path LIB = UI.resolve(Path.of("lib", "auditLifecycle.ts"));
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "audit_lc", "service", "FindingService.java");
    private static final Path WP_CONTROLLER = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "audit_lc", "controller", "AuditLifecycleController.java");

    private static String read(Path p) {
        try {
            assertThat(Files.exists(p)).as("%s must exist", p).isTrue();
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Strip line and block comments in ONE left-to-right alternation.
     *
     * <p>⚠ The order is load-bearing. Written the obvious way — block first, then line — a
     * {@code //} comment whose prose contains a literal {@code /*} opens a block that runs to
     * the next closing delimiter; F2-198 lost 45% of a file that way and reported a population
     * a third of the true size, which reads exactly like "those files are fine". A corrupted
     * source yields a SMALLER population, so this is the quietest way for a scan to be wrong.
     */
    private static String stripComments(String src) {
        return src.replaceAll("(?<!:)//[^\\n]*|/\\*(?s:.)*?\\*/", "");
    }

    /** A server machine, read by reflection rather than parsed out of the source. */
    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> serverMachine(Class<?> owner, String field) {
        try {
            Field f = owner.getDeclaredField(field);
            f.setAccessible(true);
            return (Map<String, Set<String>>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(owner.getSimpleName() + "." + field
                + " must exist and be static", e);
        }
    }

    private static Map<String, Set<String>> serverMachine() {
        return serverMachine(FindingService.class, "ALLOWED");
    }

    /**
     * The four (frontend table, server machine, frontend unmanaged list) triples.
     *
     * ⚠⚠ F2-203 started as "wire the finding writes" and found the SAME defect in every
     * register of the module: {@code audit_lc} has zero CHECK constraints and each table holds
     * status values its own service's vocabulary does not declare — 19 of 46 rows in dev and
     * prod, none of which can be advanced by any route. Two of the three sibling label maps
     * crashed on them. 🔑 <b>Each crash was hidden by an accident</b> (a broken picker; a
     * {@code plans[0]} that happened to pick the other row; a ternary whose "overdue" branch
     * won), and this slice's own plan-selection fix is what exposed the second — so a gate
     * scoped to findings alone would have certified three broken registers.
     */
    private record Register(String name, String feTable, Class<?> owner, String serverField,
                            String feUnmanaged, String expectedUnmanaged) {}

    private static final List<Register> REGISTERS = List.of(
        new Register("finding", "FINDING_ALLOWED", FindingService.class, "ALLOWED",
            "FINDING_UNMANAGED_STATUSES", "IN_PROGRESS,REMEDIATED,VERIFIED"),
        new Register("annual plan", "PLAN_ALLOWED", AuditLifecycleService.class, "AP_TRANSITIONS",
            "PLAN_UNMANAGED_STATUSES", "APPROVED"),
        new Register("working paper", "WP_ALLOWED", AuditLifecycleService.class, "WP_TRANSITIONS",
            "WP_UNMANAGED_STATUSES", "REVIEWED"),
        new Register("CAPA", "CAPA_ALLOWED", AuditLifecycleService.class, "CAPA_TRANSITIONS",
            "CAPA_UNMANAGED_STATUSES", "VERIFIED"));

    /**
     * Parse {@code FINDING_ALLOWED} out of the TypeScript.
     *
     * <p>Anchored on the assignment, not on a bare bracket: F2-196's first draft matched the
     * type annotation {@code readonly string[]} and returned an empty set, which reads exactly
     * like "the frontend declares none".
     */
    private static Map<String, Set<String>> frontendMachine() {
        return frontendMachine("FINDING_ALLOWED");
    }

    private static Map<String, Set<String>> frontendMachine(String constant) {
        String src = stripComments(read(LIB));
        Matcher block = Pattern.compile(
            "export const " + constant + "\\s*:[^=]*=\\s*\\{(.*?)\\n\\};", Pattern.DOTALL).matcher(src);
        assertThat(block.find())
            .as("%s must be declared in lib/auditLifecycle.ts as an object literal", constant)
            .isTrue();
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("(\\w+)\\s*:\\s*\\[([^\\]]*)\\]").matcher(block.group(1));
        while (entry.find()) {
            Set<String> targets = new LinkedHashSet<>();
            Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(entry.group(2));
            while (tok.find()) targets.add(tok.group(1));
            out.put(entry.group(1), targets);
        }
        assertThat(out).as("the parse must find edges — an empty map means the pattern rotted")
            .isNotEmpty();
        return out;
    }

    private static Set<String> frontendUnmanaged() {
        return frontendUnmanaged("FINDING_UNMANAGED_STATUSES");
    }

    private static Set<String> frontendUnmanaged(String constant) {
        String src = stripComments(read(LIB));
        Matcher m = Pattern.compile(
            constant + "\\s*:[^=]*=\\s*\\n?\\s*\\[([^\\]]*)\\]").matcher(src);
        assertThat(m.find()).as("%s must be declared", constant).isTrue();
        Set<String> out = new LinkedHashSet<>();
        Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(m.group(1));
        while (tok.find()) out.add(tok.group(1));
        return out;
    }

    @Test
    @DisplayName("the frontend's finding machine equals FindingService.ALLOWED, edge for edge")
    void frontendMachineMatchesTheServer() {
        Map<String, Set<String>> server = serverMachine();
        Map<String, Set<String>> fe = frontendMachine();

        // The server omits terminal states from its map entirely (getOrDefault → empty), while
        // the frontend must list them so `FINDING_TERMINAL` can be DERIVED rather than typed
        // twice (F2-196). So compare the edge SETS, treating an absent server key as no edges.
        Set<String> states = new TreeSet<>(fe.keySet());
        states.addAll(server.keySet());
        for (String state : states) {
            Set<String> want = server.getOrDefault(state, Set.of());
            Set<String> got = fe.getOrDefault(state, Set.of());
            assertThat(new TreeSet<>(got))
                .as("state %s: the frontend offers %s, the server allows %s. A button the server "
                    + "refuses answers 409 to an auditor closing a finding; a button the "
                    + "frontend omits makes a legal transition unreachable.", state, got, want)
                .isEqualTo(new TreeSet<>(want));
        }
    }

    @Test
    @DisplayName("every state the frontend can advance INTO is in the server's transition pattern")
    void everyTargetIsAcceptedByTheDtoPattern() {
        // FindingDto.TransitionRequest carries @Pattern(OPEN|MGMT_RESPONSE|…). @Valid fires
        // BEFORE the service, so a target outside it is a 400 that never reaches the machine —
        // a different failure from the 409 the machine gives, and one no parity check on
        // ALLOWED alone would see.
        String dto = stripComments(read(Path.of("src", "main", "java", "com", "tss", "grc",
            "module", "audit_lc", "dto", "FindingDto.java")));
        Matcher m = Pattern.compile("@Pattern\\(regexp = \"([A-Z_|]+)\"\\)\\s*\\n?\\s*private String status")
            .matcher(dto);
        assertThat(m.find()).as("TransitionRequest.status must carry a @Pattern").isTrue();
        Set<String> accepted = Set.of(m.group(1).split("\\|"));
        for (Set<String> targets : frontendMachine().values()) {
            for (String target : targets) {
                assertThat(accepted)
                    .as("the frontend can advance into %s, which TransitionRequest's @Pattern "
                        + "rejects with a 400 before the machine is consulted", target)
                    .contains(target);
            }
        }
    }

    @Test
    @DisplayName("no state is both managed by the server and declared unmanaged by the frontend")
    void noStateIsBothManagedAndDeclaredUnmanaged() {
        // ⚠ The reverse direction, and the one that rots silently. The frontend renders a
        // "No declared transition" badge for these three and offers no button. If a state is
        // later given an edge here, that badge becomes a false statement over a row the server
        // would advance — and nothing on the frontend side could notice.
        Set<String> managed = serverMachine().keySet();
        for (String unmanaged : frontendUnmanaged()) {
            assertThat(managed)
                .as("%s is declared unmanaged in lib/auditLifecycle.ts, but FindingService."
                    + "ALLOWED now has an entry for it. Either delete it from "
                    + "FINDING_UNMANAGED_STATUSES and add it to FINDING_ALLOWED, or drop the "
                    + "server edge — the screen currently tells an auditor this finding cannot "
                    + "be advanced.", unmanaged)
                .doesNotContain(unmanaged);
        }
        // And the claim must not be empty: these three are the measured population, and a
        // slice that "fixes" the disclosure by deleting the list would hide the finding.
        assertThat(frontendUnmanaged())
            .as("the three statuses 7 of 20 real rows carry must stay declared until an owner "
                + "decides how to reconcile them — deleting the list removes the disclosure, "
                + "not the defect")
            .containsExactlyInAnyOrder("IN_PROGRESS", "REMEDIATED", "VERIFIED");
    }

    @Test
    @DisplayName("the server mints a finding's reference and sequence — never the caller")
    void createIgnoresAnyClientSuppliedIdentity() {
        String svc = stripComments(read(SERVICE));
        // F2-172(d)'s rule, third instance after the intake substrate and DSR cases: only the
        // server may issue a tracking code. Assert the CALL SHAPE, not a substring — F2-194 had
        // two assertions satisfied by a type name and an import respectively.
        assertThat(svc)
            .as("create must always mint the external id")
            .containsPattern("f\\.setExternalId\\(generateExternalId\\(\\)\\)");
        assertThat(svc)
            .as("create must not fall back to a client-supplied id")
            .doesNotContainPattern("setExternalId\\(\\s*req\\.getId\\(\\)");
        assertThat(svc)
            .as("the ref number is a per-engagement sequence the client cannot know")
            .doesNotContainPattern("req\\.getRefNumber\\(\\)");
    }

    @Test
    @DisplayName("a working paper's author is the authenticated principal, not a body field")
    void workingPaperAuthorComesFromTheePrincipal() {
        String ctl = stripComments(read(WP_CONTROLLER));
        Matcher create = Pattern.compile(
            "createWorkingPaper\\(@RequestBody WorkingPaper body,\\s*\\n?\\s*Authentication auth\\)\\s*\\{(.*?)\\n    \\}",
            Pattern.DOTALL).matcher(ctl);
        assertThat(create.find())
            .as("createWorkingPaper must take the Authentication so it can stamp the author. It "
                + "used to save the body verbatim while the page passed an invented auditor's "
                + "name, so that name was PERSISTED as the author of an IIA 2330 working paper "
                + "— and the reviewer stamped by the very next transition was correct.")
            .isTrue();
        assertThat(create.group(1))
            .as("the handler must overwrite whatever author the body carried")
            .containsPattern("body\\.setAuthor\\(actor\\(auth\\)\\)");
    }

    @Test
    @DisplayName("ALL FOUR frontend machines match their server counterparts, edge for edge")
    void everyRegisterMachineMatchesTheServer() {
        for (Register r : REGISTERS) {
            Map<String, Set<String>> server = serverMachine(r.owner(), r.serverField());
            Map<String, Set<String>> fe = frontendMachine(r.feTable());
            Set<String> states = new TreeSet<>(fe.keySet());
            states.addAll(server.keySet());
            for (String state : states) {
                Set<String> want = server.getOrDefault(state, Set.of());
                Set<String> got = fe.getOrDefault(state, Set.of());
                assertThat(new TreeSet<>(got))
                    .as("%s register, state %s: the frontend offers %s, %s.%s allows %s. A "
                        + "button the server refuses answers 409; one the frontend omits makes "
                        + "a legal transition unreachable.",
                        r.name(), state, got, r.owner().getSimpleName(), r.serverField(), want)
                    .isEqualTo(new TreeSet<>(want));
            }
        }
    }

    @Test
    @DisplayName("no register's unmanaged list names a status its server machine declares")
    void noRegisterDeclaresAnUnmanagedStatusTheServerManages() {
        // ⚠ The reverse direction for all four, and the one that rots silently: each screen
        // renders a "No declared transition" badge over these rows. Give one of them a server
        // edge and the badge becomes a false statement over a row the server would advance.
        for (Register r : REGISTERS) {
            Set<String> managed = serverMachine(r.owner(), r.serverField()).keySet();
            Set<String> unmanaged = frontendUnmanaged(r.feUnmanaged());
            for (String u : unmanaged) {
                assertThat(managed)
                    .as("%s: %s is declared unmanaged in lib/auditLifecycle.ts but %s.%s now "
                        + "has an entry for it — the screen tells an auditor this row cannot be "
                        + "advanced", r.name(), u, r.owner().getSimpleName(), r.serverField())
                    .doesNotContain(u);
            }
            // And the measured population must stay declared: a slice that "fixes" the
            // disclosure by emptying the list removes the disclosure, not the defect.
            assertThat(String.join(",", new TreeSet<>(unmanaged)))
                .as("%s: the statuses its real rows carry must stay declared until an owner "
                    + "decides how to reconcile them", r.name())
                .isEqualTo(String.join(",", new TreeSet<>(Set.of(r.expectedUnmanaged().split(",")))));
        }
    }

    @Test
    @DisplayName("the comment stripper handles both orders, pinned in both directions")
    void stripperIsPinnedBothWays() {
        String trap = "int a = 1; // see /* this note\nint b = 2;\n/* real block */\nint c = 3;";
        String out = stripComments(trap);
        assertThat(out).as("a /* inside a // comment must not open a block").contains("int b = 2;");
        assertThat(out).contains("int c = 3;");
        assertThat(out).as("a real block comment must still go").doesNotContain("real block");
        assertThat(stripComments("String u = \"https://x/y\"; // gone"))
            .as("a URL's // must not be treated as a comment").contains("https://x/y");
    }

    @Test
    @DisplayName("the parsers fail loudly rather than returning an empty population")
    void parsersCannotSilentlyFindNothing() {
        // 🔑 A rotted parser returns nothing, and nothing satisfies every "must not contain"
        // assertion in the file. F2-196's own gate matched a TypeScript type annotation and
        // returned an empty set, which read as "the frontend declares none".
        assertThat(frontendMachine()).hasSize(5);
        assertThat(frontendMachine().get("OPEN")).containsExactly("MGMT_RESPONSE");
        assertThat(frontendUnmanaged()).hasSize(3);
        assertThat(serverMachine()).hasSize(4);   // terminal CLOSED is absent by design
        // All four, so a rotted parse cannot pass by finding nothing for three of them.
        for (Register r : REGISTERS) {
            assertThat(frontendMachine(r.feTable()))
                .as("%s: the %s parse found no edges", r.name(), r.feTable()).isNotEmpty();
            assertThat(frontendUnmanaged(r.feUnmanaged()))
                .as("%s: the %s parse found no statuses", r.name(), r.feUnmanaged()).isNotEmpty();
            assertThat(serverMachine(r.owner(), r.serverField()))
                .as("%s: %s is empty", r.name(), r.serverField()).isNotEmpty();
        }
    }
}
