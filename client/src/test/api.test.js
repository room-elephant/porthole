import { describe, it, expect, vi, beforeEach } from 'vitest';

// Create mock axios instance before importing api
const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockAxiosInstance = {
  get: mockGet,
  post: mockPost,
  put: mockPut,
  interceptors: {
    response: {
      use: vi.fn(),
    },
  },
};

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mockAxiosInstance),
  },
}));

// Import after mocking
const { fetchContainers, fetchDockerHealth, refreshVersions, selectContainerEndpoint } = await import('../api');

describe('api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('posts a manual version refresh', async () => {
    mockPost.mockResolvedValue({ status: 202 });
    await refreshVersions();
    expect(mockPost).toHaveBeenCalledWith('/api/versions/refresh');
  });

  it('replaces the shared selection and returns confirmed preferences', async () => {
    const selection = { id: 'custom', source: 'custom', url: 'https://app.example.com/' };
    const confirmed = { sourceId: 'remote', containerName: 'app', preferences: { selectedEndpoint: selection } };
    mockPut.mockResolvedValue({ data: confirmed });
    expect(await selectContainerEndpoint({ sourceId: 'remote', containerName: 'app', selectedEndpoint: selection })).toEqual(confirmed);
    expect(mockPut).toHaveBeenCalledWith('/api/containers/selected-url', confirmed);
    mockPut.mockResolvedValue({ data: { ...confirmed, preferences: { selectedEndpoint: null } } });
    await selectContainerEndpoint({ sourceId: 'remote', containerName: 'app', selectedEndpoint: null });
    expect(mockPut).toHaveBeenLastCalledWith('/api/containers/selected-url', { ...confirmed, preferences: { selectedEndpoint: null } });
  });

  describe('fetchContainers', () => {
    it('fetches containers with default params', async () => {
      const mockData = [{ id: '1', name: 'container-1' }];
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchContainers({ showAll: false, showStopped: false });

      expect(mockGet).toHaveBeenCalledWith('/api/containers', {
        params: { includeWithoutEndpoints: false, includeStopped: false },
        timeout: 0,
        signal: undefined,
      });
      expect(result).toEqual(mockData);
    });

    it('fetches containers with showAll and showStopped enabled', async () => {
      const mockData = [{ id: '1', name: 'container-1' }];
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchContainers({ showAll: true, showStopped: true });

      expect(mockGet).toHaveBeenCalledWith('/api/containers', {
        params: { includeWithoutEndpoints: true, includeStopped: true },
        timeout: 0,
        signal: undefined,
      });
      expect(result).toEqual(mockData);
    });

    it('passes abort signal when provided', async () => {
      const mockData = [];
      const controller = new AbortController();
      mockGet.mockResolvedValue({ data: mockData });

      await fetchContainers({ showAll: false, showStopped: false, signal: controller.signal });

      expect(mockGet).toHaveBeenCalledWith('/api/containers', {
        params: { includeWithoutEndpoints: false, includeStopped: false },
        timeout: 0,
        signal: controller.signal,
      });
    });

    it('throws error when API call fails', async () => {
      const error = new Error('Network error');
      mockGet.mockRejectedValue(error);

      await expect(fetchContainers({ showAll: false, showStopped: false })).rejects.toThrow('Network error');
    });
  });

  describe('fetchDockerHealth', () => {
    it('fetches docker health status', async () => {
      const mockData = { status: 'UP' };
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchDockerHealth();

      expect(mockGet).toHaveBeenCalledWith('/actuator/health/docker', {
        signal: undefined,
      });
      expect(result).toEqual(mockData);
    });

    it('passes abort signal when provided', async () => {
      const mockData = { status: 'UP' };
      const controller = new AbortController();
      mockGet.mockResolvedValue({ data: mockData });

      await fetchDockerHealth({ signal: controller.signal });

      expect(mockGet).toHaveBeenCalledWith('/actuator/health/docker', {
        signal: controller.signal,
      });
    });

    it('handles DOWN status', async () => {
      const mockData = { status: 'DOWN' };
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchDockerHealth();

      expect(result).toEqual({ status: 'DOWN' });
    });
  });
});
