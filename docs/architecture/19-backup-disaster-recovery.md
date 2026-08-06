# 19 - Backup and Disaster Recovery
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## Purpose

This document defines the backup strategy and disaster recovery procedures for the TSS GRC platform. A GRC platform stores regulatory compliance evidence, risk assessments, audit findings, and policy documents that are legally required to be preserved. Data loss is not acceptable, and recovery procedures must be well-documented and tested.

---

## 1. Recovery Objectives

### 1.1 Recovery Point Objective (RPO)

**Target**: < 1 hour of data loss.

**Achieved via**: SQL Server transaction log backups every 15 minutes. In the worst case, the most recent 15 minutes of transactions could be lost. In practice, point-in-time recovery can restore to any transaction within the last backup chain.

### 1.2 Recovery Time Objective (RTO)

**Target**: < 4 hours from failure detection to full service restoration.

**Breakdown**:

| Phase | Duration | Activities |
|---|---|---|
| Detection | 0-15 min | Health check failure, alert triggered |
| Assessment | 15-30 min | Identify failure scope, determine recovery path |
| Database restore | 1-2 hours | Restore full + differential + transaction logs |
| Application startup | 15-30 min | Deploy application, run health checks |
| Verification | 30-60 min | Verify data integrity, functional testing |
| **Total** | **~2-4 hours** | |

---

## 2. Backup Strategy

### 2.1 SQL Server Backup Schedule

**Decision**: Three-tier backup strategy using SQL Server native backup capabilities.

| Backup Type | Frequency | Retention | Purpose |
|---|---|---|---|
| Full backup | Daily at 01:00 AST | 30 days | Complete database snapshot |
| Differential backup | Every 6 hours (07:00, 13:00, 19:00) | 7 days | Changes since last full backup |
| Transaction log backup | Every 15 minutes | 7 days | Granular point-in-time recovery |

**Rationale**: This schedule provides RPO < 15 minutes while keeping backup storage manageable. Daily full backups serve as recovery baselines. Differentials reduce restore time compared to replaying an entire day of transaction logs. 15-minute log backups provide near-continuous protection.

### 2.2 Backup Implementation

```sql
-- Full backup (daily, 01:00 AST)
BACKUP DATABASE [tss_grc]
TO DISK = N'D:\Backups\tss_grc_full_$(date).bak'
WITH
    COMPRESSION,
    CHECKSUM,
    STATS = 10,
    NAME = N'tss_grc-Full Database Backup',
    DESCRIPTION = N'Daily full backup';

-- Differential backup (every 6 hours)
BACKUP DATABASE [tss_grc]
TO DISK = N'D:\Backups\tss_grc_diff_$(datetime).bak'
WITH
    DIFFERENTIAL,
    COMPRESSION,
    CHECKSUM,
    STATS = 10,
    NAME = N'tss_grc-Differential Backup';

-- Transaction log backup (every 15 minutes)
BACKUP LOG [tss_grc]
TO DISK = N'D:\Backups\tss_grc_log_$(datetime).trn'
WITH
    COMPRESSION,
    CHECKSUM,
    STATS = 10,
    NAME = N'tss_grc-Transaction Log Backup';
```

### 2.3 Backup Compression and Encryption

**Decision**: Compression enabled on all backups; encryption enabled for offsite copies.

| Feature | Setting | Rationale |
|---|---|---|
| Compression | Enabled | Reduces backup size by 60-80%, reducing storage cost and transfer time |
| Checksum | Enabled | Detects backup corruption early |
| Encryption | Offsite copies only | Protects data in transit and at rest on external media |

**Backup encryption** (for offsite copies):

