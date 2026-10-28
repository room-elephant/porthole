import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useEndpointSelection } from '../../hooks/useEndpointSelection';
import { selectContainerEndpoint } from '../../api';
import { createMockContainer } from '../testUtils';

vi.mock('../../api', () => ({ selectContainerEndpoint: vi.fn() }));
const port = { id: 'published-port:80', source: 'published-port', url: 'http://192.168.50.6/' };
const root = { id: 'traefik:root', source: 'traefik', url: 'https://app.example.com/' };
const none = { id: 'none', source: 'none' };
const key = ['containers', { showAll: true, showStopped: false }];
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const container = (selection = null, overrides = {}) => createMockContainer({ name: 'app', endpoints: [port, root], preferences: { selectedEndpoint: selection && selection.source !== 'custom' ? { id: selection.id, source: selection.source } : selection }, ...overrides });
const inventory = containers => ({ sources: [{ id: 'local', name: 'Local', status: 'UP', error: null }], containers });
const setup = (initial = container()) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(key, inventory([initial]));
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  const useSelection = () => {
    const { data } = useQuery({ queryKey: key, queryFn: () => inventory([initial]) });
    return useEndpointSelection(data.containers[0]);
  };
  return { client, wrapper, useSelection };
};

beforeEach(() => { vi.clearAllMocks(); });
describe('shared endpoint selection', () => {
  it.each([port, none, { id: 'custom', source: 'custom', url: 'https://custom.example.com/' }])('reads inventory preference $id without browser storage', selection => {
    const { wrapper, useSelection } = setup(container(selection));
    const { result } = renderHook(useSelection, { wrapper });
    expect(result.current.selectedEndpoint).toEqual(selection.id === 'none' ? null : selection.id === port.id ? port : selection);
    expect(result.current.selectedEndpointId).toBe(selection.id);
    expect(localStorage.getItem).not.toHaveBeenCalled();
    expect(localStorage.setItem).not.toHaveBeenCalled();
  });

  it.each([null, { id: 'removed', source: 'traefik' }])('uses automatic fallback without saving when preference is %s', selection => {
    const { wrapper, useSelection } = setup(container(selection));
    expect(renderHook(useSelection, { wrapper }).result.current.selectedEndpoint).toEqual(root);
    expect(selectContainerEndpoint).not.toHaveBeenCalled();
  });

  it('applies confirmed replacements across every inventory without adding absent containers or changing another source', async () => {
    const { client, wrapper, useSelection } = setup(container({ id: 'custom', source: 'custom', url: 'https://old/' }));
    const alternate = ['containers', { showAll: false, showStopped: true }];
    client.setQueryData(alternate, inventory([container(null, { id: 'recreated' }), container(null, { sourceId: 'other' })]));
    client.setQueryData(['containers', 'empty'], inventory([]));
    client.getQueryCache().build(client, { queryKey: ['containers', 'not-loaded'] });
    selectContainerEndpoint.mockResolvedValue({ sourceId: 'local', containerName: 'app', preferences: { selectedEndpoint: { id: port.id, source: port.source } } });
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint({ id: port.id, source: port.source }));
    await waitFor(() => expect(result.current.selectedEndpoint).toEqual(port));
    expect(selectContainerEndpoint).toHaveBeenCalledWith({ sourceId: 'local', containerName: 'app', selectedEndpoint: { id: port.id, source: port.source } });
    expect(client.getQueryData(alternate).containers[0].preferences).toEqual({ selectedEndpoint: { id: port.id, source: port.source } });
    expect(client.getQueryData(alternate).containers[1].preferences.selectedEndpoint).toBeNull();
    expect(client.getQueryData(['containers', 'empty']).containers).toEqual([]);
    expect(client.getQueryData(['containers', 'not-loaded'])).toBeUndefined();
    expect(localStorage.setItem).not.toHaveBeenCalled();
  });

  it('keeps the confirmed selection and exposes errors when saving fails', async () => {
    const { wrapper, useSelection } = setup(container(port));
    const request = deferred();
    selectContainerEndpoint.mockReturnValue(request.promise);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(result.current.isSaving).toBe(true));
    expect(result.current.selectedEndpoint).toEqual(port);
    await act(async () => request.resolve(Promise.reject(new Error('Disk is full'))));
    await waitFor(() => expect(result.current.saveError?.message).toBe('Disk is full'));
    expect(result.current.selectedEndpoint).toEqual(port);
    expect(result.current.isSaving).toBe(false);
  });

  it('cancels inventory requests started before and during saving so old responses cannot undo confirmation', async () => {
    const { client, wrapper, useSelection } = setup();
    const old = deferred();
    const during = deferred();
    const save = deferred();
    const signals = [];
    const fetchOld = (request, queryKey) => client.fetchQuery({ queryKey, staleTime: 0, queryFn: ({ signal }) => { signals.push(signal); return request.promise; } }).catch(() => {});
    const beforeRequest = fetchOld(old, key);
    selectContainerEndpoint.mockReturnValue(save.promise);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(1));
    const duringRequest = fetchOld(during, key);
    await act(async () => save.resolve({ sourceId: 'local', containerName: 'app', preferences: { selectedEndpoint: none } }));
    await waitFor(() => expect(result.current.selectedEndpointId).toBe('none'));
    await act(async () => { old.resolve(inventory([container()])); during.resolve(inventory([container()])); await Promise.all([beforeRequest, duringRequest]); });
    expect(signals.every(signal => signal.aborted)).toBe(true);
    expect(client.getQueryData(key).containers[0].preferences.selectedEndpoint).toEqual(none);
    expect(result.current.selectedEndpoint).toBeNull();
  });

  it('blocks overlapping saves across hook instances while different containers save independently', async () => {
    const { wrapper } = setup();
    const save = deferred();
    selectContainerEndpoint.mockReturnValue(save.promise);
    const { result } = renderHook(() => ({ first: useEndpointSelection(container()), second: useEndpointSelection(container()), other: useEndpointSelection(container(null, { sourceId: 'remote' })) }), { wrapper });
    act(() => { result.current.first.selectEndpoint(none); result.current.second.selectEndpoint(port); result.current.other.selectEndpoint(null); });
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(2));
    expect(result.current.second.isSaving).toBe(true);
    expect(selectContainerEndpoint.mock.calls.map(([request]) => request.sourceId)).toEqual(['local', 'remote']);
    await act(async () => save.resolve({ sourceId: 'local', containerName: 'app', preferences: { selectedEndpoint: none } }));
    await waitFor(() => expect(result.current.first.isSaving).toBe(false));
  });
});
