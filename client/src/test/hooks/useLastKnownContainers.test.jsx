import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useLastKnownContainers } from '../../hooks/useLastKnownContainers';

const walker = { id: 'walker', name: 'Walker', status: 'UP' };
const mike = { id: 'mike', name: 'Mike', status: 'UP' };
const x = { id: 'x', sourceId: 'walker' };
const y = { id: 'y', sourceId: 'walker' };
const m = { id: 'm', sourceId: 'mike' };

describe('useLastKnownContainers', () => {
  it('keeps the last containers of a host that went down, until it is up again', () => {
    const { result, rerender } = renderHook(({ data }) => useLastKnownContainers(data), {
      initialProps: { data: { sources: [walker], containers: [x] } },
    });
    expect(result.current).toEqual([x]);

    rerender({ data: { sources: [{ ...walker, status: 'DOWN' }], containers: [] } });
    expect(result.current).toEqual([x]);

    rerender({ data: { sources: [walker], containers: [y] } });
    expect(result.current).toEqual([y]);
  });

  it('merges stored containers of a down host with current ones of an up host', () => {
    const { result, rerender } = renderHook(({ data }) => useLastKnownContainers(data), {
      initialProps: { data: { sources: [walker, mike], containers: [x, m] } },
    });
    rerender({ data: { sources: [walker, { ...mike, status: 'DOWN' }], containers: [x] } });
    expect(result.current).toEqual([x, m]);
  });

  it('returns nothing for a host that was never up', () => {
    const { result } = renderHook(() => useLastKnownContainers({ sources: [{ ...walker, status: 'DOWN' }], containers: [] }));
    expect(result.current).toEqual([]);
  });
});
