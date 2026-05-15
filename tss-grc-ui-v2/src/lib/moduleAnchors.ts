// Per-module regulator anchors — feed the RegulatorPostureCard on each
// module's overview tab. Each list represents the canonical authority
// coverage an auditor expects to see for that domain.
import type { RegulatorAnchor } from './evidenceAnchors';

// ─── RISK ──────────────────────────────────────────────────────────────────
export const RISK_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NRC',  frameworkCode: 'NRC_NATIONAL_RISK', controlRef: '§2.1', citationText: 'Enterprise risk register feeds national sectoral risk view.', controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NRC',  frameworkCode: 'NRC_NATIONAL_RISK', controlRef: '§3.4', citationText: 'Quarterly risk-state report submission.',                       controlStatus: 'PARTIAL' },
  { authorityCode: 'COSO', frameworkCode: 'COSO_ERM',          controlRef: 'Strategy #4',  citationText: 'Risk appetite + tolerance.',                            controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'COSO', frameworkCode: 'COSO_ERM',          controlRef: 'Performance #16', citationText: 'KRI threshold response.',                            controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_31000',         controlRef: '§6.4',         citationText: 'Risk identification + scoring.',                        controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_31000',         controlRef: '§6.5',         citationText: 'Risk treatment + acceptance.',                          controlStatus: 'IMPLEMENTED' },
  // Replaced NIST_RMF (US framework) with the closest Saudi-relevant anchor:
  // ISO 31000 §6.3 covers categorisation + tiering equivalently and is the
  // standard a Saudi enterprise actually attests to.
  { authorityCode: 'ISO',  frameworkCode: 'ISO_31000',         controlRef: '§6.3',         citationText: 'Risk identification — categorisation + tiering.',       controlStatus: 'PARTIAL' },
  { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',          controlRef: '1.2',          citationText: 'Risk management framework — bank-specific.',           controlStatus: 'IMPLEMENTED' },
];

// ─── CYBER ─────────────────────────────────────────────────────────────────
export const CYBER_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'PR-1.1',   citationText: 'Information security policy approved by senior leadership.', controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'AC-2.1.2', citationText: 'Privileged access review.',                                   controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '5.2.4',    citationText: 'Vulnerability scanning programme.',                           controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'IR-3.1',   citationText: 'Incident response plan tested annually.',                     controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',  frameworkCode: 'NCA_CCC',   controlRef: '§3.1',     citationText: 'Cloud cybersecurity controls — Saudi sovereign data.',        controlStatus: 'PARTIAL' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.5.1',    citationText: 'Information security policies.',                              controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.16',   citationText: 'Monitoring activities (SIEM coverage).',                      controlStatus: 'PARTIAL' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.20',   citationText: 'Network security configuration.',                             controlStatus: 'PARTIAL' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.24',   citationText: 'Use of cryptography.',                                        controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: '3.1.4',    citationText: 'Privileged access management.',                               controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: '3.3.4',    citationText: 'Vulnerability + patch management.',                           controlStatus: 'IMPLEMENTED' },
  // Replaced NIST CSF + NIST 800-61 with NCA equivalents — the Saudi
  // cyber regulator covers the same ground, and an auditor expects to
  // see NCA-cited controls on a Saudi cyber posture card.
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'AC-2.1',   citationText: 'Identity management + access control.',                       controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'IR-3.2',   citationText: 'Incident-response lifecycle (detect → contain → recover).',  controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'AICPA',frameworkCode: 'SOC_2',     controlRef: 'CC6.1',    citationText: 'Logical + physical access.',                                  controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'PCI',  frameworkCode: 'PCI_DSS',   controlRef: '11.3',     citationText: 'Quarterly external + internal vuln scans.',                   controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'CITC', frameworkCode: 'CITC_CLOUD',controlRef: '§4.2',     citationText: 'Cloud cybersecurity baseline.',                               controlStatus: 'PARTIAL' },
];

