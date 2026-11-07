import { useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createLink, deleteLink, fetchLinks, selectLinkIcon, updateLink, uploadLinkIcon } from '../api';

const LINKS_KEY = ['links'];

export const useLinks = () => useQuery({
  queryKey: LINKS_KEY,
  queryFn: ({ signal }) => fetchLinks({ signal }),
  retry: false,
  staleTime: 30 * 1000,
  refetchInterval: 30 * 1000,
});

/**
 * Cancels polls around a write so one begun earlier can't overwrite it, then refetches.
 * These run even if the dialog that started the write has closed.
 */
const useRefreshOptions = () => {
  const queryClient = useQueryClient();
  return {
    onMutate: () => queryClient.cancelQueries({ queryKey: LINKS_KEY }),
    onSettled: async () => {
      await queryClient.cancelQueries({ queryKey: LINKS_KEY });
      await queryClient.invalidateQueries({ queryKey: LINKS_KEY });
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

export const useDeleteLink = () => useMutation({ mutationFn: id => deleteLink(id), ...useRefreshOptions() });

/** Changes a link's icon. Has the shape IconPicker expects; `retry` repeats the last save that started. */
export const useLinkIcon = (link) => {
  const lastSave = useRef(null);
  const mutation = useMutation({
    mutationKey: ['link-icon', link.id],
    mutationFn: ({ name, file }) => (file ? uploadLinkIcon(link.id, file) : selectLinkIcon(link.id, name)),
    ...useRefreshOptions(),
  });
  const start = (variables, { onSaved } = {}) => {
    if (mutation.isPending) return;
    lastSave.current = { variables, onSaved };
    mutation.mutate(variables, onSaved ? { onSuccess: () => onSaved() } : undefined);
  };
  return {
    selectIcon: (name, options) => start({ name }, options),
    uploadIcon: (file, options) => start({ file }, options),
    retry: () => {
      if (lastSave.current) start(lastSave.current.variables, lastSave.current);
    },
    isSaving: mutation.isPending,
    saveError: mutation.error,
    clearError: mutation.reset,
  };
};
