package com.tss.grc.module.ethics.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.ethics.entity.Disclosure;
import com.tss.grc.module.ethics.entity.EthicsCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-306 — a business reference is settable while absent and immutable once set.
 *
 * <h3>What this is testing and why it had no test before</h3>
 *
 * {@code ClientSuppliedReferenceRatchetTest}'s business-reference leg is scoped to
 * {@code CREATE_ISH}, a verb list that deliberately excludes {@code update} because its claim is
 * <em>"a reference is chosen when a record is born"</em>. The <b>rename</b> question is therefore
 * outside its population by construction — and renaming is arguably the sharper harm: the record
 * exists, an auditor may already cite the code, and the citation breaks silently.
 *
 * <p>Measured across the tree: <b>44 update-path sites in 34 services</b> write a reference-shaped
 * field from the request. They split three ways, and only one kind is this defect — see
 * {@code ClientSuppliedReferenceRatchetTest}'s census leg. The two closed here are the kind-1
 * members in {@code ethics}: {@code EthicsCase#caseCode} and {@code Disclosure#disclosureCode}, both
 * reached through an {@code apply(target, src)} helper <b>shared by create and update</b>, and
 * neither minted by anything.
 *
 * <p>⚠ {@code DisclosureService} had <b>no test class at all</b>, and a path with no test is not a
 * path somebody decided was safe ([[F2-248]]). {@link com.tss.grc.module.ethics.service
 * .EthicsCaseServiceTest} covered the case register's lifecycle and never its reference.
 *
 * <p>These are unit tests on the primitive plus the two entity shapes rather than service tests,
 * deliberately: the services' {@code apply} helpers are private and both call sites pass the same
 * four arguments, so what is worth pinning is the RULE and the fact that a stored code cannot be
 * replaced. {@code EntityBoundPartialUpdateTest} already drives the services end to end.
 */
class ReferenceRenameContractTest {

    private static final String WHY = "Omit caseCode, or send the value already recorded.";

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(1L); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    @Test
    void aReferenceIsSettableWhileAbsent() {
        EthicsCase c = new EthicsCase();
        ServerOwnedState.requireImmutableReference(
            "ETH-2026-001", c.getCaseCode(), "caseCode", "Ethics case (new)", WHY);
        // no refusal, so the create may assign it
        c.setCaseCode("ETH-2026-001");
        assertThat(c.getCaseCode()).isEqualTo("ETH-2026-001");
    }

    @Test
    void anEchoIsAccepted() {
        // The ethics edit form spreads the whole record back, so refusing a present-but-identical
        // value would break the screen that works today -- the one failure a fix must never have.
        ServerOwnedState.requireImmutableReference(
            "ETH-2026-001", "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
    }

    @Test
    void anEchoIsAcceptedCaseInsensitively() {
        // Every text column here collates case-insensitively (F2-179), so a caller round-tripping a
        // value that differs only in case is echoing, not renaming.
        ServerOwnedState.requireImmutableReference(
            "eth-2026-001", "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
    }

    @Test
    void anOmittedReferenceLeavesTheStoredOneAlone() {
        ServerOwnedState.requireImmutableReference(
            null, "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
        ServerOwnedState.requireImmutableReference(
            "   ", "ETH-2026-001", "caseCode", "Ethics case 7", WHY);
    }

    @Test
    void renamingAStoredReferenceIsRefused() {
        assertThatThrownBy(() -> ServerOwnedState.requireImmutableReference(
                "ETH-2026-999", "ETH-2026-001", "caseCode", "Ethics case 7", WHY))
            .isInstanceOf(IllegalArgumentException.class)
            // names the field ...
            .hasMessageContaining("caseCode")
            // ... the value already recorded, so the caller can see what they would have replaced ...
            .hasMessageContaining("ETH-2026-001")
            // ... and the consequence, which is the reason the rule exists.
            .hasMessageContaining("silently stop resolving")
            // ⚠ and it must NOT name a route. requireUnchanged's wording ends "That move goes
            // through <route>", and for a reference there IS no route -- naming one that does not
            // exist is F2-302's defect, in the same primitive.
            .hasMessageNotContainingAny("goes through", "cannot be moved to");
    }

    /**
     * The two entities this closes, asserted so a later slice cannot quietly re-add a plain setter
     * write: a fresh record has no code, which is what makes "settable while absent" the create path
     * and "immutable once set" the update path, with no flag threaded through the shared helper.
     */
    @Test
    void bothClosedEntitiesStartWithNoReference() {
        assertThat(new EthicsCase().getCaseCode()).isNull();
        assertThat(new Disclosure().getDisclosureCode()).isNull();
    }
}
