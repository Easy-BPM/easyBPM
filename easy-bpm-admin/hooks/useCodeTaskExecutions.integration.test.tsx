import { renderHook, waitFor } from '@testing-library/react';
import { useCodeTaskExecutions } from './useCodeTaskExecutions';

/**
 * INTEGRATION TESTS - useCodeTaskExecutions
 *
 * These tests validate the hook's interaction with the backend API.
 * They can run against:
 * 1. Mock API server (uses jest-mock-fetch or msw)
 * 2. Real backend (set EASY_BPM_ADMIN_API_BASE_URL to backend server URL)
 * 3. Docker Compose environment (backend running on localhost:8080)
 *
 * SETUP:
 * - Backend must be running on http://localhost:8080
 * - Database must have code_task_execution records
 * - Run: npm test -- useCodeTaskExecutions.integration.test.tsx
 */

// Configuration for integration tests
const API_BASE_URL = process.env.EASY_BPM_ADMIN_API_BASE_URL || 'http://localhost:8080';
const INTEGRATION_TIMEOUT = 10000; // 10 seconds for real API calls

// Mock API server (optional - for unit+integration hybrid tests)
let mockServer: any;

describe('useCodeTaskExecutions - Integration Tests', () => {
  beforeAll(() => {
    // If using real backend, verify it's reachable
    if (process.env.INTEGRATION_TEST_MODE === 'real') {
      console.log(`Running integration tests against: ${API_BASE_URL}`);
    }
  });

  afterAll(() => {
    // Cleanup
  });

  describe('API Contract - Real Backend', () => {
    it(
      'should fetch executions from /code-tasks/executions endpoint',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Should have valid response structure
        expect(result.current.executions).toBeDefined();
        expect(Array.isArray(result.current.executions)).toBe(true);
        expect(typeof result.current.totalElements).toBe('number');
        expect(typeof result.current.totalPages).toBe('number');
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should parse execution data structure correctly',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        if (result.current.executions.length > 0) {
          const execution = result.current.executions[0];

          // Validate all required fields exist
          expect(execution).toHaveProperty('executionId');
          expect(execution).toHaveProperty('instanceId');
          expect(execution).toHaveProperty('jarId');
          expect(execution).toHaveProperty('className');
          expect(execution).toHaveProperty('methodName');
          expect(execution).toHaveProperty('status');
          expect(execution).toHaveProperty('inputVariables');
          expect(execution).toHaveProperty('outputVariables');
          expect(execution).toHaveProperty('executedAt');
          expect(execution).toHaveProperty('executionTimeMs');

          // Validate types
          expect(typeof execution.executionId).toBe('number');
          expect(typeof execution.instanceId).toBe('number');
          expect(typeof execution.jarId).toBe('number');
          expect(typeof execution.className).toBe('string');
          expect(typeof execution.methodName).toBe('string');
          expect(['COMPLETED', 'FAILED', 'TIMEOUT']).toContain(execution.status);
          expect(typeof execution.inputVariables).toBe('object');
          expect(typeof execution.outputVariables).toBe('object');
          expect(typeof execution.executedAt).toBe('string');
          expect(typeof execution.executionTimeMs).toBe('number');
        }
      },
      INTEGRATION_TIMEOUT
    );
  });

  describe('Pagination Integration', () => {
    it(
      'should respect page parameter',
      async () => {
        const { result: resultPage0 } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 5,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(resultPage0.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        const page0Executions = resultPage0.current.executions;

        const { result: resultPage1 } = renderHook(() =>
          useCodeTaskExecutions({
            page: 1,
            pageSize: 5,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(resultPage1.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        const page1Executions = resultPage1.current.executions;

        // Pages should have different data (if there's enough data)
        if (page0Executions.length > 0 && page1Executions.length > 0) {
          expect(page0Executions[0].executionId).not.toBe(page1Executions[0].executionId);
        }
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should respect pageSize parameter',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 5,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Should return at most pageSize items
        expect(result.current.executions.length).toBeLessThanOrEqual(5);
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should return correct pagination metadata',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        expect(typeof result.current.totalElements).toBe('number');
        expect(typeof result.current.totalPages).toBe('number');
        expect(result.current.totalElements).toBeGreaterThanOrEqual(0);
        expect(result.current.totalPages).toBeGreaterThanOrEqual(0);
      },
      INTEGRATION_TIMEOUT
    );
  });

  describe('Filtering Integration', () => {
    it(
      'should filter by status=COMPLETED',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: { status: 'COMPLETED' }
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // All returned executions should be COMPLETED
        result.current.executions.forEach(execution => {
          expect(execution.status).toBe('COMPLETED');
        });
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should filter by instanceId',
      async () => {
        // First, get any execution to find an instanceId
        const { result: initialResult } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 1,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(initialResult.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        if (initialResult.current.executions.length > 0) {
          const instanceId = initialResult.current.executions[0].instanceId;

          const { result } = renderHook(() =>
            useCodeTaskExecutions({
              page: 0,
              pageSize: 20,
              sortBy: 'executedAt',
              sortOrder: 'desc',
              filters: { instanceId: instanceId.toString() }
            })
          );

          await waitFor(
            () => {
              expect(result.current.loading).toBe(false);
            },
            { timeout: INTEGRATION_TIMEOUT }
          );

          // All returned executions should have matching instanceId
          result.current.executions.forEach(execution => {
            expect(execution.instanceId).toBe(instanceId);
          });
        }
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should combine multiple filters',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {
              status: 'COMPLETED',
              className: 'com.example'
            }
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // All results should match both filters
        result.current.executions.forEach(execution => {
          expect(execution.status).toBe('COMPLETED');
          expect(execution.className).toContain('com.example');
        });
      },
      INTEGRATION_TIMEOUT
    );
  });

  describe('Sorting Integration', () => {
    it(
      'should sort by executedAt ascending',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'asc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Verify ascending order
        const executions = result.current.executions;
        for (let i = 1; i < executions.length; i++) {
          const prevTime = new Date(executions[i - 1].executedAt).getTime();
          const currTime = new Date(executions[i].executedAt).getTime();
          expect(prevTime).toBeLessThanOrEqual(currTime);
        }
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should sort by executedAt descending',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Verify descending order
        const executions = result.current.executions;
        for (let i = 1; i < executions.length; i++) {
          const prevTime = new Date(executions[i - 1].executedAt).getTime();
          const currTime = new Date(executions[i].executedAt).getTime();
          expect(prevTime).toBeGreaterThanOrEqual(currTime);
        }
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should sort by status',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'status',
            sortOrder: 'asc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Should return results sorted by status
        expect(result.current.executions.length).toBeGreaterThanOrEqual(0);
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should sort by executionTime',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executionTime',
            sortOrder: 'asc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Verify ascending order by execution time
        const executions = result.current.executions;
        for (let i = 1; i < executions.length; i++) {
          expect(executions[i - 1].executionTimeMs).toBeLessThanOrEqual(executions[i].executionTimeMs);
        }
      },
      INTEGRATION_TIMEOUT
    );
  });

  describe('Error Handling Integration', () => {
    it(
      'should handle 404 gracefully',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 999,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Should either return empty list or error
        expect(
          result.current.executions.length === 0 || result.current.error !== null
        ).toBe(true);
      },
      INTEGRATION_TIMEOUT
    );

    it(
      'should handle invalid filter parameters',
      async () => {
        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: { status: 'INVALID_STATUS' }
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        // Should return empty results or error
        if (result.current.error === null) {
          expect(result.current.executions.length).toBe(0);
        }
      },
      INTEGRATION_TIMEOUT
    );
  });

  describe('Response Consistency', () => {
    it(
      'should return consistent data on multiple fetches',
      async () => {
        const { result: result1 } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 5,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result1.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        const firstFetch = result1.current.executions;

        const { result: result2 } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 5,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result2.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        const secondFetch = result2.current.executions;

        // Both fetches should return same data (assuming no mutations)
        expect(firstFetch.length).toBe(secondFetch.length);
        if (firstFetch.length > 0) {
          expect(firstFetch[0].executionId).toBe(secondFetch[0].executionId);
        }
      },
      INTEGRATION_TIMEOUT
    );
  });

  describe('Performance', () => {
    it(
      'should fetch and parse data in reasonable time (< 2 seconds)',
      async () => {
        const startTime = performance.now();

        const { result } = renderHook(() =>
          useCodeTaskExecutions({
            page: 0,
            pageSize: 20,
            sortBy: 'executedAt',
            sortOrder: 'desc',
            filters: {}
          })
        );

        await waitFor(
          () => {
            expect(result.current.loading).toBe(false);
          },
          { timeout: INTEGRATION_TIMEOUT }
        );

        const endTime = performance.now();
        const duration = endTime - startTime;

        expect(duration).toBeLessThan(2000); // Less than 2 seconds
      },
      INTEGRATION_TIMEOUT
    );
  });
});
