import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '../constants';

export function useVersionPreference(entryId) {
 return useLocalStorage(STORAGE_KEYS.VERSION_CHECK(entryId), true);
}
