package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberIncident;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CyberIncidentService} — extracted from the former CyberService
 * god-class (QUAL-CP-001). Locks the 9-step incident response state machine:
 *
 *   DETECTION → LOGGING → TRIAGE → CONTAINMENT → INVESTIGATION → ERADICATION
 *             → RECOVERY → CLOSURE → LESSONS_LEARNED
 *
 * plus SEC-CYB-001 tenant-scoped soft delete (foreign id must not be touched).
 */
@ExtendWith(MockitoExtension.class)
class CyberIncidentServiceTest {

    private static final Long ORG_ID     = 1L;
    private static final Long ID         = 100L;
    private static final Long FOREIGN_ID = 999L;

    @Mock private CyberIncidentRepository incidentRepo;
    @InjectMocks private CyberIncidentService service;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    @Test
    void startResponse_walks_full_INCIDENT_workflow() {
        String[] chain = { "DETECTION", "LOGGING", "TRIAGE", "CONTAINMENT",
                           "INVESTIGATION", "ERADICATION", "RECOVERY", "CLOSURE", "LESSONS_LEARNED" };
        for (int i = 0; i < chain.length - 1; i++) {
            CyberIncident inc = new CyberIncident();
            inc.setId(ID);
            inc.setWorkflowStatus(chain[i]);
            when(incidentRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(inc));
            when(incidentRepo.save(any(CyberIncident.class))).thenAnswer(inv -> inv.getArgument(0));

            CyberIncident after = service.startResponse(ID);
            assertThat(after.getWorkflowStatus()).isEqualTo(chain[i + 1]);
        }
    }

    @Test
    void startResponse_at_LESSONS_LEARNED_is_no_op() {
        CyberIncident inc = new CyberIncident();
        inc.setId(ID);
        inc.setWorkflowStatus("LESSONS_LEARNED");
        when(incidentRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(inc));
        when(incidentRepo.save(any(CyberIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberIncident after = service.startResponse(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("LESSONS_LEARNED");
    }

    @Test
    void deleteIncident_soft_deletes_via_save() {
        CyberIncident inc = new CyberIncident();
        inc.setId(ID);
        when(incidentRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(inc));
        when(incidentRepo.save(any(CyberIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteIncident(ID);
        assertThat(inc.getIsDeleted()).isTrue();
    }

    // SEC-CYB-001 (cross-tenant IDOR) regression lock.
    @Test
    void deleteIncident_crossTenantId_throwsAndDoesNotSoftDelete() {
        when(incidentRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteIncident(FOREIGN_ID))
                .isInstanceOf(EntityNotFoundException.class);

        // Soft delete is a save() of is_deleted=true — must not happen for a
        // foreign row.
        verify(incidentRepo, never()).save(any(CyberIncident.class));
    }
}
