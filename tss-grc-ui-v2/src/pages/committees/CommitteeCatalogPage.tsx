import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Landmark, Banknote, Shield, Building2, Activity, Briefcase,
  FileText, BookOpen, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import {
  listTemplates, listForms,
  TYPE_LABEL_EN, TYPE_LABEL_AR, DOMAIN_TONE,
  type CommitteeTemplate, type FormTemplate, type RegulatoryDomain,
} from '../../lib/committees';

/**
 * Committee catalog — the entry point for the GRC-SME content.
 *
 * <p>Shows the 19 Saudi-regulated committee templates organised by
 * regulatory domain (banking → SAMA, listed → CMA, public → NDMO/SDAIA,
 * general → all sectors). Each card opens the full charter view at
 * /committees/catalog/:templateCode with mandate / authority /
 * composition / standing items / applicable forms.
 *
 * <p>The 10-form bilingual library is also surfaced here so users can
 * download the templates they need (charter, agenda, minutes, motion,
 * action register, COI declaration, skills matrix, annual evaluation).
 */

const DOMAIN_LABEL_EN: Record<RegulatoryDomain, string> = {
  BANKING: 'Banking (SAMA)',
  INSURANCE: 'Insurance (SAMA)',
  CMA: 'Listed companies (CMA)',
  PUBLIC: 'Public sector',
  HEALTHCARE: 'Healthcare',
  GENERAL: 'General — all sectors',
};

const DOMAIN_LABEL_AR: Record<RegulatoryDomain, string> = {
  BANKING: 'المصرفي (ساما)',
  INSURANCE: 'التأمين (ساما)',
  CMA: 'الشركات المدرجة (هيئة السوق المالية)',
  PUBLIC: 'القطاع العام',
  HEALTHCARE: 'الرعاية الصحية',
  GENERAL: 'عام — جميع القطاعات',
};

const DOMAIN_ICON: Record<RegulatoryDomain, typeof Landmark> = {
  BANKING: Banknote,
  INSURANCE: Shield,
  CMA: Landmark,
  PUBLIC: Building2,
  HEALTHCARE: Activity,
  GENERAL: Briefcase,
};

const DOMAIN_ORDER: RegulatoryDomain[] = ['CMA', 'BANKING', 'GENERAL', 'PUBLIC', 'INSURANCE', 'HEALTHCARE'];

