package com.tss.grc.module.audit_eng.intake;

import com.tss.grc.module.audit_eng.dto.AuditEngagementDto;
import com.tss.grc.module.audit_eng.service.AuditEngagementService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code AUDIT_REQUEST} → {@code audit_eng.audit_engagement} — F2-172.
 *
 * <p>Returns the engagement's <b>external id</b> (a UUID string), because that is what
 * {@code AuditEngagementDto.id} is and what every route in this module keys on. It is also why
 * {@link RegisterPromotionPort#promote} returns a String rather than a Long: two of the fourteen
 * registers do not key on a bigint, and widening the type later would have meant touching all of
 * them.
 *
 * <p>Status is left to the service, which pins {@code PLANNING} — a requested audit is not a
 * scheduled one, and the annual-plan link is a decision for the audit lead.
 */
@Component
@RequiredArgsConstructor
public class EngagementPromotionAdapter implements RegisterPromotionPort {

    private final AuditEngagementService engagements;

    @Override public String target() { return "ENGAGEMENT"; }

    /** {@code AuditEngagementController} guards with {@code hasPermission('AUDIT','WRITE')}. */
    @Override public String requiredPermission() { return "AUDIT:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        AuditEngagementDto dto = new AuditEngagementDto();
        dto.setEngagementCode("ENG-" + ctx.referenceCode());
        dto.setTitleEn(ctx.titleEn(255, "auditTitle", "scopeDescription"));
        dto.setTitleAr(ctx.titleAr(255));
        dto.setScopeEn(ctx.firstText("scopeDescription").orElseGet(ctx::description));
        dto.setObjectiveEn(ctx.firstText("objectives").orElse(null));
        dto.setAuditType("INTERNAL");
        dto.setFramework(ctx.firstText("frameworks").orElse(null));
        dto.setLeadAuditor(ctx.ownerName());
        return engagements.create(dto).getId();
    }
}
