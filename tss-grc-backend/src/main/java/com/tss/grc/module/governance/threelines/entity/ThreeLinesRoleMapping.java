package com.tss.grc.module.governance.threelines.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

/**
 * Maps a system role to one of the four IIA 2020 Three Lines of Defense
 * bands ({@code LINE_1}, {@code LINE_2}, {@code LINE_3}, {@code BOARD}).
 *
 * <p>Why a mapping table (not a column on {@code iam.roles})?
 *
 * <ul>
 *   <li><b>Per-tenant tuning</b> — the same role can legitimately map to a
 *       different band in a different tenant. Per-org overrides set
 *       {@code organisationId}; system-wide defaults keep it {@code NULL}.</li>
 *   <li><b>Saudi regulatory expectation</b> (SAMA CSF 1.1.1 + NCA ECC-2 1-1)
 *       is to prove "clear allocation of risk and compliance responsibilities."
 *       This row is the queryable, auditable artefact of that allocation.</li>
 *   <li><b>Highest-band-wins resolution</b> — when a user holds multiple
 *       roles, {@link com.tss.grc.module.governance.threelines.service.ThreeLinesService}
 *       picks the highest band for assurance independence
 *       (LINE_3 &gt; LINE_2 &gt; LINE_1; BOARD treated as a separate
 *       oversight band).</li>
 * </ul>
 *
 * <p>Backed by table {@code platform.three_lines_role_mapping} (migration V166).
 */
@Entity
@Table(schema = "platform", name = "three_lines_role_mapping")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ThreeLinesRoleMapping extends BaseEntity {

    /** NULL when this is the system-wide default; set for per-tenant overrides. */
    @Column(name = "organisation_id")
    private Long organisationId;

    /** Matches {@code iam.roles.role_name}. */
    @Column(name = "role_name", nullable = false, length = 80)
    private String roleName;

    /** {@code LINE_1} | {@code LINE_2} | {@code LINE_3} | {@code BOARD}. */
    @Column(name = "lod_band", nullable = false, length = 20)
    private String lodBand;

    @Column(name = "title_en", nullable = false, length = 160)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 160)
    private String titleAr;

    @Column(name = "rationale_en", length = 500)
    private String rationaleEn;

    @Column(name = "rationale_ar", length = 500)
    private String rationaleAr;
}
