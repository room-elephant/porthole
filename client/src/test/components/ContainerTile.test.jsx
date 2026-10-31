import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import ContainerTile from '../../components/ContainerTile';
import { createMockContainer, publishedEndpoint, renderInventory, inventoryWithSelection } from '../testUtils';

import { selectContainerEndpoint } from '../../api';
vi.mock('../../api', () => ({ selectContainerEndpoint: vi.fn() }));

let savedSelections;
const renderLiveInventory = (children, containers) => {
  const initial = { sources: [], containers };
  return renderInventory(children, initial, () => savedSelections.reduce(inventoryWithSelection, initial));
};
const renderLiveTile = container => renderLiveInventory(({ containers }) => <ContainerTile container={containers[0]} />, [container]);

const renderWithQuery = (ui) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(ui, {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });
};

const settingsName = 'Test Container settings';
const openSettings = () => fireEvent.click(screen.getByRole('button', { name: settingsName }));
const update = { currentVersion: '1.0', latestVersion: '2.0', updateAvailable: true };
const dot = () => document.querySelector('.status-dot');
const tile = () => document.querySelector('.tile');

describe('ContainerTile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    savedSelections = [];
    selectContainerEndpoint.mockImplementation(async request => { savedSelections.push(request); });
  });

  it('navigates using server flags without preference metadata', () => {
    const endpoints = [publishedEndpoint(80, false), publishedEndpoint(443, true)];
    renderWithQuery(<ContainerTile container={createMockContainer({ endpoints })} />);
    expect(screen.getByRole('link')).toHaveAttribute('href', 'http://localhost:443/');
  });

  it('rerenders when only server selected flags change', () => {
    const container = createMockContainer({ endpoints: [publishedEndpoint(80, true), publishedEndpoint(443, false)] });
    const view = renderWithQuery(<ContainerTile container={container} />);
    expect(screen.getByRole('link')).toHaveAttribute('href', 'http://localhost:80/');
    view.rerender(<ContainerTile container={{ ...container, endpoints: [publishedEndpoint(80, false), publishedEndpoint(443, true)] }} />);
    expect(screen.getByRole('link')).toHaveAttribute('href', 'http://localhost:443/');
  });

  it('refreshes an open settings selection when only hasWebUi changes', () => {
    const container = createMockContainer({ endpoints: [publishedEndpoint(80, false), publishedEndpoint(443, false)] });
    const view = renderWithQuery(<ContainerTile container={container} />);
    fireEvent.click(screen.getAllByRole('button', { name: settingsName })[0]);
    expect(screen.getByRole('radio', { name: 'No web UI' })).not.toBeChecked();
    view.rerender(<ContainerTile container={{ ...container, hasWebUi: false }} />);
    expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked();
  });

  it('should render container name and image', () => {
    renderWithQuery(<ContainerTile container={createMockContainer({ displayName: 'My Container', image: 'nginx:latest' })} />);
    expect(screen.getByText('My Container')).toHaveClass('tile-name');
    expect(screen.getByTitle('nginx:latest')).toHaveClass('tile-image');
  });

  it('shows the image wrapped in LRM marks with the full image as title', () => {
    renderWithQuery(<ContainerTile container={createMockContainer({ image: 'lscr.io/linuxserver/sonarr:latest' })} />);
    const image = screen.getByTitle('lscr.io/linuxserver/sonarr:latest');
    expect(image).toHaveClass('tile-image');
    expect(image.textContent).toBe('\u200Elscr.io/linuxserver/sonarr:latest\u200E');
  });

  it('renders the status label before the image', () => {
    const { container: root } = renderWithQuery(
      <ContainerTile container={createMockContainer({ state: 'exited', status: 'Exited (0) 1 hour ago' })} />,
    );
    const label = root.querySelector('.tile-label');
    const image = root.querySelector('.tile-image');
    expect(label).not.toBeNull();
    expect(label.compareDocumentPosition(image) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(root.querySelector('.tile-dot')).toHaveTextContent('·');
  });

  it('is a new-tab link to the selected endpoint with the host as subtitle', () => {
    renderWithQuery(<ContainerTile container={createMockContainer()} sourceName="Walker" sourceOnline />);
    const link = screen.getByRole('link', { name: /Test Container/ });
    expect(link).toHaveAttribute('href', 'http://localhost:8080/');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener');
    expect(screen.getByText('localhost:8080')).toHaveClass('tile-host');
    expect(screen.getByTitle('test-image:latest')).toHaveClass('tile-image');
    expect(link.querySelector('.tile-go')).toHaveAttribute('aria-hidden', 'true');
    expect(window.open).not.toHaveBeenCalled();
  });

  it('shows an optional host tag before the subtitle', () => {
    renderWithQuery(<ContainerTile container={createMockContainer()} hostTag="Walker" />);
    expect(screen.getByText('Walker')).toHaveClass('tile-tag');
  });

  it('renders and lets settings change the launch URL when storage access is blocked', async () => {
    const descriptor = Object.getOwnPropertyDescriptor(window, 'localStorage');
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    try {
      Object.defineProperty(window, 'localStorage', {
        configurable: true,
        get: () => { throw new DOMException('Storage is blocked', 'SecurityError'); },
      });
      renderLiveTile(createMockContainer({ endpoints: [publishedEndpoint(80), publishedEndpoint(8080)] }));
      fireEvent.click(screen.getAllByRole('button', { name: settingsName })[0]);
      fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:8080/' }));
      await waitFor(() => expect(screen.getByRole('link')).toHaveAttribute('href', 'http://localhost:8080/'));
      fireEvent.keyDown(document, { key: 'Escape' });
      expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'http://localhost:8080/');
    } finally {
      Object.defineProperty(window, 'localStorage', descriptor);
      warning.mockRestore();
    }
  });

  it('should render container icon', () => {
    renderWithQuery(<ContainerTile container={createMockContainer({ name: 'nginx', iconUrl: 'https://example.com/nginx-icon.png' })} />);
    expect(document.querySelector('img')).toHaveAttribute('src', 'https://example.com/nginx-icon.png');
  });

  it('should use porthole icon for porthole container', () => {
    renderWithQuery(<ContainerTile container={createMockContainer({ name: 'porthole-container', self: true, iconUrl: 'https://example.com/other.png' })} />);
    expect(document.querySelector('img')).toHaveAttribute('src', 'porthole-mark.svg');
  });

  it('falls back to the default icon when the image fails to load', () => {
    renderWithQuery(<ContainerTile container={createMockContainer()} />);
    const img = document.querySelector('img');
    fireEvent.error(img);
    expect(img.getAttribute('src')).toContain('docker.webp');
  });

  describe('Status', () => {
    it.each([
      ['running', 'Up 2 minutes (unhealthy)', 'dot-bad', 'Unhealthy', 'unhealthy'],
      ['running', 'Up 2 seconds (health: starting)', 'dot-warn', 'Starting', 'starting'],
      ['restarting', 'Restarting (1) 2 seconds ago', 'dot-warn', 'Restarting', 'restarting'],
      ['running', 'Up 2 minutes (healthy)', 'dot-ok', null, 'ok'],
      ['running', 'Up 2 hours', 'dot-ok', null, 'ok'],
      ['running', null, 'dot-ok', null, 'ok'],
    ])('shows %s with status %s as %s', (state, status, dotClass, label, kind) => {
      renderWithQuery(<ContainerTile container={createMockContainer({ state, status })} />);
      expect(dot()).toHaveClass(dotClass);
      expect(dot()).toHaveAttribute('title', label ?? 'Running');
      expect(tile()).toHaveClass(`tile--${kind}`);
      if (label) expect(screen.getByText(label)).toHaveClass('tile-label', `label-${kind}`);
      else expect(document.querySelector('.tile-label')).toBeNull();
    });

    it('keeps a restarting tile openable as a link', () => {
      renderWithQuery(<ContainerTile container={createMockContainer({ state: 'restarting', status: 'Restarting (1) 2 seconds ago' })} />);
      expect(screen.getByRole('link', { name: /Test Container/ })).toBeInTheDocument();
    });

    it('updates dot, label and tile as health changes without a lifecycle change', () => {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      const container = createMockContainer({ status: 'Up 2 minutes (healthy)' });
      const view = (status) => (
        <QueryClientProvider client={client}>
          <ContainerTile container={{ ...container, status }} />
        </QueryClientProvider>
      );
      const rendered = render(view(container.status));
      expect(dot()).toHaveClass('dot-ok');
      for (const [status, dotClass, title] of [
        ['Up 3 minutes (unhealthy)', 'dot-bad', 'Unhealthy'],
        ['Up 3 minutes (health: starting)', 'dot-warn', 'Starting'],
        ['Up 4 minutes (healthy)', 'dot-ok', 'Running'],
      ]) {
        rendered.rerender(view(status));
        expect(dot()).toHaveClass(dotClass);
        expect(dot()).toHaveAttribute('title', title);
      }
    });

    it.each([['exited', 'Stopped'], ['paused', 'Paused']])('renders a %s container as a dashed settings button', async (state, label) => {
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer({ state })} />);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(tile()).toHaveClass('tile--dashed');
      expect(screen.getByText(label)).toHaveClass('tile-label');
      await user.click(screen.getAllByRole('button', { name: settingsName })[0]);
      expect(screen.getByRole('heading', { name: 'Image' })).toBeInTheDocument();
      expect(window.open).not.toHaveBeenCalled();
    });

    it('renders containers of an offline host as Offline, not links', () => {
      renderWithQuery(<ContainerTile container={createMockContainer()} sourceOnline={false} />);
      expect(screen.getByText('Offline')).toHaveClass('label-offline');
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(tile()).toHaveClass('tile--dashed');
    });

    it('prefers Unhealthy over Update', () => {
      renderWithQuery(<ContainerTile container={createMockContainer({ status: 'Up 2 minutes (unhealthy)', version: update })} />);
      expect(screen.getByText('Unhealthy')).toBeInTheDocument();
      expect(screen.queryByText('Update')).not.toBeInTheDocument();
      expect(screen.getByTitle('Update available')).toBeInTheDocument();
    });
  });

  describe('Keyboard behavior', () => {
    it('tabs from the link to the settings button and opens settings with Enter', async () => {
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer()} />);
      await user.tab();
      expect(screen.getByRole('link', { name: /Test Container/ })).toHaveFocus();
      await user.tab();
      expect(screen.getByRole('button', { name: settingsName })).toHaveFocus();
      await user.keyboard('{Enter}');
      expect(screen.getByRole('heading', { name: 'Image' })).toBeInTheDocument();
    });

    it('opens the chooser from the keyboard for ambiguous endpoints', async () => {
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [publishedEndpoint(80), publishedEndpoint(443)] })} />);
      await user.tab();
      expect(screen.getAllByRole('button', { name: settingsName })[0]).toHaveFocus();
      await user.keyboard('{Enter}');
      expect(screen.getAllByText('Port')).not.toHaveLength(0);
      expect(window.open).not.toHaveBeenCalled();
    });
  });

  describe('Settings opening', () => {
    it('opens settings when endpoints exist but none can be chosen automatically', () => {
      const endpoints = [
        { id: 'a', source: 'traefik', url: 'https://app.example.com/a', selected: false },
        { id: 'b', source: 'traefik', url: 'https://app.example.com/b', selected: false },
      ];
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints })} />);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(screen.getByText('Choose a URL')).toHaveClass('tile-host');
      expect(tile()).not.toHaveClass('tile--dashed');
      fireEvent.click(screen.getAllByRole('button', { name: settingsName })[0]);
      expect(screen.getAllByText('Traefik')).not.toHaveLength(0);
    });

    it('keeps an unhealthy tile red when no endpoint is selected', () => {
      renderWithQuery(<ContainerTile container={createMockContainer({ status: 'Up 2 minutes (unhealthy)', endpoints: [] })} />);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(tile()).toHaveClass('tile--unhealthy');
      expect(tile()).not.toHaveClass('tile--dashed');
    });

    it('shows ambiguous published ports in the chooser', () => {
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [publishedEndpoint(80), publishedEndpoint(443)] })} />);
      fireEvent.click(screen.getAllByRole('button', { name: settingsName })[0]);
      expect(screen.getAllByText('Port')).not.toHaveLength(0);
    });

    it('should open settings modal from the menu button without navigating', () => {
      renderWithQuery(<ContainerTile container={createMockContainer()} />);
      openSettings();
      expect(screen.getByText('test-container')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Image' })).toBeInTheDocument();
      expect(window.open).not.toHaveBeenCalled();
    });

    it('allows settings and version controls on a tile without endpoints', () => {
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [], hasEndpoints: false })} />);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      fireEvent.click(screen.getAllByRole('button', { name: settingsName })[0]);
      fireEvent.click(screen.getByRole('checkbox'));
      expect(localStorage.getItem('version_check_v2_["local","test-container"]')).toBe('false');
    });
  });

  describe('Update indicator', () => {
    it('labels an update in purple when the badge preference is on', () => {
      renderWithQuery(<ContainerTile container={createMockContainer({ version: update })} />);
      expect(screen.getByText('Update')).toHaveClass('label-update');
      expect(screen.getByTitle('Update available')).toHaveClass('update-badge');
      expect(dot()).toHaveClass('dot-ok');
      expect(dot()).toHaveAttribute('title', 'Running');
    });

    it.each([null, { currentVersion: '1.0', latestVersion: '1.0', updateAvailable: false }])('shows no badge for version %s', version => {
      renderWithQuery(<ContainerTile container={createMockContainer({ version })} />);
      expect(screen.queryByTitle('Update available')).not.toBeInTheDocument();
      expect(screen.queryByText('Update')).not.toBeInTheDocument();
    });

    it('rerenders new embedded results when other container metadata is unchanged', () => {
      const container = createMockContainer({ version: null });
      const view = renderWithQuery(<ContainerTile container={container} />);
      expect(screen.queryByTitle('Update available')).not.toBeInTheDocument();
      const updated = { ...container, version: update };
      view.rerender(<ContainerTile container={updated} />);
      expect(screen.getByTitle('Update available')).toBeInTheDocument();
      view.rerender(<ContainerTile container={{ ...updated, version: { ...update, updateAvailable: false } }} />);
      expect(screen.queryByTitle('Update available')).not.toBeInTheDocument();
    });

    it('keeps the stored preference for hiding update notifications', () => {
      localStorage.setItem('version_check_v2_["local","test-container"]', 'false');
      renderWithQuery(<ContainerTile container={createMockContainer({ version: update })} />);
      expect(screen.queryByTitle('Update available')).not.toBeInTheDocument();
      openSettings();
      fireEvent.click(screen.getByRole('checkbox'));
      // The tile and the open dialog's icon both carry the badge.
      expect(screen.getAllByTitle('Update available')).toHaveLength(2);
    });
  });

  describe('No web UI choice', () => {
    it('turns the tile into a settings button and lets settings restore a URL', async () => {
      const user = userEvent.setup();
      renderLiveTile(createMockContainer({ endpoints: [publishedEndpoint(53, true)] }));
      await user.click(screen.getByRole('button', { name: settingsName }));
      await user.click(screen.getByRole('radio', { name: 'No web UI' }));
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(tile()).not.toHaveClass('tile--dashed');
      expect(selectContainerEndpoint).toHaveBeenCalledWith({ sourceId: 'local', containerName: 'test-container', selectedEndpoint: { id: 'none', source: 'none' } });
      await user.click(screen.getByRole('checkbox'));
      expect(localStorage.getItem('version_check_v2_["local","test-container"]')).toBe('false');
      await user.click(screen.getByRole('radio', { name: 'http://localhost:53/' }));
      await user.keyboard('{Escape}');
      expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'http://localhost:53/');
      expect(tile()).not.toHaveClass('tile--dashed');
    });

    it('opens the saved no endpoint choice from the main button', async () => {
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false })} />);
      await user.tab();
      expect(screen.getAllByRole('button', { name: settingsName })[0]).toHaveFocus();
      await user.keyboard('{Enter}');
      expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked();
    });
  });

  it('opens Traefik by default and a saved published port when explicitly selected', async () => {
    const root = { id: 'proxy', source: 'traefik', url: 'https://transmission.example.com/', selected: true };
    renderLiveTile(createMockContainer({ endpoints: [publishedEndpoint(51413), root] }));
    expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', root.url);
    openSettings();
    fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:51413/' }));
    await waitFor(() => expect(screen.getByRole('link')).toHaveAttribute('href', 'http://localhost:51413/'));
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'http://localhost:51413/');
  });

  it('updates a memoized tile when the resolved custom candidate changes', () => {
    const container = createMockContainer();
    const view = renderWithQuery(<ContainerTile container={container} />);
    view.rerender(<ContainerTile container={{ ...container, endpoints: [{ id: 'custom', source: 'custom', url: 'https://first.example.com/', selected: true }] }} />);
    expect(screen.getByRole('link')).toHaveAttribute('href', 'https://first.example.com/');
    view.rerender(<ContainerTile container={{ ...container, endpoints: [{ id: 'custom', source: 'custom', url: 'https://edited.example.com/', selected: true }] }} />);
    expect(screen.getByRole('link')).toHaveAttribute('href', 'https://edited.example.com/');
  });

  it('uses a refreshed URL even when the endpoint ID is unchanged', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const container = createMockContainer();
    const view = render(<QueryClientProvider client={client}><ContainerTile container={container} /></QueryClientProvider>);
    view.rerender(<QueryClientProvider client={client}><ContainerTile container={{ ...container, endpoints: [{ ...container.endpoints[0], url: 'http://other.example.com:8080/' }] }} /></QueryClientProvider>);
    expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'http://other.example.com:8080/');
    expect(screen.getByText('other.example.com:8080')).toHaveClass('tile-host');
  });

  it('rerenders when the offline state, source name or host tag changes', () => {
    const container = createMockContainer();
    const view = renderWithQuery(<ContainerTile container={container} sourceOnline hostTag="A" />);
    expect(screen.getByRole('link')).toBeInTheDocument();
    view.rerender(<ContainerTile container={container} sourceOnline={false} hostTag="A" />);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    view.rerender(<ContainerTile container={container} sourceOnline={false} hostTag="B" />);
    expect(screen.getByText('B')).toHaveClass('tile-tag');
  });

  it('launches the new name preference after a rename with the same Docker ID', () => {
    const root = { id: 'proxy', source: 'traefik', url: 'https://app.example.com/', selected: false };
    const container = createMockContainer({ endpoints: [publishedEndpoint(8080, true), root] });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = render(<QueryClientProvider client={client}><ContainerTile container={container} /></QueryClientProvider>);
    view.rerender(<QueryClientProvider client={client}><ContainerTile container={{ ...container, name: 'renamed', endpoints: [publishedEndpoint(8080, false), { ...root, selected: true }] }} /></QueryClientProvider>);
    expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', root.url);
  });

  it('keeps long names on one line inside tile-name', () => {
    const name = 'n'.repeat(80);
    renderWithQuery(<ContainerTile container={createMockContainer({ displayName: name })} />);
    expect(document.querySelector('.tile-name')).toHaveTextContent(name);
  });

  it('syncs two tiles of the same container in the same tab', async () => {
    const container = createMockContainer({ endpoints: [publishedEndpoint(80), publishedEndpoint(8080)] });
    renderLiveInventory(({ containers }) => <><ContainerTile container={containers[0]} /><ContainerTile container={containers[0]} /></>, [container]);
    fireEvent.click(screen.getAllByRole('button', { name: settingsName })[0]);
    fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:8080/' }));
    await waitFor(() => expect(screen.getAllByRole('link', { name: /Test Container/ })).toHaveLength(2));
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getAllByRole('link', { name: /Test Container/ })).toHaveLength(2);
  });
});
