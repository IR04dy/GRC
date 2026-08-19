package com.tss.grc.module.scm.service;

import com.tss.grc.module.scm.entity.Vendor;
import com.tss.grc.module.scm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one place in {@code module/scm} that answers <i>"is this my vendor, and what is it
 * called?"</i>.
 *
 * <h2>Why this exists (F2-246)</h2>
 *
 * Four SCM registers store a vendor <b>id</b> beside a copy of that vendor's <b>name</b>:
 * {@code scm.exit_plans}, {@code scm.alternate_suppliers},
 * {@code scm.supplier_resilience_tests} and {@code scm.fourth_party_exposures}. Each create
 * took <em>both</em> from the request, so the two halves of one reference had two different
 * authors and nothing compared them — F2-245's shape, replicated four times.
 *
 * <p>Two facts made it live rather than latent, measured identical in dev and prod:
 *
 * <ul>
 *   <li><b>32 of 53 live rows carry a vendor label and no id at all.</b> Every one of the
 *       four create forms offered a <em>required free-text</em> vendor-name box and no
 *       picker, and none of them sent the id — so the rows the platform's own screens
 *       produce are exactly the unlinked ones, and the 21 that are linked came from the
 *       seed. {@code ExitPlanRepository.findByOrganisationIdAndVendorIdOrderByCreatedAtDesc}
 *       and {@code AlternateSupplierRepository.findByOrganisationIdAndPrimaryVendorId…}
 *       are coverage queries over that id, so <em>"which vendors have an exit plan?"</em>
 *       — a SAMA outsourcing question — could never see 7 of 13 real plans.</li>
 *   <li><b>The id was never verified.</b> None of the four tables carries a FOREIGN KEY on
 *       its vendor column, so a caller could name another tenant's vendor or a vendor that
 *       does not exist. Probed live as {@code ranalyst}: both stored at HTTP 201.</li>
 * </ul>
 *
 * <h2>Which half is wrong, and how the schema says so</h2>
 *
 * {@code AlternateSupplier} declares {@code primary_vendor_id} <em>and</em>
 * {@code primary_vendor_name} for the vendor being replaced, and for the replacement only
 * {@code alternate_vendor_name} — <b>there is no {@code alternate_vendor_id} column at
 * all.</b> {@code FourthPartyExposure} is the same shape ({@code fourth_party_name}, no
 * id). So the schema itself distinguishes the two: a downstream or substitute party is
 * deliberately free text because it need not be a registered vendor, while the
 * <em>subject</em> vendor has an id column and a coverage query and is meant to resolve.
 * <b>When a table declares an id beside a label for one party and only a label for
 * another, the schema is stating which reference is supposed to be resolvable.</b>
 *
 * <h2>The rule</h2>
 *
 * <ul>
 *   <li>An id was supplied → resolve it <b>inside the caller's tenant</b> and <b>derive the
 *       label from the row that resolved</b>. Anything the caller said the vendor was
 *       called is discarded: only the vendor register knows its own name.</li>
 *   <li>No id → keep the caller's label, because it is then the only thing identifying the
 *       supplier. This is deliberately still accepted: nothing refuses it today and 32 live
 *       rows were created that way, so refusing would break requests that work (F2-182).
 *       The register discloses such a row as <em>unlinked</em> rather than letting it read
 *       like a resolved one.</li>
 * </ul>
 *
 * <p>⚠ {@link ScmService} was <b>already correct</b> — it has resolved vendors in-tenant
 * through {@code requireOwnVendor} and derived the label onto the <em>response</em> since it
 * was written, which is exactly this fix. That is what made the four siblings look covered
 * (F2-215): a reader who finds the right pattern in a module's main service does not then
 * ask whether the peripheral ones share it. {@code requireOwnVendor} now delegates here, so
 * there is one tenant check rather than two that agree today and nobody maintaining that
 * (F2-177).
 */
@Component
@RequiredArgsConstructor
public class VendorReferenceResolver {

    private final VendorRepository vendors;

    /**
     * A resolved vendor reference: the id to join on, and the label to display.
     *
     * <p>{@code id == null} means the caller named a supplier that is not in the vendor
     * register. That is a different fact from a resolved reference and the register says so
     * — F2-209's three states, where "not recorded" and "cannot be recorded here" must not
     * look alike.
     */
    public record VendorReference(Long id, String name) {}

    /**
     * Resolve the (id, label) pair for a register row.
     *
     * @param vendorId     the caller's claim about <em>which</em> vendor, or {@code null}
     * @param suppliedName the caller's claim about what it is <em>called</em> — honoured
     *                     only when {@code vendorId} is {@code null}
     * @param orgId        the tenant the row belongs to. Passed in rather than read from
     *                     {@code TenantContext} so the anchor is always the owning row's
     *                     tenant, which is what a caller-independent path (a sweep) needs
     *                     (F2-242).
     */
    public VendorReference resolve(Long vendorId, String suppliedName, Long orgId) {
        if (vendorId == null) {
            String name = suppliedName == null ? "" : suppliedName.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "A vendor must be identified: supply vendorId to link this record to "
                      + "the vendor register, or vendorName if the supplier is not registered. "
                      + "A row with neither names nobody.");
            }
            return new VendorReference(null, name);
        }
        Vendor v = requireOwn(vendorId, orgId);
        return new VendorReference(v.getId(), v.getNameEn());
    }

    /**
     * The label an <b>update</b> may store.
     *
     * <p>{@code FourthPartyExposureService#update} declares {@code primaryVendorName} and no
     * id, so before F2-246 a caller could rename the label on a row whose id points at a real
     * vendor — making a <em>linked</em> reference describe the wrong vendor after the fact.
     * That is the route by which F2-245's divergence becomes reachable without a create.
     *
     * <p>So: a linked row's label belongs to the vendor register and an edit may
     * <b>echo</b> it but not <b>change</b> it (F2-231/F2-234 — refusing a change while
     * accepting an echo is what keeps a form that round-trips the whole row working). An
     * unlinked row's label is the only thing identifying the supplier, so it stays editable.
     *
     * <p>⚠ The change is <em>refused</em> rather than silently dropped: a field the server
     * quietly ignores tells the caller their value mattered (F2-184).
     */
    public String relabel(Long storedVendorId, String storedName, String supplied) {
        String name = supplied == null ? "" : supplied.trim();
        if (storedVendorId == null) {
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "This record identifies its supplier only by name, so the name cannot "
                      + "be blanked. Link it to the vendor register instead.");
            }
            return name;
        }
        if (name.equals(storedName)) {
            return storedName;
        }
        throw new IllegalArgumentException(
                "This record is linked to vendor " + storedVendorId + ", so its vendor name is "
              + "derived from the vendor register and cannot be edited here. Rename the vendor, "
              + "or point this record at a different vendor.");
    }

    /**
     * The vendor, or a refusal. Used both here and by {@link ScmService}.
     *
     * <p>⚠ One message for <i>"no such vendor"</i> and <i>"another tenant's vendor"</i>, on
     * purpose: distinguishing them lets a caller enumerate foreign vendor ids from the error
     * text (F2-244). {@code Vendor} carries {@code @Where(is_deleted = 0)}, so a
     * soft-deleted vendor is already invisible to {@code findById}.
     */
    public Vendor requireOwn(Long vendorId, Long orgId) {
        return vendors.findById(vendorId)
                .filter(v -> orgId != null && orgId.equals(v.getOrganisationId()))
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + vendorId));
    }
}
