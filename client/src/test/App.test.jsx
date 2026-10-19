import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, within, fireEvent } from '@testing-library/react';
import App from '../App';
import { renderWithQuery, createMockContainer } from './testUtils';
import { useContainers } from '../hooks/useContainers';

vi.mock('../hooks/useContainers');

const walker = { id: 'walker', name: 'Walker', status: 'UP', error: null };
const mike = { id: 'mike', name: 'Mike', status: 'UP', error: null };
const query = data => useContainers.mockReturnValue({ data, isLoading: false, error: null });
const filter = () => screen.getByRole('searchbox', { name: 'Filter containers' });

describe('App', () => {
  beforeEach(() => { vi.clearAllMocks(); localStorage.clear(); });

  it('shows six horizontal skeletons and a status while loading', () => {
    useContainers.mockReturnValue({ isLoading: true });
    renderWithQuery(<App />);
    expect(document.querySelectorAll('.skeleton-tile')).toHaveLength(6);
    expect(screen.getByRole('status')).toHaveTextContent('Connecting…');
    expect(screen.getByRole('status').tagName).toBe('OUTPUT');
    expect(screen.getByText('Porthole')).toHaveClass('wordmark');
  });

  it('reports global fetch errors without identifying a Docker host as down', () => {
    useContainers.mockReturnValue({ error: { response: { status: 502 } }, isLoading: false });
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
    expect(screen.queryByText('Porthole', { selector: '.tile-name, .tile-title, h3, span' })).toBeNull();
    expect(screen.getByRole('button', { name: /Walker/ })).toHaveTextContent('1');
  });

  it('shows the self container by default', () => {
    query({ sources: [walker], containers: [
      createMockContainer({ id: 'b', sourceId: 'walker', displayName: 'Porthole', self: true }),
    ] });
    renderWithQuery(<App />);
    expect(screen.getAllByText('Porthole').length).toBeGreaterThan(1);
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

  it('keeps last-known containers of a host that went down', () => {
    query({ sources: [walker, mike], containers: [createMockContainer({ id: 'm1', sourceId: 'mike', displayName: 'Mike app' })] });
    const view = renderWithQuery(<App />);
    query({ sources: [walker, { ...mike, status: 'DOWN', error: 'gone' }], containers: [] });
    view.rerender(<App />);
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
    expect(useContainers).toHaveBeenLastCalledWith({ showAll: false, showStopped: true });
    expect(screen.queryByRole('button', { name: 'Include stopped' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Clear filter' }));
    expect(filter()).toHaveValue('');
    expect(screen.getByText('Sonarr')).toBeInTheDocument();
  });

  it('settings show every source and filters apply to the combined query', () => {
    query({ sources: [walker, { ...mike, status: 'DOWN' }], containers: [] });
    renderWithQuery(<App />);
    fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
    const rows = document.querySelectorAll('.host-row');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('Connected');
    expect(rows[1]).toHaveTextContent('Unreachable');
    fireEvent.click(screen.getByLabelText('Show stopped containers'));
    expect(useContainers).toHaveBeenLastCalledWith({ showAll: false, showStopped: true });
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
