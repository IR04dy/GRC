// Per-entity regulator anchors — every Finding / Control / Policy / Risk
// is anchored to one or more regulator + control citation. Same shape as
// evidenceAnchors but keyed by entity-id. industry-standard GRC tooling "Issue" + "Policy"
// + "Control" cite-table pattern.
import type { RegulatorAnchor } from './evidenceAnchors';

// ── Findings ──────────────────────────────────────────────────────────────
// Keyed by COMPLIANCE_FINDINGS.id (mock ids 31-38 from mockCompliance.ts).
export const FINDING_ANCHORS_BY_ID: Record<number, RegulatorAnchor[]> = {
  31: [
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Article 29', citationText: 'Cross-border transfer safeguards.', controlStatus: 'NOT_IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.5',      controlStatus: 'NOT_IMPLEMENTED' },
  ],
  32: [
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'AC-2.1.2', citationText: 'Privileged session recording.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.18',   controlStatus: 'PARTIAL' },
    { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',  controlRef: '3.1.4',    controlStatus: 'PARTIAL' },
  ],
  33: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.8.16', citationText: 'Monitoring activities.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'IR-3.2', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCA', frameworkCode: 'NCA_ECC',  controlRef: 'DE.AE',  controlStatus: 'PARTIAL' },
  ],
  34: [
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: 'CYB-1.4.3', citationText: 'Cryptographic key management.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.24',    controlStatus: 'PARTIAL' },
    { authorityCode: 'PCI',  frameworkCode: 'PCI_DSS',   controlRef: '3.6',       controlStatus: 'PARTIAL' },
  ],
  35: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '8.5.4', citationText: 'Exercise + testing programme.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP', controlRef: '§6',    controlStatus: 'IMPLEMENTED' },
  ],
  36: [
    { authorityCode: 'AICPA', frameworkCode: 'SOC_2',     controlRef: 'CC9.2', citationText: 'Vendor + business-partner management.', controlStatus: 'NOT_IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.21', controlStatus: 'NOT_IMPLEMENTED' },
    { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',  controlRef: '4.1',    controlStatus: 'NOT_IMPLEMENTED' },
  ],
  37: [
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: '1.6',    citationText: 'Risk-acceptance + exception management.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.8.24', controlStatus: 'PARTIAL' },
  ],
  38: [
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Article 18', citationText: 'Retention limitation.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.4',      controlStatus: 'PARTIAL' },
  ],
};

// ── Controls (compliance) ─────────────────────────────────────────────────
// Keyed by COMPLIANCE_CONTROLS.id (mock ids 1-10).
export const CONTROL_ANCHORS_BY_ID: Record<number, RegulatorAnchor[]> = {
  1: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.1',   citationText: 'Information security policies.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'PR-1.1',  controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',  controlRef: '1.1.1',   controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'AICPA', frameworkCode: 'SOC_2',     controlRef: 'CC1.4',   controlStatus: 'IMPLEMENTED' },
  ],
  2: [
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.5.7', citationText: 'Threat intelligence.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCA', frameworkCode: 'NCA_ECC',  controlRef: 'ID.RA', controlStatus: 'PARTIAL' },
  ],
  3: [
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'AC-2',   citationText: 'Account management lifecycle.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.16', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'AICPA', frameworkCode: 'SOC_2',     controlRef: 'CC6.2',  controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'PCI',   frameworkCode: 'PCI_DSS',   controlRef: '8.1',    controlStatus: 'IMPLEMENTED' },
  ],
  4: [
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: 'IR-3', citationText: 'Incident response plan.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.5.24', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'NCA', frameworkCode: 'NCA_ECC', controlRef: '§3.2', controlStatus: 'IMPLEMENTED' },
  ],
  5: [
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Article 29', citationText: 'Cross-border transfer safeguards.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.5',      controlStatus: 'PARTIAL' },
  ],
  6: [
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Article 23', citationText: 'Data-subject rights handling.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.3',      controlStatus: 'IMPLEMENTED' },
  ],
  7: [
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',  controlRef: 'CYB-1.4', citationText: 'Cryptographic key management.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.24',  controlStatus: 'PARTIAL' },
    { authorityCode: 'PCI',  frameworkCode: 'PCI_DSS',   controlRef: '3.5',     controlStatus: 'PARTIAL' },
  ],
  8: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '8.4', citationText: 'Continuity strategies.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP', controlRef: '§5.3', controlStatus: 'PARTIAL' },
  ],
  9: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.8.16', citationText: 'Monitoring activities.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'IR-3.2', controlStatus: 'PARTIAL' },
  ],
  10: [
    { authorityCode: 'AICPA', frameworkCode: 'SOC_2',     controlRef: 'CC3.1', citationText: 'Information communicated externally.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.5', controlStatus: 'IMPLEMENTED' },
  ],
};

