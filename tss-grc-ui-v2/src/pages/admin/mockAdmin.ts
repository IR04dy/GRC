export interface AdminUser {
  id: number;
  initials: string;
  name: string;
  username: string;
  email: string;
  role: string;
  lastLogin: string;
  status: 'ACTIVE' | 'PENDING' | 'CLOSED';
}

export const ADMIN_USERS: AdminUser[] = [
  { id: 1, initials: 'AA', name: 'Ahmed Al-Qahtani',     username: 'admin',        email: 'admin@tss-grc.local',         role: 'Platform Admin',  lastLogin: '2026-04-30 09:14', status: 'ACTIVE' },
  { id: 2, initials: 'LS', name: 'Layla Al-Saud',         username: 'riskmanager',  email: 'layla.s@tss-grc.local',       role: 'Risk Manager',    lastLogin: '2026-04-29 17:02', status: 'ACTIVE' },
  { id: 3, initials: 'MO', name: 'Mohammed Al-Otaibi',    username: 'auditor',      email: 'm.otaibi@tss-grc.local',      role: 'Internal Auditor',lastLogin: '2026-04-28 11:38', status: 'ACTIVE' },
  { id: 4, initials: 'SD', name: 'Sara Al-Dosari',        username: 'sara.d',       email: 'sara.d@tss-grc.local',        role: 'Privacy Officer', lastLogin: '2026-04-27 14:20', status: 'ACTIVE' },
  { id: 5, initials: 'KG', name: 'Khalid Al-Ghamdi',      username: 'khalid.g',     email: 'khalid.g@tss-grc.local',      role: 'Compliance Lead', lastLogin: '2026-04-26 09:55', status: 'ACTIVE' },
  { id: 6, initials: 'NH', name: 'Noura Al-Harbi',        username: 'noura.h',      email: 'noura.h@tss-grc.local',       role: 'BCM Coordinator', lastLogin: '—',                 status: 'PENDING' },
  { id: 7, initials: 'OD', name: 'Omar Al-Dawsari',       username: 'omar.d',       email: 'omar.d@tss-grc.local',        role: 'Cyber Analyst',   lastLogin: '2026-04-25 16:10', status: 'ACTIVE' },
];
