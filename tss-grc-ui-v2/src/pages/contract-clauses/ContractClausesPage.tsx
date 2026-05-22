import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Stamp, CheckCircle2, Archive, ChevronRight, ChevronDown, BookOpen, AlertTriangle, Files,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listClauses, approveClause, deprecateClause,
  CLAUSE_STATUS_TONE, CLAUSE_RISK_TONE, CLAUSE_NEGOTIABILITY_TONE, CLAUSE_CATEGORY_LABEL,
  type ContractClause, type ClauseCategory,
} from '../../lib/contractClauses';

type TabId = 'all' | 'DATA_PROTECTION' | 'SLA' | 'AUDIT_RIGHTS' | 'EXIT_RIGHTS'
           | 'SECURITY' | 'COMPLIANCE' | 'LIABILITY' | 'SUBPROCESSORS' | 'FORCE_MAJEURE'
           | 'draft' | 'deprecated';

export function ContractClausesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<ContractClause[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listClauses(undefined, undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('cclLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    return {
      total:      rows.length,
      approved:   rows.filter(r => r.status === 'APPROVED').length,
      draft:      rows.filter(r => r.status === 'DRAFT').length,
      deprecated: rows.filter(r => r.status === 'DEPRECATED').length,
      critical:   rows.filter(r => r.riskLevel === 'CRITICAL' && r.status === 'APPROVED').length,
      fixed:      rows.filter(r => r.negotiability === 'FIXED' && r.status === 'APPROVED').length,
    };
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all')        return rows;
    if (tab === 'draft')      return rows.filter(r => r.status === 'DRAFT');
    if (tab === 'deprecated') return rows.filter(r => r.status === 'DEPRECATED');
    return rows.filter(r => r.category === tab as ClauseCategory);
  }, [rows, tab]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',             label: `${t('cclTabAll')} (${rows.length})` },
    { id: 'DATA_PROTECTION', label: `${t('cclCat_DATA_PROTECTION')} (${rows.filter(r => r.category === 'DATA_PROTECTION').length})` },
    { id: 'SLA',             label: `${t('cclCat_SLA')} (${rows.filter(r => r.category === 'SLA').length})` },
    { id: 'AUDIT_RIGHTS',    label: `${t('cclCat_AUDIT_RIGHTS')} (${rows.filter(r => r.category === 'AUDIT_RIGHTS').length})` },
    { id: 'EXIT_RIGHTS',     label: `${t('cclCat_EXIT_RIGHTS')} (${rows.filter(r => r.category === 'EXIT_RIGHTS').length})` },
    { id: 'SECURITY',        label: `${t('cclCat_SECURITY')} (${rows.filter(r => r.category === 'SECURITY').length})` },
    { id: 'COMPLIANCE',      label: `${t('cclCat_COMPLIANCE')} (${rows.filter(r => r.category === 'COMPLIANCE').length})` },
    { id: 'LIABILITY',       label: `${t('cclCat_LIABILITY')} (${rows.filter(r => r.category === 'LIABILITY').length})` },
    { id: 'SUBPROCESSORS',   label: `${t('cclCat_SUBPROCESSORS')} (${rows.filter(r => r.category === 'SUBPROCESSORS').length})` },
    { id: 'FORCE_MAJEURE',   label: `${t('cclCat_FORCE_MAJEURE')} (${rows.filter(r => r.category === 'FORCE_MAJEURE').length})` },
    { id: 'draft',           label: `${t('cclTabDraft')} (${stats.draft})` },
    { id: 'deprecated',      label: `${t('cclTabDeprecated')} (${stats.deprecated})` },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  async function onApprove(id: number) {
    setBusyId(id);
    try { await approveClause(id); toast.success(t('cclApproved')); await reload(); }
    catch { toast.danger(t('cclActionFailed')); }
    finally { setBusyId(null); }
  }

  async function onDeprecate(id: number) {
    const reason = window.prompt(t('cclDeprecatePrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await deprecateClause(id, reason.trim()); toast.success(t('cclDeprecated')); await reload(); }
    catch { toast.danger(t('cclActionFailed')); }
    finally { setBusyId(null); }
  }

  return (
    <Shell title={t('cclPageTitle')} sub={t('cclPageSub')}>
      <PageHeader eyebrow={t('navThirdParty')} title={t('cclPageTitle')} sub={t('cclPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={BookOpen}      label={t('cclKpiApproved')}   value={stats.approved} tone="success" />
        <Kpi Icon={Files}         label={t('cclKpiDraft')}      value={stats.draft}    tone="warning" />
        <Kpi Icon={AlertTriangle} label={t('cclKpiCritical')}   value={stats.critical} tone="danger" />
        <Kpi Icon={Stamp}         label={t('cclKpiFixed')}      value={stats.fixed}    tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('cclPageTitle')} Icon={BookOpen}>
        {loading
          ? <EmptyState Icon={BookOpen} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={BookOpen} title={t('cclEmpty')} sub={t('cclEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('cclColCode')}</th>
                    <th>{t('cclColTitle')}</th>
                    <th>{t('cclColCategory')}</th>
                    <th>{t('cclColRisk')}</th>
                    <th>{t('cclColNegotiability')}</th>
                    <th>{t('cclColVersion')}</th>
                    <th>{t('scnColStatus')}</th>
                    <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.id);
                    return (
                      <Fragment key={r.id}>
                        <tr>
                          <td>
                            <button onClick={() => toggle(r.id)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td className="mono">{r.clauseCode}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td>{t(CLAUSE_CATEGORY_LABEL[r.category])}</td>
                          <td><Badge tone={CLAUSE_RISK_TONE[r.riskLevel]}>{r.riskLevel}</Badge></td>
                          <td><Badge tone={CLAUSE_NEGOTIABILITY_TONE[r.negotiability]}>{r.negotiability}</Badge></td>
                          <td className="mono">v{r.version}</td>
                          <td><Badge tone={CLAUSE_STATUS_TONE[r.status]}>{statusLabel(r.status, lang)}</Badge></td>
                          <td style={{ textAlign: 'right' }}>
                            {r.status === 'DRAFT' && (
                              <Button size="sm" variant="primary" Icon={CheckCircle2}
                                disabled={busyId === r.id} onClick={() => onApprove(r.id)}>
                                {t('scnApprove')}
                              </Button>
                            )}
                            {(r.status === 'APPROVED' || r.status === 'DRAFT') && (
                              <>{' '}
                                <Button size="sm" variant="ghost" Icon={Archive}
                                  disabled={busyId === r.id} onClick={() => onDeprecate(r.id)}>
                                  {t('cclDeprecate')}
                                </Button>
                              </>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={9} style={{ background: 'var(--muted)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {r.applicableTo && (
                                  <p>
                                    <strong>{t('cclApplicableTo')}:</strong>{' '}
                                    <span className="mono">{r.applicableTo}</span>
                                  </p>
                                )}
                                <p style={{ marginTop: 6 }}>
                                  <strong>{t('cclBody')}:</strong>
                                </p>
                                <p style={{
                                  whiteSpace: 'pre-wrap', background: 'var(--card)',
                                  padding: 8, borderRadius: 4, fontSize: 12,
                                }}>
                                  {lang === 'ar' && r.bodyAr ? r.bodyAr : r.bodyEn}
                                </p>
                                {r.guidanceEn && (
                                  <p style={{ marginTop: 6 }}>
                                    <strong>{t('cclGuidance')}:</strong>{' '}
                                    {lang === 'ar' && r.guidanceAr ? r.guidanceAr : r.guidanceEn}
                                  </p>
                                )}
                                {r.regulatoryRefs && (
                                  <p style={{ marginTop: 6 }}>
                                    <strong>{t('cclRegRefs')}:</strong>{' '}
                                    <span className="mono">{r.regulatoryRefs}</span>
                                  </p>
                                )}
                                {r.owner && (
                                  <p style={{ marginTop: 6, color: 'var(--muted-foreground)' }}>
                                    <strong>{t('cclOwner')}:</strong> {r.owner}
                                    {r.tags && <>{' · '}<span className="mono">{r.tags}</span></>}
                                  </p>
                                )}
                                {r.deprecationReason && (
                                  <p style={{ marginTop: 6, color: 'var(--danger)' }}>
                                    <strong>{t('cclDeprecationReason')}:</strong> {r.deprecationReason}
                                  </p>
                                )}
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            )
        }
      </Card>
    </Shell>
  );
}
