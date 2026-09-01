package com.tss.grc.module.rrp.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.rrp.dto.RrpDtos.CreateIndicatorRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.DashboardResponse;
import com.tss.grc.module.rrp.entity.RrpCycle;
import com.tss.grc.module.rrp.repository.RrpCycleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-396 — an RRP recovery indicator is a 3-band RAG whose GREEN/AMBER thresholds must be
 * strictly ordered per direction (HIGHER_IS_BETTER needs green &gt; amber; LOWER_IS_BETTER
 * needs green &lt; amber) for {@code computeBand} to produce correct bands. No write path
 * enforced that ordering, so an inverted pair silently mis-banded — a breaching SAMA recovery
 * indicator read GREEN and the {@code RRP_INDICATOR_THRESHOLD_BREACHED} escalation never fired.
 * {@code validateThresholdOrder} now guards the sole threshold writer, {@code addIndicator},
 * at its first line (mirroring VulnerabilitySlaPolicyService.validatePctOrder).
 *
 * <p>Hermetic: the rejection happens before any repository/tenant interaction, so the service
 * is built with null collaborators — the throw fires before any is used ([[F2-320]] / [[F2-325]]).
 */
@ExtendWith(MockitoExtension.class)
class RrpServiceTest {

    private static final Long ORG = 1L;

    private RrpService service;

    /** Only {@code dashboard()} needs a collaborator, and only this one (F2-421). */
    @Mock private RrpCycleRepository cycleRepo;

