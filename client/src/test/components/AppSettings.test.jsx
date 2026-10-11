import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AppSettings from '../../components/AppSettings';

describe('AppSettings', () => {
  const defaultProps = {
    showStopped: false,
    showAll: false,
    onToggleShowStopped: vi.fn(),
    onToggleShowAll: vi.fn(),
    onClose: vi.fn(),
    dockerStatus: 'UP',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render Settings title', () => {
    render(<AppSettings {...defaultProps} />);
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });

  describe('Docker Status', () => {
    it('should show Connected when status is UP', () => {
      render(<AppSettings {...defaultProps} dockerStatus="UP" />);
      expect(screen.getByText('Docker: Connected')).toBeInTheDocument();
    });

    it('should show Unavailable when status is DOWN', () => {
      render(<AppSettings {...defaultProps} dockerStatus="DOWN" />);
      expect(screen.getByText('Docker: Unavailable')).toBeInTheDocument();
    });

    it('should show Checking... when status is CHECKING', () => {
      render(<AppSettings {...defaultProps} dockerStatus="CHECKING" />);
      expect(screen.getByText('Docker: Checking...')).toBeInTheDocument();
    });

    it('should show Unknown for unknown status', () => {
      render(<AppSettings {...defaultProps} dockerStatus="UNKNOWN" />);
      expect(screen.getByText('Docker: Unknown')).toBeInTheDocument();
    });

    it('should show status indicator with correct class for UP', () => {
      render(<AppSettings {...defaultProps} dockerStatus="UP" />);
      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-running');
    });

    it('should show status indicator with correct class for DOWN', () => {
      render(<AppSettings {...defaultProps} dockerStatus="DOWN" />);
      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-stopped');
    });
  });

  describe('Show Stopped Containers Toggle', () => {
    it('should render toggle', () => {
      render(<AppSettings {...defaultProps} />);
      expect(screen.getByText('Show stopped containers')).toBeInTheDocument();
    });

    it('should be unchecked when showStopped is false', () => {
      render(<AppSettings {...defaultProps} showStopped={false} />);
      const checkboxes = screen.getAllByRole('checkbox');
      const stoppedCheckbox = checkboxes[0];
      expect(stoppedCheckbox).not.toBeChecked();
    });

    it('should be checked when showStopped is true', () => {
      render(<AppSettings {...defaultProps} showStopped={true} />);
      const checkboxes = screen.getAllByRole('checkbox');
      const stoppedCheckbox = checkboxes[0];
      expect(stoppedCheckbox).toBeChecked();
    });

    it('should call onToggleShowStopped when clicked', () => {
      const onToggleShowStopped = vi.fn();
      render(
        <AppSettings {...defaultProps} onToggleShowStopped={onToggleShowStopped} />
      );

      const checkboxes = screen.getAllByRole('checkbox');
      fireEvent.click(checkboxes[0]);

      expect(onToggleShowStopped).toHaveBeenCalledWith(true);
    });
  });

  describe('Show Containers Without Ports Toggle', () => {
    it('should render toggle', () => {
      render(<AppSettings {...defaultProps} />);
      expect(screen.getByText('Show containers without endpoints')).toBeInTheDocument();
    });

    it('should be unchecked when showAll is false', () => {
      render(<AppSettings {...defaultProps} showAll={false} />);
      const checkboxes = screen.getAllByRole('checkbox');
      const showAllCheckbox = checkboxes[1];
      expect(showAllCheckbox).not.toBeChecked();
    });

    it('should be checked when showAll is true', () => {
      render(<AppSettings {...defaultProps} showAll={true} />);
      const checkboxes = screen.getAllByRole('checkbox');
      const showAllCheckbox = checkboxes[1];
      expect(showAllCheckbox).toBeChecked();
    });

    it('should call onToggleShowAll when clicked', () => {
      const onToggleShowAll = vi.fn();
      render(
        <AppSettings {...defaultProps} onToggleShowAll={onToggleShowAll} />
      );

      const checkboxes = screen.getAllByRole('checkbox');
      fireEvent.click(checkboxes[1]);

      expect(onToggleShowAll).toHaveBeenCalledWith(true);
    });
  });

  it('should call onClose when modal is closed', () => {
    const onClose = vi.fn();
    render(<AppSettings {...defaultProps} onClose={onClose} />);

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

