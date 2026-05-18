package com.tss.grc.module.platform.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Department;
import com.tss.grc.module.platform.repository.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Department CRUD — minimal create/update/list/archive lifecycle.
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code code} required, unique per tenant, ≤50 chars.</li>
 *   <li>{@code nameEn} required.</li>
 *   <li>Self-loop guarded: {@code parentId} may not equal {@code id}.</li>
 *   <li>{@code status} ∈ {ACTIVE, INACTIVE, ARCHIVED}.</li>
 * </ul>
 * Parent chain depth is not enforced — the table tolerates any depth and
 * the UI flattens for display.
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");

    private final DepartmentRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public List<Department> list() {
        return repo.findByOrganisationIdOrderByCodeAsc(TenantContext.getCurrentTenant());
    }

    @Transactional(readOnly = true)
    public Department get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
    }

    @Transactional
    public Department create(Department body) {
        validate(body, /*existingId*/ null);
        body.setId(null);
        body.setOrganisationId(TenantContext.getCurrentTenant());
        Department saved = repo.save(body);
        audit.log("CREATE", "Department", saved.getId(),
            "Department created: " + saved.getCode());
        return saved;
    }

    @Transactional
    public Department update(Long id, Department patch) {
        Department existing = get(id);
        if (patch.getNameEn()      != null) existing.setNameEn(patch.getNameEn());
        if (patch.getNameAr()      != null) existing.setNameAr(patch.getNameAr());
        if (patch.getParentId()    != null) existing.setParentId(patch.getParentId());
        if (patch.getHeadUserId()  != null) existing.setHeadUserId(patch.getHeadUserId());
        if (patch.getCostCentre()  != null) existing.setCostCentre(patch.getCostCentre());
        if (patch.getDescription() != null) existing.setDescription(patch.getDescription());
        if (patch.getStatus()      != null) {
            if (!STATUSES.contains(patch.getStatus()))
                throw new IllegalArgumentException("status must be one of " + STATUSES);
            existing.setStatus(patch.getStatus());
        }
        validate(existing, id);
        Department saved = repo.save(existing);
        audit.log("UPDATE", "Department", saved.getId(),
            "Department updated: " + saved.getCode());
        return saved;
    }

    @Transactional
    public Department archive(Long id) {
        Department d = get(id);
        d.setStatus("ARCHIVED");
        d.setUpdatedAt(LocalDateTime.now());
        Department saved = repo.save(d);
        audit.log("CUSTOM", "Department", saved.getId(),
            "Department archived: " + saved.getCode());
        return saved;
    }

    private void validate(Department d, Long existingId) {
        if (d.getCode() == null || d.getCode().isBlank())
            throw new IllegalArgumentException("code is required");
        if (d.getNameEn() == null || d.getNameEn().isBlank())
            throw new IllegalArgumentException("nameEn is required");
        if (d.getParentId() != null && d.getParentId().equals(existingId))
            throw new IllegalArgumentException("Department cannot be its own parent");
        if (d.getStatus() != null && !STATUSES.contains(d.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);

        // Uniqueness check: only fail when the conflicting row is a different one.
        repo.findByOrganisationIdAndCode(TenantContext.getCurrentTenant(), d.getCode())
            .filter(other -> !other.getId().equals(existingId))
            .ifPresent(other -> {
                throw new IllegalArgumentException(
                    "Department code already exists for this tenant: " + d.getCode());
            });
    }
}
