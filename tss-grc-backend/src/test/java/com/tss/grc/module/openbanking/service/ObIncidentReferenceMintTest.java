package com.tss.grc.module.openbanking.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.openbanking.dto.ObDtos.RaiseIncidentRequest;
import com.tss.grc.module.openbanking.entity.ObIncident;
import com.tss.grc.module.openbanking.repository.ObApiCallRepository;
import com.tss.grc.module.openbanking.repository.ObConsentRepository;
import com.tss.grc.module.openbanking.repository.ObEventRepository;
import com.tss.grc.module.openbanking.repository.ObIncidentRepository;
import com.tss.grc.module.openbanking.repository.ObTppRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-337 — <b>an open-banking incident reference is minted from the highest existing ordinal
 * (a MAX), never from a COUNT.</b>
 *
 * <p>{@code generateIncidentReference} used to build {@code OBI-<year>-NNN} from
 * {@code count(this-year incidents among the 500 most recent) + 1}. A count re-mints an ordinal
 * already in use whenever a reference is absent from the counted set — beyond the 500-row window
 * (deterministic past 500 incidents/year) or after a soft-delete — and {@code ux_ob_incident_reference}
 * (a filtered unique index on {@code (organisation_id, reference) WHERE is_deleted = 0}) then rejects
 * the insert with a 500 on a SAMA-reportable register. This is F2-48's collision minter, and the fix
 * mirrors {@link com.tss.grc.module.consumer.complaints.service.ComplaintService}: seed from the MAX
 * ordinal, then a collision-skip loop backstopped by the unique index.
 *
 * <p>Pure synchronous minting logic, fully visible to a mocked repository — no DB, tenant,
 * row_version or collation dimension — so verified hermetically (the F2-320/F2-325 calibration
 * standard). {@code tppId} is left null so {@code raiseIncident} never touches the consent/TPP path.
 */
@ExtendWith(MockitoExtension.class)
class ObIncidentReferenceMintTest {

    private static final Long ORG = 7L;

    @Mock private ObTppRepository tppRepo;
    @Mock private ObConsentRepository consentRepo;
    @Mock private ObApiCallRepository apiCallRepo;
    @Mock private ObIncidentRepository incidentRepo;
    @Mock private ObEventRepository eventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private ObService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static RaiseIncidentRequest req() {
        return RaiseIncidentRequest.builder()
                .incidentType("API_OUTAGE").severity("HIGH")
                .titleEn("Outage").titleAr("انقطاع")
                .samaReportable(Boolean.TRUE)
                .build();   // tppId null on purpose — skips recountTpp/loadTpp
    }

    private static ObIncident withRef(String reference) {
        ObIncident i = new ObIncident();
        i.setReference(reference);
        return i;
    }

    private String mint() {
        when(incidentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<ObIncident> captor = ArgumentCaptor.forClass(ObIncident.class);
        service.raiseIncident(req());
        verify(incidentRepo).save(captor.capture());
        return captor.getValue().getReference();
    }

    // ── the defect: a count re-mints a used ordinal; a MAX does not ───────────────────────────────

    @Test void mintsFromTheMaxOrdinalNotACount_soAGapDoesNotReuseAReference() {
        int year = LocalDate.now().getYear();
        // Highest LIVE reference is -003 (e.g. -001/-002 soft-deleted, or older ones beyond the old
        // 500-row window). A COUNT of the live rows would give 1 → ordinal 2 → OBI-year-002, colliding
        // with a used slot; the MAX gives 3 → ordinal 4.
        when(incidentRepo.findTopByOrganisationIdAndReferenceStartingWithOrderByReferenceDesc(
                eq(ORG), any())).thenReturn(Optional.of(withRef("OBI-" + year + "-003")));
        when(incidentRepo.existsByOrganisationIdAndReference(eq(ORG), any())).thenReturn(false);

        assertThat(mint()).isEqualTo("OBI-" + year + "-004");
    }

    @Test void skipsACollidingOrdinal_backstoppedByTheUniqueIndex() {
        int year = LocalDate.now().getYear();
        when(incidentRepo.findTopByOrganisationIdAndReferenceStartingWithOrderByReferenceDesc(
                eq(ORG), any())).thenReturn(Optional.of(withRef("OBI-" + year + "-003")));
        // A concurrent create already took -004; the loop must step over it rather than 500.
        when(incidentRepo.existsByOrganisationIdAndReference(ORG, "OBI-" + year + "-004")).thenReturn(true);
        when(incidentRepo.existsByOrganisationIdAndReference(ORG, "OBI-" + year + "-005")).thenReturn(false);

        assertThat(mint()).isEqualTo("OBI-" + year + "-005");
    }

    @Test void firstIncidentOfTheYearStartsAtOne() {
        int year = LocalDate.now().getYear();
        when(incidentRepo.findTopByOrganisationIdAndReferenceStartingWithOrderByReferenceDesc(
                eq(ORG), any())).thenReturn(Optional.empty());
        when(incidentRepo.existsByOrganisationIdAndReference(eq(ORG), any())).thenReturn(false);

        assertThat(mint()).isEqualTo("OBI-" + year + "-001");
    }

    // ── grounding: the minted reference always matches the canonical zero-padded format ───────────

    @Test void referenceMatchesTheCanonicalFormat() {
        when(incidentRepo.findTopByOrganisationIdAndReferenceStartingWithOrderByReferenceDesc(
                eq(ORG), any())).thenReturn(Optional.empty());
        when(incidentRepo.existsByOrganisationIdAndReference(eq(ORG), any())).thenReturn(false);

        assertThat(mint()).matches("OBI-\\d{4}-\\d{3}");
    }
}
