import { Construction } from 'lucide-react';
import { useI18n } from '../lib/i18n';
import { Shell } from '../components/layout/Shell';
import { PageHeader } from '../components/layout/PageHeader';
import { Card } from '../components/ui/Card';

export function PlaceholderPage({ moduleKey }: { moduleKey: string }) {
  const { t } = useI18n();
  const moduleName = t(moduleKey, moduleKey);
  return (
    <Shell title={moduleName}>
      <PageHeader title={moduleName} sub={t('comingSoonSub')} />
      <Card>
        <div className="placeholder">
          <Construction size={56} strokeWidth={1.4} />
          <h4>{t('comingSoon')}</h4>
          <p>{t('comingSoonSub')}</p>
        </div>
      </Card>
    </Shell>
  );
}
