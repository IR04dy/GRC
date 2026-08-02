package com.tss.grc.module.change_adapters;

import com.tss.grc.module.change_mgmt.spi.IncidentPort;
import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.dto.IncidentResponse;
import com.tss.grc.module.incident.service.IncidentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * S5 / CE-1 — {@link IncidentPort} over {@code module.incident} (CH-13's rollback hook).
 *
 * <p>The eight nulls and the {@code "OPEN"} literal that used to sit in
 * {@code ChangeManagementService} live here instead. That is the whole point of a narrow port: the
 * change module said what it wanted ("open an incident for this failed change") and this class knows
 * how the incident module wants to be asked. Previously the change module knew both, which is why it
 * imported a DTO in order to send nulls through most of it.
 *
 * <p>{@code discoveryDate} is today, not the change's implementation date: the incident was
 * *discovered* when the rollback was recorded, and back-dating discovery would shift an incident-
 * response SLA to a moment nobody was watching.
 */
@Component
public class IncidentAdapter implements IncidentPort {

    private final IncidentService incidents;

    public IncidentAdapter(@Autowired(required = false) IncidentService incidents) {
        this.incidents = incidents;
    }

    @Override
    public Optional<RaisedIncident> raiseFromChange(String titleEn, String titleAr, String severity,
                                                    String category, String ownerName, String description) {
        if (incidents == null) return Optional.empty();
        IncidentResponse inc = incidents.create(new IncidentRequest(
            titleEn, titleAr, severity, category, "OPEN",
            blank(ownerName),
            LocalDate.now(), null,
            description,
            null, null, null));
        return inc == null ? Optional.empty()
                           : Optional.of(new RaisedIncident(inc.id(), inc.title()));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
}
