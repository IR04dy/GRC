import { Link } from 'react-router-dom';
import {
  Award, ShieldCheck, Network, AlertOctagon, FileText, Lock, ArrowRight,
  Globe, Database, MessageSquare, Send, BookOpen,
} from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';

/**
 * Trust Center — DD-61.
 *
 * Public-facing transparency page modelled on continuous-monitoring tooling + continuous-monitoring tooling + industry-standard tooling
 * Trust Center patterns. Single source of truth for "is TSS GRC safe to
 * trust with my data?" — no auth, browseable by customers, regulators,
 * insurers, prospects, security researchers.
 *
 * Five sections:
 *   1. Security & compliance posture — every active certification with
 *      issuing body, scope, valid-until, audit-firm
 *   2. Sub-processors — every 3rd party we share customer data with,
 *      with country of processing and PDPL safeguard
 *   3. Public breach log — every PDPL Art. 27 breach we have disclosed,
 *      summarised + status
 *   4. Customer rights — direct links to DSR / Speak-Up / VRP portals
 *   5. Contact — DPO, CISO Office, Legal
 *
 * Standards anchor:
 *   PDPL Art. 5 + Art. 8       — transparency obligations
 *   ISO 27001:2022 §A.5.13     — labelling of information
 *   ISO 27017                  — public assertions on cloud services
 *   GDPR Art. 13               — information to data subjects
 */
