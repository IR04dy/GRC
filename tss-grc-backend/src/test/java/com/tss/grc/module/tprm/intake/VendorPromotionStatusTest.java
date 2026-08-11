package com.tss.grc.module.tprm.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.service.TprmService;
import com.tss.grc.support.MigrationVocabularies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-180 — a promoted vendor must be created with a status {@code tprm.vendors} accepts.
 *
 * <p>{@link VendorPromotionAdapter} wrote {@code ONBOARDING} for as long as it existed, and
 * {@code CK_vendors_status} allows {@code ACTIVE|INACTIVE|PENDING_REVIEW|TERMINATED} — so promoting a
 * {@code VENDOR_ONBOARDING} submission had never once succeeded. All 28 vendors in dev are
 * {@code ACTIVE}, which is what a register looks like when nothing has entered it through the intake
 * door.
 *
 * <p>This is the second adapter with that exact shape, after {@code RiskPromotionAdapter}'s
 * {@code IDENTIFIED}, and both javadocs argued their word on governance grounds — correctly. 🔑 <b>Being
 * right about what a state means is not evidence about what the column stores it in.</b>
 *
 * <p>The vocabulary is read from the migration rather than asserted as a literal, for the reason
 * {@code RiskPromotionStatusTest} gives: a test that restates the constant it is checking passes forever
 * and compares nothing.
 */
@ExtendWith(MockitoExtension.class)
class VendorPromotionStatusTest {

    @Mock TprmService tprm;
    @Captor ArgumentCaptor<Vendor> captor;

    private VendorPromotionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new VendorPromotionAdapter(tprm);
    }

    @Test
    @DisplayName("the promoted status is one CK_vendors_status actually allows")
    void promotedStatusIsInTheConstrainedVocabulary() {
        Set<String> allowed = vocabularyOf("CK_vendors_status");

        assertThat(allowed).as(
            "anti-vacuity: an empty vocabulary would make the assertion below pass on nothing, which is "
            + "how a guard becomes a decoration")
            .contains("ACTIVE", "PENDING_REVIEW")
            .hasSizeGreaterThanOrEqualTo(4);

        assertThat(VendorPromotionAdapter.PROMOTED_STATUS).as(
            "⚠ ONBOARDING is the word for what this state means and was never a value the column stores. "
            + "Allowed: %s", allowed)
            .isIn(allowed);
    }

    @Test
    @DisplayName("promotion does not create an ACTIVE vendor — that would skip the onboarding gate")
    void promotedVendorIsNotActive() {
        assertThat(VendorPromotionAdapter.PROMOTED_STATUS).as(
            "the SAMA Outsourcing §5.1 tier classification and the IT / security / legal / finance "
            + "reviews are the whole point of the intake; a vendor created ACTIVE has skipped all of them. "
            + "Fixing the CHECK violation by reaching for the most obviously legal value would have traded "
            + "a failed promotion for a silent control bypass.")
            .isNotEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("the adapter puts that status on the vendor it hands to the service")
    void theStatusReachesTheEntity() {
        when(tprm.createVendor(any())).thenReturn(saved());

        adapter.promote(new IntakePromotionContext(
            9L, "VO-Q1W2-E3R4", "VENDOR_ONBOARDING", "HIGH", "Rana", "rana@example.sa", false, "en",
            LocalDateTime.of(2026, 8, 11, 9, 30), null, "compliance",
            Map.of("vendorName", "Najm Cloud Services", "tier", "TIER_2"),
            "Register a new vendor", "تسجيل مورد جديد"));

        verify(tprm).createVendor(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VendorPromotionAdapter.PROMOTED_STATUS);
        assertThat(captor.getValue().getRiskRating()).as(
            "risk_rating carries its own CHECK, fed by the shared severity clamp")
            .isIn(IntakePromotionContext.SEVERITY_BANDS);
    }

    private static Vendor saved() {
        Vendor v = new Vendor();
        v.setId(4_200L);
        return v;
    }

    /**
     * The last definition of {@code name} in migration-version order — the state a server ends in.
     *
     * <p>⚙ This walk used to live here, and in {@code PromotionVocabularyTest}, and in
     * {@code AutomationRecipientKeyRatchetTest}: three copies, of which only one knew about the
     * {@code PEND}-from-{@code N'PENDING'} trap. F2-179 extracted it to
     * {@link MigrationVocabularies} for the reason {@code SeatWindowPredicateSharingTest} enforces
     * elsewhere — a predicate written twice drifts, and the copy that predates the discovery does
     * not know what the other one learned.
     */
    private static Set<String> vocabularyOf(String name) {
        return MigrationVocabularies.valuesOf(name);
    }
}
