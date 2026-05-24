import { describe, it, expect, vi, beforeEach } from 'vitest';

// Create mock axios instance before importing api
const mockGet = vi.fn();
const mockAxiosInstance = {
  get: mockGet,
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
const { fetchContainers, fetchContainerVersion, fetchDockerHealth } = await import('../api');

describe('api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('fetchContainers', () => {
    it('fetches containers with default params', async () => {
      const mockData = [{ id: '1', name: 'container-1' }];
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchContainers({ showAll: false, showStopped: false });

      expect(mockGet).toHaveBeenCalledWith('/api/containers', {
        params: { includeWithoutPorts: false, includeStopped: false },
        signal: undefined,
      });
      expect(result).toEqual(mockData);
    });

    it('fetches containers with showAll and showStopped enabled', async () => {
      const mockData = [{ id: '1', name: 'container-1' }];
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchContainers({ showAll: true, showStopped: true });

      expect(mockGet).toHaveBeenCalledWith('/api/containers', {
        params: { includeWithoutPorts: true, includeStopped: true },
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
        params: { includeWithoutPorts: false, includeStopped: false },
        signal: controller.signal,
      });
    });

    it('throws error when API call fails', async () => {
      const error = new Error('Network error');
      mockGet.mockRejectedValue(error);

      await expect(fetchContainers({ showAll: false, showStopped: false })).rejects.toThrow('Network error');
    });
  });

  describe('fetchContainerVersion', () => {
    it('fetches version for a container', async () => {
      const mockData = { hasUpdate: true, currentVersion: '1.0', latestVersion: '2.0' };
      mockGet.mockResolvedValue({ data: mockData });

      const result = await fetchContainerVersion({ containerId: 'container-123' });

      expect(mockGet).toHaveBeenCalledWith('/api/containers/container-123/version', {
        signal: undefined,
      });
      expect(result).toEqual(mockData);
    });

    it('passes abort signal when provided', async () => {
      const mockData = { hasUpdate: false };
      const controller = new AbortController();
      mockGet.mockResolvedValue({ data: mockData });

      await fetchContainerVersion({ containerId: 'container-123', signal: controller.signal });

      expect(mockGet).toHaveBeenCalledWith('/api/containers/container-123/version', {
        signal: controller.signal,
      });
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
