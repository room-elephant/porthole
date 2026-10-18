import { createElement, useState, useCallback, useRef, useEffect } from 'react';
import { useContainerPrefs } from './useContainerPrefs';
import ContainerSettings from '../components/ContainerSettings';
import { containerStatus } from '../utils/status';

/**
 * Shared behaviour of a container tile or chip: preferences, derived status and
 * the owned settings dialog. `onSettingsOpenChange(container, open)` is optional.
 */
export const useContainerCard = (container, { sourceOnline = true, allowLegacyMigration = false, onSettingsOpenChange } = {}) => {
  const [open, setOpen] = useState(false);
  // Latest values for the unmount cleanup below.
  const latest = useRef({});
  useEffect(() => {
    latest.current = { open, container, onSettingsOpenChange };
  });
  // A card that vanishes with its dialog open never gets to close it: release the owner's hold.
  useEffect(() => () => {
    const { open: wasOpen, container: last, onSettingsOpenChange: notify } = latest.current;
    if (wasOpen) notify?.(last, false);
  }, []);
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
