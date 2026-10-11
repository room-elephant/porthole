import { useEffect } from 'react';
import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '../constants';
import { NO_ENDPOINT_ID, resolveEndpointSelection } from '../utils/endpoints';

export const useEndpointSelection = (containerName, endpoints) => {
  const key = STORAGE_KEYS.ENDPOINT_PREF(containerName);
  const [savedId, selectEndpoint] = useLocalStorage(key, null);

  useEffect(() => {
    if (savedId !== null || localStorage.getItem(key) !== null) return;
    const legacy = localStorage.getItem(STORAGE_KEYS.PORT_PREF(containerName));
    if (legacy === null) return;
    let port;
    try {
      port = JSON.parse(legacy);
    } catch {
      return;
    }
    if (typeof port !== 'number' || !Number.isInteger(port)) return;
    const candidate = endpoints.find(endpoint => endpoint.id === `published-port:${port}` && endpoint.source === 'published-port');
    if (candidate) selectEndpoint(candidate.id);
  }, [containerName, endpoints, key, savedId, selectEndpoint]);

  const selectedEndpoint = resolveEndpointSelection(endpoints, savedId);
  const selectedEndpointId = savedId === NO_ENDPOINT_ID ? NO_ENDPOINT_ID : selectedEndpoint?.id ?? null;

  return { selectedEndpoint, selectedEndpointId, selectEndpoint };
};
