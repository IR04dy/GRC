package com.tss.grc.module.tprm.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.service.TprmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code VENDOR_ONBOARDING} → {@code tprm.vendors} — F2-172.
 *
 * <p>The one kind whose form already collects Arabic ({@code vendorNameAr}), so this is the one
 * adapter that does not have to compose it: a vendor's Arabic legal name is not derivable from
 * anything, which is presumably why the form asks. When the submitter left it blank the composed
 * fallback applies, and it says what the record is rather than repeating the Latin name in an
 * Arabic column.
 *
 * <p>Status is {@code PENDING_REVIEW}, not {@code ACTIVE}: promotion registers the vendor so the tier
 * classification and the IT / security / legal / finance reviews can start (SAMA Outsourcing §5.1).
 * Creating it active would skip the entire onboarding gate the intake exists to open.
 *
 * <p>⚠⚠ <b>It said {@code ONBOARDING} until F2-180, and {@code CK_vendors_status} does not allow that
 * value — so promoting a {@code VENDOR_ONBOARDING} submission had never once worked.</b> The column
 * accepts {@code ACTIVE|INACTIVE|PENDING_REVIEW|TERMINATED}, and all 28 vendors in dev are
 * {@code ACTIVE}, which is what a register looks like when nothing has ever entered it through the
 * front door. This is the second instance of the {@link com.tss.grc.module.risk.intake.RiskPromotionAdapter}
 * shape — a status word chosen for what it <em>means</em>, by an author who never asked the column what
 * it accepts — and the two were written in the same slice. {@code PENDING_REVIEW} is the member of the
 * constrained vocabulary that carries the intended meaning: registered, reviews not yet done.
 *
 * <p>⚙ Why {@code PENDING_REVIEW} rather than widening the CHECK to admit {@code ONBOARDING}: the
 * vocabulary already has a word for this state, 28 rows and the register's own filters use it, and a
 * new status value would have to be taught to every status-grouped query and label map on the way to
 * meaning exactly what {@code PENDING_REVIEW} already means.
 */
@Component
@RequiredArgsConstructor
public class VendorPromotionAdapter implements RegisterPromotionPort {

    /**
     * The status a promoted vendor is created with. A named constant rather than an inline literal so
     * {@code PromotionVocabularyTest} can read it and compare it against the vocabulary
     * {@code CK_vendors_status} declares in the migrations.
     */
    static final String PROMOTED_STATUS = "PENDING_REVIEW";

    private final TprmService tprm;

    @Override public String target() { return "VENDOR"; }

    @Override public String requiredPermission() { return "TPRM:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        Vendor v = new Vendor();
        v.setVendorName(ctx.titleEn(255, "vendorName"));
        v.setVendorNameAr(ctx.firstText("vendorNameAr").orElseGet(() -> ctx.titleAr(255)));
        v.setVendorType(ctx.firstText("tier", "vendorType").orElse("SERVICE"));
        v.setCountry(ctx.firstText("country").orElse("SA"));
        v.setContactName(ctx.anonymous() ? null : ctx.submitterName());
        v.setContactEmail(ctx.firstText("contactEmail").orElse(ctx.anonymous() ? null : ctx.submitterEmail()));
        v.setRiskRating(ctx.severityOrMedium());
        v.setStatus(PROMOTED_STATUS);
        v.setDescription(ctx.firstText("serviceDescription").orElseGet(ctx::description));
        return String.valueOf(tprm.createVendor(v).getId());
    }
}