```sql
-- Create certificate for backup encryption
CREATE MASTER KEY ENCRYPTION BY PASSWORD = '${MASTER_KEY_PASSWORD}';
CREATE CERTIFICATE TSSBackupCert WITH SUBJECT = 'TSS GRC Backup Encryption Certificate';

-- Encrypted backup
BACKUP DATABASE [tss_grc]
TO DISK = N'\\offsite\backups\tss_grc_full_encrypted.bak'
WITH
    COMPRESSION,
    ENCRYPTION (ALGORITHM = AES_256, SERVER CERTIFICATE = TSSBackupCert);
```

---

## 3. Backup Storage

### 3.1 Storage Locations

**Decision**: Local primary + offsite secondary copy.

| Location | Type | Purpose | Retention |
|---|---|---|---|
| Local disk (D:\Backups) | Primary | Fast restore for common scenarios | 7 days (full), 3 days (diff/log) |
| Network share / NAS | Secondary | Protection against local disk failure | 30 days |
| Cloud storage (future) | Tertiary | Protection against site failure | 90 days |

**Rationale**: Local disk provides the fastest restore time for the most common failure scenarios (database corruption, accidental data deletion). Network share protects against local disk failure. Cloud storage (Azure Blob, S3) provides geographic redundancy for disaster scenarios affecting the entire site.

### 3.2 Backup Transfer

```
Local backup
  → Copy to network share (scheduled task, within 30 min of backup completion)
  → Copy to cloud storage (future, via azcopy or aws s3 sync)
```

### 3.3 Storage Sizing

Assumptions: 50 GB database, 70% compression ratio.

| Backup Type | Compressed Size | Daily Volume | 30-Day Storage |
|---|---|---|---|
| Full (1/day) | ~15 GB | 15 GB | 450 GB |
| Differential (3/day) | ~3-8 GB avg | 15 GB | 105 GB |
| Transaction log (96/day) | ~50 MB avg | 5 GB | 35 GB |
| **Total** | | **~35 GB/day** | **~590 GB** |

---

## 4. File Backup

### 4.1 Application File Storage

In addition to the database, the following file-based data must be backed up:

| Data | Location | Backup Method |
|---|---|---|
| Uploaded attachments | `${tss.storage.upload-dir}` | File system backup (rsync or robocopy) |
| Generated reports | `${tss.storage.reports-dir}` | File system backup |
| Organization logos | `${tss.storage.branding-dir}` | File system backup |
| Exported data files | `${tss.storage.export-dir}` | File system backup (short retention) |

**Schedule**: File backup runs immediately after the daily full database backup.

```bash
# File backup script (Linux)
#!/bin/bash
BACKUP_DATE=$(date +%Y%m%d)
SOURCE_DIR="/opt/tss/storage"
BACKUP_DIR="/backup/tss/files/${BACKUP_DATE}"

rsync -az --delete "${SOURCE_DIR}/" "${BACKUP_DIR}/"
```

```powershell
# File backup script (Windows)
$backupDate = Get-Date -Format "yyyyMMdd"
$sourceDir = "E:\tss\storage"
$backupDir = "D:\Backups\files\$backupDate"

robocopy $sourceDir $backupDir /MIR /Z /MT:4
```

### 4.2 File-Database Consistency

**Important**: File backups and database backups must be coordinated. The database contains file metadata (paths, checksums) that must match the actual files on disk.

**Strategy**: Database backup runs first, then file backup. If a file is uploaded between the database backup and file backup, the file exists on disk but the metadata might reference it from the point-in-time database restore. This is acceptable because the file exists (no data loss); the metadata may need minor reconciliation.

---

## 5. Application Configuration Backup

### 5.1 Configuration to Back Up

| Item | Location | Backup Method |
|---|---|---|
| application.yml | Application artifact | Included in deployment artifact |
| Environment variables | Server configuration | Documented in operations runbook |
| SSL/TLS certificates | Certificate store / file system | Backed up with file system backup |
| Encryption keys | External key file | Separate, restricted backup (not with database) |
| Flyway migration scripts | Application artifact | Source control (Git) |

### 5.2 Encryption Key Backup

