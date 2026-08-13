import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Filter as FilterIcon, UserCheck, X as XIcon } from 'lucide-react';
import { type ColumnDef } from '@tanstack/react-table';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { DataTableV2 } from '../../components/ui/data-table';
import {
  FilterBuilder, useFilterState, type FieldDef,
} from '../../components/ui/filter';
import { RecordPicker } from '../../components/ui/RecordPicker';
import { SavedViewSelector } from '../../components/ui/SavedViewSelector';
import { BulkActionsBar } from '../../components/ui/BulkActionsBar';
import { BarChart, PieChart } from '../../components/charts';
import {
  loadVulns, transitionVuln, hydrateVulns, vulnsLoadFailed, nextVulnStatuses,
  vulnStatusLabel, vulnStatusTone, ALL_VULN_STATUSES,
  type Vulnerability, type VulnSeverity, type VulnStatus,
} from '../../lib/cyberLifecycle';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';

/**
 * Cyber Vulnerabilities — pilot page wiring the full Sprint 4 stack:
 *   - DataTableV2 (TanStack-backed: sort, paginate, virtualize, select)
 *   - FilterBuilder (multi-condition AND/OR with operators)
 *   - SavedViewSelector (per-page persisted view configurations)
 *   - BulkActionsBar (multi-row triage / assign)
 *   - RecordPicker (assign-to-user via the generic registry)
 *   - Recharts (severity breakdown — drill-down to filtered list)
 *
 * This page is the reference implementation. Other 30+ list pages migrate
 * by following the same pattern.
 *
 * ⚠⚠ F2-205 — which is exactly why its data source mattered more than most. Every one of those
 * six mechanisms was real and wired, and the rows they operated on were SIX HARD-CODED
 * `Vulnerability` literals in `lib/cyberLifecycle.ts`, while `cyber_lc.vulnerabilities` held 36
 * real rows in dev and prod behind a complete guarded controller with no client at all. A page
 * whose own comment nominates it as the pattern to copy teaches whatever it does, including this.
 *
 * 🔑 The sharpest part was the realtime subscription below, and it is a shape no instrument in
 * this tree could see. `VulnerabilityService.transition` really does call
 * `broadcaster.broadcastEntityUpdate(orgId, "vulnerability", …)`, which publishes to
 * `/topic/org.{orgId}.changed.vulnerability`; this page really did subscribe to exactly that
 * topic. The wire was live, correct and end-to-end — and its handler re-read `localStorage`. So
 * another user's real transition arrived here as a websocket frame that triggered a refresh of a
 * browser constant. **A live server wire terminating in a constant reads, from either end, as
 * an integration that works**: the backend sees a subscriber, the frontend sees a handler firing.
 */
