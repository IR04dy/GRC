package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-239 — the change implementation checklist: {@code { status: next }} must move the status and
 * nothing else, and it must be ACCEPTED.
 *
 * <p>Two defects met on this one route, and the second was holding the first shut.
 *
 * <ul>
 *   <li>{@code ChangeTask.sortOrder} was initialised to {@code 0}, so
 *       {@code if (body.getSortOrder() != null)} was true on every request and a status flip
 *       collapsed the task's position. The server ORDERS the checklist by that column.</li>
 *   <li>{@code ChangeTask.titleAr} is {@code @NotBlank} (F2-30) and the route bound the entity with
 *       {@code @Valid}, so the panel's one-key body was refused with a 400 before the service was
 *       entered. Probed live: neither "Add task" nor the status toggle had ever worked.</li>
 * </ul>
 *
 * <p>The {@code @Valid} half is asserted structurally by {@code NeverFiringNullGuardContractTest}
 * (a unit test cannot see a controller annotation). What is asserted here is the half a unit test
 * CAN see: the service is a partial merge, an omitted field is left alone, and a BLANK mandatory
 * field is still refused — which is the property F2-30 was protecting and the reason dropping
 * {@code @Valid} does not widen anything.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangeTaskPartialUpdateTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 2L;

    @Mock private ChangeTaskRepository taskRepo;
    @Mock private ChangeRequestRepository changeRepo;
    @Mock private AuditLogService audit;
    @InjectMocks private ChangeManagementService service;

    /** Both task routes start with {@code require(changeRequestId)} — the tenant scope check. */
    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        ChangeRequest parent = new ChangeRequest();
        parent.setId(CHANGE_ID);
        parent.setOrganisationId(ORG);
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(parent));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private ChangeTask stored() {
        ChangeTask t = new ChangeTask();
        t.setId(4L);
        t.setChangeRequestId(CHANGE_ID);
        t.setTitleEn("Cut over the primary region");
        t.setTitleAr("تحويل المنطقة الرئيسية");
        t.setOwnerName("Ops");
        t.setStatus("PENDING");
        t.setSortOrder(300);
        return t;
    }

    /** Exactly what {@code ChangeDetailPanel.flipTask} sends: one key. */
    private static ChangeTask flipBody(String to) {
        ChangeTask b = new ChangeTask();
        b.setStatus(to);
        return b;
    }

    @Test
    @DisplayName("a status flip leaves the task's position in the checklist alone")
    void statusFlipDoesNotCollapseSortOrder() {
        ChangeTask t = stored();
        when(taskRepo.findById(4L)).thenReturn(Optional.of(t));
        when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeTask saved = service.updateTask(4L, flipBody("IN_PROGRESS"));

        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getSortOrder())
            .as("tasks are returned OrderBySortOrderAscIdAsc, so a reset moves the flipped task to "
                + "the front of the implementation checklist — measured 41 of 41 dev rows and 40 of "
                + "41 prod rows carry a non-zero sort_order")
            .isEqualTo(300);
        assertThat(saved.getTitleAr())
            .as("and the Arabic title the caller never sent must survive — this route dropped @Valid "
                + "precisely so a partial body is allowed to omit it")
            .isEqualTo("تحويل المنطقة الرئيسية");
    }

    @Test
    @DisplayName("DONE still stamps completedAt, and only the first time")
    void doneStampsCompletionOnce() {
        ChangeTask t = stored();
        when(taskRepo.findById(4L)).thenReturn(Optional.of(t));
        when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeTask done = service.updateTask(4L, flipBody("DONE"));
        assertThat(done.getCompletedAt()).isNotNull();
        var first = done.getCompletedAt();
        service.updateTask(4L, flipBody("DONE"));
        assertThat(done.getCompletedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("a BLANK mandatory title is still refused — that is what F2-30's @NotBlank protected")
    void blankMandatoryTitleIsRefused() {
        ChangeTask t = stored();
        when(taskRepo.findById(4L)).thenReturn(Optional.of(t));

        ChangeTask body = new ChangeTask();
        body.setTitleAr("   ");
        assertThatThrownBy(() -> service.updateTask(4L, body))
            .as("OMITTING the Arabic title leaves the row's own alone; sending it BLANK is the thing "
                + "that must fail, and bean validation on the bound entity could not tell them apart")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("titleAr");
    }

    @Test
    @DisplayName("the create owns both defaults the entity stopped initialising")
    void addTaskSuppliesStatusAndSortOrder() {
        when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeTask bare = new ChangeTask();
        bare.setTitleEn("Snapshot the database");
        bare.setTitleAr("أخذ نسخة من قاعدة البيانات");
        ChangeTask saved = service.addTask(CHANGE_ID, bare);

        // Both columns are NOT NULL. If addTask stops setting one, the fix has turned a silent reset
        // into a 500 on create and ddl-auto=none means nothing else in the tree would notice.
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("a caller-supplied sort order on create is still honoured")
    void addTaskHonoursAnExplicitSortOrder() {
        when(taskRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeTask bare = new ChangeTask();
        bare.setTitleEn("Second step");
        bare.setTitleAr("الخطوة الثانية");
        bare.setSortOrder(20);
        assertThat(service.addTask(CHANGE_ID, bare).getSortOrder())
            .as("the default must not become an override — that would be the mirror defect")
            .isEqualTo(20);
    }
}
