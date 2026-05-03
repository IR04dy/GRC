import { useEffect, useState } from 'react';
import { GitBranch, ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card } from './Card';
import { Badge, type BadgeTone } from './Badge';
import { changeApi, type LinkedChangeView } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';

const STATUS_TONE: Record<string, BadgeTone> = {
  DRAFT: 'neutral', SUBMITTED: 'info', IMPACT_ASSESSED: 'info',
  CAB_PENDING: 'warning', CAB_APPROVED: 'primary', CAB_REJECTED: 'danger',
  EMERGENCY_APPROVED: 'accent',
  SCHEDULED: 'primary', IMPLEMENTING: 'accent', IMPLEMENTED: 'success',
  ROLLED_BACK: 'danger', POST_REVIEW: 'info', CLOSED: 'success', CANCELLED: 'neutral',
};
const TYPE_TONE: Record<string, BadgeTone> = {
  STANDARD: 'success', NORMAL: 'primary', EMERGENCY: 'danger',
};

/**
 * Drop-in card listing the change requests linked to a given entity.
 * Use this from any module page to show "linked changes" without bespoke
 * code per module.
 *
 * Example:
 *   <LinkedChangesPanel entityType="RISK" entityId={risk.id} />
 */
export function LinkedChangesPanel({ entityType, entityId }: { entityType: string; entityId: number }) {
  const { t, lang } = useI18n();
  const [links, setLinks] = useState<LinkedChangeView[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) return;
    setLoading(true);
    changeApi.linksForEntity(entityType, entityId)
      .then(setLinks)
      .catch(() => setLinks([]))
      .finally(() => setLoading(false));
  }, [entityType, entityId]);

  return (
    <Card title={t('changesLinkedTitle')} Icon={GitBranch}
          action={links.length > 0 ? <Badge tone="primary">{links.length}</Badge> : null}>
      {loading
        ? <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>…</div>
        : links.length === 0
          ? <div style={{ color: 'var(--ink-3)', fontSize: 12 }}>{t('changesLinkedEmpty')}</div>
          : (
            <div style={{ display: 'grid', gap: 6 }}>
              {links.map(l => (
                <div key={l.linkId} style={{
                  display: 'flex', alignItems: 'center', gap: 8,
                  padding: 8, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                  fontSize: 12,
                }}>
                  <span className="mono" style={{ color: 'var(--ink-3)', minWidth: 90 }}>{l.changeCode ?? `#${l.changeId}`}</span>
                  <Badge tone={TYPE_TONE[l.changeType] ?? 'neutral'}>{l.changeType}</Badge>
                  <Badge tone={STATUS_TONE[l.status] ?? 'neutral'} dot>{l.status.replace(/_/g, ' ')}</Badge>
                  <span style={{ flex: 1, minWidth: 0, color: 'var(--ink-1)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {lang === 'ar' && l.titleAr ? l.titleAr : l.titleEn}
                  </span>
                  <Badge tone="info">{l.linkType.replace(/_/g, ' ')}</Badge>
                  <Link to={`/changes`} style={{ color: 'var(--primary)', display: 'inline-flex', alignItems: 'center', gap: 2, fontSize: 11 }}>
                    {t('changesLinkedView')} <ChevronRight size={11} />
                  </Link>
                </div>
              ))}
            </div>
          )
      }
    </Card>
  );
}
