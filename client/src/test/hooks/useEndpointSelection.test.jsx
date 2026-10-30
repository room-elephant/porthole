import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider, useQuery, onlineManager } from '@tanstack/react-query';
import { useEndpointSelection } from '../../hooks/useEndpointSelection';
import { selectContainerEndpoint } from '../../api';
import { createMockContainer } from '../testUtils';

vi.mock('../../api', () => ({ selectContainerEndpoint: vi.fn() }));
const port = { id: 'published-port:80', source: 'published-port', url: 'http://192.168.50.6/', selected: false };
const root = { id: 'traefik:root', source: 'traefik', url: 'https://app.example.com/', selected: true };
const none = { id: 'none', source: 'none' };
const custom = { id: 'custom', source: 'custom', url: 'https://custom.example.com/', selected: true };
const key = ['containers', { showAll: true, showStopped: false }];
const alternate = ['containers', { showAll: false, showStopped: true }];
const deferred = () => { let resolve; let reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const container = (selection = null, overrides = {}) => createMockContainer({ name: 'app', endpoints: [port, root], hasWebUi: selection?.source !== 'none', ...overrides });
const inventory = containers => ({ sources: [{ id: 'local', name: 'Local', status: 'UP', error: null }], containers });
const noUi = () => container(none, { endpoints: [port, { ...root, selected: false }] });
const confirmation = { sourceId: 'local', containerName: 'app', preferences: { selectedEndpoint: none } };
const setup = (initial = container(), fetch = vi.fn().mockResolvedValue(inventory([initial]))) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(key, inventory([initial]));
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  const useSelection = () => {
    const { data } = useQuery({ queryKey: key, queryFn: fetch });
    return useEndpointSelection(data.containers[0]);
  };
  return { client, wrapper, useSelection, fetch };
};

