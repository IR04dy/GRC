export interface BcmPlan {
  id: number;
  code: string;
  title: string;
  owner: string;
  criticality: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  rto: string;
  rpo: string;
  lastReview: string;
  status: 'APPROVED' | 'UNDER_REVIEW' | 'DRAFT';
}

export const BCM_PLANS: BcmPlan[] = [
  { id: 1, code: 'BCP-001', title: 'Core Banking System Recovery',  owner: 'Mohammed Al-Otaibi', criticality: 'CRITICAL', rto: '4h',  rpo: '15m', lastReview: '2026-03-15', status: 'APPROVED' },
  { id: 2, code: 'BCP-002', title: 'Payment Gateway Recovery',      owner: 'Layla Al-Saud',      criticality: 'CRITICAL', rto: '2h',  rpo: '5m',  lastReview: '2026-04-02', status: 'APPROVED' },
  { id: 3, code: 'BCP-003', title: 'Customer Service Operations',   owner: 'Khalid Al-Ghamdi',   criticality: 'HIGH',     rto: '8h',  rpo: '1h',  lastReview: '2026-02-28', status: 'APPROVED' },
  { id: 4, code: 'BCP-004', title: 'Branch Network Continuity',     owner: 'Sara Al-Dosari',     criticality: 'HIGH',     rto: '12h', rpo: '2h',  lastReview: '2026-04-10', status: 'UNDER_REVIEW' },
  { id: 5, code: 'BCP-005', title: 'Data Center Failover',          owner: 'Ahmed Al-Qahtani',   criticality: 'CRITICAL', rto: '1h',  rpo: '0',   lastReview: '2026-04-22', status: 'APPROVED' },
  { id: 6, code: 'BCP-006', title: 'Workforce Remote Operations',   owner: 'Noura Al-Harbi',     criticality: 'MEDIUM',   rto: '24h', rpo: '4h',  lastReview: '2026-01-18', status: 'DRAFT' },
];
