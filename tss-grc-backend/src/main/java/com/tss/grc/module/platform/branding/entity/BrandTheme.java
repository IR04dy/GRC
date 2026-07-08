package com.tss.grc.module.platform.branding.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * Export P2 — per-tenant brand theme, persisted server-side so exported PDFs and
 * workbooks can carry the tenant's identity (name, tagline, primary/accent colour,
 * logo). Until now branding lived only in the browser (localStorage {@code whiteLabel.ts})
 * and never reached the server-side export engines, so every tenant's PDF/XLSX used
 * the default TSS palette.
 *
 * <p>One row per organisation. Null fields fall back to the platform defaults at
 * resolve time (see {@code BrandThemeService}).
 */
@Entity
@Table(schema = "platform", name = "brand_theme")
@Where(clause = "is_deleted = 0")
@Getter
@Setter
@NoArgsConstructor
public class BrandTheme extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "org_name_en", length = 200)
    private String orgNameEn;

    @Column(name = "org_name_ar", length = 200)
    private String orgNameAr;

    @Column(name = "tagline_en", length = 300)
    private String taglineEn;

    @Column(name = "tagline_ar", length = 300)
    private String taglineAr;

    /** Primary brand colour as {@code #RRGGBB}. */
    @Column(name = "primary_hex", length = 7)
    private String primaryHex;

    /** Accent brand colour as {@code #RRGGBB}. */
    @Column(name = "accent_hex", length = 7)
    private String accentHex;

    /** Logo as a data URL (data:image/png;base64,...). NVARCHAR(MAX). */
    @Column(name = "logo_data_url", columnDefinition = "NVARCHAR(MAX)")
    private String logoDataUrl;
}
