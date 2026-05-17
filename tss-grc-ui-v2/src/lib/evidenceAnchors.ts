// Multi-authority anchoring for evidence + controls + findings.
// One evidence item can satisfy controls under several authorities at once
// (collect-once / reuse-many) — industry-standard GRC tooling "Citation" pattern.

export type ControlStatus = 'IMPLEMENTED' | 'PARTIAL' | 'COMPENSATING' | 'NOT_IMPLEMENTED' | 'NOT_APPLICABLE';

export interface RegulatorAnchor {
  /** Authority code (joined to AuthorityDto.code: 'NCA' / 'SAMA' / …). */
  authorityCode: string;
  /** Framework code under that authority (`NCA_ECC`, `SAMA_CSF`). */
  frameworkCode: string;
  /** Citation reference within the framework (`AC-2.1.2.a`, `§4.3`). */
  controlRef: string;
  /** Optional short verbatim citation for the audit trail. */
  citationText?: string;
  /** Effectiveness status reported by this evidence against the cited control. */
  controlStatus: ControlStatus;
}

export const STATUS_TONE: Record<ControlStatus, 'success' | 'warning' | 'accent' | 'danger' | 'neutral'> = {
  IMPLEMENTED:    'success',
  PARTIAL:        'warning',
  COMPENSATING:   'accent',
  NOT_IMPLEMENTED:'danger',
  NOT_APPLICABLE: 'neutral',
};

export const STATUS_LABEL: Record<ControlStatus, string> = {
  IMPLEMENTED:    'Implemented',
  PARTIAL:        'Partial',
  COMPENSATING:   'Compensating',
  NOT_IMPLEMENTED:'Not Implemented',
  NOT_APPLICABLE: 'N/A',
};

/**
 * Pre-built sample anchor sets — used by the demo Evidence catalogue so
 * every evidence row carries multi-authority anchors out of the box.
 */
export const ANCHOR_PACKS: Record<string, RegulatorAnchor[]> = {
  // Authoritative ISMS policy
  ISMS_POLICY: [
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',    controlRef: 'PR-1.1.1', citationText: 'Documented ISMS approved by senior management.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001',  controlRef: 'A.5.1',    citationText: 'Policies for information security.',           controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',   controlRef: '1.1.1',    citationText: 'Information security policy approved by board.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'AICPA',frameworkCode: 'SOC_2',      controlRef: 'CC1.4',    controlStatus: 'IMPLEMENTED' },
  ],
  // Vulnerability scan report
  VULN_SCAN: [
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '5.2.4',    citationText: 'Quarterly vulnerability scanning of all systems.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.8',    citationText: 'Management of technical vulnerabilities.',         controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: '3.3.4',    controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'PCI',  frameworkCode: 'PCI_DSS',   controlRef: '11.3',     citationText: 'Quarterly external + internal vuln scans.',       controlStatus: 'IMPLEMENTED' },
  ],
  // Firewall rules snapshot
  FIREWALL_RULES: [
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '4.2.1',    controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.20.1', citationText: 'Network security configuration.',                  controlStatus: 'PARTIAL' },
  ],
  // PDPL data inventory
  PDPL_INVENTORY: [
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Art. 31',   citationText: 'Records of processing activities (RoPA).',       controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.2.1',   controlStatus: 'PARTIAL' },
  ],
  // Backup validation
  BACKUP_VALIDATION: [
    { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP',  controlRef: '§5.3',     controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '8.13.1',   citationText: 'Backup of information.',                          controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA',  frameworkCode: 'SAMA_BCP',   controlRef: '4.5',      controlStatus: 'IMPLEMENTED' },
  ],
  // SAMA CSF attestation cert
  SAMA_ATTESTATION: [
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: '1.1',       citationText: 'Annual board-attested CSF compliance certificate.', controlStatus: 'NOT_IMPLEMENTED' },
  ],
  // PAM recertification
  PAM_RECERT: [
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'AC-2.1.2', citationText: 'Quarterly recertification of privileged access.',  controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.18',   controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',  controlRef: '3.1.4',    controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'AICPA', frameworkCode: 'SOC_2',     controlRef: 'CC6.3',    citationText: 'Restricts logical access to information assets.',  controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'PCI',   frameworkCode: 'PCI_DSS',   controlRef: '7.1',      controlStatus: 'IMPLEMENTED' },
  ],
  // Internal audit plan
  AUDIT_PLAN: [
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'PR-1.5',   controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.5.35',   citationText: 'Independent review of information security.',       controlStatus: 'IMPLEMENTED' },
  ],
  // Awareness campaign
  AWARENESS: [
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '2.10.1',   citationText: 'Cybersecurity awareness programme.',               controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.6.3.1',  controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'NAZAHA',frameworkCode: 'NAZAHA_ANTI_CORRUPTION', controlRef: '§7', controlStatus: 'IMPLEMENTED' },
  ],
};
