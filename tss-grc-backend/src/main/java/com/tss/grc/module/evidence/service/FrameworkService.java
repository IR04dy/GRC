package com.tss.grc.module.evidence.service;

import com.tss.grc.module.evidence.dto.ControlResponse;
import com.tss.grc.module.evidence.dto.FrameworkResponse;
import com.tss.grc.module.evidence.dto.SubControlResponse;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FrameworkService {

    private final EvidenceFrameworkRepository frameworkRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceSubControlRepository subControlRepository;

    @Transactional(readOnly = true)
    public List<FrameworkResponse> listFrameworks() {
        List<EvidenceFramework> frameworks = frameworkRepository.findByIsActiveTrue();

        List<Long> frameworkIds = frameworks.stream().map(EvidenceFramework::getId).toList();
        List<EvidenceControl> allControls = controlRepository.findByFrameworkIdIn(frameworkIds);

        List<Long> controlIds = allControls.stream().map(EvidenceControl::getId).toList();
        List<EvidenceSubControl> allSubControls = subControlRepository.findByControlIdIn(controlIds);

        Map<Long, List<EvidenceSubControl>> subsByControl = allSubControls.stream()
                .collect(Collectors.groupingBy(EvidenceSubControl::getControlId));

        Map<Long, List<EvidenceControl>> controlsByFramework = allControls.stream()
                .collect(Collectors.groupingBy(EvidenceControl::getFrameworkId));

        return frameworks.stream()
                .map(f -> toFrameworkResponse(f,
                        controlsByFramework.getOrDefault(f.getId(), List.of()),
                        subsByControl))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubControlResponse> listSubControls(Long controlId) {
        return subControlRepository.findByControlIdAndIsActiveTrue(controlId)
                .stream()
                .map(sc -> toSubControlResponse(sc, null))
                .toList();
    }

    private FrameworkResponse toFrameworkResponse(EvidenceFramework f,
                                                   List<EvidenceControl> controls,
                                                   Map<Long, List<EvidenceSubControl>> subsByControl) {
        List<ControlResponse> controlResponses = controls.stream()
                .map(c -> toControlResponse(c, subsByControl.getOrDefault(c.getId(), List.of())))
                .toList();
        return new FrameworkResponse(f.getId(), f.getExternalId(), f.getCode(),
                f.getNameEn(), f.getNameAr(), f.getVersion(), f.getDescriptionEn(),
                f.getIssuer(), f.getIsActive(), controlResponses);
    }

    private ControlResponse toControlResponse(EvidenceControl c, List<EvidenceSubControl> subs) {
        List<SubControlResponse> subResponses = subs.stream()
                .map(sc -> toSubControlResponse(sc, null))
                .toList();
        return new ControlResponse(c.getId(), c.getExternalId(), c.getFrameworkId(),
                c.getControlCode(), c.getNameEn(), c.getNameAr(), c.getDescriptionEn(),
                c.getIsActive(), subResponses);
    }

    private SubControlResponse toSubControlResponse(EvidenceSubControl sc, Long evidenceCount) {
        return new SubControlResponse(sc.getId(), sc.getExternalId(), sc.getControlId(),
                sc.getSubControlCode(), sc.getNameEn(), sc.getNameAr(), sc.getDescriptionEn(),
                sc.getEvidenceGuidance(), sc.getIsActive(),
                evidenceCount != null ? evidenceCount : 0L);
    }
}
