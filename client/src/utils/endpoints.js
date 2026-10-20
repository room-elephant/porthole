import { STORAGE_KEYS } from '../constants';

export const NO_ENDPOINT_ID = 'none';

export const resolveEndpointSelection = (endpoints, savedId) => {
  if (savedId === NO_ENDPOINT_ID) return null;

  const saved = endpoints.find(endpoint => endpoint.id === savedId);
  if (saved) return saved;

  const traefik = endpoints.filter(endpoint => endpoint.source === 'traefik');
  if (traefik.length === 1) return traefik[0];
  if (traefik.length > 1) {
    const roots = traefik.filter(endpoint => new URL(endpoint.url).pathname === '/');
    return roots.length === 1 ? roots[0] : null;
  }
  return endpoints.length === 1 ? endpoints[0] : null;
};

const titleCase = (source) => source.split(/[-_]+/).filter(Boolean).map(word => word[0].toUpperCase() + word.slice(1)).join(' ');

const sourceTitle = (source) => {
  if (source === 'traefik') return 'Traefik URLs';
  if (source === 'published-port') return 'Published Ports';
  return `${titleCase(source)} URLs`;
};

export const endpointLabel = (url) => url.replace(/^[a-z][a-z0-9+.-]*:\/\//i, '').replace(/\/$/, '');

export const endpointTag = (source) => {
  if (source === 'traefik') return 'Traefik';
  if (source === 'published-port') return 'Port';
  return titleCase(source);
};

const sourceOrder = (source) => {
  if (source === 'traefik') return 0;
  if (source === 'published-port') return 1;
  return 2;
};

export const groupEndpointsBySource = (endpoints) => {
  const groups = new Map();
  endpoints.forEach(endpoint => {
    if (!groups.has(endpoint.source)) groups.set(endpoint.source, []);
    groups.get(endpoint.source).push(endpoint);
  });
  return [...groups].sort(([a], [b]) => sourceOrder(a) - sourceOrder(b) || a.localeCompare(b))
    .map(([source, candidates]) => ({ source, title: sourceTitle(source), endpoints: candidates }));
};

/** True when the container has no web UI: no endpoints, or "No web UI" chosen in its settings. */
export const isHeadless = (container) => {
  if (!container.hasEndpoints) return true;
  try {
    const stored = localStorage.getItem(STORAGE_KEYS.ENDPOINT_PREF(container.sourceId, container.name));
    return stored !== null && JSON.parse(stored) === NO_ENDPOINT_ID;
  } catch {
    return false;
  }
};
