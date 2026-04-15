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

export const getEdgePath = (source: BpmnNode, target: BpmnNode): string => {
  const sourceCenter = { x: source.position.x + source.width / 2, y: source.position.y + source.height / 2 };
  const targetCenter = { x: target.position.x + target.width / 2, y: target.position.y + target.height / 2 };

  const dx = targetCenter.x - sourceCenter.x;
  const dy = targetCenter.y - sourceCenter.y;

  let startDir: 'top' | 'right' | 'bottom' | 'left' = 'right';
  let endDir: 'top' | 'right' | 'bottom' | 'left' = 'left';

  // Determine best connection faces based on relative position
  if (Math.abs(dx) > Math.abs(dy)) {
    // Horizontal relationship dominant
    if (dx > 0) {
      startDir = 'right';
      endDir = 'left';
    } else {
      startDir = 'left';
      endDir = 'right';
    }
  } else {
    // Vertical relationship dominant
    if (dy > 0) {
      startDir = 'bottom';
      endDir = 'top';
    } else {
      startDir = 'top';
      endDir = 'bottom';
    }
  }

  const start = getHandlePosition(source, startDir);
  const end = getHandlePosition(target, endDir);

  // Manhattan Routing (Orthogonal lines)
  // Calculate midpoints to create 90-degree turns
  
  if (['left', 'right'].includes(startDir)) {
      const midX = (start.x + end.x) / 2;
      return `M ${start.x} ${start.y} L ${midX} ${start.y} L ${midX} ${end.y} L ${end.x} ${end.y}`;
  } else {
      const midY = (start.y + end.y) / 2;
      return `M ${start.x} ${start.y} L ${start.x} ${midY} L ${end.x} ${midY} L ${end.x} ${end.y}`;
  }
};

export const generateId = (prefix: string = 'node'): string => {
  return `${prefix}_${Math.random().toString(36).substr(2, 9)}`;
};
