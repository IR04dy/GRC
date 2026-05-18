package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KSA peak-season window — audit Tier-4 #28.
 *
 * <p>System-defined calendar of Saudi-native demand peaks (Hajj, Umrah peak,
 * Ramadan, Eid Al-Fitr, Eid Al-Adha, National Day, Founding Day, Riyadh
 * Season, MoH capacity, Saudi quarter-end, back-to-school). A BIA's
 * {@code peakPeriodsText} field plus the season's {@code seasonType} drive
 * a "Plans relevant to this peak" lens on the dashboard.
 */
@Entity
@Table(schema = "bcm", name = "peak_seasons")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmPeakSeason {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_code", nullable = false, length = 50)
    private String seasonCode;

    @Column(name = "season_name_en", nullable = false, length = 255)
    private String seasonNameEn;

    @Column(name = "season_name_ar", nullable = false, length = 255)
    private String seasonNameAr;

    /** HAJJ | UMRAH_PEAK | RAMADAN | EID_AL_FITR | EID_AL_ADHA | NATIONAL_DAY | FOUNDING_DAY | RIYADH_SEASON | MOH_CAPACITY | BACK_TO_SCHOOL | SAUDI_QUARTER_END */
    @Column(name = "season_type", nullable = false, length = 40)
    private String seasonType;

    @Column(name = "gregorian_start_date", nullable = false)
    private LocalDate gregorianStartDate;

    @Column(name = "gregorian_end_date", nullable = false)
    private LocalDate gregorianEndDate;

    @Column(name = "hijri_start_year")
    private Integer hijriStartYear;

    @Column(name = "hijri_start_month")
    private Integer hijriStartMonth;

    @Column(name = "risk_dimensions_json", columnDefinition = "NVARCHAR(MAX)")
    private String riskDimensionsJson;

    @Column(name = "affected_services", length = 1000)
    private String affectedServices;

    @Column(name = "recommended_actions", columnDefinition = "NVARCHAR(MAX)")
    private String recommendedActions;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;
    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
