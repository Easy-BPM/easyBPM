import type { WorkflowDefinition } from '../types';

type Edge = { from: string; to: string };

/** History is interleaved across tokens, not a single consecutive path. */
export const getVisitedEdges = (definition: WorkflowDefinition, edges: Edge[], history: string[], current: string[]) => {
  const visited = new Set(history);
  const active = new Set(current);
  const reached = new Set([...history, ...current]);
  const result = new Set<string>();
  for (let i = 1; i < history.length; i++) result.add(`${history[i - 1]}::${history[i]}`);
  const gateways = definition.nodes.filter(node => node.type.toLowerCase().replace(/[-_]/g, '') === 'parallelgateway');
  // Gateways may be omitted from nodeHistory; infer a passed gateway only when
  // its incoming nodes have finished and a downstream node is already reached.
  let changed = true;
  while (changed) {
    changed = false;
    for (const gateway of gateways) {
      if (reached.has(gateway.id)) continue;
      const incoming = edges.filter(edge => edge.to === gateway.id);
      if (incoming.length && incoming.every(edge => reached.has(edge.from) && !active.has(edge.from)) &&
          edges.some(edge => edge.from === gateway.id && reached.has(edge.to))) {
        reached.add(gateway.id);
        changed = true;
      }
    }
  }
  for (const gateway of gateways) {
    if (!reached.has(gateway.id)) continue;
    for (const edge of edges) {
      if (edge.to === gateway.id && reached.has(edge.from) && !active.has(edge.from)) result.add(`${edge.from}::${edge.to}`);
      if (edge.from === gateway.id && !active.has(gateway.id) && (visited.has(edge.to) || active.has(edge.to))) result.add(`${edge.from}::${edge.to}`);
    }
  }
  return result;
};

export const isParallelRuntimeVariable = (name: string) => /^__pg_.+_arrivals$/.test(name);
