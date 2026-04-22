import React from 'react';
import { Code2 } from 'lucide-react';

/**
 * CodeTaskNode
 * 
 * Visual representation of a Code Task node on the BPMN canvas
 * - Display code icon
 * - Show method name as label
 * - Highlight when selected
 */

interface CodeTaskNodeProps {
  id: string;
  x: number;
  y: number;
  width?: number;
  height?: number;
  label?: string;
  methodName?: string;
  className?: string;
  isSelected?: boolean;
  onClick?: () => void;
  onDoubleClick?: () => void;
}

export const CodeTaskNode: React.FC<CodeTaskNodeProps> = ({
  id,
  x,
  y,
  width = 120,
  height = 80,
  label = 'Code Task',
  methodName,
  className,
  isSelected = false,
  onClick,
  onDoubleClick
}) => {
  const displayLabel = methodName ? `${methodName}()` : label;

  return (
    <g
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      style={{ cursor: 'pointer' }}
    >
      {/* Background Rectangle */}
      <rect
        x={x}
        y={y}
        width={width}
        height={height}
        rx={4}
        fill="white"
        stroke={isSelected ? '#2563eb' : '#d1d5db'}
        strokeWidth={isSelected ? 3 : 2}
        className={isSelected ? 'shadow-lg' : ''}
      />

      {/* Icon Background */}
      <circle
        cx={x + 20}
        cy={y + height / 2}
        r={16}
        fill="#dbeafe"
        stroke="none"
      />

      {/* Code Icon (represented as text/SVG) */}
      <text
        x={x + 20}
        y={y + height / 2 + 6}
        textAnchor="middle"
        fontSize="20"
        fill="#2563eb"
        fontWeight="bold"
      >
        {'<>'}
      </text>

      {/* Label */}
      <text
        x={x + width / 2}
        y={y + height / 2 + 4}
        textAnchor="middle"
        fontSize="12"
        fill="#1f2937"
        fontWeight="500"
        className="pointer-events-none"
      >
        <tspan x={x + width / 2} dy="0">
          {displayLabel.length > 15 ? displayLabel.substring(0, 12) + '...' : displayLabel}
        </tspan>
      </text>

      {/* Class name if available */}
      {className && (
        <text
          x={x + width / 2}
          y={y + height / 2 + 18}
          textAnchor="middle"
          fontSize="10"
          fill="#6b7280"
          className="pointer-events-none"
        >
          {className.split('.').pop()?.substring(0, 12)}
        </text>
      )}
    </g>
  );
};

/**
 * CodeTaskPaletteItem
 * 
 * Draggable palette item for creating Code Task nodes
 */

interface CodeTaskPaletteItemProps {
  onDragStart?: (e: React.DragEvent) => void;
}

export const CodeTaskPaletteItem: React.FC<CodeTaskPaletteItemProps> = ({ onDragStart }) => {
  return (
    <div
      draggable
      onDragStart={onDragStart}
      className="flex items-center gap-2 px-4 py-3 bg-blue-50 border-2 border-dashed border-blue-300 rounded-lg cursor-move hover:bg-blue-100 hover:border-blue-400 transition"
    >
      <Code2 className="w-5 h-5 text-blue-600" />
      <div className="flex-1">
        <p className="font-semibold text-blue-900">Code Task</p>
        <p className="text-xs text-blue-700">Execute Java code</p>
      </div>
    </div>
  );
};

export default CodeTaskNode;
