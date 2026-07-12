import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Leaf, Landmark, ArrowRightLeft, FileBarChart2, Briefcase, Coins, Plus, RefreshCw,
  Download, CheckCircle2, XCircle, TrendingUp,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Tabs } from '../../components/ui/Tabs';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  greenFinanceApi, greenPortfolioApi, ApiError,
  type GfInstrumentDto, type GfProjectDto, type GfAllocationDto, type GfSummaryDto,
  type GfReportDto, type GpAssetDto, type GpTaxonomyDto, type GpSummaryDto, type GpCreditDto,
} from '../../lib/api';

type TabId = 'instruments' | 'uop' | 'reports' | 'portfolio' | 'credits';

const INPUT: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 130, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};
const EMPTY: React.CSSProperties = { padding: 14, color: 'var(--ink-3)', fontSize: 13 };
const LIFECYCLE = ['DRAFT', 'SHARIAH_REVIEW', 'APPROVED', 'ISSUED', 'REPORTING', 'MATURED'];

function tone(s: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (s) {
    case 'APPROVED': case 'ISSUED': case 'PUBLISHED': case 'ELIGIBLE': case 'RETIRED': return 'success';
    case 'PROPOSED': case 'PENDING': case 'DRAFT': case 'SHARIAH_REVIEW': case 'UNASSESSED': return 'warning';
    case 'REJECTED': case 'INELIGIBLE': return 'danger';
    case 'REPORTING': case 'TRANSITION': case 'PURCHASED': return 'info';
    default: return 'neutral';
  }
}
const fmt = (n?: number | null) => n == null ? '—' : Number(n).toLocaleString('en-US', { maximumFractionDigits: 2 });

/**
 * Green Finance (الصكوك الخضراء والمحفظة الخضراء) — plan §6 UI family over the
 * G1–G3 APIs: instrument register + Shariah lifecycle, use-of-proceeds ledger
 * (maker-checker + §4.2 figures), allocation/impact reports, the green portfolio
 * (taxonomy tagging + GAR), and the RVCMC carbon-credit ledger.
 */
export function GreenFinancePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [tab, setTab] = useState<TabId>('instruments');
  const [instruments, setInstruments] = useState<GfInstrumentDto[]>([]);
  const [portfolio, setPortfolio] = useState<GpSummaryDto | null>(null);
  const [credits, setCredits] = useState<GpCreditDto[]>([]);

  const reload = useCallback(() => {
    greenFinanceApi.instruments().then(setInstruments).catch(() => setInstruments([]));
    greenPortfolioApi.summary().then(setPortfolio).catch(() => setPortfolio(null));
    greenPortfolioApi.credits().then(setCredits).catch(() => setCredits([]));
  }, []);
  useEffect(() => { reload(); }, [reload]);

  const retired = useMemo(
    () => credits.filter(c => c.status === 'RETIRED').reduce((s, c) => s + Number(c.quantityTco2e), 0),
    [credits]);

  const KPIS = [
    { label: isAr ? 'الأدوات المُصدرة' : 'Instruments', value: String(instruments.length) },
    { label: isAr ? 'نسبة الأصول الخضراء GAR' : 'Green Asset Ratio', value: portfolio ? `${fmt(portfolio.greenAssetRatioPct)}%` : '—' },
    { label: isAr ? 'جودة البيانات المرجحة' : 'Weighted DQ (PCAF)', value: portfolio ? fmt(portfolio.weightedDataQuality) : '—' },
    { label: isAr ? 'أرصدة كربون مسحوبة' : 'Credits retired (tCO₂e)', value: fmt(retired) },
  ];

  return (
    <Shell title={isAr ? 'التمويل الأخضر' : 'Green Finance'}
      sub={isAr ? 'الصكوك الخضراء واستخدام المتحصلات والمحفظة الخضراء وأرصدة الكربون.' : 'Green sukuk, use of proceeds, the green portfolio, and carbon credits.'}>
      <PageHeader
        eyebrow={isAr ? 'الاستدامة · REG-7/8/11' : 'Sustainability · REG-7/8/11'}
        title={isAr ? 'التمويل الأخضر والمحفظة الخضراء' : 'Green Finance & Portfolio'}
        sub={isAr
          ? 'سجل الأدوات مع حالة الاعتماد الشرعي، دفتر استخدام المتحصلات (أربع عيون)، تقارير التخصيص والأثر، وسم المحفظة، وأرصدة الكربون الطوعية.'
          : 'Instrument register with Shariah state, the 4-eyes use-of-proceeds ledger, allocation & impact reports, portfolio tagging, and voluntary carbon credits.'}
      />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10, margin: '4px 0 14px' }}>
        {KPIS.map(k => (
          <div key={k.label} style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: '10px 12px' }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{k.label}</div>
            <div style={{ fontSize: 22, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{k.value}</div>
          </div>
        ))}
      </div>
      <Tabs<TabId>
        items={[
          { id: 'instruments', label: isAr ? 'سجل الأدوات' : 'Instruments',      Icon: Landmark },
          { id: 'uop',         label: isAr ? 'استخدام المتحصلات' : 'Use of Proceeds', Icon: ArrowRightLeft },
          { id: 'reports',     label: isAr ? 'تقارير التخصيص والأثر' : 'Reports', Icon: FileBarChart2 },
          { id: 'portfolio',   label: isAr ? 'المحفظة الخضراء' : 'Green Portfolio', Icon: Briefcase },
          { id: 'credits',     label: isAr ? 'أرصدة الكربون' : 'Carbon Credits',  Icon: Coins },
        ]}
        active={tab} onChange={setTab}
      />
      <div style={{ marginTop: 16 }}>
        {tab === 'instruments' && <InstrumentsTab instruments={instruments} reload={reload} />}
        {tab === 'uop'         && <UopTab instruments={instruments} />}
        {tab === 'reports'     && <ReportsTab instruments={instruments} />}
        {tab === 'portfolio'   && <PortfolioTab summary={portfolio} reload={reload} />}
        {tab === 'credits'     && <CreditsTab credits={credits} reload={reload} />}
      </div>
    </Shell>
  );
}

