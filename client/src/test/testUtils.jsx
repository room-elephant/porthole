import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';

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

  return render(ui, { wrapper: Wrapper, ...options });
};

/**
 * Creates a mock container object for testing.
 */
export const createMockContainer = (overrides = {}) => ({
  id: 'container-1',
  sourceId: 'local',
  name: 'test-container',
  displayName: 'Test Container',
  image: 'test-image:latest',
  state: 'running',
  status: 'Up 2 hours',
  project: null,
  endpoints: [publishedEndpoint(8080)],
  hasEndpoints: true,
  iconUrl: 'https://example.com/icon.png',
  self: false,
  ...overrides,
});

export const publishedEndpoint = port => ({ id: `published-port:${port}`, source: 'published-port', url: `http://localhost:${port}/` });
