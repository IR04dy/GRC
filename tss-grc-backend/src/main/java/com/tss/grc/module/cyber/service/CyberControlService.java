package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cyber controls register.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberControlService {

    private final CyberControlRepository controlRepo;
    private final CyberFrameworkReferenceResolver frameworks;

    // PERF-PG-004: hard cap for the unpaginated "/all" dropdown dumps.
    private static final int DROPDOWN_CAP = 500;

    @Transactional(readOnly = true)
    public Page<CyberControl> getControls(Long orgId, Pageable p) {
        return controlRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public List<CyberControl> getAllControls(Long orgId) {
        // PERF-PG-004: cap the dropdown dump (see DROPDOWN_CAP).
        return controlRepo.findByOrganisationId(orgId, PageRequest.of(0, DROPDOWN_CAP)).getContent();
    }

    @Transactional(readOnly = true)
    public CyberControl getControl(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return controlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberControl not found: " + id));
    }

    @Transactional
    public CyberControl saveControl(CyberControl c, Long orgId) {
        // CY-H — title_en is NOT NULL; reject a blank/empty body with 400 not a DB 500.
        c.setTitleEn(CyberInputValidation.requireText(c.getTitleEn(), "titleEn"));
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        c.setId(null);
        c.setOrganisationId(orgId);
        c.setIsDeleted(false);
        // F2-252 — resolve the framework reference and DERIVE its label. The tenant comes from the
        // caller's argument, never from `c` (which is only stamped one line above; F2-246 shipped
        // that bug and every linked create 404'd).
        applyFrameworkReference(c, c, orgId, true);
        return controlRepo.save(c);
    }

    /**
     * F2-252 — the single writer of the {@code (framework_id, framework_name)} pair.
     *
     * <h2>The rule</h2>
     * {@code framework_name} is <b>derived</b> whenever {@code framework_id} resolves. It is the
     * caller's own string only when there is no id — a bare label stays legal, because the schema
     * says so (both columns nullable) and live rows are made that way, so requiring an id would
     * refuse requests that work today (F2-182).
     *
     * <h2>⚠ Why an omitted id on UPDATE must not clear the column</h2>
     * The route binds the ENTITY, so Jackson cannot tell {@code "frameworkId": null} from an absent
     * key. {@code applyEditable} wrote the field unconditionally, and {@code CyberPage}'s payload
     * builders — {@code CONTROL_EMPTY} and {@code controlToReq} — carry {@code frameworkName} and
     * <b>no {@code frameworkId}</b>. So editing any control through the platform's own screen NULLED
     * its framework link at HTTP 200, on a column populated on <b>75 of 75</b> live rows in dev and
     * prod, while leaving the label standing — so the row still <em>read</em> correctly on the
     * register and only a join could tell. F2-212's headline: a wire-contract gate proves the types
     * agree and cannot prove the payload builder sends what the type permits, and on a full-replace
     * verb that gap is silent data destruction.
     *
     * <h2>⚠ Why a label CHANGE on a linked row is refused rather than honoured</h2>
     * Relinking by name would need a name→framework lookup, and this pair's whole hazard is that
     * two registers share an id space and near-identical names ("NCA ECC" here, "NCA Essential
     * Cybersecurity Controls" there). A caller that wants a different framework sends its id. An
     * ECHO of the stored label is accepted, so a form that round-trips the row keeps working
     * (F2-231/F2-234).
     */
    private void applyFrameworkReference(CyberControl target, CyberControl req, Long orgId,
                                         boolean isCreate) {
        Long sentId = req.getFrameworkId();
        String sentName = trimToNull(req.getFrameworkName());
        String sentNameAr = trimToNull(req.getFrameworkNameAr());

        if (sentId != null) {
            var f = frameworks.require(sentId, orgId);
            requireEchoOrNothing("frameworkName", sentName, f.getName(),
                "does not name framework " + sentId + " ('" + f.getName() + "')", SEND_ID_ALONE);
            requireEchoOrNothing("frameworkNameAr", sentNameAr, f.getNameAr(),
                "does not name framework " + sentId + " ('" + f.getNameAr() + "')", SEND_ID_ALONE);
            target.setFrameworkId(f.getId());
            target.setFrameworkName(f.getName());
            target.setFrameworkNameAr(f.getNameAr());
            return;
        }

        Long storedId = isCreate ? null : target.getFrameworkId();
        if (storedId == null) {
            // Unlinked: BOTH labels are the caller's to state, and to clear. There is no parent to
            // derive from, so an Arabic label here records a framework that is not in the register
            // — which house rule 7 wants recordable rather than English-only.
            target.setFrameworkId(null);
            target.setFrameworkName(sentName);
            target.setFrameworkNameAr(sentNameAr);
            return;
        }

        // Linked, and the caller named no id. Keep BOTH halves — see the javadoc above.
        var derived = frameworks.labelsFor(storedId, orgId);
        boolean resolved = derived.en() != null;
        String current = resolved ? derived.en() : target.getFrameworkName();
        String currentAr = resolved ? derived.ar() : target.getFrameworkNameAr();
        requireEchoOrNothing("frameworkName", sentName, current,
            "contradicts this control's linked framework " + storedId + " ('" + current + "')",
            SEND_ID_TO_RELINK);
        requireEchoOrNothing("frameworkNameAr", sentNameAr, currentAr,
            "contradicts this control's linked framework " + storedId + " ('" + currentAr + "')",
            SEND_ID_TO_RELINK);
        target.setFrameworkName(current);
        // F2-277: the Arabic half rides with the English one. Falling back to the stored value
        // when the framework has since become unresolvable keeps the two halves describing the
        // same row — deriving one and keeping the other is how a pair comes apart.
        target.setFrameworkNameAr(currentAr);
    }

    /**
     * A label the server derives: not stated is fine, an ECHO of the derived value is fine,
     * anything else is refused and told where to go (F2-231/F2-234).
     *
     * <p>⚠ F2-277 — one helper for both halves rather than two hand-written comparisons. The
     * English half had this rule and the Arabic half did not, which would have made a sent Arabic
     * label an INERT field: declared on the wire, silently discarded, and therefore telling a
     * caller their value matters (F2-184). Case-insensitive because the columns collate that way
     * (F2-179).
     */
    private static final String SEND_ID_ALONE =
        "the label is derived from the framework, so send frameworkId alone, or send the "
            + "framework's real name";

    /**
     * ⚠ A DIFFERENT remedy, and keeping the two apart is the point. Here the caller sent no id
     * and a label that differs, i.e. is trying to RELINK BY NAME — the one operation this pair's
     * hazard forbids, because two registers share an id space and carry near-identical names. An
     * existing test asserts this wording, and it caught the first draft of the shared helper
     * flattening both refusals into one message: a diagnostic whose only job is to send the
     * caller somewhere is worthless if it sends them to the wrong place (F2-243).
     */
    private static final String SEND_ID_TO_RELINK = "send frameworkId to change the link";

    private static void requireEchoOrNothing(String field, String sent, String derivedValue,
                                             String because, String remedy) {
        if (sent == null) return;
        String current = trimToNull(derivedValue);
        if (current != null && sent.equalsIgnoreCase(current)) return;
        throw new IllegalArgumentException(
            field + " '" + sent + "' " + because + " — " + remedy);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional
    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * ⚠ This is the one member of the family that was NOT losing data: `CyberControlRequest`
     * happens to declare all thirteen editable columns, so nothing the client omits was in
     * fact omitted. It was converted anyway, because persisting the REQUEST OBJECT leaves
     * the hazard latent rather than absent — the day somebody adds a column to
     * `CyberControl` without adding it to the frontend type, every edit starts erasing it,
     * silently. Closing a harmless drift is cheaper than the reader who later has to work
     * out why four siblings are one shape and this one another.
     */
    private void applyEditable(CyberControl existing, CyberControl req) {
        // ⚠ F2-252 — frameworkId/frameworkName are NOT copied here. They are the one pair on this
        // entity where an omitted key must mean "leave the link alone" rather than "clear it", so
        // they go through applyFrameworkReference, which is their single writer. Copying them here
        // is how editing a control's title nulled its framework_id on 75 of 75 live rows.
        existing.setDomain(req.getDomain());
        existing.setControlNumber(req.getControlNumber());
        existing.setTitleEn(req.getTitleEn());
        existing.setTitleAr(req.getTitleAr());
        existing.setProcedureEn(req.getProcedureEn());
        existing.setControlType(req.getControlType());
        existing.setControlOwner(req.getControlOwner());
        existing.setReviewFrequency(req.getReviewFrequency());
        existing.setMaturityLevel(req.getMaturityLevel());
        existing.setImplementationStatus(req.getImplementationStatus());
        existing.setStatus(req.getStatus());
        // Last, so it reads the row's own stored link rather than a half-applied one. The tenant is
        // the LOADED row's here — not the caller's claim — which is what makes it safe on this path
        // (contrast saveControl, where the row has only just been stamped; see F2-246).
        applyFrameworkReference(existing, req, existing.getOrganisationId(), false);
    }

    public CyberControl updateControl(Long id, CyberControl req) {
        CyberControl existing = getControl(id);
        applyEditable(existing, req);
        return controlRepo.save(existing);
    }

    @Transactional
    public void deleteControl(Long id) {
        CyberControl c = getControl(id);
        c.setIsDeleted(true);
        controlRepo.save(c);
    }
}
