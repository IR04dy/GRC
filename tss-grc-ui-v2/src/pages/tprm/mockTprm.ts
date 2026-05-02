export interface Vendor {
  id: number;
  name: string;
  service: string;
  tier: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  score: number;
  nextReview: string;
  status: 'ACTIVE' | 'UNDER_REVIEW' | 'PENDING';
}

export const VENDORS: Vendor[] = [
  { id: 1, name: 'Microsoft Saudi Arabia', service: 'Cloud Infrastructure',  tier: 'CRITICAL', score: 18, nextReview: '2026-09-15', status: 'ACTIVE' },
  { id: 2, name: 'STC Solutions',           service: 'Telecom & Connectivity', tier: 'CRITICAL', score: 22, nextReview: '2026-08-10', status: 'ACTIVE' },
  { id: 3, name: 'Saudi Information Tech',  service: 'IT Services',           tier: 'HIGH',     score: 41, nextReview: '2026-06-22', status: 'UNDER_REVIEW' },
  { id: 4, name: 'Elm Co.',                 service: 'Identity Verification', tier: 'HIGH',     score: 33, nextReview: '2026-07-08', status: 'ACTIVE' },
  { id: 5, name: 'Mobily Business',         service: 'WAN & Internet',        tier: 'MEDIUM',   score: 52, nextReview: '2026-05-30', status: 'ACTIVE' },
  { id: 6, name: 'Cloud Solutions Co.',     service: 'Backup & DR',           tier: 'MEDIUM',   score: 67, nextReview: '2026-05-15', status: 'UNDER_REVIEW' },
  { id: 7, name: 'Datapro Saudi',           service: 'Help Desk Outsourcing', tier: 'LOW',      score: 12, nextReview: '2026-11-02', status: 'ACTIVE' },
  { id: 8, name: 'Riyadh Print Services',   service: 'Document Management',   tier: 'LOW',      score: 8,  nextReview: '2026-12-15', status: 'PENDING' },
];
