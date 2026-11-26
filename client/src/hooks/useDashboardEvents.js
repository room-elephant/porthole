import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { API } from '../constants';
import { DASHBOARD_KEY } from '../utils/dashboard';

/** How long to wait before reopening a stream the browser gave up on, for example after a 503. */
export const REOPEN_DELAY = 30 * 1000;

const revisionOf = (event) => {
  try {
    return JSON.parse(event.data).revision;
  } catch {
    return undefined;
  }
};

/**
 * Refetches the dashboard when the server signals a revision the cache doesn't have. Each stream starts with the
 * current revision, so reconnecting after a restart or a lost connection refetches only if something changed.
 */
export const useDashboardEvents = () => {
  const queryClient = useQueryClient();
  useEffect(() => {
    if (typeof EventSource === 'undefined') return undefined;
    let source;
    let reopen;
    const onChanged = (event) => {
      const revision = revisionOf(event);
      if (revision !== undefined && revision === queryClient.getQueryData(DASHBOARD_KEY)?.revision) return;
      queryClient.invalidateQueries({ queryKey: DASHBOARD_KEY });
    };
    const open = () => {
      source = new EventSource(API.EVENTS);
      source.addEventListener('changed', onChanged);
      // The browser retries a dropped connection itself; it gives up only on an error response.
      source.onerror = () => {
        if (source.readyState !== EventSource.CLOSED) return;
        source.close();
        reopen = setTimeout(open, REOPEN_DELAY);
      };
    };
    open();
    return () => {
      clearTimeout(reopen);
      source.close();
    };
  }, [queryClient]);
};
