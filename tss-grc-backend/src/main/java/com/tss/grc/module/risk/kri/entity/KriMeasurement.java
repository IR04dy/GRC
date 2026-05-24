package com.tss.grc.module.risk.kri.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One observation of a {@link KriDefinition} — Sprint 13.
 *
 * <p>Measurements are append-only. The {@link com.tss.grc.common.model.BaseEntity}
 * {@code is_deleted} column is kept for the PDPL retention sweep but
 * no service path mutates a row after insert.
 *
 * <p>{@link #status} is computed at insert time by
 * {@link com.tss.grc.module.risk.kri.service.KriMeasurementService#evaluateStatus}
 * — persisting it (rather than recomputing on every read) means the
 * audit log can prove "what colour did this KRI show on day X" even
 * if the underlying thresholds are later edited.
 */
@Entity
@Table(schema = "risk", name = "kri_measurement")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class KriMeasurement extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "kri_id", nullable = false)
    private Long kriId;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt = LocalDateTime.now();

    @Column(name = "value", nullable = false, precision = 18, scale = 6)
    private BigDecimal value;

    /** GREEN | AMBER | RED | NA. */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "GREEN";

    /** AUTO_SQL | AUTO_CALLBACK | MANUAL | IMPORTED. */
    @Column(name = "source", nullable = false, length = 40)
    private String source = "MANUAL";

    @Column(name = "measured_by_user_id")
    private Long measuredByUserId;

    @Column(name = "notes_en", length = 1000)
    private String notesEn;

    @Column(name = "notes_ar", length = 1000)
    private String notesAr;
}