beforeEach(() => { vi.clearAllMocks(); });
describe('shared endpoint selection', () => {
  it('uses server flags without preference metadata', () => {
    const { wrapper, useSelection } = setup(container());
    const { result } = renderHook(useSelection, { wrapper });
    expect(result.current.selectedEndpoint).toEqual(root);
    expect(result.current.selectedEndpointId).toBe(root.id);
    expect(localStorage.getItem).not.toHaveBeenCalled();
    expect(selectContainerEndpoint).not.toHaveBeenCalled();
  });

  it('leaves an ambiguous inventory unselected without preference metadata', () => {
    const { wrapper, useSelection } = setup(container(null, { endpoints: [port, { ...root, selected: false }] }));
    const { result } = renderHook(useSelection, { wrapper });
    expect(result.current.selectedEndpoint).toBeNull();
    expect(result.current.selectedEndpointId).toBeNull();
  });

  it('distinguishes explicit none and reads custom candidates from the inventory', () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => ({ none: useEndpointSelection(noUi()), custom: useEndpointSelection(container(null, { endpoints: [custom] })) }), { wrapper });
    expect(result.current.none.selectedEndpoint).toBeNull();
    expect(result.current.none.selectedEndpointId).toBe('none');
    expect(result.current.custom.selectedEndpoint).toEqual(custom);
    expect(result.current.custom.selectedEndpointId).toBe('custom');
  });

  it('keeps the old selection pending through PUT and active GET, and refreshes stale filtered inventory on use', async () => {
    const write = deferred();
    const refresh = deferred();
    const filteredRefresh = deferred();
    const fetch = vi.fn().mockReturnValue(refresh.promise);
    const filteredFetch = vi.fn().mockReturnValue(filteredRefresh.promise);
    const { client, wrapper, useSelection } = setup(container(), fetch);
    const remote = container(null, { sourceId: 'other' });
    client.setQueryData(alternate, inventory([container(null, { id: 'recreated' }), remote]));
    client.setQueryDefaults(alternate, { queryFn: filteredFetch });
    client.setQueryData(['containers', 'empty'], inventory([]));
    selectContainerEndpoint.mockReturnValue(write.promise);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(1));
    expect(fetch).not.toHaveBeenCalled();
    expect(result.current.isSaving).toBe(true);
    await act(async () => write.resolve(confirmation));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    expect(result.current.isSaving).toBe(true);
    expect(result.current.selectedEndpoint).toEqual(root);
    expect(client.getQueryState(alternate).isInvalidated).toBe(true);
    expect(client.getQueryState(['containers', 'empty']).isInvalidated).toBe(true);
    expect(filteredFetch).not.toHaveBeenCalled();
    expect(client.getQueryData(alternate).containers[1]).toEqual(remote);
    await act(async () => refresh.resolve(inventory([noUi()])));
    await waitFor(() => expect(result.current.isSaving).toBe(false));
    expect(result.current.selectedEndpointId).toBe('none');
    expect(selectContainerEndpoint).toHaveBeenCalledWith({ sourceId: 'local', containerName: 'app', selectedEndpoint: none });
    const filtered = renderHook(() => useQuery({ queryKey: alternate }), { wrapper });
    await waitFor(() => expect(filteredFetch).toHaveBeenCalledTimes(1));
    await act(async () => filteredRefresh.resolve(inventory([noUi(), remote])));
    await waitFor(() => expect(filtered.result.current.data.containers[0].hasWebUi).toBe(false));
    expect(filtered.result.current.data.containers[1]).toEqual(remote);
    expect(localStorage.setItem).not.toHaveBeenCalled();
  });

  it('keeps the confirmed selection and exposes save errors without fetching when PUT fails', async () => {
    const { wrapper, useSelection, fetch } = setup();
    selectContainerEndpoint.mockRejectedValue(new Error('Disk is full'));
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(result.current.saveError?.message).toBe('Disk is full'));
    expect(result.current.selectedEndpoint).toEqual(root);
    expect(result.current.isSaving).toBe(false);
    expect(result.current.refreshError).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('reports successful PUT with failed GET separately and clears feedback on a later successful inventory GET without another write', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error('Offline')).mockResolvedValue(inventory([noUi()]));
    const { client, wrapper, useSelection } = setup(container(), fetch);
    selectContainerEndpoint.mockResolvedValue(confirmation);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(result.current.refreshError?.message).toBe('Offline'));
    expect(result.current.saveError).toBeNull();
    expect(result.current.selectedEndpoint).toEqual(root);
    expect(result.current.isSaving).toBe(false);
    await act(async () => client.refetchQueries({ queryKey: key, exact: true }));
    await waitFor(() => expect(result.current.refreshError).toBeNull());
    expect(result.current.selectedEndpointId).toBe('none');
    expect(selectContainerEndpoint).toHaveBeenCalledTimes(1);
  });

  it('clears failed-refresh feedback even when a later GET returns structurally identical inventory', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error('Offline')).mockResolvedValue(inventory([container()]));
    const { client, wrapper, useSelection } = setup(container(), fetch);
    selectContainerEndpoint.mockResolvedValue(confirmation);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(result.current.refreshError).toBeTruthy());
    await act(async () => client.refetchQueries({ queryKey: key, exact: true }));
    await waitFor(() => expect(result.current.refreshError).toBeNull());
    expect(selectContainerEndpoint).toHaveBeenCalledTimes(1);
  });

  it('does not carry refresh failure feedback into another source with the same container name', async () => {
    const fetch = vi.fn().mockRejectedValue(new Error('Offline'));
    const { client, wrapper, useSelection } = setup(container(), fetch);
    selectContainerEndpoint.mockResolvedValue(confirmation);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(result.current.refreshError?.message).toBe('Offline'));
    await act(async () => client.setQueryData(key, inventory([container(null, { sourceId: 'remote' })])));
    await waitFor(() => expect(result.current.refreshError).toBeNull());
  });

  it('cancels inventory requests started before and during saving so old responses cannot undo resolved GET state', async () => {
    const old = deferred();
    const during = deferred();
    const refresh = deferred();
    const write = deferred();
    const signals = [];
    const requests = [old, during, refresh];
    const fetch = vi.fn(({ signal }) => { signals.push(signal); return requests.shift().promise; });
    const { client, wrapper, useSelection } = setup(container(), fetch);
    const { result } = renderHook(useSelection, { wrapper });
    const beforeRequest = client.refetchQueries({ queryKey: key, exact: true });
    selectContainerEndpoint.mockReturnValue(write.promise);
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(1));
    const duringRequest = client.refetchQueries({ queryKey: key, exact: true });
    await act(async () => write.resolve(confirmation));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    expect(signals.slice(0, 2).every(signal => signal.aborted)).toBe(true);
    expect(result.current.isSaving).toBe(true);
    await act(async () => refresh.resolve(inventory([noUi()])));
    await waitFor(() => expect(result.current.selectedEndpointId).toBe('none'));
    await act(async () => { old.resolve(inventory([container()])); during.resolve(inventory([container()])); await Promise.all([beforeRequest, duringRequest]); });
    expect(result.current.selectedEndpoint).toBeNull();
    expect(client.getQueryData(key).containers[0].hasWebUi).toBe(false);
  });

  it('keeps a container save locked when a different source starts saving during its confirmation GET', async () => {
    const canceledRead = deferred();
    const replacementRead = deferred();
    const otherWrite = deferred();
    const fetch = vi.fn().mockReturnValueOnce(canceledRead.promise).mockReturnValue(replacementRead.promise);
    const { client, wrapper, useSelection } = setup(container(), fetch);
    selectContainerEndpoint.mockResolvedValueOnce(confirmation).mockReturnValueOnce(otherWrite.promise);
    const { result } = renderHook(() => ({ first: useSelection(), other: useEndpointSelection(container(null, { sourceId: 'remote' })) }), { wrapper });
    act(() => result.current.first.selectEndpoint(none));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    act(() => result.current.other.selectEndpoint(null));
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(2));
    expect(client.isMutating({ mutationKey: ['selected-endpoint', 'local', 'app'], exact: true })).toBe(1);
    act(() => result.current.first.selectEndpoint(port));
    expect(selectContainerEndpoint).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    await act(async () => replacementRead.resolve(inventory([noUi()])));
    await waitFor(() => expect(result.current.first.isSaving).toBe(false));
    await act(async () => otherWrite.resolve({ sourceId: 'remote', containerName: 'app', preferences: { selectedEndpoint: null } }));
    await waitFor(() => expect(result.current.other.isSaving).toBe(false));
    expect(result.current.first.selectedEndpointId).toBe('none');
  });

  it('keeps a saved selection pending if the inventory GET is paused until the connection resumes', async () => {
    const write = deferred();
    const refresh = deferred();
    const fetch = vi.fn(() => refresh.promise);
    const { client, wrapper, useSelection } = setup(container(), fetch);
    selectContainerEndpoint.mockReturnValue(write.promise);
    const { result } = renderHook(useSelection, { wrapper });
    act(() => result.current.selectEndpoint(none));
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(1));
    try {
      onlineManager.setOnline(false);
      await act(async () => write.resolve(confirmation));
      await waitFor(() => expect(client.getQueryState(key).fetchStatus).toBe('paused'));
      expect(client.isMutating({ mutationKey: ['selected-endpoint', 'local', 'app'], exact: true })).toBe(1);
      expect(fetch).not.toHaveBeenCalled();
      act(() => onlineManager.setOnline(true));
      await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
      await act(async () => refresh.resolve(inventory([noUi()])));
      await waitFor(() => expect(result.current.isSaving).toBe(false));
      expect(result.current.selectedEndpointId).toBe('none');
    } finally {
      onlineManager.setOnline(true);
    }
  });

  it('blocks overlapping same-identity saves across hooks through refresh while other sources save independently', async () => {
    const refresh = deferred();
    const write = deferred();
    const { wrapper, useSelection } = setup(container(), () => refresh.promise);
    selectContainerEndpoint.mockReturnValue(write.promise);
    const { result } = renderHook(() => ({ first: useSelection(), second: useEndpointSelection(container()), other: useEndpointSelection(container(null, { sourceId: 'remote' })) }), { wrapper });
    act(() => { result.current.first.selectEndpoint(none); result.current.second.selectEndpoint(port); result.current.other.selectEndpoint(null); });
    await waitFor(() => expect(selectContainerEndpoint).toHaveBeenCalledTimes(2));
    await act(async () => write.resolve(confirmation));
    expect(result.current.second.isSaving).toBe(true);
    act(() => result.current.second.selectEndpoint(port));
    expect(selectContainerEndpoint).toHaveBeenCalledTimes(2);
    expect(selectContainerEndpoint.mock.calls.map(([request]) => request.sourceId)).toEqual(['local', 'remote']);
    await act(async () => refresh.resolve(inventory([noUi()])));
    await waitFor(() => expect(result.current.first.isSaving).toBe(false));
  });
});
