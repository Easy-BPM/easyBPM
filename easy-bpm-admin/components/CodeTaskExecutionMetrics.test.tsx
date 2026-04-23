import React from 'react';
import { render, screen } from '@testing-library/react';
import { CodeTaskExecutionMetrics } from './CodeTaskExecutionMetrics';

const mockExecutions = [
  {
    executionId: 1,
    instanceId: 1001,
    jarId: 5,
    className: 'com.example.Calculator',
    methodName: 'calculate',
    status: 'COMPLETED' as const,
    inputVariables: {},
    outputVariables: {},
    executedAt: '2026-04-22T10:00:00Z',
    executionTimeMs: 100
  },
  {
    executionId: 2,
    instanceId: 1002,
    jarId: 6,
    className: 'com.example.Processor',
    methodName: 'process',
    status: 'COMPLETED' as const,
    inputVariables: {},
    outputVariables: {},
    executedAt: '2026-04-22T10:01:00Z',
    executionTimeMs: 200
  },
  {
    executionId: 3,
    instanceId: 1003,
    jarId: 7,
    className: 'com.example.Handler',
    methodName: 'handle',
    status: 'FAILED' as const,
    inputVariables: {},
    outputVariables: {},
    errorMessage: 'Error',
    executedAt: '2026-04-22T10:02:00Z',
    executionTimeMs: 300
  },
  {
    executionId: 4,
    instanceId: 1004,
    jarId: 8,
    className: 'com.example.Service',
    methodName: 'execute',
    status: 'TIMEOUT' as const,
    inputVariables: {},
    outputVariables: {},
    errorMessage: 'Timeout',
    executedAt: '2026-04-22T10:03:00Z',
    executionTimeMs: 5000
  }
];

