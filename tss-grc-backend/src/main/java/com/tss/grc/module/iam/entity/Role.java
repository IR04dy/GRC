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

    // PERF-NQ-002: LAZY (was EAGER). The auth path fetches roles+permissions in
    // one query via UserRepository.findWithRoles* (@EntityGraph); other readers
    // access permissions within an open @Transactional session.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        schema = "iam", name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();

    /**
     * F2-62 — the one predicate that decides whether this role confers access.
     *
     * <p>{@code is_active} used to be a <b>dead control</b>: five code paths wrote it and two
     * read it, but nothing enforced it. In particular {@code CustomUserDetailsService} minted
     * {@code ROLE_<name>} plus every permission of every assigned role without consulting it,
     * so deactivating a role removed no access at all — while the access-governance evidence
     * package handed an auditor a "Status: INACTIVE" row for that same role. The report and
     * the reality disagreed, and the report was the artefact leaving the building.
     *
     * <p>Callers share this method rather than re-deriving the test, because the two that
     * mattered were the enforcement path and the evidence pack: if those two ever disagree
     * again the defect is invisible from either side alone. {@code RoleIsActiveEnforcementTest}
     * pins their agreement.
     *
     * <p>Deliberately {@code Boolean.TRUE.equals} — identical to the predicate the evidence
     * pack already used, so NULL reads as INACTIVE. The column is {@code NOT NULL DEFAULT 1}
     * and measured zero NULLs in both databases, so this only ever concerns a transient
     * in-memory entity, whose field initialiser above is {@code true}.
     *
     * <p>Not a JavaBean accessor by design: {@code Role} maps by field access, and a
     * {@code getX}/{@code isX} name here would invite Hibernate to treat it as a property.
     */
    public boolean grantsAuthority() {
        return Boolean.TRUE.equals(isActive);
    }
}
