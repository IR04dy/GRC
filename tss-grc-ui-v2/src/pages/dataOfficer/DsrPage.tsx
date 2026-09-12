import { useEffect, useState } from 'react';
import { Plus, Inbox, FolderOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { CountdownRing } from '../../components/viz/CountdownRing';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  dataOfficerApi,
  DSR_STATUS_LABEL, DSR_STATUS_TONE, DSR_TYPE_LABEL,
  nextDsrStatuses,
  type DsrResponse, type DsrStatus, type DsrRequestType,
} from '../../lib/dataOfficer';

const REQUEST_TYPES: DsrRequestType[] = ['ACCESS','RECTIFY','ERASE','RESTRICT','PORTABILITY','OBJECT'];
const CHANNELS = ['WEB','MOBILE','BRANCH','EMAIL','CALL','SDAIA_PORTAL'];
const VERIFICATION_METHODS = ['NAFATH','IN_BRANCH_KYC','SIGNED_PDF'];

export function DsrPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<DsrResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Create form
  const [showNew, setShowNew] = useState(false);
  const [reference, setReference] = useState('');
  const [requestType, setRequestType] = useState<DsrRequestType>('ACCESS');
  const [channel, setChannel] = useState('WEB');
  const [requesterEmail, setRequesterEmail] = useState('');
  const [requesterNameEn, setRequesterNameEn] = useState('');
  const [requesterNameAr, setRequesterNameAr] = useState('');
  const [summaryEn, setSummaryEn] = useState('');
  const [summaryAr, setSummaryAr] = useState('');

  // Move form
  const [moveTarget, setMoveTarget] = useState<DsrResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<DsrResponse | null>(null);
  const [toStatus, setToStatus] = useState<DsrStatus | null>(null);
  const [verifMethod, setVerifMethod] = useState('NAFATH');
  const [responseEn, setResponseEn] = useState('');
  const [responseAr, setResponseAr] = useState('');
  const [rejectReasonEn, setRejectReasonEn] = useState('');
  const [rejectReasonAr, setRejectReasonAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    try { setRows(await dataOfficerApi.listDsr()); setError(null); }
    catch (err) { setError(err instanceof ApiError ? err.message : 'Failed to load'); }
  };
  useEffect(() => { void reload(); }, [demoMode]);

  async function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await dataOfficerApi.createDsr({
        reference, requestType, channel,
        requesterEmail: requesterEmail || undefined,
        requesterNameEn: requesterNameEn || undefined,
        requesterNameAr: requesterNameAr || undefined,
        requestSummaryEn: summaryEn || undefined,
        requestSummaryAr: summaryAr || undefined,
      });
      setShowNew(false); setReference(''); setRequesterEmail(''); setRequesterNameEn('');
      setRequesterNameAr(''); setSummaryEn(''); setSummaryAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Create failed');
    } finally { setBusy(false); }
  }

  async function submitMove(e: React.FormEvent) {
    e.preventDefault();
    if (!moveTarget || !toStatus) return;
    setBusy(true);
    try {
      await dataOfficerApi.transitionDsr(moveTarget.id, {
        toStatus,
        idVerificationMethod: toStatus === 'ID_VERIFIED' ? verifMethod : undefined,
        responseTextEn: (toStatus === 'FULFILLED' || toStatus === 'PARTIAL') ? (responseEn || undefined) : undefined,
        responseTextAr: (toStatus === 'FULFILLED' || toStatus === 'PARTIAL') ? (responseAr || undefined) : undefined,
        rejectionReasonEn: toStatus === 'REJECTED' ? (rejectReasonEn || undefined) : undefined,
        rejectionReasonAr: toStatus === 'REJECTED' ? (rejectReasonAr || undefined) : undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setResponseEn(''); setResponseAr(''); setRejectReasonEn(''); setRejectReasonAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Move failed');
    } finally { setBusy(false); }
  }

  const cols: Column<DsrResponse>[] = [
    { header: ar ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: d => <span style={{ fontSize: 12 }}>{d.reference}</span> },
    { header: ar ? 'النوع' : 'Type', width: '100px',
      cell: d => <Badge>{DSR_TYPE_LABEL[d.requestType][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'مقدّم الطلب' : 'Requester',
      cell: d => (
        <div style={{ fontSize: 12 }}>
          {ar ? (d.requesterNameAr || d.requesterNameEn || '—') : (d.requesterNameEn || d.requesterNameAr || '—')}
          {d.requesterEmail && <div style={{ fontSize: 11, opacity: 0.7 }}>{d.requesterEmail}</div>}
        </div>
      ) },
    { header: ar ? 'القناة' : 'Channel', width: '110px',
      cell: d => <span style={{ fontSize: 12 }}>{d.channel ?? '—'}</span> },
    { header: ar ? 'الحالة' : 'Status', width: '160px',
      cell: d => <Badge tone={DSR_STATUS_TONE[d.status]}>
        {DSR_STATUS_LABEL[d.status][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'المهلة' : 'SLA', width: '130px',
      cell: d => {
        if (d.hoursLeft == null) return <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>;
        const overdue = d.hoursLeft < 0;
        // Per-row 30-day SLA ring (720h window) depleting green→amber→red.
        return (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <CountdownRing compact live={false} size={34} thickness={5} rtl={ar}
              deadlineMs={Date.now() + d.hoursLeft * 3_600_000}
              totalMs={720 * 3_600_000} />
            <span style={{ fontSize: 11, color: overdue ? 'var(--danger)' : 'var(--ink-3)', fontWeight: overdue ? 700 : 500 }}>
              {overdue ? (ar ? `متأخّر ${Math.abs(d.hoursLeft)}س` : `${Math.abs(d.hoursLeft)}h late`)
                       : (ar ? `${d.hoursLeft}س` : `${d.hoursLeft}h`)}
            </span>
          </span>
        );
      } },
    { header: '', width: '140px',
      cell: d => {
        const next = nextDsrStatuses(d.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(d); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation(); setMoveTarget(d); setToStatus(next[0]); }}>
                {ar ? 'تحريك' : 'Move'}
              </Button>
            )}
          </span>
        );
      } },
  ];

  return (
    <Shell>
      <PageHeader
        eyebrow={ar ? 'حقوق أصحاب البيانات' : 'DSR'}
        title={ar ? 'طلبات حقوق أصحاب البيانات' : 'Data Subject Rights Requests'}
        sub={ar
          ? 'مادة ٩-١٤ من نظام حماية البيانات الشخصية — ٣٠ يوماً للرد (ساعة المهلة لكل طلب)'
          : 'PDPL Art. 9-14 — 30-day SLA per request (countdown shown per row)'}
        actions={
          <Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>
            {showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'طلب جديد' : 'New request')}
          </Button>
        }
      />

      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}

      {showNew && (
        <Card>
          <form onSubmit={submitCreate} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={reference} onChange={e => setReference(e.target.value)} placeholder="DSR-2026-NNNN" /></label>
            <label>{ar ? 'النوع' : 'Type'}
              <select className="input" value={requestType} onChange={e => setRequestType(e.target.value as DsrRequestType)}>
                {REQUEST_TYPES.map(t => <option key={t} value={t}>{DSR_TYPE_LABEL[t][ar ? 'ar' : 'en']}</option>)}
              </select>
            </label>
            <label>{ar ? 'القناة' : 'Channel'}
              <select className="input" value={channel} onChange={e => setChannel(e.target.value)}>
                {CHANNELS.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </label>
            <label>{ar ? 'البريد الإلكتروني' : 'Email'}<input className="input" type="email" value={requesterEmail} onChange={e => setRequesterEmail(e.target.value)} /></label>
            <label>{ar ? 'الاسم (EN)' : 'Name (EN)'}<input className="input" value={requesterNameEn} onChange={e => setRequesterNameEn(e.target.value)} /></label>
            <label>{ar ? 'الاسم (AR)' : 'Name (AR)'}<input className="input" value={requesterNameAr} onChange={e => setRequesterNameAr(e.target.value)} /></label>
            <label style={{ gridColumn: 'span 2' }}>{ar ? 'ملخّص الطلب (EN)' : 'Request summary (EN)'}<textarea className="input" rows={2} value={summaryEn} onChange={e => setSummaryEn(e.target.value)} /></label>
            <label style={{ gridColumn: 'span 2' }}>{ar ? 'ملخّص الطلب (AR)' : 'Request summary (AR)'}<textarea className="input" rows={2} value={summaryAr} onChange={e => setSummaryAr(e.target.value)} dir="rtl" /></label>
            <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
              <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Register'}</Button>
            </div>
          </form>
        </Card>
      )}

      {moveTarget && toStatus && (
        <Card>
          <form onSubmit={submitMove} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            <div style={{ gridColumn: 'span 2', fontWeight: 700 }}>
              {ar ? `تحريك ${moveTarget.reference} → ` : `Move ${moveTarget.reference} → `}
              <select value={toStatus} onChange={e => setToStatus(e.target.value as DsrStatus)} className="input" style={{ marginInlineStart: 8, width: 'auto', display: 'inline-block' }}>
                {nextDsrStatuses(moveTarget.status).map(s => (
                  <option key={s} value={s}>{DSR_STATUS_LABEL[s][ar ? 'ar' : 'en']}</option>
                ))}
              </select>
            </div>
            {toStatus === 'ID_VERIFIED' && (
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'طريقة التحقّق من الهوية' : 'ID verification method'}
                <select className="input" value={verifMethod} onChange={e => setVerifMethod(e.target.value)}>
                  {VERIFICATION_METHODS.map(m => <option key={m} value={m}>{m}</option>)}
                </select>
              </label>
            )}
            {(toStatus === 'FULFILLED' || toStatus === 'PARTIAL') && (
              <>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'الرد (EN)' : 'Response (EN)'}<textarea className="input" rows={3} value={responseEn} onChange={e => setResponseEn(e.target.value)} /></label>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'الرد (AR)' : 'Response (AR)'}<textarea className="input" rows={3} value={responseAr} onChange={e => setResponseAr(e.target.value)} dir="rtl" /></label>
              </>
            )}
            {toStatus === 'REJECTED' && (
              <>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'سبب الرفض (EN)' : 'Rejection reason (EN)'}<textarea className="input" rows={2} value={rejectReasonEn} onChange={e => setRejectReasonEn(e.target.value)} required /></label>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'سبب الرفض (AR)' : 'Rejection reason (AR)'}<textarea className="input" rows={2} value={rejectReasonAr} onChange={e => setRejectReasonAr(e.target.value)} dir="rtl" required /></label>
              </>
            )}
            <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button type="button" variant="ghost" onClick={() => { setMoveTarget(null); setToStatus(null); }}>{ar ? 'إغلاق' : 'Cancel'}</Button>
              <Button type="submit" disabled={busy}>{ar ? 'تنفيذ' : 'Apply'}</Button>
            </div>
          </form>
        </Card>
      )}

      <Card>
        {rows.length === 0 ? (
          <EmptyState Icon={Inbox} title={ar ? 'لا توجد طلبات' : 'No requests'} sub={ar ? 'سجّل أول طلب' : 'Register the first DSR'} />
        ) : (
          <DataTable columns={cols} rows={rows} />
        )}
      </Card>
      <EntityDocsModal
        entityType="DSR"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.reference}
        onClose={() => setDocsTarget(null)}
      />
    </Shell>
  );
}
