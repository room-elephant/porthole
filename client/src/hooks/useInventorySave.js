import { useEffect, useState } from 'react';
import { isCancelledError, useIsMutating, useMutation, useQueryClient } from '@tanstack/react-query';
import { DASHBOARD_KEY } from '../utils/dashboard';

/** Refetches active inventory, again if a cancelled GET left any of it invalidated. */
const refetchUntilConfirmed = async (queryClient) => {
  const pending = queryClient.getQueryCache().findAll({ queryKey: DASHBOARD_KEY, type: 'active' })
    .filter(query => query.state.isInvalidated && !query.isStatic());
  if (pending.length === 0) return;
  await Promise.all(pending.map(query => queryClient.fetchQuery(query.options).catch(error => {
    // Another container's save may cancel this GET; join/restart its
    // replacement before releasing this container's mutation lock.
    if (!isCancelledError(error)) throw error;
  })));
  await refetchUntilConfirmed(queryClient);
};

/**
 * Saves a container setting, then holds the save open until the inventory confirms it.
 * A successful write is never retried, even when the refetch after it fails.
 * `save` returns whether it started; a save already running makes it a no-op.
 */
export const useInventorySave = ({ container, mutationKey, mutationFn }) => {
  const queryClient = useQueryClient();
  const [refreshFailure, setRefreshFailure] = useState(null);
  const refreshError = refreshFailure?.entryId === container.entryId ? refreshFailure.error : null;
  const filters = { mutationKey, exact: true };
  const savingCount = useIsMutating(filters);
  // A later inventory refresh also recovers saved-but-not-refreshed feedback, even
  // when structural sharing preserves the same container object.
  useEffect(() => queryClient.getQueryCache().subscribe(event => {
    if (event.type === 'updated' && event.query.queryKey[0] === DASHBOARD_KEY[0]
      && event.action.type === 'success' && !event.action.manual
      && event.query.state.data?.entries?.some(candidate => candidate.id === container.entryId)) {
      setRefreshFailure(null);
    }
  }), [queryClient, container.entryId]);
  const mutation = useMutation({
    mutationKey,
    mutationFn,
    onMutate: () => {
      setRefreshFailure(null);
      return queryClient.cancelQueries({ queryKey: DASHBOARD_KEY });
    },
    onSuccess: async () => {
      // Polls begun during PUT must not overwrite the subsequent resolved GET.
      await queryClient.cancelQueries({ queryKey: DASHBOARD_KEY });
      try {
        // Invalidate every variant; inactive ones refresh when next used. Await
        // the active queries directly so a paused GET also holds the save lock.
        await queryClient.invalidateQueries({ queryKey: DASHBOARD_KEY, refetchType: 'none' });
        await refetchUntilConfirmed(queryClient);
      } catch (error) {
        // The write succeeded. Keep the rendered inventory and never retry PUT.
        setRefreshFailure({ entryId: container.entryId, error });
      }
    },
  });
  const save = (variables, { onSaved } = {}) => {
    // QueryClient's mutation state changes synchronously, including between two clicks in one render.
    if (queryClient.isMutating(filters)) return false;
    mutation.mutate(variables, onSaved ? { onSuccess: () => onSaved() } : undefined);
    return true;
  };
  return { save, isSaving: savingCount > 0, saveError: mutation.error, refreshError, clearError: mutation.reset };
};
