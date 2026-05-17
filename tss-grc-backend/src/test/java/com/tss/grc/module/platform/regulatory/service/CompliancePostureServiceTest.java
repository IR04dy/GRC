package com.tss.grc.module.platform.regulatory.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlMapping;
import com.tss.grc.module.platform.controls.repository.ControlMappingRepository;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocument;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CompliancePostureService — F5/F6 coverage math lock.
 *
 * <ul>
 *   <li>coverageFor counts mapped vs gap citations correctly and
 *       groups mapped controls by domain.</li>
 *   <li>Coverage strength promotion: a citation mapped by both a FULL
 *       and a PARTIAL control counts as FULL.</li>
 *   <li>coveragePct = mapped / total × 100 (one-decimal rounding).</li>
 *   <li>getCitationView raises EntityNotFoundException when the citation
 *       isn't in tenant.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CompliancePostureServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long DOC_ID = 100L;

    @Mock private AuthorityDocumentRepository documents;
    @Mock private AuthorityDocumentCitationRepository citations;
    @Mock private ControlMappingRepository mappings;
    @Mock private ControlRepository controls;
    @InjectMocks private CompliancePostureService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static AuthorityDocument doc() {
        AuthorityDocument d = new AuthorityDocument();
        d.setId(DOC_ID); d.setOrganisationId(ORG_ID);
        d.setCode("NCA-ECC-V2"); d.setFamily("NCA Cyber"); d.setIssuer("NCA");
        d.setVersion("2.0"); d.setTitleEn("ECC"); d.setJurisdiction("KSA");
        return d;
    }

    private static AuthorityDocumentCitation cit(long id, String code) {
        AuthorityDocumentCitation c = new AuthorityDocumentCitation();
        c.setId(id); c.setOrganisationId(ORG_ID); c.setAuthorityDocumentId(DOC_ID);
        c.setCitationCode(code); c.setTitleEn("C-" + code);
        return c;
    }

    private static ControlMapping map(long id, long ctrlId, long citId, String coverage) {
        ControlMapping m = new ControlMapping();
        m.setId(id); m.setOrganisationId(ORG_ID);
        m.setControlId(ctrlId); m.setCitationId(citId); m.setCoverage(coverage);
        return m;
    }

    private static Control ctrl(long id, String domain) {
        Control c = new Control();
        c.setId(id); c.setOrganisationId(ORG_ID);
        c.setControlCode("C-" + id); c.setDomain(domain);
        c.setTitleEn("ctrl"); c.setControlType("PREVENTIVE");
        c.setAutomationLevel("MANUAL"); c.setStatus("ACTIVE");
        c.setVersion("1.0");
        return c;
    }

    // ─── getCitationView ──────────────────────────────────────────

    @Test void getCitationView_notInTenantRaises() {
        when(citations.findByIdAndOrganisationId(99L, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCitationView(99L))
            .hasMessageContaining("Citation not found");
    }

    @Test void getCitationView_returnsParentDocFields() {
        when(citations.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(cit(7, "1-1")));
        when(documents.findByIdAndOrganisationId(DOC_ID, ORG_ID)).thenReturn(Optional.of(doc()));

        CompliancePostureService.CitationView out = service.getCitationView(7L);
        assertThat(out.citationCode()).isEqualTo("1-1");
        assertThat(out.authorityCode()).isEqualTo("NCA-ECC-V2");
        assertThat(out.authorityFamily()).isEqualTo("NCA Cyber");
    }

    // ─── coverageFor — math ───────────────────────────────────────

    @Test void coverage_countsMappedAndGapCorrectly() {
        when(documents.findByIdAndOrganisationId(DOC_ID, ORG_ID)).thenReturn(Optional.of(doc()));
        AuthorityDocumentCitation c1 = cit(1, "1-1");
        AuthorityDocumentCitation c2 = cit(2, "1-2");
        AuthorityDocumentCitation c3 = cit(3, "1-3");
        when(citations.findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(ORG_ID, DOC_ID))
            .thenReturn(List.of(c1, c2, c3));

        // c1 mapped FULL by ctrl 100 (CYBER); c2 mapped PARTIAL by ctrl 200 (BCM); c3 has no mapping.
        when(mappings.findByOrganisationIdAndCitationIdOrderByIdAsc(ORG_ID, 1L)).thenReturn(List.of(map(10, 100, 1, "FULL")));
        when(mappings.findByOrganisationIdAndCitationIdOrderByIdAsc(ORG_ID, 2L)).thenReturn(List.of(map(11, 200, 2, "PARTIAL")));
        when(mappings.findByOrganisationIdAndCitationIdOrderByIdAsc(ORG_ID, 3L)).thenReturn(List.of());
        when(controls.findByIdAndOrganisationId(100L, ORG_ID)).thenReturn(Optional.of(ctrl(100, "CYBER")));
        when(controls.findByIdAndOrganisationId(200L, ORG_ID)).thenReturn(Optional.of(ctrl(200, "BCM")));

        CompliancePostureService.DocumentCoverage cov = service.coverageFor(DOC_ID);
        assertThat(cov.totalCitations()).isEqualTo(3);
        assertThat(cov.mappedCitations()).isEqualTo(2);
        assertThat(cov.gapCitations()).isEqualTo(1);
        assertThat(cov.fullCoverage()).isEqualTo(1);
        assertThat(cov.partialCoverage()).isEqualTo(1);
        assertThat(cov.compensatingCoverage()).isEqualTo(0);
        assertThat(cov.controlsByDomain()).containsEntry("CYBER", 1).containsEntry("BCM", 1);
        assertThat(cov.coveragePct()).isEqualTo(66.7);  // 2/3 → 66.7%
        assertThat(cov.gaps()).hasSize(1);
        assertThat(cov.gaps().get(0).citationCode()).isEqualTo("1-3");
    }

    @Test void coverage_strongestCoverageWinsWhenMultiple() {
        when(documents.findByIdAndOrganisationId(DOC_ID, ORG_ID)).thenReturn(Optional.of(doc()));
        AuthorityDocumentCitation c1 = cit(1, "X");
        when(citations.findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(ORG_ID, DOC_ID))
            .thenReturn(List.of(c1));
        // One citation mapped by BOTH a PARTIAL and a FULL control — should count once as FULL.
        when(mappings.findByOrganisationIdAndCitationIdOrderByIdAsc(ORG_ID, 1L))
            .thenReturn(List.of(map(10, 100, 1, "PARTIAL"), map(11, 200, 1, "FULL")));
        when(controls.findByIdAndOrganisationId(100L, ORG_ID)).thenReturn(Optional.of(ctrl(100, "CYBER")));
        when(controls.findByIdAndOrganisationId(200L, ORG_ID)).thenReturn(Optional.of(ctrl(200, "BCM")));

        CompliancePostureService.DocumentCoverage cov = service.coverageFor(DOC_ID);
        assertThat(cov.fullCoverage()).isEqualTo(1);
        assertThat(cov.partialCoverage()).isEqualTo(0);
        // Both controls' domains still appear in the per-domain rollup.
        assertThat(cov.controlsByDomain()).containsKeys("CYBER", "BCM");
    }

    @Test void coverage_emptyDocReturnsZeroPct() {
        when(documents.findByIdAndOrganisationId(DOC_ID, ORG_ID)).thenReturn(Optional.of(doc()));
        when(citations.findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(ORG_ID, DOC_ID))
            .thenReturn(List.of());

        CompliancePostureService.DocumentCoverage cov = service.coverageFor(DOC_ID);
        assertThat(cov.totalCitations()).isZero();
        assertThat(cov.mappedCitations()).isZero();
        assertThat(cov.coveragePct()).isEqualTo(0.0);
    }

    // ─── heatmap — happy path ─────────────────────────────────────

    @Test void heatmap_returnsRowPerDoc() {
        AuthorityDocument d1 = doc();
        AuthorityDocument d2 = doc(); d2.setId(101L); d2.setCode("ISO-27001"); d2.setFamily("International");
        when(documents.findByOrganisationIdOrderByFamilyAscCodeAsc(ORG_ID)).thenReturn(List.of(d1, d2));
        // Coverage details exercised separately — for the heatmap we just want one row per doc.
        when(documents.findByIdAndOrganisationId(eq(DOC_ID), eq(ORG_ID))).thenReturn(Optional.of(d1));
        when(documents.findByIdAndOrganisationId(eq(101L), eq(ORG_ID))).thenReturn(Optional.of(d2));
        lenient().when(citations.findByOrganisationIdAndAuthorityDocumentIdOrderBySequenceOrderAsc(eq(ORG_ID), any()))
            .thenReturn(List.of());

        CompliancePostureService.PostureHeatmap heat = service.heatmap(null);
        assertThat(heat.rows()).hasSize(2);
        assertThat(heat.familyTotals())
            .containsEntry("NCA Cyber", 1L).containsEntry("International", 1L);
    }
}
