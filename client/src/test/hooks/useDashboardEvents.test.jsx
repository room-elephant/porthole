import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useDashboardEvents, REOPEN_DELAY } from '../../hooks/useDashboardEvents';
import { DASHBOARD_KEY } from '../../utils/dashboard';

/** Stands in for the browser's EventSource; tests drive its events by hand. */
class FakeEventSource {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 2;
  static instances = [];

  constructor(url) {
    this.url = url;
    this.readyState = FakeEventSource.CONNECTING;
    this.listeners = {};
    this.close = vi.fn(() => { this.readyState = FakeEventSource.CLOSED; });
    FakeEventSource.instances.push(this);
  }

  addEventListener(type, listener) {
    this.listeners[type] = [...(this.listeners[type] ?? []), listener];
  }

  emit(type, data) {
    (this.listeners[type] ?? []).forEach(listener => listener({ type, data }));
  }

  fail(readyState) {
    this.readyState = readyState;
    this.onerror?.(new Event('error'));
  }
}

const setup = (revision = 5) => {
  const queryClient = new QueryClient();
  queryClient.setQueryData(DASHBOARD_KEY, { revision, sources: [], entries: [] });
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  const hook = renderHook(() => useDashboardEvents(), { wrapper });
  return { ...hook, queryClient, invalidate, source: () => FakeEventSource.instances.at(-1) };
};

describe('useDashboardEvents', () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal('EventSource', FakeEventSource);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('listens to the change stream', () => {
    const { source } = setup();
    expect(source().url).toBe('/api/events');
  });

  it('refetches the dashboard when the revision is new', () => {
    const { source, invalidate } = setup(5);
    act(() => source().emit('changed', '{"revision":6}'));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: DASHBOARD_KEY });
  });

  it('ignores the revision the dashboard already has', () => {
    const { source, invalidate } = setup(5);
    act(() => source().emit('changed', '{"revision":5}'));
    expect(invalidate).not.toHaveBeenCalled();
  });

  it('refetches when an event cannot be read', () => {
    const { source, invalidate } = setup(5);
    act(() => source().emit('changed', 'not json'));
    expect(invalidate).toHaveBeenCalledTimes(1);
  });

  it('refetches after a reconnect whose first event carries a new revision', () => {
    const { source, invalidate } = setup(5);
    act(() => source().fail(FakeEventSource.CONNECTING));
    act(() => source().emit('changed', '{"revision":1762596000000}'));
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(invalidate).toHaveBeenCalledTimes(1);
  });

  it('reopens a stream the browser gave up on after a delay', () => {
    vi.useFakeTimers();
    const { source } = setup();
    const first = source();
    act(() => first.fail(FakeEventSource.CLOSED));
    expect(FakeEventSource.instances).toHaveLength(1);
    act(() => vi.advanceTimersByTime(REOPEN_DELAY));
    expect(first.close).toHaveBeenCalled();
    expect(FakeEventSource.instances).toHaveLength(2);
  });

  it('closes the stream and any pending reopen on unmount', () => {
    vi.useFakeTimers();
    const { source, unmount } = setup();
    const first = source();
    act(() => first.fail(FakeEventSource.CLOSED));
    unmount();
    act(() => vi.advanceTimersByTime(REOPEN_DELAY));
    expect(first.close).toHaveBeenCalled();
    expect(FakeEventSource.instances).toHaveLength(1);
  });

  it('does nothing in a browser without EventSource', () => {
    vi.stubGlobal('EventSource', undefined);
    const { invalidate } = setup();
    expect(FakeEventSource.instances).toHaveLength(0);
    expect(invalidate).not.toHaveBeenCalled();
  });
});
