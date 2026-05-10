package com.tss.grc.module.workflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(schema = "workflow", name = "states")
@Getter @Setter @NoArgsConstructor
public class WorkflowState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 150)
    private String nameEn;

    @Column(name = "name_ar", length = 150)
    private String nameAr;

    @Column(name = "is_initial", nullable = false)
    private Boolean isInitial = false;

    @Column(name = "is_terminal", nullable = false)
    private Boolean isTerminal = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Free-form colour token — 'success' / 'danger' / 'primary' / hex etc. */
    @Column(name = "ui_color", length = 20)
    private String uiColor;

    @Column(name = "description_en", length = 500)
    private String descriptionEn;
}
