import { describe, it, expect } from 'vitest';
import {
  currentIconName,
  imageSearchTerm,
  isUploadedIcon,
  libraryIconUrl,
  parseIconLibrary,
  searchIcons,
  validateIconFile,
} from '../../utils/icons';

const lib = parseIconLibrary({
  'pi-hole': {},
  'home-assistant': { aliases: ['hass'] },
  'aliased-icon': { aliases: ['PostgreSQL'] },
  coredns: {},
});
const names = icons => icons.map(icon => icon.name);
const file = (name, type, size) => ({ name, type, size });

describe('parseIconLibrary', () => {
  it('sorts entries by name and defaults aliases', () => {
    expect(parseIconLibrary({ b: {}, a: { aliases: ['x'] } })).toEqual([{ name: 'a', aliases: ['x'] }, { name: 'b', aliases: [] }]);
  });

  it('sorts alphabetically regardless of case', () => {
    expect(names(parseIconLibrary({ Zulu: {}, alpha: {}, Bravo: {} }))).toEqual(['alpha', 'Bravo', 'Zulu']);
  });

  it.each([[null], ['nope'], [[1, 2]], [{}]])('throws on unusable metadata %j', metadata => {
    expect(() => parseIconLibrary(metadata)).toThrow();
  });
});

describe('searchIcons', () => {
  it('ignores punctuation and case', () => {
    expect(names(searchIcons(lib, 'pihole'))).toEqual(['pi-hole']);
    expect(names(searchIcons(lib, 'HomeAssistant'))).toEqual(['home-assistant']);
    expect(names(searchIcons(lib, 'PI-HOLE'))).toEqual(['pi-hole']);
  });

  it('matches aliases', () => {
    expect(names(searchIcons(lib, 'postgres'))).toEqual(['aliased-icon']);
    expect(names(searchIcons(lib, 'hass'))).toEqual(['home-assistant']);
  });

  it('returns everything when nothing searchable remains', () => {
    expect(searchIcons(lib, '')).toHaveLength(lib.length);
    expect(searchIcons(lib, '---')).toHaveLength(lib.length);
    expect(searchIcons(lib, '  ')).toHaveLength(lib.length);
  });

  it('returns nothing without a match', () => {
    expect(searchIcons(lib, 'my-custom-app')).toEqual([]);
  });
});

describe('imageSearchTerm', () => {
  it.each([
    ['lscr.io/linuxserver/transmission:latest', 'transmission'],
    ['registry.example.com:5000/team/app:1', 'app'],
    ['registry.example.com:5000/team/app', 'app'],
    ['nginx@sha256:aaa', 'nginx'],
    ['ghcr.io/org/app:1.2@sha256:aaa', 'app'],
    ['nginx', 'nginx'],
    ['', ''],
    [null, ''],
    [undefined, ''],
    ['Unknown', ''],
    ['sha256:0123456789abcdef', ''],
  ])('%s gives "%s"', (image, term) => {
    expect(imageSearchTerm(image)).toBe(term);
  });
});

describe('iconUrl helpers', () => {
  it('recognizes uploads by their path', () => {
    expect(isUploadedIcon('/api/icons/abc.webp')).toBe(true);
    expect(isUploadedIcon('https://cdn.jsdelivr.net/gh/x/webp/coredns.webp')).toBe(false);
    expect(isUploadedIcon('porthole-mark.svg')).toBe(false);
    expect(isUploadedIcon(undefined)).toBe(false);
  });

  it('reads the library name from the last path segment', () => {
    expect(currentIconName('https://cdn.jsdelivr.net/gh/x/webp/coredns.webp')).toBe('coredns');
    expect(currentIconName('https://cdn.example.com/icons/some.name.svg')).toBe('some.name');
    expect(currentIconName('porthole-mark.svg')).toBe('porthole-mark');
  });

  it('has no library name for an upload or a missing url', () => {
    expect(currentIconName('/api/icons/abc.webp')).toBeNull();
    expect(currentIconName(undefined)).toBeNull();
  });

  it('builds library tile urls', () => {
    expect(libraryIconUrl('coredns')).toBe('https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/webp/coredns.webp');
  });
});

describe('validateIconFile', () => {
  it('checks type before size', () => {
    expect(validateIconFile(file('logo.svg', 'image/svg+xml', 5000000))).toBe("logo.svg isn't supported. Use PNG, WebP or JPEG.");
  });

  it('reports the size of an oversized file', () => {
    expect(validateIconFile(file('a.png', 'image/png', 2516582))).toBe('That file is 2.4 MB. Choose an image up to 1 MB.');
    expect(validateIconFile(file('a.png', 'image/png', 1048577))).toBe('That file is 1.0 MB. Choose an image up to 1 MB.');
  });

  it.each([
    ['a.png', 'image/png', 1048576],
    ['a.webp', 'image/webp', 1],
    ['a.jpg', 'image/jpeg', 1],
  ])('accepts %s', (name, type, size) => {
    expect(validateIconFile(file(name, type, size))).toBeNull();
  });
});
