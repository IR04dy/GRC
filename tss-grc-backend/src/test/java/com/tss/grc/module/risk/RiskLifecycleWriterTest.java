package com.tss.grc.module.risk;

import com.tss.grc.module.risk.service.RiskAcceptanceService;
import com.tss.grc.module.risk.service.RiskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-164 — <b>{@code risk.status = 'ACCEPTED'} has exactly one writer, and it is the
 * one that checked an acceptance record.</b>
 *
 * <p>ERM's headline control is: a HIGH/VERY_HIGH/CRITICAL or over-appetite risk may
 * only be CLOSED once it carries a live treatment plan — <i>unless</i> it carries a
 * formal acceptance instead. The exemption used to be spelled {@code status ==
 * "ACCEPTED"}, and {@code POST /risks/{id}/accept} set that status at
 * {@code RISK:WRITE} with no justification, no compensating controls, no expiry and
 * no sign-off. So the control had a one-click bypass at the weaker of its two
 * permission tiers, and the bypass was <b>permanent</b>: {@code expireSweep} reopens
 * risks by iterating acceptance <i>records</i>, and the bare flip created none.
 *
 * <h3>⚠ Why a source scan and not a unit test</h3>
 * The defect was never a wrong value — every unit test passed, including one that
 * asserted the exemption. It was a <i>second door to the same outcome</i>, and a
 * test of either door in isolation sees nothing wrong. What has to be pinned is the
 * shape of the module: only {@link com.tss.grc.module.risk.service.RiskAcceptanceService}
 * may reach the writer, and no controller may route to it.
 *
 * <p>🔑 The reusable part: <b>the artefact recording this design already existed and
 * was inert.</b> {@code RiskAcceptance}'s javadoc has said E-A <i>"Replaces the bare
 * RiskService.accept() status flip"</i> since the day it was written — a statement of
 * intent with nothing enforcing it, so the replacement landed and the replaced code
 * stayed. Same class as {@code AttachmentAccessPolicy}'s <i>"It has to move WITH the
 * parent, not later"</i> (F2-52), which two slices then ignored. A comment is not a
 * control.
 */
class RiskLifecycleWriterTest {

    private static final Path RISK_MODULE =
        Paths.get("src/main/java/com/tss/grc/module/risk");

