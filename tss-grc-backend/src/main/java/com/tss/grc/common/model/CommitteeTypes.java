package com.tss.grc.common.model;

import java.util.List;
import java.util.Set;

/**
 * The one vocabulary of {@code admin.committees.committee_type}, so the several places that need it
 * cannot disagree about it.
 *
 * <h3>Why this class exists</h3>
 * Three separate statements of this vocabulary had already drifted apart before it did:
 * <ul>
 *   <li>{@code CK_com_type} in the database — <b>22</b> values ({@code V40}, extended by {@code V154}).</li>
 *   <li>{@code BoardPackDto.audience}'s {@code @Pattern} — <b>17</b>, missing {@code OTHER},
 *       {@code ARCH_REVIEW}, {@code INVESTMENT}, {@code ALCO}, {@code AML}. All five have existed
 *       since {@code V154}, 177 migrations before {@code V331} widened that pattern "to the real
 *       vocabulary" — so the widening was five short on the day it shipped, and the message it
 *       refuses with ("must be an admin.committees.committee_type value") named exactly what the
 *       value <em>was</em>. A tenant with an ALCO or an Investment committee could not scope a board
 *       pack to it.</li>
 *   <li>{@code CommitteeCreateRequest.committeeType} — <b>no vocabulary at all</b>, only
 *       {@code @NotBlank @Size(max = 30)}. The database CHECK was the sole enforcement, so an
 *       off-vocabulary type arrived as a {@code ConstraintViolationException} and surfaced as
 *       <b>500 "An unexpected database error occurred."</b> The field that <em>sets</em> the
 *       vocabulary was unvalidated while the field that merely <em>references</em> it was validated
 *       and ratcheted.</li>
 * </ul>
 *
 * <p>D-11 recorded that the drift "cannot recur unnoticed" because {@code V331} throws if a stored
 * audience fails to resolve. That guard is real and it points the wrong way: it checks rows against
 * the vocabulary, never the vocabulary's several definitions against each other. Which is why the
 * ratchets that come with this class check both directions — {@code CommitteeTypeVocabularyTest}
 * ties every Java statement of it together, and {@code CommitteeGoldenPathIT} ties the Java
 * vocabulary to {@code CK_com_type} itself, against a real database. No unit test can do the second.
 *
 * <h3>Why {@code CAB} and {@code ECAB} are deliberately absent</h3>
 * <b>ADR-017 D-2:</b> "ITIL 4 supports this: the CAB is <i>a role</i>, not necessarily a standing
 * committee, so the platform must not assume a committee exists." A tenant nominates one of its real
 * committees — IT Steering, Executive, Architecture Review — as the CAB through
 * {@code admin.committee_gate_map}. Adding {@code CAB} here would make CAB-ness a property of a
 * committee rather than a charter fact the tenant states, quietly reversing the decision that
 * unblocked the whole change-governance epic.
 */
public final class CommitteeTypes {

    private CommitteeTypes() {}

    /**
     * Every value {@code CK_com_type} permits, in the constraint's own order.
     *
     * <p><b>Adding one is a migration, not an edit here.</b> Widening this list without widening
     * {@code CK_com_type} turns a 400 into a 500 — the exact defect this class was extracted to fix.
     * {@code CommitteeGoldenPathIT} will fail the moment the two disagree in either direction.
     */
    public static final List<String> VOCABULARY = List.of(
        "BOARD",
        "EXEC_COMMITTEE",
        "RISK_COMMITTEE",
        "AUDIT_COMMITTEE",
        "BCM_COMMITTEE",
        "COMPLIANCE_COMMITTEE",
        "TECH_COMMITTEE",
        "NOMINATION_REMUNERATION",
        "CYBER_SECURITY",
        "DATA_GOVERNANCE",
        "PRIVACY_DPO",
        "CRISIS_MANAGEMENT",
        "AML",
        "IT_STEERING",
        "ALCO",
        "INVESTMENT",
        "AI_ETHICS",
        "ANTI_FRAUD",
        "INTERNAL_CONTROL",
        "ESG_SUSTAINABILITY",
        "ARCH_REVIEW",
        "OTHER");

    public static final Set<String> VALUES = Set.copyOf(VOCABULARY);

    /**
     * The same vocabulary as a bean-validation regexp. {@code @Pattern} needs a compile-time
     * constant, so this cannot be derived from {@link #VOCABULARY} at runtime — which is precisely
     * how the two drifted before. {@code CommitteeTypeVocabularyTest} asserts they still agree.
     */
    public static final String REGEX =
        "BOARD|EXEC_COMMITTEE|RISK_COMMITTEE|AUDIT_COMMITTEE|BCM_COMMITTEE|COMPLIANCE_COMMITTEE"
        + "|TECH_COMMITTEE|NOMINATION_REMUNERATION|CYBER_SECURITY|DATA_GOVERNANCE|PRIVACY_DPO"
        + "|CRISIS_MANAGEMENT|AML|IT_STEERING|ALCO|INVESTMENT|AI_ETHICS|ANTI_FRAUD"
        + "|INTERNAL_CONTROL|ESG_SUSTAINABILITY|ARCH_REVIEW|OTHER";

    /** Message shared by every field validated against this vocabulary. */
    public static final String MESSAGE =
        "must be one of the platform's committee types (admin.committees.committee_type)";

    public static boolean isValid(String candidate) {
        return candidate != null && VALUES.contains(candidate.trim());
    }
}
