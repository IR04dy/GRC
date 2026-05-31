import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Inbox, ShieldCheck, ScrollText, Siren, BookOpen,
  Archive, Database, Sigma, Scale, AlertOctagon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { dataOfficerApi, type DashboardResponse } from '../../lib/dataOfficer';

/** Sprint 33 — Saudi Data Officer Toolkit unified dashboard.
 *  One landing surface for the DPO/CDO: DSR queue, breach 72h-clock,
 *  consent, DPIA, notices, retention, NDR, catalog, quality, ethics. */
export function DataOfficerDashboardPage() {
  const { lang } = useI18n();
  const demoMode = !isAuthenticated();
  const [d, setD] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demoMode) return;
    dataOfficerApi.dashboard()
      .then(setD)
      .catch(err => setError(err instanceof ApiError ? err.message : 'Failed to load'));
  }, [demoMode]);

  return (
    <Shell>
      <PageHeader
        eyebrow={lang === 'ar' ? 'المسؤول التنفيذي للبيانات · المرحلة ٣٣' : 'Data Officer · Sprint 33'}
        title={lang === 'ar' ? 'مركز قيادة مسؤول البيانات' : 'Data Officer Command Centre'}
        sub={lang === 'ar'
          ? 'نظام حماية البيانات الشخصية + معايير NDMO + سدايا — كل ما يحتاجه المسؤول التنفيذي للبيانات في مكان واحد'
          : 'PDPL + NDMO standards + SDAIA — everything the DPO/CDO needs in one place'}
      />

      {error && <Card><div style={{ padding: 16, color: '#a00' }}>{error}</div></Card>}

      {/* Top-row KPIs — the four numbers a DPO looks at every morning */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, marginBottom: 16 }}>
        <Kpi Icon={Inbox} label={lang === 'ar' ? 'طلبات حقوق أصحاب البيانات المفتوحة' : 'Open DSRs'}
             value={d ? d.dsrOpen : '—'}
             tone={d && d.dsrOverdue > 0 ? 'danger' : 'primary'} />
        <Kpi Icon={Siren} label={lang === 'ar' ? 'خروقات مفتوحة' : 'Open Breaches'}
             value={d ? d.breachOpen : '—'}
             tone={d && d.breachOverdueSdaia > 0 ? 'danger' : 'primary'} />
        <Kpi Icon={ShieldCheck} label={lang === 'ar' ? 'موافقات نافذة' : 'Active Consents'}
             value={d ? d.consentActive : '—'} tone="success" />
        <Kpi Icon={ScrollText} label={lang === 'ar' ? 'تقييمات الأثر المفتوحة' : 'Open DPIAs'}
             value={d ? d.dpiaOpen : '—'} tone="info" />
      </div>

      {/* Sub-module tiles — one click into each surface */}
      <Card>
        <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)', fontWeight: 700 }}>
          {lang === 'ar' ? 'المهام اليومية لمسؤول البيانات' : 'Daily Data Officer surfaces'}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 1, background: 'var(--border)' }}>
          <Tile to="/data-officer/dsr" Icon={Inbox}
            title={lang === 'ar' ? 'حقوق أصحاب البيانات (DSR)' : 'Data Subject Rights (DSR)'}
            sub={lang === 'ar' ? 'مادة ٩-١٤ من نظام حماية البيانات · ٣٠ يوماً للرد' : 'PDPL Art. 9-14 · 30-day SLA'}
            value={d?.dsrOpen} alert={d?.dsrOverdue} />
          <Tile to="/data-officer/consents" Icon={ShieldCheck}
            title={lang === 'ar' ? 'إدارة الموافقات' : 'Consent Management'}
            sub={lang === 'ar' ? 'مادة ٥-٦ من نظام حماية البيانات · موافقة بغرض محدّد' : 'PDPL Art. 5-6 · purpose-bound opt-in'}
            value={d?.consentActive} />
          <Tile to="/data-officer/dpia" Icon={ScrollText}
            title={lang === 'ar' ? 'تقييم الأثر (DPIA)' : 'DPIA Workbench'}
            sub={lang === 'ar' ? 'مادة ٢٥ من نظام حماية البيانات · المعالجة عالية المخاطر' : 'PDPL Art. 25 · high-risk processing'}
            value={d?.dpiaOpen} />
          <Tile to="/data-officer/breaches" Icon={Siren}
            title={lang === 'ar' ? 'إبلاغ الخروقات (ساعة الـ٧٢)' : 'Breach Notification (72h clock)'}
            sub={lang === 'ar' ? 'مادة ٢٠ من نظام حماية البيانات · إبلاغ سدايا خلال ٧٢ ساعة' : 'PDPL Art. 20 · SDAIA within 72h'}
            value={d?.breachOpen} alert={d?.breachOverdueSdaia} />
          <Tile to="/data-officer/notices" Icon={BookOpen}
            title={lang === 'ar' ? 'إدارة إشعارات الخصوصية' : 'Privacy Notice Manager'}
            sub={lang === 'ar' ? 'مادة ١٢ من نظام حماية البيانات · ثنائي اللغة · مُعدَّل النسخة' : 'PDPL Art. 12 · bilingual · versioned'}
            value={d?.noticesPublished}
            alert={d?.noticesNeedingReConsent} />
          <Tile to="/data-officer/retention" Icon={Archive}
            title={lang === 'ar' ? 'الاحتفاظ والتخلّص' : 'Retention & Disposal'}
            sub={lang === 'ar' ? 'مادة ١٨ من نظام حماية البيانات · مدد وأدلة تخلّص' : 'PDPL Art. 18 · schedules + disposal evidence'}
            value={d?.disposalScheduled} alert={d?.retentionDue} />
          <Tile to="/data-officer/ndr" Icon={ScrollText}
            title={lang === 'ar' ? 'السجل الوطني للبيانات (NDR)' : 'National Data Register (NDR)'}
            sub={lang === 'ar' ? 'تقديم سنوي إلى سدايا · معايير NDMO' : 'Annual SDAIA submission · NDMO standards'}
            value={d ? ndrStatusLabel(d.ndrCurrentYearStatusInt, lang) : '—'} />
          <Tile to="/data-officer/catalog" Icon={Database}
            title={lang === 'ar' ? 'دليل البيانات والنَّسَب' : 'Data Catalog + Lineage'}
            sub={lang === 'ar' ? 'مجال إدارة البيانات في NDMO · ملاك ووصاة' : 'NDMO Data Management · owners + stewards'}
            value={d?.catalogEntries}
            hint={d ? `${d.catalogPiiEntries} PII` : undefined} />
          <Tile to="/data-officer/quality" Icon={Sigma}
            title={lang === 'ar' ? 'بطاقات جودة البيانات' : 'Data Quality Scorecards'}
            sub={lang === 'ar' ? 'مجال جودة البيانات في NDMO · القواعد والتشغيلات' : 'NDMO DQM · rules + runs'}
            value={d?.qualityRules} alert={d?.qualityRedRunsLast30} />
          <Tile to="/data-officer/ethics" Icon={Scale}
            title={lang === 'ar' ? 'لجنة أخلاقيات البيانات' : 'Data Ethics Committee'}
            sub={lang === 'ar' ? 'أخلاقيات الذكاء الاصطناعي في سدايا + أخلاقيات بيانات NDMO' : 'SDAIA AI Ethics + NDMO Data Ethics'}
            value={d?.ethicsOpen} />
        </div>
      </Card>
    </Shell>
  );
}

