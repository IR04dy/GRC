package com.tss.grc.module.reporting.dto;

import com.tss.grc.module.reporting.dto.BoardPackAggregate.Scope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-5 — the contract between the one builder and its three consumers.
 *
 * <p>Collapsing two renderers onto one aggregate introduced a shape that can be partially absent:
 * the deck renders seven of the nine sections, and building the other two for it would have meant
 * three queries per active framework plus a call into the sustainability dashboard for a document
 * that shows neither. {@link Scope} makes that explicit, and the cost of explicit is that a
 * {@code DECK} aggregate carries two {@code null} sections — so anything walking the aggregate has
 * to tolerate them. This class pins that, and the live PPTX probe proves the deck renderer actually
 * does.
 */
class BoardPackAggregateShapeTest {

    @Test
    void deckScopeOmitsExactlyTheTwoSectionsTheDeckDoesNotRender() {
        assertThat(Scope.FULL.includesFrameworks()).isTrue();
        assertThat(Scope.FULL.includesSustainability()).isTrue();

        assertThat(Scope.DECK.includesFrameworks())
            .as("the deck has no framework-coverage slide; building it costs three queries per "
              + "active framework")
            .isFalse();
        assertThat(Scope.DECK.includesSustainability())
            .as("the deck has no ESG slide; building it calls the composed sustainability dashboard")
            .isFalse();
    }

    @Test
    void rowCountTalliesItemisedRowsAndSurvivesTheDeckShape() {
        BoardPackAggregate deck = new BoardPackAggregate(
            null, null,
            List.of(risk(1), risk(2), risk(3)),
            null,
            List.of(finding(1), finding(2)),
            new BoardPackAggregate.Kris(1, 0, 0, 0, List.of(kri("K1"))),
            null,                                    // frameworkCoverage — absent under DECK
            null, null,
            null);                                   // sustainability — absent under DECK

        assertThat(deck.rowCount())
            .as("3 risks + 2 findings + 1 KRI, with the two DECK-absent sections contributing "
              + "nothing rather than throwing — this value is what the export audit trail records "
              + "as the volume that left the building (F2-41)")
            .isEqualTo(6);
    }

    @Test
    void rowCountCountsFrameworkRowsWhenPresent() {
        BoardPackAggregate full = new BoardPackAggregate(
            null, null, List.of(risk(1)), null, List.of(),
            new BoardPackAggregate.Kris(0, 0, 0, 0, List.of()),
            List.of(framework("ECC"), framework("PDPL")),
            null, null, null);

        assertThat(full.rowCount()).isEqualTo(3);
    }

    /**
     * The bilingual accessors fall back to English rather than rendering an empty cell — Arabic
     * names are mandatory on create, but historical rows predate that rule.
     */
    @Test
    void bilingualAccessorsFallBackToEnglishWhenArabicIsAbsent() {
        BoardPackAggregate.RiskRow withAr =
            new BoardPackAggregate.RiskRow(1L, "R-00001", "Outage", "انقطاع", 20, 12, "OPEN");
        BoardPackAggregate.RiskRow withoutAr =
            new BoardPackAggregate.RiskRow(2L, "R-00002", "Outage", null, 20, 12, "OPEN");

        assertThat(withAr.title(true)).isEqualTo("انقطاع");
        assertThat(withAr.title(false)).isEqualTo("Outage");
        assertThat(withoutAr.title(true))
            .as("an Arabic pack must not render a blank risk title just because titleAr is null")
            .isEqualTo("Outage");
    }

    @Test
    void kriBandTotalCoversEveryKriIncludingTheUnmeasured() {
        BoardPackAggregate.Kris k = new BoardPackAggregate.Kris(4, 2, 1, 3, List.of());
        assertThat(k.total())
            .as("the deck sizes its band bar from this total; excluding no-data KRIs would make the "
              + "bar claim full coverage the tenant does not have")
            .isEqualTo(10);
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private static BoardPackAggregate.RiskRow risk(long id) {
        return new BoardPackAggregate.RiskRow(id, "R-" + id, "Risk " + id, null, 10, 5, "OPEN");
    }

    private static BoardPackAggregate.FindingRow finding(long id) {
        return new BoardPackAggregate.FindingRow(id, "Finding " + id, "HIGH", "owner", 12L, "OPEN");
    }

    private static BoardPackAggregate.KriRow kri(String code) {
        return new BoardPackAggregate.KriRow(1L, code, "KRI", null, "GREEN", null, null, null);
    }

    private static BoardPackAggregate.FrameworkRow framework(String code) {
        return new BoardPackAggregate.FrameworkRow(code, code, null, 3, 2, 50, 1);
    }
}
