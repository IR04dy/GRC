import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity, AlertTriangle, CheckCircle2, Clock, RefreshCw,
  ScanSearch, ShieldAlert, ShieldCheck, TrendingUp,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { useI18n } from '../../lib/i18n';
import {
  sanctionsApi, hitStatusLabel, matchTypeLabel,
  type SanctionsDashboard, type ScreeningHit,
} from '../../lib/sanctions';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Sprint 13 — Sanctions Screening KPI dashboard.
 *
 * <p>Five tiles: checks last 24h, hits pending review, SLA breaches,
 * confirmed true positives, false-positive rate.
 *
 * <p>Saudi-native Arabic labels used here:
 *   "الفحص ضد قوائم العقوبات" — Sanctions screening
 *   "تطابق احتمالي" / "تطابق مُؤكّد" — Potential / Confirmed hit
 *   "تجاوز المهلة" — SLA breach
 */
export function SanctionsDashboardPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [state, setState] = useState<LoadState>('idle');
  const [snap, setSnap] = useState<SanctionsDashboard | null>(null);
  const [err, setErr] = useState<string | null>(null);
  // Pending-review hit queue — surfaces listHits so the compliance officer can
  // adjudicate (reviewHit) or escalate (escalateHit) straight from the dashboard.
  const [hits, setHits] = useState<ScreeningHit[]>([]);
  const [hitBusy, setHitBusy] = useState<number | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      const [s, h] = await Promise.all([
        sanctionsApi.dashboard(),
        sanctionsApi.listHits('PENDING_REVIEW').catch(() => [] as ScreeningHit[]),
      ]);
      setSnap(s);
      setHits(Array.isArray(h) ? h : []);
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); }, []);

  async function reviewHit(hit: ScreeningHit, newStatus: 'FALSE_POSITIVE' | 'TRUE_POSITIVE',
                           dispositionEn: string, dispositionAr: string) {
    if (hitBusy) return;
    setHitBusy(hit.id);
    try {
      await sanctionsApi.reviewHit(hit.id, { newStatus, dispositionEn, dispositionAr });
      await load();
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
    } finally { setHitBusy(null); }
  }

  async function escalateHit(hit: ScreeningHit) {
    if (hitBusy) return;
    setHitBusy(hit.id);
    try {
      await sanctionsApi.escalateHit(hit.id, {
        reasonEn: 'Escalated from the screening dashboard for senior AML review.',
        reasonAr: 'صُعِّد من لوحة الفحص لمراجعة من جهة أعلى في مكافحة غسل الأموال.',
      });
      await load();
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
    } finally { setHitBusy(null); }
  }

  return (
    <Shell
      title={isAr ? 'الفحص ضد قوائم العقوبات' : 'Sanctions screening'}
      sub={isAr
        ? 'منصة الفحص في الزمن الحقيقي ضد OFAC SDN والأمم المتحدة والاتحاد الأوروبي وقائمة الإرهاب لساما والقائمة الوطنية السعودية'
        : 'Real-time screening against OFAC SDN, UN, EU, SAMA local-terror, and the KSA National watchlist'}
    >
      <PageHeader
        eyebrow={isAr ? 'مكافحة غسل الأموال وتمويل الإرهاب' : 'AML / CFT'}
        title={isAr ? 'لوحة الفحص ضد قوائم العقوبات' : 'Sanctions screening dashboard'}
        sub={isAr
          ? 'يفي بالتزام مؤسسة النقد العربي السعودي بقواعد مكافحة غسل الأموال وتمويل الإرهاب (٢٠١٨ §٣.٤) وتوصيات مجموعة العمل المالي ٥ و٦ و٧'
          : 'Operationalises SAMA AML/CFT Rules 2018 §3.4 and FATF Recommendations 5, 6, 7'}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/aml/sanctions/screen">
              <Button variant="primary" size="sm">
                <ScanSearch size={14} /> {isAr ? 'فحص جديد' : 'New screening'}
              </Button>
            </Link>
            <Link to="/aml/sanctions/lists">
              <Button variant="ghost" size="sm">
                <ShieldCheck size={14} /> {isAr ? 'قوائم العقوبات' : 'Lists'}
              </Button>
            </Link>
            <Link to="/aml/sanctions/checks">
              <Button variant="ghost" size="sm">
                <Activity size={14} /> {isAr ? 'سجلّ الفحوصات' : 'Check log'}
              </Button>
            </Link>
          </>
        }
      />

      {state === 'loading' && (
        <Card>
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        </Card>
      )}

      {state === 'error' && (
        <Card>
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>
            {err ?? (isAr ? 'تعذّر تحميل البيانات' : 'Could not load data')}
          </div>
        </Card>
      )}

      {state === 'ready' && snap && (
        <>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: 12, marginBottom: 16,
          }}>
            <KpiTile
              icon={Activity}
              value={String(snap.checksLast24h)}
              label={isAr ? 'فحوصات خلال ٢٤ ساعة' : 'Checks (24 h)'}
              tone="info"
              to="/aml/sanctions/checks"
            />
            <KpiTile
              icon={AlertTriangle}
              value={String(snap.hitsPendingReview)}
              label={isAr ? 'تطابقات في انتظار المراجعة' : 'Hits pending review'}
              tone={snap.hitsPendingReview > 0 ? 'warning' : 'neutral'}
              to="/aml/sanctions/checks"
            />
            <KpiTile
              icon={Clock}
              value={String(snap.hitsSlaBreached)}
              label={isAr ? 'تجاوز المهلة (٢٤ س)' : 'SLA breached'}
              tone={snap.hitsSlaBreached > 0 ? 'danger' : 'success'}
              to="/aml/sanctions/checks"
            />
            <KpiTile
              icon={ShieldAlert}
              value={String(snap.hitsTruePositive)}
              label={isAr ? 'تطابقات مُؤكّدة' : 'Confirmed positives'}
              tone={snap.hitsTruePositive > 0 ? 'danger' : 'neutral'}
              to="/aml/sanctions/checks"
            />
            <KpiTile
              icon={TrendingUp}
              value={snap.falsePositiveRate == null
                ? '—'
                : `${(snap.falsePositiveRate * 100).toFixed(1)}%`}
              label={isAr ? 'نسبة الإيجابيات الخاطئة' : 'False-positive rate'}
              tone="accent"
              to="/aml/sanctions/checks"
            />
            <KpiTile
              icon={CheckCircle2}
              value={String(snap.totalChecks)}
              label={isAr ? 'إجمالي الفحوصات' : 'Total checks'}
              tone="neutral"
              to="/aml/sanctions/dashboard"
            />
          </div>

          {hits.length > 0 && (
            <Card>
              <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8 }}>
                <AlertTriangle size={16} style={{ color: 'var(--warning)' }} />
                <span style={{ fontSize: 14, fontWeight: 600 }}>
                  {isAr ? `تطابقات في انتظار المراجعة (${hits.length})` : `Hits pending review (${hits.length})`}
                </span>
                <span style={{ fontSize: 12, color: 'var(--ink-3)', marginInlineStart: 'auto' }}>
                  {isAr ? 'فرز خلال ٢٤ ساعة' : 'Adjudicate within 24 h'}
                </span>
              </div>
              <DataTable<ScreeningHit>
                columns={hitCols(isAr, hitBusy, reviewHit, escalateHit)}
                rows={hits}
              />
            </Card>
          )}

          <Card>
            <div style={{ padding: 16 }}>
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                {isAr ? 'كيف يعمل هذا؟' : 'How does this work?'}
              </div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.6 }}>
                {isAr
                  ? 'يقوم محرّك الفحص بمطابقة كل عميل جديد وكل طرف معاملة ضد القوائم النشطة في الزمن الحقيقي. تطابق تامّ للاسم = ١٠٠، تطابق تقريبي = ٩٠، اسم بديل = ٨٥، مع زيادات لتطابق تاريخ الميلاد والبلد ووثيقة الهوية. كل تطابق فوق العتبة ٧٥ يفتح حالة فرز يتولاها فريق الالتزام في غضون ٢٤ ساعة قبل تسجيل تجاوز المهلة.'
                  : 'The engine matches every new customer and transaction counterparty in real time against the active lists. Exact-name → 100, fuzzy → 90, alias → 85, with boosts for DOB / country / document match. Every hit ≥ threshold 75 opens a triage case that the compliance team must adjudicate within 24 hours before SLA breach is recorded.'}
              </div>
            </div>
          </Card>

          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 12, textAlign: isAr ? 'end' : 'start' }}>
            {isAr ? 'آخر تحديث:' : 'As of:'} {snap.asOf.slice(0, 19).replace('T', ' ')}
          </div>
        </>
      )}
    </Shell>
  );
}

