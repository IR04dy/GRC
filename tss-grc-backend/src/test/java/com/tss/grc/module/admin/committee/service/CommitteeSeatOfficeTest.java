package com.tss.grc.module.admin.committee.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S6c — the seat-office classifier, tested against the titles that are actually in the databases.
 *
 * <p>The corpus is not invented: {@link CommitteeSeatOffice#MEASURED_TITLES} is the ten distinct
 * {@code renewal_status = 'ACTIVE'} values of {@code committee_member_terms.role} measured in dev and
 * production on 2026-08-03. A classifier tested only against titles the author thought of is a
 * classifier tested against the author.
 */
class CommitteeSeatOfficeTest {

    @Test
    @DisplayName("every title that exists in the databases classifies to something usable")
    void everyMeasuredTitleClassifies() {
        Map<String, CommitteeSeatOffice> got = new java.util.LinkedHashMap<>();
        for (String t : CommitteeSeatOffice.MEASURED_TITLES) got.put(t, CommitteeSeatOffice.classify(t));

        assertThat(got).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
            Map.entry("Member",               CommitteeSeatOffice.MEMBER),
            Map.entry("Standing Invitee",     CommitteeSeatOffice.OBSERVER),
            Map.entry("Chair",                CommitteeSeatOffice.CHAIR),
            Map.entry("Independent Director", CommitteeSeatOffice.MEMBER),
            Map.entry("Member (Independent)", CommitteeSeatOffice.MEMBER),
            Map.entry("Secretary",            CommitteeSeatOffice.SECRETARY),
            Map.entry("Vice-Chair",           CommitteeSeatOffice.DEPUTY_CHAIR),
            Map.entry("Chair (Independent)",  CommitteeSeatOffice.CHAIR),
            Map.entry("Chairman",             CommitteeSeatOffice.CHAIR),
            Map.entry("Executive Director",   CommitteeSeatOffice.MEMBER),
            // F2-177 — the six the CAB/ECAB rosters added on 2026-08-05, unnoticed for four days. This
            // test could not have caught the drift: a title absent from MEASURED_TITLES is simply not
            // asserted about, so the corpus growing is invisible to the corpus test. Only re-querying
            // the column finds it, which is why the constant's javadoc now carries two dates.
            Map.entry("CAB Chair",                CommitteeSeatOffice.CHAIR),
            Map.entry("CAB Member — Compliance",  CommitteeSeatOffice.MEMBER),
            Map.entry("CAB Member — Security",    CommitteeSeatOffice.MEMBER),
            Map.entry("ECAB Chair",               CommitteeSeatOffice.CHAIR),
            Map.entry("ECAB Member — Risk",       CommitteeSeatOffice.MEMBER),
            Map.entry("ECAB Member — Compliance", CommitteeSeatOffice.MEMBER)));

        // Anti-vacuity: an empty or shrunken corpus would make the assertion above trivially true.
        assertThat(CommitteeSeatOffice.MEASURED_TITLES).hasSize(16);
    }

    @Test
    @DisplayName("F2-177 — an unreadable title is UNCLASSIFIED, and that is what the drift risked")
    void anUnreadableTitleIsNotQuietlyAMember() {
        // All six titles the drift introduced happen to classify correctly, which is luck, not design.
        // `CAB Convenor` is the counter-example: a plausible tenant title that this classifier cannot
        // read. It must land in UNCLASSIFIED — reported — rather than defaulting to MEMBER, because a
        // chair-required quorum for a committee whose chair reads as an ordinary member is quietly
        // unsatisfiable. Asserted here so the *consequence* of a stale corpus is pinned, not just its size.
        assertThat(CommitteeSeatOffice.classify("CAB Convenor"))
            .isEqualTo(CommitteeSeatOffice.UNCLASSIFIED);
        assertThat(CommitteeSeatOffice.UNCLASSIFIED.presides()).isFalse();
    }

    @Test
    @DisplayName("three spellings of the same office all read as CHAIR — the reason this class exists")
    void theChairIsWrittenThreeWays() {
        // `role` is free text. Chair / Chairman / Chair (Independent) are one office typed three
        // ways, and all three occur in both databases.
        assertThat(List.of("Chair", "Chairman", "Chair (Independent)"))
            .allSatisfy(t -> assertThat(CommitteeSeatOffice.classify(t)).isEqualTo(CommitteeSeatOffice.CHAIR));
    }

    @Test
    @DisplayName("⚠ Vice-Chair is NOT the chair — the mistake a LIKE '%CHAIR%' predicate makes")
    void theDeputyIsADistinctOffice() {
        // This is the assertion the whole class is for. `LIKE '%CHAIR%'` matches Vice-Chair, and both
        // databases hold exactly one such seat, so the generous predicate would have silently
        // classified a deputy as the chair. Same shape as F2-94's `also` key sweeping in entity-type
        // names alongside role names.
        assertThat(CommitteeSeatOffice.classify("Vice-Chair")).isEqualTo(CommitteeSeatOffice.DEPUTY_CHAIR);
        assertThat(CommitteeSeatOffice.classify("Deputy Chairman")).isEqualTo(CommitteeSeatOffice.DEPUTY_CHAIR);
        assertThat(CommitteeSeatOffice.classify("Vice-Chair")).isNotEqualTo(CommitteeSeatOffice.CHAIR);
    }

    @Test
    @DisplayName("a deputy presides; a secretary does not")
    void whoMayPreside() {
        // The decision, asserted where it is stated: charters conventionally let the vice-chair
        // preside in the chair's absence, and refusing that would refuse legitimate meetings.
        // Keeping the minutes is not presiding.
        Map<CommitteeSeatOffice, Boolean> expected = new EnumMap<>(CommitteeSeatOffice.class);
        expected.put(CommitteeSeatOffice.CHAIR,        true);
        expected.put(CommitteeSeatOffice.DEPUTY_CHAIR, true);
        expected.put(CommitteeSeatOffice.SECRETARY,    false);
        expected.put(CommitteeSeatOffice.MEMBER,       false);
        expected.put(CommitteeSeatOffice.OBSERVER,     false);
        expected.put(CommitteeSeatOffice.UNCLASSIFIED, false);

        // Every enum constant must have a stated answer — a new office added without deciding
        // whether it presides would otherwise inherit `false` silently.
        assertThat(expected.keySet()).containsExactlyInAnyOrder(CommitteeSeatOffice.values());
        expected.forEach((office, presides) ->
            assertThat(office.presides()).as("%s.presides()", office).isEqualTo(presides));
    }

    @Test
    @DisplayName("an unreadable title is UNCLASSIFIED, never MEMBER")
    void unknownTitlesAreNotQuietlyDemoted() {
        // Defaulting an unknown title to MEMBER would make a chair-required quorum permanently
        // unsatisfiable for a committee whose chair is recorded in a language or wording the
        // classifier does not know — and it would look like a data problem, not a code one.
        for (String t : List.of("Rais", "", "   ", "Convener", "قائد")) {
            assertThat(CommitteeSeatOffice.classify(t))
                .as("classify(%s)", t).isEqualTo(CommitteeSeatOffice.UNCLASSIFIED);
        }
        assertThat(CommitteeSeatOffice.classify(null)).isEqualTo(CommitteeSeatOffice.UNCLASSIFIED);
    }

    @Test
    @DisplayName("classification ignores case and surrounding whitespace")
    void normalisation() {
        assertThat(CommitteeSeatOffice.classify("  chairman  ")).isEqualTo(CommitteeSeatOffice.CHAIR);
        assertThat(CommitteeSeatOffice.classify("VICE-CHAIR")).isEqualTo(CommitteeSeatOffice.DEPUTY_CHAIR);
        assertThat(CommitteeSeatOffice.classify("secretary")).isEqualTo(CommitteeSeatOffice.SECRETARY);
    }
}
