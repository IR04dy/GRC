# 04 — API Object Model
> **HISTORIC — point-in-time snapshot (all files 2026-05-18). Not a statement of current state.**
> ⚠ **This is PACK B of two generations, and neither supersedes the other** — finding **F2-135**. This pack is 7 days newer than PACK A (`Saudi-GRC-Platform-Integration/` at the repo root) and deeper on the 16 shared sections, but **does not contain it**: PACK A's sections 16–18 (AI features, frontend requirements, Excel workbook — 1,010 lines) have no counterpart here, and this pack's `16-Assumptions` reuses that number for different content. Which pack governs is an **open owner decision**.
> For what the platform is today read [`CLAUDE.md`](../../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


JSON shapes the REST surface returns. Generated from the JPA
entities via SpringDoc OpenAPI; live spec at
`http://<host>:8084/v3/api-docs` and Swagger UI at
`/swagger-ui.html` (dev only).

## Canonical envelope conventions

- **Single resource:** the entity as JSON. Field names = camelCase
  Java getter names.
- **Paged list:** Spring Data `Page<T>` shape:
  ```json
  { "content": [...], "totalElements": 123, "totalPages": 7,
    "number": 0, "size": 20, "first": true, "last": false }
  ```
- **Error responses:** Spring's `ProblemDetail` (RFC 7807):
  ```json
  { "type": "/errors/illegal-transition", "title": "Illegal transition",
    "status": 400, "detail": "Cannot move PUBLISHED to DRAFT" }
  ```
- **Tenant scoping:** implicit. `organisationId` is read from the
  JWT, never accepted from clients.
- **Audit fields:** `createdAt`, `createdBy`, `updatedAt`, `updatedBy`
  are read-only on responses.

## Per-module headline DTOs

### Authority Document (F1)

```json
{
  "id": 12,
  "organisationId": 1,
  "code": "NCA-ECC-V2",
  "family": "NCA Cyber",
  "jurisdiction": "KSA",
  "issuer": "NCA",
  "version": "2.0",
  "titleEn": "Essential Cybersecurity Controls v2",
  "titleAr": "الضوابط الأساسية للأمن السيبراني — الإصدار الثاني",
  "summaryEn": "NCA mandatory baseline for every regulated entity.",
  "summaryAr": "الحد الأدنى للأمن السيبراني المُلزِم لكل جهة خاضعة.",
  "effectiveDateG": "2024-01-01",
  "effectiveDateH": "1445-06-19",
  "isPlaceholder": true,
  "translationStatus": "EN_ONLY",
  "status": "ACTIVE"
}
```

### Citation

```json
{
  "id": 234,
  "authorityDocumentId": 12,
  "citationCode": "2-1-3",
  "parentCitationCode": "2-1",
  "depth": 2,
  "titleEn": "Asset inventory completeness",
  "titleAr": "اكتمال جرد الأصول",
  "requirementTextEn": "Maintain a complete, up-to-date asset inventory…",
  "weight": 1.0,
  "mandatory": true,
  "isPlaceholder": true,
  "translationStatus": "EN_ONLY"
}
```

### Control (F2 spine)

```json
{
  "id": 45,
  "controlCode": "C-IAM-014",
  "version": "1.0",
  "domain": "CYBER",
  "titleEn": "Privileged access reviewed quarterly",
  "titleAr": "مراجعة الصلاحيات المميّزة فصلياً",
  "controlType": "DETECTIVE",
  "automationLevel": "SEMI",
  "frequency": "QUARTERLY",
  "status": "ACTIVE",
  "legacySourceModule": "cyber",
  "legacyId": 117
}
```

### Control Mapping

```json
{
  "id": 7,
  "controlId": 45,
  "citationId": 234,
  "coverage": "FULL",
  "rationaleEn": "Quarterly privileged-access review satisfies ECC 2-2 IAM.",
  "lastReviewedAt": "2026-05-17T11:23:00"
}
```

### Risk

```json
{
  "id": 100,
  "riskCode": "RISK-CYB-0042",
  "titleEn": "Inadequate privileged-access governance",
  "titleAr": "حوكمة الصلاحيات المميّزة غير كافية",
  "categoryId": 7,
  "status": "TREATMENT",
  "inherentLikelihood": 4, "inherentImpact": 5, "inherentScore": 20,
  "residualLikelihood": 3, "residualImpact": 4, "residualScore": 12,
  "ownerId": 12, "sourceModule": "ERM"
}
```

### Risk Assessment

```json
{ "id": 501, "riskId": 100, "assessorId": 7,
  "assessmentDate": "2026-05-17", "likelihood": 3, "impact": 4,
  "assessmentNotesEn": "Post-CAPA AR-09 closure" }
```

### Risk Treatment

```json
{ "id": 600, "riskId": 100, "treatmentType": "MITIGATE",
  "descriptionEn": "Roll out PAM tool across Tier-1 systems",
  "ownerId": 22, "dueDate": "2026-09-30", "status": "IN_PROGRESS" }
```

### Audit Log Entry (F7)

```json
{
  "id": 9012,
  "actionType": "TRANSITION",
  "entityType": "RiskTreatment", "entityId": 600,
  "userUsername": "alice", "userRole": "RISK_MANAGER",
  "workflowStatusBefore": "PLANNED", "workflowStatusAfter": "IN_PROGRESS",
  "ipAddress": "10.20.30.40", "userAgent": "Mozilla/5.0 …",
  "sourceModule": "risk", "sourceEndpoint": "POST /api/v1/risk/risks/100/treatments/600/transition",
  "correlationId": "f1a2…",
  "severity": "INFO",
  "message": "Treatment 600 transitioned PLANNED → IN_PROGRESS",
  "createdAt": "2026-05-17T11:24:01"
}
```

### Automation Rule (F8)

```json
{
  "id": 4,
  "ruleCode": "AR-04",
  "nameEn": "Cyber incident affecting personal data → 72-hour PDPL breach timer",
  "nameAr": "حادث سيبراني يمسّ بيانات شخصية → تشغيل عدّاد 72 ساعة",
  "triggerEvent": "INCIDENT_CREATED",
  "conditionJson": "{\"affects_personal_data\":true,\"incident_type\":\"CYBER\"}",
  "actionJson": "{\"type\":\"CREATE_RECORD\",\"entity\":\"DataBreach\",\"then\":{\"type\":\"ESCALATE\",\"to_role\":\"DPO\",\"after_minutes\":4320}}",
  "priority": 5,
  "enabled": true,
  "lastFiredAt": "2026-05-17T11:24:00",
  "fireCount": 3,
  "source": "PROMPT_14"
}
```

### Compliance Posture row (F6)

```json
{
  "authorityDocumentId": 12,
  "code": "NCA-ECC-V2",
  "family": "NCA Cyber",
  "titleEn": "Essential Cybersecurity Controls v2",
  "totalCitations": 9, "mappedCitations": 5, "coveragePct": 55.6,
  "controlsByDomain": { "CYBER": 4, "TPRM": 1 }
}
```

### Module License (F9)

```json
{
  "organisationId": 1,
  "moduleCode": "RISK",
  "tier": "ENTERPRISE",
  "seatCount": null,
  "validFrom": "2026-01-01", "validTo": null,
  "enabled": true
}
```

## Standard request headers

- `Authorization: Bearer <access-jwt>` — required on every endpoint
  except `/api/v1/auth/login` + `/api/v1/auth/refresh`.
- `X-Correlation-Id: <uuid>` — optional; if missing the platform
  generates one for the request and stamps it on every audit row.
- `Accept-Language: ar | en` — UI hint; backend always returns both
  bilingual columns, frontend picks.
- `Content-Type: application/json` — required on POST/PUT/PATCH.

---

**Full live API spec:** `http://<host>:8084/v3/api-docs`
**Sample payloads per endpoint:** `13-API-Specification/13_Sample_JSON_Payloads.md`.
