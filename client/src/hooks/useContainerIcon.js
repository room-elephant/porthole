import { useRef } from 'react';
import { selectEntryIcon, uploadEntryIcon } from '../api';
import { useInventorySave } from './useInventorySave';

/** Changes a container's icon. `retry` repeats the last save that started. */
export const useContainerIcon = (container) => {
  const lastSave = useRef(null);
  const { save, isSaving, saveError, refreshError, clearError } = useInventorySave({
    container,
    mutationKey: ['container-icon', container.sourceId, container.name],
    mutationFn: ({ file, ...request }) => (file ? uploadEntryIcon({ ...request, file }) : selectEntryIcon(request)),
  });
  const identity = { entryId: container.entryId };
  const start = (variables, options) => {
    if (save(variables, options)) lastSave.current = { variables, options };
  };
  return {
    selectIcon: (name, options) => start({ ...identity, name }, options),
    uploadIcon: (file, options) => start({ ...identity, file }, options),
    retry: () => {
      if (lastSave.current) start(lastSave.current.variables, lastSave.current.options);
    },
    isSaving,
    saveError,
    refreshError,
    clearError,
  };
};