    /** The single sanctioned writer, and the single sanctioned caller of it. */
    private static final String WRITER  = "acceptOnApprovedAcceptance";
    private static final String CALLER  = "RiskAcceptanceService.java";

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Block and line comments out, so a status named in prose is not read as code. */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\r\\n]*", "");
    }

    @Test
    @DisplayName("no controller in module/risk exposes a route that sets ACCEPTED directly")
    void noControllerRouteToAccepted() throws IOException {
        // The retired endpoint, and anything shaped like it. A mapping whose path
        // ends in /accept on a risk resource is the exact door F2-164 closed.
        Pattern mapping = Pattern.compile(
            "@(?:Post|Put|Patch)Mapping\\(\\s*\"([^\"]*accept[^\"]*)\"", Pattern.CASE_INSENSITIVE);

        List<String> offenders = new ArrayList<>();
        for (Path p : javaFiles(RISK_MODULE.resolve("controller"))) {
            String src = read(p);
            Matcher m = mapping.matcher(src);
            while (m.find()) {
                String path = m.group(1);
                // `/{riskId}/acceptances` and `/acceptances/{id}/...` are the
                // GOVERNED surface — they create and approve the record. What must
                // not exist is a bare transition, i.e. a path ending in /accept.
                if (path.toLowerCase().endsWith("/accept")) {
                    offenders.add(p.getFileName() + " → " + path);
                }
            }
        }
        assertThat(offenders)
            .as("A risk may only become ACCEPTED by approving an acceptance record "
                + "(POST /risks/{riskId}/acceptances then POST /acceptances/{id}/approve, "
                + "the second at RISK:APPROVE). A bare `/accept` transition re-opens F2-164: "
                + "it exempts the risk from the mandatory-treatment gate, at RISK:WRITE, "
                + "with no record for the expiry sweep to ever reopen. Offenders: %s", offenders)
            .isEmpty();
    }

    @Test
    @DisplayName("acceptOnApprovedAcceptance is called only by RiskAcceptanceService")
    void onlyTheAcceptanceServiceMayFlipARiskToAccepted() throws IOException {
        List<String> callers = new ArrayList<>();
        for (Path p : javaFiles(RISK_MODULE)) {
            String name = p.getFileName().toString();
            if (name.equals("RiskService.java")) continue;      // the declaration itself
            if (read(p).contains(WRITER)) callers.add(name);
        }
        assertThat(callers)
            .as("Only %s may flip a risk to ACCEPTED — it is the method that has verified "
                + "justification, compensating controls, a future expiry, CRO sign-off and "
                + "(above appetite) Board sign-off. Any other caller is reaching the outcome "
                + "without the checks. Callers found: %s", CALLER, callers)
            .containsExactly(CALLER);
    }

    @Test
    @DisplayName("the close gate's exemption reads the acceptance record, not the status string")
    void closeGateExemptionIsBackedByARecord() throws IOException {
        String src = read(RISK_MODULE.resolve("service/RiskService.java"));

        // Isolate close(); a stale `"ACCEPTED".equals(...)` anywhere else (require(),
        // the state-machine comment) is fine and must not trip this.
        int from = src.indexOf("public RiskResponse close(Long id)");
        assertThat(from).as("close(Long id) not found — this test is measuring nothing").isGreaterThan(0);
        int to = src.indexOf("private boolean hasLiveFormalAcceptance", from);
        assertThat(to).as("hasLiveFormalAcceptance not found after close() — the exemption "
            + "helper was renamed or removed, so this test can no longer see the gate").isGreaterThan(from);
        String closeBody = src.substring(from, to);

        assertThat(closeBody)
            .as("The exemption must consult the acceptance RECORD. `status == 'ACCEPTED'` is a "
                + "projection of that record and can exist without it — that asymmetry IS F2-164.")
            .contains("hasLiveFormalAcceptance(r)")
            .doesNotContain("\"ACCEPTED\".equals(r.getStatus())");

        assertThat(read(RISK_MODULE.resolve("repository/RiskAcceptanceRepository.java")))
            .as("The query has to bound the time-box as well as the status. An APPROVED "
                + "acceptance past its expiry must not exempt anything just because the "
                + "expiry sweep has not run yet — the gate fails closed.")
            .contains("existsByOrganisationIdAndRiskIdAndStatusAndExpiresAtGreaterThanEqual");
    }

    @Test
    @DisplayName("an acceptance that may be RAISED must be one that can be APPROVED")
    void raisableAndApprovableAreTheSameSet() throws IOException {
        // 🔑 The live probe caught this; no unit test would have. The retired `accept()`
        // allowed only {OPEN, UNDER_REVIEW}, while the governed path's flip was a bare
        // save() with NO state guard — so approving an acceptance for a MITIGATING risk
        // worked. Copying the old allowed-from set onto the new writer therefore answered
        // 400 "Risk must be in [OPEN, UNDER_REVIEW]" for a request `request()` had just
        // created: a governed path you can enter and cannot finish, which is precisely the
        // defect class F2-164 exists to remove. Two predicates for one rule is the shape to
        // distrust — so there is now one, and this test is what keeps it that way.
        assertThat(RiskService.RETAINABLE_STATUSES)
            .as("a terminal status is a reopen decision (E-J), not an acceptance")
            .doesNotContain("CLOSED", "ARCHIVED")
            .as("MITIGATING must be retainable — stopping treatment to retain the risk is a "
                + "real decision, and `request()` has always allowed raising one there")
            .contains("MITIGATING", "OPEN", "UNDER_REVIEW");

        String acc = read(RISK_MODULE.resolve("service/RiskAcceptanceService.java"));
        assertThat(acc)
            .as("`request()` and the writer must consult the SAME constant. A second status "
                + "list in this file is how the two predicates drift back apart.")
            .contains("RiskService.RETAINABLE_STATUSES");
        // ⚠ Scoped to the two PRECONDITIONS, not the whole file. `revoke` and
        // `expireSweep` legitimately name "UNDER_REVIEW" as the reopen TARGET, and a
        // whole-file `doesNotContain` flagged them — the same too-wide-pattern mistake as
        // the FE's `.status ===` scan in this slice, twice in one change.
        String stripped = stripComments(acc);
        for (String method : new String[]{"public RiskAcceptance request(", "public RiskAcceptance approve("}) {
            int at = stripped.indexOf(method);
            assertThat(at).as("%s not found — this check is measuring nothing", method).isGreaterThan(0);
            int next = stripped.indexOf("@Transactional", at + 1);
            String body = stripped.substring(at, next > at ? next : stripped.length());
            assertThat(body)
                .as("%s must gate on RiskService.RETAINABLE_STATUSES, never on its own "
                    + "status literals — a second list is how the two predicates drift apart "
                    + "and re-create the un-approvable acceptance", method)
                .doesNotContain("\"UNDER_REVIEW\"")
                .doesNotContain("\"MITIGATING\"")
                .doesNotContain("\"OPEN\"");
        }
    }

    @Test
    @DisplayName("the governed flip is audited and emits, so V132's SAMA §10.4 rule still fires")
    void theGovernedFlipGoesThroughTheAuditedTransition() throws IOException {
        String src = read(RISK_MODULE.resolve("service/RiskAcceptanceService.java"));

        // ⚠ This is the trap that made "just delete the bypass" wrong. V132 seeds an
        // F8 rule on RISK_TRANSITIONED with {"status":"ACCEPTED","previousStatus":
        // "OPEN"} — the SAMA §10.4 flag for accepting without formal review. The
        // governed approve() used to set the status with a bare save(), emitting no
        // event and writing no TRANSITION audit row, so that detective control could
        // ONLY be triggered by the ungoverned endpoint it exists to flag. Retiring
        // the endpoint without routing approve() through doTransition would have
        // silenced the rule while looking like a pure security improvement.
        int from = src.indexOf("public RiskAcceptance approve(");
        assertThat(from).as("approve( not found").isGreaterThan(0);
        int to = src.indexOf("public RiskAcceptance reject(", from);
        String approveBody = src.substring(from, to > from ? to : src.length());

        assertThat(approveBody)
            .as("approve() must flip the risk through RiskService's audited, "
                + "event-emitting transition — not with a bare status write.")
            .contains("riskService." + WRITER)
            .doesNotContain("setRiskStatus(saved.getRiskId(), \"ACCEPTED\")");

        // …and the sweep must NOT use it: expireSweep runs off-request across all
        // tenants, where TenantContext is unset and mustGet()'s tenant filter throws.
        int sweep = src.indexOf("public int expireSweep()");
        assertThat(sweep).as("expireSweep() not found").isGreaterThan(0);
        assertThat(src.substring(sweep))
            .as("expireSweep is off-request (scheduler, all tenants) — routing it through "
                + "the tenant-scoped transition is the off-request audit landmine, not a fix.")
            .doesNotContain(WRITER);
    }
}
