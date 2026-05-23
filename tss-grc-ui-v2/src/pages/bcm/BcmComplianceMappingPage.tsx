import { useEffect, useMemo, useState } from 'react';
import { ShieldCheck, AlertOctagon, FileCheck2, BookOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listCompliance, coverageByFramework, overallStats,
  assess, attachEvidence, attachGap,
  FRAMEWORK_LABELS, statusTone,
  type BcmComplianceMapping, type Framework, type ComplianceStatus,
} from '../../lib/bcmCompliance';

/**
 * BCM Compliance Mapping page — audit Tier-1 #5.
 *
 * <p>Closes spec §P and audit-Journey-D ("Compliance Officer maps BCM
 * activities to ISO 22301") which previously had 0 ✅ / 5 🔴.
 *
 * <p>UI:
 * <ul>
 *   <li>Coverage strip per framework (compliant / partial / non-compliant /
 *       not-assessed counts as progress bars).</li>
 *   <li>Framework filter dropdown above the table.</li>
 *   <li>Table of requirements with inline assess + attach evidence/gap actions.</li>
 *   <li>PLACEHOLDER banner when any row is still flagged is_placeholder.</li>
 * </ul>
 */
const FRAMEWORKS: Framework[] = ['ISO_22301', 'SAMA_OPRES', 'DGA', 'NCEMA', 'INTERNAL', 'CUSTOM'];

const STATUS_OPTIONS: { value: ComplianceStatus; label: string; labelAr: string }[] = [
  { value: 'NOT_ASSESSED',  label: 'Not assessed',  labelAr: 'لم يُقيَّم' },
  { value: 'COMPLIANT',     label: 'Compliant',     labelAr: 'ممتثل' },
  { value: 'PARTIAL',       label: 'Partial',       labelAr: 'جزئي' },
  { value: 'NON_COMPLIANT', label: 'Non-compliant', labelAr: 'غير ممتثل' },
  { value: 'NOT_APPLICABLE',label: 'N/A',           labelAr: 'لا ينطبق' },
];

