import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useCreateLink, useDeleteLink, useLinkIcon, useLinks, useUpdateLink } from '../../hooks/useLinks';
import { createLink, deleteEntry, fetchLinks, selectEntryIcon, updateLink, uploadEntryIcon } from '../../api';

vi.mock('../../api', () => ({
  createLink: vi.fn(),
  deleteEntry: vi.fn(),
  fetchLinks: vi.fn(),
  selectEntryIcon: vi.fn(),
  updateLink: vi.fn(),
  uploadEntryIcon: vi.fn(),
}));

const link = { id: 'a'.repeat(32), name: 'Router', url: 'http://r/', stack: null, iconUrl: null };
const details = { name: 'Gateway', url: 'http://r/', stack: null };
const deferred = () => {
  let resolve; let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

const setup = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  return { client, wrapper };
};

/** Watches links as the dashboard does, so a refetch has an active query to refresh. */
const withLinks = useHook => () => ({ links: useLinks(), hook: useHook() });

beforeEach(() => {
  vi.resetAllMocks();
  fetchLinks.mockResolvedValue([link]);
});

describe('useLinks', () => {
  it('fetches links', async () => {
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinks(), { wrapper });
    await waitFor(() => expect(result.current.data).toEqual([link]));
  });

  it('does not retry a failed fetch', async () => {
    fetchLinks.mockRejectedValue(new Error('down'));
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinks(), { wrapper });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(fetchLinks).toHaveBeenCalledTimes(1);
  });
});

describe('useUpdateLink', () => {
  it('runs saves for one link in order', async () => {
    const first = deferred();
    updateLink.mockReturnValueOnce(first.promise).mockResolvedValueOnce(link);
    const { wrapper } = setup();
    const { result } = renderHook(() => useUpdateLink(link.id), { wrapper });

    act(() => {
      result.current.mutate({ ...details, name: 'One' });
      result.current.mutate({ ...details, name: 'Two' });
    });
    await waitFor(() => expect(updateLink).toHaveBeenCalledTimes(1));
    expect(updateLink).toHaveBeenCalledWith(link.id, { ...details, name: 'One' });

    await act(async () => { first.resolve(link); });
    await waitFor(() => expect(updateLink).toHaveBeenCalledTimes(2));
    expect(updateLink).toHaveBeenLastCalledWith(link.id, { ...details, name: 'Two' });
  });

  it('refetches links and leaves containers alone', async () => {
    updateLink.mockResolvedValue(link);
    const { client, wrapper } = setup();
    client.setQueryData(['containers', {}], { sources: [], containers: [] });
    const { result } = renderHook(withLinks(() => useUpdateLink(link.id)), { wrapper });
    await waitFor(() => expect(fetchLinks).toHaveBeenCalledTimes(1));

    await act(async () => { await result.current.hook.mutateAsync(details); });

    await waitFor(() => expect(fetchLinks).toHaveBeenCalledTimes(2));
    expect(client.getQueryState(['containers', {}]).isInvalidated).toBe(false);
  });

  it('does not let a poll begun before the save overwrite it', async () => {
    const stalePoll = deferred();
    const { client, wrapper } = setup();
    const { result } = renderHook(withLinks(() => useUpdateLink(link.id)), { wrapper });
    await waitFor(() => expect(result.current.links.data).toEqual([link]));

    fetchLinks.mockReturnValueOnce(stalePoll.promise);
    act(() => { client.refetchQueries({ queryKey: ['links'] }); });
    const saved = { ...link, name: 'Gateway' };
    updateLink.mockResolvedValue(saved);
    fetchLinks.mockResolvedValue([saved]);

    await act(async () => { await result.current.hook.mutateAsync(details); });
    await waitFor(() => expect(result.current.links.data).toEqual([saved]));

    await act(async () => { stalePoll.resolve([link]); });
    expect(result.current.links.data).toEqual([saved]);
  });

  it('exposes a failed save and does not retry it', async () => {
    updateLink.mockRejectedValue(new Error('disk full'));
    const { wrapper } = setup();
    const { result } = renderHook(() => useUpdateLink(link.id), { wrapper });

    act(() => { result.current.mutate(details); });

    await waitFor(() => expect(result.current.error?.message).toBe('disk full'));
    expect(updateLink).toHaveBeenCalledTimes(1);
  });
});

