import { createElement, useState, useCallback } from 'react';
import { useContainerPrefs } from './useContainerPrefs';
import ContainerSettings from '../components/ContainerSettings';
import { containerStatus } from '../utils/status';

/**
 * Shared behaviour of a container tile or chip: preferences, derived status and
 * the owned settings dialog. `onSettingsOpenChange(container, open)` is optional.
 */
export const useContainerCard = (container, { sourceOnline = true, allowLegacyMigration = false, onSettingsOpenChange } = {}) => {
  const [open, setOpen] = useState(false);
  const prefs = useContainerPrefs(container, allowLegacyMigration);
  const showUpdate = Boolean(prefs.checkUpdates && container.version?.updateAvailable);
  const status = containerStatus(container, { sourceOnline, showUpdate });

  const openSettings = useCallback(() => {
    setOpen(true);
    onSettingsOpenChange?.(container, true);
  }, [onSettingsOpenChange, container]);
  const closeSettings = useCallback(() => {
    setOpen(false);
    onSettingsOpenChange?.(container, false);
  }, [onSettingsOpenChange, container]);

  const settingsDialog = open
    ? createElement(ContainerSettings, {
      containerName: container.name,
      endpoints: container.endpoints,
      selectedEndpointId: prefs.selectedEndpointId,
      checkUpdates: prefs.checkUpdates,
      onClose: closeSettings,
      onSelectEndpoint: prefs.selectEndpoint,
      onToggleCheckUpdates: prefs.setCheckUpdates,
    })
    : null;

  return { prefs, status, showUpdate, openSettings, settingsDialog };
};
