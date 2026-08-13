package com.tss.grc.security;

import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import jakarta.validation.constraints.Pattern;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>F2-208 — the CAPA lifecycle has one definition, and the client asks it.</b>
 *
 * <h3>What this catches</h3>
 * {@code lib/capa.ts} declared <b>eight</b> CAPA states — IDENTIFIED, IMPLEMENTING, OWNER_TESTED,
 * INDEPENDENT_TESTED, AUDIT_SIGNED_OFF, CLOSED, VERIFICATION_FAILED, CANCELLED — against
 * {@link CapaTaskService}'s <b>five</b>. Nothing compared them, and nothing needed to, because the
 * client never called {@code transition}: it wrote its own status into a {@code localStorage}
 * overlay that was applied on top of the server's rows when the register was rendered.
 *
 * <p>Three of those eight could not arrive from the server at all — the projection function had no
 * source status that mapped to them — so they were producible only by a local write. 🔑 <b>A state
 * only the browser can reach is a state only the browser knows about</b>, and OWNER_TESTED /
 * AUDIT_SIGNED_OFF are not decoration: they are the owner test and the independent sign-off an ISO
 * 9001 §10.2 or IIA 2500 reviewer asks to see.
 *
 * <p>So this is the same gate {@code VulnerabilityMachineParityTest} is for F2-205's register, and
 * it is written the same way on purpose: reflect the authority, parse the client, compare in both
 * directions, and check the request DTO's {@code @Pattern} agrees with both.
 *
 * <h3>Why the parity is bidirectional and exact</h3>
 * A one-way check ("every state the client names exists on the server") would have passed the
 * five-state client this slice wrote while missing a sixth state the server gained later. A
 * count-based check would pass a client that renamed one. The comparison is edge-for-edge on the
 * whole table.
 */
class CapaMachineParityTest {

    private static final Path LIB = Path.of("..", "tss-grc-ui-v2", "src", "lib", "capa.ts");

    // ── the authority ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> serverTable() {
        try {
            Field f = CapaTaskService.class.getDeclaredField("ALLOWED");
            f.setAccessible(true);
            return (Map<String, Set<String>>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("CapaTaskService.ALLOWED must exist and be static", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> serverTerminal() {
        try {
            Field f = CapaTaskService.class.getDeclaredField("TERMINAL");
            f.setAccessible(true);
            return (List<String>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("CapaTaskService.TERMINAL must exist and be static", e);
        }
    }

    // ── the client ────────────────────────────────────────────────────────────

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p.toAbsolutePath(), e);
        }
    }

    /**
     * ⚠ Comments first, and the line-comment branch leads the alternation (F2-198). This file's
     * client counterpart documents the eight states it deleted, by name, in its header — a
     * comment-blind parse would read the epitaph as the corpse and report a client that still
     * declares OWNER_TESTED. Comment-blind here OVER-reports, so it fails loudly; the ordering bug
     * F2-198 recorded under-reports, which is the dangerous direction.
     */
    private static String stripComments(String s) {
        return s.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", "");
    }

    /** The body of an object literal assigned to {@code name}. */
    private static String objectBody(String src, String name) {
        int at = src.indexOf(name);
        assertThat(at).as("%s not found in %s", name, LIB).isGreaterThanOrEqualTo(0);
        int open = src.indexOf('{', at);
        assertThat(open).as("%s is not an object literal", name).isGreaterThanOrEqualTo(0);
        int depth = 1, i = open + 1;
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }
        return src.substring(open + 1, i - 1);
    }

    private static Map<String, Set<String>> clientTable() {
        String body = objectBody(stripComments(read(LIB)), "CAPA_ALLOWED");
        Map<String, Set<String>> out = new LinkedHashMap<>();
        Matcher m = java.util.regex.Pattern
            .compile("([A-Z][A-Z0-9_]*)\\s*:\\s*\\[([^\\]]*)\\]").matcher(body);
        while (m.find()) {
            Set<String> targets = new LinkedHashSet<>();
            Matcher t = java.util.regex.Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(m.group(2));
            while (t.find()) targets.add(t.group(1));
            out.put(m.group(1), targets);
        }
        assertThat(out).as("CAPA_ALLOWED parsed to nothing — the parser, not the table, is broken")
            .isNotEmpty();
        return out;
    }

    /** The `CapaStatus` union's members. */
    private static Set<String> clientUnion() {
        String src = stripComments(read(LIB));
        Matcher m = java.util.regex.Pattern
            .compile("export type CapaStatus\\s*=([^;]+);").matcher(src);
        assertThat(m.find()).as("export type CapaStatus not found in %s", LIB).isTrue();
        Set<String> out = new TreeSet<>();
        Matcher t = java.util.regex.Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(m.group(1));
        while (t.find()) out.add(t.group(1));
        assertThat(out).as("the CapaStatus union parsed to nothing").isNotEmpty();
        return out;
    }

