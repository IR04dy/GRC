package com.tss.grc.module.sust.annex.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.ghg.service.GhgEmissionRecordService;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AnnexGeneratorService — S-I: the SGI / CMA / TCFD sections read the live
 * registers (were hardcoded arrays), counts reflect live sizes, and the HTML
 * is RTL.
 */
@ExtendWith(MockitoExtension.class)
class AnnexGeneratorServiceTest {

    private static final Long ORG = 1L;
    private static final short YEAR = 2026;

    @Mock private GhgEmissionRecordRepository ghgRepo;
    @Mock private GhgEmissionRecordService ghgService;
    @Mock private ClimateRiskOpportunityRepository climateRepo;
    @Mock private SgiCommitmentRepository sgiRepo;
    @Mock private CmaDisclosureRepository cmaRepo;
    @Mock private TcfdItemRepository tcfdRepo;
    @InjectMocks private AnnexGeneratorService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static SgiCommitment sgi() {
        SgiCommitment c = new SgiCommitment();
        c.setId(1L); c.setOrganisationId(ORG); c.setSector("ENERGY");
        c.setTitleEn("Live SGI net-zero 2060"); c.setTitleAr("حياد كربوني 2060");
        c.setCurrentValue(new BigDecimal("8.4")); c.setTargetValue(new BigDecimal("100"));
        c.setUnit("%"); c.setStatus("ON_TRACK"); c.setTargetYear((short) 2060);
        return c;
    }
    private static CmaDisclosure cma() {
        CmaDisclosure d = new CmaDisclosure();
        d.setId(1L); d.setOrganisationId(ORG); d.setReportingYear(YEAR);
        d.setArticle("91"); d.setClause("(a)"); d.setDisclosureStatus("DISCLOSED");
        d.setTitleEn("Live CMA board oversight"); d.setEvidenceRef("AR-2026 §3");
        return d;
    }
    private static TcfdItem tcfd() {
        TcfdItem t = new TcfdItem();
        t.setId(1L); t.setOrganisationId(ORG); t.setReportingYear(YEAR);
        t.setPillar("GOVERNANCE"); t.setRecommendationCode("a"); t.setDisclosureStatus("DISCLOSED");
        t.setTitleEn("Live TCFD board oversight");
        return t;
    }

    @Test
    void generate_readsLiveSgiCmaTcfd_notHardcoded() {
        when(ghgService.aggregate(YEAR)).thenReturn(Map.of());
        when(ghgService.yoy(YEAR)).thenReturn(Map.of());
        when(ghgRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());
        when(climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(eq(ORG), any())).thenReturn(Page.empty());
        when(sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(eq(ORG), any()))
                .thenReturn(new PageImpl<>(List.of(sgi())));
        when(cmaRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of(cma()));
        when(tcfdRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of(tcfd()));

        AnnexGeneratorService.AnnexContent out = service.generate(YEAR, "TSS Bank", "بنك تس");

        // live titles present; the old hardcoded SGI headline is gone
        assertThat(out.markdown())
                .contains("Live SGI net-zero 2060")
                .contains("Live CMA board oversight")
                .contains("Live TCFD board oversight")
                .doesNotContain("Net-zero greenhouse gases by 2060");
        // counts reflect the live registers (not the old 7 / 11 / 11)
        assertThat(out.json())
                .contains("\"sgiCommitmentCount\": 1")
                .contains("\"cmaArticleCount\": 1")
                .contains("\"tcfdItemCount\": 1");
        // RTL fix
        assertThat(out.html()).contains("dir=\"rtl\"");
    }

    @Test
    void generate_emptyRegisters_renderPlaceholders() {
        when(ghgService.aggregate(YEAR)).thenReturn(Map.of());
        when(ghgService.yoy(YEAR)).thenReturn(Map.of());
        when(ghgRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());
        when(climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(eq(ORG), any())).thenReturn(Page.empty());
        when(sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(eq(ORG), any())).thenReturn(Page.empty());
        when(cmaRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());
        when(tcfdRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());

        AnnexGeneratorService.AnnexContent out = service.generate(YEAR, "TSS Bank", "بنك");

        assertThat(out.markdown()).contains("No SGI commitments registered");
        assertThat(out.json()).contains("\"cmaArticleCount\": 0");
    }
}
