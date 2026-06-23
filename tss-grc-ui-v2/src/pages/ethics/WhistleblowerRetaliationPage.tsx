import { useEffect, useMemo, useState } from 'react';
import { ShieldCheck, ShieldAlert, Inbox, Gavel, UserX, Plus, Check, X } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  whistleblowerApi,
  type RetaliationComplaintResponse, type RetaliationStatus,
  type CaseResponse,
} from '../../lib/whistleblower';

const STATUS_LABEL: Record<RetaliationStatus, { en: string; ar: string }> = {
  RECEIVED:  { en: 'Received',  ar: 'وارد' },
  VALIDATED: { en: 'Validated', ar: 'مقبول للمراجعة' },
  UPHELD:    { en: 'Upheld',    ar: 'مُثبَت' },
  DISMISSED: { en: 'Dismissed', ar: 'مرفوض' },
};
const STATUS_TONE: Record<RetaliationStatus, BadgeTone> = {
  RECEIVED: 'info', VALIDATED: 'warning', UPHELD: 'danger', DISMISSED: 'neutral',
};

const INPUT_STYLE: React.CSSProperties = {
  padding: '6px 8px', fontSize: 13, width: '100%', border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

type ClaimForm = {
  relatedCaseId: string;
  claimantName: string;
  claimedActor: string;
  claimedAction: string;
  claimedAtPeriodStart: string;
};
const EMPTY_FORM: ClaimForm = { relatedCaseId: '', claimantName: '', claimedActor: '', claimedAction: '', claimedAtPeriodStart: '' };

/**
 * Whistleblower retaliation protection — surfaces
 * GET/POST /api/v1/ethics/whistleblower/retaliation (Sprint-13 sub-flow).
 *
 * An independent protector (HR Director / Compliance Officer) tracks claims
 * that a good-faith reporter suffered retaliation, linked to the originating
 * whistleblower case. The state machine is RECEIVED → VALIDATED →
 * UPHELD | DISMISSED. This page is the protector's register: file a claim
 * against a case, then validate / uphold / dismiss it. Anti-retaliation
 * protection is mandated by SAMA Corporate Governance Art.28 and CMA
 * Listed-Companies CG Art.87. Bilingual; statuses on semantic tones.
 */
export function WhistleblowerRetaliationPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<RetaliationComplaintResponse[]>([]);
  const [cases, setCases] = useState<CaseResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<ClaimForm>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [r, c] = await Promise.all([
        whistleblowerApi.retaliation.list(),
        whistleblowerApi.list().catch(() => [] as CaseResponse[]),
      ]);
      setRows(r);
      setCases(c);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : (isAr ? 'تعذّر تحميل البلاغات' : 'Failed to load complaints'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const kpis = useMemo(() => ({
    total: rows.length,
    received: rows.filter(r => r.status === 'RECEIVED').length,
    validated: rows.filter(r => r.status === 'VALIDATED').length,
    upheld: rows.filter(r => r.status === 'UPHELD').length,
    dismissed: rows.filter(r => r.status === 'DISMISSED').length,
  }), [rows]);

  const caseLabel = (id: number) => {
    const c = cases.find(x => x.id === id);
    if (!c) return `#${id}`;
    const subj = isAr ? (c.subjectAr || c.subjectEn) : c.subjectEn;
    return `${c.caseCode}${subj ? ` — ${subj}` : ''}`;
  };

  async function act(id: number, action: 'validate' | 'uphold' | 'dismiss') {
    setBusyId(id);
    try {
      const updated = await whistleblowerApi.retaliation[action](id);
      setRows(prev => prev.map(r => (r.id === id ? updated : r)));
      const msg = action === 'validate' ? (isAr ? 'تم قبول البلاغ للمراجعة' : 'Claim validated')
        : action === 'uphold' ? (isAr ? 'تم إثبات الانتقام' : 'Retaliation upheld')
        : (isAr ? 'تم رفض البلاغ' : 'Claim dismissed');
      toast.success(msg);
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (isAr ? 'تعذّر تنفيذ الإجراء' : 'Action failed'));
    } finally {
      setBusyId(null);
    }
  }

  async function submitClaim() {
    if (!form.relatedCaseId) { toast.danger(isAr ? 'اختر البلاغ المرتبط' : 'Select the related case'); return; }
    if (!form.claimedActor.trim() || !form.claimedAction.trim()) {
      toast.danger(isAr ? 'أدخل الجهة والفعل المزعوم' : 'Enter the alleged actor and action');
      return;
    }
    setSubmitting(true);
    try {
      await whistleblowerApi.retaliation.claim({
        relatedCaseId: Number(form.relatedCaseId),
        claimantName: form.claimantName.trim() || undefined,
        claimedActor: form.claimedActor.trim(),
        claimedAction: form.claimedAction.trim(),
        claimedAtPeriodStart: form.claimedAtPeriodStart || undefined,
      });
      toast.success(isAr ? 'تم تسجيل بلاغ الانتقام' : 'Retaliation claim filed');
      setForm(EMPTY_FORM);
      setShowForm(false);
      void load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (isAr ? 'تعذّر التسجيل' : 'Failed to file claim'));
    } finally {
      setSubmitting(false);
    }
  }

  const cols: Column<RetaliationComplaintResponse>[] = [
    {
      header: isAr ? 'البلاغ المرتبط' : 'Related case', width: '220px',
      cell: r => <span style={{ fontSize: 12.5, color: 'var(--ink-1)' }}>{caseLabel(r.relatedCaseId)}</span>,
    },
    {
      header: isAr ? 'المُبلِّغ' : 'Claimant', width: '140px',
      cell: r => r.claimantName
        ? <span style={{ fontSize: 12.5 }}>{r.claimantName}</span>
        : <span style={{ fontSize: 12, color: 'var(--ink-3)', fontStyle: 'italic' }}>{isAr ? 'مجهول' : 'Anonymous'}</span>,
    },
    {
      header: isAr ? 'الجهة المزعومة' : 'Alleged actor', width: '170px',
      cell: r => <span style={{ fontSize: 12.5 }}>{r.claimedActor}</span>,
    },
    {
      header: isAr ? 'الفعل المزعوم' : 'Alleged action',
      cell: r => <span style={{ fontSize: 12.5, color: 'var(--ink-2)' }}>{r.claimedAction}</span>,
    },
    {
      header: isAr ? 'منذ' : 'Since', width: '110px',
      cell: r => <span className="mono text-2xs text-muted">{r.claimedAtPeriodStart
        ? new Date(r.claimedAtPeriodStart).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB')
        : '—'}</span>,
    },
    {
      header: isAr ? 'الحالة' : 'Status', width: '130px',
      cell: r => <Badge tone={STATUS_TONE[r.status]}>{STATUS_LABEL[r.status][isAr ? 'ar' : 'en']}</Badge>,
    },
    {
      header: isAr ? 'الإجراء' : 'Action', width: '200px',
      cell: r => {
        const busy = busyId === r.id;
        if (r.status === 'RECEIVED') {
          return (
            <Button size="sm" variant="primary" Icon={ShieldCheck} disabled={busy} onClick={() => act(r.id, 'validate')}>
              {isAr ? 'قبول للمراجعة' : 'Validate'}
            </Button>
          );
        }
        if (r.status === 'VALIDATED') {
          return (
            <div style={{ display: 'flex', gap: 6 }}>
              <Button size="sm" variant="danger" Icon={Gavel} disabled={busy} onClick={() => act(r.id, 'uphold')}>
                {isAr ? 'إثبات' : 'Uphold'}
              </Button>
              <Button size="sm" variant="ghost" Icon={X} disabled={busy} onClick={() => act(r.id, 'dismiss')}>
                {isAr ? 'رفض' : 'Dismiss'}
              </Button>
            </div>
          );
        }
        return (
          <span style={{ fontSize: 11.5, color: 'var(--ink-3)', display: 'inline-flex', alignItems: 'center', gap: 5 }}>
            {r.status === 'UPHELD' ? <Gavel size={13} /> : <Check size={13} />}
            {isAr ? 'مُغلق' : 'Closed'}
          </span>
        );
      },
    },
  ];

  return (
    <Shell
      title={isAr ? 'حماية المبلّغين من الانتقام' : 'Retaliation protection'}
      sub={isAr ? 'متابعة بلاغات الانتقام المرتبطة بقضايا الإبلاغ' : 'Track retaliation claims linked to whistleblower cases'}
    >
      <PageHeader
        eyebrow={isAr ? 'الأخلاقيات والسلوك · حماية المبلّغين' : 'Ethics & Conduct · Whistleblower protection'}
        title={isAr ? 'حماية المبلّغين من الانتقام' : 'Retaliation protection'}
        sub={isAr
          ? 'يتولّى حامٍ مستقلّ متابعة بلاغات تعرّض المُبلِّغ بحُسن نيّة للانتقام، مرتبطةً بالقضية الأصلية. تمرّ كل حالة بمسار: وارد ← مقبول للمراجعة ← مُثبَت / مرفوض. حماية المبلّغين من الانتقام إلزاميّة وفق المادة 28 من لوائح حوكمة الشركات (البنك المركزي) والمادة 87 (هيئة السوق المالية).'
          : 'An independent protector tracks claims that a good-faith reporter suffered retaliation, linked to the originating case. Each runs RECEIVED → VALIDATED → UPHELD / DISMISSED. Anti-retaliation protection is mandated by SAMA Corporate Governance Art.28 and CMA Listed-Companies CG Art.87.'}
        actions={
          <Button variant="primary" Icon={Plus} onClick={() => setShowForm(v => !v)}>
            {isAr ? 'بلاغ انتقام جديد' : 'New retaliation claim'}
          </Button>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Inbox}       label={isAr ? 'إجمالي البلاغات' : 'Total claims'} value={kpis.total} tone="primary" to="/ethics" />
        <Kpi Icon={ShieldAlert} label={isAr ? 'بانتظار القبول' : 'Awaiting validation'} value={kpis.received} tone={kpis.received > 0 ? 'info' : 'neutral'} to="/ethics" />
        <Kpi Icon={Gavel}       label={isAr ? 'قيد المراجعة' : 'Under review'} value={kpis.validated} tone={kpis.validated > 0 ? 'warning' : 'neutral'} to="/ethics" />
        <Kpi Icon={ShieldCheck} label={isAr ? 'مُثبَتة' : 'Upheld'} value={kpis.upheld} tone={kpis.upheld > 0 ? 'danger' : 'neutral'} to="/ethics" />
        <Kpi Icon={UserX}       label={isAr ? 'مرفوضة' : 'Dismissed'} value={kpis.dismissed} tone="neutral" to="/ethics" />
      </div>

      {showForm && (
        <Card title={isAr ? 'تسجيل بلاغ انتقام' : 'File a retaliation claim'} Icon={Plus}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
            <Field label={isAr ? 'البلاغ المرتبط *' : 'Related case *'}>
              <select value={form.relatedCaseId} onChange={e => setForm(f => ({ ...f, relatedCaseId: e.target.value }))}
                style={INPUT_STYLE} aria-label={isAr ? 'البلاغ المرتبط' : 'Related case'}>
                <option value="">{isAr ? '— اختر البلاغ —' : '— select a case —'}</option>
                {cases.map(c => (
                  <option key={c.id} value={c.id}>{c.caseCode} — {isAr ? (c.subjectAr || c.subjectEn) : c.subjectEn}</option>
                ))}
              </select>
            </Field>
            <Field label={isAr ? 'اسم المُبلِّغ (اختياري)' : 'Claimant name (optional)'}>
              <input value={form.claimantName} onChange={e => setForm(f => ({ ...f, claimantName: e.target.value }))}
                style={INPUT_STYLE} placeholder={isAr ? 'يُترك فارغًا للبلاغ المجهول' : 'leave blank for anonymous'} />
            </Field>
            <Field label={isAr ? 'الجهة المزعومة *' : 'Alleged actor *'}>
              <input value={form.claimedActor} onChange={e => setForm(f => ({ ...f, claimedActor: e.target.value }))}
                style={INPUT_STYLE} placeholder={isAr ? 'مثال: المدير المباشر' : 'e.g. Line manager'} />
            </Field>
            <Field label={isAr ? 'بداية الفترة' : 'Period start'}>
              <input type="date" value={form.claimedAtPeriodStart} onChange={e => setForm(f => ({ ...f, claimedAtPeriodStart: e.target.value }))}
                style={INPUT_STYLE} />
            </Field>
          </div>
          <div style={{ marginTop: 12 }}>
            <Field label={isAr ? 'الفعل المزعوم *' : 'Alleged action *'}>
              <textarea value={form.claimedAction} onChange={e => setForm(f => ({ ...f, claimedAction: e.target.value }))}
                rows={2} style={{ ...INPUT_STYLE, resize: 'vertical', fontFamily: 'var(--font-sans)', lineHeight: 1.5 }}
                placeholder={isAr ? 'صف فعل الانتقام المزعوم…' : 'Describe the alleged retaliatory action…'} />
            </Field>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <Button variant="primary" Icon={Check} disabled={submitting} onClick={submitClaim}>
              {submitting ? (isAr ? 'جارٍ الحفظ…' : 'Filing…') : (isAr ? 'تسجيل البلاغ' : 'File claim')}
            </Button>
            <Button variant="ghost" onClick={() => { setShowForm(false); setForm(EMPTY_FORM); }}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
          </div>
        </Card>
      )}

      <Card title={isAr ? 'سجلّ بلاغات الانتقام' : 'Retaliation register'} Icon={ShieldAlert}>
        {loading ? (
          <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : error ? (
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            empty={<EmptyState
              Icon={ShieldCheck}
              title={isAr ? 'لا توجد بلاغات انتقام' : 'No retaliation claims'}
              sub={isAr ? 'تظهر بلاغات الانتقام هنا عند تسجيلها مقابل قضية إبلاغ.' : 'Retaliation claims appear here once filed against a whistleblower case.'} />}
          />
        )}
      </Card>
    </Shell>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-1)' }}>{label}</span>
      {children}
    </label>
  );
}
