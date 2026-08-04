package com.tss.grc.module.change_mgmt.entity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * S8e / CE-2 — the change model lifecycle vocabulary, kept equal to {@code ck_cmodel_status}.
 *
 * <p>{@code ChangeModelCatalogueTest} reads this class <b>and</b> V354's CHECK definition and fails
 * if they drift — the F2-91 pattern, after that finding measured what happens when a Java constant
 * and a database constraint hold different versions of the same closed set.
 *
 * <p>Four states, and the two that are not {@code DRAFT}/{@code APPROVED} exist because a
 * pre-approval has to be withdrawable without being erased:
 * <ul>
 *   <li>{@code SUSPENDED} — temporarily not conferring pre-approval (an incident, a failed audit)
 *       while the procedure and its history stay readable;</li>
 *   <li>{@code RETIRED} — permanently superseded. Not a soft delete: changes that cited it keep
 *       pointing at a row an auditor can still read, which is the whole reason
 *       {@code change_model_usage} references the model rather than copying its text.</li>
 * </ul>
 */
public final class ChangeModelStatus {

    private ChangeModelStatus() { }

    /** Authored, not yet an authority. May not be applied to a change. */
    public static final String DRAFT = "DRAFT";

    /** Approved by a {@code CHANGE:APPROVE} holder who is not the author. */
    public static final String APPROVED = "APPROVED";

    /** Withdrawn for now; history intact. */
    public static final String SUSPENDED = "SUSPENDED";

    /** Superseded for good. Existing usages still resolve. */
    public static final String RETIRED = "RETIRED";

    public static final Set<String> ALL =
        new LinkedHashSet<>(List.of(DRAFT, APPROVED, SUSPENDED, RETIRED));

    /** The only status from which a model may be applied to a change. */
    public static boolean isApplicable(String status) {
        return APPROVED.equals(status);
    }
}