export function TrustCenterPage() {
  const { t, lang } = useI18n();

  // Demo data — in production these would come from a Trust Center API.
  const certifications = [
    { code: 'ISO 27001:2022', issuer: 'BSI',         scope: lang === 'ar' ? 'كامل نطاق نظام إدارة أمن المعلومات' : 'Full ISMS scope', validUntil: '2027-04-04' },
    { code: 'ISO 22301:2019', issuer: 'BSI',         scope: lang === 'ar' ? 'استمرارية الأعمال — الخدمات الحرجة' : 'BCM — critical services', validUntil: '2027-03-22' },
    { code: 'SOC 2 Type II',  issuer: 'PwC',         scope: lang === 'ar' ? 'الأمان والتوافر والسرية' : 'Security · Availability · Confidentiality', validUntil: '2027-03-22' },
    { code: 'PCI DSS v4.0',   issuer: 'AT&T QSA',    scope: lang === 'ar' ? 'بيئة بيانات حاملي البطاقات' : 'Cardholder Data Environment', validUntil: '2027-02-08' },
    { code: 'NCA ECC',        issuer: 'NCA',         scope: lang === 'ar' ? 'الضوابط الأساسية للأمن السيبراني' : 'Essential Cybersecurity Controls', validUntil: '2027-04-22' },
    { code: 'PDPL Compliance', issuer: 'SDAIA',      scope: lang === 'ar' ? 'البيانات الشخصية — كامل المنظمة' : 'Personal data — organisation-wide', validUntil: '2027-04-22' },
  ];

  const subProcessors = [
    { name: 'Amazon Web Services (KSA)',     country: 'Saudi Arabia', purpose: lang === 'ar' ? 'استضافة الخدمات الأساسية' : 'Core service hosting',           safeguard: lang === 'ar' ? 'استضافة محلية كاملة' : 'In-Kingdom hosting' },
    { name: 'STC Cloud',                     country: 'Saudi Arabia', purpose: lang === 'ar' ? 'استضافة الخدمات الأساسية' : 'Core service hosting',           safeguard: lang === 'ar' ? 'مورد سيادي' : 'Sovereign provider' },
    { name: 'CloudFlare',                    country: 'USA',          purpose: lang === 'ar' ? 'حماية حركة المرور وCDN' : 'Edge / CDN / DDoS',                 safeguard: lang === 'ar' ? 'بنود تعاقدية معيارية' : 'SCC + IRC compliant' },
    { name: 'Auth0 (Okta)',                  country: 'USA',          purpose: lang === 'ar' ? 'هوية العملاء' : 'Customer identity',                            safeguard: lang === 'ar' ? 'بنود تعاقدية معيارية' : 'SCC + ISO 27001 + SOC 2' },
    { name: 'Stripe',                        country: 'USA',          purpose: lang === 'ar' ? 'معالجة المدفوعات' : 'Payment processing',                       safeguard: lang === 'ar' ? 'PCI DSS + بنود معيارية' : 'PCI DSS + SCC' },
    { name: 'SendGrid',                      country: 'USA',          purpose: lang === 'ar' ? 'بريد إلكتروني تشغيلي' : 'Transactional email',                  safeguard: lang === 'ar' ? 'بنود تعاقدية معيارية' : 'SCC compliant' },
    { name: 'Datadog',                       country: 'USA',          purpose: lang === 'ar' ? 'مراقبة الأنظمة' : 'Application monitoring',                     safeguard: lang === 'ar' ? 'مُجمَّع — لا بيانات شخصية' : 'Aggregated — no PII' },
  ];

  const breachLog = [
    {
      date: '2026-04-12', refCode: 'INC-018',
      summary: lang === 'ar'
        ? 'مشاركة عرضية مُفرِطة في تصدير بيانات العملاء — 5% صفوف زائدة لمدة أسبوعين'
        : 'Customer-data export accidental over-share — 5% extra rows for 2 weeks',
      status: 'CLOSED', subjectsAffected: '~ 2,400',
    },
    {
      date: '2026-03-22', refCode: 'INC-014',
      summary: lang === 'ar'
        ? 'حملة تخمين كلمات مرور VPN — تم احتواؤها قبل أي تسجيل ناجح'
        : 'VPN credential-stuffing campaign — contained before any successful sign-in',
      status: 'CLOSED', subjectsAffected: '0 confirmed',
    },
  ];

  return (
    <PublicShell title={t('portalTrustTitle')} sub={t('portalTrustSub')}>
      {/* Hero stat strip */}
      <Card>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16,
          padding: 8, textAlign: 'center',
        }}>
          <Stat Icon={Award}        value={certifications.length} label={t('portalTrustStatCerts')} />
          <Stat Icon={Network}      value={subProcessors.length}  label={t('portalTrustStatSubProc')} />
          <Stat Icon={ShieldCheck}  value="99.95%"                label={t('portalTrustStatUptime')} />
          <Stat Icon={AlertOctagon} value={breachLog.length}      label={t('portalTrustStatBreaches')} />
        </div>
      </Card>

      {/* Active certifications */}
      <Card title={t('portalTrustCertsTitle')} Icon={Award}
        action={<Badge tone="info">{certifications.length} {t('portalTrustActive')}</Badge>}>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('portalTrustCertsBlurb')}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {certifications.map(c => (
            <div key={c.code} style={{
              display: 'grid', gridTemplateColumns: '160px 1fr 140px 110px',
              gap: 12, padding: '12px 4px', borderBottom: '1px dashed var(--border)',
              alignItems: 'center',
            }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--primary)' }}>{c.code}</span>
              <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{c.scope}</span>
              <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{c.issuer}</span>
              <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                <Badge tone="success">{t('portalTrustValid')}</Badge> {c.validUntil}
              </span>
            </div>
          ))}
        </div>
      </Card>

      {/* Sub-processors */}
      <Card title={t('portalTrustSubProcTitle')} Icon={Network}
        action={<Badge tone="info">{subProcessors.length}</Badge>}>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('portalTrustSubProcBlurb')}
        </p>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {subProcessors.map(sp => (
            <div key={sp.name} style={{
              display: 'grid', gridTemplateColumns: '200px 130px 1fr 200px',
              gap: 12, padding: '10px 4px', borderBottom: '1px dashed var(--border)',
              alignItems: 'center',
            }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>{sp.name}</span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
                <Globe size={12} /> {sp.country}
              </span>
              <span style={{ fontSize: 12, color: 'var(--ink-2)' }}>{sp.purpose}</span>
              <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                <Lock size={10} style={{ display: 'inline', marginInlineEnd: 4 }} />
                {sp.safeguard}
              </span>
            </div>
          ))}
        </div>
      </Card>

      {/* Public breach log */}
      <Card title={t('portalTrustBreachLogTitle')} Icon={AlertOctagon}
        action={<Badge tone={breachLog.length === 0 ? 'success' : 'warning'}>
          {breachLog.length} {breachLog.length === 1 ? t('portalTrustEntry') : t('portalTrustEntries')}
        </Badge>}>
        <p style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('portalTrustBreachLogBlurb')}
        </p>
        {breachLog.length === 0 ? (
          <div style={{
            background: 'var(--success-bg)', padding: 12, borderRadius: 'var(--r-sm)',
            color: 'var(--success-fg)', fontSize: 13, textAlign: 'center',
          }}>
            {t('portalTrustNoBreach')}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {breachLog.map(b => (
              <div key={b.refCode} style={{
                display: 'grid', gridTemplateColumns: '110px 100px 1fr 130px 90px',
                gap: 12, padding: '12px 4px', borderBottom: '1px dashed var(--border)',
                alignItems: 'flex-start',
              }}>
                <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{b.date}</span>
                <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-2)' }}>{b.refCode}</span>
                <span style={{ fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.5 }}>{b.summary}</span>
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {t('portalTrustSubjects')}: {b.subjectsAffected}
                </span>
                <Badge tone={b.status === 'CLOSED' ? 'success' : 'warning'}>{b.status}</Badge>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Customer-rights links */}
      <Card title={t('portalTrustRightsTitle')} Icon={ShieldCheck}>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12,
        }}>
          <RightsTile to="/portal/dsr"     Icon={Database}      title={t('portalTrustRightsDsr')}     blurb={t('portalTrustRightsDsrBlurb')} />
          <RightsTile to="/portal/speakup" Icon={MessageSquare} title={t('portalTrustRightsSpeakUp')} blurb={t('portalTrustRightsSpeakUpBlurb')} />
          <RightsTile to="/portal/track"   Icon={Send}          title={t('portalTrustRightsTrack')}   blurb={t('portalTrustRightsTrackBlurb')} />
        </div>
      </Card>

      {/* Contact */}
      <Card title={t('portalTrustContactTitle')} Icon={BookOpen}>
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12, fontSize: 13,
        }}>
          <Contact role={t('portalTrustContactDpo')}      email="dpo@tss.sa" />
          <Contact role={t('portalTrustContactCiso')}     email="security@tss.sa" />
          <Contact role={t('portalTrustContactLegal')}    email="legal@tss.sa" />
          <Contact role={t('portalTrustContactRegulator')} email="compliance@tss.sa" />
        </div>
      </Card>
    </PublicShell>
  );
}

