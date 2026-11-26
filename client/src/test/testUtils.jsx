import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { DASHBOARD_KEY, splitDashboard } from '../utils/dashboard';

/**
 * Creates a wrapper with QueryClientProvider for testing hooks.
 */
export const createQueryWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });
  
  return ({ children }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );
};

/**
 * Renders a component wrapped with QueryClientProvider.
 */
export const renderWithQuery = (ui, options = {}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

  const Wrapper = ({ children }) => (
    <QueryClientProvider client={queryClient}>
      {children}
    </QueryClientProvider>
  );

  return { ...render(ui, { wrapper: Wrapper, ...options }), queryClient };
};

/**
 * Creates a mock container object for testing.
 */
export const createMockContainer = (overrides = {}) => withEntryId({
  id: 'container-1',
  sourceId: 'local',
  name: 'test-container',
  displayName: 'Test Container',
  image: 'test-image:latest',
  state: 'running',
  status: 'Up 2 hours',
  project: null,
  endpoints: [publishedEndpoint(8080, true)],
  hasEndpoints: true,
  hasWebUi: overrides.hasEndpoints ?? true,
  iconUrl: 'https://example.com/icon.png',
  self: false,
  ...overrides,
});

/** Adds the entry ID the server reports for a container, unless the fixture set one. */
export const withEntryId = container => ({ entryId: `container:${container.sourceId}:${container.name}`, ...container });

export const publishedEndpoint = (port, selected = false) => ({ id: `published-port:${port}`, source: 'published-port', url: `http://localhost:${port}/`, selected });

/** The dashboard entry the server sends for a container view fixture. */
export const toEntry = (view) => {
  const container = withEntryId(view);
  return {
    id: container.entryId,
    kind: 'container',
    name: container.displayName,
    stack: container.project ?? null,
    icon: { url: container.iconUrl ?? null, choice: container.iconChoice ?? null },
    endpoints: container.endpoints,
    hasWebUi: container.hasWebUi,
    container: {
      sourceId: container.sourceId,
      name: container.name,
      defaultName: container.defaultName ?? container.displayName,
      image: container.image,
      state: container.state ?? null,
      status: container.status ?? null,
      self: Boolean(container.self),
      version: container.version ?? null,
    },
  };
};

/** The link entry the server sends for a link view fixture. */
export const toLinkEntry = link => ({
  id: link.id,
  kind: 'link',
  name: link.name,
  stack: link.stack ?? null,
  icon: { url: link.iconUrl ?? null, choice: link.iconChoice ?? null },
  endpoints: [{ id: 'custom', source: 'custom', url: link.url, selected: true }],
  hasWebUi: true,
  container: null,
});

/** GET /api/dashboard for view fixtures: `{ sources, containers, links }`. */
export const toDashboard = ({ sources = [], containers = [], links = [] }) => ({
  sources,
  entries: [...containers.map(toEntry), ...links.map(toLinkEntry)],
});

/** A dashboard query in a test, with the same key and view selection as the app. */
export const dashboardQuery = queryFn => ({
  queryKey: DASHBOARD_KEY,
  queryFn: async context => toDashboard(await queryFn(context)),
  select: splitDashboard,
});

/** Render the real inventory-to-component flow so confirmed cache updates reach props. */
export const renderInventory = (children, inventory, queryFn = () => inventory) => {
  function InventoryView() {
    const { data } = useQuery({
      ...dashboardQuery(queryFn),
      initialData: toDashboard(inventory),
      staleTime: Infinity,
    });
    return children(data);
  }
  return renderWithQuery(<InventoryView />);
};

/** Resolved GET fixture after an explicit choice; automatic resolution belongs to server tests. */
export const inventoryWithSelection = (inventory, { entryId, selectedEndpoint }) => ({
  ...inventory,
  containers: inventory.containers.map(container => {
    if (withEntryId(container).entryId !== entryId) return container;
    const endpoints = container.endpoints.filter(endpoint => endpoint.source !== 'custom')
      .map(endpoint => ({ ...endpoint, selected: endpoint.id === selectedEndpoint.id && endpoint.source === selectedEndpoint.source }));
    if (selectedEndpoint.source === 'custom') endpoints.push({ ...selectedEndpoint, selected: true });
    return { ...container, endpoints, hasEndpoints: endpoints.length > 0, hasWebUi: selectedEndpoint.source !== 'none' && endpoints.length > 0 };
  }),
});
