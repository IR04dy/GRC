package com.tss.grc.module.platform.licensing.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LicensingService — F9 enablement + validation rule lock.
 *
 * <ul>
 *   <li>enabledModulesFor returns only enabled, in-date entitlements.</li>
 *   <li>enabledModulesFor filters out disabled rows + past-validity + future-validity rows.</li>
 *   <li>upsert rejects unknown module codes.</li>
 *   <li>upsert rejects invalid tier values.</li>
 *   <li>upsert defaults tier=PRO, enabled=true, validFrom=today.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LicensingServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private OrganisationLicenseRepository repo;
    @Mock private OrganisationRepository organisations;
    @Mock private AuditLogService audit;

    /**
     * F2-412 — a <b>REAL</b> {@link LicensedModuleReader} over the mocked repository, never a
     * {@code @Mock}. The entitlement-filtering legs below assert enabled/valid-from/valid-to
     * behaviour; with a mocked reader they would assert the mock and pass over any filtering defect
     * (F2-237). Constructed by hand rather than by {@code @InjectMocks} for the same reason.
     */
    private LicensingService service;

    @BeforeEach
    void wire() {
        service = new LicensingService(repo, organisations, audit, new LicensedModuleReader(repo));
    }

    private static OrganisationLicense lic(String code, boolean enabled,
                                            LocalDate from, LocalDate to) {
        OrganisationLicense l = new OrganisationLicense();
        l.setId(1L); l.setOrganisationId(ORG_ID);
        l.setModuleCode(code); l.setEnabled(enabled);
        l.setTier("ENTERPRISE");
        l.setValidFrom(from); l.setValidTo(to);
        return l;
    }

    @Test void enabledModulesFor_skipsDisabledAndOutOfDate() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow  = LocalDate.now().plusDays(1);
        LocalDate longAgo   = LocalDate.now().minusYears(2);
        LocalDate farFuture = LocalDate.now().plusYears(2);

        when(repo.findByOrganisationIdOrderByModuleCodeAsc(ORG_ID)).thenReturn(List.of(
            lic("RISK",   true,  yesterday, tomorrow),
            lic("BCM",    true,  longAgo,   yesterday),    // expired
            lic("CYBER",  false, yesterday, tomorrow),     // disabled
            lic("PDPL",   true,  tomorrow,  farFuture),    // future-dated
            lic("TPRM",   true,  yesterday, null)          // open-ended
        ));

        Set<String> enabled = service.enabledModulesFor(ORG_ID);
        assertThat(enabled).containsExactlyInAnyOrder("RISK", "TPRM");
    }

    @Test void isModuleEnabled_returnsFalseForNullOrUnlicensed() {
        when(repo.findByOrganisationIdOrderByModuleCodeAsc(ORG_ID)).thenReturn(List.of());
        assertThat(service.isModuleEnabled(ORG_ID, null)).isFalse();
        assertThat(service.isModuleEnabled(ORG_ID, "RISK")).isFalse();
    }

    @Test void upsert_rejectsUnknownModule() {
        OrganisationLicense body = new OrganisationLicense();
        body.setOrganisationId(ORG_ID); body.setModuleCode("FAKE");
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown module");
    }

    @Test void upsert_rejectsInvalidTier() {
        OrganisationLicense body = new OrganisationLicense();
        body.setOrganisationId(ORG_ID); body.setModuleCode("RISK"); body.setTier("PLATINUM");
        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tier");
    }

    @Test void upsert_defaultsApplied() {
        OrganisationLicense body = new OrganisationLicense();
        body.setOrganisationId(ORG_ID); body.setModuleCode("RISK");
        body.setTier(null); body.setEnabled(null); body.setValidFrom(null);

        when(repo.findByOrganisationIdAndModuleCode(ORG_ID, "RISK")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> {
            OrganisationLicense saved = inv.getArgument(0); saved.setId(42L); return saved;
        });

        OrganisationLicense saved = service.upsert(body);
        assertThat(saved.getTier()).isEqualTo("PRO");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getValidFrom()).isEqualTo(LocalDate.now());
    }

    @Test void upsert_existingRowUpdatesFields() {
        OrganisationLicense existing = lic("RISK", true, LocalDate.now().minusDays(10), null);
        existing.setTier("PRO");
        when(repo.findByOrganisationIdAndModuleCode(ORG_ID, "RISK")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrganisationLicense patch = new OrganisationLicense();
        patch.setOrganisationId(ORG_ID); patch.setModuleCode("RISK"); patch.setTier("ENTERPRISE");

        OrganisationLicense saved = service.upsert(patch);
        assertThat(saved.getTier()).isEqualTo("ENTERPRISE");
        // existing fields preserved
        assertThat(saved.getEnabled()).isTrue();
    }

    /**
     * The partial update must leave out what the caller left out — asserted against values the
     * default would have <em>changed</em>, which is the whole point.
     *
     * <p>⚠ {@link #upsert_existingRowUpdatesFields()} above already claimed "existing fields
     * preserved" and passed while the opposite was true: the entity initialised
     * {@code enabled = true}, so the patch overwrote a {@code true} with a {@code true} and the
     * assertion could not tell preservation from overwriting. <b>A test that asserts a field still
     * equals the value the bug would also produce is not testing anything.</b> Here the row starts
     * DISABLED, on FREE, dated last year — three values no default would produce.
     */
    @Test void upsert_partialUpdateDoesNotResurrectDefaults() {
        LocalDate lastYear = LocalDate.now().minusYears(1);
        OrganisationLicense existing = lic("RISK", false, lastYear, null);
        existing.setTier("FREE");
        when(repo.findByOrganisationIdAndModuleCode(ORG_ID, "RISK")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // The operator is only annotating the row. Nothing about tier, dates, or enablement.
        OrganisationLicense patch = new OrganisationLicense();
        patch.setOrganisationId(ORG_ID);
        patch.setModuleCode("RISK");
        patch.setNotes("Contract renewal pending — do not re-enable");

        OrganisationLicense saved = service.upsert(patch);

        assertThat(saved.getEnabled())
            .as("a patch that says nothing about enablement must NOT re-enable a disabled licence — "
                + "de-licensing is the one commercial control this table carries, and an entity "
                + "field default silently undid it on any unrelated edit")
            .isFalse();
        assertThat(saved.getTier())
            .as("a patch that says nothing about tier must not downgrade ENTERPRISE/FREE to PRO")
            .isEqualTo("FREE");
        assertThat(saved.getValidFrom())
            .as("a patch that says nothing about dates must not reset the licence start to today, "
                + "which is the contract start date")
            .isEqualTo(lastYear);
        assertThat(saved.getNotes()).isEqualTo("Contract renewal pending — do not re-enable");
    }

    /** A retired code may be wound down on an existing row and never newly created (ADR-018 §5). */
    @Test void upsert_refusesToNewlyLicenseARetiredCode() {
        when(repo.findByOrganisationIdAndModuleCode(ORG_ID, "POLICY")).thenReturn(Optional.empty());
        OrganisationLicense body = new OrganisationLicense();
        body.setOrganisationId(ORG_ID); body.setModuleCode("POLICY");

        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retired")
            .as("the refusal must name the SKU to buy instead, or the operator has to read an ADR")
            .hasMessageContaining("COMPLIANCE");
    }

    @Test void upsert_stillAllowsWindingDownAnExistingRetiredRow() {
        OrganisationLicense existing = lic("POLICY", true, LocalDate.now().minusYears(1), null);
        when(repo.findByOrganisationIdAndModuleCode(ORG_ID, "POLICY")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrganisationLicense patch = new OrganisationLicense();
        patch.setOrganisationId(ORG_ID); patch.setModuleCode("POLICY");
        patch.setValidTo(LocalDate.now().plusMonths(3));

        assertThat(service.upsert(patch).getValidTo()).isEqualTo(LocalDate.now().plusMonths(3));
    }

    /** A Base-retired code has no successor to recommend, so the message must say so instead. */
    @Test void upsert_refusalForABaseCodeSaysThereIsNothingToGrant() {
        when(repo.findByOrganisationIdAndModuleCode(ORG_ID, "EVIDENCE")).thenReturn(Optional.empty());
        OrganisationLicense body = new OrganisationLicense();
        body.setOrganisationId(ORG_ID); body.setModuleCode("EVIDENCE");

        assertThatThrownBy(() -> service.upsert(body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Base Platform")
            .hasMessageContaining("nothing to grant");
    }

    @Test void resolveLicensingTarget_refusesAnOrganisationThatDoesNotExist() {
        when(organisations.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.resolveLicensingTarget(999L))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .as("organisation_licenses carries no FK to platform.organisations, so an unchecked id "
                + "writes rows nothing will ever read")
            .hasMessageContaining("999");
    }

    @Test void resolveLicensingTarget_honoursAnOrganisationThatDoesExist() {
        when(organisations.existsById(2L)).thenReturn(true);
        assertThat(service.resolveLicensingTarget(2L))
            .as("F2-45: the body's organisationId must reach the row. Overwriting it with the "
                + "caller's tenant did not refuse the cross-tenant grant, it answered 200 for the "
                + "wrong organisation")
            .isEqualTo(2L);
    }
}
