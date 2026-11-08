import { useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createLink, deleteEntry, selectEntryIcon, updateLink, uploadEntryIcon } from '../api';
import { DASHBOARD_KEY } from '../utils/dashboard';

/**
 * Cancels polls around a write so one begun earlier can't overwrite it, then refetches.
 * These run even if the dialog that started the write has closed.
 */
const useRefreshOptions = () => {
  const queryClient = useQueryClient();
  return {
    onMutate: () => queryClient.cancelQueries({ queryKey: DASHBOARD_KEY }),
    onSettled: async () => {
      await queryClient.cancelQueries({ queryKey: DASHBOARD_KEY });
      await queryClient.invalidateQueries({ queryKey: DASHBOARD_KEY });
    },
  };
};

export const useCreateLink = () => useMutation({ mutationFn: details => createLink(details), ...useRefreshOptions() });

/** Saves for one link run in order, so a later edit can't be overwritten by an earlier one. */
export const useUpdateLink = id => useMutation({
  mutationFn: details => updateLink(id, details),
  scope: { id: `link-${id}` },
  ...useRefreshOptions(),
});

export const useDeleteLink = () => useMutation({ mutationFn: id => deleteEntry(id), ...useRefreshOptions() });

const saveLinkIcon = ({ id, name, file }) => (file
  ? uploadEntryIcon({ entryId: id, file })
  : selectEntryIcon({ entryId: id, name }));

/** Saves an icon for a link given when saving, for a link that may not have existed at render. */
export const useSetLinkIcon = () => useMutation({ mutationFn: request => saveLinkIcon(request), ...useRefreshOptions() });

/** Changes a link's icon. Has the shape IconPicker expects; `retry` repeats the last save that started. */
export const useLinkIcon = (link) => {
  const lastSave = useRef(null);
  const mutation = useMutation({
    mutationKey: ['link-icon', link.id],
    mutationFn: request => saveLinkIcon(request),
    ...useRefreshOptions(),
  });
  const start = (variables, { onSaved } = {}) => {
    if (mutation.isPending) return;
    lastSave.current = { variables, onSaved };
    mutation.mutate(variables, onSaved ? { onSuccess: () => onSaved() } : undefined);
  };
  return {
    selectIcon: (name, options) => start({ id: link.id, name }, options),
    uploadIcon: (file, options) => start({ id: link.id, file }, options),
    retry: () => {
      if (lastSave.current) start(lastSave.current.variables, lastSave.current);
    },
    isSaving: mutation.isPending,
    saveError: mutation.error,
    clearError: mutation.reset,
  };
};
