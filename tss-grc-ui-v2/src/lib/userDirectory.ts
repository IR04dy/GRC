// User directory — Phase A of the infrastructure round.
//
// Realistic Saudi user directory powering the assignee picker. Replaces
// the seeded-string owner fields scattered across all the lifecycle
// workspaces. Inspired by ServiceNow `sys_user` table + OneTrust's
// people directory.
//
// In production the source is SCIM/AD/IAM, but the consumer-side API is
// the same — search by query, return matched users with avatar, role,
// department, contact channels.

export type UserRole =
  | 'CEO' | 'COO' | 'CIO' | 'CTO' | 'CFO' | 'CHRO' | 'CISO' | 'CRO' | 'DPO' | 'CCO' | 'CAE'
  | 'AUDIT_MANAGER' | 'AUDIT_SENIOR' | 'AUDIT_STAFF'
  | 'RISK_MANAGER' | 'RISK_ANALYST'
  | 'COMPLIANCE_OFFICER' | 'COMPLIANCE_ANALYST'
  | 'CYBER_DEFENSE_HEAD' | 'SOC_ANALYST' | 'CSIRT_LEAD' | 'IT_OPS_LEAD'
  | 'BCM_LEAD' | 'BCM_COORD'
  | 'PRIVACY_ANALYST'
  | 'PROCUREMENT_HEAD' | 'TPRM_LEAD' | 'VENDOR_MGR'
  | 'BOARD_CHAIR' | 'AC_CHAIR' | 'AC_MEMBER'
  | 'LEGAL_COUNSEL' | 'COMMS_HEAD'
  | 'HRSD_LIAISON' | 'SAMA_LIAISON' | 'NCA_LIAISON' | 'SDAIA_LIAISON';

export interface User {
  id: string;
  /** Display name English. */
  nameEn: string;
  /** Display name Arabic. */
  nameAr: string;
  role: UserRole;
  department: string;
  email: string;
  /** E.164 Saudi mobile (+9665…). */
  phone: string;
  /** Two-letter avatar fallback. */
  initials: string;
  /** Avatar background colour (deterministic). */
  avatarColor: string;
  /** Whether currently on call (for crisis routing). */
  onCall?: boolean;
  /** Last seen timestamp (for stale-user dimming). */
  lastSeenAt?: string;
}

const _now = new Date().toISOString();

