import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { BookOpen, AlertTriangle, Edit3, Trash2, Plus, CheckCircle2, Globe } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  helpApi, HELP_MODULE_LABEL, HELP_SEVERITY_LABEL, KNOWN_ROUTES,
  type HelpTopicResponse,
} from '../../lib/help';

/**
 * Admin — help topics CRUD + coverage-gap widget.
 *
 * <p>The coverage-gap widget walks {@code KNOWN_ROUTES} and reports
 * which routes have zero help topics attached. This surfaces the
 * pages where users will hit "No help for this page yet" and gives
 * the content team a prioritised authoring backlog.
 *
 * <p>Auth: gated by {@code KB:WRITE} at the backend — the same permission that guards KB-article
 * authoring, held by GRC_ADMINISTRATOR, ORG_ADMIN and COMPLIANCE_OFFICER. The page is reachable
 * from /admin/help-topics and renders a friendly forbidden state when the API returns 403.
 *
 * <p>⚠ This comment used to say {@code PLATFORM_ADMIN} or {@code CONTENT_EDITOR}. F2-52:
 * CONTENT_EDITOR exists in no database, so the authoring surface was the platform administrator's
 * alone — including for the compliance officer this page was built for, who could list every topic
 * and save none. Deletion remains PLATFORM_ADMIN-only.
 */
