package com.tss.grc.module.iam.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(schema = "iam", name = "permissions")
@Getter @Setter @NoArgsConstructor
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Column(name = "description_en", length = 300)
    private String descriptionEn;
}
