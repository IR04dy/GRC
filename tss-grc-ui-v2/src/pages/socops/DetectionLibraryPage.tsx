// SOC-E — the SOC detection-rule / use-case library, MITRE ATT&CK-mapped. List + create.
// Every rule carries a required ATT&CK technique so the SOC can reason about coverage and a
// threat-intel action can link an IOC to the detection that watches for it.
import { useEffect, useMemo, useState } from 'react';
import { Radar, RefreshCw, Plus, ShieldCheck, Crosshair, Power } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Input } from '../../components/ui/Input';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type DetectionRule, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const SEVERITY_TONE: Record<string, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low' | 'neutral'> = {
  CRITICAL: 'risk-crit', HIGH: 'risk-high', MEDIUM: 'risk-med', LOW: 'risk-low', INFO: 'neutral',
};
const STATUS_TONE: Record<string, 'success' | 'info' | 'warning' | 'neutral' | 'danger'> = {
  ACTIVE: 'success', TUNING: 'warning', DRAFT: 'info', DISABLED: 'neutral', RETIRED: 'danger',
};

const TYPE_OPTIONS = ['SIEM', 'EDR', 'NDR', 'IDS', 'IPS', 'CORRELATION', 'THREAT_HUNT', 'UEBA', 'SOAR', 'CLOUD', 'CUSTOM'];
const STATUS_OPTIONS = ['DRAFT', 'ACTIVE', 'TUNING', 'DISABLED', 'RETIRED'];
const SEV_OPTIONS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

