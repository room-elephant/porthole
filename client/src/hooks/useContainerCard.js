import { createElement, useState, useCallback, useRef, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useContainerPrefs } from './useContainerPrefs';
import ContainerSettings from '../components/ContainerSettings';
import { containerStatus } from '../utils/status';

/**
 * Shared behaviour of a container tile or chip: preferences, derived status and
 * the owned settings dialog. `onSettingsOpenChange(container, open)` is optional.
 */
export const useContainerCard = (container, { sourceOnline = true, sourceName, onSettingsOpenChange } = {}) => {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
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
  const prefs = useContainerPrefs(container);
  const showUpdate = Boolean(prefs.checkUpdates && container.version?.updateAvailable);
  const status = containerStatus(container, { sourceOnline, showUpdate });

  const openSettings = useCallback(() => {
    setOpen(true);
    onSettingsOpenChange?.(container, true);
  }, [onSettingsOpenChange, container]);
  const closeSettings = useCallback(() => {
    setOpen(false);
    // Active confirmation can reach the cache before React delivers updated inventory props.
    const confirmed = queryClient.getQueriesData({ queryKey: ['containers'], type: 'active' })
      .flatMap(([, inventory]) => inventory?.containers ?? [])
      .find(candidate => candidate.sourceId === container.sourceId && candidate.name === container.name);
    onSettingsOpenChange?.(confirmed ?? container, false);
  }, [onSettingsOpenChange, container, queryClient]);

  const settingsDialog = open
    ? createElement(ContainerSettings, {
      container,
      sourceName,
      status,
      showUpdate,
      selectedEndpointId: prefs.selectedEndpointId,
      checkUpdates: prefs.checkUpdates,
      isSaving: prefs.isSaving,
      saveError: prefs.saveError,
      refreshError: prefs.refreshError,
      onClose: closeSettings,
      onSelectEndpoint: prefs.selectEndpoint,
      onToggleCheckUpdates: prefs.setCheckUpdates,
    })
    : null;

  return { prefs, status, showUpdate, openSettings, settingsDialog };
};
