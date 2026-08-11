package com.tss.grc.module.policy.intake;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.entity.PolicyException;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.service.PolicyManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code POLICY_EXCEPTION} → {@code policy.policy_exception} — F2-172.
 *
 * <h3>An exception is an exception TO something</h3>
 * {@code policy_exception.policy_id} is {@code NOT NULL} and {@code createException} refuses a null,
 * so this promotion resolves the submitter's {@code policyCode} to a policy this tenant owns and
 * throws — naming the code — when it does not exist. That is not a limitation to work around: an
 * exception whose parent policy is unknown cannot be reviewed, cannot expire with it, and cannot
 * appear on the policy's own exceptions tab, which is where a reviewer looks for it.
 *
 * <p>The submission is not lost when this happens; it stays open, and either the code was typed
 * wrong or the policy is not on the platform yet. Both are things a policy officer can fix, and
 * neither is fixable once a promotion has silently attached the exception to nothing.
 */
@Component
@RequiredArgsConstructor
public class PolicyExceptionPromotionAdapter implements RegisterPromotionPort {

    /**
     * The status a promoted exception is created with — {@code ck_policy_exc_status}. Named for the pin;
     * see {@code PromotionVocabularyTest}.
     */
    static final String PROMOTED_STATUS = "REQUESTED";

    private final PolicyManagementService policies;
    private final PolicyDocumentRepository documents;

    @Override public String target() { return "EXCEPTION"; }

    /** {@code PolicyController} guards the policy module with {@code COMPLIANCE:WRITE}. */
    @Override public String requiredPermission() { return "COMPLIANCE:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        PolicyException e = new PolicyException();
        e.setPolicyId(resolvePolicyId(ctx));
        e.setExceptionCode("EX-" + ctx.referenceCode());
        e.setTitleEn(ctx.titleEn(255, "policyTitle", "exceptionScope"));
        e.setTitleAr(ctx.titleAr(255));
        e.setJustificationEn(ctx.firstText("businessJustification").orElseGet(ctx::description));
        e.setCompensatingControlsEn(ctx.firstText("compensatingControls").orElse(null));
        e.setRequestorName(ctx.ownerName());
        e.setRequestorEmail(ctx.anonymous() ? null : ctx.submitterEmail());
        e.setRiskLevel(ctx.severityOrMedium());
        e.setRequestedAt(ctx.acknowledgedAt());
        e.setExpiryDate(sunset(ctx));
        e.setStatus(PROMOTED_STATUS);
        return String.valueOf(policies.createException(e).getId());
    }

    private Long resolvePolicyId(IntakePromotionContext ctx) {
        String code = ctx.firstText("policyCode").orElse("");
        Long orgId = TenantContext.requireOrganisationId();
        List<PolicyDocument> owned = documents.findByOrganisationIdOrderByPolicyCodeAscVersionDesc(orgId);
        return owned.stream()
            .filter(p -> p.getPolicyCode() != null
                && p.getPolicyCode().trim().equalsIgnoreCase(code.trim()))
            // Newest version of that code — an exception is raised against what is in force.
            .max(Comparator.comparing(p -> nullSafe(p.getVersion())))
            .map(PolicyDocument::getId)
            .orElseThrow(() -> new IllegalStateException(
                "Policy-exception request " + ctx.referenceCode() + " cites policy '" + code
                + "', which this organisation has no policy document for. An exception must hang "
                + "off the policy it excepts. Register the policy first, or correct the code."));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private LocalDate sunset(IntakePromotionContext ctx) {
        return ctx.firstText("sunsetDate").map(raw -> {
            try {
                return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
            } catch (DateTimeParseException e) {
                return null;
            }
        }).orElse(null);
    }
}
