export interface ComplianceControl {
  id: number;
  framework: string;
  code: string;
  title: string;
  progress: number;
  lastTested: string;
  status: 'IMPLEMENTED' | 'IN_PROGRESS' | 'NOT_IMPLEMENTED' | 'UNDER_REVIEW';
}

export const COMPLIANCE_CONTROLS: ComplianceControl[] = [
  { id: 1, framework: 'ISO 27001', code: 'A.5.1',   title: 'Information security policies',           progress: 100, lastTested: '2026-04-15', status: 'IMPLEMENTED' },
  { id: 2, framework: 'ISO 27001', code: 'A.5.7',   title: 'Threat intelligence',                     progress: 78,  lastTested: '2026-03-22', status: 'IN_PROGRESS' },
  { id: 3, framework: 'NCA ECC',   code: 'AC-2',    title: 'Account management lifecycle',            progress: 92,  lastTested: '2026-04-08', status: 'IMPLEMENTED' },
  { id: 4, framework: 'NCA ECC',   code: 'IR-3',    title: 'Incident response plan',                  progress: 88,  lastTested: '2026-04-12', status: 'IMPLEMENTED' },
  { id: 5, framework: 'PDPL',      code: 'PDPL-7',  title: 'Cross-border transfer safeguards',        progress: 45,  lastTested: '2026-02-28', status: 'IN_PROGRESS' },
  { id: 6, framework: 'PDPL',      code: 'PDPL-12', title: 'Data subject rights handling',            progress: 81,  lastTested: '2026-04-01', status: 'IMPLEMENTED' },
  { id: 7, framework: 'SAMA CSF',  code: 'CYB-1.4', title: 'Cryptographic key management',            progress: 64,  lastTested: '2026-03-14', status: 'UNDER_REVIEW' },
  { id: 8, framework: 'ISO 22301', code: '8.4',    title: 'Business continuity strategies',          progress: 0,   lastTested: '—',          status: 'NOT_IMPLEMENTED' },
];
