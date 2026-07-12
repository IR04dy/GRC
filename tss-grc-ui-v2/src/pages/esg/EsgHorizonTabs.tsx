import { useCallback, useEffect, useState } from 'react';
import { Target, BookOpenCheck, Factory, Plus, RefreshCw, ShieldAlert } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  esgHorizonApi, ApiError,
  type EsgTargetViewDto, type IfrsReqDto, type EnvPermitViewDto, type CbamLineDto,
} from '../../lib/api';

/**
 * ESG regulatory-horizon tabs (green-finance plan §5.4–§5.7 UI): org-wide targets
 * with piecewise trajectory + auto-CAPA sweep, the IFRS S1/S2 readiness checklist,
 * and environmental compliance (NCEC permits + violations, EU CBAM screen).
 */

const INPUT: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 110, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};
const EMPTY: React.CSSProperties = { padding: 14, color: 'var(--ink-3)', fontSize: 13 };
const fmt = (n?: number | null) => n == null ? '—' : Number(n).toLocaleString('en-US', { maximumFractionDigits: 2 });

// ─── Targets & transition (§5.5) ─────────────────────────────────────────────

export function EsgTargetsTab() {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [rows, setRows] = useState<EsgTargetViewDto[]>([]);
  const [form, setForm] = useState({
    targetCode: '', titleEn: '', scope: 'GHG_S1', unit: 'tCO2e',
    baselineYear: '2020', baselineValue: '', targetYear: '2030', targetValue: '',
  });

  const load = useCallback(async () => {
    try { setRows(await esgHorizonApi.targets()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load targets'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function create() {
    if (!form.targetCode || !form.titleEn || !form.baselineValue || !form.targetValue) {
      toast.danger(isAr ? 'أكمل الحقول المطلوبة' : 'Complete the required fields'); return;
    }
    try {
      await esgHorizonApi.createTarget({
        targetCode: form.targetCode, titleEn: form.titleEn, scope: form.scope, unit: form.unit,
        baselineYear: Number(form.baselineYear), baselineValue: Number(form.baselineValue),
        targetYear: Number(form.targetYear), targetValue: Number(form.targetValue), sgiAligned: true,
      });
      toast.success(isAr ? 'أُنشئ المستهدف' : 'Target created');
      setForm(f => ({ ...f, targetCode: '', titleEn: '', baselineValue: '', targetValue: '' }));
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  async function addActual(t: EsgTargetViewDto) {
    const year = prompt(isAr ? 'سنة القراءة:' : 'Actual year:');
    const value = year ? prompt(isAr ? 'القيمة:' : 'Value:') : null;
    if (!year || !value) return;
    try {
      await esgHorizonApi.addActual(t.target.id, Number(year), Number(value));
      toast.success(isAr ? 'سُجِّلت القراءة' : 'Actual recorded');
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function addMilestone(t: EsgTargetViewDto) {
    const year = prompt(isAr ? 'سنة المعلم (داخل المدى):' : 'Milestone year (inside the span):');
    const value = year ? prompt(isAr ? 'القيمة:' : 'Value:') : null;
    if (!year || !value) return;
    try {
      await esgHorizonApi.addMilestone(t.target.id, Number(year), Number(value));
      toast.success(isAr ? 'أُضيف المعلم — المسار أصبح متعرجاً عبره' : 'Milestone added — trajectory now bends through it');
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function sweep() {
    try {
      const r = await esgHorizonApi.trajectorySweep();
      toast.success(isAr ? `أُثير ${r.capasRaised} إجراء تصحيحي` : `${r.capasRaised} auto-CAPA raised`);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Sweep failed'); }
  }

  return (
    <Card title={isAr ? 'المستهدفات وخطة التحوّل (صافي الصفر ٢٠٦٠)' : 'Targets & transition (net-zero 2060)'} Icon={Target}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.targetCode} onChange={e => setForm(f => ({ ...f, targetCode: e.target.value }))}
            placeholder="TGT-01" style={{ ...INPUT, minWidth: 80 }} />
          <input value={form.titleEn} onChange={e => setForm(f => ({ ...f, titleEn: e.target.value }))}
            placeholder={isAr ? 'العنوان' : 'Title'} style={{ ...INPUT, minWidth: 150 }} />
          <select value={form.scope} onChange={e => setForm(f => ({ ...f, scope: e.target.value }))} style={INPUT}>
            {['GHG_S1', 'GHG_S2', 'GHG_S3', 'ENERGY', 'WATER', 'WASTE', 'RENEWABLES_SHARE'].map(s => <option key={s}>{s}</option>)}
          </select>
          <input type="number" value={form.baselineYear} onChange={e => setForm(f => ({ ...f, baselineYear: e.target.value }))}
            style={{ ...INPUT, minWidth: 70 }} title={isAr ? 'سنة الأساس' : 'Baseline year'} />
          <input type="number" value={form.baselineValue} onChange={e => setForm(f => ({ ...f, baselineValue: e.target.value }))}
            placeholder={isAr ? 'قيمة الأساس' : 'Baseline'} style={{ ...INPUT, minWidth: 85 }} />
          <input type="number" value={form.targetYear} onChange={e => setForm(f => ({ ...f, targetYear: e.target.value }))}
            style={{ ...INPUT, minWidth: 70 }} title={isAr ? 'سنة المستهدف' : 'Target year'} />
          <input type="number" value={form.targetValue} onChange={e => setForm(f => ({ ...f, targetValue: e.target.value }))}
            placeholder={isAr ? 'المستهدف' : 'Target'} style={{ ...INPUT, minWidth: 85 }} />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void create()}>{isAr ? 'إنشاء' : 'Create'}</Button>
          <Button size="sm" variant="ghost" Icon={ShieldAlert} onClick={() => void sweep()}
            title={isAr ? 'فحص الانحراف → إجراء تصحيحي' : 'Trajectory sweep → auto-CAPA'} />
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
        </div>
      }>
      {rows.length === 0
        ? <div style={EMPTY}>{isAr ? 'لا مستهدفات.' : 'No targets.'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th><th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'النطاق' : 'Scope'}</th>
              <th>{isAr ? 'الأساس → المستهدف' : 'Baseline → Target'}</th>
              <th>{isAr ? 'معالم' : 'Milestones'}</th>
              <th>{isAr ? 'آخر فعلي/متوقع' : 'Latest actual / expected'}</th>
              <th>{isAr ? 'المسار' : 'Trajectory'}</th><th></th>
            </tr></thead>
            <tbody>
              {rows.map(v => {
                const latest = v.actuals[v.actuals.length - 1];
                return (
                  <tr key={v.target.id}>
                    <td className="mono text-2xs">{v.target.targetCode}</td>
                    <td className="text-2xs">{isAr && v.target.titleAr ? v.target.titleAr : v.target.titleEn}</td>
                    <td><Badge tone="info">{v.target.scope}</Badge></td>
                    <td className="mono text-2xs">
                      {fmt(v.target.baselineValue)} ({v.target.baselineYear}) → {fmt(v.target.targetValue)} ({v.target.targetYear})
                    </td>
                    <td className="mono text-2xs">{v.milestones.length || '—'}</td>
                    <td className="mono text-2xs">
                      {latest ? `${fmt(latest.value)} (${latest.year}) / ${fmt(v.expectedForLatestActual)}` : '—'}
                    </td>
                    <td><Badge tone={v.trajectory === 'ON_TRACK' ? 'success' : v.trajectory === 'OFF_TRACK' ? 'danger' : 'neutral'} dot>
                      {v.trajectory}</Badge></td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      <Button size="sm" variant="ghost" onClick={() => void addActual(v)}>{isAr ? 'فعلي' : 'Actual'}</Button>
                      <Button size="sm" variant="ghost" onClick={() => void addMilestone(v)}>{isAr ? 'معلم' : 'Milestone'}</Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>}
    </Card>
  );
}

// ─── IFRS S1/S2 readiness (§5.4) ─────────────────────────────────────────────

export function IfrsTab() {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [rows, setRows] = useState<IfrsReqDto[]>([]);
  const [summary, setSummary] = useState<{ total: number; covered: number; partial: number; gap: number; coveragePct: number } | null>(null);

  const load = useCallback(async () => {
    try {
      setRows(await esgHorizonApi.ifrs());
      setSummary(await esgHorizonApi.ifrsSummary());
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load checklist'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function setStatus(r: IfrsReqDto, status: string) {
    const coverageRef = status !== 'GAP'
      ? prompt(isAr ? 'مرجع التغطية (أين يُلبّى المتطلب):' : 'Coverage reference (where it is met):') ?? undefined
      : undefined;
    try {
      await esgHorizonApi.setIfrsStatus(r.id, status, coverageRef);
      toast.success(`${r.reqCode} → ${status}`);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Update failed'); }
  }

  return (
    <Card title={isAr ? 'جاهزية IFRS S1/S2 (اعتماد SOCPA)' : 'IFRS S1/S2 readiness (SOCPA)'} Icon={BookOpenCheck}
      action={summary && (
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <Badge tone={summary.coveragePct >= 80 ? 'success' : summary.coveragePct >= 50 ? 'warning' : 'danger'} dot>
            {isAr ? `التغطية ${summary.coveragePct}%` : `${summary.coveragePct}% coverage`}
          </Badge>
          <Badge tone="neutral">{summary.covered} ✓ · {summary.partial} ± · {summary.gap} ✗</Badge>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} />
        </div>
      )}>
      <table className="grc-table">
        <thead><tr>
          <th>{isAr ? 'الرمز' : 'Code'}</th><th>{isAr ? 'المعيار' : 'Std'}</th>
          <th>{isAr ? 'الركيزة' : 'Pillar'}</th><th>{isAr ? 'المتطلب' : 'Requirement'}</th>
          <th>{isAr ? 'الحالة' : 'Status'}</th><th>{isAr ? 'مرجع التغطية' : 'Coverage'}</th><th></th>
        </tr></thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.id}>
              <td className="mono text-2xs">{r.reqCode}</td>
              <td><Badge tone="info">{r.standard}</Badge></td>
              <td className="text-2xs">{r.pillar}</td>
              <td className="text-2xs">{isAr && r.titleAr ? r.titleAr : r.titleEn}</td>
              <td><Badge tone={r.status === 'COVERED' ? 'success' : r.status === 'PARTIAL' ? 'warning' : 'danger'} dot>{r.status}</Badge></td>
              <td className="text-2xs">{r.coverageRef ?? '—'}</td>
              <td style={{ whiteSpace: 'nowrap' }}>
                {['COVERED', 'PARTIAL', 'GAP'].filter(s => s !== r.status).map(s =>
                  <Button key={s} size="sm" variant="ghost" onClick={() => void setStatus(r, s)}>{s}</Button>)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

// ─── Env compliance: NCEC permits + CBAM (§5.6/§5.7) ─────────────────────────

export function EnvComplianceTab() {
  const { lang } = useI18n(); const isAr = lang === 'ar'; const toast = useToast();
  const [permits, setPermits] = useState<EnvPermitViewDto[]>([]);
  const [cbam, setCbam] = useState<CbamLineDto[]>([]);
  const [cbamSummary, setCbamSummary] = useState<{ totalEmbeddedTco2e: number; byGroup: Record<string, number> } | null>(null);
  const [pForm, setPForm] = useState({ permitNumber: '', facilityEn: '', expiryDate: '' });
  const [cForm, setCForm] = useState({
    cnGroup: 'ALUMINIUM', productDescEn: '', periodYear: String(new Date().getFullYear()),
    periodQuarter: '1', exportVolumeTonnes: '', embeddedTco2ePerTonne: '',
  });

  const load = useCallback(async () => {
    try {
      setPermits(await esgHorizonApi.permits());
      setCbam(await esgHorizonApi.cbam());
      setCbamSummary(await esgHorizonApi.cbamSummary());
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load env data'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void load(); }, [load]);

  async function createPermit() {
    if (!pForm.permitNumber || !pForm.facilityEn || !pForm.expiryDate) {
      toast.danger(isAr ? 'رقم التصريح والمنشأة وتاريخ الانتهاء مطلوبة' : 'Permit number, facility, expiry required'); return;
    }
    try {
      await esgHorizonApi.createPermit(pForm);
      toast.success(isAr ? 'سُجِّل التصريح' : 'Permit registered');
      setPForm({ permitNumber: '', facilityEn: '', expiryDate: '' });
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  async function sweep() {
    try {
      const r = await esgHorizonApi.permitSweep(90);
      toast.success(isAr ? `أُثير ${r.capasRaised} إجراء تصحيحي` : `${r.capasRaised} auto-CAPA raised`);
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Sweep failed'); }
  }

  async function violation(p: EnvPermitViewDto) {
    const desc = prompt(isAr ? 'وصف المخالفة:' : 'Violation description:');
    if (!desc) return;
    try {
      await esgHorizonApi.recordViolation(p.permit.id, {
        violationDate: new Date().toISOString().slice(0, 10), descriptionEn: desc, severity: 'HIGH',
      });
      toast.success(isAr ? 'سُجِّلت المخالفة وفُتح إجراء تصحيحي' : 'Violation recorded — CAPA opened');
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  async function createCbam() {
    if (!cForm.productDescEn || !cForm.exportVolumeTonnes || !cForm.embeddedTco2ePerTonne) {
      toast.danger(isAr ? 'أكمل حقول CBAM' : 'Complete the CBAM fields'); return;
    }
    try {
      await esgHorizonApi.createCbam({
        cnGroup: cForm.cnGroup, productDescEn: cForm.productDescEn,
        periodYear: Number(cForm.periodYear), periodQuarter: Number(cForm.periodQuarter),
        exportVolumeTonnes: Number(cForm.exportVolumeTonnes),
        embeddedTco2ePerTonne: Number(cForm.embeddedTco2ePerTonne),
      });
      toast.success(isAr ? 'أُضيف سطر CBAM' : 'CBAM line added');
      setCForm(f => ({ ...f, productDescEn: '', exportVolumeTonnes: '', embeddedTco2ePerTonne: '' }));
      await load();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Create failed'); }
  }

  return (
    <>
      <Card title={isAr ? 'التصاريح البيئية (المركز الوطني للرقابة على الالتزام البيئي)' : 'NCEC environmental permits'} Icon={Factory}
        action={
          <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <input value={pForm.permitNumber} onChange={e => setPForm(f => ({ ...f, permitNumber: e.target.value }))}
              placeholder={isAr ? 'رقم التصريح' : 'Permit no.'} style={INPUT} />
            <input value={pForm.facilityEn} onChange={e => setPForm(f => ({ ...f, facilityEn: e.target.value }))}
              placeholder={isAr ? 'المنشأة' : 'Facility'} style={INPUT} />
            <input type="date" value={pForm.expiryDate} onChange={e => setPForm(f => ({ ...f, expiryDate: e.target.value }))} style={INPUT} />
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => void createPermit()}>{isAr ? 'تسجيل' : 'Register'}</Button>
            <Button size="sm" variant="ghost" Icon={ShieldAlert} onClick={() => void sweep()}
              title={isAr ? 'فحص الانتهاء (90ي)' : 'Expiry sweep (90d)'} />
          </div>
        }>
        {permits.length === 0
          ? <div style={EMPTY}>{isAr ? 'لا تصاريح.' : 'No permits.'}</div>
          : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'التصريح' : 'Permit'}</th><th>{isAr ? 'المنشأة' : 'Facility'}</th>
                <th>{isAr ? 'ينتهي' : 'Expiry'}</th><th>{isAr ? 'أيام متبقية' : 'Days left'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th><th></th>
              </tr></thead>
              <tbody>
                {permits.map(v => (
                  <tr key={v.permit.id}>
                    <td className="mono text-2xs">{v.permit.permitNumber}</td>
                    <td className="text-2xs">{isAr && v.permit.facilityAr ? v.permit.facilityAr : v.permit.facilityEn}</td>
                    <td className="text-2xs">{v.permit.expiryDate}</td>
                    <td className="mono text-2xs" style={{ color: v.daysToExpiry < 0 ? 'var(--danger-fg, #b91c1c)' : undefined }}>
                      {v.daysToExpiry}
                    </td>
                    <td><Badge tone={v.permit.status === 'VALID' ? 'success' : v.permit.status === 'EXPIRED' ? 'danger' : 'warning'} dot>
                      {v.permit.status}</Badge></td>
                    <td>
                      <Button size="sm" variant="ghost" onClick={() => void violation(v)}>{isAr ? 'مخالفة' : 'Violation'}</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>}
      </Card>
      <div style={{ height: 16 }} />
      <Card title={isAr ? 'شاشة CBAM الأوروبية (الانبعاثات المضمّنة للصادرات)' : 'EU CBAM exposure screen'} Icon={Factory}
        action={
          <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <select value={cForm.cnGroup} onChange={e => setCForm(f => ({ ...f, cnGroup: e.target.value }))} style={INPUT}>
              {['ALUMINIUM', 'STEEL', 'CEMENT', 'FERTILISERS', 'HYDROGEN', 'ELECTRICITY'].map(g => <option key={g}>{g}</option>)}
            </select>
            <input value={cForm.productDescEn} onChange={e => setCForm(f => ({ ...f, productDescEn: e.target.value }))}
              placeholder={isAr ? 'المنتج' : 'Product'} style={{ ...INPUT, minWidth: 150 }} />
            <input type="number" value={cForm.exportVolumeTonnes} onChange={e => setCForm(f => ({ ...f, exportVolumeTonnes: e.target.value }))}
              placeholder={isAr ? 'الحجم (طن)' : 'Volume (t)'} style={{ ...INPUT, minWidth: 85 }} />
            <input type="number" value={cForm.embeddedTco2ePerTonne} onChange={e => setCForm(f => ({ ...f, embeddedTco2ePerTonne: e.target.value }))}
              placeholder="tCO₂e/t" style={{ ...INPUT, minWidth: 80 }} />
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => void createCbam()}>{isAr ? 'إضافة' : 'Add'}</Button>
          </div>
        }>
        {cbamSummary && (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', paddingBottom: 10 }}>
            <Badge tone="warning" dot>
              {isAr ? `الإجمالي ${fmt(cbamSummary.totalEmbeddedTco2e)} tCO₂e` : `Total ${fmt(cbamSummary.totalEmbeddedTco2e)} tCO₂e`}
            </Badge>
            {Object.entries(cbamSummary.byGroup).map(([g, v]) => <Badge key={g} tone="neutral">{g}: {fmt(v)}</Badge>)}
          </div>
        )}
        {cbam.length === 0
          ? <div style={EMPTY}>{isAr ? 'لا أسطر CBAM.' : 'No CBAM lines.'}</div>
          : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'المجموعة' : 'Group'}</th><th>{isAr ? 'المنتج' : 'Product'}</th>
                <th>{isAr ? 'الفترة' : 'Period'}</th><th>{isAr ? 'الحجم (طن)' : 'Volume (t)'}</th>
                <th>tCO₂e/t</th><th>{isAr ? 'الإجمالي' : 'Total tCO₂e'}</th><th>{isAr ? 'الأساس' : 'Basis'}</th>
              </tr></thead>
              <tbody>
                {cbam.map(c => (
                  <tr key={c.id}>
                    <td><Badge tone="info">{c.cnGroup}</Badge></td>
                    <td className="text-2xs">{c.productDescEn}</td>
                    <td className="mono text-2xs">{c.periodYear}-Q{c.periodQuarter}</td>
                    <td className="mono text-2xs">{fmt(c.exportVolumeTonnes)}</td>
                    <td className="mono text-2xs">{fmt(c.embeddedTco2ePerTonne)}</td>
                    <td className="mono text-2xs">{fmt(c.totalEmbeddedTco2e)}</td>
                    <td className="text-2xs">{c.dataBasis}</td>
                  </tr>
                ))}
              </tbody>
            </table>}
      </Card>
    </>
  );
}
