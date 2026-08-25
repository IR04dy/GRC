package com.tss.grc.module.reginspect.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateInspectionRequest;
import com.tss.grc.module.reginspect.entity.RegInspection;
import com.tss.grc.module.reginspect.repository.RegDocumentRequestRepository;
import com.tss.grc.module.reginspect.repository.RegInspectionRepository;
import com.tss.grc.module.reginspect.repository.RegInspectionUpdateRepository;
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
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-354 — {@code RegInspectService.generateReference} must mint the SAMA/NCA on-site-inspection
 * reference {@code <regulator>-OSI-<year>-Q<q>-NNN} from {@code MAX(ordinal) + 1}, not from a
 * {@code count(...) + 1} over a capped {@code PageRequest.of(0, 200)} window.
 *
 * <p>The old seed was wrong two ways: the derived finder respects {@code @Where(is_deleted = 0)}
 * (live rows only), and the 200-row window is itself capped — so a soft-delete gap, a concurrent
 * create, or more than 200 inspections in a quarter re-minted an in-use ordinal, and the filtered
 * unique index {@code ux_reginspect_external_reference (organisation_id, external_reference) WHERE
 * is_deleted = 0} (V341) then 500'd the INSERT (the F2-337 / F2-351 / F2-353 collision shape).
 *
 * <p>A unit test cannot trigger a real unique-index 500, so the proof is the SEEDING (MAX, not the
 * capped count — {@code verify(never())} the capped {@code Pageable} finder), the skip-loop over a
 * live collision, and the year+quarter scope of the seed pattern. Reverting to the capped-count body
 * makes these legs fail (the MAX/exists stubs go unused under strict stubbing, and the reverted body
 * calls the un-stubbed capped finder — a null {@code Page} it dereferences). F2-248: the service had
 * no test.
 */
@ExtendWith(MockitoExtension.class)
class RegInspectServiceTest {

    private static final Long ORG = 4L;

    @Mock private RegInspectionRepository inspectionRepo;
    @Mock private RegDocumentRequestRepository drRepo;
    @Mock private RegInspectionUpdateRepository updateRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private RegInspectService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** Drives the public create with a save stub, returning the external reference the service minted. */
    private String mintedReference() {
        when(inspectionRepo.save(any(RegInspection.class))).thenAnswer(inv -> {
            RegInspection i = inv.getArgument(0);
            i.setId(77L);
            return i;
        });
        service.createInspection(CreateInspectionRequest.builder()
                .regulator("SAMA").titleEn("On-site review").titleAr("مراجعة ميدانية").build());
        ArgumentCaptor<RegInspection> cap = ArgumentCaptor.forClass(RegInspection.class);
        verify(inspectionRepo).save(cap.capture());
        return cap.getValue().getExternalReference();
    }

    @Test
    void firstInspectionOrdinalIsOne() {
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), anyString())).thenReturn(0);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(eq(ORG), anyString()))
                .thenReturn(false);

        String ref = mintedReference();

        assertThat(ref).startsWith("SAMA-OSI-").endsWith("-001");
        // Sourcing (the defect): the ordinal comes from the uncapped MAX finder, never the capped
        // PageRequest window.
        verify(inspectionRepo, never())
                .findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class));
    }

    @Test
    void ordinalSeedsFromMaxNotCappedCount() {
        // Seven ordinals issued this quarter (some rows may be soft-deleted, so a live count over a
        // capped window would be lower and re-mint a live ordinal — the collision). MAX + 1 is next.
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), anyString())).thenReturn(7);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(eq(ORG), anyString()))
                .thenReturn(false);

        assertThat(mintedReference()).endsWith("-008");
        verify(inspectionRepo, never())
                .findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class));
    }

    @Test
    void ordinalSkipsAnExistingLiveReference() {
        // A peer create grabbed ordinal 6 in the window between the seed read and this INSERT; the
        // skip-loop (live-only existsBy) steps over it.
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), anyString())).thenReturn(5);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(
                eq(ORG), argThat(s -> s != null && s.endsWith("-006")))).thenReturn(true);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(
                eq(ORG), argThat(s -> s != null && s.endsWith("-007")))).thenReturn(false);

        assertThat(mintedReference()).endsWith("-007");
    }

    @Test
    void seedPatternScopesToYearAndQuarter() {
        ArgumentCaptor<String> pat = ArgumentCaptor.forClass(String.class);
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), pat.capture())).thenReturn(0);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(eq(ORG), anyString()))
                .thenReturn(false);

        mintedReference();

        // The MAX seed is scoped to every regulator's inspection this year+quarter (the ordinal is a
        // shared per-quarter sequence), so the pattern is %-OSI-<year>-Q<q>-%.
        assertThat(pat.getValue()).contains("-OSI-").contains("-Q").startsWith("%").endsWith("-%");
    }
}