    @BeforeEach void setUp() {
        service = new RrpService(null, null, null, null, null, null, null);
        TenantContext.setCurrentTenant(ORG);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static CreateIndicatorRequest indicator(String direction, String green, String amber) {
        return CreateIndicatorRequest.builder()
                .direction(direction)
                .greenThreshold(new BigDecimal(green))
                .amberThreshold(new BigDecimal(amber))
                .build();
    }

    /** Discriminating: a HIGHER_IS_BETTER indicator with green &lt; amber (inverted) is rejected
     *  at addIndicator's first line. Pre-fix (guard call removed) the pair reaches the persistence
     *  path instead, so no IllegalArgumentException is thrown and this leg goes RED. */
    @Test void addIndicator_rejectsInvertedHigherIsBetterThresholds() {
        assertThatThrownBy(() -> service.addIndicator(1L, indicator("HIGHER_IS_BETTER", "11.0", "13.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greenThreshold")
                .hasMessageContaining("greater than");
    }

    /** Discriminating: a LOWER_IS_BETTER indicator with green &gt; amber (inverted) is rejected. */
    @Test void addIndicator_rejectsInvertedLowerIsBetterThresholds() {
        assertThatThrownBy(() -> service.addIndicator(1L, indicator("LOWER_IS_BETTER", "5.0", "3.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greenThreshold")
                .hasMessageContaining("less than");
    }

    /** Guard (non-discriminating): correctly-ordered thresholds (per the RrpIndicator worked
     *  examples — CET1 green 13 &gt; amber 11; NPL green 3 &lt; amber 5) and any null threshold pass. */
    @Test void validateThresholdOrder_acceptsCorrectlyOrderedAndNullThresholds() {
        assertThatCode(() -> {
            RrpService.validateThresholdOrder("HIGHER_IS_BETTER", new BigDecimal("13.0"), new BigDecimal("11.0"));
            RrpService.validateThresholdOrder("LOWER_IS_BETTER", new BigDecimal("3.0"), new BigDecimal("5.0"));
            RrpService.validateThresholdOrder("HIGHER_IS_BETTER", null, new BigDecimal("11.0"));
            RrpService.validateThresholdOrder("LOWER_IS_BETTER", new BigDecimal("3.0"), null);
        }).doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════════════════════════════════
    // F2-421 — dashboard() derived its overdue count and its indicator sums from a 50-row
    // fiscal-year window, beside four all-time countBy figures in the same method.
    //
    // fiscal_year DESC keeps the NEWEST cycles; an overdue cycle is one whose submission_due_date
    // has PASSED, which correlates with OLDER fiscal years — so the cap dropped exactly the rows
    // the figure looks for. ADVERSE, not merely orthogonal, and it under-reported: the flattering
    // direction (F2-193 / F2-195 / F2-210).
    //
    // Two derivations in the same method are CORRECT — currentRecoveryCapacitySarM reads element 0
    // (the newest fiscal year) and recentCycles takes .limit(10) of the same ordering — so a
    // blanket ban on capping would fail on correct code (F2-214). Hence one uncapped list feeding
    // every derivation (F2-352 / F2-406 / F2-415 / F2-420), plus an exactly-asserted census of the
    // caps that remain.
    // ══════════════════════════════════════════════════════════════════════════════════════════

    /**
     * DISCRIMINATING. An ACTIVE cycle past its own submission due date, lying OUTSIDE the newest
     * fiscal years, must still be counted. Pre-fix the window is all {@code dashboard()} can see,
     * so it reports 0 and this leg goes RED on the VALUE.
     *
     * <p>Both finders are stubbed {@code lenient()} on purpose: the reverted service calls the
     * paged one and the fixed service calls the uncapped one, so a strict stub would make the
     * control fail with {@code UnnecessaryStubbingException} rather than on the figure (F2-406).
     */
    @Test void overdueCountsEveryActiveCycleNotJustTheNewestFiscalYears() {
        RrpCycle newest = cycle(2026, "IN_PREPARATION", LocalDate.of(2099, 12, 31), 1, 2, 500L);
        RrpCycle recent = cycle(2025, "APPROVED",       LocalDate.of(2099, 12, 31), 3, 4, 400L);
        RrpCycle old    = cycle(2010, "IN_PREPARATION", LocalDate.of(2011, 3, 31),  5, 6, 100L);

        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(ORG))
            .thenReturn(List.of(newest, recent, old));
        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(eqOrg(), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(newest, recent)));

        DashboardResponse d = dashboard();

        assertThat(d.getOverdueCycleCount())
            .as("FY2010 is IN_PREPARATION and 15 years past its due date. It falls outside the "
              + "newest fiscal years, which is exactly where an overdue cycle ends up — so a "
              + "windowed count reports 0 and a late SAMA recovery-plan filing is invisible")
            .isEqualTo(1L);

        verify(cycleRepo, never()).findByOrganisationIdOrderByFiscalYearDesc(eqOrg(), any());
    }

    /**
     * DISCRIMINATING. The comment on the fetch says "Sum indicators across all cycles for this
     * org", so the sum must span every cycle rather than the window. Pre-fix: red 4, amber 6.
     */
    @Test void indicatorSumsSpanEveryCycleNotTheWindow() {
        RrpCycle newest = cycle(2026, "IN_PREPARATION", LocalDate.of(2099, 12, 31), 1, 2, 500L);
        RrpCycle recent = cycle(2025, "APPROVED",       LocalDate.of(2099, 12, 31), 3, 4, 400L);
        RrpCycle old    = cycle(2010, "CLOSED",         LocalDate.of(2011, 3, 31),  5, 6, 100L);

        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(ORG))
            .thenReturn(List.of(newest, recent, old));
        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(eqOrg(), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(newest, recent)));

        DashboardResponse d = dashboard();

        assertThat(d.getIndicatorsRed()).as("1 + 3 + 5 across every cycle").isEqualTo(9L);
        assertThat(d.getIndicatorsAmber()).as("2 + 4 + 6 across every cycle").isEqualTo(12L);
    }

    /**
     * GUARD (non-discriminating). {@code currentRecoveryCapacitySarM} reads element 0 of the
     * ordering — the newest fiscal year — so for THAT figure the cap order IS the question it asks
     * and the uncapped list must preserve it. This is the half a blanket de-capping would break.
     */
    @Test void currentRecoveryCapacityStillComesFromTheNewestFiscalYear() {
        RrpCycle newest = cycle(2026, "APPROVED", LocalDate.of(2099, 12, 31), 0, 0, 777L);
        RrpCycle older  = cycle(2019, "CLOSED",   LocalDate.of(2020, 1, 1),   0, 0, 111L);

        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(ORG))
            .thenReturn(List.of(newest, older));
        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(eqOrg(), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(newest, older)));

        assertThat(dashboard().getCurrentRecoveryCapacitySarM()).isEqualTo(777L);
    }

