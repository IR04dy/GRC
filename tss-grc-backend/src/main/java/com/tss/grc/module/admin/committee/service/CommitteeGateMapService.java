package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeGateMap;
import com.tss.grc.module.admin.committee.repository.CommitteeGateMapRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.change_mgmt.service.ChangeGovernanceHealthService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ADR-017 <b>D-2</b> — the write side of the per-tenant change-authority configuration.
 *
 * <p><b>Why this class did not exist.</b> {@code admin.committee_gate_map} has been the per-tenant
 * CAB/ECAB identity since V310 (2026-07-23) and was seeded by V314, but it shipped with an entity, a
 * two-method read repository and <b>no writer of any kind</b> — no service, no endpoint, no screen.
 * The only way a tenant could name its own change authority was a DBA {@code INSERT}. That is why
 * D-2's requirement that the failure be "a guided onboarding moment that names the screen" could not
 * be satisfied by a message alone: there was no screen to name, so naming one would have been a lie
 * of exactly the kind this programme keeps finding in guard comments.
 *
 * <p>The REST surface for this service is {@code ChangeAuthorityController} in {@code change_mgmt} —
 * see its javadoc for why the endpoint is change-governance while this service stays in the committee
 * domain that owns the row it writes and the state it validates.
 *
 * <p><b>Validation is the same predicate the gate enforces.</b> An upsert refuses a committee that is
 * missing, in another tenant, soft-deleted or {@code DISSOLVED} — the conditions
 * {@link ChangeGovernanceHealthService} treats as unconfigured and
 * {@code CommitteeReferralService.refer} rejects at referral time. Accepting a value here that the
 * enforcement point rejects later is how a tenant ends up with a gate that reads configured and
 * refuses every approval.
 */
@Service
@RequiredArgsConstructor
public class CommitteeGateMapService {

    /** The authority gates the CH-17 matrix can demand a committee for. {@code AUTO} needs none. */
    public static final Set<String> GATES = Set.of("CAB", "ECAB");

    private final CommitteeGateMapRepository gateMapRepo;
    private final CommitteeRepository committeeRepo;
    private final ChangeGovernanceHealthService governanceHealth;
    private final AuditLogService audit;

    /**
     * The calling tenant's configuration for every gate — including the gates that have none, so the
     * screen can render an empty row rather than omitting it. {@code configured} is
     * {@link ChangeGovernanceHealthService}'s verdict, not "a row exists": a row pointing at a
     * dissolved committee lists with {@code configured=false} and its own fault reason.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> current() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, CommitteeGateMap> byGate = new HashMap<>();
        for (CommitteeGateMap g : gateMapRepo.findByOrganisationId(org)) byGate.put(g.getGate(), g);

        List<Map<String, Object>> out = new ArrayList<>();
        for (String gate : List.of("CAB", "ECAB")) {   // ordered for the UI, not GATES' set order
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("gate", gate);
            CommitteeGateMap g = byGate.get(gate);
            row.put("committeeId", g == null ? null : g.getCommitteeId());
            if (g != null) {
                committeeRepo.findByIdAndOrganisationId(g.getCommitteeId(), org).ifPresent(c -> {
                    row.put("committeeNameEn", c.getNameEn());
                    row.put("committeeNameAr", c.getNameAr());
                    row.put("committeeType",   c.getCommitteeType());
                    row.put("committeeStatus", c.getStatus());
                });
            }
            String fault = governanceHealth.gateConfigurationFault(org, gate);
            row.put("configured", fault == null);
            row.put("fault", fault);
            out.add(row);
        }
        return out;
    }

    /**
     * Name (or re-point) the committee that acts as this tenant's {@code gate}. Idempotent on
     * (tenant, gate) — the table carries a unique index on that pair, so this updates in place
     * rather than accumulating history.
     */
    @Transactional
    public Map<String, Object> upsert(String gate, Long committeeId) {
        Long org = TenantContext.getCurrentTenant();
        String g = gate == null ? "" : gate.trim().toUpperCase(Locale.ROOT);
        if (!GATES.contains(g))
            throw new IllegalArgumentException("gate must be one of " + GATES + " (AUTO needs no committee)");
        if (committeeId == null)
            throw new IllegalArgumentException("committeeId is required — to clear the gate, DELETE it");

        Committee c = committeeRepo.findByIdAndOrganisationId(committeeId, org)
            .orElseThrow(() -> new EntityNotFoundException(
                "Committee " + committeeId + " not found in this organisation"));
        if ("DISSOLVED".equals(c.getStatus()))
            throw new IllegalArgumentException("Committee " + committeeId + " is dissolved and cannot "
                + "act as the " + g + " authority — constitute an ACTIVE committee first");

        Optional<CommitteeGateMap> existing = gateMapRepo.findByOrganisationIdAndGate(org, g);
        CommitteeGateMap row = existing.orElseGet(CommitteeGateMap::new);
        Long previous = existing.map(CommitteeGateMap::getCommitteeId).orElse(null);
        row.setOrganisationId(org);
        row.setGate(g);
        row.setCommitteeId(committeeId);
        row.setIsDeleted(false);
        CommitteeGateMap saved = gateMapRepo.save(row);

        audit.log("GATE_MAP_SET", "CommitteeGateMap", saved.getId(),
            "Change authority " + g + " → committee " + committeeId
                + (previous == null ? " (was unset)" : " (was " + previous + ")"));
        return Map.of("gate", g, "committeeId", committeeId, "configured",
            governanceHealth.gateConfigurationFault(org, g) == null);
    }

    /**
     * Clear the gate. Soft-deletes, matching the platform convention and keeping the audit trail —
     * and note what this now means: after ADR-017 D-2 the tenant's changes that route to this gate
     * <b>stop being approvable</b>, which is the point. The audit line says so, because "who
     * unconfigured the CAB" is the first question after a blocked release.
     */
    @Transactional
    public void clear(String gate) {
        Long org = TenantContext.getCurrentTenant();
        String g = gate == null ? "" : gate.trim().toUpperCase(Locale.ROOT);
        if (!GATES.contains(g))
            throw new IllegalArgumentException("gate must be one of " + GATES);
        CommitteeGateMap row = gateMapRepo.findByOrganisationIdAndGate(org, g)
            .orElseThrow(() -> new EntityNotFoundException("No " + g + " authority is configured"));
        Long was = row.getCommitteeId();
        row.setIsDeleted(true);
        gateMapRepo.save(row);
        audit.log("GATE_MAP_CLEAR", "CommitteeGateMap", row.getId(),
            "Change authority " + g + " cleared (was committee " + was + ") — changes whose "
                + "authorisation matrix demands " + g + " can no longer be approved until it is re-set");
    }

    /**
     * The committees that could serve as an authority in this tenant: ACTIVE or INACTIVE, never
     * DISSOLVED. Feeds the screen's picker so an administrator cannot choose a value the upsert
     * would reject.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> eligibleCommittees() {
        Long org = TenantContext.getCurrentTenant();
        return committeeRepo.findByOrganisationId(org).stream()
            .filter(c -> !"DISSOLVED".equals(c.getStatus()))
            .map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("nameEn", c.getNameEn());
                m.put("nameAr", c.getNameAr());
                m.put("committeeType", c.getCommitteeType());
                m.put("status", c.getStatus());
                return m;
            })
            .toList();
    }
}
