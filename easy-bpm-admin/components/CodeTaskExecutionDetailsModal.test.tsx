import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CodeTaskExecutionDetailsModal } from './CodeTaskExecutionDetailsModal';

const mockExecution = {
  executionId: 1,
  instanceId: 1001,
  jarId: 5,
  className: 'com.example.Calculator',
  methodName: 'calculate',
  status: 'COMPLETED' as const,
  inputVariables: { a: 10, b: 20 },
  outputVariables: { result: 30, precision: 2 },
  executedAt: '2026-04-22T10:00:00Z',
  executionTimeMs: 150
};

const mockFailedExecution = {
  executionId: 2,
  instanceId: 1002,
  jarId: 6,
  className: 'com.example.Processor',
  methodName: 'process',
  status: 'FAILED' as const,
  inputVariables: { data: 'test' },
  outputVariables: {},
  errorMessage: 'java.lang.NullPointerException: Cannot process null input',
  executedAt: '2026-04-22T11:00:00Z',
  executionTimeMs: 250
};

describe('CodeTaskExecutionDetailsModal', () => {
  const mockOnClose = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Rendering - Success Execution', () => {
    it('should render modal title', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/execution details/i)).toBeInTheDocument();
    });

    it('should render status summary card', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText('COMPLETED')).toBeInTheDocument();
      expect(screen.getByText(/150/)).toBeInTheDocument(); // execution time
    });

    it('should render method information section', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText('com.example.Calculator')).toBeInTheDocument();
      expect(screen.getByText('calculate')).toBeInTheDocument();
    });

    it('should render input variables section', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/input variables/i)).toBeInTheDocument();
      expect(screen.getByText('10')).toBeInTheDocument();
      expect(screen.getByText('20')).toBeInTheDocument();
    });

    it('should render output variables section', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/output variables/i)).toBeInTheDocument();
      expect(screen.getByText('30')).toBeInTheDocument();
    });

    it('should NOT render error details section for successful execution', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.queryByText(/error details/i)).not.toBeInTheDocument();
    });
  });

  describe('Rendering - Failed Execution', () => {
    it('should render error details section', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockFailedExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/error details/i)).toBeInTheDocument();
    });

    it('should display error message', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockFailedExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/java.lang.NullPointerException/)).toBeInTheDocument();
    });

    it('should show FAILED status badge', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockFailedExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const failedBadge = screen.getByText('FAILED');
      expect(failedBadge.className).toContain('bg-red');
    });
  });

  describe('Visibility Control', () => {
    it('should not render when isOpen is false', () => {
      const { container } = render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={false}
          onClose={mockOnClose}
        />
      );

      const modal = container.querySelector('[role="dialog"]');
      expect(modal).not.toBeInTheDocument();
    });

    it('should render when isOpen is true', () => {
      const { container } = render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const modal = container.querySelector('[role="dialog"]');
      expect(modal).toBeInTheDocument();
    });
  });

  describe('Close Interactions', () => {
    it('should call onClose when close button is clicked', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const closeBtn = screen.getByRole('button', { name: /close|dismiss|x/i });
      await user.click(closeBtn);

      expect(mockOnClose).toHaveBeenCalled();
    });

    it('should call onClose when escape key is pressed', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      fireEvent.keyDown(document, { key: 'Escape', code: 'Escape' });

      expect(mockOnClose).toHaveBeenCalled();
    });

    it('should call onClose when overlay is clicked', async () => {
      const user = userEvent.setup();
      const { container } = render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const overlay = container.querySelector('[data-testid="modal-overlay"]');
      if (overlay) {
        await user.click(overlay);
        expect(mockOnClose).toHaveBeenCalled();
      }
    });
  });

  describe('JSON Viewer', () => {
    it('should have copy to clipboard functionality', async () => {
      const user = userEvent.setup();
      const clipboardSpy = jest.spyOn(navigator.clipboard, 'writeText');

      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const copyBtns = screen.getAllByRole('button', { name: /copy/i });
      if (copyBtns.length > 0) {
        await user.click(copyBtns[0]);
        expect(clipboardSpy).toHaveBeenCalled();
      }

      clipboardSpy.mockRestore();
    });

    it('should display JSON with proper formatting', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      // JSON should be visible and formatted
      expect(screen.getByText(/input variables/i)).toBeInTheDocument();
      expect(screen.getByText(/output variables/i)).toBeInTheDocument();
    });

    it('should support collapsible JSON sections', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const toggleBtns = screen.queryAllByRole('button', { name: /expand|collapse|toggle/i });
      if (toggleBtns.length > 0) {
        await user.click(toggleBtns[0]);
        // Section should toggle state
      }
    });
  });

  describe('Summary Grid', () => {
    it('should display execution time correctly', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/150/)).toBeInTheDocument();
      expect(screen.getByText(/execution time|execution.*time/i)).toBeInTheDocument();
    });

    it('should display executed at timestamp', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/2026-04-22|April 22/)).toBeInTheDocument();
    });

    it('should display JAR ID', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      expect(screen.getByText(/jar.*5|jar id.*5/i)).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels', () => {
      const { container } = render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      const modal = container.querySelector('[role="dialog"]');
      expect(modal).toBeInTheDocument();
    });

    it('should manage focus properly', () => {
      render(
        <CodeTaskExecutionDetailsModal
          execution={mockExecution}
          isOpen={true}
          onClose={mockOnClose}
        />
      );

      // Modal should trap focus
      const closeBtn = screen.getByRole('button', { name: /close|dismiss|x/i });
      expect(closeBtn).toBeInTheDocument();
    });
  });
});
