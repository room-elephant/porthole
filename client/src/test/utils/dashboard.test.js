import { describe, it, expect } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { cachedContainer, isShown, splitDashboard, toContainerView, toLinkView } from '../../utils/dashboard';

const containerEntry = (overrides = {}) => ({
  id: 'container:walker:smh-sonarr-1',
  kind: 'container',
  name: 'sonarr',
  stack: 'smh',
  icon: { url: 'https://cdn.example/sonarr.webp', choice: null },
  endpoints: [{ id: 'traefik:https://sonarr.example/', source: 'traefik', url: 'https://sonarr.example/', selected: true }],
  hasWebUi: true,
  container: {
    sourceId: 'walker',
    name: 'smh-sonarr-1',
    defaultName: 'sonarr',
    image: 'linuxserver/sonarr:4',
    state: 'running',
    status: 'Up 2 hours',
    self: false,
    version: { currentVersion: '4.0.1', latestVersion: '4.0.2', updateAvailable: true },
  },
  ...overrides,
});

const linkEntry = (overrides = {}) => ({
  id: 'link:3f2a9c1e5b7d4e8a9c217d4e5f6a8b90',
  kind: 'link',
  name: 'Router',
  stack: 'base',
  icon: { url: '/api/icons/abc.webp', choice: { upload: 'abc.webp' } },
  endpoints: [{ id: 'custom', source: 'custom', url: 'http://192.168.50.1/', selected: true }],
  hasWebUi: true,
  container: null,
  ...overrides,
});

describe('dashboard', () => {
  it('maps a container entry to the view the components use', () => {
    expect(toContainerView(containerEntry())).toEqual({
      entryId: 'container:walker:smh-sonarr-1',
      sourceId: 'walker',
      name: 'smh-sonarr-1',
      displayName: 'sonarr',
      defaultName: 'sonarr',
      image: 'linuxserver/sonarr:4',
      endpoints: containerEntry().endpoints,
      hasEndpoints: true,
      hasWebUi: true,
      iconUrl: 'https://cdn.example/sonarr.webp',
      iconChoice: null,
      project: 'smh',
      state: 'running',
      status: 'Up 2 hours',
      version: { currentVersion: '4.0.1', latestVersion: '4.0.2', updateAvailable: true },
      self: false,
    });
  });

  it('maps a link entry', () => {
    expect(toLinkView(linkEntry())).toEqual({
      id: 'link:3f2a9c1e5b7d4e8a9c217d4e5f6a8b90',
      name: 'Router',
      url: 'http://192.168.50.1/',
      stack: 'base',
      iconUrl: '/api/icons/abc.webp',
      iconChoice: { upload: 'abc.webp' },
    });
  });

  it('splits entries by kind and keeps order', () => {
    const sources = [{ id: 'walker', name: 'Walker', status: 'UP', error: null, lastSuccessAt: null }];
    const second = containerEntry({ id: 'container:walker:b', container: { ...containerEntry().container, name: 'b' } });
    const split = splitDashboard({ sources, entries: [containerEntry(), linkEntry(), second] });

    expect(split.sources).toBe(sources);
    expect(split.containers.map(container => container.entryId)).toEqual(['container:walker:smh-sonarr-1', 'container:walker:b']);
    expect(split.links.map(link => link.id)).toEqual([linkEntry().id]);
  });

  it('splits an empty or missing dashboard', () => {
    expect(splitDashboard(undefined)).toEqual({
      revision: null, versionChecks: { running: false, lastCompletedAt: null }, sources: [], containers: [], links: [],
    });
  });

  it('carries the revision and version-check state', () => {
    const versionChecks = { running: true, lastCompletedAt: '2026-11-08T09:00:00Z' };
    const split = splitDashboard({ revision: 42, versionChecks, sources: [], entries: [] });

    expect(split.revision).toBe(42);
    expect(split.versionChecks).toBe(versionChecks);
  });

  it('filters stopped and endpoint-less containers locally', () => {
    const running = toContainerView(containerEntry());
    const stopped = { ...running, state: 'exited' };
    const quiet = { ...running, endpoints: [], hasEndpoints: false, hasWebUi: false };
    const customOnly = { ...quiet, endpoints: [{ id: 'custom', source: 'custom', url: 'https://x/', selected: true }], hasEndpoints: true };
    const settings = { showAll: false, showStopped: false, showSelf: true };

    expect(isShown(running, settings)).toBe(true);
    expect(isShown(stopped, settings)).toBe(false);
    expect(isShown(stopped, { ...settings, showStopped: true })).toBe(true);
    expect(isShown(quiet, settings)).toBe(false);
    expect(isShown(quiet, { ...settings, showAll: true })).toBe(true);
    expect(isShown(customOnly, settings)).toBe(true);
    ['created', 'dead', 'removing'].forEach(state => expect(isShown({ ...running, state }, settings)).toBe(false));
    ['paused', 'restarting', null].forEach(state => expect(isShown({ ...running, state }, settings)).toBe(true));
  });

  it('hides self unless asked', () => {
    const self = { ...toContainerView(containerEntry()), self: true };

    expect(isShown(self, { showAll: true, showStopped: true, showSelf: false })).toBe(false);
    expect(isShown(self, { showAll: true, showStopped: true, showSelf: true })).toBe(true);
  });

  it('finds a container in the dashboard cache', () => {
    const queryClient = new QueryClient();
    expect(cachedContainer(queryClient, 'container:walker:smh-sonarr-1')).toBeUndefined();
    queryClient.setQueryData(['dashboard'], { sources: [], entries: [containerEntry(), linkEntry()] });

    expect(cachedContainer(queryClient, 'container:walker:smh-sonarr-1')?.displayName).toBe('sonarr');
    expect(cachedContainer(queryClient, 'container:walker:gone')).toBeUndefined();
  });
});
