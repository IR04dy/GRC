// BCM library client — Tier-3 #15 & #16 + Tier-4 #28.
//
// Read-only catalogs (plan templates · exercise scenarios · KSA peak
// seasons) plus the "clone template" write action. The shared `api`
// helper resolves to the parsed JSON body — DO NOT chain `.data`.

import { api } from './api';
import type { BcpPlan, BcpPlanType } from './bcpPlans';

// ─── Types ────────────────────────────────────────────────────────

export interface BcpPlanTemplate {
  id:                 number;
  templateCode:       string;
  templateNameEn:     string;
  templateNameAr?:    string;
  planType:           BcpPlanType;
  scenarioCategory:   string;
  descriptionEn?:     string;
  descriptionAr?:     string;
  defaultRtoHours?:   number;
  defaultRpoHours?:   number;
  defaultMtpdHours?:  number;
  sectionsJson:       string;
  tagsJson?:          string;
}

export interface ExerciseScenario {
  id:                  number;
  scenarioCode:        string;
  titleEn:             string;
  titleAr?:            string;
  scenarioCategory:    string;
  exerciseType:        'TABLETOP'|'WALKTHROUGH'|'FUNCTIONAL'|'FAILOVER'|'SIMULATION'|'FULL_DR';
  durationMinutes:     number;
  descriptionEn:       string;
  descriptionAr?:      string;
  objectivesEn?:       string;
  objectivesAr?:       string;
  injectsJson:         string;
  successCriteriaJson: string;
  roleAssignmentsJson?: string;
  tagsJson?:           string;
}

export interface PeakSeason {
  id:                  number;
  seasonCode:          string;
  seasonNameEn:        string;
  seasonNameAr:        string;
  seasonType:          string;
  gregorianStartDate:  string;
  gregorianEndDate:    string;
  hijriStartYear?:     number;
  hijriStartMonth?:    number;
  riskDimensionsJson?: string;
  affectedServices?:   string;
  recommendedActions?: string;
  notesEn?:            string;
  notesAr?:            string;
}

// ─── Endpoints ────────────────────────────────────────────────────

const BASE = '/api/v1/bcm/library';

export function listPlanTemplates(planType?: BcpPlanType, category?: string): Promise<BcpPlanTemplate[]> {
  const q = new URLSearchParams();
  if (planType) q.set('planType', planType);
  if (category) q.set('category', category);
  return api.get<BcpPlanTemplate[]>(`${BASE}/plan-templates${q.toString() ? '?' + q : ''}`);
}

export function clonePlanTemplate(code: string,
                                  titleEn?: string, titleAr?: string,
                                  sourceBiaId?: number): Promise<BcpPlan> {
  return api.post<BcpPlan>(`${BASE}/plan-templates/${encodeURIComponent(code)}/clone`,
    { titleEn, titleAr, sourceBiaId });
}

export function listExerciseScenarios(category?: string, type?: string): Promise<ExerciseScenario[]> {
  const q = new URLSearchParams();
  if (category) q.set('category', category);
  if (type)     q.set('type', type);
  return api.get<ExerciseScenario[]>(`${BASE}/exercise-scenarios${q.toString() ? '?' + q : ''}`);
}

export function listPeakSeasons(): Promise<PeakSeason[]> {
  return api.get<PeakSeason[]>(`${BASE}/peak-seasons`);
}

export function upcomingPeakSeasons(daysAhead = 120): Promise<PeakSeason[]> {
  return api.get<PeakSeason[]>(`${BASE}/peak-seasons/upcoming?daysAhead=${daysAhead}`);
}
