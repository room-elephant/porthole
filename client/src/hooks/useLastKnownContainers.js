import { useMemo, useState } from 'react';

/**
 * Keeps showing the last containers seen for a source while it is not UP.
 * @param {{ sources: object[], containers: object[] }} data
 * @returns {object[]} current containers plus the stored list of every non-UP source
 */
export const useLastKnownContainers = ({ sources, containers }) => {
  // A stable Map (not a ref: it is read while rendering, which the refs lint rule forbids).
  const [stored] = useState(() => new Map());
  return useMemo(() => {
    const live = new Set(sources.filter(source => source.status === 'UP').map(source => source.id));
    live.forEach(id => stored.set(id, containers.filter(container => container.sourceId === id)));
    const kept = sources
      .filter(source => !live.has(source.id))
      .flatMap(source => stored.get(source.id) ?? []);
    return [...containers, ...kept];
  }, [stored, sources, containers]);
};
