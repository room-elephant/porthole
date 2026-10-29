import { useEffect, useState } from 'react';
import { isCancelledError, useIsMutating, useMutation, useQueryClient } from '@tanstack/react-query';
import { selectContainerEndpoint } from '../api';
import { NO_ENDPOINT_ID } from '../utils/endpoints';

/** Navigation uses only server-resolved inventory; preferences describe explicit settings. */
export const useEndpointSelection = (container) => {
  const queryClient = useQueryClient();
  const [refreshFailure, setRefreshFailure] = useState(null);
  const refreshError = refreshFailure?.sourceId === container.sourceId && refreshFailure?.containerName === container.name
    ? refreshFailure.error : null;
  const mutationKey = ['selected-endpoint', container.sourceId, container.name];
  const filters = { mutationKey, exact: true };
  const savingCount = useIsMutating(filters);
  // A later inventory refresh also recovers saved-but-not-refreshed feedback, even
  // when structural sharing preserves the same container object.
  useEffect(() => queryClient.getQueryCache().subscribe(event => {
    if (event.type === 'updated' && event.query.queryKey[0] === 'containers'
      && event.action.type === 'success' && !event.action.manual
      && event.query.state.data?.containers.some(candidate =>
        candidate.sourceId === container.sourceId && candidate.name === container.name)) {
      setRefreshFailure(null);
    }
  }), [queryClient, container.sourceId, container.name]);
  const mutation = useMutation({
    mutationKey,
    mutationFn: selection => selectContainerEndpoint(selection),
    onMutate: () => {
      setRefreshFailure(null);
      return queryClient.cancelQueries({ queryKey: ['containers'] });
    },
    onSuccess: async (_confirmed, selection) => {
      // Polls begun during PUT must not overwrite the subsequent resolved GET.
      await queryClient.cancelQueries({ queryKey: ['containers'] });
      try {
        // Invalidate every variant; inactive ones refresh when next used. Await
        // the active queries directly so a paused GET also holds the save lock.
        await queryClient.invalidateQueries({ queryKey: ['containers'], refetchType: 'none' });
        const pendingReads = () => queryClient.getQueryCache().findAll({ queryKey: ['containers'], type: 'active' })
          .filter(query => query.state.isInvalidated && !query.isStatic());
        let pending = pendingReads();
        while (pending.length) {
          await Promise.all(pending.map(query => queryClient.fetchQuery(query.options).catch(error => {
            // Another container's save may cancel this GET; join/restart its
            // replacement before releasing this container's mutation lock.
            if (!isCancelledError(error)) throw error;
          })));
          pending = pendingReads();
        }
      } catch (error) {
        // The write succeeded. Keep the rendered inventory and never retry PUT.
        setRefreshFailure({ sourceId: selection.sourceId, containerName: selection.containerName, error });
      }
    },
  });
  const selectedEndpoint = container.endpoints.find(endpoint => endpoint.selected) ?? null;
  const selectedEndpointId = selectedEndpoint?.id
    ?? (container.preferences?.selectedEndpoint?.id === NO_ENDPOINT_ID ? NO_ENDPOINT_ID : null);
  const selectEndpoint = (selectedEndpoint) => {
    // QueryClient's mutation state changes synchronously, including between two clicks in one render.
    if (queryClient.isMutating(filters)) return;
    mutation.mutate({ sourceId: container.sourceId, containerName: container.name, selectedEndpoint });
  };
  return { selectedEndpoint, selectedEndpointId, selectEndpoint, isSaving: savingCount > 0, saveError: mutation.error, refreshError };
};
