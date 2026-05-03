import { Link } from 'react-router-dom';
import { Landmark, ArrowRight } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { AUTHORITIES, type AuthorityDto } from '../../lib/authorities';
import { useI18n } from '../../lib/i18n';

/**
 * Directory of every regulatory + advisory authority the platform tracks.
 * Saudi authorities first, then international benchmarks. Each card opens
 * the authority's workspace where the auditor can drill in.
 */
export function AuthoritiesIndexPage() {
  const { lang } = useI18n();
  const saudi  = AUTHORITIES.filter(a => a.authorityType === 'SAUDI_REGULATORY' || a.authorityType === 'SAUDI_ADVISORY');
  const intl   = AUTHORITIES.filter(a => a.authorityType === 'INTERNATIONAL');

  return (
    <Shell title="Regulatory Authorities" sub="Saudi regulators + international benchmarks">
      <PageHeader
        eyebrow="Authority Document Library"
        title="Regulatory Authorities"
        sub="Single source of truth for every regulator + standard the platform tracks. Each authority opens an auditor workspace with cited controls, anchored evidence, findings and engagements."
      />

      <Card title="Saudi authorities" Icon={Landmark}
        action={<Badge tone="primary">{saudi.length}</Badge>}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 8,
        }}>
          {saudi.map(a => <AuthorityCard key={a.code} a={a} lang={lang} />)}
        </div>
      </Card>

      <Card title="International benchmarks" Icon={Landmark}
        action={<Badge tone="primary">{intl.length}</Badge>}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 8,
        }}>
          {intl.map(a => <AuthorityCard key={a.code} a={a} lang={lang} />)}
        </div>
      </Card>
    </Shell>
  );
}

function AuthorityCard({ a, lang }: { a: AuthorityDto; lang: string }) {
  return (
    <Link to={`/authority/${a.code}`} style={{
      display: 'grid',
      gridTemplateColumns: '4px 44px 1fr auto',
      gap: 10,
      padding: '10px 12px 10px 0',
      borderRadius: 'var(--r-sm)',
      border: '1px solid var(--border)',
      background: 'var(--surface-1)',
      textDecoration: 'none',
      color: 'inherit',
      alignItems: 'center',
      transition: 'box-shadow var(--dur-fast) var(--ease-standard), border-color var(--dur-fast) var(--ease-standard)',
    }}
      onMouseEnter={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.borderColor = 'var(--primary-300)';
        el.style.boxShadow   = '0 2px 8px rgba(0,0,0,0.06)';
      }}
      onMouseLeave={(e) => {
        const el = e.currentTarget as HTMLElement;
        el.style.borderColor = 'var(--border)';
        el.style.boxShadow   = '';
      }}>
      <span style={{ alignSelf: 'stretch', background: a.colorToken }} />
      <div style={{
        width: 40, height: 40,
        borderRadius: 'var(--r-sm)',
        background: a.colorToken, color: '#fff',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontWeight: 800, fontSize: 12,
        marginInlineStart: 4,
      }}>
        {a.code.slice(0, 4)}
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--ink-1)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {lang === 'ar' ? a.nameAr : a.nameEn}
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {a.frameworkCodes.length} framework{a.frameworkCodes.length !== 1 ? 's' : ''} · {a.domains.slice(0, 2).join(' · ')}
        </div>
      </div>
      <ArrowRight size={14} style={{ color: 'var(--ink-3)', flexShrink: 0 }} />
    </Link>
  );
}
