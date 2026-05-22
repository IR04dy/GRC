import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Landmark, BookOpen, Users, ShieldCheck, FileText, ArrowUpRight,
  Clock, Award, Building2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { DetailHeader } from '../../components/layout/DetailHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import {
  getTemplate, getStandingItems, listForms,
  TYPE_LABEL_EN, TYPE_LABEL_AR, DOMAIN_TONE,
  type CommitteeTemplate, type StandingItem, type FormTemplate,
} from '../../lib/committees';

/**
 * Full charter view for one of the 19 catalog templates. Renders the
 * mandate / authority / composition / quorum / reporting line content
 * as-is (the V154 seed is real GRC-SME content), then lists the
 * committee's standing agenda items + applicable forms.
 *
 * <p>Route: `/committees/catalog/:templateCode`
 */
export function CommitteeTemplateDetailPage() {
  const { templateCode } = useParams<{ templateCode: string }>();
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [tpl, setTpl] = useState<CommitteeTemplate | null>(null);
  const [items, setItems] = useState<StandingItem[]>([]);
  const [forms, setForms] = useState<FormTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!templateCode) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([
      getTemplate(templateCode),
      getStandingItems(templateCode),
    ])
      .then(([t, si]) => {
        if (cancelled) return;
        setTpl(t);
        setItems(si);
        return listForms(t.committeeType);
      })
      .then(f => { if (!cancelled && f) setForms(f); })
      .catch(e => { if (!cancelled) setError((e as Error).message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [templateCode]);

  if (loading) {
    return (
      <Shell title={isAr ? 'كتالوج اللجان' : 'Committee Catalog'}>
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
          {isAr ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      </Shell>
    );
  }

  if (error || !tpl) {
    return (
      <Shell title={isAr ? 'كتالوج اللجان' : 'Committee Catalog'}>
        <DetailHeader
          breadcrumbs={[
            { label: isAr ? 'الكتالوج' : 'Catalog', to: '/committees/catalog' },
            { label: templateCode ?? '?' },
          ]}
          title={isAr ? 'تعذّر التحميل' : 'Failed to load'}
          sub={error ?? (isAr ? 'القالب غير موجود' : 'Template not found')}
          backTo="/committees/catalog"
        />
        <EmptyState Icon={Landmark} title={isAr ? 'القالب غير موجود' : 'Template not found'} />
      </Shell>
    );
  }

  return (
    <Shell title={isAr ? 'كتالوج اللجان' : 'Committee Catalog'}>
      <DetailHeader
        breadcrumbs={[
          { label: isAr ? 'الكتالوج' : 'Catalog', to: '/committees/catalog' },
          { label: tpl.templateCode },
        ]}
        eyebrow={isAr ? TYPE_LABEL_AR[tpl.committeeType] : TYPE_LABEL_EN[tpl.committeeType]}
        title={isAr ? tpl.nameAr : tpl.nameEn}
        sub={<>
          <Badge tone={DOMAIN_TONE[tpl.regulatoryDomain]}>{tpl.regulatoryDomain}</Badge>
          {' '}
          <Badge tone="info">{tpl.cadence}</Badge>
          {' '}
          <Badge tone="neutral">{isAr ? `نصاب ${tpl.quorumPct}%` : `Quorum ${tpl.quorumPct}%`}</Badge>
          {' '}
          {tpl.independenceRequired === 1 && <Badge tone="warning">{isAr ? 'استقلالية مطلوبة' : 'Independence required'}</Badge>}
          {' '}
          {tpl.financialExpertRequired === 1 && <Badge tone="warning">{isAr ? 'خبير مالي مطلوب' : 'Financial expert required'}</Badge>}
        </>}
        backTo="/committees/catalog"
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)', gap: 16, alignItems: 'start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Mandate */}
          <Card title={isAr ? 'الولاية' : 'Mandate'} Icon={Landmark}>
            <p style={{ margin: 0, lineHeight: 1.7, fontSize: 13, whiteSpace: 'pre-wrap' }}>
              {isAr ? tpl.mandateAr : tpl.mandateEn}
            </p>
          </Card>

          {/* Authority */}
          <Card title={isAr ? 'الصلاحيات' : 'Authority'} Icon={ShieldCheck}>
            <p style={{ margin: 0, lineHeight: 1.7, fontSize: 13, whiteSpace: 'pre-wrap' }}>
              {isAr ? tpl.authorityAr : tpl.authorityEn}
            </p>
          </Card>

          {/* Composition */}
          <Card title={isAr ? 'التشكيل' : 'Composition'} Icon={Users}>
            <p style={{ margin: 0, lineHeight: 1.7, fontSize: 13, whiteSpace: 'pre-wrap' }}>
              {isAr ? tpl.compositionAr : tpl.compositionEn}
            </p>
          </Card>

          {/* Escalation */}
          {tpl.escalationEn && (
            <Card title={isAr ? 'التصعيد' : 'Escalation'} Icon={ArrowUpRight}>
              <p style={{ margin: 0, lineHeight: 1.7, fontSize: 13, whiteSpace: 'pre-wrap' }}>
                {isAr ? (tpl.escalationAr ?? tpl.escalationEn) : tpl.escalationEn}
              </p>
            </Card>
          )}

          {/* Regulator citation */}
          {tpl.regulatorCitationEn && (
            <Card title={isAr ? 'المرجع التنظيمي' : 'Regulator Citation'} Icon={Award}>
              <p style={{ margin: 0, lineHeight: 1.7, fontSize: 13, whiteSpace: 'pre-wrap', color: 'var(--ink-2)' }}>
                {isAr ? (tpl.regulatorCitationAr ?? tpl.regulatorCitationEn) : tpl.regulatorCitationEn}
              </p>
              {tpl.regulatorAnchors && (
                <div style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-3)' }}>
                  <strong>{isAr ? 'النطاقات: ' : 'Anchors: '}</strong>
                  {tpl.regulatorAnchors}
                </div>
              )}
            </Card>
          )}

          {/* Regulatory reporting */}
          {tpl.regulatoryReportingEn && (
            <Card title={isAr ? 'الرفع التنظيمي' : 'Regulatory Reporting'} Icon={Building2}>
              <p style={{ margin: 0, lineHeight: 1.7, fontSize: 13, color: 'var(--ink-2)' }}>
                {tpl.regulatoryReportingEn}
              </p>
            </Card>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Snapshot */}
          <Card title={isAr ? 'مختصر' : 'Snapshot'} Icon={Clock}>
            <dl style={{
              display: 'grid', gridTemplateColumns: '120px 1fr',
              margin: 0, gap: '6px 12px', fontSize: 12,
            }}>
              <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'الرمز' : 'Code'}</dt>
              <dd className="mono" style={{ margin: 0 }}>{tpl.templateCode}</dd>

              <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'النوع' : 'Type'}</dt>
              <dd style={{ margin: 0 }}>{isAr ? TYPE_LABEL_AR[tpl.committeeType] : TYPE_LABEL_EN[tpl.committeeType]}</dd>

              <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'النطاق' : 'Domain'}</dt>
              <dd style={{ margin: 0 }}>{tpl.regulatoryDomain}</dd>

              <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'التواتر' : 'Cadence'}</dt>
              <dd style={{ margin: 0 }}>{tpl.cadence}</dd>

              <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'النصاب' : 'Quorum'}</dt>
              <dd style={{ margin: 0 }}>{tpl.quorumPct}%</dd>

              {tpl.termYears && (<>
                <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'مدة العضوية' : 'Term'}</dt>
                <dd style={{ margin: 0 }}>{tpl.termYears} {isAr ? 'سنوات' : 'years'}</dd>
              </>)}

              {tpl.reportsToTemplateCode && (<>
                <dt style={{ color: 'var(--ink-3)' }}>{isAr ? 'يرفع إلى' : 'Reports to'}</dt>
                <dd style={{ margin: 0 }}>
                  <Link to={`/committees/catalog/${encodeURIComponent(tpl.reportsToTemplateCode)}`}
                        style={{ color: 'var(--primary)', textDecoration: 'none' }}>
                    {tpl.reportsToTemplateCode}
                  </Link>
                </dd>
              </>)}
            </dl>
          </Card>

          {/* Standing items */}
          <Card title={isAr ? 'البنود الدائمة لجدول الأعمال' : 'Standing Agenda Items'}
                Icon={BookOpen}
                action={<Badge tone="info">{items.length}</Badge>}>
            <ol style={{ listStyle: 'none', margin: 0, padding: 0,
                         display: 'flex', flexDirection: 'column', gap: 6 }}>
              {items.map(it => (
                <li key={it.id} style={{
                  padding: '6px 8px',
                  borderBottom: '1px dashed var(--border)',
                  fontSize: 12,
                }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
                    <span className="mono" style={{ color: 'var(--ink-3)', fontSize: 10 }}>#{it.seq}</span>
                    <span style={{ flex: 1, color: 'var(--ink-1)', fontWeight: 500 }}>
                      {isAr ? it.topicAr : it.topicEn}
                    </span>
                    {it.isMandatory === 1 && (
                      <Badge tone="primary">{isAr ? 'إلزامي' : 'Mandatory'}</Badge>
                    )}
                  </div>
                  {(it.presenterRole || it.sourceModule) && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2, marginLeft: 18 }}>
                      {it.presenterRole && <span>{it.presenterRole}</span>}
                      {it.presenterRole && it.sourceModule && ' · '}
                      {it.sourceModule && (
                        <Badge tone="neutral" className="me-1">{it.sourceModule}</Badge>
                      )}
                      {' '}
                      <span className="mono">{it.minutesAllocated} min</span>
                    </div>
                  )}
                </li>
              ))}
            </ol>
          </Card>

          {/* Applicable forms */}
          <Card title={isAr ? 'النماذج المتاحة' : 'Applicable Forms'}
                Icon={FileText}
                action={<Badge tone="success">{forms.length}</Badge>}>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0,
                         display: 'flex', flexDirection: 'column', gap: 6 }}>
              {forms.map(f => (
                <li key={f.formCode}>
                  <Link to={`/committees/catalog/forms/${encodeURIComponent(f.formCode)}`}
                        style={{
                          display: 'block',
                          padding: '6px 8px',
                          fontSize: 12,
                          color: 'var(--ink-1)',
                          textDecoration: 'none',
                          borderRadius: 'var(--r-sm)',
                          background: 'var(--surface-2)',
                        }}>
                    <div style={{ fontWeight: 500 }}>{isAr ? f.titleAr : f.titleEn}</div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                      <Badge tone="info" className="me-1">{f.formType}</Badge>
                      <span className="mono">v{f.version}</span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
    </Shell>
  );
}
