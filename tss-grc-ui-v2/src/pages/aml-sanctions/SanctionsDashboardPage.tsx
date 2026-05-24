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
import { useI18n } from '../../lib/i18n';
import { sanctionsApi, type SanctionsDashboard } from '../../lib/sanctions';

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

  async function load() {
    setState('loading'); setErr(null);
    try {
      setSnap(await sanctionsApi.dashboard());
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { void load(); }, []);

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
            />
            <KpiTile
              icon={AlertTriangle}
              value={String(snap.hitsPendingReview)}
              label={isAr ? 'تطابقات في انتظار المراجعة' : 'Hits pending review'}
              tone={snap.hitsPendingReview > 0 ? 'warning' : 'neutral'}
            />
            <KpiTile
              icon={Clock}
              value={String(snap.hitsSlaBreached)}
              label={isAr ? 'تجاوز المهلة (٢٤ س)' : 'SLA breached'}
              tone={snap.hitsSlaBreached > 0 ? 'danger' : 'success'}
            />
            <KpiTile
              icon={ShieldAlert}
              value={String(snap.hitsTruePositive)}
              label={isAr ? 'تطابقات مُؤكّدة' : 'Confirmed positives'}
              tone={snap.hitsTruePositive > 0 ? 'danger' : 'neutral'}
            />
            <KpiTile
              icon={TrendingUp}
              value={snap.falsePositiveRate == null
                ? '—'
                : `${(snap.falsePositiveRate * 100).toFixed(1)}%`}
              label={isAr ? 'نسبة الإيجابيات الخاطئة' : 'False-positive rate'}
              tone="accent"
            />
            <KpiTile
              icon={CheckCircle2}
              value={String(snap.totalChecks)}
              label={isAr ? 'إجمالي الفحوصات' : 'Total checks'}
              tone="neutral"
            />
          </div>

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

function KpiTile({ icon: Icon, value, label, tone }:
  { icon: typeof Activity; value: string; label: string; tone: BadgeTone }) {
  return (
    <Card>
      <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--ink-3)' }}>
          <Icon size={14} />
          <span style={{ fontSize: 11 }}>{label}</span>
        </div>
        <div style={{ fontSize: 24, fontWeight: 700 }}>
          <Badge tone={tone}>{value}</Badge>
        </div>
      </div>
    </Card>
  );
}
