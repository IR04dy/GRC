// Intake seed records — demo submissions for the unauthenticated portal path.
//
// ⚠ F2-172 moved these OUT of `lib/intake.ts`, and the move is not only about the 800-line
// ceiling. These five submissions are fabricated, and until F2-172 the triager inbox
// rendered them unconditionally — so a signed-in triager with an empty or unreachable
// queue saw five cases that do not exist and could press Promote on one. `useIntake()`
// now reads them only when there is no session, which is the public-portal path F2-138
// still blocks. Keeping them in their own module makes the one import that consumes them
// visible.

import type { IntakeEvent, IntakeRecord, IntakeStatus } from './intake';

// ── Seed data ───────────────────────────────────────────────────────────
// A handful of pre-seeded intake records so the inbox view isn't empty
// in demo mode and the case-tracker has something to look up.

function ev(at: string, status: IntakeStatus, actor: string, note?: string): IntakeEvent {
  return { at, status, actor, ...(note ? { note } : {}) };
}

export const SEED_INTAKE: IntakeRecord[] = [
  {
    id: 'intake-001',
    referenceCode: 'DS-K7L9-Z4M2',
    kind: 'DSR_REQUEST',
    status: 'TRIAGING',
    channel: 'WEB_FORM',
    submitterIsAnonymous: false,
    submitterRole: 'CUSTOMER',
    submitterName: 'Mariam Al-Otaibi',
    submitterEmail: 'm.alotaibi@example.sa',
    payload: {
      requestType: 'ACCESS',
      scope: 'All personal data the company holds about me, including marketing profiles and call recordings.',
      verificationMethod: 'NATIONAL_ID',
      idLast4: '1234',
    },
    assignedTo: 'DPO',
    slaDueAt: '2026-06-03T09:30:00',
    severity: 'MEDIUM',
    acknowledgedAt: '2026-05-04T09:30:00',
    history: [
      ev('2026-05-04T09:30:00', 'SUBMITTED', 'anonymous', 'Submitted via /portal/dsr'),
      ev('2026-05-04T09:30:00', 'TRIAGING', 'system', 'Auto-acknowledged. Assigned to DPO.'),
    ],
    locale: 'en',
  },
  {
    id: 'intake-002',
    referenceCode: 'SU-X9P3-T2W7',
    kind: 'SPEAKUP',
    status: 'TRIAGING',
    channel: 'WEB_FORM',
    submitterIsAnonymous: true,
    submitterRole: 'PUBLIC',
    payload: {
      category: 'PROCUREMENT_FRAUD',
      summary: 'Suspicious sole-source contract awarded to a vendor where a procurement-team relative is on the board.',
      involvedParties: 'Procurement team (department-level — names withheld for now)',
      evidenceAvailable: true,
      evidenceDescription: 'Email exchanges and the bid-evaluation matrix.',
    },
    assignedTo: 'ETHICS_CTE',
    slaDueAt: '2026-05-11T14:15:00',
    severity: 'HIGH',
    acknowledgedAt: '2026-05-04T14:15:00',
    history: [
      ev('2026-05-04T14:15:00', 'SUBMITTED', 'anonymous', 'Submitted via /portal/speakup'),
      ev('2026-05-04T14:15:00', 'TRIAGING', 'system', 'Auto-acknowledged. Assigned to Ethics Committee.'),
    ],
    locale: 'en',
  },
  {
    id: 'intake-003',
    referenceCode: 'SI-B5D8-N3J1',
    kind: 'SECURITY_INCIDENT',
    status: 'PROMOTED',
    channel: 'WEB_FORM',
    submitterIsAnonymous: false,
    submitterRole: 'EMPLOYEE',
    submitterName: 'Hassan Al-Saud',
    submitterEmail: 'iam.team@tss.sa',
    payload: {
      category: 'PHISHING',
      summary: 'Targeted phishing email referencing internal project codenames received by 4 finance team members.',
      observedAt: '2026-04-22T08:15:00',
      systemsAffected: 'Email gateway only — no clicks reported',
      severitySelfAssessed: 'MEDIUM',
    },
    assignedTo: 'CSIRT',
    slaDueAt: '2026-04-25T08:30:00',
    severity: 'MEDIUM',
    acknowledgedAt: '2026-04-22T08:30:00',
    promotedTo: { entityType: 'INCIDENT', recordId: 14, promotedAt: '2026-04-22T11:00:00' },
    history: [
      ev('2026-04-22T08:30:00', 'SUBMITTED', 'iam.team@tss.sa'),
      ev('2026-04-22T08:30:00', 'TRIAGING', 'system', 'Routed to CSIRT.'),
      ev('2026-04-22T11:00:00', 'PROMOTED', 'csirt.lead@tss.sa', 'Promoted to incident #14 — VPN credential-stuffing campaign'),
    ],
    locale: 'en',
  },
  {
    id: 'intake-004',
    referenceCode: 'PE-F2H6-K8R4',
    kind: 'POLICY_EXCEPTION',
    status: 'TRIAGING',
    channel: 'WEB_FORM',
    submitterIsAnonymous: false,
    submitterRole: 'EMPLOYEE',
    submitterName: 'Layla Al-Saud',
    submitterEmail: 'sre.lead@tss.sa',
    payload: {
      policyCode: 'POL-LEG-002',
      policyTitle: 'Legacy Crypto Sunset',
      exceptionScope: 'TLS 1.0 listener on legacy ATM gateway until vendor patch lands.',
      compensatingControls: 'Network ACL restricts to two known CIDR ranges; CyberArk session recording enforced; daily log review.',
      requestedDuration: '90 days',
      sunsetDate: '2026-08-04',
      businessJustification: 'Vendor patch ETA Q3; replacement infrastructure procurement in flight.',
    },
    assignedTo: 'POLICY_OFFICER',
    slaDueAt: '2026-05-11T11:00:00',
    severity: 'HIGH',
    acknowledgedAt: '2026-05-04T11:00:00',
    history: [
      ev('2026-05-04T11:00:00', 'SUBMITTED', 'sre.lead@tss.sa'),
      ev('2026-05-04T11:00:00', 'TRIAGING', 'system', 'Routed to Policy Officer + CISO peer review.'),
    ],
    locale: 'en',
  },
  {
    id: 'intake-005',
    referenceCode: 'VR-Q4N2-Y8X3',
    kind: 'VRP_DISCLOSURE',
    status: 'CLOSED',
    channel: 'WEB_FORM',
    submitterIsAnonymous: true,
    submitterRole: 'PUBLIC',
    payload: {
      category: 'XSS',
      affectedUrl: 'https://tss.sa/marketing/feedback?q=<script>',
      reproductionSteps: '1. Open the feedback form. 2. Enter <script>alert(1)</script> as the query parameter. 3. Reflected XSS triggers.',
      severitySelfAssessed: 'MEDIUM',
      cve: '',
      researcherHandle: 'r3dteam',
      contactEmail: 'r3dteam@protonmail.example',
    },
    assignedTo: 'CISO_OFFICE',
    slaDueAt: '2026-04-22T16:45:00',
    severity: 'MEDIUM',
    acknowledgedAt: '2026-04-15T16:45:00',
    history: [
      ev('2026-04-15T16:45:00', 'SUBMITTED', 'anonymous'),
      ev('2026-04-15T16:45:00', 'TRIAGING', 'system'),
      ev('2026-04-18T10:00:00', 'PROMOTED', 'soc.lead@tss.sa', 'Reproduced — patched in marketing-microsite v2.4.1'),
      ev('2026-04-22T15:00:00', 'CLOSED', 'soc.lead@tss.sa', 'Researcher confirmed fix. Bounty paid.'),
    ],
    locale: 'en',
  },
];
