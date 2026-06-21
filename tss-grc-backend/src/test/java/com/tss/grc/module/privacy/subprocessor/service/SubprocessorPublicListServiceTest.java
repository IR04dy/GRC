package com.tss.grc.module.privacy.subprocessor.service;

import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.PublicSubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import com.tss.grc.module.privacy.subprocessor.repository.SubprocessorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SubprocessorPublicListService} — PDPL Article 12 transparency.
 *
 * <p>Locks:
 * <ul>
 *   <li>only APPROVED rows are returned</li>
 *   <li>RETIRED / SUSPENDED / PROPOSED / UNDER_DPO_REVIEW are filtered</li>
 *   <li>internal fields are stripped (vendorId, dpoApprovedByUserId,
 *       reviewedAt, DPA dates, lawfulBasis, authorisationType)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SubprocessorPublicListServiceTest {

    @Mock private SubprocessorRepository repo;
    private SubprocessorPublicListService service;

    @BeforeEach
    void setUp() { service = new SubprocessorPublicListService(repo); }

    private static Subprocessor row(String code, String legalName, String legalAr,
                                     String country, String safeguard, String stage) {
        Subprocessor s = new Subprocessor();
        s.setId((long) code.hashCode());
        s.setExternalId(UUID.randomUUID());
        s.setOrganisationId(1L);
        s.setSubprocessorCode(code);
        s.setSubProcessorLegalName(legalName);
        s.setSubProcessorLegalNameAr(legalAr);
        s.setCountryOfProcessing(country);
        s.setRegionOfProcessing(country.equals("SA") ? "Riyadh" : "Dublin");
        s.setProcessingPurposeEn("Hosting workloads for the bank");
        s.setProcessingPurposeAr("استضافة أعباء البنك");
        s.setCrossBorderSafeguardType(safeguard);
        s.setLifecycleStage(stage);
        // Internal-only fields the public list MUST strip:
        s.setVendorId(42L);
        s.setDpoApprovedByUserId(7L);
        s.setDpaSignedDate(LocalDate.now().minusDays(10));
        s.setLawfulBasis("CONTRACT");
        s.setAuthorisationType("GENERAL");
        return s;
    }

    @Test
    void publicList_onlyApprovedRowsReturned() {
        when(repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(1L, "APPROVED"))
                .thenReturn(List.of(
                        row("SUBP-2026-001", "Microsoft KSA", "مايكروسوفت", "SA", "KSA_RESIDENT", "APPROVED"),
                        row("SUBP-2026-002", "Microsoft IE",  "مايكروسوفت أيرلندا", "IE", "SCC_PLUS_TIA", "APPROVED")));

        List<PublicSubprocessorResponse> out = service.publicList(1L);
        assertThat(out).hasSize(2);
        // Repository contract verifies the filter at the query level.
        verify(repo).findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(1L, "APPROVED");
    }

    @Test
    void publicList_retiredFilteredAtRepoLayer() {
        // Repo returns only APPROVED rows — the service must rely on
        // the repo filter (not on a post-query check) so the SQL plan
        // stays sargable on the (org, lifecycle_stage) index.
        when(repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(1L, "APPROVED"))
                .thenReturn(List.of(row("SUBP-2026-001", "Microsoft KSA", "مايكروسوفت", "SA", "KSA_RESIDENT", "APPROVED")));

        List<PublicSubprocessorResponse> out = service.publicList(1L);
        assertThat(out).extracting(PublicSubprocessorResponse::subprocessorCode)
                .containsExactly("SUBP-2026-001");
    }

    @Test
    void publicList_strippedFieldsNotOnPublicResponse() {
        // PublicSubprocessorResponse only has these fields. Reflectively
        // verify that no internal field name leaked into the record
        // declaration — locks the API contract.
        var components = PublicSubprocessorResponse.class.getRecordComponents();
        var fieldNames = java.util.Arrays.stream(components)
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(fieldNames).doesNotContain(
                "vendorId", "dpoApprovedByUserId", "dpaSignedDate",
                "lawfulBasis", "authorisationType", "tiaReference",
                "dpaExpiryDate", "lastReviewedAt", "nextReviewDueAt",
                "createdAt", "updatedAt"
        );
    }

    @Test
    void publicList_responseExposesPublicCoreFields() {
        when(repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(1L, "APPROVED"))
                .thenReturn(List.of(row("SUBP-2026-001", "Microsoft KSA", "مايكروسوفت", "SA", "KSA_RESIDENT", "APPROVED")));

        PublicSubprocessorResponse r = service.publicList(1L).get(0);
        assertThat(r.subprocessorCode()).isEqualTo("SUBP-2026-001");
        assertThat(r.subProcessorLegalName()).isEqualTo("Microsoft KSA");
        assertThat(r.subProcessorLegalNameAr()).isEqualTo("مايكروسوفت");
        assertThat(r.countryOfProcessing()).isEqualTo("SA");
        assertThat(r.crossBorderSafeguardType()).isEqualTo("KSA_RESIDENT");
        assertThat(r.processingPurposeEn()).isNotBlank();
        assertThat(r.processingPurposeAr()).isNotBlank();
    }

    @Test
    void publicList_emptyWhenNoApproved() {
        when(repo.findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(1L, "APPROVED"))
                .thenReturn(List.of());

        assertThat(service.publicList(1L)).isEmpty();
    }

    @Test
    void publicList_nullOrgNeverRunsUnscopedRead() {
        // SEC-PRIV-003 defence-in-depth: a null/unresolved org must return
        // empty WITHOUT hitting the repository, so this method can never
        // perform an unscoped cross-tenant read.
        assertThat(service.publicList(null)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(repo);
    }
}
