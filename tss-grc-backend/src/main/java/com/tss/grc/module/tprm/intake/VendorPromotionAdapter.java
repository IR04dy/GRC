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
 * <p>Status is {@code ONBOARDING}, not {@code ACTIVE}: promotion registers the vendor so the tier
 * classification and the IT / security / legal / finance reviews can start (SAMA Outsourcing §5.1).
 * Creating it active would skip the entire onboarding gate the intake exists to open.
 */
@Component
@RequiredArgsConstructor
public class VendorPromotionAdapter implements RegisterPromotionPort {

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
        v.setStatus("ONBOARDING");
        v.setDescription(ctx.firstText("serviceDescription").orElseGet(ctx::description));
        return String.valueOf(tprm.createVendor(v).getId());
    }
}
