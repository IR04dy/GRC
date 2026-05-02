export interface DataSubjectRequest {
  id: number;
  code: string;
  subject: string;
  type: 'Access' | 'Erasure' | 'Rectification' | 'Portability' | 'Objection';
  received: string;
  due: string;
  daysToDue: number;
  status: 'OPEN' | 'IN_PROGRESS' | 'APPROVED' | 'CLOSED' | 'PENDING';
}

export const DSRS: DataSubjectRequest[] = [
  { id: 1, code: 'DSR-2026-014', subject: 'Ahmed M.',     type: 'Access',        received: '2026-04-25', due: '2026-05-25', daysToDue: 25, status: 'IN_PROGRESS' },
  { id: 2, code: 'DSR-2026-013', subject: 'Layla S.',     type: 'Erasure',       received: '2026-04-22', due: '2026-04-29', daysToDue: 2,  status: 'PENDING' },
  { id: 3, code: 'DSR-2026-012', subject: 'Mohammed O.',  type: 'Portability',   received: '2026-04-18', due: '2026-05-18', daysToDue: 18, status: 'IN_PROGRESS' },
  { id: 4, code: 'DSR-2026-011', subject: 'Sara D.',      type: 'Rectification', received: '2026-04-12', due: '2026-04-19', daysToDue: -11, status: 'CLOSED' },
  { id: 5, code: 'DSR-2026-010', subject: 'Khalid G.',    type: 'Access',        received: '2026-04-08', due: '2026-04-15', daysToDue: -15, status: 'CLOSED' },
  { id: 6, code: 'DSR-2026-009', subject: 'Noura H.',     type: 'Objection',     received: '2026-04-03', due: '2026-04-10', daysToDue: -20, status: 'APPROVED' },
];
