package com.tss.grc.module.sust.materiality.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import com.tss.grc.module.sust.materiality.repository.MaterialTopicRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MaterialTopicService — locks the S-Q double-materiality behaviour:
 * <ul>
 *   <li>the score is MAX(impact, financial), never the old average — a topic is
 *       as material as its stronger perspective;</li>
 *   <li>scoring writes both axes (and mirrors the legacy columns);</li>
 *   <li>out-of-range / null axes are rejected;</li>
 *   <li>the matrix classifies each topic as material-from-impact,
 *       material-from-financial, and/or double-material, with quadrant counts.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MaterialTopicServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private MaterialTopicRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private MaterialTopicService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test
    void score_isMaxOfBothAxes_notAverage() {
        MaterialTopic t = topic(3L, "DRAFT");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // impact 4.7, financial 2.0 → the OLD average was 3.35 (HIGH); double
        // materiality takes the MAX = 4.7 (CRITICAL).
        MaterialTopic out = service.score(3L, new BigDecimal("4.7"), new BigDecimal("2.0"));

        assertThat(out.getImpactMateriality()).isEqualByComparingTo("4.7");
        assertThat(out.getFinancialMateriality()).isEqualByComparingTo("2.0");
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("4.7");   // max, not 3.35
        assertThat(out.getPriorityTier()).isEqualTo("CRITICAL");
        assertThat(out.getStatus()).isEqualTo("SCORED");
        // legacy columns mirrored
        assertThat(out.getStakeholderImportance()).isEqualByComparingTo("4.7");
        assertThat(out.getBusinessImpact()).isEqualByComparingTo("2.0");
    }

    @Test
    void score_materialFromFinancialAxisAlone_stillCritical() {
        MaterialTopic t = topic(4L, "DRAFT");
        when(repo.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic out = service.score(4L, new BigDecimal("1.0"), new BigDecimal("4.2"));
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("4.2");
        assertThat(out.getPriorityTier()).isEqualTo("CRITICAL");
    }

    @Test
    void score_rejectsOutOfRangeAndNull() {
        MaterialTopic t = topic(5L, "DRAFT");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.score(5L, new BigDecimal("6"), new BigDecimal("3")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.score(5L, null, new BigDecimal("3")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_recomputesScoreFromAxes() {
        MaterialTopic req = topic(null, "DRAFT");
        req.setImpactMateriality(new BigDecimal("3.5"));
        req.setFinancialMateriality(new BigDecimal("1.0"));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic out = service.create(req);
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("3.5");
        assertThat(out.getPriorityTier()).isEqualTo("HIGH");
        assertThat(out.getStatus()).isEqualTo("DRAFT");   // create never scores past DRAFT
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateMatrix_classifiesEachQuadrant() {
        MaterialTopic dbl  = scored(11L, "4.0", "4.0"); // double-material
        MaterialTopic imp  = scored(12L, "4.0", "1.0"); // impact only
        MaterialTopic fin  = scored(13L, "1.0", "4.0"); // financial only
        MaterialTopic none = scored(14L, "1.0", "1.0"); // neither
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(dbl, imp, fin, none));

        Map<String, Object> m = service.generateMatrix((short) 2025);

        assertThat(m.get("impactMaterialCount")).isEqualTo(2);      // dbl + imp
        assertThat(m.get("financialMaterialCount")).isEqualTo(2);   // dbl + fin
        assertThat(m.get("doubleMaterialCount")).isEqualTo(1);      // dbl
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) m.get("matrix");
        assertThat(matrix).hasSize(4);
        Map<String, Object> dblEntry = matrix.stream()
            .filter(e -> Long.valueOf(11L).equals(e.get("id"))).findFirst().orElseThrow();
        assertThat(dblEntry.get("materialFromImpact")).isEqualTo(true);
        assertThat(dblEntry.get("materialFromFinancial")).isEqualTo(true);
        assertThat(dblEntry.get("doubleMaterial")).isEqualTo(true);
        assertThat(dblEntry).containsKeys("impactMateriality", "financialMateriality");
    }

    // ── helpers ──────────────────────────────────────────────────────
    // ── F2-237 — the range was a property of ONE route, not of the column ───────────────────────
    //
    // score() refused anything outside 0..5, mirrored the legacy pair and recomputed; create() and
    // update() wrote all four columns raw. The register's own schema said 0..5 about HALF of it —
    // ck_mt_stake_score and ck_mt_biz_score bound the LEGACY mirror and there was no constraint at
    // all on the canonical axes (F2-215's two-of-N shape, at the schema; V379 adds the other two).
    //
    // These legs are per ROUTE on purpose. A test that only drove score() is what let the other two
    // writers drift for as long as they existed.

    @Test
    void create_refusesAnAxisOutsideTheDeclaredRange() {
        MaterialTopic req = topic(null, "DRAFT");
        req.setImpactMateriality(new BigDecimal("9.9"));
        req.setFinancialMateriality(new BigDecimal("3.0"));

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("impactMateriality")
            .hasMessageContaining("9.9");
    }

    /**
     * ⚠⚠ THE LIVE DEFECT, and the reason a null-guard was never a guard here.
     *
     * <p>{@code PUT /api/sustainability/materiality/{id}} binds the ENTITY, and {@code MaterialTopic}
     * declares {@code = BigDecimal.ZERO} on all four score columns because they are NOT NULL. So a
     * request that omits the key deserialises to ZERO rather than to null, and
     * {@code if (req.getImpactMateriality() != null)} was true on <b>every</b> request: the line read
     * as a partial update and behaved as a full replace. Editing an unrelated field zeroed both axes,
     * and recomputeScore then took materiality_score to 0.0 and priority_tier to LOW.
     *
     * <p>This fixture is the wire: {@code new MaterialTopic()} holds exactly what Jackson produces
     * for a body that never mentions the axes.
     */
    @Test
    void update_cannotZeroTheAxesWhenTheCallerOmitsThem() {
        MaterialTopic cur = scored(10L, "4.4", "3.3");
        cur.setMaterialityScore(new BigDecimal("4.4"));
        cur.setPriorityTier("CRITICAL");
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic req = new MaterialTopic();          // == a PUT body with no axis keys
        assertThat(req.getImpactMateriality())
            .as("the initialiser is why the old null-guard could never fire — if this ever becomes "
                + "null, re-read update() before relaxing anything")
            .isNotNull();
        req.setOwnerName("Someone editing only the owner");

        MaterialTopic out = service.update(10L, req);

        assertThat(out.getImpactMateriality()).isEqualByComparingTo("4.4");
        assertThat(out.getFinancialMateriality()).isEqualByComparingTo("3.3");
        assertThat(out.getMaterialityScore()).isEqualByComparingTo("4.4");
        assertThat(out.getPriorityTier()).isEqualTo("CRITICAL");
    }

    /**
     * And it may not write them when the caller DOES send them either: the axes are owned by
     * {@code create()} and by {@code score()}, which is what this register already says out loud
     * ("Scoring is owned by the dedicated Score action"). An edit route that cannot write them
     * cannot zero them, and the legacy mirror cannot be driven apart from the canonical pair —
     * {@code stakeholder_importance} / {@code business_impact} are the same two facts under their
     * pre-S-Q names, and update() used to write all four independently (F2-196's disjoint-vocabulary
     * shape inside a single row; measured 0 disagreeing rows in dev and prod, so it was latent).
     */
    @Test
    void update_doesNotWriteTheAxesEvenWhenTheCallerSendsThem() {
        MaterialTopic cur = scored(8L, "4.0", "3.0");
        // a real stored row has the mirror in step — score() has always written both pairs, so a
        // fixture that leaves the mirror at its 0 initialiser would make the assertion below
        // measure the fixture rather than the route (F2-179: supply one thing at a time).
        cur.setStakeholderImportance(new BigDecimal("4.0"));
        cur.setBusinessImpact(new BigDecimal("3.0"));
        when(repo.findByIdAndOrganisationId(8L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic req = new MaterialTopic();
        req.setImpactMateriality(new BigDecimal("1.0"));
        req.setStakeholderImportance(new BigDecimal("5.0"));   // a second opinion on the same fact
        req.setFinancialMateriality(new BigDecimal("1.0"));
        req.setBusinessImpact(new BigDecimal("5.0"));

        MaterialTopic out = service.update(8L, req);

        assertThat(out.getImpactMateriality()).isEqualByComparingTo("4.0");
        assertThat(out.getFinancialMateriality()).isEqualByComparingTo("3.0");
        assertThat(out.getStakeholderImportance())
            .as("the mirror stays with its canonical axis, never with the caller's other number")
            .isEqualByComparingTo("4.0");
        assertThat(out.getBusinessImpact()).isEqualByComparingTo("3.0");
    }

    /**
     * 🔑 F2-188 — {@code recomputeScore} runs on every save, so an edit to a topic's owner name can
     * move its materiality score and its priority tier. The old audit line read "Material topic
     * updated" whatever moved: a duplicated derivation agrees with the original on the row and
     * differs on the audit trail, which is the part nothing asserts.
     *
     * <p>This is not hypothetical on this register. Measured 2026-08-18, byte-identical in dev and
     * prod: 20 of 26 live rows hold a materiality_score LOWER than MAX(impact, financial) and none
     * holds one higher, and 14 priority tiers would move on the next save — 12 of them to CRITICAL.
     * So the first operator to edit one of those rows for any reason will move a CMA Article 92(a)
     * disclosure input, and the trail has to say so.
     */
    @Test
    void update_auditNamesTheDerivedMoveWhenTheScoreOrTierChanges() {
        MaterialTopic cur = scored(11L, "4.6", "4.2");
        cur.setMaterialityScore(new BigDecimal("3.9"));      // the stored, stale derivation
        cur.setPriorityTier("HIGH");
        when(repo.findByIdAndOrganisationId(11L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic req = new MaterialTopic();
        req.setOwnerName("Editing only the owner");
        service.update(11L, req);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("UPDATE"), eq("MaterialTopic"), eq(11L), msg.capture());
        assertThat(msg.getValue())
            .contains("3.9")            // from
            .contains("4.6")            // to — MAX(4.6, 4.2)
            .contains("HIGH")
            .contains("CRITICAL");
    }

    @Test
    void update_auditStaysQuietWhenNeitherDerivedValueMoves() {
        MaterialTopic cur = scored(12L, "4.6", "4.2");
        cur.setMaterialityScore(new BigDecimal("4.6"));
        cur.setPriorityTier("CRITICAL");
        when(repo.findByIdAndOrganisationId(12L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialTopic req = new MaterialTopic();
        req.setOwnerName("Editing only the owner");
        service.update(12L, req);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("UPDATE"), eq("MaterialTopic"), eq(12L), msg.capture());
        assertThat(msg.getValue())
            .as("no derived move, so no invented one — F2-197: an invented all-clear closes a question")
            .isEqualTo("Material topic updated");
    }

    /**
     * The structural half, and the reason it exists: F2-231 measured that extracting a write into a
     * helper makes the old site invisible to a setter-site scan, so "the range is checked" has to be
     * pinned as "there is exactly one place that writes it".
     */
    @Test
    void everyAxisColumnHasExactlyOneWriterInTheService() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/tss/grc/module/sust/materiality/service/MaterialTopicService.java"),
            java.nio.charset.StandardCharsets.UTF_8);
        // strip comments in one left-to-right alternation — this file's own comments quote the
        // setter calls being counted (F2-198; two sequential passes eat 45% of a file).
        String code = src.replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", "");

        for (String setter : List.of("setImpactMateriality", "setFinancialMateriality",
                                     "setStakeholderImportance", "setBusinessImpact")) {
            long n = java.util.regex.Pattern.compile("\\b" + setter + "\\s*\\(")
                .matcher(code).results().count();
            assertThat(n)
                .as("%s must be written in exactly one place (applyAxes), so the range check, the "
                    + "legacy mirror and the recompute cannot be bypassed by a fourth write path",
                    setter)
                .isEqualTo(1L);
        }
        assertThat(code)
            .as("and that one place is the shared writer")
            .contains("private void applyAxes(");
    }

    private static MaterialTopic topic(Long id, String status) {
        MaterialTopic t = new MaterialTopic();
        t.setId(id);
        t.setOrganisationId(ORG_ID);
        t.setReportingYear((short) 2025);
        t.setCategory("ENVIRONMENTAL");
        t.setTopicEn("Climate change");
        t.setStatus(status);
        return t;
    }

    private static MaterialTopic scored(Long id, String impact, String financial) {
        MaterialTopic t = topic(id, "SCORED");
        t.setImpactMateriality(new BigDecimal(impact));
        t.setFinancialMateriality(new BigDecimal(financial));
        t.setPriorityTier("HIGH");
        return t;
    }
}
