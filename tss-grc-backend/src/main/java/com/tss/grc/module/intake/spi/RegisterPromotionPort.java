package com.tss.grc.module.intake.spi;

/**
 * One register's answer to "turn this intake submission into a real record" — F2-172.
 *
 * <p>Implementations live in the module that <em>owns</em> the record, never here: see the
 * {@link com.tss.grc.module.intake.spi package javadoc} for why the dependency has to point that
 * way. Spring collects them as a {@code List} and {@link PromotionRegistry} indexes them by
 * {@link #target()}.
 *
 * <h3>The contract, and it is short on purpose</h3>
 * {@link #promote} must return the identifier of a record that <b>now exists in the database for
 * the current tenant</b>, or throw. It may not return a placeholder, a synthesised id, or the id
 * the client asked for. Returning an id for a row that was never written is precisely the defect
 * F2-171 and F2-172 exist to remove, and it is invisible from the caller's side — which is why
 * {@link PromotionRegistry} refuses a blank return rather than trusting the contract to hold.
 */
public interface RegisterPromotionPort {

    /**
     * The {@code RegisterTarget} this port serves — {@code "DPIA"}, {@code "RISK"}, … matching the
     * frontend {@code RegisterTarget} union and the catalogue's {@code defaultTarget}. Exactly one
     * port per target; {@link PromotionRegistry} fails at startup on a duplicate, because two
     * beans claiming one target means promotions land in whichever bean Spring happened to order
     * last.
     */
    String target();

    /**
     * The permission the caller must <b>also</b> hold, as {@code "MODULE:ACTION"} — the same pair
     * the target module's own create endpoint demands ({@code "PRIVACY:WRITE"} for a DPIA,
     * {@code "RISK:WRITE"} for a risk).
     *
     * <h3>Why promotion is not authorised by {@code INTAKE:TRIAGE} alone</h3>
     * Because that would make one permission a create-anything door into fourteen modules, reachable
     * without holding a single one of their guards. The direct route
     * ({@code POST /api/v1/privacy/dpias}) demands {@code PRIVACY:WRITE}; a second route to the same
     * write that demands something else is the F2-04 shape — two doors, one guarded.
     *
     * <p>Measured before choosing this rather than after: in dev exactly one non-admin user holds
     * {@code INTAKE:TRIAGE} ({@code compliance}, via {@code COMPLIANCE_OFFICER} and
     * {@code GRC_ADMINISTRATOR}), and that user already holds every permission the fourteen ports
     * below name. So the rule costs nothing today and closes the door before somebody grants
     * {@code INTAKE:TRIAGE} to a wider audience — which is the whole point of doing it now.
     */
    String requiredPermission();

    /**
     * Create the register record and return its id as a string.
     *
     * <p>String rather than {@code Long} because {@code intake.intake_records.promoted_to_record_id}
     * is {@code NVARCHAR(40)} and not every register keys on a bigint — CAPA tasks carry a code.
     *
     * @throws IllegalStateException when the record cannot be created for a reason the triager
     *         should see (a required parent does not exist, the module is not provisioned). The
     *         handler maps it to 409 with the message, so say what is missing.
     */
    String promote(IntakePromotionContext ctx);
}
