import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { GitBranch, FileText } from 'lucide-react';
import { HijriDate } from '../ui/HijriDate';
import { useI18n } from '../../lib/i18n';
import {
  versionsForPolicy, STAGE_LABEL, STAGE_TONE, currentVersion,
} from '../../lib/policyLifecycle';

/**
 * PolicyVersionHistory — Sprint 6.
 *
 * Per-version timeline showing: version number, status, effective period,
 * bilingual change summary, author, optional content excerpt. Most-recent
 * first; a "current" badge marks the active version. Hijri dates render
 * alongside Gregorian on every effective-from / effective-to row.
 */
export function PolicyVersionHistory({ policyId }: { policyId: number }) {
  const { t, lang } = useI18n();
  const versions = versionsForPolicy(policyId);
  const current = currentVersion(versions);

  if (versions.length === 0) {
    return (
      <Card title={t('policyVersionsTitle')} Icon={GitBranch}>
        <div style={{ padding: 24, textAlign: 'center', fontSize: 12, color: 'var(--ink-3)' }}>
          {t('policyVersionsEmpty')}
        </div>
      </Card>
    );
  }

  return (
    <Card title={t('policyVersionsTitle')} Icon={GitBranch}
      action={<Badge tone="primary">{versions.length} {t('policyVersionsCountSuffix')}</Badge>}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginBottom: 12 }}>
        {t('policyVersionsSub')}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {versions.map(v => {
          const isCurrent = current?.version === v.version;
          return (
            <div key={v.version} style={{
              padding: '14px 0',
              borderBottom: '1px dashed var(--border)',
              display: 'grid',
              gridTemplateColumns: '110px 1fr',
              gap: 14,
              alignItems: 'flex-start',
            }}>
              <div>
                <div style={{
                  fontSize: 22,
                  fontWeight: 800,
                  color: isCurrent ? 'var(--primary)' : 'var(--ink-2)',
                  fontFamily: 'var(--font-mono)',
                }}>v{v.version}</div>
                {isCurrent && (
                  <Badge tone="primary">{t('policyVersionCurrent')}</Badge>
                )}
                <div style={{ marginTop: 6 }}>
                  <Badge tone={STAGE_TONE[v.status]}>
                    {lang === 'ar' ? STAGE_LABEL[v.status].ar : STAGE_LABEL[v.status].en}
                  </Badge>
                </div>
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 13, color: 'var(--ink-1)', lineHeight: 1.6 }}>
                  {lang === 'ar' ? v.changeSummaryAr : v.changeSummaryEn}
                </div>
                <div style={{ display: 'flex', gap: 16, fontSize: 11, color: 'var(--ink-3)', marginTop: 8, flexWrap: 'wrap' }}>
                  {v.effectiveFrom && (
                    <div>
                      <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('policyVersionEffectiveFrom')}</div>
                      <HijriDate iso={v.effectiveFrom} layout="stacked" mono />
                    </div>
                  )}
                  {v.effectiveTo && (
                    <div>
                      <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('policyVersionEffectiveTo')}</div>
                      <HijriDate iso={v.effectiveTo} layout="stacked" mono />
                    </div>
                  )}
                  {v.approvedBy && (
                    <div>
                      <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('policyVersionApprover')}</div>
                      <span style={{ color: 'var(--ink-2)' }}>{v.approvedBy}</span>
                    </div>
                  )}
                  <div>
                    <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('policyVersionAuthor')}</div>
                    <span style={{ color: 'var(--ink-2)' }}>{lang === 'ar' ? v.authorAr : v.authorEn}</span>
                  </div>
                </div>
                {(v.excerptEn || v.excerptAr) && (
                  <div style={{
                    marginTop: 10,
                    padding: 10,
                    background: 'var(--surface-2)',
                    borderRadius: 'var(--r-sm)',
                    borderInlineStart: '3px solid var(--primary)',
                    fontSize: 12,
                    lineHeight: 1.6,
                    color: 'var(--ink-2)',
                  }}>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      <FileText size={10} /> {t('policyVersionExcerpt')}
                    </div>
                    <div>{lang === 'ar' && v.excerptAr ? v.excerptAr : v.excerptEn}</div>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}
