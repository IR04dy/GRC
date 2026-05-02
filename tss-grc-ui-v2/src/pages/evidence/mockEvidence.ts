export interface EvidenceItem {
  id: number;
  code: string;
  title: string;
  type: 'Policy' | 'Screenshot' | 'Audit Report' | 'Configuration' | 'Certificate';
  framework: string;
  expiry: string;
  daysToExpiry: number;
  status: 'APPROVED' | 'PENDING' | 'UNDER_REVIEW' | 'EXPIRED';
}

export const EVIDENCE: EvidenceItem[] = [
  { id: 1, code: 'EV-1083', title: 'Information Security Policy v3.2',          type: 'Policy',         framework: 'ISO 27001', expiry: '2027-04-15', daysToExpiry: 350, status: 'APPROVED' },
  { id: 2, code: 'EV-1082', title: 'Q1 2026 Vulnerability Scan Report',          type: 'Audit Report',   framework: 'NCA ECC',   expiry: '2026-07-01', daysToExpiry: 62,  status: 'APPROVED' },
  { id: 3, code: 'EV-1081', title: 'Firewall Rules Configuration Snapshot',      type: 'Configuration',  framework: 'ISO 27001', expiry: '2026-05-12', daysToExpiry: 12,  status: 'UNDER_REVIEW' },
  { id: 4, code: 'EV-1080', title: 'PDPL Data Inventory Report Q4 2025',         type: 'Audit Report',   framework: 'PDPL',      expiry: '2026-05-08', daysToExpiry: 8,   status: 'PENDING' },
  { id: 5, code: 'EV-1079', title: 'Backup Validation Screenshot (Apr 2026)',    type: 'Screenshot',     framework: 'ISO 22301', expiry: '2026-08-10', daysToExpiry: 102, status: 'APPROVED' },
  { id: 6, code: 'EV-1078', title: 'PCI DSS Attestation Certificate 2025',       type: 'Certificate',    framework: 'SAMA CSF',  expiry: '2026-04-30', daysToExpiry: 0,   status: 'EXPIRED' },
];
