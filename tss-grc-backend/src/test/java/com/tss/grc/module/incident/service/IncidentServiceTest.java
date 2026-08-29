package com.tss.grc.module.incident.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.entity.Incident;
import com.tss.grc.module.incident.repository.IncidentRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    // CY-C: closeIncident now auto-raises a CAPA when lessons-learned are present;
    // these workflow tests don't set lessons, so create() is never called.
    @Mock private com.tss.grc.module.capa.service.CapaTaskService capaService;
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

    // ─── F8 audit + DomainEvent emission ─────────────────────────────

    @Test
    void create_emits_INCIDENT_CREATED_event_and_audit() {
        when(repo.save(any(Incident.class))).thenAnswer(inv -> {
            Incident i = inv.getArgument(0);
            i.setId(INC_ID);
            return i;
        });
        IncidentRequest req = new IncidentRequest(
            "Ransomware on host-42", "هجوم فدية على host-42",
            "CRITICAL", "MALWARE", "OPEN", "soc@tss",
            null, null, "Encrypted shares observed", null, null, null);

        service.create(req);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        DomainEvent de = cap.getValue();
        assertThat(de.triggerEvent()).isEqualTo("INCIDENT_CREATED");
        assertThat(de.entityType()).isEqualTo("Incident");
        assertThat(de.payload()).containsEntry("severity", "CRITICAL");
        assertThat(de.payload()).containsEntry("category", "MALWARE");
        verify(audit).log(eq("CREATE"), eq("Incident"), eq(INC_ID), anyString());
    }

    @Test
    void advancePhase_emits_INCIDENT_PHASE_CHANGED_with_previousPhase() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.advancePhase(INC_ID);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        DomainEvent de = cap.getValue();
        assertThat(de.triggerEvent()).isEqualTo("INCIDENT_PHASE_CHANGED");
        assertThat(de.payload()).containsEntry("nistPhase", "CONTAINMENT");
        assertThat(de.payload()).containsEntry("previousPhase", "ANALYSIS");
        verify(audit).log(eq("PHASE_ADVANCE"), eq("Incident"), eq(INC_ID), anyString());
    }

    @Test
    void closeIncident_emits_INCIDENT_CLOSED_event() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("POST_INCIDENT")));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.closeIncident(INC_ID);

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        DomainEvent de = cap.getValue();
        assertThat(de.triggerEvent()).isEqualTo("INCIDENT_CLOSED");
        assertThat(de.payload()).containsEntry("status", "CLOSED");
        assertThat(de.payload()).containsEntry("previousPhase", "POST_INCIDENT");
        verify(audit).log(eq("CLOSE"), eq("Incident"), eq(INC_ID), anyString());
    }

    @Test
    void notifyRegulator_emits_INCIDENT_REGULATOR_NOTIFIED_with_regulator_in_payload() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyRegulator(INC_ID, "NCA_HEY");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        DomainEvent de = cap.getValue();
        assertThat(de.triggerEvent()).isEqualTo("INCIDENT_REGULATOR_NOTIFIED");
        assertThat(de.payload()).containsEntry("regulator", "NCA_HEY");
        assertThat(de.payload()).containsKey("notifiedAt");
        verify(audit).log(eq("REGULATOR_NOTIFIED"), eq("Incident"), eq(INC_ID), anyString());
    }
    // ─── F2-281: the PDPL classification and the bodies it feeds ─────

    /**
     * Builds a classified incident. It has to go through reflection because the
     * eight bridge columns are mapped {@code updatable = false} with
     * {@code @Setter(AccessLevel.NONE)} — only {@code CyberPdplBridgeService}'s
     * native SQL may write them, so no production API can build this state, and
     * reaching past the API here is the honest way to fixture it.
     */
    private static Incident classified(int subjects, String categories) {
        Incident i = incident("ANALYSIS");
        i.setTitle("Phishing Email Campaign Detected");
        i.setDiscoveryDate(java.time.LocalDate.of(2026, 4, 1));
        org.springframework.test.util.ReflectionTestUtils.setField(
            i, "affectsPersonalData", (short) 1);
        org.springframework.test.util.ReflectionTestUtils.setField(
            i, "dataSubjectsAffectedEst", subjects);
        org.springframework.test.util.ReflectionTestUtils.setField(
            i, "dataCategoriesAffected", categories);
        org.springframework.test.util.ReflectionTestUtils.setField(
            i, "piiClassifiedAt", java.time.LocalDateTime.of(2026, 5, 7, 11, 29));
        org.springframework.test.util.ReflectionTestUtils.setField(
            i, "linkedDataBreachCode", "07627381-1268-42EC-8E31-F8ED586093AC");
        return i;
    }

    private static String body(java.util.List<Map<String, Object>> tmpls, String code) {
        return tmpls.stream().filter(t -> code.equals(t.get("code"))).findFirst()
            .map(t -> (String) t.get("prefilledBody")).orElseThrow();
    }

    private static Map<String, Object> template(java.util.List<Map<String, Object>> tmpls, String code) {
        return tmpls.stream().filter(t -> code.equals(t.get("code"))).findFirst().orElseThrow();
    }

    /**
     * The finding. Measured live before the fix: incident 1 stores 1,450 data
     * subjects and EMAIL,NATIONAL_ID,PHONE_NUMBER, and the PDPL Article 20
     * notification the platform generated for the Saudi Data Office contained
     * neither — the two facts Article 20 turns on, omitted from the document
     * while the same row held them.
     */
    @Test
    void dataOfficeBody_carries_the_subject_count_and_the_data_categories() {
        when(repo.findById(INC_ID)).thenReturn(
            Optional.of(classified(1450, "EMAIL,NATIONAL_ID,PHONE_NUMBER")));

        String b = body(service.regulatoryTemplates(INC_ID), "DATA_OFFICE");

        assertThat(b).contains("PDPL Article 20");
        assertThat(b).contains("DATA SUBJECTS AFFECTED (EST.): 1450");
        assertThat(b).contains("CATEGORIES OF PERSONAL DATA AFFECTED: EMAIL,NATIONAL_ID,PHONE_NUMBER");
        assertThat(b).contains("LINKED PDPL BREACH RECORD: 07627381-1268-42EC-8E31-F8ED586093AC");

        // Found in the browser after every gate was green: LocalDateTime.toString()
        // emits `2026-05-07T11:29:52.134` -- a raw ISO stamp with milliseconds, two
        // lines under `DISCOVERED AT: 2026-04-01`, on a document going to a
        // regulator (F2-209 recorded the same blemish on the SDAIA console).
        assertThat(b).contains("PERSONAL DATA AFFECTED: CONFIRMED (classified 2026-05-07)");
        assertThat(b).doesNotContain("2026-05-07T");
    }

    /**
     * The load-bearing leg. On a breach notification "0 data subjects affected"
     * is a factual claim, so an unassessed count must read as absent — F2-213's
     * rule that a reset is worse than a null, at the point where the value
     * leaves the platform.
     */
    @Test
    void dataOfficeBody_renders_an_unassessed_count_as_absent_and_never_as_zero() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));

        String b = body(service.regulatoryTemplates(INC_ID), "DATA_OFFICE");

        assertThat(b).contains("DATA SUBJECTS AFFECTED (EST.): <not yet assessed>");
        assertThat(b).contains("CATEGORIES OF PERSONAL DATA AFFECTED: <not yet assessed>");
        assertThat(b).contains("PERSONAL DATA AFFECTED: NOT YET CLASSIFIED");
        assertThat(b).doesNotContain("AFFECTED (EST.): 0");
        assertThat(b).contains("LINKED PDPL BREACH RECORD: <none");
    }

    /**
     * The HRSD body printed {@code EMPLOYEES AFFECTED: <count>} and
     * {@code DATA CATEGORIES AFFECTED: <e.g. salary, ID, contract>} — literal
     * placeholders asking a human to supply values the row already stored.
     */
    @Test
    void hrsdBody_uses_the_stored_values_instead_of_asking_a_human_for_them() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(classified(380, "EMAIL,SALARY")));

        String b = body(service.regulatoryTemplates(INC_ID), "HRSD");

        assertThat(b).contains("EMPLOYEES AFFECTED: 380");
        assertThat(b).contains("DATA CATEGORIES AFFECTED: EMAIL,SALARY");
        assertThat(b).doesNotContain("<count>");
        assertThat(b).doesNotContain("<e.g. salary, ID, contract>");
    }

    /** And the placeholder survives where there is genuinely nothing to fill it from. */
    @Test
    void hrsdBody_still_discloses_an_unassessed_count() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));

        String b = body(service.regulatoryTemplates(INC_ID), "HRSD");

        assertThat(b).contains("EMPLOYEES AFFECTED: <not yet assessed>");
        assertThat(b).doesNotContain("EMPLOYEES AFFECTED: 0");
    }

    /**
     * Applicability was prose for a human to judge — right for SAMA and CITC,
     * which the platform cannot know, and wrong for PDPL Article 20, which is
     * exactly the question the stored classification answers.
     */
    @Test
    void everyTemplate_carries_the_classification_as_one_incident_level_fact() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(classified(1450, "EMAIL")));
        assertThat(service.regulatoryTemplates(INC_ID))
            .allSatisfy(t -> assertThat(t.get("piiClassification")).isEqualTo("CONFIRMED"));

        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));
        assertThat(service.regulatoryTemplates(INC_ID))
            .allSatisfy(t -> assertThat(t.get("piiClassification")).isEqualTo("NOT_CLASSIFIED"));
    }

    /**
     * The withdrawal, asserted rather than assumed. Suppressing the Article 20
     * notification for an unclassified incident would turn "nobody has assessed
     * this yet" into a clean bill on a statutory clock, and under-scoping a
     * statutory obligation is the finding an examiner writes up (F2-196/F2-197).
     */
    @Test
    void anUnclassifiedIncident_is_still_offered_the_Article20_notification() {
        when(repo.findById(INC_ID)).thenReturn(Optional.of(incident("ANALYSIS")));

        java.util.List<Map<String, Object>> tmpls = service.regulatoryTemplates(INC_ID);

        assertThat(tmpls).hasSize(5);
        Map<String, Object> pdpl = template(tmpls, "DATA_OFFICE");
        assertThat(pdpl.get("piiClassification")).isEqualTo("NOT_CLASSIFIED");
        assertThat(pdpl.get("deadlineHours")).isEqualTo(72);
        assertThat(pdpl).containsKey("overdue");
    }

    /**
     * The snapshot carried {@code dataOfficeNotifiedAt} — the Article 20
     * notification stamp — and not the classification that makes that
     * notification owed. Two of N, in one payload (F2-215).
     */
    @Test
    void phaseSnapshot_carries_the_fact_that_makes_the_PDPL_clock_owed() {
        when(repo.findById(INC_ID)).thenReturn(
            Optional.of(classified(1450, "EMAIL,NATIONAL_ID")));
        when(repo.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> snap = service.advancePhase(INC_ID);

        assertThat(snap).containsKey("dataOfficeNotifiedAt");
        assertThat(snap).containsEntry("affectsPersonalData", true);
        assertThat(snap).containsEntry("linkedDataBreachCode",
            "07627381-1268-42EC-8E31-F8ED586093AC");
        assertThat(snap.get("piiClassifiedAt")).isNotNull();
    }

    // ─── F2-345: MTTR (avgDays) averages over ALL resolved incidents, not the capped
    //             most-recently-created 200 window ─────────────────────────────────────
    private static Incident withDates(java.time.LocalDate discovery, java.time.LocalDate resolution) {
        Incident i = new Incident();
        i.setOrganisationId(ORG_ID);
        i.setDiscoveryDate(discovery);
        i.setResolutionDate(resolution);
        return i;
    }

    @Test
    void avgDays_averagesOverAllResolvedWithDates_notTheCappedRecentWindow() {
        // The uncapped resolved-with-dates set: day-diffs 2, 4, and -1 (a data error, dropped by
        // the d >= 0 guard) -> mean of {2, 4} = 3.0.
        java.util.List<Incident> resolved = java.util.List.of(
            withDates(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 3)),    // 2
            withDates(java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 2, 5)),    // 4
            withDates(java.time.LocalDate.of(2026, 3, 5), java.time.LocalDate.of(2026, 3, 4)));   // -1, dropped
        when(repo.findResolvedWithDates(ORG_ID)).thenReturn(resolved);
        // Had the service kept sourcing MTTR from the capped recent window it would read 15.0.
        org.mockito.Mockito.lenient().when(repo.findRecent(eq(ORG_ID), any())).thenReturn(java.util.List.of(
            withDates(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 11)),   // 10
            withDates(java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 2, 21))));  // 20

        var stats = service.getStats();

        assertThat(stats.avgDays()).isEqualTo(3.0);
        // The sourcing IS the fix: MTTR reads the uncapped resolved-with-dates set, and getStats
        // no longer touches the 200-capped most-recent window at all.
        verify(repo).findResolvedWithDates(ORG_ID);
        verify(repo, org.mockito.Mockito.never()).findRecent(any(), any());
    }

    // ─── F2-390: overdue boundary — a statutory deadline is breached AT the deadline ──
    //
    // overdue was `(deadlineHours - hoursSinceDiscovery) < 0`, and hoursSinceDiscovery is
    // ChronoUnit.HOURS.between(...) floored to whole hours, so a notification read
    // "not overdue" AT its exact deadline and for up to a full hour past it — on the
    // PDPL Article 20 72-hour clock (and NCA/CITC 24h, SAMA 48h). Inclusive `<= 0` flips
    // overdue at the exact hour boundary, matching RegulatorCountdownService
    // (`minutesLeft <= 0 -> BREACHED`) for the same regulator clock.
    //
    // template(...) takes hoursSinceDiscovery as a parameter, so the boundary is driven
    // directly here — regulatoryTemplates() computes it from LocalDateTime.now(), which a
    // unit test cannot pin to an exact deadline (F2-178). No stubbing: template() is a
    // pure function of its arguments.

    /** The discriminating leg: at exactly the deadline, unnotified, overdue must be true. */
    @Test
    void overdue_isTrueAtExactlyTheDeadline_whenNotNotified() {
        Map<String, Object> m = service.template(
            "DATA_OFFICE", "t", "t", 72, 72L, null, "chan", "hint", "body", "CONFIRMED");
        // Pre-fix (`0 < 0`) this was false; fixed (`0 <= 0`) it is true.
        assertThat(m).containsEntry("overdue", true);
        assertThat(m).containsEntry("hoursRemaining", 0L);
    }

    /** Guard: the whole hour before the deadline is not overdue (unchanged by the fix). */
    @Test
    void overdue_isFalseTheHourBeforeTheDeadline() {
        Map<String, Object> m = service.template(
            "DATA_OFFICE", "t", "t", 72, 71L, null, "chan", "hint", "body", "CONFIRMED");
        assertThat(m).containsEntry("overdue", false);
    }

    /** Guard: the notifiedAt == null half still gates — a notified breach is never overdue,
     *  even at the deadline, so the boundary fix did not drop the notified gate. */
    @Test
    void overdue_isFalseAtTheDeadline_whenAlreadyNotified() {
        Map<String, Object> m = service.template(
            "DATA_OFFICE", "t", "t", 72, 72L,
            java.time.LocalDateTime.of(2026, 5, 1, 10, 0), "chan", "hint", "body", "CONFIRMED");
        assertThat(m).containsEntry("overdue", false);
    }

    /** Guard: well past the deadline, unnotified, is still overdue (unchanged by the fix). */
    @Test
    void overdue_isTrueWellPastTheDeadline_whenNotNotified() {
        Map<String, Object> m = service.template(
            "DATA_OFFICE", "t", "t", 72, 100L, null, "chan", "hint", "body", "CONFIRMED");
        assertThat(m).containsEntry("overdue", true);
    }

}
