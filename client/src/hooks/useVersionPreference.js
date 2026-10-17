import { useEffect } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '../constants';
export function useVersionPreference(sourceId, name, migrateLegacy) {
 const key = STORAGE_KEYS.VERSION_CHECK(sourceId, name);
 const [enabled, setEnabled] = useLocalStorage(key, true);
 useEffect(() => {
  if (!migrateLegacy) return;
  try {
   if (localStorage.getItem(key) !== null) return;
   const old = JSON.parse(localStorage.getItem(`version_check_${name}`));
   if (typeof old === 'boolean') setEnabled(old);
  } catch { /* Keep default when storage is blocked or malformed. */ }
 }, [key, name, migrateLegacy, setEnabled]);
 return [enabled, setEnabled];
}