// ─── PRIVACY ───────────────────────────────────────────────────────────────
export const PRIVACY_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 5',  citationText: 'Lawful basis for personal-data processing.',     controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 11', citationText: 'Consent obtained + recorded.',                   controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 20', citationText: 'Data-breach 72-hour notification window.',       controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 23', citationText: 'Data-subject rights — 30-day fulfilment SLA.',   controlStatus: 'PARTIAL' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 25', citationText: 'DPIA required for high-risk processing.',         controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 29', citationText: 'Cross-border transfer safeguards.',              controlStatus: 'NOT_IMPLEMENTED' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',    controlRef: 'Article 31', citationText: 'Records of processing activities.',              controlStatus: 'PARTIAL' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.2',    citationText: 'Privacy notices + transparency.',                controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.3',    citationText: 'Subject-access response process.',               controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.5',    citationText: 'Cross-border transfer impact assessment.',       controlStatus: 'PARTIAL' },
];

// ─── BCM ───────────────────────────────────────────────────────────────────
export const BCM_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP',  controlRef: '§4.1',    citationText: 'Business continuity policy approved by leadership.', controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP',  controlRef: '§5.3',    citationText: 'Recovery time + recovery point objectives.',         controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_CRISIS', controlRef: '§6',    citationText: 'Crisis-management activation procedure.',           controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '§8.2.2',   citationText: 'Business impact analysis (BIA).',                   controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '§8.4',     citationText: 'Continuity strategies + plans.',                    controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '§8.5',     citationText: 'Exercise + testing programme.',                     controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '§8.13',    citationText: 'Backup + restore validation.',                      controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA',  frameworkCode: 'SAMA_BCP',  controlRef: '4.5',      citationText: 'Banking continuity recovery testing.',              controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA',  frameworkCode: 'SAMA_BCP',  controlRef: '4.7',      citationText: 'Crisis-communication readiness.',                   controlStatus: 'PARTIAL' },
];

// ─── TPRM ──────────────────────────────────────────────────────────────────
export const TPRM_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NRC',   frameworkCode: 'NRC_TIER_MAPPING', controlRef: '§4.1',  citationText: 'Tier-1 vendor mapping completeness.',                   controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NRC',   frameworkCode: 'NRC_TIER_MAPPING', controlRef: '§4.3',  citationText: 'Tier-N sub-supplier visibility.',                       controlStatus: 'PARTIAL' },
  // Replaced NIST 800-161 (US supply-chain) with NCA OTCC + ISO 28001 —
  // the same supply-chain risk management ground covered by Saudi-relevant
  // frameworks. NCA OTCC explicitly governs critical-infrastructure
  // operators' supplier security; ISO 28001 is the international SC standard.
  { authorityCode: 'NCA',   frameworkCode: 'NCA_OTCC',         controlRef: 'SC-3',  citationText: 'Supplier risk management plan (critical infra).',       controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',   frameworkCode: 'NCA_OTCC',         controlRef: 'SC-5',  citationText: 'Supplier acquisition + onboarding strategy.',           controlStatus: 'PARTIAL' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_28001',        controlRef: '§6.2',  citationText: 'Supplier security assessment.',                         controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_27001',        controlRef: 'A.5.19',citationText: 'Supplier-relationship information security.',           controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_27001',        controlRef: 'A.5.21',citationText: 'Supplier-chain ICT services.',                          controlStatus: 'PARTIAL' },
  { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',         controlRef: '4.1',   citationText: 'Outsourcing + third-party risk management.',           controlStatus: 'IMPLEMENTED' },
];

// ─── SCM (broader supply-chain ops, not just IT vendors) ───────────────────
export const SCM_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NRC',   frameworkCode: 'NRC_TIER_MAPPING', controlRef: '§4.1',  citationText: 'Multi-tier supplier mapping (T1 → T-N).',               controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NRC',   frameworkCode: 'NRC_TIER_MAPPING', controlRef: '§5.2',  citationText: 'Single-point-of-failure register.',                     controlStatus: 'PARTIAL' },
  { authorityCode: 'NRC',   frameworkCode: 'NRC_TIER_MAPPING', controlRef: '§6.3',  citationText: 'Foreign-dependency risk assessment.',                   controlStatus: 'PARTIAL' },
  // Replaced NIST 800-161 with NCA OTCC (same supply-chain ground, Saudi).
  { authorityCode: 'NCA',   frameworkCode: 'NCA_OTCC',         controlRef: 'SC-3',  citationText: 'Supply-chain risk management plan.',                    controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_28001',        controlRef: '§5',    citationText: 'Supply-chain security management.',                     controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'CITC',  frameworkCode: 'CITC_CLOUD',       controlRef: '§5.1',  citationText: 'Cloud + ICT supply-chain residency.',                   controlStatus: 'PARTIAL' },
];

