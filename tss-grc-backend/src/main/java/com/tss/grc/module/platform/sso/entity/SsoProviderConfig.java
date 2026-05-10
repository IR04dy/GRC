package com.tss.grc.module.platform.sso.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Where;

/**
 * SSO provider configuration — Sprint 6 production hardening.
 *
 * One row per (tenant, provider-kind). Holds the metadata + secrets-ref
 * (the actual secret lives in AWS Secrets Manager / Spring Cloud Config;
 * this row holds only the reference). Production reads this on every
 * SAML / OIDC request and on the Nafath callback.
 */
@Entity
@Table(name = "sso_provider_config",
       uniqueConstraints = @UniqueConstraint(columnNames = {"organisation_id", "kind"}),
       indexes = @Index(name = "idx_sso_org", columnList = "organisation_id"))
@Where(clause = "is_deleted = 0")
public class SsoProviderConfig extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** NAFATH / SAML / OIDC. */
    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "configured", nullable = false)
    private Boolean configured;

    /** Display name for the login button. */
    @Column(name = "display_name_en", length = 200)
    private String displayNameEn;

    @Column(name = "display_name_ar", length = 200)
    private String displayNameAr;

    /** Nafath app id. Production keeps the secret in Secrets Manager. */
    @Column(name = "nafath_app_id", length = 100)
    private String nafathAppId;

    /** SAML IdP metadata URL. */
    @Column(name = "saml_metadata_url", length = 500)
    private String samlMetadataUrl;

    /** SAML IdP issuer / entity id. */
    @Column(name = "saml_issuer_entity_id", length = 500)
    private String samlIssuerEntityId;

    /** OIDC well-known URL. */
    @Column(name = "oidc_issuer_url", length = 500)
    private String oidcIssuerUrl;

    @Column(name = "last_login_at")
    private String lastLoginAt;

    public SsoProviderConfig() {}

    // getters/setters
    public Long getOrganisationId() { return organisationId; }
    public void setOrganisationId(Long organisationId) { this.organisationId = organisationId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Boolean getConfigured() { return configured; }
    public void setConfigured(Boolean configured) { this.configured = configured; }
    public String getDisplayNameEn() { return displayNameEn; }
    public void setDisplayNameEn(String displayNameEn) { this.displayNameEn = displayNameEn; }
    public String getDisplayNameAr() { return displayNameAr; }
    public void setDisplayNameAr(String displayNameAr) { this.displayNameAr = displayNameAr; }
    public String getNafathAppId() { return nafathAppId; }
    public void setNafathAppId(String nafathAppId) { this.nafathAppId = nafathAppId; }
    public String getSamlMetadataUrl() { return samlMetadataUrl; }
    public void setSamlMetadataUrl(String samlMetadataUrl) { this.samlMetadataUrl = samlMetadataUrl; }
    public String getSamlIssuerEntityId() { return samlIssuerEntityId; }
    public void setSamlIssuerEntityId(String samlIssuerEntityId) { this.samlIssuerEntityId = samlIssuerEntityId; }
    public String getOidcIssuerUrl() { return oidcIssuerUrl; }
    public void setOidcIssuerUrl(String oidcIssuerUrl) { this.oidcIssuerUrl = oidcIssuerUrl; }
    public String getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(String lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