describe('useCreateLink', () => {
  it('returns the new link once the list has it', async () => {
    createLink.mockResolvedValue(link);
    const { wrapper } = setup();
    const { result } = renderHook(withLinks(() => useCreateLink()), { wrapper });
    await waitFor(() => expect(fetchLinks).toHaveBeenCalledTimes(1));

    let created;
    await act(async () => { created = await result.current.hook.mutateAsync(details); });

    expect(created).toEqual(link);
    expect(createLink).toHaveBeenCalledWith(details);
  });
});

describe('useDeleteLink', () => {
  it('deletes and refetches', async () => {
    deleteEntry.mockResolvedValue();
    const { wrapper } = setup();
    const { result } = renderHook(withLinks(() => useDeleteLink()), { wrapper });
    await waitFor(() => expect(fetchLinks).toHaveBeenCalledTimes(1));

    await act(async () => { await result.current.hook.mutateAsync(link.id); });

    expect(deleteEntry).toHaveBeenCalledWith(link.id);
    expect(fetchLinks).toHaveBeenCalledTimes(2);
  });
});

describe('useLinkIcon', () => {
  const file = new File(['x'], 'logo.png', { type: 'image/png' });

  it('saves a library pick and reports back', async () => {
    selectEntryIcon.mockResolvedValue();
    const onSaved = vi.fn();
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinkIcon(link), { wrapper });

    act(() => { result.current.selectIcon('router', { onSaved }); });

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(selectEntryIcon).toHaveBeenCalledWith({ entryId: link.id, name: 'router' });
  });

  it('uploads a file', async () => {
    uploadEntryIcon.mockResolvedValue();
    const onSaved = vi.fn();
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinkIcon(link), { wrapper });

    act(() => { result.current.uploadIcon(file, { onSaved }); });

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(uploadEntryIcon).toHaveBeenCalledWith({ entryId: link.id, file: file });
  });

  it('retry repeats the last save, which clearError forgets the failure of', async () => {
    selectEntryIcon.mockRejectedValueOnce(new Error('down')).mockResolvedValueOnce();
    const onSaved = vi.fn();
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinkIcon(link), { wrapper });

    act(() => { result.current.selectIcon('router', { onSaved }); });
    await waitFor(() => expect(result.current.saveError?.message).toBe('down'));
    expect(onSaved).not.toHaveBeenCalled();

    act(() => { result.current.retry(); });
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(selectEntryIcon).toHaveBeenCalledTimes(2);
    expect(result.current.saveError).toBeNull();
  });

  it('ignores a second save while one is running', async () => {
    const first = deferred();
    selectEntryIcon.mockReturnValueOnce(first.promise);
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinkIcon(link), { wrapper });

    act(() => { result.current.selectIcon('router'); });
    await waitFor(() => expect(result.current.isSaving).toBe(true));
    act(() => { result.current.selectIcon('other'); });
    await act(async () => { first.resolve(); });

    expect(selectEntryIcon).toHaveBeenCalledTimes(1);
  });

  it('clearError resets the failure', async () => {
    selectEntryIcon.mockRejectedValueOnce(new Error('down'));
    const { wrapper } = setup();
    const { result } = renderHook(() => useLinkIcon(link), { wrapper });

    act(() => { result.current.selectIcon('router'); });
    await waitFor(() => expect(result.current.saveError).not.toBeNull());
    act(() => { result.current.clearError(); });

    await waitFor(() => expect(result.current.saveError).toBeNull());
  });
});