export function VulnerabilitiesV2Page() {
  const { lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [allVulns, setAllVulns] = useState<Vulnerability[]>([]);
  const [activeViewId, setActiveViewId] = useState<string | undefined>();
  const [filterOpen, setFilterOpen] = useState(false);

  useEffect(() => {
    void hydrateVulns().then(() => setAllVulns(loadVulns()));
  }, []);

  // Sprint 4 §2.7 — subscribe to live vulnerability transitions on this org.
  // The backend broadcaster emits to /topic/org.{orgId}.changed.vulnerability
  // on every transition, so any list page viewing the same data stays in sync
  // without polling. Details below in lib/useRealtimeEntity.ts.
  //
  // ⚠⚠ F2-205: this handler was `setAllVulns(loadVulns())` — and `loadVulns()` read
  // `localStorage`. The subscription was correct, the topic matched, the frame arrived, and the
  // response was to re-read a constant that no server event could ever have changed. It now
  // re-fetches (`force` — a broadcast means the server's copy has moved, so the cached promise
  // must not be reused), which is the only reading under which the subscription means anything.
  useRealtimeOrgEvent('changed.vulnerability', () => {
    void hydrateVulns(true).then(() => setAllVulns(loadVulns()));
  });

  // ─── Filter state ────────────────────────────────────────────────
  const filterFields: FieldDef[] = useMemo(() => [
    { key: 'titleEn',  label: lang === 'ar' ? 'العنوان' : 'Title',     type: 'string' },
    { key: 'severity', label: lang === 'ar' ? 'الخطورة' : 'Severity',  type: 'select',
      options: [
        { value: 'CRITICAL', label: 'CRITICAL' },
        { value: 'HIGH',     label: 'HIGH' },
        { value: 'MEDIUM',   label: 'MEDIUM' },
        { value: 'LOW',      label: 'LOW' },
      ]},
    // ⚠⚠ F2-205: a FIFTH restatement of the vocabulary lived here, and it had the same two
    // omissions as the other four — six options, no RISK_ACCEPTED and no FALSE_POSITIVE. So even
    // once the rows were real, 7 of the 36 could not have been filtered for: an analyst asking
    // "show me everything whose risk we have accepted" had no way to ask it. Derived from the
    // machine now, so a state cannot exist and be unfilterable.
    { key: 'status',   label: lang === 'ar' ? 'الحالة' : 'Status',     type: 'select',
      options: ALL_VULN_STATUSES.map(s => ({ value: s, label: vulnStatusLabel(s, lang) })) },
    { key: 'cvssScore', label: 'CVSS',                                 type: 'number' },
    { key: 'detectedAt', label: lang === 'ar' ? 'تاريخ الاكتشاف' : 'Detected', type: 'date' },
  ], [lang]);

  const { group, setGroup, predicate, conditionCount } = useFilterState();

  const filtered = useMemo(() => allVulns.filter(predicate), [allVulns, predicate]);

  // ─── Table columns ───────────────────────────────────────────────
  const columns: ColumnDef<Vulnerability>[] = useMemo(() => [
    {
      id: 'sel',
      header: ({ table }) => (
        <input
          type="checkbox"
          checked={table.getIsAllRowsSelected()}
          onChange={table.getToggleAllRowsSelectedHandler()}
        />
      ),
      cell: ({ row }) => (
        <input
          type="checkbox"
          checked={row.getIsSelected()}
          onChange={row.getToggleSelectedHandler()}
          onClick={(e) => e.stopPropagation()}
        />
      ),
      enableSorting: false,
      size: 36,
    },
    { id: 'id',       accessorKey: 'id',       header: 'ID' },
    { id: 'title',    header: lang === 'ar' ? 'العنوان' : 'Title',
      accessorFn: (r) => lang === 'ar' ? r.titleAr : r.titleEn },
    { id: 'severity', accessorKey: 'severity',
      header: lang === 'ar' ? 'الخطورة' : 'Severity',
      cell: ({ getValue }) => <SeverityBadge sev={getValue() as VulnSeverity} /> },
    { id: 'cvss',     accessorKey: 'cvssScore', header: 'CVSS' },
    // ⚠ F2-205: this column had no cell renderer, so it printed the raw token — and the browser
    // check is what surfaced it: an Arabic-primary UI showing `RISK_ACCEPTED`. It mattered less
    // when the rows were six invented ones with familiar-looking statuses; it matters now that
    // eight real ECC states reach the screen, two of which no reader has seen before. House rule
    // 7 (user-visible records are bilingual) and the labels already exist — read through the
    // total function, never `MAP[status][lang]` (F2-203 found three TypeErrors of that shape).
    { id: 'status',   accessorKey: 'status',
      header: lang === 'ar' ? 'الحالة' : 'Status',
      cell: ({ getValue }) => {
        const st = getValue() as string;
        return <Badge tone={vulnStatusTone(st)}>{vulnStatusLabel(st, lang)}</Badge>;
      } },
    { id: 'detected', accessorKey: 'detectedAt',
      header: lang === 'ar' ? 'الاكتشاف' : 'Detected',
      cell: ({ getValue }) => new Date(getValue() as string).toLocaleDateString(lang) },
  ], [lang]);

  // ─── Selection state for BulkActions ─────────────────────────────
  const [selectedRows, setSelectedRows] = useState<Vulnerability[]>([]);

  // ─── Charts (severity breakdown for drill-down demo) ─────────────
  const severityCounts = useMemo(() => {
    const c: Record<VulnSeverity, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
    filtered.forEach(v => { c[v.severity] = (c[v.severity] ?? 0) + 1; });
    return [
      { name: 'CRITICAL', value: c.CRITICAL, color: '#dc2626' },
      { name: 'HIGH',     value: c.HIGH,     color: '#f59e0b' },
      { name: 'MEDIUM',   value: c.MEDIUM,   color: '#2563eb' },
      { name: 'LOW',      value: c.LOW,      color: '#16a34a' },
    ].filter(d => d.value > 0);
  }, [filtered]);

  // ─── Bulk actions ────────────────────────────────────────────────
  /**
   * Triage every selected row the machine permits it for, one server call each.
   *
   * ⚠⚠ F2-205. Three things were wrong with the version this replaces, and they compound:
   *
   *   1. `transitionVuln` wrote to `localStorage`, so the whole action persisted nothing — an
   *      analyst triaged forty findings, read "Triaged 40", and reloaded to find none of them
   *      triaged.
   *   2. `r.status === 'NEW'` is a second opinion about the machine. It happens to agree here,
   *      and would silently stop agreeing the moment TRIAGED became reachable from anywhere
   *      else. It asks `nextVulnStatuses` now.
   *   3. The toast counted INTENTIONS. Nothing could fail, so `ok` was just "how many rows I
   *      looped over" — which is the reporting-success-without-acting shape F2-201 found on the
   *      approvals page. Against a real server a row CAN be refused (409 from a concurrent
   *      change, 403 mid-session), so the count now reports what the server actually did, and a
   *      failure is named rather than folded into "skipped".
   *
   * Sequential rather than `Promise.all` on purpose: these are governance transitions that each
   * write an audit row and publish a domain event, and a burst of forty parallel writes to one
   * register is how ROWVERSION contention gets discovered in production.
   */
  const bulkTriage = async (rows: Vulnerability[]) => {
    let done = 0, skipped = 0, failed = 0;
    for (const r of rows) {
      if (!nextVulnStatuses(r.status).includes('TRIAGED')) { skipped++; continue; }
      try {
        await transitionVuln(r.id, 'TRIAGED');
        done++;
      } catch (e) {
        failed++;
        console.warn('[vulns] triage refused for', r.id, e instanceof ApiError ? e.message : e);
      }
    }
    setAllVulns(loadVulns());
    setSelectedRows([]);
    const msg = lang === 'ar'
      ? `تم فرز ${done}${skipped ? ` · تم تجاوز ${skipped}` : ''}${failed ? ` · فشل ${failed}` : ''}`
      : `Triaged ${done}${skipped ? ` · ${skipped} not triageable` : ''}${failed ? ` · ${failed} refused` : ''}`;
    if (failed) toast.danger(msg); else toast.success(msg);
  };

  // ─── Saved-view snapshot/apply ───────────────────────────────────
  const onSnapshot = () => ({ name: '', filter: group, sort: [], visibleColumns: [] });
  const onApplyView = (v: any) => {
    if (v.filter) setGroup(v.filter);
    setActiveViewId(v.id);
  };

  return (
    <Shell title={lang === 'ar' ? 'الثغرات (الإصدار الثاني)' : 'Vulnerabilities (V2)'} sub="industry-standard GRC tooling parity stack">
      <PageHeader
        title={lang === 'ar' ? 'الثغرات (الإصدار الثاني)' : 'Vulnerabilities (V2)'}
        sub={lang === 'ar' ? 'قائمة TanStack · مكافئة لنظام industry-standard GRC tooling' : 'TanStack-powered list · industry-standard GRC tooling parity stack'}
      />

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
        <Card title={lang === 'ar' ? 'حسب الخطورة' : 'By severity'}>
          <PieChart
            data={severityCounts}
            innerRadius={50}
            onPointClick={(d) => {
              setGroup({
                id: 'root', combinator: 'AND',
                conditions: [{ id: 'auto', field: 'severity', type: 'select', op: 'eq', value: d.name }],
              });
            }}
            height={220}
          />
        </Card>
        <Card title={lang === 'ar' ? 'حسب الحالة' : 'By status'}>
          <BarChart
            data={statusCounts(filtered, lang)}
            xKey="status"
            series={[{ key: 'count', name: lang === 'ar' ? 'العدد' : 'Count' }]}
            height={220}
          />
        </Card>
      </div>

      {/* F2-205: a load failure is stated, not shown as an empty register. An empty list on a
          page whose data used to be six constants is exactly the "absence reads as health"
          shape F2-197 closed one level up. */}
      {vulnsLoadFailed() && (
        <div role="alert" style={{ display: 'flex', gap: 12, alignItems: 'center', padding: '10px 14px', marginBottom: 12, border: '1px solid var(--danger)', borderRadius: 6, fontSize: 13 }}>
          <span>
            {lang === 'ar'
              ? 'تعذّر تحميل سجل الثغرات من الخادم. القائمة أدناه ليست السجل.'
              : 'The vulnerability register could not be loaded from the server. The list below is not the register.'}
          </span>
          <button type="button"
            onClick={() => { void hydrateVulns(true).then(() => setAllVulns(loadVulns())); }}
            style={{ padding: '4px 10px', border: '1px solid var(--border)', borderRadius: 6, background: 'var(--surface-1)', cursor: 'pointer', fontSize: 12 }}>
            {lang === 'ar' ? 'إعادة المحاولة' : 'Retry'}
          </button>
        </div>
      )}

      <Card title={lang === 'ar' ? 'القائمة' : 'List'}>
        {/* Toolbar */}
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12, flexWrap: 'wrap' }}>
          <button
            type="button"
            onClick={() => setFilterOpen(o => !o)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', border: '1px solid var(--border)', borderRadius: 6, background: 'var(--surface-1)', cursor: 'pointer', fontSize: 12 }}
          >
            <FilterIcon size={14} />
            <span>{lang === 'ar' ? 'تصفية' : 'Filter'}</span>
            {conditionCount > 0 && <Badge tone="primary">{conditionCount}</Badge>}
          </button>

          <SavedViewSelector
            pageKey="cyber/vulns"
            activeViewId={activeViewId}
            onApply={onApplyView}
            onSnapshot={onSnapshot}
          />

          <RecordPicker entity="user" placeholder={lang === 'ar' ? 'مسنَدة لـ' : 'Assignee'} size="sm" />

          <span style={{ marginInlineStart: 'auto', fontSize: 12, color: 'var(--ink-3)' }}>
            {filtered.length} / {allVulns.length}
          </span>
        </div>

        {filterOpen && (
          <div style={{ marginBottom: 12 }}>
            <FilterBuilder fields={filterFields} group={group} onChange={setGroup} />
          </div>
        )}

        <DataTableV2
          data={filtered}
          columns={columns}
          getRowId={(v) => v.id}
          enableSelection
          onSelectionChange={setSelectedRows}
          pageSize={25}
          virtualization={{ enabled: filtered.length > 500 }}
          emptyState={lang === 'ar' ? 'لا توجد ثغرات' : 'No vulnerabilities'}
          onRowClick={(v) => navigate(`/cyber-lifecycle?tab=vulns&vulnId=${encodeURIComponent(v.id)}`)}
        />
      </Card>

      <BulkActionsBar
        selected={selectedRows}
        onClear={() => setSelectedRows([])}
        actions={[
          {
            id: 'triage', label: lang === 'ar' ? 'فرز الجديدة' : 'Triage NEW',
            icon: <UserCheck size={14} />,
            onClick: bulkTriage,
            // ⚠ F2-205: this read `r.status === 'NEW'`, and the new gate caught it AFTER I had
            // already fixed `bulkTriage` itself to ask the machine. Leaving it meant the button's
            // enabled state and the action's behaviour were two separate opinions about the same
            // question — which is the defect this slice is about, reintroduced one line from the
            // fix. 🔑 A surface has more than one place that consults a status, and the one that
            // decides whether a control is OFFERED is easy to miss because it is not the one that
            // does the work.
            disabled: (rows) => !rows.some(r => nextVulnStatuses(r.status).includes('TRIAGED')),
          },
          {
            id: 'clear', label: lang === 'ar' ? 'إلغاء' : 'Clear',
            icon: <XIcon size={14} />,
            onClick: () => setSelectedRows([]),
          },
        ]}
      />
    </Shell>
  );
}

/**
 * The "by status" chart's categories.
 *
 * ⚠ F2-205: this returned the raw token as the axis label, so the chart read `RISK_ACCEPTED` in an
 * Arabic-primary UI — the same gap as the table column, found the same way (by looking at the
 * page). It also ordered categories by first appearance in the filtered rows, i.e. by whatever the
 * server happened to return first, which is F2-197's "declaration order dressed up as meaning".
 * Ordered by the machine now, and a status with no rows is simply absent rather than a zero bar.
 */
function statusCounts(rows: Vulnerability[], lang: 'en' | 'ar') {
  const c: Record<string, number> = {};
  rows.forEach(r => { c[r.status] = (c[r.status] ?? 0) + 1; });
  return ALL_VULN_STATUSES
    .filter(s => c[s])
    .map(s => ({ status: vulnStatusLabel(s, lang), count: c[s] }));
}

function SeverityBadge({ sev }: { sev: VulnStatus | VulnSeverity | string }) {
  const tone: BadgeTone = sev === 'CRITICAL' ? 'danger' : sev === 'HIGH' ? 'warning' : sev === 'MEDIUM' ? 'info' : 'success';
  return <Badge tone={tone}>{sev}</Badge>;
}
