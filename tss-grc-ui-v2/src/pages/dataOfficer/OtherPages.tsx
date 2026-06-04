import { useEffect, useState } from 'react';
import { Plus, ShieldCheck, ScrollText, BookOpen, Archive, Database, Sigma, Scale, FolderOpen } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import {
  dataOfficerApi,
  CONSENT_STATUS_LABEL, CONSENT_STATUS_TONE,
  DPIA_STATUS_LABEL, DPIA_STATUS_TONE,
  NOTICE_STATUS_LABEL, NOTICE_STATUS_TONE, nextNoticeStatuses,
  NDR_STATUS_LABEL, NDR_STATUS_TONE, nextNdrStatuses,
  ETHICS_STATUS_LABEL, ETHICS_STATUS_TONE, nextEthicsStatuses,
  type ConsentResponse, type DpiaResponse, type NoticeResponse, type NoticeStatus,
  type RetentionRuleResponse, type DisposalJobResponse, type NdrResponse, type NdrStatus,
  type CatalogEntryResponse, type QualityRuleResponse, type EthicsResponse, type EthicsStatus,
} from '../../lib/dataOfficer';

/* ─── Helpers ─────────────────────────────────────────────────────── */
function useList<T>(loader: () => Promise<T[]>) {
  const [rows, setRows] = useState<T[]>([]);
  const [error, setError] = useState<string | null>(null);
  const reload = async () => {
    if (!isAuthenticated()) return;
    try { setRows(await loader()); setError(null); }
    catch (err) { setError(err instanceof ApiError ? err.message : 'Failed to load'); }
  };
  useEffect(() => { void reload(); }, []);
  return { rows, error, reload, setError };
}

/* ─── Consent ─────────────────────────────────────────────────────── */
export function ConsentPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows, error, reload } = useList<ConsentResponse>(() => dataOfficerApi.listConsents());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [docsTarget, setDocsTarget] = useState<ConsentResponse | null>(null);
  const [ref, setRef] = useState(''); const [subject, setSubject] = useState('');
  const [purpose, setPurpose] = useState('MARKETING'); const [channel, setChannel] = useState('WEB');
  const [legalBasis, setLegalBasis] = useState('CONSENT');
  const [isSpecial, setIsSpecial] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.recordConsent({
        reference: ref, dataSubjectId: subject, purposeCode: purpose,
        channel, legalBasis, isSpecialCategory: isSpecial,
        grantedAt: new Date().toISOString(),
      });
      setShowNew(false); setRef(''); setSubject('');
      await reload();
    } finally { setBusy(false); }
  }
  async function withdraw(id: number) {
    const reason = prompt(ar ? 'سبب السحب' : 'Withdrawal reason');
    if (!reason) return;
    await dataOfficerApi.withdrawConsent(id, reason);
    await reload();
  }

  const cols: Column<ConsentResponse>[] = [
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '140px', cell: c => <span style={{ fontSize: 12 }}>{c.reference}</span> },
    { header: ar ? 'صاحب البيانات' : 'Subject', cell: c => <span style={{ fontSize: 12 }}>{c.dataSubjectId ?? '—'}</span> },
    { header: ar ? 'الغرض' : 'Purpose', cell: c => <span style={{ fontSize: 12 }}>{c.purposeCode}</span> },
    { header: ar ? 'الأساس' : 'Basis', cell: c => <span style={{ fontSize: 12 }}>{c.legalBasis ?? '—'}</span> },
    { header: ar ? 'الحالة' : 'Status', width: '120px',
      cell: c => <Badge tone={CONSENT_STATUS_TONE[c.status]}>{CONSENT_STATUS_LABEL[c.status][ar ? 'ar' : 'en']}</Badge> },
    { header: '', width: '150px', align: 'end',
      cell: c => (
        <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
          <Button size="sm" variant="ghost" Icon={FolderOpen} title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
            onClick={() => setDocsTarget(c)} />
          {c.status === 'GRANTED' && <Button size="sm" variant="ghost" onClick={() => withdraw(c.id)}>{ar ? 'سحب' : 'Withdraw'}</Button>}
        </span>
      ) },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'الموافقات · المرحلة ٣٣' : 'Consent · Sprint 33'}
        title={ar ? 'سجل الموافقات' : 'Consent Registry'}
        sub={ar ? 'مادة ٥-٦ — موافقة بغرض محدّد · تتبع المنح والسحب' : 'PDPL Art. 5-6 — purpose-bound · grant + withdrawal trace'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'موافقة جديدة' : 'New consent')}</Button>}
      />
      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="CON-2026-NNNN" /></label>
          <label>{ar ? 'صاحب البيانات' : 'Data subject'}<input className="input" value={subject} onChange={e => setSubject(e.target.value)} placeholder="customer-no / national-id (hashed)" /></label>
          <label>{ar ? 'الغرض' : 'Purpose'}
            <select className="input" value={purpose} onChange={e => setPurpose(e.target.value)}>
              {['MARKETING','CREDIT_DECISION','PROFILING','FRAUD_DETECTION','CUSTOMER_SERVICE','THIRD_PARTY_SHARING'].map(p => <option key={p}>{p}</option>)}
            </select></label>
          <label>{ar ? 'القناة' : 'Channel'}
            <select className="input" value={channel} onChange={e => setChannel(e.target.value)}>
              {['WEB','MOBILE','BRANCH','IVR','ATM'].map(c => <option key={c}>{c}</option>)}
            </select></label>
          <label>{ar ? 'الأساس النظامي' : 'Legal basis'}
            <select className="input" value={legalBasis} onChange={e => setLegalBasis(e.target.value)}>
              {['CONSENT','CONTRACT','LEGAL_OBLIGATION','VITAL_INTEREST','PUBLIC_INTEREST','LEGITIMATE_INTEREST'].map(b => <option key={b}>{b}</option>)}
            </select></label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={isSpecial} onChange={e => setIsSpecial(e.target.checked)} />
            {ar ? 'بيانات حسّاسة (مادة ٤)' : 'Special category (Art. 4)'}</label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Record'}</Button>
          </div></form></Card>
      )}
      <Card>{rows.length === 0
        ? <EmptyState Icon={ShieldCheck} title={ar ? 'لا توجد موافقات' : 'No consents'} sub={ar ? 'سجّل أول موافقة' : 'Record the first consent'} />
        : <DataTable columns={cols} rows={rows} />}</Card>
      <EntityDocsModal entityType="CONSENT" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        recordLabel={docsTarget?.reference} onClose={() => setDocsTarget(null)} />
    </Shell>
  );
}

