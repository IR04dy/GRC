package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * CH-17: a change freeze / blackout window. During an active window,
 * ChangeManagementService rejects a move to SCHEDULED whose scheduled window
 * intersects it — unless the change is EMERGENCY or the window allows
 * emergencies. Scope ALL freezes the whole org; CATEGORY freezes only changes
 * whose {@code category} equals {@code scopeValue}.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_freeze_window")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeFreezeWindow {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_ar", length = 200)
    private String nameAr;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "scope", nullable = false, length = 20)
    private String scope = "ALL";          // ALL | CATEGORY

    @Column(name = "scope_value", length = 50)
    private String scopeValue;             // category when scope = CATEGORY

    @Column(name = "allow_emergency", nullable = false)
    private Boolean allowEmergency = true;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;

    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;
}
