package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberAsset;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cyber asset inventory.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberAssetService {

    private final CyberAssetRepository assetRepo;

    // PERF-PG-004: hard cap for the unpaginated "/all" dropdown dumps.
    private static final int DROPDOWN_CAP = 500;

    @Transactional(readOnly = true)
    public Page<CyberAsset> getAssets(Long orgId, Pageable p) {
        return assetRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public List<CyberAsset> getAllAssets(Long orgId) {
        // PERF-PG-004: cap the dropdown dump (see DROPDOWN_CAP).
        return assetRepo.findByOrganisationId(orgId, PageRequest.of(0, DROPDOWN_CAP)).getContent();
    }

    @Transactional
    public CyberAsset saveAsset(CyberAsset a, Long orgId) {
        // CY-H — asset_name is NOT NULL; reject a blank/empty body with 400 not a DB 500.
        a.setAssetName(CyberInputValidation.requireText(a.getAssetName(), "assetName"));
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        a.setId(null);
        a.setOrganisationId(orgId);
        a.setIsDeleted(false);
        return assetRepo.save(a);
    }

    @Transactional
    public CyberAsset updateAsset(Long id, CyberAsset req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberAsset existing = assetRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberAsset not found: " + id));
        applyEditable(existing, req);
        return assetRepo.save(existing);
    }

    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * Persisting the REQUEST OBJECT reset the three link counters to their `int` initialiser
     * of 0, so an asset carrying vulnerabilities, incidents and controls read as carrying
     * none after any edit. `CyberAssetRequest` declares neither; they are maintained by the
     * link paths.
     */
    private void applyEditable(CyberAsset existing, CyberAsset req) {
        existing.setAssetName(req.getAssetName());
        existing.setAssetType(req.getAssetType());
        existing.setBusinessOwner(req.getBusinessOwner());
        existing.setTechnicalOwner(req.getTechnicalOwner());
        existing.setClassification(req.getClassification());
        existing.setCriticality(req.getCriticality());
        existing.setIpAddress(req.getIpAddress());
        existing.setHostname(req.getHostname());
        existing.setOperatingSystem(req.getOperatingSystem());
        existing.setStatus(req.getStatus());
    }

    @Transactional
    public void deleteAsset(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberAsset a = assetRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberAsset not found: " + id));
        a.setIsDeleted(true);
        assetRepo.save(a);
    }
}
