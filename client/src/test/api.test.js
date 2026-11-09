import { describe, it, expect, vi, beforeEach } from 'vitest';

// Create mock axios instance before importing api
const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockDelete = vi.fn();
const mockPatch = vi.fn();
const mockAxiosInstance = {
  get: mockGet,
  post: mockPost,
  put: mockPut,
  patch: mockPatch,
  delete: mockDelete,
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
const { fetchDashboard, fetchIconLibrary, refreshVersions, patchEntry, toEndpointPatch, selectContainerEndpoint, uploadIcon, selectEntryIcon, uploadEntryIcon, createLink, updateLink, deleteEntry } = await import('../api');

const MERGE_PATCH = { headers: { 'Content-Type': 'application/merge-patch+json' } };

describe('api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('posts a manual version refresh', async () => {
    mockPost.mockResolvedValue({ status: 202 });
    await refreshVersions();
    expect(mockPost).toHaveBeenCalledWith('/api/versions/refresh');
  });

  it('patches an entry by its encoded id with merge-patch json', async () => {
    mockPatch.mockResolvedValue({ status: 204 });
    expect(await patchEntry('container:walker:a.b', { name: 'App' })).toBeUndefined();
    expect(mockPatch).toHaveBeenCalledWith('/api/entries/container%3Awalker%3Aa.b', { name: 'App' }, MERGE_PATCH);
  });

  it('maps every endpoint selection to its patch value', () => {
    expect(toEndpointPatch(null)).toBeNull();
    expect(toEndpointPatch(undefined)).toBeNull();
    expect(toEndpointPatch({ id: 'none', source: 'none' })).toBe('none');
    expect(toEndpointPatch({ id: 'custom', source: 'custom', url: 'https://app.example.com/' })).toEqual({ url: 'https://app.example.com/' });
    expect(toEndpointPatch({ id: 'traefik:https://a/', source: 'traefik' })).toEqual({ id: 'traefik:https://a/' });
  });

  it('saves an endpoint selection on the container entry', async () => {
    mockPatch.mockResolvedValue({ status: 204 });
    await selectContainerEndpoint({ entryId: 'container:remote:app', selectedEndpoint: { id: 'custom', source: 'custom', url: 'https://app.example.com/' } });
    expect(mockPatch).toHaveBeenCalledWith('/api/entries/container%3Aremote%3Aapp', { endpoint: { url: 'https://app.example.com/' } }, MERGE_PATCH);
    await selectContainerEndpoint({ entryId: 'container:remote:app', selectedEndpoint: null });
    expect(mockPatch).toHaveBeenLastCalledWith('/api/entries/container%3Aremote%3Aapp', { endpoint: null }, MERGE_PATCH);
  });

  it('saves a library icon on an entry', async () => {
    mockPatch.mockResolvedValue({ status: 204 });
    expect(await selectEntryIcon({ entryId: 'container:remote:app', name: 'coredns' })).toBeUndefined();
    expect(mockPatch).toHaveBeenCalledWith('/api/entries/container%3Aremote%3Aapp', { icon: { library: 'coredns' } }, MERGE_PATCH);
  });

  it('posts raw icon bytes with the file type and a longer timeout', async () => {
    mockPost.mockResolvedValue({ status: 201, data: { id: 'abc.png' } });
    const file = new File(['x'], 'logo.png', { type: 'image/png' });
    expect(await uploadIcon(file)).toBe('abc.png');
    expect(mockPost).toHaveBeenCalledWith('/api/icons', file, { headers: { 'Content-Type': 'image/png' }, timeout: 30000 });
  });

  it('posts a file without a type as octet-stream', async () => {
    mockPost.mockResolvedValue({ status: 201, data: { id: 'abc.svg' } });
    const file = new File(['<svg/>'], 'logo');
    await uploadIcon(file);
    expect(mockPost.mock.calls[0][2].headers).toEqual({ 'Content-Type': 'application/octet-stream' });
  });

  it('uploads an icon and then saves it on the entry', async () => {
    mockPost.mockResolvedValue({ status: 201, data: { id: 'abc.png' } });
    mockPatch.mockResolvedValue({ status: 204 });
    const file = new File(['x'], 'logo.png', { type: 'image/png' });
    expect(await uploadEntryIcon({ entryId: 'link:00', file })).toBeUndefined();
    expect(mockPatch).toHaveBeenCalledWith('/api/entries/link%3A00', { icon: { upload: 'abc.png' } }, MERGE_PATCH);
  });

  it('does not patch when the upload fails', async () => {
    mockPost.mockRejectedValue(new Error('too large'));
    const file = new File(['x'], 'logo.png', { type: 'image/png' });
    await expect(uploadEntryIcon({ entryId: 'link:00', file })).rejects.toThrow('too large');
    expect(mockPatch).not.toHaveBeenCalled();
  });

  it('fetches the icon library metadata', async () => {
    const signal = new AbortController().signal;
    mockGet.mockResolvedValue({ data: { coredns: {} } });
    expect(await fetchIconLibrary({ signal })).toEqual({ coredns: {} });
    expect(mockGet).toHaveBeenCalledWith('https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/metadata.json', { signal });
  });

  describe('fetchDashboard', () => {
    it('reads the whole dashboard without a timeout', async () => {
      const dashboard = { sources: [], entries: [] };
      const controller = new AbortController();
      mockGet.mockResolvedValue({ data: dashboard });

      expect(await fetchDashboard({ signal: controller.signal })).toEqual(dashboard);
      expect(mockGet).toHaveBeenCalledWith('/api/dashboard', { timeout: 0, signal: controller.signal });
    });

    it('throws error when API call fails', async () => {
      mockGet.mockRejectedValue(new Error('Network error'));

      await expect(fetchDashboard({})).rejects.toThrow('Network error');
    });
  });
});

describe('links api', () => {
  const id = `link:${'a'.repeat(32)}`;
  const details = { name: 'Router', url: 'http://r/', stack: null };

  beforeEach(() => { vi.clearAllMocks(); });

  it('creates a link entry and returns it', async () => {
    mockPost.mockResolvedValue({ data: { id } });
    await expect(createLink(details)).resolves.toEqual({ id });
    expect(mockPost).toHaveBeenCalledWith('/api/entries', details);
  });

  it('updates a link by patching its name, url and stack', async () => {
    mockPatch.mockResolvedValue({});
    await updateLink(id, details);
    expect(mockPatch).toHaveBeenCalledWith(`/api/entries/${encodeURIComponent(id)}`, { name: 'Router', endpoint: { url: 'http://r/' }, stack: null }, MERGE_PATCH);
  });

  it('deletes an entry', async () => {
    mockDelete.mockResolvedValue({});
    await deleteEntry(id);
    expect(mockDelete).toHaveBeenCalledWith(`/api/entries/${encodeURIComponent(id)}`);
  });
});
