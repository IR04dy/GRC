package com.tss.grc.module.controlmap.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.controlmap.dto.ControlLinkResponse;
import com.tss.grc.module.controlmap.entity.ControlLink;
import com.tss.grc.module.controlmap.repository.ControlLinkRepository;
import com.tss.grc.module.evidence.entity.EvidenceControl;
import com.tss.grc.module.evidence.entity.EvidenceFramework;
import com.tss.grc.module.evidence.entity.EvidenceSubControl;
import com.tss.grc.module.evidence.repository.EvidenceControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceFrameworkRepository;
import com.tss.grc.module.evidence.repository.EvidenceSubControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ControlMapService {

    private final ControlLinkRepository repo;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceFrameworkRepository frameworkRepository;

    /** Allowed satisfaction levels; anything else falls back to FULL. */
    private static final Set<String> SATISFACTIONS = Set.of("FULL", "PARTIAL", "PLANNED");

    private static String normalizeSatisfaction(String s) {
        if (s == null) return "FULL";
        String up = s.trim().toUpperCase();
        return SATISFACTIONS.contains(up) ? up : "FULL";
    }

    @Transactional(readOnly = true)
    public List<ControlLinkResponse> getMappings(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndEntityTypeAndEntityId(orgId, entityType, entityId)
            .stream().map(l -> resolve(l.getSubControlId(), l.getSatisfaction())).toList();
    }

    /** Replace-set: the entity's mappings become exactly the supplied sub-control ids. */
    @Transactional
    public List<ControlLinkResponse> setMappings(String entityType, Long entityId,
                                                 List<Long> subControlIds, Map<Long, String> satisfaction,
                                                 Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        repo.deleteForEntity(orgId, entityType, entityId);
        Map<Long, String> sat = satisfaction == null ? Map.of() : satisfaction;
        List<Long> distinct = subControlIds == null ? List.of()
            : subControlIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long scId : distinct) {
            ControlLink l = new ControlLink();
            l.setOrganisationId(orgId);
            l.setEntityType(entityType);
            l.setEntityId(entityId);
            l.setSubControlId(scId);
            l.setSatisfaction(normalizeSatisfaction(sat.get(scId)));
            l.setCreatedBy(userId);
            repo.save(l);
        }
        return distinct.stream()
            .map(scId -> resolve(scId, normalizeSatisfaction(sat.get(scId)))).toList();
    }

    private ControlLinkResponse resolve(Long scId, String satisfaction) {
        String sat = normalizeSatisfaction(satisfaction);
        EvidenceSubControl sc = subControlRepository.findById(scId).orElse(null);
        if (sc == null) {
            return new ControlLinkResponse(scId, null, null, null, null, null, sat);
        }
        String controlCode = null, frameworkCode = null;
        EvidenceControl ctrl = controlRepository.findById(sc.getControlId()).orElse(null);
        if (ctrl != null) {
            controlCode = ctrl.getControlCode();
            EvidenceFramework fw = frameworkRepository.findById(ctrl.getFrameworkId()).orElse(null);
            if (fw != null) frameworkCode = fw.getCode();
        }
        return new ControlLinkResponse(scId, sc.getSubControlCode(), sc.getNameEn(), sc.getNameAr(),
            controlCode, frameworkCode, sat);
    }
}
