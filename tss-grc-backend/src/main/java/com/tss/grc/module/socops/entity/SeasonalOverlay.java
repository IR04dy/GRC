package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Saudi-specific seasonal risk window — Hajj, Umrah, Ramadan, National Day.
 *
 * <p>During these windows the threat profile shifts: DDoS surge against
 * payment rails, phishing spikes targeting expat remittance and pilgrimage
 * apps (Tawakkalna/Nusuk), e-payment fraud peaks. The F8 automation engine
 * consults this table to auto-tighten KRI thresholds + incident SLAs during
 * an active overlay.
 */
@Entity
@Table(schema = "socops", name = "seasonal_overlay")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SeasonalOverlay extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "overlay_code", nullable = false, length = 40) private String overlayCode;
    @Column(name = "title_en", nullable = false, length = 200) private String titleEn;
    @Column(name = "title_ar", length = 200) private String titleAr;
    @Column(name = "starts_at", nullable = false) private LocalDateTime startsAt;
    @Column(name = "ends_at", nullable = false) private LocalDateTime endsAt;
    @Column(name = "hijri_label", length = 60) private String hijriLabel;
    @Column(name = "threat_profile", columnDefinition = "NVARCHAR(MAX)") private String threatProfile;
    @Column(name = "auto_tighten_kris", nullable = false) private Boolean autoTightenKris = true;
    @Column(name = "auto_tighten_slas", nullable = false) private Boolean autoTightenSlas = true;
    @Column(name = "notify_recipients", columnDefinition = "NVARCHAR(MAX)") private String notifyRecipients;
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
}
