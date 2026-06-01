import { useEffect, useState } from 'react';
import { Plus, Siren, FolderOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  dataOfficerApi,
  BREACH_STATUS_LABEL, BREACH_STATUS_TONE,
  nextBreachStatuses,
  type BreachResponse, type BreachStatus, type Severity,
} from '../../lib/dataOfficer';

const SEVERITIES: Severity[] = ['LOW','MEDIUM','HIGH','CRITICAL'];
const CAUSES = ['INSIDER','EXTERNAL_ATTACK','SYSTEM_FAILURE','MISCONFIGURATION','PROCESSOR_ERROR','LOST_DEVICE'];
const BREACH_TYPES = ['CONFIDENTIALITY','INTEGRITY','AVAILABILITY'];

export function BreachPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const demoMode = !isAuthenticated();

  const [rows, setRows] = useState<BreachResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [showNew, setShowNew] = useState(false);
  const [reference, setReference] = useState('');
  const [titleEn, setTitleEn] = useState(''); const [titleAr, setTitleAr] = useState('');
  const [detectedAt, setDetectedAt] = useState(() => new Date().toISOString().slice(0, 16));
  const [breachType, setBreachType] = useState('CONFIDENTIALITY');
  const [cause, setCause] = useState('EXTERNAL_ATTACK');
  const [severity, setSeverity] = useState<Severity>('MEDIUM');
  const [affectedSubjects, setAffectedSubjects] = useState('');
  const [isSpecial, setIsSpecial] = useState(false);
  const [isCrossBorder, setIsCrossBorder] = useState(false);
  const [descEn, setDescEn] = useState(''); const [descAr, setDescAr] = useState('');

  const [moveTarget, setMoveTarget] = useState<BreachResponse | null>(null);
  const [docsTarget, setDocsTarget] = useState<BreachResponse | null>(null);
  const [toStatus, setToStatus] = useState<BreachStatus | null>(null);
  const [sdaiaRef, setSdaiaRef] = useState('');
  const [containEn, setContainEn] = useState(''); const [containAr, setContainAr] = useState('');
  const [closureEn, setClosureEn] = useState(''); const [closureAr, setClosureAr] = useState('');

  const reload = async () => {
    if (demoMode) return;
    try { setRows(await dataOfficerApi.listBreaches()); setError(null); }
    catch (err) { setError(err instanceof ApiError ? err.message : 'Failed to load'); }
  };
  useEffect(() => { void reload(); }, [demoMode]);

  async function submitCreate(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await dataOfficerApi.createBreach({
        reference, titleEn, titleAr,
        detectedAt: new Date(detectedAt).toISOString(),
        breachType, cause, severity,
        affectedSubjectCount: affectedSubjects ? Number(affectedSubjects) : undefined,
        isSpecialCategory: isSpecial, isCrossBorder,
        descriptionEn: descEn || undefined, descriptionAr: descAr || undefined,
      });
      setShowNew(false); setReference(''); setTitleEn(''); setTitleAr('');
      setAffectedSubjects(''); setDescEn(''); setDescAr('');
      setIsSpecial(false); setIsCrossBorder(false);
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
      await dataOfficerApi.transitionBreach(moveTarget.id, {
        toStatus,
        sdaiaReference: toStatus === 'NOTIFIED_SDAIA' ? (sdaiaRef || undefined) : undefined,
        containmentActionsEn: (toStatus === 'CONTAINED' || toStatus === 'REMEDIATING') ? (containEn || undefined) : undefined,
        containmentActionsAr: (toStatus === 'CONTAINED' || toStatus === 'REMEDIATING') ? (containAr || undefined) : undefined,
        closureSummaryEn: (toStatus === 'CLOSED' || toStatus === 'FALSE_POSITIVE') ? (closureEn || undefined) : undefined,
        closureSummaryAr: (toStatus === 'CLOSED' || toStatus === 'FALSE_POSITIVE') ? (closureAr || undefined) : undefined,
      });
      setMoveTarget(null); setToStatus(null);
      setSdaiaRef(''); setContainEn(''); setContainAr(''); setClosureEn(''); setClosureAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Move failed');
    } finally { setBusy(false); }
  }

  const cols: Column<BreachResponse>[] = [
    { header: ar ? 'المرجع' : 'Reference', width: '140px', mono: true,
      cell: b => <span style={{ fontSize: 12 }}>{b.reference}</span> },
    { header: ar ? 'العنوان' : 'Title',
      cell: b => (
        <div>
          <div style={{ fontWeight: 600, fontSize: 13 }}>{ar ? b.titleAr : b.titleEn}</div>
          <div style={{ fontSize: 11, opacity: 0.7 }}>
            {b.affectedSubjectCount != null && (ar ? `${b.affectedSubjectCount} متأثرين · ` : `${b.affectedSubjectCount} affected · `)}
            {b.severity && <Badge tone={b.severity === 'CRITICAL' ? 'danger' : b.severity === 'HIGH' ? 'warning' : 'neutral'}>{b.severity}</Badge>}
            {b.isSpecialCategory && <span style={{ marginInlineStart: 6, color: 'var(--danger,#a00)' }}>· {ar ? 'بيانات حسّاسة' : 'special category'}</span>}
          </div>
        </div>
      ) },
    { header: ar ? 'الحالة' : 'Status', width: '160px',
      cell: b => <Badge tone={BREACH_STATUS_TONE[b.status]}>{BREACH_STATUS_LABEL[b.status][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'مهلة سدايا' : 'SDAIA 72h', width: '140px',
      cell: b => {
        if (b.sdaiaNotifiedAt) return <Badge tone="success">{ar ? 'تمّ الإبلاغ' : 'Notified'}</Badge>;
        if (b.hoursToSdaiaDeadline == null) return <span style={{ fontSize: 12, opacity: 0.5 }}>—</span>;
        const overdue = b.hoursToSdaiaDeadline < 0;
        return <span style={{ fontSize: 12, fontWeight: 700, color: overdue ? 'var(--danger,#d92d20)' : b.hoursToSdaiaDeadline < 12 ? '#d97706' : undefined }}>
          {overdue ? (ar ? `متأخّر ${Math.abs(b.hoursToSdaiaDeadline)}س` : `${Math.abs(b.hoursToSdaiaDeadline)}h late`)
                   : (ar ? `${b.hoursToSdaiaDeadline}س متبقية` : `${b.hoursToSdaiaDeadline}h left`)}
        </span>;
      } },
    { header: '', width: '140px',
      cell: b => {
        const next = nextBreachStatuses(b.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(b); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation(); setMoveTarget(b); setToStatus(next[0]); }}>
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
        eyebrow={ar ? 'إبلاغ الخروقات · المرحلة ٣٣' : 'Breach Notification · Sprint 33'}
        title={ar ? 'سجل خروقات البيانات الشخصية' : 'Personal Data Breach Register'}
        sub={ar
          ? 'مادة ٢٠ من نظام حماية البيانات — إبلاغ سدايا خلال ٧٢ ساعة من اكتشاف الخرق (ساعة العدّ التنازلي لكل خرق)'
          : 'PDPL Art. 20 — Notify SDAIA within 72 hours of detection (countdown shown per row)'}
        actions={
          <Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>
            {showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'تسجيل خرق' : 'Log breach')}
          </Button>
        }
      />

      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}

      {showNew && (
        <Card>
          <form onSubmit={submitCreate} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={reference} onChange={e => setReference(e.target.value)} placeholder="BR-2026-NNNN" /></label>
            <label>{ar ? 'تاريخ الاكتشاف' : 'Detected at'}<input className="input" type="datetime-local" required value={detectedAt} onChange={e => setDetectedAt(e.target.value)} /></label>
            <label>{ar ? 'العنوان (EN)' : 'Title (EN)'}<input className="input" required value={titleEn} onChange={e => setTitleEn(e.target.value)} /></label>
            <label>{ar ? 'العنوان (AR)' : 'Title (AR)'}<input className="input" required value={titleAr} onChange={e => setTitleAr(e.target.value)} dir="rtl" /></label>
            <label>{ar ? 'النوع' : 'Type'}
              <select className="input" value={breachType} onChange={e => setBreachType(e.target.value)}>{BREACH_TYPES.map(t => <option key={t}>{t}</option>)}</select>
            </label>
            <label>{ar ? 'السبب' : 'Cause'}
              <select className="input" value={cause} onChange={e => setCause(e.target.value)}>{CAUSES.map(c => <option key={c}>{c}</option>)}</select>
            </label>
            <label>{ar ? 'الخطورة' : 'Severity'}
              <select className="input" value={severity} onChange={e => setSeverity(e.target.value as Severity)}>{SEVERITIES.map(s => <option key={s}>{s}</option>)}</select>
            </label>
            <label>{ar ? 'عدد المتأثرين' : 'Affected subjects'}<input className="input" type="number" value={affectedSubjects} onChange={e => setAffectedSubjects(e.target.value)} /></label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" checked={isSpecial} onChange={e => setIsSpecial(e.target.checked)} />
              {ar ? 'بيانات حسّاسة (مادة ٤)' : 'Special-category data (Art. 4)'}
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="checkbox" checked={isCrossBorder} onChange={e => setIsCrossBorder(e.target.checked)} />
              {ar ? 'عبر الحدود' : 'Cross-border'}
            </label>
            <label style={{ gridColumn: 'span 2' }}>{ar ? 'الوصف (EN)' : 'Description (EN)'}<textarea className="input" rows={2} value={descEn} onChange={e => setDescEn(e.target.value)} /></label>
            <label style={{ gridColumn: 'span 2' }}>{ar ? 'الوصف (AR)' : 'Description (AR)'}<textarea className="input" rows={2} value={descAr} onChange={e => setDescAr(e.target.value)} dir="rtl" /></label>
            <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
              <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Log'}</Button>
            </div>
          </form>
        </Card>
      )}

      {moveTarget && toStatus && (
        <Card>
          <form onSubmit={submitMove} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            <div style={{ gridColumn: 'span 2', fontWeight: 700 }}>
              {ar ? `تحريك ${moveTarget.reference} → ` : `Move ${moveTarget.reference} → `}
              <select value={toStatus} onChange={e => setToStatus(e.target.value as BreachStatus)} className="input" style={{ marginInlineStart: 8, width: 'auto', display: 'inline-block' }}>
                {nextBreachStatuses(moveTarget.status).map(s => <option key={s} value={s}>{BREACH_STATUS_LABEL[s][ar ? 'ar' : 'en']}</option>)}
              </select>
            </div>
            {toStatus === 'NOTIFIED_SDAIA' && (
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'مرجع سدايا' : 'SDAIA reference'}<input className="input" value={sdaiaRef} onChange={e => setSdaiaRef(e.target.value)} placeholder="SDAIA/INC/2026/NNNN" required /></label>
            )}
            {(toStatus === 'CONTAINED' || toStatus === 'REMEDIATING') && (
              <>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'إجراءات الاحتواء (EN)' : 'Containment actions (EN)'}<textarea className="input" rows={3} value={containEn} onChange={e => setContainEn(e.target.value)} /></label>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'إجراءات الاحتواء (AR)' : 'Containment actions (AR)'}<textarea className="input" rows={3} value={containAr} onChange={e => setContainAr(e.target.value)} dir="rtl" /></label>
              </>
            )}
            {(toStatus === 'CLOSED' || toStatus === 'FALSE_POSITIVE') && (
              <>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'ملخص الإغلاق (EN)' : 'Closure summary (EN)'}<textarea className="input" rows={3} value={closureEn} onChange={e => setClosureEn(e.target.value)} /></label>
                <label style={{ gridColumn: 'span 2' }}>{ar ? 'ملخص الإغلاق (AR)' : 'Closure summary (AR)'}<textarea className="input" rows={3} value={closureAr} onChange={e => setClosureAr(e.target.value)} dir="rtl" /></label>
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
          <EmptyState Icon={Siren} title={ar ? 'لا توجد خروقات' : 'No breaches'} sub={ar ? 'لم يتمّ تسجيل أيّ خرق' : 'No breach has been logged yet'} />
        ) : (
          <DataTable columns={cols} rows={rows} />
        )}
      </Card>
      <EntityDocsModal
        entityType="BREACH"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.reference}
        onClose={() => setDocsTarget(null)}
      />
    </Shell>
  );
}
