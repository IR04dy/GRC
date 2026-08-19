package com.tss.grc.module.tprm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The single writer of a {@code (vendor id, vendor label)} pair anywhere in {@code module/tprm}.
 *
 * <h2>Why this exists (F2-247)</h2>
 *
 * <p>{@code tprm.third_party_cyber_assessments} stores {@code vendor_id} (which vendor) beside
 * {@code vendor_name} (what it is called), and {@code ThirdPartyCyberAssessmentService.create}
 * bound the ENTITY and took both halves straight from the caller's body — no resolution, no
 * tenant check. Measured identical in dev and prod: <b>8 of 16 live assessments carry a vendor
 * label and no link</b> ({@code TPC-2026-001..008}), and the labels are annotated prose
 * ({@code 'New vendor — Sentry IO'}, {@code 'Legacy vendor — Backup Tape Logistics LLC'}) —
 * which is <i>why</i> they can never be joined to the register.
 *
 * <p>The wire contract declared the defect: {@code vendorId?: number} (optional) beside
 * {@code vendorName: string} (required), and {@code ThirdPartyCyberPage}'s form offered a
 * {@code required: true} free-text vendor box with no picker and posted no id at all. So the rows
 * the platform's own screen produces are exactly the unlinked ones; the 8 that ARE linked came
 * from the seed. <b>A form that collects a label instead of a link produces a row nothing can
 * join</b> (F2-246, on the four {@code module/scm} registers that share this shape).
 *
 * <p>Probed live as {@code compliance} (TPRM:WRITE, no APPROVE) against a planted org-2 victim,
 * all four halves confirmed: a label-only create answered 201, another tenant's vendor id
 * answered 201 and was stored, {@code vendorId: 999999} answered 201 (the column carries no
 * FOREIGN KEY, so the database refuses nothing either), and a real id beside a <i>lying</i> label
 * stored the lie — F2-245's divergence, producible on demand.
 *
 * <h2>Where this module already knew</h2>
 *
 * <p>{@link TprmService#createAssessment} resolves its vendor with {@code getVendor(req.getVendorId())}
 * under the comment <i>"verify org ownership"</i>. That is the correct artefact, one service over
 * in the same module, and {@code tprm/cyber} imported nothing from {@code tprm/*} — it had no
 * {@code VendorRepository} at all, so it could not have resolved a vendor if it had wanted to.
 * F2-215's two-of-N: a reader who finds the mechanism present in the module has no reason to ask
 * which of its registers actually use it. {@code getVendor} now delegates here, so there is one
 * predicate rather than two copies nobody was comparing (F2-177).
 *
 * <h2>Why the unlinked case is kept</h2>
 *
 * <p>Nothing refuses a label-only create today and 8 live rows were made that way, so
 * {@code require} would break working requests (F2-182's mechanical test). A third-party cyber
 * assessment of a supplier that is not in the vendor register is a real thing to want. The row is
 * therefore <b>disclosed as unlinked</b> rather than refused (F2-209's three states), and the
 * label stays editable exactly where there is no id to derive it from.
 *
 * <h2>Why the {@code Tprm} prefix is load-bearing</h2>
 *
 * <p>⚠⚠ This class was called {@code VendorReferenceResolver}, mirroring F2-246's {@code module/scm}
 * one — and <b>the application refused to start</b>: two {@code @Component} classes with the same
 * simple name get the same default bean name, and Spring throws
 * {@code ConflictingBeanDefinitionException} at context startup. <b>All 4,229 unit tests passed over
 * it</b>, because none of them boots a Spring context, and {@code mvn -o clean package} is
 * deliberately database-free so it never starts the app either. Only launching it found this.
 *
 * <p>F2-222 filed the duplicate-simple-name hazard for <i>entities</i> and measured it as latent —
 * 15 of 433 names declared in two packages, <i>"and nobody is maintaining that"</i>. For a Spring
 * bean it is not latent: it is a hard startup failure. The prefix is also the honest name, for the
 * same reason {@code loadTprmVendorOptions} carries one on the frontend: there are two vendor
 * registers for one concept, and reaching for the wrong resolver is the easy mistake.
 * {@code UniqueBeanNameContractTest} now makes the collision a build failure rather than a launch
 * failure.
 */
@Component
@RequiredArgsConstructor
public class TprmVendorReferenceResolver {

    private final VendorRepository vendors;

    /** A resolved reference: an id that names a row of this tenant's register, or no id at all. */
    public record VendorReference(Long id, String name) {}

    /**
     * Resolve the pair a create was given.
     *
     * <p>⚠ Pass the tenant from the REQUEST, never from the half-built entity. F2-246's fix was
     * wrong in one of its four registers for exactly this reason: the resolver ran eighteen lines
     * above {@code setOrganisationId}, so the tenant argument was null, the filter could never
     * match, and every LINKED create answered 404. Both probe legs that expect a refusal passed on
     * that broken code — <b>a refusal caused by a null tenant is indistinguishable from a correct
     * one</b> — and only the leg asserting the derived label saw it.
     *
     * @param vendorId     the caller's claimed link, or {@code null} for an unregistered supplier
     * @param suppliedName the caller's label, used only when there is no id to derive one from
     * @param orgId        the caller's tenant, from the request
     */
    public VendorReference resolve(Long vendorId, String suppliedName, Long orgId) {
        if (vendorId == null) {
            String name = suppliedName == null ? "" : suppliedName.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "A vendor must be identified: supply vendorId to link this assessment to "
                      + "the vendor register, or vendorName if the supplier is not registered. "
                      + "An assessment with neither names nobody.");
            }
            return new VendorReference(null, name);
        }
        Vendor v = requireOwn(vendorId, orgId);
        // The label is DERIVED, never echoed. A caller-supplied label beside a resolved id is the
        // half a human reads while the id is the half a coverage query joins on, and nothing was
        // comparing them (F2-245).
        return new VendorReference(v.getId(), v.getVendorName());
    }

    /**
     * Apply a label an UPDATE supplied: accept an echo, refuse a change.
     *
     * <p>Where the row is linked the label is not the caller's to set — but a form that round-trips
     * the whole row must keep working, so an identical value is accepted and a different one is
     * refused (F2-231/F2-234). An update cannot re-open what the create closed.
     */
    public String relabel(Long storedVendorId, String storedName, String supplied) {
        String name = supplied == null ? "" : supplied.trim();
        if (storedVendorId == null) {
            // No link, so the label is the only identification this row has: editable, and it may
            // not be blanked away.
            return name.isEmpty() ? storedName : name;
        }
        if (name.isEmpty() || name.equals(storedName)) {
            return storedName;
        }
        throw new IllegalArgumentException(
                "This assessment is linked to vendor " + storedVendorId + ", so its vendor name is "
              + "derived from the vendor register and cannot be edited here. Change the vendor's "
              + "name in the register, or re-point this assessment with a different vendorId.");
    }

    /**
     * The tenant-scoped vendor load.
     *
     * <p>One message for <i>"no such vendor"</i> and <i>"another tenant's vendor"</i>, deliberately:
     * distinguishing them lets a caller enumerate foreign vendor ids from the error text (F2-244,
     * copied from {@code CommitteeMemberRegistryService#requireUserInTenant}, which records why).
     * {@link Vendor} carries {@code @Where(clause = "is_deleted = 0")}, so a soft-deleted vendor is
     * already invisible here.
     */
    public Vendor requireOwn(Long vendorId, Long orgId) {
        return vendors.findById(vendorId)
                .filter(v -> orgId != null && orgId.equals(v.getOrganisationId()))
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + vendorId));
    }

    /** Convenience for callers already inside a request, where the tenant is the ambient one. */
    public Vendor requireOwn(Long vendorId) {
        return requireOwn(vendorId, TenantContext.getCurrentTenant());
    }
}
