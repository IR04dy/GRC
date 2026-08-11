package com.tss.grc.module.change_adapters;

import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.service.ChangeManagementService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/**
 * {@code CHANGE_REQUEST} → {@code change.change_request} — F2-172.
 *
 * <h3>Why this one adapter is not in {@code module/<target>/intake} like the other twelve</h3>
 * {@code ChangeModuleBoundaryTest} enforces ADR-012: {@code module/change_mgmt/**} may import only
 * itself, {@code common.*}, {@code security.*} and {@code platform.*}. {@code
 * com.tss.grc.module.intake.spi} is a peer module, so an adapter placed under {@code
 * change_mgmt/intake} would fail that test — correctly, and in the same commit that declared the
 * new seam. This package exists for exactly that, is already homed to the {@code CHANGE} SKU in
 * {@code SkuRegistry}, and already holds the four ports bridging Change to its peers.
 *
 * <p>The direction is the same as the other twelve, just expressed one package out: the arrow
 * points from {@code CHANGE} to a Base interface, never from Base to {@code CHANGE}.
 *
 * <h3>{@code title_ar} is NOT NULL on this table</h3>
 * Alone among the fourteen targets, {@code change_request.title_ar} is a non-nullable column, so
 * the bilingual rule is load-bearing here rather than conventional. The form does collect
 * {@code titleAr}; the composed catalogue title covers the case where it was left blank, and a
 * blank would otherwise be a constraint violation surfacing as a 500 from inside a promotion.
 */
@Component
@RequiredArgsConstructor
public class ChangePromotionAdapter implements RegisterPromotionPort {

    /**
     * The status a promoted change is created with — {@code ck_change_status}, a 16-value vocabulary of
     * which this is deliberately the first.
     */
    static final String PROMOTED_STATUS = "DRAFT";

    /**
     * Every value this adapter can write to {@code change_requests.change_type} — {@code ck_change_type}.
     *
     * <p>⚠ This used to be {@code ctx.firstText("changeType").orElse("NORMAL")}: whatever the payload
     * said, straight into a CHECK-constrained column. It happens to work today because
     * {@code CHANGE_REQUEST}'s form offers exactly {@code STANDARD | NORMAL | EMERGENCY} — so this was
     * not a defect, it was the {@link com.tss.grc.module.incident.intake.IncidentPromotionAdapter} defect
     * with a form that happened to agree. F2-180 clamps it because "the caller is currently sending legal
     * values" is a fact about today's frontend, not a property of the server, and the payload does not
     * have to come from that form.
     *
     * <p>⚙ Nearly a real collision: {@code CAPITAL_CHANGE_NOTIFICATION}'s form also has a
     * {@code changeType} field, whose values are {@code INCREASE | DECREASE}. Only the catalogue's
     * routing keeps them apart — that kind promotes to {@code POLICY}, not here — so one re-routing
     * would have turned a near-miss into the same failure.
     */
    static final Set<String> CHANGE_TYPES = Set.of("STANDARD", "NORMAL", "EMERGENCY");

    /** The type an unrecognised or absent {@code changeType} becomes. */
    static final String CHANGE_TYPE_DEFAULT = "NORMAL";

    private final ChangeManagementService changes;

    @Override public String target() { return "CHANGE"; }

    @Override public String requiredPermission() { return "CHANGE:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        ChangeRequest c = new ChangeRequest();
        c.setChangeCode("CR-" + ctx.referenceCode());
        c.setTitleEn(ctx.titleEn(255, "titleEn", "description"));
        c.setTitleAr(ctx.firstText("titleAr").orElseGet(() -> ctx.titleAr(255)));
        c.setDescriptionEn(ctx.description());
        c.setChangeType(changeType(ctx));
        c.setRiskLevel(ctx.severityOrMedium());
        c.setRollbackPlan(ctx.firstText("rollbackPlan").orElse(null));
        c.setRequestedByName(ctx.ownerName());
        c.setRequestedDate(ctx.receivedOn());
        c.setScheduledStart(dateTime(ctx));
        // DRAFT, not SUBMITTED: the CAB gate is a transition the change module owns, and entering
        // its queue from a side door is the F2-04 shape in workflow form.
        c.setStatus(PROMOTED_STATUS);
        return String.valueOf(changes.create(c).getId());
    }

    private String changeType(IntakePromotionContext ctx) {
        return ctx.firstText("changeType")
            .map(s -> s.trim().toUpperCase(Locale.ROOT))
            .filter(CHANGE_TYPES::contains)
            .orElse(CHANGE_TYPE_DEFAULT);
    }

    private java.time.LocalDateTime dateTime(IntakePromotionContext ctx) {
        return ctx.firstText("proposedScheduleAt").map(raw -> {
            try {
                return raw.length() == 10
                    ? LocalDate.parse(raw).atStartOfDay()
                    : java.time.LocalDateTime.parse(raw);
            } catch (DateTimeParseException e) {
                return null;
            }
        }).orElse(null);
    }
}
