import { describe, it, expect } from 'vitest';
import { isHeadless, groupEndpointsBySource, endpointLabel, endpointTag } from '../../utils/endpoints';

const port = { id: 'published-port:51413', source: 'published-port', url: 'http://192.168.50.6:51413/', selected: false };
const root = { id: 'traefik:root', source: 'traefik', url: 'https://transmission.example.com/', selected: false };
const path = { id: 'traefik:dl', source: 'traefik', url: 'https://transmission.example.com/dl/', selected: false };
const other = { id: 'ngnext:app', source: 'ngnext', url: 'https://app.example.com/', selected: false };

describe('tile and chip partitioning', () => {
  it.each([
    [{ hasWebUi: true, hasEndpoints: true, endpoints: [port, root] }, false],
    [{ hasWebUi: false, hasEndpoints: true, endpoints: [port] }, true],
    [{ hasWebUi: false, hasEndpoints: false, endpoints: [] }, true],
    [{ hasWebUi: true, hasEndpoints: true, endpoints: [{ id: 'custom', source: 'custom', url: 'https://custom/', selected: true }] }, false],
  ])('partitions resolved inventory %s', (container, expected) => {
    expect(isHeadless(container)).toBe(expected);
  });
});

describe('endpoint groups', () => {
  it('orders preferred sources then unfamiliar sources and preserves every candidate', () => {
    const prototype = { ...other, id: 'prototype', source: '__proto__' };
    const groups = groupEndpointsBySource([other, path, port, root, prototype]);
    expect(groups.map(group => group.source)).toEqual(['traefik', 'published-port', '__proto__', 'ngnext']);
    expect(groups.map(group => group.title)).toEqual(['Traefik URLs', 'Published Ports', 'Proto URLs', 'Ngnext URLs']);
    expect(groups[0].endpoints).toEqual([path, root]);
    expect(groups.flatMap(group => group.endpoints)).toHaveLength(5);
  });
  it('has no sections for no candidates', () => expect(groupEndpointsBySource([])).toEqual([]));
});

describe('endpointLabel', () => {
  it('strips the scheme and one trailing slash', () => {
    expect(endpointLabel('https://sonarr.example.com/')).toBe('sonarr.example.com');
    expect(endpointLabel('http://192.168.50.6:8096/')).toBe('192.168.50.6:8096');
    expect(endpointLabel('https://x.example.com/app/')).toBe('x.example.com/app');
  });
});

describe('endpointTag', () => {
  it('names known and unknown sources', () => {
    expect(endpointTag('traefik')).toBe('Traefik');
    expect(endpointTag('published-port')).toBe('Port');
    expect(endpointTag('ng_next')).toBe('Ng Next');
  });
});
