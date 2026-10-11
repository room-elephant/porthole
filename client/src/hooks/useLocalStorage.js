import { useState, useCallback, useEffect } from 'react';

const parseStoredValue = (stored, defaultValue) => {
  if (stored === null) return defaultValue;
  try {
    return JSON.parse(stored);
  } catch {
    return stored;
  }
};

/**
 * Custom hook for persisting state in localStorage with cross-tab synchronization.
 * @param {string} key - The localStorage key
 * @param {*} defaultValue - Default value when no stored value exists
 * @returns {[*, function]} Tuple of [value, setValue]
 */
export const useLocalStorage = (key, defaultValue) => {
  const [state, setState] = useState(() => ({
    key,
    value: parseStoredValue(localStorage.getItem(key), defaultValue),
  }));

  // A renamed container must use its new preference scope before returning a value.
  let current = state;
  if (state.key !== key) {
    current = { key, value: parseStoredValue(localStorage.getItem(key), defaultValue) };
    setState(current);
  }

  useEffect(() => {
    const handleStorageChange = (event) => {
      if (event.key === key) {
        setState({ key, value: parseStoredValue(event.newValue, defaultValue) });
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, [key, defaultValue]);

  const setStoredValue = useCallback((newValue) => {
    setState(previous => ({ key, value: typeof newValue === 'function' ? newValue(previous.value) : newValue }));
    try {
      localStorage.setItem(key, JSON.stringify(newValue));
    } catch (error) {
      console.warn('localStorage quota exceeded or unavailable:', error);
    }
  }, [key]);

  return [current.value, setStoredValue];
};