/* ─── DPIA ────────────────────────────────────────────────────────── */
const DPIA_TRIGGERS = ['NEW_PROCESSING','SCOPE_CHANGE','NEW_TECHNOLOGY','SPECIAL_CATEGORY','LARGE_SCALE','AUTOMATED_DECISION','CROSS_BORDER','SURVEILLANCE'];
export function DpiaPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows, error, reload } = useList<DpiaResponse>(() => dataOfficerApi.listDpia());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [docsTarget, setDocsTarget] = useState<DpiaResponse | null>(null);
  const [ref, setRef] = useState(''); const [titleEn, setTitleEn] = useState(''); const [titleAr, setTitleAr] = useState('');
  const [trigger, setTrigger] = useState('NEW_PROCESSING');
  const [highRisk, setHighRisk] = useState(false);
  const [descEn, setDescEn] = useState(''); const [descAr, setDescAr] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createDpia({ reference: ref, titleEn, titleAr, triggerReason: trigger,
        isHighRisk: highRisk, processingDescriptionEn: descEn, processingDescriptionAr: descAr });
      setShowNew(false); setRef(''); setTitleEn(''); setTitleAr(''); setDescEn(''); setDescAr('');
      await reload();
    } finally { setBusy(false); }
  }

  const cols: Column<DpiaResponse>[] = [
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '140px', cell: d => <span style={{ fontSize: 12 }}>{d.reference}</span> },
    { header: ar ? 'العنوان' : 'Title',
      cell: d => <div><div style={{ fontWeight: 600, fontSize: 13 }}>{ar ? d.titleAr : d.titleEn}</div>
        <div style={{ fontSize: 11, opacity: 0.7 }}>{d.triggerReason}{d.isHighRisk && <span style={{ color: 'var(--danger,#a00)' }}> · {ar ? 'عالي المخاطر' : 'high-risk'}</span>}</div></div> },
    { header: ar ? 'الحالة' : 'Status', width: '180px',
      cell: d => <Badge tone={DPIA_STATUS_TONE[d.status]}>{DPIA_STATUS_LABEL[d.status][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'مراجعة قادمة' : 'Next review', width: '120px',
      cell: d => <span style={{ fontSize: 12 }}>{d.nextReviewDue ?? '—'}</span> },
    { header: '', width: '60px', align: 'end',
      cell: d => <Button variant="ghost" size="sm" Icon={FolderOpen}
        title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
        onClick={(e) => { e.stopPropagation(); setDocsTarget(d); }} /> },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'تقييم الأثر · المرحلة ٣٣' : 'DPIA · Sprint 33'}
        title={ar ? 'سجل تقييمات أثر حماية البيانات' : 'DPIA Register'}
        sub={ar ? 'مادة ٢٥ — تقييم الأثر للمعالجة عالية المخاطر · رأي مسؤول الحماية' : 'PDPL Art. 25 — Impact assessment for high-risk processing · DPO opinion log'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'تقييم جديد' : 'New DPIA')}</Button>}
      />
      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="DPIA-2026-NNNN" /></label>
          <label>{ar ? 'المُحرّك' : 'Trigger'}
            <select className="input" value={trigger} onChange={e => setTrigger(e.target.value)}>{DPIA_TRIGGERS.map(t => <option key={t}>{t}</option>)}</select></label>
          <label>{ar ? 'العنوان (EN)' : 'Title (EN)'}<input className="input" required value={titleEn} onChange={e => setTitleEn(e.target.value)} /></label>
          <label>{ar ? 'العنوان (AR)' : 'Title (AR)'}<input className="input" required value={titleAr} onChange={e => setTitleAr(e.target.value)} dir="rtl" /></label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={highRisk} onChange={e => setHighRisk(e.target.checked)} />
            {ar ? 'عالي المخاطر' : 'High-risk'}</label>
          <div></div>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'وصف المعالجة (EN)' : 'Processing description (EN)'}<textarea className="input" rows={2} value={descEn} onChange={e => setDescEn(e.target.value)} /></label>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'وصف المعالجة (AR)' : 'Processing description (AR)'}<textarea className="input" rows={2} value={descAr} onChange={e => setDescAr(e.target.value)} dir="rtl" /></label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Draft'}</Button>
          </div></form></Card>
      )}
      <Card>{rows.length === 0
        ? <EmptyState Icon={ScrollText} title={ar ? 'لا توجد تقييمات' : 'No DPIAs'} sub={ar ? 'سجّل أول تقييم أثر' : 'Draft the first DPIA'} />
        : <DataTable columns={cols} rows={rows} />}</Card>
      <EntityDocsModal entityType="DPIA" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        recordLabel={docsTarget?.reference} onClose={() => setDocsTarget(null)} />
    </Shell>
  );
}

