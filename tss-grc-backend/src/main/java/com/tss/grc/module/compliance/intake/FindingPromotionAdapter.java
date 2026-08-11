package com.tss.grc.module.compliance.intake;

import com.tss.grc.module.compliance.dto.FindingRequest;
import com.tss.grc.module.compliance.service.ComplianceService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code CONTROL_OBSERVATION} → {@code compliance.audit_finding} — F2-172.
 *
 * <p>{@code clauseRef} carries the {@code controlReference} the observer typed. It is free text on
 * the form, so it cannot be resolved to a {@code frameworkId} here without inventing a mapping;
 * putting it in {@code clause_ref} keeps the observer's own reference visible to whoever picks the
 * finding up, instead of dropping it.
 */
@Component
@RequiredArgsConstructor
public class FindingPromotionAdapter implements RegisterPromotionPort {

    /**
     * The status a promoted finding is created with — {@code CK_findings_status}. Named so
     * {@code PromotionVocabularyTest} can read it: F2-180 found two adapters whose status word was one
     * the column had never accepted, and an inline literal gives a pin nothing to compare.
     */
    static final String PROMOTED_STATUS = "OPEN";

    private final ComplianceService compliance;

    @Override public String target() { return "FINDING"; }

    @Override public String requiredPermission() { return "COMPLIANCE:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        FindingRequest req = new FindingRequest(
            ctx.titleEn(255, "observation", "summary"),
            null,                                    // assessmentId — raised outside an assessment
            ctx.severityOrMedium(),
            ctx.ownerName(),
            null,                                    // capa — opened by the finding's own workflow
            null,                                    // dueDate — set when the finding is accepted
            PROMOTED_STATUS,
            null,
            ctx.firstText("controlReference").map(s -> s.length() > 100 ? s.substring(0, 100) : s)
               .orElse(null));
        return String.valueOf(compliance.createFinding(req).id());
    }
}
