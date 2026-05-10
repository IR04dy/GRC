package com.tss.grc.module.platform.sso.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Where;

/**
 * Nafath transaction — Sprint 6 production hardening.
 *
 * One row per Nafath login attempt. The state machine is:
 *   PENDING → APPROVED / REJECTED / EXPIRED
 *
 * Production posts to Nafath's API at initiation, then receives a callback
 * (or polls) for the user's decision. The random_number is the 2-digit
 * value the user picks in the Nafath app to confirm the request.
 */
@Entity
@Table(name = "nafath_transaction",
       indexes = {
           @Index(name = "idx_nafath_org", columnList = "organisation_id"),
           @Index(name = "idx_nafath_started", columnList = "started_at"),
       })
@Where(clause = "is_deleted = 0")
public class NafathTransaction extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "transaction_id", nullable = false, length = 200, unique = true)
    private String transactionId;

    /** Hashed national-ID. Plain ID never persisted. */
    @Column(name = "national_id_hash", nullable = false, length = 64)
    private String nationalIdHash;

    @Column(name = "random_number", nullable = false)
    private Integer randomNumber;

    /** PENDING / APPROVED / REJECTED / EXPIRED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private String startedAt;

    @Column(name = "decided_at")
    private String decidedAt;

    @Column(name = "expires_in_seconds", nullable = false)
    private Integer expiresInSeconds;

    /** Reference to the source IP / user-agent for audit. */
    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public NafathTransaction() {}

    public Long getOrganisationId() { return organisationId; }
    public void setOrganisationId(Long organisationId) { this.organisationId = organisationId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getNationalIdHash() { return nationalIdHash; }
    public void setNationalIdHash(String nationalIdHash) { this.nationalIdHash = nationalIdHash; }
    public Integer getRandomNumber() { return randomNumber; }
    public void setRandomNumber(Integer randomNumber) { this.randomNumber = randomNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getDecidedAt() { return decidedAt; }
    public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
