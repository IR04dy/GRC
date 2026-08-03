package com.tss.grc.module.admin.committee;

import java.util.List;

/**
 * W1-5 / F2-18 — the golden path a committee decision must survive, declared once.
 *
 * <h3>Why this list is a separate class</h3>
 * {@link CommitteeGoldenPathIT} <em>executes</em> these steps; {@code CommitteeGoldenPathContractTest}
 * — which runs in the ordinary surefire suite, with no database — <em>asserts</em> that the IT still
 * covers every one of them. Both read this constant, so the two cannot drift into agreeing about
 * different things.
 *
 * <p>That is the F2-62 lesson applied to a test rather than to a control: a report and its
 * enforcement point must share their predicate <em>textually</em>, not merely happen to agree.
 * Two lists that "say the same thing" in two files is exactly the shape that let
 * {@code iam.roles.is_active} be described as enforced for months while it enforced nothing.
 *
 * <h3>Why a step list at all</h3>
 * The committee engine is the platform's audit-evidence backbone and had 99 unit tests before this
 * class existed — every one of them supplying its own inputs to a mocked repository. The gap F2-18
 * names is not "more assertions"; it is that <b>no automated test had ever carried one decision from
 * constitution to consequence</b>. A step that silently stops being exercised would restore that gap
 * without any test turning red, which is why the coverage of this list is itself asserted.
 */
public final class CommitteeGoldenPath {

    private CommitteeGoldenPath() {}

    /**
     * The ordered golden path. Each entry names a method on {@link CommitteeGoldenPathIT}; the IT
     * records the step as it completes and asserts the recorded set equals this list at the end, so
     * an early return or a swallowed exception cannot leave the walk looking finished.
     */
    public static final List<String> STEPS = List.of(
        "constituteCommittee",
        "appointVotingRoster",
        "raiseChange",
        "referChangeToCommittee",
        "openMeeting",
        "recordSeatBoundAttendance",
        "assertQuorumIsTheRosterNotTheSheet",
        "raiseMotionAndBindItToTheReferral",
        "castOwnVoteThenRecordTwoOnBehalfIncludingARecusal",
        "completeMeeting",
        "approveMinutesAsASecondPrincipal",
        // S7a / F2-100 — the decision is announced when the minutes make it binding, not at
        // create. Only an end-to-end walk can assert the ordering: the votes were cast while the
        // meeting was IN_PROGRESS, so this is the step at which the outcome event can fire.
        "assertTheDecisionWasAnnouncedOnApprovedMinutes",
        "verifyTheESignatureOverTheMinutes",
        "advanceTheChangeOnTheCommitteeDecision",
        "assertTheAuditLedgerRecordedTheDecision"
    );

    /** Every artefact this walk creates is tagged with this, and the tag is deliberate. */
    public static final String EVIDENCE_TAG = "[DEMO-EVIDENCE]";

    /**
     * Code prefix on every row the walk creates. Cleanup is scoped to it, so the teardown predicate
     * can only ever match rows this test made — never a seeded committee or a real change.
     */
    public static final String CODE_PREFIX = "W15-GP";
}