export function DetectionLibraryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<DetectionRule> | null>(null);
  const [technique, setTechnique] = useState('');
  const [query, setQuery] = useState('');
  const [errMsg, setErrMsg] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.detections(0, 100, query || undefined); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [query]);

  const rows = page?.content ?? [];
  const counts = useMemo(() => ({
    total: rows.length,
    active: rows.filter(r => r.status === 'ACTIVE').length,
    enabled: rows.filter(r => r.enabled).length,
    techniques: new Set(rows.flatMap(r => (r.mitreTechniques ?? '').split(',').map(s => s.trim()).filter(Boolean))).size,
  }), [rows]);

  const createFields: FieldDef[] = [
    { key: 'ruleCode',        kind: 'text',     label: isAr ? 'رمز القاعدة' : 'Rule code', required: true, span: 6, placeholder: 'DET-001' },
    { key: 'detectionType',   kind: 'select',   label: isAr ? 'النوع' : 'Detection type', required: true, span: 6, options: TYPE_OPTIONS.map(v => ({ value: v, label: v })) },
    { key: 'titleEn',         kind: 'text',     label: isAr ? 'العنوان (EN)' : 'Title (EN)', required: true, span: 12 },
    { key: 'titleAr',         kind: 'text',     label: isAr ? 'العنوان (AR)' : 'Title (AR)', span: 12 },
    { key: 'mitreTechniques', kind: 'text',     label: isAr ? 'تقنيات MITRE (مطلوب)' : 'MITRE technique(s) — required', required: true, span: 6, placeholder: 'T1566, T1059.001' },
    { key: 'mitreTactics',    kind: 'text',     label: isAr ? 'تكتيكات MITRE' : 'MITRE tactic(s)', span: 6, placeholder: 'TA0001' },
    { key: 'platform',        kind: 'text',     label: isAr ? 'المنصة' : 'Platform', span: 6, placeholder: 'Splunk / Sentinel …' },
    { key: 'logSource',       kind: 'text',     label: isAr ? 'مصدر السجل' : 'Log source', span: 6 },
    { key: 'severity',        kind: 'select',   label: isAr ? 'الخطورة' : 'Severity', span: 6, options: SEV_OPTIONS.map(v => ({ value: v, label: v })) },
    { key: 'status',          kind: 'select',   label: isAr ? 'الحالة' : 'Status', span: 6, options: STATUS_OPTIONS.map(v => ({ value: v, label: v })) },
    { key: 'detectionLogic',  kind: 'textarea', label: isAr ? 'منطق الكشف / الاستعلام' : 'Detection logic / query', rows: 3, span: 12 },
    { key: 'description',     kind: 'textarea', label: isAr ? 'الوصف' : 'Description', rows: 2, span: 12 },
  ];

  return (
    <Shell title={isAr ? 'مكتبة الكشف' : 'Detection library'} sub={isAr ? 'قواعد الكشف بربط MITRE ATT&CK' : 'Detection rules mapped to MITRE ATT&CK'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'مكتبة الكشف' : 'Detection library'}
        sub={isAr ? 'قواعد SIEM / EDR / NDR وحالات الاستخدام — مربوطة بإطار MITRE ATT&CK' : 'SIEM / EDR / NDR rules and use-cases — mapped to MITRE ATT&CK'}
        actions={<>
          <Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>
          <Button variant="primary" size="sm" onClick={() => setCreateOpen(true)}><Plus size={14} /> {isAr ? 'قاعدة كشف' : 'New detection'}</Button>
        </>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Radar}       label={isAr ? 'الإجمالي' : 'Total rules'}    value={String(counts.total)}      tone="neutral" />
        <Kpi Icon={ShieldCheck} label={isAr ? 'نشطة' : 'Active'}             value={String(counts.active)}     tone={counts.active > 0 ? 'success' : 'neutral'} />
        <Kpi Icon={Power}       label={isAr ? 'مُفعّلة' : 'Enabled'}          value={String(counts.enabled)}    tone="info" />
        <Kpi Icon={Crosshair}   label={isAr ? 'تقنيات ATT&CK' : 'ATT&CK techniques'} value={String(counts.techniques)} tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12, maxWidth: 360 }}>
        <Input value={technique} onChange={e => setTechnique(e.target.value)}
          placeholder={isAr ? 'تصفية بتقنية MITRE، مثل T1566' : 'Filter by MITRE technique, e.g. T1566'}
          onKeyDown={e => { if (e.key === 'Enter') setQuery(technique.trim()); }} />
        <Button variant="secondary" size="sm" onClick={() => setQuery(technique.trim())}>{isAr ? 'تصفية' : 'Filter'}</Button>
        {query && <Button variant="ghost" size="sm" onClick={() => { setTechnique(''); setQuery(''); }}>{isAr ? 'مسح' : 'Clear'}</Button>}
      </div>

      <Card>
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && <div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>{errMsg}</div>}
        {state === 'ready' && rows.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'لا توجد قواعد كشف بعد.' : 'No detection rules yet.'}</div>}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th>
              <th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'المنصة' : 'Platform'}</th>
              <th>MITRE ATT&CK</th>
              <th>{isAr ? 'الخطورة' : 'Severity'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{rows.map(d => (
              <tr key={d.id}>
                <td className="mono" style={{ fontSize: 11 }}>{d.ruleCode}</td>
                <td style={{ fontSize: 12, maxWidth: 300 }}>
                  <strong>{isAr ? (d.titleAr ?? d.titleEn) : d.titleEn}</strong>
                  {!d.enabled && <Badge tone="neutral" className="ms-1">{isAr ? 'معطّلة' : 'disabled'}</Badge>}
                </td>
                <td><Badge tone="info">{d.detectionType}</Badge></td>
                <td style={{ fontSize: 11, color: 'var(--ink-3)' }}>{d.platform ?? '—'}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-2)', maxWidth: 200 }}>{d.mitreTechniques}</td>
                <td>{d.severity ? <Badge tone={SEVERITY_TONE[d.severity] ?? 'neutral'}>{d.severity}</Badge> : '—'}</td>
                <td><Badge tone={STATUS_TONE[d.status] ?? 'neutral'}>{d.status}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>

      <FormModal<Record<string, string>>
        open={createOpen}
        title={isAr ? 'قاعدة كشف جديدة' : 'New detection rule'}
        fields={createFields}
        initial={{ ruleCode: '', detectionType: 'SIEM', titleEn: '', titleAr: '', mitreTechniques: '', mitreTactics: '', platform: '', logSource: '', severity: 'MEDIUM', status: 'DRAFT', detectionLogic: '', description: '' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={async (v) => {
          await socopsApi.detectionCreate(v as Partial<DetectionRule>);
          toast.success(isAr ? 'تم إنشاء قاعدة الكشف' : 'Detection rule created');
          setCreateOpen(false);
          void load();
        }}
        submitLabel={isAr ? 'إنشاء' : 'Create'}
      />
    </Shell>
  );
}
