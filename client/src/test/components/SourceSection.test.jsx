import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, fireEvent, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import SourceSection, { OfflineBanner } from '../../components/SourceSection';
import { renderWithQuery, createMockContainer, renderInventory, publishedEndpoint, inventoryWithSelection } from '../testUtils';
import { selectContainerEndpoint } from '../../api';
vi.mock('../../api', () => ({ selectContainerEndpoint: vi.fn() }));
import { buildSections } from '../../utils/containers';
import { GROUP_BY } from '../../constants';

const source = { id: 'walker', name: 'Walker', status: 'UP', error: null };
const mike = { id: 'mike', name: 'Mike', status: 'UP', error: null };
const mk = (overrides) => createMockContainer({ sourceId: 'walker', ...overrides });
let savedSelections;

const renderSection = (containers, { sources = [source], groupBy = GROUP_BY.HOST_STACK, showAll = true, showStopped = true, index = 0, queryFn } = {}) => {
  const sourcesById = Object.fromEntries(sources.map(s => [s.id, s]));
  return renderInventory(inventory => (
    <SourceSection section={buildSections({ ...inventory, groupBy })[index]} sourcesById={sourcesById} showAll={showAll} showStopped={showStopped} />
  ), { sources, containers }, queryFn ?? (() => savedSelections.reduce(inventoryWithSelection, { sources, containers })));
};

