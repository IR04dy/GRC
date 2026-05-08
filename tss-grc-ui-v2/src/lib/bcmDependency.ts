// BCM dependency mapping — Sprint 6 follow-on (B4).
//
// Process → asset → vendor → region. The Sprint 5 asset spine already
// records asset dependencies + hostedByVendor; this layer turns that
// graph into the BCM-team narrative: "if AWS Riyadh goes down, here are
// the processes that fail, in RTO order, with the vendors holding each."

import {
  ASSETS, transitiveBlastRadius, upstreamOf, downstreamOf,
  type Asset,
} from './assets';

// ─── Dependency-tree node ──────────────────────────────────────────────────

export interface DepNode {
  asset: Asset;
  /** Distance from root (0 = root). */
  level: number;
  /** Path of asset codes from root → this node. */
  path: string[];
}

export function buildUpstreamTree(rootCode: string, maxDepth = 4): DepNode[] {
  const root = ASSETS.find(a => a.code === rootCode);
  if (!root) return [];
  const out: DepNode[] = [{ asset: root, level: 0, path: [root.code] }];
  const visited = new Set<string>([root.code]);
  const queue: DepNode[] = [out[0]];
  while (queue.length > 0) {
    const cur = queue.shift()!;
    if (cur.level >= maxDepth) continue;
    for (const up of upstreamOf(cur.asset.code)) {
      if (visited.has(up.code)) continue;
      visited.add(up.code);
      const node: DepNode = {
        asset: up,
        level: cur.level + 1,
        path: [...cur.path, up.code],
      };
      out.push(node);
      queue.push(node);
    }
  }
  return out;
}

export function buildDownstreamTree(rootCode: string, maxDepth = 4): DepNode[] {
  const root = ASSETS.find(a => a.code === rootCode);
  if (!root) return [];
  const out: DepNode[] = [{ asset: root, level: 0, path: [root.code] }];
  const visited = new Set<string>([root.code]);
  const queue: DepNode[] = [out[0]];
  while (queue.length > 0) {
    const cur = queue.shift()!;
    if (cur.level >= maxDepth) continue;
    for (const down of downstreamOf(cur.asset.code)) {
      if (visited.has(down.code)) continue;
      visited.add(down.code);
      const node: DepNode = {
        asset: down,
        level: cur.level + 1,
        path: [...cur.path, down.code],
      };
      out.push(node);
      queue.push(node);
    }
  }
  return out;
}

// ─── Critical processes ────────────────────────────────────────────────────

export function criticalProcesses(): Asset[] {
  return ASSETS.filter(a => a.type === 'PROCESS' && (a.criticality === 'CRITICAL' || a.criticality === 'HIGH'));
}

// ─── Vendor / region rollup ────────────────────────────────────────────────
//
// Walk a process's full upstream tree to enumerate every distinct vendor
// and cloud region it ultimately depends on. Tells the BCM team "if this
// vendor disappears, X processes fail."

export interface VendorRollup {
  vendorCode: string;
  /** Process codes that depend on this vendor. */
  processCodes: string[];
}

export function vendorRollupForProcesses(processes: ReadonlyArray<Asset> = criticalProcesses()): VendorRollup[] {
  const map = new Map<string, Set<string>>();
  for (const p of processes) {
    const tree = buildUpstreamTree(p.code, 8);
    for (const node of tree) {
      if (node.asset.hostedByVendor) {
        if (!map.has(node.asset.hostedByVendor)) map.set(node.asset.hostedByVendor, new Set());
        map.get(node.asset.hostedByVendor)!.add(p.code);
      }
    }
  }
  return Array.from(map.entries())
    .map(([vendorCode, set]) => ({ vendorCode, processCodes: Array.from(set) }))
    .sort((a, b) => b.processCodes.length - a.processCodes.length);
}

export interface RegionRollup {
  regionCode: string;
  processCodes: string[];
}

export function regionRollupForProcesses(processes: ReadonlyArray<Asset> = criticalProcesses()): RegionRollup[] {
  const map = new Map<string, Set<string>>();
  for (const p of processes) {
    const tree = buildUpstreamTree(p.code, 8);
    for (const node of tree) {
      if (node.asset.type === 'CLOUD_REGION') {
        if (!map.has(node.asset.code)) map.set(node.asset.code, new Set());
        map.get(node.asset.code)!.add(p.code);
      }
    }
  }
  return Array.from(map.entries())
    .map(([regionCode, set]) => ({ regionCode, processCodes: Array.from(set) }))
    .sort((a, b) => b.processCodes.length - a.processCodes.length);
}

// ─── RTO chain analysis ────────────────────────────────────────────────────
//
// For a given process, the realistic RTO is the WORST RTO across its full
// upstream chain (an asset can't recover faster than its slowest dependency).
// Surfaces the "RTO mismatch" risk that BCM teams flag in audits.

export interface RtoChainResult {
  processCode: string;
  processNameEn: string;
  processNameAr: string;
  declaredRtoHours?: number;
  /** Worst RTO from the upstream chain. */
  effectiveRtoHours: number;
  /** Asset code that drives the worst RTO. */
  bottleneckAssetCode: string;
  /** Whether there's a mismatch (effective > declared). */
  mismatch: boolean;
}

export function rtoChainAnalysis(processes: ReadonlyArray<Asset> = criticalProcesses()): RtoChainResult[] {
  return processes.map(p => {
    const tree = buildUpstreamTree(p.code, 8);
    let worst = p.rtoHours ?? 0;
    let bottleneck = p.code;
    for (const node of tree) {
      if (node.asset.rtoHours != null && node.asset.rtoHours > worst) {
        worst = node.asset.rtoHours;
        bottleneck = node.asset.code;
      }
    }
    return {
      processCode: p.code,
      processNameEn: p.nameEn,
      processNameAr: p.nameAr,
      declaredRtoHours: p.rtoHours,
      effectiveRtoHours: worst,
      bottleneckAssetCode: bottleneck,
      mismatch: p.rtoHours != null && worst > p.rtoHours,
    };
  });
}

// ─── Aggregate stats ───────────────────────────────────────────────────────

export interface BcmDepStats {
  criticalProcessCount: number;
  uniqueVendors: number;
  uniqueRegions: number;
  rtoMismatches: number;
  /** Largest blast radius — process whose failure impacts the most assets. */
  largestProcessImpact: { processCode: string; impact: number };
}

export function aggregateBcmDeps(): BcmDepStats {
  const processes = criticalProcesses();
  const vendors = vendorRollupForProcesses(processes);
  const regions = regionRollupForProcesses(processes);
  const rto = rtoChainAnalysis(processes);
  const blastList = processes.map(p => ({
    processCode: p.code,
    impact: transitiveBlastRadius(p.code).size,
  }));
  const largest = blastList.sort((a, b) => b.impact - a.impact)[0];
  return {
    criticalProcessCount: processes.length,
    uniqueVendors: vendors.length,
    uniqueRegions: regions.length,
    rtoMismatches: rto.filter(r => r.mismatch).length,
    largestProcessImpact: largest,
  };
}
