export interface CyberControl {
  id: number;
  code: string;
  title: string;
  framework: string;
  coverage: number;
  status: 'IMPLEMENTED' | 'IN_PROGRESS' | 'NOT_IMPLEMENTED';
}

export const CONTROLS: CyberControl[] = [
  { id: 1, code: 'AC-2.1.1', title: 'User account lifecycle management',     framework: 'NCA ECC',   coverage: 92, status: 'IMPLEMENTED' },
  { id: 2, code: 'AC-2.1.2', title: 'Privileged access review',              framework: 'NCA ECC',   coverage: 78, status: 'IN_PROGRESS' },
  { id: 3, code: 'A.5.15',   title: 'Access control policy',                 framework: 'ISO 27001', coverage: 100, status: 'IMPLEMENTED' },
  { id: 4, code: 'A.8.16',   title: 'Monitoring activities',                 framework: 'ISO 27001', coverage: 64,  status: 'IN_PROGRESS' },
  { id: 5, code: 'CYB-1.4.3',title: 'Cryptographic key management',          framework: 'SAMA CSF',  coverage: 81,  status: 'IMPLEMENTED' },
  { id: 6, code: 'A.8.23',   title: 'Web filtering',                         framework: 'ISO 27001', coverage: 0,   status: 'NOT_IMPLEMENTED' },
  { id: 7, code: 'IR-3.1.1', title: 'Incident response plan',                framework: 'NCA ECC',   coverage: 88,  status: 'IMPLEMENTED' },
];

export interface CyberVuln {
  id: number;
  cve: string;
  asset: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  discovered: string;
  sla: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'CLOSED';
}

export const VULNS: CyberVuln[] = [
  { id: 1, cve: 'CVE-2025-3041', asset: 'web-app-prod-01',  severity: 'CRITICAL', discovered: '2026-04-22', sla: '2026-04-29', status: 'OPEN' },
  { id: 2, cve: 'CVE-2025-2918', asset: 'api-gw-prod',      severity: 'HIGH',     discovered: '2026-04-19', sla: '2026-05-03', status: 'IN_PROGRESS' },
  { id: 3, cve: 'CVE-2025-2104', asset: 'db-primary',       severity: 'HIGH',     discovered: '2026-04-15', sla: '2026-04-29', status: 'IN_PROGRESS' },
  { id: 4, cve: 'CVE-2024-9831', asset: 'jenkins-ci',       severity: 'MEDIUM',   discovered: '2026-04-10', sla: '2026-05-10', status: 'OPEN' },
  { id: 5, cve: 'CVE-2024-7712', asset: 'corp-wiki',        severity: 'LOW',      discovered: '2026-04-02', sla: '2026-06-01', status: 'OPEN' },
  { id: 6, cve: 'CVE-2024-5641', asset: 'ldap-server',      severity: 'CRITICAL', discovered: '2026-03-28', sla: '2026-04-04', status: 'CLOSED' },
];
