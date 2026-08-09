package com.tss.grc.module.privacy.intake;

import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.privacy.entity.Dpia;
import com.tss.grc.module.privacy.service.DpiaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code DPIA_INITIATION} → {@code privacy.dpia} — F2-172.
 *
 * <p>This is the record F2-171 was found on: the Privacy page routes DPIA creation through the
 * Services Center by design ({@code PrivacyPage.tsx}: <i>"DD-76: route DSR / breach / DPIA creation
 * through Services Center"</i>), so {@code dpiaApi.create} is uncalled <b>because</b> promotion is
 * meant to create it — and promotion created nothing. The screen showed the promoted DPIA with live
 * Approve / Submit / Reject buttons addressing an id the server had never issued.
 *
 * <p>The payload key is {@code processingActivityName}, which is what {@code intakeForms.ts}
 * actually collects. The frontend adapter this replaces read {@code projectName} — a key no form
 * emits — so every promoted DPIA title fell through to the {@code "Promoted from …"} default.
 */
@Component
@RequiredArgsConstructor
public class DpiaPromotionAdapter implements RegisterPromotionPort {

    private final DpiaService dpias;

    @Override public String target() { return "DPIA"; }

    @Override public String requiredPermission() { return "PRIVACY:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        Dpia d = new Dpia();
        d.setTitleEn(ctx.titleEn(500, "processingActivityName", "description"));
        d.setTitleAr(ctx.titleAr(500));
        d.setTriggerReason(ctx.firstText("triggerReason").orElse("NEW_PROCESSING_ACTIVITY"));
        d.setStatus("DRAFT");
        d.setOverallResidualRisk(ctx.severityOrMedium());
        d.setAssessmentDate(ctx.receivedOn());
        d.setConclusion(ctx.description());
        return String.valueOf(dpias.create(d).getId());
    }
}
