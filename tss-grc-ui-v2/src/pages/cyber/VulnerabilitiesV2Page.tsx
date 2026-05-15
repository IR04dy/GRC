import { useEffect, useMemo, useState } from 'react';
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
  loadVulns, transitionVuln, type Vulnerability, type VulnSeverity, type VulnStatus,
} from '../../lib/cyberLifecycle';
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
 */
export function VulnerabilitiesV2Page() {
  const { lang } = useI18n();
  const toast = useToast();
  const [allVulns, setAllVulns] = useState<Vulnerability[]>([]);
  const [activeViewId, setActiveViewId] = useState<string | undefined>();
  const [filterOpen, setFilterOpen] = useState(false);

  useEffect(() => {
    setAllVulns(loadVulns());
  }, []);

  // Sprint 4 §2.7 — subscribe to live vulnerability transitions on this org.
  // The backend broadcaster emits to /topic/org.{orgId}.changed.vulnerability
  // on every transition, so any list page viewing the same data stays in sync
  // without polling. Details below in lib/useRealtimeEntity.ts.
  useRealtimeOrgEvent('changed.vulnerability', () => {
    setAllVulns(loadVulns());
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
    { key: 'status',   label: lang === 'ar' ? 'الحالة' : 'Status',     type: 'select',
      options: [
        { value: 'NEW',                   label: 'NEW' },
        { value: 'TRIAGED',               label: 'TRIAGED' },
        { value: 'OWNER_ASSIGNED',        label: 'OWNER_ASSIGNED' },
        { value: 'IN_REMEDIATION',        label: 'IN_REMEDIATION' },
        { value: 'AWAITING_VERIFICATION', label: 'AWAITING_VERIFICATION' },
        { value: 'CLOSED',                label: 'CLOSED' },
      ]},
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
    { id: 'status',   accessorKey: 'status',
      header: lang === 'ar' ? 'الحالة' : 'Status' },
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
  const bulkTriage = (rows: Vulnerability[]) => {
    let ok = 0, skip = 0;
    rows.forEach(r => {
      if (r.status === 'NEW') {
        transitionVuln(r.id, 'TRIAGED'); ok++;
      } else { skip++; }
    });
    setAllVulns(loadVulns());
    setSelectedRows([]);
    toast.success(lang === 'ar'
      ? `تم فرز ${ok} (تجاوز ${skip} غير الجديدة)`
      : `Triaged ${ok} (skipped ${skip} not in NEW)`);
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
            data={statusCounts(filtered)}
            xKey="status"
            series={[{ key: 'count', name: lang === 'ar' ? 'العدد' : 'Count' }]}
            height={220}
          />
        </Card>
      </div>

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
          pageSize={25}
          virtualization={{ enabled: filtered.length > 500 }}
          emptyState={lang === 'ar' ? 'لا توجد ثغرات' : 'No vulnerabilities'}
          onRowClick={(v) => console.log('row click', v.id)}
        />
      </Card>

      <BulkActionsBar
        selected={selectedRows /* TODO: wire from DataTableV2 selection state */}
        onClear={() => setSelectedRows([])}
        actions={[
          {
            id: 'triage', label: lang === 'ar' ? 'فرز الجديدة' : 'Triage NEW',
            icon: <UserCheck size={14} />,
            onClick: bulkTriage,
            disabled: (rows) => !rows.some(r => r.status === 'NEW'),
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

function statusCounts(rows: Vulnerability[]) {
  const c: Record<string, number> = {};
  rows.forEach(r => { c[r.status] = (c[r.status] ?? 0) + 1; });
  return Object.entries(c).map(([status, count]) => ({ status, count }));
}

function SeverityBadge({ sev }: { sev: VulnStatus | VulnSeverity | string }) {
  const tone: BadgeTone = sev === 'CRITICAL' ? 'danger' : sev === 'HIGH' ? 'warning' : sev === 'MEDIUM' ? 'info' : 'success';
  return <Badge tone={tone}>{sev}</Badge>;
}
