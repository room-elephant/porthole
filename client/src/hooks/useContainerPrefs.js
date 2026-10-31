import { useEndpointSelection } from './useEndpointSelection';
import { useVersionPreference } from './useVersionPreference';

/** Shared endpoint choices and browser-local update notification preferences. */
export const useContainerPrefs = (container) => {
  const selection = useEndpointSelection(container);
  const [checkUpdates, setCheckUpdates] = useVersionPreference(container.sourceId, container.name);
  return { ...selection, checkUpdates, setCheckUpdates };
};
