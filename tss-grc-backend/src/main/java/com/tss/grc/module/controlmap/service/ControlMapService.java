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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ControlMapService {

    private final ControlLinkRepository repo;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceFrameworkRepository frameworkRepository;

    @Transactional(readOnly = true)
    public List<ControlLinkResponse> getMappings(String entityType, Long entityId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndEntityTypeAndEntityId(orgId, entityType, entityId)
            .stream().map(l -> resolve(l.getSubControlId())).toList();
    }

    /** Replace-set: the entity's mappings become exactly the supplied sub-control ids. */
    @Transactional
    public List<ControlLinkResponse> setMappings(String entityType, Long entityId,
                                                 List<Long> subControlIds, Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        repo.deleteForEntity(orgId, entityType, entityId);
        List<Long> distinct = subControlIds == null ? List.of()
            : subControlIds.stream().filter(Objects::nonNull).distinct().toList();
        for (Long scId : distinct) {
            ControlLink l = new ControlLink();
            l.setOrganisationId(orgId);
            l.setEntityType(entityType);
            l.setEntityId(entityId);
            l.setSubControlId(scId);
            l.setCreatedBy(userId);
            repo.save(l);
        }
        return distinct.stream().map(this::resolve).toList();
    }

    private ControlLinkResponse resolve(Long scId) {
        EvidenceSubControl sc = subControlRepository.findById(scId).orElse(null);
        if (sc == null) {
            return new ControlLinkResponse(scId, null, null, null, null, null);
        }
        String controlCode = null, frameworkCode = null;
        EvidenceControl ctrl = controlRepository.findById(sc.getControlId()).orElse(null);
        if (ctrl != null) {
            controlCode = ctrl.getControlCode();
            EvidenceFramework fw = frameworkRepository.findById(ctrl.getFrameworkId()).orElse(null);
            if (fw != null) frameworkCode = fw.getCode();
        }
        return new ControlLinkResponse(scId, sc.getSubControlCode(), sc.getNameEn(), sc.getNameAr(),
            controlCode, frameworkCode);
    }
}
