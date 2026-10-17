import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import AppSettings from '../../components/AppSettings';
import { refreshVersions } from '../../api';

vi.mock('../../api', () => ({ refreshVersions: vi.fn() }));

describe('AppSettings', () => {
  const defaultProps = {
    showStopped: false,
    showAll: false,
    onToggleShowStopped: vi.fn(),
    onToggleShowAll: vi.fn(),
    onClose: vi.fn(),
    sources: [{id:'local',name:'Local',status:'UP'}],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('starts a version check and prevents duplicate clicks while sending', async () => {
    let resolve;
    refreshVersions.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<AppSettings {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: 'Check versions now' }));
    expect(screen.getByRole('button', { name: 'Starting check…' })).toBeDisabled();
    expect(refreshVersions).toHaveBeenCalledTimes(1);
    await act(async () => resolve());
    expect(screen.getByRole('status')).toHaveTextContent('Version check started');
    expect(screen.getByRole('button', { name: 'Check versions now' })).toBeEnabled();
  });

  it('shows a retryable error when starting the check fails', async () => {
    refreshVersions.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(undefined);
    render(<AppSettings {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: 'Check versions now' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not start version check');
    fireEvent.click(screen.getByRole('button', { name: 'Check versions now' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Version check started');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('should render Settings title', () => {
    render(<AppSettings {...defaultProps} />);
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });

  describe('Docker Status', () => {
    it.each([
      ['UP', 'Connected'],
      ['DOWN', 'Unavailable'],
      ['ERROR', 'Error'],
      ['CHECKING', 'Checking...'],
      ['UNKNOWN', 'Unknown'],
    ])('shows %s as %s', (status, label) => {
      render(<AppSettings {...defaultProps} sources={[{ id: 'local', name: 'Local', status }]} />);
      expect(screen.getByText(`Local: ${label}`)).toBeInTheDocument();
    });

    it.each([
      ['UP', 'status-running'],
      ['DOWN', 'status-stopped'],
    ])('uses the correct indicator for %s', (status, indicatorClass) => {
      render(<AppSettings {...defaultProps} sources={[{ id: 'local', name: 'Local', status }]} />);
      expect(document.querySelector('.status-indicator')).toHaveClass(indicatorClass);
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

