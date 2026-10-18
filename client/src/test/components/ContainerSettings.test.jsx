import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { publishedEndpoint, createMockContainer } from '../testUtils';
import ContainerSettings from '../../components/ContainerSettings';

describe('ContainerSettings', () => {
  const defaultProps = {
    container: createMockContainer({
      name: 'nginx',
      displayName: 'Nginx',
      endpoints: [publishedEndpoint(80), publishedEndpoint(443)],
    }),
    sourceName: 'Walker',
    status: { kind: 'ok', dot: 'ok', label: '' },
    showUpdate: false,
    selectedEndpointId: 'published-port:80',
    checkUpdates: true,
    onClose: vi.fn(),
    onSelectEndpoint: vi.fn(),
    onToggleCheckUpdates: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render container name as title', () => {
    render(<ContainerSettings {...defaultProps} />);
    expect(screen.getByRole('dialog', { name: 'Nginx' })).toBeInTheDocument();
  });

  it('should render port options', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(screen.getByText('http://localhost:80/')).toBeInTheDocument();
    expect(screen.getByText('http://localhost:443/')).toBeInTheDocument();
  });

  it('should mark the selected port', () => {
    render(<ContainerSettings {...defaultProps} selectedEndpointId="published-port:80" />);

    const selectedEndpointOption = screen.getByText('http://localhost:80/').closest('.endpoint-option');
    expect(selectedEndpointOption).toHaveClass('selected');
  });

  it('should call onSelectEndpoint when port is clicked', () => {
    const onSelectEndpoint = vi.fn();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);

    fireEvent.click(screen.getByText('http://localhost:443/'));

    expect(onSelectEndpoint).toHaveBeenCalledWith('published-port:443');
  });

  it('offers no endpoint in the same selection group as all URL sources', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} selectedEndpointId="none" onSelectEndpoint={onSelectEndpoint} container={{ ...defaultProps.container, endpoints: [
      { id: 'root', source: 'traefik', url: 'https://app.example.com/' },
      publishedEndpoint(80),
    ] }} />);

    const group = screen.getByRole('group', { name: 'Tile opens' });
    const radios = within(group).getAllByRole('radio');
    expect(radios).toHaveLength(3);
    expect(new Set(radios.map(radio => radio.name)).size).toBe(1);
    expect(within(group).getByRole('radio', { name: 'No web UI' })).toBeChecked();

    await user.click(within(group).getByRole('radio', { name: 'https://app.example.com/' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith('root');
  });

  it('allows selecting no endpoint from the keyboard', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
    screen.getByRole('radio', { name: 'No web UI' }).focus();
    await user.keyboard(' ');
    expect(onSelectEndpoint).toHaveBeenCalledWith('none');
  });

  it('should render version updates toggle', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(screen.getByText('Show update badge')).toBeInTheDocument();
    expect(screen.getByRole('checkbox')).toBeInTheDocument();
  });

  it('should have toggle checked when checkUpdates is true', () => {
    render(<ContainerSettings {...defaultProps} checkUpdates={true} />);

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).toBeChecked();
  });

  it('should have toggle unchecked when checkUpdates is false', () => {
    render(<ContainerSettings {...defaultProps} checkUpdates={false} />);

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();
  });

  it('should call onToggleCheckUpdates when toggle is changed', () => {
    const onToggleCheckUpdates = vi.fn();
    render(
      <ContainerSettings
        {...defaultProps}
        checkUpdates={false}
        onToggleCheckUpdates={onToggleCheckUpdates}
      />
    );

    fireEvent.click(screen.getByRole('checkbox'));

    expect(onToggleCheckUpdates).toHaveBeenCalledWith(true);
  });

  it('should call onClose when modal is closed', () => {
    const onClose = vi.fn();
    render(<ContainerSettings {...defaultProps} onClose={onClose} />);

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should not render port section when no ports', () => {
    render(<ContainerSettings {...defaultProps} container={{ ...defaultProps.container, endpoints: [] }} />);

    expect(screen.queryByText('Port')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Image' })).toBeInTheDocument();
  });

  it('should render description text', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(screen.getByText(/this only hides the badge/)).toBeInTheDocument();
  });

  it('lists endpoints with Traefik/Port tags in order, then No web UI', () => {
    render(<ContainerSettings {...defaultProps} container={{ ...defaultProps.container, endpoints: [
      publishedEndpoint(8080),
      { id: 'root', source: 'traefik', url: 'https://a/' },
    ] }} />);

    const labels = screen.getAllByRole('radio').map(radio => radio.closest('label').textContent);
    expect(labels[0]).toMatch(/https:\/\/a\//);
    expect(labels[1]).toMatch(/localhost:8080/);
    expect(labels[2]).toMatch(/No web UI/);
    expect(screen.getByText('Traefik')).toHaveClass('endpoint-tag', 'tag-traefik');
    expect(screen.getByText('Port')).toHaveClass('endpoint-tag', 'tag-published-port');
    expect(screen.getByText('Shown as a small chip instead of a tile')).toBeInTheDocument();
  });

  it('shows the host and stack in the header', () => {
    render(<ContainerSettings {...defaultProps} container={{ ...defaultProps.container, name: 'test-container', project: 'smh' }} />);

    expect(screen.getByText('test-container · Walker / smh')).toBeInTheDocument();
  });

  it('shows Up to date with the running version', () => {
    const version = { currentVersion: '1.2', latestVersion: '1.2', updateAvailable: false };
    render(<ContainerSettings {...defaultProps} container={{ ...defaultProps.container, version }} />);

    expect(screen.getByText('Up to date')).toBeInTheDocument();
    expect(screen.getByText('1.2')).toBeInTheDocument();
    expect(screen.queryByText('Latest')).not.toBeInTheDocument();
  });

  it('shows Running and Latest when an update exists', () => {
    const version = { currentVersion: '4.0.5', latestVersion: '4.0.6', updateAvailable: true };
    render(<ContainerSettings {...defaultProps} showUpdate container={{ ...defaultProps.container, version }} />);

    expect(screen.getByText('Update available')).toBeInTheDocument();
    expect(screen.getByText('4.0.5')).toBeInTheDocument();
    expect(screen.getByText('4.0.6')).toBeInTheDocument();
    expect(screen.getByText('Latest')).toBeInTheDocument();
  });

  it('says Version unknown without version info', () => {
    render(<ContainerSettings {...defaultProps} />);

    expect(screen.getByText('Version unknown')).toBeInTheDocument();
    expect(screen.queryByText('Latest')).not.toBeInTheDocument();
  });

  it('says Version unknown when the latest version cannot be resolved', () => {
    const version = { currentVersion: '1.2', latestVersion: null, updateAvailable: false };
    render(<ContainerSettings {...defaultProps} container={{ ...defaultProps.container, version }} />);

    expect(screen.getByText('Version unknown')).toBeInTheDocument();
    expect(screen.queryByText('Up to date')).not.toBeInTheDocument();
  });

  it('checks No web UI when the container has no endpoints', () => {
    render(<ContainerSettings {...defaultProps} selectedEndpointId={null} container={{ ...defaultProps.container, endpoints: [] }} />);

    expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked();
  });

  it('groups all sources and offers one accessible selection across sections', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} container={{ ...defaultProps.container, endpoints: [
      { id: 'root', source: 'traefik', url: 'https://app.example.com/' },
      { id: 'dl', source: 'traefik', url: 'https://app.example.com/dl/' },
      publishedEndpoint(80),
      { id: 'custom', source: 'ngnext', url: 'https://custom.example.com/' },
      { id: 'proto', source: '__proto__', url: 'https://proto.example.com/' },
    ] }} />);
    expect(screen.getAllByRole('radio').map(radio => radio.value)).toEqual(['root', 'dl', 'published-port:80', 'proto', 'custom', 'none']);
    expect(screen.getByRole('group', { name: 'Tile opens' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio').filter(radio => radio.checked)).toHaveLength(1);
    screen.getByRole('radio', { name: 'https://proto.example.com/' }).focus();
    await user.keyboard(' ');
    expect(onSelectEndpoint).toHaveBeenCalledWith('proto');
    await user.click(screen.getByRole('radio', { name: 'https://custom.example.com/' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith('custom');
  });

  it('shows no checked option when selection is ambiguous', () => {
    render(<ContainerSettings {...defaultProps} selectedEndpointId={null} />);
    expect(screen.getAllByRole('radio').filter(radio => radio.checked)).toHaveLength(0);
  });

});
