package com.tss.grc.module.change_mgmt.spi;

import java.util.Optional;

/**
 * S5 / CE-1 — the change module's outbound port to incident management (ADR-012).
 *
 * <p>CH-13: a rolled-back change is a textbook incident precursor, so the change module opens an
 * incident and back-links it. That made `change_mgmt` depend on `module.incident` — three imports
 * (`IncidentRequest`, `IncidentResponse`, `IncidentService`) for a single call that needs an id back.
 *
 * <p><b>Narrowed on purpose.</b> The port exposes one verb and returns two fields. The peer's
 * `IncidentRequest` has eleven components, most of which the change module passes as null; importing
 * a DTO to send nulls through it is the coupling, not the call. If a later slice needs the incident's
 * full record, it belongs on an incident *read* port with its own justification — a wide port is how
 * a boundary erodes without anyone deciding to erode it.
 *
 * <p>Implemented in {@code module.change_adapters}, outside both modules. Optional, like the direct
 * service was: when the adapter is absent the rollback hook no-ops rather than failing the
 * transition, because losing the derived incident must not block recording that a change was rolled
 * back.
 */
public interface IncidentPort {

    /** Just enough of the raised incident to back-link it. */
    record RaisedIncident(Long id, String incidentCode) { }

    /**
     * Open an incident derived from a change.
     *
     * @param severity mapped from the change's risk level by the caller — the change module owns
     *                 that mapping, because it is a statement about change risk, not about incidents
     * @return the new incident, or empty when incident management is unavailable
     */
    Optional<RaisedIncident> raiseFromChange(String titleEn, String titleAr, String severity,
                                             String category, String ownerName, String description);
}
