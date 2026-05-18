import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Wrench, FileText, ChevronLeft, ChevronRight, History, GitCompare, Plus, ListOrdered } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listPlans, planStats, getPlan, createPlan, transitionPlan, reopenPlan,
  listSections, editSection,
  listVersions, diffVersions,
  planStatusTone,
  type BcpPlan, type BcpPlanType, type BcpPlanStatus, type BcpSection, type BcpVersion,
} from '../../lib/bcpPlans';

/**
 * BCP / DRP Builder page — audit T1.4b.
 *
 * <p>Two modes on one route:
 * <ul>
 *   <li><b>Register</b> — KPI strip + table of all plans with status badges
 *       and quick "Open builder" / lifecycle actions.</li>
 *   <li><b>Builder</b> — three tabs:
 *     <ol>
 *       <li><b>Sections</b> — left nav of section keys, right editor with
 *           EN + AR text panes. Saves on blur.</li>
 *       <li><b>Versions</b> — list of past snapshots with "Compare to v1"
 *           buttons that open the diff tab.</li>
 *       <li><b>Diff</b> — side-by-side JSON panes for two versions.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>Edits are blocked when the plan is APPROVED / PUBLISHED / ARCHIVED /
 * SUPERSEDED / CANCELLED — the backend enforces; the UI greys out controls.
 */
type Mode = 'register' | 'builder';
type BuilderTab = 'sections' | 'versions' | 'diff';

const NEXT_STATUS: Record<BcpPlanStatus, BcpPlanStatus[]> = {
  DRAFT:        ['UNDER_REVIEW', 'CANCELLED'],
  UNDER_REVIEW: ['APPROVED', 'DRAFT'],
  APPROVED:     ['PUBLISHED', 'DRAFT'],
  PUBLISHED:    ['ARCHIVED', 'SUPERSEDED'],
  ARCHIVED:     [],
  SUPERSEDED:   [],
  CANCELLED:    [],
};

