// Demo-mode fallback for the unified Incidents register. Aligned with the
// IncidentDto shape in lib/api.ts.
import type { IncidentDto } from '../../lib/api';
import { demoRows } from '../../lib/demoIds';

export const INCIDENTS: IncidentDto[] = demoRows([
  { id: 1, title: 'Ransomware on file-share server',          titleAr: 'برنامج فدية على خادم الملفات',
    severity: 'CRITICAL', category: 'CYBER',          status: 'CONTAINED',
    assignee: 'csirt.lead@tss.sa', discoveryDate: '2026-04-29',
    description: 'Crypto-locker activity detected on fs-prod-02; immediate isolation engaged.' },
  { id: 2, title: 'Phishing wave targeting finance team',     titleAr: 'موجة تصيد تستهدف قسم المالية',
    severity: 'HIGH',     category: 'CYBER',          status: 'IN_PROGRESS',
    assignee: 'csirt.lead@tss.sa', discoveryDate: '2026-04-27',
    description: 'Credential-harvest campaign with deep-fake voicemail vector; 3 user reports.' },
  { id: 3, title: 'PDPL data-subject notification missed',    titleAr: 'تأخر إشعار صاحب البيانات',
    severity: 'HIGH',     category: 'PRIVACY',        status: 'OPEN',
    assignee: 'dpo@tss.sa', discoveryDate: '2026-04-25',
    description: '30-day PDPL response deadline exceeded by 3 days for a data-access request.' },
  { id: 4, title: 'API gateway latency spike',                titleAr: 'ارتفاع زمن الاستجابة في بوابة API',
    severity: 'MEDIUM',   category: 'OPERATIONS',     status: 'CLOSED',
    assignee: 'sre.team@tss.sa',  discoveryDate: '2026-04-22',
    description: 'Sustained p99 latency above 800 ms across 3 endpoints; mitigated via circuit-breaker.' },
  { id: 5, title: 'Unauthorized access · DC physical',        titleAr: 'دخول غير مصرح به للموقع المادي',
    severity: 'CRITICAL', category: 'ACCESS_CONTROL', status: 'ERADICATED',
    assignee: 'physical.security@tss.sa', discoveryDate: '2026-04-19',
    description: 'Tailgate event in DC1 cage; CCTV review and badge audit completed.' },
  { id: 6, title: 'Suspicious admin login from new geo',      titleAr: 'تسجيل دخول إداري مشبوه من موقع جديد',
    severity: 'MEDIUM',   category: 'CYBER',          status: 'CLOSED',
    assignee: 'soc.lead@tss.sa',  discoveryDate: '2026-04-18',
    description: 'Privileged admin signed in from impossible-travel geo; access revoked + reset.' },
  { id: 7, title: 'Vendor data leak (loyalty platform)',      titleAr: 'تسرب بيانات لدى مزود برنامج الولاء',
    severity: 'HIGH',     category: 'PRIVACY',        status: 'IN_PROGRESS',
    assignee: 'dpo@tss.sa',       discoveryDate: '2026-04-02',
    description: 'Misconfigured S3 bucket exposed customer phone+email for 6 hours.' },
  { id: 8, title: 'DDoS against marketing site',              titleAr: 'هجوم حرمان من الخدمة على الموقع التسويقي',
    severity: 'LOW',      category: 'CYBER',          status: 'CLOSED',
    assignee: 'noc.duty@tss.sa',  discoveryDate: '2026-03-28',
    description: 'Volumetric attack peaked at 12 Gbps; mitigated by Cloudflare under-attack mode.' },
]);
