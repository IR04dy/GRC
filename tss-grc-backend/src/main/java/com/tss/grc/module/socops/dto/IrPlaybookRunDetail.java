package com.tss.grc.module.socops.dto;

import com.tss.grc.module.socops.entity.IrPlaybookRun;
import com.tss.grc.module.socops.entity.IrPlaybookRunTask;

import java.util.List;

/**
 * A playbook run together with its ordered tasks — the shape the frontend needs
 * to render a run and let an analyst complete tasks (epic SOC-B). Returned by
 * invoke, get-run, and every task transition so the client always gets the
 * up-to-date run header + checklist in one round trip.
 */
public record IrPlaybookRunDetail(IrPlaybookRun run, List<IrPlaybookRunTask> tasks) {}
