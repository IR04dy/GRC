package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.dto.ChangeCommentRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeComment;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeCommentRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-248 — the forged-author test for the <b>change-comment</b> path, and the twin
 * {@link CabVoteProvenanceTest} never got.
 *
 * <h2>What was wrong</h2>
 *
 * <p>{@code POST /api/v1/changes/{id}/comments} bound the {@link ChangeComment} entity, so
 * {@code authorName} and {@code authorId} arrived from the caller and the service wrote whatever
 * they contained. In front of that sat {@code if (getAuthorName() == null)
 * setAuthorName("Unknown")} — a <b>default, not a guard</b> (F2-218): it discarded nothing a caller
 * sent and supplied a label only when the caller stayed silent.
 *
 * <p>Probed live as {@code ranalyst} (a {@code RISK_ANALYST} — the least-privileged holder of
 * {@code CHANGE:WRITE}), against a planted change request:
 * <pre>
 *   POST .../comments {"authorName":"so_officer", "body":"..."}   → 201, stored 'so_officer'
 *   POST .../comments {"authorId":8, ...}                          → 201, stored author_id = 8
 *   POST .../comments {"body":"..."}                               → 201, stored 'Unknown'
 *   POST .../comments {"authorName":"You", ...}                    → 201, stored 'You'
 * </pre>
 * {@code author_id} was NULL on <b>45 of 45</b> dev rows and <b>46 of 46</b> prod rows — nothing had
 * ever written it — and the prod register holds a row authored by the literal string {@code 'You'},
 * which is the frontend's {@code getUser()?.username ?? 'You'} fallback having fired for real,
 * under a comment claiming it had been removed.
 *
 * <h2>🔑 Why this test did not exist, and the lesson</h2>
 *
 * <p>{@link CabVoteProvenanceTest} is DF-4's forged-actor test for the CAB-approval path in this
 * same service, and {@code recordApproval} has since resolved {@code approverId} from the principal
 * and used it as the CM-C2 segregation input <i>"which the caller cannot choose"</i>.
 * {@code create} captures {@code requestedById} through {@link ChangeSoD} for the same reason
 * (CE-15/F2-112). <b>The comment thread beside them was the one people-bearing column in the module
 * nobody swept</b> — F2-215's two-of-N — and {@code ChangeSoD}'s own javadoc states the rule it
 * needed: <i>"the id is the part a gate can stand on, and only the server can know it."</i>
 *
 * <p>There was <b>no test of {@code addComment} at all</b>, of any kind. A path with no test is not
 * a path someone decided was safe.
 *
 * <h2>Why the assertions are shaped like this</h2>
 *
 * <p>The strongest one is a <b>compile-time</b> fact rather than a runtime assertion:
 * {@link ChangeCommentRequest} declares only {@code body}, so the forgery cannot be expressed by a
 * caller and the pre-fix scenario is <i>unwritable</i> in this test. That is F2-233's closure — a
 * field the bound type does not declare can never arrive — and it is why
 * {@link #theRequestTypeCannotCarryAnAuthorAtAll()} reflects over the record's components instead of
 * trusting the reader.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeCommentProvenanceTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 700L;
    private static final long COMMENTER_UID = 5L;      // ranalyst, the probe's persona
    private static final String COMMENTER = "ranalyst";

    @Mock ChangeRequestRepository  changeRepo;
    @Mock ChangeApprovalRepository approvalRepo;
    @Mock ChangeTaskRepository     taskRepo;
    @Mock ChangeLinkRepository     linkRepo;
    @Mock ChangeCommentRepository  commentRepo;
    @Mock AuditLogService          audit;
    @Mock ApplicationEventPublisher publisher;

    @InjectMocks ChangeManagementService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change()));
        when(commentRepo.save(any(ChangeComment.class))).thenAnswer(i -> i.getArgument(0));
        authAs(COMMENTER, COMMENTER_UID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private ChangeRequest change() {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-0700");
        c.setStatus("CAB_APPROVED");
        c.setChangeType("NORMAL");
        return c;
    }

    /**
     * {@link ChangeSoD#actor()} resolves a {@link GrcUserDetails} principal; a bare username token
     * would leave the id null and the id assertions would be about nothing (the F2-223 idiom).
     */
    private static void authAs(String username, Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new GrcUserDetails(userId, ORG, username, "x", true, true, List.of()),
                "x", List.of()));
    }

    @Test
    @DisplayName("both halves of the authorship come from the authenticated principal")
    void theAuthorIsTheActingPrincipal() {
        ChangeComment saved = service.addComment(CHANGE_ID,
            new ChangeCommentRequest("The DR rollback was rehearsed and signed off."));

        assertThat(saved.getAuthorName()).isEqualTo(COMMENTER);
        assertThat(saved.getAuthorId()).isEqualTo(COMMENTER_UID);
    }

    /**
     * The structural closure, asserted rather than assumed.
     *
     * <p>F2-233's rule: a field the bound type does not declare can never arrive. If somebody
     * re-adds {@code authorName} to this record — or reverts the controller to binding the entity —
     * the forgery becomes expressible again, and no runtime assertion above would notice, because a
     * caller-supplied author that happens to equal the principal's name looks correct.
     */
    @Test
    @DisplayName("the request type cannot carry an author at all")
    void theRequestTypeCannotCarryAnAuthorAtAll() {
        List<String> components = java.util.Arrays.stream(
                ChangeCommentRequest.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(components)
            .as("ChangeCommentRequest may declare only the comment text. An author field here is "
              + "how a RISK_ANALYST stored a CAB comment as the CISO.")
            .containsExactly("body");
    }

    @Test
    @DisplayName("the literal 'Unknown' default is gone — an omitted author is resolved, not labelled")
    void anOmittedAuthorIsResolvedRatherThanLabelled() {
        ChangeComment saved = service.addComment(CHANGE_ID, new ChangeCommentRequest("no author sent"));

        assertThat(saved.getAuthorName())
            .as("'Unknown' was a default, not a guard, and it still reads as an attribution")
            .isNotEqualTo("Unknown")
            .isEqualTo(COMMENTER);
    }

    /**
     * F2-236 — refusing is deliberately louder than recording a blank. A comment whose author is
     * {@code ""} still reads as a record of who wrote it, which is worse than one that could not be
     * written.
     */
    @Test
    @DisplayName("with no principal to record, the comment is refused rather than left blank")
    void refusesWhenThereIsNoPrincipal() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.addComment(CHANGE_ID, new ChangeCommentRequest("orphan")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no identified author");

        verify(commentRepo, never()).save(any());
    }

    /** The refusal that already worked must keep working — the fix must not widen what is accepted. */
    @Test
    @DisplayName("a blank body is still refused")
    void aBlankBodyIsStillRefused() {
        assertThatThrownBy(() -> service.addComment(CHANGE_ID, new ChangeCommentRequest("   ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("body");

        verify(commentRepo, never()).save(any());
    }

    /**
     * F2-223 — the trail must read off the SAVED ROW, never the request.
     *
     * <p>Pre-fix the audit row contradicted itself: {@code platform.audit_log.user_username} held
     * the real actor while the message this line composes said <i>"by You"</i>. The framework
     * already knew who acted; the module's own message disagreed with the column beside it.
     */
    @Test
    @DisplayName("the audit message names the resolved author and its user id")
    void theAuditTrailNamesTheResolvedAuthor() {
        service.addComment(CHANGE_ID, new ChangeCommentRequest("body text"));

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(anyString(), anyString(), anyLong(), msg.capture());

        assertThat(msg.getValue())
            .contains(COMMENTER)
            .contains("user#" + COMMENTER_UID);
    }

    /** The comment is attached to the change named in the path, not one named in the body. */
    @Test
    @DisplayName("the comment is bound to the path's change request")
    void theCommentIsBoundToThePathChange() {
        assertThat(service.addComment(CHANGE_ID, new ChangeCommentRequest("x")).getChangeRequestId())
            .isEqualTo(CHANGE_ID);
    }
}
