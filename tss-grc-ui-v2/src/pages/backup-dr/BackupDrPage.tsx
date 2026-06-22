import { useMemo } from 'react';
import {
  Database, Cloud, Calendar, ShieldCheck, Clock, Lock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { HijriDate } from '../../components/ui/HijriDate';
import {
  BACKUP_JOBS, REPLICATION_LINKS, DR_DRILLS,
  HEALTH_TONE, REPL_TONE, DRILL_TONE,
  aggregateBackupDr,
} from '../../lib/backupDr';

/**
 * BackupDrPage — Sprint 6 production hardening.
 *
 * SAMA Operational Resilience Framework §3 evidence dashboard.
 *   • Backup jobs — last run, size, retention, encryption
 *   • Replication — KSA Riyadh ↔ Jeddah lag vs RPO target
 *   • DR-drill calendar — last verified RTO + lessons-learned + planned
 *
 * Drives the production-readiness checklist's Backup + DR row.
 */
export function BackupDrPage() {
  const { t, lang } = useI18n();
  const stats = useMemo(() => aggregateBackupDr(), []);

  return (
    <Shell title={t('drTitle')} sub={t('drSub')}>
      <PageHeader
        eyebrow={t('drEyebrow')}
        title={t('drTitle')}
        sub={t('drPageSub')}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Database}     label={t('drKpiBackupJobs')}    value={String(stats.totalJobs)} tone="primary" />
        <Kpi Icon={ShieldCheck}   label={t('drKpiBackupOk')}      value={String(stats.okJobs)} tone={stats.failedJobs > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Cloud}          label={t('drKpiReplication')}   value={`${stats.inSyncLinks} / ${stats.totalReplLinks}`} tone={stats.laggingLinks > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Calendar}       label={t('drKpiDrills')}        value={String(stats.drillsCompleted)} tone="info" to="/dr-plans" />
        <Kpi Icon={Clock}          label={t('drKpiLastRto')}       value={stats.lastDrillRtoMin ? `${stats.lastDrillRtoMin}m` : '—'} tone="success" to="/dr-plans" />
      </div>

      {/* Backup jobs */}
      <Card title={t('drBackupsTitle')} Icon={Database}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('drBackupsSub')}
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
          <thead>
            <tr>
              <th style={th}>{t('drTblJob')}</th>
              <th style={th}>{t('drTblKind')}</th>
              <th style={th}>{t('drTblTarget')}</th>
              <th style={th}>{t('drTblLastRun')}</th>
              <th style={th}>{t('drTblSize')}</th>
              <th style={th}>{t('drTblRetention')}</th>
              <th style={th}>{t('drTblEncrypted')}</th>
              <th style={th}>{t('drTblHealth')}</th>
            </tr>
          </thead>
          <tbody>
            {BACKUP_JOBS.map(job => (
              <tr key={job.code} style={{ borderBottom: '1px dashed var(--border)' }}>
                <td style={td}>
                  <strong>{lang === 'ar' ? job.nameAr : job.nameEn}</strong>
                  <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{job.code}</div>
                </td>
                <td style={td}>{job.kind}</td>
                <td style={{ ...td, fontSize: 11 }}>{job.target}</td>
                <td style={td}>
                  {job.lastBackupAt
                    ? <HijriDate iso={job.lastBackupAt.slice(0, 10)} layout="inline" mono />
                    : '—'}
                </td>
                <td style={{ ...td, fontFamily: 'var(--font-mono)' }}>{job.sizeGb?.toFixed(1)} GB</td>
                <td style={{ ...td, fontFamily: 'var(--font-mono)' }}>{Math.round(job.retentionDays / 365)} y</td>
                <td style={td}>
                  {job.encrypted
                    ? <Badge tone="success"><Lock size={10} style={{ marginInlineEnd: 4, verticalAlign: 'middle' }} />AES-256</Badge>
                    : <Badge tone="danger">No</Badge>}
                </td>
                <td style={td}>
                  <Badge tone={HEALTH_TONE[job.health]} dot>{job.health}</Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {/* Replication */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('drReplicationTitle')} Icon={Cloud}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('drReplicationSub')}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 12 }}>
            {REPLICATION_LINKS.map(link => {
              const onTarget = link.lagSeconds <= link.rpoTargetSeconds;
              return (
                <div key={link.code} style={{
                  padding: 14, borderRadius: 'var(--r-md)',
                  background: onTarget ? 'var(--success-bg)' : 'var(--warning-bg)',
                  border: `1px solid ${onTarget ? 'var(--success-fg)' : 'var(--warning-fg)'}`,
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                    <span className="mono" style={{ fontSize: 11 }}>{link.code}</span>
                    <Badge tone={REPL_TONE[link.status]} dot>{link.status}</Badge>
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--ink-1)', lineHeight: 1.5 }}>
                    <span>{lang === 'ar' ? link.fromRegionAr : link.fromRegionEn}</span>
                    <span style={{ color: 'var(--ink-3)' }}> → </span>
                    <span>{lang === 'ar' ? link.toRegionAr : link.toRegionEn}</span>
                  </div>
                  <div style={{ display: 'flex', gap: 12, marginTop: 8, fontSize: 11 }}>
                    <div>
                      <div style={{ color: 'var(--ink-3)', fontSize: 10, textTransform: 'uppercase' }}>{t('drLag')}</div>
                      <div className="mono" style={{ fontSize: 16, fontWeight: 700, color: onTarget ? 'var(--success-fg)' : 'var(--warning-fg)' }}>{link.lagSeconds}s</div>
                    </div>
                    <div>
                      <div style={{ color: 'var(--ink-3)', fontSize: 10, textTransform: 'uppercase' }}>{t('drRpoTarget')}</div>
                      <div className="mono" style={{ fontSize: 16, fontWeight: 700 }}>{link.rpoTargetSeconds}s</div>
                    </div>
                  </div>
                  {link.lastVerifiedAt && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 8 }}>
                      <Clock size={10} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
                      {t('drLastVerified')}: {link.lastVerifiedAt.slice(11, 16)}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      {/* DR drills */}
      <div style={{ marginTop: 16 }}>
        <Card title={t('drDrillsTitle')} Icon={Calendar}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
            {t('drDrillsSub')}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {DR_DRILLS.map(drill => (
              <div key={drill.code} style={{
                padding: '14px 0', borderBottom: '1px dashed var(--border)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                  <span className="mono" style={{ fontSize: 11 }}>{drill.code}</span>
                  {drill.result && <Badge tone={DRILL_TONE[drill.result]}>{drill.result.replace('_', ' ')}</Badge>}
                  {!drill.conductedAt && <Badge tone="info">{t('drPlanned')}</Badge>}
                  {drill.actualRtoMinutes != null && (
                    <Badge tone={drill.actualRtoMinutes <= drill.targetRtoMinutes ? 'success' : 'warning'}>
                      RTO {drill.actualRtoMinutes}m / {drill.targetRtoMinutes}m
                    </Badge>
                  )}
                </div>
                <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
                  {lang === 'ar' ? drill.titleAr : drill.titleEn}
                </div>
                <div style={{ display: 'flex', gap: 16, fontSize: 11, color: 'var(--ink-3)', marginBottom: 6 }}>
                  <div>
                    <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('drScheduled')}</div>
                    <HijriDate iso={drill.scheduledFor} layout="stacked" mono />
                  </div>
                  {drill.conductedAt && (
                    <div>
                      <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('drConducted')}</div>
                      <HijriDate iso={drill.conductedAt} layout="stacked" mono />
                    </div>
                  )}
                  {drill.certifiedBy && (
                    <div>
                      <div style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>{t('drCertifiedBy')}</div>
                      <span style={{ color: 'var(--ink-2)' }}>{drill.certifiedBy}</span>
                    </div>
                  )}
                </div>
                {(drill.lessonsEn || drill.lessonsAr) && (
                  <div style={{
                    marginTop: 8, padding: 10,
                    background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                    borderInlineStart: '3px solid var(--primary)',
                    fontSize: 12, lineHeight: 1.6, color: 'var(--ink-2)',
                  }}>
                    <div style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--ink-3)', marginBottom: 4 }}>
                      {t('drLessons')}
                    </div>
                    {lang === 'ar' ? drill.lessonsAr : drill.lessonsEn}
                  </div>
                )}
              </div>
            ))}
          </div>
        </Card>
      </div>
    </Shell>
  );
}

const th = {
  padding: '8px 10px', textAlign: 'start' as const, fontSize: 10,
  textTransform: 'uppercase' as const, letterSpacing: '0.05em',
  color: 'var(--ink-3)', background: 'var(--surface-2)',
  borderBottom: '2px solid var(--ink-3)',
};
const td = { padding: '10px', verticalAlign: 'middle' as const, lineHeight: 1.5 };
