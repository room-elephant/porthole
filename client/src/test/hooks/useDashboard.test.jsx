import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useDashboard } from '../../hooks/useDashboard';
import { fetchDashboard } from '../../api';

vi.mock('../../api', () => ({ fetchDashboard: vi.fn() }));

const wrapper = ({ children }) => (
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    {children}
  </QueryClientProvider>
);

const dashboard = {
  sources: [{ id: 'walker', name: 'Walker', status: 'UP', error: null, lastSuccessAt: null }],
  entries: [
    {
      id: 'container:walker:app', kind: 'container', name: 'app', stack: null,
      icon: { url: 'https://cdn/app.webp', choice: null }, endpoints: [], hasWebUi: false,
      container: { sourceId: 'walker', name: 'app', defaultName: 'app', image: 'app:1', state: 'running', status: 'Up', self: false, version: null },
    },
    {
      id: 'link:00', kind: 'link', name: 'Router', stack: null, icon: { url: null, choice: null },
      endpoints: [{ id: 'custom', source: 'custom', url: 'http://r/', selected: true }], hasWebUi: true, container: null,
    },
  ],
};

describe('useDashboard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('reads the dashboard once and splits it into views', async () => {
    fetchDashboard.mockResolvedValue(dashboard);

    const { result } = renderHook(() => useDashboard(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(fetchDashboard).toHaveBeenCalledTimes(1);
    expect(fetchDashboard.mock.calls[0][0]).toHaveProperty('signal');
    expect(result.current.data.sources).toEqual(dashboard.sources);
    expect(result.current.data.containers.map(container => container.entryId)).toEqual(['container:walker:app']);
    expect(result.current.data.links).toEqual([{ id: 'link:00', name: 'Router', url: 'http://r/', stack: null, iconUrl: null, iconChoice: null }]);
  });

  it('reports a failed read', async () => {
    fetchDashboard.mockRejectedValue(new Error('down'));

    const { result } = renderHook(() => useDashboard(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
