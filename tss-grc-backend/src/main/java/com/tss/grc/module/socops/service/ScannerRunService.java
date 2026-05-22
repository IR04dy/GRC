package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.socops.entity.ScannerRun;
import com.tss.grc.module.socops.repository.ScannerRunRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScannerRunService {

    private final ScannerRunRepository repo;

    @Transactional(readOnly = true)
    public Page<ScannerRun> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByStartedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public ScannerRun get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("ScannerRun " + id));
    }
}
