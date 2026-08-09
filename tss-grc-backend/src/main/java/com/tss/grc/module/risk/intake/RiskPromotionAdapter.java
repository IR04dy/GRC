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
 * <p>Status is {@code OPEN}: a promoted risk has been raised, not assessed. Anything further along
 * would let it skip the register's own assessment step.
 *
 * <p>⚠⚠ <b>It said {@code IDENTIFIED} until F2-175, and {@code CK_risks_status} does not allow that
 * value — so promoting a {@code RISK_ISSUE} had never once worked.</b> The insert failed with
 * <em>"The INSERT statement conflicted with the CHECK constraint CK_risks_status"</em>, surfacing to the
 * caller as a 409 whose message says <em>"either something it refers to does not exist, or the record is
 * still referenced elsewhere"</em> — a description of a foreign key, on a failure that is nothing of the
 * kind. The javadoc even argued for {@code IDENTIFIED} on governance grounds, which is exactly right
 * about the intent and was never a value this column accepts. {@code OPEN} is the member of the
 * constrained vocabulary that carries that meaning (V29/V244:
 * {@code OPEN|UNDER_REVIEW|MITIGATING|ACCEPTED|CLOSED|ARCHIVED|TRANSFERRED}).
 *
 * <p>⚙ Why nobody noticed: F2-172 live-probed promotion into {@code privacy.dpia} and reasoned about the
 * other twelve adapters from the code. And the only intake kind targeting {@code RISK} is
 * {@code RISK_ISSUE}, whose sole producer was the drift monitor — a path F2-175 then measured as never
 * having run. Two independent reasons for the same blind spot, which is why it took a probe of the
 * <em>chain</em> rather than of the adapter.
 *
 * @see com.tss.grc.module.risk.intake.RiskPromotionStatusTest the pin, read from the migration so the
 *      unit suite stays database-free
 */
@Component
@RequiredArgsConstructor
public class RiskPromotionAdapter implements RegisterPromotionPort {

    /**
     * The status a promoted risk is created with. A named constant rather than an inline literal so the
     * pin has something to read: {@code RiskPromotionStatusTest} checks it against the vocabulary
     * {@code CK_risks_status} declares in the migrations, which is the only copy of that list inside
     * the repository.
     */
    static final String PROMOTED_STATUS = "OPEN";

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
            PROMOTED_STATUS,
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
