import { useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, Printer, Activity, AlertOctagon, ClipboardCheck, Stamp,
  Fingerprint, Server, GitBranch, Landmark, FileText, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { buildBoardPack } from '../../lib/boardPack';
import { APPROVER_REGISTRY } from '../../lib/approvals';
import { formatHijri } from '../../lib/sla';
import { appetiteSeverityLabel } from '../../lib/riskAppetiteSaudi';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import './boardPack.css';

/**
 * BoardPackPage — Sprint 5 Executive Board Pack export.
 *
 * Print-styled, bilingual artifact a CEO or board chair can carry to the
 * audit committee. Aggregates live signal from every Sprint 4-5 module:
 * appetite breaches, board approvals, audit engagements, PDPL posture,
 * CCM coverage, asset blast-radius hot-spots, recent chain fires, and a
 * regulator-coverage tally.
 *
 * "Download" triggers window.print() with @media print rules in
 * boardPack.css that hide the chrome and lay sections out as A4 pages.
 */
export function BoardPackPage() {
  const { t, lang } = useI18n();
  const pack = useMemo(() => buildBoardPack(), []);

  // Track A4 — pull tenant branding so the cover renders the customer's
  // logo + organisation name in lieu of the default TSS shield.
  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);
  const orgName = lang === 'ar' ? brand.organisationNameAr : brand.organisationNameEn;
  const orgTagline = lang === 'ar' ? brand.taglineAr : brand.taglineEn;

  return (
    <Shell title={t('boardPackTitle')} sub={t('boardPackSub')}>
      {/*
        F2-42 — this page's figures are SAMPLE DATA, and until it is consolidated it must say
        so on screen and in print.

        The platform has two board packs. The real one is server-side: BoardPackService and
        BoardPackPptxService read live repositories (organisation, risk, findings, KRI
        definitions + measurements, changes, ethics cases, AML alerts) and are exported by
        GET /api/v1/reporting/board-pack.pdf and .pptx — both verified returning real bytes,
        and both already linked from the dashboard. This page renders a different thing:
        buildBoardPack() in lib/boardPack.ts assembles its payload from eight hardcoded
        arrays (SAUDI_APPETITES, APPROVAL_REQUESTS, CCM_CHECKS, ASSETS, CHAIN_RULES,
        ALL_WORKFLOWS, AUDIT_ENGAGEMENTS, BREACHES). So the screen and the PDF disagree, and
        the PDF is the correct one.

        The fix is a consolidation decision, not a rewire — reconstructing on the client what
        the server already computes would duplicate the logic and the two would drift. Until
        that decision lands (F2-42), the honest interim measure is a banner: a board pack that
        is unlabelled sample data is the actual defect, and labelling it costs nothing. It is
        deliberately NOT hidden from print, because a printed pack is exactly the artefact
        that gets quoted in a meeting.
      */}
      <div style={{
        display: 'flex', alignItems: 'flex-start', gap: 10, marginBottom: 16,
        padding: '10px 14px', borderRadius: 'var(--r-sm)',
        background: 'var(--warning-bg)', color: 'var(--warning-fg)',
        border: '1px solid var(--warning-fg)', font: '600 12.5px var(--font-sans)',
      }}>
        <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: 1 }} />
        <span>
          {lang === 'ar'
            ? 'بيانات نموذجية — الأرقام في هذه الصفحة توضيحية وليست بيانات مؤسستك. للحصول على حزمة المجلس الفعلية، نزّلها بصيغة PDF أو PowerPoint من لوحة المعلومات.'
            : 'Sample data — the figures on this page are illustrative and are not your organisation’s. For the real board pack, download the PDF or PowerPoint from the dashboard.'}
        </span>
      </div>

      {/* Action bar — hidden during print */}
      <div className="bp-actions" style={{
        display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 16,
      }}>
        <Button variant="primary" Icon={Printer} onClick={() => window.print()}>
          {t('boardPackPrint')}
        </Button>
      </div>

      <div className="bp-document" dir={lang === 'ar' ? 'rtl' : 'ltr'}>
        {/* ───── COVER ───── */}
        <section className="bp-page bp-cover">
          <div className="bp-cover-mark" style={brand.logoDataUrl ? { background: 'transparent', boxShadow: 'none' } : undefined}>
            {brand.logoDataUrl
              ? <img src={brand.logoDataUrl} alt={orgName} style={{ width: 96, height: 96, objectFit: 'contain' }} />
              : <ShieldCheck size={48} />}
          </div>
          <div className="bp-cover-eyebrow">{orgName}</div>
          <h1 className="bp-cover-title">{lang === 'ar' ? pack.cover.titleAr : pack.cover.titleEn}</h1>
          <div className="bp-cover-period">
            {lang === 'ar' ? pack.cover.periodAr : pack.cover.periodEn}
          </div>
          <div className="bp-cover-meta">
            <div>
              <span className="bp-meta-label">{t('boardPackGenerated')}</span>
              <span className="bp-meta-value">
                {pack.cover.generatedAt}
                <span className="bp-meta-hijri"> · {pack.cover.generatedAtHijri}</span>
              </span>
            </div>
            <div>
              <span className="bp-meta-label">{t('boardPackClassification')}</span>
              <span className="bp-meta-value">{t('boardPackConfidential')}</span>
            </div>
          </div>
          <div className="bp-cover-tagline">
            {orgTagline || t('boardPackCoverTagline')}
          </div>
        </section>

        {/* ───── SECTION 1: EXECUTIVE SUMMARY ───── */}
        <section className="bp-page">
          <SectionHeader idx={1} icon={Activity} title={t('boardPackS1Title')} sub={t('boardPackS1Sub')} />
          <div className="bp-kpi-grid">
            <KpiTile label={t('boardPackKpiActive')}     value={pack.executiveSummary.activeWorkflows}        tone="primary" />
            <KpiTile label={t('boardPackKpiOverdue')}    value={pack.executiveSummary.overdueWorkflows}       tone={pack.executiveSummary.overdueWorkflows > 0 ? 'danger' : 'success'} />
            <KpiTile label={t('boardPackKpiAppetite')}    value={pack.executiveSummary.appetiteBreaches}       tone={pack.executiveSummary.appetiteBreaches > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('boardPackKpiBoardApp')}    value={pack.executiveSummary.boardApprovalsPending}  tone="info" />
            <KpiTile label={t('boardPackKpiPdpl')}        value={pack.executiveSummary.pdpl72hWindowOpen}      tone={pack.executiveSummary.pdpl72hWindowOpen > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('boardPackKpiCcm')}         value={`${pack.executiveSummary.ccmCoveragePct}%`}   tone={pack.executiveSummary.ccmCoveragePct >= 80 ? 'success' : 'warning'} />
          </div>
        </section>

        {/* ───── SECTION 2: APPETITE BREACHES ───── */}
        <section className="bp-page">
          <SectionHeader idx={2} icon={AlertOctagon} title={t('boardPackS2Title')} sub={t('boardPackS2Sub')} />
          {pack.appetiteBreaches.length === 0
            ? <p className="bp-empty">{t('boardPackS2Empty')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('boardPackTblStatement')}</th>
                    <th>{t('boardPackTblSeverity')}</th>
                    <th>{t('boardPackTblExposure')}</th>
                    <th>{t('boardPackTblOwner')}</th>
                    <th>{t('boardPackTblCitation')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.appetiteBreaches.map(({ statement, severity, workflow }) => (
                    <tr key={statement.id}>
                      <td><strong>{lang === 'ar' && statement.titleAr ? statement.titleAr : statement.titleEn}</strong></td>
                      <td><span className={`bp-pill bp-pill-${severityClass(severity)}`}>
                        {appetiteSeverityLabel(severity, lang)}
                      </span></td>
                      <td className="mono">{statement.currentExposure}{statement.unit ? ` ${statement.unit}` : ''} / {statement.toleranceUpper}{statement.unit ? ` ${statement.unit}` : ''}</td>
                      <td>{statement.approvedBy ?? '—'}</td>
                      <td className="bp-citation">{workflow.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
          }
        </section>

        {/* ───── SECTION 3: BOARD-LEVEL APPROVALS ───── */}
        <section className="bp-page">
          <SectionHeader idx={3} icon={ClipboardCheck} title={t('boardPackS3Title')} sub={t('boardPackS3Sub')} />
          {pack.boardApprovals.length === 0
            ? <p className="bp-empty">{t('boardPackS3Empty')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('boardPackTblSubject')}</th>
                    <th>{t('boardPackTblModule')}</th>
                    <th>{t('boardPackTblNeedsApproval')}</th>
                    <th>{t('boardPackTblDue')}</th>
                    <th>{t('boardPackTblCitation')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.boardApprovals.map(req => {
                    const outstandingBoardRoles = req.approverRoles.filter(role =>
                      ['BOARD', 'AUDIT_COMMITTEE', 'RISK_COMMITTEE', 'SHARIA_COMMITTEE'].includes(role)
                      && !req.decisions.some(d => d.role === role && d.decision === 'APPROVED'),
                    );
                    return (
                      <tr key={req.id}>
                        <td><strong>{lang === 'ar' ? req.titleAr : req.titleEn}</strong></td>
                        <td>{req.module}</td>
                        <td>{outstandingBoardRoles.map(r => {
                          const p = APPROVER_REGISTRY[r];
                          return lang === 'ar' ? p.titleAr : p.titleEn;
                        }).join(', ')}</td>
                        <td className="mono">{req.dueDate}<br /><span className="bp-meta-hijri">{formatHijri(req.dueDate, lang)}</span></td>
                        <td className="bp-citation">{req.regulatorCitation ?? '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
          }
        </section>

        {/* ───── SECTION 4: AUDIT ENGAGEMENTS ───── */}
        <section className="bp-page">
          <SectionHeader idx={4} icon={Stamp} title={t('boardPackS4Title')} sub={t('boardPackS4Sub')} />
          {pack.auditEngagements.length === 0
            ? <p className="bp-empty">{t('boardPackS4Empty')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('boardPackTblEngagement')}</th>
                    <th>{t('boardPackTblFramework')}</th>
                    <th>{t('boardPackTblFirm')}</th>
                    <th>{t('boardPackTblMilestone')}</th>
                    <th>{t('boardPackTblDaysLeft')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.auditEngagements.map(e => (
                    <tr key={e.code}>
                      <td><strong>{e.titleEn}</strong><br /><span className="mono">{e.code}</span></td>
                      <td>{e.framework.replace(/_/g, ' ')}</td>
                      <td>{e.auditorFirm ?? '—'}<br /><span className="bp-meta-hijri">{e.leadAuditor ?? ''}</span></td>
                      <td>{lang === 'ar' ? e.currentMilestoneAr : e.currentMilestoneEn}<br />
                          {e.currentMilestoneDate && <span className="mono bp-meta-hijri">{e.currentMilestoneDate} · {formatHijri(e.currentMilestoneDate, lang)}</span>}</td>
                      <td className="mono">
                        {e.daysRemaining != null
                          ? <span className={e.daysRemaining < 0 ? 'bp-pill bp-pill-danger' : e.daysRemaining <= 5 ? 'bp-pill bp-pill-warning' : ''}>
                              {e.daysRemaining < 0 ? `${-e.daysRemaining}d ${t('boardPackOverdue')}` : `${e.daysRemaining}d`}
                            </span>
                          : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
          }
        </section>

        {/* ───── SECTION 5: PDPL POSTURE ───── */}
        <section className="bp-page">
          <SectionHeader idx={5} icon={Fingerprint} title={t('boardPackS5Title')} sub={t('boardPackS5Sub')} />
          <div className="bp-kpi-grid bp-kpi-grid-3">
            <KpiTile label={t('boardPackPdplOpen')}     value={pack.pdpl.open}     tone="info" />
            <KpiTile label={t('boardPackPdplInWindow')} value={pack.pdpl.in72hWindow} tone={pack.pdpl.in72hWindow > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('boardPackPdplBreached')} value={pack.pdpl.breached72h} tone={pack.pdpl.breached72h > 0 ? 'danger' : 'success'} />
          </div>
          {pack.pdpl.sampleBreach && (
            <div className="bp-callout">
              <div className="bp-callout-title">{t('boardPackPdplActiveCase')}</div>
              <div><strong>{pack.pdpl.sampleBreach.titleEn}</strong></div>
              <div className="bp-callout-meta">
                {t('boardPackPdplSeverity')}: {pack.pdpl.sampleBreach.severity} ·
                {' '}{pack.pdpl.sampleBreach.hoursRemaining < 0
                  ? `${Math.round(-pack.pdpl.sampleBreach.hoursRemaining)}h ${t('boardPackOverdue')}`
                  : `${Math.round(pack.pdpl.sampleBreach.hoursRemaining)}h ${t('boardPackToSdaia')}`}
              </div>
            </div>
          )}
        </section>

        {/* ───── SECTION 6: CCM COVERAGE ───── */}
        <section className="bp-page">
          <SectionHeader idx={6} icon={FileText} title={t('boardPackS6Title')} sub={t('boardPackS6Sub')} />
          <div className="bp-kpi-grid">
            <KpiTile label={t('boardPackCcmTotal')}    value={pack.ccm.totalChecks}     tone="primary" />
            <KpiTile label={t('boardPackCcmFresh')}    value={pack.ccm.fresh}            tone="success" />
            <KpiTile label={t('boardPackCcmStale')}    value={pack.ccm.stale}            tone="warning" />
            <KpiTile label={t('boardPackCcmExpired')}  value={pack.ccm.expired}          tone={pack.ccm.expired > 0 ? 'danger' : 'success'} />
            <KpiTile label={t('boardPackCcmCoverage')}  value={`${pack.ccm.coveragePct}%`} tone={pack.ccm.coveragePct >= 80 ? 'success' : 'warning'} />
            <KpiTile label={t('boardPackCcmControls')}  value={pack.ccm.controlsCovered}  tone="info" />
          </div>
        </section>

        {/* ───── SECTION 7: ASSET HOT-SPOTS ───── */}
        <section className="bp-page">
          <SectionHeader idx={7} icon={Server} title={t('boardPackS7Title')} sub={t('boardPackS7Sub')} />
          <table className="bp-table">
            <thead>
              <tr>
                <th>{t('boardPackTblAsset')}</th>
                <th>{t('boardPackTblType')}</th>
                <th>{t('boardPackTblCriticality')}</th>
                <th>{t('boardPackTblBlast')}</th>
                <th>{t('boardPackTblResidency')}</th>
                <th>{t('boardPackTblRegulators')}</th>
              </tr>
            </thead>
            <tbody>
              {pack.topAssets.map(a => (
                <tr key={a.code}>
                  <td><strong>{lang === 'ar' ? a.nameAr : a.nameEn}</strong><br /><span className="mono">{a.code}</span></td>
                  <td>{a.type.replace(/_/g, ' ')}</td>
                  <td><span className={`bp-pill bp-pill-${critClass(a.criticality)}`}>{a.criticality}</span></td>
                  <td className="mono">{a.transitiveImpact}</td>
                  <td>{a.dataResidency}</td>
                  <td>{a.regulatorScope.filter(r => r !== 'NONE').join(', ') || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {/* ───── SECTION 8: CHAIN FIRES ───── */}
        <section className="bp-page">
          <SectionHeader idx={8} icon={GitBranch} title={t('boardPackS8Title')} sub={t('boardPackS8Sub')} />
          {pack.recentChainFires.length === 0
            ? <p className="bp-empty">{t('boardPackS8Empty')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('boardPackTblRule')}</th>
                    <th>{t('boardPackTblFrom')}</th>
                    <th>{t('boardPackTblTo')}</th>
                    <th>{t('boardPackTblFiredAt')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.recentChainFires.slice(0, 10).map(f => (
                    <tr key={f.id}>
                      <td><strong>{lang === 'ar' ? f.ruleNameAr : f.ruleNameEn}</strong></td>
                      <td className="mono">{f.triggerModule} · {f.triggerSourceRef}</td>
                      <td className="mono">{f.spawnedModule} · {f.spawnedRef}</td>
                      <td className="mono">{f.firedAt.slice(0, 16).replace('T', ' ')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
          }
        </section>

        {/* ───── SECTION 9: REGULATORS ───── */}
        <section className="bp-page">
          <SectionHeader idx={9} icon={Landmark} title={t('boardPackS9Title')} sub={t('boardPackS9Sub')} />
          <table className="bp-table">
            <thead>
              <tr>
                <th>{t('boardPackTblRegulator')}</th>
                <th>{t('boardPackTblItems')}</th>
              </tr>
            </thead>
            <tbody>
              {pack.regulators.map(r => (
                <tr key={r.code}>
                  <td>
                    <strong>{r.code}</strong> — {lang === 'ar' ? r.nameAr : r.nameEn}
                  </td>
                  <td className="mono">{r.activeItems}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {/* ───── SIGN-OFF ───── */}
        <section className="bp-page bp-signoff">
          <SectionHeader idx={10} icon={AlertTriangle} title={t('boardPackS10Title')} sub={t('boardPackS10Sub')} />
          <div className="bp-signoff-grid">
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignBoardChair')}</div>
            </div>
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignAuditChair')}</div>
            </div>
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignRiskChair')}</div>
            </div>
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignCEO')}</div>
            </div>
          </div>
          <p className="bp-methodology">{t('boardPackMethodology')}</p>
        </section>
      </div>
    </Shell>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function SectionHeader({ idx, icon: Icon, title, sub }: { idx: number; icon: typeof Activity; title: string; sub: string }) {
  return (
    <div className="bp-section-header">
      <div className="bp-section-num">{idx}</div>
      <div className="bp-section-text">
        <div className="bp-section-title"><Icon size={16} /> {title}</div>
        <div className="bp-section-sub">{sub}</div>
      </div>
    </div>
  );
}

function KpiTile({ label, value, tone }: { label: string; value: string | number; tone: 'primary' | 'success' | 'warning' | 'danger' | 'info' }) {
  return (
    <div className={`bp-kpi bp-kpi-${tone}`}>
      <div className="bp-kpi-value">{value}</div>
      <div className="bp-kpi-label">{label}</div>
    </div>
  );
}

function severityClass(s: string): string {
  return s === 'CRITICAL' ? 'danger' : s === 'EXCEEDED' ? 'warning' : s === 'WARNING' ? 'info' : 'success';
}
function critClass(c: string): string {
  return c === 'CRITICAL' ? 'danger' : c === 'HIGH' ? 'warning' : c === 'MEDIUM' ? 'info' : 'success';
}
