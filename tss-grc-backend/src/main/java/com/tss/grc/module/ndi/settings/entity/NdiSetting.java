package com.tss.grc.module.ndi.settings.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(schema = "ndi", name = "settings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiSetting {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "setting_key", nullable = false, length = 80) private String settingKey;
    @Column(nullable = false, length = 40)                       private String category;
    @Column(name = "value_type", nullable = false, length = 20)  private String valueType;
    @Column(name = "value_text", columnDefinition = "NVARCHAR(MAX)") private String valueText;

    @Column(name = "name_en", nullable = false, length = 255) private String nameEn;
    @Column(name = "name_ar", length = 255)                   private String nameAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(name = "default_value", columnDefinition = "NVARCHAR(MAX)") private String defaultValue;
    @Column(name = "is_locked",     nullable = false)                   private Boolean isLocked;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (category == null)  category = "GENERAL";
        if (valueType == null) valueType = "STRING";
        if (isLocked == null)  isLocked = false;
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
