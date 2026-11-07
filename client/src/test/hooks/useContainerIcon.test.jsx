import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useContainerIcon } from '../../hooks/useContainerIcon';
import { useIconLibrary } from '../../hooks/useIconLibrary';
import { fetchIconLibrary, selectContainerIcon, uploadContainerIcon } from '../../api';
import { createMockContainer } from '../testUtils';

vi.mock('../../api', () => ({
  fetchIconLibrary: vi.fn(),
  selectContainerEndpoint: vi.fn(),
  selectContainerIcon: vi.fn(),
  uploadContainerIcon: vi.fn(),
}));

const key = ['containers', { showAll: true, showStopped: false }];
const container = createMockContainer({ sourceId: 'walker', name: 'app' });
const inventory = { sources: [], containers: [container] };
const image = new File(['x'], 'logo.png', { type: 'image/png' });
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };

const setup = (fetch = vi.fn().mockResolvedValue(inventory)) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  client.setQueryData(key, inventory);
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  const useIcon = () => {
    useQuery({ queryKey: key, queryFn: fetch });
    return useContainerIcon(container);
  };
  return { wrapper, useIcon, fetch };
};

beforeEach(() => { vi.clearAllMocks(); });

describe('useContainerIcon', () => {
  it('saves a library icon, refetches inventory, then reports saved', async () => {
    selectContainerIcon.mockResolvedValue(undefined);
    const { wrapper, useIcon, fetch } = setup();
    const onSaved = vi.fn(() => expect(fetch).toHaveBeenCalledTimes(1));
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.selectIcon('coredns', { onSaved }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(selectContainerIcon).toHaveBeenCalledWith({ sourceId: 'walker', containerName: 'app', name: 'coredns' });
    expect(result.current.saveError).toBeNull();
    expect(result.current.refreshError).toBeNull();
  });

  it('uploads a file', async () => {
    uploadContainerIcon.mockResolvedValue(undefined);
    const { wrapper, useIcon } = setup();
    const onSaved = vi.fn();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.uploadIcon(image, { onSaved }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(uploadContainerIcon).toHaveBeenCalledWith({ sourceId: 'walker', containerName: 'app', file: image });
  });

  it('reports a failed write without calling saved, and retry repeats the same call', async () => {
    selectContainerIcon.mockRejectedValueOnce(new Error('disk full')).mockResolvedValue(undefined);
    const { wrapper, useIcon } = setup();
    const onSaved = vi.fn();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.selectIcon('coredns', { onSaved }));
    await waitFor(() => expect(result.current.saveError?.message).toBe('disk full'));
    expect(onSaved).not.toHaveBeenCalled();

    act(() => result.current.retry());
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(selectContainerIcon).toHaveBeenCalledTimes(2);
    expect(selectContainerIcon).toHaveBeenLastCalledWith({ sourceId: 'walker', containerName: 'app', name: 'coredns' });
    expect(result.current.saveError).toBeNull();
  });

  it('retries an upload with the same file', async () => {
    uploadContainerIcon.mockRejectedValueOnce(new Error('disk full')).mockResolvedValue(undefined);
    const { wrapper, useIcon } = setup();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.uploadIcon(image));
    await waitFor(() => expect(result.current.saveError).not.toBeNull());
    act(() => result.current.retry());
    await waitFor(() => expect(uploadContainerIcon).toHaveBeenCalledTimes(2));
    expect(uploadContainerIcon).toHaveBeenLastCalledWith({ sourceId: 'walker', containerName: 'app', file: image });
  });

  it('never repeats a successful write when only the refetch fails', async () => {
    selectContainerIcon.mockResolvedValue(undefined);
    const { wrapper, useIcon } = setup(vi.fn().mockRejectedValue(new Error('offline')));
    const onSaved = vi.fn();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.selectIcon('coredns', { onSaved }));
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(result.current.refreshError?.message).toBe('offline'));
    expect(result.current.saveError).toBeNull();
    expect(selectContainerIcon).toHaveBeenCalledTimes(1);
  });

  it('ignores a second save while one is running', async () => {
    const write = deferred();
    selectContainerIcon.mockReturnValue(write.promise);
    const { wrapper, useIcon } = setup();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => {
      result.current.selectIcon('coredns');
      result.current.selectIcon('grafana');
    });
    await waitFor(() => expect(result.current.isSaving).toBe(true));
    expect(selectContainerIcon).toHaveBeenCalledTimes(1);
    write.resolve();
    await waitFor(() => expect(result.current.isSaving).toBe(false));
  });

  it('clears a shown error', async () => {
    selectContainerIcon.mockRejectedValue(new Error('disk full'));
    const { wrapper, useIcon } = setup();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.selectIcon('coredns'));
    await waitFor(() => expect(result.current.saveError).not.toBeNull());
    act(() => result.current.clearError());
    await waitFor(() => expect(result.current.saveError).toBeNull());
  });

  it('does nothing on retry before any save', () => {
    const { wrapper, useIcon } = setup();
    const { result } = renderHook(useIcon, { wrapper });
    act(() => result.current.retry());
    expect(selectContainerIcon).not.toHaveBeenCalled();
    expect(uploadContainerIcon).not.toHaveBeenCalled();
  });
});

describe('useIconLibrary', () => {
  const wrapper = ({ children }) => <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>;

  it('does not fetch until enabled', () => {
    renderHook(() => useIconLibrary(false), { wrapper });
    expect(fetchIconLibrary).not.toHaveBeenCalled();
  });

  it('parses the metadata once enabled', async () => {
    fetchIconLibrary.mockResolvedValue({ b: {}, a: {} });
    const { result } = renderHook(() => useIconLibrary(true), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual([{ name: 'a', aliases: [] }, { name: 'b', aliases: [] }]));
  });

  it('reports unusable metadata as an error without retrying', async () => {
    fetchIconLibrary.mockResolvedValue(null);
    const { result } = renderHook(() => useIconLibrary(true), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(fetchIconLibrary).toHaveBeenCalledTimes(1);
  });
});
