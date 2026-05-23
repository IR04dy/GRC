package com.tss.grc.module.integrations.servicenow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Generic mapping row between a GRC record and its representation in an
 * external system. Backs the V163 {@code platform.integration_external_link}
 * table.
 *
 * <p>Soft-deleted via {@code is_deleted} (managed manually — this entity
 * doesn't extend {@code BaseEntity} because the table is a thin lookup
 * row and doesn't need audit columns).
 */
@Entity
@Table(name = "integration_external_link", schema = "platform")
@SQLRestriction("is_deleted = 0")
public class IntegrationExternalLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "integration", nullable = false, length = 40)
    private String integration;

    @Column(name = "grc_module", nullable = false, length = 40)
    private String grcModule;

    @Column(name = "grc_record_id", nullable = false, length = 120)
    private String grcRecordId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(name = "external_number", length = 80)
    private String externalNumber;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    @Column(name = "last_pushed_at")
    private LocalDateTime lastPushedAt;

    @Column(name = "last_pulled_at")
    private LocalDateTime lastPulledAt;

    @Column(name = "last_known_state", length = 60)
    private String lastKnownState;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── getters/setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganisationId() { return organisationId; }
    public void setOrganisationId(Long organisationId) { this.organisationId = organisationId; }
    public String getIntegration() { return integration; }
    public void setIntegration(String integration) { this.integration = integration; }
    public String getGrcModule() { return grcModule; }
    public void setGrcModule(String grcModule) { this.grcModule = grcModule; }
    public String getGrcRecordId() { return grcRecordId; }
    public void setGrcRecordId(String grcRecordId) { this.grcRecordId = grcRecordId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getExternalNumber() { return externalNumber; }
    public void setExternalNumber(String externalNumber) { this.externalNumber = externalNumber; }
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
    public LocalDateTime getLastPushedAt() { return lastPushedAt; }
    public void setLastPushedAt(LocalDateTime lastPushedAt) { this.lastPushedAt = lastPushedAt; }
    public LocalDateTime getLastPulledAt() { return lastPulledAt; }
    public void setLastPulledAt(LocalDateTime lastPulledAt) { this.lastPulledAt = lastPulledAt; }
    public String getLastKnownState() { return lastKnownState; }
    public void setLastKnownState(String lastKnownState) { this.lastKnownState = lastKnownState; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean deleted) { isDeleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