    /**
     * GUARD (non-discriminating). {@code recentCycles} is a PREVIEW: {@code .limit(10)} of the same
     * ordering, newest first. A capped list is fine to RENDER and wrong to AGGREGATE (F2-197), and
     * that distinction is the whole point of this slice — so the preview cap is asserted to survive.
     */
    @Test void recentCyclesIsStillTheTenNewestAsAPreview() {
        List<RrpCycle> twelve = new ArrayList<>();
        for (int fy = 2026; fy > 2014; fy--) {
            twelve.add(cycle(fy, "CLOSED", LocalDate.of(2099, 12, 31), 0, 0, 0L));
        }
        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(ORG)).thenReturn(twelve);
        lenient().when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(eqOrg(), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(twelve));

        assertThat(dashboard().getRecentCycles()).hasSize(10);
        assertThat(dashboard().getRecentCycles().get(0).getFiscalYear()).isEqualTo(2026);
    }

    /**
     * STRUCTURAL. The window is gone from {@code dashboard()} and the uncapped overload is what it
     * calls. Asserted as a CALL SHAPE rather than a bare token, because a token is satisfied by an
     * import or by prose (F2-194).
     *
     * <p>⚠ Comments MUST be stripped: the fix's own comment quotes {@code PageRequest.of(0, 50)} to
     * record what it replaced, so a raw scan reads the epitaph as the corpse and fails on a CORRECT
     * tree (F2-193 / F2-415).
     */
    @Test void theFiscalYearWindowIsGoneFromTheDashboard() {
        String dash = stripJavaComments(methodBody(rrpServiceSource(), "public DashboardResponse dashboard()"));

        assertThat(dash)
            .as("dashboard() must not cap its own population — that is the defect")
            .doesNotContain("PageRequest");
        assertThat(Pattern.compile("findByOrganisationIdOrderByFiscalYearDesc\\s*\\(\\s*org\\(\\)\\s*\\)")
                .matcher(dash).find())
            .as("it must call the UNCAPPED overload; a bare token would be satisfied by the paged call")
            .isTrue();
        assertThat(dash)
            .as("a positive control on the stripper: it must not have eaten the real code")
            .contains("getSubmissionDueDate")
            .contains("ACTIVE_STATUSES")
            .contains("overdueCycleCount")
            .contains("indicatorsRed");
    }

    /**
     * CENSUS, asserted EXACTLY rather than as a ceiling — a new capped fetch must fail this leg and
     * be read (F2-188), and a ceiling would absorb it inside a tolerated total (F2-420).
     *
     * <p>Each survivor is correct for its own reason, which is why the instrument is a census and
     * not a ban: a ban would fail on correct code (F2-214).
     */
    @Test void everyRemainingCapInTheServiceIsNamed() {
        Map<String, String> expected = Map.of(
            "PageRequest.of(page, size)",
            "listCycles — a genuine paged read whose page and size come from the caller. There is "
          + "no figure derived from it, so there is nothing for a cap to make false.",
            "PageRequest.of(0, 200)",
            "nextOrdinal — F2-356's documented SPLIT-METHOD count-seed minter: it returns a long "
          + "its caller formats, so count-seed and reference build sit in different methods and "
          + "that gate cannot see it. F2-260 / F2-355 / F2-356 each read it and left it filed "
          + "(one ordinal per fiscal year), so it is NOT re-opened here.",
            ".limit(10)",
            "recentCycles — a preview taken from the uncapped ordered list. A capped list is fine "
          + "to RENDER and wrong to AGGREGATE (F2-197); this one renders.");

        Set<String> found = capsIn(stripJavaComments(rrpServiceSource()));

        assertThat(found)
            .as("every cap in RrpService must be named with a reason. A new one fails here and "
              + "must be read before it is tolerated")
            .containsExactlyInAnyOrderElementsOf(expected.keySet());

        // Floors AFTER the primary claim (F2-414 / F2-417): a floor that fires first turns a real
        // finding into output that reads as "the detector changed".
        assertThat(found).as("anti-vacuity: a detector that stops matching must fail, not pass")
            .hasSizeGreaterThanOrEqualTo(3);
        assertThat(expected.values()).allSatisfy(reason ->
            assertThat(reason.length())
                .as("a bare label is not a reason (F2-188)")
                .isGreaterThan(60));
    }

    // ─── F2-421 fixtures and source helpers ──────────────────────────────────────────────────

    /** {@code dashboard()} reads only {@code cycleRepo}; the other six collaborators stay null. */
    private DashboardResponse dashboard() {
        return new RrpService(cycleRepo, null, null, null, null, null, null).dashboard();
    }

    private static Long eqOrg() { return org.mockito.ArgumentMatchers.eq(ORG); }

    private static RrpCycle cycle(int fiscalYear, String status, LocalDate due,
                                  int red, int amber, long capacity) {
        RrpCycle c = new RrpCycle();
        c.setId((long) fiscalYear);
        c.setOrganisationId(ORG);
        c.setReference("RRP-" + fiscalYear + "-001");
        c.setFiscalYear(fiscalYear);
        c.setTitleEn("Recovery Plan FY" + fiscalYear);
        c.setTitleAr("خطة التعافي " + fiscalYear);
        c.setStatus(status);
        c.setSubmissionDueDate(due);
        c.setIndicatorsRed(red);
        c.setIndicatorsAmber(amber);
        c.setTotalRecoveryCapacitySarM(capacity);
        return c;
    }

    private static String rrpServiceSource() {
        Path p = Paths.get("src", "main", "java", "com", "tss", "grc",
                           "module", "rrp", "service", "RrpService.java");
        try {
            return Files.readString(p.toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read RrpService.java", e);
        }
    }

    /**
     * A CHARACTER scanner, not a regex — deliberately. {@code GuardExpressions.stripComments} is the
     * shared primitive and is the regex alternation form, which {@code MeasuredBaselineFreshnessTest}
     * records throwing {@code StackOverflowError} on this codebase's larger files (the JVM recurses
     * per iteration of {@code [\s\S]*?}). A scanner is also immune to a {@code //} inside a string
     * literal. Consolidating the scanners into {@code support/} is filed, not done here.
     */
    private static String stripJavaComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {                       // a literal is not a comment
                char q = c;
                out.append(c);
                i++;
                while (i < n) {
                    char d = src.charAt(i);
                    out.append(d);
                    i++;
                    if (d == '\\' && i < n) { out.append(src.charAt(i)); i++; continue; }
                    if (d == q) break;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;    // keep the newline
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i = Math.min(i + 2, n);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The brace-balanced body of the method whose signature line contains {@code signature}. */
    private static String methodBody(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at).as("signature not found: " + signature).isNotNegative();
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int j = open; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, j + 1);
        }
        throw new IllegalStateException("unbalanced braces after " + signature);
    }

    /** Every cap expression in the source, whitespace-normalised. */
    private static Set<String> capsIn(String src) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = Pattern.compile("PageRequest\\.of\\s*\\(([^)]*)\\)|\\.limit\\s*\\(\\s*(\\d+)\\s*\\)")
                .matcher(src);
        while (m.find()) {
            found.add(m.group(1) != null
                ? "PageRequest.of(" + m.group(1).replaceAll("\\s+", " ").trim() + ")"
                : ".limit(" + m.group(2) + ")");
        }
        return found;
    }
}
