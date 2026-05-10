package com.tss.grc.module.incident.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * IncidentService — Sprint 4 §4.1 test backlog starter.
 *
 * Locks the NIST SP 800-61r2 phase machine + status mapping + regulator
 * notification side effects. The 6-phase chain is:
 *
 *   IDENTIFICATION → ANALYSIS → CONTAINMENT → ERADICATION → RECOVERY → POST_INCIDENT
 *
 * Status auto-derives from phase (OPEN / INVESTIGATING / RESOLVED) and
 * `closeIncident` flips to CLOSED + sets resolutionDate.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long INC_ID = 42L;

    @Mock private IncidentRepository repo;
    @InjectMocks private IncidentService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Incident incident(String phase) {
        Incident i = new Incident();
        i.setId(INC_ID);
        i.setOrganisationId(ORG_ID);
        i.setNistPhase(phase);
        i.setStatus("OPEN");
        return i;
    }

    // ─── advancePhase ────────────────────────────────────────────────

    @Test
    void advancePhase_walks_full_NIST_chain() {
        // IDENTIFICATION → ANALYSIS → CONTAINMENT → ERADICATION → RECOVERY → POST_INCIDENT
        String[] chain = { "IDENTIFICATION", "ANALYSIS", "CONTAINMENT", "ERADICATION", "RECOVERY", "POST_INCIDENT" };
        for (int i = 0; i < chain.length - 1; i++) {
            when(repo.findById(INC_ID)).thenReturn(Optional.of(incident(chain[i])));
            when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
            Map<String, Object> snap = service.advancePhase(INC_ID);
            assertThat(snap.get("nistPhase")).isEqualTo(chain[i + 1]);
        }
    }

    @Test
    void advancePhase_at_terminal_POST_INCIDENT_throws() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("POST_INCIDENT")));

        assertThatThrownBy(() -> service.advancePhase(INC_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("terminal phase POST_INCIDENT");
    }

    @Test
    void advancePhase_unknown_phase_treated_as_IDENTIFICATION() {
        // Defensive: corrupted/legacy record with unknown phase advances to ANALYSIS
        Incident i = incident("WHATEVER");
        when(repo.findById(INC_ID)).thenReturn(Optional.of(i));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snap = service.advancePhase(INC_ID);
        assertThat(snap.get("nistPhase")).isEqualTo("ANALYSIS");
    }

    // ─── setPhase ────────────────────────────────────────────────────

    @Test
    void setPhase_jumps_to_target_and_remaps_status() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("IDENTIFICATION")));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snap = service.setPhase(INC_ID, "RECOVERY");
        assertThat(snap.get("nistPhase")).isEqualTo("RECOVERY");
        assertThat(snap.get("status")).isEqualTo("RESOLVED");      // RECOVERY → RESOLVED per phaseToStatus
    }

    @Test
    void setPhase_invalid_phase_throws() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("IDENTIFICATION")));

        assertThatThrownBy(() -> service.setPhase(INC_ID, "FAKE_PHASE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("phase must be one of");
    }

    @Test
    void setPhase_to_CONTAINMENT_maps_status_to_INVESTIGATING() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("IDENTIFICATION")));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snap = service.setPhase(INC_ID, "CONTAINMENT");
        assertThat(snap.get("status")).isEqualTo("INVESTIGATING");
    }

    // ─── closeIncident ───────────────────────────────────────────────

    @Test
    void closeIncident_sets_status_CLOSED_and_resolution_date() {
        Incident i = incident("RECOVERY");
        when(repo.findById(INC_ID)).thenReturn(Optional.of(i));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snap = service.closeIncident(INC_ID);
        assertThat(snap.get("status")).isEqualTo("CLOSED");
        assertThat(i.getResolutionDate()).isNotNull();
    }

    // ─── notifyRegulator ─────────────────────────────────────────────

    @Test
    void notifyRegulator_NCA_HEY_stamps_correct_field() {
        Incident i = incident("ANALYSIS");
        when(repo.findById(INC_ID)).thenReturn(Optional.of(i));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyRegulator(INC_ID, "NCA_HEY");
        assertThat(i.getNcaHeyNotifiedAt()).isNotNull();
    }

    @Test
    void notifyRegulator_SAMA_stamps_correct_field() {
        Incident i = incident("CONTAINMENT");
        when(repo.findById(INC_ID)).thenReturn(Optional.of(i));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyRegulator(INC_ID, "SAMA");
        assertThat(i.getSamaNotifiedAt()).isNotNull();
    }

    @Test
    void notifyRegulator_unknown_throws() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));

        assertThatThrownBy(() -> service.notifyRegulator(INC_ID, "NOT_A_REGULATOR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("regulator must be one of");
    }

    // ─── Tenant scope ────────────────────────────────────────────────

    @Test
    void advancePhase_unknown_id_throws_EntityNotFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advancePhase(999L))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Incident not found");
    }

    @Test
    void advancePhase_other_tenant_throws_EntityNotFound() {
        // Filter by org happens in require(); incident with other org_id should not be reachable
        Incident other = incident("IDENTIFICATION");
        other.setOrganisationId(999L);
        when(repo.findById(INC_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.advancePhase(INC_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
