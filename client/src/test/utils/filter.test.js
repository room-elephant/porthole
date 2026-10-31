import { describe, it, expect } from 'vitest';
import { matchesFilter } from '../../utils/filter';
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
