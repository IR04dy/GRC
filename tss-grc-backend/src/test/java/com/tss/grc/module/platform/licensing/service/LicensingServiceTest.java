package com.tss.grc.module.platform.licensing.service;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import com.tss.grc.module.platform.licensing.repository.OrganisationLicenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    @Mock private AuditLogService audit;
    @InjectMocks private LicensingService service;

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
}
