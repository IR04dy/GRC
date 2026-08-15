package com.tss.grc.common.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The fields a JPA entity's SERVER owns may never arrive from a request body, on ANY entity.
 *
 * <h2>F2-206 — the primary key</h2>
 *
 * <p>{@code POST /api/v1/privacy/ropa} with {@code {"id": 1, …}} answered <b>HTTP 201
 * Created</b> and <b>overwrote</b> processing activity 1 — because a non-null id makes
 * {@code CrudRepository#save} call {@code merge()} instead of {@code persist()}. Probed live
 * 2026-08-13 against a PDPL Article 31 record: {@code activity_name_ar}, {@code purpose_en},
 * {@code retention_period_months} and {@code data_categories_json} were nulled (the body did
 * not mention them), the lawful basis moved {@code LEGAL_OBLIGATION → CONSENT}, and
 * {@code special_category_flag} — the flag that makes the activity Article 25 high-risk —
 * was cleared. The response said a record had been created.
 *
 * <p>Cross-tenant reach was <b>probed and refuted</b>: naming another organisation's row id
 * answers 500 and changes nothing, so the tenant boundary holds. The live defect is
 * destruction of the caller's own tenant's records through an endpoint that reports success.
 *
 * <h2>⚠⚠ F2-215 — the other EIGHT fields, which F2-206 left behind</h2>
 *
 * <p>{@link com.tss.grc.common.model.BaseEntity} declares <b>nine</b> server-owned fields and
 * marks every one of them {@code READ_ONLY}. Its own javadoc explains why, field by field:
 * forged audit attribution, backdated records, <em>"an ordinary update could soft-delete or
 * resurrect a record, bypassing whatever guard the DELETE endpoint carries"</em>, optimistic
 * locking defeated. <b>F2-206 correctly widened that protection to every mapped type — for
 * {@code id} only</b>, because its probe body asked about one field at a time and its own
 * lesson ("one field per question") was about the PROBE. That lesson silently became the scope
 * of the FIX.
 *
 * <p>🔑 <b>Generalising an invariant one field at a time leaves the remainder looking
 * covered, because the mechanism that covers them is visibly present.</b> A reader who finds
 * this class does not then go and check which fields it names. Measured 2026-08-15 by
 * reflection over {@code target/classes}: of the 259 entities that do not extend
 * {@code BaseEntity}, <b>{@code createdAt} bound on 222, {@code isDeleted} on 209,
 * {@code updatedAt} on 146, {@code createdBy} on 111, {@code updatedBy} on 104,
 * {@code deletedBy} on 56, {@code rowVersion} on 52 and {@code deletedAt} on 50 — and NONE of
 * the 960 declarations carried an annotation.</b> That is <b>170 of the 214 entity-bound write
 * endpoints, across 102 entities</b>.
 *
 * <p>⚠ {@code isDeleted} also shows why a probe's SHAPE is part of its population.
 * {@code {"isDeleted": true}} binds on 177 entities and <b>throws on 35</b>, which map the
 * column as {@code Short}; F2-206 recorded that as making those entities <em>unprobeable</em>
 * and moved on. {@code {"isDeleted": 1}} binds on <b>all 209</b>. The typing never protected
 * them — it refused one JSON shape — and the entities concerned are
 * {@code TreatmentPlan}, {@code Vulnerability}, {@code DsrCase}, {@code Finding},
 * {@code CrossBorderTransfer}: the statutory registers.
 *
 * <h2>What was proved live, and what was refuted</h2>
 *
 * <p>Probed 2026-08-15 as {@code compliance} (not {@code admin} — PLATFORM_ADMIN bypasses in
 * code) against {@code POST /api/v1/controls}, which binds
 * {@code module.platform.controls.Control}. Every row was planted by the probe and removed
 * with a {@code CHARINDEX} delete (F2-206's rule: a probe that demonstrates a destructive
 * defect plants its own victim).
 *
 * <ul>
 *   <li><b>CONFIRMED — forged attribution.</b> {@code "createdBy": "ceo@tss.sa"} was stored
 *       verbatim in {@code platform.controls.created_by}. That is DF-3/DF-4's defect, which
 *       the platform has previously treated as an authority bypass and fixed one endpoint at
 *       a time.</li>
 *   <li><b>CONFIRMED — backdating.</b> {@code "createdAt": "2001-01-01T00:00:00"} was stored,
 *       overwriting the entity's own {@code = LocalDateTime.now()} initialiser.</li>
 *   <li><b>CONFIRMED — a record born deleted.</b> {@code "isDeleted": true} on a CREATE stored
 *       the row soft-deleted. {@code Control} carries {@code @Where(clause = "is_deleted = 0")},
 *       so the row is invisible to every query <em>including the service's own duplicate
 *       check</em> — while the UNIQUE index still sees it. The control code is then permanently
 *       unusable, and the caller is told only that <em>"a record with these values already
 *       exists"</em> for a code the register shows nothing for.</li>
 *   <li><b>REFUTED — soft-delete through the UPDATE endpoint.</b> {@code updateControl} copies
 *       a nine-field whitelist onto the loaded row, so {@code isDeleted} never reaches it
 *       (F2-22b's {@code INERT} verdict). Recorded rather than quietly dropped: the claim was
 *       mine and the probe is what disproved it.</li>
 * </ul>
 *
 * <p>⚠ The refutation is the reason this is closed at the wire rather than endpoint by
 * endpoint. Whether a bound field <em>reaches the row</em> depends on each service, and
 * {@code MassAssignmentRatchetTest}'s risk profile says exactly that in its own header. Auditing
 * 170 endpoints to find the subset that lets {@code isDeleted} through would produce a list that
 * is wrong the next time somebody writes {@code repo.save(body)}.
 *
 * <h2>Why an introspector, and why these nine names</h2>
 *
 * <p>An introspector <b>has no population to get wrong</b>: it is consulted for every property
 * of every type Jackson binds, so a new entity is covered on the day it is written and there is
 * no registry to forget. The alternative was ~960 annotations across 259 files.
 *
 * <p>{@code READ_ONLY} and never {@code @JsonIgnore}: serialise exactly as before, refuse to
 * deserialise. {@code @JsonIgnore} would strip {@code id} from every response and the entire
 * frontend resolves records by id. It also does not remove the property from the deserialiser —
 * the frontend echoes whole records back on update paths, and under
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} that would turn working requests into 400s.
 *
 * <p><b>Nulling a field the wire used to supply is only safe if the server supplies it.</b>
 * Measured before the change, not assumed — four mechanisms can fill one, and the fourth is
 * the one a reflection scan cannot see:
 * <ul>
 *   <li>{@code isDeleted} and {@code rowVersion}: <b>zero</b> entities where nothing fills it
 *       (200 field initialisers, 34 {@code @Version}, the rest lifecycle callbacks).</li>
 *   <li>{@code createdBy}, {@code updatedBy}, {@code deletedAt}, {@code deletedBy}: every
 *       unfilled column is <b>nullable</b>, and a caller-supplied value in one of them is the
 *       forgery this closes.</li>
 *   <li>{@code createdAt}: the scan reported <b>10</b> entities with a NOT NULL column and no
 *       filler — and all ten declare {@code insertable = false}, so Hibernate never writes the
 *       column and the database default does. 🔑 <b>The scan located; reading is what measured.
 *       A missing fourth mechanism reads exactly like a missing value</b>, and acting on that
 *       number would have been the F2-40 trap: annotating blind and breaking a working create.</li>
 * </ul>
 *
 * <p>Also checked before the change: <b>no service copies one of these from a request body</b>
 * (378 setter sites, all server values), and the frontend sends one in exactly two payloads,
 * both of them fabricated corpora.
 *
 * <h2>What this deliberately does NOT do</h2>
 *
 * <p>It does not touch {@code externalId}. A client-chosen business reference is a different
 * defect (F2-172(d)) fixed per service, because some references are legitimately supplied by
 * an integrating system while a database primary key never is.
 *
 * <p>It does not touch {@code organisationId}, which is {@code MassAssignmentRatchetTest}'s own
 * TENANT tier and a different question — services assign it explicitly from
 * {@code TenantContext}, so closing it at the wire needs its own measurement. Filed, not done.
 *
 * @see com.tss.grc.common.model.BaseEntity the mapped superclass whose field list this mirrors
 * @see com.tss.grc.security.ServerOwnedFieldsInvariantTest the gate that asserts it
 * @see com.tss.grc.security.MassAssignmentRatchetTest the ratchet whose population this widens
 */
@Component
public class ServerOwnedFieldsModule extends SimpleModule {

    /**
     * Exactly {@link com.tss.grc.common.model.BaseEntity}'s nine {@code READ_ONLY} fields.
     *
     * <p>Kept as a mirror of that class rather than a fresh judgement, and
     * {@code ServerOwnedFieldsInvariantTest} asserts the two lists are equal by reflection —
     * so a tenth server-owned field added to {@code BaseEntity} fails the build until it is
     * covered here too. That assertion is the whole point of this slice: the previous version
     * of this class named one of the nine and nothing could tell.
     */
    public static final Set<String> SERVER_OWNED_FIELDS = Set.of(
        "id",
        "createdAt", "updatedAt",
        "createdBy", "updatedBy",
        "isDeleted", "deletedAt", "deletedBy",
        "rowVersion");

    public ServerOwnedFieldsModule() {
        super("server-owned-fields");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        // insert, not append: this introspector must be consulted BEFORE the default one so a
        // class that says nothing about these fields still gets READ_ONLY. We defer to super
        // for every property that is not one of them.
        context.insertAnnotationIntrospector(new EntityFieldsAreServerOwned());
    }

    /** Reports {@code READ_ONLY} for a server-owned field of any mapped type. */
    static final class EntityFieldsAreServerOwned extends JacksonAnnotationIntrospector {

        @Override
        public JsonProperty.Access findPropertyAccess(Annotated a) {
            if (isServerOwned(a)) {
                return JsonProperty.Access.READ_ONLY;
            }
            return super.findPropertyAccess(a);
        }

        /**
         * Matches the FIELD only, and that is a measured decision, not an oversight.
         *
         * <p>F2-206's first draft also matched {@code getX}/{@code setX} on the theory that
         * Jackson might resolve the property through Lombok's accessors when the field is
         * private. The mutation harness disproved it: deleting the accessor branch left the
         * invariant green across all 433 entities, so the branch was <b>dead code that no
         * assertion covered</b> — and {@code BaseEntity} is the standing proof, since its own
         * {@code READ_ONLY} annotations have always been on the fields.
         *
         * <p>🔑 <b>A surviving mutation is not always a hole in the gate; sometimes it is the
         * gate telling you the code has a branch nothing needs.</b>
         */
        private static boolean isServerOwned(Annotated a) {
            return a instanceof AnnotatedField f
                && SERVER_OWNED_FIELDS.contains(f.getName())
                && isMapped(f.getDeclaringClass());
        }

        /**
         * {@code @Entity} covers the concrete types; {@code @MappedSuperclass} covers a field
         * declared one level up, which is where {@code BaseEntity} keeps all nine — so the
         * check must ask about both or it would miss the very population that already works.
         */
        private static boolean isMapped(Class<?> c) {
            return c != null
                && (c.isAnnotationPresent(Entity.class) || c.isAnnotationPresent(MappedSuperclass.class));
        }
    }
}
