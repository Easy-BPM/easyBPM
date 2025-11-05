import React from 'react';
import { useDrag } from 'react-dnd';

const nodeTypes = [
  { type: 'StartEvent', name: 'Start' },
  { type: 'UserTask', name: 'User Task' },
  { type: 'ServiceTask', name: 'Service Task' },
  { type: 'EndEvent', name: 'End' }
];

export default function Sidebar() {
  return (
    <div style={{ width: '200px', borderRight: '1px solid #ccc', padding: '10px' }}>
      <h3>Components</h3>
      {nodeTypes.map((node) => (
        <DraggableNode key={node.type} node={node} />
      ))}
    </div>
  );
}

function DraggableNode({ node }) {
  const [{ isDragging }, drag] = useDrag(() => ({
    type: 'node',
    item: { ...node, id: `${node.type}_${Date.now()}_${Math.random()}` }, // ID único já aqui
    collect: (monitor) => ({ isDragging: !!monitor.isDragging() })
  }));

  return (
    <div
      ref={drag}
      style={{
        opacity: isDragging ? 0.5 : 1,
        margin: '5px 0',
        padding: '5px',
        background: '#ddd',
        cursor: 'grab'
      }}
    >
      {node.name}
    </div>
  );
}
