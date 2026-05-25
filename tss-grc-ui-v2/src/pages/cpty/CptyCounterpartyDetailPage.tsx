import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Users, Banknote, AlertTriangle, Plus, Activity } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  cptyApi,
  STATUS_LABEL, STATUS_TONE, SECTOR_LABEL,
  BAND_LABEL, BAND_TONE,
  ACTION_TYPE_LABEL, ACTION_TYPE_TONE,
  EVENT_TYPE_LABEL,
  type CounterpartyResponse, type RatingResponse,
  type RatingActionResponse, type LimitResponse, type EventResponse,
  type ActionType, type RatingType, type LimitScope, type TriggerSource,
} from '../../lib/cpty';

type Tab = 'ratings' | 'actions' | 'limits' | 'timeline';

export function CptyCounterpartyDetailPage() {
  const { id } = useParams();
  const cptyId = Number(id);
  const { lang } = useI18n();

  const [cpty, setCpty] = useState<CounterpartyResponse | null>(null);
  const [ratings, setRatings] = useState<RatingResponse[]>([]);
  const [actions, setActions] = useState<RatingActionResponse[]>([]);
  const [limits, setLimits] = useState<LimitResponse[]>([]);
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);
  const [tab, setTab] = useState<Tab>('ratings');

  // Modal state
  const [modal, setModal] = useState<null | 'rating' | 'action' | 'limit' | 'utilisation'>(null);
  const [limitForUtil, setLimitForUtil] = useState<LimitResponse | null>(null);

  // Rating form
  const [rType, setRType] = useState<RatingType>('INTERNAL');
  const [rGrade, setRGrade] = useState('');
  const [rScale, setRScale] = useState('');
  const [rAgency, setRAgency] = useState('');
  const [rPd, setRPd] = useState('');
  const [rLgd, setRLgd] = useState('');
  const [rEad, setREad] = useState('');
  const [rOutlook, setROutlook] = useState('STABLE');
  const [rRationaleEn, setRRationaleEn] = useState('');
  const [rRationaleAr, setRRationaleAr] = useState('');

  // Action form
  const [aType, setAType] = useState<ActionType>('DOWNGRADE');
  const [aGradeBefore, setAGradeBefore] = useState('');
  const [aGradeAfter, setAGradeAfter] = useState('');
  const [aTrigger, setATrigger] = useState<TriggerSource>('INTERNAL_REVIEW');
  const [aRationaleEn, setARationaleEn] = useState('');
  const [aRationaleAr, setARationaleAr] = useState('');

  // Limit form
  const [lScope, setLScope] = useState<LimitScope>('COUNTERPARTY');
  const [lGroupRef, setLGroupRef] = useState('');
  const [lDisplayEn, setLDisplayEn] = useState('');
  const [lDisplayAr, setLDisplayAr] = useState('');
  const [lLimit, setLLimit] = useState(0);
  const [lUtilised, setLUtilised] = useState(0);

  // Utilisation form
  const [uUtilised, setUUtilised] = useState(0);
  const [uNoteEn, setUNoteEn] = useState('');
  const [uNoteAr, setUNoteAr] = useState('');

  const reload = async () => {
    if (!cptyId) return;
    setLoading(true); setError(null);
    try {
      const [c, r, a, l, e] = await Promise.all([
        cptyApi.get(cptyId),
        cptyApi.listRatings(cptyId).catch(() => []),
        cptyApi.listActions(cptyId).catch(() => []),
        cptyApi.listLimitsForCounterparty(cptyId).catch(() => []),
        cptyApi.listEvents(cptyId).catch(() => []),
      ]);
      setCpty(c); setRatings(r); setActions(a); setLimits(l); setEvents(e);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الطرف' : 'Failed to load counterparty'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [cptyId]);

  const closeModal = () => {
    setModal(null); setLimitForUtil(null);
    setRGrade(''); setRScale(''); setRAgency('');
    setRPd(''); setRLgd(''); setREad('');
    setRRationaleEn(''); setRRationaleAr('');
    setAGradeBefore(''); setAGradeAfter('');
    setARationaleEn(''); setARationaleAr('');
    setLGroupRef(''); setLDisplayEn(''); setLDisplayAr('');
    setLLimit(0); setLUtilised(0);
    setUUtilised(0); setUNoteEn(''); setUNoteAr('');
  };

  const doAssignRating = async () => {
    if (!cpty || !rGrade.trim()) return;
    setActing(true);
    try {
      await cptyApi.assignRating(cpty.id, {
        ratingType: rType,
        effectiveDate: new Date().toISOString().slice(0, 10),
        grade: rGrade.trim(),
        scale: rScale || undefined,
        agency: rAgency || undefined,
        pd: rPd ? Number(rPd) : undefined,
        lgd: rLgd ? Number(rLgd) : undefined,
        eadSarM: rEad ? Number(rEad) : undefined,
        outlook: rOutlook as 'STABLE' | 'POSITIVE' | 'NEGATIVE' | 'DEVELOPING',
        rationaleEn: rRationaleEn || undefined,
        rationaleAr: rRationaleAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Assign rating failed');
    } finally { setActing(false); }
  };

  const doRecordAction = async () => {
    if (!cpty) return;
    setActing(true);
    try {
      await cptyApi.recordAction(cpty.id, {
        actionType: aType,
        actionDate: new Date().toISOString().slice(0, 10),
        gradeBefore: aGradeBefore || undefined,
        gradeAfter: aGradeAfter || undefined,
        triggerSource: aTrigger,
        rationaleEn: aRationaleEn || undefined,
        rationaleAr: aRationaleAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Record action failed');
    } finally { setActing(false); }
  };

  const doUpsertLimit = async () => {
    if (!cpty || !lDisplayEn.trim() || !lDisplayAr.trim() || lLimit <= 0) return;
    setActing(true);
    try {
      await cptyApi.upsertLimit({
        scope: lScope,
        counterpartyId: lScope === 'COUNTERPARTY' ? cpty.id : undefined,
        groupReference: lScope === 'GROUP' ? lGroupRef : undefined,
        displayNameEn: lDisplayEn.trim(),
        displayNameAr: lDisplayAr.trim(),
        limitSarM: lLimit,
        utilisedSarM: lUtilised,
        approvedAt: new Date().toISOString().slice(0, 10),
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Upsert limit failed');
    } finally { setActing(false); }
  };

  const doRecordUtilisation = async () => {
    if (!limitForUtil) return;
    setActing(true);
    try {
      await cptyApi.recordUtilisation(limitForUtil.id, {
        utilisedSarM: uUtilised,
        refreshedAt: new Date().toISOString().slice(0, 10),
        noteEn: uNoteEn || undefined,
        noteAr: uNoteAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Record utilisation failed');
    } finally { setActing(false); }
  };

  if (loading && !cpty) {
    return (
      <Shell title={lang === 'ar' ? 'الطرف المقابل' : 'Counterparty'}>
        <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      </Shell>
    );
  }
  if (!cpty) {
    return (
      <Shell title={lang === 'ar' ? 'الطرف المقابل' : 'Counterparty'}>
        <EmptyState Icon={Users}
          title={lang === 'ar' ? 'لم يُعثر على الطرف' : 'Counterparty not found'}
          sub={error || ''} />
      </Shell>
    );
  }

  const ratingCols: Column<RatingResponse>[] = [
    { header: lang === 'ar' ? 'النوع' : 'Type', width: '100px',
      cell: r => <Badge tone={r.ratingType === 'INTERNAL' ? 'info' : 'neutral'}>
        {r.ratingType}
      </Badge> },
    { header: lang === 'ar' ? 'الدرجة' : 'Grade', width: '100px',
      cell: r => <strong style={{ fontSize: 13 }}>{r.grade}</strong> },
    { header: lang === 'ar' ? 'الوكالة' : 'Agency', width: '120px',
      cell: r => <span style={{ fontSize: 12 }}>{r.agency ?? '—'}</span> },
    { header: lang === 'ar' ? 'النظرة' : 'Outlook', width: '120px',
      cell: r => <span style={{ fontSize: 12 }}>{r.outlook}</span> },
    { header: 'PD', width: '90px',
      cell: r => <span style={{ fontSize: 12 }}>
        {r.pd != null ? (Number(r.pd) * 100).toFixed(2) + '%' : '—'}
      </span> },
    { header: 'LGD', width: '90px',
      cell: r => <span style={{ fontSize: 12 }}>
        {r.lgd != null ? (Number(r.lgd) * 100).toFixed(1) + '%' : '—'}
      </span> },
    { header: lang === 'ar' ? 'EAD' : 'EAD (M)', width: '100px',
      cell: r => <span style={{ fontSize: 12 }}>{r.eadSarM ?? '—'}</span> },
    { header: lang === 'ar' ? 'سارٍ من' : 'Effective', width: '110px',
      cell: r => <span style={{ fontSize: 12 }}>
        {new Date(r.effectiveDate).toLocaleDateString(
          lang === 'ar' ? 'ar-SA' : 'en-GB')}
      </span> },
  ];

  const actionCols: Column<RatingActionResponse>[] = [
    { header: lang === 'ar' ? 'التاريخ' : 'Date', width: '110px',
      cell: a => <span style={{ fontSize: 12 }}>
        {new Date(a.actionDate).toLocaleDateString(
          lang === 'ar' ? 'ar-SA' : 'en-GB')}
      </span> },
    { header: lang === 'ar' ? 'الإجراء' : 'Action', width: '170px',
      cell: a => <Badge tone={ACTION_TYPE_TONE[a.actionType]}>
        {ACTION_TYPE_LABEL[a.actionType][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'من → إلى' : 'From → To', width: '140px',
      cell: a => <span style={{ fontSize: 12 }}>
        {a.gradeBefore ?? '—'} → {a.gradeAfter ?? '—'}
      </span> },
    { header: lang === 'ar' ? 'المصدر' : 'Source', width: '140px',
      cell: a => <span style={{ fontSize: 12 }}>{a.triggerSource ?? '—'}</span> },
    { header: lang === 'ar' ? 'المبرّر' : 'Rationale',
      cell: a => <span style={{ fontSize: 12 }}>
        {lang === 'ar' ? a.rationaleAr : a.rationaleEn}
      </span> },
  ];

  const limitCols: Column<LimitResponse>[] = [
    { header: lang === 'ar' ? 'الاسم' : 'Name',
      cell: l => <span style={{ fontWeight: 500, fontSize: 13 }}>
        {lang === 'ar' ? l.displayNameAr : l.displayNameEn}
        <Badge tone="info" className="m-l-6">
          {l.scope === 'GROUP'
            ? (lang === 'ar' ? 'مجموعة' : 'group')
            : (lang === 'ar' ? 'طرف' : 'cpty')}
        </Badge>
      </span> },
    { header: lang === 'ar' ? 'المستخدم' : 'Utilised', width: '170px',
      cell: l => <span style={{ fontSize: 12 }}>
        {Number(l.utilisedSarM).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}
        {' / '}
        {Number(l.limitSarM).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}
        {' '}{lang === 'ar' ? 'م.ر' : 'SAR M'}
      </span> },
    { header: '%', width: '80px',
      cell: l => <strong style={{ fontSize: 13 }}>{Number(l.utilisationPct).toFixed(1)}%</strong> },
    { header: lang === 'ar' ? 'النطاق' : 'Band', width: '110px',
      cell: l => <Badge tone={BAND_TONE[l.band]}>
        {BAND_LABEL[l.band][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'إجراء' : 'Action', width: '120px',
      cell: l => (
        <Button variant="ghost"
          onClick={(e) => {
            e.stopPropagation();
            setLimitForUtil(l);
            setUUtilised(Number(l.utilisedSarM ?? 0));
            setModal('utilisation');
          }}>
          {lang === 'ar' ? 'تحديث' : 'Record'}
        </Button>
      ) },
  ];

  return (
    <Shell title={cpty.reference}>
      <div style={{ marginBottom: 8 }}>
        <Link to="/cpty/counterparties"
          style={{ fontSize: 13, color: 'var(--ink-muted)' }}>
          ← {lang === 'ar' ? 'العودة للأطراف' : 'Back to counterparties'}
        </Link>
      </div>
      <PageHeader
        eyebrow={`${cpty.reference} · ${SECTOR_LABEL[cpty.sector][lang === 'ar' ? 'ar' : 'en']}`}
        title={lang === 'ar' ? cpty.legalNameAr : cpty.legalNameEn}
        sub={lang === 'ar' ? 'ملف الطرف المقابل' : 'Counterparty profile'}
        actions={
          <Badge tone={STATUS_TONE[cpty.status]}>
            {STATUS_LABEL[cpty.status][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Kpi Icon={Activity} tone="info"
          label={lang === 'ar' ? 'التصنيف الداخلي' : 'Internal grade'}
          value={cpty.currentInternalGrade ?? '—'} />
        <Kpi Icon={Activity} tone="neutral"
          label={lang === 'ar' ? 'التصنيف الخارجي' : 'External rating'}
          value={cpty.currentExternalRating
            ? `${cpty.currentExternalRating} (${cpty.currentExternalAgency ?? ''})`
            : '—'} />
        <Kpi Icon={Banknote} tone="info"
          label={lang === 'ar' ? 'إجمالي التعرّض' : 'Total exposure'}
          value={Number(cpty.totalExposureSarM ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US') + ' ' +
            (lang === 'ar' ? 'م.ر' : 'SAR M')} />
        <Kpi Icon={AlertTriangle} tone={limits.some(l => l.band === 'BREACH') ? 'danger'
            : limits.some(l => l.band === 'RED') ? 'danger'
            : limits.some(l => l.band === 'AMBER') ? 'warning' : 'success'}
          label={lang === 'ar' ? 'حدود الطرف' : 'Limits'}
          value={limits.length} />
      </div>

      {/* Tabs */}
      <div style={{
        display: 'flex', gap: 4, marginTop: 16, marginBottom: 8,
        borderBottom: '1px solid var(--card-border)',
      }}>
        {(['ratings', 'actions', 'limits', 'timeline'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            style={{
              padding: '8px 16px', border: 'none', background: 'none',
              fontSize: 13, fontWeight: 500, cursor: 'pointer',
              borderBottom: tab === t ? '2px solid var(--primary)' : '2px solid transparent',
              color: tab === t ? 'var(--primary)' : 'var(--ink)',
            }}>
            {t === 'ratings' && (lang === 'ar' ? 'التصنيفات' : 'Ratings')}
            {t === 'actions' && (lang === 'ar' ? 'الإجراءات' : 'Actions')}
            {t === 'limits' && (lang === 'ar' ? 'الحدود' : 'Limits')}
            {t === 'timeline' && (lang === 'ar' ? 'السجل' : 'Timeline')}
          </button>
        ))}
      </div>

      {tab === 'ratings' && (
        <Card padded
          title={lang === 'ar' ? 'التصنيفات الائتمانية' : 'Credit ratings'}
          action={
            <Button variant="primary" Icon={Plus}
              onClick={() => setModal('rating')}>
              {lang === 'ar' ? 'تصنيف' : 'Assign rating'}
            </Button>
          }>
          <DataTable columns={ratingCols} rows={ratings}
            empty={<EmptyState Icon={Activity}
              title={lang === 'ar' ? 'لا تصنيفات' : 'No ratings yet'}
              sub={lang === 'ar'
                ? 'أسند تصنيفاً داخلياً (PD/LGD/EAD) أو خارجياً.'
                : 'Assign an INTERNAL (PD/LGD/EAD) or EXTERNAL agency rating.'} />}
          />
        </Card>
      )}

      {tab === 'actions' && (
        <Card padded
          title={lang === 'ar' ? 'إجراءات التصنيف' : 'Rating actions'}
          action={
            <Button variant="primary" Icon={Plus}
              onClick={() => setModal('action')}>
              {lang === 'ar' ? 'إجراء' : 'Record action'}
            </Button>
          }>
          <DataTable columns={actionCols} rows={actions}
            empty={<EmptyState Icon={Activity}
              title={lang === 'ar' ? 'لا إجراءات' : 'No actions yet'}
              sub={lang === 'ar'
                ? 'سجّل خفض / رفع / مراقبة / تعثّر.'
                : 'Record downgrade / upgrade / watchlist / default.'} />}
          />
        </Card>
      )}

      {tab === 'limits' && (
        <Card padded
          title={lang === 'ar' ? 'حدود التعرّض' : 'Exposure limits'}
          action={
            <Button variant="primary" Icon={Plus}
              onClick={() => setModal('limit')}>
              {lang === 'ar' ? 'حدّ' : 'Approve limit'}
            </Button>
          }>
          <DataTable columns={limitCols} rows={limits}
            empty={<EmptyState Icon={Banknote}
              title={lang === 'ar' ? 'لا حدود' : 'No limits yet'}
              sub={lang === 'ar'
                ? 'اعتمد حدّاً على الطرف أو على المجموعة.'
                : 'Approve a counterparty- or group-scope limit.'} />}
          />
        </Card>
      )}

      {tab === 'timeline' && (
        <Card padded title={lang === 'ar' ? 'السجل الزمني' : 'Timeline'}>
          {events.length === 0 ? (
            <InlineEmpty>{lang === 'ar' ? 'لا أحداث.' : 'No events yet.'}</InlineEmpty>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {events.map(e => (
                <li key={e.id} style={{
                  display: 'grid', gridTemplateColumns: '160px 1fr',
                  padding: '8px 0', borderBottom: '1px solid var(--card-border)',
                  fontSize: 13,
                }}>
                  <div style={{ color: 'var(--ink-muted)' }}>
                    {new Date(e.occurredAt).toLocaleString(
                      lang === 'ar' ? 'ar-SA' : 'en-GB')}
                  </div>
                  <div>
                    <Badge tone="neutral" className="m-r-6">
                      {EVENT_TYPE_LABEL[e.eventType][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                    {e.bandBefore && e.bandAfter && (
                      <Badge tone={BAND_TONE[e.bandAfter]}>
                        {e.bandBefore} → {e.bandAfter}
                      </Badge>
                    )}
                    {(e.noteEn || e.noteAr) && (
                      <div style={{ marginTop: 4 }}>
                        {lang === 'ar' ? e.noteAr : e.noteEn}
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {/* Modal */}
      {modal && (
        <div style={{
          position: 'fixed', inset: 0,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 50,
        }} onClick={closeModal}>
          <div style={{
            background: 'var(--card-bg)',
            padding: 20, borderRadius: 8,
            minWidth: 420, maxWidth: 600, maxHeight: '85vh', overflowY: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{ marginTop: 0, fontSize: 16 }}>
              {modal === 'rating' && (lang === 'ar' ? 'إسناد تصنيف' : 'Assign rating')}
              {modal === 'action' && (lang === 'ar' ? 'تسجيل إجراء تصنيف' : 'Record rating action')}
              {modal === 'limit' && (lang === 'ar' ? 'اعتماد حدّ تعرّض' : 'Approve exposure limit')}
              {modal === 'utilisation' && (lang === 'ar' ? 'تسجيل استخدام' : 'Record utilisation')}
            </h3>

            {modal === 'rating' && (
              <>
                <Field label={lang === 'ar' ? 'النوع' : 'Type'}>
                  <select value={rType}
                    onChange={e => setRType(e.target.value as RatingType)}
                    style={inputStyle}>
                    <option value="INTERNAL">INTERNAL</option>
                    <option value="EXTERNAL">EXTERNAL</option>
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'الدرجة' : 'Grade'}>
                  <input value={rGrade} onChange={e => setRGrade(e.target.value)}
                    placeholder={rType === 'INTERNAL' ? 'R3' : 'A+'}
                    style={inputStyle} />
                </Field>
                {rType === 'EXTERNAL' && (
                  <>
                    <Field label={lang === 'ar' ? 'الوكالة' : 'Agency'}>
                      <input value={rAgency} onChange={e => setRAgency(e.target.value)}
                        placeholder="Moody's / S&P / Fitch / CARE Saudi"
                        style={inputStyle} />
                    </Field>
                    <Field label={lang === 'ar' ? 'المقياس' : 'Scale'}>
                      <input value={rScale} onChange={e => setRScale(e.target.value)}
                        placeholder="S&P long-term" style={inputStyle} />
                    </Field>
                  </>
                )}
                {rType === 'INTERNAL' && (
                  <>
                    <Field label="PD (e.g. 0.0035)">
                      <input value={rPd} onChange={e => setRPd(e.target.value)}
                        type="number" step="any" style={inputStyle} />
                    </Field>
                    <Field label="LGD (e.g. 0.45)">
                      <input value={rLgd} onChange={e => setRLgd(e.target.value)}
                        type="number" step="any" style={inputStyle} />
                    </Field>
                    <Field label="EAD (SAR M)">
                      <input value={rEad} onChange={e => setREad(e.target.value)}
                        type="number" step="any" style={inputStyle} />
                    </Field>
                  </>
                )}
                <Field label={lang === 'ar' ? 'النظرة' : 'Outlook'}>
                  <select value={rOutlook} onChange={e => setROutlook(e.target.value)}
                    style={inputStyle}>
                    <option value="STABLE">STABLE</option>
                    <option value="POSITIVE">POSITIVE</option>
                    <option value="NEGATIVE">NEGATIVE</option>
                    <option value="DEVELOPING">DEVELOPING</option>
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'المبرّر (إنجليزي)' : 'Rationale (English)'}>
                  <textarea rows={2} value={rRationaleEn}
                    onChange={e => setRRationaleEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={lang === 'ar' ? 'المبرّر (عربي)' : 'Rationale (Arabic)'}>
                  <textarea rows={2} dir="rtl" value={rRationaleAr}
                    onChange={e => setRRationaleAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            {modal === 'action' && (
              <>
                <Field label={lang === 'ar' ? 'الإجراء' : 'Action type'}>
                  <select value={aType}
                    onChange={e => setAType(e.target.value as ActionType)}
                    style={inputStyle}>
                    {(['WATCHLIST_NEGATIVE', 'WATCHLIST_POSITIVE', 'WATCHLIST_REMOVE',
                       'DOWNGRADE', 'UPGRADE', 'DEFAULT', 'RECOVERY', 'OUTLOOK_CHANGE'] as ActionType[])
                      .map(t => (
                      <option key={t} value={t}>
                        {ACTION_TYPE_LABEL[t][lang === 'ar' ? 'ar' : 'en']}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'الدرجة قبل' : 'Grade before'}>
                  <input value={aGradeBefore} onChange={e => setAGradeBefore(e.target.value)}
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الدرجة بعد' : 'Grade after'}>
                  <input value={aGradeAfter} onChange={e => setAGradeAfter(e.target.value)}
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'المصدر' : 'Trigger source'}>
                  <select value={aTrigger}
                    onChange={e => setATrigger(e.target.value as TriggerSource)}
                    style={inputStyle}>
                    {(['INTERNAL_REVIEW', 'EXTERNAL_AGENCY', 'EARLY_WARNING',
                       'KRI_BREACH', 'MANUAL'] as TriggerSource[]).map(t => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'المبرّر (إنجليزي)' : 'Rationale (English)'}>
                  <textarea rows={2} value={aRationaleEn}
                    onChange={e => setARationaleEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={lang === 'ar' ? 'المبرّر (عربي)' : 'Rationale (Arabic)'}>
                  <textarea rows={2} dir="rtl" value={aRationaleAr}
                    onChange={e => setARationaleAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            {modal === 'limit' && (
              <>
                <Field label={lang === 'ar' ? 'النطاق' : 'Scope'}>
                  <select value={lScope}
                    onChange={e => setLScope(e.target.value as LimitScope)}
                    style={inputStyle}>
                    <option value="COUNTERPARTY">{lang === 'ar' ? 'طرف' : 'COUNTERPARTY'}</option>
                    <option value="GROUP">{lang === 'ar' ? 'مجموعة' : 'GROUP'}</option>
                  </select>
                </Field>
                {lScope === 'GROUP' && (
                  <Field label={lang === 'ar' ? 'مرجع المجموعة' : 'Group reference'}>
                    <input value={lGroupRef} onChange={e => setLGroupRef(e.target.value)}
                      placeholder="ARAMCO_GROUP" style={inputStyle} />
                  </Field>
                )}
                <Field label={lang === 'ar' ? 'الاسم (إنجليزي)' : 'Display name (English)'}>
                  <input value={lDisplayEn} onChange={e => setLDisplayEn(e.target.value)}
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الاسم (عربي)' : 'Display name (Arabic)'}>
                  <input value={lDisplayAr} dir="rtl"
                    onChange={e => setLDisplayAr(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الحدّ (م.ر)' : 'Limit (SAR M)'}>
                  <input type="number" value={lLimit} min={0}
                    onChange={e => setLLimit(Number(e.target.value))} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'المستخدم الحالي (م.ر)' : 'Current utilised (SAR M)'}>
                  <input type="number" value={lUtilised} min={0}
                    onChange={e => setLUtilised(Number(e.target.value))} style={inputStyle} />
                </Field>
              </>
            )}

            {modal === 'utilisation' && limitForUtil && (
              <>
                <div style={{ fontSize: 13, marginBottom: 8 }}>
                  {lang === 'ar' ? 'الحدّ' : 'Limit'}:{' '}
                  <strong>{limitForUtil.displayNameEn}</strong>
                  {' — '}
                  {Number(limitForUtil.limitSarM).toLocaleString()}{' '}
                  {lang === 'ar' ? 'م.ر' : 'SAR M'}
                </div>
                <Field label={lang === 'ar' ? 'المستخدم الجديد (م.ر)' : 'New utilised (SAR M)'}>
                  <input type="number" value={uUtilised} min={0} step="any"
                    onChange={e => setUUtilised(Number(e.target.value))} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)'}>
                  <textarea rows={2} value={uNoteEn}
                    onChange={e => setUNoteEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)'}>
                  <textarea rows={2} dir="rtl" value={uNoteAr}
                    onChange={e => setUNoteAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <Button variant="primary" disabled={acting}
                onClick={
                  modal === 'rating' ? doAssignRating
                    : modal === 'action' ? doRecordAction
                    : modal === 'limit' ? doUpsertLimit
                    : doRecordUtilisation
                }>
                {acting
                  ? (lang === 'ar' ? 'جارٍ…' : 'Working…')
                  : (lang === 'ar' ? 'تأكيد' : 'Confirm')}
              </Button>
              <Button variant="secondary" onClick={closeModal} disabled={acting}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--card-border)',
  borderRadius: 4,
  fontSize: 13,
  width: '100%',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 8 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
