package com.tss.grc.module.policy.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.service.PolicyManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code EMPLOYMENT_CONTRACT} / {@code BENEFICIAL_OWNERSHIP_DISCLOSURE} /
 * {@code CAPITAL_CHANGE_NOTIFICATION} → {@code policy.policy_document} — F2-172.
 *
 * <p>Three DD-88/DD-94 kinds the catalogue routes to {@code POLICY}: a Saudi Labor Law Art. 51
 * contract, an MOC beneficial-ownership disclosure and a capital-change notification. They are
 * governed documents with an owner, a version and an approval path, which is what a policy document
 * is on this platform — so they land as {@code DRAFT} documents rather than as anything more
 * finished. {@code PolicyManagementService.create} pins {@code DRAFT} itself and says why: a client
 * that could create one already approved would skip the state machine and the four-eyes gate.
 *
 * <p>The policy code is derived from the intake reference code, which makes it unique per
 * submission and traceable. {@code create} rejects a duplicate {@code (code, version)} outright, so
 * a code derived from anything the submitter types would turn a second contract for the same
 * employee into a failed promotion.
 */
@Component
@RequiredArgsConstructor
public class PolicyDocumentPromotionAdapter implements RegisterPromotionPort {

    private final PolicyManagementService policies;

    @Override public String target() { return "POLICY"; }

    @Override public String requiredPermission() { return "COMPLIANCE:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        PolicyDocument d = new PolicyDocument();
        d.setPolicyCode("POL-" + ctx.referenceCode());
        d.setVersion("1.0");
        d.setTitleEn(ctx.titleEn(255, "employeeName", "beneficialOwnerNameEn", "changeType"));
        d.setTitleAr(ctx.firstText("beneficialOwnerNameAr").orElseGet(() -> ctx.titleAr(255)));
        d.setCategory(category(ctx));
        d.setSummaryEn(ctx.description());
        d.setOwnerName(ctx.ownerName());
        return String.valueOf(policies.create(d, ctx.actor()).getId());
    }

    private String category(IntakePromotionContext ctx) {
        return switch (ctx.kind()) {
            case "EMPLOYMENT_CONTRACT" -> "HR";
            case "BENEFICIAL_OWNERSHIP_DISCLOSURE", "CAPITAL_CHANGE_NOTIFICATION" -> "CORPORATE";
            default -> "GENERAL";
        };
    }
}
