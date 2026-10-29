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

  it('separates Status, Tile opens and Image with two dividers in order', () => {
    render(<ContainerSettings {...defaultProps} />);
    const body = screen.getByRole('dialog').querySelector('.modal-body');
    const kids = [...body.children];
    const isDivider = el => el.classList.contains('settings-divider');
    expect(body.querySelectorAll('.settings-divider')).toHaveLength(2);
    expect(kids.map(el => (isDivider(el) ? 'divider' : el.tagName.toLowerCase()))).toEqual([
      'section', 'divider', 'fieldset', 'divider', 'div',
    ]);
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

    expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'published-port:443', source: 'published-port' });
  });

  it('offers no endpoint in the same selection group as all URL sources', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} selectedEndpointId="none" onSelectEndpoint={onSelectEndpoint} container={{ ...defaultProps.container, endpoints: [
      { id: 'root', source: 'traefik', url: 'https://app.example.com/', selected: false },
      publishedEndpoint(80),
    ] }} />);

    const group = screen.getByRole('group', { name: 'Tile opens' });
    const radios = within(group).getAllByRole('radio');
    expect(radios).toHaveLength(4);
    expect(new Set(radios.map(radio => radio.name)).size).toBe(1);
    expect(within(group).getByRole('radio', { name: 'No web UI' })).toBeChecked();

    await user.click(within(group).getByRole('radio', { name: 'https://app.example.com/' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'root', source: 'traefik' });
  });

  it('allows selecting no endpoint from the keyboard', async () => {
    const onSelectEndpoint = vi.fn();
    const user = userEvent.setup();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
    screen.getByRole('radio', { name: 'No web UI' }).focus();
    await user.keyboard(' ');
    expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'none', source: 'none' });
  });

  it('should render version updates toggle', () => {
    render(<ContainerSettings {...defaultProps} />);

    const toggle = screen.getByRole('checkbox', { name: 'Show update badge' });
    expect(toggle).toHaveAccessibleName('Show update badge');
    expect(toggle).toHaveAccessibleDescription('Checks keep running either way; this only hides the badge.');
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
      { id: 'root', source: 'traefik', url: 'https://a/', selected: false },
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
      { id: 'root', source: 'traefik', url: 'https://app.example.com/', selected: false },
      { id: 'dl', source: 'traefik', url: 'https://app.example.com/dl/', selected: false },
      publishedEndpoint(80),
      { id: 'ngnext', source: 'ngnext', url: 'https://custom.example.com/', selected: false },
      { id: 'proto', source: '__proto__', url: 'https://proto.example.com/', selected: false },
    ] }} />);
    expect(screen.getAllByRole('radio').map(radio => radio.value)).toEqual(['root', 'dl', 'published-port:80', 'proto', 'ngnext', 'none', 'custom']);
    expect(screen.getByRole('group', { name: 'Tile opens' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio').filter(radio => radio.checked)).toHaveLength(1);
    screen.getByRole('radio', { name: 'https://proto.example.com/' }).focus();
    await user.keyboard(' ');
    expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'proto', source: '__proto__' });
    await user.click(screen.getByRole('radio', { name: 'https://custom.example.com/' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'ngnext', source: 'ngnext' });
  });

  it('shows no checked option when selection is ambiguous', () => {
    render(<ContainerSettings {...defaultProps} selectedEndpointId={null} />);
    expect(screen.getAllByRole('radio').filter(radio => radio.checked)).toHaveLength(0);
  });

  it('only saves custom input after an explicit valid submission', () => {
    const onSelectEndpoint = vi.fn();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
    fireEvent.click(screen.getByRole('radio', { name: 'Custom URL' }));
    const input = screen.getByRole('textbox', { name: 'Custom URL address' });
    fireEvent.change(input, { target: { value: 'https://custom.example.com/path' } });
    expect(onSelectEndpoint).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Save custom URL' }));
    expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'custom', source: 'custom', url: 'https://custom.example.com/path' });
  });

  it.each(['', 'relative/path', 'ftp://example.com/', 'javascript:alert(1)', 'https://', 'http:example.com', 'https:///example.com', 'https://user:password@example.com', 'https://example.com/a b'])('rejects invalid custom URL %s without a save', value => {
    const onSelectEndpoint = vi.fn();
    render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
    fireEvent.click(screen.getByRole('radio', { name: 'Custom URL' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Custom URL address' }), { target: { value } });
    fireEvent.click(screen.getByRole('button', { name: 'Save custom URL' }));
    expect(screen.getByRole('alert')).toHaveTextContent(/absolute HTTP or HTTPS URL/i);
    expect(onSelectEndpoint).not.toHaveBeenCalled();
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true');
  });

  it('edits an existing custom URL and clears its draft when a confirmed discovered choice replaces it', () => {
    const onSelectEndpoint = vi.fn();
    const custom = { id: 'custom', source: 'custom', url: 'https://old.example.com/' };
    const container = { ...defaultProps.container, endpoints: [...defaultProps.container.endpoints, { ...custom, selected: true }], preferences: { selectedEndpoint: custom } };
    const view = render(<ContainerSettings {...defaultProps} container={container} selectedEndpointId="custom" onSelectEndpoint={onSelectEndpoint} />);
    expect(screen.getByRole('radio', { name: 'Custom URL' })).toBeChecked();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'https://new.example.com/' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save custom URL' }));
    expect(onSelectEndpoint).toHaveBeenLastCalledWith({ id: 'custom', source: 'custom', url: 'https://new.example.com/' });
    fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:443/' }));
    expect(onSelectEndpoint).toHaveBeenLastCalledWith({ id: 'published-port:443', source: 'published-port' });
    view.rerender(<ContainerSettings {...defaultProps} container={defaultProps.container} selectedEndpointId="published-port:443" onSelectEndpoint={onSelectEndpoint} />);
    fireEvent.click(screen.getByRole('radio', { name: 'Custom URL' }));
    expect(screen.getByRole('textbox')).toHaveValue('');
  });

  it('retains form focus when the confirmed custom URL arrives', () => {
    const custom = { id: 'custom', source: 'custom', url: 'https://new.example.com/' };
    const view = render(<ContainerSettings {...defaultProps} />);
    fireEvent.click(screen.getByRole('radio', { name: 'Custom URL' }));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: custom.url } });
    screen.getByRole('button', { name: 'Save custom URL' }).focus();
    view.rerender(<ContainerSettings {...defaultProps} selectedEndpointId="custom" container={{ ...defaultProps.container, endpoints: [...defaultProps.container.endpoints, { ...custom, selected: true }], preferences: { selectedEndpoint: custom } }} />);
    expect(screen.getByRole('button', { name: 'Save custom URL' })).toHaveFocus();
  });

  it('disables selection controls while saving and exposes pending and error feedback', () => {
    const container = { ...defaultProps.container, endpoints: [{ id: 'custom', source: 'custom', url: 'https://example.com/', selected: true }], preferences: { selectedEndpoint: { id: 'custom', source: 'custom', url: 'https://example.com/' } } };
    const view = render(<ContainerSettings {...defaultProps} container={container} selectedEndpointId="custom" isSaving />);
    expect(screen.getByText('Saving selection…')).toHaveAttribute('role', 'status');
    screen.getAllByRole('radio').forEach(radio => expect(radio).toBeDisabled());
    expect(screen.getByRole('textbox')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save custom URL' })).toBeDisabled();
    view.rerender(<ContainerSettings {...defaultProps} container={container} selectedEndpointId="custom" saveError={new Error('Disk is full')} />);
    expect(screen.getByRole('alert')).toHaveTextContent(/could not save.*Disk is full/i);
    expect(screen.getByRole('textbox')).toHaveValue('https://example.com/');
    expect(screen.getByRole('radio', { name: 'Custom URL' })).toBeChecked();
  });

  it('uses one custom control and pre-fills a server-resolved custom endpoint without saved metadata', () => {
    const custom = { id: 'custom', source: 'custom', url: 'https://resolved.example.com/', selected: true };
    render(<ContainerSettings {...defaultProps} selectedEndpointId="custom" container={{ ...defaultProps.container, endpoints: [...defaultProps.container.endpoints, custom] }} />);
    expect(screen.getAllByRole('radio', { name: 'Custom URL' })).toHaveLength(1);
    expect(screen.queryByRole('radio', { name: custom.url })).not.toBeInTheDocument();
    expect(screen.getByRole('textbox')).toHaveValue(custom.url);
  });

  it('reports a saved selection whose refresh failed without calling it a save failure', () => {
    render(<ContainerSettings {...defaultProps} refreshError={new Error('Offline')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Selection saved, but containers could not be refreshed. Reload to see the change.');
    expect(screen.queryByText(/Could not save selection/)).not.toBeInTheDocument();
  });

  describe('status section', () => {
    it('is the first section, before Tile opens', () => {
      render(<ContainerSettings {...defaultProps} />);
      const status = screen.getByRole('heading', { name: 'Status' });
      const tile = screen.getByText('Tile opens');
      expect(status.compareDocumentPosition(tile) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('shows title, health and uptime', () => {
      const container = createMockContainer({ ...defaultProps.container, state: 'running', status: 'Up 3 days (healthy)' });
      render(<ContainerSettings {...defaultProps} container={container} />);
      const row = screen.getByRole('status');
      expect(row).toHaveTextContent('Running');
      expect(row).toHaveTextContent('Healthy');
      expect(row).toHaveTextContent('Up 3 days');
    });

    it('shows a note for paused containers outside the status row', () => {
      const container = createMockContainer({ ...defaultProps.container, state: 'paused', status: 'Up 1h (Paused)' });
      render(<ContainerSettings {...defaultProps} container={container} status={{ kind: 'paused', dot: 'off', label: 'Paused', openable: false }} />);
      const note = screen.getByText("Processes are frozen, so the tile won't open until it's resumed.");
      expect(note).toBeInTheDocument();
      expect(screen.getByRole('status')).not.toContainElement(note);
    });

    it('marks unhealthy rows with the bad tone', () => {
      const container = createMockContainer({ ...defaultProps.container, state: 'running', status: 'Up 1h (unhealthy)' });
      render(<ContainerSettings {...defaultProps} container={container} status={{ kind: 'unhealthy', dot: 'bad', label: 'Unhealthy', openable: true }} />);
      expect(screen.getByRole('status')).toHaveClass('status-row--bad');
    });
  });
});
