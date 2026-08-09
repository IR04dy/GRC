package com.tss.grc.module.risk.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.risk.dto.RiskRequest;
import com.tss.grc.module.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code RISK_ISSUE} → {@code risk.risks} — F2-172.
 *
 * <p>The intake form asks the submitter for {@code likelihoodSelfAssessed} and
 * {@code impactSelfAssessed} on the platform's 1–5 scale, so the promoted risk carries the
 * reporter's own numbers rather than a severity band expanded back into a score. The frontend
 * adapter this replaces did the latter — mapping {@code MEDIUM} to likelihood 3 / impact 3 — which
 * threw away two answers the submitter had actually given.
 *
 * <p>Status is {@code IDENTIFIED}: a promoted risk has been raised, not assessed. Anything further
 * along would let it skip the register's own assessment step.
 */
@Component
@RequiredArgsConstructor
public class RiskPromotionAdapter implements RegisterPromotionPort {

    private final RiskService risks;

    @Override public String target() { return "RISK"; }

    @Override public String requiredPermission() { return "RISK:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        int likelihood = scale(ctx, "likelihoodSelfAssessed");
        int impact = scale(ctx, "impactSelfAssessed");
        RiskRequest req = new RiskRequest(
            ctx.titleEn(300, "summary", "description"),
            ctx.titleAr(300),
            ctx.description(),
            null,                       // descriptionAr — the payload carries no Arabic body text
            null, null,                 // categoryId / ownerId — free-text on the form, not ids
            likelihood, impact,
            null, null,                 // residual: unknown until the risk is assessed
            "IDENTIFIED",
            null);
        return String.valueOf(risks.create(req).id());
    }

    /**
     * The submitter's 1–5 answer, falling back to the severity band. Out-of-range values are
     * clamped rather than rejected: {@code RiskRequest} validates {@code @Min(1) @Max(5)}, and a
     * bean-validation failure raised from inside a promotion reads as a bug in intake, not as
     * "the form let somebody type 7".
     */
    private int scale(IntakePromotionContext ctx, String key) {
        return ctx.firstText(key)
            .map(s -> s.replaceAll("[^0-9]", ""))
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .map(n -> Math.max(1, Math.min(5, n)))
            .orElseGet(() -> switch (ctx.severityOrMedium()) {
                case "LOW" -> 2;
                case "HIGH" -> 4;
                case "CRITICAL" -> 5;
                default -> 3;
            });
    }
}
