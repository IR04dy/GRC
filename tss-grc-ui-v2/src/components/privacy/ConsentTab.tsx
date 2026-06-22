import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Users, ShieldAlert, FileSignature, ChevronRight, Clock, ScrollText,
  TrendingDown, AlertTriangle, X as XIcon, CheckCheck,
} from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge, type BadgeTone } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Kpi } from '../ui/Kpi';
import { Progress } from '../ui/Progress';
import { InlineEmpty } from '../ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { consentApi, ApiError, type ConsentRecordDto, type ConsentStatsDto } from '../../lib/api';

// PDPL Article 5 — Saudi-native purpose codes a real-world Saudi entity
// will recognise. Aligned with SDAIA's published RoPA guidance + SAMA's
// SVOC (Single View of Customer) consent buckets. Each purpose carries
// its Arabic label so the UI renders natively in RTL mode rather than
// transliterating.
const PURPOSE_LABEL: Record<string, { en: string; ar: string }> = {
  // Identity / service delivery
  KYC_ONBOARDING:        { en: 'KYC / identity onboarding',     ar: 'التحقق من الهوية' },
  ACCOUNT_OPERATION:     { en: 'Operate customer account',       ar: 'تشغيل حساب العميل' },
  TRANSACTION_PROCESSING:{ en: 'Process transactions',           ar: 'تنفيذ المعاملات' },
  SERVICE_DELIVERY:      { en: 'Deliver requested service',      ar: 'تقديم الخدمة المطلوبة' },
  // Marketing & profiling — needs explicit consent under PDPL
  MARKETING_EMAIL:       { en: 'Marketing email',                ar: 'التسويق عبر البريد' },
  MARKETING_SMS:         { en: 'Marketing SMS',                  ar: 'التسويق عبر الرسائل النصية' },
  MARKETING_WHATSAPP:    { en: 'Marketing via WhatsApp',         ar: 'التسويق عبر واتساب' },
  PROFILING:             { en: 'Behavioural profiling',          ar: 'تحليل سلوكي للملف الشخصي' },
  PERSONALISATION:       { en: 'Personalised recommendations',   ar: 'تخصيص التوصيات' },
  // Sharing
  THIRD_PARTY_SHARING:   { en: '3rd-party data sharing',         ar: 'مشاركة البيانات مع طرف ثالث' },
  CROSS_BORDER:          { en: 'Cross-border transfer',          ar: 'النقل خارج المملكة' },
  // Special category — Article 6
  HEALTH_DATA:           { en: 'Health data processing',         ar: 'معالجة البيانات الصحية' },
  CREDIT_SCORING:        { en: 'Credit scoring (SIMAH)',         ar: 'التقييم الائتماني (سمة)' },
  BIOMETRIC:             { en: 'Biometric authentication',       ar: 'المصادقة الحيوية' },
  LOCATION:              { en: 'Location tracking',              ar: 'تتبع الموقع' },
  // Compliance / regulatory
  AML_SCREENING:         { en: 'AML / sanctions screening',      ar: 'فحص غسل الأموال والعقوبات' },
  REGULATORY_REPORTING:  { en: 'Regulatory reporting',           ar: 'الإبلاغ التنظيمي' },
  CALL_RECORDING:        { en: 'Call recording',                 ar: 'تسجيل المكالمات' },
  EMPLOYEE_MONITORING:   { en: 'Employee activity monitoring',   ar: 'مراقبة نشاط الموظفين' },
};

const CHANNEL_LABEL: Record<string, { en: string; ar: string }> = {
  WEB_FORM:  { en: 'Web portal',      ar: 'البوابة الإلكترونية' },
  API:       { en: 'API / mobile',    ar: 'واجهة برمجية / الجوال' },
  PAPER:     { en: 'Paper form',      ar: 'نموذج ورقي' },
  IN_PERSON: { en: 'Branch / in-person', ar: 'فرع / حضوريًا' },
  EMAIL:     { en: 'Email',           ar: 'البريد الإلكتروني' },
  OTHER:     { en: 'Other',           ar: 'أخرى' },
};

