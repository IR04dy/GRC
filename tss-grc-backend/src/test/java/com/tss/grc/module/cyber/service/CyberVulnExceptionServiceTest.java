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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // ── F2-328: a create is always born REQUESTED; approval is the approve path's ──────────────

    @Test
    void saveException_cannotBeBornApproved() {
        VulnException e = new VulnException();
        e.setTitleEn("Accept SQLi on legacy portal");
        e.setWorkflowStatus("APPROVED");            // the bypass: born approved, skipping approveException
        e.setApprovedAt(LocalDate.of(2020, 1, 1));  // and a forged approval date
        assertThatThrownBy(() -> service.saveException(e, ORG_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("approve");
    }

    @Test
    void saveException_defaultsToRequested_andScrubsApprovedAt() {
        VulnException e = new VulnException();
        e.setTitleEn("Accept SQLi on legacy portal");
        // workflowStatus omitted (null, no initialiser); a forged approvedAt supplied
        e.setApprovedAt(LocalDate.of(2020, 1, 1));
        when(exceptionRepo.save(any(VulnException.class))).thenAnswer(inv -> inv.getArgument(0));

        VulnException saved = service.saveException(e, ORG_ID);
        assertThat(saved.getWorkflowStatus()).isEqualTo("REQUESTED");
        assertThat(saved.getApprovedAt()).as("approvedAt belongs to the approve path (F2-213)").isNull();
    }

}