export function BcpBuilderPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [mode, setMode] = useState<Mode>('register');
  const [planTypeFilter, setPlanTypeFilter] = useState<BcpPlanType | ''>('');
  const [items, setItems] = useState<BcpPlan[]>([]);
  const [stats, setStats] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);

  // Builder state
  const [plan, setPlan] = useState<BcpPlan | null>(null);
  const [sections, setSections] = useState<BcpSection[]>([]);
  const [activeSection, setActiveSection] = useState<BcpSection | null>(null);
  const [versions, setVersions] = useState<BcpVersion[]>([]);
  const [builderTab, setBuilderTab] = useState<BuilderTab>('sections');
  const [diffFrom, setDiffFrom] = useState<number | null>(null);
  const [diffTo, setDiffTo] = useState<number | null>(null);
  const [diffPayload, setDiffPayload] = useState<{ fromJson: string; toJson: string } | null>(null);

  // ── Register loaders ──
  async function reload() {
    setLoading(true);
    try {
      const [page, st] = await Promise.all([
        listPlans(planTypeFilter || undefined, 0, 200),
        planStats(),
      ]);
      setItems(page.content);
      setStats(st);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحميل' : 'Load failed'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [planTypeFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  // Honour ?openPlan=N from the BIA wizard's "Generate plan" navigation:
  // open the freshly-created plan in the builder so the author can start
  // filling sections immediately without an extra click.
  const [searchParams, setSearchParams] = useSearchParams();
  useEffect(() => {
    const id = Number(searchParams.get('openPlan'));
    if (Number.isFinite(id) && id > 0) {
      void openPlan(id);
      // Strip the query param so refresh doesn't re-trigger
      const next = new URLSearchParams(searchParams);
      next.delete('openPlan');
      setSearchParams(next, { replace: true });
    }
  }, [searchParams]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Builder loaders ──
  async function openPlan(id: number) {
    try {
      const [p, secs, vers] = await Promise.all([
        getPlan(id), listSections(id), listVersions(id),
      ]);
      setPlan(p);
      setSections(secs);
      setActiveSection(secs[0] ?? null);
      setVersions(vers);
      setBuilderTab('sections');
      setMode('builder');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  function backToRegister() {
    setMode('register');
    setPlan(null);
    setSections([]);
    setActiveSection(null);
    setVersions([]);
    setDiffPayload(null);
    void reload();
  }

  // ── Plan-level actions ──
  async function newPlan() {
    const type = (window.prompt(isAr ? 'النوع (BCP أو DRP)' : 'Type (BCP or DRP)', 'BCP') ?? '').toUpperCase();
    if (type !== 'BCP' && type !== 'DRP') {
      toast.warning(isAr ? 'النوع يجب أن يكون BCP أو DRP' : 'Type must be BCP or DRP'); return;
    }
    const title = window.prompt(isAr ? 'العنوان' : 'Title');
    if (!title) return;
    try {
      const created = await createPlan({ titleEn: title, planType: type as BcpPlanType });
      toast.success(isAr ? `تم إنشاء ${created.planCode}` : `${created.planCode} created`);
      await openPlan(created.id);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function moveStatus(to: BcpPlanStatus) {
    if (!plan) return;
    try {
      const out = await transitionPlan(plan.id, to);
      setPlan(out);
      // After APPROVED, refresh versions so the new snapshot appears
      if (to === 'APPROVED') setVersions(await listVersions(out.id));
      toast.success(isAr ? `الحالة → ${to}` : `Status → ${to}`);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function handleReopen() {
    if (!plan) return;
    try {
      const out = await reopenPlan(plan.id);
      setPlan(out);
      toast.success(isAr ? 'تم إعادة الفتح للتحرير' : 'Reopened for editing');
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  // ── Section editing ──
  async function saveSection(bodyEn: string, bodyAr: string) {
    if (!activeSection) return;
    try {
      const saved = await editSection(activeSection.id, bodyEn, bodyAr);
      setSections(sections.map(s => s.id === saved.id ? saved : s));
      setActiveSection(saved);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  // ── Diff ──
  async function loadDiff(from: number, to: number) {
    if (!plan) return;
    setDiffFrom(from); setDiffTo(to);
    try {
      const d = await diffVersions(plan.id, from, to);
      setDiffPayload({ fromJson: d.fromJson, toJson: d.toJson });
      setBuilderTab('diff');
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  const isLocked = plan != null && (plan.status === 'APPROVED' || plan.status === 'PUBLISHED'
    || plan.status === 'ARCHIVED' || plan.status === 'SUPERSEDED' || plan.status === 'CANCELLED');

  // ── Register mode ──
  if (mode === 'register') {
    return (
      <Shell>
        <PageHeader
          title={isAr ? 'محرر خطط الاستمرارية والتعافي' : 'BCP / DRP plan builder'}
          sub={isAr
            ? 'خطط متعددة الأقسام وفق ISO 22301 §8.4.2 مع نسخ موَّقع ومقارنة بين النسخ.'
            : 'Multi-section plans per ISO 22301 §8.4.2 with versioned snapshots and version diff.'}
          eyebrow={isAr ? 'استمرارية الأعمال' : 'Business Continuity'}
          actions={<Button variant="primary" onClick={() => void newPlan()}>
            <Plus size={14} /> {isAr ? 'خطة جديدة' : 'New plan'}
          </Button>}
        />

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(160px,1fr))', gap: 12, marginBottom: 12 }}>
          <Kpi Icon={FileText} label={isAr ? 'BCP'      : 'BCP plans'}   value={items.filter(p => p.planType === 'BCP').length} tone="info" />
          <Kpi Icon={FileText} label={isAr ? 'DRP'      : 'DRP plans'}   value={items.filter(p => p.planType === 'DRP').length} tone="info" />
          <Kpi Icon={Wrench}   label={isAr ? 'منشورة'   : 'Published'}   value={stats.status_published ?? 0}                   tone="success" />
          <Kpi Icon={History}  label={isAr ? 'في التحرير' : 'Draft'}     value={stats.status_draft ?? 0}                       tone="warning" />
        </div>

        <Card title={isAr ? 'الخطط' : 'Plans'} action={
          <select value={planTypeFilter} onChange={e => setPlanTypeFilter(e.target.value as BcpPlanType | '')}
                  style={{ padding: '4px 8px', borderRadius: 4, border: '1px solid var(--border)' }}>
            <option value="">{isAr ? 'كل الأنواع' : 'All types'}</option>
            <option value="BCP">BCP</option>
            <option value="DRP">DRP</option>
          </select>
        }>
          {loading && <div className="text-muted" style={{ fontSize: 11 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
          {items.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد خطط بعد — ابدأ بـ "خطة جديدة" أو من تحليل أثر معتمد عبر صفحة الـBIA' : 'No plans yet — click "New plan" or generate from an approved BIA on the BIA page'} />
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
                <thead><tr>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الكود' : 'Code'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'النوع' : 'Type'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'العنوان' : 'Title'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'الحالة' : 'Status'}</th>
                  <th style={{ textAlign: 'start' }}>{isAr ? 'النسخة' : 'Version'}</th>
                  <th style={{ textAlign: 'start' }}>RTO</th>
                  <th style={{ textAlign: 'start' }}>RPO</th>
                  <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr></thead>
                <tbody>
                  {items.map(p => (
                    <tr key={p.id}>
                      <td className="mono">{p.planCode}</td>
                      <td><Badge tone={p.planType === 'BCP' ? 'info' : 'primary'}>{p.planType}</Badge></td>
                      <td>{p.titleEn}</td>
                      <td><Badge tone={planStatusTone(p.status)}>{p.status}</Badge></td>
                      <td className="mono">v{p.currentVersion}</td>
                      <td className="mono">{p.rtoHours ?? '—'}h</td>
                      <td className="mono">{p.rpoHours ?? '—'}h</td>
                      <td style={{ textAlign: 'end' }}>
                        <Button variant="ghost" onClick={() => void openPlan(p.id)}>{isAr ? 'فتح' : 'Open'}</Button>
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

  // ── Builder mode ──
  if (!plan) return null;
  const tabs: TabItem<BuilderTab>[] = [
    { id: 'sections', label: isAr ? `أقسام (${sections.length})`  : `Sections (${sections.length})`,  Icon: ListOrdered },
    { id: 'versions', label: isAr ? `النسخ (${versions.length})`   : `Versions (${versions.length})`,   Icon: History },
    { id: 'diff',     label: isAr ? 'المقارنة'                     : 'Diff',                            Icon: GitCompare },
  ];

  return (
    <Shell>
      <PageHeader
        title={`${plan.planCode} — ${plan.titleEn}`}
        sub={`${plan.planType} · ${isAr ? 'النسخة' : 'version'} v${plan.currentVersion} · RTO ${plan.rtoHours ?? '?'}h · RPO ${plan.rpoHours ?? '?'}h`}
        eyebrow={isAr ? 'محرر الخطة' : 'Plan builder'}
        actions={<>
          <Button variant="ghost" onClick={backToRegister}>
            <ChevronLeft size={14} /> {isAr ? 'عودة للسجل' : 'Back to register'}
          </Button>
          {(plan.status === 'APPROVED' || plan.status === 'UNDER_REVIEW') && (
            <Button variant="ghost" onClick={() => void handleReopen()}>
              {isAr ? 'إعادة فتح للتحرير' : 'Reopen for editing'}
            </Button>
          )}
          {NEXT_STATUS[plan.status].map(s => (
            <Button key={s} variant={s === 'CANCELLED' ? 'danger' : 'primary'} onClick={() => void moveStatus(s)}>
              {s}
            </Button>
          ))}
        </>}
      />

      <div style={{ marginBottom: 12, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <Badge tone={planStatusTone(plan.status)}>{plan.status}</Badge>
        {isLocked && (
          <span className="text-muted" style={{ fontSize: 12 }}>
            {isAr ? 'الخطة مقفلة. أعد الفتح للتحرير.' : 'Plan is locked. Reopen to edit.'}
          </span>
        )}
      </div>

      <Tabs items={tabs} active={builderTab} onChange={setBuilderTab} />

      <div style={{ marginTop: 12 }}>
        {builderTab === 'sections' && (
          <div style={{ display: 'grid', gridTemplateColumns: '240px 1fr', gap: 12 }}>
            <Card title={isAr ? 'الأقسام' : 'Sections'}>
              {sections.length === 0
                ? <InlineEmpty text={isAr ? 'لا أقسام' : 'No sections'} />
                : <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: 2 }}>
                    {sections.map(s => (
                      <li key={s.id}>
                        <button onClick={() => setActiveSection(s)} style={{
                          width: '100%', textAlign: 'start',
                          padding: '6px 10px', borderRadius: 4, cursor: 'pointer',
                          background: activeSection?.id === s.id ? 'var(--surface-2)' : 'transparent',
                          border: '1px solid var(--border)', fontSize: 12,
                        }}>
                          <div style={{ fontWeight: 500 }}>{isAr ? (s.sectionTitleAr ?? s.sectionTitleEn) : s.sectionTitleEn}</div>
                          <div className="text-muted mono" style={{ fontSize: 10 }}>{s.sectionKey}</div>
                        </button>
                      </li>
                    ))}
                  </ul>}
            </Card>
            <Card title={activeSection ? (isAr ? activeSection.sectionTitleAr : activeSection.sectionTitleEn) ?? '?' : '?'}>
              {!activeSection ? <InlineEmpty text={isAr ? 'اختر قسماً' : 'Pick a section'} /> : (
                <SectionEditor section={activeSection} onSave={saveSection} locked={isLocked} isAr={isAr} />
              )}
            </Card>
          </div>
        )}

        {builderTab === 'versions' && (
          <Card title={isAr ? 'سجل النسخ' : 'Version history'}>
            {versions.length === 0
              ? <InlineEmpty text={isAr ? 'لا توجد نسخ معتمدة بعد. اعتمد الخطة لتسجيل نسخة.' : 'No approved versions yet. Approve the plan to record one.'} />
              : <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
                  <thead><tr>
                    <th>{isAr ? 'النسخة' : 'Version'}</th>
                    <th>{isAr ? 'تاريخ الاعتماد' : 'Approved at'}</th>
                    <th>{isAr ? 'بواسطة' : 'By'}</th>
                    <th>{isAr ? 'ملخص' : 'Summary'}</th>
                    <th style={{ textAlign: 'end' }}>{isAr ? 'مقارنة' : 'Compare'}</th>
                  </tr></thead>
                  <tbody>
                    {versions.map(v => (
                      <tr key={v.id}>
                        <td className="mono">v{v.versionNumber}</td>
                        <td className="mono">{v.approvedAt?.slice(0, 16) ?? '—'}</td>
                        <td>{v.approvedBy ?? '—'}</td>
                        <td>{v.changeSummary ?? '—'}</td>
                        <td style={{ textAlign: 'end' }}>
                          {versions.length > 1 && v.versionNumber !== versions[versions.length - 1].versionNumber && (
                            <Button variant="ghost"
                                    onClick={() => void loadDiff(versions[versions.length - 1].versionNumber, v.versionNumber)}>
                              <GitCompare size={12} /> {isAr ? `قارن بـ v${versions[versions.length - 1].versionNumber}` : `vs v${versions[versions.length - 1].versionNumber}`}
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
          </Card>
        )}

        {builderTab === 'diff' && (
          <Card title={diffFrom != null && diffTo != null ? `${isAr ? 'مقارنة' : 'Diff'} v${diffFrom} ↔ v${diffTo}` : (isAr ? 'مقارنة' : 'Diff')}>
            {!diffPayload
              ? <InlineEmpty text={isAr ? 'اختر نسختين من تبويب النسخ.' : 'Pick two versions on the Versions tab.'} />
              : <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>v{diffFrom}</div>
                    <pre style={preStyle}>{diffPayload.fromJson}</pre>
                  </div>
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>v{diffTo}</div>
                    <pre style={preStyle}>{diffPayload.toJson}</pre>
                  </div>
                </div>}
          </Card>
        )}
      </div>
    </Shell>
  );
}

// ─── Section editor (controlled, saves on blur) ──────────────

function SectionEditor({ section, onSave, locked, isAr }: {
  section: BcpSection;
  onSave: (bodyEn: string, bodyAr: string) => void;
  locked: boolean;
  isAr: boolean;
}) {
  const [bodyEn, setBodyEn] = useState(section.bodyEn ?? '');
  const [bodyAr, setBodyAr] = useState(section.bodyAr ?? '');
  // Reset when navigating to a different section
  useMemo(() => { setBodyEn(section.bodyEn ?? ''); setBodyAr(section.bodyAr ?? ''); }, [section.id]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div style={{ display: 'grid', gap: 10 }}>
      <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
        <span className="text-muted">{isAr ? 'النص (إنجليزي)' : 'Body (EN)'}</span>
        <textarea value={bodyEn} disabled={locked}
                  onChange={e => setBodyEn(e.target.value)}
                  onBlur={() => onSave(bodyEn, bodyAr)}
                  style={{ ...textareaStyle, minHeight: 180 }}
                  placeholder={isAr ? 'اكتب محتوى القسم هنا. يدعم Markdown البسيط.' : 'Write the section body here. Simple markdown supported.'} />
      </label>
      <label style={{ display: 'grid', gap: 4, fontSize: 12 }}>
        <span className="text-muted">{isAr ? 'النص (عربي)' : 'Body (AR)'}</span>
        <textarea value={bodyAr} disabled={locked}
                  onChange={e => setBodyAr(e.target.value)}
                  onBlur={() => onSave(bodyEn, bodyAr)}
                  style={{ ...textareaStyle, minHeight: 180, direction: 'rtl' }} />
      </label>
      {section.lastEditedBy && (
        <div className="text-muted" style={{ fontSize: 11 }}>
          {isAr ? 'آخر تعديل' : 'Last edited'}: <span className="mono">{section.lastEditedAt?.slice(0, 16)}</span> {isAr ? 'بواسطة' : 'by'} {section.lastEditedBy}
        </div>
      )}
    </div>
  );
}

const textareaStyle: React.CSSProperties = {
  padding: '10px 12px', border: '1px solid var(--border)', borderRadius: 4,
  fontSize: 13, fontFamily: 'inherit', background: 'var(--surface)', width: '100%',
  lineHeight: 1.6, resize: 'vertical',
};

const preStyle: React.CSSProperties = {
  background: 'var(--surface-2)', borderRadius: 4, padding: 10,
  fontSize: 11, lineHeight: 1.5, overflow: 'auto', maxHeight: 600,
  fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
};