const STATUS_TONE: Record<string, BadgeTone> = {
  ACTIVE:     'success',
  WITHDRAWN:  'danger',
  EXPIRED:    'warning',
  SUPERSEDED: 'neutral',
};
const LEGAL_BASIS_TONE: Record<string, BadgeTone> = {
  CONSENT:             'primary',
  LEGITIMATE_INTEREST: 'info',
  CONTRACT:            'success',
  LEGAL_OBLIGATION:    'accent',
  VITAL_INTEREST:      'warning',
  PUBLIC_TASK:         'neutral',
};

/** Decide what an "expiring soon" consent looks like — anything with a
 *  valid_until between now and the threshold below counts. 30 days is
 *  the typical refresh-window Saudi banks set for marketing consent. */
const EXPIRY_WARN_DAYS = 30;

interface ChannelMix { channel: string; granted: number; withdrawn: number; ratePct: number; }
interface PurposeMix { purpose: string; granted: number; withdrawn: number; ratePct: number; }

/** Real-data Consent dashboard. Replaces the hardcoded
 *  `Progress` bars that lived inline on PrivacyPage.
 *
 *  Surfaces:
 *    - Lifecycle KPIs (active / withdrawn / expired / superseded)
 *    - Channel mix — where consent was collected
 *    - Purpose mix — what subjects actually consented to
 *    - Withdrawal-rate breakdown — by purpose (marketing typically tops this)
 *    - Recent grants + withdrawals tables
 *    - Expiring-soon alert strip
 *
 *  Falls back to empty cards when the backend has no rows yet so the page
 *  still renders for a fresh tenant — but it never shows fake percentages.
 */
