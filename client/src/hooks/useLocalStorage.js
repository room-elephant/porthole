import { useState, useCallback, useEffect, useLayoutEffect, useRef } from 'react';

const SAME_TAB_EVENT = 'porthole:storage';

const parseStoredValue = (stored, defaultValue) => {
  if (stored === null) return defaultValue;
  try {
    return JSON.parse(stored);
  } catch {
    return stored;
  }
};

const readStoredValue = (key, defaultValue) => {
  try {
    return parseStoredValue(localStorage.getItem(key), defaultValue);
  } catch {
    return defaultValue;
  }
};

/**
 * Custom hook for persisting state in localStorage, kept in sync across tabs
 * (native `storage` event) and across hook instances in the same tab
 * (`porthole:storage` event dispatched by the setter).
 * @param {string} key - The localStorage key
 * @param {*} defaultValue - Default value when no stored value exists
 * @returns {[*, function]} Tuple of [value, setValue]
 */
export const useLocalStorage = (key, defaultValue) => {
  const [state, setState] = useState(() => ({
    key,
    value: readStoredValue(key, defaultValue),
  }));
  const instance = useRef(Symbol('useLocalStorage'));

  // A renamed container must use its new preference scope before returning a value.
  let current = state;
  if (state.key !== key) {
    current = { key, value: readStoredValue(key, defaultValue) };
    setState(current);
  }

  useEffect(() => {
    const handleStorageChange = (event) => {
      if (event.key === key) {
        setState({ key, value: parseStoredValue(event.newValue, defaultValue) });
      }
    };
    const handleSameTabChange = (event) => {
      if (event.detail?.key !== key || event.detail.source === instance.current) return;
      try {
        setState({ key, value: parseStoredValue(localStorage.getItem(key), defaultValue) });
      } catch { /* Storage is unavailable: keep the in-memory value. */ }
    };

    window.addEventListener('storage', handleStorageChange);
    window.addEventListener(SAME_TAB_EVENT, handleSameTabChange);
    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener(SAME_TAB_EVENT, handleSameTabChange);
    };
  }, [key, defaultValue]);

  const setStoredValue = useCallback((newValue) => {
    setState(previous => ({ key, value: typeof newValue === 'function' ? newValue(previous.value) : newValue }));
    try {
      localStorage.setItem(key, JSON.stringify(newValue));
      window.dispatchEvent(new CustomEvent(SAME_TAB_EVENT, { detail: { key, source: instance.current } }));
    } catch (error) {
      console.warn('localStorage quota exceeded or unavailable:', error);
    }
  }, [key]);

  return [current.value, setStoredValue];
};

/**
 * Revision counter that increments whenever any stored preference changes,
 * in this tab or another. Lets derived views (filters, counts) recompute.
 * @returns {number}
 */
export const useStorageRevision = () => {
  const [revision, setRevision] = useState(0);
  // Layout effect: subscribe before any child's passive effect (e.g. a preference
  // write) updates storage and dispatches the same-tab event.
  useLayoutEffect(() => {
    const bump = () => setRevision(value => value + 1);
    window.addEventListener('storage', bump);
    window.addEventListener(SAME_TAB_EVENT, bump);
    return () => {
      window.removeEventListener('storage', bump);
      window.removeEventListener(SAME_TAB_EVENT, bump);
    };
  }, []);
  return revision;
};
