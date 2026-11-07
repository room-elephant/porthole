import { selectContainerEndpoint } from '../api';
import { NO_ENDPOINT_ID } from '../utils/endpoints';
import { useInventorySave } from './useInventorySave';

/** Navigation and settings selection use only server-resolved inventory. */
export const useEndpointSelection = (container) => {
  const { save, isSaving, saveError, refreshError } = useInventorySave({
    container,
    mutationKey: ['selected-endpoint', container.sourceId, container.name],
    mutationFn: selection => selectContainerEndpoint(selection),
  });
  const selectedEndpoint = container.endpoints.find(endpoint => endpoint.selected) ?? null;
  const selectedEndpointId = selectedEndpoint?.id
    ?? (!container.hasWebUi ? NO_ENDPOINT_ID : null);
  const selectEndpoint = (selectedEndpoint) => {
    save({ sourceId: container.sourceId, containerName: container.name, selectedEndpoint });
  };
  return { selectedEndpoint, selectedEndpointId, selectEndpoint, isSaving, saveError, refreshError };
};
