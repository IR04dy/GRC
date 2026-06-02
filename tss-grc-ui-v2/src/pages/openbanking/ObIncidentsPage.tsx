import { useEffect, useState } from 'react';
import { AlertOctagon, Plus, Send } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  obApi,
  INCIDENT_TYPE_LABEL, INCIDENT_STATUS_LABEL, INCIDENT_STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  nextIncidentStatuses,
  type IncidentResponse, type IncidentType,
  type IncidentSeverity, type IncidentStatus,
} from '../../lib/openbanking';

const INCIDENT_TYPES: IncidentType[] = [
  'CONSENT_VIOLATION', 'SCOPE_CREEP', 'CERT_MISUSE',
  'TPP_ON_TPP_FRAUD', 'API_ABUSE', 'DATA_LEAK',
  'BULK_REVOCATION_FRAUD_SIGNAL', 'OUTAGE', 'OTHER',
];

const SEVERITIES: IncidentSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/**
 * Sprint 19 PR #3 — OB-incident register.
 * Operators raise + triage incidents. Notify-SAMA records the 24h SLA
 * notification reference (AR-247).
 */
export function ObIncidentsPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<IncidentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  const [showNew, setShowNew] = useState(false);
  const [tppId, setTppId] = useState<number | ''>('');
  const [incidentType, setIncidentType] = useState<IncidentType>('CONSENT_VIOLATION');
  const [severity, setSeverity] = useState<IncidentSeverity>('HIGH');
  const [titleEn, setTitleEn] = useState('');
  const [titleAr, setTitleAr] = useState('');
  const [descEn, setDescEn] = useState('');
  const [descAr, setDescAr] = useState('');
  const [samaReportable, setSamaReportable] = useState(false);
  const [affected, setAffected] = useState<number | ''>('');

  const [transitionTarget, setTransitionTarget] = useState<IncidentResponse | null>(null);
  const [transitionTo, setTransitionTo] = useState<IncidentStatus | null>(null);
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');

  const [samaTarget, setSamaTarget] = useState<IncidentResponse | null>(null);
  const [samaRef, setSamaRef] = useState('');

  const reload = async () => {
    if (demoMode) return;
    setLoading(true); setError(null);
    try {
      const r = await obApi.listIncidents(0, 200);
      setRows(r.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل سجل الحوادث' : 'Failed to load incident register'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, []);

  const submitNew = async () => {
    if (!titleEn.trim() || !titleAr.trim()) return;
    setActing(true); setError(null);
    try {
      await obApi.raiseIncident({
        tppId: typeof tppId === 'number' ? tppId : undefined,
        incidentType,
        severity,
        titleEn: titleEn.trim(),
        titleAr: titleAr.trim(),
        descriptionEn: descEn || undefined,
        descriptionAr: descAr || undefined,
        samaReportable,
        affectedCustomerCount: typeof affected === 'number' ? affected : undefined,
      });
      setShowNew(false);
      setTppId(''); setIncidentType('CONSENT_VIOLATION'); setSeverity('HIGH');
      setTitleEn(''); setTitleAr(''); setDescEn(''); setDescAr('');
      setSamaReportable(false); setAffected('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح الحادثة' : 'Failed to raise incident'));
    } finally { setActing(false); }
  };

  const submitTransition = async () => {
    if (!transitionTarget || !transitionTo) return;
    setActing(true); setError(null);
    try {
      await obApi.transitionIncident(transitionTarget.id, {
        toStatus: transitionTo,
        noteEn: noteEn || undefined,
        noteAr: noteAr || undefined,
      });
      setTransitionTarget(null); setTransitionTo(null);
      setNoteEn(''); setNoteAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ التحوّل' : 'Failed to transition'));
    } finally { setActing(false); }
  };

  const submitSama = async () => {
    if (!samaTarget || !samaRef.trim()) return;
    setActing(true); setError(null);
    try {
      await obApi.notifySama(samaTarget.id, { samaNotificationRef: samaRef.trim() });
      setSamaTarget(null); setSamaRef('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تسجيل البلاغ' : 'Failed to record SAMA notification'));
    } finally { setActing(false); }
  };

  const cols: Column<IncidentResponse>[] = [
    {
      header: lang === 'ar' ? 'المرجع' : 'Reference', mono: true, width: '140px',
      cell: i => <span style={{ fontSize: 12 }}>{i.reference}</span>,
    },
    {
      header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: i => (
        <span style={{ fontWeight: 500, fontSize: 13 }}>
          {lang === 'ar' ? i.titleAr : i.titleEn}
          {i.samaReportable && (
            <Badge tone="danger" className="m-l-6">
              {lang === 'ar' ? 'بلاغ ساما' : 'SAMA-reportable'}
            </Badge>
          )}
          {i.samaNotifiedAt && (
            <Badge tone="info" className="m-l-6">
              {lang === 'ar' ? 'تمّ الإبلاغ' : 'notified'}
            </Badge>
          )}
        </span>
      ),
    },
    {
      header: lang === 'ar' ? 'النوع' : 'Type', width: '170px',
      cell: i => (
        <Badge tone="neutral">
          {INCIDENT_TYPE_LABEL[i.incidentType][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الخطورة' : 'Severity', width: '110px',
      cell: i => (
        <Badge tone={SEVERITY_TONE[i.severity]}>
          {SEVERITY_LABEL[i.severity][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Status', width: '130px',
      cell: i => (
        <Badge tone={INCIDENT_STATUS_TONE[i.status]}>
          {INCIDENT_STATUS_LABEL[i.status][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'كشف' : 'Detected', width: '140px',
      cell: i => <span style={{ fontSize: 11 }}>{i.detectedAt?.slice(0, 16)}</span>,
    },
    {
      header: lang === 'ar' ? 'عملاء متأثّرون' : 'Affected', width: '100px',
      cell: i => <span style={{ fontSize: 12 }}>{i.affectedCustomerCount ?? 0}</span>,
    },
    {
      header: '', width: '180px',
      cell: i => {
        const next = nextIncidentStatuses(i.status);
        return (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}
               onClick={e => e.stopPropagation()}>
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={() => {
                  setTransitionTarget(i); setTransitionTo(next[0]);
                  setNoteEn(''); setNoteAr('');
                }}>
                {lang === 'ar' ? 'تحريك' : 'Move'}
              </Button>
            )}
            {i.samaReportable && !i.samaNotifiedAt && (
              <Button variant="primary" size="sm"
                onClick={() => { setSamaTarget(i); setSamaRef(''); }}>
                {lang === 'ar' ? 'إبلاغ' : 'Notify'}
              </Button>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <Shell
      title={lang === 'ar' ? 'حوادث المصرفية المفتوحة' : 'OB Incidents'}
      sub={lang === 'ar'
        ? 'حوادث الواجهات والموافقات الخاصّة بمزوّدي الخدمة وإطار ساما.'
        : 'API + consent incidents tied to TPPs and the SAMA OBF framework.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'المصرفية المفتوحة · المرحلة ١٩'
                                : 'Open Banking · Sprint 19'}
        title={lang === 'ar' ? 'الحوادث' : 'Incidents'}
        sub={lang === 'ar'
          ? 'سجل حوادث الواجهات + الموافقات + الشهادات'
          : 'API, consent, and certificate incidents'}
        actions={
          <Button onClick={() => setShowNew(s => !s)}
                  variant="primary" Icon={Plus}>
            {showNew
              ? (lang === 'ar' ? 'إغلاق' : 'Cancel')
              : (lang === 'ar' ? 'فتح حادثة' : 'Raise incident')}
          </Button>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {showNew && (
        <div style={{ marginBottom: 16 }}>
          <Card padded
            title={lang === 'ar' ? 'فتح حادثة جديدة' : 'Raise a new OB incident'}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: 12,
            }}>
              <Field label={lang === 'ar' ? 'معرّف المزوّد (اختياري)'
                                            : 'TPP id (optional)'}>
                <input type="number" value={tppId}
                  onChange={e => setTppId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="123" />
              </Field>
              <Field label={lang === 'ar' ? 'النوع' : 'Type'}>
                <select value={incidentType}
                  onChange={e => setIncidentType(e.target.value as IncidentType)}
                  style={inputStyle}>
                  {INCIDENT_TYPES.map(t => (
                    <option key={t} value={t}>
                      {INCIDENT_TYPE_LABEL[t][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'الخطورة' : 'Severity'}>
                <select value={severity}
                  onChange={e => setSeverity(e.target.value as IncidentSeverity)}
                  style={inputStyle}>
                  {SEVERITIES.map(s => (
                    <option key={s} value={s}>
                      {SEVERITY_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={lang === 'ar' ? 'عملاء متأثّرون' : 'Affected customers'}>
                <input type="number" value={affected}
                  onChange={e => setAffected(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle} placeholder="0" />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (إنجليزي) *'
                                            : 'Title (English) *'}>
                <input value={titleEn} onChange={e => setTitleEn(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="TPP exfiltrated balances outside scope" />
              </Field>
              <Field label={lang === 'ar' ? 'العنوان (عربي) *'
                                            : 'Title (Arabic) *'}>
                <input value={titleAr} dir="rtl"
                  onChange={e => setTitleAr(e.target.value)}
                  style={inputStyle} maxLength={256}
                  placeholder="سحب المزوّد للأرصدة خارج النطاق المصرَّح به" />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (إنجليزي)'
                                            : 'Description (English)'}>
                <textarea value={descEn} onChange={e => setDescEn(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="What happened, where, blast radius…" />
              </Field>
              <Field label={lang === 'ar' ? 'الوصف (عربي)' : 'Description (Arabic)'}>
                <textarea value={descAr} dir="rtl"
                  onChange={e => setDescAr(e.target.value)}
                  rows={3} style={inputStyle}
                  placeholder="ما الذي حدث وأين ومدى التأثير…" />
              </Field>
              <Field label={lang === 'ar' ? 'بلاغ ساما؟' : 'SAMA-reportable?'}>
                <label style={{ display: 'flex', gap: 6, alignItems: 'center',
                  fontSize: 13, padding: '6px 0' }}>
                  <input type="checkbox" checked={samaReportable}
                    onChange={e => setSamaReportable(e.target.checked)} />
                  {lang === 'ar'
                    ? 'تأثير جوهري على خدمة المصرفية المفتوحة (٢٤ ساعة)'
                    : 'Materially affects OB service (24h SAMA window)'}
                </label>
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitNew}
                      disabled={acting || !titleEn.trim() || !titleAr.trim()}>
                {acting
                  ? (lang === 'ar' ? 'جارٍ الفتح…' : 'Raising…')
                  : (lang === 'ar' ? 'فتح الحادثة' : 'Raise incident')}
              </Button>
              <Button variant="secondary" onClick={() => setShowNew(false)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {transitionTarget && transitionTo && (
        <div style={{ marginBottom: 16 }}>
          <Card padded
            title={lang === 'ar'
              ? `تحريك الحادثة ${transitionTarget.reference}`
              : `Move incident ${transitionTarget.reference}`}>
            <Field label={lang === 'ar' ? 'الحالة الجديدة' : 'Next status'}>
              <select value={transitionTo}
                onChange={e => setTransitionTo(e.target.value as IncidentStatus)}
                style={inputStyle}>
                {nextIncidentStatuses(transitionTarget.status).map(s => (
                  <option key={s} value={s}>
                    {INCIDENT_STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}
                  </option>
                ))}
              </select>
            </Field>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: 12, marginTop: 12,
            }}>
              <Field label={lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)'}>
                <textarea value={noteEn} onChange={e => setNoteEn(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
              <Field label={lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)'}>
                <textarea value={noteAr} dir="rtl"
                  onChange={e => setNoteAr(e.target.value)}
                  rows={3} style={inputStyle} />
              </Field>
            </div>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" onClick={submitTransition} disabled={acting}>
                {acting
                  ? (lang === 'ar' ? 'جارٍ التنفيذ…' : 'Applying…')
                  : (lang === 'ar' ? 'تنفيذ' : 'Apply')}
              </Button>
              <Button variant="secondary"
                onClick={() => { setTransitionTarget(null); setTransitionTo(null); }}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      {samaTarget && (
        <div style={{ marginBottom: 16 }}>
          <Card padded
            title={lang === 'ar'
              ? `إبلاغ ساما — ${samaTarget.reference}`
              : `Notify SAMA — ${samaTarget.reference}`}>
            <Field label={lang === 'ar'
              ? 'مرجع بلاغ ساما (إلزامي خلال ٢٤ ساعة من الكشف)'
              : 'SAMA notification ref (mandatory within 24h of detection)'}>
              <input value={samaRef} onChange={e => setSamaRef(e.target.value)}
                style={inputStyle} maxLength={128}
                placeholder="SAMA-OBF-NOTIF-2026-014" />
            </Field>
            <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
              <Button variant="primary" Icon={Send}
                      onClick={submitSama} disabled={acting || !samaRef.trim()}>
                {acting
                  ? (lang === 'ar' ? 'جارٍ التسجيل…' : 'Recording…')
                  : (lang === 'ar' ? 'تسجيل البلاغ' : 'Record notification')}
              </Button>
              <Button variant="secondary" onClick={() => setSamaTarget(null)}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </Card>
        </div>
      )}

      <Card padded
        title={lang === 'ar' ? 'كل الحوادث' : 'All incidents'}>
        {loading ? (
          <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : (
          <DataTable
            columns={cols}
            rows={rows}
            empty={<EmptyState Icon={AlertOctagon}
              title={lang === 'ar' ? 'لا حوادث' : 'No incidents yet'}
              sub={demoMode
                ? (lang === 'ar' ? 'سجّل دخولك لعرض الحوادث.' : 'Log in to view incidents.')
                : (lang === 'ar'
                  ? 'حين يخفق مزوّد في النطاق أو يتجاوز الموافقة، افتح هنا.'
                  : 'When a TPP breaches scope or consent, raise it here.')} />}
          />
        )}
      </Card>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--border)',
  borderRadius: 4,
  fontSize: 13,
  width: '100%',
  fontFamily: 'inherit',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
