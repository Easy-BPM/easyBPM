import React from 'react';
import { useDrag } from 'react-dnd';

const nodeStyles = {
  StartEvent: {
    width: 50,
    height: 50,
    borderRadius: '50%',
    border: '2px solid black',
    background: '#00b050',
    color: 'white'
  },
  EndEvent: {
    width: 50,
    height: 50,
    borderRadius: '50%',
    border: '2px solid black',
    background: '#c00000',
    color: 'white'
  },
  UserTask: {
    width: 120,
    height: 60,
    borderRadius: 8,
    background: '#1E90FF',
    color: 'white',
    boxShadow: '1px 1px 3px rgba(0,0,0,0.2)'
  },
  ServiceTask: {
    width: 120,
    height: 60,
    borderRadius: 8,
    background: '#FFA500',
    color: 'white',
    boxShadow: '1px 1px 3px rgba(0,0,0,0.2)'
  }
};

export default function Node({ node, selected, onClick }) {
  const [{ isDragging }, drag] = useDrag(() => ({
    type: 'canvasNode',
    item: { id: node.id, type: 'canvasNode' },
    collect: (monitor) => ({ isDragging: !!monitor.isDragging() })
  }));

  const style = nodeStyles[node.type] || {
    width: 100,
    height: 50,
    background: '#ccc',
    borderRadius: 5,
    color: 'black'
  };

  return (
    <div
      ref={drag}
      onClick={onClick}
      style={{
        position: 'absolute',
        top: node.y,
        left: node.x,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontWeight: 'bold',
        cursor: 'move',
        opacity: isDragging ? 0.5 : 1,
        border: selected ? '3px solid #FF0000' : style.border,
        ...style
      }}
    >
      {node.name || node.type}
    </div>
  );
}
