import React, { useEffect, useState } from 'react';
import { getTasks } from '../api/bpmApi';
import { Link } from 'react-router-dom';

const TaskList: React.FC = () => {
  const [tasks, setTasks] = useState<any[]>([]);

  useEffect(() => {
    getTasks().then(res => setTasks(res.data.content || []));
  }, []);

  return (
    <div className="container mt-4">
      <h2>Tasks</h2>
      <ul className="list-group">
        {tasks.map(task => (
          <li key={task.id} className="list-group-item d-flex justify-content-between">
            <span>{task.name || 'Task #' + task.id}</span>
            <Link className="btn btn-sm btn-secondary" to={`/tasks/${task.id}`}>Open</Link>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default TaskList;