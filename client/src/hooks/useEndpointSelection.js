import { useEffect } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '../constants';
import { NO_ENDPOINT_ID, resolveEndpointSelection } from '../utils/endpoints';
export const useEndpointSelection = (sourceId, containerName, endpoints, {migrateLegacy = false} = {}) => {
 const key = STORAGE_KEYS.ENDPOINT_PREF(sourceId, containerName);
 const [savedId, selectEndpoint] = useLocalStorage(key, null);
 useEffect(() => {
  if (!migrateLegacy) return;
  try {
   if (savedId !== null || localStorage.getItem(key) !== null) return;
   let oldId;
   try { oldId = JSON.parse(localStorage.getItem(`endpoint_pref_${containerName}`)); } catch { oldId = null; }
   if (oldId === NO_ENDPOINT_ID || endpoints.some(endpoint => endpoint.id === oldId)) {selectEndpoint(oldId);return;}
   let port;
   try { port = JSON.parse(localStorage.getItem(STORAGE_KEYS.PORT_PREF(containerName))); } catch { return; }
   if (typeof port !== 'number' || !Number.isInteger(port)) return;
   const candidate = endpoints.find(endpoint => endpoint.id === `published-port:${port}` && endpoint.source === 'published-port');
   if (candidate) selectEndpoint(candidate.id);
  } catch { /* Storage may be unavailable. Automatic selection still works. */ }
 }, [containerName, endpoints, key, savedId, selectEndpoint, migrateLegacy]);
 const selectedEndpoint = resolveEndpointSelection(endpoints, savedId);
 const selectedEndpointId = savedId === NO_ENDPOINT_ID ? NO_ENDPOINT_ID : selectedEndpoint?.id ?? null;
 return {selectedEndpoint, selectedEndpointId, selectEndpoint};
};
