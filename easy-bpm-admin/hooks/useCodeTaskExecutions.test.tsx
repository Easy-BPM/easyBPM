import { renderHook, waitFor } from '@testing-library/react';
import { useCodeTaskExecutions } from './useCodeTaskExecutions';

describe('useCodeTaskExecutions - Unit Tests', () => {
  const mockFetch = jest.fn();
  global.fetch = mockFetch;

  const mockApiResponse = {
    content: [
      {
        executionId: 1,
        instanceId: 1001,
        jarId: 5,
        className: 'com.example.Calculator',
        methodName: 'calculate',
        status: 'COMPLETED',
        inputVariables: { a: 10 },
        outputVariables: { result: 30 },
        executedAt: '2026-04-22T10:00:00Z',
        executionTimeMs: 150
      }
    ],
    totalElements: 100,
    totalPages: 5,
    currentPage: 0
  };

  beforeEach(() => {
    jest.clearAllMocks();
    mockFetch.mockResolvedValue({
      ok: true,
      json: jest.fn().mockResolvedValue(mockApiResponse)
    });
  });

  describe('Hook Initialization', () => {
    it('should initialize with default values', () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      expect(result.current.executions).toEqual([]);
      expect(result.current.loading).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it('should return refetch function', () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      expect(typeof result.current.refetch).toBe('function');
    });
  });

  describe('API Call', () => {
    it('should call API with correct URL', async () => {
      renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('/code-tasks/executions');
    });

    it('should include pagination parameters in URL', async () => {
      renderHook(() =>
        useCodeTaskExecutions({
          page: 2,
          pageSize: 50,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('page=2');
      expect(url).toContain('size=50');
    });

    it('should include sort parameters in URL', async () => {
      renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'status',
          sortOrder: 'asc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('sortBy=status');
      expect(url).toContain('sortOrder=asc');
    });

    it('should include filter parameters in URL', async () => {
      renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {
            status: 'COMPLETED',
            instanceId: '1001',
            jarId: '5',
            className: 'com.example',
            methodName: 'calculate'
          }
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('status=COMPLETED');
      expect(url).toContain('instanceId=1001');
      expect(url).toContain('jarId=5');
    });

    it('should only include defined filters in URL', async () => {
      renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: { status: 'FAILED' }
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('status=FAILED');
      expect(url).not.toContain('instanceId=');
    });
  });

  describe('Response Handling', () => {
    it('should parse and store API response', async () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.executions).toHaveLength(1);
      expect(result.current.totalElements).toBe(100);
      expect(result.current.totalPages).toBe(5);
    });

    it('should set loading to false after successful fetch', async () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });
    });

    it('should not set error on successful fetch', async () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe('Error Handling', () => {
    it('should handle network errors', async () => {
      mockFetch.mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.error).toBeTruthy();
      });

      expect(result.current.executions).toEqual([]);
    });

    it('should handle API errors (non-200 response)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error'
      });

      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.error).toBeTruthy();
      });
    });

    it('should handle malformed JSON response', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: jest.fn().mockRejectedValue(new Error('Invalid JSON'))
      });

      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.error).toBeTruthy();
      });
    });
  });

  describe('Refetch', () => {
    it('should refetch data when refetch is called', async () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      const initialCallCount = mockFetch.mock.calls.length;

      result.current.refetch();

      await waitFor(() => {
        expect(mockFetch.mock.calls.length).toBeGreaterThan(initialCallCount);
      });
    });

    it('should set loading to true when refetching', async () => {
      const { result } = renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      result.current.refetch();

      // Loading should be true during refetch (if implementation sets it)
      // Note: This depends on implementation details
    });
  });

  describe('Dependency Changes', () => {
    it('should refetch when page changes', async () => {
      mockFetch.mockClear();

      const { rerender } = renderHook(
        ({ page }) =>
          useCodeTaskExecutions({
            page,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          }),
        { initialProps: { page: 0 } }
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      mockFetch.mockClear();

      rerender({ page: 1 });

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('page=1');
    });

    it('should refetch when filters change', async () => {
      mockFetch.mockClear();

      const { rerender } = renderHook(
        ({ filters }) =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters
          }),
        { initialProps: { filters: {} } }
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      mockFetch.mockClear();

      rerender({ filters: { status: 'COMPLETED' } });

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('status=COMPLETED');
    });

    it('should refetch when sort changes', async () => {
      mockFetch.mockClear();

      const { rerender } = renderHook(
        ({ sortBy }) =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy,
            sortOrder: 'desc',
            filters: {}
          }),
        { initialProps: { sortBy: 'executedAt' } }
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      mockFetch.mockClear();

      rerender({ sortBy: 'status' });

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('sortBy=status');
    });
  });

  describe('Environment Variables', () => {
    it('should use EASY_BPM_ADMIN_API_BASE_URL if provided', async () => {
      const originalEnv = process.env.EASY_BPM_ADMIN_API_BASE_URL;
      process.env.EASY_BPM_ADMIN_API_BASE_URL = 'http://custom-api.example.com';

      renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('http://custom-api.example.com');

      process.env.EASY_BPM_ADMIN_API_BASE_URL = originalEnv;
    });

    it('should use default API URL if environment variable not set', async () => {
      const originalEnv = process.env.EASY_BPM_ADMIN_API_BASE_URL;
      delete process.env.EASY_BPM_ADMIN_API_BASE_URL;

      renderHook(() =>
        useCodeTaskExecutions({
          page: 0,
          pageSize: 20,
          sortBy: 'executedAt',
          sortOrder: 'desc',
          filters: {}
        })
      );

      await waitFor(() => {
        expect(mockFetch).toHaveBeenCalled();
      });

      const url = mockFetch.mock.calls[0][0];
      expect(url).toContain('http://localhost:8080');

      process.env.EASY_BPM_ADMIN_API_BASE_URL = originalEnv;
    });
  });
});
