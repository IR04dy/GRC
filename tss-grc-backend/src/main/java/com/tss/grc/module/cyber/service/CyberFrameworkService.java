package com.tss.grc.module.cyber.service;

import com.tss.grc.module.cyber.entity.CyberFramework;
import com.tss.grc.module.cyber.repository.CyberFrameworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cyber framework catalogue (ECC / CSCC / CCC / OTCC …).
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001):
 * one service per aggregate, the controller fans out. Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberFrameworkService {

    private final CyberFrameworkRepository frameworkRepo;

    // PERF-PG-004: hard cap for the unpaginated "/all" dropdown dumps so a single
    // request can never fetch the whole org-scoped table.
    private static final int DROPDOWN_CAP = 500;

    @Transactional(readOnly = true)
    public List<CyberFramework> getFrameworks(Long orgId) {
        // PERF-PG-004: cap the dropdown dump (see DROPDOWN_CAP).
        return frameworkRepo.findByOrganisationId(orgId, PageRequest.of(0, DROPDOWN_CAP)).getContent();
    }

    @Transactional
    public CyberFramework saveFramework(CyberFramework f, Long orgId) {
        // Mass-assignment defense — controller accepts the entity directly
        // via @RequestBody. Null id forces save() to INSERT instead of
        // merge() into a potentially cross-tenant row.
        f.setId(null);
        f.setOrganisationId(orgId);
        f.setIsDeleted(false);
        return frameworkRepo.save(f);
    }
}
