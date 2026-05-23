import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, RefreshCw, Play, Copy, AlertCircle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type IrPlaybook } from '../../lib/socops';

/**
 * Incident-Response playbook catalog.
 *
 * Two list panels:
 *   • Active playbooks tenant has already cloned
 *   • System templates (6 seed: Phishing, Ransomware, BEC, DDoS, Insider,
 *     BCP-Activation) — one click clones and surfaces in the active list.
 *
 * The "Invoke" action fires the IR_PLAYBOOK_INVOKED trigger handled by
 * V151 rule AR-128 — visible to the SOC manager + creates an AAR row in BCM.
 */
type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Tab = 'active' | 'templates';

const SEVERITY_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger',
  HIGH: 'warning',
  MEDIUM: 'info',
  LOW: 'neutral',
};

const STATUS_TONE: Record<string, 'success' | 'info' | 'neutral'> = {
  ACTIVE: 'success',
  TEMPLATE: 'info',
  DRAFT: 'neutral',
  ARCHIVED: 'neutral',
};

export function IrPlaybooksPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [active, setActive] = useState<IrPlaybook[]>([]);
  const [templates, setTemplates] = useState<IrPlaybook[]>([]);
  const [tab, setTab] = useState<Tab>('active');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading');
    setErrMsg(null);
    // Per-call .catch() — if templates endpoint fails the playbooks tab
    // should still render (and vice versa). Whole-page error only when
    // BOTH calls fail.
    let anyError: unknown = null;
    const [a, t] = await Promise.all([
      socopsApi.playbooks().catch((e) => { anyError = e; return [] as IrPlaybook[]; }),
      socopsApi.templates().catch((e) => { anyError = e; return [] as IrPlaybook[]; }),
    ]);
    setActive(a);
    setTemplates(t);
    if (a.length === 0 && t.length === 0 && anyError) {
      setErrMsg((anyError as Error)?.message ?? String(anyError));
      setState('error');
    } else {
      setState('ready');
    }
  }
  useEffect(() => { void load(); }, []);

  async function doClone(templateCode: string) {
    try {
      await socopsApi.cloneTemplate(templateCode);
      toast.success(isAr ? 'تم نسخ القالب' : 'Template cloned');
      void load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Clone failed');
    }
  }
  async function doInvoke(id: number) {
    setBusyId(id);
    try {
      await socopsApi.invokePlaybook(id);
      toast.success(isAr ? 'تم تفعيل الدليل — راجع المركز' : 'Playbook invoked — check SOC manager');
      void load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Invoke failed');
    } finally {
      setBusyId(null);
    }
  }

  const counts = useMemo(() => ({
    active: active.length,
    invocations: active.reduce((sum, p) => sum + (p.invocationCount ?? 0), 0),
    templates: templates.length,
    critical: active.filter(p => p.severityTrigger === 'CRITICAL').length,
  }), [active, templates]);

  const rows = tab === 'active' ? active : templates;

  return (
    <Shell title={isAr ? 'أدلة الاستجابة' : 'IR playbooks'}
           sub={isAr ? 'دفاتر تشغيل المركز للأحداث الكبرى' : "SOC runbooks for major incidents"}>
      <PageHeader
        eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'أدلة الاستجابة (Playbooks)' : 'Incident Response Playbooks'}
        sub={isAr ? 'NIST SP 800-61: اكتشاف → فرز → احتواء → إزالة → استعادة → دروس مستفادة'
                  : 'NIST SP 800-61 phases — DETECT → TRIAGE → CONTAIN → ERADICATE → RECOVER → LESSONS_LEARNED'}
        actions={
          <Button variant="ghost" size="sm" onClick={() => void load()}>
            <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
          </Button>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={BookOpen}  label={isAr ? 'أدلة نشطة' : 'Active playbooks'}    value={String(counts.active)}      tone="info" />
        <Kpi Icon={Play}      label={isAr ? 'مرات التفعيل' : 'Total invocations'} value={String(counts.invocations)} tone="info" />
        <Kpi Icon={AlertCircle} label={isAr ? 'لأحداث حرجة' : 'For CRITICAL'}     value={String(counts.critical)}    tone={counts.critical > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Copy}      label={isAr ? 'قوالب جاهزة' : 'System templates'}   value={String(counts.templates)}   tone="neutral" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12 }}>
        {([
          { id: 'active',    label: isAr ? 'النشطة'    : 'Active',     count: counts.active },
          { id: 'templates', label: isAr ? 'القوالب'  : 'Templates',  count: counts.templates },
        ] as { id: Tab; label: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setTab(td.id)}
            style={{
              padding: '8px 12px', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === td.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
              borderRadius: 'var(--r-sm)', cursor: 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            {td.label}
            <Badge tone={tab === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {state === 'loading' && (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        )}
        {state === 'error' && (
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}>
            <div style={{ color: 'var(--danger-fg)', marginBottom: 8 }}>{errMsg ?? 'Error'}</div>
            <Button size="sm" variant="primary" onClick={() => void load()}>
              {isAr ? 'إعادة المحاولة' : 'Retry'}
            </Button>
          </div>
        )}
        {state === 'ready' && rows.length === 0 && (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>
        )}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{isAr ? 'الرمز' : 'Code'}</th>
                <th>{isAr ? 'العنوان' : 'Title'}</th>
                <th>{isAr ? 'التصنيف' : 'Category'}</th>
                <th>{isAr ? 'الخطورة' : 'Severity trigger'}</th>
                <th>{isAr ? 'MTTD/MTTR (دقائق)' : 'Target MTTD/MTTR (min)'}</th>
                <th>{isAr ? 'الأطر' : 'Frameworks'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'الإصدار' : 'Version'}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map(p => (
                <tr key={p.id}>
                  <td className="mono" style={{ fontSize: 11 }}>
                    <Link to={`/socops/playbooks/${p.id}`}
                          style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                      {p.playbookCode}
                    </Link>
                  </td>
                  <td style={{ fontSize: 11 }}>
                    <strong>{isAr ? (p.titleAr ?? p.titleEn) : p.titleEn}</strong>
                  </td>
                  <td style={{ fontSize: 11 }}>{p.category}</td>
                  <td>
                    {p.severityTrigger
                      ? <Badge tone={SEVERITY_TONE[p.severityTrigger] ?? 'neutral'}>{p.severityTrigger}</Badge>
                      : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                  </td>
                  <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {p.targetMttdMinutes ?? '—'} / {p.targetMttrMinutes ?? '—'}
                  </td>
                  <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{p.alignedFrameworks ?? '—'}</td>
                  <td><Badge tone={STATUS_TONE[p.status] ?? 'neutral'}>{p.status}</Badge></td>
                  <td className="mono" style={{ fontSize: 10 }}>v{p.version}</td>
                  <td>
                    {tab === 'templates' && (
                      <Button size="sm" variant="primary"
                        onClick={() => void doClone(p.playbookCode)}>
                        <Copy size={12} /> {isAr ? 'نسخ' : 'Clone'}
                      </Button>
                    )}
                    {tab === 'active' && (
                      <Button size="sm" variant="primary" disabled={busyId === p.id}
                        onClick={() => void doInvoke(p.id)}>
                        <Play size={12} /> {isAr ? 'تفعيل' : 'Invoke'}
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
