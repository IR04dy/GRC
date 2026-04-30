package com.tss.grc.module.risk.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.RiskCategory;
import com.tss.grc.module.risk.repository.RiskCategoryRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/risk/categories")
@RequiredArgsConstructor
@Tag(name = "Risk Categories")
@SecurityRequirement(name = "bearerAuth")
public class RiskCategoryController {

    private final RiskCategoryRepository categoryRepository;

    @GetMapping
    @PreAuthorize("hasPermission('RISK', 'READ')")
    public List<RiskCategory> list() {
        return categoryRepository.findByOrganisationIdAndIsDeletedFalse(TenantContext.getCurrentTenant());
    }
}
