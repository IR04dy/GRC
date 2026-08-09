package com.tss.grc.module.ethics.intake;

import com.tss.grc.module.ethics.entity.EthicsCase;
import com.tss.grc.module.ethics.service.EthicsCaseService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code SPEAKUP} / {@code ETHICS_DISCLOSURE} → {@code ethics.ethics_case} — F2-172.
 *
 * <h3>Anonymity has to survive the promotion</h3>
 * SpeakUp is the whistleblower channel (Nazaha, ISO 37001 §8.3) and the submission may be
 * anonymous. {@code is_anonymous} is copied from the intake and the reporter fields are left null
 * when it is set — an anonymous report that acquires a name at promotion time is worse than one
 * that was never anonymous, because the reporter was told otherwise.
 *
 * <p>{@code ownerName()} deliberately is <b>not</b> used here. Everywhere else it falls back to the
 * triager so the record has a person on it; on an anonymous ethics case that would attribute the
 * report to the triager, who is not the reporter.
 */
@Component
@RequiredArgsConstructor
public class EthicsCasePromotionAdapter implements RegisterPromotionPort {

    private final EthicsCaseService cases;

    @Override public String target() { return "ETHICS_CASE"; }

    /**
     * {@code COMPLIANCE:WRITE}, not {@code ETHICS:WRITE} — measured, not assumed:
     * {@code EthicsCaseController} guards with {@code hasPermission('COMPLIANCE','WRITE')}. An
     * {@code ETHICS} module does exist in the permission catalogue, which is exactly why this is
     * worth a sentence: guessing the obvious one would have made promotion stricter than the
     * direct route, and a 403 nobody can explain reads as a broken feature.
     */
    @Override public String requiredPermission() { return "COMPLIANCE:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        EthicsCase c = new EthicsCase();
        c.setCaseCode("EC-" + ctx.referenceCode());
        c.setTitleEn(ctx.titleEn(255, "summary", "description", "disclosureType"));
        c.setTitleAr(ctx.titleAr(255));
        c.setCategory(ctx.firstText("category", "disclosureType").orElse("OTHER"));
        c.setSeverity(ctx.severityOrMedium());
        c.setIntakeChannel("WEB_FORM");
        c.setIsAnonymous(ctx.anonymous());
        c.setReporterName(ctx.anonymous() ? null : ctx.submitterName());
        c.setReporterEmail(ctx.anonymous() ? null : ctx.submitterEmail());
        c.setDescription(ctx.description());
        c.setStatus("INTAKE");
        c.setTriageDecision("PENDING");
        c.setRegulatorNotified(false);
        return String.valueOf(cases.create(c).getId());
    }
}
