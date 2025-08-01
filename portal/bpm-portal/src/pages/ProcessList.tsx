import React, { useEffect, useState } from 'react';
import { getProcesses, startProcess } from '../api/bpmApi';

const ProcessList: React.FC = () => {
  const [processes, setProcesses] = useState<any[]>([]);

  useEffect(() => {
    getProcesses().then(res => setProcesses(res.data.content || []));
  }, []);

  const handleStart = (id: number) => {
    startProcess(id).then(() => alert('Process started'));
  };

  return (
    <div className="container mt-4">
      <h2>Available Processes</h2>
      <ul className="list-group">
        {processes.map(proc => (
          <li key={proc.id} className="list-group-item d-flex justify-content-between">
            {proc.name}
            <button className="btn btn-sm btn-primary" onClick={() => handleStart(proc.id)}>
              Start
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default ProcessList;