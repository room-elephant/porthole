import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '../constants';

export function useVersionPreference(sourceId, name) {
 return useLocalStorage(STORAGE_KEYS.VERSION_CHECK(sourceId, name), true);
}
