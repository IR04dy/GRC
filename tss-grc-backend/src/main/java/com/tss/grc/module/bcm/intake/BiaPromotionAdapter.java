package com.tss.grc.module.bcm.intake;

import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.bcm.service.BcmService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import com.tss.grc.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code BIA_SUBMISSION} → {@code bcm.bia_record} — F2-172.
 *
 * <h3>The one adapter that can legitimately refuse</h3>
 * A {@code BiaRecord} has no {@code organisation_id} of its own: {@code BcmService.createBia}
 * derives tenancy from the parent {@code CriticalProcess} and throws if that process is missing or
 * belongs to another tenant. So a BIA cannot be created out of thin air, and the intake form asks
 * for {@code businessUnit} and {@code processName} as <b>free text</b> — a name, not an id.
 *
 * <p>This resolves the name against the tenant's own critical processes and, when nothing matches,
 * throws with the list of what it could have matched. That is the honest outcome: inventing a
 * process to hang the BIA on would put an assessment against the wrong service, and creating an
 * orphan BIA is not possible without breaking the tenancy the parent link provides. The submission
 * stays open for the BCM lead to register the process first — which is a real workflow, not a
 * failure of it.
 */
@Component
@RequiredArgsConstructor
public class BiaPromotionAdapter implements RegisterPromotionPort {

    private final BcmService bcm;
    private final CriticalProcessRepository processes;

    @Override public String target() { return "BIA"; }

    @Override public String requiredPermission() { return "BCM:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        BiaRecord b = new BiaRecord();
        b.setProcessId(resolveProcessId(ctx));
        b.setAssessmentDate(ctx.receivedOn());
        b.setRtoHours(hours(ctx, "rtoHours"));
        b.setRpoHours(hours(ctx, "rpoHours"));
        b.setMtpdHours(hours(ctx, "mtpdHours"));
        b.setOverallImpact(ctx.severityOrMedium());
        b.setStatus("DRAFT");
        return String.valueOf(bcm.createBia(b).getId());
    }

    private Long resolveProcessId(IntakePromotionContext ctx) {
        Long orgId = TenantContext.requireOrganisationId();
        // An explicit id wins when the form ever grows one; today it never does, and that is why
        // the name path below is the real one rather than a fallback nobody exercises.
        Optional<Long> explicit = ctx.firstText("processId")
            .map(s -> s.replaceAll("[^0-9]", ""))
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong);
        List<CriticalProcess> owned = processes.findByOrganisationId(orgId);
        if (explicit.isPresent()) {
            return owned.stream().filter(p -> explicit.get().equals(p.getId())).findFirst()
                .map(CriticalProcess::getId)
                .orElseThrow(() -> new IllegalStateException(
                    "Critical process " + explicit.get() + " does not belong to this organisation."));
        }
        String wanted = ctx.firstText("processName", "businessUnit").orElse("");
        return owned.stream()
            .filter(p -> matches(p, wanted))
            .findFirst()
            .map(CriticalProcess::getId)
            .orElseThrow(() -> new IllegalStateException(
                "BIA submission " + ctx.referenceCode() + " names the process '" + wanted
                + "', which is not a critical process in this organisation. A BIA is an assessment "
                + "OF a process — register the process first, then promote. Known processes: "
                + owned.stream().map(CriticalProcess::getProcessNameEn).sorted().toList()));
    }

    private boolean matches(CriticalProcess p, String wanted) {
        if (wanted.isBlank()) return false;
        String w = wanted.toLowerCase(Locale.ROOT).trim();
        return eq(p.getProcessNameEn(), w) || eq(p.getProcessNameAr(), w);
    }

    private boolean eq(String a, String lowerWanted) {
        return a != null && a.toLowerCase(Locale.ROOT).trim().equals(lowerWanted);
    }

    private Integer hours(IntakePromotionContext ctx, String key) {
        return ctx.firstText(key)
            .map(s -> s.replaceAll("[^0-9]", ""))
            .filter(s -> !s.isEmpty())
            .map(Integer::parseInt)
            .orElse(null);
    }
}
