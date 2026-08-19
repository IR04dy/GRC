package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "scm", name = "slas")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Sla {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "metric_name_en", length = 255)
    private String metricNameEn;

    @Column(name = "metric_name_ar", length = 255)
    private String metricNameAr;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "measurement_frequency", length = 30)
    private String measurementFrequency;

    @Column(name = "current_value", precision = 10, scale = 2)
    private BigDecimal currentValue;

    // ⚠⚠ F2-241 — this was `= "ON_TRACK"`, a token `CK_scm_sla_status` REFUSES
    // (NOT_MEASURED | AT_RISK | BREACHED | MET). The column is NOT NULL with the DEFAULT
    // 'NOT_MEASURED', which is the right answer and says so: an SLA nobody has measured yet is
    // NOT_MEASURED, not on track. 🔑 ON_TRACK is also the FLATTERING value — it asserts the supplier
    // is meeting the target — so had anyone "fixed" this by widening the CHECK rather than correcting
    // the entity, a brand-new SLA would have claimed compliance it had never demonstrated. F2-238
    // measured that inversion from the other side.
    //
    // ⚠ Corrected rather than deleted, which is the opposite of F2-240's remedy for the adjacent
    // shape, and the discriminator is whether a create path exists to own the default. This register
    // has none at all — no controller, no route, no frontend client, and `SlaRepository` is injected
    // into ScmService and never used (F2-204's NO_SERVER_SURFACE shape, 14 live rows in dev and
    // prod). Deleting the initialiser would leave a NOT NULL column with no writer to fill it, which
    // is the one hazard F2-238 flagged for that fix shape.
    @Column(name = "status", length = 30)
    private String status = "NOT_MEASURED";

    @Column(name = "last_measured_at")
    private LocalDateTime lastMeasuredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
