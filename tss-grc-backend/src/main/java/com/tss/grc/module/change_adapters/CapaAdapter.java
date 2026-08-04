package com.tss.grc.module.change_adapters;

import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.change_mgmt.spi.CapaPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * S7d / CE-7 — {@link CapaPort} over {@code module.capa}, the Base universal CAPA tracker.
 *
 * <p><b>{@code origin_type} lives here, not in the change module.</b> {@code "change_pir"} is a value
 * in the CAPA tracker's vocabulary, and it is spelled the way the eight existing origin types are
 * spelled — lower snake case ({@code control_test}, {@code audit_finding},
 * {@code processor_assessment}), measured off {@code capa.tasks} in both databases rather than
 * guessed. A change-module constant would have been a second home for a peer's vocabulary, which is
 * the coupling {@link CapaPort} exists to avoid.
 *
 * <p>Everything downstream of {@code CapaTaskService.create} comes for free and is the reason to go
 * through the service rather than the repository: the audit line, the {@code CAPA_TASK_CREATED} F8
 * event (so a tenant can route a high-severity change follow-up to whoever it wants), the realtime
 * broadcast, and the external-id generator that gives the CAPA a human-quotable code.
 */
@Component
public class CapaAdapter implements CapaPort {

    /**
     * The CAPA tracker's own name for "this came from a change's post-implementation review".
     * Lower snake case to match the eight origin types already in {@code capa.tasks}.
     */
    static final String ORIGIN_TYPE = "change_pir";

    private final CapaTaskService capas;

    public CapaAdapter(@Autowired(required = false) CapaTaskService capas) {
        this.capas = capas;
    }

    @Override
    public Optional<RaisedCapa> raiseFromChangePir(String changeCode,
                                                   String titleEn, String titleAr,
                                                   String descriptionEn, String descriptionAr,
                                                   String capaType, String severity,
                                                   String owner, LocalDate targetDate) {
        if (capas == null) return Optional.empty();
        CapaTaskDto req = CapaTaskDto.builder()
            .originType(ORIGIN_TYPE)
            .originId(changeCode)
            .titleEn(titleEn).titleAr(titleAr)
            .descriptionEn(descriptionEn).descriptionAr(descriptionAr)
            .capaType(capaType).severity(severity)
            .owner(owner).targetDate(targetDate)
            .build();
        CapaTaskDto saved = capas.create(req);
        return saved == null ? Optional.empty()
                             : Optional.of(new RaisedCapa(saved.getNumericId(), saved.getId()));
    }
}
