package com.tss.grc.module.cyber.service;

import com.tss.grc.module.cyber.entity.CyberFramework;
import com.tss.grc.module.cyber.repository.CyberFrameworkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * F2-252 — the one place that answers <i>"which framework is this, in MY tenant?"</i> for a
 * caller-supplied {@code framework_id}.
 *
 * <h2>Why this exists</h2>
 * {@code cyber.cyber_controls} declares {@code framework_id} <b>beside</b> {@code framework_name} —
 * a soft foreign key and a copy of the parent's identity. Both halves were caller-supplied and
 * neither was resolved, so three things were true at once, all probed live 2026-08-20:
 *
 * <ul>
 *   <li>a <b>nonexistent</b> framework id was stored (the column carries no FOREIGN KEY — V342 added
 *       one for the five tables pointing at {@code compliance.frameworks} and this column points at
 *       a different register, so it got none);</li>
 *   <li><b>another tenant's</b> framework was stored — {@code CyberFramework.organisation_id} is
 *       NOT NULL, and {@code CyberFrameworkRepository.findByIdAndOrganisationId} has existed since
 *       SEC-CYB-001 and was simply never called from the control path; and</li>
 *   <li>a <b>label contradicting the id</b> was stored: {@code framework_id = 1} ("NCA ECC") beside
 *       {@code framework_name = 'ISO 27001'}. The register renders the label and a join uses the id,
 *       so the two surfaces disagreed with nothing comparing them (F2-245).</li>
 * </ul>
 *
 * <h2>⚠ The trap that makes this family worse here than elsewhere</h2>
 * There are <b>two disjoint framework registers with overlapping id spaces</b> — this one
 * ({@code cyber.cyber_frameworks}, 6 rows, tenant-owned) and {@code compliance.frameworks}
 * (24 rows, global, W2-1a-i's regulator catalogue). Ids 1–6 exist in both and name <b>different
 * frameworks</b>: id 2 is "ISO 27001" here and "NCA Cloud Cybersecurity Controls" there. So a
 * reference resolved against the wrong register <b>dereferences cleanly and lies</b> — it does not
 * fail, it produces a plausible name. Measuring this pair against {@code compliance.frameworks}
 * reported 75 of 75 rows divergent; measured against this register the same 75 rows <b>agree</b>.
 * That is F2-246's "the pairing is the measurement" with a mechanism: when two registers share an
 * id space, a wrong pairing cannot fail loudly.
 *
 * <p><b>Which register is authoritative for a cyber control is an owner decision</b> and is filed,
 * not decided here. This class resolves against the one the 75 live rows actually use.
 *
 * @see CyberControlService#applyFrameworkReference
 */
@Component
@RequiredArgsConstructor
public class CyberFrameworkReferenceResolver {

    private final CyberFrameworkRepository frameworkRepo;

    /**
     * The framework that id names <b>within this tenant</b>, or empty.
     *
     * <p>⚠ The tenant argument is the caller's, never a field of a half-built row: F2-246 shipped a
     * resolver that read {@code entity.getOrganisationId()} before it had been stamped, so the
     * filter could never match and every LINKED create answered 404 — and the two probe legs that
     * expect a refusal passed over it, because a refusal caused by a null tenant is
     * indistinguishable from a correct one.
     */
    public Optional<CyberFramework> resolve(Long frameworkId, Long orgId) {
        if (frameworkId == null || orgId == null) return Optional.empty();
        return frameworkRepo.findByIdAndOrganisationId(frameworkId, orgId);
    }

    /**
     * The framework that id names in this tenant, or a 400 naming the field.
     *
     * <p>One message for "no such framework" and "another tenant's framework", copied from
     * {@code CommitteeMemberRegistryService#requireUserInTenant}, which records why: distinguishing
     * them lets a caller enumerate another tenant's ids from the error text.
     */
    public CyberFramework require(Long frameworkId, Long orgId) {
        return resolve(frameworkId, orgId).orElseThrow(() -> new IllegalArgumentException(
            "frameworkId " + frameworkId + " does not name a framework in this organisation"));
    }

    /**
     * The derived labels for a resolvable framework, in <b>both languages</b>, or a pair of nulls.
     * Never the caller's strings.
     *
     * <p>⚠ F2-277 — this replaced a {@code labelFor} that returned the English name alone. A
     * single method rather than two is not tidiness: {@code labelFor}/{@code labelArFor} would
     * each have resolved the framework independently, so the two halves of one label could in
     * principle come from two reads. Returning them together makes them <b>one derivation from one
     * resolved row</b>, which is F2-245's rule that the claim preventing drift is about where the
     * writes live.
     *
     * <p>{@code nameAr} is nullable in the parent (2 of 6 rows), so a null Arabic half here is an
     * honest "the framework has no Arabic name" and never a failure to derive.
     */
    public Labels labelsFor(Long frameworkId, Long orgId) {
        return resolve(frameworkId, orgId)
                .map(f -> new Labels(f.getName(), f.getNameAr()))
                .orElse(Labels.NONE);
    }

    /** Both halves of a framework's derived label, always read from one resolved row. */
    public record Labels(String en, String ar) {
        public static final Labels NONE = new Labels(null, null);
    }
}