/* ─── Privacy Notice ───────────────────────────────────────────────── */
const NOTICE_CHANNELS = ['WEB','MOBILE','BRANCH','IVR','ATM','SMS','EMAIL'];
export function NoticePage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows, error, reload, setError } = useList<NoticeResponse>(() => dataOfficerApi.listNotices());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ref, setRef] = useState(''); const [ver, setVer] = useState('');
  const [ch, setCh] = useState('WEB');
  const [titleEn, setTitleEn] = useState(''); const [titleAr, setTitleAr] = useState('');
  const [bodyEn, setBodyEn] = useState(''); const [bodyAr, setBodyAr] = useState('');
  const [reConsent, setReConsent] = useState(false);

  // Lifecycle transition (PDPL Art.12 — DRAFT→IN_REVIEW→PUBLISHED→SUPERSEDED/RETIRED)
  const [moveTarget, setMoveTarget] = useState<NoticeResponse | null>(null);
  const [toStatus, setToStatus] = useState<NoticeStatus | null>(null);
  const [supersededBy, setSupersededBy] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createNotice({ reference: ref, versionLabel: ver, channel: ch,
        titleEn, titleAr, bodyEn, bodyAr, requiresReConsent: reConsent });
      setShowNew(false); setRef(''); setVer(''); setTitleEn(''); setTitleAr(''); setBodyEn(''); setBodyAr('');
      await reload();
    } finally { setBusy(false); }
  }

  async function submitMove(e: React.FormEvent) {
    e.preventDefault();
    if (!moveTarget || !toStatus) return;
    setBusy(true);
    try {
      await dataOfficerApi.transitionNotice(moveTarget.id, {
        toStatus,
        supersededById: toStatus === 'SUPERSEDED' && supersededBy ? Number(supersededBy) : undefined,
      });
      setMoveTarget(null); setToStatus(null); setSupersededBy('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transition failed');
    } finally { setBusy(false); }
  }

  const [docsTarget, setDocsTarget] = useState<NoticeResponse | null>(null);
  const cols: Column<NoticeResponse>[] = [
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '120px', cell: n => <span style={{ fontSize: 12 }}>{n.reference}</span> },
    { header: ar ? 'النسخة' : 'Version', width: '110px', cell: n => <span style={{ fontSize: 12 }}>{n.versionLabel}</span> },
    { header: ar ? 'القناة' : 'Channel', width: '90px', cell: n => <Badge>{n.channel}</Badge> },
    { header: ar ? 'العنوان' : 'Title', cell: n => <span style={{ fontSize: 13 }}>{ar ? n.titleAr : n.titleEn}</span> },
    { header: ar ? 'الحالة' : 'Status', width: '140px',
      cell: n => <Badge tone={NOTICE_STATUS_TONE[n.status]}>{NOTICE_STATUS_LABEL[n.status][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'يتطلّب موافقة جديدة' : 'Re-consent', width: '110px',
      cell: n => n.requiresReConsent ? <Badge tone="warning">{ar ? 'نعم' : 'Yes'}</Badge> : null },
    { header: '', width: '150px', align: 'end',
      cell: n => {
        const next = nextNoticeStatuses(n.status);
        return (
          <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end' }}>
            <Button variant="ghost" size="sm" Icon={FolderOpen}
              title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
              onClick={(e) => { e.stopPropagation(); setDocsTarget(n); }} />
            {next.length > 0 && (
              <Button variant="ghost" size="sm"
                onClick={(e) => { e.stopPropagation(); setMoveTarget(n); setToStatus(next[0]); }}>
                {ar ? 'تحريك' : 'Advance'}
              </Button>
            )}
          </span>
        );
      } },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'إشعارات الخصوصية · المرحلة ٣٣' : 'Privacy Notices · Sprint 33'}
        title={ar ? 'إدارة إشعارات الخصوصية' : 'Privacy Notice Manager'}
        sub={ar ? 'مادة ١٢ — نسخ ثنائية اللغة لكل قناة · تتبع تغيّر النسخة' : 'PDPL Art. 12 — bilingual versioned notices per channel'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'إشعار جديد' : 'New notice')}</Button>}
      />
      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="NOT-2026-NNNN" /></label>
          <label>{ar ? 'تسمية النسخة' : 'Version label'}<input className="input" required value={ver} onChange={e => setVer(e.target.value)} placeholder="v3.2-2026-Q2" /></label>
          <label>{ar ? 'القناة' : 'Channel'}
            <select className="input" value={ch} onChange={e => setCh(e.target.value)}>{NOTICE_CHANNELS.map(c => <option key={c}>{c}</option>)}</select></label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={reConsent} onChange={e => setReConsent(e.target.checked)} />
            {ar ? 'يتطلّب موافقة جديدة (تغيير جوهري)' : 'Requires re-consent (material change)'}</label>
          <label>{ar ? 'العنوان (EN)' : 'Title (EN)'}<input className="input" required value={titleEn} onChange={e => setTitleEn(e.target.value)} /></label>
          <label>{ar ? 'العنوان (AR)' : 'Title (AR)'}<input className="input" required value={titleAr} onChange={e => setTitleAr(e.target.value)} dir="rtl" /></label>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'النص (EN)' : 'Body (EN)'}<textarea className="input" rows={4} required value={bodyEn} onChange={e => setBodyEn(e.target.value)} /></label>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'النص (AR)' : 'Body (AR)'}<textarea className="input" rows={4} required value={bodyAr} onChange={e => setBodyAr(e.target.value)} dir="rtl" /></label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'إعداد مسوّدة' : 'Draft'}</Button>
          </div></form></Card>
      )}
      {moveTarget && toStatus && (
        <Card><form onSubmit={submitMove} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <div style={{ gridColumn: 'span 2', fontWeight: 700 }}>
            {ar ? `تحريك ${moveTarget.reference} → ` : `Advance ${moveTarget.reference} → `}
            <select value={toStatus} onChange={e => setToStatus(e.target.value as NoticeStatus)} className="input" style={{ marginInlineStart: 8, width: 'auto', display: 'inline-block' }}>
              {nextNoticeStatuses(moveTarget.status).map(s => <option key={s} value={s}>{NOTICE_STATUS_LABEL[s][ar ? 'ar' : 'en']}</option>)}
            </select>
          </div>
          {toStatus === 'SUPERSEDED' && (
            <label style={{ gridColumn: 'span 2' }}>{ar ? 'استُبدِل بالإشعار رقم (اختياري)' : 'Superseded by notice # (optional)'}
              <input className="input" type="number" value={supersededBy} onChange={e => setSupersededBy(e.target.value)} placeholder={ar ? 'معرّف النسخة الأحدث' : 'newer version id'} /></label>
          )}
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => { setMoveTarget(null); setToStatus(null); setSupersededBy(''); }}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تنفيذ' : 'Apply'}</Button>
          </div>
        </form></Card>
      )}
      <Card>{rows.length === 0
        ? <EmptyState Icon={BookOpen} title={ar ? 'لا توجد إشعارات' : 'No notices'} sub={ar ? 'سجّل أول إشعار' : 'Draft the first notice'} />
        : <DataTable columns={cols} rows={rows} />}</Card>
      <EntityDocsModal entityType="PRIVACY_NOTICE" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        recordLabel={docsTarget?.reference} onClose={() => setDocsTarget(null)} />
    </Shell>
  );
}

