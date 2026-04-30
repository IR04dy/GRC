package com.tss.grc.module.platform.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.util.UUID;

@Entity
@Table(schema = "platform", name = "organisations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Organisation extends BaseEntity {
    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    @Column(name = "name_ar", length = 300)
    private String nameAr;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(name = "industry_sector", length = 100)
    private String industrySector;

    @Column(name = "country_code", length = 3)
    private String countryCode = "SAU";

    @Column(name = "subscription_tier", length = 20)
    private String subscriptionTier = "STARTER";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor = "#1a2744";
}