function Stat({ Icon, value, label }: { Icon: typeof Award; value: string | number; label: string }) {
  return (
    <div>
      <Icon size={26} color="var(--primary)" strokeWidth={1.8} />
      <div style={{ fontSize: 26, fontWeight: 700, color: 'var(--ink-1)', marginTop: 6 }}>{value}</div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginTop: 2 }}>
        {label}
      </div>
    </div>
  );
}

function RightsTile({ to, Icon, title, blurb }: { to: string; Icon: typeof Award; title: string; blurb: string }) {
  return (
    <Link to={to} style={{
      display: 'block', textDecoration: 'none', color: 'inherit',
      padding: 14, borderRadius: 'var(--r-sm)',
      background: 'var(--surface-2)', border: '1px solid var(--border)',
    }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 6 }}>
        <Icon size={18} color="var(--primary)" />
        <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-1)' }}>{title}</span>
      </div>
      <p style={{ margin: 0, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.5 }}>{blurb}</p>
      <div style={{ marginTop: 8, fontSize: 12, fontWeight: 600, color: 'var(--primary)', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
        <FileText size={12} /> <span>→</span> <ArrowRight size={12} />
      </div>
    </Link>
  );
}

function Contact({ role, email }: { role: string; email: string }) {
  return (
    <div style={{ padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{role}</div>
      <a href={`mailto:${email}`} className="mono" style={{ fontSize: 13, color: 'var(--primary)', textDecoration: 'none' }}>
        {email}
      </a>
    </div>
  );
}