/* ─── Retention ───────────────────────────────────────────────────── */
const DATA_CATEGORIES = ['CUSTOMER_KYC','TRANSACTION_LOG','MARKETING_PROFILE','EMPLOYEE_RECORD','AML_RECORD','RISK_SCORE','CALL_RECORDING','EMAIL_LOG','SYSTEM_LOG'];
const DISPOSAL_METHODS = ['CRYPTO_SHRED','PHYSICAL_DESTRUCTION','OVERWRITE','ANONYMISE'];
export function RetentionPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows: rules, error: e1, reload: reloadR } = useList<RetentionRuleResponse>(() => dataOfficerApi.listRules());
  const { rows: jobs } = useList<DisposalJobResponse>(() => dataOfficerApi.listDisposals());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ref, setRef] = useState(''); const [cat, setCat] = useState('CUSTOMER_KYC');
  const [months, setMonths] = useState('60');
  const [legalEn, setLegalEn] = useState(''); const [legalAr, setLegalAr] = useState('');
  const [disposal, setDisposal] = useState('CRYPTO_SHRED');
  const [reviewMonths, setReviewMonths] = useState('12');

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createRule({ reference: ref, dataCategory: cat,
        retentionMonths: Number(months), legalBasisEn: legalEn, legalBasisAr: legalAr,
        disposalMethod: disposal, reviewCadenceMonths: Number(reviewMonths) });
      setShowNew(false); setRef(''); setLegalEn(''); setLegalAr('');
      await reloadR();
    } finally { setBusy(false); }
  }

  const [docsTarget, setDocsTarget] = useState<RetentionRuleResponse | null>(null);
  const cols: Column<RetentionRuleResponse>[] = [
    { header: '', width: '50px',
      cell: row => <Button variant="ghost" size="sm" Icon={FolderOpen} title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
        onClick={(e) => { e.stopPropagation(); setDocsTarget(row); }} /> },
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '120px', cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span> },
    { header: ar ? 'الفئة' : 'Category', cell: r => <span style={{ fontSize: 13 }}>{r.dataCategory}</span> },
    { header: ar ? 'المدة' : 'Retention', width: '100px',
      cell: r => <span style={{ fontSize: 12 }}>{r.retentionMonths} {ar ? 'شهر' : 'mo'}</span> },
    { header: ar ? 'طريقة التخلّص' : 'Disposal', width: '160px',
      cell: r => <span style={{ fontSize: 12 }}>{r.disposalMethod ?? '—'}</span> },
    { header: ar ? 'مراجعة قادمة' : 'Next review', width: '120px',
      cell: r => <span style={{ fontSize: 12 }}>{r.nextReviewDue ?? '—'}</span> },
    { header: ar ? 'الحالة' : 'Status', width: '110px',
      cell: r => <Badge tone={r.status === 'ACTIVE' ? 'success' : 'neutral'}>{r.status}</Badge> },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'الاحتفاظ والتخلّص · المرحلة ٣٣' : 'Retention & Disposal · Sprint 33'}
        title={ar ? 'جدول الاحتفاظ والتخلّص' : 'Retention & Disposal Schedule'}
        sub={ar ? 'مادة ١٨ — مدد الاحتفاظ لكل فئة · أدلة التخلّص المشفّرة' : 'PDPL Art. 18 — Per-category retention windows · cryptographic disposal evidence'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'قاعدة جديدة' : 'New rule')}</Button>}
      />
      {e1 && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{e1}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="RET-2026-NNNN" /></label>
          <label>{ar ? 'الفئة' : 'Category'}
            <select className="input" value={cat} onChange={e => setCat(e.target.value)}>{DATA_CATEGORIES.map(c => <option key={c}>{c}</option>)}</select></label>
          <label>{ar ? 'مدة الاحتفاظ (شهراً)' : 'Retention (months)'}<input className="input" type="number" required value={months} onChange={e => setMonths(e.target.value)} /></label>
          <label>{ar ? 'دورة المراجعة (شهراً)' : 'Review cadence (months)'}<input className="input" type="number" value={reviewMonths} onChange={e => setReviewMonths(e.target.value)} /></label>
          <label>{ar ? 'طريقة التخلّص' : 'Disposal method'}
            <select className="input" value={disposal} onChange={e => setDisposal(e.target.value)}>{DISPOSAL_METHODS.map(m => <option key={m}>{m}</option>)}</select></label>
          <div></div>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'الأساس النظامي (EN)' : 'Legal basis (EN)'}<textarea className="input" rows={2} value={legalEn} onChange={e => setLegalEn(e.target.value)} /></label>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'الأساس النظامي (AR)' : 'Legal basis (AR)'}<textarea className="input" rows={2} value={legalAr} onChange={e => setLegalAr(e.target.value)} dir="rtl" /></label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Register'}</Button>
          </div></form></Card>
      )}
      <Card>{rules.length === 0
        ? <EmptyState Icon={Archive} title={ar ? 'لا توجد قواعد احتفاظ' : 'No retention rules'} sub={ar ? 'أنشئ أول قاعدة' : 'Create the first rule'} />
        : <DataTable columns={cols} rows={rules} />}</Card>
      <EntityDocsModal entityType="RETENTION_RULE" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        onClose={() => setDocsTarget(null)} />
      {jobs.length > 0 && (
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
            {ar ? `جدول التخلّص (${jobs.length})` : `Disposal jobs (${jobs.length})`}
          </div>
          <DataTable rows={jobs} columns={[
            { header: ar ? 'المرجع' : 'Ref', mono: true, width: '120px', cell: j => <span style={{ fontSize: 12 }}>{j.reference}</span> },
            { header: ar ? 'القاعدة' : 'Rule', width: '90px', cell: j => <span style={{ fontSize: 12 }}>#{j.retentionRuleId}</span> },
            { header: ar ? 'مجدول' : 'Scheduled', cell: j => <span style={{ fontSize: 12 }}>{j.scheduledFor}</span> },
            { header: ar ? 'الحالة' : 'Status', width: '120px', cell: j => <Badge>{j.status}</Badge> },
          ]} />
        </Card>
      )}
    </Shell>
  );
}

