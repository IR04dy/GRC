package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.entity.AmlAlert;
import com.tss.grc.module.ethics.repository.AmlAlertRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-289 — an AML alert's decision tuple: what was decided, by whom, and when.
 *
 * <h2>The defect</h2>
 *
 * <p>{@code AmlAlertService#transition} stamped {@code decidedByName} and {@code decidedAt} in
 * <b>two of its three</b> decision branches. {@code ESCALATED} — which records a
 * {@code TRUE_POSITIVE} disposition, the analyst's conclusion that a monitored transaction is
 * genuinely suspicious and the referral an MLRO acts on under SAMA AML/CFT and FATF R.20 — stamped
 * neither. {@code ESCALATED → CLOSED} is a declared edge and the {@code CLOSED} branch keeps
 * decision metadata as-is, so an alert could be escalated as a confirmed true positive and closed
 * with no decider recorded anywhere, permanently.
 *
 * <p>🔑🔑 The five-line comment explaining the attribution was byte-identical in the two branches
 * that had it, and the gap sat between them. <b>Repetition reads as a rule</b> — F2-215's two-of-N,
 * sharpened. And F2-223 is the slice that added the attribution, to two of three branches: F2-287's
 * headline, that the slice which establishes a rule is the worst-placed slice to apply it to its own
 * leftovers.
 *
 * <p><b>Latent, and this says so.</b> 0 rows are {@code ESCALATED} and both rows carrying a
 * disposition also carry a decider, identical in dev and prod — but the Escalate control is offered
 * on every {@code INVESTIGATING} alert and three real alerts are in that state, so it is reachable
 * from the platform's own screen (F2-283: a fix whose live effect is nil is a latent-hazard closure
 * and reporting it as a discovery is the error this register keeps warning about).
 *
 * <p>⚙ The directories are REAL over their mocked repositories, never mocks of the directories
 * themselves — a mocked resolver answers nothing and every leg below would go green asserting
 * nothing (F2-237).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmlDispositionAttributionTest {

    private static final Long ORG = 1L;
    private static final Long ALERT = 900L;
    private static final String ACTOR = "compliance";

    @Mock AmlAlertRepository repo;
    @Mock UserRepository users;
    @Mock com.tss.grc.module.ethics.repository.KycSubjectRepository subjectRepo;

    AmlAlertService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        service = new AmlAlertService(repo, new TenantUserDirectory(users),
            new KycSubjectDirectory(subjectRepo));
        when(repo.save(any(AmlAlert.class))).thenAnswer(i -> i.getArgument(0));
        signIn(ACTOR);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String username) {
        GrcUserDetails u = new GrcUserDetails(3L, ORG, username, "x", true, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, "x", u.getAuthorities()));
    }

    /** An alert parked in the state from which every decision outcome is reachable. */
    private AmlAlert investigating() {
        AmlAlert a = new AmlAlert();
        a.setId(ALERT);
        a.setOrganisationId(ORG);
        a.setStatus("INVESTIGATING");
        when(repo.findById(ALERT)).thenReturn(Optional.of(a));
        return a;
    }

    private AmlAlert move(String target, Map<String, Object> body) {
        return service.transition(ALERT, target, body);
    }

    // ── the three decision outcomes, each stamping the full tuple ─────────────

    @Test
    @DisplayName("ESCALATED records the decider and the clock — the branch that recorded neither")
    void escalatedRecordsTheDeciderAndTheClock() {
        investigating();
        AmlAlert saved = move("ESCALATED", Map.of());

        assertThat(saved.getStatus()).isEqualTo("ESCALATED");
        assertThat(saved.getDisposition())
            .as("the branch's own default: escalation is a conclusion that the alert is genuine")
            .isEqualTo("TRUE_POSITIVE");
        assertThat(saved.getDecidedByName())
            .as("this is the whole finding — the disposition was recorded and the decider was not")
            .isEqualTo(ACTOR);
        assertThat(saved.getDecidedAt())
            .as("and a disposition with a decider but no clock would be F2-223's split from the "
              + "other side")
            .isNotNull();
    }

    @Test
    @DisplayName("CLEARED records the decider and the clock, and defaults to FALSE_POSITIVE")
    void clearedRecordsTheDeciderAndTheClock() {
        investigating();
        AmlAlert saved = move("CLEARED", Map.of());

        assertThat(saved.getStatus()).isEqualTo("CLEARED");
        assertThat(saved.getDisposition()).isEqualTo("FALSE_POSITIVE");
        assertThat(saved.getDecidedByName()).isEqualTo(ACTOR);
        assertThat(saved.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("STR_FILED records the decider, the clock, and the filing separately")
    void strFiledRecordsTheDeciderTheClockAndTheFiling() {
        investigating();
        AmlAlert saved = move("STR_FILED", Map.of("strReference", "SAFIU-2026-0001"));

        assertThat(saved.getStatus()).isEqualTo("STR_FILED");
        assertThat(saved.getDisposition()).isEqualTo("TRUE_POSITIVE");
        assertThat(saved.getStrFiled()).isTrue();
        assertThat(saved.getStrReference()).isEqualTo("SAFIU-2026-0001");
        assertThat(saved.getStrFiledAt())
            .as("the STR's own timestamp is separate, which is why unifying decidedAt on "
              + "latest-wins loses nothing")
            .isNotNull();
        assertThat(saved.getDecidedByName()).isEqualTo(ACTOR);
        assertThat(saved.getDecidedAt()).isNotNull();
    }

    // ── the multiplicity rule ─────────────────────────────────────────────────

    @Test
    @DisplayName("a second decision moves BOTH halves of the attribution, never one")
    void aSecondDecisionMovesBothHalves() {
        AmlAlert a = investigating();

        move("ESCALATED", Map.of());
        String firstDecider = a.getDecidedByName();
        var firstClock = a.getDecidedAt();
        assertThat(firstDecider).isEqualTo(ACTOR);

        // ⚠ A timestamp taken BETWEEN the two decisions is what makes the split visible here, and
        // this leg did not have one at first: asserting only `isAfterOrEqualTo(firstClock)` is
        // satisfied by a PRESERVED clock, so the mutation that re-adds
        // `if (getDecidedAt() == null)` was killed by the structural leg alone and survived this
        // one — a leg whose NAME claimed more than its assertion could see, which is the family
        // this register keeps catching (F2-159, F2-279). The sleep removes the only way the two
        // readings could tie.
        sleepMillis(2);
        var betweenDecisions = java.time.LocalDateTime.now();
        sleepMillis(2);

        signIn("mlro");
        AmlAlert saved = move("STR_FILED", Map.of("strReference", "SAFIU-2026-0002"));

        assertThat(saved.getDecidedByName())
            .as("ESCALATED -> STR_FILED is the only two-disposition path the machine allows, and "
              + "the disposition column itself is overwritten by the later decision — so the "
              + "attribution must describe the SAME decision the disposition records")
            .isEqualTo("mlro");
        assertThat(saved.getDecidedAt())
            .as("STR_FILED used to preserve an existing decidedAt while overwriting decidedByName "
              + "unconditionally, i.e. the name from decision 2 beside the clock from decision 1. "
              + "That was latent only because ESCALATED wrote no clock for it to preserve — and "
              + "fixing ESCALATED is what would have armed it. Compared against a reading taken "
              + "BETWEEN the two decisions, because a preserved clock satisfies any comparison "
              + "against the first one.")
            .isAfter(firstClock)
            .isAfterOrEqualTo(betweenDecisions);
    }

    private static void sleepMillis(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    @Test
    @DisplayName("CLOSED keeps the decision metadata, which is why the gap was permanent")
    void closedKeepsTheDecisionMetadata() {
        AmlAlert a = investigating();
        move("ESCALATED", Map.of());
        a.setStatus("ESCALATED");

        AmlAlert saved = move("CLOSED", Map.of());

        assertThat(saved.getStatus()).isEqualTo("CLOSED");
        assertThat(saved.getDisposition()).isEqualTo("TRUE_POSITIVE");
        assertThat(saved.getDecidedByName())
            .as("CLOSED deliberately keeps decision metadata as-is. Pre-fix that preserved an "
              + "ABSENCE: escalate and close, and the alert was shut as a confirmed true positive "
              + "with no decider, with no route left that could add one.")
            .isEqualTo(ACTOR);
    }

    // ── the vocabulary ────────────────────────────────────────────────────────

    @Test
    @DisplayName("an unrecognised disposition is refused, naming the field")
    void anUnrecognisedDispositionIsRefused() {
        investigating();
        assertThatThrownBy(() -> move("CLEARED", Map.of("disposition", "PROBABLY_FINE")))
            .as("ethics.aml_alerts had ZERO CHECK constraints, so before V393 this was stored "
              + "verbatim. An unrecognised AML disposition does not degrade gracefully — it is a "
              + "conclusion the register cannot report (F2-182's third case).")
            .hasMessageContaining("disposition");
    }

    @Test
    @DisplayName("a mis-cased disposition is normalised, not stored verbatim")
    void aMisCasedDispositionIsNormalised() {
        investigating();
        AmlAlert saved = move("ESCALATED", Map.of("disposition", "true_positive"));

        assertThat(saved.getDisposition())
            .as("the collation is Latin1_General_100_CI_AS_SC_UTF8, so the CHECK accepts "
              + "'true_positive' and every Java comparison would then disagree with the column "
              + "(F2-179). This is why the writer change and V393 are one commit.")
            .isEqualTo("TRUE_POSITIVE");
    }

    @Test
    @DisplayName("a caller may still choose the disposition within the vocabulary")
    void aCallerMayStillChooseWithinTheVocabulary() {
        investigating();
        AmlAlert saved = move("ESCALATED", Map.of("disposition", "FALSE_POSITIVE"));

        assertThat(saved.getDisposition())
            .as("the branch default is a fallback, not a forced value: escalating an alert the "
              + "analyst believes is a false positive — for a second opinion — is a real thing, and "
              + "whether it should be refused is a product question about AML practice, not a "
              + "side effect of an attribution fix (F2-183/F2-196). The boundary is pinned here so "
              + "the next reader has to decide it deliberately.")
            .isEqualTo("FALSE_POSITIVE");
    }

    // ── the attribution refuses rather than blanks ────────────────────────────

    @Test
    @DisplayName("with no authenticated principal the decision is refused, not recorded blank")
    void withNoPrincipalTheDecisionIsRefused() {
        investigating();
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> move("ESCALATED", Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .as("F2-236 — a governance record whose decider is \"\" reads as an attribution, so it "
              + "is worse than one that could not be written")
            .hasMessageContaining("AML disposition");
    }

    // ── severity, the column the KPI reads through an inclusion list ──────────

    @Test
    @DisplayName("an unrecognised severity is refused on the entity-bound PUT")
    void anUnrecognisedSeverityIsRefused() {
        investigating();
        AmlAlert patch = new AmlAlert();
        patch.setSeverity("SEVERE");

        assertThatThrownBy(() -> service.update(ALERT, patch))
            .as("analytics() buckets by an inclusion list of exactly four tokens, so a fifth value "
              + "makes the alert invisible to the KPI strip rather than merely odd (F2-197)")
            .hasMessageContaining("severity");
    }

    @Test
    @DisplayName("a mis-cased severity is normalised on the way in")
    void aMisCasedSeverityIsNormalised() {
        investigating();
        AmlAlert patch = new AmlAlert();
        patch.setSeverity("critical");

        assertThat(service.update(ALERT, patch).getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("alert_type stays open, deliberately")
    void alertTypeStaysOpen() {
        investigating();
        AmlAlert patch = new AmlAlert();
        patch.setAlertType("SOME_NEW_TM_RULE_FAMILY");

        assertThat(service.update(ALERT, patch).getAlertType())
            .as("10 distinct values are stored and no vocabulary for alert_type is declared "
              + "anywhere in either codebase, so constraining it would mean inventing one. "
              + "Asserted as a decision rather than left as an absence (F2-183).")
            .isEqualTo("SOME_NEW_TM_RULE_FAMILY");
    }
}
