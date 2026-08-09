package com.tss.grc.module.incident.intake;

import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.service.IncidentService;
import com.tss.grc.module.intake.spi.IntakePromotionContext;
import com.tss.grc.module.intake.spi.RegisterPromotionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code SECURITY_INCIDENT} / {@code VRP_DISCLOSURE} / {@code WORKPLACE_INJURY} /
 * {@code CONSUMER_COMPLAINT_RESPONSE} → {@code incident.incidents} — F2-172.
 *
 * <p>Four kinds share this target and three of them do not look like a security event: a GOSI-
 * reportable workplace injury (24-hour notification) and a Maaroof consumer complaint (5 working
 * days) are both catalogued to {@code INCIDENT}. So the category comes from whichever field that
 * kind's form supplies rather than from a constant, and {@code discoveryDate} is the reporter's
 * timestamp where there is one.
 *
 * <p>{@code discoveryDate} is when the event was <b>observed</b>, not when it was promoted — the
 * same reasoning {@code IncidentAdapter} states for the change-rollback path, and for the same
 * reason: an incident-response SLA anchored to the triage moment starts at a point nobody was
 * watching.
 */
@Component
@RequiredArgsConstructor
public class IncidentPromotionAdapter implements RegisterPromotionPort {

    private final IncidentService incidents;

    @Override public String target() { return "INCIDENT"; }

    @Override public String requiredPermission() { return "INCIDENT:WRITE"; }

    @Override
    public String promote(IntakePromotionContext ctx) {
        IncidentRequest req = new IncidentRequest(
            ctx.titleEn(255, "summary", "description", "responseSummary", "injuryType"),
            ctx.titleAr(255),
            ctx.severityOrMedium(),
            category(ctx),
            "OPEN",
            ctx.ownerName(),
            ctx.receivedOn(),
            null,                        // resolutionDate — open by definition
            ctx.description(),
            null, null, null);           // timeline / lessons — nothing to record yet
        return String.valueOf(incidents.create(req).id());
    }

    private String category(IntakePromotionContext ctx) {
        return ctx.firstText("category", "complaintCategory", "injuryType")
            .orElseGet(() -> switch (ctx.kind()) {
                case "WORKPLACE_INJURY" -> "HSE";
                case "CONSUMER_COMPLAINT_RESPONSE" -> "CONSUMER";
                default -> "CYBER";
            });
    }
}
