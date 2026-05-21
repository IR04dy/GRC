// BCM dependency graph client — audit Tier-2.

import { api } from './api';

export type NodeType = 'BIA' | 'SERVICE' | 'PROCESS' | 'BCP' | 'DRP' | 'STRATEGY' | 'CLOUD' | 'SUPPLIER';

export interface GraphNode {
  id: string;
  type: NodeType;
  label: string;
  data: Record<string, unknown>;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relationship: string;
}

export interface GraphSummary {
  nodeCount: number;
  edgeCount: number;
  nodesByType: Record<string, number>;
  criticalServicesCount: number;
  rtoMismatchesCount: number;
}

export interface GraphPayload {
  nodes: GraphNode[];
  edges: GraphEdge[];
  summary: GraphSummary;
}

export async function fetchDependencyGraph(): Promise<GraphPayload> {
  return api.get<GraphPayload>('/api/v1/bcm/dependency-graph');
}

/** Stable column ordering for layered layout — left to right by type. */
export const COLUMN_ORDER: NodeType[] = ['BIA', 'SERVICE', 'PROCESS', 'BCP', 'DRP', 'STRATEGY', 'CLOUD', 'SUPPLIER'];

/** Hex fill per node type — matches the UI accent palette. */
export const NODE_FILL: Record<NodeType, string> = {
  BIA:      '#3a80c8',  // info blue
  SERVICE:  '#2e9c5e',  // success green
  PROCESS:  '#5f7c8c',  // neutral slate
  BCP:      '#c47e00',  // warning amber
  DRP:      '#9b4dca',  // accent purple
  STRATEGY: '#0d8eae',  // accent teal
  CLOUD:    '#c8362c',  // danger red (cloud = vendor risk)
  SUPPLIER: '#8a3ab9',  // accent magenta
};
