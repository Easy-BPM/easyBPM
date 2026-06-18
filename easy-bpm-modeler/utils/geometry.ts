import { Position, BpmnNode } from '../types';

export const snapToGrid = (value: number, gridSize: number = 10): number => {
  return Math.round(value / gridSize) * gridSize;
};

const getHandlePosition = (node: BpmnNode, direction: 'top' | 'right' | 'bottom' | 'left'): Position => {
  const halfW = node.width / 2;
  const halfH = node.height / 2;
  const center = { x: node.position.x + halfW, y: node.position.y + halfH };

  switch (direction) {
    case 'top': return { x: center.x, y: node.position.y };
    case 'right': return { x: node.position.x + node.width, y: center.y };
    case 'bottom': return { x: center.x, y: node.position.y + node.height };
    case 'left': return { x: node.position.x, y: center.y };
  }
};

type HandleDirection = 'top' | 'right' | 'bottom' | 'left';

const getBestHandleDirection = (from: Position, to: Position): HandleDirection => {
  const dx = to.x - from.x;
  const dy = to.y - from.y;

  if (Math.abs(dx) > Math.abs(dy)) {
    return dx > 0 ? 'right' : 'left';
  }

  return dy > 0 ? 'bottom' : 'top';
};

const getOppositeHandleDirection = (direction: HandleDirection): HandleDirection => {
  switch (direction) {
    case 'top': return 'bottom';
    case 'right': return 'left';
    case 'bottom': return 'top';
    case 'left': return 'right';
  }
};

export const pointsToPath = (points: Position[]): string => {
  if (points.length === 0) return '';
  const [start, ...rest] = points;
  return `M ${start.x} ${start.y}${rest.map(point => ` L ${point.x} ${point.y}`).join('')}`;
};

export const getEdgeRoutePoints = (source: BpmnNode, target: BpmnNode, waypoints: Position[] = []): Position[] => {
  const sourceCenter = { x: source.position.x + source.width / 2, y: source.position.y + source.height / 2 };
  const targetCenter = { x: target.position.x + target.width / 2, y: target.position.y + target.height / 2 };

  const firstTarget = waypoints[0] || targetCenter;
  const lastSource = waypoints[waypoints.length - 1] || sourceCenter;
  const startDir = getBestHandleDirection(sourceCenter, firstTarget);
  const endDir = getOppositeHandleDirection(getBestHandleDirection(lastSource, targetCenter));

  const start = getHandlePosition(source, startDir);
  const end = getHandlePosition(target, endDir);

  if (waypoints.length > 0) {
    return [start, ...waypoints, end];
  }

  // Manhattan Routing (Orthogonal lines)
  // Calculate midpoints to create 90-degree turns
  
  if (['left', 'right'].includes(startDir)) {
      const midX = (start.x + end.x) / 2;
      return [start, { x: midX, y: start.y }, { x: midX, y: end.y }, end];
  }

  const midY = (start.y + end.y) / 2;
  return [start, { x: start.x, y: midY }, { x: end.x, y: midY }, end];
};

export const getEdgePath = (source: BpmnNode, target: BpmnNode, waypoints: Position[] = []): string => {
  return pointsToPath(getEdgeRoutePoints(source, target, waypoints));
};

export const generateId = (prefix: string = 'node'): string => {
  return `${prefix}_${Math.random().toString(36).substr(2, 9)}`;
};
