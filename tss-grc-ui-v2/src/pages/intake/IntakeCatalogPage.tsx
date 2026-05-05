import { Link } from 'react-router-dom';
import {
  ShieldAlert, Lock, AlertTriangle, ClipboardCheck, Shield,
  Truck, Fingerprint, Activity, Briefcase, GitBranch, Award, FileCheck2,
  ArrowRight, Inbox,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { INTAKE_CATALOG, type IntakeKind } from '../../lib/intake';
import { INTAKE_FORM_FIELDS } from '../../lib/intakeForms';

/**
 * IntakeCatalogPage — DD-59.
 *
 * Authenticated employee landing for the intake catalog. Lists every
 * intake kind that has an employee-facing form (i.e. publicSurface =
 * false AND has a form schema in INTAKE_FORM_FIELDS).
 *
 * Modelled on ServiceNow IRM Service Catalog: tile per intake item,
 * regulator citation in the eyebrow, SLA badge, "Submit" CTA.
 */
const KIND_ICON: Record<IntakeKind, LucideIcon> = {
  SECURITY_INCIDENT:  ShieldAlert,
  PRIVACY_BREACH:     Lock,
  RISK_ISSUE:         AlertTriangle,
  CONTROL_OBSERVATION: ClipboardCheck,
  POLICY_EXCEPTION:   Shield,
  VENDOR_ONBOARDING:  Truck,
  DPIA_INITIATION:    Fingerprint,
  BIA_SUBMISSION:     Activity,
  AUDIT_REQUEST:      Briefcase,
  CHANGE_REQUEST:     GitBranch,
  ETHICS_DISCLOSURE:  Award,
  CONTROL_ATTESTATION: FileCheck2,
  // Public-only — never surfaced here
  DSR_REQUEST:        Lock,
  BREACH_REPORT:      Lock,
  VRP_DISCLOSURE:     Lock,
  SPEAKUP:            Lock,
};

export function IntakeCatalogPage() {
  const { t, lang } = useI18n();

  // Show only auth'd kinds that have a form schema wired up
  // ── Domain grouping — 5 sections, ServiceNow-style category split ──
  const sections: Array<{ titleKey: string; kinds: IntakeKind[] }> = [
    { titleKey: 'intakeCatalogSectionCyberOps', kinds: ['SECURITY_INCIDENT', 'CHANGE_REQUEST'] },
    { titleKey: 'intakeCatalogSectionRiskRes',  kinds: ['RISK_ISSUE', 'BIA_SUBMISSION'] },
    { titleKey: 'intakeCatalogSectionCompAud',  kinds: ['CONTROL_OBSERVATION', 'POLICY_EXCEPTION', 'CONTROL_ATTESTATION', 'AUDIT_REQUEST'] },
    { titleKey: 'intakeCatalogSectionPrivacy',  kinds: ['PRIVACY_BREACH', 'DPIA_INITIATION'] },
    { titleKey: 'intakeCatalogSectionTpEthics', kinds: ['VENDOR_ONBOARDING', 'ETHICS_DISCLOSURE'] },
  ];

  return (
    <Shell title={t('intakeCatalogTitle')} sub={t('intakeCatalogSub')}>
      <PageHeader
        eyebrow={t('intakeCatalogEyebrow')}
        title={t('intakeCatalogTitle')}
        sub={t('intakeCatalogPageSub')}
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {sections.map(section => {
          // Skip section if none of its kinds are wired up
          const kinds = section.kinds.filter(k => INTAKE_CATALOG[k] && INTAKE_FORM_FIELDS[k]);
          if (kinds.length === 0) return null;
          return (
            <Card key={section.titleKey}
              title={t(section.titleKey)}
              action={<Badge tone="info">{kinds.length}</Badge>}>
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: 12,
              }}>
                {kinds.map(kind => <CatalogTile key={kind} kind={kind} t={t} lang={lang} />)}
              </div>
            </Card>
          );
        })}
      </div>
    </Shell>
  );
}

function CatalogTile({ kind, t, lang }: { kind: IntakeKind; t: (k: string) => string; lang: string }) {
  const e = INTAKE_CATALOG[kind];
  const Icon = KIND_ICON[kind] ?? Inbox;
  return (
    <Link to={`/intake/${kind.toLowerCase()}`}
      style={{
        display: 'block', textDecoration: 'none', color: 'inherit',
        padding: 16,
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-sm)',
        background: 'var(--surface-2)',
        transition: 'border-color var(--dur-fast) var(--ease-standard), box-shadow var(--dur-fast)',
      }}
      onMouseEnter={ev => {
        const el = ev.currentTarget as HTMLElement;
        el.style.borderColor = 'var(--primary)';
        el.style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)';
      }}
      onMouseLeave={ev => {
        const el = ev.currentTarget as HTMLElement;
        el.style.borderColor = 'var(--border)';
        el.style.boxShadow = '';
      }}>
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', marginBottom: 10 }}>
        <div style={{
          width: 38, height: 38, borderRadius: 'var(--r-sm)',
          background: 'var(--primary-50)', color: 'var(--primary)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexShrink: 0,
        }}>
          <Icon size={20} strokeWidth={2} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            {e.reference}
          </div>
          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--ink-1)', marginTop: 2, lineHeight: 1.3 }}>
            {lang === 'ar' ? e.titleAr : e.titleEn}
          </div>
        </div>
      </div>
      <p style={{ margin: 0, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5, minHeight: 36 }}>
        {lang === 'ar' ? e.blurbAr : e.blurbEn}
      </p>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12 }}>
        <Badge tone="info">
          {e.slaHours >= 24
            ? `${Math.round(e.slaHours / 24)} ${t('intakeCatalogDaysSla')}`
            : `${e.slaHours} ${t('intakeCatalogHoursSla')}`}
        </Badge>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, fontWeight: 600, color: 'var(--primary)' }}>
          {t('intakeCatalogSubmit')} <ArrowRight size={14} />
        </span>
      </div>
    </Link>
  );
}
