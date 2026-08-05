package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.dto.UnauthorisedDispositionRequest;
import com.tss.grc.module.change_mgmt.entity.UnauthorisedChangeDisposition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S8f / CE-11 — the unauthorised-change reconciliation.
 *
 * <p>What these tests are for: the register's value rests entirely on <b>which failures it calls
 * unauthorised</b>. Get that classification wrong in the generous direction and it reports a clean
 * platform that isn't; wrong in the strict direction and it cries wolf until nobody reads it. Both
 * failures were live possibilities on the seeded data, so the classification is pinned here rather
 * than left to the service to decide consistently.
 */
class UnauthorisedChangeRegisterTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/sqlserver");

    private static String v355() {
        try (Stream<Path> walk = Files.walk(MIGRATIONS)) {
            return walk.filter(p -> p.getFileName().toString().startsWith("V355__"))
                       .findFirst()
                       .map(p -> {
                           try {
                               return Files.readString(p, StandardCharsets.UTF_8);
                           } catch (IOException e) {
                               throw new UncheckedIOException(e);
                           }
                       })
                       .orElseThrow(() -> new AssertionError(
                           "V355 is missing — dispositions have no table"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static UnauthorisedChangeFinding finding(String reason, String severity) {
        return new UnauthorisedChangeFinding(7L, "CHG-2025-0007", "t", "ت", "NORMAL", "MEDIUM",
            "SCHEDULED", reason, severity, false, "CAB", 1, 0, true, "NOT_REQUIRED",
            null, null, null, null);
    }

    // ── the classification that is the whole slice ───────────────────────────────────────────────

    @Test
    @DisplayName("an unattributed-but-approved change is NOT an authority gap")
    void unattributedIsNotAnAuthorityGap() {
        // ⚠ The measurement that forced this: six of dev's eleven findings are this code. The votes
        // satisfy the cell in full and only cab_decision_at is missing (F2-103's legacy). Counting
        // them as unauthorised would report eight unauthorised changes where three is the truth.
        assertThat(finding(UnauthorisedChangeFinding.UNATTRIBUTED_AUTHORISATION,
                           UnauthorisedChangeFinding.MEDIUM).isAuthorityGap())
            .as("authority was present and recorded; only the platform's own stamp is missing. "
              + "Reporting that as an unauthorised change overstates a compliance breach.")
            .isFalse();
    }

    @Test
    @DisplayName("every other reason IS an authority gap — none quietly excluded")
    void everyOtherReasonIsAnAuthorityGap() {
        for (String reason : UnauthorisedChangeFinding.REASON_CODES) {
            boolean expected = !UnauthorisedChangeFinding.UNATTRIBUTED_AUTHORISATION.equals(reason);
            assertThat(finding(reason, UnauthorisedChangeFinding.HIGH).isAuthorityGap())
                .as("%s — a reason excluded from the authority-gap set is a reason the KPI stops "
                  + "counting, which is how a register goes quiet without anyone deciding it should",
                    reason)
                .isEqualTo(expected);
        }
        assertThat(UnauthorisedChangeFinding.AUTHORITY_GAP_REASONS)
            .hasSize(UnauthorisedChangeFinding.REASON_CODES.size() - 1);
    }

    @Test
    @DisplayName("a finding is keyed by change AND reason, so two failures dispose separately")
    void findingKeyDistinguishesReasons() {
        String a = finding(UnauthorisedChangeFinding.INSUFFICIENT_APPROVALS,
                           UnauthorisedChangeFinding.HIGH).key();
        String b = finding(UnauthorisedChangeFinding.EXPIRED_MODEL_APPLIED,
                           UnauthorisedChangeFinding.HIGH).key();
        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo("7:" + UnauthorisedChangeFinding.INSUFFICIENT_APPROVALS);
    }

    // ── vocabularies stay closed, and agree with the schema ──────────────────────────────────────

    @Test
    @DisplayName("reason codes and severities are unique and published")
    void vocabulariesAreClosed() {
        assertThat(new HashSet<>(UnauthorisedChangeFinding.REASON_CODES))
            .as("a duplicate reason code would collapse two different governance failures into one")
            .hasSameSizeAs(UnauthorisedChangeFinding.REASON_CODES);
        assertThat(UnauthorisedChangeFinding.SEVERITIES).containsExactly("HIGH", "MEDIUM");
        assertThat(UnauthorisedChangeFinding.REASON_CODES).hasSize(6);
    }

    @Test
    @DisplayName("Java's disposition states are exactly the states ck_ucd_state permits")
    void javaAndSqlVocabulariesAgree() {
        // Two copies of one vocabulary drift. This is the test that notices — the same invariant
        // ChangeModelCatalogueTest holds over V354.
        String sql = v355();
        for (String state : UnauthorisedChangeDisposition.STATES)
            assertThat(sql)
                .as("V355's ck_ucd_state must permit %s, or the service can build a row the "
                  + "database refuses — a 500 on a governance sign-off", state)
                .contains("'" + state + "'");

        int open = sql.indexOf("CONSTRAINT ck_ucd_state CHECK (state IN");
        assertThat(open).as("ck_ucd_state should be a literal IN list this test can read")
            .isGreaterThan(-1);
        String inList = sql.substring(open, sql.indexOf("))", open));
        long quoted = inList.chars().filter(c -> c == '\'').count() / 2;
        assertThat(quoted)
            .as("ck_ucd_state permits %d states while Java publishes %d — the extra one is "
              + "reachable by a direct UPDATE and nothing in the UI can render it",
                quoted, UnauthorisedChangeDisposition.STATES.size())
            .isEqualTo(UnauthorisedChangeDisposition.STATES.size());
    }

    @Test
    @DisplayName("V355 proves its CHECKs reject, rather than asserting they exist")
    void migrationProvesTheChecksEnforce() {
        // Reading sys.check_constraints proves a NAME. is_disabled/is_not_trusted proves it is armed.
        // Only an attempted insert proves it fires — and a check that cannot fail is not a check.
        String sql = v355();
        assertThat(sql).contains("BEGIN TRANSACTION").contains("ROLLBACK TRANSACTION");
        assertThat(sql)
            .as("V355 must attempt a blank-signer insert and THROW if it is accepted")
            .contains("@rejectedBlank");
        assertThat(sql)
            .as("V355 must attempt an invented-state insert and THROW if it is accepted")
            .contains("@rejectedState");
        assertThat(sql).contains("is_disabled = 1 OR is_not_trusted = 1");
        // And it must clean up after itself: a surviving self-check row is a seeded sign-off.
        assertThat(sql).contains("a self-check row survived");
    }

    // ── the DTO is what makes the CHECK sufficient ───────────────────────────────────────────────

    @Test
    @DisplayName("the disposition DTO carries no signer and no timestamp")
    void dispositionDtoCarriesNoAuthority() {
        List<String> fields = Stream.of(UnauthorisedDispositionRequest.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        assertThat(fields)
            .as("ck_ucd_disposed only checks that the signer fields are POPULATED, not who populated "
              + "them. A body-supplied name would let one person record another's sign-off and the "
              + "constraint would pass it — F2-24's shape, and the reason ChangeModelRequest carries "
              + "no authorised* fields either.")
            .doesNotContain("disposedById", "disposedByName", "disposedAt",
                            "organisationId", "changeRequestId", "reasonCode", "id", "isDeleted");
        assertThat(fields).containsExactly("state", "note");
    }

    // ── the population definition ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every EFFECTIVE status is also PAST_AUTHORISATION")
    void effectiveIsASubsetOfPastAuthorisation() {
        // A change cannot have taken effect without having passed the point where authorisation was
        // due. If these sets ever diverge, a live change becomes invisible to the reconciliation.
        assertThat(UnauthorisedChangeRegisterService.PAST_AUTHORISATION)
            .containsAll(UnauthorisedChangeRegisterService.EFFECTIVE);
        assertThat(UnauthorisedChangeRegisterService.EFFECTIVE)
            .as("anti-vacuity")
            .isNotEmpty()
            .hasSizeLessThan(UnauthorisedChangeRegisterService.PAST_AUTHORISATION.size());
    }

    @Test
    @DisplayName("the population statuses are real states of the change machine")
    void populationStatusesAreRealStates() {
        // The status set is prose until something checks it against the state machine. A typo here
        // silently shrinks the population and the register reports fewer findings than exist.
        java.util.Set<String> known = statesFromTheMachine();
        assertThat(known).as("anti-vacuity — the reflection found no states").hasSizeGreaterThan(10);

        for (String s : UnauthorisedChangeRegisterService.PAST_AUTHORISATION)
            assertThat(known)
                .as("%s is in the reconciliation's population but is not a state the change machine "
                  + "can reach, so it matches no row and narrows the register silently", s)
                .contains(s);
    }

    /** Every state the machine can be in or reach, read from the running class rather than a copy. */
    @SuppressWarnings("unchecked")
    private static java.util.Set<String> statesFromTheMachine() {
        try {
            java.lang.reflect.Field f =
                ChangeManagementService.class.getDeclaredField("NORMAL_TRANSITIONS");
            f.setAccessible(true);
            java.util.Map<String, java.util.Set<String>> transitions =
                (java.util.Map<String, java.util.Set<String>>) f.get(null);
            java.util.Set<String> all = new HashSet<>(transitions.keySet());
            transitions.values().forEach(all::addAll);
            return all;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                "ChangeManagementService.NORMAL_TRANSITIONS is gone or renamed — this test reads the "
              + "running state machine on purpose, so fix the reference rather than the assertion", e);
        }
    }

    // ── the second authorisation route ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("the port can be asked whether the COMMITTEE authorised a change, without throwing")
    void committeeRouteIsQueryable() {
        // ⚠ This test exists because the first draft of the register read change_approvals only.
        // CAB_APPROVED has two routes, and the committee route — the one S6 and S7 built, and the
        // system of record for a referred change — writes no vote rows in this module. Counting votes
        // alone would have reported every committee-routed change as unauthorised.
        //
        // The register therefore needs a NON-THROWING form of assertDecisionApproves. If that method
        // ever disappears from the port, the register silently loses sight of the second route and
        // starts inventing findings against the platform's preferred path, so its presence is pinned
        // here rather than assumed.
        assertThatCode(() -> com.tss.grc.module.change_mgmt.spi.CommitteePort.class
                .getMethod("decisionApproves", String.class, Long.class, int.class))
            .as("CommitteePort.decisionApproves is the register's only way to see committee authority "
              + "without catching an exception per change. Its throwing sibling stays for the write "
              + "path, which wants the message.")
            .doesNotThrowAnyException();

        assertThatCode(() -> com.tss.grc.module.change_mgmt.spi.CommitteePort.class
                .getMethod("assertDecisionApproves", String.class, Long.class, int.class))
            .as("and the throwing form must survive — ChangeManagementService's CAB_APPROVED gate "
              + "needs the reason, not a boolean")
            .doesNotThrowAnyException();
    }

    // ── blind spots are declared ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the register publishes what it cannot detect")
    void undetectableClassesArePublished() {
        // A register that silently covers eight of ten failure modes reads exactly like one that
        // covers ten. This is S8b's NOT_MEASURABLE argument applied to a detector rather than a metric.
        assertThat(UnauthorisedChangeRegisterService.UNDETECTABLE)
            .as("if this list is ever emptied, the register starts claiming total coverage — and the "
              + "drift feed it would need still does not exist (measured: 0 CMDB tables, 0 baseline "
              + "tables, 0 tables in the integrations schema)")
            .contains("UNTICKETED_PRODUCTION_CHANGE", "IMPLEMENTED_DURING_FREEZE");
        assertThat(UnauthorisedChangeRegisterService.SCOPE)
            .as("the scope must name what is reconciled, so a consumer can tell this register from "
              + "A17-D3's EMERGENCY_RATIFICATION_ONLY sink without reading the plan")
            .isEqualTo("RECORDED_CHANGES_VS_AUTHORISATION_MATRIX");
    }

    // ── dispositions annotate, never erase ───────────────────────────────────────────────────────

    @Test
    @DisplayName("attaching a disposition leaves the derived facts untouched")
    void dispositionDoesNotRewriteTheFinding() {
        UnauthorisedChangeFinding open =
            finding(UnauthorisedChangeFinding.INSUFFICIENT_APPROVALS, UnauthorisedChangeFinding.HIGH);
        assertThat(open.isOpen()).isTrue();

        UnauthorisedChangeDisposition d = new UnauthorisedChangeDisposition();
        d.setState(UnauthorisedChangeDisposition.ACCEPTED_RISK);
        d.setDisposedByName("so_officer");
        d.setDisposedAt(LocalDateTime.now());
        d.setNote("Carried to the December board.");

        UnauthorisedChangeFinding disposed = open.withDisposition(d);
        assertThat(disposed.isOpen()).isFalse();
        assertThat(disposed.dispositionState()).isEqualTo(UnauthorisedChangeDisposition.ACCEPTED_RISK);
        assertThat(disposed.isAuthorityGap())
            .as("⚠ a disposed gap is still a gap. If signing off removed it from the count, an "
              + "operator could reach a clean dashboard without fixing anything.")
            .isTrue();
        // Every derived field survives: the disposition is an annotation, not an edit.
        assertThat(disposed.reasonCode()).isEqualTo(open.reasonCode());
        assertThat(disposed.severity()).isEqualTo(open.severity());
        assertThat(disposed.approvalsRecorded()).isEqualTo(open.approvalsRecorded());
        assertThat(disposed.minApprovalsRequired()).isEqualTo(open.minApprovalsRequired());
        assertThat(disposed.key()).isEqualTo(open.key());
    }

    @Test
    @DisplayName("there is no state that closes a finding")
    void noDispositionStateMeansResolved() {
        // Deliberate: "somebody signed this off" and "this never happened" are different facts, and a
        // register that merges them is worth nothing to an auditor.
        assertThat(UnauthorisedChangeDisposition.STATES)
            .doesNotContain("CLOSED", "RESOLVED", "DISMISSED", "IGNORED")
            .containsExactly("ACCEPTED_RISK", "RETROSPECTIVELY_AUTHORISED", "REVERSED",
                             "FALSE_POSITIVE");
    }
}
