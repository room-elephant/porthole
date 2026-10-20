import { describe, it, expect } from 'vitest';
import { resolveEndpointSelection, groupEndpointsBySource, endpointLabel, endpointTag } from '../../utils/endpoints';

const port = { id: 'published-port:51413', source: 'published-port', url: 'http://192.168.50.6:51413/' };
const root = { id: 'traefik:root', source: 'traefik', url: 'https://transmission.example.com/' };
const path = { id: 'traefik:dl', source: 'traefik', url: 'https://transmission.example.com/dl/' };
const other = { id: 'ngnext:app', source: 'ngnext', url: 'https://app.example.com/' };

describe('endpoint selection', () => {
  it('honors a saved port over Traefik', () => expect(resolveEndpointSelection([port, root], port.id)).toBe(port));
  it('prefers a sole Traefik URL over other sources', () => expect(resolveEndpointSelection([port, root, other])).toBe(root));
  it('prefers the unique root Traefik route', () => expect(resolveEndpointSelection([path, root, port])).toBe(root));
  it('leaves multiple root routes ambiguous', () => expect(resolveEndpointSelection([root, { ...root, id: 'second', url: 'https://other.example.com/' }])).toBeNull());
  it('leaves multiple non-root routes ambiguous', () => expect(resolveEndpointSelection([path, { ...path, id: 'second', url: 'https://example.com/app' }])).toBeNull());
  it('opens a sole port or unknown provider candidate', () => {
    expect(resolveEndpointSelection([port])).toBe(port);
    expect(resolveEndpointSelection([other])).toBe(other);
  });
  it('leaves multiple candidates without Traefik ambiguous', () => expect(resolveEndpointSelection([port, other])).toBeNull());
  it('returns null for no endpoints', () => expect(resolveEndpointSelection([])).toBeNull());
  it('falls back from an unavailable saved ID', () => expect(resolveEndpointSelection([root], 'removed')).toBe(root));
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
