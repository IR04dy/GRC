import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Shield, Banknote, Send, CheckCircle2, AlertTriangle, Plus,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  rrpApi,
  CYCLE_STATUS_LABEL, CYCLE_STATUS_TONE,
  OPTION_CATEGORY_LABEL, FEASIBILITY_LABEL,
  MATERIALITY_LABEL, MATERIALITY_TONE,
  INDICATOR_CATEGORY_LABEL, BAND_LABEL, BAND_TONE,
  EVENT_TYPE_LABEL, nextAllowed,
  type CycleResponse, type OptionResponse, type CriticalFunctionResponse,
  type IndicatorResponse, type EventResponse,
  type CycleStatus, type OptionCategory, type Feasibility,
  type Materiality, type IndicatorCategory, type Direction, type Band,
} from '../../lib/rrp';

type Tab = 'options' | 'critical' | 'indicators' | 'timeline';

export function RrpCycleDetailPage() {
  const { id } = useParams();
  const cycleId = Number(id);
  const { lang } = useI18n();

  const [cycle, setCycle] = useState<CycleResponse | null>(null);
  const [options, setOptions] = useState<OptionResponse[]>([]);
  const [cfs, setCfs] = useState<CriticalFunctionResponse[]>([]);
  const [indicators, setIndicators] = useState<IndicatorResponse[]>([]);
  const [events, setEvents] = useState<EventResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);
  const [tab, setTab] = useState<Tab>('options');

  // Action modal
  const [modal, setModal] = useState<null | 'transition' | 'submit' | 'option'
                                       | 'critical' | 'indicator' | 'measure'>(null);
  const [pendingTo, setPendingTo] = useState<CycleStatus | null>(null);
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');
  const [ackRef, setAckRef] = useState('');
  const [measureIndicatorId, setMeasureIndicatorId] = useState<number | null>(null);
  const [measureValue, setMeasureValue] = useState('');
  const [measureAt, setMeasureAt] = useState(
    new Date().toISOString().slice(0, 10));

  // Option form
  const [oCode, setOCode] = useState('');
  const [oCat, setOCat] = useState<OptionCategory>('CAPITAL');
  const [oTitleEn, setOTitleEn] = useState('');
  const [oTitleAr, setOTitleAr] = useState('');
  const [oCapacity, setOCapacity] = useState(0);
  const [oTime, setOTime] = useState(30);
  const [oFeas, setOFeas] = useState<Feasibility>('MEDIUM');

  // Critical function form
  const [cfCode, setCfCode] = useState('');
  const [cfTitleEn, setCfTitleEn] = useState('');
  const [cfTitleAr, setCfTitleAr] = useState('');
  const [cfMat, setCfMat] = useState<Materiality>('HIGH');
  const [cfMtpd, setCfMtpd] = useState(24);

  // Indicator form
  const [iCode, setICode] = useState('');
  const [iCat, setICat] = useState<IndicatorCategory>('CAPITAL');
  const [iTitleEn, setITitleEn] = useState('');
  const [iTitleAr, setITitleAr] = useState('');
  const [iUnit, setIUnit] = useState('%');
  const [iDirection, setIDirection] = useState<Direction>('HIGHER_IS_BETTER');
  const [iGreen, setIGreen] = useState('');
  const [iAmber, setIAmber] = useState('');
  const [iValue, setIValue] = useState('');

  const reload = async () => {
    if (!cycleId) return;
    setLoading(true); setError(null);
    try {
      const [c, opts, cfList, inds, evs] = await Promise.all([
        rrpApi.getCycle(cycleId),
        rrpApi.listOptions(cycleId).catch(() => []),
        rrpApi.listCriticalFunctions(cycleId).catch(() => []),
        rrpApi.listIndicators(cycleId).catch(() => []),
        rrpApi.listEvents(cycleId).catch(() => []),
      ]);
      setCycle(c); setOptions(opts); setCfs(cfList);
      setIndicators(inds); setEvents(evs);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الدورة' : 'Failed to load cycle'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [cycleId]);

  const closeModal = () => {
    setModal(null); setPendingTo(null);
    setNoteEn(''); setNoteAr(''); setAckRef('');
    setOCode(''); setOTitleEn(''); setOTitleAr(''); setOCapacity(0); setOTime(30);
    setCfCode(''); setCfTitleEn(''); setCfTitleAr(''); setCfMtpd(24);
    setICode(''); setITitleEn(''); setITitleAr('');
    setIGreen(''); setIAmber(''); setIValue('');
    setMeasureIndicatorId(null); setMeasureValue('');
  };

  const doTransition = async () => {
    if (!cycle || !pendingTo) return;
    setActing(true);
    try {
      await rrpApi.transitionCycle(cycle.id, {
        toStatus: pendingTo,
        noteEn: noteEn || undefined, noteAr: noteAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transition failed');
    } finally { setActing(false); }
  };

  const doSubmit = async () => {
    if (!cycle || !ackRef.trim()) return;
    setActing(true);
    try {
      await rrpApi.submitCycle(cycle.id, {
        regulatorAckReference: ackRef.trim(),
        noteEn: noteEn || undefined, noteAr: noteAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Submit failed');
    } finally { setActing(false); }
  };

  const doAck = async () => {
    if (!cycle) return;
    setActing(true);
    try {
      await rrpApi.acknowledgeCycle(cycle.id);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Ack failed');
    } finally { setActing(false); }
  };

  const doAddOption = async () => {
    if (!cycle || !oCode.trim() || !oTitleEn.trim() || !oTitleAr.trim()) return;
    setActing(true);
    try {
      await rrpApi.addOption(cycle.id, {
        optionCode: oCode.trim(), category: oCat,
        titleEn: oTitleEn.trim(), titleAr: oTitleAr.trim(),
        capacitySarM: oCapacity, timeToExecuteDays: oTime,
        feasibility: oFeas,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Add option failed');
    } finally { setActing(false); }
  };

  const doAddCf = async () => {
    if (!cycle || !cfCode.trim() || !cfTitleEn.trim() || !cfTitleAr.trim()) return;
    setActing(true);
    try {
      await rrpApi.addCriticalFunction(cycle.id, {
        functionCode: cfCode.trim(),
        titleEn: cfTitleEn.trim(), titleAr: cfTitleAr.trim(),
        materiality: cfMat,
        maxTolerableDowntimeHours: cfMtpd,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Add CF failed');
    } finally { setActing(false); }
  };

  const doAddIndicator = async () => {
    if (!cycle || !iCode.trim() || !iTitleEn.trim() || !iTitleAr.trim()) return;
    setActing(true);
    try {
      await rrpApi.addIndicator(cycle.id, {
        indicatorCode: iCode.trim(), category: iCat,
        titleEn: iTitleEn.trim(), titleAr: iTitleAr.trim(),
        unit: iUnit, direction: iDirection,
        greenThreshold: iGreen ? Number(iGreen) : undefined,
        amberThreshold: iAmber ? Number(iAmber) : undefined,
        currentValue: iValue ? Number(iValue) : undefined,
        measuredAt: iValue ? new Date().toISOString().slice(0, 10) : undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Add indicator failed');
    } finally { setActing(false); }
  };

  const doMeasure = async () => {
    if (!measureIndicatorId || !measureValue) return;
    setActing(true);
    try {
      await rrpApi.recordMeasurement(measureIndicatorId, {
        value: Number(measureValue),
        measuredAt: measureAt,
        noteEn: noteEn || undefined, noteAr: noteAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Measurement failed');
    } finally { setActing(false); }
  };

  if (loading && !cycle) {
    return (
      <Shell title={lang === 'ar' ? 'الدورة' : 'Cycle'}>
        <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      </Shell>
    );
  }
  if (!cycle) {
    return (
      <Shell title={lang === 'ar' ? 'الدورة' : 'Cycle'}>
        <EmptyState Icon={Shield}
          title={lang === 'ar' ? 'لم تُعثر على الدورة' : 'Cycle not found'}
          sub={error || ''} />
      </Shell>
    );
  }

  const next = nextAllowed(cycle.status);
  const isTerminal = cycle.status === 'CLOSED';
  const inOptionsCfsAddable = !isTerminal && cycle.status !== 'SUBMITTED'
                              && cycle.status !== 'ACKNOWLEDGED';

  const optionCols: Column<OptionResponse>[] = [
    { header: lang === 'ar' ? 'الرمز' : 'Code', width: '100px', mono: true,
      cell: o => <span style={{ fontSize: 12 }}>{o.optionCode}</span> },
    { header: lang === 'ar' ? 'الفئة' : 'Category', width: '130px',
      cell: o => <Badge tone="info">
        {OPTION_CATEGORY_LABEL[o.category][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: o => <span style={{ fontSize: 13, fontWeight: 500 }}>
        {lang === 'ar' ? o.titleAr : o.titleEn}
      </span> },
    { header: lang === 'ar' ? 'الطاقة' : 'Capacity', width: '120px',
      cell: o => <span style={{ fontSize: 12 }}>
        {o.capacitySarM.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')}{' '}
        {lang === 'ar' ? 'م.ر.' : 'SAR M'}
      </span> },
    { header: lang === 'ar' ? 'زمن' : 'Days', width: '70px',
      cell: o => <span style={{ fontSize: 12 }}>{o.timeToExecuteDays}d</span> },
    { header: lang === 'ar' ? 'الجدوى' : 'Feasibility', width: '110px',
      cell: o => <Badge tone={
        o.feasibility === 'HIGH' ? 'success'
          : o.feasibility === 'LOW' ? 'danger' : 'warning'}>
        {FEASIBILITY_LABEL[o.feasibility][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
  ];

  const cfCols: Column<CriticalFunctionResponse>[] = [
    { header: lang === 'ar' ? 'الرمز' : 'Code', width: '90px', mono: true,
      cell: c => <span style={{ fontSize: 12 }}>{c.functionCode}</span> },
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: c => <span style={{ fontSize: 13, fontWeight: 500 }}>
        {lang === 'ar' ? c.titleAr : c.titleEn}
      </span> },
    { header: lang === 'ar' ? 'الأهمّية' : 'Materiality', width: '130px',
      cell: c => <Badge tone={MATERIALITY_TONE[c.materiality]}>
        {MATERIALITY_LABEL[c.materiality][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'الحدّ الأقصى' : 'MTPD', width: '100px',
      cell: c => <span style={{ fontSize: 12 }}>
        {c.maxTolerableDowntimeHours ?? '—'}h
      </span> },
    { header: lang === 'ar' ? 'العملاء' : 'Customers', width: '120px',
      cell: c => <span style={{ fontSize: 12 }}>
        {c.customerCountEstimate?.toLocaleString() ?? '—'}
      </span> },
  ];

  const indCols: Column<IndicatorResponse>[] = [
    { header: lang === 'ar' ? 'الرمز' : 'Code', width: '120px', mono: true,
      cell: i => <span style={{ fontSize: 12 }}>{i.indicatorCode}</span> },
    { header: lang === 'ar' ? 'الفئة' : 'Category', width: '140px',
      cell: i => <Badge tone="info">
        {INDICATOR_CATEGORY_LABEL[i.category][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'العنوان' : 'Title',
      cell: i => <span style={{ fontSize: 13, fontWeight: 500 }}>
        {lang === 'ar' ? i.titleAr : i.titleEn}
      </span> },
    { header: lang === 'ar' ? 'القيمة' : 'Current', width: '110px',
      cell: i => <span style={{ fontSize: 12 }}>
        {i.currentValue == null ? '—'
          : `${i.currentValue}${i.unit ? ' ' + i.unit : ''}`}
      </span> },
    { header: lang === 'ar' ? 'النطاق' : 'Band', width: '100px',
      cell: i => <Badge tone={BAND_TONE[i.currentBand]}>
        {BAND_LABEL[i.currentBand][lang === 'ar' ? 'ar' : 'en']}
      </Badge> },
    { header: lang === 'ar' ? 'إجراء' : 'Action', width: '120px',
      cell: i => (
        <Button variant="ghost"
          onClick={(e) => {
            e.stopPropagation();
            setMeasureIndicatorId(i.id);
            setModal('measure');
          }}>
          {lang === 'ar' ? 'قياس' : 'Measure'}
        </Button>
      ) },
  ];

  return (
    <Shell title={cycle.reference}>
      <div style={{ marginBottom: 8 }}>
        <Link to="/rrp/cycles"
          style={{ fontSize: 13, color: 'var(--ink-muted)' }}>
          ← {lang === 'ar' ? 'العودة للدورات' : 'Back to cycles'}
        </Link>
      </div>
      <PageHeader
        eyebrow={`${cycle.reference} · FY${cycle.fiscalYear}`}
        title={lang === 'ar' ? cycle.titleAr : cycle.titleEn}
        sub={lang === 'ar' ? 'ورشة دورة الخطّة' : 'RRP cycle workspace'}
        actions={
          <Badge tone={CYCLE_STATUS_TONE[cycle.status]}>
            {CYCLE_STATUS_LABEL[cycle.status][lang === 'ar' ? 'ar' : 'en']}
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
        <Kpi Icon={Banknote} tone="info"
          label={lang === 'ar' ? 'طاقة الاستعادة' : 'Recovery capacity'}
          value={(cycle.totalRecoveryCapacitySarM ?? 0).toLocaleString(
            lang === 'ar' ? 'ar-SA' : 'en-US') + ' ' +
            (lang === 'ar' ? 'م.ر.' : 'SAR M')} />
        <Kpi Icon={Shield} tone="info"
          label={lang === 'ar' ? 'الوظائف الحيوية' : 'Critical functions'}
          value={cycle.criticalFunctionCount ?? 0} />
        <Kpi Icon={AlertTriangle} tone={
          (cycle.indicatorsRed ?? 0) > 0 ? 'danger'
            : (cycle.indicatorsAmber ?? 0) > 0 ? 'warning' : 'success'}
          label={lang === 'ar' ? 'مؤشّرات أحمر/كهرماني' : 'Amber/Red'}
          value={(cycle.indicatorsRed ?? 0) + (cycle.indicatorsAmber ?? 0)} />
        <Kpi Icon={CheckCircle2} tone={
          cycle.submittedAt ? 'success' : 'neutral'}
          label={lang === 'ar' ? 'تاريخ التقديم' : 'Submitted'}
          value={cycle.submittedAt
            ? new Date(cycle.submittedAt).toLocaleDateString(
                lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'} />
      </div>

      {/* Action panel */}
      {!isTerminal && (
        <Card padded
          title={lang === 'ar' ? 'الإجراءات المتاحة' : 'Available actions'}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {next.map(to => (
              <Button key={to} variant="secondary"
                onClick={() => { setPendingTo(to); setModal('transition'); }}>
                → {CYCLE_STATUS_LABEL[to][lang === 'ar' ? 'ar' : 'en']}
              </Button>
            ))}
            {cycle.status === 'APPROVED' && (
              <Button variant="primary" Icon={Send}
                onClick={() => setModal('submit')}>
                {lang === 'ar' ? 'تقديم لساما' : 'Submit to SAMA'}
              </Button>
            )}
            {cycle.status === 'SUBMITTED' && (
              <Button variant="primary" Icon={CheckCircle2}
                onClick={doAck} disabled={acting}>
                {lang === 'ar' ? 'تسجيل إقرار ساما' : 'Record SAMA ack'}
              </Button>
            )}
          </div>
        </Card>
      )}

      {/* Tabs */}
      <div style={{
        display: 'flex', gap: 4, marginTop: 16, marginBottom: 8,
        borderBottom: '1px solid var(--border)',
      }}>
        {(['options', 'critical', 'indicators', 'timeline'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            style={{
              padding: '8px 16px', border: 'none', background: 'none',
              fontSize: 13, fontWeight: 500, cursor: 'pointer',
              borderBottom: tab === t ? '2px solid var(--primary)' : '2px solid transparent',
              color: tab === t ? 'var(--primary)' : 'var(--ink)',
            }}>
            {t === 'options' && (lang === 'ar' ? 'الخيارات' : 'Options')}
            {t === 'critical' && (lang === 'ar' ? 'الوظائف الحيوية' : 'Critical Fns')}
            {t === 'indicators' && (lang === 'ar' ? 'المؤشّرات' : 'Indicators')}
            {t === 'timeline' && (lang === 'ar' ? 'السجل' : 'Timeline')}
          </button>
        ))}
      </div>

      {tab === 'options' && (
        <Card padded
          title={lang === 'ar' ? 'خيارات الاستعادة' : 'Recovery options'}
          action={inOptionsCfsAddable ? (
            <Button variant="primary" Icon={Plus}
              onClick={() => setModal('option')}>
              {lang === 'ar' ? 'خيار' : 'Add option'}
            </Button>
          ) : undefined}>
          <DataTable columns={optionCols} rows={options}
            empty={<EmptyState Icon={Banknote}
              title={lang === 'ar' ? 'لا خيارات' : 'No options yet'}
              sub={lang === 'ar'
                ? 'أضف خيارات رأس المال والسيولة وبيع الأصول.'
                : 'Add capital, liquidity, and asset-sale options.'} />}
          />
        </Card>
      )}

      {tab === 'critical' && (
        <Card padded
          title={lang === 'ar' ? 'الوظائف الحيوية' : 'Critical functions'}
          action={inOptionsCfsAddable ? (
            <Button variant="primary" Icon={Plus}
              onClick={() => setModal('critical')}>
              {lang === 'ar' ? 'وظيفة' : 'Add function'}
            </Button>
          ) : undefined}>
          <DataTable columns={cfCols} rows={cfs}
            empty={<EmptyState Icon={Shield}
              title={lang === 'ar' ? 'لا توجد وظائف حيوية'
                                    : 'No critical functions yet'}
              sub={lang === 'ar'
                ? 'سجّل الدفعات، الإيداع، والائتمان كحدّ أدنى.'
                : 'Register payments, deposits, and credit at a minimum.'} />}
          />
        </Card>
      )}

      {tab === 'indicators' && (
        <Card padded
          title={lang === 'ar' ? 'مؤشّرات الاستعادة' : 'Recovery indicators'}
          action={inOptionsCfsAddable ? (
            <Button variant="primary" Icon={Plus}
              onClick={() => setModal('indicator')}>
              {lang === 'ar' ? 'مؤشّر' : 'Add indicator'}
            </Button>
          ) : undefined}>
          <DataTable columns={indCols} rows={indicators}
            empty={<EmptyState Icon={AlertTriangle}
              title={lang === 'ar' ? 'لا مؤشّرات' : 'No indicators yet'}
              sub={lang === 'ar'
                ? 'كفاية رأس المال، تغطية السيولة، التمويل المستقر…'
                : 'CAR, LCR, NSFR, NPL ratio, cost of risk…'} />}
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
                  display: 'grid',
                  gridTemplateColumns: '160px 1fr',
                  padding: '8px 0',
                  borderBottom: '1px solid var(--border)',
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
                    {e.bandAfter && (
                      <Badge tone={BAND_TONE[e.bandAfter as Band]}>
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
              {modal === 'transition' && pendingTo && (lang === 'ar'
                ? 'الانتقال إلى '
                : 'Transition to ') + CYCLE_STATUS_LABEL[pendingTo][lang === 'ar' ? 'ar' : 'en']}
              {modal === 'submit' && (lang === 'ar' ? 'تقديم لساما' : 'Submit to SAMA')}
              {modal === 'option' && (lang === 'ar' ? 'إضافة خيار استعادة' : 'Add recovery option')}
              {modal === 'critical' && (lang === 'ar' ? 'إضافة وظيفة حيوية' : 'Add critical function')}
              {modal === 'indicator' && (lang === 'ar' ? 'إضافة مؤشّر' : 'Add indicator')}
              {modal === 'measure' && (lang === 'ar' ? 'تسجيل قياس' : 'Record measurement')}
            </h3>

            {modal === 'transition' && (
              <>
                <Field label={lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)'}>
                  <textarea rows={2} value={noteEn}
                    onChange={e => setNoteEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)'}>
                  <textarea rows={2} dir="rtl" value={noteAr}
                    onChange={e => setNoteAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            {modal === 'submit' && (
              <>
                <Field label={lang === 'ar' ? 'رقم إقرار ساما' : 'SAMA ack reference'}>
                  <input value={ackRef} onChange={e => setAckRef(e.target.value)}
                    placeholder="SAMA-RRP-2026-00042" style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)'}>
                  <textarea rows={2} value={noteEn}
                    onChange={e => setNoteEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)'}>
                  <textarea rows={2} dir="rtl" value={noteAr}
                    onChange={e => setNoteAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            {modal === 'option' && (
              <>
                <Field label={lang === 'ar' ? 'الرمز' : 'Code'}>
                  <input value={oCode} onChange={e => setOCode(e.target.value)}
                    placeholder="OPT-001" style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الفئة' : 'Category'}>
                  <select value={oCat}
                    onChange={e => setOCat(e.target.value as OptionCategory)}
                    style={inputStyle}>
                    {Object.entries(OPTION_CATEGORY_LABEL).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v[lang === 'ar' ? 'ar' : 'en']}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}>
                  <input value={oTitleEn} onChange={e => setOTitleEn(e.target.value)}
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}>
                  <input value={oTitleAr} dir="rtl"
                    onChange={e => setOTitleAr(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الطاقة (م.ر.)' : 'Capacity (SAR M)'}>
                  <input type="number" value={oCapacity} min={0}
                    onChange={e => setOCapacity(Number(e.target.value))} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'زمن التنفيذ (أيام)' : 'Time-to-execute (days)'}>
                  <input type="number" value={oTime} min={0}
                    onChange={e => setOTime(Number(e.target.value))} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الجدوى' : 'Feasibility'}>
                  <select value={oFeas} onChange={e => setOFeas(e.target.value as Feasibility)}
                    style={inputStyle}>
                    {(['HIGH', 'MEDIUM', 'LOW'] as Feasibility[]).map(f => (
                      <option key={f} value={f}>
                        {FEASIBILITY_LABEL[f][lang === 'ar' ? 'ar' : 'en']}
                      </option>
                    ))}
                  </select>
                </Field>
              </>
            )}

            {modal === 'critical' && (
              <>
                <Field label={lang === 'ar' ? 'الرمز' : 'Code'}>
                  <input value={cfCode} onChange={e => setCfCode(e.target.value)}
                    placeholder="CF-001" style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}>
                  <input value={cfTitleEn} onChange={e => setCfTitleEn(e.target.value)}
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}>
                  <input value={cfTitleAr} dir="rtl"
                    onChange={e => setCfTitleAr(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الأهمّية' : 'Materiality'}>
                  <select value={cfMat} onChange={e => setCfMat(e.target.value as Materiality)}
                    style={inputStyle}>
                    {Object.entries(MATERIALITY_LABEL).map(([k, v]) => (
                      <option key={k} value={k}>{v[lang === 'ar' ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'الحدّ الأقصى للتعطّل (ساعات)' : 'MTPD (hours)'}>
                  <input type="number" value={cfMtpd} min={0}
                    onChange={e => setCfMtpd(Number(e.target.value))} style={inputStyle} />
                </Field>
              </>
            )}

            {modal === 'indicator' && (
              <>
                <Field label={lang === 'ar' ? 'الرمز' : 'Code'}>
                  <input value={iCode} onChange={e => setICode(e.target.value)}
                    placeholder="IND-CET1" style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الفئة' : 'Category'}>
                  <select value={iCat}
                    onChange={e => setICat(e.target.value as IndicatorCategory)}
                    style={inputStyle}>
                    {Object.entries(INDICATOR_CATEGORY_LABEL).map(([k, v]) => (
                      <option key={k} value={k}>{v[lang === 'ar' ? 'ar' : 'en']}</option>
                    ))}
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'العنوان (إنجليزي)' : 'Title (English)'}>
                  <input value={iTitleEn} onChange={e => setITitleEn(e.target.value)}
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'العنوان (عربي)' : 'Title (Arabic)'}>
                  <input value={iTitleAr} dir="rtl"
                    onChange={e => setITitleAr(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الوحدة' : 'Unit'}>
                  <input value={iUnit} onChange={e => setIUnit(e.target.value)}
                    placeholder="%" style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'الاتجاه' : 'Direction'}>
                  <select value={iDirection}
                    onChange={e => setIDirection(e.target.value as Direction)}
                    style={inputStyle}>
                    <option value="HIGHER_IS_BETTER">
                      {lang === 'ar' ? 'الأعلى أفضل (CAR, LCR)' : 'Higher is better (CAR, LCR)'}
                    </option>
                    <option value="LOWER_IS_BETTER">
                      {lang === 'ar' ? 'الأقل أفضل (NPL, تكلفة المخاطر)' : 'Lower is better (NPL, cost of risk)'}
                    </option>
                  </select>
                </Field>
                <Field label={lang === 'ar' ? 'حدّ الأخضر' : 'Green threshold'}>
                  <input type="number" value={iGreen} step="any"
                    onChange={e => setIGreen(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'حدّ الكهرماني' : 'Amber threshold'}>
                  <input type="number" value={iAmber} step="any"
                    onChange={e => setIAmber(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'القيمة الحالية' : 'Current value'}>
                  <input type="number" value={iValue} step="any"
                    onChange={e => setIValue(e.target.value)} style={inputStyle} />
                </Field>
              </>
            )}

            {modal === 'measure' && (
              <>
                <Field label={lang === 'ar' ? 'القيمة الجديدة' : 'New value'}>
                  <input type="number" value={measureValue} step="any"
                    onChange={e => setMeasureValue(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'تاريخ القياس' : 'Measured at'}>
                  <input type="date" value={measureAt}
                    onChange={e => setMeasureAt(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)'}>
                  <textarea rows={2} value={noteEn}
                    onChange={e => setNoteEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)'}>
                  <textarea rows={2} dir="rtl" value={noteAr}
                    onChange={e => setNoteAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <Button variant="primary" disabled={acting}
                onClick={
                  modal === 'transition' ? doTransition
                    : modal === 'submit' ? doSubmit
                    : modal === 'option' ? doAddOption
                    : modal === 'critical' ? doAddCf
                    : modal === 'indicator' ? doAddIndicator
                    : doMeasure
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

      {/* Documents & control mapping — recovery/resolution plan, board approval, controls */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="RRP_CYCLE" entityId={cycle.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="RRP_CYCLE" entityId={cycle.id} />
          </div>
        </Card>
      </div>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--border)',
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