function ndrStatusLabel(code: number | undefined, lang: string): string {
  if (code == null) return '—';
  const map: Record<string, { en: string; ar: string }> = {
    '0': { en: 'Draft',     ar: 'مسوّدة' },
    '1': { en: 'Ready',     ar: 'جاهز' },
    '2': { en: 'Submitted', ar: 'مُقدَّم' },
    '3': { en: 'Accepted',  ar: 'مقبول' },
  };
  return map[String(code)]?.[lang === 'ar' ? 'ar' : 'en'] ?? '—';
}

interface TileProps {
  to: string;
  Icon: typeof Inbox;
  title: string;
  sub: string;
  value?: number | string;
  alert?: number;
  hint?: string;
}
function Tile({ to, Icon, title, sub, value, alert, hint }: TileProps) {
  const showAlert = typeof alert === 'number' && alert > 0;
  return (
    <Link to={to} style={{
      display: 'block', padding: '14px 16px',
      background: 'var(--surface)',
      textDecoration: 'none', color: 'inherit',
      transition: 'all 0.15s',
    }} onMouseEnter={e => (e.currentTarget.style.background = 'var(--surface-2)')}
       onMouseLeave={e => (e.currentTarget.style.background = 'var(--surface)')}>
      <div style={{ display: 'flex', alignItems: 'start', gap: 12 }}>
        <div style={{ flexShrink: 0, color: showAlert ? 'var(--danger, #d92d20)' : 'var(--primary-300)' }}>
          {showAlert ? <AlertOctagon size={18} strokeWidth={1.8} /> : <Icon size={18} strokeWidth={1.8} />}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, marginBottom: 3 }}>{title}</div>
          <div style={{ fontSize: 11.5, opacity: 0.7, lineHeight: 1.4 }}>{sub}</div>
        </div>
        <div style={{ textAlign: 'end', minWidth: 60 }}>
          <div style={{ fontSize: 22, fontWeight: 700, lineHeight: 1, color: showAlert ? 'var(--danger, #d92d20)' : 'inherit' }}>
            {value ?? '—'}
          </div>
          {showAlert && (
            <div style={{ fontSize: 10, color: 'var(--danger, #d92d20)', marginTop: 4, fontWeight: 600 }}>
              {alert} ⚠
            </div>
          )}
          {!showAlert && hint && (
            <div style={{ fontSize: 10, opacity: 0.6, marginTop: 4 }}>{hint}</div>
          )}
        </div>
      </div>
    </Link>
  );
}
