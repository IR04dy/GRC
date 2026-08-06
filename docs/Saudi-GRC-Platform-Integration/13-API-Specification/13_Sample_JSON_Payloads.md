# 13 — Sample JSON Payloads
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


Curated samples for the most-used endpoints. Pair with Swagger UI
at `/swagger-ui.html` for the full surface.

## Auth

### `POST /api/v1/auth/login`

Request:
```json
{ "username": "admin", "password": "Admin@12345" }
```
Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "expiresInSec": 3600,
  "user": {
    "id": 1, "username": "admin", "roles": ["PLATFORM_ADMIN"],
    "organisationId": 1, "enabled_modules": ["RISK","BCM","SCM","CYBER","PDPL","TPRM","POLICY","NDI","COMPLIANCE","AUDIT_LC","INCIDENT","EVIDENCE","REPORTING","REGULATORY","CONTROL_LIB","POSTURE","AUTOMATION","AUDIT_LOG","PLATFORM_ADMIN"]
  }
}
```

## Risk

### `POST /api/v1/risk/risks`

Request:
```json
{
  "titleEn": "Inadequate privileged-access governance",
  "titleAr": "حوكمة الصلاحيات المميّزة غير كافية",
  "categoryId": 7,
  "descriptionEn": "Lack of quarterly review of privileged accounts may allow standing access for ex-employees.",
  "inherentLikelihood": 4,
  "inherentImpact": 5,
  "ownerUserId": 12
}
```
Response (201):
```json
{
  "id": 100, "riskCode": "RISK-CYB-0042",
  "titleEn": "Inadequate privileged-access governance",
  "inherentLikelihood": 4, "inherentImpact": 5, "inherentScore": 20,
  "residualLikelihood": null, "residualImpact": null, "residualScore": null,
  "status": "OPEN", "ownerUserId": 12, "createdAt": "2026-05-17T11:00:00"
}
```

### `POST /api/v1/risk/risks/100/assessments`

Request:
```json
{ "likelihood": 3, "impact": 4,
  "assessmentDate": "2026-05-17",
  "assessmentNotesEn": "Post-CAPA AR-09 closure" }
```
Response:
```json
{ "id": 501, "riskId": 100, "likelihood": 3, "impact": 4,
  "assessmentDate": "2026-05-17",
  "assessmentNotesEn": "Post-CAPA AR-09 closure" }
```

(The parent risk's residualLikelihood / residualImpact /
residualScore now read 3 / 4 / 12.)

## Authority Documents (F1)

### `GET /api/v1/regulatory/authority-documents?family=NCA Cyber`

```json
[
  { "id": 1, "code": "NCA-ECC-V2", "family": "NCA Cyber", "issuer": "NCA", "version": "2.0",
    "titleEn": "Essential Cybersecurity Controls v2", "titleAr": "الضوابط الأساسية للأمن السيبراني",
    "status": "ACTIVE", "isPlaceholder": true, "translationStatus": "EN_ONLY" },
  { "id": 2, "code": "NCA-CSCC-V1", "family": "NCA Cyber", "issuer": "NCA", "version": "1.0",
    "titleEn": "Critical Systems Cybersecurity Controls", "titleAr": "ضوابط الأمن السيبراني للأنظمة الحساسة",
    "status": "ACTIVE", "isPlaceholder": true, "translationStatus": "EN_ONLY" }
]
```

## Control Library (F2)

### `POST /api/v1/control-mappings`

Request:
```json
{
  "controlId": 45,
  "citationId": 234,
  "coverage": "FULL",
  "rationaleEn": "Quarterly privileged-access review satisfies ECC 2-2 IAM."
}
```

### `GET /api/v1/risks/100/control-links`

```json
[
  { "id": 9, "riskId": 100, "controlId": 45, "linkType": "MITIGATES",
    "effectiveness": "EFFECTIVE", "rationaleEn": "PAM tool in production",
    "lastReviewedAt": "2026-05-17T11:00:00" }
]
```

## Compliance Posture (F5/F6)

### `GET /api/v1/regulatory/authority-documents/1/coverage`

```json
{
  "authorityDocumentId": 1, "code": "NCA-ECC-V2", "family": "NCA Cyber",
  "titleEn": "Essential Cybersecurity Controls v2",
  "totalCitations": 9, "mappedCitations": 5,
  "fullCoverage": 4, "partialCoverage": 1, "compensatingCoverage": 0,
  "gapCitations": 4,
  "controlsByDomain": { "CYBER": 4, "TPRM": 1 },
  "gaps": [
    { "id": 23, "citationCode": "3", "titleEn": "Cybersecurity Resilience" },
    { "id": 24, "citationCode": "5", "titleEn": "Industrial Control Systems Cybersecurity" }
  ],
  "coveragePct": 55.6
}
```

### `GET /api/v1/regulatory/posture`

```json
{
  "rows": [
    { "authorityDocumentId": 1, "code": "NCA-ECC-V2", "family": "NCA Cyber",
      "titleEn": "ECC v2", "totalCitations": 9, "mappedCitations": 5,
      "coveragePct": 55.6, "controlsByDomain": { "CYBER": 4, "TPRM": 1 } }
  ],
  "domains": ["BCM","CYBER","PRIVACY","TPRM"],
  "familyTotals": { "NCA Cyber": 6, "SAMA": 4, "Privacy": 1, "International": 5 }
}
```

## Audit Log (F7)

### `GET /api/v1/audit-log?actionType=TRANSITION&page=0&size=20`

```json
{
  "content": [
    { "id": 9012, "actionType": "TRANSITION",
      "entityType": "RiskTreatment", "entityId": 600,
      "workflowStatusBefore": "PLANNED", "workflowStatusAfter": "IN_PROGRESS",
      "userUsername": "alice", "ipAddress": "10.20.30.40",
      "correlationId": "f1a2-3e4b…", "severity": "INFO",
      "createdAt": "2026-05-17T11:24:01" }
  ],
  "totalElements": 1, "totalPages": 1, "number": 0, "size": 20
}
```

## Automation Rules (F8)

### `PATCH /api/v1/automation/rules/4`

Request:
```json
{ "enabled": false }
```

### `GET /api/v1/automation/runs?page=0&size=20`

```json
{
  "content": [
    { "id": 700, "ruleId": 4, "triggerEvent": "INCIDENT_CREATED",
      "entityType": "Incident", "entityId": 42,
      "correlationId": "c-…", "outcome": "SUCCESS",
      "resultSummary": "AUDIT(WARN) emitted",
      "firedAt": "2026-05-17T11:23:55" }
  ]
}
```

## Module Licensing (F9)

### `GET /api/v1/licensing/me/enabled`

```json
{
  "organisationId": 1,
  "enabled_modules": ["RISK","BCM","SCM","CYBER","PDPL","TPRM","POLICY","NDI","COMPLIANCE","AUDIT_LC","INCIDENT","EVIDENCE","REPORTING","REGULATORY","CONTROL_LIB","POSTURE","AUTOMATION","AUDIT_LOG","PLATFORM_ADMIN"]
}
```

### `POST /api/v1/licensing/modules`

Request:
```json
{ "moduleCode": "RISK", "tier": "ENTERPRISE", "validTo": "2027-12-31" }
```

## Errors (RFC 7807)

```json
{
  "type": "/errors/illegal-transition",
  "title": "Illegal transition",
  "status": 400,
  "detail": "Cannot move RETIRED to ACTIVE",
  "instance": "/api/v1/controls/45/transition"
}
```

---

**See also:** Swagger UI at `/swagger-ui.html`.
