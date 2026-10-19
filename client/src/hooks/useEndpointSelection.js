import { useLocalStorage } from './useLocalStorage';
import { STORAGE_KEYS } from '../constants';
import { NO_ENDPOINT_ID, resolveEndpointSelection } from '../utils/endpoints';
export const useEndpointSelection = (sourceId, containerName, endpoints) => {
 const key = STORAGE_KEYS.ENDPOINT_PREF(sourceId, containerName);
 const [savedId, selectEndpoint] = useLocalStorage(key, null);
 const selectedEndpoint = resolveEndpointSelection(endpoints, savedId);
 const selectedEndpointId = savedId === NO_ENDPOINT_ID ? NO_ENDPOINT_ID : selectedEndpoint?.id ?? null;
 return {selectedEndpoint, selectedEndpointId, selectEndpoint};
};
