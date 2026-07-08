package com.tss.grc.module.platform.branding.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.branding.dto.BrandThemeRequest;
import com.tss.grc.module.platform.branding.dto.BrandThemeResponse;
import com.tss.grc.module.platform.branding.entity.BrandTheme;
import com.tss.grc.module.platform.branding.repository.BrandThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.util.Optional;

/**
 * Resolves the per-tenant brand theme and fills platform defaults for any unset field.
 * Consumed by the export engines (PDF cover colour + XLSX header fill) so a tenant's
 * saved branding actually reaches the generated files.
 *
 * <p>Colour defaults deliberately mirror {@code PdfFonts.BRAND_PRIMARY/BRAND_ACCENT}
 * and the text defaults mirror the client {@code whiteLabel.ts} defaults, so an
 * un-customised tenant renders exactly as before this feature landed.
 */
@Service
@RequiredArgsConstructor
public class BrandThemeService {

    /** #094242 — matches {@code PdfFonts.BRAND_PRIMARY}. */
    public static final String DEFAULT_PRIMARY_HEX = "#094242";
    /** #C9A977 — matches {@code PdfFonts.BRAND_ACCENT}. */
    public static final String DEFAULT_ACCENT_HEX  = "#C9A977";
    public static final String DEFAULT_ORG_EN      = "Trusted Security Solutions";
    public static final String DEFAULT_ORG_AR      = "الخدمات الموثوقة للأمن السيبراني";
    public static final String DEFAULT_TAGLINE_EN  = "Saudi-first GRC platform";
    public static final String DEFAULT_TAGLINE_AR  = "منصة الحوكمة والمخاطر والامتثال — صناعة سعودية";

    private final BrandThemeRepository repo;

    // ── reads ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Optional<BrandTheme> getForCurrentOrg() {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) return Optional.empty();
        return repo.findByOrganisationId(org);
    }

    /**
     * Brand values for the export engines. Colours are always populated (platform
     * default when unset); text/logo overrides are left null so the caller can fall
     * back to its own source (e.g. the Organisation entity's name).
     */
    @Transactional(readOnly = true)
    public ResolvedBrand resolve() {
        BrandTheme t = getForCurrentOrg().orElse(null);
        return new ResolvedBrand(
            t == null ? null : t.getOrgNameEn(),
            t == null ? null : t.getOrgNameAr(),
            t == null ? null : t.getTaglineEn(),
            t == null ? null : t.getTaglineAr(),
            defaultIfBlank(t == null ? null : t.getPrimaryHex(), DEFAULT_PRIMARY_HEX),
            defaultIfBlank(t == null ? null : t.getAccentHex(),  DEFAULT_ACCENT_HEX),
            t == null ? null : t.getLogoDataUrl()
        );
    }

    /** Fully-defaulted view for the settings UI (every field shows its effective value). */
    @Transactional(readOnly = true)
    public BrandThemeResponse resolveResponse() {
        BrandTheme t = getForCurrentOrg().orElse(null);
        return new BrandThemeResponse(
            defaultIfBlank(t == null ? null : t.getOrgNameEn(), DEFAULT_ORG_EN),
            defaultIfBlank(t == null ? null : t.getOrgNameAr(), DEFAULT_ORG_AR),
            defaultIfBlank(t == null ? null : t.getTaglineEn(), DEFAULT_TAGLINE_EN),
            defaultIfBlank(t == null ? null : t.getTaglineAr(), DEFAULT_TAGLINE_AR),
            defaultIfBlank(t == null ? null : t.getPrimaryHex(), DEFAULT_PRIMARY_HEX),
            defaultIfBlank(t == null ? null : t.getAccentHex(),  DEFAULT_ACCENT_HEX),
            t == null ? null : t.getLogoDataUrl(),
            t != null
        );
    }

    // ── write ────────────────────────────────────────────────────────────

    @Transactional
    public BrandThemeResponse upsert(BrandThemeRequest req) {
        Long org = TenantContext.requireOrganisationId();
        BrandTheme t = repo.findByOrganisationId(org).orElseGet(() -> {
            BrandTheme n = new BrandTheme();
            n.setOrganisationId(org);
            return n;
        });
        t.setOrgNameEn(blankToNull(req.orgNameEn()));
        t.setOrgNameAr(blankToNull(req.orgNameAr()));
        t.setTaglineEn(blankToNull(req.taglineEn()));
        t.setTaglineAr(blankToNull(req.taglineAr()));
        t.setPrimaryHex(blankToNull(req.primaryHex()));
        t.setAccentHex(blankToNull(req.accentHex()));
        t.setLogoDataUrl(blankToNull(req.logoDataUrl()));
        repo.save(t);
        return resolveResponse();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** Parse {@code #RRGGBB} (with or without the hash) into an AWT colour; returns
     *  {@code fallback} for null/blank/malformed input. Never throws. */
    public static Color parseHex(String hex, Color fallback) {
        if (hex == null) return fallback;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() != 6) return fallback;
        try {
            return new Color(
                Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String defaultIfBlank(String s, String dflt) {
        return (s == null || s.isBlank()) ? dflt : s.trim();
    }

    /**
     * Brand values for export engines. Colours are always present; call
     * {@link #primaryColor()} / {@link #accentColor()} for AWT colours.
     */
    public record ResolvedBrand(
        String orgNameEn, String orgNameAr,
        String taglineEn, String taglineAr,
        String primaryHex, String accentHex,
        String logoDataUrl
    ) {
        public Color primaryColor() { return parseHex(primaryHex, Color.BLACK); }
        public Color accentColor()  { return parseHex(accentHex, Color.BLACK); }
    }
}
