// Sample risk data — used when no JWT is present (demo mode).
// Lifted from the design system's app.jsx so the pilot looks identical to the kit.
import { type RiskResponse } from '../../lib/api';

export const MOCK_RISKS: RiskResponse[] = [
  { id: 1, riskCode: 'R-0042', titleEn: 'Sensitive Data Breach',     titleAr: 'تسريب بيانات حساسة',  categoryNameEn: 'Cyber',       categoryNameAr: 'سيبراني',     ownerFullNameEn: 'Ahmed Al-Qahtani',     status: 'OPEN',         inherentLikelihood: 5, inherentImpact: 4, inherentScore: 20 },
  { id: 2, riskCode: 'R-0041', titleEn: 'Critical Service Outage',   titleAr: 'انقطاع خدمة حرجة',    categoryNameEn: 'Operational', categoryNameAr: 'تشغيلي',     ownerFullNameEn: 'Layla Al-Saud',         status: 'IN_PROGRESS',  inherentLikelihood: 3, inherentImpact: 4, inherentScore: 12 },
  { id: 3, riskCode: 'R-0040', titleEn: 'Supplier Concentration',    titleAr: 'تمركز الموردين',     categoryNameEn: 'Third-Party', categoryNameAr: 'أطراف ثالثة', ownerFullNameEn: 'Mohammed Al-Otaibi',    status: 'MITIGATE',     inherentLikelihood: 3, inherentImpact: 3, inherentScore: 9 },
  { id: 4, riskCode: 'R-0039', titleEn: 'Phishing Campaign',         titleAr: 'هجمات تصيّد',         categoryNameEn: 'Cyber',       categoryNameAr: 'سيبراني',     ownerFullNameEn: 'Sara Al-Dosari',        status: 'TREATED',      inherentLikelihood: 3, inherentImpact: 3, inherentScore: 9 },
  { id: 5, riskCode: 'R-0038', titleEn: 'Regulatory Non-Compliance', titleAr: 'عدم امتثال تنظيمي',   categoryNameEn: 'Compliance',  categoryNameAr: 'امتثال',     ownerFullNameEn: 'Khalid Al-Ghamdi',      status: 'OPEN',         inherentLikelihood: 3, inherentImpact: 5, inherentScore: 15 },
  { id: 6, riskCode: 'R-0037', titleEn: 'Cloud Mis-configuration',   titleAr: 'سوء تهيئة سحابية',    categoryNameEn: 'Tech',        categoryNameAr: 'تقني',       ownerFullNameEn: 'Noura Al-Harbi',        status: 'IN_PROGRESS',  inherentLikelihood: 4, inherentImpact: 2, inherentScore: 8 },
];
