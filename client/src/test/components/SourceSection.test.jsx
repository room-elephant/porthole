import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, within } from '@testing-library/react';
import SourceSection from '../../components/SourceSection';
import { renderWithQuery, createMockContainer } from '../testUtils';
import { renderHook, act } from '@testing-library/react';
import { useVersionPreference } from '../../hooks/useVersionPreference';
import { useEndpointSelection } from '../../hooks/useEndpointSelection';
const source = { id:'walker',name:'Walker',status:'UP',error:null };
describe('source sections', () => {
 beforeEach(() => {localStorage.clear();vi.clearAllMocks();});
 it('keeps an unavailable host visible without stale containers', () => {
  renderWithQuery(<SourceSection source={{...source,status:'DOWN',error:'Docker is not reachable'}} containers={[createMockContainer()]} showAll={false} showStopped={false} allowLegacyMigration={false}/>);
  expect(screen.getByRole('region',{name:'Walker'})).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Docker unavailable');
  expect(screen.queryByText('Test Container')).not.toBeInTheDocument();
 });
 it('separates a literal null project and a prototype-like project from standalone containers', () => {
  const containers=[createMockContainer({id:'1',project:null}),createMockContainer({id:'2',project:'null'}),createMockContainer({id:'3',project:'__proto__'})];
  renderWithQuery(<SourceSection source={source} containers={containers} showAll={true} showStopped={true} allowLegacyMigration={true}/>);
  expect(screen.getByRole('heading',{name:'null'})).toBeInTheDocument();
  expect(screen.getByRole('heading',{name:'__proto__'})).toBeInTheDocument();
  expect(within(screen.getByRole('region',{name:'Walker'})).getAllByTitle('Container Settings')).toHaveLength(3);
 });
 it('keeps endpoint preferences separate for matching container names on different sources', () => {
  const endpoints=[{id:'published-port:80',source:'published-port',url:'http://example.com/'},{id:'traefik:test',source:'traefik',url:'https://example.com/'}];
  const {result}=renderHook(() => ({walker:useEndpointSelection('walker','same',endpoints),mike:useEndpointSelection('mike','same',endpoints)}));
  act(() => result.current.walker.selectEndpoint('none'));
  expect(result.current.walker.selectedEndpoint).toBeNull();
  expect(result.current.mike.selectedEndpoint.id).toBe('traefik:test');
 });
 it('does not import a legacy preference when multiple hosts exist', () => {
  localStorage.setItem('endpoint_pref_same','"none"');
  const endpoints=[{id:'published-port:80',source:'published-port',url:'http://example.com/'}];
  const {result}=renderHook(() => useEndpointSelection('mike','same',endpoints,{migrateLegacy:false}));
  expect(result.current.selectedEndpoint).toEqual(endpoints[0]);
 });
 it('migrates single-source explicit endpoint and disabled update preferences without changing legacy keys', () => {
  localStorage.setItem('endpoint_pref_same','"none"');localStorage.setItem('version_check_same','false');
  const endpoints=[{id:'published-port:80',source:'published-port',url:'http://example.com/'}];
  const {result}=renderHook(() => ({selection:useEndpointSelection('walker','same',endpoints,{migrateLegacy:true}),version:useVersionPreference('walker','same',true)}));
  expect(result.current.selection.selectedEndpoint).toBeNull();expect(result.current.version[0]).toBe(false);
  expect(localStorage.getItem('endpoint_pref_same')).toBe('"none"');expect(localStorage.getItem('version_check_same')).toBe('false');
 });
 it('does not import an old update preference for multiple sources', () => {
  localStorage.setItem('version_check_same','false');
  const {result}=renderHook(() => useVersionPreference('mike','same',false));
  expect(result.current[0]).toBe(true);
 });
});