/* ─── NDR ─────────────────────────────────────────────────────────── */
export function NdrPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows, error, reload, setError } = useList<NdrResponse>(() => dataOfficerApi.listNdr());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ref, setRef] = useState(''); const [year, setYear] = useState(String(new Date().getFullYear()));
  const [titleEn, setTitleEn] = useState(''); const [titleAr, setTitleAr] = useState('');

  // Annual-cycle transition (SDAIA NDR — DRAFT→INTERNAL_REVIEW→READY→SUBMITTED→ACCEPTED/REJECTED/REVISION)
  const [moveTarget, setMoveTarget] = useState<NdrResponse | null>(null);
  const [toStatus, setToStatus] = useState<NdrStatus | null>(null);
  const [submissionRef, setSubmissionRef] = useState('');
  const [datasetCount, setDatasetCount] = useState('');
  const [respEn, setRespEn] = useState(''); const [respAr, setRespAr] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createNdr({ reference: ref, cycleYear: Number(year), titleEn, titleAr });
      setShowNew(false); setRef(''); setTitleEn(''); setTitleAr('');
      await reload();
    } finally { setBusy(false); }
  }

  const NDR_RESP = ['ACCEPTED', 'REJECTED', 'REVISION_REQUESTED'];
  async function submitMove(e: React.FormEvent) {
    e.preventDefault();
    if (!moveTarget || !toStatus) return;
    setBusy(true);
    try {
      const resp = NDR_RESP.includes(toStatus);
      await dataOfficerApi.transitionNdr(moveTarget.id, {
        toStatus,
        submissionRef: toStatus === 'SUBMITTED' ? (submissionRef || undefined) : undefined,
        datasetCount: toStatus === 'SUBMITTED' && datasetCount ? Number(datasetCount) : undefined,
        responseSummaryEn: resp ? (respEn || undefined) : undefined,
        responseSummaryAr: resp ? (respAr || undefined) : undefined,
      });
      setMoveTarget(null); setToStatus(null); setSubmissionRef(''); setDatasetCount(''); setRespEn(''); setRespAr('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transition failed');
    } finally { setBusy(false); }
  }
  const [docsTarget, setDocsTarget] = useState<NdrResponse | null>(null);
  const cols: Column<NdrResponse>[] = [
    { header: '', width: '50px',
      cell: row => <Button variant="ghost" size="sm" Icon={FolderOpen} title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
        onClick={(e) => { e.stopPropagation(); setDocsTarget(row); }} /> },
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '140px', cell: n => <span style={{ fontSize: 12 }}>{n.reference}</span> },
    { header: ar ? 'السنة' : 'Year', width: '80px', cell: n => <span style={{ fontSize: 13, fontWeight: 600 }}>{n.cycleYear}</span> },
    { header: ar ? 'العنوان' : 'Title', cell: n => <span style={{ fontSize: 13 }}>{ar ? (n.titleAr ?? n.titleEn) : (n.titleEn ?? n.titleAr) ?? '—'}</span> },
    { header: ar ? 'عدد البيانات' : 'Datasets', width: '110px', cell: n => <span style={{ fontSize: 12 }}>{n.datasetCount}</span> },
    { header: ar ? 'الحالة' : 'Status', width: '160px',
      cell: n => <Badge tone={NDR_STATUS_TONE[n.status]}>{NDR_STATUS_LABEL[n.status][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'مرجع سدايا' : 'SDAIA ref', width: '140px', cell: n => <span style={{ fontSize: 12 }}>{n.submissionRef ?? '—'}</span> },
    { header: '', width: '120px', align: 'end',
      cell: n => {
        const next = nextNdrStatuses(n.status);
        return next.length > 0 ? (
          <Button variant="ghost" size="sm"
            onClick={(e) => { e.stopPropagation(); setMoveTarget(n); setToStatus(next[0]); }}>
            {ar ? 'تحريك' : 'Advance'}
          </Button>
        ) : null;
      } },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'السجل الوطني للبيانات · المرحلة ٣٣' : 'NDR · Sprint 33'}
        title={ar ? 'تقديم السجل الوطني للبيانات (NDR)' : 'National Data Register submissions'}
        sub={ar ? 'تقديم سنوي إلى سدايا · بوابة معايير NDMO' : 'Annual SDAIA submission · NDMO standards gateway'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'دورة جديدة' : 'New cycle')}</Button>}
      />
      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="NDR-2026-001" /></label>
          <label>{ar ? 'السنة' : 'Cycle year'}<input className="input" type="number" required value={year} onChange={e => setYear(e.target.value)} /></label>
          <label>{ar ? 'العنوان (EN)' : 'Title (EN)'}<input className="input" value={titleEn} onChange={e => setTitleEn(e.target.value)} /></label>
          <label>{ar ? 'العنوان (AR)' : 'Title (AR)'}<input className="input" value={titleAr} onChange={e => setTitleAr(e.target.value)} dir="rtl" /></label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'إنشاء' : 'Create'}</Button>
          </div></form></Card>
      )}
      {moveTarget && toStatus && (
        <Card><form onSubmit={submitMove} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <div style={{ gridColumn: 'span 2', fontWeight: 700 }}>
            {ar ? `تحريك ${moveTarget.reference} → ` : `Advance ${moveTarget.reference} → `}
            <select value={toStatus} onChange={e => setToStatus(e.target.value as NdrStatus)} className="input" style={{ marginInlineStart: 8, width: 'auto', display: 'inline-block' }}>
              {nextNdrStatuses(moveTarget.status).map(s => <option key={s} value={s}>{NDR_STATUS_LABEL[s][ar ? 'ar' : 'en']}</option>)}
            </select>
          </div>
          {toStatus === 'SUBMITTED' && (
            <>
              <label>{ar ? 'مرجع التقديم لدى سدايا' : 'SDAIA submission reference'}<input className="input" value={submissionRef} onChange={e => setSubmissionRef(e.target.value)} placeholder="SDAIA-NDR-2026-..." /></label>
              <label>{ar ? 'عدد مجموعات البيانات' : 'Dataset count'}<input className="input" type="number" min="0" value={datasetCount} onChange={e => setDatasetCount(e.target.value)} /></label>
            </>
          )}
          {NDR_RESP.includes(toStatus) && (
            <>
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'ملخّص رد سدايا (EN)' : 'SDAIA response summary (EN)'}<textarea className="input" rows={2} value={respEn} onChange={e => setRespEn(e.target.value)} /></label>
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'ملخّص رد سدايا (AR)' : 'SDAIA response summary (AR)'}<textarea className="input" rows={2} value={respAr} onChange={e => setRespAr(e.target.value)} dir="rtl" /></label>
            </>
          )}
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => { setMoveTarget(null); setToStatus(null); setSubmissionRef(''); setDatasetCount(''); setRespEn(''); setRespAr(''); }}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تنفيذ' : 'Apply'}</Button>
          </div>
        </form></Card>
      )}
      <Card>{rows.length === 0
        ? <EmptyState Icon={ScrollText} title={ar ? 'لا توجد دورات تقديم' : 'No submission cycles'} sub={ar ? 'ابدأ دورة هذا العام' : 'Start this year\'s cycle'} />
        : <DataTable columns={cols} rows={rows} />}</Card>
      <EntityDocsModal entityType="NDR" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        onClose={() => setDocsTarget(null)} />
    </Shell>
  );
}

