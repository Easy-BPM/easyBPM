import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import ProcessList from './pages/ProcessList';
import TaskList from './pages/TaskList';
import TaskDetail from './pages/TaskDetail';

const RoutesApp = () => (
  <Routes>
    <Route path="/" element={<Navigate to="/processes" replace />} />
    <Route path="/processes" element={<ProcessList />} />
    <Route path="/tasks" element={<TaskList />} />
    <Route path="/tasks/:id" element={<TaskDetail />} />
  </Routes>
);


export default RoutesApp;