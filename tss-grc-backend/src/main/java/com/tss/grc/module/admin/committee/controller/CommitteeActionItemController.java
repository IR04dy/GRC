package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.service.CommitteeActionItemService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/committees/action-items")
@RequiredArgsConstructor
@RequireModule("BOARD")
public class CommitteeActionItemController {

    private final CommitteeActionItemService service;

    @GetMapping
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Page<CommitteeActionItem> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public CommitteeActionItem get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/by-meeting/{meetingId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeActionItem> byMeeting(@PathVariable Long meetingId) {
        return service.listByMeeting(meetingId);
    }

    @GetMapping("/by-committee/{committeeId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeActionItem> byCommittee(@PathVariable Long committeeId) {
        return service.listByCommittee(committeeId);
    }

    /** Open + in-progress + overdue actions for one committee. Used by
     *  the agenda auto-populator to pre-fill the "matters arising"
     *  section of the next meeting. */
    @GetMapping("/open-for-committee/{committeeId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeActionItem> openForCommittee(@PathVariable Long committeeId) {
        return service.openActionsForCommittee(committeeId);
    }

    /** Open actions assigned to a specific user — for the user's inbox. */
    @GetMapping("/open-for-owner/{ownerUserId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeActionItem> openForOwner(@PathVariable Long ownerUserId) {
        return service.openActionsForOwner(ownerUserId);
    }

    @PostMapping
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeActionItem create(@RequestBody CommitteeActionItem in) {
        return service.create(in);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeActionItem start(@PathVariable Long id) {
        return service.startProgress(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeActionItem complete(@PathVariable Long id,
                                         @RequestBody CompleteRequest req) {
        return service.complete(id, req.completionNotesEn(), req.completedBy());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeActionItem cancel(@PathVariable Long id, @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }

    @PostMapping("/{id}/defer")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeActionItem defer(@PathVariable Long id, @RequestBody DeferRequest req) {
        return service.defer(id, req.newDueDate(), req.reason());
    }

    public record CompleteRequest(String completionNotesEn, Long completedBy) {}
    public record CancelRequest(String reason) {}
    public record DeferRequest(LocalDate newDueDate, String reason) {}
}