export function HelpTopicsAdminPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [topics, setTopics] = useState<HelpTopicResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [moduleFilter, setModuleFilter] = useState<string>('ALL');

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      const rows = await helpApi.listAll();
      setTopics(rows);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) {
        setError(t('helpAdminForbidden', 'You do not have permission to view this page.'));
      } else {
        setError(e instanceof Error ? e.message : 'Failed to load');
      }
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Coverage gaps ─────────────────────────────────────────────
  // A route is "covered" if at least one topic's routePattern matches
  // it. We approximate the matching client-side using a wildcard-aware
  // glob check; the server-side service is the source of truth but
  // re-implementing the rule here keeps the widget responsive.
  const coverage = useMemo(() => {
    const patterns = topics.map(t => t.routePattern);
    function matches(pattern: string, route: string): boolean {
      const p = pattern.toLowerCase();
      const r = route.toLowerCase();
      if (p === r) return true;
      if (!p.includes('*')) return false;
      // translate pattern to regex (`*` -> [^/]+)
      let regex = '^';
      for (const c of p) {
        if (c === '*') regex += '[^/]+';
        else if ('\\.[]{}()+?^$|'.includes(c)) regex += '\\' + c;
        else regex += c;
      }
      regex += '$';
      try { return new RegExp(regex).test(r); } catch { return false; }
    }
    const covered: string[] = [];
    const uncovered: string[] = [];
    for (const route of KNOWN_ROUTES) {
      const hit = patterns.some(p => matches(p, route));
      (hit ? covered : uncovered).push(route);
    }
    return { covered, uncovered };
  }, [topics]);

  // ─── Filtered + sorted topic list ──────────────────────────────
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return topics.filter(t => {
      if (moduleFilter !== 'ALL' && t.module !== moduleFilter) return false;
      if (q.length === 0) return true;
      return (
        t.topicCode.toLowerCase().includes(q) ||
        t.titleEn.toLowerCase().includes(q) ||
        t.titleAr.toLowerCase().includes(q) ||
        t.routePattern.toLowerCase().includes(q)
      );
    });
  }, [topics, search, moduleFilter]);

  async function remove(topic: HelpTopicResponse) {
    if (!confirm(isAr ? `حذف الموضوع ${topic.topicCode}؟` : `Delete topic ${topic.topicCode}?`)) return;
    try {
      await helpApi.remove(topic.externalId);
      toast.success(isAr ? 'تم الحذف' : 'Topic deleted');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  const moduleOptions = ['ALL', ...new Set(topics.map(t => t.module))];

  return (
    <Shell
      title={t('helpAdminPageTitle', 'Help topics admin')}
      sub={t('helpAdminPageSub', 'Curate the contextual help shown in the floating drawer')}
    >
      <PageHeader
        title={t('helpAdminPageTitle', 'Help topics admin')}
        sub={t('helpAdminPageSub', 'Curate the contextual help shown in the floating drawer')}
        actions={
          <Button
            variant="primary"
            Icon={Plus}
            onClick={() => toast.info(isAr ? 'محرّر الإنشاء قيد التنفيذ' : 'Authoring editor coming next iteration')}>
            {t('helpAdminNew', 'New topic')}
          </Button>
        }
      />

      {error && (
        <Card>
          <div style={{
            padding: 12, color: 'var(--danger, #ef4444)',
            display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <AlertTriangle size={16} />
            {error}
          </div>
        </Card>
      )}

      {/* KPIs */}
      <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', marginBottom: 16 }}>
        <Kpi Icon={BookOpen}      label={t('helpAdminKpiTopics', 'Total topics')}      value={topics.length} to="/admin" />
        <Kpi Icon={CheckCircle2}  label={t('helpAdminKpiCovered', 'Routes covered')}   value={coverage.covered.length}   tone="success" to="/admin" />
        <Kpi Icon={AlertTriangle} label={t('helpAdminKpiGaps', 'Coverage gaps')}       value={coverage.uncovered.length} tone={coverage.uncovered.length > 0 ? 'warning' : 'success'} to="/admin" />
        <Kpi Icon={Globe}         label={t('helpAdminKpiKnownRoutes', 'Known routes')} value={KNOWN_ROUTES.length} to="/admin" />
      </div>

      {/* Coverage-gap widget */}
      {coverage.uncovered.length > 0 && (
        <Card>
          <div style={{ padding: 14 }}>
            <h3 style={{ fontSize: 14, fontWeight: 700, margin: 0, marginBottom: 8 }}>
              {t('helpAdminGapsTitle', 'Routes without a help topic')}
            </h3>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 10 }}>
              {t('helpAdminGapsSub', 'These pages exist in the app but no help topic targets them. Authoring backlog.')}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {coverage.uncovered.map(r => (
                <span key={r} className="mono" style={{
                  fontSize: 11, padding: '3px 8px',
                  background: 'var(--warning-50, #fefce8)',
                  border: '1px solid var(--warning, #f59e0b)',
                  borderRadius: 4, color: 'var(--warning-700, #92400e)',
                }}>
                  {r}
                </span>
              ))}
            </div>
          </div>
        </Card>
      )}

      {/* Filters */}
      <Card>
        <div style={{ padding: 12, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder={t('helpAdminSearchPlaceholder', 'Search by code, title or route')}
            style={{
              flex: '1 1 240px', minWidth: 0, padding: '8px 12px',
              border: '1px solid var(--border)', borderRadius: 6,
              fontSize: 13, background: 'var(--surface-1)',
            }}
          />
          <select
            value={moduleFilter}
            onChange={e => setModuleFilter(e.target.value)}
            style={{
              padding: '8px 12px', border: '1px solid var(--border)',
              borderRadius: 6, fontSize: 13, background: 'var(--surface-1)',
            }}
          >
            {moduleOptions.map(m => (
              <option key={m} value={m}>
                {m === 'ALL'
                  ? (isAr ? 'كل الوحدات' : 'All modules')
                  : HELP_MODULE_LABEL[m as keyof typeof HELP_MODULE_LABEL]?.[lang] ?? m}
              </option>
            ))}
          </select>
        </div>
      </Card>

      {/* Topics table */}
      <Card>
        {loading && (
          <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {t('helpAdminLoading', 'Loading…')}
          </div>
        )}
        {!loading && filtered.length === 0 && (
          <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {t('helpAdminEmpty', 'No topics found.')}
          </div>
        )}
        {!loading && filtered.length > 0 && (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead>
              <tr style={{ background: 'var(--surface-2)' }}>
                <th style={thStyle}>{t('helpAdminColCode', 'Code')}</th>
                <th style={thStyle}>{t('helpAdminColTitle', 'Title')}</th>
                <th style={thStyle}>{t('helpAdminColRoute', 'Route')}</th>
                <th style={thStyle}>{t('helpAdminColModule', 'Module')}</th>
                <th style={thStyle}>{t('helpAdminColSeverity', 'Severity')}</th>
                <th style={thStyle}>{t('helpAdminColScope', 'Scope')}</th>
                <th style={thStyle}></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(topic => (
                <tr key={topic.id} style={{ borderTop: '1px solid var(--border)' }}>
                  <td style={tdStyle}>
                    <span className="mono" style={{ fontSize: 11 }}>{topic.topicCode}</span>
                  </td>
                  <td style={tdStyle}>
                    {isAr ? topic.titleAr : topic.titleEn}
                  </td>
                  <td style={tdStyle}>
                    <span className="mono" style={{ fontSize: 11 }}>{topic.routePattern}</span>
                  </td>
                  <td style={tdStyle}>
                    <Badge tone="info">{HELP_MODULE_LABEL[topic.module]?.[lang] ?? topic.module}</Badge>
                  </td>
                  <td style={tdStyle}>
                    <Badge tone={topic.severity === 'WARNING' ? 'warning' : topic.severity === 'TIP' ? 'success' : 'info'}>
                      {HELP_SEVERITY_LABEL[topic.severity]?.[lang] ?? topic.severity}
                    </Badge>
                  </td>
                  <td style={tdStyle}>
                    {topic.organisationId == null
                      ? <Badge tone="neutral">{isAr ? 'عام' : 'Global'}</Badge>
                      : <Badge tone="info">{isAr ? 'مؤسّسي' : 'Tenant'}</Badge>}
                  </td>
                  <td style={tdStyle}>
                    <div style={{ display: 'inline-flex', gap: 4 }}>
                      <button
                        onClick={() => toast.info(isAr ? 'محرّر التعديل قيد التنفيذ' : 'Edit coming next iteration')}
                        title={t('helpAdminEdit', 'Edit')}
                        style={iconBtn}>
                        <Edit3 size={14} />
                      </button>
                      {topic.organisationId != null && (
                        <button
                          onClick={() => void remove(topic)}
                          title={t('helpAdminDelete', 'Delete')}
                          style={{ ...iconBtn, color: 'var(--danger, #ef4444)' }}>
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}

const thStyle: CSSProperties = {
  padding: '10px 12px', textAlign: 'start',
  fontSize: 11, fontWeight: 700,
  color: 'var(--ink-2)', textTransform: 'uppercase',
  letterSpacing: '0.05em',
};
const tdStyle: CSSProperties = { padding: '10px 12px', verticalAlign: 'middle' };
const iconBtn: CSSProperties = {
  border: 'none', background: 'transparent', cursor: 'pointer',
  padding: 4, color: 'var(--ink-2)',
};
