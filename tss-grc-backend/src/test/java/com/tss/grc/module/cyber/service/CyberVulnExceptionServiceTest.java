package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.VulnException;
import com.tss.grc.module.cyber.repository.VulnExceptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link CyberVulnExceptionService} — extracted from the former CyberService
 * god-class (QUAL-CP-001). Locks exception approval (status + approvedAt stamp).
 */
@ExtendWith(MockitoExtension.class)
class CyberVulnExceptionServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ID     = 100L;

    @Mock private VulnExceptionRepository exceptionRepo;
    @InjectMocks private CyberVulnExceptionService service;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    @Test
    void approveException_sets_workflow_APPROVED_and_stamps_approvedAt() {
        VulnException ex = new VulnException();
        ex.setId(ID);
        ex.setWorkflowStatus("REQUESTED");
        when(exceptionRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(ex));
        when(exceptionRepo.save(any(VulnException.class))).thenAnswer(inv -> inv.getArgument(0));

        VulnException after = service.approveException(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("APPROVED");
        assertThat(after.getApprovedAt()).isEqualTo(LocalDate.now());
    }
}