// ─── ETHICS / FINANCIAL CRIME ─────────────────────────────────────────────
export const ETHICS_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NAZAHA', frameworkCode: 'NAZAHA_ANTI_CORRUPTION', controlRef: '§4',  citationText: 'Anti-bribery + anti-corruption programme.',          controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NAZAHA', frameworkCode: 'NAZAHA_ANTI_CORRUPTION', controlRef: '§7',  citationText: 'Whistleblower hotline + protection.',                controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',    frameworkCode: 'ISO_37001',              controlRef: '§4.5',citationText: 'Anti-bribery management system.',                    controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',    frameworkCode: 'ISO_37001',              controlRef: '§6.5',citationText: 'Conflict-of-interest disclosures.',                  controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA',   frameworkCode: 'SAMA_AML',               controlRef: '§4.3',citationText: 'Customer due diligence + EDD.',                      controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA',   frameworkCode: 'SAMA_AML',               controlRef: '§5',   citationText: 'Transaction monitoring + STR filing.',              controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'FATF',   frameworkCode: 'FATF',                   controlRef: 'R10',  citationText: 'Customer due diligence.',                            controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'FATF',   frameworkCode: 'FATF',                   controlRef: 'R20',  citationText: 'Reporting of suspicious transactions.',              controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'CMA',    frameworkCode: 'CMA_AML',                controlRef: '§3',   citationText: 'Capital-market AML obligations.',                    controlStatus: 'PARTIAL' },
];

// ─── INCIDENTS (cross-domain) ──────────────────────────────────────────────
export const INCIDENT_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',     controlRef: 'IR-3.1',  citationText: 'Incident response plan.',                                  controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',     controlRef: 'IR-3.5',  citationText: 'NCA HEY 24-hour notification (cyber).',                    controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SDAIA', frameworkCode: 'PDPL',        controlRef: 'Art. 20', citationText: 'PDPL 72-hour breach notification.',                         controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'CITC',  frameworkCode: 'CITC_CYBERSEC', controlRef: '§3.4',  citationText: 'Telecom 24-hour incident reporting.',                       controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',    controlRef: '5.1',     citationText: 'Banking incident notification + escalation.',              controlStatus: 'IMPLEMENTED' },
  // Replaced NIST 800-61 with NCA ECC IR controls — equivalent Saudi anchor.
  { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',     controlRef: 'IR-3.4',  citationText: 'Incident-handling lifecycle (detect → contain → recover).', controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',   frameworkCode: 'ISO_27001',   controlRef: 'A.5.24',  citationText: 'Information security incident management.',                 controlStatus: 'IMPLEMENTED' },
];

// ─── COMPLIANCE (cross-cutting) ────────────────────────────────────────────
// Compliance is the meta-domain — it shows the union of all other anchors.
export const COMPLIANCE_ANCHORS: RegulatorAnchor[] = [
  ...RISK_ANCHORS, ...CYBER_ANCHORS, ...PRIVACY_ANCHORS, ...BCM_ANCHORS,
  ...TPRM_ANCHORS, ...ETHICS_ANCHORS, ...INCIDENT_ANCHORS,
];

// ─── CHANGE (ITIL-anchored) ────────────────────────────────────────────────
export const CHANGE_ANCHORS: RegulatorAnchor[] = [
  { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '§1.6',     citationText: 'Change-management programme.',                                controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.32',   citationText: 'Change management.',                                          controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: '3.5',      citationText: 'Change management of IT systems.',                            controlStatus: 'IMPLEMENTED' },
  { authorityCode: 'AICPA',frameworkCode: 'SOC_2',     controlRef: 'CC8.1',    citationText: 'Change-management approvals + testing.',                      controlStatus: 'IMPLEMENTED' },
];
