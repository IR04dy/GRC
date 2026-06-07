import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Wrench, FileText, ChevronLeft, History, GitCompare, Plus, ListOrdered, Send, Download, BookOpen, Trash2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listPlans, planStats, getPlan, createPlan, transitionPlan, reopenPlan,
  listSections, editSection, addSection,
  listVersions, diffVersions,
  planStatusTone, downloadPlanPdf,
  type BcpPlan, type BcpPlanType, type BcpPlanStatus, type BcpSection, type BcpVersion,
} from '../../lib/bcpPlans';
import {
  listDistribution, distributionStats, distribute, acknowledge, decline,
  ackStatusTone,
  type PlanAcknowledgement, type DistributionStats,
} from '../../lib/planDistribution';
import {
  attestPlan, reviewStateTone, sweepReview,
} from '../../lib/planReview';
import {
  generateBinder, listBindersForPlan, downloadBinder, archiveBinder,
  statusTone as binderStatusTone, languageLabel, fmtBytes,
  type BinderExport, type BinderLanguage,
} from '../../lib/bcpBinder';
import '../../styles/pages/bcp-builder.css';

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
type BuilderTab = 'sections' | 'versions' | 'diff' | 'distribution' | 'binder';

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

  // "New plan" modal — replaces the native window.prompt flow so the
  // user gets a proper in-app form (planType + bilingual title + optional
  // recovery targets) rather than the browser's pop-up.
  const [newOpen, setNewOpen] = useState(false);

  // Builder state
  const [plan, setPlan] = useState<BcpPlan | null>(null);
  const [sections, setSections] = useState<BcpSection[]>([]);
  const [activeSection, setActiveSection] = useState<BcpSection | null>(null);
  const [versions, setVersions] = useState<BcpVersion[]>([]);
  const [builderTab, setBuilderTab] = useState<BuilderTab>('sections');
  const [diffFrom, setDiffFrom] = useState<number | null>(null);
  const [diffTo, setDiffTo] = useState<number | null>(null);
  const [diffPayload, setDiffPayload] = useState<{ fromJson: string; toJson: string } | null>(null);

  // Distribution (Tier-2 #11) — recipients + ack stats for the currently
  // open plan. Loaded lazily on first switch to the Distribution tab so
  // the section-editor flow doesn't pay a 2nd network round trip.
  const [acks, setAcks] = useState<PlanAcknowledgement[]>([]);
  const [distStats, setDistStats] = useState<DistributionStats | null>(null);
  const [distOpen, setDistOpen] = useState(false);  // distribute-modal

  // Slice 10-F — offline BCP binder PDFs. The list is loaded lazily on
  // first switch to the Binder tab. binderOpen drives the language-picker
  // modal; binderBusy gates the submit button while the (synchronous)
  // generate API call is in flight.
  const [binders, setBinders] = useState<BinderExport[]>([]);
  const [bindersLoaded, setBindersLoaded] = useState(false);
  const [binderOpen, setBinderOpen] = useState(false);
  const [binderBusy, setBinderBusy] = useState(false);

  // Ad-hoc section authoring — lets a plan owner add a bespoke ISO 22301
  // §8.4.2 section beyond the canonical catalogue (e.g. a vendor-specific
  // dependency or a regulator-requested annex) without leaving the builder.
  // Backend blocks this on locked plans, so the trigger is gated on !isLocked.
  // Memoised initial keeps a stable identity so FormModal's open-reset effect
  // (deps [open, initial]) doesn't wipe typed input on a parent re-render.
  const [openSection, setOpenSection] = useState(false);
  const sectionInitial = useMemo(() => emptySection(), []);

  // ── Register loaders ──
  async function reload() {
    setLoading(true);
    // Per-call .catch() — list and stats should fail independently so the
    // plan list still renders if the stats aggregator hiccups (and vice versa).
    const [page, st] = await Promise.all([
      listPlans(planTypeFilter || undefined, 0, 200).catch(() => ({ content: [] as BcpPlan[], totalElements: 0 })),
      planStats().catch(() => ({} as Record<string, number>)),
    ]);
    setItems(page.content);
    setStats(st);
    setLoading(false);
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
  // The "+ New plan" button now opens an in-app modal (see <FormModal/> at
  // the bottom of register mode). This handler runs on submit.
  async function submitNewPlan(v: NewPlanForm) {
    const planType = (v.planType || 'BCP') as BcpPlanType;
    const titleEn = (v.titleEn ?? '').trim();
    if (!titleEn) {
      toast.warning(isAr ? 'العنوان الإنجليزي مطلوب' : 'English title is required');
      return;
    }
    try {
      const payload: Partial<BcpPlan> = {
        planType,
        titleEn,
        titleAr: v.titleAr?.trim() || undefined,
        rtoHours:  v.rtoHours  ? Number(v.rtoHours)  : undefined,
        rpoHours:  v.rpoHours  ? Number(v.rpoHours)  : undefined,
        mtpdHours: v.mtpdHours ? Number(v.mtpdHours) : undefined,
        responseTeamRole: v.responseTeamRole?.trim() || undefined,
      };
      const created = await createPlan(payload);
      toast.success(isAr ? `تم إنشاء ${created.planCode}` : `${created.planCode} created`);
      setNewOpen(false);
      await openPlan(created.id);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء الخطة' : 'Plan create failed'));
    }
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

  // Add an ad-hoc section. Mirrors the backend's validation (key + EN title
  // required; key unique per plan; order auto-computed when blank) so the
  // user gets an inline message before the round-trip. On success we re-fetch
  // the section list and focus the newly-added section in the editor.
  async function submitSection(v: SectionForm) {
    if (!plan) return;
    const sectionKey     = (v.sectionKey ?? '').trim();
    const sectionTitleEn = (v.sectionTitleEn ?? '').trim();
    if (!sectionKey) {
      toast.warning(isAr ? 'مفتاح القسم مطلوب' : 'Section key is required');
      return;
    }
    if (!/^[a-z0-9_]+$/.test(sectionKey)) {
      toast.warning(isAr
        ? 'المفتاح: أحرف إنجليزية صغيرة وأرقام وشرطة سفلية فقط'
        : 'Key: lowercase letters, digits and underscores only');
      return;
    }
    if (!sectionTitleEn) {
      toast.warning(isAr ? 'العنوان الإنجليزي مطلوب' : 'English title is required');
      return;
    }
    // sectionOrder is a number-kind field (runtime value is a number or '').
    // Leave undefined when blank so the backend appends after the last section.
    let sectionOrder: number | undefined;
    const rawOrder = String(v.sectionOrder ?? '').trim();
    if (rawOrder !== '') {
      const n = Number(v.sectionOrder);
      if (!Number.isFinite(n) || n < 1) {
        toast.warning(isAr ? 'الترتيب يجب أن يكون رقماً موجباً' : 'Order must be a positive number');
        return;
      }
      sectionOrder = Math.round(n);
    }
    try {
      const created = await addSection(plan.id, {
        sectionKey,
        sectionTitleEn,
        sectionTitleAr: v.sectionTitleAr?.trim() || undefined,
        sectionOrder,
        bodyEn: v.bodyEn?.trim() || undefined,
        bodyAr: v.bodyAr?.trim() || undefined,
      });
      const secs = await listSections(plan.id);
      setSections(secs);
      setActiveSection(secs.find(s => s.id === created.id) ?? created);
      setOpenSection(false);
      toast.success(isAr ? `تم إضافة القسم ${created.sectionKey}` : `Section ${created.sectionKey} added`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إضافة القسم' : 'Add section failed'));
    }
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

  // ── Distribution (Tier-2 #11) ──
  async function reloadDistribution() {
    if (!plan) return;
    // Per-call .catch() — show acks list even if stats aggregator is down.
    const [list, s] = await Promise.all([
      listDistribution(plan.id).catch(() => [] as PlanAcknowledgement[]),
      distributionStats(plan.id).catch(() => null as DistributionStats | null),
    ]);
    setAcks(Array.isArray(list) ? list : []);
    setDistStats(s);
  }
  // Auto-load when the user switches to the Distribution tab the first time.
  useEffect(() => {
    if (builderTab === 'distribution' && plan && distStats === null) {
      void reloadDistribution();
    }
  }, [builderTab, plan]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submitDistribute(form: DistributeForm) {
    if (!plan) return;
    // Parse "Name <email> :: Role" lines into Recipient[].
    const recipients = (form.recipientsText ?? '').split('\n')
      .map(l => l.trim()).filter(Boolean)
      .map(parseRecipient);
    if (recipients.length === 0) {
      toast.warning(isAr ? 'أدخل مستلماً واحداً على الأقل' : 'Enter at least one recipient');
      return;
    }
    try {
      await distribute(plan.id, recipients,
        (form.channel || 'EMAIL') as 'EMAIL'|'PORTAL'|'IN_PERSON'|'PRINT'|'OTHER',
        form.notes);
      toast.success(isAr ? `تم التوزيع لـ ${recipients.length} مستلم` : `Distributed to ${recipients.length} recipients`);
      setDistOpen(false);
      await reloadDistribution();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التوزيع' : 'Distribute failed'));
    }
  }

  // ── Plan review attestation (Tier-2 #10) ──
  async function attestRow(p: BcpPlan) {
    try {
      const a = await attestPlan(p.id, 'STILL_CURRENT',
        isAr ? 'تأكيد دوري — لا تغييرات جوهرية' : 'Periodic attestation — no material change');
      toast.success(isAr
        ? `تم تأكيد ${p.planCode} — المراجعة القادمة ${a.newNextReviewDate ?? '—'}`
        : `${p.planCode} attested — next review ${a.newNextReviewDate ?? '—'}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التأكيد' : 'Attest failed'));
    }
  }

  async function runReviewSweep() {
    try {
      const r = await sweepReview();
      toast.success(isAr
        ? `جارية: ${r.current} · قريبة: ${r.dueSoon} · متأخرة: ${r.overdue} (تم تصعيد ${r.flippedToOverdue})`
        : `Current: ${r.current} · Due soon: ${r.dueSoon} · Overdue: ${r.overdue} (escalated ${r.flippedToOverdue})`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function ackRow(row: PlanAcknowledgement, action: 'ack' | 'decline') {
    try {
      if (action === 'ack')      await acknowledge(row.id);
      else                       await decline(row.id);
      toast.success(action === 'ack'
        ? (isAr ? 'تم التأكيد' : 'Acknowledged')
        : (isAr ? 'تم الرفض' : 'Declined'));
      await reloadDistribution();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ── Slice 10-F binder ──
  async function reloadBinders() {
    if (!plan) return;
    const list = await listBindersForPlan(plan.id).catch(() => [] as BinderExport[]);
    setBinders(Array.isArray(list) ? list : []);
    setBindersLoaded(true);
  }
  useEffect(() => {
    if (builderTab === 'binder' && plan && !bindersLoaded) {
      void reloadBinders();
    }
  }, [builderTab, plan, bindersLoaded]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submitBinder(v: BinderForm) {
    if (!plan) return;
    setBinderBusy(true);
    try {
      const out = await generateBinder(plan.id, (v.language || 'BILINGUAL') as BinderLanguage);
      if (out.status === 'READY') {
        toast.success(isAr
          ? `تم إنشاء الكتيّب ${out.exportCode}`
          : `Binder ${out.exportCode} ready`);
      } else if (out.status === 'FAILED') {
        toast.danger(isAr
          ? `فشل الإنشاء: ${out.errorMessage ?? '—'}`
          : `Generation failed: ${out.errorMessage ?? '—'}`);
      } else {
        toast.info(isAr ? 'جارٍ الإنشاء…' : 'Generating…');
      }
      setBinderOpen(false);
      await reloadBinders();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إنشاء الكتيّب' : 'Binder generation failed'));
    } finally {
      setBinderBusy(false);
    }
  }

  async function handleBinderDownload(b: BinderExport) {
    try {
      await downloadBinder(b.id, b.exportCode);
      // Counter is incremented server-side; refresh so the UI reflects it.
      await reloadBinders();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التنزيل' : 'Download failed'));
    }
  }

  async function handleBinderArchive(b: BinderExport) {
    try {
      await archiveBinder(b.id);
      toast.success(isAr ? 'تمت الأرشفة' : 'Archived');
      await reloadBinders();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل الأرشفة' : 'Archive failed'));
    }
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
          actions={<Button variant="primary" onClick={() => setNewOpen(true)}>
            <Plus size={14} /> {isAr ? 'خطة جديدة' : 'New plan'}
          </Button>}
        />

        <div className="bcp-builder-kpis">
          <Kpi Icon={FileText} label={isAr ? 'BCP'      : 'BCP plans'}   value={items.filter(p => p.planType === 'BCP').length} tone="info" />
          <Kpi Icon={FileText} label={isAr ? 'DRP'      : 'DRP plans'}   value={items.filter(p => p.planType === 'DRP').length} tone="info" />
          <Kpi Icon={Wrench}   label={isAr ? 'منشورة'   : 'Published'}   value={stats.status_published ?? 0}                   tone="success" />
          <Kpi Icon={History}  label={isAr ? 'في التحرير' : 'Draft'}     value={stats.status_draft ?? 0}                       tone="warning" />
          {/* Tier-2 #10 review-state KPIs sit alongside the lifecycle KPIs so
              the page tells the owner both "what's still in flight" and
              "what's about to need re-attestation" in one glance. */}
          <Kpi Icon={History}  label={isAr ? 'مراجعة قريبة' : 'Review due ≤30d'}
               value={items.filter(p => p.reviewState === 'DUE_SOON').length} tone="warning" />
          <Kpi Icon={History}  label={isAr ? 'مراجعة متأخرة' : 'Review overdue'}
               value={items.filter(p => p.reviewState === 'OVERDUE').length} tone="danger" />
        </div>

        <Card title={isAr ? 'الخطط' : 'Plans'} action={
          <div className="bcp-builder-toolbar">
            <Button variant="ghost" onClick={() => void runReviewSweep()}>
              {isAr ? 'تحديث حالات المراجعة' : 'Refresh review states'}
            </Button>
            <select value={planTypeFilter} onChange={e => setPlanTypeFilter(e.target.value as BcpPlanType | '')}
                    className="bcp-builder-filter-select">
              <option value="">{isAr ? 'كل الأنواع' : 'All types'}</option>
              <option value="BCP">BCP</option>
              <option value="DRP">DRP</option>
            </select>
          </div>
        }>
          {loading && <div className="text-muted bcp-builder-loading-line">{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
          {items.length === 0 ? (
            <InlineEmpty text={isAr ? 'لا توجد خطط بعد — ابدأ بـ "خطة جديدة" أو من تحليل أثر معتمد عبر صفحة الـBIA' : 'No plans yet — click "New plan" or generate from an approved BIA on the BIA page'} />
          ) : (
            <div className="bcp-builder-table-scroll">
              <table className="data-table bcp-builder-table">
                <thead><tr>
                  <th className="bcp-builder-th-start">{isAr ? 'الكود' : 'Code'}</th>
                  <th className="bcp-builder-th-start">{isAr ? 'النوع' : 'Type'}</th>
                  <th className="bcp-builder-th-start">{isAr ? 'العنوان' : 'Title'}</th>
                  <th className="bcp-builder-th-start">{isAr ? 'الحالة' : 'Status'}</th>
                  <th className="bcp-builder-th-start">{isAr ? 'النسخة' : 'Version'}</th>
                  <th className="bcp-builder-th-start">RTO</th>
                  <th className="bcp-builder-th-start">RPO</th>
                  <th className="bcp-builder-th-start">{isAr ? 'المراجعة' : 'Review'}</th>
                  <th className="bcp-builder-th-end">{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr></thead>
                <tbody>
                  {items.map(p => (
                    <tr key={p.id}>
                      <td className="mono">{p.planCode}</td>
                      <td><Badge tone={p.planType === 'BCP' ? 'info' : 'primary'}>{p.planType}</Badge></td>
                      <td>{p.titleEn}</td>
                      <td><Badge tone={planStatusTone(p.status)}>{statusLabel(p.status, lang)}</Badge></td>
                      <td className="mono">v{p.currentVersion}</td>
                      <td className="mono">{p.rtoHours ?? '—'}h</td>
                      <td className="mono">{p.rpoHours ?? '—'}h</td>
                      <td>
                        <Badge tone={reviewStateTone(p.reviewState)}>{p.reviewState ?? 'CURRENT'}</Badge>
                        {p.nextReviewDate && (
                          <div className="text-muted mono bcp-builder-review-next">
                            {isAr ? 'القادمة' : 'next'}: {p.nextReviewDate}
                          </div>
                        )}
                      </td>
                      <td className="bcp-builder-td-end">
                        <Button variant="ghost" onClick={() => void attestRow(p)}>
                          {isAr ? 'تأكيد المراجعة' : 'Attest'}
                        </Button>
                        <Button variant="ghost" onClick={() => void openPlan(p.id)}>{isAr ? 'فتح' : 'Open'}</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <FormModal<NewPlanForm>
          open={newOpen}
          title={isAr ? 'خطة جديدة' : 'New plan'}
          size="md"
          submitLabel={isAr ? 'إنشاء' : 'Create'}
          fields={newPlanFields(isAr)}
          initial={emptyNewPlan()}
          onClose={() => setNewOpen(false)}
          onSubmit={submitNewPlan}
        />
      </Shell>
    );
  }

  // ── Builder mode ──
  if (!plan) return null;
  const tabs: TabItem<BuilderTab>[] = [
    { id: 'sections',     label: isAr ? `أقسام (${sections.length})`  : `Sections (${sections.length})`,  Icon: ListOrdered },
    { id: 'versions',     label: isAr ? `النسخ (${versions.length})`   : `Versions (${versions.length})`,   Icon: History },
    { id: 'diff',         label: isAr ? 'المقارنة'                     : 'Diff',                            Icon: GitCompare },
    { id: 'distribution', label: isAr ? `التوزيع (${acks.length})`     : `Distribution (${acks.length})`,   Icon: Send },
    { id: 'binder',       label: isAr ? `الكتيّب (${binders.length})`   : `Binder (${binders.length})`,      Icon: BookOpen },
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
          <Button variant="ghost" onClick={() => void downloadPlanPdf(plan.id, plan.planCode)}>
            <Download size={14} /> {isAr ? 'تنزيل PDF' : 'Download PDF'}
          </Button>
          <Button variant="ghost" onClick={() => setBinderOpen(true)}>
            <BookOpen size={14} /> {isAr ? 'تصدير الكتيّب' : 'Export binder'}
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

      <div className="bcp-builder-badge-row">
        <Badge tone={planStatusTone(plan.status)}>{plan.status}</Badge>
        {isLocked && (
          <span className="text-muted bcp-builder-locked-hint">
            {isAr ? 'الخطة مقفلة. أعد الفتح للتحرير.' : 'Plan is locked. Reopen to edit.'}
          </span>
        )}
      </div>

      <Tabs items={tabs} active={builderTab} onChange={setBuilderTab} />

      <div className="bcp-builder-content">
        {builderTab === 'sections' && (
          <div className="bcp-builder-sections-grid">
            <Card title={isAr ? 'الأقسام' : 'Sections'}
                  action={!isLocked
                    ? <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenSection(true)}>
                        {isAr ? 'إضافة قسم' : 'Add section'}
                      </Button>
                    : undefined}>
              {sections.length === 0
                ? <InlineEmpty text={isAr ? 'لا أقسام' : 'No sections'} />
                : <ul className="bcp-builder-section-list">
                    {sections.map(s => (
                      <li key={s.id}>
                        <button onClick={() => setActiveSection(s)}
                          className={`bcp-builder-section-btn${activeSection?.id === s.id ? ' is-active' : ''}`}>
                          <div className="bcp-builder-section-title">{isAr ? (s.sectionTitleAr ?? s.sectionTitleEn) : s.sectionTitleEn}</div>
                          <div className="text-muted mono bcp-builder-section-key">{s.sectionKey}</div>
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
              : <table className="data-table bcp-builder-table">
                  <thead><tr>
                    <th>{isAr ? 'النسخة' : 'Version'}</th>
                    <th>{isAr ? 'تاريخ الاعتماد' : 'Approved at'}</th>
                    <th>{isAr ? 'بواسطة' : 'By'}</th>
                    <th>{isAr ? 'ملخص' : 'Summary'}</th>
                    <th className="bcp-builder-th-end">{isAr ? 'مقارنة' : 'Compare'}</th>
                  </tr></thead>
                  <tbody>
                    {versions.map(v => (
                      <tr key={v.id}>
                        <td className="mono">v{v.versionNumber}</td>
                        <td className="mono">{v.approvedAt?.slice(0, 16) ?? '—'}</td>
                        <td>{v.approvedBy ?? '—'}</td>
                        <td>{v.changeSummary ?? '—'}</td>
                        <td className="bcp-builder-td-end">
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
              : <div className="bcp-builder-diff-grid">
                  <div>
                    <div className="bcp-builder-diff-label">v{diffFrom}</div>
                    <pre className="bcp-builder-diff-pre">{diffPayload.fromJson}</pre>
                  </div>
                  <div>
                    <div className="bcp-builder-diff-label">v{diffTo}</div>
                    <pre className="bcp-builder-diff-pre">{diffPayload.toJson}</pre>
                  </div>
                </div>}
          </Card>
        )}

        {builderTab === 'distribution' && (
          <Card title={isAr ? 'توزيع الخطة والإقرارات' : 'Plan distribution & acknowledgement'}
                action={<Button variant="primary" onClick={() => setDistOpen(true)}>
                  <Send size={14} /> {isAr ? 'توزيع جديد' : 'Distribute'}
                </Button>}>
            {distStats && (
              <div className="bcp-builder-dist-kpis">
                <Kpi Icon={Send}    label={isAr ? 'إجمالي'    : 'Total'}        value={distStats.total}                          tone="info" />
                <Kpi Icon={History} label={isAr ? 'بالانتظار' : 'Pending'}      value={distStats.pending}                        tone="warning" />
                <Kpi Icon={History} label={isAr ? 'مُقَر'      : 'Acknowledged'} value={distStats.acknowledged}                   tone="success" />
                <Kpi Icon={History} label={isAr ? 'مرفوضة'   : 'Declined'}     value={distStats.declined}                       tone="danger" />
                <Kpi Icon={History} label={isAr ? 'متقادمة'   : 'Stale'}        value={distStats.stale}                          tone="neutral" />
                <Kpi Icon={History} label={isAr ? 'نسبة الإقرار' : 'Ack %'}     value={`${distStats.acknowledgedPercent.toFixed(1)}%`} tone={distStats.acknowledgedPercent >= 80 ? 'success' : distStats.acknowledgedPercent >= 50 ? 'warning' : 'danger'} />
              </div>
            )}
            {acks.length === 0
              ? <InlineEmpty text={isAr ? 'لم تُوزَّع الخطة بعد.' : 'Plan has not been distributed yet.'} />
              : <div className="bcp-builder-table-scroll">
                  <table className="data-table bcp-builder-table">
                    <thead><tr>
                      <th className="bcp-builder-th-start">{isAr ? 'النسخة' : 'Version'}</th>
                      <th className="bcp-builder-th-start">{isAr ? 'المستلم' : 'Recipient'}</th>
                      <th className="bcp-builder-th-start">{isAr ? 'الدور' : 'Role'}</th>
                      <th className="bcp-builder-th-start">{isAr ? 'القناة' : 'Channel'}</th>
                      <th className="bcp-builder-th-start">{isAr ? 'وُزِّعت' : 'Distributed'}</th>
                      <th className="bcp-builder-th-start">{isAr ? 'الحالة' : 'Status'}</th>
                      <th className="bcp-builder-th-start">{isAr ? 'أُقِرَّت' : 'Acked at'}</th>
                      <th className="bcp-builder-th-end">{isAr ? 'إجراءات' : 'Actions'}</th>
                    </tr></thead>
                    <tbody>
                      {acks.map(a => (
                        <tr key={a.id}>
                          <td className="mono">v{a.planVersion}</td>
                          <td>{a.recipientName}{a.recipientEmail ? <span className="text-muted text-2xs"> &lt;{a.recipientEmail}&gt;</span> : null}</td>
                          <td className="text-muted">{a.recipientRole ?? '—'}</td>
                          <td className="mono text-2xs">{a.distributionChannel}</td>
                          <td className="mono text-2xs">{a.distributedAt?.slice(0, 16) ?? '—'}</td>
                          <td><Badge tone={ackStatusTone(a.status)}>{statusLabel(a.status, lang)}</Badge></td>
                          <td className="mono text-2xs">{a.acknowledgedAt?.slice(0, 16) ?? '—'}</td>
                          <td className="bcp-builder-td-end">
                            {a.status === 'PENDING' && (
                              <>
                                <Button variant="ghost" onClick={() => void ackRow(a, 'ack')}>{isAr ? 'تأكيد' : 'Acknowledge'}</Button>
                                <Button variant="ghost" onClick={() => void ackRow(a, 'decline')}>{isAr ? 'رفض' : 'Decline'}</Button>
                              </>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>}
          </Card>
        )}
      </div>

      {builderTab === 'binder' && (
        <Card title={isAr ? 'كتيّب الخطة للطباعة دون اتصال' : 'Offline plan binder'}
              action={<Button variant="primary" onClick={() => setBinderOpen(true)}>
                <BookOpen size={14} /> {isAr ? 'تصدير الكتيّب' : 'Export binder'}
              </Button>}>
          {/* SAMA BCF Annex D context — explain WHY this exists so a
              first-time auditor / reviewer immediately understands. */}
          <p className="text-muted bcp-builder-hint">
            {isAr
              ? 'يُلبّي هذا الكتيّب اشتراط ساما (إطار استمرارية الأعمال — الملحق د) بإتاحة كل خطة استمرارية لخدمة حرجة كنسخة قابلة للطباعة دون اتصال بالمنصة.'
              : 'Each binder satisfies SAMA BCF Annex D — every critical-business-service BCP must be available off-line so the Crisis Room can fall back to paper.'}
          </p>
          {binders.length === 0
            ? <InlineEmpty text={isAr
                ? 'لا توجد كتيّبات بعد — اضغط "تصدير الكتيّب" لإنشاء أوّل نسخة.'
                : 'No binders yet — click "Export binder" to generate the first version.'} />
            : <div className="bcp-builder-table-scroll">
                <table className="data-table bcp-builder-table">
                  <thead><tr>
                    <th className="bcp-builder-th-start">{isAr ? 'الكود' : 'Code'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'الإصدار' : 'Version'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'اللغة' : 'Language'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'الحالة' : 'Status'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'الصفحات' : 'Pages'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'الحجم' : 'Size'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'أُنشئ' : 'Generated'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'بواسطة' : 'By'}</th>
                    <th className="bcp-builder-th-start">{isAr ? 'التنزيلات' : 'Downloads'}</th>
                    <th className="bcp-builder-th-end">{isAr ? 'إجراءات' : 'Actions'}</th>
                  </tr></thead>
                  <tbody>
                    {binders.map(b => (
                      <tr key={b.id}>
                        <td className="mono">{b.exportCode}</td>
                        <td className="mono">{b.versionLabel ?? '—'}</td>
                        <td><Badge tone="neutral">{languageLabel(b.language, isAr)}</Badge></td>
                        <td><Badge tone={binderStatusTone(b.status)}>{b.status}</Badge></td>
                        <td className="mono">{b.pageCount ?? '—'}</td>
                        <td className="mono">{fmtBytes(b.byteSize)}</td>
                        <td className="mono text-2xs">{b.generatedAt?.slice(0, 16) ?? '—'}</td>
                        <td>{b.generatedByName ?? '—'}</td>
                        <td className="mono">{b.downloadCount}</td>
                        <td className="bcp-builder-td-end">
                          {b.status === 'READY' && (
                            <Button variant="ghost" onClick={() => void handleBinderDownload(b)}>
                              <Download size={12} /> {isAr ? 'تنزيل' : 'Download'}
                            </Button>
                          )}
                          {b.status !== 'ARCHIVED' && (
                            <Button variant="ghost" onClick={() => void handleBinderArchive(b)}>
                              <Trash2 size={12} /> {isAr ? 'أرشفة' : 'Archive'}
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>}
        </Card>
      )}

      <FormModal<SectionForm>
        open={openSection}
        title={isAr ? 'إضافة قسم' : 'Add section'}
        size="md"
        submitLabel={isAr ? 'إضافة' : 'Add'}
        fields={sectionFields(isAr)}
        initial={sectionInitial}
        onClose={() => setOpenSection(false)}
        onSubmit={submitSection}
      />

      <FormModal<DistributeForm>
        open={distOpen}
        title={isAr ? 'توزيع الخطة' : 'Distribute plan'}
        size="md"
        submitLabel={isAr ? 'توزيع' : 'Distribute'}
        fields={distributeFields(isAr)}
        initial={emptyDistribute()}
        onClose={() => setDistOpen(false)}
        onSubmit={submitDistribute}
      />

      <FormModal<BinderForm>
        open={binderOpen}
        title={isAr ? 'تصدير كتيّب الخطة' : 'Export plan binder'}
        size="sm"
        submitLabel={binderBusy
          ? (isAr ? 'جارٍ الإنشاء…' : 'Generating…')
          : (isAr ? 'إنشاء PDF' : 'Generate PDF')}
        fields={binderFields(isAr)}
        initial={emptyBinder()}
        onClose={() => setBinderOpen(false)}
        onSubmit={submitBinder}
      />
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
    <div className="bcp-builder-editor">
      <label className="bcp-builder-editor-field">
        <span className="text-muted">{isAr ? 'النص (إنجليزي)' : 'Body (EN)'}</span>
        <textarea value={bodyEn} disabled={locked}
                  onChange={e => setBodyEn(e.target.value)}
                  onBlur={() => onSave(bodyEn, bodyAr)}
                  className="bcp-builder-editor-textarea"
                  placeholder={isAr ? 'اكتب محتوى القسم هنا. يدعم Markdown البسيط.' : 'Write the section body here. Simple markdown supported.'} />
      </label>
      <label className="bcp-builder-editor-field">
        <span className="text-muted">{isAr ? 'النص (عربي)' : 'Body (AR)'}</span>
        <textarea value={bodyAr} disabled={locked}
                  onChange={e => setBodyAr(e.target.value)}
                  onBlur={() => onSave(bodyEn, bodyAr)}
                  className="bcp-builder-editor-textarea bcp-builder-editor-textarea--rtl" />
      </label>
      {section.lastEditedBy && (
        <div className="text-muted bcp-builder-editor-meta">
          {isAr ? 'آخر تعديل' : 'Last edited'}: <span className="mono">{section.lastEditedAt?.slice(0, 16)}</span> {isAr ? 'بواسطة' : 'by'} {section.lastEditedBy}
        </div>
      )}
    </div>
  );
}

// ─── New-plan form definition ────────────────────────────────────
//
// Shape kept simple (string-typed for number fields) so it plugs straight
// into the generic FormModal without per-field type gymnastics. The submit
// handler coerces numeric fields with `Number(...)` before the API call.

interface NewPlanForm {
  planType:           string;
  titleEn:            string;
  titleAr:            string;
  rtoHours:           string;
  rpoHours:           string;
  mtpdHours:          string;
  responseTeamRole:   string;
}

function emptyNewPlan(): NewPlanForm {
  return { planType: 'BCP', titleEn: '', titleAr: '',
           rtoHours: '', rpoHours: '', mtpdHours: '',
           responseTeamRole: '' };
}

// ─── Add-section form definition ─────────────────────────────────
//
// All-string shape (sectionOrder is a number-kind field whose runtime
// value FormModal stores as a number; the submit handler coerces it).
// Mirrors the backend contract: section_key ≤ 50 chars unique per plan,
// section_title_en required, the rest optional.

interface SectionForm {
  sectionKey:      string;
  sectionTitleEn:  string;
  sectionTitleAr:  string;
  sectionOrder:    string;
  bodyEn:          string;
  bodyAr:          string;
}

function emptySection(): SectionForm {
  return { sectionKey: '', sectionTitleEn: '', sectionTitleAr: '',
           sectionOrder: '', bodyEn: '', bodyAr: '' };
}

function sectionFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'text', key: 'sectionKey', label: isAr ? 'مفتاح القسم' : 'Section key',
      span: 6, required: true, maxLength: 50,
      placeholder: isAr ? 'مثلاً: vendor_dependencies' : 'e.g. vendor_dependencies' },
    { kind: 'number', key: 'sectionOrder', label: isAr ? 'الترتيب (اختياري)' : 'Order (optional)',
      span: 6, min: 1 },

    { kind: 'text', key: 'sectionTitleEn', label: isAr ? 'العنوان (إنجليزي)' : 'Title (EN)',
      span: 6, required: true, maxLength: 255,
      placeholder: isAr ? 'مثلاً: Vendor dependencies' : 'e.g. Vendor dependencies' },
    { kind: 'text', key: 'sectionTitleAr', label: isAr ? 'العنوان (عربي)' : 'Title (AR)',
      span: 6, maxLength: 255,
      placeholder: isAr ? 'مثلاً: اعتماديات الموردين' : 'e.g. اعتماديات الموردين' },

    { kind: 'textarea', key: 'bodyEn', label: isAr ? 'النص (إنجليزي)' : 'Body (EN)', span: 12, rows: 3 },
    { kind: 'textarea', key: 'bodyAr', label: isAr ? 'النص (عربي)' : 'Body (AR)', span: 12, rows: 3 },
  ];
}

// ─── Distribute form (Tier-2 #11) ────────────────────────────────
//
// One textarea where the author types one recipient per line.
// Accepted line shapes:
//   "Name <email@x.com>"
//   "Name <email@x.com> :: Role"
//   "Name :: Role"
//   "email@x.com"
// We parse each line into a Recipient on submit. A free-text textarea
// is the right shape until we have a real user picker — copy-paste
// from existing contact lists is the demo flow.

interface DistributeForm {
  channel:         string;
  notes:           string;
  recipientsText:  string;
}

function emptyDistribute(): DistributeForm {
  return { channel: 'EMAIL', notes: '', recipientsText: '' };
}

function distributeFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'select', key: 'channel', label: isAr ? 'القناة' : 'Channel', span: 6, required: true,
      options: [
        { value: 'EMAIL',     label: isAr ? 'بريد إلكتروني' : 'Email' },
        { value: 'PORTAL',    label: isAr ? 'بوابة'         : 'Portal' },
        { value: 'IN_PERSON', label: isAr ? 'لقاء مباشر'    : 'In person' },
        { value: 'PRINT',     label: isAr ? 'نسخة مطبوعة'   : 'Print' },
        { value: 'OTHER',     label: isAr ? 'أخرى'          : 'Other' },
      ] },
    { kind: 'text', key: 'notes', label: isAr ? 'ملاحظات (اختياري)' : 'Notes (optional)',
      span: 6, placeholder: isAr ? 'مثلاً: التوزيع بعد تمرين Q1' : 'e.g. Distributed after Q1 exercise' },
    { kind: 'textarea', key: 'recipientsText', rows: 8, span: 12,
      label: isAr
        ? 'المستلمون (سطر لكل مستلم) — التنسيق: الاسم <البريد> :: الدور'
        : 'Recipients (one per line) — format: Name <email> :: Role' },
  ];
}

function parseRecipient(line: string): { name: string; email?: string; role?: string } {
  // Split off the "::Role" suffix first.
  let role: string | undefined;
  const roleIdx = line.indexOf('::');
  let head = line;
  if (roleIdx >= 0) {
    role = line.slice(roleIdx + 2).trim() || undefined;
    head = line.slice(0, roleIdx).trim();
  }
  // Extract "<email>" if present.
  const m = head.match(/^(.*?)\s*<\s*([^>]+)\s*>\s*$/);
  if (m) return { name: m[1].trim() || m[2].trim(), email: m[2].trim(), role };
  // No angle brackets — if the whole head looks like an email, use it as both.
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(head)) return { name: head, email: head, role };
  // Otherwise treat the line as a plain name.
  return { name: head, role };
}

// ─── Slice 10-F binder form ──────────────────────────────────────

interface BinderForm {
  language: string;
}
function emptyBinder(): BinderForm { return { language: 'BILINGUAL' }; }
function binderFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'select', key: 'language', span: 12, required: true,
      label: isAr ? 'اللغة' : 'Language',
      options: [
        { value: 'BILINGUAL', label: isAr ? 'ثنائي اللغة (الموصى به)' : 'Bilingual (recommended)' },
        { value: 'EN',        label: isAr ? 'إنجليزي'                  : 'English only' },
        { value: 'AR',        label: isAr ? 'عربي'                     : 'Arabic only' },
      ] },
  ];
}

function newPlanFields(isAr: boolean): FieldDef[] {
  return [
    { kind: 'select', key: 'planType', label: isAr ? 'النوع' : 'Plan type', span: 6, required: true,
      options: [
        { value: 'BCP', label: isAr ? 'BCP — استمرارية الأعمال' : 'BCP — Business Continuity' },
        { value: 'DRP', label: isAr ? 'DRP — التعافي من الكوارث' : 'DRP — Disaster Recovery' },
      ] },
    { kind: 'text', key: 'responseTeamRole', label: isAr ? 'دور فريق الاستجابة' : 'Response team role',
      span: 6, placeholder: isAr ? 'مثلاً: مدير عمليات الخدمات المصرفية' : 'e.g. Core Banking Lead' },

    { kind: 'text', key: 'titleEn', label: isAr ? 'العنوان (إنجليزي)' : 'Title (EN)', span: 12, required: true,
      placeholder: isAr ? 'مثلاً: Core Banking BCP' : 'e.g. Core Banking BCP' },
    { kind: 'text', key: 'titleAr', label: isAr ? 'العنوان (عربي)' : 'Title (AR)', span: 12,
      placeholder: isAr ? 'مثلاً: خطة استمرارية الخدمات المصرفية الأساسية' : 'e.g. خطة استمرارية الخدمات المصرفية الأساسية' },

    { kind: 'number', key: 'rtoHours',  label: 'RTO (h)',  span: 4, min: 0 },
    { kind: 'number', key: 'rpoHours',  label: 'RPO (h)',  span: 4, min: 0 },
    { kind: 'number', key: 'mtpdHours', label: 'MTPD (h)', span: 4, min: 0 },
  ];
}
