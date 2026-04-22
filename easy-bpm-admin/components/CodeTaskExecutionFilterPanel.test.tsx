import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CodeTaskExecutionFilterPanel } from './CodeTaskExecutionFilterPanel';

describe('CodeTaskExecutionFilterPanel', () => {
  const mockOnFilterChange = jest.fn();
  const mockOnClearFilters = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render filter panel title', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      expect(screen.getByText(/filter|filters/i)).toBeInTheDocument();
    });

    it('should render status dropdown', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const statusDropdown = screen.getByRole('combobox', { name: /status/i });
      expect(statusDropdown).toBeInTheDocument();
    });

    it('should render instance ID input', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const instanceInput = screen.getByPlaceholderText(/instance id/i);
      expect(instanceInput).toBeInTheDocument();
    });

    it('should render JAR ID input', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const jarInput = screen.getByPlaceholderText(/jar id/i);
      expect(jarInput).toBeInTheDocument();
    });

    it('should render Class Name input', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const classInput = screen.getByPlaceholderText(/class name/i);
      expect(classInput).toBeInTheDocument();
    });

    it('should render Method Name input', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const methodInput = screen.getByPlaceholderText(/method name/i);
      expect(methodInput).toBeInTheDocument();
    });

    it('should render clear filters button', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const clearBtn = screen.getByRole('button', { name: /clear/i });
      expect(clearBtn).toBeInTheDocument();
    });
  });

  describe('Status Filter', () => {
    it('should change status filter on selection', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const statusDropdown = screen.getByRole('combobox', { name: /status/i });
      await user.click(statusDropdown);

      const completedOption = screen.getByRole('option', { name: /completed/i });
      await user.click(completedOption);

      expect(mockOnFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'COMPLETED' })
      );
    });

    it('should display default "All Statuses" option', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const statusDropdown = screen.getByRole('combobox', { name: /status/i });
      await user.click(statusDropdown);

      expect(screen.getByRole('option', { name: /all statuses/i })).toBeInTheDocument();
    });

    it('should include all status options', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const statusDropdown = screen.getByRole('combobox', { name: /status/i });
      await user.click(statusDropdown);

      expect(screen.getByRole('option', { name: /completed/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /failed/i })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: /timeout/i })).toBeInTheDocument();
    });
  });

  describe('Text Filters', () => {
    it('should apply instance ID filter on input change', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const instanceInput = screen.getByPlaceholderText(/instance id/i);
      await user.type(instanceInput, '1001');

      expect(mockOnFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({ instanceId: '1001' })
      );
    });

    it('should apply JAR ID filter on input change', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const jarInput = screen.getByPlaceholderText(/jar id/i);
      await user.type(jarInput, '5');

      expect(mockOnFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({ jarId: '5' })
      );
    });

    it('should apply class name filter on input change', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const classInput = screen.getByPlaceholderText(/class name/i);
      await user.type(classInput, 'com.example');

      expect(mockOnFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({ className: 'com.example' })
      );
    });

    it('should apply method name filter on input change', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const methodInput = screen.getByPlaceholderText(/method name/i);
      await user.type(methodInput, 'calculate');

      expect(mockOnFilterChange).toHaveBeenCalledWith(
        expect.objectContaining({ methodName: 'calculate' })
      );
    });
  });

  describe('Clear Filters', () => {
    it('should call onClearFilters when clear button is clicked', async () => {
      const user = userEvent.setup();
      render(
        <CodeTaskExecutionFilterPanel
          filters={{ status: 'COMPLETED', instanceId: '1001' }}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const clearBtn = screen.getByRole('button', { name: /clear/i });
      await user.click(clearBtn);

      expect(mockOnClearFilters).toHaveBeenCalled();
    });

    it('should clear all input values when clear filters is called', async () => {
      const user = userEvent.setup();
      const { rerender } = render(
        <CodeTaskExecutionFilterPanel
          filters={{ status: 'COMPLETED', instanceId: '1001' }}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      rerender(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const instanceInput = screen.getByPlaceholderText(/instance id/i) as HTMLInputElement;
      expect(instanceInput.value).toBe('');
    });
  });

  describe('Populated Filters', () => {
    it('should display filter values when provided', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{
            status: 'FAILED',
            instanceId: '1001',
            jarId: '5',
            className: 'com.example.Processor',
            methodName: 'process'
          }}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      const instanceInput = screen.getByPlaceholderText(/instance id/i) as HTMLInputElement;
      const jarInput = screen.getByPlaceholderText(/jar id/i) as HTMLInputElement;
      const classInput = screen.getByPlaceholderText(/class name/i) as HTMLInputElement;
      const methodInput = screen.getByPlaceholderText(/method name/i) as HTMLInputElement;

      expect(instanceInput.value).toBe('1001');
      expect(jarInput.value).toBe('5');
      expect(classInput.value).toBe('com.example.Processor');
      expect(methodInput.value).toBe('process');
    });
  });

  describe('Active Filter Indicator', () => {
    it('should show indicator when filters are active', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{ status: 'COMPLETED' }}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      expect(screen.getByText(/1 filter/i)).toBeInTheDocument();
    });

    it('should show count of active filters', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{
            status: 'FAILED',
            instanceId: '1001',
            jarId: '5'
          }}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      expect(screen.getByText(/3 filter/i)).toBeInTheDocument();
    });

    it('should not show indicator when no filters are active', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      expect(screen.queryByText(/filter/i)).not.toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have properly labeled inputs', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      expect(screen.getByLabelText(/status/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/instance id/i)).toBeInTheDocument();
    });

    it('should have descriptive placeholder text', () => {
      render(
        <CodeTaskExecutionFilterPanel
          filters={{}}
          onFilterChange={mockOnFilterChange}
          onClearFilters={mockOnClearFilters}
        />
      );

      expect(screen.getByPlaceholderText(/instance id/i)).toBeInTheDocument();
      expect(screen.getByPlaceholderText(/jar id/i)).toBeInTheDocument();
    });
  });
});
