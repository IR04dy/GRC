package com.tss.grc.module.iam.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.common.exception.DuplicateResourceException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.dto.UserRequest;
import com.tss.grc.module.iam.dto.UserResponse;
import com.tss.grc.module.iam.service.UserService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.licensing.SkuRegistry;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.FirstAdministrator;
import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.OnboardTenantRequest;
import com.tss.grc.module.iam.onboarding.dto.TenantOnboardingDtos.OnboardTenantResponse;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * F2-81 — the tenant onboarding transaction.
 *
 * <p>Before this the platform could not create an organisation: no {@code POST} on
 * {@code OrganisationController} other than {@code switch/{id}}, and <b>zero</b> {@code save} calls on
 * {@code OrganisationRepository} anywhere in the codebase. The tenants that exist were inserted by a
 * Python bootstrap.
 */
@ExtendWith(MockitoExtension.class)
class TenantOnboardingServiceTest {

    /** The host operator's own tenant — set on the thread while the new tenant is provisioned. */
    private static final Long HOST_ORG = 1L;
    private static final Long NEW_ORG = 42L;

    @Mock private OrganisationRepository organisations;
    @Mock private OrganisationLicenseRepository licences;
    @Mock private UserService users;
    @Mock private AuditLogService audit;

    @InjectMocks private TenantOnboardingService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(HOST_ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static OnboardTenantRequest request() {
        return new OnboardTenantRequest(
            "STC Bank", "بنك إس تي سي", "stcb2", "sa", "enterprise", "Banking",
            new FirstAdministrator("stcb.admin", "admin@stcbank.sa", "Str0ng-Passw0rd!x",
                                   "Nouf Al-Harbi", "نوف الحربي"));
    }

    private void givenSavesSucceed() {
        when(organisations.findByShortCode(anyString())).thenReturn(Optional.empty());
        when(organisations.save(any(Organisation.class))).thenAnswer(inv -> {
            Organisation o = inv.getArgument(0);
            o.setId(NEW_ORG);
            return o;
        });
        when(users.createIn(anyLong(), any(UserRequest.class))).thenReturn(
            new UserResponse(7L, "stcb.admin", "admin@stcbank.sa", "Nouf Al-Harbi", "نوف الحربي",
                             "Platform Administrator", true, false, Set.of("ORG_ADMIN"), null, null));
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the organisation is created, short code upper-cased, both names kept")
    void createsTheOrganisation() {
        givenSavesSucceed();

        OnboardTenantResponse out = service.onboard(request());

        ArgumentCaptor<Organisation> saved = ArgumentCaptor.forClass(Organisation.class);
        verify(organisations).save(saved.capture());
        Organisation org = saved.getValue();
        assertThat(org.getShortCode())
            .as("the short code is a public identifier that portal links carry (F2-83), so it is "
                + "normalised rather than stored however it was typed")
            .isEqualTo("STCB2");
        assertThat(org.getNameEn()).isEqualTo("STC Bank");
        assertThat(org.getNameAr()).as("house rule 7 — Arabic is not optional").isEqualTo("بنك إس تي سي");
        assertThat(org.getCountryCode()).isEqualTo("SA");
        assertThat(org.getSubscriptionTier()).isEqualTo("ENTERPRISE");
        assertThat(org.getIsActive()).isTrue();
        assertThat(out.organisationId()).isEqualTo(NEW_ORG);
        assertThat(out.portalLinkHint()).contains("?org=STCB2");
    }

    @Test
    @DisplayName("every ratified SKU gets a usable licence row — tier, validFrom and enabled all set")
    void licensesEveryRatifiedSku() {
        givenSavesSucceed();

        OnboardTenantResponse out = service.onboard(request());

        ArgumentCaptor<OrganisationLicense> rows = ArgumentCaptor.forClass(OrganisationLicense.class);
        verify(licences, times(out.licenceRowsCreated())).save(rows.capture());
        List<OrganisationLicense> saved = rows.getAllValues();

        assertThat(saved).hasSize(15);
        assertThat(saved).allSatisfy(r -> {
            assertThat(r.getOrganisationId()).isEqualTo(NEW_ORG);
            // OrganisationLicense deliberately has no field initialisers for these three — its own
            // javadoc records that defaults there once let a PATCH of `notes` silently re-enable a
            // module and reset its start date. A provisioning path must state all three, or it
            // writes rows licence_coverage.py counts as present and unusable.
            assertThat(r.getTier()).as("tier unset — the row would be present but not usable").isNotBlank();
            assertThat(r.getValidFrom()).as("validFrom unset").isNotNull();
            assertThat(r.getEnabled()).as("enabled unset").isTrue();
        });
        assertThat(saved).extracting(OrganisationLicense::getValidFrom)
            .allMatch(d -> !((LocalDate) d).isAfter(LocalDate.now()));
    }

    @Test
    @DisplayName("the SKU set is derived from SkuRegistry, so a new SKU is licensed without editing this service")
    void theSkuSetIsDerivedNotTranscribed() {
        Set<String> expected = new TreeSet<>(SkuRegistry.PACKAGE_TO_SKU.values());
        expected.addAll(SkuRegistry.CARVE_OUTS.values());

        assertThat(TenantOnboardingService.ratifiedSkus())
            .as("licence_coverage.py records that the ratified SKU set already has three "
                + "transcriptions the suite keeps in step. A fourth, hard-coded in the onboarding "
                + "service, would be a new authority for the same fact — and a SKU added to ADR-018 "
                + "would then silently stop being licensed to new tenants.")
            .isEqualTo(expected)
            .hasSize(15);
    }

    @Test
    @DisplayName("the first administrator is created in the NEW organisation, not the caller's")
    void theAdministratorBelongsToTheNewTenant() {
        givenSavesSucceed();

        service.onboard(request());

        ArgumentCaptor<UserRequest> req = ArgumentCaptor.forClass(UserRequest.class);
        verify(users).createIn(eq(NEW_ORG), req.capture());
        assertThat(req.getValue().username()).isEqualTo("stcb.admin");
        assertThat(req.getValue().roles()).containsExactly("ORG_ADMIN");
        verify(users, never()).create(any(UserRequest.class));
    }

    @Test
    @DisplayName("provisioning runs inside a cross-tenant write scope — otherwise prod rejects every row")
    void provisioningOpensACrossTenantScope() {
        // This is the one behaviour a unit test can see and a dev probe cannot: the tenant
        // write-guard interceptor only runs under the prod profile, so without this assertion the
        // whole feature could ship working in dev and reject every insert in production — which is
        // F2-92's exact history.
        AtomicBoolean allowedAtSaveTime = new AtomicBoolean(false);
        when(organisations.findByShortCode(anyString())).thenReturn(Optional.empty());
        when(organisations.save(any(Organisation.class))).thenAnswer(inv -> {
            allowedAtSaveTime.set(TenantContext.isCrossTenantWriteAllowed());
            Organisation o = inv.getArgument(0);
            o.setId(NEW_ORG);
            return o;
        });
        when(users.createIn(anyLong(), any(UserRequest.class))).thenReturn(
            new UserResponse(7L, "stcb.admin", "a@b.sa", "N", "ن", null, true, false,
                             Set.of("ORG_ADMIN"), null, null));

        service.onboard(request());

        assertThat(allowedAtSaveTime.get())
            .as("the organisation was written while TenantContext still held the host operator's "
                + "tenant and no cross-tenant scope was open — prod's write guard would reject it")
            .isTrue();
        assertThat(TenantContext.isCrossTenantWriteAllowed())
            .as("the scope must close again; leaving it open would disable the guard for whatever "
                + "the thread does next")
            .isFalse();
    }

    @Test
    @DisplayName("an audit row is written, and it names the tenant that was onboarded")
    void writesAnAuditRow() {
        givenSavesSucceed();

        service.onboard(request());

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("TENANT_ONBOARDED"), eq("Organisation"), eq(NEW_ORG), msg.capture());
        assertThat(msg.getValue()).contains("STCB2").contains("STC Bank");
    }

    @Test
    @DisplayName("a duplicate short code is refused by field, and nothing at all is written")
    void refusesADuplicateShortCode() {
        Organisation existing = new Organisation();
        existing.setId(3L);
        existing.setShortCode("STCB2");
        when(organisations.findByShortCode("STCB2")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.onboard(request()))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("STCB2");
        assertThat(((DuplicateResourceException) catchIt()).getField())
            .as("F2-88's contract: name the field so the operator fixes the one thing that is wrong")
            .isEqualTo("shortCode");

        // The pre-flight exists so the unique index never fires; more importantly, a refusal must
        // leave no trace — a tenant that half-exists is worse than one that does not.
        verify(organisations, never()).save(any(Organisation.class));
        verify(licences, never()).save(any(OrganisationLicense.class));
        verify(users, never()).createIn(anyLong(), any(UserRequest.class));
        verify(audit, never()).log(anyString(), anyString(), any(), anyString());
    }

    private Throwable catchIt() {
        List<Throwable> caught = new ArrayList<>();
        try {
            service.onboard(request());
        } catch (Throwable t) {
            caught.add(t);
        }
        return caught.get(0);
    }
}
