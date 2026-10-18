import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act, within } from '@testing-library/react';
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

  describe('Hosts', () => {
    const hosts = [
      { id: 'walker', name: 'Walker', status: 'UP' },
      { id: 'mike', name: 'Mike', status: 'DOWN' },
    ];

    it('lists hosts with container counts and status', () => {
      render(<AppSettings {...defaultProps} sources={hosts} counts={{ walker: 3 }} />);
      const rows = document.querySelectorAll('.host-row');
      expect(rows).toHaveLength(2);
      expect(rows[0]).toHaveTextContent('Walker');
      expect(rows[0]).toHaveTextContent('3 containers');
      expect(rows[0]).toHaveTextContent('Connected');
      expect(rows[1]).toHaveTextContent('Mike');
      expect(rows[1]).toHaveTextContent('0 containers');
      expect(screen.getByText('Unreachable')).toBeInTheDocument();
    });

    it('singularises a host with one container', () => {
      render(<AppSettings {...defaultProps} counts={{ local: 1 }} />);
      expect(document.querySelector('.host-count')).toHaveTextContent(/^1 container$/);
    });

    it.each([
      ['UP', 'Connected'],
      ['DOWN', 'Unreachable'],
      ['ERROR', 'Error'],
      ['CHECKING', 'Checking…'],
    ])('shows %s as %s', (status, label) => {
      render(<AppSettings {...defaultProps} sources={[{ id: 'local', name: 'Local', status }]} counts={{}} />);
      expect(screen.getByText(label)).toBeInTheDocument();
    });

    it('keeps the unknown connection row when there are no sources', () => {
      render(<AppSettings {...defaultProps} sources={[]} />);
      expect(screen.getByText('Connections: Unknown')).toBeInTheDocument();
    });
  });

  describe('Group containers by', () => {
    it('switches grouping with a pressed segmented control', () => {
      const onGroupByChange = vi.fn();
      render(<AppSettings {...defaultProps} groupBy="host-stack" onGroupByChange={onGroupByChange} />);
      const group = screen.getByRole('group', { name: 'Group containers by' });
      expect(group.tagName).toBe('FIELDSET');
      expect(within(group).getByRole('button', { name: 'Host › Stack' })).toHaveAttribute('aria-pressed', 'true');
      expect(within(group).getByRole('button', { name: 'Host' })).toHaveAttribute('aria-pressed', 'false');
      fireEvent.click(within(group).getByRole('button', { name: 'Stack' }));
      expect(onGroupByChange).toHaveBeenCalledWith('stack');
      fireEvent.click(within(group).getByRole('button', { name: 'Host' }));
      expect(onGroupByChange).toHaveBeenCalledWith('host');
    });
  });

  describe('Show Stopped Containers Toggle', () => {
    it('should render toggle', () => {
      render(<AppSettings {...defaultProps} />);
      expect(screen.getByLabelText('Show stopped containers')).toBeInTheDocument();
      expect(screen.getByText('Shown with a dashed outline.')).toBeInTheDocument();
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
    it('renames the no-endpoint toggle', () => {
      render(<AppSettings {...defaultProps} />);
      expect(screen.getByLabelText('Show containers without a web UI')).toBeInTheDocument();
      expect(screen.queryByText('Show containers without endpoints')).not.toBeInTheDocument();
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

