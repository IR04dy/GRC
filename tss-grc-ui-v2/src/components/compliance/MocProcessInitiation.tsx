// MOC process-initiation card — DD-94.
//
// Mirror of HrsdProcessInitiation for Saudi Ministry of Commerce. Each tile
// is a launchable workflow backed by an IntakeKind that routes to the
// COMPLIANCE_OFFICER queue with a tier-driven SLA.

import { useNavigate } from 'react-router-dom';
import { Send, ShieldAlert, Users, FileText, Banknote, MessageSquareWarning } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import type { IntakeKind } from '../../lib/intake';
import { INTAKE_CATALOG } from '../../lib/intake';

interface MocProcess {
  kind: IntakeKind;
  Icon: typeof Send;
  cadenceKey: string;
  tone: 'danger' | 'warning' | 'info' | 'success' | 'primary';
}

const MOC_PROCESSES: MocProcess[] = [
  { kind: 'TASATTUR_SELF_ASSESSMENT',         Icon: ShieldAlert,         cadenceKey: 'mocCadenceANNUAL',       tone: 'warning' },
  { kind: 'BENEFICIAL_OWNERSHIP_DISCLOSURE',  Icon: Users,                cadenceKey: 'mocCadenceEVENT',         tone: 'info'    },
  { kind: 'CR_ANNUAL_CONFIRMATION',           Icon: FileText,             cadenceKey: 'mocCadenceANNUAL',        tone: 'primary' },
  { kind: 'CAPITAL_CHANGE_NOTIFICATION',      Icon: Banknote,             cadenceKey: 'mocCadenceEVENT',         tone: 'warning' },
  { kind: 'CONSUMER_COMPLAINT_RESPONSE',      Icon: MessageSquareWarning, cadenceKey: 'mocCadenceCOMPLAINT',    tone: 'danger'  },
];

export function MocProcessInitiation() {
  const { t, lang } = useI18n();
  const navigate = useNavigate();

  return (
    <Card title={t('mocProcessTitle')} Icon={Send}
      action={<Badge tone="primary">{MOC_PROCESSES.length} {t('mocProcessCount')}</Badge>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('mocProcessSub')}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10 }}>
        {MOC_PROCESSES.map(p => {
          const meta = INTAKE_CATALOG[p.kind];
          const slaLabel = meta.slaHours >= 24
            ? `${Math.round(meta.slaHours / 24)} ${t('intakeCatalogDaysSla')}`
            : `${meta.slaHours} ${t('intakeCatalogHoursSla')}`;
          return (
            <button key={p.kind}
              onClick={() => navigate(`/intake/${p.kind.toLowerCase()}`)}
              style={{
                textAlign: lang === 'ar' ? 'right' : 'left',
                padding: 14, borderRadius: 'var(--r-md)',
                background: 'var(--surface-2)', border: '1px solid var(--border)',
                cursor: 'pointer', display: 'flex', flexDirection: 'column', gap: 8,
              }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <p.Icon size={18} style={{ color: `var(--${p.tone}-fg, var(--ink-2))` }} />
                <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-1)', flex: 1 }}>
                  {lang === 'ar' ? meta.titleAr : meta.titleEn}
                </div>
                <Badge tone={p.tone}>{t(p.cadenceKey)}</Badge>
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', lineHeight: 1.5 }}>
                {lang === 'ar' ? meta.blurbAr : meta.blurbEn}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 10, color: 'var(--ink-3)', flexWrap: 'wrap' }}>
                <span>{t('mocProcessReference')}: <span className="mono">{meta.reference}</span></span>
                <span>·</span>
                <span>SLA: <span className="mono">{slaLabel}</span></span>
              </div>
              <div style={{
                display: 'inline-flex', alignItems: 'center', gap: 4,
                padding: '4px 10px', alignSelf: 'flex-start',
                background: 'var(--primary-50, var(--info-bg))',
                color: 'var(--primary-700, var(--info-fg))',
                border: '1px solid var(--primary-100, var(--border))',
                borderRadius: 'var(--r-sm)',
                fontSize: 11, fontWeight: 600,
              }}>
                <Send size={11} /> {t('mocProcessLaunch')}
              </div>
            </button>
          );
        })}
      </div>
    </Card>
  );
}

/** Map an MOC requirement code to the IntakeKind that initiates the related
 *  workflow. Used by MocPanel to render an inline launcher next to each
 *  Tasattur indicator / Consumer-Protection / CR requirement row. */
export const MOC_REQUIREMENT_TO_INTAKE: Record<string, IntakeKind> = {
  'TST-1': 'TASATTUR_SELF_ASSESSMENT',
  'TST-2': 'TASATTUR_SELF_ASSESSMENT',
  'TST-3': 'TASATTUR_SELF_ASSESSMENT',
  'TST-4': 'BENEFICIAL_OWNERSHIP_DISCLOSURE',
  'TST-5': 'TASATTUR_SELF_ASSESSMENT',
  'TST-6': 'TASATTUR_SELF_ASSESSMENT',
  'CR-1':  'CR_ANNUAL_CONFIRMATION',
  'CR-2':  'BENEFICIAL_OWNERSHIP_DISCLOSURE',
  'CR-3':  'CAPITAL_CHANGE_NOTIFICATION',
  'CP-1':  'CONSUMER_COMPLAINT_RESPONSE',
  'CP-2':  'CONSUMER_COMPLAINT_RESPONSE',
};
