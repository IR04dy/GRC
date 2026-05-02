export interface Supplier {
  id: number;
  name: string;
  country: string;
  category: 'Hardware' | 'SaaS' | 'Logistics' | 'Professional Services' | 'Manufacturing';
  score: number;
  status: 'ACTIVE' | 'UNDER_REVIEW' | 'PENDING' | 'CLOSED';
}

export const SUPPLIERS: Supplier[] = [
  { id: 1, name: 'Aramco IT Procurement',    country: 'KSA', category: 'SaaS',                  score: 9.4, status: 'ACTIVE' },
  { id: 2, name: 'Cisco MENA Distribution',   country: 'UAE', category: 'Hardware',              score: 8.8, status: 'ACTIVE' },
  { id: 3, name: 'Deloitte ME Advisory',      country: 'UAE', category: 'Professional Services', score: 8.5, status: 'ACTIVE' },
  { id: 4, name: 'Tabuk Logistics Group',     country: 'KSA', category: 'Logistics',             score: 7.2, status: 'UNDER_REVIEW' },
  { id: 5, name: 'Bahri Maritime Services',   country: 'KSA', category: 'Logistics',             score: 6.8, status: 'ACTIVE' },
  { id: 6, name: 'Riyadh Cables Group',       country: 'KSA', category: 'Manufacturing',         score: 5.4, status: 'PENDING' },
  { id: 7, name: 'TechMahindra Saudi',        country: 'IN',  category: 'Professional Services', score: 7.9, status: 'ACTIVE' },
  { id: 8, name: 'NewCo Trading',             country: 'IN',  category: 'Manufacturing',         score: 3.2, status: 'UNDER_REVIEW' },
];