function hitCols(
  isAr: boolean,
  hitBusy: number | null,
  reviewHit: (h: ScreeningHit, s: 'FALSE_POSITIVE' | 'TRUE_POSITIVE', en: string, ar: string) => void,
  escalateHit: (h: ScreeningHit) => void,
): Column<ScreeningHit>[] {
  return [
    {
      header: isAr ? 'الاسم المُطابَق' : 'Matched name',
      cell: h => (
        <div>
          <div style={{ fontWeight: 600, fontSize: 13 }}>
            {isAr ? (h.matchedNameAr || h.matchedNameEn || '—') : (h.matchedNameEn || h.matchedNameAr || '—')}
          </div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{h.listCode ?? '—'}</div>
        </div>
      ),
    },
    {
      header: isAr ? 'الدرجة' : 'Score', width: '90px', align: 'center',
      cell: h => <Badge tone={h.matchScore >= 95 ? 'danger' : h.matchScore >= 85 ? 'warning' : 'neutral'}>{h.matchScore}</Badge>,
    },
    {
      header: isAr ? 'نوع التطابق' : 'Match type', width: '150px',
      cell: h => <span style={{ fontSize: 12 }}>{matchTypeLabel(h.matchType, isAr)}</span>,
    },
    {
      header: isAr ? 'الحالة' : 'Status', width: '140px', align: 'center',
      cell: h => <Badge tone="warning">{hitStatusLabel(h.status, isAr)}</Badge>,
    },
    {
      header: '', width: '280px', align: 'end',
      cell: h => (
        <span style={{ display: 'inline-flex', gap: 4, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          <Button size="sm" variant="secondary" disabled={hitBusy === h.id}
            onClick={() => reviewHit(h, 'FALSE_POSITIVE',
              'Reviewed from dashboard queue — false positive (no true match).',
              'روجعت من لوحة الفحص — إيجابية خاطئة (لا يوجد تطابق فعلي).')}>
            {isAr ? 'إيجابية خاطئة' : 'False positive'}
          </Button>
          <Button size="sm" variant="danger" disabled={hitBusy === h.id}
            onClick={() => reviewHit(h, 'TRUE_POSITIVE',
              'Reviewed from dashboard queue — confirmed true positive.',
              'روجعت من لوحة الفحص — تطابق مُؤكّد.')}>
            {isAr ? 'تطابق مُؤكّد' : 'True positive'}
          </Button>
          <Button size="sm" variant="ghost" disabled={hitBusy === h.id}
            onClick={() => escalateHit(h)}>
            {isAr ? 'تصعيد' : 'Escalate'}
          </Button>
        </span>
      ),
    },
  ];
}

function KpiTile({ icon: Icon, value, label, tone, to }:
  { icon: typeof Activity; value: string; label: string; tone: BadgeTone; to?: string }) {
  const inner = (
    <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--ink-3)' }}>
        <Icon size={14} />
        <span style={{ fontSize: 11 }}>{label}</span>
      </div>
      <div style={{ fontSize: 24, fontWeight: 700 }}>
        <Badge tone={tone}>{value}</Badge>
      </div>
    </div>
  );
  return (
    <Card>
      {to ? (
        <Link to={to} style={{ textDecoration: 'none', color: 'inherit', display: 'block', cursor: 'pointer' }}>
          {inner}
        </Link>
      ) : inner}
    </Card>
  );
}