describe('source sections', () => {
 beforeEach(() => {localStorage.clear();vi.clearAllMocks();
  savedSelections = [];
  selectContainerEndpoint.mockImplementation(async request => { savedSelections.push(request); return { sourceId: request.sourceId, containerName: request.containerName, preferences: { selectedEndpoint: request.selectedEndpoint } }; });
 });

 it('shows host heading meta with container and stack counts', () => {
  renderSection([mk({ id: '1' }), mk({ id: '2', project: 'smh' }), mk({ id: '3', project: 'media' })]);
  const region = screen.getByRole('region', { name: 'Walker' });
  expect(within(region).getByRole('heading', { level: 2, name: 'Walker' })).toBeInTheDocument();
  expect(region.querySelector('.section-meta')).toHaveTextContent('3 containers · 2 stacks · Connected');
 });

 it('omits the stack count when there are no stacks and singularizes', () => {
  renderSection([mk()]);
  expect(screen.getByRole('region', { name: 'Walker' }).querySelector('.section-meta').textContent).toBe('1 container · Connected');
 });

 it('labels stacks in small caps with counts and puts standalone first', () => {
  renderSection([mk({ id: 'a', project: 'smh', displayName: 'A' }), mk({ id: 'b', project: 'smh', displayName: 'B' }), mk({ id: 's', displayName: 'Solo' })]);
  const label = document.querySelector('.stack-label');
  expect(label).toHaveTextContent('smh');
  expect(label.querySelector('.stack-count')).toHaveTextContent('2');
  expect(document.querySelectorAll('.stack-label')).toHaveLength(1);
  const groups = document.querySelectorAll('.stack-group');
  expect(groups[0]).toHaveTextContent('Solo');
  expect(groups[1]).toHaveTextContent('smh');
 });

 it('renders containers without a web UI as chips after the tiles', () => {
  renderSection([mk({ id: 'h', displayName: 'Headless', hasEndpoints: false, endpoints: [] }), mk({ id: 'w', displayName: 'Web' })]);
  const chip = screen.getByRole('button', { name: 'Headless settings' });
  expect(chip).toHaveClass('chip');
  expect(screen.getByText('No web UI')).toBeInTheDocument();
  const row = document.querySelector('.chip-row');
  expect(row).toContainElement(chip);
  expect(document.querySelector('.tile-grid')).not.toContainElement(chip);
  expect(document.querySelector('.tile-grid .tile-name')).toHaveTextContent('Web');
 });

 it('keeps the dialog open and the tile in place while choosing No web UI, then moves it to a chip on close', async () => {
  renderSection([mk()]);
  fireEvent.click(screen.getAllByRole('button', { name: 'Test Container settings' })[0]);
  fireEvent.click(screen.getByRole('radio', { name: /No (endpoint|web UI)/ }));
  await waitFor(() => expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked());
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(document.querySelector('.tile-grid .tile')).not.toBeNull();
  expect(document.querySelector('.chip')).toBeNull();
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  const chip = screen.getByRole('button', { name: 'Test Container settings' });
  expect(chip).toHaveClass('chip');
  expect(document.activeElement).toBe(chip);
 });

 it('keeps the dialog open while a chip gains an endpoint, then moves it to a tile and focuses its menu', async () => {
  renderSection([mk({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false })]);
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  fireEvent.click(screen.getByRole('radio', { name: /localhost:8080/ }));
  await waitFor(() => expect(screen.getByRole('radio', { name: /localhost:8080/ })).toBeChecked());
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(document.querySelector('.chip')).not.toBeNull();
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(document.querySelector('.chip')).toBeNull();
  expect(screen.getByRole('link', { name: /Test Container/ })).toBeInTheDocument();
  expect(document.activeElement).toHaveClass('tile-menu');
 });

 it('restores focus using the latest confirmed cache when closed before the inventory rerenders', async () => {
  const { queryClient } = renderSection([mk()]);
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  act(() => {
   queryClient.setQueriesData({ queryKey: ['containers'] }, inventory => ({ ...inventory, containers: [mk({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false })] }));
   fireEvent.keyDown(document, { key: 'Escape' });
  });
  await waitFor(() => expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveClass('chip'));
  expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveFocus();
 });

 it('uses the active confirmation instead of stale inactive variants for a closed-dialog focus handoff', async () => {
  const initial = { sources: [source], containers: [mk()] };
  const confirmed = { sources: [source], containers: [mk({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false })] };
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  queryClient.setQueryData(['containers', { showAll: false, showStopped: false }], initial);
  const activeKey = ['containers', { showAll: true, showStopped: true }];
  let completeRefresh;
  const read = vi.fn(() => new Promise(resolve => { completeRefresh = resolve; }));
  function View() {
   const { data } = useQuery({ queryKey: activeKey, queryFn: read, initialData: initial });
   return <SourceSection section={buildSections({ ...data, groupBy: GROUP_BY.HOST_STACK })[0]} sourcesById={{ walker: source }} showAll showStopped />;
  }
  render(<QueryClientProvider client={queryClient}><View /></QueryClientProvider>);
  const opener = screen.getByRole('button', { name: 'Test Container settings' });
  opener.focus();
  fireEvent.click(opener);
  fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  fireEvent.keyDown(document, { key: 'Escape' });
  await act(async () => completeRefresh(confirmed));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveClass('chip'));
  expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveFocus();
 });

 it.each([
  ['tile-to-chip', 'PUT'], ['tile-to-chip', 'GET'],
  ['chip-to-custom-tile', 'PUT'], ['chip-to-custom-tile', 'GET'],
 ])('restores focus for %s when closed during %s', async (direction, closeDuring) => {
  let confirm;
  let completeRefresh;
  const refresh = new Promise(resolve => { completeRefresh = resolve; });
  const read = vi.fn(() => refresh);
  selectContainerEndpoint.mockImplementation(({ sourceId, containerName, selectedEndpoint }) => new Promise(resolve => {
   confirm = () => { savedSelections.push({ sourceId, containerName, selectedEndpoint }); resolve({ sourceId, containerName, preferences: { selectedEndpoint } }); };
  }));
  const startsAsChip = direction === 'chip-to-custom-tile';
  const initial = mk(startsAsChip ? { endpoints: [], hasEndpoints: false } : {});
  renderSection([initial], { queryFn: read });
  const opener = screen.getByRole('button', { name: 'Test Container settings' });
  opener.focus();
  fireEvent.click(opener);
  if (startsAsChip) {
   fireEvent.click(screen.getByRole('radio', { name: 'Custom URL' }));
   fireEvent.change(screen.getByRole('textbox'), { target: { value: 'https://custom.example.com/' } });
   fireEvent.click(screen.getByRole('button', { name: 'Save custom URL' }));
  } else {
   fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  }
  await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(1));
  if (closeDuring === 'GET') {
   await act(async () => confirm());
   await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
   expect(screen.getByRole('group', { name: 'Tile opens' })).toBeDisabled();
  }
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(opener).toHaveFocus();
  if (closeDuring === 'PUT') await act(async () => confirm());
  await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  expect(opener).toHaveFocus();
  expect(opener).toHaveClass(startsAsChip ? 'chip' : 'tile-menu');
  await act(async () => completeRefresh(savedSelections.reduce(inventoryWithSelection, { sources: [source], containers: [initial] })));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveClass(startsAsChip ? 'tile-menu' : 'chip'));
  expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveFocus();
 });

 it('does not steal focus when the user moves elsewhere during the confirmation GET', async () => {
  let completeRefresh;
  const read = vi.fn(() => new Promise(resolve => { completeRefresh = resolve; }));
  const containers = [mk(), mk({ id: 'other', name: 'other', displayName: 'Other' })];
  renderSection(containers, { queryFn: read });
  const opener = screen.getByRole('button', { name: 'Test Container settings' });
  opener.focus();
  fireEvent.click(opener);
  fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  fireEvent.keyDown(document, { key: 'Escape' });
  const other = screen.getByRole('button', { name: 'Other settings' });
  other.focus();
  await act(async () => completeRefresh(savedSelections.reduce(inventoryWithSelection, { sources: [source], containers })));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveClass('chip'));
  expect(other).toHaveFocus();
 });

 it('releases a closed-dialog focus handoff when GET fails and does not move focus on later recovery', async () => {
  let failRefresh;
  const read = vi.fn().mockImplementationOnce(() => new Promise((resolve, reject) => { failRefresh = reject; }));
  const containers = [mk(), mk({ id: 'other', name: 'other', displayName: 'Other' })];
  const { queryClient } = renderSection(containers, { queryFn: read });
  const opener = screen.getByRole('button', { name: 'Test Container settings' });
  opener.focus();
  fireEvent.click(opener);
  fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  fireEvent.keyDown(document, { key: 'Escape' });
  await act(async () => failRefresh(new Error('Offline')));
  await waitFor(() => expect(queryClient.isMutating()).toBe(0));
  expect(opener).toHaveFocus();
  const other = screen.getByRole('button', { name: 'Other settings' });
  other.focus();
  read.mockResolvedValue(savedSelections.reduce(inventoryWithSelection, { sources: [source], containers }));
  await act(async () => queryClient.refetchQueries({ queryKey: ['containers'] }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveClass('chip'));
  expect(other).toHaveFocus();
  expect(selectContainerEndpoint).toHaveBeenCalledTimes(1);
 });

 it('releases a pending handoff when the sole open container disappears', async () => {
  let confirm;
  selectContainerEndpoint.mockImplementation(({ sourceId, containerName, selectedEndpoint }) => new Promise(resolve => {
   confirm = () => { savedSelections.push({ sourceId, containerName, selectedEndpoint }); resolve({ sourceId, containerName, preferences: { selectedEndpoint } }); };
  }));
  const { queryClient } = renderSection([mk()]);
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(1));
  await act(async () => queryClient.setQueriesData({ queryKey: ['containers'] }, inventory => ({ ...inventory, containers: [] })));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  await act(async () => confirm());
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Nothing running on Walker yet' })).toBeInTheDocument();
 });

 it('releases the freeze when the open container disappears and later returns', () => {
  const other = mk({ id: 'other', name: 'other', displayName: 'Other' });
  const sources = [source];
  const sourcesById = { walker: source };
  const render = containers => (
   <SourceSection section={buildSections({ sources, containers, groupBy: GROUP_BY.HOST_STACK })[0]} sourcesById={sourcesById} showAll showStopped />
  );
  const view = renderWithQuery(render([mk(), other]));
  fireEvent.click(screen.getAllByRole('button', { name: 'Test Container settings' })[0]);
  view.rerender(render([other]));
  view.rerender(render([mk({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false }), other]));
  expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveClass('chip');
  expect(document.querySelectorAll('.chip')).toHaveLength(1);
 });

 it('shows an offline banner and last-known containers as offline tiles', () => {
  const down = { ...source, status: 'DOWN', error: 'Docker is not reachable' };
  renderSection([mk()], { sources: [down] });
  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent("Can't reach Walker");
  expect(alert).toHaveTextContent('Docker is not reachable');
  expect(alert).toHaveTextContent('Retrying automatically.');
  expect(screen.getByRole('region', { name: 'Walker' })).toHaveTextContent('Unreachable');
  expect(screen.getByText('Offline')).toBeInTheDocument();
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  expect(screen.getByText('Test Container')).toBeInTheDocument();
 });

 it('shows the banner for an ERROR host with no containers and no empty state', () => {
  renderSection([], { sources: [{ ...source, status: 'ERROR', error: 'Could not retrieve containers' }] });
  expect(screen.getByRole('alert')).toHaveTextContent('Could not retrieve containers');
  expect(screen.queryByText('No containers found')).not.toBeInTheDocument();
 });

 it('shows checking text while a host is being checked', () => {
  renderSection([], { sources: [{ ...source, status: 'CHECKING' }] });
  expect(screen.getByText('Checking…')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
 });

 it('shows offline chips for headless containers on an unreachable host', () => {
  renderSection([mk({ hasEndpoints: false, endpoints: [] })], { sources: [{ ...source, status: 'DOWN', error: 'x' }] });
  expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveTextContent('Test Container · Offline');
 });

 it('exports a reusable OfflineBanner', () => {
  renderWithQuery(<OfflineBanner source={{ ...mike, status: 'DOWN', error: 'timeout' }} />);
  const alert = screen.getByRole('alert');
  expect(alert).toHaveTextContent("Can't reach Mike");
  expect(alert).toHaveTextContent('timeout');
  expect([...alert.children].map(el => getComputedStyle(el).display)).not.toContain('inline');
  expect(alert.querySelectorAll('p')).toHaveLength(3);
 });

 it('shows the full illustration, host heading and docs link in the empty state for a connected host', () => {
  renderSection([]);
  expect(screen.getByRole('heading', { name: 'Nothing running on Walker yet' })).toBeInTheDocument();
  const img = document.querySelector('.empty-state img');
  expect(img).toHaveAttribute('src', 'porthole-full.webp');
  expect(img).toHaveAttribute('width', '112');
  const link = screen.getByRole('link', { name: 'Add more Docker hosts' });
  expect(link).toHaveAttribute('href', 'https://github.com/room-elephant/porthole/blob/main/docs/CONFIGURATION.md');
  expect(link).toHaveAttribute('target', '_blank');
  expect(link).toHaveAttribute('rel', 'noopener');
 });

 it('describes stack sections with their hosts', () => {
  renderSection([mk({ id: '1', project: 'smh' }), createMockContainer({ id: '2', sourceId: 'mike', project: 'smh' })], { sources: [source, mike], groupBy: GROUP_BY.STACK });
  expect(screen.getByRole('region', { name: 'smh' }).querySelector('.section-meta')).toHaveTextContent('2 containers · Walker, Mike');
 });

 it('tags tiles with their host in stack grouping', () => {
  renderSection([mk({ id: '1', project: 'smh' })], { groupBy: GROUP_BY.STACK });
  expect(screen.getByText('Walker')).toHaveClass('tile-tag');
 });

 it('tags chips with their host in stack grouping', () => {
  renderSection([mk({ id: '1', project: 'smh', hasEndpoints: false, endpoints: [] })], { groupBy: GROUP_BY.STACK });
  expect(screen.getByRole('button', { name: 'Test Container settings' })).toHaveTextContent('Test Container · Walker');
 });

 it('does not throw when storage is blocked', () => {
  const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
  try {
   renderSection([mk()]);
   expect(screen.getByRole('link', { name: /Test Container/ })).toBeInTheDocument();
  } finally { spy.mockRestore(); }
 });

 it('separates a literal null project and a prototype-like project from standalone containers', () => {
  const containers=[mk({id:'1',project:null}),mk({id:'2',project:'null'}),mk({id:'3',project:'__proto__'})];
  renderSection(containers);
  expect(screen.getByText('null', { selector: '.stack-label span' })).toBeInTheDocument();
  expect(screen.getByText('__proto__', { selector: '.stack-label span' })).toBeInTheDocument();
  expect(within(screen.getByRole('region',{name:'Walker'})).getAllByRole('link')).toHaveLength(3);
 });

 it.each([
  [false, false, 'No running containers with endpoints.', 'Show stopped containers', 'Show containers without a web UI'],
  [true, false, 'No running containers found.', 'Show stopped containers', null],
  [false, true, 'No containers with endpoints found.', 'Show containers without a web UI', null],
  [true, true, 'Start a container and it shows up here automatically', 'Traefik URL or published port.', null],
 ])('describes an empty inventory with showAll=%s and showStopped=%s', (showAll, showStopped, description, suggestion, alternative) => {
  renderSection([], { showAll, showStopped });
  const message = screen.getByText(text => text.startsWith(description));
  expect(message).toHaveTextContent(suggestion);
  if (alternative) expect(message).toHaveTextContent(alternative);
 });

 it('renders a custom-only container as a linked tile', () => {
  renderSection([mk({ endpoints: [{ id: 'custom', source: 'custom', url: 'https://custom.example.com/', selected: true }], hasEndpoints: true })]);
  expect(screen.getByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'https://custom.example.com/');
  expect(document.querySelector('.chip')).toBeNull();
 });

 it('saves a custom URL on a chip without closing its dialog, then moves to a linked tile with focus', async () => {
  renderSection([mk({ endpoints: [], hasEndpoints: false })]);
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  fireEvent.click(screen.getByRole('radio', { name: 'Custom URL' }));
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'https://custom.example.com/' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save custom URL' }));
  await waitFor(() => expect(screen.queryByText('Saving selection…')).not.toBeInTheDocument());
  expect(selectContainerEndpoint).toHaveBeenCalledWith({ sourceId: 'walker', containerName: 'test-container', selectedEndpoint: { id: 'custom', source: 'custom', url: 'https://custom.example.com/' } });
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(document.querySelector('.chip')).not.toBeNull();
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(await screen.findByRole('link', { name: /Test Container/ })).toHaveAttribute('href', 'https://custom.example.com/');
  expect(document.activeElement).toHaveClass('tile-menu');
 });

 it('retains rendered inventory after a saved selection fails to refresh and clears its feedback after GET recovery', async () => {
  const read = vi.fn().mockRejectedValueOnce(new Error('Offline'));
  const initial = mk();
  const { queryClient } = renderSection([initial], { queryFn: read });
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Selection saved, but containers could not be refreshed. Reload to see the change.');
  expect(screen.queryByText(/Could not save selection/)).not.toBeInTheDocument();
  expect(screen.getByRole('radio', { name: /localhost:8080/ })).toBeChecked();
  expect(screen.getByRole('group', { name: 'Tile opens' })).not.toBeDisabled();
  expect(document.querySelector('.tile-main')).toHaveAttribute('href', 'http://localhost:8080/');
  read.mockResolvedValue({ sources: [source], containers: [mk({ endpoints: [publishedEndpoint(8080, false)], hasWebUi: false })] });
  await act(async () => queryClient.refetchQueries({ queryKey: ['containers'] }));
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  expect(screen.getByRole('radio', { name: 'No web UI' })).toBeChecked();
  expect(selectContainerEndpoint).toHaveBeenCalledTimes(1);
 });

 it('keeps server ambiguity as an unlinked tile and explicit none as a chip', () => {
  renderSection([
   mk({ id: 'ambiguous', name: 'ambiguous', displayName: 'Ambiguous', endpoints: [publishedEndpoint(80, false), publishedEndpoint(443, false)] }),
   mk({ id: 'none', name: 'none', displayName: 'None', endpoints: [publishedEndpoint(8080, false)], hasWebUi: false }),
   mk({ id: 'selected', name: 'selected', displayName: 'Selected' }),
  ]);
  expect(document.querySelector('.tile-grid')).toHaveTextContent('Ambiguous');
  expect(screen.getByText('Choose a URL')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'None settings' })).toHaveClass('chip');
  expect(screen.getByRole('link', { name: /Selected/ })).toHaveAttribute('href', 'http://localhost:8080/');
 });

 it('shows failed saves without closing the dialog or changing the confirmed tile', async () => {
  selectContainerEndpoint.mockRejectedValue(new Error('Read-only filesystem'));
  renderSection([mk()]);
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  fireEvent.click(screen.getByRole('radio', { name: 'No web UI' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Read-only filesystem');
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: /localhost:8080/ })).toBeChecked();
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.getByRole('link', { name: /Test Container/ })).toBeInTheDocument();
 });
});
