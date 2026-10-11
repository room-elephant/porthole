export const resolveEndpointSelection = (endpoints, savedId) => {
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

const sourceTitle = (source) => {
  if (source === 'traefik') return 'Traefik URLs';
  if (source === 'published-port') return 'Published Ports';
  return `${source.split(/[-_]+/).filter(Boolean).map(word => word[0].toUpperCase() + word.slice(1)).join(' ')} URLs`;
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
