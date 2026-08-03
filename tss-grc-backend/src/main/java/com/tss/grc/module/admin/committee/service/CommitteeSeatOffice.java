package com.tss.grc.module.admin.committee.service;

import java.util.List;
import java.util.Locale;

/**
 * S6c — the office a roster seat holds, classified from the free-text title the tenant typed.
 *
 * <p><b>Why this exists.</b> {@code committee_member_terms.role} is a <em>governance title</em> in
 * free text, not a controlled value. Measured across both databases on 2026-08-03, the active seats
 * carry <b>ten distinct titles</b> — and three of them name the same office:
 * {@code Chair}, {@code Chairman}, {@code Chair (Independent)}. Any policy that asks "was the chair
 * present?" has to answer it from that, so the mapping needs one home rather than being re-derived at
 * each call site (the [[F2-62]] rule: one predicate, every consumer).
 *
 * <p><b>⚠ Why not {@code LIKE '%CHAIR%'}.</b> It also matches <b>{@code Vice-Chair}</b>, which is a
 * different office — exactly the mistake F2-94's {@code also} key made, where a generous match
 * swept in values that meant something else. Here the generosity happens to be *nearly* right and
 * that is what makes it dangerous: a deputy standing in for the chair is usually acceptable to a
 * charter, but "usually" is a decision, not a regex. So the deputy is classified as
 * {@link #DEPUTY_CHAIR} and the policy states, in one readable place, that a deputy satisfies a
 * chair requirement — rather than a substring silently deciding it.
 *
 * <p><b>Unrecognised titles are {@link #UNCLASSIFIED}, never MEMBER.</b> A tenant may type anything;
 * defaulting an unknown title to "ordinary member" would make a chair-required quorum quietly
 * unsatisfiable for a committee whose chair is recorded as, say, {@code "Rais"}. UNCLASSIFIED is
 * reported so the tenant can see the title the platform could not read.
 */
public enum CommitteeSeatOffice {

    /** The presiding office. Titles measured: {@code Chair}, {@code Chairman}, {@code Chair (Independent)}. */
    CHAIR,
    /** Stands in for the chair. Titles measured: {@code Vice-Chair}. Also matches Deputy Chair. */
    DEPUTY_CHAIR,
    /** Keeps the record. Titles measured: {@code Secretary}. Never counts toward a chair requirement. */
    SECRETARY,
    /**
     * An ordinary seat. Titles measured: {@code Member}, {@code Member (Independent)},
     * {@code Independent Director}, {@code Executive Director}.
     */
    MEMBER,
    /** Attends without a seat's authority. Titles measured: {@code Standing Invitee}. */
    OBSERVER,
    /** The platform could not read this title. Deliberately not MEMBER — see the class javadoc. */
    UNCLASSIFIED;

    /**
     * Every distinct active seat title measured in dev and production on 2026-08-03. Kept here so
     * {@code CommitteeSeatOfficeTest} can assert the classifier reads the real corpus rather than an
     * invented one — the anti-vacuity habit that four under-counting scans earned this month.
     */
    public static final List<String> MEASURED_TITLES = List.of(
        "Member", "Standing Invitee", "Chair", "Independent Director", "Member (Independent)",
        "Secretary", "Vice-Chair", "Chair (Independent)", "Chairman", "Executive Director");

    /**
     * Classify a free-text seat title.
     *
     * <p>Order matters: the deputy test runs <b>before</b> the chair test, because every deputy title
     * also contains "chair".
     */
    public static CommitteeSeatOffice classify(String title) {
        if (title == null) return UNCLASSIFIED;
        String t = title.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) return UNCLASSIFIED;

        // Deputy first — "Vice-Chair" and "Deputy Chairman" both contain CHAIR.
        if (t.contains("VICE") || t.contains("DEPUTY")) {
            return t.contains("CHAIR") ? DEPUTY_CHAIR : MEMBER;
        }
        if (t.contains("CHAIR")) return CHAIR;               // Chair · Chairman · Chair (Independent)
        if (t.contains("SECRETARY")) return SECRETARY;
        if (t.contains("INVITEE") || t.contains("OBSERVER") || t.contains("GUEST")) return OBSERVER;
        if (t.contains("MEMBER") || t.contains("DIRECTOR")) return MEMBER;
        return UNCLASSIFIED;
    }

    /**
     * Does a seat holding this office satisfy a "the chair must be present" quorum clause?
     *
     * <p><b>The decision, stated once:</b> yes for {@link #CHAIR} and {@link #DEPUTY_CHAIR}. Charters
     * conventionally read *"the Chair or, in the Chair's absence, the Vice-Chair shall preside"*, and
     * a policy that refused a properly deputising vice-chair would refuse a legitimate meeting — the
     * shape S6b-ii spent a whole slice avoiding. A secretary does <em>not</em> satisfy it: keeping the
     * minutes is not presiding.
     */
    public boolean presides() {
        return this == CHAIR || this == DEPUTY_CHAIR;
    }
}