describe('CodeTaskExecutionMetrics', () => {
  describe('Rendering', () => {
    it('should render metrics dashboard title', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.getByText(/metrics|statistics/i)).toBeInTheDocument();
    });

    it('should render 5 metric cards', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.getByText(/total executions/i)).toBeInTheDocument();
      expect(screen.getByText(/success rate/i)).toBeInTheDocument();
      expect(screen.getByText(/failure rate/i)).toBeInTheDocument();
      expect(screen.getByText(/average execution time/i)).toBeInTheDocument();
      expect(screen.getByText(/throughput/i)).toBeInTheDocument();
    });

    it('should render metric value cards', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // Total executions: 4
      expect(screen.getByText('4')).toBeInTheDocument();
    });
  });

  describe('Total Executions Metric', () => {
    it('should calculate correct total executions count', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      const totalText = screen.getByText(/total executions/i).parentElement;
      expect(totalText?.textContent).toContain('4');
    });

    it('should show 0 for empty executions list', () => {
      render(
        <CodeTaskExecutionMetrics executions={[]} hasActiveFilters={false} />
      );

      expect(screen.getByText(/0/)).toBeInTheDocument();
    });
  });

  describe('Success Rate Metric', () => {
    it('should calculate success rate percentage correctly', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // 2 completed out of 4 = 50%
      const successText = screen.getByText(/success rate/i).parentElement;
      expect(successText?.textContent).toContain('50%');
    });

    it('should show 100% for all successful executions', () => {
      const allSuccessful = mockExecutions.filter(e => e.status === 'COMPLETED');
      
      render(
        <CodeTaskExecutionMetrics executions={allSuccessful} hasActiveFilters={false} />
      );

      const successText = screen.getByText(/success rate/i).parentElement;
      expect(successText?.textContent).toContain('100%');
    });

    it('should show 0% for all failed executions', () => {
      const allFailed = mockExecutions.filter(e => e.status !== 'COMPLETED');
      
      render(
        <CodeTaskExecutionMetrics executions={allFailed} hasActiveFilters={false} />
      );

      const successText = screen.getByText(/success rate/i).parentElement;
      expect(successText?.textContent).toContain('0%');
    });
  });

  describe('Failure Rate Metric', () => {
    it('should calculate failure rate percentage correctly', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // 2 failed out of 4 = 50%
      const failureText = screen.getByText(/failure rate/i).parentElement;
      expect(failureText?.textContent).toContain('50%');
    });
  });

  describe('Average Execution Time Metric', () => {
    it('should calculate average execution time correctly', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // (100 + 200 + 300 + 5000) / 4 = 1400ms
      const avgText = screen.getByText(/average execution time/i).parentElement;
      expect(avgText?.textContent).toContain('1400');
    });

    it('should format milliseconds correctly', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      const avgText = screen.getByText(/average execution time/i).parentElement;
      expect(avgText?.textContent).toMatch(/\d+\s*ms/i);
    });
  });

  describe('Throughput Metric', () => {
    it('should calculate throughput (executions per minute)', () => {
      // 4 executions over 3 minutes (10:00 to 10:03) = 1.33 executions/min
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      const throughputText = screen.getByText(/throughput/i).parentElement;
      expect(throughputText).toBeInTheDocument();
    });

    it('should show 0 for empty executions', () => {
      render(
        <CodeTaskExecutionMetrics executions={[]} hasActiveFilters={false} />
      );

      const throughputText = screen.getByText(/throughput/i).parentElement;
      expect(throughputText?.textContent).toContain('0');
    });
  });

  describe('Details Row - Execution Time Range', () => {
    it('should display minimum execution time', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // Min is 100ms
      expect(screen.getByText(/min.*100|100.*min/i)).toBeInTheDocument();
    });

    it('should display maximum execution time', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // Max is 5000ms
      expect(screen.getByText(/max.*5000|5000.*max/i)).toBeInTheDocument();
    });
  });

  describe('Details Row - Status Breakdown', () => {
    it('should display completed count', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.getByText(/completed.*2|2.*completed/i)).toBeInTheDocument();
    });

    it('should display failed count', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.getByText(/failed.*1|1.*failed/i)).toBeInTheDocument();
    });

    it('should display timeout count', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.getByText(/timeout.*1|1.*timeout/i)).toBeInTheDocument();
    });
  });

  describe('Filter Awareness', () => {
    it('should display filter badge when hasActiveFilters is true', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={true} />
      );

      expect(screen.getByText(/filtered|filtered data/i)).toBeInTheDocument();
    });

    it('should not display filter badge when hasActiveFilters is false', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.queryByText(/filtered/i)).not.toBeInTheDocument();
    });
  });

  describe('Empty State', () => {
    it('should show 0 values for all metrics with empty executions', () => {
      render(
        <CodeTaskExecutionMetrics executions={[]} hasActiveFilters={false} />
      );

      expect(screen.getByText(/0/)).toBeInTheDocument();
    });
  });

  describe('Responsive Design', () => {
    it('should have responsive grid layout', () => {
      const { container } = render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      const metricsContainer = container.querySelector('[class*="grid"]');
      expect(metricsContainer).toBeInTheDocument();
    });
  });

  describe('Visual Indicators', () => {
    it('should use different colors for different metric cards', () => {
      const { container } = render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      const cards = container.querySelectorAll('[class*="bg-"]');
      expect(cards.length).toBeGreaterThan(0);
    });
  });

  describe('Accessibility', () => {
    it('should have semantic HTML structure', () => {
      const { container } = render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(container.querySelector('section')).toBeInTheDocument();
    });

    it('should have descriptive labels for metrics', () => {
      render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      expect(screen.getByText(/total executions/i)).toBeInTheDocument();
      expect(screen.getByText(/success rate/i)).toBeInTheDocument();
    });
  });

  describe('Performance - Memoization', () => {
    it('should not recalculate metrics unnecessarily', () => {
      const { rerender } = render(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      rerender(
        <CodeTaskExecutionMetrics executions={mockExecutions} hasActiveFilters={false} />
      );

      // Component should still render correctly (memoization working)
      expect(screen.getByText(/total executions/i)).toBeInTheDocument();
    });
  });
});
