import { useState } from 'react';
import {
  Plug, Activity, Clock, BookOpen, Database, Zap, Pause, Play,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  CONNECTOR_REGISTRY, CADENCE_LABEL,
  CONNECTOR_STATUS_TONE, CONNECTOR_STATUS_LABEL,
  type ConnectorDefinition, type ConnectorStatus,
} from '../../lib/connectors';
import { CcmDashboard } from '../../components/compliance/CcmDashboard';

/**
 * Connectors page — DD-62.
 *
 * Visualises the third intake channel — automated connectors pulling
 * from external monitoring + scanner + regulator-watch sources. Each
 * row shows source / what it auto-creates / cadence / status / 30-day
 * intake count.
 *
 * ServiceNow IRM "Continuous Monitoring" + Vanta "Integrations" pattern.
 */
export function ConnectorsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [filter, setFilter] = useState<'ALL' | ConnectorStatus>('ALL');

  const connectors = CONNECTOR_REGISTRY;
  const active = connectors.filter(c => c.status === 'ACTIVE');
  const configured = connectors.filter(c => c.status === 'CONFIGURED');
  const intake30d = active.reduce((acc, c) => acc + (c.intake30d ?? 0), 0);

  const visible = filter === 'ALL' ? connectors : connectors.filter(c => c.status === filter);

  return (
    <Shell title={t('connectorsTitle')} sub={t('connectorsSub')}>
      <PageHeader
        eyebrow={t('connectorsEyebrow')}
        title={t('connectorsTitle')}
        sub={t('connectorsPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Activity} label={t('connectorsKpiActive')}     value={active.length}     tone="success" />
        <Kpi Icon={Plug}     label={t('connectorsKpiConfigured')} value={configured.length} tone="info" />
        <Kpi Icon={Database} label={t('connectorsKpiTotal')}      value={connectors.length} tone="primary" />
        <Kpi Icon={Zap}      label={t('connectorsKpiIntake30d')}  value={intake30d}         tone="accent" />
      </div>

      {/* Sprint 4 — Continuous Control Monitoring panel sits above the
          connector registry. Every check the connectors run is graded by
          freshness; stale or expired evidence becomes a workflow instance
          fed into the cross-module SLA pulse. */}
      <div style={{ marginBottom: 16 }}>
        <CcmDashboard />
      </div>

      <Card title={t('connectorsRegistryTitle')} Icon={Plug}
        action={<select value={filter} onChange={e => setFilter(e.target.value as 'ALL' | ConnectorStatus)}
          style={{
            padding: '6px 8px', fontSize: 12,
            border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-2)', color: 'var(--ink-1)',
          }}>
          <option value="ALL">{t('connectorsFilterAll')}</option>
          <option value="ACTIVE">{t('connectorsFilterActive')}</option>
          <option value="CONFIGURED">{t('connectorsFilterConfigured')}</option>
          <option value="AVAILABLE">{t('connectorsFilterAvailable')}</option>
        </select>}>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('connectorsBlurb')}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {visible.map(c => (
            <ConnectorRow key={c.id} c={c} t={t} lang={lang} toast={toast} />
          ))}
        </div>
      </Card>
    </Shell>
  );
}

function ConnectorRow({ c, t, lang, toast }: {
  c: ConnectorDefinition;
  t: (k: string) => string;
  lang: 'en' | 'ar';
  toast: { info: (m: string) => void; success: (m: string) => void };
}) {
  const statusLabel = CONNECTOR_STATUS_LABEL[c.status][lang === 'ar' ? 'ar' : 'en'];
  const cadenceLabel = CADENCE_LABEL[c.cadence][lang === 'ar' ? 'ar' : 'en'];

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '38px 1fr 130px 110px 110px 90px 110px',
      gap: 10, padding: '12px 0',
      borderBottom: '1px dashed var(--border)',
      alignItems: 'center',
    }}>
      <div style={{
        width: 32, height: 32, borderRadius: 'var(--r-sm)',
        background: 'var(--primary-50)', color: 'var(--primary)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Plug size={16} strokeWidth={2} />
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-1)' }}>
          {lang === 'ar' ? c.nameAr : c.nameEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, lineHeight: 1.5 }}>
          {lang === 'ar' ? c.descriptionAr : c.descriptionEn}
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
          <Badge tone="neutral">
            <BookOpen size={10} style={{ marginInlineEnd: 4 }} />
            {c.reference}
          </Badge>
          {c.targetEntity && <Badge tone="info">→ {c.targetEntity}</Badge>}
          {c.intakeKind && <Badge tone="accent">→ intake</Badge>}
        </div>
      </div>
      <div>
        <div className="mono" style={{ fontSize: 11, color: 'var(--ink-2)' }}>{c.source}</div>
        {c.owner && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.owner}</div>}
      </div>
      <div>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
          <Clock size={12} /> {cadenceLabel}
        </div>
      </div>
      <div>
        <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          {c.lastRunAt ? c.lastRunAt.slice(5, 16).replace('T', ' ') : '—'}
        </div>
      </div>
      <div style={{ textAlign: 'center' }}>
        <span className="mono" style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-1)' }}>
          {c.intake30d ?? 0}
        </span>
        <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t('connectorsIntake30d')}</div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
        <Badge tone={CONNECTOR_STATUS_TONE[c.status]} dot>{statusLabel}</Badge>
        {c.status === 'ACTIVE' && (
          <Button variant="ghost" size="sm" Icon={Pause} onClick={() => toast.info(t('connectorsPauseHint'))}>
            {t('connectorsPause')}
          </Button>
        )}
        {(c.status === 'CONFIGURED' || c.status === 'AVAILABLE' || c.status === 'DISABLED') && (
          <Button variant="primary" size="sm" Icon={Play} onClick={() => toast.success(t('connectorsActivated'))}>
            {t('connectorsActivate')}
          </Button>
        )}
      </div>
    </div>
  );
}
