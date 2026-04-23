import { useState, useEffect } from 'react';

interface UseCodeTaskExecutionsProps {
  page?: number;
  size?: number;
  status?: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  instanceId?: number;
  jarId?: number;
  className?: string;
  methodName?: string;
  sortBy?: 'status' | 'executedAt' | 'executionTime';
  sortOrder?: 'asc' | 'desc';
}

interface CodeTaskExecution {
  executionId: number;
  instanceId: number;
  jarId: number;
  className: string;
  methodName: string;
  status: 'COMPLETED' | 'FAILED' | 'TIMEOUT';
  inputVariables: Record<string, any>;
  outputVariables: Record<string, any>;
  errorMessage?: string;
  executedAt: string;
  executionTimeMs: number;
}

interface ExecutionResponse {
  content: CodeTaskExecution[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

const API_BASE_URL = process.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const useCodeTaskExecutions = (props: UseCodeTaskExecutionsProps) => {
  const {
    page = 0,
    size = 20,
    status,
    instanceId,
    jarId,
    className,
    methodName,
    sortBy = 'executedAt',
    sortOrder = 'desc'
  } = props;

  const [executions, setExecutions] = useState<CodeTaskExecution[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchExecutions = async () => {
    setLoading(true);
    setError(null);

    try {
      // Build query parameters
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('size', size.toString());
      params.append('sortBy', sortBy);
      params.append('sortOrder', sortOrder);

      if (status) params.append('status', status);
      if (instanceId) params.append('instanceId', instanceId.toString());
      if (jarId) params.append('jarId', jarId.toString());
      if (className) params.append('className', className);
      if (methodName) params.append('methodName', methodName);

      const response = await fetch(
        `${API_BASE_URL}/code-tasks/executions?${params.toString()}`
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data: ExecutionResponse = await response.json();

      setExecutions(data.content || []);
      setTotalElements(data.totalElements || 0);
      setTotalPages(data.totalPages || 0);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to fetch executions';
      setError(errorMessage);
      setExecutions([]);
      setTotalElements(0);
      setTotalPages(0);
    } finally {
      setLoading(false);
    }
  };

  // Fetch when dependencies change
  useEffect(() => {
    fetchExecutions();
  }, [page, size, status, instanceId, jarId, className, methodName, sortBy, sortOrder]);

  return {
    executions,
    totalElements,
    totalPages,
    loading,
    error,
    refetch: fetchExecutions
  };
};