**Critical**: Encryption keys must be backed up separately from the database. Without the key, application-level encrypted fields (MFA secrets, integration passwords) cannot be decrypted.

| Item | Backup Location | Access |
|---|---|---|
| AES-256 encryption key | Separate secure storage (USB, HSM, sealed envelope) | Restricted to designated key custodians |
| TDE certificate | SQL Server backup with password protection | DBA access only |
| Backup encryption certificate | Separate from encrypted backups | Stored with key custodians |

---

## 6. Backup Verification

### 6.1 Automated Restore Tests

**Decision**: Monthly automated restore tests to a test environment.

**Rationale**: Backups that cannot be restored are worthless. Regular automated testing catches issues early: corrupted backup files, missing transaction log chains, changed encryption keys, storage permission problems.

**Monthly test procedure**:

```
1. Select most recent full backup + differential + logs
2. Restore to isolated test SQL Server instance
   RESTORE DATABASE [tss_grc_test]
   FROM DISK = N'latest_full.bak'
   WITH NORECOVERY, REPLACE;

   RESTORE DATABASE [tss_grc_test]
   FROM DISK = N'latest_diff.bak'
   WITH NORECOVERY;

   RESTORE LOG [tss_grc_test]
   FROM DISK = N'latest_log_1.trn'
   WITH NORECOVERY;
   -- ... apply remaining logs ...

   RESTORE DATABASE [tss_grc_test]
   WITH RECOVERY;

3. Run DBCC CHECKDB to verify integrity
   DBCC CHECKDB ([tss_grc_test]) WITH NO_INFOMSGS, ALL_ERRORMSGS;

4. Run application smoke tests against restored database
   - User can log in
   - Risk list loads with expected count
   - Dashboard renders with data
   - Audit log contains recent entries

5. Record test results in operations log
6. Clean up test database
```

### 6.2 Backup Monitoring

| Check | Frequency | Alert Condition |
|---|---|---|
| Backup job completion | After each backup | Job failure or non-completion |
| Backup file existence | Daily | Expected backup file missing |
| Backup file size | Daily | Suspiciously small (< 50% of expected) |
| Offsite copy status | Daily | Copy not completed within 1 hour |
| Restore test result | Monthly | Test failure or integrity errors |
| Backup storage capacity | Weekly | < 20% free space on backup volume |

---

## 7. Disaster Scenarios and Recovery Procedures

### 7.1 Scenario 1: Database Corruption

**Cause**: Hardware failure, SQL Server bug, storage corruption.

**Detection**: Application errors, DBCC CHECKDB failures, SQL Server error log.

**Recovery procedure**:

```
1. Stop the application (prevent further writes to corrupted database)
2. Assess corruption scope using DBCC CHECKDB
3. If minor corruption:
   a. Attempt DBCC CHECKDB with REPAIR_ALLOW_DATA_LOSS (last resort)
   b. Verify data integrity after repair
4. If major corruption:
   a. Restore from latest full backup
      RESTORE DATABASE [tss_grc] FROM DISK = N'latest_full.bak'
      WITH NORECOVERY, REPLACE;
   b. Apply latest differential
      RESTORE DATABASE [tss_grc] FROM DISK = N'latest_diff.bak'
      WITH NORECOVERY;
   c. Apply all transaction logs up to point of corruption
      RESTORE LOG [tss_grc] FROM DISK = N'log1.trn' WITH NORECOVERY;
      RESTORE LOG [tss_grc] FROM DISK = N'log2.trn' WITH NORECOVERY;
      -- ... repeat for all available logs ...
      RESTORE DATABASE [tss_grc] WITH RECOVERY;
5. Run DBCC CHECKDB to verify restored database
6. Start application, verify health checks pass
7. Notify users, verify data integrity with stakeholders
8. Post-incident report within 24 hours
```

**Estimated recovery time**: 2-3 hours.

### 7.2 Scenario 2: Server Failure