/** Saudi-flavoured user directory — names, roles, contact channels, all real-shaped. */
export const USER_DIRECTORY: User[] = [
  // C-suite
  { id: 'usr-001', nameEn: 'Abdullah Al-Saud',     nameAr: 'عبدالله آل سعود',       role: 'CEO',                 department: 'Executive',        email: 'ceo@org.sa',                  phone: '+966500000005', initials: 'AS', avatarColor: '#0d3a6a', lastSeenAt: _now },
  { id: 'usr-002', nameEn: 'Norah Al-Mutairi',    nameAr: 'نورة المطيري',         role: 'COO',                 department: 'Executive',        email: 'coo@org.sa',                  phone: '+966500000012', initials: 'NM', avatarColor: '#7c3aed', lastSeenAt: _now },
  { id: 'usr-003', nameEn: 'Khalid Al-Harbi',     nameAr: 'خالد الحربي',           role: 'CIO',                 department: 'Technology',        email: 'cio@org.sa',                  phone: '+966500000004', initials: 'KH', avatarColor: '#0a6b3a', lastSeenAt: _now },
  { id: 'usr-004', nameEn: 'Ahmed Al-Qahtani',    nameAr: 'أحمد القحطاني',         role: 'CTO',                 department: 'Technology',        email: 'cto@org.sa',                  phone: '+966500000040', initials: 'AQ', avatarColor: '#0a6b3a', onCall: true, lastSeenAt: _now },
  { id: 'usr-005', nameEn: 'Mohammed Al-Mahmoud', nameAr: 'محمد المحمود',         role: 'CFO',                 department: 'Finance',           email: 'cfo@org.sa',                  phone: '+966500000050', initials: 'MM', avatarColor: '#012169', lastSeenAt: _now },
  { id: 'usr-006', nameEn: 'Salman Al-Dossari',   nameAr: 'سلمان الدوسري',         role: 'CHRO',                department: 'HR',                email: 'chro@org.sa',                 phone: '+966500000013', initials: 'SD', avatarColor: '#c2410c', lastSeenAt: _now },

  // Risk + Compliance + Audit + Privacy
  { id: 'usr-010', nameEn: 'Mohammed Al-Mahmoud (CRO)', nameAr: 'محمد المحمود (CRO)', role: 'CRO',           department: 'Risk',              email: 'cro@org.sa',                  phone: '+966500000051', initials: 'MM', avatarColor: '#dc2626', lastSeenAt: _now },
  { id: 'usr-011', nameEn: 'Hessa Al-Rasheed',    nameAr: 'حصة الرشيد',            role: 'COMPLIANCE_OFFICER',  department: 'Compliance',        email: 'cco@org.sa',                  phone: '+966500000052', initials: 'HR', avatarColor: '#7c3aed', lastSeenAt: _now },
  { id: 'usr-012', nameEn: 'Reem Al-Shehri',      nameAr: 'ريم الشهري',            role: 'CAE',                 department: 'Internal Audit',     email: 'cae@org.sa',                  phone: '+966500000053', initials: 'RS', avatarColor: '#1e40af', lastSeenAt: _now },
  { id: 'usr-013', nameEn: 'Layla Al-Ghamdi',     nameAr: 'ليلى الغامدي',          role: 'DPO',                 department: 'Privacy',           email: 'dpo@org.sa',                  phone: '+966500000006', initials: 'LG', avatarColor: '#9333ea', lastSeenAt: _now },
  { id: 'usr-014', nameEn: 'Sara Al-Zahrani',     nameAr: 'سارة الزهراني',         role: 'CISO',                department: 'Cybersecurity',      email: 'ciso@org.sa',                 phone: '+966500000003', initials: 'SZ', avatarColor: '#dc2626', onCall: true, lastSeenAt: _now },

  // Audit team
  { id: 'usr-020', nameEn: 'Sara Al-Zahrani (Audit Mgr)', nameAr: 'سارة الزهراني (مدير تدقيق)', role: 'AUDIT_MANAGER', department: 'Internal Audit', email: 'audit-mgr@org.sa',      phone: '+966500000060', initials: 'SZ', avatarColor: '#1e40af', lastSeenAt: _now },
  { id: 'usr-021', nameEn: 'Faisal Al-Otaibi',    nameAr: 'فيصل العتيبي',          role: 'AUDIT_SENIOR',        department: 'Internal Audit',     email: 'audit-snr@org.sa',            phone: '+966500000061', initials: 'FO', avatarColor: '#1e40af', lastSeenAt: _now },
  { id: 'usr-022', nameEn: 'Khalid Al-Harbi (Audit)', nameAr: 'خالد الحربي (تدقيق)', role: 'AUDIT_STAFF',     department: 'Internal Audit',     email: 'audit-staff@org.sa',          phone: '+966500000062', initials: 'KH', avatarColor: '#1e40af', lastSeenAt: _now },

  // Cyber team
  { id: 'usr-030', nameEn: 'Mohammed Al-Sulaiman', nameAr: 'محمد السليمان',        role: 'CYBER_DEFENSE_HEAD',  department: 'Cybersecurity',      email: 'csirt-lead@org.sa',           phone: '+966500000002', initials: 'MS', avatarColor: '#dc2626', onCall: true, lastSeenAt: _now },
  { id: 'usr-031', nameEn: 'Faisal Al-Otaibi (SOC)', nameAr: 'فيصل العتيبي (SOC)', role: 'SOC_ANALYST',         department: 'Cybersecurity',      email: 'soc-oncall@org.sa',           phone: '+966500000001', initials: 'FO', avatarColor: '#dc2626', onCall: true, lastSeenAt: _now },

  // BCM + Crisis
  { id: 'usr-040', nameEn: 'Layla Al-Ghamdi (BCM)', nameAr: 'ليلى الغامدي (BCM)', role: 'BCM_LEAD',           department: 'Resilience',         email: 'bcm-head@org.sa',             phone: '+966500000070', initials: 'LG', avatarColor: '#0a6b3a', lastSeenAt: _now },

  // Procurement / TPRM
  { id: 'usr-050', nameEn: 'Bandar Al-Subaie',     nameAr: 'بندر السبيعي',          role: 'PROCUREMENT_HEAD',    department: 'Procurement',        email: 'procurement@org.sa',          phone: '+966500000022', initials: 'BS', avatarColor: '#c2410c', lastSeenAt: _now },
  { id: 'usr-051', nameEn: 'Tariq Al-Maliki',     nameAr: 'طارق المالكي',          role: 'TPRM_LEAD',           department: 'Procurement',        email: 'tprm-lead@org.sa',            phone: '+966500000021', initials: 'TM', avatarColor: '#c2410c', lastSeenAt: _now },

  // Board + AC
  { id: 'usr-060', nameEn: 'Khalid Al-Falih',      nameAr: 'خالد الفالح',           role: 'AC_CHAIR',            department: 'Board',              email: 'ac-chair@org.sa',             phone: '+966500000080', initials: 'KF', avatarColor: '#0d3a6a', lastSeenAt: _now },

  // Regulator liaisons
  { id: 'usr-070', nameEn: 'Mansour Al-Qahtani',  nameAr: 'منصور القحطاني',         role: 'NCA_LIAISON',          department: 'Regulatory',         email: 'nca-liaison@org.sa',          phone: '+966500000090', initials: 'MQ', avatarColor: '#0a6b3a', lastSeenAt: _now },
  { id: 'usr-071', nameEn: 'Yousef Al-Anazi',      nameAr: 'يوسف العنزي',            role: 'SAMA_LIAISON',         department: 'Regulatory',         email: 'sama-liaison@org.sa',         phone: '+966500000091', initials: 'YA', avatarColor: '#0d3a6a', lastSeenAt: _now },
  { id: 'usr-072', nameEn: 'Adel Al-Jubair',       nameAr: 'عادل الجبير',            role: 'SDAIA_LIAISON',        department: 'Regulatory',         email: 'sdaia-liaison@org.sa',        phone: '+966500000092', initials: 'AJ', avatarColor: '#9333ea', lastSeenAt: _now },
];

