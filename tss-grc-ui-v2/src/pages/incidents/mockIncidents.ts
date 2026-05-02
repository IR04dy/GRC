export interface Incident {
  id: number;
  code: string;
  title: string;
  category: 'Cyber' | 'Privacy' | 'Ops' | 'Physical' | 'Compliance';
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  detectedAt: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'CONTAINED' | 'ERADICATED' | 'RECOVERED' | 'CLOSED';
}

export const INCIDENTS: Incident[] = [
  { id: 1, code: 'INC-2026-042', title: 'Ransomware on file-share server',         category: 'Cyber',   severity: 'CRITICAL', detectedAt: '2026-04-29 02:14', status: 'CONTAINED' },
  { id: 2, code: 'INC-2026-041', title: 'Phishing wave targeting finance team',    category: 'Cyber',   severity: 'HIGH',     detectedAt: '2026-04-27 09:08', status: 'IN_PROGRESS' },
  { id: 3, code: 'INC-2026-040', title: 'PDPL data subject notification missed',   category: 'Privacy', severity: 'HIGH',     detectedAt: '2026-04-25 14:02', status: 'OPEN' },
  { id: 4, code: 'INC-2026-039', title: 'API gateway latency spike',                category: 'Ops',     severity: 'MEDIUM',   detectedAt: '2026-04-22 18:45', status: 'CLOSED' },
  { id: 5, code: 'INC-2026-038', title: 'Unauthorized access · DC physical',        category: 'Physical',severity: 'CRITICAL', detectedAt: '2026-04-19 23:11', status: 'ERADICATED' },
  { id: 6, code: 'INC-2026-037', title: 'Suspicious admin login from new geo',      category: 'Cyber',   severity: 'MEDIUM',   detectedAt: '2026-04-18 06:30', status: 'CLOSED' },
];
