import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useLocalStorage, useStorageRevision } from '../../hooks/useLocalStorage';

describe('useLocalStorage', () => {
  beforeEach(() => {
    localStorage.getItem.mockReturnValue(null);
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should return default value when localStorage is empty', () => {
    const { result } = renderHook(() => useLocalStorage('testKey', 'defaultValue'));
    expect(result.current[0]).toBe('defaultValue');
  });

  it('uses the default and keeps settings in memory when storage reads and writes throw', () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const getItem = localStorage.getItem.getMockImplementation();
    const setItem = localStorage.setItem.getMockImplementation();
    const unavailable = () => { throw new DOMException('Storage is blocked', 'SecurityError'); };
    try {
      localStorage.getItem.withImplementation(unavailable, () => {
        localStorage.setItem.withImplementation(unavailable, () => {
          const { result } = renderHook(() => useLocalStorage('blocked', true));
          expect(result.current[0]).toBe(true);
          act(() => result.current[1](false));
          expect(result.current[0]).toBe(false);
        });
      });
    } finally {
      localStorage.getItem.mockImplementation(getItem);
      localStorage.setItem.mockImplementation(setItem);
      warning.mockRestore();
    }
  });

  it('uses the default when accessing localStorage itself throws', () => {
    const descriptor = Object.getOwnPropertyDescriptor(window, 'localStorage');
    try {
      Object.defineProperty(window, 'localStorage', {
        configurable: true,
        get: () => { throw new DOMException('Storage is blocked', 'SecurityError'); },
      });
      const { result } = renderHook(() => useLocalStorage('blocked', 'default'));
      expect(result.current[0]).toBe('default');
    } finally {
      Object.defineProperty(window, 'localStorage', descriptor);
    }
  });

  it('uses the new default when storage becomes unreadable after a key change', () => {
    localStorage.getItem.mockReturnValue('"old preference"');
    const { result, rerender } = renderHook(
      ({ key }) => useLocalStorage(key, 'default'),
      { initialProps: { key: 'old' } },
    );
    expect(result.current[0]).toBe('old preference');
    try {
      localStorage.getItem.withImplementation(() => { throw new Error('Storage is blocked'); }, () => {
        rerender({ key: 'new' });
        expect(result.current[0]).toBe('default');
      });
    } finally {
      localStorage.getItem.mockReturnValue(null);
    }
  });

  it('should return stored value from localStorage', () => {
    localStorage.getItem.mockReturnValue('"storedValue"');
    
    const { result } = renderHook(() => useLocalStorage('testKey', 'defaultValue'));
    
    expect(result.current[0]).toBe('storedValue');
  });

  it('should store value in localStorage when set', () => {
    const { result } = renderHook(() => useLocalStorage('testKey', 'defaultValue'));
    
    act(() => {
      result.current[1]('newValue');
    });
    
    expect(result.current[0]).toBe('newValue');
    expect(localStorage.setItem).toHaveBeenCalledWith('testKey', '"newValue"');
  });

  it('should handle boolean values', () => {
    localStorage.getItem.mockReturnValue('true');
    
    const { result } = renderHook(() => useLocalStorage('boolKey', false));
    
    expect(result.current[0]).toBe(true);
    
    act(() => {
      result.current[1](false);
    });
    
    expect(result.current[0]).toBe(false);
    expect(localStorage.setItem).toHaveBeenCalledWith('boolKey', 'false');
  });

  it('should handle object values', () => {
    const defaultObj = { foo: 'bar' };
    localStorage.getItem.mockReturnValue('{"stored":"object"}');
    
    const { result } = renderHook(() => useLocalStorage('objKey', defaultObj));
    
    expect(result.current[0]).toEqual({ stored: 'object' });
  });

  it('should handle null stored value', () => {
    localStorage.getItem.mockReturnValue(null);
    
    const { result } = renderHook(() => useLocalStorage('nullKey', 'default'));
    
    expect(result.current[0]).toBe('default');
  });

  it('should handle invalid JSON in localStorage', () => {
    localStorage.getItem.mockReturnValue('not-valid-json');
    
    const { result } = renderHook(() => useLocalStorage('invalidKey', 'default'));
    
    expect(result.current[0]).toBe('not-valid-json');
  });

  it('should sync value across tabs via storage event', async () => {
    localStorage.getItem.mockReturnValue(null);
    const { result } = renderHook(() => useLocalStorage('syncKey', 'initial'));
    
    expect(result.current[0]).toBe('initial');
    
    await act(async () => {
      const event = new StorageEvent('storage', {
        key: 'syncKey',
        newValue: '"updatedFromOtherTab"',
      });
      window.dispatchEvent(event);
    });
    
    await waitFor(() => {
      expect(result.current[0]).toBe('updatedFromOtherTab');
    });
  });

  it('should not update for different key storage events', async () => {
    localStorage.getItem.mockReturnValue(null);
    const { result } = renderHook(() => useLocalStorage('myKey', 'initial'));
    
    expect(result.current[0]).toBe('initial');
    
    await act(async () => {
      const event = new StorageEvent('storage', {
        key: 'differentKey',
        newValue: '"changed"',
      });
      window.dispatchEvent(event);
    });
    
    expect(result.current[0]).toBe('initial');
  });

  it('should handle localStorage quota exceeded gracefully', () => {
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    localStorage.setItem.mockImplementationOnce(() => {
      throw new Error('QuotaExceededError');
    });
    
    const { result } = renderHook(() => useLocalStorage('quotaKey', 'default'));
    
    act(() => {
      result.current[1]('newValue');
    });
    
    expect(result.current[0]).toBe('newValue');
    expect(consoleWarnSpy).toHaveBeenCalled();
    
    consoleWarnSpy.mockRestore();
  });

  it('syncs two hooks with the same key in the same tab', () => {
    const store = {};
    localStorage.getItem.mockImplementation(key => store[key] ?? null);
    localStorage.setItem.mockImplementation((key, value) => { store[key] = value; });
    const a = renderHook(() => useLocalStorage('k', 1));
    const b = renderHook(() => useLocalStorage('k', 1));
    act(() => a.result.current[1](2));
    expect(b.result.current[0]).toBe(2);
  });

  it('does not touch hooks with another key in the same tab', () => {
    const a = renderHook(() => useLocalStorage('k', 1));
    const other = renderHook(() => useLocalStorage('other', 1));
    act(() => a.result.current[1](2));
    expect(other.result.current[0]).toBe(1);
  });

  it('announces a same-tab write as a porthole:storage event for its key', () => {
    const seen = [];
    const listener = event => seen.push(event.detail.key);
    window.addEventListener('porthole:storage', listener);
    try {
      const { result } = renderHook(() => useLocalStorage('announced', 1));
      act(() => result.current[1](2));
      expect(seen).toEqual(['announced']);
    } finally {
      window.removeEventListener('porthole:storage', listener);
    }
  });

  describe('useStorageRevision', () => {
    it('increments on same-tab and cross-tab events and stops after unmount', () => {
      const { result, unmount } = renderHook(() => useStorageRevision());
      const start = result.current;
      act(() => { window.dispatchEvent(new CustomEvent('porthole:storage', { detail: { key: 'x' } })); });
      expect(result.current).toBe(start + 1);
      act(() => { window.dispatchEvent(new StorageEvent('storage', { key: 'x' })); });
      expect(result.current).toBe(start + 2);
      const remove = vi.spyOn(window, 'removeEventListener');
      unmount();
      expect(remove).toHaveBeenCalledWith('porthole:storage', expect.any(Function));
      expect(remove).toHaveBeenCalledWith('storage', expect.any(Function));
      remove.mockRestore();
    });
  });
});
