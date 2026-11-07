import { describe, it, expect } from 'vitest';
import { matchesFilter, matchesLinkFilter } from '../../utils/filter';
import { createMockContainer } from '../testUtils';

const ct = createMockContainer({
  name: 'smh-sonarr-1',
  displayName: 'Sonarr',
  image: 'lscr.io/linuxserver/sonarr:latest',
  endpoints: [{ id: 't', source: 'traefik', url: 'https://tv.example.com/', selected: false }],
});

describe('matchesFilter', () => {
  it.each(['sonarr', 'SONARR', 'smh-', 'linuxserver', 'tv.example', 'walk', '', '   '])('matches %j', q =>
    expect(matchesFilter(ct, q, 'Walker')).toBe(true));

  it('rejects a non-matching query and trims whitespace', () => {
    expect(matchesFilter(ct, 'plex', 'Walker')).toBe(false);
    expect(matchesFilter(ct, '  sonarr  ', 'Walker')).toBe(true);
  });
});

describe('matchesLinkFilter', () => {
  const link = { name: 'Router', url: 'http://192.168.50.1/' };

  it('matches everything with no query', () => {
    expect(matchesLinkFilter(link, '  ')).toBe(true);
  });

  it('matches name, URL and the External source, ignoring case', () => {
    expect(matchesLinkFilter(link, 'ROUT')).toBe(true);
    expect(matchesLinkFilter(link, '50.1')).toBe(true);
    expect(matchesLinkFilter(link, 'exter')).toBe(true);
  });

  it('rejects anything else', () => {
    expect(matchesLinkFilter(link, 'nas')).toBe(false);
  });
});
