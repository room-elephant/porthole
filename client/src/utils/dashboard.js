/** The dashboard query's key; every write refreshes it. */
export const DASHBOARD_KEY = ['dashboard'];

const STOPPED_STATES = new Set(['created', 'exited', 'dead', 'removing']);

/** A container entry in the shape the tiles, chips and settings dialog read. */
export const toContainerView = (entry) => ({
  entryId: entry.id,
  sourceId: entry.container.sourceId,
  name: entry.container.name,
  displayName: entry.name,
  defaultName: entry.container.defaultName,
  image: entry.container.image,
  endpoints: entry.endpoints,
  hasEndpoints: entry.endpoints.length > 0,
  hasWebUi: entry.hasWebUi,
  iconUrl: entry.icon.url,
  iconChoice: entry.icon.choice,
  project: entry.stack,
  state: entry.container.state,
  status: entry.container.status,
  version: entry.container.version,
  self: entry.container.self,
});

/** A link entry in the shape the link tile and its settings read. */
export const toLinkView = (entry) => ({
  id: entry.id,
  name: entry.name,
  url: entry.endpoints[0]?.url ?? null,
  stack: entry.stack,
  iconUrl: entry.icon.url,
  iconChoice: entry.icon.choice,
});

/** Sources as served, then container and link views in the server's order. */
export const splitDashboard = (dashboard) => {
  const entries = dashboard?.entries ?? [];
  return {
    sources: dashboard?.sources ?? [],
    containers: entries.filter(entry => entry.kind === 'container').map(toContainerView),
    links: entries.filter(entry => entry.kind === 'link').map(toLinkView),
  };
};

/** The server sends every container; the browser decides which ones the current settings show. */
export const isShown = (container, { showAll, showStopped, showSelf }) => {
  if (!showStopped && STOPPED_STATES.has(container.state)) return false;
  if (!showAll && !container.hasEndpoints) return false;
  return showSelf || !container.self;
};

/** The container as the cached dashboard has it now, which can be ahead of the props React delivered. */
export const cachedContainer = (queryClient, entryId) => {
  const entry = queryClient.getQueryData(DASHBOARD_KEY)?.entries?.find(candidate => candidate.id === entryId);
  return entry?.kind === 'container' ? toContainerView(entry) : undefined;
};