export function BcmComplianceMappingPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [filter, setFilter] = useState<Framework | ''>('');
  const [items, setItems] = useState<BcmComplianceMapping[]>([]);
  const [coverage, setCoverage] = useState<Record<string, Record<string, number>>>({});
  const [stats, setStats] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);

  async function reload() {
    setLoading(true);
    // Per-call .catch() so a partial backend failure (e.g. missing-table 503
    // from the global handler in PR #117) doesn't blank the whole page.
    // Each panel degrades independently to its empty default.
    const [list, cov, st] = await Promise.all([
      listCompliance(filter || undefined).catch(() => [] as BcmComplianceMapping[]),
      coverageByFramework().catch(() => ({} as Record<string, Record<string, number>>)),
      overallStats().catch(() => ({} as Record<string, number>)),
    ]);
    setItems(list);
    setCoverage(cov);
    setStats(st);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [filter]); // eslint-disable-line react-hooks/exhaustive-deps

  const hasPlaceholders = useMemo(() => items.some(i => i.isPlaceholder), [items]);

  async function doAssess(m: BcmComplianceMapping) {
    const status = window.prompt(
      isAr ? 'الحالة (COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_APPLICABLE)' : 'Status (COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_APPLICABLE)',
      m.complianceStatus) as ComplianceStatus | null;
    if (!status) return;
    const reviewer = window.prompt(isAr ? 'المراجع' : 'Reviewer name') ?? '';
    const notes    = window.prompt(isAr ? 'ملاحظات (اختياري)' : 'Notes (optional)') ?? '';
    try {
      await assess(m.id, status, reviewer, notes);
      toast.success(isAr ? 'تم التقييم' : 'Assessed');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function doAttachEvidence(m: BcmComplianceMapping) {
    const raw = window.prompt(isAr ? 'رقم سجل الدليل' : 'Evidence record id');
    if (!raw) return;
    const evidenceId = Number(raw);
    if (!Number.isFinite(evidenceId) || evidenceId <= 0) {
      toast.warning(isAr ? 'رقم غير صالح' : 'Invalid id'); return;
    }
    try { await attachEvidence(m.id, evidenceId); toast.success(isAr ? 'تم الربط' : 'Attached'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function doAttachGap(m: BcmComplianceMapping) {
    const raw = window.prompt(isAr ? 'رقم الفجوة' : 'Gap id');
    if (!raw) return;
    const gapId = Number(raw);
    if (!Number.isFinite(gapId) || gapId <= 0) {
      toast.warning(isAr ? 'رقم غير صالح' : 'Invalid id'); return;
    }
    try { await attachGap(m.id, gapId); toast.success(isAr ? 'تم الربط' : 'Attached'); await reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'مطابقة الامتثال للاستمرارية' : 'BCM compliance mapping'}
        sub={isAr
          ? 'ربط أنشطة BCM بمتطلبات ISO 22301 / SAMA OPRES / DGA / NCEMA مع تقييم لكل بند.'
          : 'Link BCM activities to ISO 22301 / SAMA OPRES / DGA / NCEMA requirements with per-clause assessment.'}
        eyebrow={isAr ? 'الامتثال' : 'Compliance'}
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 12, marginBottom: 14 }}>
        <Kpi Icon={BookOpen}    label={isAr ? 'إجمالي البنود'   : 'Total requirements'} value={items.length}             tone="info" />
        <Kpi Icon={FileCheck2}  label={isAr ? 'ممتثل'           : 'Compliant'}          value={stats.compliant ?? 0}      tone="success" />
        <Kpi Icon={ShieldCheck} label={isAr ? 'جزئي'            : 'Partial'}            value={stats.partial ?? 0}        tone="warning" />
        <Kpi Icon={AlertOctagon} label={isAr ? 'غير ممتثل'      : 'Non-compliant'}      value={stats.non_compliant ?? 0}  tone="danger" />
      </div>

      {hasPlaceholders && (
        <div style={{ background: 'var(--surface-2)', border: '1px solid var(--warning, #c47e00)', borderRadius: 6,
                      padding: '8px 12px', marginBottom: 12, fontSize: 12 }}>
          <Badge tone="warning">
            {isAr
              ? 'بعض البنود مؤقتة — تتطلب استيراد المكتبة الرسمية للضوابط'
              : 'Some rows are placeholder — replace with official regulatory control library import'}
          </Badge>
        </div>
      )}

      <Card title={isAr ? 'التغطية حسب الإطار' : 'Coverage by framework'}>
        {Object.keys(coverage).length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد بيانات تغطية' : 'No coverage data'} />
        ) : (
          <div style={{ display: 'grid', gap: 10 }}>
            {Object.entries(coverage).map(([fw, row]) => {
              const total = Object.values(row).reduce((a, b) => a + b, 0);
              const compl = row.compliant ?? 0;
              const pct = total > 0 ? Math.round(100 * compl / total) : 0;
              return (
                <div key={fw} style={{ fontSize: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <strong>{isAr ? FRAMEWORK_LABELS[fw as Framework].ar : FRAMEWORK_LABELS[fw as Framework].en}</strong>
                    <span className="mono">{pct}% compliant ({compl}/{total})</span>
                  </div>
                  <div style={{ display: 'flex', gap: 2, marginTop: 4, height: 10, background: 'var(--surface-2)', borderRadius: 3 }}>
                    <div style={{ width: `${pctOf(row, total, 'compliant')}%`,      background: 'var(--success, #2e9c5e)' }} />
                    <div style={{ width: `${pctOf(row, total, 'partial')}%`,        background: 'var(--warning, #c47e00)' }} />
                    <div style={{ width: `${pctOf(row, total, 'non_compliant')}%`,  background: 'var(--danger, #c8362c)' }} />
                    <div style={{ width: `${pctOf(row, total, 'not_assessed')}%`,   background: 'var(--info, #3a80c8)' }} />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      <div style={{ height: 12 }} />

      <Card title={isAr ? 'متطلبات الامتثال' : 'Requirements'}
            action={<select value={filter} onChange={e => setFilter(e.target.value as Framework | '')}
                            style={{ padding: '4px 8px', borderRadius: 4, border: '1px solid var(--border)' }}>
              <option value="">{isAr ? 'كل الأطر' : 'All frameworks'}</option>
              {FRAMEWORKS.map(f => <option key={f} value={f}>{isAr ? FRAMEWORK_LABELS[f].ar : FRAMEWORK_LABELS[f].en}</option>)}
            </select>}>
        {loading && <div className="text-muted" style={{ fontSize: 11 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {items.length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد متطلبات' : 'No requirements'} />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', fontSize: 12 }}>
              <thead>
                <tr>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الإطار' : 'Framework'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الكود' : 'Code'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'العنوان' : 'Title'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'سير العمل' : 'Workflow'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'دليل' : 'Evidence'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'فجوة' : 'Gap'}</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(m => (
                  <tr key={m.id}>
                    <td><Badge tone="info">{m.sourceFramework}</Badge></td>
                    <td className="mono">{m.requirementCode}</td>
                    <td>
                      {isAr ? (m.requirementTitleAr ?? m.requirementTitleEn) : m.requirementTitleEn}
                      {m.isPlaceholder && <span style={{ marginInlineStart: 4 }}><Badge tone="warning">placeholder</Badge></span>}
                    </td>
                    <td>{m.relatedWorkflow ?? '—'}</td>
                    <td><Badge tone={statusTone(m.complianceStatus)}>
                      {STATUS_OPTIONS.find(s => s.value === m.complianceStatus)?.[isAr ? 'labelAr' : 'label']}
                    </Badge></td>
                    <td>{m.evidenceId ? <span className="mono">#{m.evidenceId}</span> : <span className="text-muted">—</span>}</td>
                    <td>{m.gapId      ? <span className="mono">#{m.gapId}</span>      : <span className="text-muted">—</span>}</td>
                    <td style={{ textAlign: 'end' }}>
                      <Button variant="ghost" onClick={() => void doAssess(m)}>{isAr ? 'تقييم' : 'Assess'}</Button>
                      <Button variant="ghost" onClick={() => void doAttachEvidence(m)}>{isAr ? 'ربط دليل' : 'Evidence'}</Button>
                      <Button variant="ghost" onClick={() => void doAttachGap(m)}>{isAr ? 'ربط فجوة' : 'Gap'}</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Shell>
  );
}

function pctOf(row: Record<string, number>, total: number, key: string): number {
  if (total === 0) return 0;
  return Math.round(100 * (row[key] ?? 0) / total);
}