export function CommitteeCatalogPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [templates, setTemplates] = useState<CommitteeTemplate[]>([]);
  const [forms, setForms] = useState<FormTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listTemplates(), listForms()])
      .then(([t, f]) => {
        if (cancelled) return;
        setTemplates(t);
        setForms(f);
      })
      .catch(e => { if (!cancelled) setError((e as Error).message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  /** Group templates by regulatory domain — drives the section layout. */
  const grouped = useMemo(() => {
    const map = new Map<RegulatoryDomain, CommitteeTemplate[]>();
    for (const tpl of templates) {
      const arr = map.get(tpl.regulatoryDomain) ?? [];
      arr.push(tpl);
      map.set(tpl.regulatoryDomain, arr);
    }
    return map;
  }, [templates]);

  const stats = useMemo(() => ({
    templates: templates.length,
    forms: forms.length,
    domains: grouped.size,
    independenceRequired: templates.filter(t => t.independenceRequired === 1).length,
  }), [templates, forms, grouped]);

  return (
    <Shell
      title={isAr ? 'كتالوج اللجان والاجتماعات' : 'Committees & Meetings Catalog'}
      sub={isAr
        ? '19 لجنة منظَّمة سعودياً — ميثاق كامل + جدول أعمال دائم + مكتبة نماذج'
        : '19 Saudi-regulated committees — full charters + standing agendas + forms library'}
    >
      <PageHeader
        eyebrow={isAr ? 'الحوكمة والإطار التنظيمي' : 'Governance & Regulatory'}
        title={isAr ? 'كتالوج اللجان والاجتماعات' : 'Committees & Meetings Catalog'}
        sub={isAr
          ? 'CMA · SAMA · NCA · PDPL · NDMO · SDAIA — كل لجنة مع مرجعها التنظيمي'
          : 'CMA · SAMA · NCA · PDPL · NDMO · SDAIA — every committee anchored to its regulator'}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Landmark}      label={isAr ? 'قوالب اللجان'      : 'Committee templates'} value={String(stats.templates)} tone="info" />
        <Kpi Icon={FileText}      label={isAr ? 'النماذج المتاحة'    : 'Forms available'}      value={String(stats.forms)}     tone="success" onClick={() => document.getElementById('forms-library-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={BookOpen}      label={isAr ? 'النطاقات التنظيمية' : 'Regulatory domains'}    value={String(stats.domains)}   tone="neutral" />
        <Kpi Icon={AlertTriangle} label={isAr ? 'تتطلب الاستقلالية'   : 'Independence required'} value={String(stats.independenceRequired)} tone="warning" />
      </div>

      {loading && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
      {error && <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>{error}</div>}

      {!loading && !error && DOMAIN_ORDER.map(domain => {
        const items = grouped.get(domain);
        if (!items || items.length === 0) return null;
        const Icon = DOMAIN_ICON[domain];
        return (
          <div key={domain} style={{ marginBottom: 16 }}>
          <Card
            title={isAr ? DOMAIN_LABEL_AR[domain] : DOMAIN_LABEL_EN[domain]}
            Icon={Icon}
            action={<Badge tone={DOMAIN_TONE[domain]}>{items.length}</Badge>}
          >
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: 12,
            }}>
              {items.map(tpl => (
                <Link
                  key={tpl.templateCode}
                  to={`/committees/catalog/${encodeURIComponent(tpl.templateCode)}`}
                  style={{
                    display: 'block',
                    padding: 12,
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--r-sm)',
                    background: 'var(--surface-1)',
                    textDecoration: 'none',
                    color: 'inherit',
                  }}
                >
                  <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--ink-1)', marginBottom: 4 }}>
                    {isAr ? tpl.nameAr : tpl.nameEn}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', marginBottom: 6 }}>
                    <span className="mono">{tpl.templateCode}</span>
                    {' · '}{isAr ? TYPE_LABEL_AR[tpl.committeeType] : TYPE_LABEL_EN[tpl.committeeType]}
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 6 }}>
                    <Badge tone="info">{tpl.cadence}</Badge>
                    <Badge tone="neutral">{isAr ? `نصاب ${tpl.quorumPct}%` : `Quorum ${tpl.quorumPct}%`}</Badge>
                    {tpl.independenceRequired === 1 && (
                      <Badge tone="warning">{isAr ? 'استقلالية' : 'Independence'}</Badge>
                    )}
                    {tpl.financialExpertRequired === 1 && (
                      <Badge tone="warning">{isAr ? 'خبير مالي' : 'Financial expert'}</Badge>
                    )}
                  </div>
                  {tpl.regulatorAnchors && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                      <span style={{ fontWeight: 600 }}>{isAr ? 'المرجع: ' : 'Anchor: '}</span>
                      {tpl.regulatorAnchors}
                    </div>
                  )}
                </Link>
              ))}
            </div>
          </Card>
          </div>
        );
      })}

      {/* Forms library card */}
      {!loading && !error && forms.length > 0 && (
        <div id="forms-library-section">
        <Card
          title={isAr ? 'مكتبة نماذج اللجان' : 'Forms Library'}
          Icon={FileText}
          action={<Badge tone="success">{forms.length}</Badge>}
        >
          <p style={{ fontSize: 12, color: 'var(--ink-3)', margin: '0 0 12px' }}>
            {isAr
              ? 'نماذج ثنائية اللغة جاهزة للتنزيل تغطي دورة الاجتماع كاملة — الميثاق، جدول الأعمال، صفحة غلاف ورقة القراءة، الحضور والنصاب، الاقتراح، المحضر، سجل الإجراءات، إعلان تعارض المصالح، مصفوفة المهارات، التقييم السنوي.'
              : 'Bilingual downloadable forms covering the full meeting lifecycle — Charter, Agenda, Pre-read Coversheet, Attendance & Quorum, Motion, Minutes, Action Register, COI Declaration, Skills Matrix, Annual Evaluation.'}
          </p>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
            gap: 8,
          }}>
            {forms.map(f => (
              <Link
                key={f.formCode}
                to={`/committees/catalog/forms/${encodeURIComponent(f.formCode)}`}
                style={{
                  display: 'block',
                  padding: 10,
                  border: '1px solid var(--border)',
                  borderRadius: 'var(--r-sm)',
                  background: 'var(--surface-1)',
                  textDecoration: 'none',
                  color: 'inherit',
                }}
              >
                <div style={{ fontWeight: 600, fontSize: 12, color: 'var(--ink-1)', marginBottom: 2 }}>
                  {isAr ? f.titleAr : f.titleEn}
                </div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                  <Badge tone="info" className="me-1">{f.formType}</Badge>
                  <span className="mono">v{f.version}</span>
                </div>
              </Link>
            ))}
          </div>
        </Card>
        </div>
      )}
    </Shell>
  );
}
