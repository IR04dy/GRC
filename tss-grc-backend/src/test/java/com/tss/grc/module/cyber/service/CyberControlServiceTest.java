package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CyberControlService} — extracted from the former CyberService
 * god-class (QUAL-CP-001). SEC-CYB-001 (cross-tenant IDOR) regression lock:
 * a caller authenticated for ORG_A must not be able to read/overwrite a row
 * that lives only in another tenant.
 */
@ExtendWith(MockitoExtension.class)
class CyberControlServiceTest {

    private static final Long ORG_A      = 1L;
    private static final Long FOREIGN_ID = 999L;

    @Mock private CyberControlRepository controlRepo;
    @InjectMocks private CyberControlService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_A); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test
    void getControl_crossTenantId_throwsAndScopesByTenant() {
        // Row exists only in org B → scoped lookup with ORG_A returns empty.
        when(controlRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getControl(FOREIGN_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateControl_crossTenantId_throwsAndDoesNotSave() {
        when(controlRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        CyberControl req = new CyberControl();
        req.setTitleEn("Attacker-supplied update");

        assertThatThrownBy(() -> service.updateControl(FOREIGN_ID, req))
                .isInstanceOf(EntityNotFoundException.class);

        // The foreign row must never be merged/overwritten.
        verify(controlRepo, never()).save(any(CyberControl.class));
    }
}
