package com.tss.grc.module.iam.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "iam", name = "roles")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Role extends BaseEntity {
    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id")
    private Long organisationId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "display_name_en", nullable = false, length = 200)
    private String displayNameEn;

    @Column(name = "display_name_ar", length = 200)
    private String displayNameAr;

    @Column(name = "is_system_role")
    private Boolean isSystemRole = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        schema = "iam", name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
}
