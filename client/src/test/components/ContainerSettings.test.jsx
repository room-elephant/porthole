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
    expect(radios).toHaveLength(3);
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
    expect(screen.getAllByRole('radio').map(radio => radio.value)).toEqual(['root', 'dl', 'published-port:80', 'proto', 'ngnext', 'none']);
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

  describe('custom URL', () => {
    const SAVED = 'https://saved.example.com/';
    const discovered = defaultProps.container.endpoints;
    const withCustom = (url = SAVED, base = discovered) => ({ ...defaultProps.container, endpoints: [...base, { id: 'custom', source: 'custom', url, selected: true }] });
    const renderSaved = (props = {}) => render(<ContainerSettings {...defaultProps} container={withCustom()} selectedEndpointId="custom" {...props} />);
    const addButton = () => screen.getByRole('button', { name: 'Add custom URL' });
    const editButton = () => screen.getByRole('button', { name: 'Edit custom URL' });
    const customRadio = () => screen.getByRole('radio', { name: 'Custom URL' });
    const urlInput = () => screen.getByRole('textbox', { name: 'Custom URL' });
    const button = name => screen.getByRole('button', { name });

    it('offers a dashed add button after No web UI when no custom URL exists', () => {
      render(<ContainerSettings {...defaultProps} />);
      expect(screen.getAllByRole('radio').map(radio => radio.value)).toEqual(['published-port:80', 'published-port:443', 'none']);
      expect(screen.getByRole('radio', { name: 'No web UI' }).compareDocumentPosition(addButton()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(addButton()).toHaveClass('custom-url-add');
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    });

    it('shows a saved URL as an option row between detected endpoints and No web UI', () => {
      renderSaved();
      expect(screen.getAllByRole('radio').map(radio => radio.value)).toEqual(['published-port:80', 'published-port:443', 'custom', 'none']);
      expect(customRadio()).toBeChecked();
      expect(screen.getByText(SAVED)).toHaveClass('endpoint-url');
      expect(screen.getByText(SAVED)).toHaveAttribute('title', SAVED);
      expect(screen.getByText('Custom')).toHaveClass('endpoint-tag');
      expect(editButton()).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Add custom URL' })).not.toBeInTheDocument();
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    });

    it('marks only a selected custom row as selected', () => {
      const view = renderSaved();
      expect(customRadio().closest('.endpoint-option')).toHaveClass('selected');
      view.rerender(<ContainerSettings {...defaultProps} selectedEndpointId="published-port:80" />);
      expect(customRadio().closest('.endpoint-option')).not.toHaveClass('selected');
    });

    it('opens an empty focused panel without Remove when adding', () => {
      render(<ContainerSettings {...defaultProps} />);
      fireEvent.click(addButton());
      expect(urlInput()).toHaveValue('');
      expect(urlInput()).toHaveAttribute('type', 'url');
      expect(urlInput()).toHaveFocus();
      expect(button('Save')).toBeInTheDocument();
      expect(button('Cancel')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Add custom URL' })).not.toBeInTheDocument();
    });

    it('turns the row into a prefilled panel with Remove when editing', () => {
      renderSaved();
      fireEvent.click(editButton());
      expect(urlInput()).toHaveValue(SAVED);
      expect(button('Remove')).toBeInTheDocument();
      expect(screen.queryByRole('radio', { name: 'Custom URL' })).not.toBeInTheDocument();
      expect(urlInput().compareDocumentPosition(screen.getByRole('radio', { name: 'No web UI' })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('saves a valid URL and collapses into the selected row once the server confirms it', () => {
      const onSelectEndpoint = vi.fn();
      const url = 'https://custom.example.com/path';
      const view = render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
      fireEvent.click(addButton());
      fireEvent.change(urlInput(), { target: { value: `  ${url} ` } });
      expect(onSelectEndpoint).not.toHaveBeenCalled();
      fireEvent.click(button('Save'));
      expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'custom', source: 'custom', url });
      expect(urlInput()).toBeInTheDocument();
      view.rerender(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} container={withCustom(url)} selectedEndpointId="custom" />);
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
      expect(customRadio()).toBeChecked();
      expect(customRadio()).toHaveFocus();
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('submits with Enter', async () => {
      const onSelectEndpoint = vi.fn();
      const user = userEvent.setup();
      render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
      fireEvent.click(addButton());
      await user.type(urlInput(), 'https://typed.example.com/{Enter}');
      expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'custom', source: 'custom', url: 'https://typed.example.com/' });
    });

    it('collapses without a request when an unchanged selected URL is saved', () => {
      const onSelectEndpoint = vi.fn();
      renderSaved({ onSelectEndpoint });
      fireEvent.click(editButton());
      fireEvent.click(button('Save'));
      expect(onSelectEndpoint).not.toHaveBeenCalled();
      expect(customRadio()).toBeChecked();
    });

    it.each(['', 'relative/path', 'ftp://example.com/', 'javascript:alert(1)', 'https://', 'http:example.com', 'https:///example.com', 'https://user:password@example.com', 'https://example.com/a b'])('rejects %s, saving nothing and keeping focus in the field', async value => {
      const onSelectEndpoint = vi.fn();
      const user = userEvent.setup();
      render(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} />);
      await user.click(addButton());
      fireEvent.change(urlInput(), { target: { value } });
      await user.click(button('Save'));
      expect(onSelectEndpoint).not.toHaveBeenCalled();
      const message = screen.getByRole('alert');
      expect(message).toHaveTextContent('Enter an http or https address');
      expect(urlInput()).toHaveAttribute('aria-invalid', 'true');
      expect(urlInput()).toHaveAttribute('aria-describedby', message.id);
      expect(urlInput()).toHaveFocus();
    });

    it('clears the validation message when the field changes', () => {
      render(<ContainerSettings {...defaultProps} />);
      fireEvent.click(addButton());
      fireEvent.click(button('Save'));
      fireEvent.change(urlInput(), { target: { value: 'https://' } });
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(urlInput()).toHaveAttribute('aria-invalid', 'false');
    });

    it('keeps the panel and draft when the save fails', () => {
      const props = { ...defaultProps };
      const view = render(<ContainerSettings {...props} />);
      fireEvent.click(addButton());
      fireEvent.change(urlInput(), { target: { value: 'https://custom.example.com/' } });
      fireEvent.click(button('Save'));
      view.rerender(<ContainerSettings {...props} saveError={new Error('Disk is full')} />);
      expect(screen.getByRole('alert')).toHaveTextContent(/could not save.*Disk is full/i);
      expect(urlInput()).toHaveValue('https://custom.example.com/');
    });

    it('cancelling an add returns to the add button and refocuses it', () => {
      render(<ContainerSettings {...defaultProps} />);
      fireEvent.click(addButton());
      fireEvent.change(urlInput(), { target: { value: 'https://draft.example.com/' } });
      fireEvent.click(button('Cancel'));
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
      expect(addButton()).toHaveFocus();
      fireEvent.click(addButton());
      expect(urlInput()).toHaveValue('');
    });

    it('cancelling an edit discards the draft and restores the row', () => {
      const onSelectEndpoint = vi.fn();
      renderSaved({ onSelectEndpoint });
      fireEvent.click(editButton());
      fireEvent.change(urlInput(), { target: { value: 'https://draft.example.com/' } });
      fireEvent.click(button('Cancel'));
      expect(onSelectEndpoint).not.toHaveBeenCalled();
      expect(screen.getByText(SAVED)).toBeInTheDocument();
      expect(customRadio()).toHaveFocus();
      fireEvent.click(editButton());
      expect(urlInput()).toHaveValue(SAVED);
    });

    it('removing a selected URL falls back to the first detected endpoint', () => {
      const onSelectEndpoint = vi.fn();
      const view = renderSaved({ onSelectEndpoint });
      fireEvent.click(editButton());
      fireEvent.click(button('Remove'));
      expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'published-port:80', source: 'published-port' });
      view.rerender(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} selectedEndpointId="published-port:80" />);
      expect(addButton()).toHaveFocus();
      expect(screen.queryByRole('radio', { name: 'Custom URL' })).not.toBeInTheDocument();
    });

    it('removing a selected URL falls back to No web UI when nothing is detected', () => {
      const onSelectEndpoint = vi.fn();
      render(<ContainerSettings {...defaultProps} container={withCustom(SAVED, [])} selectedEndpointId="custom" onSelectEndpoint={onSelectEndpoint} />);
      fireEvent.click(editButton());
      fireEvent.click(button('Remove'));
      expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'none', source: 'none' });
    });

    it('removing an unselected URL sends no request', () => {
      const onSelectEndpoint = vi.fn();
      const view = renderSaved({ onSelectEndpoint });
      view.rerender(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} selectedEndpointId="published-port:80" />);
      fireEvent.click(editButton());
      fireEvent.click(button('Remove'));
      expect(onSelectEndpoint).not.toHaveBeenCalled();
      expect(addButton()).toBeInTheDocument();
    });

    it('keeps the row until the dialog closes after another option is selected, and re-selecting resends the URL', () => {
      const onSelectEndpoint = vi.fn();
      const view = renderSaved({ onSelectEndpoint });
      fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:443/' }));
      expect(onSelectEndpoint).toHaveBeenLastCalledWith({ id: 'published-port:443', source: 'published-port' });
      view.rerender(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} selectedEndpointId="published-port:443" />);
      expect(customRadio()).not.toBeChecked();
      expect(screen.getByText(SAVED)).toBeInTheDocument();
      // A later poll delivers a new container object without the custom candidate.
      view.rerender(<ContainerSettings {...defaultProps} container={{ ...defaultProps.container }} onSelectEndpoint={onSelectEndpoint} selectedEndpointId="published-port:443" />);
      fireEvent.click(customRadio());
      expect(onSelectEndpoint).toHaveBeenLastCalledWith({ id: 'custom', source: 'custom', url: SAVED });
    });

    it('edits an unselected URL and selects it on save', () => {
      const onSelectEndpoint = vi.fn();
      const view = renderSaved({ onSelectEndpoint });
      view.rerender(<ContainerSettings {...defaultProps} onSelectEndpoint={onSelectEndpoint} selectedEndpointId="published-port:80" />);
      fireEvent.click(editButton());
      fireEvent.click(button('Save'));
      expect(onSelectEndpoint).toHaveBeenCalledWith({ id: 'custom', source: 'custom', url: SAVED });
    });

    it('forgets a deselected URL once the dialog is reopened', () => {
      const view = renderSaved();
      view.rerender(<ContainerSettings {...defaultProps} selectedEndpointId="published-port:80" />);
      view.unmount();
      render(<ContainerSettings {...defaultProps} selectedEndpointId="published-port:80" />);
      expect(addButton()).toBeInTheDocument();
      expect(screen.queryByText(SAVED)).not.toBeInTheDocument();
    });

    it('closes an open panel when another option is selected', () => {
      render(<ContainerSettings {...defaultProps} />);
      fireEvent.click(addButton());
      fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:443/' }));
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    });

    it('disables the row and panel while saving and exposes pending and error feedback', () => {
      const view = renderSaved({ isSaving: true });
      expect(screen.getByText('Saving selection…')).toHaveAttribute('role', 'status');
      screen.getAllByRole('radio').forEach(radio => expect(radio).toBeDisabled());
      expect(editButton()).toBeDisabled();
      view.rerender(<ContainerSettings {...defaultProps} container={withCustom()} selectedEndpointId="custom" saveError={new Error('Disk is full')} />);
      expect(screen.getByRole('alert')).toHaveTextContent(/could not save.*Disk is full/i);
      expect(customRadio()).toBeChecked();
    });
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
