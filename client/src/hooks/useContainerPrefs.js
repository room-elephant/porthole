import { useEndpointSelection } from './useEndpointSelection';
import { useVersionPreference } from './useVersionPreference';

/**
 * Per-container preferences: which endpoint to open and whether update
 * notifications are shown. Both persist in localStorage and stay in sync
 * between every component that reads the same container.
 */
export const useContainerPrefs = (container, allowLegacyMigration = false) => {
  const { selectedEndpoint, selectedEndpointId, selectEndpoint } = useEndpointSelection(
    container.sourceId, container.name, container.endpoints, { migrateLegacy: allowLegacyMigration });
  const [checkUpdates, setCheckUpdates] = useVersionPreference(container.sourceId, container.name, allowLegacyMigration);
  return { selectedEndpoint, selectedEndpointId, selectEndpoint, checkUpdates, setCheckUpdates };
};
