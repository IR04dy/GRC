import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity, AlertTriangle, CheckCheck, Database, FileCheck2,
  ShieldCheck, Sparkles, Timer, Wrench,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadableSection } from '../../components/ui/LoadableSection';
import {
  ermGap, evidenceGap, privacyGap,
  type AppetiteVariance, type AtRiskTransfer, type EvidenceConnector,
} from '../../lib/buyerGap';
import { ai, type AiSuggestion } from '../../lib/ai';
import { aar, type CrisisAar } from '../../lib/aar';
import { useToast } from '../../lib/toast';
import { useLoadable } from '../../lib/useLoadable';

/**
 * Buyer-Assessment Completion Center.
 *
 * Single page that exposes the V113 + V115 + V116 backend endpoints
 * (§5.1 ERM + §5.4 Privacy + §5.9 Evidence + §27 AI features +
 * Tier-4 #23 NCEMA AAR) in one demo-friendly surface. Each card is
 * stateless: click the action button, see the result, no nav required.
 */
export function BuyerGapPage() {
  const toast = useToast();

  // Resource-per-card model: each Loadable is independent so one failing
  // endpoint doesn't blank the whole page.
  const varianceL  = useLoadable<AppetiteVariance>(() => ermGap.appetiteVariance());
  const connectorL = useLoadable<EvidenceConnector[]>(() => evidenceGap.listConnectors());
  const transferL  = useLoadable<AtRiskTransfer[]>(() => privacyGap.atRiskTransfers());
  const aarL       = useLoadable<CrisisAar[]>(() => aar.list());

  // Sweep buttons + AI demo carry transient UI state, not page state.
  const [attestSweepBusy, setAttestSweepBusy] = useState(false);
  const [freshSweepBusy,  setFreshSweepBusy]  = useState(false);
  const [dsrSweepBusy,    setDsrSweepBusy]    = useState(false);
  const [pdplSweepBusy,   setPdplSweepBusy]   = useState(false);
  const [aiOutput,        setAiOutput]        = useState<AiSuggestion | null>(null);
  const [aiBusy,          setAiBusy]          = useState(false);

  const STATUS_TONE: Record<string, BadgeTone> = {
    AVAILABLE: 'info', ENABLED: 'success', DISABLED: 'neutral',
    DRAFT: 'warning', DEPRECATED: 'danger',
    PUBLISHED: 'success', UNDER_REVIEW: 'info', APPROVED: 'success',
    RESCINDED: 'danger', ACCEPTED: 'success', REJECTED: 'danger',
  };

  // ── action handlers ─────────────────────────────────────────────────
  async function runAttestationSweep() {
    setAttestSweepBusy(true);
    try {
      const r = await ermGap.attestationSweep();
      toast.success(`Attestation sweep: ${r.overdue} risks overdue, ${r.firedEvents} events emitted`);
    } catch (e) {
      toast.danger('Attestation sweep failed: ' + (e as Error).message);
    } finally { setAttestSweepBusy(false); }
  }

  async function runFreshnessSweep() {
    setFreshSweepBusy(true);
    try {
      const r = await evidenceGap.freshnessSweep();
      toast.success(`Evidence sweep: ${r.itemsScanned} scanned, ${r.flippedToStale} flipped to STALE`);
    } catch (e) {
      toast.danger('Freshness sweep failed: ' + (e as Error).message);
    } finally { setFreshSweepBusy(false); }
  }

  async function toggleConnector(c: EvidenceConnector) {
    const next = c.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    try {
      const updated = await evidenceGap.updateConnector(c.code, { status: next });
      connectorL.replace(prev => (prev ?? []).map(x => x.code === c.code ? updated : x));
    } catch (e) {
      toast.danger('Update failed: ' + (e as Error).message);
    }
  }

  async function runDsrSweep() {
    setDsrSweepBusy(true);
    try {
      const r = await privacyGap.dsrSlaSweep();
      toast.success(`DSR SLA sweep: ${r.openOverdue} overdue, ${r.firedEvents} PDPL_DSR_SLA_BREACH emitted`);
    } catch (e) {
      toast.danger('DSR sweep failed: ' + (e as Error).message);
    } finally { setDsrSweepBusy(false); }
  }

  async function runPdplSweep() {
    setPdplSweepBusy(true);
    try {
      const r = await privacyGap.pdpl72hSweep();
      toast.success(
        `PDPL 72h sweep: ${r.personalDataCrises} PD crises, ${r.deadlinesSet} deadlines set, ${r.newlyBreached} newly breached`);
    } catch (e) {
      toast.danger('PDPL sweep failed: ' + (e as Error).message);
    } finally { setPdplSweepBusy(false); }
  }

  async function runAiBoardSummary() {
    setAiBusy(true);
    try {
      const s = await ai.boardSummary({
        openHighRisks: varianceL.data?.risksAboveAppetite ?? 0,
        overdueCapa: 3,
        ncaCompliancePct: 82,
        pdplCompliancePct: 76,
        bcmReadinessScore: 88,
        activeIncidents: 2,
      });
      setAiOutput(s);
    } catch (e) {
      toast.danger('AI suggestion failed: ' + (e as Error).message);
    } finally { setAiBusy(false); }
  }

  function parseOutput(s: AiSuggestion): Record<string, unknown> {
    try { return JSON.parse(s.outputJson); } catch { return {}; }
  }

  return (
    <Shell title="Buyer-Assessment Completion Center"
           sub="V113 + V115 + V116 — §5.1 ERM, §5.4 Privacy, §5.9 Evidence, §27 AI, Tier-4 NCEMA AAR">

      <div className="buyer-gap-grid">

        {/* §5.1 ERM — appetite variance */}
        <Card title="§5.1 ERM — Appetite vs Exposure" Icon={Activity}>
          <LoadableSection
            loading={varianceL.loading}
            error={varianceL.error}
            onRetry={varianceL.reload}
            data={varianceL.data}
            emptyTitle="No risks registered yet"
            emptySub="Add an enterprise risk to see appetite vs exposure."
            empty={(v) => v.totalRisks === 0}
          >
            {(variance) => (
              <div>
                <div style={{ display: 'flex', gap: 12, marginBottom: 12, flexWrap: 'wrap' }}>
                  <Badge tone="neutral">Total: {variance.totalRisks}</Badge>
                  <Badge tone="danger">Above appetite: {variance.risksAboveAppetite}</Badge>
                  <Badge tone="success">Within: {variance.risksWithinAppetite}</Badge>
                </div>
                <div className="text-xs" style={{ color: 'var(--ink-3)' }}>
                  Categories tracked: {Object.keys(variance.byCategory).length}
                </div>
              </div>
            )}
          </LoadableSection>
        </Card>

        {/* §5.1 ERM — attestation sweep */}
        <Card title="§5.1 ERM — Risk Owner Attestation" Icon={CheckCheck}>
          <p className="text-sm" style={{ marginBottom: 12, color: 'var(--ink-3)' }}>
            Quarterly (90-day) cadence — SAMA + NCA ECC §1-3 expectation.
            Sweep flags risks past their <code>next_attestation_date</code> and emits
            <code> RISK_ATTESTATION_OVERDUE</code>.
          </p>
          <Button onClick={runAttestationSweep} disabled={attestSweepBusy}
                  Icon={Wrench}>
            {attestSweepBusy ? 'Sweeping…' : 'Run attestation sweep'}
          </Button>
        </Card>

        {/* §5.9 Evidence — freshness */}
        <Card title="§5.9 Evidence — Freshness Sweep" Icon={FileCheck2}>
          <p className="text-sm" style={{ marginBottom: 12, color: 'var(--ink-3)' }}>
            Walks evidence past <code>review_due_date</code>, flips status to
            <code> STALE</code>, and emits <code>EVIDENCE_REVIEW_OVERDUE</code>.
          </p>
          <Button onClick={runFreshnessSweep} disabled={freshSweepBusy}
                  Icon={Wrench}>
            {freshSweepBusy ? 'Sweeping…' : 'Run freshness sweep'}
          </Button>
        </Card>

        {/* §5.9 Evidence — connectors */}
        <Card title="§5.9 Evidence — Connector Library" Icon={Database}>
          <LoadableSection
            loading={connectorL.loading}
            error={connectorL.error}
            onRetry={connectorL.reload}
            data={connectorL.data}
            emptyTitle="No connectors registered"
            emptySub="Connector catalog seeds on tenant provisioning."
          >
            {(connectors) => (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {connectors.map(c => (
                  <div key={c.code} style={{
                    display: 'flex', justifyContent: 'space-between',
                    alignItems: 'center', padding: '6px 0',
                    borderBottom: '1px solid var(--border)',
                  }}>
                    <div>
                      <div style={{ fontWeight: 500 }}>{c.nameEn}</div>
                      <div className="text-2xs" style={{ color: 'var(--ink-3)' }}>
                        {c.vendor} · {c.category}
                      </div>
                    </div>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      <Badge tone={STATUS_TONE[c.status] ?? 'neutral'}>{c.status}</Badge>
                      <Button size="sm" onClick={() => toggleConnector(c)}>
                        {c.status === 'ENABLED' ? 'Disable' : 'Enable'}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </LoadableSection>
        </Card>

        {/* §5.4 Privacy — DSR sweep */}
        <Card title="§5.4 Privacy — DSR 30-day SLA" Icon={Timer}>
          <p className="text-sm" style={{ marginBottom: 12, color: 'var(--ink-3)' }}>
            PDPL Article 4 — 30-day fulfilment deadline. Sweep flips
            overdue cases and emits <code>PDPL_DSR_SLA_BREACH</code> once per case.
          </p>
          <Button onClick={runDsrSweep} disabled={dsrSweepBusy}
                  Icon={Wrench}>
            {dsrSweepBusy ? 'Sweeping…' : 'Run DSR SLA sweep'}
          </Button>
        </Card>

        {/* §5.4 Privacy — PDPL 72-hour */}
        <Card title="§5.4 Privacy — PDPL Article 20 (72h)" Icon={AlertTriangle}>
          <p className="text-sm" style={{ marginBottom: 12, color: 'var(--ink-3)' }}>
            Personal-data breach notification window. Sweep computes
            <code> detection_time + 72h</code> for every personal-data crisis and
            flips <code>pdpl_72h_breached</code> when the window passes.
          </p>
          <Button onClick={runPdplSweep} disabled={pdplSweepBusy}
                  Icon={Wrench}>
            {pdplSweepBusy ? 'Sweeping…' : 'Run 72-hour sweep'}
          </Button>
        </Card>

        {/* §5.4 Privacy — at-risk transfers */}
        <Card title="§5.4 Privacy — At-Risk Cross-Border" Icon={ShieldCheck}>
          <LoadableSection
            loading={transferL.loading}
            error={transferL.error}
            onRetry={transferL.reload}
            data={transferL.data}
            emptyTitle="No flagged transfers"
            emptySub="No cross-border transfers are missing adequacy or mechanism details."
          >
            {(transfers) => (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {transfers.slice(0, 4).map(t => (
                  <div key={t.id} className="text-xs" style={{
                    padding: 8, borderRadius: 6,
                    background: 'var(--surface-2, var(--surface))',
                  }}>
                    <div style={{ fontWeight: 500 }}>{t.activityName} → {t.receivingCountry}</div>
                    <div style={{ color: 'var(--ink-3)', marginTop: 2 }}>{t.riskNote}</div>
                  </div>
                ))}
              </div>
            )}
          </LoadableSection>
        </Card>

        {/* §27 AI — board summary */}
        <Card title="§27 AI — Board Summary Draft" Icon={Sparkles}>
          <p className="text-sm" style={{ marginBottom: 12, color: 'var(--ink-3)' }}>
            Heuristic generator (LLM gateway pending). Persisted as DRAFT;
            human reviewer accepts/rejects before it lands on a board pack.
          </p>
          <Button onClick={runAiBoardSummary} disabled={aiBusy} Icon={Sparkles}>
            {aiBusy ? 'Generating…' : 'Generate board summary'}
          </Button>
          {aiOutput && (
            <div style={{ marginTop: 12 }}>
              <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <Badge tone={STATUS_TONE[aiOutput.status] ?? 'neutral'}>{aiOutput.status}</Badge>
                {aiOutput.confidence != null && (
                  <Badge tone="info">Confidence {Number(aiOutput.confidence).toFixed(0)}%</Badge>
                )}
              </div>
              <pre style={{
                background: 'var(--surface-2)', padding: 10, borderRadius: 6,
                fontSize: 11, maxHeight: 200, overflow: 'auto', whiteSpace: 'pre-wrap',
              }}>
                {String(parseOutput(aiOutput).summaryEn ?? '')}
              </pre>
            </div>
          )}
        </Card>

        {/* Tier-4 #23 — NCEMA AAR */}
        <Card title="Tier-4 — NCEMA After-Action Reports" Icon={FileCheck2}>
          <LoadableSection
            loading={aarL.loading}
            error={aarL.error}
            onRetry={aarL.reload}
            data={aarL.data}
            emptyTitle="No AARs yet"
            emptySub="Generate one from any closed crisis event."
          >
            {(aars) => (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {aars.slice(0, 5).map(a => (
                  <Link key={a.id} to={`/bcm/aars/${a.id}`} className="text-xs"
                        style={{
                          display: 'flex', justifyContent: 'space-between',
                          padding: '6px 0', borderBottom: '1px solid var(--border)',
                          textDecoration: 'none', color: 'inherit',
                        }}>
                    <div>
                      <div style={{ fontWeight: 500, color: 'var(--ink-1)' }}>{a.reportCode}</div>
                      <div style={{ color: 'var(--ink-3)' }}>{a.titleEn}</div>
                    </div>
                    <Badge tone={STATUS_TONE[a.status] ?? 'neutral'}>{a.status}</Badge>
                  </Link>
                ))}
              </div>
            )}
          </LoadableSection>
        </Card>

      </div>
    </Shell>
  );
}
