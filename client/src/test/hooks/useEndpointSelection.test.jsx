import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useEndpointSelection } from '../../hooks/useEndpointSelection';

const port = { id: 'published-port:80', source: 'published-port', url: 'http://192.168.50.6/' };
const root = { id: 'traefik:root', source: 'traefik', url: 'https://app.example.com/' };
const endpoints = [port, root];
const key = 'endpoint_pref_app';

describe('useEndpointSelection', () => {
  beforeEach(() => { localStorage.clear(); vi.clearAllMocks(); });

  it('migrates a valid numeric legacy preference and preserves the old key', () => {
    localStorage.setItem('port_pref_app', '80');
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    expect(result.current.selectedEndpoint).toBe(port);
    expect(localStorage.getItem(key)).toBe(JSON.stringify(port.id));
    expect(localStorage.getItem('port_pref_app')).toBe('80');
  });

  it('gives the new key precedence over legacy preferences', () => {
    localStorage.setItem('port_pref_app', '80');
    localStorage.setItem(key, JSON.stringify(root.id));
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem(key)).toBe(JSON.stringify(root.id));
  });

  it.each(['bad json', '"80"', 'null', '{}', '9999'])('ignores invalid or unavailable legacy value %s', legacy => {
    localStorage.setItem('port_pref_app', legacy);
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem(key)).toBeNull();
  });

  it('does not migrate when a malformed new preference exists', () => {
    localStorage.setItem('port_pref_app', '80');
    localStorage.setItem(key, 'bad json');
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem(key)).toBe('bad json');
  });

  it('does not persist an automatic default', () => {
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.setItem).not.toHaveBeenCalled();
  });

  it('falls back when a saved endpoint disappears', () => {
    localStorage.setItem(key, JSON.stringify(port.id));
    const { result, rerender } = renderHook(({ candidates }) => useEndpointSelection('app', candidates), { initialProps: { candidates: endpoints } });
    expect(result.current.selectedEndpoint).toBe(port);
    rerender({ candidates: [root] });
    expect(result.current.selectedEndpoint).toBe(root);
  });

  it('synchronizes explicit selection from another tab', () => {
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    act(() => window.dispatchEvent(new StorageEvent('storage', { key, newValue: JSON.stringify(port.id) })));
    expect(result.current.selectedEndpoint).toBe(port);
  });

  it('keeps explicit selection in memory when storage is unavailable', () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const { result } = renderHook(() => useEndpointSelection('app', endpoints));
    localStorage.setItem.mockImplementationOnce(() => { throw new Error('quota'); });
    act(() => result.current.selectEndpoint(port.id));
    expect(result.current.selectedEndpoint).toBe(port);
    expect(warning).toHaveBeenCalled();
    warning.mockRestore();
  });
  it('reads the renamed container preference without carrying the old saved ID', () => {
    localStorage.setItem(key, JSON.stringify(port.id));
    localStorage.setItem('endpoint_pref_renamed', JSON.stringify(root.id));
    const { result, rerender } = renderHook(({ name }) => useEndpointSelection(name, endpoints), { initialProps: { name: 'app' } });
    expect(result.current.selectedEndpoint).toBe(port);
    rerender({ name: 'renamed' });
    expect(result.current.selectedEndpoint).toBe(root);
  });

  it('uses automatic selection when a renamed container has no saved preference', () => {
    localStorage.setItem(key, JSON.stringify(port.id));
    const { result, rerender } = renderHook(({ name }) => useEndpointSelection(name, endpoints), { initialProps: { name: 'app' } });
    rerender({ name: 'renamed' });
    expect(result.current.selectedEndpoint).toBe(root);
    expect(localStorage.getItem('endpoint_pref_renamed')).toBeNull();
  });

  it('migrates the renamed container legacy preference and writes only its new key', () => {
    localStorage.setItem(key, JSON.stringify(root.id));
    localStorage.setItem('port_pref_renamed', '80');
    const { result, rerender } = renderHook(({ name }) => useEndpointSelection(name, endpoints), { initialProps: { name: 'app' } });
    rerender({ name: 'renamed' });
    expect(result.current.selectedEndpoint).toBe(port);
    expect(localStorage.getItem('endpoint_pref_renamed')).toBe(JSON.stringify(port.id));
    expect(localStorage.getItem(key)).toBe(JSON.stringify(root.id));
  });

});
