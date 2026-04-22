import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CodeTaskExecutionTable } from './CodeTaskExecutionTable';

const mockExecutions = [
  {
    executionId: 1,
    instanceId: 1001,
    jarId: 5,
    className: 'com.example.Calculator',
    methodName: 'calculate',
    status: 'COMPLETED' as const,
    inputVariables: { a: 10 },
    outputVariables: { result: 30 },
    executedAt: '2026-04-22T10:00:00Z',
    executionTimeMs: 150
  },
  {
    executionId: 2,
    instanceId: 1002,
    jarId: 6,
    className: 'com.example.Processor',
    methodName: 'process',
    status: 'FAILED' as const,
    inputVariables: { data: 'test' },
    outputVariables: {},
    errorMessage: 'NullPointerException',
    executedAt: '2026-04-22T11:00:00Z',
    executionTimeMs: 250
  },
  {
    executionId: 3,
    instanceId: 1003,
    jarId: 7,
    className: 'com.example.Handler',
    methodName: 'handle',
    status: 'TIMEOUT' as const,
    inputVariables: {},
    outputVariables: {},
    errorMessage: 'Execution timeout after 5000ms',
    executedAt: '2026-04-22T12:00:00Z',
    executionTimeMs: 5000
  }
];

describe('CodeTaskExecutionTable', () => {
  const mockOnRowClick = jest.fn();
  const mockOnSort = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render table headers', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      expect(screen.getByText('Execution ID')).toBeInTheDocument();
      expect(screen.getByText('Instance ID')).toBeInTheDocument();
      expect(screen.getByText('Method')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
      expect(screen.getByText('Executed At')).toBeInTheDocument();
      expect(screen.getByText('Time (ms)')).toBeInTheDocument();
    });

    it('should render all execution rows', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      expect(screen.getByText('calculate')).toBeInTheDocument();
      expect(screen.getByText('process')).toBeInTheDocument();
      expect(screen.getByText('handle')).toBeInTheDocument();
    });

    it('should render execution IDs', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      expect(screen.getByText('1')).toBeInTheDocument();
      expect(screen.getByText('2')).toBeInTheDocument();
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('should render instance IDs with blue badge styling', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const instanceIds = screen.getAllByText(/^100[123]$/);
      expect(instanceIds).toHaveLength(3);
      instanceIds.forEach(id => {
        expect(id.className).toContain('bg-blue');
      });
    });

    it('should render status badges with correct colors', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const completedBadge = screen.getByText('COMPLETED');
      const failedBadge = screen.getByText('FAILED');
      const timeoutBadge = screen.getByText('TIMEOUT');

      expect(completedBadge.className).toContain('bg-green');
      expect(failedBadge.className).toContain('bg-red');
      expect(timeoutBadge.className).toContain('bg-orange');
    });

    it('should render formatted timestamps', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      expect(screen.getByText(/10:00|10:00:00/)).toBeInTheDocument();
      expect(screen.getByText(/11:00|11:00:00/)).toBeInTheDocument();
    });

    it('should render execution times in milliseconds', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      expect(screen.getByText('150')).toBeInTheDocument();
      expect(screen.getByText('250')).toBeInTheDocument();
      expect(screen.getByText('5000')).toBeInTheDocument();
    });
  });

  describe('Empty State', () => {
    it('should render empty state message', () => {
      render(
        <CodeTaskExecutionTable
          executions={[]}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      expect(screen.getByText(/no executions found|empty/i)).toBeInTheDocument();
    });
  });

  describe('Row Interactions', () => {
    it('should call onRowClick when row is clicked', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const calculateRow = screen.getByText('calculate').closest('tr');
      if (calculateRow) {
        await user.click(calculateRow);
      }

      expect(mockOnRowClick).toHaveBeenCalledWith(mockExecutions[0]);
    });

    it('should highlight row on hover', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const row = screen.getByText('calculate').closest('tr');
      if (row) {
        await user.hover(row);
        expect(row.className).toContain('hover');
      }
    });
  });

  describe('Sorting', () => {
    it('should call onSort with column name on header click', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const statusHeader = screen.getByText('Status');
      await user.click(statusHeader);

      expect(mockOnSort).toHaveBeenCalledWith('status');
    });

    it('should display sort indicator on active column', () => {
      render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="status"
          sortOrder="asc"
        />
      );

      const statusHeader = screen.getByText('Status');
      expect(statusHeader.parentElement?.querySelector('svg')).toBeInTheDocument();
    });

    it('should show correct sort direction icon', () => {
      const { rerender } = render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="asc"
        />
      );

      let header = screen.getByText('Executed At');
      let icon = header.parentElement?.querySelector('svg');
      expect(icon?.className).toContain('up');

      rerender(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      header = screen.getByText('Executed At');
      icon = header.parentElement?.querySelector('svg');
      expect(icon?.className).toContain('down');
    });
  });

  describe('Accessibility', () => {
    it('should have accessible table structure', () => {
      const { container } = render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const table = container.querySelector('table');
      expect(table).toBeInTheDocument();

      const thead = container.querySelector('thead');
      expect(thead).toBeInTheDocument();

      const tbody = container.querySelector('tbody');
      expect(tbody).toBeInTheDocument();
    });

    it('should have sortable headers marked as buttons', () => {
      const { container } = render(
        <CodeTaskExecutionTable
          executions={mockExecutions}
          onRowClick={mockOnRowClick}
          onSort={mockOnSort}
          sortBy="executedAt"
          sortOrder="desc"
        />
      );

      const buttons = container.querySelectorAll('button[role="button"]');
      expect(buttons.length).toBeGreaterThan(0);
    });
  });
});
