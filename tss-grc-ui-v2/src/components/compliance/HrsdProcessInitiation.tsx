// HRSD process-initiation card — DD-88.
//
// The Nitaqat panel displays *what* HRSD compliance looks like; this card
// is the action bar that *kicks the workflow off*. Each tile is a launchable
// HRSD process (Saudization report / workplace injury / GOSI register /
// employment contract / WPS payroll) backed by an IntakeKind in the intake
// substrate. Clicking a tile navigates to /intake/<kind>, which auto-acks,
// generates a reference code, sets the SLA, and routes to the HR_LEAD for
// triage — same flow every other intake follows.

import { useNavigate } from 'react-router-dom';
import { Send, Activity, AlertOctagon, UserPlus, FileSignature, Banknote } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { useI18n } from '../../lib/i18n';
import type { IntakeKind } from '../../lib/intake';
import { INTAKE_CATALOG } from '../../lib/intake';

interface HrsdProcess {
  kind: IntakeKind;
  Icon: typeof Send;
  /** Cadence label (already-translated). */
  cadenceKey: string;
  /** Tone for the cadence chip. */
  tone: 'danger' | 'warning' | 'info' | 'success' | 'primary';
}

const HRSD_PROCESSES: HrsdProcess[] = [
  { kind: 'SAUDIZATION_REPORT',     Icon: Activity,      cadenceKey: 'nitaqatCadenceMONTHLY',      tone: 'warning' },
  { kind: 'WORKPLACE_INJURY',       Icon: AlertOctagon,  cadenceKey: 'nitaqatCadenceEVENT_DRIVEN', tone: 'danger'  },
  { kind: 'GOSI_EMPLOYEE_REGISTER', Icon: UserPlus,      cadenceKey: 'nitaqatCadenceEVENT_DRIVEN', tone: 'info'    },
  { kind: 'EMPLOYMENT_CONTRACT',    Icon: FileSignature, cadenceKey: 'nitaqatCadenceEVENT_DRIVEN', tone: 'primary' },
  { kind: 'WPS_PAYROLL_RUN',        Icon: Banknote,      cadenceKey: 'nitaqatCadenceMONTHLY',      tone: 'warning' },
];

export function HrsdProcessInitiation() {
  const { t, lang } = useI18n();
  const navigate = useNavigate();

  return (
    <Card title={t('hrsdProcessTitle')} Icon={Send}
      action={<Badge tone="primary">{HRSD_PROCESSES.length} {t('hrsdProcessCount')}</Badge>}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('hrsdProcessSub')}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10 }}>
        {HRSD_PROCESSES.map(p => {
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
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 10, color: 'var(--ink-3)' }}>
                <span>{t('hrsdProcessReference')}: <span className="mono">{meta.reference}</span></span>
                <span>·</span>
                <span>{t('hrsdProcessAssignee')}: <span className="mono">{meta.defaultAssignee}</span></span>
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
                <Send size={11} /> {t('hrsdProcessLaunch')}
              </div>
            </button>
          );
        })}
      </div>
    </Card>
  );
}

/** Map a Nitaqat / Labor / GOSI requirement code to the IntakeKind that
 *  initiates the related workflow. Used by NitaqatPanel to render an
 *  inline "Submit now" launcher next to each requirement row. */
export const REQUIREMENT_TO_INTAKE: Record<string, IntakeKind> = {
  'NITAQAT-2': 'SAUDIZATION_REPORT',
  'LABOR-1':   'WPS_PAYROLL_RUN',
  'LABOR-2':   'EMPLOYMENT_CONTRACT',
  'GOSI-1':    'GOSI_EMPLOYEE_REGISTER',
  'GOSI-3':    'WORKPLACE_INJURY',
};
