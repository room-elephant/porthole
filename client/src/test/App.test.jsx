import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, within, fireEvent, waitFor } from '@testing-library/react';
import App from '../App';
import { renderWithQuery, createMockContainer } from './testUtils';
import { useDashboard } from '../hooks/useDashboard';
import { createLink, fetchIconLibrary, selectEntryIcon } from '../api';

vi.mock('../hooks/useDashboard');
vi.mock('../api', async importOriginal => ({ ...await importOriginal(), updateLink: vi.fn().mockResolvedValue(), createLink: vi.fn(), selectEntryIcon: vi.fn(), fetchIconLibrary: vi.fn() }));

/** What the mocked dashboard query returns: containers and sources, links, or a loading or failed read. */
let inventory = { data: { sources: [], containers: [] } };
let links = [];
useDashboard.mockImplementation(() => (inventory.data
  ? { data: { ...inventory.data, links }, isLoading: false, error: null }
  : { data: undefined, isLoading: Boolean(inventory.isLoading), error: inventory.error ?? null }));
const loading = () => { inventory = { isLoading: true }; };
const failing = () => { inventory = { error: { response: { status: 502 } } }; };
const setLinks = value => { links = value; };

const walker = { id: 'walker', name: 'Walker', status: 'UP', error: null };
const mike = { id: 'mike', name: 'Mike', status: 'UP', error: null };
const query = data => { inventory = { data }; };
const filter = () => screen.getByRole('searchbox', { name: 'Filter containers and links' });

