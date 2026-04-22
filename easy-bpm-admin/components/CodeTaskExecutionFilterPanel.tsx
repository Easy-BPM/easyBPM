import React, { useState, useEffect } from 'react';
import { X, Search } from 'lucide-react';

interface FilterState {
  status?: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  instanceId?: number;
  jarId?: number;
  className?: string;
  methodName?: string;
}

interface Props {
  filters: FilterState;
  onFilterChange: (filters: FilterState) => void;
  onClearFilters: () => void;
}

export const CodeTaskExecutionFilterPanel: React.FC<Props> = ({
  filters,
  onFilterChange,
  onClearFilters
}) => {
  const [localFilters, setLocalFilters] = useState<FilterState>(filters);
  const [instanceIdInput, setInstanceIdInput] = useState(filters.instanceId?.toString() || '');
  const [jarIdInput, setJarIdInput] = useState(filters.jarId?.toString() || '');

  // Update local filters when props change
  useEffect(() => {
    setLocalFilters(filters);
    setInstanceIdInput(filters.instanceId?.toString() || '');
    setJarIdInput(filters.jarId?.toString() || '');
  }, [filters]);

  const handleStatusChange = (status: 'COMPLETED' | 'FAILED' | 'TIMEOUT' | '') => {
    const newFilters = { ...localFilters };
    if (status === '') {
      delete newFilters.status;
    } else {
      newFilters.status = status as 'COMPLETED' | 'FAILED' | 'TIMEOUT';
    }
    setLocalFilters(newFilters);
    onFilterChange(newFilters);
  };

  const handleInstanceIdChange = (value: string) => {
    setInstanceIdInput(value);
    const newFilters = { ...localFilters };
    if (value === '') {
      delete newFilters.instanceId;
    } else {
      newFilters.instanceId = parseInt(value);
    }
    setLocalFilters(newFilters);
    onFilterChange(newFilters);
  };

  const handleJarIdChange = (value: string) => {
    setJarIdInput(value);
    const newFilters = { ...localFilters };
    if (value === '') {
      delete newFilters.jarId;
    } else {
      newFilters.jarId = parseInt(value);
    }
    setLocalFilters(newFilters);
    onFilterChange(newFilters);
  };

  const handleClassNameChange = (value: string) => {
    const newFilters = { ...localFilters };
    if (value === '') {
      delete newFilters.className;
    } else {
      newFilters.className = value;
    }
    setLocalFilters(newFilters);
    onFilterChange(newFilters);
  };

  const handleMethodNameChange = (value: string) => {
    const newFilters = { ...localFilters };
    if (value === '') {
      delete newFilters.methodName;
    } else {
      newFilters.methodName = value;
    }
    setLocalFilters(newFilters);
    onFilterChange(newFilters);
  };

  const hasActiveFilters = Object.values(localFilters).some(v => v !== undefined);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        {/* Status Filter */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">Status</label>
          <select
            value={localFilters.status || ''}
            onChange={(e) => handleStatusChange(e.target.value as any)}
            className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="">All Statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="FAILED">Failed</option>
            <option value="TIMEOUT">Timeout</option>
          </select>
        </div>

        {/* Instance ID Filter */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">Instance ID</label>
          <input
            type="number"
            value={instanceIdInput}
            onChange={(e) => handleInstanceIdChange(e.target.value)}
            placeholder="Enter instance ID"
            className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>

        {/* JAR ID Filter */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">JAR ID</label>
          <input
            type="number"
            value={jarIdInput}
            onChange={(e) => handleJarIdChange(e.target.value)}
            placeholder="Enter JAR ID"
            className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>

        {/* Class Name Filter */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">Class Name</label>
          <input
            type="text"
            value={localFilters.className || ''}
            onChange={(e) => handleClassNameChange(e.target.value)}
            placeholder="e.g., com.example"
            className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>

        {/* Method Name Filter */}
        <div>
          <label className="block text-sm font-medium text-slate-700 mb-2">Method Name</label>
          <input
            type="text"
            value={localFilters.methodName || ''}
            onChange={(e) => handleMethodNameChange(e.target.value)}
            placeholder="e.g., execute"
            className="w-full px-3 py-2 border border-slate-300 rounded-lg bg-white text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </div>
      </div>

      {/* Filter Actions */}
      {hasActiveFilters && (
        <div className="flex justify-end">
          <button
            onClick={onClearFilters}
            className="flex items-center gap-2 px-4 py-2 text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors text-sm font-medium"
          >
            <X size={16} />
            Clear All Filters
          </button>
        </div>
      )}
    </div>
  );
};