// ─── Instruments ─────────────────────────────────────────────────────────────

function InstrumentsTab({ instruments, reload }: { instruments: GfInstrumentDto[]; reload: () => void }) {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [form, setForm] = useState({ instrumentCode: '', instrumentType: 'GREEN_SUKUK', nameEn: '', amountSarMn: '' });

  async function create() {
    if (!form.instrumentCode || !form.nameEn || !form.amountSarMn) {
      toast.danger(isAr ? 'الرمز والاسم والمبلغ مطلوبة' : 'Code, name and amount are required'); return;
    }
    try {
      await greenFinanceApi.createInstrument({ ...form, amountSarMn: Number(form.amountSarMn) });
      toast.success(isAr ? 'سُجِّلت الأداة' : 'Instrument registered');
      setForm({ instrumentCode: '', instrumentType: 'GREEN_SUKUK', nameEn: '', amountSarMn: '' });
      reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  async function transition(i: GfInstrumentDto, status: string) {
    let shariahApprovalRef: string | undefined;
    if (status === 'APPROVED' && i.instrumentType.includes('SUKUK') && !i.shariahApprovalRef) {
      shariahApprovalRef = prompt(isAr ? 'مرجع اعتماد الهيئة الشرعية:' : 'Shariah board approval reference:') ?? undefined;
      if (!shariahApprovalRef) return;
    }
    try {
      await greenFinanceApi.transition(i.id, status, shariahApprovalRef);
      toast.success(`→ ${status}`);
      reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Transition failed'); }
  }

  return (
    <Card title={isAr ? 'سجل الصكوك والأدوات الخضراء' : 'Green instrument register'} Icon={Landmark}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.instrumentCode} onChange={e => setForm(f => ({ ...f, instrumentCode: e.target.value }))}
            placeholder="GS-2026-01" style={INPUT} />
          <select value={form.instrumentType} onChange={e => setForm(f => ({ ...f, instrumentType: e.target.value }))} style={INPUT}>
            {['GREEN_SUKUK', 'GREEN_BOND', 'SUSTAINABILITY_SUKUK', 'GREEN_LOAN', 'SLL'].map(t => <option key={t}>{t}</option>)}
          </select>
          <input value={form.nameEn} onChange={e => setForm(f => ({ ...f, nameEn: e.target.value }))}
            placeholder={isAr ? 'الاسم (EN)' : 'Name (EN)'} style={{ ...INPUT, minWidth: 170 }} />
          <input type="number" value={form.amountSarMn} onChange={e => setForm(f => ({ ...f, amountSarMn: e.target.value }))}
            placeholder={isAr ? 'المبلغ (م ر.س)' : 'Amount (SAR mn)'} style={INPUT} />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void create()}>{isAr ? 'تسجيل' : 'Register'}</Button>
          <Button size="sm" variant="ghost" Icon={Download} onClick={() => void greenFinanceApi.evidenceZip()}
            title={isAr ? 'حزمة أدلة التمويل الأخضر' : 'Green-Finance evidence ZIP'} />
        </div>
      }>
      {instruments.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا أدوات بعد.' : 'No instruments yet.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th><th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الاسم' : 'Name'}</th><th>{isAr ? 'المبلغ (م ر.س)' : 'Amount (SAR mn)'}</th>
              <th>{isAr ? 'الهيكل الشرعي' : 'Shariah'}</th><th>{isAr ? 'الحالة' : 'Status'}</th><th></th>
            </tr></thead>
            <tbody>
              {instruments.map(i => {
                const next = LIFECYCLE[LIFECYCLE.indexOf(i.status) + 1];
                return (
                  <tr key={i.id}>
                    <td className="mono text-2xs">{i.instrumentCode}</td>
                    <td><Badge tone="info">{i.instrumentType}</Badge></td>
                    <td className="text-2xs">{isAr && i.nameAr ? i.nameAr : i.nameEn}</td>
                    <td className="mono text-2xs">{fmt(i.amountSarMn)}</td>
                    <td className="text-2xs">{i.shariahStructure ?? '—'}{i.shariahApprovalRef ? ' ✓' : ''}</td>
                    <td><Badge tone={tone(i.status)} dot>{i.status}</Badge></td>
                    <td>
                      {next && <Button size="sm" variant="ghost" onClick={() => void transition(i, next)}>→ {next}</Button>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>}
    </Card>
  );
}

// ─── Use of Proceeds ─────────────────────────────────────────────────────────

function UopTab({ instruments }: { instruments: GfInstrumentDto[] }) {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [instrumentId, setInstrumentId] = useState<number | ''>('');
  const [projects, setProjects] = useState<GfProjectDto[]>([]);
  const [allocs, setAllocs] = useState<GfAllocationDto[]>([]);
  const [summary, setSummary] = useState<GfSummaryDto | null>(null);
  const [form, setForm] = useState({ projectId: '', amountSarMn: '', financingType: 'NEW_FINANCING' });
  const [projForm, setProjForm] = useState({ projectCode: '', nameEn: '', category: 'RENEWABLE_ENERGY', totalCostSarMn: '', actualTco2eAvoidedYr: '' });

  useEffect(() => { greenFinanceApi.projects().then(setProjects).catch(() => setProjects([])); }, []);
  const load = useCallback(async (id: number) => {
    try {
      setAllocs(await greenFinanceApi.allocations(id));
      setSummary(await greenFinanceApi.summary(id));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load ledger'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (instrumentId !== '') void load(instrumentId); }, [instrumentId, load]);

  async function createProject() {
    if (!projForm.projectCode || !projForm.nameEn || !projForm.totalCostSarMn) {
      toast.danger(isAr ? 'رمز المشروع واسمه وتكلفته مطلوبة' : 'Project code, name and cost required'); return;
    }
    try {
      await greenFinanceApi.createProject({
        ...projForm, totalCostSarMn: Number(projForm.totalCostSarMn),
        actualTco2eAvoidedYr: projForm.actualTco2eAvoidedYr ? Number(projForm.actualTco2eAvoidedYr) : undefined,
      });
      toast.success(isAr ? 'أُنشئ المشروع' : 'Project created');
      setProjForm({ projectCode: '', nameEn: '', category: 'RENEWABLE_ENERGY', totalCostSarMn: '', actualTco2eAvoidedYr: '' });
      setProjects(await greenFinanceApi.projects());
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  async function propose() {
    if (instrumentId === '' || !form.projectId || !form.amountSarMn) return;
    try {
      await greenFinanceApi.propose({
        instrumentId, projectId: Number(form.projectId), amountSarMn: Number(form.amountSarMn),
        allocationDate: new Date().toISOString().slice(0, 10), financingType: form.financingType,
      });
      toast.success(isAr ? 'اقتُرح التخصيص' : 'Allocation proposed');
      setForm({ projectId: '', amountSarMn: '', financingType: 'NEW_FINANCING' });
      await load(instrumentId);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Propose failed'); }
  }

  async function decide(a: GfAllocationDto, approve: boolean) {
    try {
      if (approve) await greenFinanceApi.approveAlloc(a.id);
      else {
        const reason = prompt(isAr ? 'سبب الرفض:' : 'Rejection reason:');
        if (!reason) return;
        await greenFinanceApi.rejectAlloc(a.id, reason);
      }
      toast.success(approve ? (isAr ? 'اعتُمد' : 'Approved') : (isAr ? 'رُفض' : 'Rejected'));
      if (instrumentId !== '') await load(instrumentId);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Decision failed'); }
  }

  const projName = (id: number) => projects.find(p => p.id === id)?.projectCode ?? `#${id}`;

  return (
    <>
      <Card title={isAr ? 'دفتر استخدام المتحصلات' : 'Use-of-proceeds ledger'} Icon={ArrowRightLeft}
        action={
          <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <select value={instrumentId} onChange={e => setInstrumentId(e.target.value === '' ? '' : Number(e.target.value))} style={INPUT}>
              <option value="">{isAr ? 'اختر أداة…' : 'Select instrument…'}</option>
              {instruments.map(i => <option key={i.id} value={i.id}>{i.instrumentCode}</option>)}
            </select>
            <select value={form.projectId} onChange={e => setForm(f => ({ ...f, projectId: e.target.value }))} style={INPUT}>
              <option value="">{isAr ? 'المشروع…' : 'Project…'}</option>
              {projects.map(p => <option key={p.id} value={p.id}>{p.projectCode}</option>)}
            </select>
            <input type="number" value={form.amountSarMn} onChange={e => setForm(f => ({ ...f, amountSarMn: e.target.value }))}
              placeholder={isAr ? 'المبلغ' : 'Amount'} style={{ ...INPUT, minWidth: 90 }} />
            <select value={form.financingType} onChange={e => setForm(f => ({ ...f, financingType: e.target.value }))} style={INPUT}>
              <option value="NEW_FINANCING">{isAr ? 'تمويل جديد' : 'New financing'}</option>
              <option value="REFINANCING">{isAr ? 'إعادة تمويل' : 'Refinancing'}</option>
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => void propose()} disabled={instrumentId === ''}>
              {isAr ? 'اقتراح' : 'Propose'}
            </Button>
          </div>
        }>
        {summary && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10, marginBottom: 12 }}>
            {[
              { l: isAr ? 'صافي المتحصلات' : 'Net proceeds', v: fmt(summary.netProceedsSarMn) },
              { l: isAr ? 'المخصص' : 'Allocated', v: fmt(summary.allocatedSarMn) },
              { l: isAr ? 'نسبة التخصيص' : 'Allocation %', v: `${fmt(summary.allocationRatioPct)}%` },
              { l: isAr ? 'إعادة التمويل' : 'Refinancing %', v: `${fmt(summary.refinancingSharePct)}%` },
              { l: 'tCO₂e/yr', v: fmt(summary.tco2eAvoidedYr) + (summary.estimateBasis ? ' *' : '') },
              { l: isAr ? 'الكثافة' : 'Intensity', v: fmt(summary.intensityTco2ePerSarMn) },
            ].map(k => (
              <div key={k.l} style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: '8px 10px' }}>
                <div style={{ fontSize: 10.5, color: 'var(--ink-3)' }}>{k.l}</div>
                <div style={{ fontSize: 17, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{k.v}</div>
              </div>
            ))}
          </div>
        )}
        {summary?.estimateBasis && (
          <div style={{ ...EMPTY, paddingTop: 0 }}>* {isAr ? 'تتضمن أرقام أثر تقديرية' : 'includes ESTIMATE-based impact figures'}</div>
        )}
        {instrumentId === ''
          ? <div style={EMPTY}>{isAr ? 'اختر أداة لعرض دفترها.' : 'Select an instrument to view its ledger.'}</div>
          : allocs.length === 0
            ? <div style={EMPTY}>{isAr ? 'لا تخصيصات بعد.' : 'No allocations yet.'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'المشروع' : 'Project'}</th><th>{isAr ? 'المبلغ' : 'Amount'}</th>
                  <th>{isAr ? 'التاريخ' : 'Date'}</th><th>{isAr ? 'النوع' : 'Type'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th><th></th>
                </tr></thead>
                <tbody>
                  {allocs.map(a => (
                    <tr key={a.id}>
                      <td className="mono text-2xs">{projName(a.projectId)}</td>
                      <td className="mono text-2xs">{fmt(a.amountSarMn)}</td>
                      <td className="text-2xs">{a.allocationDate}</td>
                      <td className="text-2xs">{a.financingType}</td>
                      <td><Badge tone={tone(a.status)} dot>{a.status}</Badge></td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        {a.status === 'PROPOSED' && <>
                          <Button size="sm" variant="ghost" Icon={CheckCircle2} onClick={() => void decide(a, true)} />
                          <Button size="sm" variant="ghost" Icon={XCircle} onClick={() => void decide(a, false)} />
                        </>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>}
      </Card>
      <div style={{ height: 16 }} />
      <Card title={isAr ? 'المشاريع المؤهلة' : 'Eligible projects'} Icon={Leaf}
        action={
          <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <input value={projForm.projectCode} onChange={e => setProjForm(f => ({ ...f, projectCode: e.target.value }))}
              placeholder="PRJ-01" style={{ ...INPUT, minWidth: 90 }} />
            <input value={projForm.nameEn} onChange={e => setProjForm(f => ({ ...f, nameEn: e.target.value }))}
              placeholder={isAr ? 'الاسم' : 'Name'} style={INPUT} />
            <select value={projForm.category} onChange={e => setProjForm(f => ({ ...f, category: e.target.value }))} style={INPUT}>
              {['RENEWABLE_ENERGY', 'ENERGY_EFFICIENCY', 'CLEAN_TRANSPORT', 'GREEN_BUILDINGS', 'WATER', 'WASTE', 'CCUS', 'OTHER_ELIGIBLE']
                .map(c => <option key={c}>{c}</option>)}
            </select>
            <input type="number" value={projForm.totalCostSarMn} onChange={e => setProjForm(f => ({ ...f, totalCostSarMn: e.target.value }))}
              placeholder={isAr ? 'التكلفة' : 'Cost'} style={{ ...INPUT, minWidth: 80 }} />
            <input type="number" value={projForm.actualTco2eAvoidedYr} onChange={e => setProjForm(f => ({ ...f, actualTco2eAvoidedYr: e.target.value }))}
              placeholder="tCO₂e/yr" style={{ ...INPUT, minWidth: 80 }} />
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => void createProject()}>{isAr ? 'إضافة' : 'Add'}</Button>
          </div>
        }>
        {projects.length === 0
          ? <div style={EMPTY}>{isAr ? 'لا مشاريع.' : 'No projects.'}</div>
          : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'الرمز' : 'Code'}</th><th>{isAr ? 'الاسم' : 'Name'}</th>
                <th>{isAr ? 'الفئة' : 'Category'}</th><th>{isAr ? 'التكلفة' : 'Cost (SAR mn)'}</th>
                <th>tCO₂e/yr</th><th>{isAr ? 'الأساس' : 'Basis'}</th>
              </tr></thead>
              <tbody>
                {projects.map(p => (
                  <tr key={p.id}>
                    <td className="mono text-2xs">{p.projectCode}</td>
                    <td className="text-2xs">{isAr && p.nameAr ? p.nameAr : p.nameEn}</td>
                    <td><Badge tone="info">{p.category}</Badge></td>
                    <td className="mono text-2xs">{fmt(p.totalCostSarMn)}</td>
                    <td className="mono text-2xs">{fmt(p.actualTco2eAvoidedYr)}</td>
                    <td><Badge tone={p.actualImpactBasis === 'ESTIMATE' ? 'warning' : 'success'}>{p.actualImpactBasis ?? 'ACTUAL'}</Badge></td>
                  </tr>
                ))}
              </tbody>
            </table>}
      </Card>
    </>
  );
}

// ─── Reports ─────────────────────────────────────────────────────────────────

function ReportsTab({ instruments }: { instruments: GfInstrumentDto[] }) {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [instrumentId, setInstrumentId] = useState<number | ''>('');
  const [rows, setRows] = useState<GfReportDto[]>([]);
  const [year, setYear] = useState(String(new Date().getFullYear()));

  const load = useCallback(async (id: number) => {
    try { setRows(await greenFinanceApi.reports(id)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load reports'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (instrumentId !== '') void load(instrumentId); }, [instrumentId, load]);

  async function act(fn: () => Promise<unknown>, ok: string) {
    try { await fn(); toast.success(ok); if (instrumentId !== '') await load(instrumentId); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  return (
    <Card title={isAr ? 'تقارير التخصيص والأثر السنوية' : 'Annual allocation & impact reports'} Icon={FileBarChart2}
      action={
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <select value={instrumentId} onChange={e => setInstrumentId(e.target.value === '' ? '' : Number(e.target.value))} style={INPUT}>
            <option value="">{isAr ? 'اختر أداة…' : 'Select instrument…'}</option>
            {instruments.map(i => <option key={i.id} value={i.id}>{i.instrumentCode}</option>)}
          </select>
          <input type="number" value={year} onChange={e => setYear(e.target.value)} style={{ ...INPUT, minWidth: 80 }} />
          <Button size="sm" variant="primary" Icon={Plus} disabled={instrumentId === ''}
            onClick={() => instrumentId !== '' && void act(
              () => greenFinanceApi.createReport({ instrumentId, periodYear: Number(year) }),
              isAr ? 'أُنشئ التقرير (مسودة مجمدة الأرقام)' : 'Report drafted (frozen figures)')}>
            {isAr ? 'إنشاء' : 'Draft'}
          </Button>
        </div>
      }>
      {instrumentId === ''
        ? <div style={EMPTY}>{isAr ? 'اختر أداة.' : 'Select an instrument.'}</div>
        : rows.length === 0
          ? <div style={EMPTY}>{isAr ? 'لا تقارير.' : 'No reports.'}</div>
          : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'الفترة' : 'Period'}</th><th>{isAr ? 'النوع' : 'Type'}</th>
                <th>{isAr ? 'المخصص' : 'Allocated'}</th><th>{isAr ? 'نسبة التخصيص' : 'Allocation %'}</th>
                <th>tCO₂e</th><th>{isAr ? 'الحالة' : 'Status'}</th><th></th>
              </tr></thead>
              <tbody>
                {rows.map(r => (
                  <tr key={r.id}>
                    <td className="mono text-2xs">{r.periodYear}</td>
                    <td className="text-2xs">{r.reportType}</td>
                    <td className="mono text-2xs">{fmt(r.allocatedSarMn)}</td>
                    <td className="mono text-2xs">{fmt(r.allocationRatioPct)}%</td>
                    <td className="mono text-2xs">{fmt(r.tco2eAvoidedYr)}</td>
                    <td><Badge tone={tone(r.status)} dot>{r.status}</Badge></td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {r.status === 'DRAFT' &&
                        <Button size="sm" variant="ghost" onClick={() => void act(() => greenFinanceApi.approveReport(r.id), isAr ? 'اعتُمد' : 'Approved')}>
                          {isAr ? 'اعتماد' : 'Approve'}</Button>}
                      {r.status === 'APPROVED' &&
                        <Button size="sm" variant="ghost" onClick={() => void act(() => greenFinanceApi.publishReport(r.id), isAr ? 'نُشر' : 'Published')}>
                          {isAr ? 'نشر' : 'Publish'}</Button>}
                      <Button size="sm" variant="ghost" onClick={() => void greenFinanceApi.reportXlsx(r.id, isAr ? 'ar' : 'en')}>XLSX</Button>
                      <Button size="sm" variant="ghost" onClick={() => void greenFinanceApi.reportDocx(r.id, isAr ? 'ar' : 'en')}>DOCX</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>}
    </Card>
  );
}

// ─── Portfolio ───────────────────────────────────────────────────────────────

function PortfolioTab({ summary, reload }: { summary: GpSummaryDto | null; reload: () => void }) {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [assets, setAssets] = useState<GpAssetDto[]>([]);
  const [taxonomy, setTaxonomy] = useState<GpTaxonomyDto[]>([]);
  const [form, setForm] = useState({ assetCode: '', nameEn: '', sector: '', exposureSarMn: '' });

  const load = useCallback(() => {
    greenPortfolioApi.assets().then(setAssets).catch(() => setAssets([]));
    greenPortfolioApi.taxonomy().then(setTaxonomy).catch(() => setTaxonomy([]));
  }, []);
  useEffect(() => { load(); }, [load]);

  async function create() {
    if (!form.assetCode || !form.nameEn || !form.exposureSarMn) {
      toast.danger(isAr ? 'الرمز والاسم والتعرض مطلوبة' : 'Code, name and exposure required'); return;
    }
    try {
      await greenPortfolioApi.createAsset({ ...form, exposureSarMn: Number(form.exposureSarMn) });
      toast.success(isAr ? 'أُضيف الأصل' : 'Asset added');
      setForm({ assetCode: '', nameEn: '', sector: '', exposureSarMn: '' });
      load(); reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  async function proposeTag(a: GpAssetDto) {
    const code = prompt((isAr ? 'رمز التصنيف الأخضر:\n' : 'Taxonomy code:\n')
      + taxonomy.map(t => `${t.code} — ${isAr && t.nameAr ? t.nameAr : t.nameEn}`).join('\n'));
    if (!code) return;
    try {
      await greenPortfolioApi.proposeTag(a.id, code.trim(), 'ELIGIBLE');
      toast.success(isAr ? 'اقتُرح الوسم' : 'Tag proposed');
      load(); reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Propose failed'); }
  }

  async function decideTag(a: GpAssetDto, approve: boolean) {
    try {
      await greenPortfolioApi.decideTag(a.id, approve);
      toast.success(approve ? (isAr ? 'اعتُمد الوسم' : 'Tag approved') : (isAr ? 'رُفض' : 'Rejected'));
      load(); reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Decision failed'); }
  }

  return (
    <Card title={isAr ? 'المحفظة الخضراء — الوسم ونسبة GAR' : 'Green portfolio — tagging & GAR'} Icon={Briefcase}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.assetCode} onChange={e => setForm(f => ({ ...f, assetCode: e.target.value }))}
            placeholder="AST-01" style={{ ...INPUT, minWidth: 90 }} />
          <input value={form.nameEn} onChange={e => setForm(f => ({ ...f, nameEn: e.target.value }))}
            placeholder={isAr ? 'الاسم' : 'Name'} style={INPUT} />
          <input value={form.sector} onChange={e => setForm(f => ({ ...f, sector: e.target.value }))}
            placeholder={isAr ? 'القطاع' : 'Sector'} style={{ ...INPUT, minWidth: 90 }} />
          <input type="number" value={form.exposureSarMn} onChange={e => setForm(f => ({ ...f, exposureSarMn: e.target.value }))}
            placeholder={isAr ? 'التعرض (م ر.س)' : 'Exposure (SAR mn)'} style={INPUT} />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void create()}>{isAr ? 'إضافة' : 'Add'}</Button>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => { load(); reload(); }} />
        </div>
      }>
      {summary && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10, marginBottom: 12 }}>
          {[
            { l: 'GAR', v: `${fmt(summary.greenAssetRatioPct)}%` },
            { l: isAr ? 'التعرض المؤهل' : 'Eligible exposure', v: fmt(summary.eligibleExposureSarMn) },
            { l: isAr ? 'جودة البيانات' : 'Weighted DQ', v: fmt(summary.weightedDataQuality) },
            { l: isAr ? 'انبعاثات ممولة' : 'Financed tCO₂e', v: fmt(summary.financedEmissionsTco2e) },
            { l: isAr ? 'مرتبطة بأصول' : 'Asset-linked tCO₂e', v: fmt(summary.assetLinkedEmissionsTco2e) },
            { l: isAr ? 'وسوم معلقة' : 'Pending tags', v: String(summary.pendingTags) },
          ].map(k => (
            <div key={k.l} style={{ border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: '8px 10px' }}>
              <div style={{ fontSize: 10.5, color: 'var(--ink-3)' }}>{k.l}</div>
              <div style={{ fontSize: 17, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{k.v}</div>
            </div>
          ))}
        </div>
      )}
      {assets.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا أصول.' : 'No assets.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الأصل' : 'Asset'}</th><th>{isAr ? 'القطاع' : 'Sector'}</th>
              <th>{isAr ? 'التعرض' : 'Exposure'}</th><th>{isAr ? 'التصنيف' : 'Taxonomy'}</th>
              <th>{isAr ? 'الأهلية' : 'Eligibility'}</th><th></th>
            </tr></thead>
            <tbody>
              {assets.map(a => (
                <tr key={a.id}>
                  <td className="text-2xs">{a.assetCode} — {isAr && a.nameAr ? a.nameAr : a.nameEn}</td>
                  <td className="text-2xs">{a.sector ?? '—'}</td>
                  <td className="mono text-2xs">{fmt(a.exposureSarMn)}</td>
                  <td className="mono text-2xs">{a.taxonomyCode ?? '—'}</td>
                  <td><Badge tone={tone(a.eligibilityStatus)} dot>{a.eligibilityStatus}</Badge></td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    {a.eligibilityStatus === 'PROPOSED'
                      ? <>
                          <Button size="sm" variant="ghost" Icon={CheckCircle2} onClick={() => void decideTag(a, true)} />
                          <Button size="sm" variant="ghost" Icon={XCircle} onClick={() => void decideTag(a, false)} />
                        </>
                      : <Button size="sm" variant="ghost" onClick={() => void proposeTag(a)}>{isAr ? 'وسم' : 'Tag'}</Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
    </Card>
  );
}

// ─── Credits ─────────────────────────────────────────────────────────────────

function CreditsTab({ credits, reload }: { credits: GpCreditDto[]; reload: () => void }) {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [form, setForm] = useState({ registry: 'RVCMC', serialRange: '', vintageYear: String(new Date().getFullYear() - 1), quantityTco2e: '' });

  async function purchase() {
    if (!form.serialRange || !form.quantityTco2e) {
      toast.danger(isAr ? 'النطاق التسلسلي والكمية مطلوبان' : 'Serial range and quantity required'); return;
    }
    try {
      await greenPortfolioApi.purchase({
        registry: form.registry, serialRange: form.serialRange,
        vintageYear: Number(form.vintageYear), quantityTco2e: Number(form.quantityTco2e),
      });
      toast.success(isAr ? 'سُجِّل الشراء' : 'Purchase recorded');
      setForm(f => ({ ...f, serialRange: '', quantityTco2e: '' }));
      reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Purchase failed'); }
  }

  async function retire(c: GpCreditDto) {
    const cert = prompt(isAr ? 'مرجع شهادة السجل:' : 'Registry certificate reference:');
    if (!cert) return;
    const purpose = prompt(isAr ? 'غرض السحب (أي مستهدف/التزام):' : 'Retirement purpose (which target/claim):');
    if (!purpose) return;
    try {
      await greenPortfolioApi.retire(c.id, cert, purpose);
      toast.success(isAr ? 'سُحبت الأرصدة' : 'Credits retired');
      reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Retire failed (offset cap / no reduction target?)'); }
  }

  return (
    <Card title={isAr ? 'أرصدة الكربون الطوعية (RVCMC)' : 'Voluntary carbon credits (RVCMC)'} Icon={Coins}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.registry} onChange={e => setForm(f => ({ ...f, registry: e.target.value }))}
            placeholder={isAr ? 'السجل' : 'Registry'} style={{ ...INPUT, minWidth: 90 }} />
          <input value={form.serialRange} onChange={e => setForm(f => ({ ...f, serialRange: e.target.value }))}
            placeholder={isAr ? 'النطاق التسلسلي' : 'Serial range'} style={INPUT} />
          <input type="number" value={form.vintageYear} onChange={e => setForm(f => ({ ...f, vintageYear: e.target.value }))}
            style={{ ...INPUT, minWidth: 80 }} />
          <input type="number" value={form.quantityTco2e} onChange={e => setForm(f => ({ ...f, quantityTco2e: e.target.value }))}
            placeholder="tCO₂e" style={{ ...INPUT, minWidth: 80 }} />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void purchase()}>{isAr ? 'شراء' : 'Purchase'}</Button>
        </div>
      }>
      <div style={{ ...EMPTY, paddingTop: 0 }}>
        <TrendingUp size={13} style={{ verticalAlign: -2, marginInlineEnd: 4 }} />
        {isAr
          ? 'السحب مقيد بـ 10% من إجمالي الخفض المفصح عنه في المستهدفات (نزاهة REG-11).'
          : 'Retirement is capped at 10% of the disclosed gross reduction across active targets (REG-11 integrity).'}
      </div>
      {credits.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا أرصدة.' : 'No credits.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'السجل' : 'Registry'}</th><th>{isAr ? 'النطاق' : 'Serial'}</th>
              <th>{isAr ? 'سنة الإصدار' : 'Vintage'}</th><th>tCO₂e</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th><th>{isAr ? 'الغرض' : 'Purpose'}</th><th></th>
            </tr></thead>
            <tbody>
              {credits.map(c => (
                <tr key={c.id}>
                  <td className="text-2xs">{c.registry}</td>
                  <td className="mono text-2xs">{c.serialRange}</td>
                  <td className="mono text-2xs">{c.vintageYear}</td>
                  <td className="mono text-2xs">{fmt(c.quantityTco2e)}</td>
                  <td><Badge tone={tone(c.status)} dot>{c.status}</Badge></td>
                  <td className="text-2xs">{c.retirementPurpose ?? '—'}</td>
                  <td>
                    {c.status === 'PURCHASED' &&
                      <Button size="sm" variant="ghost" onClick={() => void retire(c)}>{isAr ? 'سحب' : 'Retire'}</Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
    </Card>
  );
}