**Cause**: Hardware failure, OS crash, VM host failure.

**Recovery procedure**:

```
1. Provision replacement server (physical or VM)
2. Install prerequisites: JDK 21, SQL Server 2019, OS configuration
3. Restore SQL Server from offsite backup (see Scenario 1 steps)
4. Restore file storage from backup
   robocopy D:\Backups\files\latest E:\tss\storage /MIR
5. Restore application configuration (environment variables, certificates)
6. Deploy application artifact
7. Start application, verify health checks
8. Update DNS/load balancer to point to new server
9. Verify end-to-end functionality
```

**Estimated recovery time**: 3-4 hours.

### 7.3 Scenario 3: Data Center / Site Loss

**Cause**: Natural disaster, power failure, network outage affecting entire site.

**Recovery procedure**:

```
1. Activate disaster recovery site (if pre-provisioned)
   OR provision new infrastructure at alternate location
2. Retrieve offsite backups (network share or cloud storage)
3. Restore database from offsite backup
4. Restore file storage from offsite backup
5. Deploy application to new infrastructure
6. Restore encryption keys from secure offsite storage
7. Update DNS to point to DR site
8. Verify functionality
9. Communicate to users
```

**Estimated recovery time**: 4-8 hours (with pre-provisioned DR site), 12-24 hours (cold DR).

### 7.4 Scenario 4: Accidental Data Deletion

**Cause**: Admin error, application bug, malicious action.

**Recovery procedure**:

```
1. Identify the time of deletion from audit logs
2. Determine scope: which records, which tables
3. If soft-deleted (is_deleted = true):
   a. Restore via admin interface or direct database UPDATE
4. If hard-deleted:
   a. Restore database to point-in-time BEFORE deletion (on a separate instance)
      RESTORE DATABASE [tss_grc_recovery]
      FROM DISK = N'full.bak'
      WITH NORECOVERY, REPLACE;
      RESTORE LOG [tss_grc_recovery]
      FROM DISK = N'log.trn'
      WITH STOPAT = '2026-03-15T10:25:00', RECOVERY;
   b. Extract deleted records from recovery database
   c. Insert extracted records into production database
   d. Drop recovery database
5. Verify restored data integrity
6. Investigate root cause, update access controls if needed
```

**Estimated recovery time**: 1-2 hours.

---

## 8. Point-in-Time Recovery

### 8.1 SQL Server Transaction Log Restore

**Decision**: Full recovery model on the production database, enabling point-in-time recovery.

```sql
-- Verify recovery model
SELECT name, recovery_model_desc
FROM sys.databases
WHERE name = 'tss_grc';
-- Should return: FULL

-- Point-in-time restore example
RESTORE DATABASE [tss_grc_recovery]
FROM DISK = N'D:\Backups\tss_grc_full.bak'
WITH NORECOVERY, REPLACE,
     MOVE N'tss_grc' TO N'D:\Recovery\tss_grc.mdf',
     MOVE N'tss_grc_log' TO N'D:\Recovery\tss_grc_log.ldf';

RESTORE DATABASE [tss_grc_recovery]
FROM DISK = N'D:\Backups\tss_grc_diff.bak'
WITH NORECOVERY;

RESTORE LOG [tss_grc_recovery]
FROM DISK = N'D:\Backups\tss_grc_log_1.trn'
WITH NORECOVERY;

-- Stop at specific point in time
RESTORE LOG [tss_grc_recovery]
FROM DISK = N'D:\Backups\tss_grc_log_2.trn'
WITH STOPAT = N'2026-03-15 10:25:00',
     RECOVERY;
```

### 8.2 Granularity

- **15-minute granularity**: Guaranteed by transaction log backup frequency
- **Transaction-level granularity**: Within any 15-minute window, SQL Server can restore to any specific transaction using the `STOPAT` parameter
- **Marked transactions** (future): For critical batch operations, SQL Server marked transactions can provide named restore points

