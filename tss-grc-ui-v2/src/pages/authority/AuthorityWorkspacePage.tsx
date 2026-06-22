import { useMemo } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import {
  Landmark, FileText, AlertTriangle, ClipboardCheck, Briefcase,
  ArrowLeft, ExternalLink, BookCopy, ScrollText, Layers, Megaphone, ListChecks,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Donut } from '../../components/viz/Donut';
import { CoverageMatrix } from '../../components/viz/CoverageMatrix';
import { findAuthority } from '../../lib/authorities';
import {
  RISK_ANCHORS, CYBER_ANCHORS, PRIVACY_ANCHORS, BCM_ANCHORS,
  TPRM_ANCHORS, SCM_ANCHORS, ETHICS_ANCHORS, INCIDENT_ANCHORS,
  CHANGE_ANCHORS,
} from '../../lib/moduleAnchors';
import {
  STATUS_LABEL, type RegulatorAnchor, type ControlStatus,
} from '../../lib/evidenceAnchors';
import { EVIDENCE, EVIDENCE_ANCHORS_BY_ID } from '../evidence/mockEvidence';
import { COMPLIANCE_FINDINGS, AUDIT_ENGAGEMENTS, REGULATORY_CHANGES } from '../compliance/mockCompliance';
import {
  AUTHORITY_OBLIGATIONS_BY_CODE, STATUS_LABEL_OBLIGATION, STATUS_TONE_OBLIGATION,
} from '../../lib/authorityObligations';
import { useI18n } from '../../lib/i18n';
import { NitaqatPanel } from '../../components/compliance/NitaqatPanel';
import { HrsdProcessInitiation } from '../../components/compliance/HrsdProcessInitiation';
import { MocPanel } from '../../components/compliance/MocPanel';
import { MocProcessInitiation } from '../../components/compliance/MocProcessInitiation';

/**
 * Authority Workspace — industry-standard GRC tooling "Authority Document workspace"
 * pattern. The auditor's home page for a single regulator.
 *
 * Aggregates everything that touches this authority across the platform:
 *   - frameworks owned by it
 *   - anchored evidence + their per-authority control status
 *   - findings tagged to its frameworks
 *   - audit engagements scoped to it
 *   - citation library
 */
