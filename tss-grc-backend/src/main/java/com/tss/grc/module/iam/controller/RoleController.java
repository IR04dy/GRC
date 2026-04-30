package com.tss.grc.module.iam.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.repository.RoleRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasPermission('IAM', 'READ')")
    public List<Role> list() {
        return roleRepository.findByOrganisationIdOrOrganisationIdIsNull(TenantContext.getCurrentTenant());
    }
}
