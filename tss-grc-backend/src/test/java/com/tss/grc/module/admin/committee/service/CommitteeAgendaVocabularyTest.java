package com.tss.grc.module.admin.committee.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CE-13 — the agenda's two key shapes, and the readers that each handled one of them.
 *
 * <h3>The measurement</h3>
 * Across all 34 dev meetings: <b>24</b> agendas — every one of the twelve real committees, in both
 * quarters, including the CAB's own next sitting {@code MTG-IT-26Q3} — key their items
 * {@code titleEn}/{@code titleAr}. The remaining 8 (older DEMO meetings) and <b>everything</b>
 * {@link CommitteeCalendarService#populateAgenda} builds key them {@code topicEn}/{@code topicAr}.
 * None carried both.
 *
 * <h3>Why that was not cosmetic</h3>
 * The frontend agenda list reads {@code topicEn} and so rendered an ordered list of <b>empty
 * bullets</b> for those 24. The CE-14a agenda-pack PDF read {@code titleEn} and so printed {@code —}
 * for the other 8 <em>and for every agenda the builder produces</em> — which is to say for exactly the
 * agendas S7b taught to carry the CAB queue. A control that reads the wrong key is not a weaker
 * control; it is a blank page that reads as a meeting with nothing to discuss. S7f's own probe passed
 * because the meeting it exported happened to be a seeded one.
 */
class CommitteeAgendaVocabularyTest {

    /** The shape the twelve real committees' meetings are seeded with. */
    private static final String SEEDED = """
        [{"seq":1,"titleEn":"Approval of previous minutes","titleAr":"اعتماد محضر الاجتماع السابق"},
         {"seq":2,"titleEn":"Approve the 2026 IT Portfolio","titleAr":"اعتماد المحفظة","mandatory":true}]
        """;

    /** The shape {@code rowOf} writes. */
    private static final String BUILT = """
        [{"seq":1,"topicEn":"Welcome, apologies, quorum check","topicAr":"الترحيب","presenter":"Chair",
          "minutesAllocated":5,"isMandatory":true},
         {"seq":2,"topicEn":"For decision: CHG-2025-0004 — migrate the database",
          "topicAr":"للقرار: CHG-2025-0004","minutesAllocated":15,"isMandatory":true,
          "sourceModule":"CHANGE"}]
        """;

    @Test
    @DisplayName("both key shapes read — neither population renders blank")
    void bothShapesAreReadable() {
        List<Map<String, Object>> seeded = CommitteeAgendaJson.items(SEEDED);
        List<Map<String, Object>> built = CommitteeAgendaJson.items(BUILT);

        assertThat(CommitteeAgendaJson.topic(seeded.get(0), false))
            .as("24 of 34 dev agendas are this shape; the frontend printed nothing for all of them")
            .isEqualTo("Approval of previous minutes");
        assertThat(CommitteeAgendaJson.topic(seeded.get(0), true)).isEqualTo("اعتماد محضر الاجتماع السابق");
        assertThat(CommitteeAgendaJson.topic(built.get(0), false))
            .as("everything populateAgenda builds is this shape; the agenda pack printed a dash")
            .isEqualTo("Welcome, apologies, quorum check");
        assertThat(CommitteeAgendaJson.topic(built.get(0), true)).isEqualTo("الترحيب");
    }

    @Test
    @DisplayName("an Arabic reader falls through to English rather than to an empty cell")
    void arabicFallsBackToEnglish() {
        List<Map<String, Object>> items = CommitteeAgendaJson.items(
            "[{\"seq\":1,\"topicEn\":\"Matters arising\"}]");
        assertThat(CommitteeAgendaJson.topic(items.get(0), true))
            .as("a title the reader can read in the other language beats a blank line")
            .isEqualTo("Matters arising");
    }

    @Test
    @DisplayName("an item that names nothing returns null, so a caller can say so")
    void anUnnamedItemIsNullNotEmpty() {
        List<Map<String, Object>> items = CommitteeAgendaJson.items("[{\"seq\":1,\"presenter\":\"Chair\"}]");
        assertThat(CommitteeAgendaJson.topic(items.get(0), false)).isNull();
        assertThat(CommitteeAgendaJson.topic(null, false)).isNull();
    }

    @Test
    @DisplayName("mandatory is read under either key — the built items all printed 'Standing' before")
    void mandatoryUnderEitherKey() {
        assertThat(CommitteeAgendaJson.mandatory(CommitteeAgendaJson.items(BUILT).get(0)))
            .as("the builder writes isMandatory; the export's item-type column read mandatory")
            .isTrue();
        assertThat(CommitteeAgendaJson.mandatory(CommitteeAgendaJson.items(SEEDED).get(1))).isTrue();
        assertThat(CommitteeAgendaJson.mandatory(CommitteeAgendaJson.items(SEEDED).get(0))).isFalse();
    }

    @Test
    @DisplayName("decision items are found by sourceModule, not by the display prefix")
    void decisionItemsAreKeyedOnTheModule() {
        assertThat(CommitteeAgendaJson.itemsFrom(BUILT, "CHANGE")).isEqualTo(1);
        assertThat(CommitteeAgendaJson.topicsFrom(BUILT, "CHANGE"))
            .singleElement().asString()
            .as("the caller matches on the reference code inside the line, which is data the builder "
              + "puts there; the 'For decision: ' prefix is display text in two languages")
            .contains("CHG-2025-0004");
        assertThat(CommitteeAgendaJson.itemsFrom(BUILT, "ACTIONS")).isZero();
        assertThat(CommitteeAgendaJson.itemsFrom(SEEDED, "CHANGE"))
            .as("a seeded agenda tables no change — which is the CE-13 finding, not a parse failure")
            .isZero();
        assertThat(CommitteeAgendaJson.itemsFrom(BUILT, null)).isZero();
    }

    /** A charter standing item tagged with a source module, next to a real referral line. */
    private static final String MIXED = """
        [{"seq":1,"topicEn":"Change-management major changes","presenter":"CIO",
          "minutesAllocated":10,"isMandatory":true,"sourceModule":"CHANGE"},
         {"seq":2,"topicEn":"For decision: CHG-4 — migrate the database","minutesAllocated":15,
          "isMandatory":true,"sourceModule":"CHANGE","decisionRef":"CHG-4"}]
        """;

    @Test
    @DisplayName("a standing item is not a tabled referral, even sharing its source module")
    void standingItemsAreNotTabledReferrals() {
        // ⚠ The browser found this: dev's CAB agenda reported "Change items tabled: 1" with no change
        // tabled at all — the one match was the charter's standing row "Change-management major
        // changes". A module tag says who the line is about; only a reference says which record.
        assertThat(CommitteeAgendaJson.itemsFrom(MIXED, "CHANGE"))
            .as("two lines carry the module tag")
            .isEqualTo(2);
        assertThat(CommitteeAgendaJson.tabledRefs(MIXED, "CHANGE"))
            .as("but exactly one tables a record")
            .containsExactly("CHG-4");
        assertThat(CommitteeAgendaJson.tabledRefs(SEEDED, "CHANGE")).isEmpty();
        assertThat(CommitteeAgendaJson.tabledRefs(MIXED, null)).isEmpty();
    }

    @Test
    @DisplayName("a referral line written before the reference key still reports as tabled")
    void preCe13ReferralLinesStillCount() {
        String legacy = """
            [{"seq":1,"topicEn":"For decision: CHG-9 — something","sourceModule":"CHANGE"}]
            """;
        assertThat(CommitteeAgendaJson.tabledRefs(legacy, "CHANGE"))
            .as("dropping it would report a tabled item as untabled and send someone to rebuild an "
              + "agenda that is already correct")
            .singleElement().asString().contains("CHG-9");
    }

    @Test
    @DisplayName("an unbuilt or unparseable agenda is empty, never an exception")
    void agendaFailuresAreEmptyNotFatal() {
        assertThat(CommitteeAgendaJson.items(null)).isEmpty();
        assertThat(CommitteeAgendaJson.items("")).isEmpty();
        assertThat(CommitteeAgendaJson.items("   ")).isEmpty();
        assertThat(CommitteeAgendaJson.items("not json at all")).isEmpty();
        assertThat(CommitteeAgendaJson.items("{\"seq\":1}"))
            .as("an object where an array belongs is a corrupt agenda, not a one-item one")
            .isEmpty();
    }

    @Test
    @DisplayName("the canonical writer keys are the ones the builder emits")
    void writersKeepOneShape() {
        // If rowOf is ever changed to emit titleEn, this constant and that method disagree and the
        // whole point of naming a canonical key is lost.
        assertThat(CommitteeAgendaJson.CANONICAL_TITLE_EN).isEqualTo("topicEn");
        assertThat(CommitteeAgendaJson.CANONICAL_TITLE_AR).isEqualTo("topicAr");
        assertThat(CommitteeAgendaJson.CANONICAL_MANDATORY).isEqualTo("isMandatory");
        assertThat(CommitteeAgendaJson.items(BUILT).get(0))
            .containsKeys(CommitteeAgendaJson.CANONICAL_TITLE_EN,
                          CommitteeAgendaJson.CANONICAL_TITLE_AR,
                          CommitteeAgendaJson.CANONICAL_MANDATORY);
    }
}
