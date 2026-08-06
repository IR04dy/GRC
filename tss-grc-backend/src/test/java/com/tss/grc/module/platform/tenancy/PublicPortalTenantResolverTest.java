package com.tss.grc.module.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * F2-83 — the tenant resolution for {@code permitAll} public portals.
 *
 * <p>The behaviour this replaces was <b>covered by passing tests</b>: both
 * {@code WhistleblowerCaseServiceTest} and {@code ComplaintServiceTest} asserted that an anonymous
 * submission with no tenant context lands in org 1, and called it correct. So the point of this
 * class is not "add coverage" — coverage existed. It is to pin the opposite rule, including the two
 * cases the old constant could not express at all: an <i>unknown</i> code and an <i>inactive</i> one.
 */
@ExtendWith(MockitoExtension.class)
class PublicPortalTenantResolverTest {

    private static final Long CONTEXT_ORG = 7L;
    private static final Long NAMED_ORG = 3L;

    @Mock private OrganisationRepository organisations;
    @InjectMocks private PublicPortalTenantResolver resolver;

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static Organisation org(Long id, boolean active) {
        Organisation o = new Organisation();
        o.setId(id);
        o.setShortCode("STCB");
        o.setNameEn("STC Bank");
        o.setIsActive(active);
        return o;
    }

    // ─── resolve(): writes from a public form ──────────────────────────────────────────────

    @Test
    @DisplayName("a logged-in employee's own tenant wins over anything in the query string")
    void contextBeatsTheShortCode() {
        TenantContext.setCurrentTenant(CONTEXT_ORG);

        assertThat(resolver.resolve("STCB")).isEqualTo(CONTEXT_ORG);
        // Their tenant is already known; there is nothing to look up.
        verify(organisations, never()).findByShortCode(anyString());
    }

    @Test
    @DisplayName("an anonymous caller is routed to the tenant named by the short code")
    void anonymousResolvesTheNamedTenant() {
        TenantContext.clear();
        when(organisations.findByShortCode("STCB")).thenReturn(Optional.of(org(NAMED_ORG, true)));

        assertThat(resolver.resolve("STCB")).isEqualTo(NAMED_ORG);
    }

    @Test
    @DisplayName("surrounding whitespace in the portal link does not break resolution")
    void shortCodeIsTrimmed() {
        TenantContext.clear();
        when(organisations.findByShortCode("STCB")).thenReturn(Optional.of(org(NAMED_ORG, true)));

        assertThat(resolver.resolve("  STCB  ")).isEqualTo(NAMED_ORG);
    }

    // ─── the three refusals ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no short code at all is refused, NOT defaulted to org 1")
    void missingCodeIsRefused() {
        TenantContext.clear();

        for (String code : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> resolver.resolve(code))
                    .as("code %s", code == null ? "null" : "'" + code + "'")
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Unknown or inactive organisation");
        }
    }

    @Test
    @DisplayName("an unknown short code is refused — the old constant could not express this case")
    void unknownCodeIsRefused() {
        TenantContext.clear();
        when(organisations.findByShortCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("NOPE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown or inactive organisation");
    }

    @Test
    @DisplayName("a known but INACTIVE tenant is refused — a lapsed customer keeps receiving reports otherwise")
    void inactiveTenantIsRefused() {
        TenantContext.clear();
        when(organisations.findByShortCode("STCB")).thenReturn(Optional.of(org(NAMED_ORG, false)));

        assertThatThrownBy(() -> resolver.resolve("STCB"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown or inactive organisation");
    }

    @Test
    @DisplayName("every refusal carries the identical message, so it cannot be used to enumerate tenants")
    void refusalsAreIndistinguishable() {
        TenantContext.clear();
        when(organisations.findByShortCode("NOPE")).thenReturn(Optional.empty());

        String missing = catchMessage(() -> resolver.resolve(null));
        String unknown = catchMessage(() -> resolver.resolve("NOPE"));

        assertThat(missing)
                .as("a caller must not be able to tell 'no code given' from 'no such tenant'")
                .isEqualTo(unknown);
    }

    private static String catchMessage(Runnable r) {
        try {
            r.run();
            throw new AssertionError("expected a refusal");
        } catch (ResponseStatusException e) {
            return String.valueOf(e.getReason());
        }
    }

    // ─── resolveNamed(): public reads of a named tenant's published data ───────────────────

    @Test
    @DisplayName("resolveNamed ignores the caller's own tenant — it returns the one they asked for")
    void resolveNamedIgnoresContext() {
        // The distinction that matters: SubprocessorController#publicList serves a *named* tenant's
        // PDPL Article 12 list. If it preferred TenantContext the way a submission does, an
        // authenticated caller asking for STCB's list would silently receive their own.
        TenantContext.setCurrentTenant(CONTEXT_ORG);
        when(organisations.findByShortCode("STCB")).thenReturn(Optional.of(org(NAMED_ORG, true)));

        assertThat(resolver.resolveNamed("STCB"))
                .as("the list asked for, not the caller's own")
                .isEqualTo(NAMED_ORG)
                .isNotEqualTo(CONTEXT_ORG);
    }

    @Test
    @DisplayName("resolveNamed refuses rather than falling back to the caller's tenant")
    void resolveNamedRefusesWithoutACode() {
        TenantContext.setCurrentTenant(CONTEXT_ORG);

        assertThatThrownBy(() -> resolver.resolveNamed(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown or inactive organisation");
    }
}
