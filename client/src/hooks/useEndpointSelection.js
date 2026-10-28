import { useIsMutating, useMutation, useQueryClient } from '@tanstack/react-query';
import { selectContainerEndpoint } from '../api';
import { NO_ENDPOINT_ID, resolveEndpointSelection } from '../utils/endpoints';

/** Endpoint choices are confirmed server preferences carried by the inventory. */
export const useEndpointSelection = (container) => {
  const queryClient = useQueryClient();
  const mutationKey = ['selected-endpoint', container.sourceId, container.name];
  const filters = { mutationKey, exact: true };
  const savingCount = useIsMutating(filters);
  const mutation = useMutation({
    mutationKey,
    mutationFn: selection => selectContainerEndpoint(selection),
    onMutate: () => queryClient.cancelQueries({ queryKey: ['containers'] }),
    onSuccess: async (confirmed) => {
      // Also cancel polls that started during the write, before installing the confirmed value.
      await queryClient.cancelQueries({ queryKey: ['containers'] });
      queryClient.setQueriesData({ queryKey: ['containers'] }, inventory => inventory && ({
        ...inventory,
        containers: inventory.containers.map(candidate =>
          candidate.sourceId === confirmed.sourceId && candidate.name === confirmed.containerName
            ? { ...candidate, preferences: confirmed.preferences }
            : candidate),
      }));
    },
  });
  const selected = container.preferences?.selectedEndpoint;
  const selectedEndpoint = resolveEndpointSelection(container.endpoints, selected);
  const selectedEndpointId = selected?.id === NO_ENDPOINT_ID ? NO_ENDPOINT_ID : selectedEndpoint?.id ?? null;
  const selectEndpoint = (selectedEndpoint) => {
    // QueryClient's mutation state changes synchronously, including between two clicks in one render.
    if (queryClient.isMutating(filters)) return;
    mutation.mutate({ sourceId: container.sourceId, containerName: container.name, selectedEndpoint });
  };
  return { selectedEndpoint, selectedEndpointId, selectEndpoint, isSaving: savingCount > 0, saveError: mutation.error };
};