export function AuthorityWorkspacePage() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const { t, lang } = useI18n();

  const authority = code ? findAuthority(code) : undefined;
  if (!authority) {
    return (
      <Shell title={t('authwsTitle')}>
        <EmptyState Icon={Landmark} title={t('authwsAuthorityNotFound')}
          sub="NCA, SAMA, SDAIA, NRC, ISO, …" />
      </Shell>
    );
  }

  // ── Aggregation ────────────────────────────────────────────────────────
  const allAnchors: RegulatorAnchor[] = useMemo(() => [
    ...RISK_ANCHORS, ...CYBER_ANCHORS, ...PRIVACY_ANCHORS,
    ...BCM_ANCHORS,  ...TPRM_ANCHORS,  ...SCM_ANCHORS,
    ...ETHICS_ANCHORS, ...INCIDENT_ANCHORS, ...CHANGE_ANCHORS,
    ...Object.values(EVIDENCE_ANCHORS_BY_ID).flat(),
  ], []);

  const myAnchors = allAnchors.filter(a => a.authorityCode === authority.code);
  const evidenceForMe = EVIDENCE.filter(e => (EVIDENCE_ANCHORS_BY_ID[e.id] ?? []).some(a => a.authorityCode === authority.code));
  const findingsForMe = COMPLIANCE_FINDINGS.filter(f =>
    authority.frameworkCodes.some(fw => (f.clauseRef ?? '').includes(fw.replace(/_/g, ' '))) ||
    authority.frameworkCodes.includes('NCA_ECC') && (f.clauseRef ?? '').match(/^(AC|IR|PR|SE|VM|\d)/)
  );
  const engagementsForMe = AUDIT_ENGAGEMENTS.filter(e =>
    authority.frameworkCodes.includes((e.framework ?? '').replace(' ', '_').toUpperCase()) ||
    authority.frameworkCodes.includes(e.framework ?? '')
  );

  // Status breakdown for the donut
  const buckets: Record<ControlStatus, number> = {
    IMPLEMENTED: 0, PARTIAL: 0, COMPENSATING: 0, NOT_IMPLEMENTED: 0, NOT_APPLICABLE: 0,
  };
  for (const a of myAnchors) buckets[a.controlStatus] = (buckets[a.controlStatus] ?? 0) + 1;
  const totalCites = myAnchors.length || 1;
  const coveragePct = Math.round(((buckets.IMPLEMENTED + buckets.COMPENSATING + buckets.PARTIAL * 0.5) / totalCites) * 100);

  // Citation library (deduped)
  const citationLib = dedupAnchorsByControl(myAnchors);

  // Per-framework breakdown
  const perFramework = new Map<string, { count: number; implemented: number }>();
  for (const a of myAnchors) {
    const cur = perFramework.get(a.frameworkCode) ?? { count: 0, implemented: 0 };
    cur.count += 1;
    if (a.controlStatus === 'IMPLEMENTED') cur.implemented += 1;
    perFramework.set(a.frameworkCode, cur);
  }

  // Regulatory updates published by this authority
  const regulatoryUpdates = useMemo(
    () => REGULATORY_CHANGES
      .filter(r => r.sourceAuthority === authority.code)
      .sort((a, b) => (b.publishedDate ?? '').localeCompare(a.publishedDate ?? '')),
    [authority.code],
  );

  // Obligations the org must fulfil
  const obligations = AUTHORITY_OBLIGATIONS_BY_CODE[authority.code] ?? [];
  const obligationStats = {
    compliant: obligations.filter(o => o.internalStatus === 'COMPLIANT').length,
    inProgress: obligations.filter(o => o.internalStatus === 'IN_PROGRESS').length,
    gap: obligations.filter(o => o.internalStatus === 'GAP').length,
  };

  return (
    <Shell title={`Authority · ${authority.code}`} sub={authority.taglineEn}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 16 }}>
        <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/authorities')} aria-label="Back" />
        <div style={{
          width: 56, height: 56, flexShrink: 0,
          borderRadius: 'var(--r-md)',
          background: authority.colorToken,
          color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontWeight: 800, fontSize: 18,
        }}>
          {authority.code.slice(0, 4)}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>
            {authority.authorityType.replace(/_/g, ' ')}
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap', marginTop: 2 }}>
            <h1 style={{ fontSize: 22, fontWeight: 800, color: 'var(--ink-1)', letterSpacing: 'var(--tracking-tight)', margin: 0 }}>
              {lang === 'ar' ? authority.nameAr : authority.nameEn}
            </h1>
            <Badge tone="primary">{authority.code}</Badge>
            <Badge tone="info">{authority.jurisdiction}</Badge>
            {authority.domains.slice(0, 4).map(d => <Badge key={d} tone="neutral">{d}</Badge>)}
          </div>
          <div style={{ fontSize: 13, color: 'var(--ink-2)', marginTop: 4 }}>
            {lang === 'ar' && authority.taglineAr ? authority.taglineAr : authority.taglineEn}
          </div>
        </div>
        {authority.website && (
          <a href={authority.website} target="_blank" rel="noopener noreferrer"
             style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--primary-700)' }}>
            <ExternalLink size={12} /> {authority.website.replace(/^https?:\/\//, '')}
          </a>
        )}
      </div>

      {/* KPI strip */}
      <div className="kpi-grid">
        <Kpi Icon={BookCopy}        label={t('authKpiFrameworks')}  value={authority.frameworkCodes.length}                          tone="primary" to="/regulatory/library" />
        <Kpi Icon={ClipboardCheck}  label={t('authKpiCited')}       value={myAnchors.length}                                         tone="info" to="/controls/library" />
        <Kpi Icon={FileText}        label={t('authKpiEvidence')}    value={evidenceForMe.length}                                     tone="success" to="/evidence" />
        <Kpi Icon={AlertTriangle}   label={t('authKpiFindings')}    value={findingsForMe.filter(f => f.status !== 'CLOSED').length}  tone="danger" onClick={() => document.getElementById('authws-findings-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })} />
        <Kpi Icon={Briefcase}       label={t('authKpiEngagements')} value={engagementsForMe.length}                                  tone="accent" to="/audit-workspace" />
      </div>

      {/* DD-83 + DD-88: HRSD-specific surface — process-initiation tiles
          first (action-oriented), then the Nitaqat dashboard + requirements
          (informational). Non-HRSD authorities skip this entire block. */}
      {authority.code === 'HRSD' && (
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <HrsdProcessInitiation />
          <NitaqatPanel />
        </div>
      )}

      {/* DD-85: MOC-specific surface — Tasattur Risk Index + Consumer
          Protection + Commercial Registry obligations. */}
      {authority.code === 'MOC' && (
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* DD-94: MOC process-initiation card on top — launchable workflows */}
          <MocProcessInitiation />
          <MocPanel />
        </div>
      )}

      {/* Coverage donut + per-framework breakdown */}
      <div className="dash-grid" style={{ marginTop: 12 }}>
        <Card title={t('authwsCoverage')} Icon={Landmark}
          action={<Badge tone={coveragePct >= 80 ? 'success' : coveragePct >= 60 ? 'primary' : coveragePct >= 40 ? 'warning' : 'danger'}>{coveragePct}%</Badge>}>
          <Donut
            segments={[
              { label: STATUS_LABEL.IMPLEMENTED,     value: buckets.IMPLEMENTED,     color: 'var(--success)' },
              { label: STATUS_LABEL.COMPENSATING,    value: buckets.COMPENSATING,    color: 'var(--accent-700)' },
              { label: STATUS_LABEL.PARTIAL,         value: buckets.PARTIAL,         color: 'var(--cat-2)' },
              { label: STATUS_LABEL.NOT_IMPLEMENTED, value: buckets.NOT_IMPLEMENTED, color: 'var(--danger)' },
              { label: STATUS_LABEL.NOT_APPLICABLE,  value: buckets.NOT_APPLICABLE,  color: 'var(--ink-4)' },
            ].filter(s => s.value > 0)}
            centerLabel={`${coveragePct}%`} centerSub="Coverage" />
        </Card>

        <Card title={t('authwsFrameworks')} Icon={BookCopy} action={<Badge tone="primary">{authority.frameworkCodes.length}</Badge>}>
          {authority.frameworkCodes.length === 0
            ? <EmptyState Icon={BookCopy} title={t('authwsFrameworksEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {authority.frameworkCodes.map(fw => {
                  const stats = perFramework.get(fw) ?? { count: 0, implemented: 0 };
                  const pct = stats.count > 0 ? Math.round((stats.implemented / stats.count) * 100) : 0;
                  return (
                    <div key={fw} style={{ display: 'grid', gridTemplateColumns: '1fr 80px 60px', gap: 10, alignItems: 'center' }}>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--ink-1)' }}>
                          {fw.replace(/_/g, ' ')}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                          {stats.count} cited control{stats.count !== 1 ? 's' : ''}
                        </div>
                      </div>
                      <div style={{ height: 6, background: 'var(--surface-3)', borderRadius: 'var(--r-pill)', overflow: 'hidden' }}>
                        <div style={{
                          width: `${pct}%`, height: '100%',
                          background: pct >= 80 ? 'var(--success)' : pct >= 60 ? 'var(--primary)' : pct >= 40 ? 'var(--accent)' : 'var(--danger)',
                        }} />
                      </div>
                      <span className="mono" style={{ textAlign: 'end', fontSize: 12, fontWeight: 700 }}>{pct}%</span>
                    </div>
                  );
                })}
              </div>}
        </Card>
      </div>

      {/* Framework × Status heatmap */}
      <Card title="Framework × Status matrix" Icon={Layers}>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 8 }}>
          Cell value = number of cited controls in that framework with that status. Cells shaded by status colour, intensity proportional to count.
        </div>
        <CoverageMatrix
          rowHeaderLabel="Framework"
          columnLabels={['Implemented', 'Partial', 'Compensating', 'Not implemented', 'N/A']}
          rows={authority.frameworkCodes.map(fw => {
            const fwAnchors = myAnchors.filter(a => a.frameworkCode === fw);
            return {
              label: fw.replace(/_/g, ' '),
              sublabel: `${fwAnchors.length} cited control${fwAnchors.length !== 1 ? 's' : ''}`,
              cells: [
                { tone: 'success', value: fwAnchors.filter(a => a.controlStatus === 'IMPLEMENTED').length },
                { tone: 'warning', value: fwAnchors.filter(a => a.controlStatus === 'PARTIAL').length },
                { tone: 'accent',  value: fwAnchors.filter(a => a.controlStatus === 'COMPENSATING').length },
                { tone: 'danger',  value: fwAnchors.filter(a => a.controlStatus === 'NOT_IMPLEMENTED').length },
                { tone: 'neutral', value: fwAnchors.filter(a => a.controlStatus === 'NOT_APPLICABLE').length },
              ],
            };
          })}
        />
      </Card>

      {/* Regulatory updates lane */}
      <Card title={`Regulatory updates from ${authority.code}`} Icon={Megaphone}
        action={<Badge tone={regulatoryUpdates.filter(r => r.status !== 'CLOSED').length > 0 ? 'warning' : 'success'}>
          {regulatoryUpdates.filter(r => r.status !== 'CLOSED').length} active
        </Badge>}>
        {regulatoryUpdates.length === 0
          ? <EmptyState Icon={Megaphone} title={t('authwsRegUpdatesEmpty')}
              sub={authority.code} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {regulatoryUpdates.map(r => (
                <div key={r.id} style={{
                  display: 'grid',
                  gridTemplateColumns: '160px 1fr auto auto auto',
                  gap: 12, alignItems: 'center',
                  padding: '8px 10px',
                  border: '1px solid var(--border)',
                  borderRadius: 'var(--r-sm)',
                  background: 'var(--surface-1)',
                }}>
                  <div>
                    <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                      {r.publishedDate ?? '—'}
                    </div>
                    <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 2 }}>
                      eff. {r.effectiveDate ?? '—'}
                    </div>
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                      {lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                      {r.citation ?? '—'} · {r.summaryEn ?? ''}
                    </div>
                    {r.impactSummary && (
                      <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, fontStyle: 'italic' }}>
                        Impact: {r.impactSummary}
                      </div>
                    )}
                  </div>
                  <Badge tone={r.applicability === 'IN_SCOPE' ? 'primary' : r.applicability === 'OUT_OF_SCOPE' ? 'neutral' : 'warning'}>
                    {(r.applicability ?? '').replace(/_/g, ' ')}
                  </Badge>
                  <Badge tone={r.impactLevel === 'HIGH' ? 'danger' : r.impactLevel === 'MEDIUM' ? 'warning' : r.impactLevel === 'LOW' ? 'success' : 'neutral'}>
                    {r.impactLevel} impact
                  </Badge>
                  <Badge tone={r.status === 'CLOSED' ? 'success' : r.status === 'CONTROLS_UPDATED' ? 'success' : r.status === 'IMPACT_ASSESSED' ? 'info' : 'warning'}>
                    {(r.status ?? '').replace(/_/g, ' ')}
                  </Badge>
                </div>
              ))}
            </div>}
      </Card>

      {/* Obligations lane */}
      <Card title={`Obligations under ${authority.code}`} Icon={ListChecks}
        action={<div style={{ display: 'inline-flex', gap: 6 }}>
          <Badge tone="success">{obligationStats.compliant} compliant</Badge>
          <Badge tone="info">{obligationStats.inProgress} in progress</Badge>
          {obligationStats.gap > 0 && <Badge tone="danger">{obligationStats.gap} gap</Badge>}
        </div>}>
        {obligations.length === 0
          ? <EmptyState Icon={ListChecks} title={t('authwsObligationsEmpty')}
              sub={authority.code} />
          : <div style={{ display: 'flex', flexDirection: 'column' }}>
              {obligations.map(o => (
                <div key={o.code} style={{
                  display: 'grid',
                  gridTemplateColumns: '180px 1fr 110px 130px 110px',
                  gap: 10, alignItems: 'center',
                  padding: '8px 0',
                  borderBottom: '1px dashed var(--border)',
                }}>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{o.code}</span>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>
                      {lang === 'ar' ? o.titleAr : o.titleEn}
                    </div>
                    {o.noteEn && (
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>{o.noteEn}</div>
                    )}
                  </div>
                  <Badge tone="primary">{o.frameworkCode.replace(/_/g, ' ')}</Badge>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {o.internalRef ?? '—'}
                  </span>
                  <Badge tone={STATUS_TONE_OBLIGATION[o.internalStatus]} dot>
                    {STATUS_LABEL_OBLIGATION[o.internalStatus]}
                  </Badge>
                </div>
              ))}
            </div>}
      </Card>

      {/* Citation library */}
      <Card title={t('authwsCitationLib')} Icon={ScrollText} action={<Badge tone="primary">{citationLib.length}</Badge>}>
        {citationLib.length === 0
          ? <EmptyState Icon={ScrollText} title={t('authwsCitationsEmpty')} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {citationLib.map((a, i) => (
                <div key={i} style={{
                  display: 'grid',
                  gridTemplateColumns: '120px 80px 1fr 110px',
                  gap: 10, alignItems: 'center',
                  padding: '6px 8px',
                  borderBottom: '1px dashed var(--border)',
                  fontSize: 12,
                }}>
                  <span className="mono" style={{ color: 'var(--ink-3)' }}>{a.frameworkCode.replace(/_/g, ' ')}</span>
                  <span className="mono" style={{ fontWeight: 700 }}>{a.controlRef}</span>
                  <span style={{ color: 'var(--ink-2)' }}>{a.citationText ?? <em style={{ color: 'var(--ink-4)' }}>no verbatim text</em>}</span>
                  <Badge tone={STATUS_BADGE_TONE[a.controlStatus]}>{STATUS_LABEL[a.controlStatus]}</Badge>
                </div>
              ))}
            </div>
        }
      </Card>

      {/* Anchored evidence */}
      <Card title={t('authwsAnchoredEvidence')} Icon={FileText} action={<Badge tone="primary">{evidenceForMe.length}</Badge>} padded={false}>
        {evidenceForMe.length === 0
          ? <EmptyState Icon={FileText} title={t('authwsAnchoredEmpty')} />
          : <DataTable
              columns={[
                { header: 'Code',     cell: r => r.evidenceCode, mono: true },
                { header: 'Title',    cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
                { header: 'Type',     cell: r => <Badge tone="neutral">{(r.evidenceType ?? '').replace(/_/g, ' ')}</Badge> },
                { header: 'Citation', cell: r => {
                  const my = (EVIDENCE_ANCHORS_BY_ID[r.id] ?? []).find(a => a.authorityCode === authority.code);
                  return my ? <span className="mono" style={{ fontSize: 11 }}>{my.frameworkCode.replace(/_/g, ' ')} · {my.controlRef}</span> : '—';
                } },
                { header: 'Status',   cell: r => {
                  const my = (EVIDENCE_ANCHORS_BY_ID[r.id] ?? []).find(a => a.authorityCode === authority.code);
                  return my ? <Badge tone={STATUS_BADGE_TONE[my.controlStatus]} dot>{STATUS_LABEL[my.controlStatus]}</Badge> : '—';
                } },
                { header: 'Expiry',   cell: r => <span className="mono">{r.expiryDate ?? '—'}</span> },
              ] as Column<typeof EVIDENCE[number]>[]}
              rows={evidenceForMe}
              rowHref={() => '/evidence'} />
        }
      </Card>

      {/* Findings + Engagements */}
      <div id="authws-findings-section" className="dash-grid" style={{ marginTop: 12 }}>
        <Card title={t('authwsFindings')} Icon={AlertTriangle}
          action={<Badge tone="danger">{findingsForMe.filter(f => f.status !== 'CLOSED').length} {t('audwsAgingCenterSub')}</Badge>}>
          {findingsForMe.length === 0
            ? <EmptyState Icon={AlertTriangle} title={t('authwsFindingsEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {findingsForMe.slice(0, 8).map(f => (
                  <Link key={f.id} to={`/findings/${f.id}`} style={{ textDecoration: 'none', color: 'inherit',
                    padding: '6px 8px', borderRadius: 'var(--r-sm)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
                    transition: 'background var(--dur-fast) var(--ease-standard)',
                    background: 'transparent',
                  }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = 'var(--surface-2)'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {f.title}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 1 }}>
                        {f.owner ?? '—'} · due {f.dueDate ?? '—'}
                      </div>
                    </div>
                    <Badge tone={f.severity === 'CRITICAL' ? 'danger' : f.severity === 'HIGH' ? 'warning' : 'info'}>{f.severity}</Badge>
                    <Badge tone={f.status === 'CLOSED' ? 'success' : 'warning'}>{f.status}</Badge>
                  </Link>
                ))}
              </div>}
        </Card>

        <Card title={t('authwsEngagements')} Icon={Briefcase}
          action={<Badge tone="primary">{engagementsForMe.length}</Badge>}>
          {engagementsForMe.length === 0
            ? <EmptyState Icon={Briefcase} title={t('authwsEngagementsEmpty')} />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {engagementsForMe.slice(0, 8).map(e => (
                  <div key={e.id} style={{
                    padding: '6px 8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
                    borderBottom: '1px dashed var(--border)',
                  }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {e.titleEn}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {e.auditorFirm ?? '—'} · {e.leadAuditor ?? '—'}
                      </div>
                    </div>
                    <Badge tone="info">{e.auditType.replace(/_/g, ' ')}</Badge>
                    <Badge tone={e.status === 'CLOSED' ? 'success' : 'warning'}>{e.status}</Badge>
                  </div>
                ))}
              </div>}
        </Card>
      </div>

      <div style={{ marginTop: 12, fontSize: 11, color: 'var(--ink-3)' }}>
        {t('riskLoading') !== 'Loading risks…' /* keep ts happy */ ? '' : ''}
        Authority Workspace aggregates every cited control, anchored evidence, finding and engagement that touches this regulator across the platform.
      </div>
    </Shell>
  );
}

const STATUS_BADGE_TONE: Record<ControlStatus, 'success' | 'warning' | 'accent' | 'danger' | 'neutral'> = {
  IMPLEMENTED: 'success', PARTIAL: 'warning', COMPENSATING: 'accent',
  NOT_IMPLEMENTED: 'danger', NOT_APPLICABLE: 'neutral',
};

function dedupAnchorsByControl(anchors: RegulatorAnchor[]): RegulatorAnchor[] {
  const seen = new Set<string>();
  const out: RegulatorAnchor[] = [];
  for (const a of anchors) {
    const key = `${a.frameworkCode}::${a.controlRef}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(a);
  }
  return out.sort((a, b) => a.frameworkCode.localeCompare(b.frameworkCode) || a.controlRef.localeCompare(b.controlRef));
}
