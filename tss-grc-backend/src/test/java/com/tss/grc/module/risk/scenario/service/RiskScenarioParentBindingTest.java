package com.tss.grc.module.risk.scenario.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.scenario.dto.RiskScenarioDtos.CreateRequest;
import com.tss.grc.module.risk.scenario.entity.RiskScenario;
import com.tss.grc.module.risk.scenario.repository.RiskScenarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-245 — the parent risk is RESOLVED, and its identity is DERIVED from what resolves.
 *
 * <p>⚠ {@code RiskScenarioService} had <b>no unit test of any kind</b> before this slice, which
 * is part of the finding: the create wrote three caller-supplied claims about another register's
 * row and nothing anywhere compared them.
 *
 * <p>The measured consequence, dev and prod alike: 11 of 11 live scenarios store a
 * {@code risk_title_en} that is not the title of the risk their {@code risk_id} names, and every
 * {@code risk_code} is an {@code RSK-2026-NNN} value from a code space {@code risk.risks} does
 * not have — its only reference is a UUID {@code external_id}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskScenarioParentBindingTest {

    private static final Long ORG = 1L;
    private static final Long OTHER_ORG = 2L;

    @Mock private RiskScenarioRepository repo;
    @Mock private RiskRepository risks;

    private RiskScenarioService service;

    @BeforeEach
    void setUp() {
        service = new RiskScenarioService(repo, risks);
        TenantContext.setCurrentTenant(ORG);
        when(repo.save(any(RiskScenario.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * ⚠ {@code Risk} declares {@code private UUID externalId = UUID.randomUUID()} — a field
     * initialiser (F2-241's family) — so {@code new Risk()} always carries a reference. The first
     * draft of this helper only called the setter when the argument was non-null, so the "no
     * reference" fixture silently held a random UUID and the test asserting the null branch failed
     * for a reason that had nothing to do with the code. 🔑 <b>A fixture that cannot reach a branch
     * proves nothing about it</b> (F2-183: a branch that is a no-op everywhere you can measure is
     * untested code unless you build it something to do). Setting it unconditionally is what makes
     * the null case an actual test of the guard.
     */
    private static Risk risk(Long id, Long org, String externalId, String titleEn) {
        Risk r = new Risk();
        r.setId(id);
        r.setOrganisationId(org);
        r.setTitleEn(titleEn);
        r.setExternalId(externalId == null ? null : java.util.UUID.fromString(externalId));
        return r;
    }

    private static CreateRequest req(Long riskId) {
        return new CreateRequest(
                riskId,
                "Aramco contract delay — Monte Carlo (base case)", "تأخر تجديد عقد أرامكو",
                null, null,
                "MONTE_CARLO", 12, "SAR",
                "{\"iterations\":10000}", null,
                null, null, null,
                null, null, null, null);
    }

    // ── the fix ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the parent's code and title are derived from the risk, never from the caller")
    void theParentIdentityIsDerivedFromTheResolvedRisk() {
        when(risks.findById(4L)).thenReturn(Optional.of(
                risk(4L, ORG, "b7b5a877-3f7b-4580-8628-1e7fb32c04d4",
                        "Non-Compliance with NCA ECC Controls")));

        service.create(req(4L));

        ArgumentCaptor<RiskScenario> saved = ArgumentCaptor.forClass(RiskScenario.class);
        verify(repo).save(saved.capture());
        RiskScenario s = saved.getValue();

        assertThat(s.getRiskId()).isEqualTo(4L);
        // 🔑 The whole finding in one assertion: the stored label is the PARENT's title, and the
        // pre-fix live data stored "Aramco contract renewal delay" here against this same risk.
        assertThat(s.getRiskTitleEn()).isEqualTo("Non-Compliance with NCA ECC Controls");
        assertThat(s.getRiskCode()).isEqualTo("b7b5a877-3f7b-4580-8628-1e7fb32c04d4");
        // The scenario's OWN title is untouched — it is the scenario's, not the risk's.
        assertThat(s.getTitleEn()).isEqualTo("Aramco contract delay — Monte Carlo (base case)");
    }

    @Test
    @DisplayName("a risk in another organisation is refused, and nothing is written")
    void aForeignRiskIsRefused() {
        when(risks.findById(99L)).thenReturn(Optional.of(
                risk(99L, OTHER_ORG, "aef4d175-5764-4018-8a08-be85a45b0c0b", "Someone else's risk")));

        assertThatThrownBy(() -> service.create(req(99L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("a risk that does not resolve is refused indistinguishably from a foreign one")
    void anAbsentRiskIsRefusedIndistinguishably() {
        when(risks.findById(1234L)).thenReturn(Optional.empty());

        // One message for both cases, per CoiService#requireOwnUser (F2-244): distinguishing
        // "no such risk" from "another tenant's risk" lets a caller enumerate foreign ids from
        // the error text.
        String absent = catchMessage(() -> service.create(req(1234L)));
        when(risks.findById(1235L)).thenReturn(Optional.of(
                risk(1235L, OTHER_ORG, null, "Someone else's risk")));
        String foreign = catchMessage(() -> service.create(req(1235L)));

        assertThat(absent.replace("1234", "<id>"))
                .as("the two refusals must not be distinguishable beyond the id echoed back")
                .isEqualTo(foreign.replace("1235", "<id>"));
        verify(repo, never()).save(any());
    }

    private static String catchMessage(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    @DisplayName("a scenario with no parent risk stores no risk label either")
    void noParentMeansNoLabel() {
        service.create(req(null));

        ArgumentCaptor<RiskScenario> saved = ArgumentCaptor.forClass(RiskScenario.class);
        verify(repo).save(saved.capture());
        RiskScenario s = saved.getValue();

        // A risk label on a scenario with no risk is the same false claim in a different shape.
        assertThat(s.getRiskId()).isNull();
        assertThat(s.getRiskCode()).isNull();
        assertThat(s.getRiskTitleEn()).isNull();
        // ...and the risk register is not consulted at all, so a standalone scenario cannot be
        // blocked by a register the caller has no reason to touch.
        verify(risks, never()).findById(any());
    }

    @Test
    @DisplayName("a risk with no external reference stores a null code, not the string 'null'")
    void aRiskWithNoReferenceStoresNull() {
        when(risks.findById(7L)).thenReturn(Optional.of(risk(7L, ORG, null, "Untitled reference")));

        service.create(req(7L));

        ArgumentCaptor<RiskScenario> saved = ArgumentCaptor.forClass(RiskScenario.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getRiskCode()).isNull();
        assertThat(saved.getValue().getRiskTitleEn()).isEqualTo("Untitled reference");
    }
}
