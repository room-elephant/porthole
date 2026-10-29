export const NO_ENDPOINT_ID = 'none';

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

/** Ambiguous choices stay tiles; explicit none uses a chip. */
export const isHeadless = (container) => {
  if (container.endpoints.some(endpoint => endpoint.selected)) return false;
  return container.preferences?.selectedEndpoint?.id === NO_ENDPOINT_ID || !container.hasEndpoints;
};
