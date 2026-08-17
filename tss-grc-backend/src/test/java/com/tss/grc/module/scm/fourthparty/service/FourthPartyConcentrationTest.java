package com.tss.grc.module.scm.fourthparty.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.fourthparty.dto.FourthPartyExposureDtos.CreateRequest;
import com.tss.grc.module.scm.fourthparty.dto.FourthPartyExposureDtos.UpdateRequest;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.fourthparty.repository.FourthPartyExposureRepository;
import com.tss.grc.module.tprm.automation.TprmAutomationService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-229 — the concentration recompute may raise a tier and may never lower one.
 *
 * <p><b>Why this class exists and did not before.</b> There was no unit test of any kind on this
 * service, which is part of why the defect survived: {@code recomputeConcentration} assigned the
 * derived tier unconditionally across the whole tenant, on every create and every update, while the
 * service javadoc said <i>"auto-elevated"</i>, the repository javadoc said <i>"elevate"</i>, and the
 * auto-CAPA hook was written as a rising edge — a shape that only makes sense if the value is
 * monotone. Measured against the live register (2026-08-17, identical in dev and prod), all 13 rows
 * carry <b>13 distinct</b> fourth-party names, so the derivation matches nothing and computes LOW for
 * every row: any write lowered 9 of 13, including all three HIGH rows and the sole CRITICAL one.
 *
 * <p>The first test below is that measured shape, run against the real service. It fails on the
 * pre-fix code and is the only leg here that reproduces the finding rather than describing it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FourthPartyConcentrationTest {

    private static final Long ORG = 1L;

    @Mock private FourthPartyExposureRepository repo;
    @Mock private TprmAutomationService automation;

    private FourthPartyExposureService service;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.setCurrentTenant(ORG);
        service = new FourthPartyExposureService(repo);
        // `automation` is @Autowired(required = false) field injection, not a constructor arg.
        var f = FourthPartyExposureService.class.getDeclaredField("automation");
        f.setAccessible(true);
        f.set(service, automation);
        when(repo.save(any(FourthPartyExposure.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the measured defect ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the live register's exact shape: a create lowers nothing")
    void aCreateDoesNotLowerAnyStoredTierInTheMeasuredRegister() {
        // The 13 live rows as measured in dev and prod: 13 DISTINCT fourth-party names, so every
        // row's multiplicity floor is LOW, and 9 of the 13 sit above it.
        List<FourthPartyExposure> register = new ArrayList<>();
        String[][ ] measured = {
            {"Cloudflare Inc.", "MEDIUM"},
            {"Amazon Web Services (AWS Dublin)", "HIGH"},
            {"Tata Consultancy Services India HQ", "MEDIUM"},
            {"Microsoft Corporation", "HIGH"},
            {"Underlying hyperscaler region (sub-infrastructure)", "CRITICAL"},
            {"Global threat-intelligence feed provider", "MEDIUM"},
            {"Niche integration subcontractor", "MEDIUM"},
            {"Cloud telephony / IVR platform", "MEDIUM"},
            {"National identity source (Yakeen/Absher data)", "HIGH"},
            {"Akamai edge", "LOW"},
            {"Local payroll bureau", "LOW"},
            {"Courier network", "LOW"},
            {"Archive storage vendor", "LOW"},
        };
        for (int i = 0; i < measured.length; i++) {
            register.add(row((long) i + 1, measured[i][0], measured[i][1], "VALIDATED"));
        }
        when(repo.findByOrganisationId(ORG)).thenReturn(register);
        when(repo.findByIdAndOrganisationId(anyLong(), any())).thenAnswer(
            inv -> register.stream().filter(r -> r.getId().equals(inv.getArgument(0))).findFirst());

        service.create(newRequest("A brand new fourth party"));

        Map<String, String> after = register.stream().collect(
            Collectors.toMap(FourthPartyExposure::getFourthPartyName,
                             FourthPartyExposure::getConcentrationRisk));
        for (String[] m : measured) {
            assertThat(after.get(m[0]))
                .as("%s was stored %s and no write may lower it", m[0], m[1])
                .isEqualTo(m[1]);
        }
        // 13 rows in, 13 tiers out, none of them rewritten -> nothing was dirtied, so the only save
        // is the new row's own. The pre-fix code saved nine of the thirteen.
        verify(repo, times(1)).save(any(FourthPartyExposure.class));
        verify(automation, never()).onFourthPartyHighConcentration(any());
    }

    // ── elevation still happens ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("three live primaries behind one fourth party elevates LOW to HIGH, once")
    void multiplicityStillElevatesAndFiresTheCapaOnTheRisingEdge() {
        FourthPartyExposure a = row(1L, "Shared hyperscaler", "LOW", "VALIDATED");
        FourthPartyExposure b = row(2L, "Shared hyperscaler", "LOW", "DISCOVERED");
        FourthPartyExposure c = row(3L, "Shared hyperscaler", "LOW", "MITIGATED");
        FourthPartyExposure d = row(4L, "Somebody else", "LOW", "VALIDATED");
        List<FourthPartyExposure> register = new ArrayList<>(List.of(a, b, c, d));
        when(repo.findByOrganisationId(ORG)).thenReturn(register);
        when(repo.findByIdAndOrganisationId(anyLong(), any())).thenReturn(Optional.of(a));

        service.update(1L, updateRequest());

        assertThat(a.getConcentrationRisk()).isEqualTo("HIGH");
        assertThat(b.getConcentrationRisk()).isEqualTo("HIGH");
        assertThat(c.getConcentrationRisk()).isEqualTo("HIGH");
        assertThat(d.getConcentrationRisk()).as("one primary only").isEqualTo("LOW");
        verify(automation, times(3)).onFourthPartyHighConcentration(any());
    }

    @Test
    @DisplayName("a row already at CRITICAL is not a rising edge into HIGH")
    void anAlreadyCriticalRowDoesNotReFireTheCapa() {
        FourthPartyExposure a = row(1L, "Shared hyperscaler", "CRITICAL", "VALIDATED");
        FourthPartyExposure b = row(2L, "Shared hyperscaler", "CRITICAL", "VALIDATED");
        FourthPartyExposure c = row(3L, "Shared hyperscaler", "CRITICAL", "VALIDATED");
        when(repo.findByOrganisationId(ORG)).thenReturn(new ArrayList<>(List.of(a, b, c)));
        when(repo.findByIdAndOrganisationId(anyLong(), any())).thenReturn(Optional.of(a));

        service.update(1L, updateRequest());

        assertThat(a.getConcentrationRisk()).isEqualTo("CRITICAL");
        verify(automation, never()).onFourthPartyHighConcentration(any());
    }

    // ── the RETIRED rule ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a RETIRED edge earns nobody a concentration floor")
    void retiredEdgesDoNotCountTowardTheFloor() {
        // Three edges on one name, but one is retired: two live primaries -> MEDIUM, not HIGH.
        FourthPartyExposure a = row(1L, "Shared hyperscaler", "LOW", "VALIDATED");
        FourthPartyExposure b = row(2L, "Shared hyperscaler", "LOW", "VALIDATED");
        FourthPartyExposure retired = row(3L, "Shared hyperscaler", "LOW", "RETIRED");
        when(repo.findByOrganisationId(ORG)).thenReturn(new ArrayList<>(List.of(a, b, retired)));
        when(repo.findByIdAndOrganisationId(anyLong(), any())).thenReturn(Optional.of(a));

        service.update(1L, updateRequest());

        assertThat(a.getConcentrationRisk()).isEqualTo("MEDIUM");
        assertThat(b.getConcentrationRisk()).isEqualTo("MEDIUM");
        // The retired row itself still gets the floor of its own live count, which is the same
        // MEDIUM -- it is excluded from COUNTING, not from being raised.
        assertThat(retired.getConcentrationRisk()).isEqualTo("MEDIUM");
        verify(automation, never()).onFourthPartyHighConcentration(any());
    }

    // ── the create's initial state and the vocabularies ───────────────────────────────────────

    @Test
    @DisplayName("a create starts at the ladder's first tier, taken from the ladder")
    void aCreateStartsAtTheBottomOfTheLadder() {
        when(repo.findByOrganisationId(ORG)).thenReturn(new ArrayList<>());
        ArgumentCaptor<FourthPartyExposure> saved = ArgumentCaptor.forClass(FourthPartyExposure.class);

        service.create(newRequest("Fresh"));

        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getConcentrationRisk())
            .isEqualTo(ConcentrationTier.ladder().get(0))
            .isEqualTo("LOW");
        assertThat(saved.getValue().getStatus()).isEqualTo("DISCOVERED");
    }

    @Test
    @DisplayName("the caller's casing is normalised, not refused")
    void theTwoCallerSuppliedVocabulariesNormaliseCasing() {
        when(repo.findByOrganisationId(ORG)).thenReturn(new ArrayList<>());
        ArgumentCaptor<FourthPartyExposure> saved = ArgumentCaptor.forClass(FourthPartyExposure.class);

        service.create(new CreateRequest(null, "Primary", "Fourth", "SAU",
                " data_processing ", "critical", "desc", null, null, null,
                null, null, null, null));

        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getDependencyType()).isEqualTo("DATA_PROCESSING");
        assertThat(saved.getValue().getDownstreamCriticality()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("a token outside the vocabulary is still refused, naming the field")
    void anUnknownTokenIsRefused() {
        assertThatThrownBy(() -> service.create(new CreateRequest(null, "P", "F", "SAU",
                "TELEPATHY", "HIGH", "desc", null, null, null, null, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dependencyType");
    }

    @Test
    @DisplayName("every status literal the service writes is in the status vocabulary")
    void theServiceWritesOnlyDeclaredStatuses() {
        assertThat(FourthPartyExposureService.STATUS.tokens()).contains(
            FourthPartyExposureService.DISCOVERED, FourthPartyExposureService.VALIDATED,
            FourthPartyExposureService.MITIGATED, FourthPartyExposureService.RETIRED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private static FourthPartyExposure row(Long id, String name, String tier, String status) {
        FourthPartyExposure e = new FourthPartyExposure();
        e.setId(id);
        e.setOrganisationId(ORG);
        e.setPrimaryVendorName("Primary " + id);
        e.setFourthPartyName(name);
        e.setDependencyType("INFRASTRUCTURE");
        e.setDownstreamCriticality("HIGH");
        e.setConcentrationRisk(tier);
        e.setServiceDescriptionEn("seeded");
        e.setStatus(status);
        e.setIsDeleted((short) 0);
        e.setIsActive((short) 1);
        return e;
    }

    private static CreateRequest newRequest(String fourthPartyName) {
        return new CreateRequest(null, "Primary", fourthPartyName, "SAU",
                "INFRASTRUCTURE", "HIGH", "A new edge", null, null, null,
                null, null, null, null);
    }

    private static UpdateRequest updateRequest() {
        return new UpdateRequest("Primary", null, "SAU", null, null,
                "edited", null, null, null, null, null, null, null);
    }
}
