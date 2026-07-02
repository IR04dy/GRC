package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.ghg.entity.EmissionFactor;
import com.tss.grc.module.sust.ghg.repository.EmissionFactorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * EmissionFactorService — S-J versioned emission-factor library.
 * Locks the supersede-on-new-version semantics + the resolveActive contract.
 */
@ExtendWith(MockitoExtension.class)
class EmissionFactorServiceTest {

    private static final Long ORG_ID = 1L;
    private static final String CODE = "EF-GRID-KSA";

    @Mock private EmissionFactorRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private EmissionFactorService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static EmissionFactor req() {
        EmissionFactor f = new EmissionFactor();
        f.setFactorCode(CODE);
        f.setScope((short) 2);
        f.setTitleEn("Saudi grid electricity");
        f.setActivityUnit("kWh");
        f.setFactorValue(new BigDecimal("0.45"));
        f.setFactorUnit("kgCO2e/kWh");
        f.setSource("SEEC KSA grid factor");
        return f;
    }

    private static EmissionFactor existing(int version, boolean active) {
        EmissionFactor f = req();
        f.setId((long) version);
        f.setOrganisationId(ORG_ID);
        f.setVersion(version);
        f.setIsActive(active);
        return f;
    }

    @Test
    void create_firstVersion_isV1AndActive() {
        when(repo.findFirstByOrganisationIdAndFactorCodeOrderByVersionDesc(ORG_ID, CODE))
            .thenReturn(Optional.empty());
        when(repo.save(any(EmissionFactor.class))).thenAnswer(inv -> inv.getArgument(0));

        EmissionFactor saved = service.create(req());

        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getValidFrom()).isNotNull();
    }

    @Test
    void create_sameCode_bumpsVersionAndSupersedesPrior() {
        EmissionFactor priorActive = existing(1, true);
        when(repo.findFirstByOrganisationIdAndFactorCodeOrderByVersionDesc(ORG_ID, CODE))
            .thenReturn(Optional.of(priorActive));
        when(repo.findFirstByOrganisationIdAndFactorCodeAndIsActiveTrue(ORG_ID, CODE))
            .thenReturn(Optional.of(priorActive));
        when(repo.save(any(EmissionFactor.class))).thenAnswer(inv -> inv.getArgument(0));

        EmissionFactor saved = service.create(req());

        assertThat(saved.getVersion()).isEqualTo(2);      // bumped
        assertThat(saved.getIsActive()).isTrue();
        assertThat(priorActive.getIsActive()).isFalse();  // prior deactivated
        assertThat(priorActive.getValidTo()).isNotNull();
    }

    @Test
    void deactivate_setsInactiveAndStampsValidTo() {
        EmissionFactor active = existing(1, true);
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(active));
        when(repo.save(any(EmissionFactor.class))).thenAnswer(inv -> inv.getArgument(0));

        EmissionFactor after = service.deactivate(1L);

        assertThat(after.getIsActive()).isFalse();
        assertThat(after.getValidTo()).isNotNull();
    }

    @Test
    void resolveActive_throwsWhenNoActiveFactor() {
        when(repo.findFirstByOrganisationIdAndFactorCodeAndIsActiveTrue(ORG_ID, CODE))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveActive(CODE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No active emission factor");
    }
}