/* ─── Catalog ─────────────────────────────────────────────────────── */
const CATALOG_DOMAINS = ['CUSTOMER','CREDIT','PAYMENT','RISK','COMPLIANCE','HR','MARKETING','FRAUD','ANALYTICS'];
const CLASSIFICATIONS = ['PUBLIC','INTERNAL','CONFIDENTIAL','TOP_SECRET'];
const REFRESH_FREQS = ['REAL_TIME','HOURLY','DAILY','WEEKLY','MONTHLY'];
export function CatalogPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows, error, reload } = useList<CatalogEntryResponse>(() => dataOfficerApi.listCatalog());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ref, setRef] = useState(''); const [nameEn, setNameEn] = useState(''); const [nameAr, setNameAr] = useState('');
  const [domain, setDomain] = useState('CUSTOMER'); const [classification, setClassification] = useState('INTERNAL');
  const [source, setSource] = useState(''); const [refreshFreq, setRefreshFreq] = useState('DAILY');
  const [hasPii, setHasPii] = useState(false); const [hasSpecial, setHasSpecial] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createCatalogEntry({ reference: ref, datasetNameEn: nameEn, datasetNameAr: nameAr,
        domain, classification, sourceSystem: source, refreshFrequency: refreshFreq,
        containsPii: hasPii, containsSpecialCategory: hasSpecial });
      setShowNew(false); setRef(''); setNameEn(''); setNameAr(''); setSource('');
      await reload();
    } finally { setBusy(false); }
  }
  const [docsTarget, setDocsTarget] = useState<CatalogEntryResponse | null>(null);
  const cols: Column<CatalogEntryResponse>[] = [
    { header: '', width: '50px',
      cell: row => <Button variant="ghost" size="sm" Icon={FolderOpen} title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
        onClick={(e) => { e.stopPropagation(); setDocsTarget(row); }} /> },
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '120px', cell: e => <span style={{ fontSize: 12 }}>{e.reference}</span> },
    { header: ar ? 'اسم البيانات' : 'Dataset',
      cell: e => <div><div style={{ fontWeight: 600, fontSize: 13 }}>{ar ? (e.datasetNameAr ?? e.datasetNameEn) : e.datasetNameEn}</div>
        <div style={{ fontSize: 11, opacity: 0.7 }}>{e.domain} · {e.sourceSystem ?? '—'}</div></div> },
    { header: ar ? 'التصنيف' : 'Class', width: '120px',
      cell: e => e.classification ? <Badge tone={e.classification === 'TOP_SECRET' ? 'danger' : e.classification === 'CONFIDENTIAL' ? 'warning' : 'info'}>{e.classification}</Badge> : null },
    { header: ar ? 'PII / حسّاسة' : 'PII / Special', width: '120px',
      cell: e => (
        <div style={{ display: 'flex', gap: 4 }}>
          {e.containsPii && <Badge tone="warning">PII</Badge>}
          {e.containsSpecialCategory && <Badge tone="danger">{ar ? 'حسّاسة' : 'Spec'}</Badge>}
        </div>
      ) },
    { header: ar ? 'سجلات' : 'Rows', width: '110px',
      cell: e => <span style={{ fontSize: 12 }}>{e.rowCountApprox?.toLocaleString() ?? '—'}</span> },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'دليل البيانات · المرحلة ٣٣' : 'Catalog · Sprint 33'}
        title={ar ? 'دليل البيانات والنَّسَب' : 'Data Catalog + Lineage'}
        sub={ar ? 'مجال إدارة البيانات في NDMO · ملاك ووصاة وحراس' : 'NDMO Data Management — owners, stewards, custodians'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'مجموعة بيانات' : 'New dataset')}</Button>}
      />
      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="CAT-2026-NNNN" /></label>
          <label>{ar ? 'النطاق' : 'Domain'}
            <select className="input" value={domain} onChange={e => setDomain(e.target.value)}>{CATALOG_DOMAINS.map(d => <option key={d}>{d}</option>)}</select></label>
          <label>{ar ? 'اسم البيانات (EN)' : 'Dataset name (EN)'}<input className="input" required value={nameEn} onChange={e => setNameEn(e.target.value)} /></label>
          <label>{ar ? 'اسم البيانات (AR)' : 'Dataset name (AR)'}<input className="input" value={nameAr} onChange={e => setNameAr(e.target.value)} dir="rtl" /></label>
          <label>{ar ? 'النظام المصدر' : 'Source system'}<input className="input" value={source} onChange={e => setSource(e.target.value)} placeholder="T24 / Murex / Exadata" /></label>
          <label>{ar ? 'التصنيف' : 'Classification'}
            <select className="input" value={classification} onChange={e => setClassification(e.target.value)}>{CLASSIFICATIONS.map(c => <option key={c}>{c}</option>)}</select></label>
          <label>{ar ? 'تردد التحديث' : 'Refresh frequency'}
            <select className="input" value={refreshFreq} onChange={e => setRefreshFreq(e.target.value)}>{REFRESH_FREQS.map(f => <option key={f}>{f}</option>)}</select></label>
          <div></div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={hasPii} onChange={e => setHasPii(e.target.checked)} />{ar ? 'تحتوي على بيانات شخصية' : 'Contains PII'}</label>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input type="checkbox" checked={hasSpecial} onChange={e => setHasSpecial(e.target.checked)} />{ar ? 'بيانات حسّاسة (مادة ٤)' : 'Special category (Art. 4)'}</label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Register'}</Button>
          </div></form></Card>
      )}
      <Card>{rows.length === 0
        ? <EmptyState Icon={Database} title={ar ? 'الدليل فارغ' : 'Catalog is empty'} sub={ar ? 'سجّل أول مجموعة بيانات' : 'Register the first dataset'} />
        : <DataTable columns={cols} rows={rows} />}</Card>
      <EntityDocsModal entityType="DATA_CATALOG" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        onClose={() => setDocsTarget(null)} />
    </Shell>
  );
}

/* ─── Quality ─────────────────────────────────────────────────────── */
const DIMENSIONS = ['COMPLETENESS','UNIQUENESS','ACCURACY','VALIDITY','CONSISTENCY','TIMELINESS','INTEGRITY'] as const;
type Dim = typeof DIMENSIONS[number];
export function QualityPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows: rules, error: e1, reload: reloadR } = useList<QualityRuleResponse>(() => dataOfficerApi.listQualityRules());
  const { rows: runs } = useList(() => dataOfficerApi.listQualityRuns(undefined));
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ref, setRef] = useState(''); const [catalogId, setCatalogId] = useState('');
  const [nameEn, setNameEn] = useState(''); const [nameAr, setNameAr] = useState('');
  const [dim, setDim] = useState<Dim>('COMPLETENESS');
  const [thr, setThr] = useState('95');
  const [sev, setSev] = useState('MEDIUM');

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createQualityRule({ reference: ref, catalogEntryId: Number(catalogId),
        ruleNameEn: nameEn, ruleNameAr: nameAr, dimension: dim,
        thresholdPct: Number(thr), severity: sev });
      setShowNew(false); setRef(''); setCatalogId(''); setNameEn(''); setNameAr('');
      await reloadR();
    } finally { setBusy(false); }
  }

  const [docsTarget, setDocsTarget] = useState<QualityRuleResponse | null>(null);
  const cols: Column<QualityRuleResponse>[] = [
    { header: '', width: '50px',
      cell: row => <Button variant="ghost" size="sm" Icon={FolderOpen} title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
        onClick={(e) => { e.stopPropagation(); setDocsTarget(row); }} /> },
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '120px', cell: r => <span style={{ fontSize: 12 }}>{r.reference}</span> },
    { header: ar ? 'القاعدة' : 'Rule',
      cell: r => <div><div style={{ fontWeight: 600, fontSize: 13 }}>{ar ? (r.ruleNameAr ?? r.ruleNameEn) : r.ruleNameEn}</div>
        <div style={{ fontSize: 11, opacity: 0.7 }}>{r.dimension} · #{r.catalogEntryId}</div></div> },
    { header: ar ? 'الحد' : 'Threshold', width: '110px',
      cell: r => <span style={{ fontSize: 12 }}>{r.thresholdPct?.toString() ?? '—'}%</span> },
    { header: ar ? 'الشدّة' : 'Severity', width: '110px',
      cell: r => <Badge tone={r.severity === 'CRITICAL' ? 'danger' : r.severity === 'HIGH' ? 'warning' : 'neutral'}>{r.severity ?? '—'}</Badge> },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'جودة البيانات · المرحلة ٣٣' : 'Quality · Sprint 33'}
        title={ar ? 'بطاقات جودة البيانات' : 'Data Quality Scorecards'}
        sub={ar ? 'مجال جودة البيانات في NDMO · ٧ أبعاد · بطاقات أداء ربعية' : 'NDMO DQM · 7 dimensions · quarterly scorecards'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'قاعدة جودة' : 'New rule')}</Button>}
      />
      {e1 && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{e1}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="DQR-2026-NNNN" /></label>
          <label>{ar ? 'معرّف مجموعة البيانات' : 'Catalog entry id'}<input className="input" type="number" required value={catalogId} onChange={e => setCatalogId(e.target.value)} /></label>
          <label>{ar ? 'اسم القاعدة (EN)' : 'Rule name (EN)'}<input className="input" required value={nameEn} onChange={e => setNameEn(e.target.value)} /></label>
          <label>{ar ? 'اسم القاعدة (AR)' : 'Rule name (AR)'}<input className="input" value={nameAr} onChange={e => setNameAr(e.target.value)} dir="rtl" /></label>
          <label>{ar ? 'البُعد' : 'Dimension'}
            <select className="input" value={dim} onChange={e => setDim(e.target.value as Dim)}>{DIMENSIONS.map(d => <option key={d}>{d}</option>)}</select></label>
          <label>{ar ? 'الحد (%)' : 'Threshold (%)'}<input className="input" type="number" min="0" max="100" value={thr} onChange={e => setThr(e.target.value)} /></label>
          <label>{ar ? 'الشدّة' : 'Severity'}
            <select className="input" value={sev} onChange={e => setSev(e.target.value)}>{['LOW','MEDIUM','HIGH','CRITICAL'].map(s => <option key={s}>{s}</option>)}</select></label>
          <div></div>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تسجيل' : 'Register'}</Button>
          </div></form></Card>
      )}
      <Card>{rules.length === 0
        ? <EmptyState Icon={Sigma} title={ar ? 'لا توجد قواعد' : 'No rules'} sub={ar ? 'سجّل أول قاعدة جودة' : 'Register the first quality rule'} />
        : <DataTable columns={cols} rows={rules} />}</Card>
      <EntityDocsModal entityType="DATA_QUALITY_RULE" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        onClose={() => setDocsTarget(null)} />
      {runs.length > 0 && (
        <Card>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
            {ar ? `آخر تشغيلات (${runs.length})` : `Recent runs (${runs.length})`}
          </div>
          <DataTable rows={runs} columns={[
            { header: ar ? 'القاعدة' : 'Rule', width: '90px', cell: r => <span style={{ fontSize: 12 }}>#{r.qualityRuleId}</span> },
            { header: ar ? 'الوقت' : 'When', cell: r => <span style={{ fontSize: 12 }}>{new Date(r.runAt).toLocaleString()}</span> },
            { header: ar ? 'النجاح %' : 'Pass %', width: '110px', cell: r => <span style={{ fontSize: 12, fontWeight: 600 }}>{r.passPct ?? '—'}%</span> },
            { header: ar ? 'النطاق' : 'Band', width: '110px',
              cell: r => r.band ? <Badge tone={r.band === 'RED' ? 'danger' : r.band === 'AMBER' ? 'warning' : 'success'}>{r.band}</Badge> : null },
          ]} />
        </Card>
      )}
    </Shell>
  );
}

