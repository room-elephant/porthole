import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEndpointSelection } from '../../hooks/useEndpointSelection';

const port = { id: 'published-port:80', source: 'published-port', url: 'http://192.168.50.6/' };
const root = { id: 'traefik:root', source: 'traefik', url: 'https://app.example.com/' };
const endpoints = [port, root];
const key = 'endpoint_pref_v2_["local","app"]';

describe('useEndpointSelection', () => {
  beforeEach(() => { localStorage.clear(); vi.clearAllMocks(); });


  it('uses the saved endpoint selection', () => {
    localStorage.setItem(key, JSON.stringify(root.id));
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem(key)).toBe(JSON.stringify(root.id));
  });


  it('falls back when the saved preference is malformed', () => {
    localStorage.setItem(key, 'bad json');
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem(key)).toBe('bad json');
  });

  it('does not persist an automatic default', () => {
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.setItem).not.toHaveBeenCalled();
  });

  it('uses automatic endpoint selection when all storage reads are blocked', () => {
    const getItem = localStorage.getItem.getMockImplementation();
    try {
      localStorage.getItem.mockImplementation(() => { throw new DOMException('Storage is blocked', 'SecurityError'); });
      const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
      expect(result.current.selectedEndpoint).toBe(root);
      expect(localStorage.setItem).not.toHaveBeenCalled();
    } finally {
      localStorage.getItem.mockImplementation(getItem);
    }
  });


  it('persists no endpoint across remounts and endpoint changes', () => {
    const { result, unmount } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    act(() => result.current.selectEndpoint('none'));
    expect(result.current.selectedEndpoint).toBeNull();
    expect(result.current.selectedEndpointId).toBe('none');
    expect(localStorage.getItem(key)).toBe('"none"');
    unmount();

    const next = renderHook(({ candidates }) => useEndpointSelection('local', 'app', candidates), { initialProps: { candidates: [] } });
    expect(next.result.current.selectedEndpoint).toBeNull();
    expect(next.result.current.selectedEndpointId).toBe('none');
    next.rerender({ candidates: [root] });
    expect(next.result.current.selectedEndpoint).toBeNull();
    act(() => next.result.current.selectEndpoint(root.id));
    expect(next.result.current.selectedEndpoint).toBe(root);
    expect(next.result.current.selectedEndpointId).toBe(root.id);
  });

  it('honors a saved no endpoint choice', () => {
    localStorage.setItem(key, '"none"');
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    expect(result.current.selectedEndpoint).toBeNull();
    expect(localStorage.getItem(key)).toBe('"none"');
  });

  it('synchronizes no endpoint from another tab and restores defaults when cleared', () => {
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    act(() => window.dispatchEvent(new StorageEvent('storage', { key, newValue: '"none"' })));
    expect(result.current.selectedEndpoint).toBeNull();
    expect(result.current.selectedEndpointId).toBe('none');
    act(() => window.dispatchEvent(new StorageEvent('storage', { key, newValue: null })));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(result.current.selectedEndpointId).toBe(root.id);
  });

  it('falls back when a saved endpoint disappears', () => {
    localStorage.setItem(key, JSON.stringify(port.id));
    const { result, rerender } = renderHook(({ candidates }) => useEndpointSelection('local', 'app', candidates), { initialProps: { candidates: endpoints } });
    expect(result.current.selectedEndpoint).toBe(port);
    rerender({ candidates: [root] });
    expect(result.current.selectedEndpoint).toBe(root);
  });

  it('synchronizes explicit selection from another tab', () => {
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    act(() => window.dispatchEvent(new StorageEvent('storage', { key, newValue: JSON.stringify(port.id) })));
    expect(result.current.selectedEndpoint).toBe(port);
  });

  it('keeps explicit selection in memory when storage is unavailable', () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { result } = renderHook(() => useEndpointSelection('local', 'app', endpoints));
    localStorage.setItem.mockImplementationOnce(() => { throw new Error('quota'); });
    act(() => result.current.selectEndpoint(port.id));
    expect(result.current.selectedEndpoint).toBe(port);
    expect(warning).toHaveBeenCalled();
    warning.mockRestore();
  });
  it('reads the renamed container preference without carrying the old saved ID', () => {
    localStorage.setItem(key, JSON.stringify(port.id));
    localStorage.setItem( 'endpoint_pref_v2_["local","renamed"]' , JSON.stringify(root.id));
    const { result, rerender } = renderHook(({ name }) => useEndpointSelection('local', name, endpoints), { initialProps: { name: 'app' } });
    expect(result.current.selectedEndpoint).toBe(port);
    rerender({ name: 'renamed' });
    expect(result.current.selectedEndpoint).toBe(root);
  });

  it('uses automatic selection when a renamed container has no saved preference', () => {
    localStorage.setItem(key, JSON.stringify(port.id));
    const { result, rerender } = renderHook(({ name }) => useEndpointSelection('local', name, endpoints), { initialProps: { name: 'app' } });
    rerender({ name: 'renamed' });
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem( 'endpoint_pref_v2_["local","renamed"]' )).toBeNull();
  });


});
