import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import ContainerTile from '../../components/ContainerTile';
import * as api from '../../api';
import { createMockContainer, publishedEndpoint } from '../testUtils';

vi.mock('../../api', () => ({
  fetchContainerVersion: vi.fn(),
}));

const renderWithQuery = (ui) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      {ui}
    </QueryClientProvider>
  );
};

describe('ContainerTile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    api.fetchContainerVersion.mockResolvedValue({ updateAvailable: false });
  });

  it('should render container name and image', () => {
    const container = createMockContainer({
      displayName: 'My Container',
      image: 'nginx:latest',
    });

    renderWithQuery(<ContainerTile container={container} />);

    expect(screen.getByText('My Container')).toBeInTheDocument();
    expect(screen.getByText('nginx:latest')).toBeInTheDocument();
  });

  it('renders and lets settings change the launch URL when storage access is blocked', () => {
    const descriptor = Object.getOwnPropertyDescriptor(window, 'localStorage');
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    try {
      Object.defineProperty(window, 'localStorage', {
        configurable: true,
        get: () => { throw new DOMException('Storage is blocked', 'SecurityError'); },
      });
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [publishedEndpoint(80), publishedEndpoint(8080)] })} />);
      fireEvent.click(screen.getByTitle('Container Settings'));
      fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:8080/' }));
      fireEvent.keyDown(document, { key: 'Escape' });
      fireEvent.click(screen.getByRole('button', { name: /^Open / }));
      expect(window.open).toHaveBeenCalledWith('http://localhost:8080/', '_blank');
    } finally {
      Object.defineProperty(window, 'localStorage', descriptor);
      warning.mockRestore();
    }
  });

  it('should render container icon', () => {
    const container = createMockContainer({
      name: 'nginx',
      iconUrl: 'https://example.com/nginx-icon.png',
    });

    renderWithQuery(<ContainerTile container={container} />);

    const icon = screen.getByRole('img');
    expect(icon).toHaveAttribute('src', 'https://example.com/nginx-icon.png');
  });

  it('should use porthole icon for porthole container', () => {
    const container = createMockContainer({
      name: 'porthole-container',
      iconUrl: 'https://example.com/other.png',
    });

    renderWithQuery(<ContainerTile container={container} />);

    const icon = screen.getByRole('img');
    expect(icon).toHaveAttribute('src', 'porthole.webp');
  });

  describe('Status indicator', () => {
    it.each([
      ['running', 'Up 2 minutes (unhealthy)', 'status-stopped'],
      ['running', 'Up 2 seconds (health: starting)', 'status-warning'],
      ['running', 'Up 2 minutes (healthy)', 'status-running'],
      ['running', null, 'status-running'],
      ['running', undefined, 'status-running'],
      ['paused', 'Up 2 minutes (healthy) (Paused)', 'status-warning'],
      ['restarting', 'Restarting (1) 2 seconds ago', 'status-warning'],
      ['exited', 'Exited (0) 2 minutes ago', 'status-stopped'],
    ])('shows %s with status %s as %s', (state, status, expectedClass) => {
      renderWithQuery(<ContainerTile container={createMockContainer({ state, status })} />);
      expect(document.querySelector('.status-indicator')).toHaveClass(expectedClass);
    });

    it('updates color and tooltip as health changes without a lifecycle change', () => {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      const container = createMockContainer({ status: 'Up 2 minutes (healthy)' });
      const tile = status => (
        <QueryClientProvider client={client}>
          <ContainerTile container={{ ...container, status }} />
        </QueryClientProvider>
      );
      const view = render(tile(container.status));
      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-running');

      for (const [status, expectedClass] of [
        ['Up 3 minutes (unhealthy)', 'status-stopped'],
        ['Up 3 minutes (health: starting)', 'status-warning'],
        ['Up 4 minutes (healthy)', 'status-running'],
      ]) {
        view.rerender(tile(status));
        expect(indicator).toHaveClass(expectedClass);
        expect(indicator).toHaveAttribute('title', status);
      }
    });

    it('should show running status', () => {
      const container = createMockContainer({ state: 'running' });
      renderWithQuery(<ContainerTile container={container} />);

      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-running');
    });

    it('should show stopped status', () => {
      const container = createMockContainer({ state: 'exited' });
      renderWithQuery(<ContainerTile container={container} />);

      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-stopped');
    });

    it('should show warning status for paused', () => {
      const container = createMockContainer({ state: 'paused' });
      renderWithQuery(<ContainerTile container={container} />);

      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-warning');
    });

    it('should show warning status for restarting', () => {
      const container = createMockContainer({ state: 'restarting' });
      renderWithQuery(<ContainerTile container={container} />);

      const indicator = document.querySelector('.status-indicator');
      expect(indicator).toHaveClass('status-warning');
    });
  });

  describe('Keyboard behavior', () => {
    it.each(['Enter', ' '])('opens the API URL with keyboard key %s', async key => {
      const user = userEvent.setup();
      const container = createMockContainer({
        endpoints: [{ id: 'proxy', source: 'traefik', url: 'https://app.example.com/' }],
      });
      renderWithQuery(<ContainerTile container={container} />);
      const launch = screen.getByRole('button', { name: 'Open Test Container' });

      await user.tab();
      expect(launch).toHaveFocus();
      await user.keyboard(key === 'Enter' ? '{Enter}' : ' ');

      expect(window.open).toHaveBeenCalledExactlyOnceWith('https://app.example.com/', '_blank');
    });

    it('opens the chooser from the keyboard for ambiguous endpoints', async () => {
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [publishedEndpoint(80), publishedEndpoint(443)] })} />);
      const launch = screen.getByRole('button', { name: 'Open Test Container' });
      launch.focus();

      await user.keyboard('{Enter}');

      expect(screen.getByText('Published Ports')).toBeInTheDocument();
      expect(window.open).not.toHaveBeenCalled();
    });

    it('skips the disabled launch control and keeps settings keyboard-accessible', async () => {
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [], hasEndpoints: false })} />);
      expect(screen.getByRole('button', { name: 'Open Test Container' })).toBeDisabled();

      await user.tab();
      expect(screen.getByTitle('Container Settings')).toHaveFocus();
      await user.keyboard('{Enter}');

      expect(screen.getByText('Version Updates')).toBeInTheDocument();
      expect(window.open).not.toHaveBeenCalled();
    });
  });

  describe('Click behavior', () => {
    it('should open URL on click when container has single port', () => {
      const container = createMockContainer({
        endpoints: [publishedEndpoint(8080)],
        hasEndpoints: true,
      });

      renderWithQuery(<ContainerTile container={container} />);

      fireEvent.click(screen.getByRole('button', { name: /^Open / }));

      expect(window.open).toHaveBeenCalledWith('http://localhost:8080/', '_blank');
    });

    it('should not open URL when container has no ports', () => {
      const container = createMockContainer({
        endpoints: [],
        hasEndpoints: false,
      });

      renderWithQuery(<ContainerTile container={container} />);

      fireEvent.click(screen.getByRole('button', { name: /^Open / }));

      expect(window.open).not.toHaveBeenCalled();
    });

    it('should show settings modal when container has multiple ports and no preference', () => {
      const container = createMockContainer({
        endpoints: [publishedEndpoint(80), publishedEndpoint(443)],
        hasEndpoints: true,
      });

      renderWithQuery(<ContainerTile container={container} />);

      fireEvent.click(screen.getByRole('button', { name: /^Open / }));

      expect(screen.getByText('Published Ports')).toBeInTheDocument();
    });
  });

  describe('Settings button', () => {
    it('should open settings modal when clicked', () => {
      const container = createMockContainer();

      renderWithQuery(<ContainerTile container={container} />);

      fireEvent.click(screen.getByTitle('Container Settings'));

      expect(screen.getByText(container.name)).toBeInTheDocument();
      expect(screen.getByText('Version Updates')).toBeInTheDocument();
    });

    it('should not trigger card click when settings button is clicked', () => {
      const container = createMockContainer({
        endpoints: [publishedEndpoint(8080)],
        hasEndpoints: true,
      });

      renderWithQuery(<ContainerTile container={container} />);

      fireEvent.click(screen.getByTitle('Container Settings'));

      expect(window.open).not.toHaveBeenCalled();
      expect(screen.getByText('Version Updates')).toBeInTheDocument();
    });
  });

  describe('Update indicator', () => {
    it('should show update warning when update available', async () => {
      api.fetchContainerVersion.mockResolvedValue({
        updateAvailable: true,
        latestVersion: '2.0.0',
      });

      const container = createMockContainer();
      renderWithQuery(<ContainerTile container={container} />);

      await waitFor(() => {
        expect(screen.getByText('⚠️')).toBeInTheDocument();
      });
    });

    it('should not show update warning when no update available', async () => {
      api.fetchContainerVersion.mockResolvedValue({
        updateAvailable: false,
      });

      const container = createMockContainer();
      renderWithQuery(<ContainerTile container={container} />);

      await waitFor(() => {
        expect(api.fetchContainerVersion).toHaveBeenCalled();
      });

      expect(screen.queryByText('⚠️')).not.toBeInTheDocument();
    });
  });

  describe('Disabled state', () => {
    it('disables the tile when no endpoint is selected and lets settings restore a URL', async () => {
      const user = userEvent.setup();
      const container = createMockContainer({ endpoints: [publishedEndpoint(53)] });
      renderWithQuery(<ContainerTile container={container} />);
      const launch = screen.getByRole('button', { name: /^Open / });
      const card = launch.closest('.card');
      await user.click(screen.getByTitle('Container Settings'));
      await user.click(screen.getByRole('radio', { name: 'No endpoint' }));
      expect(launch).toBeDisabled();
      expect(card).toHaveClass('disabled');
      expect(screen.getByRole('radio', { name: 'No endpoint' })).toBeChecked();
      expect(localStorage.getItem('endpoint_pref_test-container')).toBe('"none"');
      await user.keyboard('{Escape}');
      await user.click(launch);
      expect(window.open).not.toHaveBeenCalled();

      await user.click(screen.getByTitle('Container Settings'));
      expect(screen.getByRole('radio', { name: 'No endpoint' })).toBeChecked();
      await user.click(screen.getByRole('checkbox'));
      expect(localStorage.getItem('version_check_test-container')).toBe('false');
      await user.click(screen.getByRole('radio', { name: 'http://localhost:53/' }));
      expect(launch).toBeEnabled();
      expect(card).not.toHaveClass('disabled');
      await user.keyboard('{Escape}');
      await user.click(launch);
      expect(window.open).toHaveBeenCalledExactlyOnceWith('http://localhost:53/', '_blank');
    });

    it('skips a tile with a saved no endpoint choice while keeping settings accessible', async () => {
      localStorage.setItem('endpoint_pref_test-container', '"none"');
      const user = userEvent.setup();
      renderWithQuery(<ContainerTile container={createMockContainer()} />);
      expect(screen.getByRole('button', { name: /^Open / })).toBeDisabled();
      await user.tab();
      expect(screen.getByTitle('Container Settings')).toHaveFocus();
      await user.keyboard('{Enter}');
      expect(screen.getByRole('radio', { name: 'No endpoint' })).toBeChecked();
      expect(window.open).not.toHaveBeenCalled();
    });

    it('should have disabled class when no public ports', () => {
      const container = createMockContainer({
        hasEndpoints: false,
        endpoints: [],
      });

      renderWithQuery(<ContainerTile container={container} />);

      const card = screen.getByRole('img').closest('.card');
      expect(card).toHaveClass('disabled');
    });

    it('should not have disabled class when has public ports', () => {
      const container = createMockContainer({
        hasEndpoints: true,
      });

      renderWithQuery(<ContainerTile container={container} />);

      const card = screen.getByRole('img').closest('.card');
      expect(card).not.toHaveClass('disabled');
    });
  });
  it('opens Traefik by default and a saved published port when explicitly selected', () => {
    const root = { id: 'proxy', source: 'traefik', url: 'https://transmission.example.com/' };
    const container = createMockContainer({ endpoints: [publishedEndpoint(51413), root] });
    renderWithQuery(<ContainerTile container={container} />);
    fireEvent.click(screen.getByRole('button', { name: /^Open / }));
    expect(window.open).toHaveBeenLastCalledWith(root.url, '_blank');
    fireEvent.click(screen.getByTitle('Container Settings'));
    fireEvent.click(screen.getByRole('radio', { name: 'http://localhost:51413/' }));
    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.click(screen.getByRole('button', { name: /^Open / }));
    expect(window.open).toHaveBeenLastCalledWith('http://localhost:51413/', '_blank');
  });

  it('uses a refreshed URL even when the endpoint ID is unchanged', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const container = createMockContainer();
    const view = render(<QueryClientProvider client={client}><ContainerTile container={container} /></QueryClientProvider>);
    view.rerender(<QueryClientProvider client={client}><ContainerTile container={{ ...container, endpoints: [{ ...container.endpoints[0], url: 'http://other.example.com:8080/' }] }} /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('button', { name: /^Open / }));
    expect(window.open).toHaveBeenLastCalledWith('http://other.example.com:8080/', '_blank');
  });

  it('allows settings and version controls on a disabled tile', () => {
    renderWithQuery(<ContainerTile container={createMockContainer({ endpoints: [], hasEndpoints: false })} />);
    fireEvent.click(screen.getByTitle('Container Settings'));
    fireEvent.click(screen.getByRole('checkbox'));
    expect(localStorage.getItem('version_check_test-container')).toBe('false');
    expect(window.open).not.toHaveBeenCalled();
  });

  it('launches the new name preference after a rename with the same Docker ID', () => {
    const root = { id: 'proxy', source: 'traefik', url: 'https://app.example.com/' };
    const container = createMockContainer({ endpoints: [publishedEndpoint(8080), root] });
    localStorage.setItem('endpoint_pref_test-container', JSON.stringify('published-port:8080'));
    localStorage.setItem('endpoint_pref_renamed', JSON.stringify(root.id));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const view = render(<QueryClientProvider client={client}><ContainerTile container={container} /></QueryClientProvider>);
    view.rerender(<QueryClientProvider client={client}><ContainerTile container={{ ...container, name: 'renamed' }} /></QueryClientProvider>);
    fireEvent.click(screen.getByRole('button', { name: /^Open / }));
    expect(window.open).toHaveBeenLastCalledWith(root.url, '_blank');
  });

});