/* ─── Ethics ──────────────────────────────────────────────────────── */
const ETHICS_SUBJECT_TYPES = ['AI_MODEL','DATA_USE_CASE','NEW_PROCESSING','OPEN_DATA_RELEASE'];
export function EthicsPage() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const { rows, error, reload, setError } = useList<EthicsResponse>(() => dataOfficerApi.listEthics());
  const [showNew, setShowNew] = useState(false);
  const [busy, setBusy] = useState(false);
  const [ref, setRef] = useState(''); const [subType, setSubType] = useState('AI_MODEL'); const [subRef, setSubRef] = useState('');
  const [titleEn, setTitleEn] = useState(''); const [titleAr, setTitleAr] = useState('');
  const [summaryEn, setSummaryEn] = useState(''); const [summaryAr, setSummaryAr] = useState('');

  // Committee lifecycle (SDAIA AI-ethics / NDMO data-ethics — REQUESTED→COMMITTEE_REVIEW→OPINION_DRAFTED→APPROVED…)
  const [moveTarget, setMoveTarget] = useState<EthicsResponse | null>(null);
  const [toStatus, setToStatus] = useState<EthicsStatus | null>(null);
  const [committeeAt, setCommitteeAt] = useState('');
  const [opinionEn, setOpinionEn] = useState(''); const [opinionAr, setOpinionAr] = useState('');
  const [conditionsEn, setConditionsEn] = useState(''); const [conditionsAr, setConditionsAr] = useState('');
  const [nextReview, setNextReview] = useState('');

  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true);
    try {
      await dataOfficerApi.createEthics({ reference: ref, subjectType: subType, subjectRef: subRef,
        titleEn, titleAr, summaryEn, summaryAr });
      setShowNew(false); setRef(''); setSubRef(''); setTitleEn(''); setTitleAr(''); setSummaryEn(''); setSummaryAr('');
      await reload();
    } finally { setBusy(false); }
  }

  const ETH_DECISION = ['APPROVED', 'APPROVED_WITH_CONDITIONS', 'REJECTED'];
  const ethShowOpinion = (s: EthicsStatus | null) => s === 'OPINION_DRAFTED' || (s != null && ETH_DECISION.includes(s));
  const ethShowReview = (s: EthicsStatus | null) => s === 'APPROVED' || s === 'APPROVED_WITH_CONDITIONS';
  async function submitMove(e: React.FormEvent) {
    e.preventDefault();
    if (!moveTarget || !toStatus) return;
    setBusy(true);
    try {
      await dataOfficerApi.transitionEthics(moveTarget.id, {
        toStatus,
        committeeMeetingAt: toStatus === 'COMMITTEE_REVIEW' && committeeAt ? `${committeeAt}T00:00:00` : undefined,
        opinionEn: ethShowOpinion(toStatus) ? (opinionEn || undefined) : undefined,
        opinionAr: ethShowOpinion(toStatus) ? (opinionAr || undefined) : undefined,
        conditionsEn: toStatus === 'APPROVED_WITH_CONDITIONS' ? (conditionsEn || undefined) : undefined,
        conditionsAr: toStatus === 'APPROVED_WITH_CONDITIONS' ? (conditionsAr || undefined) : undefined,
        nextReviewDue: ethShowReview(toStatus) ? (nextReview || undefined) : undefined,
      });
      setMoveTarget(null); setToStatus(null); setCommitteeAt(''); setOpinionEn(''); setOpinionAr('');
      setConditionsEn(''); setConditionsAr(''); setNextReview('');
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transition failed');
    } finally { setBusy(false); }
  }

  const [docsTarget, setDocsTarget] = useState<EthicsResponse | null>(null);
  const cols: Column<EthicsResponse>[] = [
    { header: '', width: '50px',
      cell: row => <Button variant="ghost" size="sm" Icon={FolderOpen} title={ar ? 'المستندات والضوابط' : 'Docs & controls'}
        onClick={(e) => { e.stopPropagation(); setDocsTarget(row); }} /> },
    { header: ar ? 'المرجع' : 'Reference', mono: true, width: '120px', cell: e => <span style={{ fontSize: 12 }}>{e.reference}</span> },
    { header: ar ? 'العنوان' : 'Title',
      cell: e => <div><div style={{ fontWeight: 600, fontSize: 13 }}>{ar ? e.titleAr : e.titleEn}</div>
        <div style={{ fontSize: 11, opacity: 0.7 }}>{e.subjectType}{e.subjectRef && ` · ${e.subjectRef}`}</div></div> },
    { header: ar ? 'الحالة' : 'Status', width: '180px',
      cell: e => <Badge tone={ETHICS_STATUS_TONE[e.status]}>{ETHICS_STATUS_LABEL[e.status][ar ? 'ar' : 'en']}</Badge> },
    { header: ar ? 'اجتماع اللجنة' : 'Committee', width: '140px',
      cell: e => <span style={{ fontSize: 12 }}>{e.committeeMeetingAt ? new Date(e.committeeMeetingAt).toLocaleDateString() : '—'}</span> },
    { header: '', width: '120px', align: 'end',
      cell: e => {
        const next = nextEthicsStatuses(e.status);
        return next.length > 0 ? (
          <Button variant="ghost" size="sm"
            onClick={(ev) => { ev.stopPropagation(); setMoveTarget(e); setToStatus(next[0]); }}>
            {ar ? 'تحريك' : 'Advance'}
          </Button>
        ) : null;
      } },
  ];
  return (
    <Shell>
      <PageHeader eyebrow={ar ? 'لجنة الأخلاقيات · المرحلة ٣٣' : 'Ethics · Sprint 33'}
        title={ar ? 'لجنة أخلاقيات البيانات والذكاء الاصطناعي' : 'Data & AI Ethics Committee'}
        sub={ar ? 'أخلاقيات الذكاء الاصطناعي في سدايا + أخلاقيات البيانات في NDMO · سجل رأي اللجنة' : 'SDAIA AI Ethics + NDMO Data Ethics · committee opinion log'}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setShowNew(s => !s)}>{showNew ? (ar ? 'إغلاق' : 'Cancel') : (ar ? 'مراجعة جديدة' : 'New review')}</Button>}
      />
      {error && <Card><div style={{ padding: 12, color: 'var(--danger,#a00)' }}>{error}</div></Card>}
      {showNew && (
        <Card><form onSubmit={submit} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <label>{ar ? 'المرجع' : 'Reference'}<input className="input" required value={ref} onChange={e => setRef(e.target.value)} placeholder="ETH-2026-NNNN" /></label>
          <label>{ar ? 'نوع الموضوع' : 'Subject type'}
            <select className="input" value={subType} onChange={e => setSubType(e.target.value)}>{ETHICS_SUBJECT_TYPES.map(t => <option key={t}>{t}</option>)}</select></label>
          <label>{ar ? 'مرجع الموضوع' : 'Subject ref'}<input className="input" value={subRef} onChange={e => setSubRef(e.target.value)} placeholder="MODEL-2026-005 / DPIA-2026-012" /></label>
          <div></div>
          <label>{ar ? 'العنوان (EN)' : 'Title (EN)'}<input className="input" required value={titleEn} onChange={e => setTitleEn(e.target.value)} /></label>
          <label>{ar ? 'العنوان (AR)' : 'Title (AR)'}<input className="input" required value={titleAr} onChange={e => setTitleAr(e.target.value)} dir="rtl" /></label>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'الملخّص (EN)' : 'Summary (EN)'}<textarea className="input" rows={3} value={summaryEn} onChange={e => setSummaryEn(e.target.value)} /></label>
          <label style={{ gridColumn: 'span 2' }}>{ar ? 'الملخّص (AR)' : 'Summary (AR)'}<textarea className="input" rows={3} value={summaryAr} onChange={e => setSummaryAr(e.target.value)} dir="rtl" /></label>
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => setShowNew(false)}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تقديم للجنة' : 'Submit to committee'}</Button>
          </div></form></Card>
      )}
      {moveTarget && toStatus && (
        <Card><form onSubmit={submitMove} style={{ padding: 16, display: 'grid', gridTemplateColumns: 'repeat(2,1fr)', gap: 12 }}>
          <div style={{ gridColumn: 'span 2', fontWeight: 700 }}>
            {ar ? `تحريك ${moveTarget.reference} → ` : `Advance ${moveTarget.reference} → `}
            <select value={toStatus} onChange={e => setToStatus(e.target.value as EthicsStatus)} className="input" style={{ marginInlineStart: 8, width: 'auto', display: 'inline-block' }}>
              {nextEthicsStatuses(moveTarget.status).map(s => <option key={s} value={s}>{ETHICS_STATUS_LABEL[s][ar ? 'ar' : 'en']}</option>)}
            </select>
          </div>
          {toStatus === 'COMMITTEE_REVIEW' && (
            <label style={{ gridColumn: 'span 2' }}>{ar ? 'تاريخ اجتماع اللجنة' : 'Committee meeting date'}
              <input className="input" type="date" value={committeeAt} onChange={e => setCommitteeAt(e.target.value)} /></label>
          )}
          {ethShowOpinion(toStatus) && (
            <>
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'رأي اللجنة (EN)' : 'Committee opinion (EN)'}<textarea className="input" rows={2} value={opinionEn} onChange={e => setOpinionEn(e.target.value)} /></label>
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'رأي اللجنة (AR)' : 'Committee opinion (AR)'}<textarea className="input" rows={2} value={opinionAr} onChange={e => setOpinionAr(e.target.value)} dir="rtl" /></label>
            </>
          )}
          {toStatus === 'APPROVED_WITH_CONDITIONS' && (
            <>
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'الشروط (EN)' : 'Conditions (EN)'}<textarea className="input" rows={2} value={conditionsEn} onChange={e => setConditionsEn(e.target.value)} /></label>
              <label style={{ gridColumn: 'span 2' }}>{ar ? 'الشروط (AR)' : 'Conditions (AR)'}<textarea className="input" rows={2} value={conditionsAr} onChange={e => setConditionsAr(e.target.value)} dir="rtl" /></label>
            </>
          )}
          {ethShowReview(toStatus) && (
            <label>{ar ? 'تاريخ المراجعة القادمة' : 'Next review due'}<input className="input" type="date" value={nextReview} onChange={e => setNextReview(e.target.value)} /></label>
          )}
          <div style={{ gridColumn: 'span 2', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button type="button" variant="ghost" onClick={() => { setMoveTarget(null); setToStatus(null); setCommitteeAt(''); setOpinionEn(''); setOpinionAr(''); setConditionsEn(''); setConditionsAr(''); setNextReview(''); }}>{ar ? 'إغلاق' : 'Cancel'}</Button>
            <Button type="submit" disabled={busy}>{ar ? 'تنفيذ' : 'Apply'}</Button>
          </div>
        </form></Card>
      )}
      <Card>{rows.length === 0
        ? <EmptyState Icon={Scale} title={ar ? 'لا توجد مراجعات' : 'No reviews'} sub={ar ? 'قدّم أول مراجعة' : 'Submit the first review'} />
        : <DataTable columns={cols} rows={rows} />}</Card>
      <EntityDocsModal entityType="AI_ETHICS_REVIEW" entityId={docsTarget?.id ?? null} open={!!docsTarget}
        onClose={() => setDocsTarget(null)} />
    </Shell>
  );
}