export function ConsentTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [records, setRecords] = useState<ConsentRecordDto[]>([]);
  const [stats, setStats]     = useState<ConsentStatsDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState<string | null>(null);

  async function load() {
    if (!isAuthenticated()) {
      setRecords([]); setStats(null); return;
    }
    setLoading(true); setError(null);
    try {
      const [page, s] = await Promise.all([consentApi.list(0, 200), consentApi.stats()]);
      setRecords(page.content ?? []);
      setStats(s);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not load consents');
      setRecords([]); setStats(null);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, []);

  // ── Derived metrics ──────────────────────────────────────────────────
  const total = records.length;
  const active     = stats?.active     ?? records.filter(r => r.status === 'ACTIVE').length;
  const withdrawn  = stats?.withdrawn  ?? records.filter(r => r.status === 'WITHDRAWN').length;
  const expired    = stats?.expired    ?? records.filter(r => r.status === 'EXPIRED').length;
  const superseded = stats?.superseded ?? records.filter(r => r.status === 'SUPERSEDED').length;

  // Withdrawal rate (one of the headline PDPL DPO KPIs — if marketing
  // consent has a withdrawal rate > 10% you have a UX or trust problem).
  const ledgerSize = total || (active + withdrawn + expired + superseded);
  const withdrawalRatePct = ledgerSize > 0 ? Math.round((withdrawn / ledgerSize) * 1000) / 10 : 0;

  const channelMix: ChannelMix[] = useMemo(() => {
    const buckets = new Map<string, { granted: number; withdrawn: number }>();
    for (const r of records) {
      const k = r.grantedVia ?? 'OTHER';
      const cur = buckets.get(k) ?? { granted: 0, withdrawn: 0 };
      cur.granted += 1;
      if (r.status === 'WITHDRAWN') cur.withdrawn += 1;
      buckets.set(k, cur);
    }
    return [...buckets.entries()]
      .map(([channel, v]) => ({
        channel, granted: v.granted, withdrawn: v.withdrawn,
        ratePct: v.granted ? Math.round((v.withdrawn / v.granted) * 1000) / 10 : 0,
      }))
      .sort((a, b) => b.granted - a.granted);
  }, [records]);

  const purposeMix: PurposeMix[] = useMemo(() => {
    const buckets = new Map<string, { granted: number; withdrawn: number }>();
    for (const r of records) {
      const k = r.purposeCode ?? 'OTHER';
      const cur = buckets.get(k) ?? { granted: 0, withdrawn: 0 };
      cur.granted += 1;
      if (r.status === 'WITHDRAWN') cur.withdrawn += 1;
      buckets.set(k, cur);
    }
    return [...buckets.entries()]
      .map(([purpose, v]) => ({
        purpose, granted: v.granted, withdrawn: v.withdrawn,
        ratePct: v.granted ? Math.round((v.withdrawn / v.granted) * 1000) / 10 : 0,
      }))
      .sort((a, b) => b.granted - a.granted)
      .slice(0, 8);
  }, [records]);

  const recentGrants = useMemo(
    () => [...records]
      .filter(r => r.status === 'ACTIVE')
      .sort((a, b) => (b.grantedAt ?? '').localeCompare(a.grantedAt ?? ''))
      .slice(0, 6),
    [records]);
  const recentWithdrawals = useMemo(
    () => [...records]
      .filter(r => r.status === 'WITHDRAWN' && r.withdrawnAt)
      .sort((a, b) => (b.withdrawnAt ?? '').localeCompare(a.withdrawnAt ?? ''))
      .slice(0, 6),
    [records]);

  // Expiring soon — anything with a validUntil between now and 30 days
  // out. Marketing consent that's about to lapse needs to be re-collected
  // or the lawful basis disappears.
  const now = Date.now();
  const expiringSoon = useMemo(
    () => records
      .filter(r => r.status === 'ACTIVE' && r.validUntil)
      .map(r => {
        const ms = new Date(r.validUntil ?? '').getTime() - now;
        return { ...r, daysLeft: Math.floor(ms / 86_400_000) };
      })
      .filter(r => r.daysLeft >= 0 && r.daysLeft <= EXPIRY_WARN_DAYS)
      .sort((a, b) => a.daysLeft - b.daysLeft),
    [records, now]);

  async function withdraw(r: ConsentRecordDto) {
    const reason = window.prompt(
      isAr ? 'سبب سحب الموافقة:' : 'Reason for withdrawing this consent:',
      isAr ? 'طلب صاحب البيانات' : 'Subject request');
    if (reason === null) return;
    try {
      await consentApi.withdraw(r.id, reason);
      toast.success(isAr ? 'تم سحب الموافقة' : 'Consent withdrawn');
      void load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Withdraw failed');
    }
  }

  function purposeLabel(code: string): string {
    const m = PURPOSE_LABEL[code];
    if (!m) return code.replace(/_/g, ' ');
    return isAr ? m.ar : m.en;
  }
  function channelLabel(code: string): string {
    const m = CHANNEL_LABEL[code];
    if (!m) return code.replace(/_/g, ' ');
    return isAr ? m.ar : m.en;
  }

  if (!isAuthenticated()) {
    return (
      <Card padded>
        <InlineEmpty>
          {isAr
            ? 'سجّل الدخول لعرض سجل الموافقات الفعلي (المادة 5 من نظام حماية البيانات الشخصية).'
            : 'Sign in to view the live consent ledger (PDPL Article 5).'}
        </InlineEmpty>
      </Card>
    );
  }
  if (loading) {
    return <Card padded><InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty></Card>;
  }
  if (error) {
    return <Card padded><InlineEmpty>{error}</InlineEmpty></Card>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

      {/* ── Lifecycle KPIs ──────────────────────────────────────────── */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, minmax(140px, 1fr))' }}>
        <Kpi Icon={Users}          label={isAr ? 'إجمالي السجلات' : 'Ledger size'}   value={ledgerSize} tone="primary" to="/data-officer/consents" />
        <Kpi Icon={CheckCheck}     label={isAr ? 'نشطة' : 'Active'}                  value={active}     tone="success" to="/data-officer/consents" />
        <Kpi Icon={XIcon}          label={isAr ? 'مسحوبة' : 'Withdrawn'}             value={withdrawn}  tone="danger" to="/data-officer/consents" />
        <Kpi Icon={Clock}          label={isAr ? 'منتهية' : 'Expired'}               value={expired}    tone="warning" to="/data-officer/consents" />
        <Kpi Icon={TrendingDown}
             label={isAr ? 'معدل السحب' : 'Withdrawal rate'}
             value={`${withdrawalRatePct}%`}
             to="/data-officer/consents"
             tone={withdrawalRatePct >= 10 ? 'danger' : withdrawalRatePct >= 5 ? 'warning' : 'success'} />
      </div>

      {/* ── Expiring-soon banner ────────────────────────────────────── */}
      {expiringSoon.length > 0 && (
        <Card title={isAr ? 'موافقات على وشك الانتهاء' : 'Expiring soon'} Icon={AlertTriangle}
              action={<Badge tone="warning">{expiringSoon.length}</Badge>}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {expiringSoon.slice(0, 5).map(r => (
              <div key={r.id} style={{
                display: 'grid', gridTemplateColumns: '1fr auto auto auto',
                gap: 10, padding: '6px 8px', alignItems: 'center',
                background: 'var(--surface-2)', borderRadius: 6, fontSize: 12,
              }}>
                <div>
                  <span style={{ fontWeight: 600 }}>{purposeLabel(r.purposeCode)}</span>
                  <span className="mono" style={{ marginInlineStart: 8, fontSize: 11, color: 'var(--ink-3)' }}>
                    {r.dataSubjectRef.slice(0, 18)}…
                  </span>
                </div>
                <Badge tone={LEGAL_BASIS_TONE[r.legalBasis] ?? 'neutral'}>{r.legalBasis.replace(/_/g, ' ')}</Badge>
                <span className="mono" style={{
                  color: r.daysLeft <= 7 ? 'var(--danger)' : 'var(--accent-700)',
                  fontWeight: 700,
                }}>
                  {r.daysLeft === 0
                    ? (isAr ? 'اليوم' : 'today')
                    : `${r.daysLeft} ${isAr ? 'يوم' : 'd'}`}
                </span>
                <Button size="sm" variant="ghost" Icon={ChevronRight} onClick={() => withdraw(r)}>
                  {isAr ? 'سحب' : 'Withdraw'}
                </Button>
              </div>
            ))}
            {expiringSoon.length > 5 && (
              <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center', marginTop: 4 }}>
                {isAr ? `+ ${expiringSoon.length - 5} موافقة أخرى` : `+ ${expiringSoon.length - 5} more`}
              </div>
            )}
          </div>
        </Card>
      )}

      <div className="dash-grid">
        {/* ── Channel mix ───────────────────────────────────────────── */}
        <Card title={isAr ? 'قنوات جمع الموافقة' : 'Collection channels'} Icon={Users}
              action={<Badge tone="primary">{channelMix.length}</Badge>}>
          {channelMix.length === 0 && <InlineEmpty>{isAr ? 'لا توجد بيانات بعد' : 'No data yet'}</InlineEmpty>}
          {channelMix.map(c => {
            const sharePct = ledgerSize > 0 ? Math.round((c.granted / ledgerSize) * 100) : 0;
            return (
              <Progress
                key={c.channel}
                label={`${channelLabel(c.channel)} · ${c.granted}${c.withdrawn > 0 ? ` (${c.withdrawn} ${isAr ? 'مسحوبة' : 'withdrawn'})` : ''}`}
                value={sharePct}
                tone={c.ratePct >= 10 ? 'danger' : c.ratePct >= 5 ? 'accent' : 'primary'}
              />
            );
          })}
        </Card>

        {/* ── Purpose mix + withdrawal rate ─────────────────────────── */}
        <Card title={isAr ? 'حسب غرض المعالجة' : 'By processing purpose'} Icon={ScrollText}
              action={<Badge tone="primary">{purposeMix.length}</Badge>}>
          {purposeMix.length === 0 && <InlineEmpty>{isAr ? 'لا توجد بيانات بعد' : 'No data yet'}</InlineEmpty>}
          {purposeMix.map(p => {
            // Show share-of-ledger as the bar — but highlight purposes with
            // a high withdrawal rate (the DPO actionable signal).
            const sharePct = ledgerSize > 0 ? Math.round((p.granted / ledgerSize) * 100) : 0;
            const isHot = p.ratePct >= 10;
            return (
              <Progress
                key={p.purpose}
                label={`${purposeLabel(p.purpose)} · ${p.granted}${isHot ? ` · ⚠ ${p.ratePct}% ${isAr ? 'سحب' : 'withdrawn'}` : ''}`}
                value={sharePct}
                tone={isHot ? 'danger' : 'primary'}
              />
            );
          })}
        </Card>
      </div>

      <div className="dash-grid">
        {/* ── Recent grants ─────────────────────────────────────────── */}
        <Card title={isAr ? 'أحدث الموافقات' : 'Recent grants'} Icon={FileSignature}>
          {recentGrants.length === 0 && <InlineEmpty>{isAr ? 'لا توجد موافقات حديثة' : 'No recent grants'}</InlineEmpty>}
          {recentGrants.map(r => (
            <Link key={r.id} to={`/privacy/consents/${r.id}`} style={{
              display: 'grid', gridTemplateColumns: '1fr auto auto',
              gap: 8, padding: '8px 0', borderBottom: '1px solid var(--border)',
              alignItems: 'center', fontSize: 12, textDecoration: 'none',
              color: 'var(--ink-1)',
            }}>
              <div>
                <div style={{ fontWeight: 600 }}>{purposeLabel(r.purposeCode)}</div>
                <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {r.dataSubjectRef.slice(0, 18)}… · {channelLabel(r.grantedVia)} · {r.language}
                </div>
              </div>
              <Badge tone={LEGAL_BASIS_TONE[r.legalBasis] ?? 'neutral'}>{r.legalBasis.replace(/_/g, ' ')}</Badge>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {fmtDate(r.grantedAt)}
              </span>
            </Link>
          ))}
        </Card>

        {/* ── Recent withdrawals ────────────────────────────────────── */}
        <Card title={isAr ? 'أحدث عمليات السحب' : 'Recent withdrawals'} Icon={ShieldAlert}>
          {recentWithdrawals.length === 0 && <InlineEmpty>{isAr ? 'لا توجد عمليات سحب حديثة' : 'No recent withdrawals'}</InlineEmpty>}
          {recentWithdrawals.map(r => (
            <div key={r.id} style={{
              display: 'grid', gridTemplateColumns: '1fr auto auto',
              gap: 8, padding: '8px 0', borderBottom: '1px solid var(--border)',
              alignItems: 'center', fontSize: 12,
            }}>
              <div>
                <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{purposeLabel(r.purposeCode)}</div>
                <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {r.dataSubjectRef.slice(0, 18)}…
                  {r.withdrawnReason && (
                    <span style={{ marginInlineStart: 6 }}>· {r.withdrawnReason.slice(0, 60)}</span>
                  )}
                </div>
              </div>
              <Badge tone={STATUS_TONE.WITHDRAWN}>{isAr ? 'مسحوبة' : 'Withdrawn'}</Badge>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {fmtDate(r.withdrawnAt)}
              </span>
            </div>
          ))}
        </Card>
      </div>

      {/* PDPL anchor — every consent dashboard a Saudi regulator looks
          at expects the legal anchor to be explicit. */}
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center', marginTop: 4 }}>
        {isAr
          ? 'نظام حماية البيانات الشخصية — المادة 5 (السجل) والمادة 6 (السحب)'
          : 'PDPL — Article 5 (ledger) & Article 6 (right of withdrawal)'}
      </div>
    </div>
  );
}

function fmtDate(s?: string): string { return s ? s.slice(0, 10) : '—'; }