    // ── the parity ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CAPA_ALLOWED mirrors CapaTaskService.ALLOWED edge for edge, both ways")
    void clientTableMirrorsTheServerTable() {
        Map<String, Set<String>> server = serverTable();
        Map<String, Set<String>> client = clientTable();

        assertThat(new TreeSet<>(client.keySet()))
            .as("the client's state set differs from the server's. A state the server cannot store "
                + "is reachable only by a local write, which is exactly what F2-208 removed — "
                + "lib/capa.ts declared 8 against these 5.")
            .isEqualTo(new TreeSet<>(server.keySet()));

        for (String state : server.keySet()) {
            assertThat(new TreeSet<>(client.get(state)))
                .as("edges out of %s differ; the server answers 409 on anything it does not list", state)
                .isEqualTo(new TreeSet<>(server.get(state)));
        }
    }

    @Test
    @DisplayName("the CapaStatus union is exactly the machine's state set")
    void theUnionIsTheStateSet() {
        assertThat(clientUnion())
            .as("the union and the transition table must not drift from each other either — the "
                + "union is what every label map, filter and lane list on the CAPA surface is keyed "
                + "on, so a member the table does not have is a lane nothing can ever enter")
            .isEqualTo(new TreeSet<>(serverTable().keySet()));
    }

    @Test
    @DisplayName("the transition request's @Pattern admits exactly the machine's states")
    void theRequestPatternAdmitsExactlyTheMachine() throws Exception {
        Field f = CapaTaskDto.TransitionRequest.class.getDeclaredField("status");
        Pattern p = f.getAnnotation(Pattern.class);
        assertThat(p)
            .as("TransitionRequest.status must carry a @Pattern — it is the outermost gate, and "
                + "capa.tasks.status has NO CHECK constraint behind it (the whole capa schema has "
                + "none), so the annotation is the only thing narrowing the column")
            .isNotNull();

        Set<String> admitted = new TreeSet<>(List.of(p.regexp().split("\\|")));
        assertThat(admitted)
            .as("the @Pattern and the machine disagree: %s vs %s", admitted, serverTable().keySet())
            .isEqualTo(new TreeSet<>(serverTable().keySet()));
    }

    @Test
    @DisplayName("TERMINAL is derived from the table, not written out a second time")
    void terminalIsDerived() {
        Map<String, Set<String>> server = serverTable();
        Set<String> expected = new TreeSet<>();
        server.forEach((state, edges) -> { if (edges.isEmpty()) expected.add(state); });

        assertThat(new TreeSet<>(serverTerminal()))
            .as("TERMINAL must equal the states with no outgoing edge. It feeds the `open` and "
                + "`overdue` queries, so a hand-written copy that drifts would quietly drop a state "
                + "from the overdue sweep — F2-196's defect, on the CAPA clock.")
            .isEqualTo(expected);

        // The structural half: the derivation must be a derivation. A literal list that happens to
        // agree today passes the assertion above, which is how the previous version survived.
        String src = read(Path.of("src", "main", "java", "com", "tss", "grc", "module", "capa",
            "service", "CapaTaskService.java"));
        Matcher m = java.util.regex.Pattern
            .compile("TERMINAL\\s*=([\\s\\S]{0,400}?);").matcher(src);
        assertThat(m.find()).as("TERMINAL's initialiser not found").isTrue();
        assertThat(m.group(1))
            .as("TERMINAL must be computed FROM ALLOWED. Scoped to its own initialiser rather than "
                + "banning a literal anywhere in the file, because F2-205 recorded a mutation that "
                + "survived exactly that mistake — a ban on the string it had just deleted.")
            .contains("ALLOWED")
            .doesNotContain("\"CLOSED\"");
    }

    @Test
    @DisplayName("the client's terminal set is derived too, and its open predicate is a complement")
    void clientTerminalIsDerivedAndOpenIsAComplement() {
        String src = stripComments(read(LIB));

        Matcher term = java.util.regex.Pattern
            .compile("CAPA_TERMINAL[^=]*=([\\s\\S]{0,300}?);").matcher(src);
        assertThat(term.find()).as("CAPA_TERMINAL not found in %s", LIB).isTrue();
        assertThat(term.group(1))
            .as("CAPA_TERMINAL must be filtered out of CAPA_ALLOWED, not listed")
            .contains("CAPA_ALLOWED")
            .doesNotContain("'CLOSED'");

        Matcher open = java.util.regex.Pattern
            .compile("function isCapaOpen[^{]*\\{([\\s\\S]{0,300}?)\\n\\}").matcher(src);
        assertThat(open.find()).as("isCapaOpen not found in %s", LIB).isTrue();
        assertThat(open.group(1))
            .as("\"still open\" must be the COMPLEMENT of the terminal set, never a second "
                + "inclusion list. F2-196: an inclusion list over an open vocabulary silently drops "
                + "whatever nobody remembered, and on this register that is a CAPA the overdue "
                + "count never mentions.")
            .contains("CAPA_TERMINAL")
            .doesNotContain("=== 'CLOSED'");
    }
}
