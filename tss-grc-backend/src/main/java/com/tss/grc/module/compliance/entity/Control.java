package com.tss.grc.module.compliance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

@Entity
@Table(schema = "compliance", name = "controls")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Control extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "framework", nullable = false, length = 100)
    private String framework;

    @Column(name = "domain", length = 100)
    private String domain;

    @Column(name = "maturity_level")
    private Integer maturityLevel = 1;

    @Column(name = "status", length = 50)
    private String status = "PLANNED";

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;
}