---

## 9. DR Testing

### 9.1 Test Schedule

| Test Type | Frequency | Scope | Duration |
|---|---|---|---|
| Backup restore | Monthly | Restore to test server, integrity check | 2 hours |
| Application recovery | Quarterly | Full restore + application deployment + smoke tests | 4 hours |
| Full DR exercise | Annually | Simulate site failure, recover to DR site | 1 day |
| Tabletop exercise | Annually | Walk through scenarios with operations team | 2 hours |

### 9.2 Test Documentation

Each DR test produces a report containing:
- Test date and participants
- Scenario simulated
- Steps executed
- Time to complete each phase
- Issues encountered
- Data integrity verification results
- Recommendations for improvement
- Sign-off by operations lead

---

## 10. Deployment-Specific DR

### 10.1 SaaS Deployment (TSS-Managed)

| Aspect | Approach |
|---|---|
| Backup responsibility | TSS operations team |
| Backup storage | Local + geo-redundant cloud storage (Saudi + secondary region) |
| Multi-tenant backup | Single database backup covers all tenants |
| Tenant-specific restore | Extract tenant data using `org_id` filter from point-in-time restore |
| SLA | RPO < 1 hour, RTO < 4 hours, documented in customer SLA |
| DR testing | Quarterly automated, annual full exercise |
| Communication | Status page for outage communication |

### 10.2 On-Premises Deployment (Customer-Managed)

| Aspect | Approach |
|---|---|
| Backup responsibility | Customer IT team |
| TSS support | Documented backup procedures, runbook, backup verification scripts |
| Backup schedule | Recommended schedule provided; customer adapts to their infrastructure |
| DR planning | TSS provides DR template; customer owns execution |
| Customer training | TSS provides backup/restore training during implementation |
| Support escalation | TSS provides L2/L3 support for DR-related issues |

**TSS-provided documentation for on-prem customers**:
- Backup configuration guide (SQL Server native backup setup)
- Backup verification script (automated monthly restore test)
- DR runbook (step-by-step recovery for each scenario)
- Monitoring configuration (backup job alerting)
- Encryption key management guide

---

## 11. Backup Retention Summary

| Data | Active | Archive | Total Retention | Rationale |
|---|---|---|---|---|
| Full database backup | 30 days local | 90 days offsite | 90 days | Recovery from any point in last 90 days |
| Differential backup | 7 days | Not archived | 7 days | Used only for recent recovery optimization |
| Transaction log backup | 7 days | Not archived | 7 days | Point-in-time recovery for recent events |
| File storage backup | 30 days | 90 days offsite | 90 days | Aligned with database backup retention |
| Audit data | 2 years active | 5 years archived | 7 years | Saudi regulatory requirement |
| Encryption keys | Active | Indefinite (rotated, not deleted) | Indefinite | Required to decrypt historical data |

---

## 12. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Backup tool | SQL Server native backup | Third-party (Veeam, Commvault) | No additional licensing; native is sufficient and well-documented |
| Full backup frequency | Daily | Weekly, twice daily | Daily provides good restore baseline; twice daily wastes storage |
| Log backup frequency | Every 15 minutes | Every 5 min, every 30 min, every hour | 15 min achieves < 1 hour RPO with reasonable I/O overhead |
| Offsite storage | Network share + cloud readiness | Tape, cloud only | Network share is available immediately; cloud added for geo-redundancy |
| Backup compression | Enabled | Disabled | 60-80% size reduction with minimal CPU impact on modern hardware |
| Backup encryption | Offsite copies only | All copies, none | Local copies on secured servers do not need encryption; offsite copies do |
| Restore test frequency | Monthly automated | Weekly, quarterly | Monthly catches issues early without excessive operational burden |
| Recovery model | FULL | SIMPLE, BULK_LOGGED | FULL required for point-in-time recovery and transaction log backups |
