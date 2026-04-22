import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CodeTaskExecutionListPage } from './CodeTaskExecutionListPage';
import * as useCodeTaskExecutionsModule from '../hooks/useCodeTaskExecutions';

// Mock the hook
jest.mock('../hooks/useCodeTaskExecutions');
const mockUseCodeTaskExecutions = useCodeTaskExecutionsModule.useCodeTaskExecutions as jest.MockedFunction<typeof useCodeTaskExecutionsModule.useCodeTaskExecutions>;

const mockExecutions = [
  {
    executionId: 1,
    instanceId: 1001,
    jarId: 5,
    className: 'com.example.Calculator',
    methodName: 'calculate',
    status: 'COMPLETED' as const,
    inputVariables: { a: 10, b: 20 },
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
  }
];

describe('CodeTaskExecutionListPage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseCodeTaskExecutions.mockReturnValue({
      executions: mockExecutions,
      totalElements: 2,
      totalPages: 1,
      loading: false,
      error: null,
      refetch: jest.fn()
    });
  });

  describe('Rendering', () => {
    it('should render page title', () => {
      render(<CodeTaskExecutionListPage />);
      expect(screen.getByText('Code Task Executions')).toBeInTheDocument();
    });

    it('should render metrics dashboard', () => {
      render(<CodeTaskExecutionListPage />);
      expect(screen.getByText('Total Executions')).toBeInTheDocument();
      expect(screen.getByText('Success Rate')).toBeInTheDocument();
    });

    it('should render filter panel', () => {
      render(<CodeTaskExecutionListPage />);
      expect(screen.getByRole('combobox')).toBeInTheDocument(); // Status dropdown
    });

    it('should render execution table', () => {
      render(<CodeTaskExecutionListPage />);
      expect(screen.getByText('calculate')).toBeInTheDocument();
      expect(screen.getByText('process')).toBeInTheDocument();
    });

    it('should render refresh button', () => {
      render(<CodeTaskExecutionListPage />);
      const refreshBtn = screen.getByRole('button', { name: /refresh/i });
      expect(refreshBtn).toBeInTheDocument();
    });
  });

  describe('Pagination', () => {
    it('should render pagination controls', () => {
      render(<CodeTaskExecutionListPage />);
      expect(screen.getByRole('button', { name: /previous/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /next/i })).toBeInTheDocument();
    });

    it('should disable previous button on first page', () => {
      render(<CodeTaskExecutionListPage />);
      const prevBtn = screen.getByRole('button', { name: /previous/i });
      expect(prevBtn).toBeDisabled();
    });

    it('should disable next button on last page', () => {
      render(<CodeTaskExecutionListPage />);
      const nextBtn = screen.getByRole('button', { name: /next/i });
      expect(nextBtn).toBeDisabled();
    });

    it('should handle pagination navigation', async () => {
      const user = userEvent.setup();
      mockUseCodeTaskExecutions.mockReturnValue({
        executions: [mockExecutions[0]],
        totalElements: 40,
        totalPages: 2,
        loading: false,
        error: null,
        refetch: jest.fn()
      });

      render(<CodeTaskExecutionListPage />);
      const nextBtn = screen.getByRole('button', { name: /next/i });
      
      expect(nextBtn).not.toBeDisabled();
      await user.click(nextBtn);
      
      expect(mockUseCodeTaskExecutions).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 })
      );
    });
  });

  describe('Filtering', () => {
    it('should apply status filter', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const statusDropdown = screen.getByRole('combobox');
      await user.click(statusDropdown);
      
      const completedOption = screen.getByRole('option', { name: /completed/i });
      await user.click(completedOption);
      
      expect(mockUseCodeTaskExecutions).toHaveBeenCalledWith(
        expect.objectContaining({ filters: expect.objectContaining({ status: 'COMPLETED' }) })
      );
    });

    it('should apply instance ID filter', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const instanceInput = screen.getByPlaceholderText(/instance id/i);
      await user.type(instanceInput, '1001');
      
      expect(mockUseCodeTaskExecutions).toHaveBeenCalledWith(
        expect.objectContaining({ filters: expect.objectContaining({ instanceId: '1001' }) })
      );
    });

    it('should clear all filters', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const clearBtn = screen.getByRole('button', { name: /clear/i });
      await user.click(clearBtn);
      
      expect(mockUseCodeTaskExecutions).toHaveBeenCalledWith(
        expect.objectContaining({ filters: {} })
      );
    });
  });

  describe('Sorting', () => {
    it('should handle column sort clicks', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const statusHeader = screen.getByText('Status');
      await user.click(statusHeader);
      
      expect(mockUseCodeTaskExecutions).toHaveBeenCalledWith(
        expect.objectContaining({ sortBy: 'status', sortOrder: 'asc' })
      );
    });

    it('should toggle sort order on same column click', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const statusHeader = screen.getByText('Status');
      await user.click(statusHeader);
      await user.click(statusHeader);
      
      expect(mockUseCodeTaskExecutions).toHaveBeenLastCalledWith(
        expect.objectContaining({ sortBy: 'status', sortOrder: 'desc' })
      );
    });
  });

  describe('Modal Interaction', () => {
    it('should open details modal on row click', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const calculateRow = screen.getByText('calculate').closest('tr');
      if (calculateRow) {
        await user.click(calculateRow);
      }
      
      expect(screen.getByText(/execution details/i)).toBeInTheDocument();
    });

    it('should close modal on close button click', async () => {
      const user = userEvent.setup();
      render(<CodeTaskExecutionListPage />);
      
      const calculateRow = screen.getByText('calculate').closest('tr');
      if (calculateRow) {
        await user.click(calculateRow);
      }
      
      const closeBtn = screen.getByRole('button', { name: /close/i });
      await user.click(closeBtn);
      
      expect(screen.queryByText(/execution details/i)).not.toBeInTheDocument();
    });

    it('should close modal on escape key', async () => {
      render(<CodeTaskExecutionListPage />);
      
      const calculateRow = screen.getByText('calculate').closest('tr');
      if (calculateRow) {
        fireEvent.click(calculateRow);
      }
      
      fireEvent.keyDown(document, { key: 'Escape', code: 'Escape' });
      
      await waitFor(() => {
        expect(screen.queryByText(/execution details/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Refresh', () => {
    it('should call refetch on refresh button click', async () => {
      const user = userEvent.setup();
      const mockRefetch = jest.fn();
      mockUseCodeTaskExecutions.mockReturnValue({
        executions: mockExecutions,
        totalElements: 2,
        totalPages: 1,
        loading: false,
        error: null,
        refetch: mockRefetch
      });
      
      render(<CodeTaskExecutionListPage />);
      
      const refreshBtn = screen.getByRole('button', { name: /refresh/i });
      await user.click(refreshBtn);
      
      expect(mockRefetch).toHaveBeenCalled();
    });

    it('should show loading indicator during refresh', () => {
      mockUseCodeTaskExecutions.mockReturnValue({
        executions: mockExecutions,
        totalElements: 2,
        totalPages: 1,
        loading: true,
        error: null,
        refetch: jest.fn()
      });

      render(<CodeTaskExecutionListPage />);
      expect(screen.getByRole('button', { name: /loading/i })).toBeDisabled();
    });
  });

  describe('Loading & Error States', () => {
    it('should show loading spinner when loading', () => {
      mockUseCodeTableExecutions.mockReturnValue({
        executions: [],
        totalElements: 0,
        totalPages: 0,
        loading: true,
        error: null,
        refetch: jest.fn()
      });

      render(<CodeTaskExecutionListPage />);
      expect(screen.getByRole('status')).toBeInTheDocument();
    });

    it('should show error message on API error', () => {
      const errorMsg = 'Failed to fetch executions';
      mockUseCodeTaskExecutions.mockReturnValue({
        executions: [],
        totalElements: 0,
        totalPages: 0,
        loading: false,
        error: new Error(errorMsg),
        refetch: jest.fn()
      });

      render(<CodeTaskExecutionListPage />);
      expect(screen.getByText(new RegExp(errorMsg))).toBeInTheDocument();
    });

    it('should show empty state when no executions', () => {
      mockUseCodeTaskExecutions.mockReturnValue({
        executions: [],
        totalElements: 0,
        totalPages: 0,
        loading: false,
        error: null,
        refetch: jest.fn()
      });

      render(<CodeTaskExecutionListPage />);
      expect(screen.getByText(/no executions found/i)).toBeInTheDocument();
    });
  });

  describe('URL Query Params Sync', () => {
    it('should initialize state from URL query params', () => {
      window.history.pushState({}, 'Test', '?page=1&status=FAILED&instanceId=1001');
      
      render(<CodeTaskExecutionListPage />);
      
      expect(mockUseCodeTaskExecutions).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 1,
          filters: expect.objectContaining({ status: 'FAILED', instanceId: '1001' })
        })
      );
      
      window.history.pushState({}, 'Test', '/');
    });

    it('should update URL when filters change', async () => {
      const user = userEvent.setup();
      const pushStateSpy = jest.spyOn(window.history, 'pushState');
      
      render(<CodeTaskExecutionListPage />);
      
      const statusDropdown = screen.getByRole('combobox');
      await user.click(statusDropdown);
      
      expect(pushStateSpy).toHaveBeenCalled();
      pushStateSpy.mockRestore();
    });
  });
});
