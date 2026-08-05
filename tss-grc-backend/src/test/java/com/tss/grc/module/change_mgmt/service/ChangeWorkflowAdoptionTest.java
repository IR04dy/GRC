package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.dto.EngineRequestDtos;
import com.tss.grc.module.change_mgmt.spi.WorkflowPort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S8h / CE-10 — workflow-engine adoption, and the boundary that makes it safe.
 *
 * <p>What these tests are for: CE-10's own wording is "<i>change authorisations become workflow
 * requests</i>", and building that literally would have been the defect. The engine has no callback
 * into any source module and emits domain events only for SENT and ESCALATED, so an engine request
 * cannot move a change's status — but an {@code APPROVAL}-typed one would leave an approval-shaped
 * artefact in the audit trail, and S8f's register (shipped one slice earlier) would still, correctly,
 * report that change as {@code NO_AUTHORISATION_RECORD}. An auditor reconciling the two would find a
 * contradiction with no explanation.
 *
 * <p>So the tests here pin two claims the code makes and one it must never make:
 * <ol>
 *   <li>the port cannot raise an authorisation-shaped request;</li>
 *   <li>the register's authorisation routes stay at <b>two</b> and never consult the engine;</li>
 *   <li>reachability is computed by the <em>notifier's own</em> finders, because a check that does not
 *       mirror the notifier is answering a different question.</li>
 * </ol>
 */
class ChangeWorkflowAdoptionTest {

