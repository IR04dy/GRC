package com.tss.grc.module.change_mgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-251 — one party per bar, and what breaks when the bars are merged.
 *
 * <h2>What this class pins</h2>
 * The behaviour of {@link ChangeSoD#compareAny} over the four bars a change's self-review refusals
 * evaluate: an id bar and a name bar for the requester, and the same pair for the owner. The
 * defect it exists for is not in {@code ChangeSoD} — that class was always right — it is in the
 * <b>shape of the party list its callers built</b>.
 *
 * <h2>⚠ Why this is a behaviour test and not only a structural one</h2>
 * {@code ChangeConditionRegisterTest} asserts the four-party shape structurally, which catches a
 * merge written back in. It cannot show <em>what the merge costs</em>, and the cost is the whole
 * finding: a change carrying a captured requester id stops refusing the owner. So the merged form
 * is reproduced here explicitly, and the test asserts that it lets through what the per-bar form
 * refuses. That is the difference between a gate that says "the code changed" and one that says
 * "the control came back".
 *
 * <p>The two facts that make this live rather than academic, measured 2026-08-20 and identical in
 * dev and prod: {@code change_owner_id} has <b>no writer anywhere in the platform</b> (NULL on 17
 * of 17 live changes), and <b>0 of 34</b> requester/owner labels resolve to a platform user — they
 * are offices and teams ({@code 'CIO Office'}, {@code 'Head of IAM'}). So the owner is nameable
 * only by label, and the label bar was the only bar that could ever decide it.
 */
class ChangeSelfReviewBarsTest {

    /** The four bars, exactly as {@code ChangeManagementService#selfReviewBars} builds them. */
    private static List<ChangeSoD.Party> perBar(Long reqId, String reqName,
                                               Long ownId, String ownName) {
        return List.of(
            new ChangeSoD.Party("requestedById", reqId, null),
            new ChangeSoD.Party("requestedByName", null, reqName),
            new ChangeSoD.Party("changeOwnerId", ownId, null),
            new ChangeSoD.Party("changeOwnerName", null, ownName));
    }

    /**
     * The shape every caller in the module used to build, reproduced so the regression is
     * demonstrable rather than asserted. {@code ChangeSoD.compareAny}'s javadoc calls this out by
     * name: <i>"never {@code Party(id, label)}"</i>.
     */
    private static List<ChangeSoD.Party> merged(Long reqId, String reqName,
                                                Long ownId, String ownName) {
        return List.of(
            new ChangeSoD.Party("requestedBy", reqId, reqName),
            new ChangeSoD.Party("changeOwner", ownId, ownName));
    }

    @Test
    @DisplayName("the owner's name bar decides even when the REQUESTER carries an id")
    void theOwnersNameBarSurvivesTheRequestersIdBeingCaptured() {
        // The live shape: CE-15 stamps requested_by_id, change_owner_id has no writer, and the owner
        // is recorded as a label. The actor is a CHANGE:APPROVE holder whose username is that label.
        ChangeSoD.Actor actor = new ChangeSoD.Actor(2L, "rmanager");
        List<ChangeSoD.Party> bars = perBar(5L, "AppSec", null, "rmanager");

        assertThat(ChangeSoD.compareAny(actor, bars))
            .as("the owner is nameable only by label, so the label bar has to decide — and it must "
              + "not be switched off by a DIFFERENT party carrying an id")
            .isEqualTo(ChangeSoD.Verdict.SAME);
        assertThat(ChangeSoD.compareAny(actor, bars).refuses()).isTrue();
    }

    @Test
    @DisplayName("the merged form lets that same actor through — this is the defect, demonstrated")
    void theMergedFormRetiresTheOwnersNameBar() {
        ChangeSoD.Actor actor = new ChangeSoD.Actor(2L, "rmanager");

        // Merged, the owner party carries a null id and so IS compared by name — the owner half of
        // the merge is not where the merge bites. The requester half is:
        assertThat(ChangeSoD.compareAny(actor, merged(5L, "AppSec", null, "rmanager")).refuses())
            .as("a merged party whose id is null still compares by name")
            .isTrue();

        // ...and here the actor IS the recorded requester by label, on a change whose requester id
        // names somebody else. Per bar this refuses; merged, rule 1 engages on the id and the name
        // is never looked at.
        assertThat(ChangeSoD.compareAny(actor, merged(5L, "rmanager", null, "CloudOps")).refuses())
            .as("MERGED: the requester's id is present, so its label is never compared — the actor "
              + "walks through a bar that names them")
            .isFalse();
        assertThat(ChangeSoD.compareAny(actor, perBar(5L, "rmanager", null, "CloudOps")).refuses())
            .as("PER BAR: the same actor is refused, because the label bar is its own bar")
            .isTrue();
    }

    @Test
    @DisplayName("the hand-written fallback keyed on BOTH ids: what the CAB vote used to do")
    void theBothIdsNullFallbackIsDeadOnceEitherIdIsCaptured() {
        ChangeSoD.Actor actor = new ChangeSoD.Actor(2L, "rmanager");
        Long reqId = 5L;
        String ownName = "rmanager";

        // The literal pre-fix predicate. It is reproduced rather than described, because "the branch
        // could not fire" is the kind of claim this codebase has repeatedly got wrong by reasoning.
        boolean selfById = actor.id() != null
            && (actor.id().equals(reqId) || actor.id().equals((Long) null));
        boolean selfByName = reqId == null   /* && ownerId == null */
            && actor.name().equalsIgnoreCase(ownName);
        assertThat(selfById || selfByName)
            .as("pre-fix: the id bar misses (the actor is the owner, not the raiser) and the name "
              + "branch is gated on the RAISER's id being absent, so nothing refuses")
            .isFalse();

        assertThat(ChangeSoD.compareAny(actor, perBar(reqId, "AppSec", null, ownName)).refuses())
            .as("post-fix: the owner's name bar decides on its own")
            .isTrue();
    }

    @Test
    @DisplayName("the ratification fallback keyed on the ACTOR having no id was dead for every caller")
    void theActorHasNoIdFallbackCannotFireForAnAuthenticatedPrincipal() {
        // GrcUserDetails always carries an id, so `actorUserId == null` was unreachable from a route.
        ChangeSoD.Actor actor = new ChangeSoD.Actor(2L, "rmanager");
        boolean nameFallback = actor.id() == null && "rmanager".equalsIgnoreCase("rmanager");
        assertThat(nameFallback)
            .as("pre-fix: an authenticated ratifier always has an id, so the name branch was "
              + "unreachable — the owner bar was undecidable on EVERY row, legacy ones included")
            .isFalse();

        assertThat(ChangeSoD.compareAny(actor, perBar(null, "AppSec", null, "rmanager")).refuses())
            .as("post-fix: a legacy row with no ids at all still refuses the owner")
            .isTrue();
    }

    @Test
    @DisplayName("refusing more, and refusing nothing that used to be allowed")
    void theConversionOnlyEverAddsRefusals() {
        ChangeSoD.Actor actor = new ChangeSoD.Actor(7L, "auditor");

        // An unrelated approver on a fully-labelled change: allowed before and after.
        assertThat(ChangeSoD.compareAny(actor, perBar(5L, "AppSec", null, "CloudOps")).refuses())
            .as("a third party is not refused — this is what stops the fix breaking working requests")
            .isFalse();

        // Everything the old predicate refused, the new one still refuses.
        assertThat(ChangeSoD.compareAny(new ChangeSoD.Actor(5L, "ranalyst"),
                perBar(5L, "AppSec", null, "CloudOps")).refuses())
            .as("the raiser by id: refused before, refused now")
            .isTrue();
        assertThat(ChangeSoD.compareAny(new ChangeSoD.Actor(9L, "CloudOps"),
                perBar(null, "AppSec", null, "CloudOps")).refuses())
            .as("the owner by name on a legacy row: refused before, refused now")
            .isTrue();
    }

    @Test
    @DisplayName("a change with nothing recorded is UNVERIFIABLE, and that is never a refusal")
    void nothingRecordedIsUnverifiableAndNotARefusal() {
        assertThat(ChangeSoD.compareAny(new ChangeSoD.Actor(2L, "rmanager"),
                perBar(null, null, null, null)))
            .as("refusing on an empty record would trap every change that exists — the mistake "
              + "ChangeSoD's javadoc says S6c and S8e each avoided")
            .isEqualTo(ChangeSoD.Verdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("strength reports how much each bar rested on, without changing any verdict")
    void strengthDescribesTheWeaknessWithoutWeakeningTheGate() {
        ChangeSoD.Actor actor = new ChangeSoD.Actor(2L, "rmanager");
        List<ChangeSoD.Party> bars = perBar(5L, "AppSec", null, "rmanager");

        assertThat(ChangeSoD.strength(actor, bars.get(0))).isEqualTo(ChangeSoD.Strength.BY_ID);
        assertThat(ChangeSoD.strength(actor, bars.get(3))).isEqualTo(ChangeSoD.Strength.BY_LABEL);
        assertThat(ChangeSoD.strength(actor, bars.get(2)))
            .as("an absent id bar rests on nothing at all")
            .isEqualTo(ChangeSoD.Strength.NONE);

        // The correction F2-251 made to Coverage's javadoc, asserted rather than left as prose: a
        // label-only party is NOT unverifiable. That false sentence is what the two hand-written
        // copies encoded as "legacy fallback only".
        assertThat(ChangeSoD.compare(actor, bars.get(3)).verified())
            .as("BY_LABEL is a real answer to a weak question, never an absent one")
            .isTrue();
    }

    @Test
    @DisplayName("the name comparison is case-insensitive and trimmed, as the legacy rule was")
    void theNameBarKeepsTheLegacyComparisonSemantics() {
        assertThat(ChangeSoD.compareAny(new ChangeSoD.Actor(2L, "RManager"),
                perBar(null, null, null, "  rmanager  ")).refuses())
            .as("preserved from assertNotSelfWaiver — a refactor that stops refusing something is "
              + "not a refactor")
            .isTrue();
    }
}
