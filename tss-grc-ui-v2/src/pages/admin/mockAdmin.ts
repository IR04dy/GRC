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

// ── Roles & permissions (RBAC) — shape matches AdminRoleDto ─────────────
export interface AdminRoleRow {
  id: number; roleName: string; displayNameEn: string; displayNameAr: string; isSystemRole?: boolean; isActive?: boolean;
}
export const ADMIN_ROLES: AdminRoleRow[] = [
  { id: 1, roleName: 'PLATFORM_ADMIN',  displayNameEn: 'Platform Administrator', displayNameAr: 'مسؤول النظام',           isSystemRole: true,  isActive: true },
  { id: 2, roleName: 'RISK_MANAGER',    displayNameEn: 'Risk Manager',           displayNameAr: 'مدير المخاطر',            isSystemRole: true,  isActive: true },
  { id: 3, roleName: 'CYBER_ANALYST',   displayNameEn: 'Cybersecurity Analyst',  displayNameAr: 'محلل الأمن السيبراني',  isSystemRole: true,  isActive: true },
  { id: 4, roleName: 'PRIVACY_OFFICER', displayNameEn: 'Privacy Officer (DPO)',  displayNameAr: 'مسؤول حماية البيانات',  isSystemRole: true,  isActive: true },
  { id: 5, roleName: 'INTERNAL_AUDITOR',displayNameEn: 'Internal Auditor',       displayNameAr: 'مراجع داخلي',             isSystemRole: true,  isActive: true },
  { id: 6, roleName: 'EXTERNAL_AUDITOR',displayNameEn: 'External Auditor',       displayNameAr: 'مراجع خارجي',             isSystemRole: true,  isActive: true },
  { id: 7, roleName: 'COMPLIANCE_LEAD', displayNameEn: 'Compliance Lead',        displayNameAr: 'مسؤول الامتثال',          isSystemRole: true,  isActive: true },
  { id: 8, roleName: 'BCM_COORDINATOR', displayNameEn: 'BCM Coordinator',        displayNameAr: 'منسق استمرارية الأعمال',  isSystemRole: true,  isActive: true },
  { id: 9, roleName: 'TPRM_LEAD',       displayNameEn: 'TPRM Lead',              displayNameAr: 'مسؤول إدارة مخاطر الأطراف الثالثة', isSystemRole: true, isActive: true },
  { id:10, roleName: 'AML_ANALYST',     displayNameEn: 'AML Analyst',            displayNameAr: 'محلل مكافحة غسل الأموال', isSystemRole: true,  isActive: true },
];

// ── Audit log events (admin tab) ────────────────────────────────────────
export interface AdminAuditEvent {
  id: number; at: string; actor: string; action: string; entity: string; ip: string; outcome: 'SUCCESS' | 'DENIED' | 'ERROR';
}
export const ADMIN_AUDIT_EVENTS: AdminAuditEvent[] = [
  { id: 1,  at: '2026-04-30 09:14:32', actor: 'admin',         action: 'USER_LOGIN',         entity: 'admin@tss-grc.local',          ip: '10.20.40.12',  outcome: 'SUCCESS' },
  { id: 2,  at: '2026-04-30 09:42:11', actor: 'admin',         action: 'CREATE_USER',        entity: 'noura.h@tss-grc.local',        ip: '10.20.40.12',  outcome: 'SUCCESS' },
  { id: 3,  at: '2026-04-30 10:15:08', actor: 'admin',         action: 'GRANT_ROLE',         entity: 'noura.h → BCM_COORDINATOR',    ip: '10.20.40.12',  outcome: 'SUCCESS' },
  { id: 4,  at: '2026-04-29 17:02:50', actor: 'riskmanager',   action: 'USER_LOGIN',         entity: 'riskmanager',                  ip: '10.20.40.18',  outcome: 'SUCCESS' },
  { id: 5,  at: '2026-04-29 14:44:21', actor: 'auditor',       action: 'EXPORT_RISK_REGISTER', entity: 'risk-register-2026-Q1.pdf',   ip: '203.0.113.4',  outcome: 'SUCCESS' },
  { id: 6,  at: '2026-04-29 14:44:18', actor: 'auditor',       action: 'USER_LOGIN',         entity: 'auditor',                      ip: '203.0.113.4',  outcome: 'SUCCESS' },
  { id: 7,  at: '2026-04-29 12:00:55', actor: 'soc.lead',      action: 'CREATE_INCIDENT',    entity: 'CYB-INC-2026-019',             ip: '10.20.40.22',  outcome: 'SUCCESS' },
  { id: 8,  at: '2026-04-29 11:30:11', actor: 'unknown',       action: 'USER_LOGIN',         entity: 'admin (4th attempt)',          ip: '198.51.100.7', outcome: 'DENIED'  },
  { id: 9,  at: '2026-04-28 11:38:02', actor: 'auditor',       action: 'APPROVE_FINDING',    entity: 'AUD-F-035',                    ip: '203.0.113.4',  outcome: 'SUCCESS' },
  { id:10,  at: '2026-04-27 14:20:43', actor: 'sara.d',        action: 'CREATE_DSR',         entity: 'DSR-2026-014',                 ip: '10.20.40.30',  outcome: 'SUCCESS' },
  { id:11,  at: '2026-04-27 09:44:02', actor: 'sara.d',        action: 'UPDATE_RISK_TREATMENT', entity: 'R-0042',                    ip: '10.20.40.30',  outcome: 'SUCCESS' },
  { id:12,  at: '2026-04-26 09:55:18', actor: 'khalid.g',      action: 'CREATE_POLICY',      entity: 'POL-AI-001',                   ip: '10.20.40.45',  outcome: 'SUCCESS' },
  { id:13,  at: '2026-04-25 16:10:09', actor: 'omar.d',        action: 'CLOSE_VULN',         entity: 'CVE-2024-5641',                ip: '10.20.40.50',  outcome: 'SUCCESS' },
  { id:14,  at: '2026-04-25 11:22:01', actor: 'admin',         action: 'CHANGE_PASSWORD',    entity: 'self',                         ip: '10.20.40.12',  outcome: 'SUCCESS' },
  { id:15,  at: '2026-04-24 22:11:40', actor: 'csirt.duty',    action: 'TRIGGER_RUNBOOK',    entity: 'BCP-002 invocation',           ip: '10.20.40.55',  outcome: 'SUCCESS' },
];
