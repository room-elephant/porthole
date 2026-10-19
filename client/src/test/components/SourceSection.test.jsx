import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, within, fireEvent } from '@testing-library/react';
import SourceSection, { OfflineBanner } from '../../components/SourceSection';
import { renderWithQuery, createMockContainer } from '../testUtils';
import { renderHook, act } from '@testing-library/react';
import { useEndpointSelection } from '../../hooks/useEndpointSelection';
import { buildSections } from '../../utils/containers';
import { GROUP_BY } from '../../constants';

const source = { id: 'walker', name: 'Walker', status: 'UP', error: null };
const mike = { id: 'mike', name: 'Mike', status: 'UP', error: null };
const mk = (overrides) => createMockContainer({ sourceId: 'walker', ...overrides });

const renderSection = (containers, { sources = [source], groupBy = GROUP_BY.HOST_STACK, showAll = true, showStopped = true, index = 0 } = {}) => {
  const sections = buildSections({ sources, containers, groupBy });
  const sourcesById = Object.fromEntries(sources.map(s => [s.id, s]));
  return renderWithQuery(
    <SourceSection section={sections[index]} sourcesById={sourcesById} showAll={showAll} showStopped={showStopped} />
  );
};

describe('source sections', () => {
 beforeEach(() => {localStorage.clear();vi.clearAllMocks();});

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

 it('keeps the dialog open and the tile in place while choosing No web UI, then moves it to a chip on close', () => {
  renderSection([mk()]);
  fireEvent.click(screen.getAllByRole('button', { name: 'Test Container settings' })[0]);
  fireEvent.click(screen.getByRole('radio', { name: /No (endpoint|web UI)/ }));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(document.querySelector('.tile-grid .tile')).not.toBeNull();
  expect(document.querySelector('.chip')).toBeNull();
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
  const chip = screen.getByRole('button', { name: 'Test Container settings' });
  expect(chip).toHaveClass('chip');
  expect(document.activeElement).toBe(chip);
 });

 it('keeps the dialog open while a chip gains an endpoint, then moves it to a tile and focuses its menu', () => {
  localStorage.setItem('endpoint_pref_v2_' + JSON.stringify(['walker', 'test-container']), '"none"');
  renderSection([mk()]);
  fireEvent.click(screen.getByRole('button', { name: 'Test Container settings' }));
  fireEvent.click(screen.getByRole('radio', { name: /localhost:8080/ }));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(document.querySelector('.chip')).not.toBeNull();
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(document.querySelector('.chip')).toBeNull();
  expect(screen.getByRole('link', { name: /Test Container/ })).toBeInTheDocument();
  expect(document.activeElement).toHaveClass('tile-menu');
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
  localStorage.setItem('endpoint_pref_v2_' + JSON.stringify(['walker', 'test-container']), '"none"');
  view.rerender(render([mk(), other]));
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
  const link = screen.getByRole('link', { name: 'Add more Docker hosts ↗' });
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

 it('keeps endpoint preferences separate for matching container names on different sources', () => {
  const endpoints=[{id:'published-port:80',source:'published-port',url:'http://example.com/'},{id:'traefik:test',source:'traefik',url:'https://example.com/'}];
  const {result}=renderHook(() => ({walker:useEndpointSelection('walker','same',endpoints),mike:useEndpointSelection('mike','same',endpoints)}));
  act(() => result.current.walker.selectEndpoint('none'));
  expect(result.current.walker.selectedEndpoint).toBeNull();
  expect(result.current.mike.selectedEndpoint.id).toBe('traefik:test');
 });
});