// ── Policies ──────────────────────────────────────────────────────────────
// Keyed by POLICIES.id (mock ids 51-58).
export const POLICY_ANCHORS_BY_ID: Record<number, RegulatorAnchor[]> = {
  51: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.1',   citationText: 'ISMS policy.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'PR-1.1',  controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA',  frameworkCode: 'SAMA_CSF',  controlRef: '1.1.1',   controlStatus: 'IMPLEMENTED' },
  ],
  52: [
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Articles 4-5',  citationText: 'Privacy + lawful basis policy.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.2',         controlStatus: 'IMPLEMENTED' },
  ],
  53: [
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_AML', controlRef: '§4.3',        citationText: 'AML programme requirements.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'FATF', frameworkCode: 'FATF',     controlRef: 'R10 + R20',   controlStatus: 'IMPLEMENTED' },
  ],
  54: [
    { authorityCode: 'NAZAHA', frameworkCode: 'NAZAHA_ANTI_CORRUPTION', controlRef: '§4', citationText: 'Code of conduct + anti-corruption.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',    frameworkCode: 'ISO_37001',              controlRef: '§7.3', controlStatus: 'IMPLEMENTED' },
  ],
  55: [
    { authorityCode: 'SDAIA', frameworkCode: 'SDAIA_AI_ETHICS', controlRef: '§3', citationText: 'Acceptable AI use principles.', controlStatus: 'PARTIAL' },
  ],
  56: [
    { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '§4.4', citationText: 'BCM policy approved by leadership.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP', controlRef: '§4.1', controlStatus: 'PARTIAL' },
  ],
  57: [
    { authorityCode: 'NCA', frameworkCode: 'NCA_OTCC', controlRef: 'SR-3', citationText: 'TPRM policy.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_28001',    controlRef: '§5',   controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_CSF',     controlRef: '4.1',  controlStatus: 'IMPLEMENTED' },
  ],
  58: [
    { authorityCode: 'ISO', frameworkCode: 'ISO_27001', controlRef: 'A.8.24', citationText: 'Sunset of legacy crypto.', controlStatus: 'PARTIAL' },
    { authorityCode: 'PCI', frameworkCode: 'PCI_DSS',   controlRef: '4.1',    controlStatus: 'PARTIAL' },
  ],
};

// ── Risks ──────────────────────────────────────────────────────────────────
// Keyed by MOCK_RISKS.id.
export const RISK_ANCHORS_BY_ID: Record<number, RegulatorAnchor[]> = {
  1:  [ // Sensitive data breach
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.34', citationText: 'Privacy + protection of PII.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Article 20', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'IR-3.5',     controlStatus: 'IMPLEMENTED' },
  ],
  2:  [ // Critical service outage
    { authorityCode: 'ISO',   frameworkCode: 'ISO_22301', controlRef: '§8.4', citationText: 'BCP scope.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP', controlRef: '§5',   controlStatus: 'PARTIAL' },
  ],
  3:  [ // Supplier concentration
    { authorityCode: 'NRC',  frameworkCode: 'NRC_TIER_MAPPING', controlRef: '§5.2', citationText: 'Single-point-of-failure register.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCA', frameworkCode: 'NCA_OTCC',     controlRef: 'SR-5', controlStatus: 'PARTIAL' },
  ],
  4:  [ // Phishing
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '2.10.1', citationText: 'Awareness programme.', controlStatus: 'IMPLEMENTED' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.6.3.1', controlStatus: 'IMPLEMENTED' },
  ],
  5:  [ // Regulatory non-compliance
    { authorityCode: 'CMA', frameworkCode: 'CMA_CG', controlRef: '§5', citationText: 'CG compliance reporting.', controlStatus: 'PARTIAL' },
  ],
  6:  [ // Cloud mis-configuration
    { authorityCode: 'NCA',  frameworkCode: 'NCA_CCC',   controlRef: '§3.1', citationText: 'Cloud cybersecurity baseline.', controlStatus: 'PARTIAL' },
    { authorityCode: 'CITC', frameworkCode: 'CITC_CLOUD', controlRef: '§4.2', controlStatus: 'PARTIAL' },
  ],
  7:  [ // AML transaction screening gap
    { authorityCode: 'SAMA', frameworkCode: 'SAMA_AML', controlRef: '§5', citationText: 'Transaction monitoring.', controlStatus: 'PARTIAL' },
    { authorityCode: 'FATF', frameworkCode: 'FATF',     controlRef: 'R20', controlStatus: 'PARTIAL' },
  ],
  8:  [ // Insider misuse of privileged access
    { authorityCode: 'NCA',   frameworkCode: 'NCA_ECC',   controlRef: 'AC-2.1.2', citationText: 'Privileged-access oversight.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27001', controlRef: 'A.5.18',   controlStatus: 'PARTIAL' },
  ],
  9:  [ // Vendor BCP inadequacy
    { authorityCode: 'NCEMA', frameworkCode: 'NCEMA_BCP', controlRef: '§7',  citationText: 'Vendor BCP requirements.', controlStatus: 'PARTIAL' },
    { authorityCode: 'NCA', frameworkCode: 'NCA_OTCC', controlRef: 'SR-3', controlStatus: 'PARTIAL' },
  ],
  10: [ // Cross-border data transfer compliance
    { authorityCode: 'SDAIA', frameworkCode: 'PDPL',      controlRef: 'Article 29', citationText: 'Cross-border transfer.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',   frameworkCode: 'ISO_27701', controlRef: 'A.7.5',      controlStatus: 'PARTIAL' },
  ],
  11: [ // IT-OT convergence
    { authorityCode: 'NCA',  frameworkCode: 'NCA_ECC',   controlRef: '5.1.1', citationText: 'Network segmentation.', controlStatus: 'PARTIAL' },
    { authorityCode: 'ISO',  frameworkCode: 'ISO_27001', controlRef: 'A.8.20', controlStatus: 'PARTIAL' },
  ],
  12: [ // Climate-driven operational risk
    { authorityCode: 'CMA',  frameworkCode: 'CMA_ESG', controlRef: '§3', citationText: 'Sustainability disclosure.', controlStatus: 'PARTIAL' },
    { authorityCode: 'COSO', frameworkCode: 'COSO_ERM', controlRef: 'Strategy #2', controlStatus: 'PARTIAL' },
  ],
};
