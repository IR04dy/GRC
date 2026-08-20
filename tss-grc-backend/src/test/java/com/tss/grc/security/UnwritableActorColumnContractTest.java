package com.tss.grc.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-256 — an actor column that NOTHING writes, beside an act clock the server stamps.
 *
 * <p><b>Why this instrument did not exist.</b> {@link SplitAttributionContractTest} finds <i>the
 * server owns WHEN and the CALLER owns WHO</i>. Its derivation walks the setter call sites inside a
 * method body and sorts each one into a clock half or an actor half:
 *
 * <pre>
 *     Matcher sm = SETTER.matcher(body);
 *     while (sm.find()) { … clockKeys.add(…) … callerActors.put(…) … }
 * </pre>
 *
 * <p>🔑🔑 <b>Both halves of that pair are built from setter call sites, so a column nobody sets
 * contributes to NEITHER half — "nobody owns WHO" is invisible to the instrument built to find "the
 * caller owns WHO".</b> {@link ParameterBorneActorTest} and
 * {@link PrincipalStampedFieldContractTest} narrow the same population further (a scalar parameter,
 * resolved at its call sites); {@link ServerStampedFieldContractTest} intersects <i>fields a guarded
 * route stamps</i> with <i>fields a write path takes from the request</i>, and a field nothing
 * writes is in neither set. All four are keyed on a write EXISTING. The invariant is real; its
 * POPULATION is narrower than it looks — and the narrowing here is none of the earlier ones (a
 * filename, a three-entry allow-list, a module name, an {@code extends} clause, a field-name list
 * inside a fix, a claim shape in a gate): it is <b>the existence of a setter call</b>.
 *
 * <p><b>The defect it found.</b> {@code ai_governance.recertification.approver_user_id} is declared
 * on the entity, returned on the wire by {@code RecertificationResponse}, and written by no code
 * path in the platform — while {@code AiLifecycleService#transitionRecertification} stamps
 * {@code decidedAt} from {@code LocalDateTime.now()} in the same {@code switch} branch. Neither
 * request record declares the field, so the caller cannot supply it and the server does not resolve
 * it: an ISO/IEC 42001 model-recertification decision recorded <i>when</i> and never <i>who</i>.
 * The correct artefact is {@code DataclassService#transitionException}, which has the identical
 * private {@code currentUserId()} helper and writes {@code e.setApproverUserId(currentUserId())}
 * beside its own {@code setApprovedAt(now())} — and {@code AiLifecycleService} has that same helper
 * three lines from the switch, feeding the timeline event's actor. 🔑 <b>Two sibling services, one
 * shape, one helper spelling, and only one of them writes the row.</b>
 *
 * <p><b>Why an unwritable column is worse than a forgeable one.</b> A column the caller can forge
 * stores a claim, and a claim can be disbelieved. A column nothing writes stores nothing, and a
 * governance register with a blank decider reads as missing data rather than as a defect — so it is
 * nobody's finding. It also cannot be repaired from any screen: unlike a role nobody holds, there is
 * no configuration that fills it (F2-255's distinction, one table down).
 *
 * <h3>Bytecode, per owner type — and the first two drafts of this gate got that wrong</h3>
 * <p>Call sites come from the compiled constant pool via ASM, keyed by the OWNER's internal name.
 * <ul>
 *   <li>Draft 1 asked whether the FIELD NAME was written anywhere in main sources, so
 *       {@code Recertification#approverUserId} read as <i>written</i> — because
 *       {@code DataclassService} and {@code RegReportingService} each write a DIFFERENT entity's
 *       {@code approverUserId}. 🔑 <b>A field-name fallback silently clears exactly the columns most
 *       likely to be spelled the same elsewhere, and it hid the site this gate was written for.</b>
 *       F2-187 recorded that mistake for methods (<i>"resolve per owner type"</i>); it recurs here
 *       one noun across, and it UNDER-reports, which reads exactly like a clean tree.</li>
 *   <li>Draft 2 resolved the owner from the SOURCE FILE NAME, and
 *       {@code module/…/entity/Entities.java} declares eleven nested {@code @Entity} classes — so
 *       all eleven collapsed into one key called {@code Entities}, whose field set was their union
 *       and whose owner name matched no bytecode at all. Eleven phantom rows, each looking exactly
 *       like a measured one. 🔑 <b>A source file is not a type.</b></li>
 * </ul>
 * <p>Both are fixed by resolving entities through {@link ClassPathScanningCandidateComponentProvider}
 * — the same idiom {@code BilingualInvariantRatchetTest} and F2-241's initialiser gate use — which
 * gives the real class, its own declared fields and the exact internal name to match against the
 * constant pool.
 *
 * <ul>
 *   <li>Draft 3 then claimed those own declared fields excluded {@code BaseEntity}'s nine "by
 *       construction rather than by a list", and that was wrong: {@code getDeclaredFields()}
 *       excludes <b>inherited</b> fields, not <b>redeclared</b> ones, and F2-215 measured 259
 *       entities outside {@code BaseEntity} that declare their own {@code createdBy} (111),
 *       {@code updatedBy} (104) and {@code deletedBy} (56). The census went 62 → 209, almost
 *       entirely row stamps. 🔑 <b>Moving to reflection fixed the nested-class bug and
 *       reintroduced the noise the source draft had excluded — a correction is not a monotone
 *       improvement, and "by construction" is a claim to check rather than a reason to stop
 *       checking.</b> {@link #ROW_STAMPS} is applied wherever the field is declared, because
 *       F2-226's rule is about what the column MEANS: every row has a creator, so a row stamp can
 *       never be the proof this gate rests on.</li>
 * </ul>
 *
 * <p><b>The one write mechanism bytecode cannot see is Jackson.</b> An endpoint that binds the
 * ENTITY writes every property by reflection, with no {@code INVOKEVIRTUAL} anywhere — so such an
 * entity's columns are writable by the caller and belong to the mass-assignment family
 * ({@link MassAssignmentRatchetTest}, F2-206/F2-215), not to this one. Entity-bound types are
 * subtracted, and the subtraction is pinned in both directions: without it this gate would report a
 * caller-writable column as unwritable, which is the opposite finding.
 *
 * <p><b>Hermetic.</b> Reflection plus bytecode from {@code target/classes} plus source for the
 * clock scan — no database and no Spring context, like {@link DarkServiceMethodTest}. The
 * actor-field vocabulary and the server-clock predicate are READ FROM
 * {@link SplitAttributionContractTest} rather than copied: a second agent-noun list in a second
 * gate would commit the very defect this family exists to find (F2-177, F2-230).
 */
class UnwritableActorColumnContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");

    /** An act clock: a time-shaped field naming something that HAPPENED. */
    private static final Pattern TIME_TAIL = Pattern.compile("(?:At|Date|On)$");

    /** {@code @RequestBody Recertification body} / {@code @Valid @RequestBody Foo f}. */
    private static final Pattern REQUEST_BODY_TYPE = Pattern.compile(
        "@RequestBody\\s+(?:@Valid\\s+)?(?:final\\s+)?([A-Z]\\w*)");

    /**
     * ROW stamps — who created or last touched the row, not who performed an act.
     *
     * <p>Excluded wherever they are declared, whether inherited from {@code BaseEntity} or
     * redeclared by the entity itself. F2-226's rule: <i>every</i> row has a creator, so a row stamp
     * can never be the proof this gate rests on. They are also already defended platform-wide by
     * {@code ServerOwnedFieldsModule} (F2-206/F2-215) and filled by the framework, so a missing
     * {@code setX} call site says nothing about them.
     */
    private static final Set<String> ROW_STAMPS = Set.of(
        "createdBy", "updatedBy", "deletedBy", "createdByUserId", "updatedByUserId",
        "deletedByUserId", "createdByName", "updatedByName");

    /**
     * Ordinary English words that end in {@code -er}/{@code -or}/{@code -Id} and name no person.
     *
     * <p>{@link SplitAttributionContractTest#isActorField} matches those suffixes because
     * {@code approver}, {@code reviewer} and {@code validator} are the platform's agent nouns. The
     * same rule matches {@code referenceNumber}, {@code merkleAnchor}, {@code statusAfter},
     * {@code scheduledFor} and {@code isCrossBorder}. F2-225 measured that bucket at 18 of 79
     * candidates and named it NOISE-WORD; publishing those as attributions with no decider would be
     * a gate failing on correct code (F2-214).
     *
     * <p>⚠ This is a NAME LIST, the instrument class this programme repeatedly finds measures its
     * own vocabulary (F2-217). It is tolerable on exactly the terms {@code REFERENCE_TAIL} is: it
     * only ever REMOVES members, and it is pinned in BOTH directions by
     * {@link #theNoiseNarrowingRemovesNonPeopleAndKeepsPeople()} — so widening it cannot quietly
     * shrink the census without the must-keep half failing.
     */
    private static final Pattern NOISE_TAIL = Pattern.compile(
        "(?:Number|Anchor|Order|Folder|Header|Footer|Marker|Identifier|Parameter|Counter"
        + "|Filter|Buffer|Layer|Cover|Other|Quarter|Character|Descriptor|Selector|Vector"
        + "|Factor|Sector|Indicator|Denominator|Numerator|Colo?r|Mirror|Cursor|Border"
        + "|After|Before|For|Trigger|Register|Provider|Container|Version|Tier"
        // Organisations and documents, not people. Both have their own instruments — a parent
        // register reference is F2-246's family (ParentIdentityCopyContractTest) and a document
        // reference is what SplitAttributionContractTest's own REFERENCE_TAIL removes. Counting
        // them here would publish "this vendor has no decider", which is not a sentence.
        + "|Vendor|VendorId|Issuer|IssuerId|EventId|RuleId|DocumentId)$",
        Pattern.CASE_INSENSITIVE);

    // ────────────────────────────────────────────────────── bytecode: who writes what

    private static Set<String> cachedCalls;
    private static Set<String> cachedClockWriters;

    /** {@code com/tss/…/Recertification#setApproverUserId} for every setter call in the jar. */
    static synchronized Set<String> setterCallsByOwner() {
        scan();
        return cachedCalls;
    }

    /**
     * The subset of those calls made from a class that also reads the server's own clock.
     *
     * <p>🔑 <b>This exists because the mutation harness proved the clock half of this gate was
     * VACUOUS.</b> Its first form asked, over all main sources, whether any {@code .setCompletedAt(}
     * anywhere took a {@code now()} argument — draft 1's field-name bug in the OTHER half of the same
     * gate. Across 432 entities the time-field names collide constantly, so the answer was
     * effectively always yes and the mutation that deleted the requirement altogether SURVIVED.
     * A surviving mutant is a hole only if the mutated code is wrong (F2-206) — here it was, and the
     * wrongness was that the condition could not fail. Resolving the clock setter by OWNER makes it
     * load-bearing.
     *
     * <p>Class-scoped rather than method-scoped on purpose: the house idiom is
     * {@code LocalDateTime now = LocalDateTime.now();} at the top of a transition and
     * {@code recordDecision(c, now)} below it, so the {@code now()} call and the setter call are
     * legitimately in different methods of the same service. Requiring them in one method would fail
     * on the very shape this slice introduced — a gate must never fail on correct code (F2-214).
     */
    static synchronized Set<String> clockWritingCallsByOwner() {
        scan();
        return cachedClockWriters;
    }

    private static void scan() {
        if (cachedCalls != null) { return; }
        Set<String> calls = new TreeSet<>();
        Set<String> clockWriters = new TreeSet<>();
        try {
            PathMatchingResourcePatternResolver r = new PathMatchingResourcePatternResolver();
            for (Resource res : r.getResources("file:./target/classes/**/*.class")) {
                Set<String> perClass = new TreeSet<>();
                boolean[] readsTheClock = {false};
                try (InputStream in = res.getInputStream()) {
                    new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                         String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String mName,
                                                            String desc, boolean isInterface) {
                                    if (mName.startsWith("set")) { perClass.add(owner + "#" + mName); }
                                    if (owner.startsWith("java/time/") && "now".equals(mName)) {
                                        readsTheClock[0] = true;
                                    }
                                }

                                @Override
                                public void visitInvokeDynamicInsn(String iName, String desc,
                                                                   Handle bsm, Object... bsmArgs) {
                                    // Foo::setBar as a method reference lands here, not in
                                    // visitMethodInsn — missing these would report a column written
                                    // only through a method reference as unwritable.
                                    for (Object a : bsmArgs) {
                                        if (a instanceof Handle h && h.getName().startsWith("set")) {
                                            perClass.add(h.getOwner() + "#" + h.getName());
                                        }
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_FRAMES);
                }
                calls.addAll(perClass);
                if (readsTheClock[0]) { clockWriters.addAll(perClass); }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cachedCalls = calls;
        cachedClockWriters = clockWriters;
    }

    // ─────────────────────────────────────────────────────────── reflection: the entities

    /**
     * Every shipped {@code @Entity}, resolved by reflection.
     *
     * <p>Fixtures are excluded by CODE SOURCE, never by a class name ending in {@code Test} — F2-192
     * recorded that a name test lets any member escape by being renamed, and F2-241 hit exactly this
     * when its own synthetic unprobeable entity turned up in a census.
     */
    static List<Class<?>> shippedEntities() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            try {
                Class<?> c = Class.forName(bd.getBeanClassName());
                var src = c.getProtectionDomain().getCodeSource();
                if (src != null && src.getLocation().toString().replace('\\', '/')
                        .contains("/test-classes/")) {
                    continue;
                }
                out.add(c);
            } catch (Throwable ignored) {
                // a class that cannot load cannot be asserted on
            }
        }
        return out;
    }

    /** Persisted instance fields the entity declares ITSELF — supertype fields excluded. */
    static Set<String> ownFields(Class<?> entity) {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : entity.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) { continue; }
            if (f.isAnnotationPresent(Transient.class)) { continue; }
            out.add(f.getName());
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────── source: the clock scan

    private static Collection<String> cachedSources;

    /** Every main source, comment-stripped once (F2-193: strip comments in every scan). */
    static synchronized Collection<String> mainSources() {
        if (cachedSources != null) { return cachedSources; }
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(MAIN_JAVA)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                out.add(SplitAttributionContractTest.strip(
                    Files.readString(p, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cachedSources = out;
        return out;
    }

    /** Every type any controller binds as a {@code @RequestBody} — Jackson writes these. */
    static Set<String> entityBoundTypes() {
        Set<String> out = new TreeSet<>();
        for (String src : mainSources()) {
            var m = REQUEST_BODY_TYPE.matcher(src);
            while (m.find()) { out.add(m.group(1)); }
        }
        return out;
    }

    /**
     * Act clocks on this entity that some service stamps from the server's own clock.
     *
     * <p>The clock half is what makes an unwritten actor column a finding rather than an unused
     * column: the row asserts that something happened at a moment the SERVER chose, so the platform
     * has already declared itself the recorder of the act (F2-223's premise).
     */
    static Set<String> serverStampedClocks(Class<?> entity, Set<String> fields) {
        return fields.stream()
            .filter(f -> TIME_TAIL.matcher(f).find())
            .filter(f -> clockWritingCallsByOwner().contains(setterKey(entity, f)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** {@code com/tss/…/Recertification#setDecidedAt} — the constant-pool key for one field. */
    private static String setterKey(Class<?> entity, String field) {
        return entity.getName().replace('.', '/') + "#set"
             + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    /** An actor field this gate counts: agent-shaped, a person, an ACT, not a noise word. */
    static boolean isPersonField(String field) {
        return SplitAttributionContractTest.isActorField(field)
            && !NOISE_TAIL.matcher(field).find()
            && !ROW_STAMPS.contains(field);
    }

    /** Is {@code Entity.setField(…)} invoked anywhere, resolved by the OWNER's internal name? */
    static boolean writtenOn(Class<?> entity, String field) {
        return setterCallsByOwner().contains(setterKey(entity, field));
    }

    /** {@code Entity#field} for every actor column with no writer beside a server-stamped clock. */
    static List<String> unwritableActorColumns() {
        Set<String> bound = entityBoundTypes();
        List<String> found = new ArrayList<>();
        for (Class<?> entity : shippedEntities()) {
            if (bound.contains(entity.getSimpleName())) { continue; }
            Set<String> fields = ownFields(entity);
            Set<String> actors = fields.stream()
                .filter(UnwritableActorColumnContractTest::isPersonField)
                .filter(f -> !writtenOn(entity, f))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (actors.isEmpty()) { continue; }
            if (serverStampedClocks(entity, fields).isEmpty()) { continue; }
            actors.forEach(a -> found.add(entity.getSimpleName() + "#" + a));
        }
        return found.stream().sorted().toList();
    }


    // ─────────────────────────────────────────────────────────────── the census

    /**
     * Actor columns nothing can write, READ and given a verdict.
     *
     * <p>An actor column with no writer is not automatically a defect — it may be a column awaiting
     * a capability nobody built, and inventing a filler for one would be F2-196's own defect. So
     * entries land here only after the code was opened, and everything else stays on
     * {@link #WORK_LIST} with no reason at all (F2-188 measured what happens when a slice writes a
     * reason for something it has not read: five claims, five false, and the fluency was what made
     * them look verified).
     */
    private static final Map<String, String> ADJUDICATED = new TreeMap<>(Map.ofEntries(
        Map.entry("AuditLogEntry#userId",
            "FILED — the actor IS recorded, by username. AuditLogService line 183 writes "
            + "setUserUsername(auth.getName()) and there is no setUserId call anywhere, so "
            + "platform.audit_log.user_id is a declared column with no writer while user_username "
            + "carries the principal (F2-248 read that column holding 'ranalyst'). Harm is limited "
            + "to a rename making an old trail ambiguous, not to an unattributed act."),
        Map.entry("EvidenceItem#collectorId",
            "FILED by F2-249 — 'evidence.evidence_items :: collector has NO writer of either half "
            + "(77 of 78 rows populated by the seed - honest data behind no code, so it cannot "
            + "regress and cannot be maintained)'. This gate rediscovering it independently is the "
            + "corroboration that the derivation works."),
        Map.entry("EvidenceItem#collectorName",
            "FILED by F2-249 — the label half of the same pair. F2-252 measured its live values as "
            + "an OFFICE ('Cyber Risk & Compliance Officer / …') rather than a person, so deriving "
            + "it from a resolved user would destroy information (F2-251's rule)."),
        Map.entry("Policy#approverId",
            "DARK TWICE — module/compliance/entity/Policy declares it and PolicyService writes it "
            + "nowhere, not even inside the methods that would; and DarkServiceMethodTest already "
            + "holds PolicyService#create, #update, #submitForLegalReview, #legalApprove, "
            + "#executiveApprove and #markReviewDue as having no caller, the legacy lifecycle "
            + "retired in favour of PolicyManagementService#transition. Closing it would mean "
            + "reviving a retired write path."),
        Map.entry("Policy#authorId",
            "DARK TWICE — the legacy compliance.Policy register's author column, written by no "
            + "method in PolicyService including its own dark #create and #update. Its live twin "
            + "is policy.documents, whose author/owner fields F2-222 froze at REVIEW; nothing "
            + "reads this one, so filling it would be reviving a retired write path rather than "
            + "closing an attribution gap."),
        Map.entry("Policy#legalReviewerId",
            "DARK TWICE — the legal-review attribution of the same retired register. "
            + "PolicyService#submitForLegalReview and #legalApprove are both in "
            + "DarkServiceMethodTest's map with no caller, and neither writes this column even so, "
            + "so the C-6 segregation control that would have consumed it lives on "
            + "PolicyManagementService#transition over a different table.")
    ));

    /**
     * Read the code before writing a reason. These are measured members with NO verdicts.
     *
     * <p><b>F2-257 closed the largest sub-shape and corrected this paragraph twice.</b> It read
     * "nine event registers declare {@code actorName} beside an {@code actorUserId} their service
     * DOES write" and then listed <b>ten</b> — the nine on this list plus {@code FraudCaseUpdate},
     * which is not a member of the census at all because its clock is a field initialiser. 🔑 <b>A
     * member excluded by an instrument's blind spot reads exactly like a member that was checked and
     * cleared</b>, and here the prose and the list disagreed by one in the reassuring direction.
     * Resolved from {@code sys.columns} in both databases, ten tables declare both halves and every
     * one of them is now written.
     *
     * <p>The second correction is the consequence. This paragraph said "every timeline entry renders
     * a blank actor". Measured across {@code tss-grc-ui-v2}, <b>no screen renders any of these ten
     * actors</b> — {@code EthicsPage} is the only surface in the platform that reads an
     * {@code actorName}, and that is the register F2-248 closed. So the blank reached API consumers
     * and the export surface rather than a page. 🔑 <b>A claim about a consequence is the
     * least-verified part of a filing</b> (F2-192), and it is worth stating narrowly: this is a fact
     * about today, not a property of the control (F2-182), since the field is on the wire and the
     * next client to declare it would render nothing.
     *
     * @see #theActorPairIsAlwaysWrittenTogether()
     */
    private static final Set<String> WORK_LIST = Set.of(
        "AccessReviewItem#userId",
        "AiSuggestion#reviewedBy",
        "AlternateSupplier#lastAssessedBy",
        "AppetiteBreachEvent#acknowledgedById",
        "ChangeCondition#ownerId",
        "CrisisAar#approvedBy",
        "EvidenceItem#chainLastVerifiedBy",
        "EvidenceVersion#chainVerifiedBy",
        "EvidenceVersion#snapshottedBy",
        "FourthPartyExposure#lastValidatedBy",
        "MtpdInheritanceAssessment#computedByUserId",
        "NdiSubmissionPackage#reviewerName",
        "ReportingYear#approvedBy",
        "ReportingYear#reviewedBy",
        "RiskAttestation#attestedBy",
        "SustainabilityReport#generatedBy",
        "SustainabilityReport#reviewedBy",
        "VendorBcpAttestation#escalatedToUserId"
    );

    // ───────────────────────────────────────────────────────────── assertions

    @Test
    @DisplayName("the census is exactly the adjudicated set plus the work list — forwards")
    void everyUnwritableActorColumnIsAccountedFor() {
        Set<String> unexplained = new TreeSet<>(unwritableActorColumns());
        unexplained.removeAll(ADJUDICATED.keySet());
        unexplained.removeAll(WORK_LIST);
        assertThat(unexplained)
            .as("""
                A new actor column that nothing can write. Either a writer was deleted, or a column \
                was added for a capability nobody built. Read the service, then add it to \
                ADJUDICATED with a verdict, or to WORK_LIST with none — do not manufacture a reason \
                (F2-188). A blank decider on a governance row reads as missing data rather than as a \
                defect, which is why nobody finds these without an instrument.""")
            .isEmpty();
    }

    @Test
    @DisplayName("every declared entry still names a real site — the reverse direction")
    void nothingIsExplainedThatIsNoLongerThere() {
        Set<String> live = new TreeSet<>(unwritableActorColumns());
        Set<String> stale = new TreeSet<>(ADJUDICATED.keySet());
        stale.addAll(WORK_LIST);
        stale.removeAll(live);
        assertThat(stale)
            .as("""
                These entries name a column that now HAS a writer, or an entity/field that no longer \
                exists. Delete the entry in the same commit that closed it. This is the leg nobody \
                writes first (F2-199) and it is the one that catches a reason written for something \
                that was never in the population.""")
            .isEmpty();
    }

    @Test
    @DisplayName("no verdict is a bare label")
    void everyVerdictNamesAConsequence() {
        for (Map.Entry<String, String> e : ADJUDICATED.entrySet()) {
            assertThat(e.getValue()).as("verdict for %s", e.getKey()).hasSizeGreaterThan(80);
            assertThat(e.getValue().replaceAll("\\s+", " "))
                .as("verdict for %s must state a verdict word", e.getKey())
                .containsPattern("FILED|DARK|BY DESIGN|CLOSED");
        }
    }

    @Test
    @DisplayName("F2-256: the recertification decision pair has ONE writer and it is not the request")
    void theRecertificationDecisionIsAttributedToThePrincipal() {
        String src = read(Path.of("src", "main", "java", "com", "tss", "grc", "module", "ai",
                                  "lifecycle", "service", "AiLifecycleService.java"));
        String code = SplitAttributionContractTest.strip(src);

        // One writer of each half — F2-245's rule: the claim that prevents drift is about WHERE the
        // writes live, not how many call sites there are.
        assertThat(count(code, "setApproverUserId"))
            .as("approverUserId must be written in exactly one place").isEqualTo(1);
        assertThat(count(code, "setDecidedAt"))
            .as("decidedAt must be written in exactly one place, beside the approver").isEqualTo(1);

        // …and that place resolves the actor from the principal, refusing rather than storing null.
        String body = methodBody(code, "recordDecision");
        assertThat(body)
            .as("the decision's actor must come from the authenticated principal")
            .containsPattern("CurrentActor\\s*\\.\\s*requireUserId\\s*\\(");
        assertThat(body)
            .as("""
                the decision's actor may not come from the request — that is the split \
                attribution SplitAttributionContractTest exists to refuse, and it is what this \
                site would become if a future slice added the field to the request record""")
            .doesNotContainPattern("\\breq\\s*\\.");

        // Both decision branches must route through it, or one state loses its decider again.
        String transition = methodBody(code, "transitionRecertification");
        assertThat(count(transition, "recordDecision\\s*\\("))
            .as("both EXPIRED and the three RECERTIFIED/REVOKED targets must record a decider")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("F2-256: the two sibling registers have NO actor column for their decision at all")
    void theSiblingRegistersMissingColumnsAreRecordedRatherThanImplied() {
        // F2-215's headline: generalising an invariant one field at a time leaves the remainder
        // looking covered, because the mechanism that covers them is visibly present. A reader who
        // finds transitionRecertification attributing its decision has no reason to ask whether the
        // two transitions above it can. They cannot — there is nowhere to write it.
        Set<String> validation = ownFields(fieldsOf("ModelValidation"));
        Set<String> retraining = ownFields(fieldsOf("RetrainingEvent"));

        assertThat(validation).as("ModelValidation stamps completedAt from the server's clock")
            .contains("completedAt");
        assertThat(validation.stream().filter(UnwritableActorColumnContractTest::isPersonField).toList())
            .as("""
                ModelValidation's only person column is validatorUserId, which createValidation \
                writes from the request at SCHEDULED — an ASSIGNMENT (who WILL validate), not an \
                attribution, and validationType admits EXTERNAL_AUDIT so the validator need not be \
                a platform principal at all (F2-224's auditorExternalParty rule). There is no \
                completedBy column, so who PASSED or FAILED a model validation is unrecordable. \
                Adding one is a migration and an owner decision — filed, and asserted here so the \
                absence is countable instead of invisible (F2-255).""")
            .containsExactly("validatorUserId");

        assertThat(retraining).as("RetrainingEvent stamps approvedAt from the server's clock")
            .contains("approvedAt");
        assertThat(retraining.stream().filter(UnwritableActorColumnContractTest::isPersonField).toList())
            .as("""
                RetrainingEvent's only person column is ownerUserId, written from the request at \
                PROPOSED — again an assignment. There is no approvedBy column, so who APPROVED a \
                model retraining is unrecordable. Same filing as ModelValidation above.""")
            .containsExactly("ownerUserId");
    }

    @Test
    @DisplayName("no shipped @Entity uses @Builder — the premise the bytecode scan rests on")
    void noEntityIsWrittenThroughABuilder() {
        // A Lombok @Builder writes each field through a builder method NAMED AFTER THE FIELD, not
        // through setX — so a @Builder entity would report every one of its columns as unwritable.
        // The house idiom is @Getter @Setter @NoArgsConstructor on all 432, and this is what makes
        // "no setX call site" mean "nothing writes it". Hard zero, and it cannot fail on correct
        // code because @Setter is always available.
        List<String> withBuilder = new ArrayList<>();
        for (Class<?> e : shippedEntities()) {
            for (var a : e.getAnnotations()) {
                if (a.annotationType().getName().equals("lombok.Builder")) {
                    withBuilder.add(e.getSimpleName());
                }
            }
            // Lombok is compile-time, so the annotation is usually gone from bytecode; the
            // structural tell that survives is a nested *Builder class.
            for (Class<?> n : e.getDeclaredClasses()) {
                if (n.getSimpleName().endsWith("Builder")) { withBuilder.add(e.getSimpleName()); }
            }
        }
        assertThat(withBuilder)
            .as("""
                A builder-written entity is invisible to this gate's setter scan and would report \
                all its columns as unwritable. Convert it to @Setter, or teach setterCallsByOwner \
                to resolve builder methods — do not leave it silently mis-measured.""")
            .isEmpty();
    }

    @Test
    @DisplayName("F2-257: an entity declaring both halves of the actor pair must have both written")
    void theActorPairIsAlwaysWrittenTogether() {
        // The claim that prevents drift is about WHERE the writes live (F2-245). Ten event/timeline
        // registers declared actor_user_id AND actor_name, every service wrote only the id, and
        // NOTHING in the platform wrote the name — so the half the response carried was the half
        // nobody filled in. This is a hard zero with no exemption map, and that is safe rather than
        // strict (F2-214): a register that does not want to record a display name should not declare
        // the column, and one that does has CurrentActor.username() available with no new module
        // edge. There is no correct code for it to fail on.
        // Asserted as the POSITIVE equality of two measured sets, not as the emptiness of their
        // difference. The mutation harness is the reason: `if (idWritten && !nameWritten)` with the
        // condition forced false SURVIVED, because on a fixed tree there is nothing left to collect
        // and an empty list is what both a correct gate and a dead one produce. Two sets that must
        // match cannot be satisfied by a collector that stops collecting.
        List<String> pairs = new ArrayList<>();
        List<String> idWritten = new ArrayList<>();
        List<String> nameWritten = new ArrayList<>();
        for (Class<?> entity : shippedEntities()) {
            Set<String> fields = ownFields(entity);
            if (!fields.contains("actorUserId") || !fields.contains("actorName")) { continue; }
            pairs.add(entity.getSimpleName());
            if (writtenOn(entity, "actorUserId")) { idWritten.add(entity.getSimpleName()); }
            if (writtenOn(entity, "actorName")) { nameWritten.add(entity.getSimpleName()); }
        }
        assertThat(pairs)
            .as("""
                anti-vacuity: the derived population. sys.columns says ten tables declare both \
                halves in dev and in prod, so a smaller number here means reflection or ownFields \
                stopped resolving and the hard zero below is a report about an empty set.""")
            .hasSizeGreaterThanOrEqualTo(10)
            .contains("AiLifecycleEvent", "CptyEvent", "DataclassEvent", "FraudCaseUpdate",
                      "ObEvent", "OutsourcingEvent", "RegInspectionUpdate", "RegReportEvent",
                      "RrpEvent", "ShariaEvent");
        assertThat(idWritten)
            .as("""
                anti-vacuity for the equality below: at least the ten measured registers must be \
                writing the id half, or the two sets match because both are empty.""")
            .hasSizeGreaterThanOrEqualTo(10);
        assertThat(nameWritten)
            .as("""
                a register that records WHICH user acted and never WHAT they are called publishes a \
                response field nothing can fill. Both halves come from the same primitive, so \
                writing one and not the other is not a decision anybody made — it is the id write \
                having been added and the name write never following it.""")
            .containsExactlyInAnyOrderElementsOf(idWritten);
    }

    @Test
    @DisplayName("F2-257: the ten registers take the name from the principal, and do not refuse on it")
    void theActorNameComesFromThePrincipalAndNeverRefuses() {
        // Two things this leg exists to stop, and neither is visible from the pair invariant above.
        //
        // (a) The name must come from the authenticated principal, never from a request body — that
        //     is F2-223's split attribution, which is the whole family this gate belongs to.
        // (b) It must be username(), NOT requireUsername(...). The id half is nullable and comes
        //     from the same primitive, so refusing on the name would make the two halves behave
        //     differently in a writer whose only job is to record a side effect: a timeline write
        //     that throws aborts the business operation it was recording (F2-191). Both null
        //     together is the property, and `ob_event` holds six live rows whose actor is a machine
        //     ('System (cert-monitor)', 'System (SIEM detection)') with no user id at all — so a
        //     register in this family legitimately has actorless events.
        Map<String, String> services = new TreeMap<>();
        services.put("ai/lifecycle", "AiLifecycleService");
        services.put("cpty", "CptyService");
        services.put("dataclass", "DataclassService");
        services.put("openbanking", "ObService");
        services.put("outsourcing", "OutsourcingService");
        services.put("reginspect", "RegInspectService");
        services.put("regreporting", "RegReportingService");
        services.put("rrp", "RrpService");
        services.put("sharia", "ShariaService");
        services.put("fraud", "FraudService");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : services.entrySet()) {
            String src = null;
            for (String s : mainSources()) {
                if (s.contains("class " + e.getValue() + " ")
                    || s.contains("class " + e.getValue() + "\n")
                    || s.contains("public class " + e.getValue())) {
                    if (s.contains("setActorName(")) { src = s; break; }
                }
            }
            if (src == null) { problems.add(e.getValue() + ": source not resolved"); continue; }
            String helper = methodBody(src, "currentActorName");
            if (helper == null) { problems.add(e.getValue() + ": no currentActorName() helper"); continue; }
            if (!helper.contains("CurrentActor.username()")) {
                problems.add(e.getValue() + ": the helper does not delegate to CurrentActor.username()");
            }
            if (helper.contains("requireUsername")) {
                problems.add(e.getValue() + ": refuses on the name half while the id half is nullable");
            }
            if (count(src, "setActorName\\s*\\(\\s*currentActorName\\s*\\(") == 0) {
                problems.add(e.getValue() + ": writes the name from something other than the helper");
            }
            if (count(src, "setActorName\\s*\\(\\s*(?:req|body|request)\\b") > 0) {
                problems.add(e.getValue() + ": takes the actor name from the request body");
            }
        }
        assertThat(problems).as("F2-257's fix shape, per register").isEmpty();
    }

    @Test
    @DisplayName("anti-vacuity: the scan resolves the whole entity population")
    void thePopulationIsLargeEnoughToBeReal() {
        // A floor has to sit above the largest WRONG answer, not above zero (F2-215). The wrong
        // answers here are "reflection resolved nothing" (0) and "the @RequestBody scan matched
        // nothing" (0) — either of which turns this gate into a report about an empty set while
        // still passing every assertion above.
        assertThat(shippedEntities()).as("entity reflection must resolve the platform's entities")
            .hasSizeGreaterThan(400);
        assertThat(entityBoundTypes()).as("the @RequestBody scan must resolve the bound types")
            .hasSizeGreaterThan(400);
        assertThat(setterCallsByOwner()).as("the bytecode scan must resolve setter call sites")
            .hasSizeGreaterThan(5_000);
        assertThat(unwritableActorColumns())
            .as("if this reaches zero, check the detector before believing the tree")
            .hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("the noise narrowing removes non-people and keeps people")
    void theNoiseNarrowingRemovesNonPeopleAndKeepsPeople() {
        for (String person : new String[]{
            "approverUserId", "reviewerName", "validatorUserId", "attestedBy", "closedBy",
            "approvedByLegalName", "collectorId", "snapshottedBy", "actorName", "ownerId"}) {
            assertThat(isPersonField(person))
                .as("must still count as a person: %s", person).isTrue();
        }
        for (String noise : new String[]{
            "referenceNumber", "merkleAnchor", "randomNumber", "incidentNumber", "sortOrder",
            "statusAfter", "scheduledFor", "isCrossBorder", "vendor", "vendorId", "issuer",
            "triggeredByEventId", "createdBy", "updatedBy", "deletedBy"}) {
            assertThat(isPersonField(noise))
                .as("must NOT count as a person: %s", noise).isFalse();
        }
    }

    @Test
    @DisplayName("blind spot, named rather than hidden: a clock filled by a FIELD INITIALISER")
    void anInitialiserStampedClockIsOutsideThisGateByConstruction() {
        // The clock half asks for an EXPLICIT server stamp — a setter call, resolved by owner, made
        // from a class that reads java.time. That is what makes it load-bearing (the mutation which
        // deleted the requirement altogether SURVIVED until it was resolved per owner). It also puts
        // F2-215's THIRD filling mechanism outside the population: `private LocalDateTime occurredAt
        // = LocalDateTime.now();` has no setter call anywhere, so the server owns WHEN with nothing
        // for this scan to find. F2-225/F2-226 measured that population at 82 entities / 87 act-clock
        // fields, so it is large and it is not measured here.
        //
        // FraudCaseUpdate is the exemplar and it is asserted in both directions, so the exclusion is
        // countable rather than invisible — a hard zero over a population an instrument cannot see is
        // the most reassuring possible artefact (F2-205).
        Class<?> fraud = fieldsOf("FraudCaseUpdate");
        Set<String> fields = ownFields(fraud);
        assertThat(fields).as("the exemplar still declares both halves")
            .contains("occurredAt", "actorName");
        assertThat(isPersonField("actorName")).isTrue();
        assertThat(writtenOn(fraud, "actorName"))
            .as("""
                F2-257 gave it a writer, so it is now out of the census for TWO independent reasons \
                and only one of them is this blind spot. The exemplar is kept because the CLOCK half \
                has not changed and that is what this leg is about — but note what the change means: \
                while the actor half was unwritten, the ONLY thing keeping this register out of the \
                census was the initialiser, and it was a real member of the family the whole time.""")
            .isTrue();
        assertThat(serverStampedClocks(fraud, fields))
            .as("""
                …and its clock is an initialiser, not a stamp, so this gate cannot see it. If this \
                ever becomes non-empty the entity gained an explicit stamp and the member belongs \
                on the WORK_LIST — do not delete this assertion, move the member.""")
            .isEmpty();
        assertThat(unwritableActorColumns())
            .as("so it is deliberately absent from the census")
            .doesNotContain("FraudCaseUpdate#actorName");
    }

    @Test
    @DisplayName("positive control: the detector fires on the literal pre-fix shape")
    void theDetectorFiresOnThePreFixShape() {
        // The shape F2-256 removed: a clock from the server's own reading, and no actor write.
        String preFix = """
            public Foo transitionRecertification(Long id, Req req) {
                Recertification c = loadRec(id);
                c.setStatus(req.toStatus());
                LocalDateTime now = LocalDateTime.now();
                switch (req.toStatus()) {
                    case "RECERTIFIED" -> { c.setDecidedAt(now); }
                }
            }
            """;
        assertThat(SplitAttributionContractTest.isServerClock(
                SplitAttributionContractTest.balancedArg(preFix, preFix.indexOf(".setDecidedAt(")),
                preFix))
            .as("""
                setDecidedAt(now) where now = LocalDateTime.now() must read as a SERVER clock — this \
                is the half that makes an unwritten actor column a finding rather than an unused \
                column, and if it stops resolving the whole census silently empties.""")
            .isTrue();
        assertThat(isPersonField("approverUserId"))
            .as("and the field this slice fixed must be recognised as a person")
            .isTrue();
    }

    // ────────────────────────────────────────────────────────────── helpers

    private static Class<?> fieldsOf(String simpleName) {
        return shippedEntities().stream()
            .filter(c -> c.getSimpleName().equals(simpleName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no shipped @Entity named " + simpleName));
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int count(String haystack, String regex) {
        var m = Pattern.compile(regex).matcher(haystack);
        int n = 0;
        while (m.find()) { n++; }
        return n;
    }

    /**
     * The brace-balanced body of a named method.
     *
     * <p>Sliced from the first {@code {} AFTER the signature — F2-250 recorded that a helper walking
     * to "the first brace" reads the NEXT method's body when the name it was given belongs to a
     * field initialiser, and F2-244 that a name-keyed lookup finds a CALL rather than a declaration.
     * Anchoring on {@code name(} preceded by a return type avoids both.
     */
    private static String methodBody(String code, String name) {
        var m = Pattern.compile("[\\w<>,.\\[\\]?]+\\s+" + Pattern.quote(name) + "\\s*\\([^)]*\\)\\s*\\{")
            .matcher(code);
        if (!m.find()) { throw new AssertionError("no declaration of " + name); }
        int open = code.indexOf('{', m.end() - 1);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') { depth++; } else if (c == '}') {
                depth--;
                if (depth == 0) { return code.substring(open, i + 1); }
            }
        }
        throw new AssertionError("unbalanced body for " + name);
    }
}
