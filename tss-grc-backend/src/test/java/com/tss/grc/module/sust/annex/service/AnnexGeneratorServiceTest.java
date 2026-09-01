package com.tss.grc.module.sust.annex.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
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
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
        when(climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(ORG)).thenReturn(List.of());
        when(sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(ORG))
                .thenReturn(List.of(sgi()));
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
        when(climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(ORG)).thenReturn(List.of());
        when(sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(ORG)).thenReturn(List.of());
        when(cmaRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());
        when(tcfdRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());

        AnnexGeneratorService.AnnexContent out = service.generate(YEAR, "TSS Bank", "بنك");

        assertThat(out.markdown()).contains("No SGI commitments registered");
        assertThat(out.json()).contains("\"cmaArticleCount\": 0");
    }

    /**
     * F2-415 — the annex's climate and SGI sections must read the <b>uncapped</b>
     * registers, because {@code buildJson} publishes their sizes as
     * {@code climateRiskCount} / {@code sgiCommitmentCount} in a CMA Articles 91-94
     * annual-report annex. Pre-fix they came through {@code PageRequest.of(0, 50)} and
     * {@code PageRequest.of(0, 100)}, so the machine-readable half of a document that
     * leaves the platform reported the window as the total (F2-197: a capped list is
     * fine to RENDER and wrong to COUNT), and the rendered sections truncated silently.
     *
     * <p>Both paged overloads are stubbed {@code lenient()} to a SHORTER window, so a
     * regression fails on the <b>value</b> — 50 rather than 60 — instead of surfacing as
     * an {@code UnnecessaryStubbingException} that reads like an unrelated harness fault
     * (F2-406's pattern).
     */
    @Test
    void generate_countsClimateAndSgiOverTheWholeRegisterNotACappedWindow() {
        when(ghgService.aggregate(YEAR)).thenReturn(Map.of());
        when(ghgService.yoy(YEAR)).thenReturn(Map.of());
        when(ghgRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());
        when(cmaRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());
        when(tcfdRepo.findByOrganisationIdAndReportingYear(ORG, YEAR)).thenReturn(List.of());

        List<ClimateRiskOpportunity> allClimates = climates(60);
        List<SgiCommitment> allSgi = sgis(120);

        // The whole register — what the annex must count and render.
        when(climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(ORG)).thenReturn(allClimates);
        when(sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(ORG)).thenReturn(allSgi);
        // The pre-fix windows, deliberately shorter and deliberately lenient.
        lenient().when(climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(eq(ORG), any()))
                .thenReturn(new PageImpl<>(allClimates.subList(0, 50)));
        lenient().when(sgiRepo.findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(eq(ORG), any()))
                .thenReturn(new PageImpl<>(allSgi.subList(0, 100)));

        AnnexGeneratorService.AnnexContent out = service.generate(YEAR, "TSS Bank", "بنك تس");

        // The counts published to downstream consumers.
        assertThat(out.json())
                .contains("\"climateRiskCount\": 60")
                .contains("\"sgiCommitmentCount\": 120");
        // And the rendered sections carry the rows the window would have dropped.
        assertThat(out.markdown())
                .contains("CLIMATE-060")
                .contains("SGI-120");
    }

    private static List<ClimateRiskOpportunity> climates(int n) {
        List<ClimateRiskOpportunity> out = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ClimateRiskOpportunity c = new ClimateRiskOpportunity();
            c.setId((long) i);
            c.setOrganisationId(ORG);
            c.setCategory("PHYSICAL");
            c.setKind("RISK");
            c.setRiskScore((short) (n - i + 1));          // riskScore DESC == insertion order
            c.setTitleEn(String.format("CLIMATE-%03d", i));
            c.setStatus("OPEN");
            c.setTimeHorizon("SHORT");
            out.add(c);
        }
        return out;
    }

    private static List<SgiCommitment> sgis(int n) {
        List<SgiCommitment> out = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            SgiCommitment c = new SgiCommitment();
            c.setId((long) i);
            c.setOrganisationId(ORG);
            c.setSector("ENERGY");
            c.setTitleEn(String.format("SGI-%03d", i));
            c.setStatus("ON_TRACK");
            c.setTargetYear((short) (2030 + i));
            out.add(c);
        }
        return out;
    }

    // ── F2-415 structural leg ────────────────────────────────────────

    /**
     * The annex generator must fetch <b>no capped population at all</b>.
     *
     * <p>{@code buildJson} publishes a size for every one of the five registers
     * {@code generate} reads — {@code recordCount}, {@code climateRiskCount},
     * {@code cmaArticleCount}, {@code tcfdItemCount}, {@code sgiCommitmentCount} — into a
     * CMA Articles 91-94 annual-report annex, i.e. a document that leaves the platform.
     * So capping <em>any</em> of them puts a window behind an all-time label. The
     * behaviour leg above covers only the two F2-415 uncapped; this one covers the other
     * three, whose counts are equally exposed and which no assertion had ever named.
     *
     * <p>⚠ The scan strips comments in ONE left-to-right alternation (F2-198 / F2-211).
     * The fix's own comment quotes {@code PageRequest.of(0, 50)} to record what it
     * replaced, and a raw scan would read that epitaph as the corpse (F2-193) — failing
     * on a correct tree, the one failure a gate must never have (F2-214). The positive
     * control below proves the detector still fires on the real shape.
     */
    @Test
    void theAnnexGeneratorFetchesNoCappedPopulation() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/tss/grc/module/sust/annex/service/AnnexGeneratorService.java"));
        String code = stripComments(src);

        assertThat(code)
            .as("a capped fetch in the annex generator becomes a windowed figure wearing "
                + "an all-time label, because buildJson counts every register it reads")
            .doesNotContain("PageRequest")
            .doesNotContain("findTop")
            .doesNotContain(".limit(");

        // Anti-vacuity: the stripper must not have eaten the method under review.
        assertThat(code)
            .as("the stripped source must still contain the fetch block")
            .contains("findByOrganisationIdOrderByRiskScoreDescIdDesc(org)")
            .contains("findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(org)");

        // Positive control: the detector fires on the literal pre-fix shape, and the
        // stripper does NOT rescue it just because a comment nearby mentions it.
        String prefix = "var climates = climateRepo.findByOrganisationIdOrderByRiskScoreDescIdDesc(org,\n"
                      + "    PageRequest.of(0, 50)).getContent();";
        assertThat(stripComments(prefix)).contains("PageRequest");
        assertThat(stripComments("// PageRequest.of(0, 50) is what this replaced\nvar x = repo.all(org);"))
            .doesNotContain("PageRequest");
    }

    /**
     * Strip line and block comments in ONE left-to-right alternation, preserving
     * newlines so line numbers survive (F2-242). Written as a single alternation
     * because two sequential passes let a {@code /*} inside a {@code //} comment run
     * away to the next closing delimiter — the bug that deleted 45% of {@code App.tsx}
     * (F2-198). The {@code (?<!:)} guard keeps a {@code https://} URL from opening a
     * line comment.
     */
    private static String stripComments(String s) {
        return java.util.regex.Pattern
            .compile("(?<!:)//[^\n]*|/\\*[\\s\\S]*?\\*/")
            .matcher(s)
            .replaceAll(m -> m.group().replaceAll("[^\n]", " "));
    }
}