    private static final Path SRC = Path.of("src/main/java/com/tss/grc");
    private static final Path ADAPTER = SRC.resolve("module/change_adapters/WorkflowAdapter.java");
    private static final Path REGISTER = SRC.resolve("module/change_mgmt/service/UnauthorisedChangeRegisterService.java");
    private static final Path ENGINE = SRC.resolve("module/workflow/requests/service/WorkflowRequestService.java");
    private static final Path NOTIFICATIONS = SRC.resolve("module/platform/service/NotificationService.java");

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("missing source: " + p, e);
        }
    }

    /**
     * The file with every comment removed.
     *
     * <p>⚠ Load-bearing, and it was found the hard way. {@code reachMirrorsTheNotifier} originally
     * scanned the raw file, so when the role lookup was deliberately swapped for
     * {@code users.findAll()} the test still passed — the adapter's own javadoc names the correct
     * finder, and a comment satisfied an assertion about behaviour. A scan that a comment can satisfy
     * is a test that cannot fail.
     */
    private static String readCode(Path p) {
        return read(p)
            .replaceAll("(?s)/\\*.*?\\*/", " ")      // block comments and javadoc
            .replaceAll("(?m)//.*$", " ");           // line comments
    }

    /**
     * The body of one method, comments already stripped — from its signature to the next
     * member declaration at class indent.
     *
     * <p>⚠ Also load-bearing, and also found by mutation. Scanning the whole adapter for
     * {@code findActiveByOrganisationIdAndRoleName} still passed after {@code reachRole} was
     * sabotaged, because {@code candidates()} legitimately calls the same finder. "The file mentions
     * it" and "this method uses it" are different claims, and only the second is the design rule.
     */
    private static String methodBody(Path p, String signature) {
        String code = readCode(p);
        int start = code.indexOf(signature);
        assertThat(start).as("method not found, so the scan proves nothing: " + signature).isGreaterThan(0);
        Matcher next = Pattern.compile("\\n    (?:private|public|protected|static|@Override)")
            .matcher(code);
        int end = next.find(start + signature.length()) ? next.start() : code.length();
        return code.substring(start, end);
    }

    // ── 1. the port cannot authorise ─────────────────────────────────────────

    @Test
    @DisplayName("APPROVAL is not a raisable type, and the refusal is a named constant not a comment")
    void approvalIsRefused() {
        assertThat(WorkflowPort.REFUSED_TYPE).isEqualTo("APPROVAL");
        assertThat(WorkflowPort.RAISABLE_TYPES)
            .as("an APPROVAL-typed engine request would look like an authorisation and confer none")
            .doesNotContain("APPROVAL");
        assertThat(WorkflowPort.RAISABLE_TYPES).doesNotContain(WorkflowPort.REFUSED_TYPE);
    }

    @Test
    @DisplayName("the adapter refuses the type before it does anything else")
    void refusalPrecedesEverything() {
        String src = readCode(ADAPTER);
        int refusal = src.indexOf("REFUSED_TYPE.equals(type)");
        int reach = src.indexOf("Reach r = reach(addressee)");
        int create = src.indexOf("requests.create(dto)");
        assertThat(refusal).as("the refusal must exist in raise()").isGreaterThan(0);
        assertThat(refusal)
            .as("refusing after resolving recipients would leak an APPROVAL attempt into the reach query")
            .isLessThan(reach);
        assertThat(reach)
            .as("nothing may be created before reachability is known")
            .isLessThan(create);
    }

    @Test
    @DisplayName("every raisable type is one the engine actually understands")
    void raisableTypesAreEngineTypes() {
        // The engine's vocabulary, as documented on WorkflowRequest.requestType.
        List<String> engineTypes = List.of("EVIDENCE_REQUEST", "APPROVAL", "REVIEW", "TASK",
            "ACKNOWLEDGEMENT", "INFORMATION", "REPORT_DISTRIBUTION", "FOLLOW_UP", "CUSTOM");
        assertThat(engineTypes)
            .as("a type the engine does not know would be accepted here and rejected there")
            .containsAll(WorkflowPort.RAISABLE_TYPES);
    }

    // ── 2. authorisation routes stay at two ──────────────────────────────────

    @Test
    @DisplayName("the unauthorised-change register never consults the workflow engine")
    void registerDoesNotConsultTheEngine() {
        String src = readCode(REGISTER);
        assertThat(src)
            .as("""
                S8f's register unions exactly two authorisation routes: recorded change_approvals \
                votes, or a carried committee resolution. If the engine became a third, every \
                engine-authorised change would be reported NO_AUTHORISATION_RECORD — and if the \
                register instead started *accepting* engine requests, an APPROVAL-typed request would \
                silently authorise changes nobody voted on.""")
            .doesNotContain("WorkflowPort")
            .doesNotContain("WorkflowRequest")
            .doesNotContain("workflow.requests");
    }

    @Test
    @DisplayName("the register still names both of its two routes")
    void registerStillHasExactlyTwoRoutes() {
        String src = readCode(REGISTER);
        // Vote route and committee route. Asserting they are both PRESENT matters as much as
        // asserting the engine is absent: a test that only forbids would pass on an empty file.
        assertThat(src).contains("countApprovalsByChange");
        assertThat(src).contains("committeeAuthorised");
        assertThat(UnauthorisedChangeRegisterService.SCOPE)
            .isEqualTo("RECORDED_CHANGES_VS_AUTHORISATION_MATRIX");
    }

    // ── 3. reach mirrors the notifier ────────────────────────────────────────

    @Test
    @DisplayName("reach lives in the same class as the fan-out it predicts")
    void reachLivesWhereTheFanOutLives() {
        String engine = readCode(ENGINE);
        // The design claim, asserted structurally rather than by comparing two copies: notifyStep and
        // reachOf are in one file, so they cannot disagree about who a recipient reaches.
        assertThat(engine).contains("private void notifyStep(");
        assertThat(engine).contains("public RecipientReach reachOf(");
    }

    @Test
    @DisplayName("the change adapter only translates — it holds no resolution of its own")
    void theAdapterOnlyTranslates() {
        String src = readCode(ADAPTER);
        // ⚠ This is the shape ChangeModuleBoundaryTest forced. An adapter that injects the directories
        // would be re-deriving notifyStep's rules one package sideways, and a mirror maintained by
        // assertion is a mirror that eventually lies.
        assertThat(src)
            .doesNotContain("RoleRepository")
            .doesNotContain("UserRepository")
            .doesNotContain("DepartmentRepository")
            .doesNotContain("CommitteeRepository")
            .doesNotContain("CommitteeMemberTermRepository")
            .doesNotContain("WorkflowRequestRepository");
        assertThat(src)
            .as("reach must be delegated to the engine, not recomputed")
            .contains("requests.reachOf(");
    }

    @Test
    @DisplayName("the engine reach resolves each kind with the notifier own finder")
    void reachUsesTheNotifiersOwnFinders() {
        // Scoped to reachOf, not to the file: reachableAddressees legitimately calls the same finders,
        // so a whole-file scan passes even when the reach rule has been replaced. An earlier draft of
        // this test made exactly that mistake and a deliberate sabotage of the role lookup survived it.
        String reach = methodBody(ENGINE, "public RecipientReach reachOf(");
        assertThat(reach)
            .as("DEPARTMENT must resolve the way notifyStep does")
            .contains("findByOrganisationIdAndDepartmentIdAndIsActiveTrue");
        assertThat(reach)
            .as("COMMITTEE must resolve the way notifyStep does")
            .contains("findActiveByCommittee");
        assertThat(reach)
            .as("ROLE must go through NotificationService, which owns the role-name rule")
            .contains("notifications.roleAudience(");

        String notify = methodBody(ENGINE, "private void notifyStep(");
        assertThat(notify).contains("findByOrganisationIdAndDepartmentIdAndIsActiveTrue");
        assertThat(notify).contains("findActiveByCommittee");
        assertThat(notify).contains("broadcastToRole(");
    }

    @Test
    @DisplayName("the role predicate is shared, not copied")
    void rolePredicateIsShared() {
        String notif = readCode(NOTIFICATIONS);
        // broadcastToRoles and roleAudience must both ask visibleRoleNames what counts as a role in
        // this tenant. Two copies would drift the moment one of them learned about inactive roles —
        // and the dry run would then stop being a preview of anything.
        assertThat(notif).contains("private Set<String> visibleRoleNames(");
        assertThat(methodBody(NOTIFICATIONS, "public RoleFanout broadcastToRoles("))
            .contains("visibleRoleNames(organisationId)");
        assertThat(methodBody(NOTIFICATIONS, "public RoleAudience roleAudience("))
            .contains("visibleRoleNames(organisationId)");
        assertThat(methodBody(NOTIFICATIONS, "public RoleAudience roleAudience("))
            .as("the dry run must not send anything")
            .doesNotContain("notificationRepository.save");
    }

    @Test
    @DisplayName("an unknown role and a role held by nobody stay separate, in both vocabularies")
    void roleReachKeepsF294sDistinction() {
        String reach = methodBody(ENGINE, "public RecipientReach reachOf(");
        assertThat(reach).contains("ROLE_NOT_FOUND").contains("ROLE_HELD_BY_NOBODY");
        assertThat(WorkflowPort.REASONS).contains("ROLE_NOT_FOUND", "ROLE_HELD_BY_NOBODY");
    }

    @Test
    @DisplayName("every reason the adapter can produce is in the port's closed vocabulary")
    void reachReasonsAreClosed() {
        String src = readCode(ADAPTER);
        Matcher m = Pattern.compile("Reach\\.no\\(\"([A-Z_]+)\"\\)").matcher(src);
        List<String> produced = new ArrayList<>();
        while (m.find()) produced.add(m.group(1));
        assertThat(produced).as("the scan must find the reasons, or it is proving nothing").isNotEmpty();

        List<String> allowed = new ArrayList<>(WorkflowPort.REASONS);
        // UNRESOLVED belongs to label resolution rather than to an addressee, hence the separate name.
        allowed.add(WorkflowPort.UNRESOLVED);
        assertThat(allowed).containsAll(produced);
    }

    @Test
    @DisplayName("an unknown role and a role held by nobody stay separate reasons")
    void unknownAndUnheldAreNotMerged() {
        // F2-94 settled this for role fan-out: "a name that is not a role cannot be fixed by granting
        // anything". Merging them would tell an operator to appoint someone to a role that does
        // not exist.
        assertThat(WorkflowPort.REASONS).contains("ROLE_NOT_FOUND", "ROLE_HELD_BY_NOBODY");
        assertThat(WorkflowPort.REASONS).contains("DEPARTMENT_NOT_FOUND", "DEPARTMENT_HAS_NO_MEMBERS");
        assertThat(WorkflowPort.REASONS).contains("COMMITTEE_NOT_FOUND", "COMMITTEE_HAS_NO_LINKED_USERS");
    }

    @Test
    @DisplayName("EXTERNAL is not an addressee type for a change task")
    void externalIsNotAddressable() {
        assertThat(WorkflowPort.ADDRESSEE_TYPES)
            .as("an EXTERNAL recipient reaches a token holder, not a platform user; a change task "
              + "assigned to an email address is a different feature with its own consent and expiry")
            .containsExactly("USER", "ROLE", "DEPARTMENT", "COMMITTEE")
            .doesNotContain("EXTERNAL");
    }

    // ── the report ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("the addressability report covers open tasks, and says so in its own payload")
    void reportStatesItsScope() {
        // Same discipline as S8f's SCOPE and S8g's unscheduledCount: a count is not interpretable
        // without the population it was taken over.
        assertThat(ChangeWorkflowService.OPEN_TASK_STATUSES)
            .containsExactly("PENDING", "IN_PROGRESS", "BLOCKED");
        assertThat(ChangeWorkflowService.OPEN_TASK_STATUSES)
            .as("a DONE task's unreachable owner is not a live problem")
            .doesNotContain("DONE", "SKIPPED");
        assertThat(recordComponentNames(ChangeWorkflowService.Addressability.class))
            .contains("scope", "openTasks", "overdueOpen", "unnamed", "reachable", "unreachable",
                      "byReason", "engineAbsent");
    }

    @Test
    @DisplayName("open-task statuses are real ChangeTask statuses")
    void openStatusesAreRealStatuses() {
        // The entity documents its own vocabulary; drifting from it would silently empty the report.
        String entity = read(SRC.resolve("module/change_mgmt/entity/ChangeTask.java"));
        for (String s : ChangeWorkflowService.OPEN_TASK_STATUSES)
            assertThat(entity).as(s + " must be a status ChangeTask actually uses").contains(s);
    }

    @Test
    @DisplayName("an absent engine reports unknown, not clean")
    void absentEngineIsNotACleanReport() {
        String src = readCode(SRC.resolve("module/change_mgmt/service/ChangeWorkflowService.java"));
        assertThat(src)
            .as("filling reachable=0 without the engineAbsent flag would look like a measurement")
            .contains("ENGINE_UNAVAILABLE");
        int flag = src.indexOf("engineAbsent");
        assertThat(flag).isGreaterThan(0);
    }

    @Test
    @DisplayName("the report counts tasks, not distinct labels")
    void reportCountsTasks() {
        String src = readCode(SRC.resolve("module/change_mgmt/service/ChangeWorkflowService.java"));
        // One unreachable label carrying nine overdue tasks is nine people who were not told.
        assertThat(src).contains("reachable += e.getValue()[0]");
        assertThat(src).contains("unreachable += e.getValue()[0]");
    }

    @Test
    @DisplayName("the report is built over tasks with no due date too")
    void reportDoesNotRequireADueDate() {
        String repo = readCode(SRC.resolve("module/change_mgmt/repository/ChangeTaskRepository.java"));
        String svc = readCode(SRC.resolve("module/change_mgmt/service/ChangeWorkflowService.java"));
        assertThat(svc)
            .as("findOverdueOpen requires dueAt < now, which drops the 18 of 41 tasks nobody dated")
            .doesNotContain("findOverdueOpen");
        assertThat(svc).contains("findOpenByOrganisation");
        assertThat(repo).contains("findOpenByOrganisation");
    }

    // ── the DTO ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the raise DTO is narrow, and its SLA is bounded")
    void raiseDtoIsNarrowAndBounded() throws Exception {
        List<String> names = recordComponentNames(EngineRequestDtos.RaiseEngineRequest.class);
        assertThat(names)
            .as("the engine's own CreateRequest has 28 fields; a change task has one owner")
            .hasSize(12)
            .doesNotContain("recipients", "branchRules", "routingMode", "completionThreshold",
                            "expandCommittees", "recipientListId");

        // ⚠ @Min/@Max do not target RECORD_COMPONENT — reading the component's annotations returns
        // null and the assertion would pass while proving nothing. Read the accessor.
        Min min = EngineRequestDtos.RaiseEngineRequest.class
            .getDeclaredMethod("slaHours").getAnnotation(Min.class);
        Max max = EngineRequestDtos.RaiseEngineRequest.class
            .getDeclaredMethod("slaHours").getAnnotation(Max.class);
        assertThat(min).isNotNull();
        assertThat(max).isNotNull();
        assertThat(min.value()).isEqualTo(1);
        assertThat(max.value()).isEqualTo(720);
    }

    @Test
    @DisplayName("the DTO cannot express an approval")
    void dtoCannotExpressAnApproval() {
        String src = read(SRC.resolve("module/change_mgmt/dto/EngineRequestDtos.java"));
        assertThat(recordComponentNames(EngineRequestDtos.RaiseEngineRequest.class))
            .as("no approver/reviewer id: those are the engine's authorisation gates")
            .doesNotContain("approverId", "reviewerId");
        assertThat(src).contains("refuses the type");
    }

    // ── the engine side ──────────────────────────────────────────────────────

    @Test
    @DisplayName("CHANGE is registered for recipient suggestions with a role that exists")
    void changeIsRegisteredForSuggestions() {
        String engine = readCode(ENGINE);
        assertThat(engine)
            .as("without a CHANGE entry the composer proposes nothing for a change record")
            .contains("Map.entry(\"CHANGE\",");
        assertThat(engine)
            .as("CHANGE_MANAGER is the only change-ish role in iam.roles as measured 2026-08-05")
            .contains("Map.entry(\"CHANGE\",     List.of(\"CHANGE_MANAGER\"))");
        // CAB_CHAIR / CHANGE_OWNER / IT_OPERATIONS were measured absent; suggesting one would offer
        // an addressee the notifier can never resolve (F2-05/F2-52's shape).
        int changeEntry = engine.indexOf("Map.entry(\"CHANGE\",");
        String entry = engine.substring(changeEntry, engine.indexOf(')', changeEntry) + 1);
        assertThat(entry).doesNotContain("CAB_CHAIR").doesNotContain("CHANGE_OWNER")
                         .doesNotContain("IT_OPERATIONS");
    }

    @Test
    @DisplayName("the adapter anchors with the engine's own record-type spelling")
    void anchorsUseTheEngineSpelling() {
        String src = readCode(ADAPTER);
        // CamelCase, like the 14 existing adopters (Policy, Risk, Dsr) — not the evidence
        // subsystem's snake_case, which S8a matched for the same "follow the peer you are joining"
        // reason.
        assertThat(src).contains("\"ChangeRequest\"").contains("\"ChangeTask\"");
        assertThat(src).contains("SOURCE_MODULE = \"CHANGE\"");
    }

    @Test
    @DisplayName("a routed request always carries a deadline")
    void routingRequiresADeadline() {
        String raise = methodBody(ADAPTER, "public Optional<RequestRef> raise(");
        // ⚠ Found by live probe, not here: the engine rejects "a response is required" with no due
        // date, and that rule is right — the SLA clock, the reminders and the escalation ladder all
        // measure from a deadline. 18 of 41 seeded change tasks have no due_at, so this is the common
        // case rather than the edge one, and passing null through produced a 400 on the happy path.
        assertThat(raise)
            .as("a due date must be derived from slaHours when one was not given")
            .contains("Math.ceil(slaHours / 24.0)");
        assertThat(raise)
            .as("and refused outright when neither is available, rather than 400ing from the engine")
            .contains("A deadline is required to route work through the engine");
        assertThat(raise)
            .as("the derived date, not the argument, must reach the engine")
            .contains("due, slaHours,");
        assertThat(read(ENGINE))
            .as("if the engine ever drops this rule, the derivation above becomes dead weight "
              + "and this test should be revisited rather than silently kept")
            .contains("dueDate is required when a response is required");
    }

    @Test
    @DisplayName("a raised request is sent, not left in DRAFT")
    void raisedRequestsAreSent() {
        String src = readCode(ADAPTER);
        // A DRAFT request has no SLA clock and notifies nobody — it would be the dead button the
        // engine exists to replace.
        assertThat(src).contains("requests.send(created.id())");
    }

    private static List<String> recordComponentNames(Class<?> c) {
        return java.util.Arrays.stream(c.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).toList();
    }
}
