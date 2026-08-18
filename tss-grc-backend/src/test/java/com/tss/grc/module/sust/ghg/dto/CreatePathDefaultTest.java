package com.tss.grc.module.sust.ghg.dto;

import com.tss.grc.module.sust.climate.dto.ClimateRiskCreateRequest;
import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-240 — the two create paths that own a default in a request record's {@code toEntity()}.
 *
 * <p>Both entities dropped an initialiser so their {@code PUT}'s {@code != null} guards could
 * actually fire, and both creates bind a purpose-built record rather than the entity — so unlike
 * the other nine registers in this sweep there is no service line to carry the default. It lives in
 * {@code toEntity()}, which is also why {@code NeverFiringNullGuardContractTest} had to widen its
 * writer scope past {@code *Service.java} to assert it.
 *
 * <p>Every column below is NOT NULL, and {@code ddl-auto=none} means no other test in the tree
 * touches the schema: if one of these defaults goes missing the create answers 500 and nothing else
 * would notice (F2-238's one real hazard for this fix shape). The structural half is asserted by
 * {@code everyRemovedInitialiserHasAWriterOnTheCreatePath}; this is the behavioural half.
 */
class CreatePathDefaultTest {

    @Nested
    @DisplayName("ClimateRiskCreateRequest — the three TCFD / IFRS S2 axes")
    class Climate {

        /**
         * Only the fields the form always sends; all three axes absent.
         *
         * <p>Positional, because that is what the record gives us — the component order is
         * kind, category, reportingYear, titleEn, titleAr, descriptionEn, descriptionAr,
         * timeHorizon, likelihood, impact, then twelve optional tail fields.
         */
        private ClimateRiskCreateRequest bare() {
            return new ClimateRiskCreateRequest(
                "RISK", "PHYSICAL_ACUTE", (short) 2026,
                "Coastal flooding at the Jeddah data centre", "الفيضانات الساحلية بمركز بيانات جدة",
                null, null,
                null, null, null,                       // timeHorizon, likelihood, impact — absent
                null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Test
        @DisplayName("a create that states no axis still lands on a persistable row")
        void defaultsAreSupplied() {
            ClimateRiskOpportunity e = bare().toEntity();
            assertThat(e.getImpact())
                .as("impact is NOT NULL; the entity no longer initialises it, so this create path is "
                    + "the only thing standing between an omitted axis and a 500")
                .isEqualTo((short) 3);
            assertThat(e.getLikelihood()).isEqualTo((short) 3);
            assertThat(e.getTimeHorizon()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("a stated axis is carried through, not overwritten by the default")
        void statedAxesWin() {
            ClimateRiskOpportunity e = new ClimateRiskCreateRequest(
                "RISK", "TRANSITION_POLICY", (short) 2026,
                "Carbon pricing exposure", "التعرض لتسعير الكربون",
                null, null,
                "LONG", (short) 5, (short) 4,
                null, null, null, null, null, null, null, null, null, null, null, null).toEntity();
            assertThat(e.getTimeHorizon()).isEqualTo("LONG");
            assertThat(e.getLikelihood()).isEqualTo((short) 5);
            assertThat(e.getImpact()).isEqualTo((short) 4);
        }

        @Test
        @DisplayName("the entity itself defaults nothing — that is the whole point of the fix")
        void theEntityNoLongerInitialisesTheAxes() {
            ClimateRiskOpportunity fresh = new ClimateRiskOpportunity();
            assertThat(fresh.getImpact())
                .as("if this is non-null again, `ClimateRiskOpportunityService#update`'s "
                    + "`if (req.getImpact() != null)` can never be false and a PUT that omits the "
                    + "axis silently resets a live assessment — 12 of 17 rows in dev and prod")
                .isNull();
            assertThat(fresh.getLikelihood()).isNull();
            assertThat(fresh.getTimeHorizon()).isNull();
        }
    }

    @Nested
    @DisplayName("GhgCreateRequest — the ISAE 3410 coverage input")
    class Ghg {

        /**
         * Component order: reportingYear, scope, category, scope3Category, sourceNameEn,
         * sourceNameAr, businessUnit, facility, activityData, activityUnit, emissionFactor,
         * emissionFactorUnit, emissionFactorId, emissionValueTco2e, coveragePercent, then six
         * optional tail fields.
         */
        private GhgCreateRequest bare(BigDecimal coverage) {
            return new GhgCreateRequest(
                (short) 2026, (short) 2, "PURCHASED_ELECTRICITY", null,
                "Riyadh HQ grid electricity", "كهرباء الشبكة للمقر الرئيسي",
                null, null,
                new BigDecimal("120000"), "kWh",
                new BigDecimal("0.524"), "tCO2e/MWh", null,
                null, coverage,
                null, null, null, null, null, null);
        }

        @Test
        @DisplayName("a create that states no coverage is taken as fully covering its source")
        void defaultIsSupplied() {
            assertThat(bare(null).toEntity().getCoveragePercent())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("a stated coverage is carried through")
        void statedCoverageWins() {
            assertThat(bare(new BigDecimal("48.00")).toEntity().getCoveragePercent())
                .isEqualByComparingTo(new BigDecimal("48.00"));
        }

        @Test
        @DisplayName("the entity itself defaults nothing, so the update's guard can fire")
        void theEntityNoLongerInitialisesCoverage() {
            assertThat(new GhgEmissionRecord().getCoveragePercent())
                .as("this initialiser was `new BigDecimal(\"100.00\")`, so a PUT omitting the key "
                    + "RAISED an assurance input to full coverage. A reset that manufactures an "
                    + "assurance does not lose a fact, it asserts one — 8 of 18 live dev rows and "
                    + "7 of 17 in prod sit between 48% and 89%")
                .isNull();
        }
    }
}