/** Search the directory by free-text (name EN/AR, role, department). */
export function searchUsers(query: string, limit = 8): User[] {
  if (!query || !query.trim()) return USER_DIRECTORY.slice(0, limit);
  const q = query.toLowerCase().trim();
  return USER_DIRECTORY
    .filter(u =>
      u.nameEn.toLowerCase().includes(q) ||
      u.nameAr.includes(q) ||
      u.role.toLowerCase().includes(q) ||
      u.department.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q)
    )
    .slice(0, limit);
}

/** Look up a user by id. */
export function getUser(id: string): User | undefined {
  return USER_DIRECTORY.find(u => u.id === id);
}

/** Look up by free-text seeded-string-style match (so legacy data still works). */
export function lookupUserByName(name: string): User | undefined {
  if (!name) return undefined;
  // Strip role parens like "(CRO)" first
  const stripped = name.replace(/\s*\([^)]+\)\s*/g, '').trim().toLowerCase();
  return USER_DIRECTORY.find(u =>
    u.nameEn.toLowerCase() === stripped ||
    u.nameEn.toLowerCase().startsWith(stripped) ||
    u.nameAr === name.trim() ||
    u.email.toLowerCase() === name.toLowerCase()
  );
}

/** Recently-active users for the picker default list. */
export function recentUsers(limit = 5): User[] {
  return [...USER_DIRECTORY]
    .filter(u => u.lastSeenAt)
    .sort((a, b) => new Date(b.lastSeenAt!).getTime() - new Date(a.lastSeenAt!).getTime())
    .slice(0, limit);
}

/** On-call users (for crisis / IR routing). */
export function onCallUsers(): User[] {
  return USER_DIRECTORY.filter(u => u.onCall);
}

/** Current user — in real app this comes from auth. Demo: pick the CEO. */
export function currentUser(): User {
  return USER_DIRECTORY.find(u => u.id === 'usr-001')!;
}

/** Format display name with role for inline display. */
export function userDisplay(u: User, lang: 'en' | 'ar'): string {
  return lang === 'ar'
    ? `${u.nameAr} (${u.role})`
    : `${u.nameEn} (${u.role})`;
}