describe('App', () => {
  beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); setLinks([]); query({ sources: [], containers: [] }); });

  it('shows six horizontal skeletons and a status while loading', () => {
    loading();
    renderWithQuery(<App />);
    expect(document.querySelectorAll('.skeleton-tile')).toHaveLength(6);
    expect(screen.getByRole('status')).toHaveTextContent('Connecting…');
    expect(screen.getByRole('status').tagName).toBe('OUTPUT');
    expect(screen.getByText('Porthole')).toHaveClass('wordmark');
  });

  it('reports global fetch errors without identifying a Docker host as down', () => {
    failing();
    renderWithQuery(<App />);
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to fetch containers');
    expect(screen.queryByText(/Can't reach/)).not.toBeInTheDocument();
  });

  it('groups matching names and projects under their owning host', () => {
    query({ sources: [walker, mike], containers: [
      createMockContainer({ id: 'same', sourceId: 'walker', project: 'shared', displayName: 'Walker app' }),
      createMockContainer({ id: 'same', sourceId: 'mike', project: 'shared', displayName: 'Mike app' }),
    ] });
    renderWithQuery(<App />);
    const w = screen.getByRole('region', { name: 'Walker' });
    const m = screen.getByRole('region', { name: 'Mike' });
    expect(within(w).getByText('Walker app')).toBeInTheDocument();
    expect(within(w).queryByText('Mike app')).not.toBeInTheDocument();
    expect(within(m).getByText('Mike app')).toBeInTheDocument();
    expect(document.querySelectorAll('.stack-label')).toHaveLength(2);
  });

  it('hides the self container and its host count when Show Porthole itself is off', () => {
    localStorage.setItem('porthole_showSelf', 'false');
    query({ sources: [walker], containers: [
      createMockContainer({ id: 'a', sourceId: 'walker', displayName: 'Sonarr' }),
      createMockContainer({ id: 'b', sourceId: 'walker', displayName: 'Porthole', self: true }),
    ] });
    renderWithQuery(<App />);
    expect(screen.getByText('Sonarr')).toBeInTheDocument();
    const section = screen.getByRole('region', { name: 'Walker' });
    const names = [...section.querySelectorAll('.tile-name')].map(el => el.textContent);
    expect(names).toEqual(['Sonarr']);
    expect(screen.getByRole('button', { name: /Walker/ }).querySelector('.pill-count')).toHaveTextContent(/^1$/);
  });

  it('shows the empty state and a zero count when Porthole is the only container and is hidden', () => {
    localStorage.setItem('porthole_showSelf', 'false');
    query({ sources: [walker], containers: [
      createMockContainer({ id: 'b', sourceId: 'walker', displayName: 'Porthole', self: true }),
    ] });
    renderWithQuery(<App />);
    const section = screen.getByRole('region', { name: 'Walker' });
    expect(within(section).getByRole('heading', { name: 'Nothing running on Walker yet' })).toBeInTheDocument();
    expect(section.querySelectorAll('.tile-name')).toHaveLength(0);
    expect(screen.getByRole('button', { name: /Walker/ }).querySelector('.pill-count')).toHaveTextContent(/^0$/);
  });

  it('shows the self container by default', () => {
    query({ sources: [walker], containers: [
      createMockContainer({ id: 'b', sourceId: 'walker', displayName: 'Porthole', self: true }),
    ] });
    renderWithQuery(<App />);
    const section = screen.getByRole('region', { name: 'Walker' });
    expect([...section.querySelectorAll('.tile-name')].map(el => el.textContent)).toEqual(['Porthole']);
  });

  it('shows the empty state when stack grouping has no containers', () => {
    localStorage.setItem('porthole_groupBy', '"stack"');
    query({ sources: [walker], containers: [] });
    renderWithQuery(<App />);
    expect(screen.getByRole('heading', { name: 'No containers found' })).toBeInTheDocument();
  });

  it('falls back to host-stack grouping for an invalid stored value', () => {
    localStorage.setItem('porthole_groupBy', '"foo"');
    query({ sources: [walker], containers: [createMockContainer({ sourceId: 'walker', project: 'smh' })] });
    renderWithQuery(<App />);
    expect(screen.getByRole('region', { name: 'Walker' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    expect(screen.getByRole('button', { name: 'Host › Stack' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('groups by stack when the setting is stored', () => {
    localStorage.setItem('porthole_groupBy', '"stack"');
    query({ sources: [walker, mike], containers: [
      createMockContainer({ id: 'a', sourceId: 'walker', project: 'shared', displayName: 'Walker app' }),
      createMockContainer({ id: 'b', sourceId: 'mike', project: 'shared', displayName: 'Mike app' }),
    ] });
    renderWithQuery(<App />);
    const region = screen.getByRole('region', { name: 'shared' });
    expect(within(region).getByText('Walker app')).toBeInTheDocument();
    expect(within(region).getByText('Mike app')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Walker' })).not.toBeInTheDocument();
  });

  it('keeps healthy containers when another source is unavailable and shows recovery', () => {
    query({ sources: [walker, { ...mike, status: 'DOWN', error: 'Docker is not reachable' }], containers: [createMockContainer({ sourceId: 'walker' })] });
    const view = renderWithQuery(<App />);
    expect(screen.getByText('Test Container')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Mike' })).getByRole('alert')).toHaveTextContent("Can't reach Mike");
    query({ sources: [walker, mike], containers: [createMockContainer({ sourceId: 'mike', displayName: 'Recovered' })] });
    view.rerender(<App />);
    expect(screen.getByText('Recovered')).toBeInTheDocument();
    expect(screen.queryByText('Docker unavailable')).not.toBeInTheDocument();
  });

  it('shows the containers the server retains for a host that went down', () => {
    query({ sources: [walker, { ...mike, status: 'DOWN', error: 'gone' }], containers: [createMockContainer({ id: 'm1', sourceId: 'mike', displayName: 'Mike app' })] });
    renderWithQuery(<App />);
    const region = screen.getByRole('region', { name: 'Mike' });
    expect(within(region).getByText('Mike app')).toBeInTheDocument();
    expect(within(region).getByRole('alert')).toHaveTextContent("Can't reach Mike");
  });

  it('shows connected empty hosts with filter guidance', () => {
    query({ sources: [walker], containers: [] });
    renderWithQuery(<App />);
    expect(screen.getByText('Nothing running on Walker yet')).toBeInTheDocument();
    expect(screen.getByText(/No running containers with endpoints/)).toBeInTheDocument();
  });

  it('shows all unavailable hosts without inventing an empty success', () => {
    query({ sources: [{ ...walker, status: 'DOWN' }, { ...mike, status: 'DOWN' }], containers: [] });
    renderWithQuery(<App />);
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.queryByText('No containers found')).not.toBeInTheDocument();
  });

  it('filters by host pill', () => {
    query({ sources: [walker, mike], containers: [
      createMockContainer({ id: 'a', sourceId: 'walker', displayName: 'Walker app' }),
      createMockContainer({ id: 'b', sourceId: 'mike', displayName: 'Mike app' }),
    ] });
    renderWithQuery(<App />);
    fireEvent.click(within(screen.getByRole('group', { name: 'Show host' })).getByRole('button', { name: /^Mike/ }));
    expect(screen.queryByRole('region', { name: 'Walker' })).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Mike' })).toBeInTheDocument();
  });

  it('falls back to all hosts when the selected host disappears', () => {
    const containers = [
      createMockContainer({ id: 'a', sourceId: 'walker', displayName: 'Walker app' }),
      createMockContainer({ id: 'b', sourceId: 'mike', displayName: 'Mike app' }),
    ];
    query({ sources: [walker, mike], containers });
    const view = renderWithQuery(<App />);
    fireEvent.click(within(screen.getByRole('group', { name: 'Show host' })).getByRole('button', { name: /^Mike/ }));
    query({ sources: [walker], containers: [containers[0]] });
    view.rerender(<App />);
    expect(screen.getByRole('region', { name: 'Walker' })).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Show host' })).getByRole('button', { name: /^All/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('group', { name: 'Show host' }).tagName).toBe('FIELDSET');
  });

  it('filters by query and shows no-results with clear and include-stopped', () => {
    query({ sources: [walker], containers: [createMockContainer({ sourceId: 'walker', displayName: 'Sonarr' })] });
    renderWithQuery(<App />);
    fireEvent.change(filter(), { target: { value: 'plex' } });
    expect(screen.getByRole('heading', { name: 'No containers match “plex”' })).toBeInTheDocument();
    expect(screen.queryByText('No containers found')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Include stopped' }));
    expect(localStorage.getItem('porthole_showStopped')).toBe('true');
    expect(screen.queryByRole('button', { name: 'Include stopped' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Clear filter' }));
    expect(filter()).toHaveValue('');
    expect(screen.getByText('Sonarr')).toBeInTheDocument();
  });

  it('settings show every source and filters apply to the combined query', () => {
    query({ sources: [walker, { ...mike, status: 'DOWN' }], containers: [] });
    renderWithQuery(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    const rows = document.querySelectorAll('.host-row:not(.host-row--external)');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('Connected');
    expect(rows[1]).toHaveTextContent('Unreachable');
    fireEvent.click(screen.getByLabelText('Show stopped containers'));
    expect(localStorage.getItem('porthole_showStopped')).toBe('true');
  });

  it('filters stopped, endpoint-less and self containers locally', () => {
    query({ sources: [walker], containers: [
      createMockContainer({ sourceId: 'walker', name: 'web', displayName: 'Web' }),
      createMockContainer({ sourceId: 'walker', name: 'old', displayName: 'Old', state: 'exited' }),
      createMockContainer({ sourceId: 'walker', name: 'quiet', displayName: 'Quiet', endpoints: [], hasEndpoints: false, hasWebUi: false }),
    ] });
    renderWithQuery(<App />);
    expect(screen.getByText('Web')).toBeInTheDocument();
    expect(screen.queryByText('Old')).not.toBeInTheDocument();
    expect(screen.queryByText('Quiet')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    fireEvent.click(screen.getByLabelText('Show stopped containers'));
    expect(screen.getByText('Old')).toBeInTheDocument();
    expect(screen.queryByText('Quiet')).not.toBeInTheDocument();
  });

  it('re-groups the page when Stack is chosen in settings', () => {
    query({ sources: [walker, mike], containers: [
      createMockContainer({ id: 'a', sourceId: 'walker', project: 'shared', displayName: 'Walker app' }),
      createMockContainer({ id: 'b', sourceId: 'mike', project: 'shared', displayName: 'Mike app' }),
    ] });
    renderWithQuery(<App />);
    expect(screen.getByRole('region', { name: 'Walker' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    fireEvent.click(screen.getByRole('button', { name: 'Stack' }));
    expect(screen.getByRole('region', { name: 'shared' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Walker' })).not.toBeInTheDocument();
  });
});

describe('App links', () => {
  const router = { id: 'r'.repeat(32), name: 'Router', url: 'http://192.168.50.1/', stack: null, iconUrl: null };
  const app = createMockContainer({ id: 'a', sourceId: 'walker', displayName: 'Sonarr' });

  beforeEach(() => { setLinks([router]); });

  it('shows the External section beside hosts', () => {
    query({ sources: [walker], containers: [app] });
    renderWithQuery(<App />);
    expect(screen.getByRole('region', { name: 'External' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^External/ })).toBeInTheDocument();
  });

  it('shows the error without links when the dashboard read fails', () => {
    failing();
    renderWithQuery(<App />);
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to fetch containers');
    expect(screen.queryByRole('link', { name: /Router/ })).not.toBeInTheDocument();
  });

  it('keeps skeletons, without links, while containers load', () => {
    loading();
    renderWithQuery(<App />);
    expect(document.querySelectorAll('.skeleton-tile')).toHaveLength(6);
    expect(screen.queryByRole('link', { name: /Router/ })).not.toBeInTheDocument();
  });

  it('returns the filter to All when the last link goes', () => {
    query({ sources: [walker], containers: [app] });
    const { rerender } = renderWithQuery(<App />);
    fireEvent.click(screen.getByRole('button', { name: /^External/ }));
    expect(screen.queryByText('Sonarr')).not.toBeInTheDocument();

    setLinks([]);
    rerender(<App />);

    expect(screen.getByText('Sonarr')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^All/ })).toHaveAttribute('aria-pressed', 'true');
  });

  it('does not show no-results for a query matching only a link', () => {
    query({ sources: [walker], containers: [app] });
    renderWithQuery(<App />);
    fireEvent.change(filter(), { target: { value: 'router' } });
    expect(screen.getByRole('link', { name: /Router/ })).toBeInTheDocument();
    expect(screen.queryByText('Sonarr')).not.toBeInTheDocument();
    expect(document.querySelector('.no-results')).toBeNull();
  });

  it('shows no-results when nothing matches', () => {
    query({ sources: [walker], containers: [app] });
    renderWithQuery(<App />);
    fireEvent.change(filter(), { target: { value: 'zzz' } });
    expect(screen.queryByRole('link', { name: /Router/ })).not.toBeInTheDocument();
    expect(screen.getByText(/zzz/)).toBeInTheDocument();
  });
});

describe('App link settings', () => {
  const router = { id: 'r'.repeat(32), name: 'Router', url: 'http://192.168.50.1/', stack: 'base', iconUrl: null };
  const web = createMockContainer({ id: 'a', sourceId: 'walker', project: 'base', displayName: 'web' });
  const openSettings = () => fireEvent.click(screen.getByRole('button', { name: 'Router link settings' }));
  const settingsOpen = () => screen.queryByRole('textbox', { name: 'Name' });

  beforeEach(() => {
    setLinks([router]);
    query({ sources: [walker], containers: [web] });
  });

  it('stays open and keeps focus when a change moves the tile to another group', () => {
    const { rerender } = renderWithQuery(<App />);
    openSettings();
    settingsOpen().focus();

    setLinks([{ ...router, stack: 'home' }]);
    rerender(<App />);

    expect(settingsOpen()).toBeInTheDocument();
    expect(settingsOpen()).toHaveFocus();
  });

  it('closes without an error when the link is deleted elsewhere, focusing Settings', async () => {
    const { rerender } = renderWithQuery(<App />);
    openSettings();

    setLinks([]);
    rerender(<App />);

    expect(settingsOpen()).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Settings' })).toHaveFocus());
  });

  it('returns focus to the link menu on closing, even after the tile moved', async () => {
    const { rerender } = renderWithQuery(<App />);
    openSettings();
    setLinks([{ ...router, stack: 'home' }]);
    rerender(<App />);

    fireEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(settingsOpen()).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Router link settings' })).toHaveFocus());
  });

  it('opens Link settings with an alert when an icon failed while adding', async () => {
    const created = { ...router, id: 'c'.repeat(32), name: 'Created' };
    createLink.mockResolvedValue(created);
    selectEntryIcon.mockRejectedValue(new Error('down'));
    fetchIconLibrary.mockResolvedValue({ router: {} });
    setLinks([router, created]);
    renderWithQuery(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    fireEvent.click(screen.getByRole('button', { name: 'Add external link' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Created' } });
    fireEvent.change(screen.getByLabelText('URL'), { target: { value: 'http://c/' } });
    fireEvent.click(screen.getByRole('button', { name: 'Choose icon for this link' }));
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search icons' }), { target: { value: '' } });
    fireEvent.click(await screen.findByRole('button', { name: 'router' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Add link' }));

    expect(await screen.findByRole('alert')).toHaveTextContent("Link added, but its icon couldn't be saved.");
    expect(screen.getByRole('heading', { name: 'Created' })).toBeInTheDocument();
  });
});

