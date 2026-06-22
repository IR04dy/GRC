package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberAsset;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
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
 * {@link CyberAssetService} — extracted from the former CyberService god-class
 * (QUAL-CP-001). SEC-CYB-001 (cross-tenant IDOR) regression lock for the asset
 * update / soft-delete paths (which use an inline scoped lookup).
 */
@ExtendWith(MockitoExtension.class)
class CyberAssetServiceTest {

    private static final Long ORG_A      = 1L;
    private static final Long FOREIGN_ID = 999L;

    @Mock private CyberAssetRepository assetRepo;
    @InjectMocks private CyberAssetService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_A); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test
    void updateAsset_crossTenantId_throwsAndDoesNotSave() {
        when(assetRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        CyberAsset req = new CyberAsset();

        assertThatThrownBy(() -> service.updateAsset(FOREIGN_ID, req))
                .isInstanceOf(EntityNotFoundException.class);

        verify(assetRepo, never()).save(any(CyberAsset.class));
    }

    @Test
    void deleteAsset_crossTenantId_throwsAndDoesNotSoftDelete() {
        when(assetRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAsset(FOREIGN_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verify(assetRepo, never()).save(any(CyberAsset.class));
    }
}
