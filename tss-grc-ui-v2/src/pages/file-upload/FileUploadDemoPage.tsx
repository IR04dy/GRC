import { useEffect, useState } from 'react';
import { Upload, ShieldCheck, FileCheck2, AlertOctagon, Database, Globe } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { FileUploadField } from '../../components/ui/FileUploadField';
import { useI18n } from '../../lib/i18n';
import { aggregateFiles, formatBytes, subscribe } from '../../lib/fileUpload';
import { getUser } from '../../lib/auth';

/**
 * FileUploadDemoPage — Sprint 6 production hardening.
 *
 * Standalone surface where you can exercise the file upload service
 * without needing to navigate into Evidence / DSR / TPRM. Drop files,
 * see the virus-scan stub mark them clean / suspicious / infected,
 * confirm Saudi-residency badges, download to round-trip, delete to
 * close the loop.
 *
 * The same `<FileUploadField />` component is what every record-detail
 * page will embed — Evidence, DSR, breach attachments, vendor SOC 2.
 */
export function FileUploadDemoPage() {
  const { t } = useI18n();
  const user = getUser();
  const userRef = user?.username ?? 'demo@tss.sa';
  const [stats, setStats] = useState(() => aggregateFiles());

  useEffect(() => subscribe(() => setStats(aggregateFiles())), []);

  return (
    <Shell title={t('fuTitle')} sub={t('fuSub')}>
      <PageHeader
        eyebrow={t('fuEyebrow')}
        title={t('fuTitle')}
        sub={t('fuPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Database}      label={t('fuKpiTotal')}       value={String(stats.total)} tone="primary" />
        <Kpi Icon={ShieldCheck}    label={t('fuKpiClean')}        value={String(stats.cleanCount)} tone="success" />
        <Kpi Icon={AlertOctagon}   label={t('fuKpiSuspicious')}    value={String(stats.suspiciousCount)} tone={stats.suspiciousCount > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Upload}          label={t('fuKpiSize')}          value={formatBytes(stats.totalBytes)} tone="info" />
        <Kpi Icon={FileCheck2}     label={t('fuKpiAttachments')}   value={String(stats.uniqueAttachments)} tone="info" />
      </div>

      <Card title={t('fuTryTitle')} Icon={Upload}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.6 }}>
          {t('fuTryNarrative')}
        </div>
        <FileUploadField
          attachedTo="demo/playground"
          uploadedBy={userRef}
          residency="KSA"
          retentionDays={1095}
        />
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card title={t('fuPolicyTitle')} Icon={Globe}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('fuPolicySub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            <PolicyTile
              title={t('fuPolicyVirusTitle')}
              body={t('fuPolicyVirusBody')}
              badge="EICAR + ClamAV"
              tone="success"
            />
            <PolicyTile
              title={t('fuPolicyHashTitle')}
              body={t('fuPolicyHashBody')}
              badge="SHA-256"
              tone="info"
            />
            <PolicyTile
              title={t('fuPolicyResidencyTitle')}
              body={t('fuPolicyResidencyBody')}
              badge="PDPL Art. 29"
              tone="warning"
            />
            <PolicyTile
              title={t('fuPolicyRetentionTitle')}
              body={t('fuPolicyRetentionBody')}
              badge="PDPL Art. 18"
              tone="info"
            />
          </div>
        </Card>
      </div>
    </Shell>
  );
}

function PolicyTile({ title, body, badge, tone }: { title: string; body: string; badge: string; tone: 'success' | 'warning' | 'info' }) {
  return (
    <div style={{
      padding: 14, borderRadius: 'var(--r-md)',
      background: 'var(--surface-2)', border: '1px solid var(--border)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <Badge tone={tone}>{badge}</Badge>
      </div>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 4 }}>{title}</div>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', lineHeight: 1.6 }}>{body}</div>
    </div>
  );
}
